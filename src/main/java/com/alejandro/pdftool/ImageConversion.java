package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Conversión entre imágenes y páginas PDF. */
final class ImageConversion {

    /** Al usar el tamaño de la imagen, se supone una resolución de pantalla de 96 ppp. */
    private static final float SCREEN_DPI = 96f;
    private static final float JPEG_EXPORT_QUALITY = 0.9f;

    private ImageConversion() {
    }

    /** Imagen lista para dibujar y giro (horario) necesario para verla derecha. */
    private record LoadedImage(PDImageXObject image, int rotation) {
        float displayWidth() {
            return rotation % 180 == 0 ? image.getWidth() : image.getHeight();
        }

        float displayHeight() {
            return rotation % 180 == 0 ? image.getHeight() : image.getWidth();
        }
    }

    /**
     * Añade la imagen como página, ajustada y centrada. Un TIFF de varias páginas (p. ej. un escaneo o un fax)
     * añade una página por cada una.
     *
     * @return páginas añadidas
     */
    static int addImagePages(PDDocument doc, Path file, ImagePageSize size, float margin) throws IOException {
        List<LoadedImage> images = load(doc, file);
        for (LoadedImage image : images) {
            addPage(doc, image, size, margin);
        }
        return images.size();
    }

    private static void addPage(PDDocument doc, LoadedImage loaded, ImagePageSize size, float margin) throws IOException {
        float imageWidth = loaded.displayWidth();
        float imageHeight = loaded.displayHeight();

        PDRectangle pageBox;
        if (size.paper() == null) {
            pageBox = new PDRectangle(imageWidth * 72f / SCREEN_DPI, imageHeight * 72f / SCREEN_DPI);
            margin = 0;
        } else {
            PDRectangle paper = size.paper();
            boolean landscape = imageWidth > imageHeight;
            pageBox = landscape ? new PDRectangle(paper.getHeight(), paper.getWidth()) : paper;
        }
        float freeWidth = pageBox.getWidth() - 2 * margin;
        float freeHeight = pageBox.getHeight() - 2 * margin;
        if (freeWidth <= 0 || freeHeight <= 0) {
            throw new IllegalArgumentException("El margen es demasiado grande para el tamaño de página.");
        }
        float scale = Math.min(freeWidth / imageWidth, freeHeight / imageHeight);
        float w = imageWidth * scale;
        float h = imageHeight * scale;
        float x = (pageBox.getWidth() - w) / 2f;
        float y = (pageBox.getHeight() - h) / 2f;

        PDPage page = new PDPage(pageBox);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(loaded.image(), placement(loaded.rotation(), x, y, w, h));
        }
    }

    /**
     * Matriz que lleva el cuadrado unidad de la imagen al rectángulo (x, y, w, h) de la página, girándola
     * {@code rotation} grados en sentido horario (w y h son las medidas ya giradas).
     */
    static Matrix placement(int rotation, float x, float y, float w, float h) {
        return switch (rotation) {
            case 90 -> new Matrix(0, -h, w, 0, x, y + h);
            case 180 -> new Matrix(-w, 0, 0, -h, x + w, y + h);
            case 270 -> new Matrix(0, h, -w, 0, x + w, y);
            default -> new Matrix(w, 0, 0, h, x, y);
        };
    }

    private static List<LoadedImage> load(PDDocument doc, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new PdfToolException("No existe la imagen «" + file + "».");
        }
        byte[] bytes = Files.readAllBytes(file);
        if (isJpeg(bytes)) {
            try {
                // El JPEG se incrusta tal cual: sin recomprimir ni perder calidad
                return List.of(new LoadedImage(JPEGFactory.createFromByteArray(doc, bytes),
                        ExifOrientation.rotationDegrees(bytes)));
            } catch (IOException | RuntimeException e) {
                // JPEG poco habitual (p. ej. 12 bits): se decodifica y se guarda sin pérdida
            }
        }
        List<LoadedImage> images = new ArrayList<>();
        for (BufferedImage frame : decode(file, bytes)) {
            images.add(new LoadedImage(LosslessFactory.createFromImage(doc, frame), 0));
        }
        return images;
    }

    /** Decodifica la imagen; de un TIFF devuelve todas sus páginas (de un GIF animado, solo el primer fotograma). */
    private static List<BufferedImage> decode(Path file, byte[] bytes) throws PdfToolException {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new PdfToolException("Formato de imagen no admitido: «" + Pdfs.name(file)
                        + "». Usa JPG, PNG, GIF, BMP o TIFF.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                boolean allPages = !"gif".equalsIgnoreCase(reader.getFormatName());
                int count = allPages ? Math.max(1, reader.getNumImages(true)) : 1;
                List<BufferedImage> frames = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    frames.add(reader.read(i));
                }
                return frames;
            } finally {
                reader.dispose();
            }
        } catch (PdfToolException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new PdfToolException("No se ha podido leer la imagen «" + Pdfs.name(file) + "»: " + e.getMessage(), e);
        }
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    static void writeImage(BufferedImage image, ImageFormat format, OutputStream out) throws IOException {
        String writerFormat = format == ImageFormat.JPG ? "jpeg" : "png";
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(writerFormat);
        if (!writers.hasNext()) {
            throw new PdfToolException("Java no tiene un codificador " + writerFormat.toUpperCase() + " disponible.");
        }
        ImageWriter writer = writers.next();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format == ImageFormat.JPG) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_EXPORT_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
