package com.cryptoforge.ui;

import com.cryptoforge.model.AppSettings;
import com.cryptoforge.model.HistoryItem;
import com.cryptoforge.model.HistoryManager;
import com.cryptoforge.model.OperationDetail;
import com.cryptoforge.model.OperationRegistry;
import com.cryptoforge.model.OperationRecipe;
import com.cryptoforge.model.RecipeVariables;
import com.cryptoforge.model.SecretVisibility;
import com.cryptoforge.utils.HistoryComparator;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public class HistoryController {

    @FXML private VBox mainHistoryContainer;
    @FXML private ComboBox<SecretVisibility> visibilityCombo;
    @FXML private Label unsafeVisibilityWarningLabel;
    @FXML private TextField historyFilterField;
    @FXML private ComboBox<String> historyModuleFilterCombo;
    @FXML private Label historySummaryLabel;
    @FXML private Button compareBtn;
    @FXML private Button exportReportBtn;
    @FXML private Button copyReportBtn;
    @FXML private Button exportRecipeBtn;
    @FXML private Button openHistoryDetailBtn;
    @FXML private Button copyHistoryDetailBtn;
    @FXML private Button exportJsonRecordBtn;
    @FXML private Button exportVisibleJsonBtn;
    @FXML private TableView<HistoryItem> historyTable;
    @FXML private TableColumn<HistoryItem, String> timeCol;
    @FXML private TableColumn<HistoryItem, String> opCol;
    @FXML private TableColumn<HistoryItem, String> actionCol;
    @FXML private TableView<OperationDetail> detailsTable;
    @FXML private TableColumn<OperationDetail, String> nameCol;
    @FXML private TableColumn<OperationDetail, String> valCol;
    @FXML private TableColumn<OperationDetail, String> classCol;

    private OperationNavigator navigator;
    private HistoryManager historyManager;
    private final ExpandedTextViewer expandedDetailViewer = new ExpandedTextViewer();
    private final ExpandedTableViewer expandedComparisonViewer = new ExpandedTableViewer();

    @FXML
    public void initialize() {
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        detailsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Table columns
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTimestamp()));
        opCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOperation()));

        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Rerun");
            {
                btn.setStyle("-fx-background-color: #0288d1; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 3 8; -fx-cursor: hand;");
                btn.setOnAction(event -> {
                    HistoryItem item = getTableView().getItems().get(getIndex());
                    if (navigator != null) {
                        navigator.restoreOperationState(item.getUiState(), item.getOperation());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        // Details columns
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        valCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().value()));
        valCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTooltip(empty || value == null || value.isBlank() ? null : new Tooltip(value));
            }
        });
        classCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().classification().name()));

        // Selection Listener
        historyTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showDetailsFor(newVal);
        });

        // Bindings for Buttons
        exportRecipeBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> historyTable.getSelectionModel().getSelectedItems().size() != 1,
            historyTable.getSelectionModel().getSelectedItems()
        ));

        exportReportBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> historyTable.getSelectionModel().getSelectedItems().size() != 1,
            historyTable.getSelectionModel().getSelectedItems()
        ));

        copyReportBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> historyTable.getSelectionModel().getSelectedItems().size() != 1,
            historyTable.getSelectionModel().getSelectedItems()
        ));

        exportJsonRecordBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> historyTable.getSelectionModel().getSelectedItems().size() != 1,
            historyTable.getSelectionModel().getSelectedItems()
        ));

        exportVisibleJsonBtn.disableProperty().bind(Bindings.isEmpty(historyTable.getItems()));

        compareBtn.disableProperty().bind(Bindings.createBooleanBinding(
            () -> historyTable.getSelectionModel().getSelectedItems().size() != 2,
            historyTable.getSelectionModel().getSelectedItems()
        ));

        openHistoryDetailBtn.disableProperty().bind(Bindings.isNull(detailsTable.getSelectionModel().selectedItemProperty()));
        copyHistoryDetailBtn.disableProperty().bind(Bindings.isNull(detailsTable.getSelectionModel().selectedItemProperty()));

        // Combo Box
        visibilityCombo.getItems().setAll(SecretVisibility.values());
        visibilityCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(SecretVisibility value) {
                if (value == null) return "";
                return switch (value) {
                    case FULL_LAB -> "Unsafe lab — show all values";
                    case MASKED -> "Masked — hide sensitive values";
                    case REDACTED -> "Redacted — remove secret values";
                };
            }

            @Override
            public SecretVisibility fromString(String ignored) {
                return null;
            }
        });
        visibilityCombo.setValue(AppSettings.getInstance().getSecretVisibility());
        visibilityCombo.setOnAction(e -> {
            AppSettings.getInstance().setSecretVisibility(visibilityCombo.getValue());
            updateVisibilityWarning();
            showDetailsFor(historyTable.getSelectionModel().getSelectedItem());
        });
        updateVisibilityWarning();

        historyFilterField.textProperty().addListener((observable, oldValue, newValue) -> refresh());
        historyModuleFilterCombo.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        mainHistoryContainer.addEventFilter(KeyEvent.KEY_PRESSED, this::handleHistoryShortcut);
    }

    public void setHistoryManager(HistoryManager historyManager) {
        this.historyManager = historyManager;
        if (this.historyManager != null) {
            refresh();
        }
    }

    public void setOperationNavigator(OperationNavigator navigator) {
        this.navigator = navigator;
    }

    /** Gives the legacy "Export History" navigation route a meaningful target. */
    public void focusExportActions() {
        if (exportVisibleJsonBtn != null) {
            exportVisibleJsonBtn.requestFocus();
        }
    }

    public void refresh() {
        if (historyManager != null && historyTable != null) {
            java.util.Set<String> selectedIds = historyTable.getSelectionModel().getSelectedItems().stream()
                    .map(HistoryItem::getId)
                    .collect(java.util.stream.Collectors.toSet());
            refreshModuleFilterOptions();
            String query = historyFilterField == null ? "" : historyFilterField.getText();
            String module = historyModuleFilterCombo == null ? null : historyModuleFilterCombo.getValue();
            List<HistoryItem> filtered = historyManager.getHistoryItems().stream()
                    .filter(item -> matchesFilter(item, query) && matchesModule(item, module))
                    .toList();
            historyTable.getItems().setAll(filtered);
            historyTable.getSelectionModel().clearSelection();
            for (int index = 0; index < filtered.size(); index++) {
                if (selectedIds.contains(filtered.get(index).getId())) {
                    historyTable.getSelectionModel().select(index);
                }
            }
            if (historyTable.getSelectionModel().isEmpty() && !filtered.isEmpty()) {
                historyTable.getSelectionModel().selectFirst();
            }
            if (historySummaryLabel != null) {
                int total = historyManager.getHistoryItems().size();
                historySummaryLabel.setText(filtered.size() == total
                        ? total + (total == 1 ? " operation" : " operations")
                        : filtered.size() + " of " + total + " operations");
            }
        }
    }

    private void handleHistoryShortcut(KeyEvent event) {
        if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
            historyFilterField.requestFocus();
            historyFilterField.selectAll();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && historyFilterField.isFocused()) {
            historyFilterField.clear();
            historyTable.requestFocus();
            event.consume();
        }
    }

    /**
     * Search metadata only. Detail values are intentionally excluded so that
     * filtering cannot become an accidental side channel for masked secrets.
     */
    private boolean matchesFilter(HistoryItem item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (containsIgnoreCase(item.getOperation(), needle) || containsIgnoreCase(item.getTimestamp(), needle)) {
            return true;
        }
        return item.getStructuredDetails() != null && item.getStructuredDetails().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(detail -> containsIgnoreCase(detail.name(), needle)
                        || containsIgnoreCase(detail.classification().name(), needle));
    }

    /** Filters using catalog metadata only; operation values and secret details are never examined. */
    private boolean matchesModule(HistoryItem item, String module) {
        return module == null || module.isBlank() || "All modules".equals(module)
                || moduleFor(item).equals(module);
    }

    private String moduleFor(HistoryItem item) {
        if (item == null) {
            return "Other";
        }
        return OperationRegistry.getInstance().resolveNavigation(item.getOperation())
                .map(com.cryptoforge.model.OperationDescriptor::getCategory)
                .orElse("Other");
    }

    private void refreshModuleFilterOptions() {
        if (historyModuleFilterCombo == null || historyManager == null) {
            return;
        }
        String selected = historyModuleFilterCombo.getValue();
        List<String> modules = historyManager.getHistoryItems().stream()
                .map(this::moduleFor)
                .distinct()
                .sorted()
                .toList();
        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        options.add("All modules");
        options.addAll(modules);
        if (!historyModuleFilterCombo.getItems().equals(options)) {
            historyModuleFilterCombo.getItems().setAll(options);
        }
        historyModuleFilterCombo.setValue(selected != null && options.contains(selected) ? selected : "All modules");
    }

    private boolean containsIgnoreCase(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(lowercaseNeedle);
    }

    /** Renders operation details according to the explicit visibility policy. */
    private void showDetailsFor(HistoryItem item) {
        if (detailsTable == null) {
            return;
        }
        detailsTable.getItems().clear();
        if (item == null) {
            return;
        }
        if (item.getStructuredDetails() != null && !item.getStructuredDetails().isEmpty()) {
            detailsTable.getItems().setAll(item.getStructuredDetails().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(this::forSelectedVisibility)
                    .toList());
        } else if (item.getDetails() != null && !item.getDetails().isEmpty()) {
            // Legacy entries do not carry a classification, so treat their raw payload as sensitive.
            detailsTable.getItems().add(forSelectedVisibility(
                    OperationDetail.sensitiveDetail("Legacy details", item.getDetails())));
        }
    }

    private OperationDetail forSelectedVisibility(OperationDetail detail) {
        SecretVisibility visibility = visibilityCombo == null || visibilityCombo.getValue() == null
                ? SecretVisibility.REDACTED : visibilityCombo.getValue();
        String value = detail.value();
        if (visibility == SecretVisibility.REDACTED
                && detail.classification() == OperationDetail.Classification.SECRET) {
            value = "***REDACTED***";
        } else if (visibility == SecretVisibility.MASKED
                && detail.classification() != OperationDetail.Classification.PUBLIC) {
            value = "***MASKED***";
        } else if (visibility == SecretVisibility.REDACTED
                && detail.classification() == OperationDetail.Classification.SENSITIVE) {
            value = "***MASKED***";
        }
        return new OperationDetail(detail.name(), value, detail.classification(), detail.multiline(), detail.format());
    }

    private void updateVisibilityWarning() {
        if (unsafeVisibilityWarningLabel == null) {
            return;
        }
        boolean unsafe = visibilityCombo != null && visibilityCombo.getValue() == SecretVisibility.FULL_LAB;
        unsafeVisibilityWarningLabel.setVisible(unsafe);
        unsafeVisibilityWarningLabel.setManaged(unsafe);
    }

    @FXML
    private void openSelectedHistoryDetail(ActionEvent event) {
        OperationDetail detail = detailsTable == null ? null : detailsTable.getSelectionModel().getSelectedItem();
        if (detail == null) {
            showError("History detail", "Select a detail first.");
            return;
        }
        javafx.stage.Window owner = mainHistoryContainer == null || mainHistoryContainer.getScene() == null
                ? null : mainHistoryContainer.getScene().getWindow();
        expandedDetailViewer.show(owner, "History Detail — " + detail.name(), detail.value());
    }

    @FXML
    private void copySelectedHistoryDetail(ActionEvent event) {
        OperationDetail detail = detailsTable == null ? null : detailsTable.getSelectionModel().getSelectedItem();
        if (detail == null) {
            showError("History detail", "Select a detail first.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(detail.value() == null ? "" : detail.value());
        Clipboard.getSystemClipboard().setContent(content);
        if (navigator != null) navigator.updateStatus("History detail copied to clipboard");
    }

    @FXML
    private void exportSelectedHistoryRecord(ActionEvent event) {
        if (historyTable == null || historyTable.getSelectionModel().getSelectedItem() == null) {
            showError("JSON Record Export", "Select a history entry first.");
            return;
        }
        SecretVisibility visibility = visibilityCombo == null ? SecretVisibility.REDACTED : visibilityCombo.getValue();
        String record = com.cryptoforge.utils.HistoryRecordExporter.toJson(
                historyTable.getSelectionModel().getSelectedItem(), visibility);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export History Record");
        chooser.setInitialFileName("cryptocarver-history-record.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showSaveDialog(mainHistoryContainer == null || mainHistoryContainer.getScene() == null
                ? null : mainHistoryContainer.getScene().getWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), record, StandardCharsets.UTF_8);
            if (navigator != null) navigator.updateStatus("JSON record exported: " + file.getName());
        } catch (Exception e) {
            showError("JSON Record Export", "Unable to write record: " + e.getMessage());
        }
    }

    @FXML
    private void exportVisibleHistoryRecords(ActionEvent event) {
        if (historyTable == null || historyTable.getItems().isEmpty()) {
            showError("Visible History Export", "No visible history entries to export.");
            return;
        }
        SecretVisibility visibility = visibilityCombo == null ? SecretVisibility.REDACTED : visibilityCombo.getValue();
        String record = com.cryptoforge.utils.HistoryRecordExporter.toJson(
                new java.util.ArrayList<>(historyTable.getItems()), visibility);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Visible History Records");
        chooser.setInitialFileName("cryptocarver-visible-history.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showSaveDialog(mainHistoryContainer == null || mainHistoryContainer.getScene() == null
                ? null : mainHistoryContainer.getScene().getWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), record, StandardCharsets.UTF_8);
            if (navigator != null) {
                navigator.updateStatus("Exported " + historyTable.getItems().size() + " visible history records");
            }
        } catch (Exception e) {
            showError("Visible History Export", "Unable to write records: " + e.getMessage());
        }
    }

    @FXML
    private void handleClearHistoryFilter(ActionEvent event) {
        if (historyFilterField != null) {
            historyFilterField.clear();
        }
        if (historyModuleFilterCombo != null) {
            historyModuleFilterCombo.setValue("All modules");
        }
    }

    @FXML
    private void handleClearHistory(ActionEvent event) {
        if (historyManager != null) {
            historyManager.clearHistory();
            refresh();
            if (detailsTable != null) {
                detailsTable.getItems().clear();
            }
            if (navigator != null) {
                navigator.updateStatus("History cleared");
            }
        }
    }

    @FXML
    private void exportSelectedHistoryRecipe(ActionEvent event) {
        if (historyTable == null || historyTable.getSelectionModel().getSelectedItem() == null) {
            showError("Recipe Export", "Select a history entry first.");
            return;
        }
        HistoryItem item = historyTable.getSelectionModel().getSelectedItem();
        OperationRecipe recipe;
        if (item.getStructuredDetails() != null && !item.getStructuredDetails().isEmpty()) {
            recipe = new OperationRecipe(
                item.getOperation(),
                item.getStructuredDetails(),
                AppSettings.getInstance().getSecretVisibility()
            );
        } else {
            showError("Recipe Export", "Legacy operations without structured details cannot be exported securely.");
            return;
        }
        
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Operation Recipe");
        chooser.setInitialFileName("cryptocarver-recipe.json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Recipe JSON", "*.json"));
        File file = chooser.showSaveDialog(mainHistoryContainer.getScene().getWindow());
        if (file == null) return;
        
        try {
            Files.writeString(file.toPath(), recipe.toJson(), StandardCharsets.UTF_8);
            if (navigator != null) {
                navigator.updateStatus("Recipe exported: " + file.getName());
            }
            Alert confirmation = new Alert(Alert.AlertType.INFORMATION, "Recipe saved to:\n" + file.getAbsolutePath(), ButtonType.OK);
            confirmation.setTitle("Recipe exported");
            confirmation.setHeaderText("Reusable non-secret configuration saved");
            confirmation.showAndWait();
        } catch (Exception e) {
            showError("Recipe Export", "Unable to write recipe: " + e.getMessage());
        }
    }

    @FXML
    private void exportSelectedHistoryReport(ActionEvent event) {
        if (historyTable == null || historyTable.getSelectionModel().getSelectedItem() == null) {
            showError("Report Export", "Select a history entry first.");
            return;
        }
        HistoryItem item = historyTable.getSelectionModel().getSelectedItem();
        SecretVisibility visibility = visibilityCombo == null ? SecretVisibility.REDACTED : visibilityCombo.getValue();
        String report = com.cryptoforge.utils.HistoryReportExporter.toMarkdown(item, visibility);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Operation Report");
        chooser.setInitialFileName("cryptocarver-operation-report.md");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        File file = chooser.showSaveDialog(mainHistoryContainer == null || mainHistoryContainer.getScene() == null
                ? null : mainHistoryContainer.getScene().getWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), report, StandardCharsets.UTF_8);
            if (navigator != null) navigator.updateStatus("Report exported: " + file.getName());
        } catch (Exception e) {
            showError("Report Export", "Unable to write report: " + e.getMessage());
        }
    }

    @FXML
    private void copySelectedHistoryReport(ActionEvent event) {
        if (historyTable == null || historyTable.getSelectionModel().getSelectedItem() == null) {
            showError("Report Copy", "Select a history entry first.");
            return;
        }
        SecretVisibility visibility = visibilityCombo == null ? SecretVisibility.REDACTED : visibilityCombo.getValue();
        String report = com.cryptoforge.utils.HistoryReportExporter.toMarkdown(
                historyTable.getSelectionModel().getSelectedItem(), visibility);
        ClipboardContent content = new ClipboardContent();
        content.putString(report);
        Clipboard.getSystemClipboard().setContent(content);
        if (navigator != null) navigator.updateStatus("Operation report copied to clipboard");
    }

    @FXML
    private void handleCompareHistory(ActionEvent event) {
        List<HistoryItem> selected = historyTable.getSelectionModel().getSelectedItems();
        if (selected == null || selected.size() != 2) return;

        SecretVisibility visibility = visibilityCombo == null ? SecretVisibility.REDACTED : visibilityCombo.getValue();
        List<HistoryComparator.DiffEntry> diffs = HistoryComparator.compare(selected.get(0), selected.get(1), visibility);

        TableView<HistoryComparator.DiffEntry> diffTable = new TableView<>();
        diffTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<HistoryComparator.DiffEntry, String> keyCol = new TableColumn<>("Property");
        keyCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().key));

        TableColumn<HistoryComparator.DiffEntry, String> val1Col = new TableColumn<>("Item 1\n" + selected.get(0).getTimestamp());
        val1Col.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().value1));

        TableColumn<HistoryComparator.DiffEntry, String> val2Col = new TableColumn<>("Item 2\n" + selected.get(1).getTimestamp());
        val2Col.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().value2));

        diffTable.getColumns().addAll(keyCol, val1Col, val2Col);
        int differenceCount = (int) diffs.stream().filter(diff -> diff.isDifferent).count();
        javafx.collections.transformation.FilteredList<HistoryComparator.DiffEntry> visibleDiffs =
                new javafx.collections.transformation.FilteredList<>(
                        javafx.collections.FXCollections.observableArrayList(diffs), ignored -> true);
        diffTable.setItems(visibleDiffs);

        diffTable.setRowFactory(tv -> new TableRow<HistoryComparator.DiffEntry>() {
            @Override
            protected void updateItem(HistoryComparator.DiffEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.isDifferent) {
                    setStyle("-fx-background-color: #ffcdd2;");
                } else {
                    setStyle("");
                }
            }
        });

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("History Comparison");
        alert.setHeaderText("Comparing:\n1) " + selected.get(0).getOperation() + "\n2) " + selected.get(1).getOperation()
                + "\n\nValues policy: " + visibility);

        Label summary = new Label(differenceCount + " different of " + diffs.size() + " compared properties");
        CheckBox onlyDifferences = new CheckBox("Show differences only");
        onlyDifferences.setSelected(differenceCount > 0);
        onlyDifferences.selectedProperty().addListener((observable, oldValue, selectedOnly) ->
                visibleDiffs.setPredicate(diff -> !selectedOnly || diff.isDifferent));
        visibleDiffs.setPredicate(diff -> !onlyDifferences.isSelected() || diff.isDifferent);
        VBox content = new VBox(8, summary, onlyDifferences, diffTable);
        content.setPrefSize(600, 400);
        alert.getDialogPane().setContent(content);
        ButtonType openExpanded = new ButtonType("Open expanded", ButtonBar.ButtonData.OTHER);
        alert.getButtonTypes().add(openExpanded);
        alert.showAndWait().ifPresent(button -> {
            if (button != openExpanded) {
                return;
            }
            javafx.stage.Window owner = mainHistoryContainer == null || mainHistoryContainer.getScene() == null
                    ? null : mainHistoryContainer.getScene().getWindow();
            expandedComparisonViewer.show(owner, "Expanded Table — History Comparison", diffTable);
        });
    }

    @FXML
    private void importOperationRecipe(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Operation Recipe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Recipe JSON", "*.json"));
        File file = chooser.showOpenDialog(mainHistoryContainer.getScene().getWindow());
        if (file == null) return;
        
        try {
            OperationRecipe recipe = OperationRecipe.fromJson(
                    Files.readString(file.toPath(), StandardCharsets.UTF_8));
            Set<String> variableNames = new TreeSet<>();
            recipe.parameters().values().forEach(value -> variableNames.addAll(
                    RecipeVariables.referencedVariables(value)));
            
            Map<String, String> variables = new LinkedHashMap<>();
            for (String name : variableNames) {
                TextInputDialog variableDialog = new TextInputDialog();
                variableDialog.setTitle("Recipe variable");
                variableDialog.setHeaderText("Value required for ${" + name + "}");
                variableDialog.setContentText(name + ":");
                Optional<String> value = variableDialog.showAndWait();
                if (value.isEmpty()) return;
                variables.put(name, value.get());
            }
            
            Map<String, String> resolvedParameters = new LinkedHashMap<>();
            recipe.parameters().forEach((key, value) -> resolvedParameters.put(key,
                    RecipeVariables.resolve(value, variables)));
            
            StringBuilder preview = new StringBuilder("Operation: ").append(recipe.operation())
                    .append("\nVersion: ").append(recipe.version()).append("\nCreated: ").append(recipe.createdAt()).append("\n\nParameters:\n");
            resolvedParameters.forEach((key, value) -> preview.append(key).append(" = ").append(value).append('\n'));
            
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, preview.toString(), ButtonType.CANCEL, ButtonType.OK);
            confirmation.setTitle("Load Operation Recipe");
            confirmation.setHeaderText("Review recipe before restoring its state");
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            
            Map<String, Object> state = new LinkedHashMap<>();
            resolvedParameters.forEach((key, value) -> { if (!"historyTimestamp".equals(key)) state.put(key, value); });
            
            if (navigator != null) {
                navigator.restoreOperationState(state, recipe.operation());
                navigator.updateStatus("Recipe loaded: " + recipe.operation() + " (review inputs before running)");
            }
        } catch (Exception e) {
            showError("Recipe Import", "Invalid or unsupported recipe: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }
}
