package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.RunBarController;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Base de los controladores de cada pantalla: acceso a la barra de ejecución y validaciones comunes. Los métodos
 * de validación muestran el error en la pantalla y devuelven {@code null} si el dato no es válido.
 */
public abstract class OperationView {

    @FXML
    protected RunBarController runBarController;

    protected final AppContext context;

    protected OperationView(AppContext context) {
        this.context = context;
    }

    /** Muestra un error de validación. */
    protected void invalid(String message) {
        runBarController.error(message);
    }

    /** Archivo de entrada obligatorio y existente. */
    protected Path inputFile(FileFieldController field, String missingMessage) {
        Path path = field.getPath();
        if (path == null) {
            invalid(missingMessage);
            return null;
        }
        if (!Files.isRegularFile(path)) {
            invalid("No existe el archivo «" + path + "».");
            return null;
        }
        return path;
    }

    /** Archivo de salida obligatorio; pregunta antes de sobrescribir si no se eligió en el diálogo. */
    protected Path outputFile(FileFieldController field, String missingMessage) {
        Path path = field.getPath();
        if (path == null) {
            invalid(missingMessage);
            return null;
        }
        if (Files.isDirectory(path)) {
            invalid("«" + path + "» es una carpeta: indica también el nombre del archivo.");
            return null;
        }
        if (!field.isConfirmedByDialog() && !runBarController.confirmOverwrite(path)) {
            return null;
        }
        return path;
    }

    /** Rangos de páginas del campo; {@code null} si hay un error (ya mostrado). */
    protected List<CliUtil.PageRange> ranges(TextField field, boolean required, String missingMessage) {
        String text = field.getText();
        if (text == null || text.isBlank()) {
            if (required) {
                invalid(missingMessage);
                return null;
            }
            return List.of();
        }
        try {
            return CliUtil.parseRanges(text);
        } catch (IllegalArgumentException e) {
            invalid(e.getMessage());
            field.requestFocus();
            return null;
        }
    }

    /** Propone una salida junto a la entrada cada vez que cambia el archivo de entrada. */
    protected static void suggestOutputBesides(FileFieldController input, FileFieldController output,
                                               String suffix, String extension) {
        input.pathProperty().addListener((obs, old, path) -> {
            if (path != null && Files.isRegularFile(path)) {
                output.suggest(OutputNames.besides(path, suffix, extension));
            }
        });
    }

    /** Valor de un Spinner editable, aceptando lo que se haya escrito aunque no se haya pulsado Intro. */
    protected static int spinnerValue(Spinner<Integer> spinner) {
        try {
            Integer typed = spinner.getValueFactory().getConverter().fromString(spinner.getEditor().getText());
            if (typed != null) {
                spinner.getValueFactory().setValue(typed);
            }
        } catch (RuntimeException ignored) {
            spinner.getEditor().setText(String.valueOf(spinner.getValue()));
        }
        return spinner.getValue();
    }

    protected static String fileName(Path path) {
        return String.valueOf(path.getFileName());
    }
}
