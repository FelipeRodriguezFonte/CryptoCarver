package com.cryptocarver.ui;

import com.cryptocarver.model.OperationSessionLog;
import com.cryptocarver.model.SessionTrailState;
import com.cryptocarver.model.SavedSession;
import com.cryptocarver.model.SavedSessionsManager;
import com.cryptocarver.service.I18nService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the saved-session list and its load/preview presentation. */
public final class SavedSessionsCoordinator {
    private final VBox container;
    private final VBox list;
    private final StatusReporter statusReporter;
    private final SavedSessionsManager manager;
    private final I18nService i18n;
    private final DialogService dialogs;
    private final Supplier<Map<String, Object>> stateCapture;
    private final Consumer<Map<String, Object>> stateRestore;
    private final Consumer<String> operationSelection;
    private final Runnable trailRefresh;
    private final Consumer<Integer> showTrailStep;
    private final SessionTrailState trailState;
    private final Supplier<String> operationSupplier;
    private final Supplier<String> subtitleSupplier;
    private final Supplier<Node> ownerNodeSupplier;
    private final com.cryptocarver.model.SavedSessionCodec codec = new com.cryptocarver.model.SavedSessionCodec();

    public SavedSessionsCoordinator(VBox container, VBox list, StatusReporter statusReporter,
                                    SavedSessionsManager manager, I18nService i18n,
                                    DialogService dialogs,
                                    Supplier<Map<String, Object>> stateCapture,
                                    Consumer<Map<String, Object>> stateRestore,
                                    Consumer<String> operationSelection, Runnable trailRefresh,
                                    Consumer<Integer> showTrailStep,
                                    SessionTrailState trailState,
                                    Supplier<String> operationSupplier, Supplier<String> subtitleSupplier,
                                    Supplier<Node> ownerNodeSupplier) {
        this.container = container;
        this.list = list;
        this.statusReporter = statusReporter;
        this.manager = manager;
        this.i18n = i18n;
        this.dialogs = dialogs;
        this.stateCapture = stateCapture;
        this.stateRestore = stateRestore;
        this.operationSelection = operationSelection;
        this.trailRefresh = trailRefresh;
        this.showTrailStep = showTrailStep;
        this.trailState = trailState;
        this.operationSupplier = operationSupplier;
        this.subtitleSupplier = subtitleSupplier;
        this.ownerNodeSupplier = ownerNodeSupplier;
    }

    public void show() {
        if (container != null) {
            container.setVisible(true);
            container.setManaged(true);
            refresh();
        }
    }

    public void refresh() {
        if (list == null) return;
        list.getChildren().clear();
        List<SavedSession> sessions = manager.getSessions();
        if (manager.hasLegacyPlaintextSecrets()) {
            Label legacyWarning = new Label(i18n.text("savedSessions.legacyWarning"));
            legacyWarning.setWrapText(true);
            Button purge = new Button(i18n.text("savedSessions.removePlaintextSecrets"));
            purge.setOnAction(event -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        i18n.text("savedSessions.removePlaintextConfirm"), ButtonType.CANCEL, ButtonType.OK);
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    manager.removeLegacyPlaintextSecrets();
                    refresh();
                }
            });
            list.getChildren().addAll(legacyWarning, purge);
        }
        if (sessions.isEmpty()) {
            Label placeholder = new Label("No saved sessions");
            placeholder.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-padding: 10;");
            list.getChildren().add(placeholder);
            return;
        }
        for (SavedSession session : sessions) {
            HBox item = new HBox(10);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setStyle("-fx-padding: 10; -fx-background-color: #2d3748; -fx-background-radius: 5; -fx-border-color: #4a5568; -fx-border-radius: 5;");
            VBox info = new VBox(2);
            Label name = new Label(session.getName());
            name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
            OperationSessionLog savedLog = session.getOperationLog();
            String steps = savedLog == null ? "" : " • " + i18n.text("sessionTrail.savedCount", savedLog.size());
            Label details = new Label(session.getTimestamp() + " • " + session.getOperation() + steps);
            details.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px;");
            info.getChildren().addAll(name, details);
            HBox.setHgrow(info, Priority.ALWAYS);
            Button load = new Button(i18n.text("savedSessions.previewAndLoad"));
            load.getStyleClass().add("action-button");
            load.setStyle("-fx-font-size: 11px; -fx-padding: 5 10;");
            load.setOnAction(event -> previewAndLoad(session));
            Button delete = new Button(i18n.text("savedSessions.delete"));
            delete.setAccessibleText(i18n.text("savedSessions.delete"));
            delete.getStyleClass().add("secondary-button");
            delete.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; -fx-text-fill: #fc8181;");
            delete.setOnAction(event -> {
                Node ownerNode = ownerNodeSupplier.get();
                javafx.stage.Window owner = ownerNode == null || ownerNode.getScene() == null
                        ? null : ownerNode.getScene().getWindow();
                if (!dialogs.confirmDestructive(owner, i18n.text("savedSessions.deleteTitle"),
                        i18n.text("savedSessions.deleteConfirm", session.getName()), i18n.text("savedSessions.delete"))) return;
                manager.removeSession(session);
                refresh();
                statusReporter.updateStatus(i18n.text("savedSessions.deleted"));
            });
            item.getChildren().addAll(info, load, delete);
            list.getChildren().add(item);
        }
    }

    public void save(String name) { save(name, null); }

    public void save(String name, char[] password) {
        if (name == null || name.trim().isEmpty()) return;
        String operation = operationSupplier.get();
        if ("Dashboard".equals(operation) || operation == null) {
            String subtitle = subtitleSupplier.get();
            if (subtitle != null) operation = subtitle;
        }
        if (operation == null || operation.isEmpty()) operation = "Generic";
        Map<String, Object> captured = stateCapture.get();
        long redacted = captured == null ? 0 : captured.entrySet().stream()
                .filter(entry -> UiStateSnapshot.holdsSecretValue(entry.getKey(), entry.getValue())).count();
        if (trailState.log() != null && !trailState.log().isEmpty()) redacted++;
        SavedSession source = new SavedSession(name, operation, captured, trailState.log());
        manager.addSession(codec.prepareForStorage(source, password));
        if (redacted > 0) statusReporter.updateStatus(i18n.text("savedSessions.redactedCount", redacted));
        statusReporter.updateStatus("Session saved: " + name);
        if (container != null && container.isVisible()) refresh();
    }

    private void previewAndLoad(SavedSession session) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("savedSessions.previewTitle"));
        dialog.setHeaderText(session.getName());
        Node ownerNode = ownerNodeSupplier.get();
        javafx.stage.Window owner = ownerNode == null || ownerNode.getScene() == null
                ? null : ownerNode.getScene().getWindow();
        if (owner != null) dialog.initOwner(owner);
        ButtonType load = new ButtonType(i18n.text("savedSessions.load"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(load, ButtonType.CANCEL);
        TextArea preview = new TextArea(SessionTrailViewFormatter.preview(session, i18n));
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefSize(560, 340);
        Label replacementNote = new Label(i18n.text("savedSessions.replacesCurrent"));
        replacementNote.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8, replacementNote, preview));
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != load) return;
        SavedSession restored = session;
        if (session.getProtectedFields() != null) {
            PasswordField password = new PasswordField();
            password.setPromptText(i18n.text("savedSessions.passwordPrompt"));
            Dialog<ButtonType> unlock = new Dialog<>();
            unlock.setTitle(i18n.text("savedSessions.passwordTitle"));
            unlock.getDialogPane().setContent(password);
            unlock.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            if (owner != null) unlock.initOwner(owner);
            if (unlock.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                try { restored = codec.restore(session, password.getText().toCharArray()); }
                catch (IllegalArgumentException error) {
                    new Alert(Alert.AlertType.ERROR, i18n.text("savedSessions.decryptError")).showAndWait();
                    return;
                } finally { password.clear(); }
            }
        }
        stateRestore.accept(restored.getUiState());
        OperationSessionLog loadedLog = restored.getOperationLog();
        OperationSessionLog trail = loadedLog == null ? new OperationSessionLog() : loadedLog;
        // Replace the current trail through the supplied callback's controller-owned state.
        trailState.replace(trail);
        operationSelection.accept(restored.getOperation());
        trailRefresh.run();
        if (!trail.isEmpty()) showTrailStep.accept(trail.size() - 1);
        statusReporter.updateStatus(i18n.text("savedSessions.loaded", session.getName()));
    }

}
