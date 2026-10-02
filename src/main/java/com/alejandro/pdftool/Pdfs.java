package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Apertura de PDFs y política común ante documentos cifrados.
 */
final class Pdfs {

    private Pdfs() {
    }

    static PDDocument open(Path input) throws IOException {
        return open(input, null);
    }

    /** Abre el PDF leyendo del disco bajo demanda (no lo carga entero en memoria). */
    static PDDocument open(Path input, String password) throws IOException {
        if (!Files.exists(input)) {
            throw new PdfToolException("File \"" + input + "\" does not exist.");
        }
        if (Files.isDirectory(input)) {
            throw new PdfToolException("\"" + input + "\" is a folder, not a PDF.");
        }
        try {
            return password == null ? Loader.loadPDF(input.toFile()) : Loader.loadPDF(input.toFile(), password);
        } catch (InvalidPasswordException e) {
            throw new PdfToolException(password == null || password.isEmpty()
                    ? "\"" + name(input) + "\" is protected with an open password. "
                    + "Remove the protection first with \"Remove password\"."
                    : "Incorrect password for \"" + name(input) + "\".", e);
        } catch (FileSystemException e) {
            throw e;
        } catch (IOException e) {
            throw new PdfToolException("Could not read \"" + name(input)
                    + "\": it does not look like a valid PDF or it is damaged (" + e.getMessage() + ").", e);
        }
    }

    /**
     * Para operaciones que modifican el documento. Un PDF cifrado solo se acepta si el cifrado no restringe nada
     * (p. ej. contraseña de propietario vacía); en ese caso se le quita el cifrado al guardar. Si su autor puso
     * restricciones, no se saltan: hay que quitarlas antes con la contraseña de propietario.
     */
    static void requireFullAccess(PDDocument doc, Path input) throws PdfToolException {
        if (!doc.isEncrypted()) {
            return;
        }
        if (doc.getCurrentAccessPermission().isOwnerPermission()) {
            doc.setAllSecurityToBeRemoved(true);
            return;
        }
        throw new PdfToolException("\"" + name(input) + "\" has security restrictions set by its author. "
                + "To modify it, first remove the protection with \"Remove password\" using the owner password.");
    }

    /** Para extraer texto o imágenes: respeta el permiso de copia del autor. */
    static void requireCopyPermission(PDDocument doc, Path input) throws PdfToolException {
        if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
            throw new PdfToolException("The author of \"" + name(input) + "\" does not allow copying its content.");
        }
    }

    static void closeAll(Collection<PDDocument> docs) {
        for (PDDocument doc : docs) {
            try {
                doc.close();
            } catch (IOException ignored) {
                // nada más que hacer: el documento ya no se usa
            }
        }
    }

    static String name(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }
}
