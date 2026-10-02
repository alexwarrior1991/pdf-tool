package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Tamaño de página al convertir imágenes en PDF. */
public enum ImagePageSize {
    A4("A4", PDRectangle.A4),
    LETTER("Carta (EE. UU.)", PDRectangle.LETTER),
    IMAGE("Tamaño de cada imagen", null);

    private final String label;
    private final PDRectangle paper;

    ImagePageSize(String label, PDRectangle paper) {
        this.label = label;
        this.paper = paper;
    }

    public String label() {
        return label;
    }

    /** Papel en vertical, o {@code null} si la página toma el tamaño de la imagen. */
    PDRectangle paper() {
        return paper;
    }

    public static ImagePageSize fromCli(String value) {
        String wanted = value.strip().toUpperCase(Locale.ROOT);
        if (wanted.equals("CARTA")) return LETTER;
        if (wanted.equals("IMAGEN") || wanted.equals("ORIGINAL")) return IMAGE;
        return Arrays.stream(values())
                .filter(s -> s.name().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tamaño de página desconocido «" + value + "». Usa: "
                        + Arrays.stream(values()).map(s -> s.name().toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", "))));
    }

    @Override
    public String toString() {
        return label;
    }
}
