package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;

import java.nio.file.Path;

/** Pantalla «Quitar contraseña» ({@code decrypt.fxml}). */
public class DecryptController extends OperationView {

    @FXML
    private FileFieldController inputController;
    @FXML
    private PasswordField passwordField;
    @FXML
    private FileFieldController outputController;

    public DecryptController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF protegido");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Ruta del PDF sin protección");
        runBarController.setText("Quitar protección");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "desprotegido", "pdf");
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF protegido.");
        if (input == null) return;
        String password = passwordField.getText();
        if (password.isEmpty()) {
            invalid("Escribe la contraseña de propietario del PDF.");
            return;
        }
        Path output = outputFile(outputController, "Indica dónde guardar el PDF sin protección.");
        if (output == null) return;
        runBarController.start("Quitando la protección…",
                progress -> {
                    PdfOps.decrypt(input, output, password);
                    return output;
                },
                result -> {
                    passwordField.clear();
                    runBarController.success("Protección eliminada: «" + fileName(output) + "».", output, null);
                });
    }
}
