package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.ThumbnailsController;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pantalla «Extraer o eliminar páginas» ({@code pages.fxml}). */
public class PagesController extends OperationView {

    @FXML
    private FileFieldController inputController;
    @FXML
    private ThumbnailsController thumbnailsController;
    @FXML
    private RadioButton keepMode;
    @FXML
    private RadioButton deleteMode;
    @FXML
    private TextField rangesField;
    @FXML
    private FileFieldController outputController;

    public PagesController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to extract or remove pages from");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the PDF to create");
        thumbnailsController.setSelectable(true);
        thumbnailsController.bindRangesField(rangesField);
        runBarController.setOnRun(this::run);
        inputController.pathProperty().addListener((obs, old, path) -> {
            thumbnailsController.load(path);
            suggestOutput();
        });
        keepMode.selectedProperty().addListener((obs, old, keep) -> modeChanged());
        modeChanged();
    }

    private void modeChanged() {
        boolean keep = keepMode.isSelected();
        thumbnailsController.setSelectionStyle(keep ? ThumbnailsController.SelectionStyle.HIGHLIGHT
                : ThumbnailsController.SelectionStyle.REMOVE);
        runBarController.setText(keep ? "Extract pages" : "Remove pages");
        suggestOutput();
    }

    private void suggestOutput() {
        Path input = inputController.getPath();
        if (input != null && Files.isRegularFile(input)) {
            outputController.suggest(OutputNames.besides(input, keepMode.isSelected() ? "extracted" : "pages_removed", "pdf"));
        }
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF.");
        if (input == null) return;
        boolean keep = keepMode.isSelected();
        List<CliUtil.PageRange> ranges = ranges(rangesField, true, keep
                ? "Select the pages to keep (click the thumbnails or type e.g. 1-3, 7)."
                : "Select the pages to remove (click the thumbnails or type e.g. 2, 5-7).");
        if (ranges == null) return;
        Path output = outputFile(outputController, "Choose where to save the resulting PDF.");
        if (output == null) return;
        if (keep) {
            runBarController.start("Extracting pages…",
                    progress -> PdfOps.extractPages(input, output, ranges),
                    pages -> {
                        runBarController.success("New PDF with " + pages + (pages == 1 ? " page" : " pages")
                                + ": \"" + fileName(output) + "\".", output, null);
                        reloadIfOverwritten(input, output);
                    });
        } else {
            runBarController.start("Removing pages…",
                    progress -> PdfOps.deletePages(input, output, ranges),
                    pages -> {
                        runBarController.success("Pages removed. The new PDF has " + pages
                                + (pages == 1 ? " page" : " pages") + ": \"" + fileName(output) + "\".", output, null);
                        reloadIfOverwritten(input, output);
                    });
        }
    }

    /** Si se ha sobrescrito el original, las miniaturas y la selección anteriores ya no valen. */
    private void reloadIfOverwritten(Path input, Path output) {
        if (sameFile(input, output)) {
            rangesField.clear();
            inputController.refresh();
            thumbnailsController.load(input);
        }
    }
}
