package com.alejandro.pdftool.gui.components;

import com.alejandro.pdftool.ErrorMessages;
import com.alejandro.pdftool.Formats;
import com.alejandro.pdftool.InputFiles;
import com.alejandro.pdftool.PdfInfo;
import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.util.OutputNames;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Campo de ruta con botón «Examinar…» y soporte para arrastrar y soltar (componente {@code file-field.fxml}).
 */
public class FileFieldController {

    public enum Mode { OPEN_PDF, SAVE_PDF, SAVE_TEXT, DIRECTORY }

    @FXML
    private VBox root;
    @FXML
    private TextField pathField;
    @FXML
    private Button browseButton;
    @FXML
    private Label summaryLabel;

    private final AppContext context;
    private final ObjectProperty<Path> path = new SimpleObjectProperty<>();
    private final IntegerProperty pageCount = new SimpleIntegerProperty(-1);
    private Mode mode = Mode.OPEN_PDF;
    /** El usuario ha escrito o elegido la ruta: ya no se sustituye por sugerencias. */
    private boolean userChosen;
    /** Elegida en el diálogo nativo, que ya pregunta antes de sobrescribir. */
    private boolean confirmedByDialog;
    private boolean settingText;
    private int summaryGeneration;

    public FileFieldController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        pathField.textProperty().addListener((obs, old, text) -> {
            path.set(parse(text));
            if (!settingText) {
                userChosen = !text.isBlank();
                confirmedByDialog = false;
            }
        });
        path.addListener((obs, old, value) -> refreshSummary(value));
        root.setOnDragOver(this::onDragOver);
        root.setOnDragDropped(this::onDragDropped);
        hideSummary();
    }

    public void configure(Mode mode, String prompt) {
        this.mode = mode;
        pathField.setPromptText(prompt);
    }

    public ReadOnlyObjectProperty<Path> pathProperty() {
        return path;
    }

    public Path getPath() {
        return path.get();
    }

    /** Número de páginas del PDF elegido (modo abrir PDF), o -1 si no se conoce. */
    public ReadOnlyIntegerProperty pageCountProperty() {
        return pageCount;
    }

    public void setPath(Path value) {
        setText(value == null ? "" : value.toString());
        userChosen = value != null;
        confirmedByDialog = false;
    }

    /** {@code true} si el campo tiene texto (aunque no sea una ruta válida). */
    public boolean hasText() {
        return !pathField.getText().isBlank();
    }

    public String getText() {
        return pathField.getText().strip();
    }

    /** Vuelve a leer el resumen (páginas y tamaño), p. ej. después de sobrescribir el archivo. */
    public void refresh() {
        refreshSummary(getPath());
    }

    /** Propone una ruta si el usuario todavía no ha elegido ninguna. */
    public void suggest(Path value) {
        if (!userChosen) {
            setText(value == null ? "" : value.toString());
            confirmedByDialog = false;
        }
    }

    public boolean isConfirmedByDialog() {
        return confirmedByDialog;
    }

    public void requestFocus() {
        pathField.requestFocus();
    }

    @FXML
    private void browse() {
        File initialDir = Optional.ofNullable(getPath())
                .map(p -> Files.isDirectory(p) ? p : p.toAbsolutePath().getParent())
                .filter(Files::isDirectory)
                .map(Path::toFile)
                .orElseGet(context::initialDirectory);
        Path chosen = switch (mode) {
            case DIRECTORY -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Elegir carpeta");
                chooser.setInitialDirectory(initialDir);
                yield toPath(chooser.showDialog(context.stage()));
            }
            case OPEN_PDF -> {
                FileChooser chooser = fileChooser("Abrir PDF", initialDir, "pdf");
                yield toPath(chooser.showOpenDialog(context.stage()));
            }
            case SAVE_PDF, SAVE_TEXT -> {
                String extension = mode == Mode.SAVE_PDF ? "pdf" : "txt";
                FileChooser chooser = fileChooser("Guardar como", initialDir, extension);
                if (getPath() != null && getPath().getFileName() != null) {
                    chooser.setInitialFileName(getPath().getFileName().toString());
                }
                yield toPath(chooser.showSaveDialog(context.stage()));
            }
        };
        if (chosen != null) {
            boolean saving = mode == Mode.SAVE_PDF || mode == Mode.SAVE_TEXT;
            Path target = saving ? OutputNames.withExtension(chosen, mode == Mode.SAVE_PDF ? "pdf" : "txt") : chosen;
            setPath(target);
            // El diálogo ya preguntó por el nombre que se escribió; si hemos añadido la extensión (Linux no lo
            // hace solo) es otro archivo y se preguntará antes de reemplazarlo.
            confirmedByDialog = saving && target.equals(chosen);
            context.rememberDirectory(target);
        }
    }

    private FileChooser fileChooser(String title, File initialDir, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialDirectory(initialDir);
        FileChooser.ExtensionFilter filter = extension.equals("pdf")
                ? new FileChooser.ExtensionFilter("Documentos PDF (*.pdf)", "*.pdf", "*.PDF")
                : new FileChooser.ExtensionFilter("Texto (*.txt)", "*.txt");
        chooser.getExtensionFilters().addAll(filter, new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        return chooser;
    }

    private void onDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles() && pick(event.getDragboard().getFiles()) != null) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        Path dropped = event.getDragboard().hasFiles() ? pick(event.getDragboard().getFiles()) : null;
        if (dropped != null) {
            setPath(dropped);
            context.rememberDirectory(dropped);
        }
        event.setDropCompleted(dropped != null);
        event.consume();
    }

    /** Del conjunto arrastrado, la primera ruta que tenga sentido para este campo. */
    private Path pick(List<File> files) {
        for (File file : files) {
            Path p = file.toPath();
            switch (mode) {
                case OPEN_PDF -> {
                    if (Files.isRegularFile(p) && InputFiles.hasExtension(p, InputFiles.PDF_EXTENSIONS)) return p;
                }
                case DIRECTORY -> {
                    if (Files.isDirectory(p)) return p;
                }
                case SAVE_PDF, SAVE_TEXT -> {
                    if (!Files.isDirectory(p)) return p;
                }
            }
        }
        return null;
    }

    private void refreshSummary(Path value) {
        int generation = ++summaryGeneration;
        pageCount.set(-1);
        if (mode != Mode.OPEN_PDF || value == null || !Files.isRegularFile(value)) {
            hideSummary();
            return;
        }
        showSummary("Leyendo…", false);
        context.previews().submit(() -> {
            String text;
            boolean warning;
            int pages = -1;
            try {
                PdfInfo info = PdfOps.info(value);
                pages = info.pages();
                text = info.pages() + (info.pages() == 1 ? " página" : " páginas") + " · " + Formats.bytes(info.fileSize())
                        + (info.encrypted() ? " · con restricciones de seguridad" : "");
                warning = info.encrypted();
            } catch (Exception | Error e) {
                text = ErrorMessages.describe(e);
                warning = true;
            }
            String summary = text;
            boolean isWarning = warning;
            int count = pages;
            Platform.runLater(() -> {
                if (generation == summaryGeneration) {
                    pageCount.set(count);
                    showSummary(summary, isWarning);
                }
            });
        });
    }

    private void showSummary(String text, boolean warning) {
        summaryLabel.setText(text);
        summaryLabel.getStyleClass().remove("field-warning");
        if (warning) summaryLabel.getStyleClass().add("field-warning");
        summaryLabel.setManaged(true);
        summaryLabel.setVisible(true);
    }

    private void hideSummary() {
        summaryLabel.setManaged(false);
        summaryLabel.setVisible(false);
    }

    private void setText(String text) {
        settingText = true;
        try {
            pathField.setText(text);
            pathField.positionCaret(text.length());
        } finally {
            settingText = false;
        }
    }

    private static Path parse(String text) {
        if (text == null || text.isBlank()) return null;
        String value = text.strip();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).strip();
        }
        try {
            return value.isEmpty() ? null : Path.of(value);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static Path toPath(File file) {
        return file == null ? null : file.toPath();
    }

}
