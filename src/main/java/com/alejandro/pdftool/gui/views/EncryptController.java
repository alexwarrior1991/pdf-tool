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
        inputController.configure(FileFieldController.Mode.OPEN_PDF, "PDF que quieres proteger");
        outputController.configure(FileFieldController.Mode.SAVE_PDF, "Ruta del PDF protegido");
        runBarController.setText("Proteger PDF");
        runBarController.setOnRun(this::run);
        suggestOutputBesides(inputController, outputController, "protegido", "pdf");
        for (PdfPermission permission : PdfPermission.values()) {
            CheckBox check = new CheckBox(permission.label());
            check.setSelected(permission == PdfPermission.PRINT);
            check.setMinWidth(270);
            permissionChecks.put(permission, check);
            permissionsPane.getChildren().add(check);
        }
    }

    private void run() {
        Path input = inputFile(inputController, "Elige el PDF que quieres proteger.");
        if (input == null) return;
        String user = userPassword.getText();
        String owner = ownerPassword.getText();
        if (!user.equals(userPasswordRepeat.getText())) {
            invalid("Las dos contraseñas de apertura no coinciden.");
            return;
        }
        if (owner.isEmpty()) {
            invalid("Escribe la contraseña de propietario: es la que permite cambiar los permisos o quitar la protección.");
            return;
        }
        if (!owner.equals(ownerPasswordRepeat.getText())) {
            invalid("Las dos contraseñas de propietario no coinciden.");
            return;
        }
        if (owner.equals(user)) {
            invalid("La contraseña de propietario debe ser distinta de la de apertura; si no, quien abra el PDF "
                    + "tendría todos los permisos.");
            return;
        }
        Path output = outputFile(outputController, "Indica dónde guardar el PDF protegido.");
        if (output == null) return;
        Set<PdfPermission> permissions = EnumSet.noneOf(PdfPermission.class);
        permissionChecks.forEach((permission, check) -> {
            if (check.isSelected()) permissions.add(permission);
        });
        runBarController.start("Protegiendo…",
                progress -> {
                    PdfOps.encrypt(input, output, owner, user, permissions);
                    return output;
                },
                result -> {
                    runBarController.success("PDF protegido con AES-256: «" + fileName(output) + "». Guarda bien la "
                            + "contraseña de propietario: sin ella no se puede quitar la protección.", output, null);
                    userPassword.clear();
                    userPasswordRepeat.clear();
                    ownerPassword.clear();
                    ownerPasswordRepeat.clear();
                });
    }
}
