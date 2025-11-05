package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PdfOps {

    // MERGE
    public static void merge(List<Path> inputs, Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        PDFMergerUtility mu = new PDFMergerUtility();
        mu.setDestinationFileName(output.toString());
        inputs.stream().filter(Files::exists)
                .map(Path::toFile)
                .forEach(file -> {
                    try {
                        mu.addSource(file);
                    } catch (FileNotFoundException e) {
                        System.err.println("No se pudo añadir el archivo: " + file.getAbsolutePath());
                    }
                });
        mu.mergeDocuments(null);
    }

    // SPLIT por rangos (crea <prefix>_partNNN.pdf)
    public static void splitByRanges(Path input, Path prefix, List<CliUtil.PageRange> ranges) throws IOException {
        Files.createDirectories(prefix.toAbsolutePath().getParent());
        try (PDDocument src = Loader.loadPDF(Files.readAllBytes(input))) {
            final int total = src.getNumberOfPages();
            AtomicInteger part = new AtomicInteger(1);
            ranges.stream()
                    .map(r -> new int[]{Math.max(1, r.start()), Math.min(total, r.end() == Integer.MAX_VALUE ? total : r.end())})
                    .filter(b -> b[0] <= b[1])
                    .forEach(b -> {
                        try (PDDocument out = new PDDocument()) {
                            IntStream.rangeClosed(b[0], b[1])
                                    .map(i -> i - 1)
                                    .mapToObj(src::getPage)
                                    .forEach(out::addPage);
                            out.save(numbered(prefix, part.getAndIncrement()).toString());

                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // COMPRESS: recomprime imágenes con JPEG calidad configurable; opcional reducción simple de DPI y limpieza de metadatos
    public static void compress(Path input, Path output, double jpegQuality, Integer maxDpi, boolean removeMetadata) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(input))) {
            if (removeMetadata) {
                Optional.ofNullable(doc.getDocumentInformation()).ifPresent(info -> info.getCOSObject().clear());
                Optional.ofNullable(doc.getDocumentCatalog()).ifPresent(catalog -> catalog.setMetadata(null));
            }

            streamPages(doc).forEach(page -> {
                PDResources resources = page.getResources();
                if (resources == null) return;

                StreamSupport.stream(resources.getXObjectNames().spliterator(), false)
                        .map(name -> Map.entry(name, safeGetXObject(resources, name)))
                        .filter(e -> e.getValue() instanceof PDImageXObject)
                        .map(e -> Map.entry(e.getKey(), (PDImageXObject) e.getValue()))
                        .forEach(e -> {
                            try {
                                PDImageXObject ximg = e.getValue();
                                BufferedImage bi = ximg.getImage();
                                if (bi == null) return;

                                // Evita recomprimir si hay alpha (opción: aplanar a blanco)
                                if (bi.getColorModel().hasAlpha()) {
                                    // O bien: bi = flattenToColor(bi, Color.WHITE);
                                    return; // Saltamos para evitar artefactos
                                }

                                // Reducción según maxDpi (heurística)
                                BufferedImage processed = Optional.ofNullable(maxDpi)
                                        .filter(dpi -> dpi > 0)
                                        .map(dpi -> resizeHeuristic(bi, Math.min(1.0, dpi / 300.0)))
                                        .orElse(bi);

                                // Crear JPEG XObject correctamente con PDFBox
                                float q = (float) Math.max(0.1, Math.min(1.0, jpegQuality));
                                PDImageXObject newImg = JPEGFactory
                                        .createFromImage(doc, processed, q);

                                resources.put(e.getKey(), newImg);
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        });
            });
            doc.save(output.toString());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // ROTATE
    public static void rotate(Path input, Path output, int degrees, List<CliUtil.PageRange> ranges) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(input))) {
            IntStream.rangeClosed(1, doc.getNumberOfPages())
                    .filter(i -> CliUtil.containsPage(ranges, i))
                    .forEach(i -> {
                        PDPage page = doc.getPage(i - 1);
                        int r = Optional.of(page.getRotation()).orElse(0);
                        page.setRotation((r + degrees) % 360);
                    });
            doc.save(output.toString());
        }
    }


    // WATERMARK (texto centrado y rotado con opacidad)
    public static void watermarkText(Path input, Path output, String text, float opacity) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(input))) {
            streamPages(doc).forEach(page -> {
                var media = page.getMediaBox();
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                    // Opacidad
                    PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant(opacity); // transparencia para el relleno
                    var res = Optional.ofNullable(page.getResources()).orElseGet(PDResources::new);
                    page.setResources(res);
                    COSName gsName = COSName.getPDFName("GS_WM");
                    res.put(gsName, gs);
                    cs.setGraphicsStateParameters(gs);

                    // Fuente y estilo
                    PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                    float fontSize = 64f;
                    cs.setFont(font, fontSize);
                    cs.setNonStrokingColor(new Color(200, 0, 0));

                    // Medidas del texto
                    float textWidth = (font.getStringWidth(text) / 1000f) * fontSize;
                    // Altura preferible: cap-height; fallback a bbox si no está
                    float textHeight = Optional.ofNullable(font.getFontDescriptor())
                            .map(fd -> fd.getCapHeight() / 1000f * fontSize)
                            .orElseGet(() -> {
                                try {
                                    return (font.getBoundingBox().getHeight() / 1000f) * fontSize;
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });

                    // Centro de la página
                    float cx = media.getWidth() / 2f;
                    float cy = media.getHeight() / 2f;

                    // Rotación (45º por defecto; hazlo parámetro si quieres)
                    double angle = Math.toRadians(45);

                    // Coloca el origen del texto en el centro, rota ahí, y compensa medio ancho/alto
                    cs.beginText();
                    cs.setTextMatrix(Matrix.getRotateInstance(angle, cx, cy));
                    cs.newLineAtOffset(-textWidth / 2f, -textHeight / 2f);
                    cs.showText(text);
                    cs.endText();


                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            doc.save(output.toString());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // EXTRAER TEXTO
    public static void extractText(Path input, Path outTxt) throws IOException {
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(input))) {
            String text = new PDFTextStripper().getText(doc);
            Optional.ofNullable(outTxt)
                    .ifPresentOrElse(p -> {
                        try {
                            Files.createDirectories(p.toAbsolutePath().getParent());
                            Files.writeString(p, text);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }, () -> System.out.println(text));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // INFO
    public static void printInfo(Path input) throws IOException {
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(input))) {
            var info = doc.getDocumentInformation();
            System.out.println("Páginas: " + doc.getNumberOfPages());
            Optional.ofNullable(info).ifPresent(i -> Stream.of(
                            Map.entry("Título", i.getTitle()),
                            Map.entry("Autor", i.getAuthor()),
                            Map.entry("Asunto", i.getSubject()),
                            Map.entry("Palabras clave", i.getKeywords()),
                            Map.entry("Productor", i.getProducer()),
                            Map.entry("Creador", i.getCreator())
                    ).filter(e -> e.getValue() != null && !e.getValue().isBlank())
                    .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue())));
        }
    }

    // ENCRYPT
    public static void encrypt(Path input, Path output, String ownerPwd, String userPwd, Set<String> perms) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(input))) {
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(perms.contains("print"));
            ap.setCanExtractContent(perms.contains("copy"));
            ap.setCanModify(perms.contains("modify"));
            StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
            spp.setEncryptionKeyLength(128);
            doc.protect(spp);
            doc.save(output.toString());
        }
    }

    // DECRYPT
    public static void decrypt(Path input, Path output, String password) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (PDDocument doc = Loader.loadPDF(Files.readAllBytes(input), password)) {
            doc.setAllSecurityToBeRemoved(true);
            doc.save(output.toString());
        }
    }

    // Helpers funcionales
    private static Stream<PDPage> streamPages(PDDocument doc) {
        return IntStream.range(0, doc.getNumberOfPages()).mapToObj(doc::getPage);
    }

    private static Path numbered(Path prefix, int idx) {
        String base = prefix.getFileName().toString();
        Path parent = Optional.ofNullable(prefix.getParent()).orElse(Path.of("."));
        return parent.resolve(String.format(Locale.ROOT, "%s_part%03d.pdf", base, idx));
    }

    private static Object safeGetXObject(PDResources res, COSName name) {
        try {
            return res.getXObject(name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage resizeHeuristic(BufferedImage src, double scale) {
        // scale en (0,1] => reducir; >=1 => mantener o ampliar (no recomendado)
        if (scale >= 1.0) return src; // no escalar al alza aquí
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
            writer.dispose();
            return bos.toByteArray();
        }
    }

    private static PDImageXObject createJpegXObject(PDDocument doc, byte[] jpegBytes) throws IOException {
        // Crear un PDStream a partir de los datos JPEG
        PDStream stream = new PDStream(doc, new ByteArrayInputStream(jpegBytes));

        // Configurar las propiedades del flujo (indicar que es una imagen con compresión DCT/JPEG)
        stream.getCOSObject().setItem(COSName.SUBTYPE, COSName.IMAGE);
        stream.getCOSObject().setItem(COSName.FILTER, COSName.DCT_DECODE);

        // Crear un recurso (PDResources) asociado para gestionarlo
        PDResources resources = new PDResources();

        // Crear el PDImageXObject con el stream y los recursos correctos
        return new PDImageXObject(stream, resources);
    }
}

