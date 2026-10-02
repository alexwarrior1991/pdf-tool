package com.alejandro.pdftool.gui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Carga la ventana principal y todas las pantallas FXML con sus controladores: detecta errores que solo aparecen
 * en ejecución (fx:id que no casan, métodos onAction inexistentes, imports que faltan...). Se salta si no hay
 * entorno gráfico (en Linux sin DISPLAY se puede ejecutar con {@code xvfb-run mvn test}).
 */
class GuiSmokeTest {

    private static final List<String> VIEWS = List.of("merge", "split", "pages", "rotate", "compress", "watermark",
            "page-numbers", "images-to-pdf", "pdf-to-images", "text", "info", "encrypt", "decrypt");

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        Assumptions.assumeTrue(GuiLauncher.isGraphicalEnvironmentAvailable(), "No graphical environment");
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        } catch (UnsupportedOperationException noDisplay) {
            Assumptions.abort("JavaFX cannot open the display: " + noDisplay.getMessage());
        }
        started.await(30, TimeUnit.SECONDS);
    }

    @Test
    void mainWindowAndEveryViewLoad() throws Exception {
        List<String> navigationTargets = onFxThread(() -> {
            AppContext context = new AppContext(new Stage(), null);
            Parent main = context.loader("main.fxml").load();
            new Scene(main);
            main.applyCss(); // crea los skins (necesario para buscar dentro del ScrollPane de la barra lateral)
            for (String view : VIEWS) {
                FXMLLoader loader = context.loader(view + ".fxml");
                assertNotNull(loader.load(), view);
                assertNotNull(loader.getController(), view);
            }
            List<String> targets = new ArrayList<>();
            for (Node node : main.lookupAll(".nav-button")) {
                targets.add((String) ((ToggleButton) node).getUserData());
            }
            return targets;
        });

        List<String> expected = new ArrayList<>(List.of("home"));
        expected.addAll(VIEWS);
        expected.sort(null);
        navigationTargets.sort(null);
        assertEquals(expected, navigationTargets, "every screen must have its button in the sidebar");
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(60, TimeUnit.SECONDS);
    }
}
