package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.SessionTrailState;
import com.cryptocarver.service.I18nService;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Owns the save-session prompt and hands accepted sessions to the saved-session coordinator. */
public final class SaveSessionCoordinator {
    private final I18nService i18n;
    private final Supplier<Map<String, Object>> stateCapture;
    private final Supplier<SessionTrailState> trailStateSupplier;
    private final Supplier<SavedSessionsCoordinator> savedSessionsSupplier;
    private final BiConsumer<String, String> warningPresenter;

    public SaveSessionCoordinator(I18nService i18n, Supplier<Map<String, Object>> stateCapture,
            Supplier<SessionTrailState> trailStateSupplier,
            Supplier<SavedSessionsCoordinator> savedSessionsSupplier,
            BiConsumer<String, String> warningPresenter) {
        this.i18n = i18n;
        this.stateCapture = stateCapture;
        this.trailStateSupplier = trailStateSupplier;
        this.savedSessionsSupplier = savedSessionsSupplier;
        this.warningPresenter = warningPresenter;
    }

    public void handleSaveSession() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("dialog.saveSession.title"));
        dialog.setHeaderText(i18n.text("dialog.saveSession.header"));
        TextField nameField = new TextField("My Session");
        nameField.setPromptText(i18n.text("dialog.saveSession.prompt"));
        CheckBox includeSecrets = new CheckBox(i18n.text("savedSessions.includeSecrets"));
        includeSecrets.setSelected(false);
        includeSecrets.setDisable(AppSettings.getInstance().getSecretVisibilityProfile()
                == SecretVisibilityProfile.REDACTED);
        long sensitiveCount = stateCapture.get().entrySet().stream()
                .filter(entry -> UiStateSnapshot.holdsSecretValue(entry.getKey(), entry.getValue())).count();
        boolean hasTrail = !trailStateSupplier.get().log().isEmpty();
        if (hasTrail) sensitiveCount++;
        final long secretsCount = sensitiveCount;
        Label secretNotice = new Label(i18n.text("savedSessions.redactedCount", secretsCount));
        secretNotice.setWrapText(true);
        secretNotice.setVisible(secretsCount > 0);
        secretNotice.setManaged(secretsCount > 0);
        Label trailNotice = new Label(i18n.text("savedSessions.redactedTrailNotice"));
        trailNotice.setWrapText(true);
        trailNotice.setVisible(hasTrail);
        trailNotice.setManaged(hasTrail);
        includeSecrets.selectedProperty().addListener((obs, wasSelected, selected) -> {
            secretNotice.setText(selected ? i18n.text("savedSessions.secretsEncrypted")
                    : i18n.text("savedSessions.redactedCount", secretsCount));
            secretNotice.setVisible(selected || secretsCount > 0);
            secretNotice.setManaged(selected || secretsCount > 0);
            trailNotice.setVisible(hasTrail && !selected);
            trailNotice.setManaged(hasTrail && !selected);
        });
        VBox content = new VBox(10, new Label(i18n.text("dialog.saveSession.prompt")), nameField,
                includeSecrets, secretNotice, trailNotice);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.orElse(ButtonType.CANCEL) != ButtonType.OK || nameField.getText().trim().isEmpty()) return;
        char[] password = null;
        if (includeSecrets.isSelected()) {
            PasswordField field = new PasswordField();
            field.setPromptText(i18n.text("savedSessions.passwordPrompt"));
            PasswordField confirmation = new PasswordField();
            confirmation.setPromptText(i18n.text("savedSessions.passwordConfirmPrompt"));
            Dialog<ButtonType> passwordDialog = new Dialog<>();
            passwordDialog.setTitle(i18n.text("savedSessions.passwordTitle"));
            passwordDialog.setHeaderText(i18n.text("savedSessions.passwordRequired"));
            passwordDialog.getDialogPane().setContent(new VBox(8, field, confirmation));
            passwordDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            boolean accepted = passwordDialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
            password = field.getText().toCharArray();
            char[] repeated = confirmation.getText().toCharArray();
            field.clear();
            confirmation.clear();
            boolean matches = Arrays.equals(password, repeated);
            Arrays.fill(repeated, '\0');
            if (!accepted || !matches || password.length < 8) {
                Arrays.fill(password, '\0');
                if (accepted) warningPresenter.accept(i18n.text("savedSessions.passwordTitle"),
                        i18n.text(matches ? "savedSessions.passwordTooShort" : "savedSessions.passwordMismatch"));
                return;
            }
        }
        savedSessionsSupplier.get().save(nameField.getText(), password);
    }
}
