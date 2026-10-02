package com.alejandro.pdftool.gui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/** Diálogos con los botones siempre en español, sea cual sea el idioma del sistema. */
public final class Dialogs {

    public static final ButtonType CANCEL = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
    public static final ButtonType CLOSE = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

    private Dialogs() {
    }

    /** Pregunta de sí o no; devuelve {@code true} si se pulsa el botón {@code confirmText}. */
    public static boolean confirm(Window owner, String title, String message, String confirmText) {
        ButtonType ok = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ok, CANCEL);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ok;
    }

    /** Muestra el detalle técnico de un error (para poder copiarlo y pedir ayuda). */
    public static void details(Window owner, String message, Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        TextArea area = new TextArea(trace.toString());
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefSize(720, 320);
        Alert alert = new Alert(Alert.AlertType.ERROR, null, CLOSE);
        alert.initOwner(owner);
        alert.setTitle("Detalles del error");
        alert.setHeaderText(message);
        alert.getDialogPane().setContent(area);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
