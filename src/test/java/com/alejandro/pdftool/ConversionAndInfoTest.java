package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.xml.DomXmpParser;
import org.apache.xmpbox.xml.XmpSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionAndInfoTest {

    @TempDir
    Path dir;

    @Test
    void jpegFromPhoneIsShownUpright() throws Exception {
        // Foto guardada "tumbada" (400x200) con EXIF 6: se ve vertical y el rojo (arriba-izquierda en los datos)
        // debe quedar arriba a la derecha.
        Path jpeg = TestPdfs.jpegWithOrientation(dir.resolve("foto.jpg"), 400, 200, 6);
        Path out = dir.resolve("fotos.pdf");

        PdfOps.imagesToPdf(List.of(jpeg), out, ImagePageSize.A4, 0, ProgressListener.NONE);

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            PDPage page = doc.getPage(0);
            assertTrue(page.getMediaBox().getHeight() > page.getMediaBox().getWidth(), "A4 vertical");
        }
        BufferedImage image = TestPdfs.render(out, 0, 30);
        int w = image.getWidth();
        int h = image.getHeight();
        assertTrue(TestPdfs.reddish(image, w * 3 / 4, h / 4, 10) > 50, "rojo arriba a la derecha");
        assertEquals(0, TestPdfs.reddish(image, w / 4, h / 4, 10), "nada de rojo arriba a la izquierda");
    }

    @Test
    void imagesKeepTheirAspectAndUseLandscapePagesWhenWide() throws Exception {
        Path png = dir.resolve("ancha.png");
        ImageIO.write(TestPdfs.photo(300, 100), "png", png.toFile());
        Path jpeg = TestPdfs.jpegWithOrientation(dir.resolve("normal.jpg"), 100, 300, 1);
        Path out = dir.resolve("out.pdf");

        assertEquals(2, PdfOps.imagesToPdf(List.of(png, jpeg), out, ImagePageSize.A4, 28, ProgressListener.NONE));

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertTrue(doc.getPage(0).getMediaBox().getWidth() > doc.getPage(0).getMediaBox().getHeight());
            assertTrue(doc.getPage(1).getMediaBox().getWidth() < doc.getPage(1).getMediaBox().getHeight());
        }
    }

    @Test
    void imageSizedPagesAndUnsupportedFiles() throws Exception {
        Path png = dir.resolve("a.png");
        ImageIO.write(TestPdfs.photo(192, 96), "png", png.toFile());
        Path out = dir.resolve("out.pdf");

        PdfOps.imagesToPdf(List.of(png), out, ImagePageSize.IMAGE, 50, ProgressListener.NONE);

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertEquals(144, doc.getPage(0).getMediaBox().getWidth(), 0.01); // 192 px a 96 ppp = 2"
            assertEquals(72, doc.getPage(0).getMediaBox().getHeight(), 0.01);
        }
        Path notAnImage = Files.writeString(dir.resolve("nota.png"), "texto");
        assertThrows(PdfToolException.class,
                () -> PdfOps.imagesToPdf(List.of(notAnImage), dir.resolve("x.pdf"), ImagePageSize.A4, 0, ProgressListener.NONE));
    }

    @Test
    void exportsSelectedPagesAsImages() throws Exception {
        Path in = TestPdfs.textPdf(dir.resolve("informe.pdf"), 3);
        Path outDir = dir.resolve("imagenes");

        List<Path> files = PdfOps.pdfToImages(in, outDir, "informe", ImageFormat.PNG, 72,
                CliUtil.parseRanges("3,1"), ProgressListener.NONE);

        assertEquals(List.of("informe_003.png", "informe_001.png"),
                files.stream().map(p -> p.getFileName().toString()).toList());
        BufferedImage page = ImageIO.read(files.get(0).toFile());
        assertEquals(595, page.getWidth(), 1);
        List<Path> jpgs = PdfOps.pdfToImages(in, outDir, "informe", ImageFormat.JPG, 36, List.of(), ProgressListener.NONE);
        assertEquals(3, jpgs.size());
        assertTrue(jpgs.get(0).toString().endsWith(".jpg"));
    }

    @Test
    void readsTextWithProgress() throws Exception {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 3);
        List<Long> progress = new ArrayList<>();
        String text = PdfOps.readText(in, (done, total) -> progress.add(done));
        assertTrue(text.contains("Pagina 1") && text.contains("Pagina 3"));
        assertEquals(List.of(1L, 2L, 3L), progress);

        Path txt = dir.resolve("out/texto.txt");
        PdfOps.extractText(in, txt);
        assertTrue(Files.readString(txt).contains("Pagina 2"));
    }

    @Test
    void infoDescribesTheDocument() throws Exception {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 2);
        PdfInfo info = PdfOps.info(in);
        assertEquals(2, info.pages());
        assertFalse(info.encrypted());
        assertNull(info.permissions());
        assertEquals(595, info.pageWidth(), 1);
        assertEquals(842, info.pageHeight(), 1);
        assertEquals(Files.size(in), info.fileSize());
        assertEquals("210 × 297 mm (A4)", Formats.pageSize(info.pageWidth(), info.pageHeight()));
    }

    @Test
    void updatesInfoDictionaryAndXmp() throws Exception {
        Path in = dir.resolve("xmp.pdf");
        try (PDDocument doc = Loader.loadPDF(TestPdfs.textPdf(dir.resolve("base.pdf"), 1).toFile())) {
            doc.getDocumentInformation().setTitle("Antiguo");
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();
            DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
            dc.setTitle("Antiguo");
            dc.addCreator("Alguien");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new XmpSerializer().serialize(xmp, bytes, true);
            PDMetadata metadata = new PDMetadata(doc);
            metadata.importXMPMetadata(bytes.toByteArray());
            doc.getDocumentCatalog().setMetadata(metadata);
            doc.save(in.toFile());
        }
        Path out = dir.resolve("out.pdf");

        assertTrue(PdfOps.updateMetadata(in, out, new DocumentMetadata("Nuevo título", "Ana Pérez", "Pruebas", "pdf, java")));

        DocumentMetadata read = PdfOps.info(out).metadata();
        assertEquals(new DocumentMetadata("Nuevo título", "Ana Pérez", "Pruebas", "pdf, java"), read);
        try (PDDocument doc = Loader.loadPDF(out.toFile());
             InputStream xmpStream = doc.getDocumentCatalog().getMetadata().exportXMPMetadata()) {
            DomXmpParser parser = new DomXmpParser();
            parser.setStrictParsing(false);
            DublinCoreSchema dc = parser.parse(xmpStream).getDublinCoreSchema();
            assertEquals("Nuevo título", dc.getTitle());
            assertEquals(List.of("Ana Pérez"), dc.getCreators());
            assertEquals(List.of("pdf", "java"), dc.getSubjects());
        }
    }

    @Test
    void clearingMetadataRemovesTheValues() throws Exception {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        PdfOps.updateMetadata(in, in, new DocumentMetadata("Título", "Autora", "Asunto", "clave"));
        PdfOps.updateMetadata(in, in, new DocumentMetadata(" ", null, "", null));
        assertEquals(new DocumentMetadata(null, null, null, null), PdfOps.info(in).metadata());
    }
}
