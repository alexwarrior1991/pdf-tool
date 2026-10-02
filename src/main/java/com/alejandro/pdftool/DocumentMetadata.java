package com.alejandro.pdftool;

/**
 * Metadatos editables de un PDF. Un valor {@code null} o vacío significa «sin valor».
 */
public record DocumentMetadata(String title, String author, String subject, String keywords) {

    public DocumentMetadata {
        title = clean(title);
        author = clean(author);
        subject = clean(subject);
        keywords = clean(keywords);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
