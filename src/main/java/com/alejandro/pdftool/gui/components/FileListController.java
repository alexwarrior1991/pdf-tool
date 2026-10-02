package com.alejandro.pdftool.gui.components;

import com.alejandro.pdftool.ErrorMessages;
import com.alejandro.pdftool.InputFiles;
import com.alejandro.pdftool.gui.AppContext;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Lista ordenable de archivos (PDFs o imágenes) con botones y arrastrar y soltar (componente
 * {@code file-list.fxml}). Las carpetas se sustituyen por los archivos que contienen, en orden natural.
 */
public class FileListController {

    public enum Kind {
        PDF("PDF", InputFiles.PDF_EXTENSIONS),
        IMAGES("images", InputFiles.IMAGE_EXTENSIONS);

        private final String noun;
        private final Set<String> extensions;

        Kind(String noun, Set<String> extensions) {
            this.noun = noun;
            this.extensions = extensions;
        }
    }

    @FXML
    private VBox root;
    @FXML
    private ListView<Path> list;
    @FXML
    private Label placeholder;
    @FXML
    private Label countLabel;
    @FXML
    private Button upButton;
    @FXML
    private Button downButton;
    @FXML
    private Button removeButton;
    @FXML
    private Button clearButton;

    private final AppContext context;
    private Kind kind = Kind.PDF;

    public FileListController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setCellFactory(view -> new FileCell());
        list.setPlaceholder(placeholder);
        list.getItems().addListener((ListChangeListener<Path>) change -> updateState());
        list.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change -> updateState());
        list.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                remove();
            }
        });
        root.setOnDragOver(this::onDragOver);
        root.setOnDragDropped(this::onDragDropped);
        updateState();
    }

    public void configure(Kind kind) {
        this.kind = kind;
        placeholder.setText("Drag " + (kind == Kind.PDF ? "PDF files" : "images") + " or folders here,\n"
                + "or use \"Add files…\"");
        updateState();
    }

    public ObservableList<Path> files() {
        return list.getItems();
    }

    @FXML
    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(kind == Kind.PDF ? "Add PDFs" : "Add images");
        chooser.setInitialDirectory(context.initialDirectory());
        List<String> patterns = new ArrayList<>();
        for (String extension : kind.extensions) {
            patterns.add("*." + extension);
            patterns.add("*." + extension.toUpperCase());
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                kind == Kind.PDF ? "PDF documents" : "Images (JPG, PNG, GIF, BMP, TIFF)", patterns));
        List<File> chosen = chooser.showOpenMultipleDialog(context.stage());
        if (chosen != null && !chosen.isEmpty()) {
            List<Path> paths = chosen.stream().map(File::toPath).sorted(InputFiles.NATURAL_ORDER).toList();
            add(paths);
            context.rememberDirectory(paths.get(0));
        }
    }

    @FXML
    private void addFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Add folder");
        chooser.setInitialDirectory(context.initialDirectory());
        File folder = chooser.showDialog(context.stage());
        if (folder != null) {
            add(List.of(folder.toPath()));
            context.rememberDirectory(folder.toPath());
        }
    }

    /** Añade archivos y el contenido de carpetas; ignora lo que no es del tipo esperado. */
    public void add(List<Path> paths) {
        List<Path> accepted = new ArrayList<>();
        try {
            for (Path path : InputFiles.expand(paths, kind.extensions, null)) {
                if (Files.isRegularFile(path) && InputFiles.hasExtension(path, kind.extensions)) {
                    accepted.add(path);
                }
            }
        } catch (IOException e) {
            countLabel.setText(ErrorMessages.describe(e));
            return;
        }
        if (accepted.isEmpty()) {
            countLabel.setText("No " + kind.noun + " found in what you added.");
            return;
        }
        list.getItems().addAll(accepted);
    }

    @FXML
    private void moveUp() {
        move(-1);
    }

    @FXML
    private void moveDown() {
        move(1);
    }

    private void move(int delta) {
        List<Integer> selected = new ArrayList<>(list.getSelectionModel().getSelectedIndices());
        if (selected.isEmpty()) return;
        selected.sort(delta < 0 ? Comparator.naturalOrder() : Comparator.reverseOrder());
        ObservableList<Path> items = list.getItems();
        if (selected.get(0) + delta < 0 || selected.get(0) + delta >= items.size()) return;
        List<Integer> moved = new ArrayList<>();
        for (int index : selected) {
            Path item = items.remove(index);
            items.add(index + delta, item);
            moved.add(index + delta);
        }
        list.getSelectionModel().clearSelection();
        moved.forEach(i -> list.getSelectionModel().select(i));
        list.scrollTo(moved.get(0));
    }

    @FXML
    private void remove() {
        List<Integer> selected = new ArrayList<>(list.getSelectionModel().getSelectedIndices());
        selected.sort(Comparator.reverseOrder());
        for (int index : selected) {
            list.getItems().remove(index);
        }
    }

    @FXML
    private void clear() {
        list.getItems().clear();
    }

    private void updateState() {
        int count = list.getItems().size();
        boolean noSelection = list.getSelectionModel().getSelectedIndices().isEmpty();
        upButton.setDisable(noSelection);
        downButton.setDisable(noSelection);
        removeButton.setDisable(noSelection);
        clearButton.setDisable(count == 0);
        countLabel.setText(count == 0 ? "" : count + (count == 1 ? " file" : " files")
                + " · processed in this order (use ▲ ▼ to change it)");
    }

    private void onDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        boolean ok = event.getDragboard().hasFiles();
        if (ok) {
            List<Path> dropped = event.getDragboard().getFiles().stream().map(File::toPath).toList();
            add(dropped);
            context.rememberDirectory(dropped.get(0));
        }
        event.setDropCompleted(ok);
        event.consume();
    }

    /** Celda con el nombre del archivo y, debajo, su carpeta. */
    private static final class FileCell extends ListCell<Path> {
        private final Label name = new Label();
        private final Label folder = new Label();
        private final VBox box = new VBox(1, name, folder);

        FileCell() {
            name.getStyleClass().add("file-name");
            folder.getStyleClass().add("file-folder");
        }

        @Override
        protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                name.setText(String.valueOf(item.getFileName()));
                folder.setText(String.valueOf(item.toAbsolutePath().getParent()));
                setGraphic(box);
                setText(null);
            }
        }
    }
}
