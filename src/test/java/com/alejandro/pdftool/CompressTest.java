package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressTest {

    @TempDir
    Path dir;

    @Test
    void sharedImageIsRecompressedOnceAndStaysShared() throws IOException {
        Path in = TestPdfs.sharedImagePdf(dir.resolve("in.pdf"), TestPdfs.photo(800, 800), 300, 300, 300);
        Path out = dir.resolve("out.pdf");

        CompressResult result = PdfOps.compress(in, out, 0.7, null, false);

        assertEquals(1, result.imagesFound());
        assertEquals(1, result.imagesRecompressed());
        assertTrue(result.bytesAfter() < result.bytesBefore() / 2, Formats.compression(result));
        List<PDImageXObject> images = pageImages(out);
        assertEquals(3, images.size());
        Set<COSStream> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        images.forEach(i -> distinct.add(i.getCOSObject()));
        assertEquals(1, distinct.size(), "la imagen compartida debe seguir siendo un único objeto");
        assertEquals(COSName.DCT_DECODE, images.get(0).getCOSObject().getFilters());
    }

    @Test
    void maxDpiUsesTheLargestDisplayedSize() throws IOException {
        // 1200 px dibujados a 1" y a 4" → a 150 ppp hacen falta 600 px para el uso más grande
        Path in = TestPdfs.sharedImagePdf(dir.resolve("in.pdf"), TestPdfs.photo(1200, 1200), 72, 288);
        Path out = dir.resolve("out.pdf");

        PdfOps.compress(in, out, 0.8, 150, false);

        PDImageXObject image = pageImages(out).get(0);
        assertEquals(600, image.getWidth(), 2);
        assertEquals(600, image.getHeight(), 2);
    }

    @Test
    void imageAlreadyBelowMaxDpiKeepsItsResolution() throws IOException {
        Path in = TestPdfs.sharedImagePdf(dir.resolve("in.pdf"), TestPdfs.photo(400, 400), 288); // 100 ppp
        Path out = dir.resolve("out.pdf");
        PdfOps.compress(in, out, 0.7, 150, false);
        assertEquals(400, pageImages(out).get(0).getWidth());
    }

    @Test
    void imagesInsideFormXObjectsAreProcessed() throws IOException {
        Path in = TestPdfs.imageInsideFormPdf(dir.resolve("in.pdf"), TestPdfs.photo(600, 600));
        Path out = dir.resolve("out.pdf");

        CompressResult result = PdfOps.compress(in, out, 0.7, null, false);

        assertEquals(1, result.imagesRecompressed());
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            PDResources pageResources = doc.getPage(0).getResources();
            PDXObject form = pageResources.getXObject(pageResources.getXObjectNames().iterator().next());
            PDResources formResources = ((PDFormXObject) form).getResources();
            PDXObject image = formResources.getXObject(formResources.getXObjectNames().iterator().next());
            assertEquals(COSName.DCT_DECODE, ((PDImageXObject) image).getCOSObject().getFilters());
        }
    }

    @Test
    void imagesWithTransparencyAreLeftAlone() throws IOException {
        BufferedImage argb = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        BufferedImage photo = TestPdfs.photo(300, 300);
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 300; x++) {
                argb.setRGB(x, y, (photo.getRGB(x, y) & 0xFFFFFF) | ((x % 255) << 24));
            }
        }
        Path in = dir.resolve("in.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject image = LosslessFactory.createFromImage(doc, argb);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(image, 50, 50, 300, 300);
            }
            doc.save(in.toFile());
        }
        Path out = dir.resolve("out.pdf");

        CompressResult result = PdfOps.compress(in, out, 0.5, 72, false);

        assertEquals(0, result.imagesRecompressed());
        PDImageXObject image = pageImages(out).get(0);
        assertEquals(COSName.FLATE_DECODE, image.getCOSObject().getFilters());
        assertTrue(image.getCOSObject().containsKey(COSName.SMASK));
    }

    @Test
    void neverReplacesAnImageWithABiggerOne() throws IOException {
        Path in = dir.resolve("in.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject jpeg = JPEGFactory.createFromImage(doc, TestPdfs.photo(500, 500), 0.3f);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(jpeg, 50, 50, 400, 400);
            }
            doc.save(in.toFile());
        }
        Path out = dir.resolve("out.pdf");

        CompressResult result = PdfOps.compress(in, out, 0.95, null, false);

        assertEquals(0, result.imagesRecompressed());
        assertTrue(result.bytesAfter() <= result.bytesBefore() * 1.05, Formats.compression(result));
    }

    @Test
    void removesMetadataWhenAsked() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        PdfOps.updateMetadata(in, in, new DocumentMetadata("Título", "Autora", null, null));
        Path out = dir.resolve("out.pdf");

        PdfOps.compress(in, out, 0.7, null, true);

        assertEquals(null, PdfOps.info(out).metadata().title());
        assertEquals(null, PdfOps.info(out).metadata().author());
        assertTrue(Files.size(out) > 0);
    }

    private static List<PDImageXObject> pageImages(Path pdf) throws IOException {
        List<PDImageXObject> images = new ArrayList<>();
        PDDocument doc = Loader.loadPDF(pdf.toFile()); // se deja abierto: los tests consultan los streams después
        for (PDPage page : doc.getPages()) {
            PDResources resources = page.getResources();
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject image) {
                    images.add(image);
                }
            }
        }
        return images;
    }
}
