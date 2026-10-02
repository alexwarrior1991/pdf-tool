package com.alejandro.pdftool;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Opciones para numerar páginas.
 *
 * @param format      texto con {@code {n}} (número de página) y {@code {total}} (último número), p. ej.
 *                    {@code "Page {n} of {total}"}
 * @param position    posición en la página
 * @param fontSize    tamaño de la fuente en puntos
 * @param margin      distancia al borde en puntos (1 cm ≈ 28,35 pt)
 * @param startNumber número que lleva la primera página numerada
 * @param pages       páginas a numerar (vacío = todas)
 */
public record PageNumberOptions(String format, Position position, float fontSize, float margin, int startNumber,
                                List<CliUtil.PageRange> pages) {

    public static final String DEFAULT_FORMAT = "Page {n} of {total}";
    public static final float DEFAULT_FONT_SIZE = 10f;
    public static final float DEFAULT_MARGIN = 28.35f;

    public PageNumberOptions {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("The page number format cannot be empty.");
        }
        if (position == null) {
            throw new IllegalArgumentException("Choose a position for the page numbers.");
        }
        if (!(fontSize >= 4 && fontSize <= 72)) {
            throw new IllegalArgumentException("The font size must be between 4 and 72 points.");
        }
        if (!(margin >= 0 && margin <= 300)) {
            throw new IllegalArgumentException("The margin must be between 0 and 300 points.");
        }
        if (startNumber < 0) {
            throw new IllegalArgumentException("The starting number cannot be negative.");
        }
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public static PageNumberOptions defaults() {
        return new PageNumberOptions(DEFAULT_FORMAT, Position.BOTTOM_CENTER, DEFAULT_FONT_SIZE, DEFAULT_MARGIN, 1,
                List.of());
    }

    public enum Position {
        TOP_LEFT("Top left"),
        TOP_CENTER("Top center"),
        TOP_RIGHT("Top right"),
        BOTTOM_LEFT("Bottom left"),
        BOTTOM_CENTER("Bottom center"),
        BOTTOM_RIGHT("Bottom right");

        private final String label;

        Position(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean top() {
            return name().startsWith("TOP");
        }

        /** -1 izquierda, 0 centro, 1 derecha. */
        public int horizontal() {
            return name().endsWith("LEFT") ? -1 : name().endsWith("RIGHT") ? 1 : 0;
        }

        /** Nombre para la CLI, p. ej. {@code bottom-center}. */
        public String cliName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Position fromCli(String value) {
            String wanted = value.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                    .filter(p -> p.cliName().equals(wanted))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown position \"" + value + "\". Use: "
                            + Arrays.stream(values()).map(Position::cliName).collect(Collectors.joining(", "))));
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
