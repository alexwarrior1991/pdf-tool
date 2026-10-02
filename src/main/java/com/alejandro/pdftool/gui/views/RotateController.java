package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.ThumbnailsController;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

import java.nio.file.Path;
import java.util.List;

/** Pantalla «Rotar páginas» ({@code rotate.fxml}). */
public class RotateController extends OperationView {

    @FXML
    private FileFieldController inputController;
    @FXML
    private ThumbnailsController thumbnailsController;
    @FXML
    private ToggleGroup angle;
    @FXML
    private RadioButton allPages;
    @FXML
    private RadioButton somePages;
    @FXML
    private TextField rangesField;
    @FXML
    private FileFieldController outputController;

    public RotateController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to rotate");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the PDF to create");
        runBarController.setText("Rotate pages");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "rotated", "pdf");
        inputController.pathProperty().addListener((obs, old, path) -> thumbnailsController.load(path));
        thumbnailsController.bindRangesField(rangesField);
        rangesField.disableProperty().bind(somePages.selectedProperty().not());
        somePages.selectedProperty().addListener((obs, old, some) -> {
            thumbnailsController.setSelectable(some);
            updatePreview();
        });
        angle.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null && old != null) {
                old.setSelected(true); // siempre debe haber un giro elegido
                return;
            }
            updatePreview();
        });
        updatePreview();
    }

    private int degrees() {
        Toggle selected = angle.getSelectedToggle();
        return selected == null ? 90 : Integer.parseInt((String) selected.getUserData());
    }

    private void updatePreview() {
        thumbnailsController.setRotationPreview(degrees(), allPages.isSelected());
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF to rotate.");
        if (input == null) return;
        List<CliUtil.PageRange> ranges = allPages.isSelected() ? List.of()
                : ranges(rangesField, true, "Select the pages to rotate (click the thumbnails or type e.g. 1-3, 7).");
        if (ranges == null) return;
        Path output = outputFile(outputController, "Choose where to save the resulting PDF.");
        if (output == null) return;
        int degrees = degrees();
        runBarController.start("Rotating pages…",
                progress -> PdfOps.rotate(input, output, degrees, ranges),
                rotated -> {
                    runBarController.success("Rotated " + rotated + (rotated == 1 ? " page" : " pages")
                            + ": \"" + fileName(output) + "\".", output, null);
                    if (sameFile(input, output)) { // se ha sobrescrito: mostrar el PDF tal y como ha quedado
                        rangesField.clear();
                        inputController.refresh();
                        thumbnailsController.load(input);
                    }
                });
    }
}
