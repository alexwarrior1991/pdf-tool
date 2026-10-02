package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.nio.file.Path;

/** Pantalla «Marca de agua» ({@code watermark.fxml}). */
public class WatermarkController extends OperationView {

    /** Tamaño de la hoja de la vista previa (proporción A4). */
    private static final double PREVIEW_WIDTH = 168;

    @FXML
    private FileFieldController inputController;
    @FXML
    private TextField textField;
    @FXML
    private Slider opacitySlider;
    @FXML
    private Label opacityLabel;
    @FXML
    private ColorPicker colorPicker;
    @FXML
    private Text previewText;
    @FXML
    private FileFieldController outputController;

    public WatermarkController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF al que quieres añadir la marca");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Ruta del PDF que se va a crear");
        runBarController.setText("Añadir marca de agua");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "marca_agua", "pdf");

        java.awt.Color defaultColor = PdfOps.DEFAULT_WATERMARK_COLOR;
        colorPicker.setValue(Color.rgb(defaultColor.getRed(), defaultColor.getGreen(), defaultColor.getBlue()));
        opacitySlider.setValue(PdfOps.DEFAULT_WATERMARK_OPACITY * 100);
        opacitySlider.valueProperty().addListener((obs, old, value) -> updatePreview());
        colorPicker.valueProperty().addListener((obs, old, value) -> updatePreview());
        textField.textProperty().addListener((obs, old, value) -> updatePreview());
        updatePreview();
    }

    /** Vista previa aproximada: mismo ángulo, color y opacidad; el tamaño se ajusta como en el PDF. */
    private void updatePreview() {
        String text = textField.getText().isBlank() ? " " : textField.getText().strip();
        opacityLabel.setText(Math.round(opacitySlider.getValue()) + " %");
        previewText.setText(text);
        previewText.setFill(colorPicker.getValue());
        previewText.setOpacity(Math.max(0.05, opacitySlider.getValue() / 100.0));
        // Igual que en PageStamper: el texto girado ocupa como mucho el 90 % del lado menor, hasta 64 pt (A4 = 595 pt)
        double scale = PREVIEW_WIDTH / 595.0;
        Text probe = new Text(text);
        probe.setFont(Font.font("System", FontWeight.BOLD, 100));
        double unitWidth = probe.getLayoutBounds().getWidth() / 100.0;
        double size = Math.min(64, 0.9 * 595 / ((unitWidth + 0.72) * Math.cos(Math.toRadians(45))));
        previewText.setFont(Font.font("System", FontWeight.BOLD, Math.max(8, size) * scale));
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF al que añadir la marca de agua.");
        if (input == null) return;
        String text = textField.getText().strip();
        if (text.isEmpty()) {
            invalid("Escribe el texto de la marca de agua.");
            return;
        }
        Path output = outputFile(outputController, "Indica dónde guardar el PDF resultante.");
        if (output == null) return;
        float opacity = (float) (Math.round(opacitySlider.getValue()) / 100.0);
        Color c = colorPicker.getValue();
        java.awt.Color color = new java.awt.Color((float) c.getRed(), (float) c.getGreen(), (float) c.getBlue());
        runBarController.start("Añadiendo la marca de agua…",
                progress -> {
                    PdfOps.watermarkText(input, output, text, opacity, color, progress);
                    return output;
                },
                result -> runBarController.success("Marca de agua añadida: «" + fileName(output) + "».", output, null));
    }
}
