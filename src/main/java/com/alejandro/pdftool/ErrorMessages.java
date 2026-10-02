package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Traduce excepciones a mensajes comprensibles en español. Lo usan tanto la CLI como la GUI.
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static String describe(Throwable error) {
        Throwable e = unwrap(error);
        if (e instanceof PdfToolException) {
            return e.getMessage();
        }
        if (e instanceof InvalidPasswordException) {
            return "El PDF está protegido con contraseña o la contraseña no es correcta.";
        }
        if (e instanceof NoSuchFileException nsf) {
            return "No se encuentra el archivo «" + nsf.getFile() + "».";
        }
        if (e instanceof FileNotFoundException) {
            return "No se encuentra el archivo: " + e.getMessage();
        }
        if (e instanceof AccessDeniedException ade) {
            String file = ade.getOtherFile() != null ? ade.getOtherFile() : ade.getFile();
            return "No se puede escribir en «" + file + "». ¿Está abierto en otro programa o es de solo lectura?";
        }
        if (e instanceof FileSystemException fse) {
            String file = fse.getOtherFile() != null ? fse.getOtherFile() : fse.getFile();
            String reason = fse.getReason() != null ? ": " + fse.getReason() : "";
            return "No se puede acceder a «" + file + "»" + reason + ". ¿Está abierto en otro programa?";
        }
        if (e instanceof NumberFormatException) {
            return "Número no válido (" + e.getMessage() + ").";
        }
        if (e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        if (e instanceof OutOfMemoryError) {
            return "No hay memoria suficiente para procesar el documento.";
        }
        if (e instanceof IOException) {
            return "No se ha podido procesar el archivo (¿está dañado o no es un PDF?): " + e.getMessage();
        }
        return "Error inesperado: " + e;
    }

    /** Quita los envoltorios habituales para llegar a la causa real. */
    public static Throwable unwrap(Throwable error) {
        Throwable e = error;
        while ((e instanceof UncheckedIOException || e instanceof CompletionException
                || e instanceof ExecutionException || e instanceof InvocationTargetException)
                && e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }
}
