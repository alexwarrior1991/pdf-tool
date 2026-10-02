package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Renderiza páginas para vistas previas. Mantiene el PDF abierto hasta {@link #close()}; no es seguro usarlo desde
 * varios hilos a la vez.
 */
public final class PageRenderer implements AutoCloseable {

    private final PDDocument doc;
    private final PDFRenderer renderer;

    private PageRenderer(PDDocument doc) {
        this.doc = doc;
        this.renderer = new PDFRenderer(doc);
        this.renderer.setSubsamplingAllowed(true); // más rápido y con menos memoria en miniaturas
    }

    public static PageRenderer open(Path pdf) throws IOException {
        return new PageRenderer(Pdfs.open(pdf));
    }

    public int pageCount() {
        return doc.getNumberOfPages();
    }

    /** Ancho de la página tal y como se ve (ya girada), en puntos. */
    public float visibleWidth(int pageIndex) {
        return PageGeometry.of(doc.getPage(pageIndex)).width();
    }

    /** Renderiza la página para que mida {@code widthPx} píxeles de ancho (tal y como se ve). */
    public BufferedImage renderToWidth(int pageIndex, int widthPx) throws IOException {
        float scale = widthPx / Math.max(1f, visibleWidth(pageIndex));
        return renderer.renderImage(pageIndex, scale, ImageType.RGB);
    }

    @Override
    public void close() throws IOException {
        doc.close();
    }
}
