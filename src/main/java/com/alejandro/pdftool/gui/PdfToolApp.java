package com.alejandro.pdftool.gui;

import com.alejandro.pdftool.gui.util.Dialogs;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.util.Objects;

/** Ventana principal de PDF Tool. Se arranca desde {@link GuiLauncher}. */
public class PdfToolApp extends Application {

    private AppContext context;

    @Override
    public void start(Stage stage) throws IOException {
        context = new AppContext(stage, getHostServices());
        Parent root = context.loader("main.fxml").load();
        // tamaño cómodo, sin pasarse de la pantalla (p. ej. portátiles de 1366×768)
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, Math.min(1180, screen.getWidth() * 0.92), Math.min(800, screen.getHeight() * 0.9));
        scene.getStylesheets().add(Objects.requireNonNull(PdfToolApp.class.getResource("app.css")).toExternalForm());

        stage.setTitle("PDF Tool");
        stage.getIcons().addAll(AppIcon.images());
        stage.setMinWidth(Math.min(900, screen.getWidth()));
        stage.setMinHeight(Math.min(600, screen.getHeight()));
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (context.isBusy() && !Dialogs.confirm(stage, "Operation in progress",
                    "An operation is still running. If you exit now, it will be canceled and the output file won't be created.",
                    "Exit anyway")) {
                event.consume();
            }
        });
        stage.show();
        warmUpPdfFonts();
    }

    /**
     * La primera vez que PDFBox usa una fuente estándar busca las fuentes del sistema (puede tardar unos
     * segundos); se hace al arrancar en un hilo propio, para que no retrase miniaturas ni operaciones.
     */
    private static void warmUpPdfFonts() {
        Thread thread = new Thread(() -> {
            try {
                new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            } catch (RuntimeException ignored) {
                // si falla aquí, la operación que la necesite mostrará el error
            }
        }, "pdftool-fuentes");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.shutdown();
        }
    }
}
