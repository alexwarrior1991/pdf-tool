package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.encryption.AccessPermission;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Permisos que se pueden conceder al proteger un PDF. */
public enum PdfPermission {
    PRINT("print", "Imprimir"),
    COPY("copy", "Copiar texto e imágenes"),
    MODIFY("modify", "Modificar el contenido"),
    ANNOTATE("annotate", "Añadir comentarios y anotaciones"),
    FILL("fill", "Rellenar formularios"),
    ASSEMBLE("assemble", "Insertar, girar o eliminar páginas");

    private final String cliName;
    private final String label;

    PdfPermission(String cliName, String label) {
        this.cliName = cliName;
        this.label = label;
    }

    public String cliName() {
        return cliName;
    }

    public String label() {
        return label;
    }

    public static PdfPermission fromCli(String name) {
        String wanted = name.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(p -> p.cliName.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Permiso desconocido «" + name + "». Usa: "
                        + Arrays.stream(values()).map(PdfPermission::cliName).collect(Collectors.joining(", "))));
    }

    /** Lista separada por comas, p. ej. {@code "print,copy"}. */
    public static Set<PdfPermission> parseCli(String list) {
        Set<PdfPermission> result = EnumSet.noneOf(PdfPermission.class);
        for (String name : list.split(",")) {
            if (!name.isBlank()) result.add(fromCli(name));
        }
        return result;
    }

    /** Permisos concedidos por un documento cifrado. */
    static Set<PdfPermission> granted(AccessPermission ap) {
        Set<PdfPermission> result = EnumSet.noneOf(PdfPermission.class);
        if (ap.canPrint()) result.add(PRINT);
        if (ap.canExtractContent()) result.add(COPY);
        if (ap.canModify()) result.add(MODIFY);
        if (ap.canModifyAnnotations()) result.add(ANNOTATE);
        if (ap.canFillInForm()) result.add(FILL);
        if (ap.canAssembleDocument()) result.add(ASSEMBLE);
        return result;
    }

    /** Permisos para cifrar: se conceden solo los indicados (más la extracción para accesibilidad). */
    static AccessPermission toAccessPermission(Set<PdfPermission> permissions) {
        AccessPermission ap = new AccessPermission();
        ap.setCanPrint(permissions.contains(PRINT));
        ap.setCanPrintFaithful(permissions.contains(PRINT));
        ap.setCanExtractContent(permissions.contains(COPY));
        ap.setCanModify(permissions.contains(MODIFY));
        ap.setCanModifyAnnotations(permissions.contains(ANNOTATE));
        ap.setCanFillInForm(permissions.contains(FILL));
        ap.setCanAssembleDocument(permissions.contains(ASSEMBLE));
        ap.setCanExtractForAccessibility(true);
        return ap;
    }
}
