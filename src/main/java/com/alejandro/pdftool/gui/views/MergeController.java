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
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Ruta del PDF que se va a crear");
        runBarController.setText("Unir PDF");
        runBarController.setOnRun(this::run);
        filesController.files().addListener((ListChangeListener<Path>) change -> {
            if (!filesController.files().isEmpty()) {
                Path folder = filesController.files().get(0).toAbsolutePath().getParent();
                outputController.suggest(OutputNames.unique(folder, "unido", "pdf"));
            }
        });
    }

    private void run() {
        List<Path> files = List.copyOf(filesController.files());
        if (files.size() < 2) {
            invalid("Añade al menos dos PDF para unir.");
            return;
        }
        Path output = outputFile(outputController, "Indica dónde guardar el PDF resultante.");
        if (output == null) return;
        runBarController.start("Uniendo " + files.size() + " archivos…",
                progress -> PdfOps.merge(files, output, progress),
                pages -> runBarController.success("Se han unido " + files.size() + " PDF (" + pages
                        + " páginas) en «" + fileName(output) + "».", output, null));
    }
}
