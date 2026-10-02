package com.alejandro.pdftool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dibuja textos encima de las páginas: marca de agua y numeración. Trabaja en coordenadas visuales
 * ({@link PageGeometry}), así que el resultado se ve igual en páginas giradas o con CropBox desplazado.
 */
final class PageStamper {

    private static final float MAX_WATERMARK_SIZE = 64f;
    private static final float MIN_WATERMARK_SIZE = 8f;
    private static final double COS_45 = Math.cos(Math.toRadians(45));

    private PageStamper() {
    }

    static void watermark(PDDocument doc, String text, float opacity, Color color, ProgressListener progress)
            throws IOException {
        // Una sola fuente y un solo estado gráfico para todo el documento: PDResources reutiliza la entrada
        // cuando ve el mismo objeto, así no se duplican recursos página a página.
        PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        requireEncodable(font, text, "La marca de agua");
        float unitWidth = font.getStringWidth(text) / 1000f;
        float unitHeight = capHeight(font);
        PDExtendedGraphicsState transparency = new PDExtendedGraphicsState();
        transparency.setNonStrokingAlphaConstant(opacity);

        int total = doc.getNumberOfPages();
        int done = 0;
        for (PDPage page : doc.getPages()) {
            PageGeometry.Visual visual = PageGeometry.of(page);
            // El texto girado 45° ocupa (ancho + alto)·cos45 en cada eje: que quepa en el 90 % del lado menor
            float size = (float) (0.9 * Math.min(visual.width(), visual.height()) / ((unitWidth + unitHeight) * COS_45));
            size = Math.max(MIN_WATERMARK_SIZE, Math.min(MAX_WATERMARK_SIZE, size));
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.saveGraphicsState();
                cs.transform(visual.toPage());
                cs.setGraphicsStateParameters(transparency);
                cs.setNonStrokingColor(color);
                cs.beginText();
                cs.setFont(font, size);
                cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), visual.width() / 2f, visual.height() / 2f));
                cs.newLineAtOffset(-unitWidth * size / 2f, -unitHeight * size / 2f);
                cs.showText(text);
                cs.endText();
                cs.restoreGraphicsState();
            }
            progress.update(++done, total);
        }
    }

    static int pageNumbers(PDDocument doc, PageNumberOptions options, ProgressListener progress) throws IOException {
        PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        requireEncodable(font, options.format().replace("{n}", "").replace("{total}", ""), "El formato de la numeración");
        int pageCount = doc.getNumberOfPages();
        List<Integer> pages = new ArrayList<>(CliUtil.resolvePages(options.pages(), pageCount));
        Collections.sort(pages);
        if (pages.isEmpty()) {
            throw new PdfToolException("Ninguna de las páginas indicadas existe (el documento tiene " + pageCount + " páginas).");
        }
        int last = options.startNumber() + pages.size() - 1;
        float size = options.fontSize();
        float margin = options.margin();
        float textHeight = capHeight(font) * size;

        for (int k = 0; k < pages.size(); k++) {
            PDPage page = doc.getPage(pages.get(k) - 1);
            String label = options.format()
                    .replace("{n}", String.valueOf(options.startNumber() + k))
                    .replace("{total}", String.valueOf(last));
            float textWidth = font.getStringWidth(label) / 1000f * size;
            PageGeometry.Visual visual = PageGeometry.of(page);
            float x = switch (options.position().horizontal()) {
                case -1 -> margin;
                case 1 -> visual.width() - margin - textWidth;
                default -> (visual.width() - textWidth) / 2f;
            };
            float y = options.position().top() ? visual.height() - margin - textHeight : margin;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.saveGraphicsState();
                cs.transform(visual.toPage());
                cs.setNonStrokingColor(Color.BLACK);
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y);
                cs.showText(label);
                cs.endText();
                cs.restoreGraphicsState();
            }
            progress.update(k + 1, pages.size());
        }
        return pages.size();
    }

    /** Comprueba de antemano que todos los caracteres existen en la fuente y lista los que no. */
    static void requireEncodable(PDFont font, String text, String what) {
        Set<String> unsupported = new LinkedHashSet<>();
        text.codePoints().forEach(cp -> {
            String character = new String(Character.toChars(cp));
            try {
                font.encode(character);
            } catch (IllegalArgumentException | IOException e) {
                unsupported.add(character);
            }
        });
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(what + " contiene caracteres que la fuente Helvetica no admite: "
                    + unsupported.stream().map(c -> "«" + c + "»").collect(Collectors.joining(" ")));
        }
    }

    /** Altura de las mayúsculas para un tamaño de fuente 1. */
    private static float capHeight(PDFont font) throws IOException {
        PDFontDescriptor descriptor = font.getFontDescriptor();
        if (descriptor != null && descriptor.getCapHeight() > 0) {
            return descriptor.getCapHeight() / 1000f;
        }
        return font.getBoundingBox().getHeight() / 1000f * 0.7f;
    }
}
