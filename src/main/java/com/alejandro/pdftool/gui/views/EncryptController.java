package com.alejandro.pdftool.gui.views;

import com.alejandro.pdftool.PdfOps;
import com.alejandro.pdftool.PdfPermission;
import com.alejandro.pdftool.gui.AppContext;
import com.alejandro.pdftool.gui.components.FileFieldController;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.FlowPane;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Pantalla «Proteger con contraseña» ({@code encrypt.fxml}). */
public class EncryptController extends OperationView {

    @FXML
    private FileFieldController inputController;
    @FXML
    private PasswordField userPassword;
    @FXML
    private PasswordField userPasswordRepeat;
    @FXML
    private PasswordField ownerPassword;
    @FXML
    private PasswordField ownerPasswordRepeat;
    @FXML
    private FlowPane permissionsPane;
    @FXML
    private FileFieldController outputController;

    private final Map<PdfPermission, CheckBox> permissionChecks = new EnumMap<>(PdfPermission.class);

    public EncryptController(AppContext context) {
        super(context);
    }

    @FXML
    private void initialize() {
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF to protect");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Path of the protected PDF");
        runBarController.setText("Protect PDF");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "protected", "pdf");
        for (PdfPermission permission : PdfPermission.values()) {
            CheckBox check = new CheckBox(permission.label());
            check.setSelected(permission == PdfPermission.PRINT);
            check.setMinWidth(270);
            permissionChecks.put(permission, check);
            permissionsPane.getChildren().add(check);
        }
    }

    private void run() {
        Path input = inputFile(inputController, "Choose the PDF to protect.");
        if (input == null) return;
        String user = userPassword.getText();
        String owner = ownerPassword.getText();
        if (!user.equals(userPasswordRepeat.getText())) {
            invalid("The two open passwords don't match.");
            return;
        }
        if (owner.isEmpty()) {
            invalid("Enter the owner password: it lets you change the permissions or remove the protection.");
            return;
        }
        if (!owner.equals(ownerPasswordRepeat.getText())) {
            invalid("The two owner passwords don't match.");
            return;
        }
        if (owner.equals(user)) {
            invalid("The owner password must be different from the open password; otherwise anyone who opens the PDF "
                    + "would have full permissions.");
            return;
        }
        Path output = outputFile(outputController, "Choose where to save the protected PDF.");
        if (output == null) return;
        Set<PdfPermission> permissions = EnumSet.noneOf(PdfPermission.class);
        permissionChecks.forEach((permission, check) -> {
            if (check.isSelected()) permissions.add(permission);
        });
        runBarController.start("Protecting…",
                progress -> {
                    PdfOps.encrypt(input, output, owner, user, permissions);
                    return output;
                },
                result -> {
                    runBarController.success("PDF protected with AES-256: \"" + fileName(output) + "\". Keep the owner "
                            + "password safe: without it, the protection can't be removed.", output, null);
                    userPassword.clear();
                    userPasswordRepeat.clear();
                    ownerPassword.clear();
                    ownerPasswordRepeat.clear();
                    if (sameFile(input, output)) inputController.refresh();
                });
    }
}
