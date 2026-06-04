package com.alejandro.pdftool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || Set.of("-h", "--help").contains(args[0])) {
            printHelp();
            return;
        }
        String cmd = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
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
            default -> {
                System.err.println("Comando no reconocido: " + cmd);
                printHelp();
                System.exit(2);
            }
        }
    }

    static void runMerge(String[] args) throws Exception {
        var list = List.of(args);
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Uso: merge -o <salida.pdf> <in1.pdf> <carpeta> [...]"));
        var inputs = IntStream.range(0, list.size())
                .filter(i -> !list.get(i).equals("-o") && (i == 0 || !list.get(i - 1).equals("-o")))
                .mapToObj(list::get)
                .map(Path::of)
                .flatMap(path -> {
                    if (Files.isDirectory(path)) {
                        try (Stream<Path> stream = Files.list(path)) {
                            return stream
                                    .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                                    .sorted()
                                    .toList() // Convertimos a lista para cerrar el stream de archivos y seguir trabajando
                                    .stream();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    } else {
                        return Stream.of(path);
                    }
                })
                .toList();


        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron archivos PDF para fusionar.");
        }

        PdfOps.merge(inputs, out);
        System.out.println("Se han fusionado " + inputs.size() + " archivos en: " + out);
    }

    static void runSplit(String[] args) throws Exception {
        if (args.length < 3)
            throw new IllegalArgumentException("Uso: split <in.pdf> -ranges \"1-3,7,10-*\" -o <prefijo>");
        var list = List.of(args);
        var in = Path.of(list.getFirst());
        var ranges = Optional.ofNullable(CliUtil.optValue(list, "-ranges")).map(CliUtil::parseRanges)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -ranges"));
        var prefix = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <prefijo>"));
        PdfOps.splitByRanges(in, prefix, ranges);
    }

    static void runCompress(String[] args) throws Exception {
        if (args.length < 3)
            throw new IllegalArgumentException("Uso: compress <in.pdf> -o <out.pdf> [-q 0.6] [--remove-metadata] [--max-dpi 150]");
        var list = List.of(args);
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var quality = Optional.ofNullable(CliUtil.optValue(list, "-q")).map(Double::parseDouble).orElse(0.7);
        var maxDpi = Optional.ofNullable(CliUtil.optValue(list, "--max-dpi")).map(Integer::parseInt).orElse(null);
        var removeMeta = list.contains("--remove-metadata");
        PdfOps.compress(in, out, quality, maxDpi, removeMeta);
    }

    static void runRotate(String[] args) throws Exception {
        var list = List.of(args);
        if (list.size() < 4)
            throw new IllegalArgumentException("Uso: rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages \"1-3,5\"]");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var deg = Optional.ofNullable(CliUtil.optValue(list, "-deg")).map(Integer::parseInt)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -deg"));
        var pages = Optional.ofNullable(CliUtil.optValue(list, "-pages")).map(CliUtil::parseRanges)
                .orElse(List.of(new CliUtil.PageRange(1, Integer.MAX_VALUE)));
        PdfOps.rotate(in, out, deg, pages);
    }

    static void runWatermark(String[] args) throws Exception {
        var list = List.of(args);
        if (list.size() < 4)
            throw new IllegalArgumentException("Uso: watermark <in.pdf> -o <out.pdf> -text \"CONFIDENTIAL\" [-opacity 0.2]");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var text = Optional.ofNullable(CliUtil.optValue(list, "-text"))
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -text"));
        var opacity = Optional.ofNullable(CliUtil.optValue(list, "-opacity")).map(Float::parseFloat).orElse(0.2f);
        PdfOps.watermarkText(in, out, text, opacity);
    }

    static void runText(String[] args) throws Exception {
        var list = List.of(args);
        if (list.isEmpty()) throw new IllegalArgumentException("Uso: text <in.pdf> [-o <out.txt>]");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of).orElse(null);
        PdfOps.extractText(in, out);
    }

    static void runInfo(String[] args) throws Exception {
        var list = List.of(args);
        if (list.isEmpty()) throw new IllegalArgumentException("Uso: info <in.pdf>");
        PdfOps.printInfo(Path.of(list.getFirst()));
    }

    static void runEncrypt(String[] args) throws Exception {
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
                .map(s -> Arrays.stream(s.split(",")).collect(Collectors.toSet()))
                .orElse(Set.of());
        PdfOps.encrypt(in, out, owner, user, perms);
    }

    static void runDecrypt(String[] args) throws Exception {
        var list = List.of(args);
        if (list.size() < 3) throw new IllegalArgumentException("Uso: decrypt <in.pdf> -o <out.pdf> -pwd <password>");
        var in = Path.of(list.getFirst());
        var out = Optional.ofNullable(CliUtil.optValue(list, "-o")).map(Path::of)
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -o <out.pdf>"));
        var pwd = Optional.ofNullable(CliUtil.optValue(list, "-pwd"))
                .orElseThrow(() -> new IllegalArgumentException("Debe indicar -pwd"));
        PdfOps.decrypt(in, out, pwd);
    }

    static void printHelp() {
        System.out.println("""
                PDF Tool - comandos:
                  merge -o <out.pdf> <in1.pdf> <carpeta> [...]  (Acepta archivos y/o carpetas)
                  split <in.pdf> -ranges "1-3,7,10-*" -o <prefix>
                  compress <in.pdf> -o <out.pdf> [-q 0.6] [--max-dpi 150] [--remove-metadata]
                  rotate <in.pdf> -o <out.pdf> -deg <90|180|270> [-pages "1-3,5"]
                  watermark <in.pdf> -o <out.pdf> -text "CONFIDENTIAL" [-opacity 0.2]
                  text <in.pdf> [-o <out.txt>]
                  info <in.pdf>
                  encrypt <in.pdf> -o <out.pdf> -ownerPwd <pwd> [-userPwd <pwd>] [-perm print,copy]
                  decrypt <in.pdf> -o <out.pdf> -pwd <password>
                """);
    }
}
