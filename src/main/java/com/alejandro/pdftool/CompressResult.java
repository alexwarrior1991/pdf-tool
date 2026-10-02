package com.alejandro.pdftool;

/**
 * Resultado de comprimir un PDF.
 *
 * @param bytesBefore        tamaño del archivo original
 * @param bytesAfter         tamaño del archivo generado
 * @param imagesFound        imágenes distintas que aparecen en el documento
 * @param imagesRecompressed imágenes que se han recomprimido
 */
public record CompressResult(long bytesBefore, long bytesAfter, int imagesFound, int imagesRecompressed) {

    /** Variación de tamaño en porcentaje (negativo si el archivo se ha reducido). */
    public double changePercent() {
        return bytesBefore == 0 ? 0 : (bytesAfter - bytesBefore) * 100.0 / bytesBefore;
    }
}
