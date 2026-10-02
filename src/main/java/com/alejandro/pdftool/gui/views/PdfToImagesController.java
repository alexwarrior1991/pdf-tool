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
import java.util.regex.Pattern;

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
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to export as images");
        folderController.configure(FileFieldController.Mode.DIRECTORY, "Folder to save the images in");
        runBarController.setText("Export images");
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
                    case 72, 96 -> dpi + " DPI (screen, small files)";
                    case 150 -> dpi + " DPI (recommended)";
                    case 300 -> dpi + " DPI (print)";
                    case 600 -> dpi + " DPI (maximum, large files)";
                    default -> dpi + " DPI";
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
                folderController.suggest(OutputNames.folderBesides(path, "images"));
                if (baseNameField.getText().isBlank() || old == null
                        || baseNameField.getText().equals(InputFiles.baseName(old))) {
                    baseNameField.setText(InputFiles.baseName(path));
                }
            }
        });
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF.");
        if (input == null) return;
        List<CliUtil.PageRange> ranges = ranges(rangesField, false, null);
        if (ranges == null) return;
        Path folder = folderController.getPath();
        if (folder == null) {
            invalid("Choose the folder to save the images in.");
            return;
        }
        String base = baseNameField.getText().strip();
        if (base.isEmpty() || base.matches(".*[\\\\/:*?\"<>|].*")) {
            invalid("Enter a valid name for the images (without \\ / : * ? \" < > |).");
            return;
        }
        ImageFormat format = formatBox.getValue();
        int dpi = dpiBox.getValue();
        int total = inputController.pageCountProperty().get();
        List<Path> planned = total > 0
                ? CliUtil.resolvePages(ranges, total).stream()
                .map(page -> PdfOps.pageImagePath(folder, base, page, total, format)).toList()
                : existingFiles(folder, Pattern.quote(base) + "_\\d{3,}\\." + format.extension());
        if (!runBarController.confirmOverwrite(planned)) return;
        runBarController.start("Exporting pages…",
                progress -> PdfOps.pdfToImages(input, folder, base, format, dpi, ranges, progress),
                files -> runBarController.success("Created " + files.size()
                        + (files.size() == 1 ? " image" : " images") + " in \"" + folder + "\".",
                        files.size() == 1 ? files.get(0) : null, folder));
    }
}
