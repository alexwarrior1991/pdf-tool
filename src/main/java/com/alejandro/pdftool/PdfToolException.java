package com.alejandro.pdftool;

import java.io.IOException;

/**
 * Error con un mensaje en español pensado para mostrarse tal cual al usuario (CLI o GUI).
 */
public class PdfToolException extends IOException {

    public PdfToolException(String message) {
        super(message);
    }

    public PdfToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
