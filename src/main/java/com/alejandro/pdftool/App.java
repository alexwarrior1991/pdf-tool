package com.alejandro.pdftool;

import com.alejandro.pdftool.gui.GuiLauncher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class App {

    private static final double MM_TO_PT = 72.0 / 25.4;

    public static void main(String[] args) {
        // Sin argumentos (p. ej. doble clic en el JAR) o con «gui» se abre la interfaz gráfica
        if (args.length == 0 || args[0].equals("gui")) {
            if (GuiLauncher.launch(args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length))) {
                return;
            }
            if (args.length > 0) {
                System.exit(1);
            }
            System.out.println("(No hay entorno gráfico disponible: se muestra la ayuda de la línea de comandos)");
        }
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** Ejecuta un comando y devuelve el código de salida: 0 bien, 1 error, 2 uso incorrecto. */
    static int run(String[] args) {
        if (args.length == 0 || Set.of("-h", "--help", "help").contains(args[0])) {
            printHelp();
            return 0;
        }
        String cmd = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (cmd) {
                case "merge" -> runMerge(rest);
                case "split" -> runSplit(rest);
                case "compress" -> runCompress(rest);
                case "rotate" -> runRotate(rest);
                case "watermark" -> runWatermark(rest);
                case "text" -> runText(rest);
                case "info" -> runInfo(rest);
                case "encrypt" -> runEncrypt(rest);
                case "decrypt" -> runDecrypt(rest);
                case "extract" -> runExtract(rest);
                case "delete" -> runDelete(rest);
                case "pagenum" -> runPageNumbers(rest);
                case "metadata" -> runMetadata(rest);
                case "images2pdf" -> runImagesToPdf(rest);
                case "pdf2images" -> runPdfToImages(rest);
                default -> {
                    System.err.println("Comando no reconocido: " + cmd);
                    printHelp();
                    return 2;
                }
            }
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            debug(e);
            return 2;
        } catch (Exception e) {
            System.err.println("Error: " + ErrorMessages.describe(e));
            debug(e);
            return 1;
        }
    }

    private static void debug(Exception e) {
        if (Boolean.getBoolean("pdftool.debug")) {
            e.printStackTrace();
        }
    }

    static void runMerge(String[] args) throws IOException {
        var list = List.of(args);
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Uso: merge -o <salida.pdf> <in1.pdf> <carpeta> [...]"));
        var inputs = PdfOps.expandPdfInputs(paths(CliUtil.positionals(list, Set.of("-o"), Set.of())), out);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron archivos PDF para fusionar.");
        }
        int pages = PdfOps.merge(inputs, out);
        System.out.println("Se han fusionado " + inputs.size() + " archivos (" + pages + " páginas) en: " + out);
    }

    static void runSplit(String[] args) throws IOException {
        if (args.length < 3)
            throw new IllegalArgumentException("Uso: split <in.pdf> -ranges \"1-3,7,10-*\" -o <prefijo>  |  split <in.pdf> -every <N> -o <prefijo>");
        var list = List.of(args);
        var in = Path.of(list.getFirst());
        var prefix = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <prefijo>"));
        var every = CliUtil.optValue(list, "-every");
        List<Path> parts;
        if (every != null) {
            parts = PdfOps.splitEvery(in, prefix, CliUtil.parseInt(every, "-every", 1, Integer.MAX_VALUE),
                    ProgressListener.NONE);
        } else {
            var ranges = Optional.ofNullable(CliUtil.optValue(list, "-ranges")).map(CliUtil::parseRanges)
                    .orElseThrow(() -> new IllegalArgumentException("Debe indicar -ranges o -every"));
            parts = PdfOps.splitByRanges(in, prefix, ranges);
        }
        System.out.println("Se han creado " + parts.size() + " archivos:");
        parts.forEach(p -> System.out.println("  " + p));
    }

    static void runCompress(String[] args) throws IOException {
        if (args.length < 3)
            throw new IllegalArgumentException("Uso: compress <in.pdf> -o <out.pdf> [-q 0.6] [--remove-metadata] [--max-dpi 150]");
        var list = List.of(args);
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var quality = Optional.ofNullable(CliUtil.optValue(list, "-q"))
                .map(q -> CliUtil.parseDouble(q, "-q", 0.1, 1.0)).orElse(PdfOps.DEFAULT_JPEG_QUALITY);
        var maxDpi = Optional.ofNullable(CliUtil.optValue(list, "--max-dpi"))
                .map(d -> CliUtil.parseInt(d, "--max-dpi", 36, 2400)).orElse(null);
        var removeMeta = list.contains("--remove-metadata");
        CompressResult result = PdfOps.compress(in, out, quality, maxDpi, removeMeta);
        System.out.println("Comprimido: " + Formats.compression(result) + " · " + result.imagesRecompressed()
                + " de " + result.imagesFound() + " imágenes recomprimidas → " + out);
    }

    static void runRotate(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 4)
            throw new IllegalArgumentException("Uso: rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages \"1-3,5\"]");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var deg = Optional.ofNullable(CliUtil.optValue(list, "-deg"))
                .map(d -> CliUtil.parseInt(d, "-deg", -360, 360))
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -deg"));
        var pages = Optional.ofNullable(CliUtil.optValue(list, "-pages")).map(CliUtil::parseRanges)
                .orElse(List.of(CliUtil.PageRange.ALL));
        int rotated = PdfOps.rotate(in, out, deg, pages);
        System.out.println("Se han girado " + rotated + " páginas → " + out);
    }

    static void runWatermark(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 4)
            throw new IllegalArgumentException("Uso: watermark <in.pdf> -o <out.pdf> -text \"CONFIDENTIAL\" [-opacity 0.2] [-color #C80000]");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var text = Optional.ofNullable(CliUtil.optValue(list, "-text"))
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -text"));
        var opacity = Optional.ofNullable(CliUtil.optValue(list, "-opacity"))
                .map(o -> (float) CliUtil.parseDouble(o, "-opacity", 0.01, 1.0)).orElse(PdfOps.DEFAULT_WATERMARK_OPACITY);
        var color = Optional.ofNullable(CliUtil.optValue(list, "-color"))
                .map(c -> CliUtil.parseColor(c, "-color")).orElse(PdfOps.DEFAULT_WATERMARK_COLOR);
        PdfOps.watermarkText(in, out, text, opacity, color, ProgressListener.NONE);
        System.out.println("Marca de agua añadida → " + out);
    }

    static void runText(String[] args) throws IOException {
        var list = List.of(args);
        if (list.isEmpty()) throw new IllegalArgumentException("Uso: text <in.pdf> [-o <out.txt>]");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of).orElse(null);
        PdfOps.extractText(in, out);
        if (out != null) {
            System.out.println("Texto guardado en: " + out);
        }
    }

    static void runInfo(String[] args) throws IOException {
        var list = List.of(args);
        if (list.isEmpty()) throw new IllegalArgumentException("Uso: info <in.pdf>");
        PdfOps.printInfo(Path.of(list.getFirst()));
    }

    static void runEncrypt(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 5)
            throw new IllegalArgumentException("Uso: encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>] [-perm print,copy]");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var owner = Optional.ofNullable(CliUtil.optValue(list, "-ownerPwd"))
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -ownerPwd"));
        var user = CliUtil.optValue(list, "-userPwd");
        var perms = Optional.ofNullable(CliUtil.optValue(list, "-perm"))
                .map(PdfPermission::parseCli)
                .orElse(Set.of());
        PdfOps.encrypt(in, out, owner, user, perms);
        System.out.println("PDF protegido con AES-256 → " + out + "  (permisos: "
                + (perms.isEmpty() ? "ninguno" : perms.stream().map(PdfPermission::cliName).collect(Collectors.joining(", ")))
                + ")");
    }

    static void runDecrypt(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 3) throw new IllegalArgumentException("Uso: decrypt <in.pdf> -o <out.pdf> -pwd <password>");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var pwd = Optional.ofNullable(CliUtil.optValue(list, "-pwd"))
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -pwd"));
        PdfOps.decrypt(in, out, pwd);
        System.out.println("Protección eliminada → " + out);
    }

    static void runExtract(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 5)
            throw new IllegalArgumentException("Uso: extract <in.pdf> -o <out.pdf> -pages \"5,1-3\"");
        var in = Path.of(list.getFirst());
        var out = requiredPath(list, "-o", "Debe indicar -o <out.pdf>");
        var pages = CliUtil.parseRanges(required(list, "-pages", "Debe indicar -pages"));
        int count = PdfOps.extractPages(in, out, pages);
        System.out.println("Se han extraído " + count + " páginas → " + out);
    }

    static void runDelete(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 5)
            throw new IllegalArgumentException("Uso: delete <in.pdf> -o <out.pdf> -pages \"2,5-7\"");
        var in = Path.of(list.getFirst());
        var out = requiredPath(list, "-o", "Debe indicar -o <out.pdf>");
        var pages = CliUtil.parseRanges(required(list, "-pages", "Debe indicar -pages"));
        int remaining = PdfOps.deletePages(in, out, pages);
        System.out.println("Páginas eliminadas; quedan " + remaining + " → " + out);
    }

    static void runPageNumbers(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 3)
            throw new IllegalArgumentException("Uso: pagenum <in.pdf> -o <out.pdf> [-format \"Página {n} de {total}\"] "
                    + "[-pos bottom-center] [-size 10] [-margin 10] [-start 1] [-pages \"2-*\"]");
        var in = Path.of(list.getFirst());
        var out = requiredPath(list, "-o", "Debe indicar -o <out.pdf>");
        var defaults = PageNumberOptions.defaults();
        var options = new PageNumberOptions(
                Optional.ofNullable(CliUtil.optValue(list, "-format")).orElse(defaults.format()),
                Optional.ofNullable(CliUtil.optValue(list, "-pos")).map(PageNumberOptions.Position::fromCli)
                        .orElse(defaults.position()),
                Optional.ofNullable(CliUtil.optValue(list, "-size"))
                        .map(s -> (float) CliUtil.parseDouble(s, "-size", 4, 72)).orElse(defaults.fontSize()),
                Optional.ofNullable(CliUtil.optValue(list, "-margin"))
                        .map(m -> (float) (CliUtil.parseDouble(m, "-margin (mm)", 0, 100) * MM_TO_PT))
                        .orElse(defaults.margin()),
                Optional.ofNullable(CliUtil.optValue(list, "-start"))
                        .map(s -> CliUtil.parseInt(s, "-start", 0, 1_000_000)).orElse(defaults.startNumber()),
                Optional.ofNullable(CliUtil.optValue(list, "-pages")).map(CliUtil::parseRanges).orElse(List.of()));
        int numbered = PdfOps.addPageNumbers(in, out, options, ProgressListener.NONE);
        System.out.println("Se han numerado " + numbered + " páginas → " + out);
    }

    static void runMetadata(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 3)
            throw new IllegalArgumentException("Uso: metadata <in.pdf> -o <out.pdf> [-title …] [-author …] [-subject …] [-keywords …]");
        var in = Path.of(list.getFirst());
        var out = requiredPath(list, "-o", "Debe indicar -o <out.pdf>");
        // Los campos que no se indican se conservan; un valor vacío ("") los borra
        DocumentMetadata current = PdfOps.info(in).metadata();
        var metadata = new DocumentMetadata(
                Optional.ofNullable(CliUtil.optValue(list, "-title")).orElse(current.title()),
                Optional.ofNullable(CliUtil.optValue(list, "-author")).orElse(current.author()),
                Optional.ofNullable(CliUtil.optValue(list, "-subject")).orElse(current.subject()),
                Optional.ofNullable(CliUtil.optValue(list, "-keywords")).orElse(current.keywords()));
        boolean complete = PdfOps.updateMetadata(in, out, metadata);
        System.out.println("Metadatos actualizados → " + out);
        if (!complete) {
            System.out.println("Aviso: los metadatos XMP del PDF no se podían leer y se han dejado como estaban.");
        }
    }

    static void runImagesToPdf(String[] args) throws IOException {
        var list = List.of(args);
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Uso: images2pdf -o <salida.pdf> <imagen1> <carpeta> [...] [-size a4|letter|image] [-margin 10]"));
        var size = Optional.ofNullable(CliUtil.optValue(list, "-size")).map(ImagePageSize::fromCli).orElse(ImagePageSize.A4);
        var margin = Optional.ofNullable(CliUtil.optValue(list, "-margin"))
                .map(m -> (float) (CliUtil.parseDouble(m, "-margin (mm)", 0, 100) * MM_TO_PT)).orElse(0f);
        var images = PdfOps.expandImageInputs(
                paths(CliUtil.positionals(list, Set.of("-o", "-size", "-margin"), Set.of())), out);
        if (images.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron imágenes (JPG, PNG, GIF, BMP o TIFF).");
        }
        int pages = PdfOps.imagesToPdf(images, out, size, margin, ProgressListener.NONE);
        System.out.println("Se ha creado un PDF de " + pages + " páginas a partir de " + images.size()
                + " imágenes: " + out);
    }

    static void runPdfToImages(String[] args) throws IOException {
        var list = List.of(args);
        if (list.size() < 3)
            throw new IllegalArgumentException("Uso: pdf2images <in.pdf> -o <carpeta> [-format png|jpg] [-dpi 150] [-pages \"1-3\"] [-name <base>]");
        var in = Path.of(list.getFirst());
        var outDir = requiredPath(list, "-o", "Debe indicar -o <carpeta>");
        var format = Optional.ofNullable(CliUtil.optValue(list, "-format")).map(ImageFormat::fromCli).orElse(ImageFormat.PNG);
        var dpi = Optional.ofNullable(CliUtil.optValue(list, "-dpi")).map(d -> CliUtil.parseInt(d, "-dpi", 36, 600)).orElse(150);
        var pages = Optional.ofNullable(CliUtil.optValue(list, "-pages")).map(CliUtil::parseRanges).orElse(List.of());
        var name = Optional.ofNullable(CliUtil.optValue(list, "-name")).orElse(InputFiles.baseName(in));
        List<Path> files = PdfOps.pdfToImages(in, outDir, name, format, dpi, pages, ProgressListener.NONE);
        System.out.println("Se han creado " + files.size() + " imágenes en: " + outDir);
    }

    private static String required(List<String> list, String flag, String message) {
        return Optional.ofNullable(CliUtil.optValue(list, flag)).orElseThrow(() -> new IllegalArgumentException(message));
    }

    private static Path requiredPath(List<String> list, String flag, String message) {
        return Path.of(required(list, flag, message));
    }

    private static List<Path> paths(List<String> values) {
        return values.stream().map(Path::of).toList();
    }

    static void printHelp() {
        System.out.println("""
                PDF Tool - comandos:
                  (sin argumentos)  abre la interfaz gráfica; también: gui
                  merge -o <out.pdf> <in1.pdf> <carpeta> [...]  (Acepta archivos y/o carpetas)
                  split <in.pdf> -ranges "1-3,7,10-*" -o <prefix>   |   split <in.pdf> -every <N> -o <prefix>
                  extract <in.pdf> -o <out.pdf> -pages "5,1-3"      (en el orden indicado)
                  delete <in.pdf> -o <out.pdf> -pages "2,5-7"
                  compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]
                  rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]
                  watermark <in.pdf> -o <out.pdf> -text "CONFIDENTIAL" [-opacity 0.2] [-color #C80000]
                  pagenum <in.pdf> -o <out.pdf> [-format "Página {n} de {total}"] [-pos bottom-center]
                          [-size 10] [-margin 10] [-start 1] [-pages "2-*"]
                  images2pdf -o <out.pdf> <img1> <carpeta> [...] [-size a4|letter|image] [-margin 10]
                  pdf2images <in.pdf> -o <carpeta> [-format png|jpg] [-dpi 150] [-pages "1-3"] [-name <base>]
                  text <in.pdf> [-o <out.txt>]
                  info <in.pdf>
                  metadata <in.pdf> -o <out.pdf> [-title …] [-author …] [-subject …] [-keywords …]
                  encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>]
                          [-perm print,copy,modify,annotate,fill,assemble]
                  decrypt <in.pdf> -o <out.pdf> -pwd <contraseña de propietario>

                Márgenes en milímetros. Añade -Dpdftool.debug=true a java para ver los detalles técnicos de un error.
                """);
    }
}
