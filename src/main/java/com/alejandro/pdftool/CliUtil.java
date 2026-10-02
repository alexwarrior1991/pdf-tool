package com.alejandro.pdftool;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CliUtil {

    private static final Pattern RANGE = Pattern.compile("(\\d+)(?:\\s*-\\s*(\\d+|\\*))?");
    private static final String RANGE_HELP = "Use the format 1-3,7,10-* (* means \"to the end\").";

    public static String optValue(List<String> args, String flag) {
        int i = args.indexOf(flag);
        return (i >= 0 && i < args.size() - 1) ? args.get(i + 1) : null;
    }

    /**
     * Argumentos sueltos: todo lo que no es una opción conocida ni el valor de una opción que lleva valor.
     */
    public static List<String> positionals(List<String> args, Set<String> flagsWithValue, Set<String> booleanFlags) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (flagsWithValue.contains(arg)) {
                i++;
            } else if (!booleanFlags.contains(arg)) {
                result.add(arg);
            }
        }
        return result;
    }

    /** Rango de páginas 1-based e inclusivo; {@code end == Integer.MAX_VALUE} significa «hasta el final». */
    public record PageRange(int start, int end) {
        public static final PageRange ALL = new PageRange(1, Integer.MAX_VALUE);

        public boolean contains(int page) {
            return page >= start && page <= end;
        }
    }

    /**
     * Interpreta rangos como {@code "1-3, 7, 10-*"}. Lanza {@link IllegalArgumentException} con un mensaje claro
     * si el formato no es válido; una especificación vacía devuelve una lista vacía.
     */
    public static List<PageRange> parseRanges(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        List<PageRange> ranges = new ArrayList<>();
        for (String raw : spec.split(",")) {
            String part = raw.strip();
            if (part.isEmpty()) continue;
            Matcher m = RANGE.matcher(part);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid page range: \"" + part + "\". " + RANGE_HELP);
            }
            int start = parsePageNumber(m.group(1), part);
            int end = m.group(2) == null ? start
                    : "*".equals(m.group(2)) ? Integer.MAX_VALUE : parsePageNumber(m.group(2), part);
            if (start < 1) {
                throw new IllegalArgumentException("Pages start at 1: \"" + part + "\".");
            }
            if (end < start) {
                throw new IllegalArgumentException("Reversed range: \"" + part + "\" (the start is greater than the end).");
            }
            ranges.add(new PageRange(start, end));
        }
        return List.copyOf(ranges);
    }

    private static int parsePageNumber(String digits, String part) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Page number too large: \"" + part + "\".");
        }
    }

    public static boolean containsPage(List<PageRange> ranges, int pageIndex1Based) {
        return ranges.stream().anyMatch(r -> r.contains(pageIndex1Based));
    }

    /**
     * Páginas concretas (1-based) que indican los rangos en un documento de {@code total} páginas, en el orden
     * escrito y sin repetir. Las páginas que no existen se ignoran. Una lista vacía de rangos equivale a todas.
     */
    public static List<Integer> resolvePages(List<PageRange> ranges, int total) {
        List<PageRange> effective = ranges.isEmpty() ? List.of(PageRange.ALL) : ranges;
        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        for (PageRange r : effective) {
            int end = Math.min(r.end(), total);
            for (int p = r.start(); p <= end; p++) {
                pages.add(p);
            }
        }
        return List.copyOf(pages);
    }

    /** Representación compacta de un conjunto de páginas, p. ej. {@code [1,2,3,7]} → {@code "1-3,7"}. */
    public static String toRangeSpec(Collection<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        Integer start = null;
        Integer prev = null;
        for (int page : new TreeSet<>(pages)) {
            if (start == null) {
                start = page;
            } else if (page != prev + 1) {
                appendRange(sb, start, prev);
                start = page;
            }
            prev = page;
        }
        if (start != null) {
            appendRange(sb, start, prev);
        }
        return sb.toString();
    }

    private static void appendRange(StringBuilder sb, int start, int end) {
        if (!sb.isEmpty()) sb.append(',');
        sb.append(start);
        if (end > start) sb.append('-').append(end);
    }

    public static int parseInt(String value, String what, int min, int max) {
        int number;
        try {
            number = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a whole number: \"" + value + "\".");
        }
        if (number < min || number > max) {
            throw new IllegalArgumentException(what + " must be between " + min + " and " + max + ": \"" + value + "\".");
        }
        return number;
    }

    /** Acepta tanto {@code 0.6} como {@code 0,6}. */
    public static double parseDouble(String value, String what, double min, double max) {
        double number;
        try {
            number = Double.parseDouble(value.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number: \"" + value + "\".");
        }
        if (!(number >= min && number <= max)) {
            throw new IllegalArgumentException(what + " must be between " + format(min) + " and " + format(max)
                    + ": \"" + value + "\".");
        }
        return number;
    }

    /** Color en formato {@code #RRGGBB} (la almohadilla es opcional). */
    public static Color parseColor(String value, String what) {
        String hex = value.strip();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException(what + " must use the format #RRGGBB, e.g. #C80000: \"" + value + "\".");
        }
        return new Color(Integer.parseInt(hex, 16));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%s", value);
    }
}
