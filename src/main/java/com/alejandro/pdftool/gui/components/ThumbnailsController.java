package com.alejandro.pdftool.gui.components;

import com.alejandro.pdftool.CliUtil;
import com.alejandro.pdftool.ErrorMessages;
import com.alejandro.pdftool.PageRenderer;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.util.FxImages;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Miniaturas de las páginas de un PDF (componente {@code thumbnails.fxml}). Se dibujan en segundo plano y
 * opcionalmente permiten seleccionar páginas con un clic, sincronizadas con un campo de rangos.
 */
public class ThumbnailsController {

    /** Cómo se marcan las páginas seleccionadas. */
    public enum SelectionStyle { HIGHLIGHT, REMOVE }

    private static final int MAX_PAGES = 300;
    private static final int THUMB_SIZE = 116;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass REMOVED = PseudoClass.getPseudoClass("removed");

    @FXML
    private VBox root;
    @FXML
    private Label statusLabel;
    @FXML
    private Hyperlink selectAllLink;
    @FXML
    private Hyperlink selectNoneLink;
    @FXML
    private FlowPane pages;

    private final AppContext context;
    private final ObservableSet<Integer> selection = FXCollections.observableSet(new TreeSet<>());
    private final IntegerProperty pageCount = new SimpleIntegerProperty(-1);
    private final AtomicInteger generation = new AtomicInteger();
    private final List<Thumb> thumbs = new ArrayList<>();
    private boolean selectable;
    private SelectionStyle selectionStyle = SelectionStyle.HIGHLIGHT;
    private int previewRotation;
    private boolean rotateAllPages = true;
    private TextField rangesField;
    private boolean syncing;

    private record Thumb(int page, VBox node, ImageView image) {
    }

    public ThumbnailsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        selection.addListener((SetChangeListener<Integer>) change -> {
            refreshStyles();
            if (rangesField != null && !syncing) {
                syncing = true;
                rangesField.setText(CliUtil.toRangeSpec(selection));
                syncing = false;
            }
        });
        pageCount.addListener((obs, old, count) -> syncFromField());
        setSelectable(false);
        showStatus("");
    }

    /** Permite seleccionar páginas haciendo clic. */
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
        selectAllLink.setVisible(selectable);
        selectAllLink.setManaged(selectable);
        selectNoneLink.setVisible(selectable);
        selectNoneLink.setManaged(selectable);
    }

    public void setSelectionStyle(SelectionStyle style) {
        this.selectionStyle = style;
        refreshStyles();
    }

    /** Páginas seleccionadas (1-based, ordenadas). */
    public ObservableSet<Integer> selection() {
        return selection;
    }

    public ReadOnlyIntegerProperty pageCountProperty() {
        return pageCount;
    }

    /** Sincroniza la selección con un campo de rangos tipo {@code 1-3,7}, en ambos sentidos. */
    public void bindRangesField(TextField field) {
        this.rangesField = field;
        field.textProperty().addListener((obs, old, text) -> syncFromField());
    }

    /** Gira en la vista previa todas las páginas o solo las seleccionadas. */
    public void setRotationPreview(int degrees, boolean allPages) {
        this.previewRotation = degrees;
        this.rotateAllPages = allPages;
        refreshStyles();
    }

    /** Carga las miniaturas de otro PDF (o vacía la vista si es null). */
    public void load(Path pdf) {
        int current = generation.incrementAndGet();
        thumbs.clear();
        pages.getChildren().clear();
        // el campo de rangos conserva lo escrito: se vuelve a aplicar cuando se conozcan las páginas del nuevo PDF
        syncing = true;
        selection.clear();
        syncing = false;
        pageCount.set(-1);
        if (pdf == null || !Files.isRegularFile(pdf)) {
            showStatus("");
            return;
        }
        showStatus("Cargando vista previa…");
        double scale = Screen.getPrimary().getOutputScaleX();
        int pixelWidth = (int) Math.round(THUMB_SIZE * Math.max(1, scale));
        context.previews().submit(() -> render(pdf, current, pixelWidth));
    }

    private void render(Path pdf, int current, int pixelWidth) {
        try (PageRenderer renderer = PageRenderer.open(pdf)) {
            int total = renderer.pageCount();
            int shown = Math.min(total, MAX_PAGES);
            Platform.runLater(() -> {
                if (current == generation.get()) pageCount.set(total);
            });
            for (int i = 0; i < shown && current == generation.get(); i++) {
                BufferedImage rendered = renderer.renderToWidth(i, pixelWidth);
                Image image = FxImages.toFxImage(rendered);
                int page = i + 1;
                Platform.runLater(() -> {
                    if (current == generation.get()) addThumb(page, image);
                });
            }
            String status = total > shown
                    ? "Vista previa de las primeras " + shown + " de " + total + " páginas"
                    : total + (total == 1 ? " página" : " páginas");
            Platform.runLater(() -> {
                if (current == generation.get()) showStatus(status);
            });
        } catch (Exception | OutOfMemoryError e) {
            String message = "Sin vista previa: " + ErrorMessages.describe(e);
            Platform.runLater(() -> {
                if (current == generation.get()) showStatus(message);
            });
        }
    }

    private void addThumb(int page, Image image) {
        ImageView view = new ImageView(image);
        view.setFitWidth(THUMB_SIZE);
        view.setFitHeight(THUMB_SIZE);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        StackPane frame = new StackPane(view);
        frame.getStyleClass().add("thumbnail-frame");
        frame.setMinSize(THUMB_SIZE + 8, THUMB_SIZE + 8);
        frame.setPrefSize(THUMB_SIZE + 8, THUMB_SIZE + 8);
        Label number = new Label(String.valueOf(page));
        number.getStyleClass().add("thumbnail-label");
        VBox node = new VBox(4, frame, number);
        node.getStyleClass().add("thumbnail");
        node.setOnMouseClicked(event -> {
            if (selectable) {
                if (!selection.remove(page)) selection.add(page);
            }
        });
        Thumb thumb = new Thumb(page, node, view);
        thumbs.add(thumb);
        pages.getChildren().add(node);
        refreshStyle(thumb);
    }

    @FXML
    private void selectAll() {
        int count = pageCount.get();
        if (count > 0) {
            List<Integer> all = new ArrayList<>();
            for (int p = 1; p <= count; p++) all.add(p);
            selection.addAll(all);
        }
    }

    @FXML
    private void selectNone() {
        selection.clear();
    }

    private void syncFromField() {
        if (rangesField == null || syncing || pageCount.get() <= 0) return;
        String text = rangesField.getText();
        List<Integer> wanted;
        try {
            wanted = text == null || text.isBlank() ? List.of()
                    : CliUtil.resolvePages(CliUtil.parseRanges(text), pageCount.get());
        } catch (IllegalArgumentException e) {
            return; // se está escribiendo: se sincroniza cuando el rango sea válido
        }
        syncing = true;
        selection.retainAll(wanted);
        selection.addAll(wanted);
        syncing = false;
    }

    private void refreshStyles() {
        thumbs.forEach(this::refreshStyle);
    }

    private void refreshStyle(Thumb thumb) {
        boolean selected = selection.contains(thumb.page());
        thumb.node().pseudoClassStateChanged(SELECTED, selected && selectionStyle == SelectionStyle.HIGHLIGHT);
        thumb.node().pseudoClassStateChanged(REMOVED, selected && selectionStyle == SelectionStyle.REMOVE);
        boolean rotate = rotateAllPages || selected;
        thumb.image().setRotate(rotate ? previewRotation : 0);
    }

    private void showStatus(String text) {
        statusLabel.setText(text);
    }
}
