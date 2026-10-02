package com.alejandro.pdftool;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Información de un PDF.
 *
 * @param fileSize     tamaño del archivo en bytes
 * @param pages        número de páginas
 * @param version      versión de PDF, p. ej. {@code "1.7"}
 * @param encrypted    si el documento está cifrado
 * @param permissions  permisos concedidos si está cifrado ({@code null} si no lo está)
 * @param pageWidth    ancho de la primera página tal y como se ve, en puntos
 * @param pageHeight   alto de la primera página tal y como se ve, en puntos
 * @param metadata     título, autor, asunto y palabras clave
 * @param creator      aplicación que creó el documento original
 * @param producer     aplicación que generó el PDF
 * @param created      fecha de creación (puede ser {@code null})
 * @param modified     fecha de modificación (puede ser {@code null})
 */
public record PdfInfo(long fileSize, int pages, String version, boolean encrypted, Set<PdfPermission> permissions,
                      float pageWidth, float pageHeight, DocumentMetadata metadata, String creator, String producer,
                      ZonedDateTime created, ZonedDateTime modified) {
}
