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
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Ruta del PDF que se va a crear");
        runBarController.setText("Crear PDF");
        runBarController.setOnRun(this::run);
        pageSizeBox.getItems().setAll(ImagePageSize.values());
        pageSizeBox.setValue(ImagePageSize.A4);
        marginSpinner.disableProperty().bind(pageSizeBox.valueProperty().isEqualTo(ImagePageSize.IMAGE));
        imagesController.files().addListener((ListChangeListener<Path>) change -> {
            if (!imagesController.files().isEmpty()) {
                Path folder = imagesController.files().get(0).toAbsolutePath().getParent();
                outputController.suggest(OutputNames.unique(folder, "imagenes", "pdf"));
            }
        });
    }

    private void run() {
        List<Path> images = List.copyOf(imagesController.files());
        if (images.isEmpty()) {
            invalid("Añade al menos una imagen.");
            return;
        }
        ImagePageSize size = pageSizeBox.getValue();
        Integer marginMm = size == ImagePageSize.IMAGE ? Integer.valueOf(0) : spinnerValue(marginSpinner, "El margen");
        if (marginMm == null) return;
        Path output = outputFile(outputController, "Indica dónde guardar el PDF.");
        if (output == null) return;
        float margin = (float) (marginMm * MM_TO_PT);
        runBarController.start("Creando el PDF…",
                progress -> PdfOps.imagesToPdf(images, output, size, margin, progress),
                pages -> runBarController.success("PDF creado con " + pages + (pages == 1 ? " página" : " páginas")
                        + ": «" + fileName(output) + "».", output, null));
    }
}
