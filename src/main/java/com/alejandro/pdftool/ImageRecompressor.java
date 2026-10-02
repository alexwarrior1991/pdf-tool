package com.alejandro.pdftool;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceN;
import org.apache.pdfbox.pdmodel.graphics.color.PDIndexed;
import org.apache.pdfbox.pdmodel.graphics.color.PDSeparation;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.util.Matrix;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recomprime en JPEG las imágenes de un documento.
 * <ol>
 *   <li>Recorre el contenido de cada página (incluidos Form XObjects y apariencias de anotaciones) para saber
 *   qué imágenes se dibujan y a qué tamaño máximo.</li>
 *   <li>Cada imagen distinta se recomprime una sola vez y, si el resultado es claramente más pequeño, se
 *   reescribe en su propio stream: todas las páginas que la usan quedan actualizadas a la vez.</li>
 * </ol>
 */
final class ImageRecompressor {

    record Result(int imagesFound, int imagesRecompressed) {
    }

    private static final int MIN_PIXELS = 32;
    private static final long MIN_BYTES = 4 * 1024;
    /** Solo se sustituye si el JPEG nuevo es al menos un 10 % más pequeño. */
    private static final double MIN_SAVING = 0.10;
    /** Margen antes de reducir resolución: no merece la pena remuestrear por un 10 %. */
    private static final double DPI_TOLERANCE = 1.10;
    private static final Set<COSName> UNSUPPORTED_FILTERS =
            Set.of(COSName.CCITTFAX_DECODE, COSName.JBIG2_DECODE, COSName.JPX_DECODE);

    /** Imagen y tamaño máximo (en puntos) con el que aparece en el documento. */
    private static final class Usage {
        final PDImageXObject image;
        double maxWidth;
        double maxHeight;

        Usage(PDImageXObject image) {
            this.image = image;
        }
    }

    private final PDDocument doc;
    private final Map<COSStream, Usage> usages = new IdentityHashMap<>();

    private ImageRecompressor(PDDocument doc) {
        this.doc = doc;
    }

    static Result recompress(PDDocument doc, double quality, Integer maxDpi, ProgressListener progress)
            throws IOException {
        ImageRecompressor recompressor = new ImageRecompressor(doc);
        recompressor.collectUsages();
        return recompressor.recompressAll((float) Math.max(0.1, Math.min(1.0, quality)), maxDpi, progress);
    }

    private void collectUsages() throws IOException {
        UsageCollector collector = new UsageCollector();
        for (PDPage page : doc.getPages()) {
            collector.processPage(page);
            for (PDAnnotation annotation : page.getAnnotations()) {
                collector.showAnnotation(annotation);
            }
        }
    }

    private Result recompressAll(float quality, Integer maxDpi, ProgressListener progress) {
        int total = usages.size();
        int done = 0;
        int recompressed = 0;
        for (Usage usage : usages.values()) {
            try {
                if (recompress(usage, quality, maxDpi)) {
                    recompressed++;
                }
            } catch (IOException | RuntimeException e) {
                // imagen que no se puede decodificar o recodificar: se deja tal cual
            }
            progress.update(++done, total);
        }
        return new Result(total, recompressed);
    }

    private boolean recompress(Usage usage, float quality, Integer maxDpi) throws IOException {
        COSStream stream = usage.image.getCOSObject();
        if (!isCandidate(stream) || isPrintColorSpace(usage.image)) {
            return false;
        }
        BufferedImage decoded = usage.image.getImage();
        if (decoded == null || decoded.getColorModel().hasAlpha()) {
            return false;
        }
        double scale = downscaleFactor(decoded.getWidth(), decoded.getHeight(), usage, maxDpi);
        BufferedImage source = scale < 1.0
                ? resize(decoded, (int) Math.max(1, Math.round(decoded.getWidth() * scale)),
                (int) Math.max(1, Math.round(decoded.getHeight() * scale)))
                : decoded;
        PDImageXObject jpeg = JPEGFactory.createFromImage(doc, source, quality);
        COSStream encoded = jpeg.getCOSObject();
        if (encoded.getLength() > stream.getLength() * (1 - MIN_SAVING)) {
            return false;
        }
        replaceInPlace(stream, encoded);
        return true;
    }

    /** Descarta máscaras, imágenes de 1 bit, formatos que no conviene tocar e imágenes diminutas. */
    private static boolean isCandidate(COSStream stream) {
        if (stream.getBoolean(COSName.IMAGE_MASK, false)
                || stream.containsKey(COSName.SMASK) || stream.containsKey(COSName.MASK)) {
            return false;
        }
        if (stream.getInt(COSName.BITS_PER_COMPONENT, 8) == 1) {
            return false;
        }
        COSBase filters = stream.getFilters();
        if (filters instanceof COSName name && UNSUPPORTED_FILTERS.contains(name)) {
            return false;
        }
        if (filters instanceof COSArray array) {
            for (COSBase filter : array) {
                if (filter instanceof COSName name && UNSUPPORTED_FILTERS.contains(name)) {
                    return false;
                }
            }
        }
        return stream.getInt(COSName.WIDTH, 0) >= MIN_PIXELS
                && stream.getInt(COSName.HEIGHT, 0) >= MIN_PIXELS
                && stream.getLength() >= MIN_BYTES;
    }

    /** Separation/DeviceN (tintas planas para imprenta) no se convierten a RGB. */
    private static boolean isPrintColorSpace(PDImageXObject image) throws IOException {
        PDColorSpace colorSpace = image.getColorSpace();
        if (colorSpace instanceof PDIndexed indexed) {
            colorSpace = indexed.getBaseColorSpace();
        }
        return colorSpace instanceof PDSeparation || colorSpace instanceof PDDeviceN;
    }

    /**
     * Factor de reducción para que la imagen no supere {@code maxDpi} en el tamaño máximo al que se muestra.
     * Se usa la misma escala en ambos ejes y se elige la más conservadora (ningún eje baja de {@code maxDpi}).
     */
    private static double downscaleFactor(int width, int height, Usage usage, Integer maxDpi) {
        if (maxDpi == null || maxDpi <= 0 || usage.maxWidth < 1 || usage.maxHeight < 1) {
            return 1.0;
        }
        double maxWidthPx = usage.maxWidth / 72.0 * maxDpi;
        double maxHeightPx = usage.maxHeight / 72.0 * maxDpi;
        double scale = Math.max(maxWidthPx / width, maxHeightPx / height);
        return scale * DPI_TOLERANCE < 1.0 ? scale : 1.0;
    }

    /** Reducción por pasos (como mucho a la mitad en cada uno) para mantener la calidad; conserva el gris. */
    private static BufferedImage resize(BufferedImage src, int targetWidth, int targetHeight) {
        boolean gray = src.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY
                && src.getColorModel().getNumComponents() == 1;
        int type = gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = src;
        int w = src.getWidth();
        int h = src.getHeight();
        do {
            w = Math.max(targetWidth, w / 2);
            h = Math.max(targetHeight, h / 2);
            BufferedImage next = new BufferedImage(w, h, type);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = next;
        } while (w != targetWidth || h != targetHeight);
        return current;
    }

    /** Sustituye los datos del stream original por el JPEG, conservando el resto de su diccionario. */
    private static void replaceInPlace(COSStream target, COSStream jpeg) throws IOException {
        try (InputStream in = jpeg.createRawInputStream(); OutputStream out = target.createRawOutputStream()) {
            in.transferTo(out);
        }
        target.setItem(COSName.FILTER, COSName.DCT_DECODE);
        target.removeItem(COSName.DECODE_PARMS);
        target.removeItem(COSName.DECODE);
        target.removeItem(COSName.DL);
        target.setInt(COSName.WIDTH, jpeg.getInt(COSName.WIDTH));
        target.setInt(COSName.HEIGHT, jpeg.getInt(COSName.HEIGHT));
        target.setInt(COSName.BITS_PER_COMPONENT, 8);
        target.setItem(COSName.COLORSPACE, jpeg.getItem(COSName.COLORSPACE));
    }

    /** Motor ligero: solo sigue la matriz de transformación y los "Do" de imágenes y formularios. */
    private final class UsageCollector extends PDFStreamEngine {

        UsageCollector() {
            addOperator(new Concatenate(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new DrawObject(this)); // entra en Form XObjects (con límite de recursión)
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (OperatorName.DRAW_OBJECT.equals(operator.getName()) && !operands.isEmpty()
                    && operands.get(0) instanceof COSName name) {
                PDResources resources = getResources();
                if (resources != null && resources.isImageXObject(name)) {
                    try {
                        if (resources.getXObject(name) instanceof PDImageXObject image) {
                            record(image, getGraphicsState().getCurrentTransformationMatrix());
                        }
                    } catch (IOException | RuntimeException e) {
                        // imagen ilegible: no se toca
                    }
                    return;
                }
            }
            super.processOperator(operator, operands);
        }

        private void record(PDImageXObject image, Matrix ctm) {
            // La imagen ocupa el cuadrado unidad transformado por la CTM; los factores llevan signo si hay giro
            // o espejo, por eso se usa la longitud de cada vector.
            double width = Math.hypot(ctm.getScaleX(), ctm.getShearY());
            double height = Math.hypot(ctm.getShearX(), ctm.getScaleY());
            Usage usage = usages.computeIfAbsent(image.getCOSObject(), key -> new Usage(image));
            usage.maxWidth = Math.max(usage.maxWidth, width);
            usage.maxHeight = Math.max(usage.maxHeight, height);
        }
    }
}
