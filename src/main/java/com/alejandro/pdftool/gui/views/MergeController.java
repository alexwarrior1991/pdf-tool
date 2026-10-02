package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.FileListController;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;

import java.nio.file.Path;
import java.util.List;

/** Pantalla «Unir PDF» ({@code merge.fxml}). */
public class MergeController extends OperationView {

    @FXML
    private FileListController filesController;
    @FXML
    private FileFieldController outputController;

    public MergeController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        filesController.configure(FileListController.Kind.PDF);
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the PDF to create");
        runBarController.setText("Merge PDFs");
        runBarController.setOnRun(this::run);
        filesController.files().addListener((ListChangeListener<Path>) change -> {
            if (!filesController.files().isEmpty()) {
                Path folder = filesController.files().get(0).toAbsolutePath().getParent();
                outputController.suggest(OutputNames.unique(folder, "merged", "pdf"));
            }
        });
    }

    private void run() {
        List<Path> files = List.copyOf(filesController.files());
        if (files.size() < 2) {
            invalid("Add at least two PDFs to merge.");
            return;
        }
        Path output = outputFile(outputController, "Choose where to save the resulting PDF.");
        if (output == null) return;
        runBarController.start("Merging " + files.size() + " files…",
                progress -> PdfOps.merge(files, output, progress),
                pages -> runBarController.success("Merged " + files.size() + " PDFs (" + pages
                        + (pages == 1 ? " page" : " pages") + ") into \"" + fileName(output) + "\".", output, null));
    }
}
