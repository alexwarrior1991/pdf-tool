package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StampAndRotateTest {

    @TempDir
    Path dir;

    @Test
    void rotatesSelectedPagesAndNormalizesAngles() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 3);
        Path out = dir.resolve("out.pdf");

        assertEquals(2, PdfOps.rotate(in, out, -90, CliUtil.parseRanges("1,3")));

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertEquals(270, doc.getPage(0).getRotation());
            assertEquals(0, doc.getPage(1).getRotation());
            assertEquals(270, doc.getPage(2).getRotation());
        }
    }

    @Test
    void rejectsAnglesThatAreNotMultiplesOf90() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        assertThrows(IllegalArgumentException.class, () -> PdfOps.rotate(in, dir.resolve("o.pdf"), 45, List.of()));
        assertThrows(PdfToolException.class, () -> PdfOps.rotate(in, dir.resolve("o.pdf"), 90, CliUtil.parseRanges("4")));
    }

    @Test
    void rotateCanOverwriteItsInput() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 2);
        PdfOps.rotate(in, in, 180, List.of());
        try (PDDocument doc = Loader.loadPDF(in.toFile())) {
            assertEquals(180, doc.getPage(1).getRotation());
        }
    }

    /**
     * La marca debe verse en diagonal de abajo-izquierda a arriba-derecha y leerse en ese sentido en cualquier
     * página. El texto empieza con letras anchas (W) y acaba con puntos: el extremo inferior izquierdo debe tener
     * mucha más tinta que el superior derecho.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 90, 180, 270})
    void watermarkReadsUpwardsOnRotatedPages(int rotation) throws IOException {
        Path in = TestPdfs.rotatedBlankPdf(dir.resolve("r" + rotation + ".pdf"), rotation);
        Path out = dir.resolve("wm" + rotation + ".pdf");

        PdfOps.watermarkText(in, out, "WWWWWWWWWWWW ....................", 1f, Color.RED, ProgressListener.NONE);

        BufferedImage image = TestPdfs.render(out, 0, 50);
        int w = image.getWidth();
        int h = image.getHeight();
        int d = Math.min(w, h) / 4;
        int box = Math.min(w, h) / 6;
        int bottomLeft = TestPdfs.reddish(image, w / 2 - d, h / 2 + d, box);
        int topRight = TestPdfs.reddish(image, w / 2 + d, h / 2 - d, box);
        int topLeft = TestPdfs.reddish(image, w / 2 - d, h / 2 - d, box);
        int bottomRight = TestPdfs.reddish(image, w / 2 + d, h / 2 + d, box);
        assertTrue(bottomLeft > 0 && topRight > 0, "la marca debe cruzar la diagonal ascendente");
        assertEquals(0, topLeft + bottomRight, "no debe haber marca en la otra diagonal");
        assertTrue(bottomLeft > topRight * 2, "el texto debe empezar abajo a la izquierda: " + bottomLeft + " vs " + topRight);
    }

    @Test
    void watermarkReusesFontAndStateOnSharedResources() throws IOException {
        Path in = TestPdfs.inheritedAttributesPdf(dir.resolve("shared.pdf"), 3);
        Path out = dir.resolve("out.pdf");

        PdfOps.watermarkText(in, out, "CONFIDENCIAL", 0.3f, Color.GRAY, ProgressListener.NONE);

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            PDResources resources = doc.getPage(0).getResources();
            int fonts = 0;
            for (COSName ignored : resources.getFontNames()) fonts++;
            int states = 0;
            for (COSName ignored : resources.getExtGStateNames()) states++;
            assertEquals(2, fonts, "fuente del texto original + una sola fuente para la marca");
            assertEquals(1, states);
        }
        assertTrue(TestPdfs.compactText(out).contains("CONFIDENCIAL"));
    }

    @Test
    void watermarkListsUnsupportedCharacters() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PdfOps.watermarkText(in, dir.resolve("o.pdf"), "Hola → 世界", 0.2f, Color.RED, ProgressListener.NONE));
        assertTrue(e.getMessage().contains("«→»") && e.getMessage().contains("«世»"), e.getMessage());
    }

    @Test
    void watermarkSupportsSpanishCharacters() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        Path out = dir.resolve("o.pdf");
        PdfOps.watermarkText(in, out, "Borrador – año 2026 ¿sí?", 0.2f, Color.RED, ProgressListener.NONE);
        assertTrue(TestPdfs.compactText(out).contains("año2026¿sí?"), TestPdfs.compactText(out));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 90, 180, 270})
    void pageNumbersGoToTheVisibleBottom(int rotation) throws IOException {
        Path in = TestPdfs.rotatedBlankPdf(dir.resolve("r" + rotation + ".pdf"), rotation);
        Path out = dir.resolve("n" + rotation + ".pdf");
        PageNumberOptions options = new PageNumberOptions("Página {n} de {total}",
                PageNumberOptions.Position.BOTTOM_CENTER, 20, 20, 1, List.of());

        PdfOps.addPageNumbers(in, out, options, ProgressListener.NONE);

        BufferedImage image = TestPdfs.render(out, 0, 50);
        assertTrue(TestPdfs.darkInBand(image, 0.85, 1.0) > 0, "el número debe estar abajo");
        assertEquals(0, TestPdfs.darkInBand(image, 0.0, 0.85));
        int centre = TestPdfs.dark(image, image.getWidth() / 2, image.getHeight() - 14, 60);
        assertTrue(centre > 0, "el número debe estar centrado");
    }

    @Test
    void pageNumbersHonourStartNumberAndPageSelection() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 4);
        Path out = dir.resolve("out.pdf");
        PageNumberOptions options = new PageNumberOptions("{n}/{total}", PageNumberOptions.Position.TOP_RIGHT,
                10, 28, 1, CliUtil.parseRanges("2-*"));

        assertEquals(3, PdfOps.addPageNumbers(in, out, options, ProgressListener.NONE));

        String text = TestPdfs.text(out);
        assertTrue(text.contains("1/3") && text.contains("3/3"), text);
        assertTrue(!text.contains("4/"), text);
    }

    @Test
    void pageGeometryMapsTheVisibleOriginForEveryRotation() {
        PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(10, 20, 100, 200));
        page.setRotation(90);
        PageGeometry.Visual visual = PageGeometry.of(page);
        assertEquals(200, visual.width(), 1e-3);
        assertEquals(100, visual.height(), 1e-3);
        // origen visual (abajo-izquierda en pantalla) = esquina inferior derecha del espacio de la página
        java.awt.geom.Point2D.Float origin = visual.toPage().transformPoint(0, 0);
        assertEquals(110, origin.x, 1e-3);
        assertEquals(20, origin.y, 1e-3);
    }
}
