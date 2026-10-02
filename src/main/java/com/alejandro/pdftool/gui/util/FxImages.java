package com.alejandro.pdftool.gui.util;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;

/** Conversión de imágenes de Java2D (lo que genera PDFBox) a JavaFX, sin depender de javafx-swing. */
public final class FxImages {

    private FxImages() {
    }

    public static WritableImage toFxImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        WritableImage result = new WritableImage(width, height);
        result.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return result;
    }
}
