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
            return "The PDF is password-protected or the password is incorrect.";
        }
        if (e instanceof NoSuchFileException nsf) {
            return "File not found: \"" + nsf.getFile() + "\".";
        }
        if (e instanceof FileNotFoundException) {
            return "File not found: " + e.getMessage();
        }
        if (e instanceof AccessDeniedException ade) {
            String file = ade.getOtherFile() != null ? ade.getOtherFile() : ade.getFile();
            return "Cannot write to \"" + file + "\". Is it open in another program or read-only?";
        }
        if (e instanceof FileSystemException fse) {
            String file = fse.getOtherFile() != null ? fse.getOtherFile() : fse.getFile();
            String reason = fse.getReason() != null ? ": " + fse.getReason() : "";
            return "Cannot access \"" + file + "\"" + reason + ". Is it open in another program?";
        }
        if (e instanceof NumberFormatException) {
            return "Invalid number (" + e.getMessage() + ").";
        }
        if (e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        if (e instanceof OutOfMemoryError) {
            return "Not enough memory to process the document.";
        }
        if (e instanceof IOException) {
            return "Could not process the file (is it damaged or not a PDF?): " + e.getMessage();
        }
        return "Unexpected error: " + e;
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
