package com.alejandro.pdftool;

import java.util.Locale;

/** Formatos legibles (en inglés) para mostrar al usuario. */
public final class Formats {

    public static final Locale LOCALE = Locale.ENGLISH;
    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    private Formats() {
    }

    /** Tamaño de archivo, p. ej. {@code "4.1 MB"}. */
    public static String bytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < UNITS.length - 1);
        return String.format(LOCALE, value >= 100 ? "%.0f %s" : "%.1f %s", value, UNITS[unit]);
    }

    /** Porcentaje con signo, p. ej. {@code "−67%"} o {@code "+3%"}. */
    public static String percent(double value) {
        long rounded = Math.round(value);
        String sign = rounded < 0 ? "−" : rounded > 0 ? "+" : "";
        return sign + Math.abs(rounded) + "%";
    }

    /** Resumen de una compresión, p. ej. {@code "12.3 MB → 4.1 MB (−67%)"}. */
    public static String compression(CompressResult result) {
        return bytes(result.bytesBefore()) + " → " + bytes(result.bytesAfter())
                + " (" + percent(result.changePercent()) + ")";
    }

    /** Medida en milímetros a partir de puntos, p. ej. {@code "210 × 297 mm"}. */
    public static String pageSize(float widthPt, float heightPt) {
        long w = Math.round(widthPt / 72.0 * 25.4);
        long h = Math.round(heightPt / 72.0 * 25.4);
        String name = paperName(w, h);
        return w + " × " + h + " mm" + (name == null ? "" : " (" + name + ")");
    }

    private static String paperName(long w, long h) {
        long shortSide = Math.min(w, h);
        long longSide = Math.max(w, h);
        String orientation = w > h ? " landscape" : "";
        if (near(shortSide, 210) && near(longSide, 297)) return "A4" + orientation;
        if (near(shortSide, 148) && near(longSide, 210)) return "A5" + orientation;
        if (near(shortSide, 297) && near(longSide, 420)) return "A3" + orientation;
        if (near(shortSide, 216) && near(longSide, 279)) return "Letter" + orientation;
        if (near(shortSide, 216) && near(longSide, 356)) return "Legal" + orientation;
        return null;
    }

    private static boolean near(long value, long expected) {
        return Math.abs(value - expected) <= 2;
    }
}
