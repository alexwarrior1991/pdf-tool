package com.alejandro.pdftool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Selección de archivos de entrada: expande carpetas y ordena como lo haría una persona. */
public final class InputFiles {

    public static final Set<String> PDF_EXTENSIONS = Set.of("pdf");
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff");

    /** Orden natural sin distinguir mayúsculas: {@code doc2.pdf} va antes que {@code doc10.pdf}. */
    public static final Comparator<Path> NATURAL_ORDER =
            Comparator.comparing((Path p) -> String.valueOf(p.getFileName()), InputFiles::compareNatural);

    private InputFiles() {
    }

    /**
     * Sustituye cada carpeta por los archivos que contiene con alguna de las extensiones indicadas (sin entrar en
     * subcarpetas), en orden natural. Los archivos indicados explícitamente se mantienen tal cual y en su orden.
     *
     * @param exclude archivo que no debe incluirse al expandir carpetas (p. ej. la propia salida), puede ser null
     */
    public static List<Path> expand(List<Path> inputs, Set<String> extensions, Path exclude) throws IOException {
        Path excluded = exclude == null ? null : exclude.toAbsolutePath().normalize();
        List<Path> result = new ArrayList<>();
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                try (Stream<Path> files = Files.list(input)) {
                    files.filter(Files::isRegularFile)
                            .filter(f -> hasExtension(f, extensions))
                            .filter(f -> !f.toAbsolutePath().normalize().equals(excluded))
                            .sorted(NATURAL_ORDER)
                            .forEach(result::add);
                }
            } else {
                result.add(input);
            }
        }
        return result;
    }

    public static boolean hasExtension(Path file, Set<String> extensions) {
        String name = String.valueOf(file.getFileName()).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && extensions.contains(name.substring(dot + 1));
    }

    /** Nombre sin extensión, p. ej. {@code informe.pdf} → {@code informe}. */
    public static String baseName(Path file) {
        String name = String.valueOf(file.getFileName());
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String numberA = stripLeadingZeros(a.substring(startA, i));
                String numberB = stripLeadingZeros(b.substring(startB, j));
                int cmp = numberA.length() != numberB.length()
                        ? Integer.compare(numberA.length(), numberB.length())
                        : numberA.compareTo(numberB);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        int cmp = Integer.compare(a.length() - i, b.length() - j);
        return cmp != 0 ? cmp : a.compareTo(b);
    }

    private static String stripLeadingZeros(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') k++;
        return digits.substring(k);
    }
}
