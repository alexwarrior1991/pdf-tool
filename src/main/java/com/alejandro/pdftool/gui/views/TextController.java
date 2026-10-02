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
import java.nio.file.Files;
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
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to extract text from");
        runBarController.setText("Extract text");
        runBarController.setOnRun(this::run);
        copyButton.disableProperty().bind(textArea.textProperty().isEmpty());
        saveButton.disableProperty().bind(textArea.textProperty().isEmpty().or(context.busyProperty()));
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF to extract text from.");
        if (input == null) return;
        runBarController.start("Reading the text…",
                progress -> PdfOps.readText(input, progress),
                text -> show(input, text));
    }

    private void show(Path input, String text) {
        source = input;
        textArea.setText(text);
        textArea.positionCaret(0);
        String trimmed = text.strip();
        long words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        statsLabel.setText(String.format(Formats.LOCALE, "%,d " + (words == 1 ? "word" : "words") + " · %,d "
                + (text.length() == 1 ? "character" : "characters"), words, text.length()));
        if (trimmed.isEmpty()) {
            runBarController.warning("The PDF has no selectable text. If it's a scanned document, "
                    + "its pages are images and you'd need a text recognition (OCR) program.");
        } else {
            runBarController.success("Text extracted from \"" + fileName(input) + "\".", null, null);
        }
    }

    @FXML
    private void copy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(textArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
        runBarController.success("Text copied to the clipboard.", null, null);
    }

    @FXML
    private void save() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save text");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text (*.txt)", "*.txt"));
        chooser.setInitialDirectory(context.initialDirectory());
        if (source != null) {
            Path suggestion = OutputNames.besides(source, "text", "txt");
            if (Files.isDirectory(suggestion.getParent())) { // la carpeta puede haber desaparecido (USB retirado…)
                chooser.setInitialDirectory(suggestion.getParent().toFile());
            }
            chooser.setInitialFileName(suggestion.getFileName().toString());
        }
        File file = chooser.showSaveDialog(context.stage());
        if (file == null) return;
        Path chosen = file.toPath();
        Path target = OutputNames.withExtension(chosen, "txt");
        // el diálogo ya preguntó por el nombre escrito; si se ha añadido «.txt» es otro archivo
        if (!target.equals(chosen) && !runBarController.confirmOverwrite(target)) return;
        String text = textArea.getText();
        context.rememberDirectory(target);
        runBarController.start("Saving…",
                progress -> {
                    PdfOps.saveText(text, target);
                    return target;
                },
                saved -> runBarController.success("Text saved to \"" + fileName(saved) + "\" (UTF-8).", saved, null));
    }
}
