package com.alejandro.pdftool;

import java.util.Locale;

/** Formato de imagen al exportar páginas. */
public enum ImageFormat {
    PNG("png", "PNG (sin pérdida)"),
    JPG("jpg", "JPG (más ligero)");

    private final String extension;
    private final String label;

    ImageFormat(String extension, String label) {
        this.extension = extension;
        this.label = label;
    }

    public String extension() {
        return extension;
    }

    public String label() {
        return label;
    }

    public static ImageFormat fromCli(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "png" -> PNG;
            case "jpg", "jpeg" -> JPG;
            default -> throw new IllegalArgumentException("Formato de imagen desconocido «" + value + "». Usa: png, jpg");
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
