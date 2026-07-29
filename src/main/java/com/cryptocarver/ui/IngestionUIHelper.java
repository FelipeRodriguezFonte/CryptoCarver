package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.MaterialDetectionResult;
import com.cryptocarver.model.MaterialDetectionResult.MaterialType;
import com.cryptocarver.service.CryptoMaterialDetector;

import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class IngestionUIHelper {

    public static void bindField(TextInputControl field, Label statusLabel, MaterialType... expectedTypes) {
        if (field == null) return;

        // Real-time listener during manual typing/editing
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            updateStatusLabel(newVal, statusLabel, expectedTypes);
        });

        // Initial evaluation
        updateStatusLabel(field.getText(), statusLabel, expectedTypes);
    }

    public static boolean pasteFromClipboard(TextInputControl field, Label statusLabel, Runnable onSuccess, MaterialType... expectedTypes) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!clipboard.hasString()) {
            if (statusLabel != null) {
                statusLabel.setText("Clipboard is empty");
                statusLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
            }
            return false;
        }

        String candidate = clipboard.getString();
        return ingestContent(candidate, field, statusLabel, onSuccess, "Paste rejected", expectedTypes);
    }

    public static boolean loadFile(Window window, TextInputControl field, Label statusLabel, Runnable onSuccess, MaterialType... expectedTypes) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Material File");
        File file = chooser.showOpenDialog(window);
        if (file == null) return false;

        try {
            String candidate = Files.readString(file.toPath());
            return ingestContent(candidate, field, statusLabel, onSuccess, "File load rejected", expectedTypes);
        } catch (Exception e) {
            if (statusLabel != null) {
                statusLabel.setText("Error reading file: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
            }
            return false;
        }
    }

    public static void populateShelfMenu(MenuButton menuButton, TextInputControl field, Label statusLabel, Runnable onSuccess, MaterialType... expectedTypes) {
        if (menuButton == null) return;

        menuButton.getItems().clear();
        List<ClipboardEntry> entries = ClipboardShelfManager.getInstance().getEntries();

        int addedCount = 0;
        for (ClipboardEntry entry : entries) {
            MaterialDetectionResult detection = CryptoMaterialDetector.detect(entry.getValue());
            if (expectedTypes != null && expectedTypes.length > 0 && !detection.isCompatibleWith(expectedTypes)) {
                continue;
            }

            String displayLabel = entry.getLabel() + " (" + detection.getType() + ")";
            MenuItem item = new MenuItem(displayLabel);
            item.setOnAction(e -> {
                ingestContent(entry.getValue(), field, statusLabel, onSuccess, "Shelf insertion rejected", expectedTypes);
            });
            menuButton.getItems().add(item);
            addedCount++;
        }

        if (addedCount == 0) {
            MenuItem emptyItem = new MenuItem("No compatible items on Shelf");
            emptyItem.setDisable(true);
            menuButton.getItems().add(emptyItem);
        }
    }

    private static boolean ingestContent(String candidate, TextInputControl field, Label statusLabel,
                                         Runnable onSuccess, String errorPrefix, MaterialType... expectedTypes) {
        if (candidate == null || candidate.trim().isEmpty()) {
            if (statusLabel != null) {
                statusLabel.setText("Material content is empty");
                statusLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
            }
            return false;
        }

        MaterialDetectionResult detection = CryptoMaterialDetector.detect(candidate);
        if (expectedTypes != null && expectedTypes.length > 0 && !detection.isCompatibleWith(expectedTypes)) {
            if (statusLabel != null) {
                statusLabel.setText(errorPrefix + ": Expected " + formatExpected(expectedTypes)
                        + " (" + detection.getStatusLabelText() + ")");
                statusLabel.setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;");
            }
            // DO NOT modify field content when incompatible
            return false;
        }

        // Content is compatible -> update text field
        if (field != null) {
            field.setText(candidate);
        }

        if (statusLabel != null) {
            statusLabel.setText(detection.getStatusLabelText());
            statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
        }

        if (onSuccess != null) {
            onSuccess.run();
        }

        return true;
    }

    private static void updateStatusLabel(String text, Label statusLabel, MaterialType... expectedTypes) {
        if (statusLabel == null) return;

        MaterialDetectionResult detection = CryptoMaterialDetector.detect(text);
        if (detection.getType() == MaterialType.EMPTY) {
            statusLabel.setText("Field is empty");
            statusLabel.setStyle("-fx-text-fill: #757575;");
            return;
        }

        if (expectedTypes != null && expectedTypes.length > 0 && !detection.isCompatibleWith(expectedTypes)) {
            statusLabel.setText("Warning: " + detection.getStatusLabelText()
                    + " (Expected: " + formatExpected(expectedTypes) + ")");
            statusLabel.setStyle("-fx-text-fill: #e65100;");
            return;
        }

        statusLabel.setText(detection.getStatusLabelText());
        statusLabel.setStyle("-fx-text-fill: #2e7d32;");
    }

    private static String formatExpected(MaterialType... expectedTypes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expectedTypes.length; i++) {
            if (i > 0) sb.append(" or ");
            sb.append(expectedTypes[i].name());
        }
        return sb.toString();
    }
}
