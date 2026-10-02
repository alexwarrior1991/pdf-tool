package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.InputFiles;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.ThumbnailsController;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pantalla «Dividir PDF» ({@code split.fxml}). */
public class SplitController extends OperationView {

    @FXML
    private FileFieldController inputController;
    @FXML
    private ThumbnailsController thumbnailsController;
    @FXML
    private RadioButton byRanges;
    @FXML
    private RadioButton byCount;
    @FXML
    private TextField rangesField;
    @FXML
    private Spinner<Integer> everySpinner;
    @FXML
    private FileFieldController folderController;
    @FXML
    private TextField baseNameField;
    @FXML
    private Label exampleLabel;

    public SplitController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF que quieres dividir");
        folderController.configure(FileFieldController.Mode.DIRECTORY, "Carpeta donde se guardarán las partes");
        runBarController.setText("Dividir PDF");
        runBarController.setOnRun(this::run);
        rangesField.disableProperty().bind(byRanges.selectedProperty().not());
        everySpinner.disableProperty().bind(byCount.selectedProperty().not());
        inputController.pathProperty().addListener((obs, old, path) -> {
            thumbnailsController.load(path);
            if (path != null && Files.isRegularFile(path)) {
                folderController.suggest(path.toAbsolutePath().getParent());
                if (baseNameField.getText().isBlank() || old == null
                        || baseNameField.getText().equals(InputFiles.baseName(old))) {
                    baseNameField.setText(InputFiles.baseName(path));
                }
            }
        });
        baseNameField.textProperty().addListener((obs, old, text) -> updateExample());
        updateExample();
    }

    private void updateExample() {
        String base = baseNameField.getText().isBlank() ? "documento" : baseNameField.getText().strip();
        exampleLabel.setText("Se crearán archivos como " + base + "_part001.pdf, " + base + "_part002.pdf…");
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF que quieres dividir.");
        if (input == null) return;
        Path folder = folderController.getPath();
        if (folder == null) {
            invalid("Elige la carpeta donde guardar las partes.");
            return;
        }
        String base = baseNameField.getText().strip();
        if (base.isEmpty() || base.matches(".*[\\\\/:*?\"<>|].*")) {
            invalid("Escribe un nombre base válido para los archivos (sin \\ / : * ? \" < > |).");
            return;
        }
        Path prefix = folder.resolve(base);
        if (byRanges.isSelected()) {
            List<CliUtil.PageRange> ranges = ranges(rangesField, true, "Escribe los rangos de páginas, p. ej. 1-3, 4-10, 11-*.");
            if (ranges == null) return;
            runBarController.start("Dividiendo…",
                    progress -> PdfOps.splitByRanges(input, prefix, ranges, progress),
                    parts -> done(parts, folder));
        } else {
            int every = spinnerValue(everySpinner);
            runBarController.start("Dividiendo…",
                    progress -> PdfOps.splitEvery(input, prefix, every, progress),
                    parts -> done(parts, folder));
        }
    }

    private void done(List<Path> parts, Path folder) {
        runBarController.success("Se han creado " + parts.size() + (parts.size() == 1 ? " archivo" : " archivos")
                + " en «" + folder + "».", parts.size() == 1 ? parts.get(0) : null, folder);
    }
}
