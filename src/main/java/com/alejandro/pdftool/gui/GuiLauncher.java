package com.alejandro.pdftool.gui;

import javafx.application.Application;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Punto de entrada de la interfaz gráfica. Es una clase aparte de {@link PdfToolApp} (que extiende
 * {@link Application}) para que el JAR ejecutable funcione sin configurar módulos de JavaFX, y para que la línea
 * de comandos nunca cargue JavaFX.
 */
public final class GuiLauncher {

    /** Referencia fija: si el logger se liberase, se perdería el filtro. */
    private static final Logger JAVAFX_LOGGER = Logger.getLogger("javafx");

    private GuiLauncher() {
    }

    /** En Windows y macOS siempre hay escritorio; en Linux hace falta un servidor gráfico. */
    public static boolean isGraphicalEnvironmentAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win") || os.contains("mac")) {
            return true;
        }
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }

    /**
     * Abre la ventana y espera a que se cierre.
     *
     * @return {@code false} si no se ha podido abrir la interfaz
     */
    public static boolean launch(String[] args) {
        if (!isGraphicalEnvironmentAvailable()) {
            return false;
        }
        // PDFBox usa Java2D para dibujar miniaturas; en modo headless no arranca el toolkit AWT junto a JavaFX
        System.setProperty("java.awt.headless", "true");
        // Con el JAR "fat" JavaFX se carga desde el classpath y siempre avisa de ello: es inofensivo
        JAVAFX_LOGGER.setFilter(rec -> rec.getMessage() == null
                || !rec.getMessage().startsWith("Unsupported JavaFX configuration"));
        try {
            Application.launch(PdfToolApp.class, args);
            return true;
        } catch (RuntimeException | LinkageError e) {
            System.err.println("Could not open the graphical interface: " + e);
            System.err.println("If the JAR was built on another operating system, rebuild it on this one with \"mvn package\".");
            return false;
        }
    }
}
