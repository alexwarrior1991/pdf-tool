package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.PageNumberOptions;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

import java.nio.file.Path;
import java.util.List;

/** Pantalla «Numerar páginas» ({@code page-numbers.fxml}). */
public class PageNumbersController extends OperationView {

    private static final double MM_TO_PT = 72.0 / 25.4;

    @FXML
    private FileFieldController inputController;
    @FXML
    private ComboBox<String> formatBox;
    @FXML
    private ComboBox<PageNumberOptions.Position> positionBox;
    @FXML
    private Spinner<Integer> sizeSpinner;
    @FXML
    private Spinner<Integer> marginSpinner;
    @FXML
    private Spinner<Integer> startSpinner;
    @FXML
    private RadioButton allPages;
    @FXML
    private RadioButton somePages;
    @FXML
    private TextField rangesField;
    @FXML
    private Label exampleLabel;
    @FXML
    private FileFieldController outputController;

    public PageNumbersController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to number");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the PDF to create");
        runBarController.setText("Number pages");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "numbered", "pdf");

        formatBox.getItems().setAll(PageNumberOptions.DEFAULT_FORMAT, "{n} / {total}", "{n}", "- {n} -", "p. {n}");
        formatBox.setValue(PageNumberOptions.DEFAULT_FORMAT);
        positionBox.getItems().setAll(PageNumberOptions.Position.values());
        positionBox.setValue(PageNumberOptions.Position.BOTTOM_CENTER);
        rangesField.disableProperty().bind(somePages.selectedProperty().not());

        formatBox.getEditor().textProperty().addListener((obs, old, value) -> updateExample());
        startSpinner.valueProperty().addListener((obs, old, value) -> updateExample());
        rangesField.textProperty().addListener((obs, old, value) -> updateExample());
        allPages.selectedProperty().addListener((obs, old, value) -> updateExample());
        inputController.pageCountProperty().addListener((obs, old, value) -> updateExample());
        updateExample();
    }

    private String format() {
        String typed = formatBox.getEditor().getText();
        return typed == null || typed.isBlank() ? PageNumberOptions.DEFAULT_FORMAT : typed;
    }

    /** Ejemplo con el primer número que se escribirá. */
    private void updateExample() {
        int pages = inputController.pageCountProperty().get();
        int numbered = pages;
        if (pages > 0 && somePages.isSelected()) {
            try {
                numbered = CliUtil.resolvePages(CliUtil.parseRanges(rangesField.getText()), pages).size();
            } catch (IllegalArgumentException e) {
                numbered = -1;
            }
        }
        int first = startSpinner.getValue();
        String total = numbered > 0 ? String.valueOf(first + numbered - 1) : "N";
        exampleLabel.setText("The first one will look like: \"" + format().replace("{n}", String.valueOf(first))
                .replace("{total}", total) + "\"");
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF to number.");
        if (input == null) return;
        List<CliUtil.PageRange> ranges = allPages.isSelected() ? List.of()
                : ranges(rangesField, true, "Enter which pages to number, e.g. 2-* to skip the cover.");
        if (ranges == null) return;
        Integer size = spinnerValue(sizeSpinner, "The font size");
        if (size == null) return;
        Integer margin = spinnerValue(marginSpinner, "The margin");
        if (margin == null) return;
        Integer start = spinnerValue(startSpinner, "The starting number");
        if (start == null) return;
        PageNumberOptions options;
        try {
            options = new PageNumberOptions(format(), positionBox.getValue(), size, (float) (margin * MM_TO_PT),
                    start, ranges);
        } catch (IllegalArgumentException e) {
            invalid(e.getMessage());
            return;
        }
        Path output = outputFile(outputController, "Choose where to save the resulting PDF.");
        if (output == null) return;
        runBarController.start("Numbering pages…",
                progress -> PdfOps.addPageNumbers(input, output, options, progress),
                numbered -> {
                    runBarController.success("Numbered " + numbered
                            + (numbered == 1 ? " page" : " pages") + ": \"" + fileName(output) + "\".", output, null);
                    if (sameFile(input, output)) inputController.refresh();
                });
    }
}
