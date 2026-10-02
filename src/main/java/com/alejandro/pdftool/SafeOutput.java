package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.List;

/**
 * Escritura "todo o nada" de un archivo de salida.
 * <p>
 * Se escribe primero en un temporal oculto de la misma carpeta y {@link #commit()} lo mueve sobre el destino
 * de forma atómica. Si algo falla, el destino queda intacto y {@link #close()} borra el temporal. Como el destino
 * solo se toca al final, la salida puede ser el mismo archivo de entrada, siempre que {@code commit()} se llame
 * después de cerrar los documentos abiertos.
 */
final class SafeOutput implements AutoCloseable {

    @FunctionalInterface
    interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int COMMIT_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 150;

    private final Path target;
    private final Path temp;
    private boolean committed;

    private SafeOutput(Path target, Path temp) {
        this.target = target;
        this.temp = temp;
    }

    static SafeOutput to(Path output) throws IOException {
        Path target = output.toAbsolutePath().normalize();
        if (Files.isDirectory(target)) {
            throw new PdfToolException("La salida «" + target + "» es una carpeta; indica un nombre de archivo.");
        }
        Path dir = target.getParent();
        Files.createDirectories(dir);
        Path temp = dir.resolve("." + target.getFileName() + "." + Long.toHexString(RANDOM.nextLong()) + ".tmp");
        return new SafeOutput(target, temp);
    }

    Path target() {
        return target;
    }

    void save(PDDocument doc) throws IOException {
        write(doc::save);
    }

    void write(Writer writer) throws IOException {
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            // si la aplicación se cierra a mitad de la operación, close() no llega a ejecutarse: que no queden
            // temporales (tras commit() el temporal ya no existe y esto no hace nada)
            temp.toFile().deleteOnExit();
            writer.writeTo(out);
        }
    }

    /**
     * Sustituye el destino por el temporal. Reintenta brevemente por si un antivirus o un servicio de
     * sincronización (OneDrive...) tiene el archivo bloqueado un instante.
     */
    void commit() throws IOException {
        FileSystemException last = null;
        for (int attempt = 1; attempt <= COMMIT_ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                committed = true;
                return;
            } catch (FileSystemException e) {
                last = e;
                if (attempt < COMMIT_ATTEMPTS && !pause()) {
                    break;
                }
            }
        }
        throw last;
    }

    static void commitAll(List<SafeOutput> outputs) throws IOException {
        for (SafeOutput output : outputs) {
            output.commit();
        }
    }

    static void closeAll(List<SafeOutput> outputs) throws IOException {
        IOException first = null;
        for (SafeOutput output : outputs) {
            try {
                output.close();
            } catch (IOException e) {
                first = first == null ? e : first;
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public void close() throws IOException {
        if (!committed) {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean pause() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
