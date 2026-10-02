package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;

/**
 * Coordenadas "visuales" de una página: origen en la esquina inferior izquierda de la zona visible (CropBox)
 * tal y como se ve en pantalla, es decir, ya aplicado el giro {@code /Rotate}. Dibujar con {@link Visual#toPage()}
 * aplicada permite colocar textos y marcas igual en cualquier página, esté girada o no.
 */
final class PageGeometry {

    /**
     * @param toPage matriz que convierte coordenadas visuales en coordenadas del espacio de la página
     * @param width  ancho visible tal y como se ve la página
     * @param height alto visible tal y como se ve la página
     */
    record Visual(Matrix toPage, float width, float height) {
    }

    private PageGeometry() {
    }

    static Visual of(PDPage page) {
        PDRectangle box = page.getCropBox();
        float x = box.getLowerLeftX();
        float y = box.getLowerLeftY();
        float w = box.getWidth();
        float h = box.getHeight();
        // /Rotate gira la página en sentido horario al mostrarla (PDFBox ya lo normaliza a 0, 90, 180 o 270)
        return switch (page.getRotation()) {
            case 90 -> new Visual(new Matrix(0, 1, -1, 0, x + w, y), h, w);
            case 180 -> new Visual(new Matrix(-1, 0, 0, -1, x + w, y + h), w, h);
            case 270 -> new Visual(new Matrix(0, -1, 1, 0, x, y + h), h, w);
            default -> new Visual(new Matrix(1, 0, 0, 1, x, y), w, h);
        };
    }
}
