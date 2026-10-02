package com.alejandro.pdftool;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Operaciones sobre PDFs. Es la fachada que usan tanto la línea de comandos como la interfaz gráfica.
 * <p>
 * Todas las operaciones que generan archivos escriben primero en un temporal y solo sustituyen el destino al
 * terminar bien ({@link SafeOutput}); las que modifican un PDF respetan sus restricciones de seguridad
 * ({@link Pdfs#requireFullAccess}).
 */
public class PdfOps {

    public static final Color DEFAULT_WATERMARK_COLOR = new Color(200, 0, 0);
    public static final float DEFAULT_WATERMARK_OPACITY = 0.2f;
    public static final double DEFAULT_JPEG_QUALITY = 0.7;

    // ENTRADAS
    public static List<Path> expandPdfInputs(List<Path> inputs, Path exclude) throws IOException {
        return InputFiles.expand(inputs, InputFiles.PDF_EXTENSIONS, exclude);
    }

    public static List<Path> expandImageInputs(List<Path> inputs, Path exclude) throws IOException {
        return InputFiles.expand(inputs, InputFiles.IMAGE_EXTENSIONS, exclude);
    }

    // MERGE

    /** @return número de páginas del PDF resultante */
    public static int merge(List<Path> inputs, Path output) throws IOException {
        return merge(inputs, output, ProgressListener.NONE);
    }

    public static int merge(List<Path> inputs, Path output, ProgressListener progress) throws IOException {
        if (inputs.isEmpty()) {
            throw new PdfToolException("There are no PDF files to merge.");
        }
        for (Path input : inputs) {
            if (!Files.isRegularFile(input)) {
                throw new PdfToolException("File \"" + input + "\" does not exist.");
            }
        }
        try (SafeOutput out = SafeOutput.to(output)) {
            int pages;
            List<PDDocument> sources = new ArrayList<>();
            try (PDDocument merged = new PDDocument()) {
                PDFMergerUtility merger = new PDFMergerUtility();
                for (int i = 0; i < inputs.size(); i++) {
                    Path input = inputs.get(i);
                    PDDocument source = Pdfs.open(input);
                    sources.add(source);
                    Pdfs.requireFullAccess(source, input);
                    try {
                        merger.appendDocument(merged, source);
                    } catch (IOException | RuntimeException e) {
                        throw new PdfToolException("Could not add \"" + Pdfs.name(input) + "\": "
                                + ErrorMessages.describe(e), e);
                    }
                    progress.update(i + 1, inputs.size());
                }
                pages = merged.getNumberOfPages();
                // los originales deben seguir abiertos hasta guardar: el resultado comparte sus recursos
                out.save(merged);
            } finally {
                Pdfs.closeAll(sources);
            }
            out.commit();
            return pages;
        }
    }

    // SPLIT por rangos (crea <prefix>_partNNN.pdf)
    public static List<Path> splitByRanges(Path input, Path prefix, List<CliUtil.PageRange> ranges) throws IOException {
        return splitByRanges(input, prefix, ranges, ProgressListener.NONE);
    }

    public static List<Path> splitByRanges(Path input, Path prefix, List<CliUtil.PageRange> ranges,
                                           ProgressListener progress) throws IOException {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Specify at least one page range.");
        }
        return split(input, prefix, total -> {
            List<int[]> bounds = new ArrayList<>();
            for (CliUtil.PageRange r : ranges) {
                int end = Math.min(r.end(), total);
                if (r.start() <= end) {
                    bounds.add(new int[]{r.start(), end});
                }
            }
            return bounds;
        }, progress);
    }

    /** Divide en partes de {@code pagesPerPart} páginas (la última puede tener menos). */
    public static List<Path> splitEvery(Path input, Path prefix, int pagesPerPart, ProgressListener progress)
            throws IOException {
        if (pagesPerPart < 1) {
            throw new IllegalArgumentException("Each part must have at least one page.");
        }
        return split(input, prefix, total -> {
            List<int[]> bounds = new ArrayList<>();
            for (int start = 1; start <= total; start += pagesPerPart) {
                bounds.add(new int[]{start, Math.min(total, start + pagesPerPart - 1)});
            }
            return bounds;
        }, progress);
    }

    private interface PartPlanner {
        List<int[]> plan(int totalPages);
    }

    private static List<Path> split(Path input, Path prefix, PartPlanner planner, ProgressListener progress)
            throws IOException {
        Path original = input.toAbsolutePath().normalize();
        List<SafeOutput> outputs = new ArrayList<>();
        try {
            try (PDDocument source = Pdfs.open(input)) {
                Pdfs.requireFullAccess(source, input);
                int total = source.getNumberOfPages();
                List<int[]> parts = planner.plan(total);
                if (parts.isEmpty()) {
                    throw new PdfToolException("None of the specified ranges exist in the document (it has "
                            + total + " pages).");
                }
                for (int i = 0; i < parts.size(); i++) {
                    int[] part = parts.get(i);
                    Path target = partPath(prefix, i + 1);
                    if (target.toAbsolutePath().normalize().equals(original)) {
                        throw new PdfToolException("Part \"" + target + "\" would overwrite the original PDF; use a different prefix.");
                    }
                    SafeOutput out = SafeOutput.to(target);
                    outputs.add(out);
                    Splitter splitter = new Splitter();
                    splitter.setStartPage(part[0]);
                    splitter.setEndPage(part[1]);
                    splitter.setSplitAtPage(part[1] - part[0] + 1);
                    List<PDDocument> docs = splitter.split(source);
                    try {
                        out.save(docs.get(0));
                    } finally {
                        Pdfs.closeAll(docs);
                    }
                    progress.update(i + 1, parts.size());
                }
            }
            SafeOutput.commitAll(outputs);
            return outputs.stream().map(SafeOutput::target).toList();
        } finally {
            SafeOutput.closeAll(outputs);
        }
    }

    // EXTRAER / ELIMINAR PÁGINAS

    /**
     * Crea un PDF solo con las páginas indicadas, en el orden en que se escriben (p. ej. {@code "5,1-3"}).
     *
     * @return número de páginas del resultado
     */
    public static int extractPages(Path input, Path output, List<CliUtil.PageRange> ranges) throws IOException {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Specify which pages to extract.");
        }
        return selectPages(input, output, total -> {
            List<Integer> pages = CliUtil.resolvePages(ranges, total);
            if (pages.isEmpty()) {
                throw new PdfToolException("None of the specified pages exist (the document has " + total + " pages).");
            }
            return pages;
        });
    }

    /** @return número de páginas que quedan */
    public static int deletePages(Path input, Path output, List<CliUtil.PageRange> ranges) throws IOException {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Specify which pages to delete.");
        }
        return selectPages(input, output, total -> {
            Set<Integer> remove = new HashSet<>(CliUtil.resolvePages(ranges, total));
            if (remove.isEmpty()) {
                throw new PdfToolException("None of the specified pages exist (the document has " + total + " pages).");
            }
            List<Integer> keep = new ArrayList<>();
            for (int page = 1; page <= total; page++) {
                if (!remove.contains(page)) keep.add(page);
            }
            if (keep.isEmpty()) {
                throw new PdfToolException("You cannot delete every page of the document.");
            }
            return keep;
        });
    }

    private interface PageChooser {
        List<Integer> choose(int totalPages) throws IOException;
    }

    private static int selectPages(Path input, Path output, PageChooser chooser) throws IOException {
        try (SafeOutput out = SafeOutput.to(output)) {
            int pages;
            try (PDDocument source = Pdfs.open(input)) {
                Pdfs.requireFullAccess(source, input);
                List<Integer> selection = chooser.choose(source.getNumberOfPages());
                try (PDDocument selected = PageSelector.select(source, selection)) {
                    pages = selected.getNumberOfPages();
                    out.save(selected);
                }
            }
            out.commit();
            return pages;
        }
    }

    // COMPRESS: recomprime imágenes con JPEG calidad configurable; opcional reducción de DPI y limpieza de metadatos
    public static CompressResult compress(Path input, Path output, double jpegQuality, Integer maxDpi,
                                          boolean removeMetadata) throws IOException {
        return compress(input, output, jpegQuality, maxDpi, removeMetadata, ProgressListener.NONE);
    }

    public static CompressResult compress(Path input, Path output, double jpegQuality, Integer maxDpi,
                                          boolean removeMetadata, ProgressListener progress) throws IOException {
        if (!(jpegQuality >= 0.1 && jpegQuality <= 1.0)) {
            throw new IllegalArgumentException("JPEG quality must be between 0.1 and 1.");
        }
        if (maxDpi != null && maxDpi < 36) {
            throw new IllegalArgumentException("Maximum DPI must be at least 36.");
        }
        long before = Files.size(input);
        ImageRecompressor.Result images;
        try (SafeOutput out = SafeOutput.to(output)) {
            try (PDDocument doc = Pdfs.open(input)) {
                Pdfs.requireFullAccess(doc, input);
                if (removeMetadata) {
                    doc.setDocumentInformation(new PDDocumentInformation());
                    doc.getDocumentCatalog().setMetadata(null);
                }
                images = ImageRecompressor.recompress(doc, jpegQuality, maxDpi, progress);
                out.save(doc);
            }
            out.commit();
        }
        return new CompressResult(before, Files.size(output), images.imagesFound(), images.imagesRecompressed());
    }

    // ROTATE

    /** @return número de páginas giradas */
    public static int rotate(Path input, Path output, int degrees, List<CliUtil.PageRange> ranges) throws IOException {
        if (degrees % 90 != 0) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees (90, 180, 270…): " + degrees);
        }
        List<CliUtil.PageRange> pages = ranges.isEmpty() ? List.of(CliUtil.PageRange.ALL) : ranges;
        try (SafeOutput out = SafeOutput.to(output)) {
            int rotated = 0;
            try (PDDocument doc = Pdfs.open(input)) {
                Pdfs.requireFullAccess(doc, input);
                int number = 0;
                for (PDPage page : doc.getPages()) {
                    number++;
                    if (CliUtil.containsPage(pages, number)) {
                        page.setRotation(Math.floorMod(page.getRotation() + degrees, 360));
                        rotated++;
                    }
                }
                if (rotated == 0) {
                    throw new PdfToolException("None of the specified pages exist (the document has "
                            + number + " pages).");
                }
                out.save(doc);
            }
            out.commit();
            return rotated;
        }
    }

    // WATERMARK (texto centrado y girado 45° con opacidad)
    public static void watermarkText(Path input, Path output, String text, float opacity) throws IOException {
        watermarkText(input, output, text, opacity, DEFAULT_WATERMARK_COLOR, ProgressListener.NONE);
    }

    public static void watermarkText(Path input, Path output, String text, float opacity, Color color,
                                     ProgressListener progress) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("The watermark text cannot be empty.");
        }
        if (!(opacity > 0 && opacity <= 1)) {
            throw new IllegalArgumentException("Opacity must be greater than 0 and at most 1.");
        }
        try (SafeOutput out = SafeOutput.to(output)) {
            try (PDDocument doc = Pdfs.open(input)) {
                Pdfs.requireFullAccess(doc, input);
                PageStamper.watermark(doc, text.strip(), opacity, color == null ? DEFAULT_WATERMARK_COLOR : color, progress);
                out.save(doc);
            }
            out.commit();
        }
    }

    // NUMERAR PÁGINAS

    /** @return número de páginas numeradas */
    public static int addPageNumbers(Path input, Path output, PageNumberOptions options, ProgressListener progress)
            throws IOException {
        try (SafeOutput out = SafeOutput.to(output)) {
            int numbered;
            try (PDDocument doc = Pdfs.open(input)) {
                Pdfs.requireFullAccess(doc, input);
                numbered = PageStamper.pageNumbers(doc, options, progress);
                out.save(doc);
            }
            out.commit();
            return numbered;
        }
    }

    // EXTRAER TEXTO
    public static String readText(Path input, ProgressListener progress) throws IOException {
        try (PDDocument doc = Pdfs.open(input)) {
            Pdfs.requireCopyPermission(doc, input);
            int total = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void endPage(PDPage page) throws IOException {
                    super.endPage(page);
                    progress.update(getCurrentPageNo(), total);
                }
            };
            return stripper.getText(doc);
        }
    }

    public static void extractText(Path input, Path outTxt) throws IOException {
        String text = readText(input, ProgressListener.NONE);
        if (outTxt == null) {
            System.out.println(text);
            return;
        }
        saveText(text, outTxt);
    }

    /** Guarda texto en UTF-8 sin dejar archivos a medias. */
    public static void saveText(String text, Path outTxt) throws IOException {
        try (SafeOutput out = SafeOutput.to(outTxt)) {
            out.write(os -> os.write(text.getBytes(StandardCharsets.UTF_8)));
            out.commit();
        }
    }

    // INFO
    public static PdfInfo info(Path input) throws IOException {
        try (PDDocument doc = Pdfs.open(input)) {
            long size = Files.size(input);
            PDDocumentInformation info = doc.getDocumentInformation();
            float width = 0;
            float height = 0;
            if (doc.getNumberOfPages() > 0) {
                PageGeometry.Visual first = PageGeometry.of(doc.getPage(0));
                width = first.width();
                height = first.height();
            }
            return new PdfInfo(size, doc.getNumberOfPages(),
                    String.format(Locale.ROOT, "%.1f", doc.getVersion()),
                    doc.isEncrypted(),
                    doc.isEncrypted() ? PdfPermission.granted(doc.getCurrentAccessPermission()) : null,
                    width, height,
                    MetadataSupport.read(doc),
                    blankToNull(info.getCreator()), blankToNull(info.getProducer()),
                    toZoned(info.getCreationDate()), toZoned(info.getModificationDate()));
        }
    }

    /** @return número de páginas, o lanza error si el PDF no se puede abrir */
    public static int pageCount(Path input) throws IOException {
        try (PDDocument doc = Pdfs.open(input)) {
            return doc.getNumberOfPages();
        }
    }

    public static void printInfo(Path input) throws IOException {
        PdfInfo info = info(input);
        System.out.println("Pages: " + info.pages());
        DocumentMetadata md = info.metadata();
        Stream.of(
                        Map.entry("Title", Optional.ofNullable(md.title())),
                        Map.entry("Author", Optional.ofNullable(md.author())),
                        Map.entry("Subject", Optional.ofNullable(md.subject())),
                        Map.entry("Keywords", Optional.ofNullable(md.keywords())),
                        Map.entry("Producer", Optional.ofNullable(info.producer())),
                        Map.entry("Creator", Optional.ofNullable(info.creator()))
                ).filter(e -> e.getValue().isPresent())
                .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue().get()));
    }

    // METADATOS

    /**
     * @return {@code false} si el PDF tenía metadatos XMP ilegibles que se han dejado sin tocar (el resto sí se
     * actualiza)
     */
    public static boolean updateMetadata(Path input, Path output, DocumentMetadata metadata) throws IOException {
        try (SafeOutput out = SafeOutput.to(output)) {
            boolean complete;
            try (PDDocument doc = Pdfs.open(input)) {
                Pdfs.requireFullAccess(doc, input);
                complete = MetadataSupport.write(doc, metadata);
                out.save(doc);
            }
            out.commit();
            return complete;
        }
    }

    // ENCRYPT
    public static void encrypt(Path input, Path output, String ownerPwd, String userPwd, Set<PdfPermission> perms)
            throws IOException {
        if (ownerPwd == null || ownerPwd.isEmpty()) {
            throw new IllegalArgumentException("An owner password is required.");
        }
        String user = userPwd == null ? "" : userPwd;
        if (user.equals(ownerPwd)) {
            throw new IllegalArgumentException("The owner password must differ from the open password; "
                    + "otherwise anyone who opens the PDF would have full permissions.");
        }
        try (SafeOutput out = SafeOutput.to(output)) {
            try (PDDocument doc = Pdfs.open(input)) {
                if (doc.isEncrypted()) {
                    throw new PdfToolException("\"" + Pdfs.name(input) + "\" is already protected. "
                            + "Remove the protection first with \"Remove password\".");
                }
                StandardProtectionPolicy policy =
                        new StandardProtectionPolicy(ownerPwd, user, PdfPermission.toAccessPermission(perms));
                policy.setEncryptionKeyLength(256); // AES-256
                doc.protect(policy);
                out.save(doc);
            }
            out.commit();
        }
    }

    // DECRYPT
    public static void decrypt(Path input, Path output, String password) throws IOException {
        try (SafeOutput out = SafeOutput.to(output)) {
            try (PDDocument doc = Pdfs.open(input, password == null ? "" : password)) {
                if (!doc.isEncrypted()) {
                    throw new PdfToolException("\"" + Pdfs.name(input) + "\" is not protected: there is nothing to remove.");
                }
                if (!doc.getCurrentAccessPermission().isOwnerPermission()) {
                    throw new PdfToolException("That password only opens \"" + Pdfs.name(input)
                            + "\". Removing the protection requires the owner password.");
                }
                doc.setAllSecurityToBeRemoved(true);
                out.save(doc);
            }
            out.commit();
        }
    }

    // IMÁGENES → PDF

    /** @return número de páginas creadas (una por imagen; un TIFF de varias páginas aporta todas) */
    public static int imagesToPdf(List<Path> images, Path output, ImagePageSize size, float marginPt,
                                  ProgressListener progress) throws IOException {
        if (images.isEmpty()) {
            throw new PdfToolException("There are no images to convert.");
        }
        if (marginPt < 0) {
            throw new IllegalArgumentException("The margin cannot be negative.");
        }
        int pages = 0;
        try (SafeOutput out = SafeOutput.to(output)) {
            try (PDDocument doc = new PDDocument()) {
                for (int i = 0; i < images.size(); i++) {
                    pages += ImageConversion.addImagePages(doc, images.get(i), size, marginPt);
                    progress.update(i + 1, images.size());
                }
                out.save(doc);
            }
            out.commit();
        }
        return pages;
    }

    // PDF → IMÁGENES

    /** Exporta cada página como {@code <baseName>_NNN.png|jpg} en {@code outputDir}. */
    public static List<Path> pdfToImages(Path input, Path outputDir, String baseName, ImageFormat format, int dpi,
                                         List<CliUtil.PageRange> ranges, ProgressListener progress) throws IOException {
        if (dpi < 36 || dpi > 600) {
            throw new IllegalArgumentException("Resolution must be between 36 and 600 DPI.");
        }
        if (baseName == null || baseName.isBlank() || baseName.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new IllegalArgumentException("Invalid base name for the images: \"" + baseName + "\".");
        }
        List<SafeOutput> outputs = new ArrayList<>();
        try {
            try (PDDocument doc = Pdfs.open(input)) {
                Pdfs.requireCopyPermission(doc, input);
                int total = doc.getNumberOfPages();
                List<Integer> pages = CliUtil.resolvePages(ranges, total);
                if (pages.isEmpty()) {
                    throw new PdfToolException("None of the specified pages exist (the document has " + total + " pages).");
                }
                PDFRenderer renderer = new PDFRenderer(doc);
                for (int i = 0; i < pages.size(); i++) {
                    int page = pages.get(i);
                    BufferedImage image = renderer.renderImageWithDPI(page - 1, dpi, ImageType.RGB);
                    SafeOutput out = SafeOutput.to(pageImagePath(outputDir, baseName, page, total, format));
                    outputs.add(out);
                    out.write(os -> ImageConversion.writeImage(image, format, os));
                    progress.update(i + 1, pages.size());
                }
            }
            SafeOutput.commitAll(outputs);
            return outputs.stream().map(SafeOutput::target).toList();
        } finally {
            SafeOutput.closeAll(outputs);
        }
    }

    // Nombres de salida

    /** Archivo de la parte {@code index} (1-based) al dividir: {@code <prefijo>_partNNN.pdf}. */
    public static Path partPath(Path prefix, int index) {
        String base = prefix.getFileName().toString();
        Path parent = Optional.ofNullable(prefix.getParent()).orElse(Path.of("."));
        return parent.resolve(String.format(Locale.ROOT, "%s_part%03d.pdf", base, index));
    }

    /** Imagen de la página {@code page} al exportar: {@code <base>_NNN.<ext>} (más cifras si hay más de 999 páginas). */
    public static Path pageImagePath(Path outputDir, String baseName, int page, int totalPages, ImageFormat format) {
        String pattern = "%s_%0" + Math.max(3, String.valueOf(totalPages).length()) + "d.%s";
        return outputDir.resolve(String.format(Locale.ROOT, pattern, baseName.strip(), page, format.extension()));
    }

    // Helpers

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ZonedDateTime toZoned(Calendar calendar) {
        if (calendar == null) return null;
        return calendar instanceof GregorianCalendar gregorian
                ? gregorian.toZonedDateTime()
                : calendar.toInstant().atZone(ZoneId.systemDefault());
    }
}
