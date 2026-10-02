package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeSplitTest {

    @TempDir
    Path dir;

    @Test
    void mergesFilesInOrderAndCountsPages() throws IOException {
        Path a = TestPdfs.textPdf(dir.resolve("a.pdf"), 2);
        Path b = TestPdfs.textPdf(dir.resolve("b.pdf"), 3);
        Path out = dir.resolve("out/merged.pdf");

        assertEquals(5, PdfOps.merge(List.of(a, b), out));
        assertEquals(5, TestPdfs.pageCount(out));
        String text = TestPdfs.text(out);
        assertTrue(text.indexOf("Pagina 2") < text.indexOf("Pagina 3"));
    }

    @Test
    void failsClearlyWhenAnInputIsMissing() throws IOException {
        Path a = TestPdfs.textPdf(dir.resolve("a.pdf"), 1);
        Path out = dir.resolve("merged.pdf");
        PdfToolException e = assertThrows(PdfToolException.class,
                () -> PdfOps.merge(List.of(a, dir.resolve("missing.pdf")), out));
        assertTrue(e.getMessage().contains("missing.pdf"));
        assertFalse(Files.exists(out));
        assertNoTempFiles();
    }

    @Test
    void folderExpansionUsesNaturalOrderAndSkipsTheOutput() throws IOException {
        Path folder = Files.createDirectories(dir.resolve("docs"));
        TestPdfs.textPdf(folder.resolve("doc10.pdf"), 1);
        TestPdfs.textPdf(folder.resolve("doc2.pdf"), 1);
        Files.writeString(folder.resolve("notes.txt"), "no es un PDF");
        Path out = folder.resolve("merged.pdf");
        Files.writeString(out, "salida anterior");

        List<Path> inputs = PdfOps.expandPdfInputs(List.of(folder), out);

        assertEquals(List.of("doc2.pdf", "doc10.pdf"), inputs.stream().map(p -> p.getFileName().toString()).toList());
    }

    @Test
    void outputMayBeOneOfTheInputs() throws IOException {
        Path a = TestPdfs.textPdf(dir.resolve("a.pdf"), 2);
        Path b = TestPdfs.textPdf(dir.resolve("b.pdf"), 1);
        PdfOps.merge(List.of(a, b), a);
        assertEquals(3, TestPdfs.pageCount(a));
    }

    @Test
    void splitKeepsAttributesInheritedFromThePageTree() throws IOException {
        Path in = TestPdfs.inheritedAttributesPdf(dir.resolve("inherited.pdf"), 4);

        List<Path> parts = PdfOps.splitByRanges(in, dir.resolve("out/part"), CliUtil.parseRanges("1-2,4"));

        assertEquals(List.of("part_part001.pdf", "part_part002.pdf"),
                parts.stream().map(p -> p.getFileName().toString()).toList());
        try (PDDocument doc = Loader.loadPDF(parts.get(1).toFile())) {
            PDPage page = doc.getPage(0);
            assertEquals(612, page.getMediaBox().getWidth(), 0.01);
            assertEquals(90, page.getRotation());
            assertNotNull(page.getResources().getFont(page.getResources().getFontNames().iterator().next()));
        }
        assertTrue(TestPdfs.compactText(parts.get(0)).contains("Pagina2"));
        assertTrue(TestPdfs.compactText(parts.get(1)).contains("Pagina4"));
    }

    @Test
    void splitEveryCreatesEvenParts() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 5);
        List<Path> parts = PdfOps.splitEvery(in, dir.resolve("p"), 2, ProgressListener.NONE);
        assertEquals(3, parts.size());
        assertEquals(List.of(2, 2, 1), pageCounts(parts));
    }

    @Test
    void splitFailsWhenNoRangeExists() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 2);
        assertThrows(PdfToolException.class, () -> PdfOps.splitByRanges(in, dir.resolve("p"), CliUtil.parseRanges("5-9")));
        assertNoTempFiles();
    }

    @Test
    void splitRefusesToOverwriteTheInput() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("x_part001.pdf"), 2);
        assertThrows(PdfToolException.class, () -> PdfOps.splitByRanges(in, dir.resolve("x"), CliUtil.parseRanges("1")));
        assertEquals(2, TestPdfs.pageCount(in));
    }

    @Test
    void extractKeepsTheRequestedOrder() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 4);
        Path out = dir.resolve("extract.pdf");

        assertEquals(3, PdfOps.extractPages(in, out, CliUtil.parseRanges("4,1-2")));

        String text = TestPdfs.text(out);
        assertTrue(text.indexOf("Pagina 4") < text.indexOf("Pagina 1"));
        assertTrue(text.indexOf("Pagina 1") < text.indexOf("Pagina 2"));
        assertFalse(text.contains("Pagina 3"));
    }

    @Test
    void deleteRemovesPagesCompletely() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 4);
        Path out = dir.resolve("deleted.pdf");

        assertEquals(2, PdfOps.deletePages(in, out, CliUtil.parseRanges("2-3")));

        String text = TestPdfs.text(out);
        assertTrue(text.contains("Pagina 1") && text.contains("Pagina 4"));
        assertFalse(text.contains("Pagina 2") || text.contains("Pagina 3"));
        assertThrows(PdfToolException.class, () -> PdfOps.deletePages(in, out, CliUtil.parseRanges("1-*")));
    }

    @Test
    void extractWorksOnInheritedAttributes() throws IOException {
        Path in = TestPdfs.inheritedAttributesPdf(dir.resolve("inherited.pdf"), 3);
        Path out = dir.resolve("extract.pdf");
        PdfOps.extractPages(in, out, CliUtil.parseRanges("3,1"));
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertEquals(90, doc.getPage(0).getRotation());
            assertEquals(800, doc.getPage(0).getMediaBox().getHeight(), 0.01);
        }
        String text = TestPdfs.compactText(out);
        assertTrue(text.indexOf("Pagina3") < text.indexOf("Pagina1"), text);
    }

    private List<Integer> pageCounts(List<Path> files) throws IOException {
        List<Integer> counts = new ArrayList<>();
        for (Path file : files) counts.add(TestPdfs.pageCount(file));
        return counts;
    }

    private void assertNoTempFiles() throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "quedan temporales");
        }
    }
}
