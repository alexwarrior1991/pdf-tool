package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.DocumentMetadata;
import com.alejandro.pdftool.ErrorMessages;
import com.alejandro.pdftool.Formats;
import com.alejandro.pdftool.PageRenderer;
import com.alejandro.pdftool.PdfInfo;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import com.alejandro.pdftool.gui.util.FxImages;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/** Pantalla «Información y metadatos» ({@code info.fxml}). */
public class InfoController extends OperationView {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Formats.SPANISH);

    @FXML
    private FileFieldController inputController;
    @FXML
    private VBox detailsBox;
    @FXML
    private Label pagesValue;
    @FXML
    private Label pageSizeValue;
    @FXML
    private Label fileSizeValue;
    @FXML
    private Label versionValue;
    @FXML
    private Label securityValue;
    @FXML
    private Label creatorValue;
    @FXML
    private Label producerValue;
    @FXML
    private Label createdValue;
    @FXML
    private Label modifiedValue;
    @FXML
    private ImageView coverView;
    @FXML
    private Label loadStatus;
    @FXML
    private TextField titleField;
    @FXML
    private TextField authorField;
    @FXML
    private TextField subjectField;
    @FXML
    private TextField keywordsField;
    @FXML
    private FileFieldController outputController;

    private int generation;

    public InfoController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF que quieres consultar o editar");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Ruta del PDF con los metadatos nuevos");
        runBarController.setText("Guardar metadatos");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "editado", "pdf");
        inputController.pathProperty().addListener((obs, old, path) -> load(path));
        showDetails(false);
    }

    private void load(Path path) {
        int current = ++generation;
        coverView.setImage(null);
        showDetails(false); // no mostrar (ni dejar editar) datos del PDF anterior mientras se lee el nuevo
        if (path == null || !Files.isRegularFile(path)) {
            loadStatus.setText("");
            return;
        }
        loadStatus.setText("Leyendo…");
        context.previews().submit(() -> {
            PdfInfo info;
            try {
                info = PdfOps.info(path);
            } catch (Exception | Error e) {
                String message = ErrorMessages.describe(e);
                Platform.runLater(() -> {
                    if (current == generation) {
                        showDetails(false);
                        loadStatus.setText(message);
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (current == generation) show(info);
            });
            if (info.pages() == 0) return;
            try (PageRenderer renderer = PageRenderer.open(path)) {
                Image cover = FxImages.toFxImage(renderer.renderToWidth(0, 300));
                Platform.runLater(() -> {
                    if (current == generation) coverView.setImage(cover);
                });
            } catch (Exception | Error ignored) {
                // sin portada: los datos del documento siguen siendo válidos
            }
        });
    }

    private void show(PdfInfo info) {
        loadStatus.setText("");
        pagesValue.setText(String.valueOf(info.pages()));
        pageSizeValue.setText(info.pages() == 0 ? "—" : Formats.pageSize(info.pageWidth(), info.pageHeight())
                + " (primera página)");
        fileSizeValue.setText(Formats.bytes(info.fileSize()));
        versionValue.setText("PDF " + info.version());
        securityValue.setText(!info.encrypted() ? "Sin protección"
                : "Protegido. Permite: " + (info.permissions().isEmpty() ? "nada (solo verlo)"
                : info.permissions().stream().map(p -> p.label().toLowerCase(Formats.SPANISH))
                .collect(Collectors.joining(", "))));
        creatorValue.setText(orDash(info.creator()));
        producerValue.setText(orDash(info.producer()));
        createdValue.setText(date(info.created()));
        modifiedValue.setText(date(info.modified()));
        DocumentMetadata md = info.metadata();
        titleField.setText(orEmpty(md.title()));
        authorField.setText(orEmpty(md.author()));
        subjectField.setText(orEmpty(md.subject()));
        keywordsField.setText(orEmpty(md.keywords()));
        showDetails(true);
    }

    private void showDetails(boolean visible) {
        detailsBox.setVisible(visible);
        detailsBox.setManaged(visible);
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF cuyos metadatos quieres cambiar.");
        if (input == null) return;
        Path output = outputFile(outputController, "Indica dónde guardar el PDF con los metadatos nuevos.");
        if (output == null) return;
        DocumentMetadata metadata = new DocumentMetadata(titleField.getText(), authorField.getText(),
                subjectField.getText(), keywordsField.getText());
        runBarController.start("Guardando metadatos…",
                progress -> PdfOps.updateMetadata(input, output, metadata),
                complete -> {
                    if (complete) {
                        runBarController.success("Metadatos guardados en «" + fileName(output) + "».", output, null);
                    } else {
                        runBarController.warning("Metadatos guardados en «" + fileName(output) + "», pero el PDF tenía "
                                + "metadatos XMP ilegibles que se han dejado como estaban: algunos programas podrían "
                                + "seguir mostrando los datos antiguos.");
                    }
                    if (sameFile(input, output)) {
                        inputController.refresh();
                        load(input);
                    }
                });
    }

    private static String date(ZonedDateTime value) {
        return value == null ? "—" : DATE.format(value);
    }

    private static String orDash(String value) {
        return value == null ? "—" : value;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
