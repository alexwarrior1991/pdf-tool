package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.RunBarController;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.fxml.FXML;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
            invalid(field.hasText() ? "La ruta «" + field.getText() + "» no es válida." : missingMessage);
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
            invalid(field.hasText() ? "La ruta «" + field.getText() + "» no es válida." : missingMessage);
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

    /**
     * Valor de un Spinner editable, tomando lo que se haya escrito aunque no se haya pulsado Intro. Si no es un
     * número válido o está fuera de rango muestra el error y devuelve {@code null}.
     */
    protected Integer spinnerValue(Spinner<Integer> spinner, String what) {
        String typed = spinner.getEditor().getText().strip();
        int value;
        try {
            value = Integer.parseInt(typed);
        } catch (NumberFormatException e) {
            invalid(what + " debe ser un número entero: «" + typed + "».");
            spinner.requestFocus();
            return null;
        }
        if (spinner.getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory factory
                && (value < factory.getMin() || value > factory.getMax())) {
            invalid(what + " debe estar entre " + factory.getMin() + " y " + factory.getMax() + ".");
            spinner.requestFocus();
            return null;
        }
        spinner.getValueFactory().setValue(value);
        return value;
    }

    /** {@code true} si la salida es el mismo archivo que la entrada (se ha sobrescrito). */
    protected static boolean sameFile(Path input, Path output) {
        return input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize());
    }

    /** Archivos de {@code folder} cuyo nombre encaja con la expresión regular (para avisar antes de reemplazarlos). */
    protected static List<Path> existingFiles(Path folder, String nameRegex) {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(f -> f.getFileName().toString().matches(nameRegex)).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    protected static String fileName(Path path) {
        return String.valueOf(path.getFileName());
    }
}
