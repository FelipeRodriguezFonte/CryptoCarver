package com.cryptocarver.ui;

import com.cryptocarver.model.OperationSessionLog;
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
    private final Supplier<Map<String, Object>> stateCapture;
    private final Consumer<Map<String, Object>> stateRestore;
    private final Consumer<String> operationSelection;
    private final Runnable trailRefresh;
    private final Consumer<Integer> showTrailStep;
    private final Supplier<OperationSessionLog> trailSupplier;
    private final Consumer<OperationSessionLog> trailReplacement;
    private final Supplier<String> operationSupplier;
    private final Supplier<String> subtitleSupplier;
    private final Supplier<Node> ownerNodeSupplier;

    public SavedSessionsCoordinator(VBox container, VBox list, StatusReporter statusReporter,
                                    SavedSessionsManager manager, I18nService i18n,
                                    Supplier<Map<String, Object>> stateCapture,
                                    Consumer<Map<String, Object>> stateRestore,
                                    Consumer<String> operationSelection, Runnable trailRefresh,
                                    Consumer<Integer> showTrailStep,
                                    Supplier<OperationSessionLog> trailSupplier,
                                    Consumer<OperationSessionLog> trailReplacement,
                                    Supplier<String> operationSupplier, Supplier<String> subtitleSupplier,
                                    Supplier<Node> ownerNodeSupplier) {
        this.container = container;
        this.list = list;
        this.statusReporter = statusReporter;
        this.manager = manager;
        this.i18n = i18n;
        this.stateCapture = stateCapture;
        this.stateRestore = stateRestore;
        this.operationSelection = operationSelection;
        this.trailRefresh = trailRefresh;
        this.showTrailStep = showTrailStep;
        this.trailSupplier = trailSupplier;
        this.trailReplacement = trailReplacement == null ? ignored -> { } : trailReplacement;
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
            Button delete = new Button("Delete");
            delete.getStyleClass().add("secondary-button");
            delete.setStyle("-fx-font-size: 11px; -fx-padding: 5 10; -fx-text-fill: #fc8181;");
            delete.setOnAction(event -> {
                manager.removeSession(session);
                refresh();
                statusReporter.updateStatus("Deleted session");
            });
            item.getChildren().addAll(info, load, delete);
            list.getChildren().add(item);
        }
    }

    public void save(String name) {
        if (name == null || name.trim().isEmpty()) return;
        String operation = operationSupplier.get();
        if ("Dashboard".equals(operation) || operation == null) {
            String subtitle = subtitleSupplier.get();
            if (subtitle != null) operation = subtitle;
        }
        if (operation == null || operation.isEmpty()) operation = "Generic";
        manager.addSession(new SavedSession(name, operation, stateCapture.get(), trailSupplier.get()));
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
        stateRestore.accept(session.getUiState());
        OperationSessionLog loadedLog = session.getOperationLog();
        OperationSessionLog trail = loadedLog == null ? new OperationSessionLog() : loadedLog;
        // Replace the current trail through the supplied callback's controller-owned state.
        trailReplacement.accept(trail);
        operationSelection.accept(session.getOperation());
        trailRefresh.run();
        if (!trail.isEmpty()) showTrailStep.accept(trail.size() - 1);
        statusReporter.updateStatus(i18n.text("savedSessions.loaded", session.getName()));
    }

}
