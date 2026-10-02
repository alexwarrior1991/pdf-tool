package com.alejandro.pdftool;

/**
 * Recibe el avance de una operación larga: {@code done} de {@code total} unidades
 * (páginas, archivos o imágenes, según la operación).
 */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (done, total) -> { };

    void update(long done, long total);
}
