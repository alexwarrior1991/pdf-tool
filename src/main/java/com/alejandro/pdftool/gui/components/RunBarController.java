package com.alejandro.pdftool.gui.components;

import com.alejandro.pdftool.ErrorMessages;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.util.Dialogs;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Botón principal de una pantalla, barra de progreso y aviso con el resultado (componente {@code run-bar.fxml}).
 * Ejecuta las operaciones en segundo plano, de una en una para toda la aplicación.
 */
public class RunBarController {

    @FXML
    private VBox root;
    @FXML
    private Button runButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private HBox banner;
    @FXML
    private Label bannerIcon;
    @FXML
    private Label bannerText;
    @FXML
    private Button openFileButton;
    @FXML
    private Button openFolderButton;
    @FXML
    private Hyperlink detailsLink;

    private final AppContext context;
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private Runnable onRun = () -> { };
    private Path resultFile;
    private Path resultFolder;
    private Throwable lastError;

    public RunBarController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        runButton.disableProperty().bind(context.busyProperty());
        setVisible(progressBar, false);
        hideBanner();
    }

    public void setText(String text) {
        runButton.setText(text);
    }

    /** Acción del botón: la pantalla valida el formulario y llama a {@link #start} o a {@link #error(String)}. */
    public void setOnRun(Runnable onRun) {
        this.onRun = Objects.requireNonNull(onRun);
    }

    /** {@code true} mientras se ejecuta la operación lanzada desde esta pantalla. */
    public ReadOnlyBooleanProperty runningProperty() {
        return running;
    }

    @FXML
    private void run() {
        hideBanner();
        onRun.run();
    }

    /** Ejecuta el trabajo en segundo plano mostrando el progreso; al acabar llama a {@code onSuccess}. */
    public <T> void start(String busyText, Work<T> work, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.run((done, total) -> updateProgress(done, total));
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        setVisible(progressBar, true);
        statusLabel.setText(busyText);
        scrollIntoView();
        context.busyProperty().set(true);
        running.set(true);
        task.setOnSucceeded(event -> {
            finish();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            finish();
            error(task.getException());
        });
        context.operations().submit(task);
    }

    private void finish() {
        progressBar.progressProperty().unbind();
        setVisible(progressBar, false);
        statusLabel.setText("");
        context.busyProperty().set(false);
        running.set(false);
    }

    /** Aviso verde con botones para abrir el resultado (cualquiera de las dos rutas puede ser null). */
    public void success(String message, Path file, Path folder) {
        showBanner("banner-success", "✔", message, null);
        resultFile = file;
        resultFolder = folder != null ? folder : file == null ? null : file.toAbsolutePath().getParent();
        setVisible(openFileButton, resultFile != null);
        setVisible(openFolderButton, resultFolder != null);
    }

    /** Aviso amarillo (la operación ha ido bien pero hay algo que conviene saber). */
    public void warning(String message) {
        showBanner("banner-warning", "!", message, null);
    }

    /** Aviso rojo para errores de validación del formulario. */
    public void error(String message) {
        showBanner("banner-error", "✖", message, null);
    }

    /** Aviso rojo con el mensaje comprensible del error y un enlace al detalle técnico. */
    public void error(Throwable error) {
        showBanner("banner-error", "✖", ErrorMessages.describe(error), error);
    }

    /**
     * Pregunta antes de sobrescribir archivos existentes.
     *
     * @return {@code true} si se puede continuar
     */
    public boolean confirmOverwrite(List<Path> targets) {
        List<Path> existing = targets.stream().filter(Objects::nonNull).filter(Files::exists).toList();
        if (existing.isEmpty()) {
            return true;
        }
        String names = existing.stream().limit(5).map(p -> "• " + p.getFileName()).collect(Collectors.joining("\n"));
        String more = existing.size() > 5 ? "\n… y " + (existing.size() - 5) + " más" : "";
        return Dialogs.confirm(context.stage(), "¿Reemplazar archivos?",
                (existing.size() == 1 ? "Este archivo ya existe y se reemplazará:\n" : "Estos archivos ya existen y se reemplazarán:\n")
                        + names + more, "Reemplazar");
    }

    public boolean confirmOverwrite(Path... targets) {
        return confirmOverwrite(Arrays.asList(targets));
    }

    public void hideBanner() {
        setVisible(banner, false);
        resultFile = null;
        resultFolder = null;
        lastError = null;
    }

    private void showBanner(String styleClass, String icon, String message, Throwable error) {
        banner.getStyleClass().removeAll("banner-success", "banner-warning", "banner-error");
        banner.getStyleClass().add(styleClass);
        bannerIcon.setText(icon);
        bannerText.setText(message);
        lastError = error;
        resultFile = null;
        resultFolder = null;
        setVisible(openFileButton, false);
        setVisible(openFolderButton, false);
        setVisible(detailsLink, error != null);
        setVisible(banner, true);
        scrollIntoView();
    }

    /** Desplaza la página para que se vea la barra (progreso o resultado) si ha quedado fuera de la vista. */
    private void scrollIntoView() {
        Platform.runLater(() -> {
            ScrollPane scroll = null;
            for (Node n = root.getParent(); n != null; n = n.getParent()) {
                if (n instanceof ScrollPane s) {
                    scroll = s;
                    break;
                }
            }
            if (scroll == null || !(scroll.getContent() instanceof Parent content)) return;
            // el aviso acaba de mostrarse: hay que recolocar (y redimensionar el contenido) antes de medir
            scroll.applyCss();
            scroll.layout();
            double contentHeight = content.getLayoutBounds().getHeight();
            double viewport = scroll.getViewportBounds().getHeight();
            if (contentHeight <= viewport) return;
            Bounds bounds = content.sceneToLocal(root.localToScene(root.getBoundsInLocal()));
            double top = scroll.getVvalue() * (contentHeight - viewport);
            if (bounds.getMinY() >= top && bounds.getMaxY() <= top + viewport) return;
            double target = Math.max(0, Math.min(1, (bounds.getMaxY() + 24 - viewport) / (contentHeight - viewport)));
            scroll.setVvalue(target);
        });
    }

    @FXML
    private void openFile() {
        context.open(resultFile);
    }

    @FXML
    private void openFolder() {
        context.open(resultFolder);
    }

    @FXML
    private void showDetails() {
        if (lastError != null) {
            Dialogs.details(context.stage(), bannerText.getText(), lastError);
        }
    }

    private static void setVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
