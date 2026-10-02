package com.alejandro.pdftool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityTest {

    @TempDir
    Path dir;

    @Test
    void encryptsWithAes256AndOnlyTheRequestedPermissions() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        Path out = dir.resolve("enc.pdf");

        PdfOps.encrypt(in, out, "propietario", "lectura", EnumSet.of(PdfPermission.PRINT, PdfPermission.FILL));

        assertThrows(InvalidPasswordException.class, () -> Loader.loadPDF(out.toFile()).close());
        try (PDDocument doc = Loader.loadPDF(out.toFile(), "lectura")) {
            PDEncryption encryption = doc.getEncryption();
            assertEquals(256, encryption.getLength());
            assertEquals(5, encryption.getVersion());
            assertEquals(6, encryption.getRevision());
            AccessPermission ap = doc.getCurrentAccessPermission();
            assertTrue(ap.canPrint() && ap.canFillInForm());
            assertFalse(ap.canExtractContent() || ap.canModify() || ap.canModifyAnnotations() || ap.canAssembleDocument());
            assertTrue(ap.canExtractForAccessibility());
        }
    }

    @Test
    void ownerAndUserPasswordsMustDiffer() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        assertThrows(IllegalArgumentException.class, () -> PdfOps.encrypt(in, dir.resolve("o.pdf"), "x", "x", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> PdfOps.encrypt(in, dir.resolve("o.pdf"), "", null, Set.of()));
    }

    @Test
    void decryptNeedsTheOwnerPassword() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        Path enc = dir.resolve("enc.pdf");
        PdfOps.encrypt(in, enc, "propietario", "lectura", Set.of());
        Path out = dir.resolve("dec.pdf");

        PdfToolException userOnly = assertThrows(PdfToolException.class, () -> PdfOps.decrypt(enc, out, "lectura"));
        assertTrue(userOnly.getMessage().contains("owner password"));
        PdfToolException wrong = assertThrows(PdfToolException.class, () -> PdfOps.decrypt(enc, out, "otra"));
        assertTrue(wrong.getMessage().contains("Incorrect password"));

        PdfOps.decrypt(enc, out, "propietario");
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertFalse(doc.isEncrypted());
        }
    }

    @Test
    void decryptExplainsWhenThereIsNothingToRemove() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        assertThrows(PdfToolException.class, () -> PdfOps.decrypt(in, dir.resolve("o.pdf"), "x"));
    }

    @Test
    void restrictedPdfsAreNotModifiedWithoutTheOwnerPassword() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 2);
        Path restricted = dir.resolve("restricted.pdf");
        PdfOps.encrypt(in, restricted, "propietario", "", EnumSet.of(PdfPermission.PRINT));
        Path out = dir.resolve("out.pdf");

        PdfToolException e = assertThrows(PdfToolException.class, () -> PdfOps.rotate(restricted, out, 90, List.of()));
        assertTrue(e.getMessage().contains("restrictions"), e.getMessage());
        assertThrows(PdfToolException.class, () -> PdfOps.merge(List.of(in, restricted), out));
        assertThrows(PdfToolException.class,
                () -> PdfOps.watermarkText(restricted, out, "X", 0.2f, Color.RED, ProgressListener.NONE));
        assertThrows(PdfToolException.class, () -> PdfOps.readText(restricted, ProgressListener.NONE));
        assertThrows(PdfToolException.class, () -> PdfOps.encrypt(restricted, out, "a", "b", Set.of()));

        PdfInfo info = PdfOps.info(restricted);
        assertTrue(info.encrypted());
        assertEquals(EnumSet.of(PdfPermission.PRINT), info.permissions());
        assertEquals(2, info.pages());
    }

    @Test
    void encryptionWithoutRestrictionsDoesNotBlockOperations() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        Path open = dir.resolve("open.pdf");
        try (PDDocument doc = Loader.loadPDF(in.toFile())) {
            doc.protect(new StandardProtectionPolicy("", "", new AccessPermission()));
            doc.save(open.toFile());
        }
        Path out = dir.resolve("out.pdf");

        PdfOps.rotate(open, out, 90, List.of());

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertFalse(doc.isEncrypted());
            assertEquals(90, doc.getPage(0).getRotation());
        }
    }

    @Test
    void passwordProtectedInputsGetAClearMessage() throws IOException {
        Path in = TestPdfs.textPdf(dir.resolve("in.pdf"), 1);
        Path enc = dir.resolve("enc.pdf");
        PdfOps.encrypt(in, enc, "propietario", "lectura", Set.of());
        PdfToolException e = assertThrows(PdfToolException.class, () -> PdfOps.info(enc));
        assertTrue(e.getMessage().contains("open password"), e.getMessage());
    }
}
