package com.alejandro.pdftool.gui.util;

import com.alejandro.pdftool.InputFiles;

import java.nio.file.Files;
import java.nio.file.Path;

/** Nombres de salida sugeridos a partir del archivo de entrada, sin pisar archivos existentes. */
public final class OutputNames {

    private OutputNames() {
    }

    /** {@code informe.pdf} + {@code "rotado"} → {@code informe_rotado.pdf} (o {@code informe_rotado (2).pdf}). */
    public static Path besides(Path input, String suffix, String extension) {
        Path dir = input.toAbsolutePath().getParent();
        return unique(dir, InputFiles.baseName(input) + "_" + suffix, extension);
    }

    /** Archivo nuevo {@code <nombre>.<ext>} en la carpeta indicada. */
    public static Path unique(Path dir, String name, String extension) {
        Path candidate = dir.resolve(name + "." + extension);
        for (int n = 2; Files.exists(candidate); n++) {
            candidate = dir.resolve(name + " (" + n + ")." + extension);
        }
        return candidate;
    }

    /** Carpeta nueva junto al archivo, p. ej. {@code informe_imagenes}. */
    public static Path folderBesides(Path input, String suffix) {
        Path dir = input.toAbsolutePath().getParent();
        String name = InputFiles.baseName(input) + "_" + suffix;
        Path candidate = dir.resolve(name);
        for (int n = 2; Files.exists(candidate); n++) {
            candidate = dir.resolve(name + " (" + n + ")");
        }
        return candidate;
    }
}
