package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibility;
import javafx.collections.FXCollections;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A detached, multi-selection view of the in-session Clipboard Shelf.
 * It is deliberately a copy-oriented tool: users can gather values and paste
 * them manually without depending on a destination-specific integration.
 */
public final class ClipboardShelfWindow {
    private final ClipboardShelfManager manager;

    public ClipboardShelfWindow(ClipboardShelfManager manager) {
        this.manager = manager;
    }

    public void show(Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Clipboard Shelf — Copy workspace");
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.NONE);

        ListView<ClipboardEntry> entries = new ListView<>();
        entries.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        entries.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(ClipboardEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    return;
                }
                String lock = isCopyAllowed(entry, currentVisibility()) ? "" : " 🔒";
                setText(entry.getLabel() + "  [" + entry.getFormat() + ", "
                        + entry.getClassification() + "]" + lock);
            }
        });

        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPromptText("Select one or more entries to inspect or copy.");
        VBox.setVgrow(preview, Priority.ALWAYS);

        Label policy = new Label();
        Button refresh = new Button("Refresh");
        Button copySelected = new Button("Copy selected");
        Button copyAll = new Button("Copy all allowed");
        Button close = new Button("Close");

        Runnable refreshEntries = () -> {
            entries.setItems(FXCollections.observableArrayList(manager.getEntries()));
            policy.setText(policyText(currentVisibility()));
            preview.clear();
        };
        Runnable liveRefresh = () -> {
            if (stage.isShowing()) {
                Platform.runLater(refreshEntries);
            }
        };
        manager.addChangeListener(liveRefresh);
        stage.setOnHidden(event -> manager.removeChangeListener(liveRefresh));
        refresh.setOnAction(event -> refreshEntries.run());
        entries.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ClipboardEntry>) change -> preview.setText(
                        buildClipboardText(entries.getSelectionModel().getSelectedItems(), currentVisibility())));
        copySelected.setOnAction(event -> copyToSystemClipboard(
                buildClipboardText(entries.getSelectionModel().getSelectedItems(), currentVisibility())));
        copyAll.setOnAction(event -> copyToSystemClipboard(buildClipboardText(manager.getEntries(), currentVisibility())));
        close.setOnAction(event -> stage.close());

        HBox actions = new HBox(8, refresh, copySelected, copyAll, close);
        VBox right = new VBox(8, policy, new Label("Copy preview"), preview, actions);
        right.setPadding(new Insets(10));
        HBox.setHgrow(right, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setLeft(entries);
        root.setCenter(right);
        BorderPane.setMargin(entries, new Insets(0, 10, 0, 0));
        entries.setPrefWidth(330);
        refreshEntries.run();

        stage.setScene(new Scene(root, 920, 560));
        stage.show();
    }

    static String buildClipboardText(Collection<ClipboardEntry> entries, SecretVisibility visibility) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        return entries.stream()
                .filter(entry -> isCopyAllowed(entry, visibility))
                .map(entry -> "--- " + entry.getLabel() + " [" + entry.getFormat() + "] ---\n"
                        + entry.getValue())
                .collect(Collectors.joining("\n\n"));
    }

    private static boolean isCopyAllowed(ClipboardEntry entry, SecretVisibility visibility) {
        if (entry == null) {
            return false;
        }
        return entry.getClassification() == OperationDetail.Classification.PUBLIC
                || isLaboratoryGeneratedKey(entry)
                || visibility == SecretVisibility.FULL_LAB;
    }

    private static boolean isLaboratoryGeneratedKey(ClipboardEntry entry) {
        String source = entry == null ? null : entry.getSourceOperation();
        return source != null && source.startsWith("Generate ") && source.contains(" Key");
    }

    private static SecretVisibility currentVisibility() {
        return com.cryptocarver.model.AppSettings.getInstance().getSecretVisibility();
    }

    private static String policyText(SecretVisibility visibility) {
        return visibility == SecretVisibility.FULL_LAB
                ? "⚠ Unsafe lab: sensitive values may be copied."
                : "Sensitive and secret values are locked. Switch to Unsafe lab to copy them.";
    }

    private static void copyToSystemClipboard(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ClipboardContent clipboard = new ClipboardContent();
        clipboard.putString(content);
        Clipboard.getSystemClipboard().setContent(clipboard);
    }
}
