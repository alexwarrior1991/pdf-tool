package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.Formats;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/** Pantalla «Extraer texto» ({@code text.fxml}). */
public class TextController extends OperationView {

    @FXML
    private FileFieldController inputController;
    @FXML
    private TextArea textArea;
    @FXML
    private Label statsLabel;
    @FXML
    private Button copyButton;
    @FXML
    private Button saveButton;

    private Path source;

    public TextController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF del que quieres sacar el texto");
        runBarController.setText("Extraer texto");
        runBarController.setOnRun(this::run);
        copyButton.disableProperty().bind(textArea.textProperty().isEmpty());
        saveButton.disableProperty().bind(textArea.textProperty().isEmpty());
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF del que quieres sacar el texto.");
        if (input == null) return;
        runBarController.start("Leyendo el texto…",
                progress -> PdfOps.readText(input, progress),
                text -> show(input, text));
    }

    private void show(Path input, String text) {
        source = input;
        textArea.setText(text);
        textArea.positionCaret(0);
        String trimmed = text.strip();
        long words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        statsLabel.setText(String.format(Formats.SPANISH, "%,d palabras · %,d caracteres", words, text.length()));
        if (trimmed.isEmpty()) {
            runBarController.warning("El PDF no contiene texto seleccionable. Si es un documento escaneado, "
                    + "sus páginas son imágenes y haría falta un programa de reconocimiento de texto (OCR).");
        } else {
            runBarController.success("Texto extraído de «" + fileName(input) + "».", null, null);
        }
    }

    @FXML
    private void copy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(textArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
        runBarController.success("Texto copiado al portapapeles.", null, null);
    }

    @FXML
    private void save() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar texto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto (*.txt)", "*.txt"));
        if (source != null) {
            Path suggestion = OutputNames.besides(source, "texto", "txt");
            chooser.setInitialDirectory(suggestion.getParent().toFile());
            chooser.setInitialFileName(suggestion.getFileName().toString());
        } else {
            chooser.setInitialDirectory(context.initialDirectory());
        }
        File file = chooser.showSaveDialog(context.stage());
        if (file == null) return;
        Path target = file.getName().contains(".") ? file.toPath() : file.toPath().resolveSibling(file.getName() + ".txt");
        String text = textArea.getText();
        context.rememberDirectory(target);
        runBarController.start("Guardando…",
                progress -> {
                    PdfOps.saveText(text, target);
                    return target;
                },
                saved -> runBarController.success("Texto guardado en «" + fileName(saved) + "» (UTF-8).", saved, null));
    }
}
