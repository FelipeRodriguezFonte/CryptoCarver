package com.cryptocarver.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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

/** Creates a large, selectable snapshot of any table shown by the application. */
final class ExpandedTableViewer {
    private Stage stage;
    private TableView<ObservableList<String>> expandedTable;
    private Label metricsLabel;
    private TextField filterField;
    private Button maximizeButton;
    private List<String> headers = List.of();
    private List<ObservableList<String>> allRows = List.of();

    void show(Window owner, String title, TableView<?> source) {
        if (stage == null) {
            createStage(owner);
        }
        populate(source);
        stage.setTitle(title);
        if (stage.isIconified()) {
            stage.setIconified(false);
        }
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    private void createStage(Window owner) {
        expandedTable = new TableView<>();
        expandedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(expandedTable, Priority.ALWAYS);

        metricsLabel = new Label();
        filterField = new TextField();
        filterField.setPromptText("Filter visible values");
        filterField.setPrefWidth(190);
        filterField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        Button clearFilterButton = new Button("Clear filter");
        clearFilterButton.setOnAction(event -> filterField.clear());
        Button copyButton = new Button("Copy TSV");
        copyButton.setOnAction(event -> copyTsv());
        Button copySelectedButton = new Button("Copy selected");
        copySelectedButton.setOnAction(event -> copySelectedTsv());
        Button saveButton = new Button("Save CSV…");
        saveButton.setOnAction(event -> saveCsv());
        Button saveJsonButton = new Button("Save JSON…");
        saveJsonButton.setOnAction(event -> saveJson());
        maximizeButton = new Button("Maximize");
        maximizeButton.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));
        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> stage.hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, filterField, clearFilterButton, metricsLabel, spacer,
                copyButton, copySelectedButton, saveButton, saveJsonButton, maximizeButton, closeButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, toolbar, expandedTable);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 1120, 700);
        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            if (owner.getScene() != null) {
                scene.getStylesheets().addAll(owner.getScene().getStylesheets());
            }
        }
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(450);
        stage.maximizedProperty().addListener((observable, wasMaximized, isMaximized) ->
                maximizeButton.setText(isMaximized ? "Restore" : "Maximize"));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void populate(TableView<?> source) {
        List<TableColumn<?, ?>> sourceColumns = new ArrayList<>();
        for (TableColumn<?, ?> column : source.getColumns()) {
            sourceColumns.add(column);
        }
        headers = sourceColumns.stream().map(TableColumn::getText).toList();
        expandedTable.getColumns().clear();
        for (int index = 0; index < headers.size(); index++) {
            final int columnIndex = index;
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(headers.get(index));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().get(columnIndex)));
            expandedTable.getColumns().add(column);
        }

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        for (Object item : source.getItems()) {
            ObservableList<String> row = FXCollections.observableArrayList();
            for (TableColumn sourceColumn : sourceColumns) {
                Object value = sourceColumn.getCellData(item);
                row.add(value == null ? "" : String.valueOf(value));
            }
            rows.add(row);
        }
        allRows = List.copyOf(rows);
        applyFilter();
    }

    private void applyFilter() {
        if (expandedTable == null) {
            return;
        }
        String query = filterField == null ? "" : filterField.getText();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ObservableList<String>> visible = needle.isBlank() ? allRows : allRows.stream()
                .filter(row -> row.stream().anyMatch(value -> value != null
                        && value.toLowerCase(Locale.ROOT).contains(needle)))
                .toList();
        expandedTable.setItems(FXCollections.observableArrayList(visible));
        metricsLabel.setText((visible.size() == allRows.size()
                ? visible.size() + " rows" : visible.size() + " of " + allRows.size() + " rows")
                + " · " + headers.size() + " columns");
    }

    private void handleShortcut(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            if (filterField.isFocused()) {
                filterField.clear();
                expandedTable.requestFocus();
            } else {
                stage.hide();
            }
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
            filterField.requestFocus();
            filterField.selectAll();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
            saveCsv();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.C) {
            copySelectedTsv();
            event.consume();
        }
    }

    private String asTsv() {
        return toTsv(headers, expandedTable.getItems());
    }

    static String toTsv(List<String> headers, List<? extends List<String>> rows) {
        StringBuilder result = new StringBuilder(String.join("\t", headers));
        for (List<String> row : rows) {
            result.append('\n').append(String.join("\t", row));
        }
        return result.toString();
    }

    private void copyTsv() {
        ClipboardContent content = new ClipboardContent();
        content.putString(asTsv());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void copySelectedTsv() {
        List<ObservableList<String>> selected = expandedTable.getSelectionModel().getSelectedItems();
        ClipboardContent content = new ClipboardContent();
        content.putString(selected.isEmpty() ? asTsv() : toTsv(headers, selected));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void saveCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save expanded table");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File selected = chooser.showSaveDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            Files.writeString(selected.toPath(), toCsv(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            metricsLabel.setText("Could not save table: " + exception.getMessage());
        }
    }

    private void saveJson() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save expanded table as JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
        File selected = chooser.showSaveDialog(stage);
        if (selected == null) {
            return;
        }
        try {
            Files.writeString(selected.toPath(), toJson(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            metricsLabel.setText("Could not save table: " + exception.getMessage());
        }
    }

    private String toCsv() {
        return toCsv(headers, expandedTable.getItems());
    }

    static String toCsv(List<String> headers, List<? extends List<String>> rows) {
        StringBuilder result = new StringBuilder(csvRow(headers));
        for (List<String> row : rows) {
            result.append('\n').append(csvRow(row));
        }
        return result.toString();
    }

    private String toJson() {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(toJsonRows(headers, expandedTable.getItems()));
    }

    static List<Map<String, String>> toJsonRows(List<String> headers, List<? extends List<String>> rows) {
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : rows) {
            Map<String, String> object = new LinkedHashMap<>();
            for (int index = 0; index < headers.size(); index++) {
                String header = headers.get(index);
                String key = header == null || header.isBlank() ? "Column " + (index + 1) : header;
                if (object.containsKey(key)) {
                    key += " " + (index + 1);
                }
                object.put(key, index < row.size() && row.get(index) != null ? row.get(index) : "");
            }
            // LinkedHashMap order is the visual column order. Map.copyOf does
            // not promise iteration order, which would make exported JSON
            // unnecessarily unstable for diffs and laboratory evidence.
            result.add(Collections.unmodifiableMap(new LinkedHashMap<>(object)));
        }
        return List.copyOf(result);
    }

    private static String csvRow(List<String> values) {
        return values.stream().map(value -> '"' + (value == null ? "" : value).replace("\"", "\"\"") + '"')
                .collect(java.util.stream.Collectors.joining(","));
    }

}
