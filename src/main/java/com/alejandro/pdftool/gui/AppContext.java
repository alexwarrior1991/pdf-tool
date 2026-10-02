package com.alejandro.pdftool.gui;

import javafx.application.HostServices;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.prefs.Preferences;

/**
 * Servicios compartidos por todas las pantallas. Los controladores FXML que tienen un constructor
 * {@code (AppContext)} lo reciben automáticamente (ver {@link #loader(String)}).
 */
public final class AppContext {

    private static final String LAST_DIRECTORY = "lastDirectory";

    private final Stage stage;
    private final HostServices hostServices;
    /** Las operaciones se ejecutan de una en una, fuera del hilo de la interfaz. */
    private final ExecutorService operations = Executors.newSingleThreadExecutor(daemon("pdftool-operacion"));
    /** Miniaturas y resúmenes de archivos: no deben esperar a que acabe una operación larga. */
    private final ExecutorService previews = Executors.newSingleThreadExecutor(daemon("pdftool-vista-previa"));
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private Path lastDirectory;

    public AppContext(Stage stage, HostServices hostServices) {
        this.stage = stage;
        this.hostServices = hostServices;
        this.lastDirectory = loadLastDirectory();
    }

    /** Cargador de un FXML de {@code com/alejandro/pdftool/gui/} cuyos controladores reciben este contexto. */
    public FXMLLoader loader(String fxml) {
        URL url = AppContext.class.getResource(fxml);
        if (url == null) {
            throw new IllegalArgumentException("No existe la vista " + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(this::createController);
        return loader;
    }

    private Object createController(Class<?> type) {
        try {
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1 && parameters[0] == AppContext.class) {
                    return constructor.newInstance(this);
                }
            }
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se puede crear el controlador " + type.getName(), e);
        }
    }

    public Stage stage() {
        return stage;
    }

    public ExecutorService operations() {
        return operations;
    }

    public ExecutorService previews() {
        return previews;
    }

    /** {@code true} mientras se ejecuta una operación (solo se permite una a la vez). */
    public BooleanProperty busyProperty() {
        return busy;
    }

    public boolean isBusy() {
        return busy.get();
    }

    /** Carpeta inicial de los diálogos de archivos: la última usada (se recuerda entre sesiones). */
    public File initialDirectory() {
        return lastDirectory != null && Files.isDirectory(lastDirectory) ? lastDirectory.toFile() : null;
    }

    public void rememberDirectory(Path fileOrDirectory) {
        if (fileOrDirectory == null) return;
        Path dir = Files.isDirectory(fileOrDirectory) ? fileOrDirectory : fileOrDirectory.toAbsolutePath().getParent();
        if (dir == null) return;
        lastDirectory = dir;
        try {
            Preferences.userNodeForPackage(AppContext.class).put(LAST_DIRECTORY, dir.toString());
        } catch (RuntimeException ignored) {
            // sin preferencias persistentes: se recuerda solo durante esta sesión
        }
    }

    /** Abre un archivo o carpeta con la aplicación predeterminada del sistema. */
    public void open(Path path) {
        if (hostServices != null && path != null) {
            hostServices.showDocument(path.toAbsolutePath().toUri().toString());
        }
    }

    public void shutdown() {
        operations.shutdownNow();
        previews.shutdownNow();
    }

    private static Path loadLastDirectory() {
        try {
            String value = Preferences.userNodeForPackage(AppContext.class).get(LAST_DIRECTORY, null);
            return value == null ? null : Path.of(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
