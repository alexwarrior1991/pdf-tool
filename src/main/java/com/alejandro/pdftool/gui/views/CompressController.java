package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CompressResult;
import com.alejandro.pdftool.Formats;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;

import java.nio.file.Path;

/** Pantalla «Comprimir» ({@code compress.fxml}). */
public class CompressController extends OperationView {

    /** Preajustes: calidad JPEG (%) y DPI máximo (null = sin límite). */
    private enum Preset {
        HIGH("High quality", 85, null),
        BALANCED("Balanced (recommended)", 70, 150),
        SMALLEST("Smallest size", 50, 96),
        CUSTOM("Custom", -1, null);

        final String label;
        final int quality;
        final Integer maxDpi;

        Preset(String label, int quality, Integer maxDpi) {
            this.label = label;
            this.quality = quality;
            this.maxDpi = maxDpi;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @FXML
    private FileFieldController inputController;
    @FXML
    private ComboBox<Preset> presetBox;
    @FXML
    private Slider qualitySlider;
    @FXML
    private Label qualityLabel;
    @FXML
    private CheckBox limitDpiCheck;
    @FXML
    private Spinner<Integer> dpiSpinner;
    @FXML
    private CheckBox removeMetadataCheck;
    @FXML
    private FileFieldController outputController;

    private boolean applyingPreset;

    public CompressController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to compress");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the compressed PDF");
        runBarController.setText("Compress PDF");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "compressed", "pdf");

        presetBox.getItems().setAll(Preset.values());
        presetBox.valueProperty().addListener((obs, old, preset) -> apply(preset));
        qualitySlider.valueProperty().addListener((obs, old, value) -> {
            qualityLabel.setText(Math.round(value.doubleValue()) + "%");
            customised();
        });
        dpiSpinner.disableProperty().bind(limitDpiCheck.selectedProperty().not());
        limitDpiCheck.selectedProperty().addListener((obs, old, value) -> customised());
        dpiSpinner.valueProperty().addListener((obs, old, value) -> customised());
        presetBox.setValue(Preset.BALANCED);
    }

    private void apply(Preset preset) {
        if (preset == null || preset == Preset.CUSTOM) return;
        applyingPreset = true;
        qualitySlider.setValue(preset.quality);
        limitDpiCheck.setSelected(preset.maxDpi != null);
        if (preset.maxDpi != null) dpiSpinner.getValueFactory().setValue(preset.maxDpi);
        applyingPreset = false;
    }

    private void customised() {
        if (!applyingPreset && presetBox.getValue() != Preset.CUSTOM) {
            presetBox.setValue(Preset.CUSTOM);
        }
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF to compress.");
        if (input == null) return;
        Integer maxDpi = null;
        if (limitDpiCheck.isSelected()) {
            maxDpi = spinnerValue(dpiSpinner, "The maximum resolution");
            if (maxDpi == null) return;
        }
        Path output = outputFile(outputController, "Choose where to save the compressed PDF.");
        if (output == null) return;
        double quality = Math.round(qualitySlider.getValue()) / 100.0;
        Integer dpiLimit = maxDpi;
        boolean removeMetadata = removeMetadataCheck.isSelected();
        runBarController.start("Compressing images…",
                progress -> PdfOps.compress(input, output, quality, dpiLimit, removeMetadata, progress),
                result -> {
                    done(result, output);
                    if (sameFile(input, output)) inputController.refresh();
                });
    }

    private void done(CompressResult result, Path output) {
        String images = result.imagesFound() == 0 ? "The PDF has no images to recompress."
                : "Images recompressed: " + result.imagesRecompressed() + " of " + result.imagesFound() + ".";
        if (result.bytesAfter() < result.bytesBefore()) {
            runBarController.success("Done: " + Formats.compression(result) + ". " + images, output, null);
        } else {
            runBarController.success("The PDF was already well optimized: " + Formats.compression(result) + ". "
                    + images + " Try \"Smallest size\".", output, null);
        }
    }
}
