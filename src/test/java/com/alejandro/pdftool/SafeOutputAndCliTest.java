package com.alejandro.pdftool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeOutputAndCliTest {

    @TempDir
    Path dir;

    @Test
    void failedWriteLeavesTargetUntouchedAndNoTempFile() throws IOException {
        Path target = Files.writeString(dir.resolve("salida.pdf"), "original");

        assertThrows(IOException.class, () -> {
            try (SafeOutput out = SafeOutput.to(target)) {
                out.write(os -> {
                    os.write("a medias".getBytes(StandardCharsets.UTF_8));
                    throw new IOException("fallo simulado");
                });
                out.commit();
            }
        });

        assertEquals("original", Files.readString(target));
        assertEquals(1, fileCount());
    }

    @Test
    void commitReplacesTheTargetAndCreatesFolders() throws IOException {
        Path target = dir.resolve("nueva/carpeta/salida.txt");
        try (SafeOutput out = SafeOutput.to(target)) {
            out.write(os -> os.write("hola".getBytes(StandardCharsets.UTF_8)));
            assertFalse(Files.exists(target), "no se toca el destino hasta confirmar");
            out.commit();
        }
        assertEquals("hola", Files.readString(target));
        assertEquals(1, fileCount());
    }

    @Test
    void cliReturnsExitCodesAndFriendlyErrors() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 3);
        assertEquals(0, App.run(new String[]{"--help"}));
        assertEquals(2, App.run(new String[]{"desconocido"}));

        String err = captureErr(() -> assertEquals(1, App.run(new String[]{"info", dir.resolve("no.pdf").toString()})));
        assertTrue(err.startsWith("Error: File \""), err);
        assertFalse(err.contains("Exception"), err);

        err = captureErr(() -> assertEquals(2, App.run(new String[]{"rotate", in.toString(), "-o",
                dir.resolve("o.pdf").toString(), "-deg", "45"})));
        assertTrue(err.contains("multiple of 90"), err);

        err = captureErr(() -> assertEquals(2, App.run(new String[]{"split", in.toString(), "-ranges", "3-1", "-o",
                dir.resolve("p").toString()})));
        assertTrue(err.contains("Reversed range"), err);
    }

    @Test
    void cliRunsTheNewCommands() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 4);
        Path folder = dir.resolve("out");
        assertEquals(0, App.run(new String[]{"extract", in.toString(), "-o", folder.resolve("e.pdf").toString(), "-pages", "4,1"}));
        assertEquals(0, App.run(new String[]{"delete", in.toString(), "-o", folder.resolve("d.pdf").toString(), "-pages", "1"}));
        assertEquals(0, App.run(new String[]{"pagenum", in.toString(), "-o", folder.resolve("n.pdf").toString(),
                "-pos", "top-right", "-margin", "10"}));
        assertEquals(0, App.run(new String[]{"metadata", in.toString(), "-o", folder.resolve("m.pdf").toString(),
                "-title", "Informe"}));
        assertEquals(0, App.run(new String[]{"pdf2images", in.toString(), "-o", folder.resolve("img").toString(),
                "-dpi", "36", "-pages", "1-2"}));
        assertEquals(0, App.run(new String[]{"images2pdf", "-o", folder.resolve("i.pdf").toString(),
                folder.resolve("img").toString()}));
        assertEquals(0, App.run(new String[]{"split", in.toString(), "-every", "3", "-o", folder.resolve("s").toString()}));

        assertEquals(2, TestPdfs.pageCount(folder.resolve("e.pdf")));
        assertEquals(3, TestPdfs.pageCount(folder.resolve("d.pdf")));
        assertEquals("Informe", PdfOps.info(folder.resolve("m.pdf")).metadata().title());
        assertEquals(2, TestPdfs.pageCount(folder.resolve("i.pdf")));
        assertTrue(Files.exists(folder.resolve("s_part002.pdf")));
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static String captureErr(Runnable action) {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
