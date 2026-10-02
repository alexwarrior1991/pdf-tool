package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Generadores de PDFs e imágenes para los tests. */
final class TestPdfs {

    private TestPdfs() {
    }

    /** PDF A4 con {@code pages} páginas; cada una contiene el texto «Pagina N». */
    static Path textPdf(Path file, int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 24);
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Pagina " + i);
                    cs.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /**
     * PDF cuyas páginas heredan /Resources, /MediaBox y /Rotate del nodo raíz /Pages (válido según la
     * especificación y habitual en algunos generadores).
     */
    static Path inheritedAttributesPdf(Path file, int pages) throws IOException {
        Path plain = textPdf(file.resolveSibling("plain-" + file.getFileName()), pages);
        try (PDDocument doc = Loader.loadPDF(plain.toFile())) {
            COSDictionary root = doc.getPages().getCOSObject();
            PDPage first = doc.getPage(0);
            root.setItem(COSName.RESOURCES, first.getResources().getCOSObject());
            root.setItem(COSName.MEDIA_BOX, new PDRectangle(612, 800).getCOSArray());
            root.setInt(COSName.ROTATE, 90);
            for (PDPage page : doc.getPages()) {
                page.getCOSObject().removeItem(COSName.RESOURCES);
                page.getCOSObject().removeItem(COSName.MEDIA_BOX);
                page.getCOSObject().removeItem(COSName.ROTATE);
            }
            doc.save(file.toFile());
        }
        Files.delete(plain);
        return file;
    }

    /** Imagen RGB con degradado y algo de ruido: ocupa mucho sin pérdida y se comprime bien en JPEG. */
    static BufferedImage photo(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255 / width + random.nextInt(24)) & 0xFF;
                int g = (y * 255 / height + random.nextInt(24)) & 0xFF;
                int b = ((x + y) * 128 / (width + height) + random.nextInt(24)) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    /** Dibuja la misma imagen (un único XObject) en cada página, al tamaño indicado en puntos. */
    static Path sharedImagePdf(Path file, BufferedImage image, float... sizesPt) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject xobject = LosslessFactory.createFromImage(doc, image);
            for (float size : sizesPt) {
                PDPage page = new PDPage(new PDRectangle(612, 792));
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(xobject, 36, 36, size, size);
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** Página cuyo único contenido es un Form XObject que dibuja la imagen. */
    static Path imageInsideFormPdf(Path file, BufferedImage image) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject xobject = LosslessFactory.createFromImage(doc, image);
            PDFormXObject form = new PDFormXObject(doc);
            form.setBBox(new PDRectangle(500, 500));
            form.setResources(new PDResources());
            try (PDFormContentStream cs = new PDFormContentStream(form)) {
                cs.drawImage(xobject, 0, 0, 500, 500);
            }
            PDPage page = new PDPage(new PDRectangle(612, 792));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawForm(form);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** PDF de una página con la rotación indicada (A4 vertical en el espacio de la página). */
    static Path rotatedBlankPdf(Path file, int rotation) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            page.setRotation(rotation);
            doc.addPage(page);
            doc.save(file.toFile());
        }
        return file;
    }

    static String text(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    /**
     * Texto sin espacios ni saltos de línea: el extractor de PDFBox parte las palabras en páginas giradas o con
     * texto en diagonal («P\nagina 1»), así se puede comprobar el contenido igualmente.
     */
    static String compactText(Path pdf) throws IOException {
        return text(pdf).replaceAll("\\s+", "");
    }

    static int pageCount(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return doc.getNumberOfPages();
        }
    }

    /** Renderiza la página tal y como se ve (aplicando /Rotate). */
    static BufferedImage render(Path pdf, int pageIndex, float dpi) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return new PDFRenderer(doc).renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
        }
    }

    /** Píxeles "rojizos" dentro de un cuadrado de lado {@code size} centrado en (cx, cy). */
    static int reddish(BufferedImage image, int cx, int cy, int size) {
        return count(image, cx, cy, size, (r, g, b) -> r > 150 && g < 120 && b < 120);
    }

    /** Píxeles oscuros dentro de un cuadrado de lado {@code size} centrado en (cx, cy). */
    static int dark(BufferedImage image, int cx, int cy, int size) {
        return count(image, cx, cy, size, (r, g, b) -> r < 100 && g < 100 && b < 100);
    }

    /** Píxeles oscuros en una franja horizontal (en fracciones de la altura). */
    static int darkInBand(BufferedImage image, double fromY, double toY) {
        int count = 0;
        for (int y = (int) (fromY * image.getHeight()); y < (int) (toY * image.getHeight()); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) < 100 && ((rgb >> 8) & 0xFF) < 100 && (rgb & 0xFF) < 100) count++;
            }
        }
        return count;
    }

    private interface ColorTest {
        boolean matches(int r, int g, int b);
    }

    private static int count(BufferedImage image, int cx, int cy, int size, ColorTest test) {
        int count = 0;
        for (int y = Math.max(0, cy - size / 2); y < Math.min(image.getHeight(), cy + size / 2); y++) {
            for (int x = Math.max(0, cx - size / 2); x < Math.min(image.getWidth(), cx + size / 2); x++) {
                int rgb = image.getRGB(x, y);
                if (test.matches((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)) count++;
            }
        }
        return count;
    }

    /** JPEG con el cuadrante superior izquierdo rojo y una etiqueta EXIF de orientación. */
    static Path jpegWithOrientation(Path file, int width, int height, int orientation) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpeg);
        byte[] bytes = jpeg.toByteArray();
        byte[] exif = {
                (byte) 0xFF, (byte) 0xE1, 0, 34,
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                0, 0, 0, 0};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes, 0, 2); // SOI
        out.write(exif);
        out.write(bytes, 2, bytes.length - 2);
        Files.write(file, out.toByteArray());
        return file;
    }
}
