package com.alejandro.pdftool.gui;

import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

/** Icono de la aplicación dibujado en código (una hoja roja con «PDF»), en varios tamaños. */
final class AppIcon {

    private AppIcon() {
    }

    static List<Image> images() {
        return List.of(draw(16), draw(32), draw(64), draw(128));
    }

    private static Image draw(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double fold = size * 0.28;
        // hoja con la esquina superior derecha doblada
        g.setFill(Color.web("#d93a2f"));
        g.fillPolygon(new double[]{0, size - fold, size, size, 0}, new double[]{0, 0, fold, size, size}, 5);
        g.setFill(Color.web("#a8231a"));
        g.fillPolygon(new double[]{size - fold, size - fold, size}, new double[]{0, fold, fold}, 3);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", FontWeight.BOLD, size * 0.34));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText("PDF", size / 2.0, size * 0.6);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return canvas.snapshot(parameters, null);
    }
}
