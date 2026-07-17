package com.cryptoforge.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Reusable, non-modal viewer for long operation results.
 * It deliberately works on a snapshot so inspecting or searching a result never
 * changes the original operation output.
 */
final class ExpandedTextViewer {
    private Stage stage;
    private TextArea contentArea;
    private TextField searchField;
    private Label metricsLabel;
    private int searchStart;

    void show(Window owner, String title, String content) {
        if (stage == null) {
            createStage(owner);
        }
        stage.setTitle(title);
        contentArea.setText(content == null ? "" : content);
        contentArea.positionCaret(0);
        searchStart = 0;
        updateMetrics();
        stage.show();
        stage.toFront();
    }

    private void createStage(Window owner) {
        contentArea = new TextArea();
        contentArea.setEditable(false);
        contentArea.setWrapText(false);
        contentArea.getStyleClass().add("code-area");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Find in result");
        searchField.setOnAction(event -> findNext());
        Button findButton = new Button("Find next");
        findButton.setOnAction(event -> findNext());

        CheckBox wrapCheck = new CheckBox("Wrap lines");
        wrapCheck.selectedProperty().addListener((obs, oldValue, wrap) -> contentArea.setWrapText(wrap));

        Button copyButton = new Button("Copy");
        copyButton.setOnAction(event -> copyContent());
        Button saveButton = new Button("Save As…");
        saveButton.setOnAction(event -> saveContent());
        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> stage.hide());

        metricsLabel = new Label();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, searchField, findButton, wrapCheck, spacer, metricsLabel,
                copyButton, saveButton, closeButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, toolbar, contentArea);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 980, 680);
        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            scene.getStylesheets().addAll(owner.getScene() == null
                    ? java.util.List.of() : owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(420);
    }

    private void findNext() {
        String query = searchField.getText();
        String content = contentArea.getText();
        if (query == null || query.isBlank() || content.isEmpty()) {
            return;
        }
        int match = content.toLowerCase(java.util.Locale.ROOT)
                .indexOf(query.toLowerCase(java.util.Locale.ROOT), searchStart);
        if (match < 0 && searchStart > 0) {
            match = content.toLowerCase(java.util.Locale.ROOT)
                    .indexOf(query.toLowerCase(java.util.Locale.ROOT));
        }
        if (match >= 0) {
            contentArea.selectRange(match, match + query.length());
            searchStart = match + query.length();
        } else {
            searchStart = 0;
        }
    }

    private void copyContent() {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(contentArea.getText());
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private void saveContent() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save expanded result");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File selected = chooser.showSaveDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            Files.writeString(selected.toPath(), contentArea.getText(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            metricsLabel.setText("Could not save result: " + exception.getMessage());
        }
    }

    private void updateMetrics() {
        String content = contentArea.getText();
        long lines = content.isEmpty() ? 0 : content.lines().count();
        metricsLabel.setText(content.length() + " chars · " + lines + " lines");
    }
}
