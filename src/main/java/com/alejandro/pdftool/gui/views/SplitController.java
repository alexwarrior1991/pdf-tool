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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to split");
        folderController.configure(FileFieldController.Mode.DIRECTORY, "Folder where the parts will be saved");
        runBarController.setText("Split PDF");
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
        String base = baseNameField.getText().isBlank() ? "document" : baseNameField.getText().strip();
        exampleLabel.setText("Creates files like " + base + "_part001.pdf, " + base + "_part002.pdf…");
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF to split.");
        if (input == null) return;
        Path folder = folderController.getPath();
        if (folder == null) {
            invalid("Choose the folder to save the parts in.");
            return;
        }
        String base = baseNameField.getText().strip();
        if (base.isEmpty() || base.matches(".*[\\\\/:*?\"<>|].*")) {
            invalid("Enter a valid base name for the files (without \\ / : * ? \" < > |).");
            return;
        }
        Path prefix = folder.resolve(base);
        int pages = inputController.pageCountProperty().get();
        if (byRanges.isSelected()) {
            List<CliUtil.PageRange> ranges = ranges(rangesField, true, "Enter the page ranges, e.g. 1-3, 4-10, 11-*.");
            if (ranges == null) return;
            int count = (int) ranges.stream().filter(r -> pages <= 0 || r.start() <= pages).count();
            if (!runBarController.confirmOverwrite(plannedParts(prefix, base, folder, pages > 0 ? count : -1))) return;
            runBarController.start("Splitting…",
                    progress -> PdfOps.splitByRanges(input, prefix, ranges, progress),
                    parts -> done(parts, folder));
        } else {
            Integer every = spinnerValue(everySpinner, "\"Pages per part\"");
            if (every == null) return;
            int count = pages > 0 ? (pages + every - 1) / every : -1;
            if (!runBarController.confirmOverwrite(plannedParts(prefix, base, folder, count))) return;
            runBarController.start("Splitting…",
                    progress -> PdfOps.splitEvery(input, prefix, every, progress),
                    parts -> done(parts, folder));
        }
    }

    /**
     * Partes que se van a crear, para avisar si ya existen. Si aún no se sabe cuántas páginas tiene el PDF
     * ({@code count < 0}), se avisa de todas las partes de ese nombre que haya en la carpeta.
     */
    private static List<Path> plannedParts(Path prefix, String base, Path folder, int count) {
        if (count < 0) {
            return existingFiles(folder, Pattern.quote(base) + "_part\\d{3,}\\.pdf");
        }
        List<Path> parts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            parts.add(PdfOps.partPath(prefix, i));
        }
        return parts;
    }

    private void done(List<Path> parts, Path folder) {
        runBarController.success("Created " + parts.size() + (parts.size() == 1 ? " file" : " files")
                + " in \"" + folder + "\".", parts.size() == 1 ? parts.get(0) : null, folder);
    }
}
