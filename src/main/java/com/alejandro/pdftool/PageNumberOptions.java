package com.alejandro.pdftool;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Opciones para numerar páginas.
 *
 * @param format      texto con {@code {n}} (número de página) y {@code {total}} (último número), p. ej.
 *                    {@code "Página {n} de {total}"}
 * @param position    posición en la página
 * @param fontSize    tamaño de la fuente en puntos
 * @param margin      distancia al borde en puntos (1 cm ≈ 28,35 pt)
 * @param startNumber número que lleva la primera página numerada
 * @param pages       páginas a numerar (vacío = todas)
 */
public record PageNumberOptions(String format, Position position, float fontSize, float margin, int startNumber,
                                List<CliUtil.PageRange> pages) {

    public static final String DEFAULT_FORMAT = "Página {n} de {total}";
    public static final float DEFAULT_FONT_SIZE = 10f;
    public static final float DEFAULT_MARGIN = 28.35f;

    public PageNumberOptions {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("El formato de la numeración no puede estar vacío.");
        }
        if (position == null) {
            throw new IllegalArgumentException("Indica la posición de la numeración.");
        }
        if (!(fontSize >= 4 && fontSize <= 72)) {
            throw new IllegalArgumentException("El tamaño de la fuente debe estar entre 4 y 72 puntos.");
        }
        if (!(margin >= 0 && margin <= 300)) {
            throw new IllegalArgumentException("El margen debe estar entre 0 y 300 puntos.");
        }
        if (startNumber < 0) {
            throw new IllegalArgumentException("El número inicial no puede ser negativo.");
        }
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public static PageNumberOptions defaults() {
        return new PageNumberOptions(DEFAULT_FORMAT, Position.BOTTOM_CENTER, DEFAULT_FONT_SIZE, DEFAULT_MARGIN, 1,
                List.of());
    }

    public enum Position {
        TOP_LEFT("Arriba a la izquierda"),
        TOP_CENTER("Arriba en el centro"),
        TOP_RIGHT("Arriba a la derecha"),
        BOTTOM_LEFT("Abajo a la izquierda"),
        BOTTOM_CENTER("Abajo en el centro"),
        BOTTOM_RIGHT("Abajo a la derecha");

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
                    .orElseThrow(() -> new IllegalArgumentException("Posición desconocida «" + value + "». Usa: "
                            + Arrays.stream(values()).map(Position::cliName).collect(Collectors.joining(", "))));
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
