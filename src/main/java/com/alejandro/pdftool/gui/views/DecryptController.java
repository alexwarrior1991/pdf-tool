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
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "Protected PDF");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the unprotected PDF");
        runBarController.setText("Remove protection");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "unprotected", "pdf");
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the protected PDF.");
        if (input == null) return;
        String password = passwordField.getText();
        if (password.isEmpty()) {
            invalid("Enter the PDF's owner password.");
            return;
        }
        Path output = outputFile(outputController, "Choose where to save the unprotected PDF.");
        if (output == null) return;
        runBarController.start("Removing protection…",
                progress -> {
                    PdfOps.decrypt(input, output, password);
                    return output;
                },
                result -> {
                    passwordField.clear();
                    runBarController.success("Protection removed: \"" + fileName(output) + "\".", output, null);
                    if (sameFile(input, output)) inputController.refresh();
                });
    }
}
