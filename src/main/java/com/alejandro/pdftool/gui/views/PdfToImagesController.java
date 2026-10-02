package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.ImageFormat;
import com.alejandro.pdftool.InputFiles;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.components.ThumbnailsController;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pantalla «PDF a imágenes» ({@code pdf-to-images.fxml}). */
public class PdfToImagesController extends OperationView {

    @FXML
    private FileFieldController inputController;
    @FXML
    private ThumbnailsController thumbnailsController;
    @FXML
    private TextField rangesField;
    @FXML
    private ComboBox<ImageFormat> formatBox;
    @FXML
    private ComboBox<Integer> dpiBox;
    @FXML
    private FileFieldController folderController;
    @FXML
    private TextField baseNameField;

    public PdfToImagesController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF del que quieres sacar imágenes");
        folderController.configure(FileFieldController.Mode.DIRECTORY, "Carpeta donde guardar las imágenes");
        runBarController.setText("Exportar imágenes");
        runBarController.setOnRun(this::run);
        thumbnailsController.setSelectable(true);
        thumbnailsController.bindRangesField(rangesField);
        formatBox.getItems().setAll(ImageFormat.values());
        formatBox.setValue(ImageFormat.PNG);
        dpiBox.getItems().setAll(72, 96, 150, 200, 300, 600);
        dpiBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer dpi) {
                if (dpi == null) return "";
                return switch (dpi) {
                    case 72, 96 -> dpi + " ppp (pantalla, ligero)";
                    case 150 -> dpi + " ppp (recomendado)";
                    case 300 -> dpi + " ppp (impresión)";
                    case 600 -> dpi + " ppp (máxima, pesado)";
                    default -> dpi + " ppp";
                };
            }

            @Override
            public Integer fromString(String text) {
                return Integer.parseInt(text.replaceAll("\\D.*", ""));
            }
        });
        dpiBox.setValue(150);
        inputController.pathProperty().addListener((obs, old, path) -> {
            thumbnailsController.load(path);
            if (path != null && Files.isRegularFile(path)) {
                folderController.suggest(OutputNames.folderBesides(path, "imagenes"));
                if (baseNameField.getText().isBlank() || old == null
                        || baseNameField.getText().equals(InputFiles.baseName(old))) {
                    baseNameField.setText(InputFiles.baseName(path));
                }
            }
        });
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF.");
        if (input == null) return;
        List<CliUtil.PageRange> ranges = ranges(rangesField, false, null);
        if (ranges == null) return;
        Path folder = folderController.getPath();
        if (folder == null) {
            invalid("Elige la carpeta donde guardar las imágenes.");
            return;
        }
        String base = baseNameField.getText().strip();
        if (base.isEmpty() || base.matches(".*[\\\\/:*?\"<>|].*")) {
            invalid("Escribe un nombre válido para las imágenes (sin \\ / : * ? \" < > |).");
            return;
        }
        ImageFormat format = formatBox.getValue();
        int dpi = dpiBox.getValue();
        runBarController.start("Exportando páginas…",
                progress -> PdfOps.pdfToImages(input, folder, base, format, dpi, ranges, progress),
                files -> runBarController.success("Se han creado " + files.size()
                        + (files.size() == 1 ? " imagen" : " imágenes") + " en «" + folder + "».",
                        files.size() == 1 ? files.get(0) : null, folder));
    }
}
