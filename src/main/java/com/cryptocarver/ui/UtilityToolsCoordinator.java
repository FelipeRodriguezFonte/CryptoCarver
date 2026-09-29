package com.cryptocarver.ui;

import com.cryptocarver.util.EpochTimestampConverter;
import com.cryptocarver.util.JsonTextFormatter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Creates the standalone utility windows exposed by the main shell. */
public final class UtilityToolsCoordinator {
    private final Supplier<Scene> ownerSceneSupplier;
    private final BiConsumer<String, Map<String, String>> historyRecorder;
    private final BiConsumer<String, String> errorPresenter;

    public UtilityToolsCoordinator(Supplier<Scene> ownerSceneSupplier,
            BiConsumer<String, Map<String, String>> historyRecorder,
            BiConsumer<String, String> errorPresenter) {
        this.ownerSceneSupplier = ownerSceneSupplier;
        this.historyRecorder = historyRecorder;
        this.errorPresenter = errorPresenter;
    }

    public void handleEpochConverter() {
        try {
            Stage stage = new Stage();
            stage.setTitle("Epoch Converter");
            VBox root = new VBox(10);
            root.setPadding(new Insets(20));
            Label timestampLabel = new Label("Unix Timestamp (seconds):");
            TextField timestampField = new TextField(String.valueOf(Instant.now().getEpochSecond()));
            Label dateLabel = new Label("Human Date (UTC):");
            TextField dateField = new TextField();
            dateField.setEditable(false);
            Button convertButton = new Button("Convert");
            convertButton.setOnAction(event -> {
                try {
                    String result = EpochTimestampConverter.toUtc(timestampField.getText());
                    dateField.setText(result);
                    Map<String, String> details = new HashMap<>();
                    details.put("Timestamp", timestampField.getText());
                    details.put("Result", result);
                    historyRecorder.accept("Epoch Converter", details);
                } catch (Exception exception) {
                    dateField.setText("Invalid input");
                }
            });
            convertButton.fire();
            root.getChildren().addAll(timestampLabel, timestampField, convertButton, dateLabel, dateField);
            Scene scene = new Scene(root, 300, 250);
            copyStylesheets(scene);
            stage.setScene(scene);
            stage.show();
        } catch (Exception exception) {
            errorPresenter.accept("Tool Error", exception.getMessage());
        }
    }

    public void handleJsonFormatter() {
        try {
            Stage stage = new Stage();
            stage.setTitle("JSON Formatter");
            VBox root = new VBox(10);
            root.setPadding(new Insets(10));
            VBox.setVgrow(root, javafx.scene.layout.Priority.ALWAYS);
            TextArea input = new TextArea();
            input.setPromptText("Paste JSON here...");
            TextArea output = new TextArea();
            output.setEditable(false);
            Button formatButton = new Button("Format");
            formatButton.setOnAction(event -> {
                try {
                    output.setText(JsonTextFormatter.format(input.getText()));
                    historyRecorder.accept("JSON Formatter", new HashMap<>());
                } catch (Exception exception) {
                    output.setText("Invalid JSON: " + exception.getMessage());
                }
            });
            root.getChildren().addAll(new Label("Input:"), input, formatButton, new Label("Output:"), output);
            Scene scene = new Scene(root, 600, 400);
            copyStylesheets(scene);
            stage.setScene(scene);
            stage.show();
        } catch (Exception exception) {
            errorPresenter.accept("Tool Error", exception.getMessage());
        }
    }

    private void copyStylesheets(Scene scene) {
        Scene ownerScene = ownerSceneSupplier.get();
        if (ownerScene != null) {
            scene.getStylesheets().addAll(ownerScene.getStylesheets());
        }
    }
}
