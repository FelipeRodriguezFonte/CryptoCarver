package com.cryptoforge.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
    private List<String> headers = List.of();

    void show(Window owner, String title, TableView<?> source) {
        if (stage == null) {
            createStage(owner);
        }
        populate(source);
        stage.setTitle(title);
        stage.show();
        stage.toFront();
    }

    private void createStage(Window owner) {
        expandedTable = new TableView<>();
        expandedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(expandedTable, Priority.ALWAYS);

        metricsLabel = new Label();
        Button copyButton = new Button("Copy TSV");
        copyButton.setOnAction(event -> copyTsv());
        Button saveButton = new Button("Save CSV…");
        saveButton.setOnAction(event -> saveCsv());
        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> stage.hide());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, metricsLabel, spacer, copyButton, saveButton, closeButton);
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
        expandedTable.setItems(rows);
        metricsLabel.setText(rows.size() + " rows · " + headers.size() + " columns");
    }

    private String asTsv() {
        StringBuilder result = new StringBuilder(String.join("\t", headers));
        for (ObservableList<String> row : expandedTable.getItems()) {
            result.append('\n').append(String.join("\t", row));
        }
        return result.toString();
    }

    private void copyTsv() {
        ClipboardContent content = new ClipboardContent();
        content.putString(asTsv());
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

    private String toCsv() {
        StringBuilder result = new StringBuilder(csvRow(headers));
        for (ObservableList<String> row : expandedTable.getItems()) {
            result.append('\n').append(csvRow(row));
        }
        return result.toString();
    }

    private String csvRow(List<String> values) {
        return values.stream().map(value -> '"' + value.replace("\"", "\"\"") + '"')
                .collect(java.util.stream.Collectors.joining(","));
    }
}
