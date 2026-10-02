package com.alejandro.pdftool.gui;

import com.alejandro.pdftool.ErrorMessages;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Ventana principal ({@code main.fxml}): barra lateral, pantalla de inicio y las pantallas de cada operación. */
public class MainController {

    private static final String HOME = "home";

    @FXML
    private ToggleGroup navigation;
    @FXML
    private StackPane content;
    @FXML
    private Node home;

    private final AppContext context;
    private final Map<String, Node> views = new HashMap<>();

    public MainController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        navigation.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                // un clic sobre la opción activa no debe dejar la barra sin selección
                if (previous != null) previous.setSelected(true);
                return;
            }
            show((String) selected.getUserData(), ((ToggleButton) selected).getText());
        });
    }

    /** Clic en una tarjeta de la pantalla de inicio. */
    @FXML
    private void openCard(MouseEvent event) {
        open((String) ((Node) event.getSource()).getUserData());
    }

    /** Abre una pantalla por su identificador (nombre del FXML sin extensión), p. ej. {@code "merge"}. */
    public void open(String id) {
        for (Toggle toggle : navigation.getToggles()) {
            if (id.equals(toggle.getUserData())) {
                navigation.selectToggle(toggle);
                return;
            }
        }
    }

    private void show(String id, String title) {
        Node view = HOME.equals(id) ? home : views.computeIfAbsent(id, this::loadView);
        content.getChildren().setAll(view);
        context.stage().setTitle(HOME.equals(id) ? "PDF Tool" : "PDF Tool — " + title);
    }

    private Node loadView(String id) {
        try {
            return context.loader(id + ".fxml").load();
        } catch (IOException | RuntimeException e) {
            Label error = new Label("No se ha podido abrir esta pantalla: " + ErrorMessages.describe(e));
            error.getStyleClass().add("view-error");
            error.setWrapText(true);
            return error;
        }
    }
}
