package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.ImagePageSize;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.FileListController;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;

import java.nio.file.Path;
import java.util.List;

/** Pantalla «Imágenes a PDF» ({@code images-to-pdf.fxml}). */
public class ImagesToPdfController extends OperationView {

    private static final double MM_TO_PT = 72.0 / 25.4;

    @FXML
    private FileListController imagesController;
    @FXML
    private ComboBox<ImagePageSize> pageSizeBox;
    @FXML
    private Spinner<Integer> marginSpinner;
    @FXML
    private FileFieldController outputController;

    public ImagesToPdfController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        imagesController.configure(FileListController.Kind.IMAGES);
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the PDF to create");
        runBarController.setText("Create PDF");
        runBarController.setOnRun(this::run);
        pageSizeBox.getItems().setAll(ImagePageSize.values());
        pageSizeBox.setValue(ImagePageSize.A4);
        marginSpinner.disableProperty().bind(pageSizeBox.valueProperty().isEqualTo(ImagePageSize.IMAGE));
        imagesController.files().addListener((ListChangeListener<Path>) change -> {
            if (!imagesController.files().isEmpty()) {
                Path folder = imagesController.files().get(0).toAbsolutePath().getParent();
                outputController.suggest(OutputNames.unique(folder, "images", "pdf"));
            }
        });
    }

    private void run() {
        List<Path> images = List.copyOf(imagesController.files());
        if (images.isEmpty()) {
            invalid("Add at least one image.");
            return;
        }
        ImagePageSize size = pageSizeBox.getValue();
        Integer marginMm = size == ImagePageSize.IMAGE ? Integer.valueOf(0) : spinnerValue(marginSpinner, "The margin");
        if (marginMm == null) return;
        Path output = outputFile(outputController, "Choose where to save the PDF.");
        if (output == null) return;
        float margin = (float) (marginMm * MM_TO_PT);
        runBarController.start("Creating the PDF…",
                progress -> PdfOps.imagesToPdf(images, output, size, margin, progress),
                pages -> runBarController.success("Created a PDF with " + pages + (pages == 1 ? " page" : " pages")
                        + ": \"" + fileName(output) + "\".", output, null));
    }
}
