package com.cryptocarver.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
    private Label findStatusLabel;
    private Button maximizeButton;
    private List<Integer> matches = List.of();
    private int activeMatch = -1;

    void show(Window owner, String title, String content) {
        if (stage == null) {
            createStage(owner);
        }
        stage.setTitle(title == null || title.isBlank() ? "Expanded Result" : title);
        // A viewer is reused between operations. Reset per-result navigation so
        // a search/selection from the previous result cannot make the second
        // opening appear stale or jump to an unrelated offset.
        searchField.clear();
        contentArea.setText(content == null ? "" : content);
        contentArea.positionCaret(0);
        resetSearch();
        updateMetrics();
        if (stage.isIconified()) {
            stage.setIconified(false);
        }
        stage.show();
        stage.toFront();
        stage.requestFocus();
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
        searchField.textProperty().addListener((observable, previous, current) -> resetSearch());
        Button findButton = new Button("Find next");
        findButton.setOnAction(event -> findNext());
        Button findPreviousButton = new Button("Find previous");
        findPreviousButton.setOnAction(event -> findPrevious());
        findStatusLabel = new Label();
        findStatusLabel.setMinWidth(48);

        CheckBox wrapCheck = new CheckBox("Wrap lines");
        wrapCheck.selectedProperty().addListener((obs, oldValue, wrap) -> contentArea.setWrapText(wrap));

        Button copyButton = new Button("Copy");
        copyButton.setOnAction(event -> copyContent());
        Button copySelectionButton = new Button("Copy selection");
        copySelectionButton.setOnAction(event -> copySelection());
        Button saveButton = new Button("Save As…");
        saveButton.setOnAction(event -> saveContent());
        maximizeButton = new Button("Maximize");
        maximizeButton.setOnAction(event -> toggleMaximized());
        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> stage.hide());

        metricsLabel = new Label();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, searchField, findButton, findPreviousButton, findStatusLabel, wrapCheck, spacer, metricsLabel,
                copyButton, copySelectionButton, saveButton, maximizeButton, closeButton);
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
        stage.maximizedProperty().addListener((observable, wasMaximized, isMaximized) ->
                maximizeButton.setText(isMaximized ? "Restore" : "Maximize"));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
    }

    /** Common shortcuts keep long-result inspection practical without mouse travel. */
    private void handleShortcut(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            stage.hide();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
            saveContent();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.G) {
            if (event.isShiftDown()) {
                findPrevious();
            } else {
                findNext();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER && event.isShiftDown()
                && searchField.isFocused()) {
            findPrevious();
            event.consume();
        }
    }

    private void toggleMaximized() {
        stage.setMaximized(!stage.isMaximized());
    }

    private void findNext() {
        if (!ensureMatches()) {
            return;
        }
        activeMatch = (activeMatch + 1) % matches.size();
        selectActiveMatch();
    }

    private void findPrevious() {
        if (!ensureMatches()) {
            return;
        }
        activeMatch = activeMatch <= 0 ? matches.size() - 1 : activeMatch - 1;
        selectActiveMatch();
    }

    private boolean ensureMatches() {
        String query = searchField.getText();
        if (query == null || query.isBlank() || contentArea.getText().isEmpty()) {
            matches = List.of();
            activeMatch = -1;
            findStatusLabel.setText("");
            return false;
        }
        if (matches.isEmpty()) {
            matches = findOccurrences(contentArea.getText(), query);
            if (matches.isEmpty()) {
                findStatusLabel.setText("No matches");
                return false;
            }
        }
        return true;
    }

    private void selectActiveMatch() {
        int start = matches.get(activeMatch);
        contentArea.selectRange(start, start + searchField.getText().length());
        findStatusLabel.setText((activeMatch + 1) + " / " + matches.size());
    }

    private void resetSearch() {
        matches = List.of();
        activeMatch = -1;
        if (findStatusLabel != null) {
            findStatusLabel.setText("");
        }
    }

    static List<Integer> findOccurrences(String content, String query) {
        if (content == null || query == null || query.isBlank()) {
            return List.of();
        }
        String haystack = content.toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        List<Integer> positions = new ArrayList<>();
        for (int position = haystack.indexOf(needle); position >= 0;
                position = haystack.indexOf(needle, position + needle.length())) {
            positions.add(position);
        }
        return List.copyOf(positions);
    }

    private void copyContent() {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(contentArea.getText());
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private void copySelection() {
        String selected = contentArea.getSelectedText();
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(selected == null || selected.isEmpty() ? contentArea.getText() : selected);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
        findStatusLabel.setText(selected == null || selected.isEmpty() ? "Copied all" : "Copied selection");
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
        metricsLabel.setText(content.length() + " chars · " + content.getBytes(StandardCharsets.UTF_8).length
                + " UTF-8 bytes · " + lines + " lines");
    }
}
