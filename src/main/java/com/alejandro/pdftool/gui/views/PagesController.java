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
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF del que quieres sacar o quitar páginas");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Ruta del PDF que se va a crear");
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
        runBarController.setText(keep ? "Extraer páginas" : "Eliminar páginas");
        suggestOutput();
    }

    private void suggestOutput() {
        Path input = inputController.getPath();
        if (input != null && Files.isRegularFile(input)) {
            outputController.suggest(OutputNames.besides(input, keepMode.isSelected() ? "extraido" : "sin_paginas", "pdf"));
        }
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF.");
        if (input == null) return;
        boolean keep = keepMode.isSelected();
        List<CliUtil.PageRange> ranges = ranges(rangesField, true, keep
                ? "Marca las páginas que quieres conservar (haz clic en las miniaturas o escribe p. ej. 1-3, 7)."
                : "Marca las páginas que quieres eliminar (haz clic en las miniaturas o escribe p. ej. 2, 5-7).");
        if (ranges == null) return;
        Path output = outputFile(outputController, "Indica dónde guardar el PDF resultante.");
        if (output == null) return;
        if (keep) {
            runBarController.start("Extrayendo páginas…",
                    progress -> PdfOps.extractPages(input, output, ranges),
                    pages -> runBarController.success("Nuevo PDF con " + pages + (pages == 1 ? " página" : " páginas")
                            + ": «" + fileName(output) + "».", output, null));
        } else {
            runBarController.start("Eliminando páginas…",
                    progress -> PdfOps.deletePages(input, output, ranges),
                    pages -> runBarController.success("Páginas eliminadas. El nuevo PDF tiene " + pages
                            + (pages == 1 ? " página" : " páginas") + ": «" + fileName(output) + "».", output, null));
        }
    }
}
