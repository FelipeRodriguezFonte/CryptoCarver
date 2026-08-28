package com.cryptocarver.ui;

import com.cryptocarver.crypto.icsf.BatchInputFormat;
import com.cryptocarver.crypto.icsf.BatchItem;
import com.cryptocarver.crypto.icsf.FindingCode;
import com.cryptocarver.crypto.icsf.IcsfBatchAnalyzer;
import com.cryptocarver.crypto.icsf.IcsfBatchRenderer;
import com.cryptocarver.crypto.icsf.IcsfBatchReport;
import com.cryptocarver.crypto.icsf.IcsfMessages;
import com.cryptocarver.crypto.icsf.IcsfText;
import com.cryptocarver.crypto.icsf.InventoryColumn;
import com.cryptocarver.crypto.icsf.InventoryRow;
import com.cryptocarver.crypto.icsf.Origin;
import com.cryptocarver.service.I18nService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The batch view of the ICSF / CCA analyser.
 *
 * <p>Four outputs on screen — statistics, findings, inventory and the report as it
 * is saved — plus the two files. Every value shown here is the single-token
 * analyser's own verdict, translated for display but never re-decided.</p>
 */
public final class IcsfBatchController {

    /** One line of the statistics table. */
    public record StatisticRow(String dimension, String value, String count, String percentage) { }

    /** One line of the findings table. */
    public record FindingRow(FindingCode code, String severity, String title, String count,
                             String tokens) { }

    @FXML private TitledPane icsfBatchPane;
    @FXML private VBox icsfBatchRoot;
    @FXML private ComboBox<Origin> icsfBatchOriginCombo;
    @FXML private ComboBox<BatchInputFormat> icsfBatchFormatCombo;
    @FXML private TextArea icsfBatchInputArea;
    @FXML private CheckBox icsfBatchDetailCheck;
    @FXML private Label icsfBatchFeedbackLabel;
    @FXML private TabPane icsfBatchTabs;
    @FXML private Tab icsfBatchInventoryTab;

    @FXML private TableView<StatisticRow> icsfBatchStatisticsTable;
    @FXML private TableColumn<StatisticRow, String> icsfStatDimensionColumn;
    @FXML private TableColumn<StatisticRow, String> icsfStatValueColumn;
    @FXML private TableColumn<StatisticRow, String> icsfStatCountColumn;
    @FXML private TableColumn<StatisticRow, String> icsfStatPercentColumn;

    @FXML private TableView<FindingRow> icsfBatchFindingsTable;
    @FXML private TableColumn<FindingRow, String> icsfFindingSeverityColumn;
    @FXML private TableColumn<FindingRow, String> icsfFindingCodeColumn;
    @FXML private TableColumn<FindingRow, String> icsfFindingTitleColumn;
    @FXML private TableColumn<FindingRow, String> icsfFindingCountColumn;
    @FXML private TextArea icsfBatchFindingDetailArea;

    @FXML private TextField icsfBatchFilterField;
    @FXML private TableView<InventoryRow> icsfBatchInventoryTable;
    @FXML private TextArea icsfBatchReportArea;

    private final ObservableList<StatisticRow> statisticRows = FXCollections.observableArrayList();
    private final ObservableList<FindingRow> findingRows = FXCollections.observableArrayList();
    private final ObservableList<InventoryRow> inventoryRows = FXCollections.observableArrayList();
    private FilteredList<InventoryRow> filteredInventory;

    private IcsfBatchReport report;
    private StatusReporter statusReporter;
    private ModuleI18n.Binding i18nBinding;

    @FXML
    private void initialize() {
        setUpCombos();
        setUpStatisticsTable();
        setUpFindingsTable();
        setUpInventoryTable();

        i18nBinding = ModuleI18n.bind(icsfBatchPane, ModuleTextCatalog.icsf());
        I18nService.getInstance().addLocaleChangeListener(locale -> refreshLocalizedRuntimeText());
    }

    private void setUpCombos() {
        icsfBatchOriginCombo.setItems(FXCollections.observableArrayList(Origin.values()));
        icsfBatchOriginCombo.setValue(Origin.INFER);
        icsfBatchOriginCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Origin origin) {
                return origin == null ? "" : t("icsf.origin." + origin.name(), origin.value());
            }
            @Override public Origin fromString(String value) {
                return Origin.INFER;
            }
        });

        icsfBatchFormatCombo.setItems(FXCollections.observableArrayList(BatchInputFormat.values()));
        icsfBatchFormatCombo.setValue(BatchInputFormat.AUTO);
        icsfBatchFormatCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(BatchInputFormat format) {
                return format == null ? "" : t("icsf.format." + format.name(), format.label());
            }
            @Override public BatchInputFormat fromString(String value) {
                return BatchInputFormat.AUTO;
            }
        });
    }

    private void setUpStatisticsTable() {
        icsfStatDimensionColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().dimension()));
        icsfStatValueColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().value()));
        icsfStatCountColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().count()));
        icsfStatPercentColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().percentage()));
        icsfBatchStatisticsTable.setItems(statisticRows);
        icsfBatchStatisticsTable.setPlaceholder(new Label(
                t("icsf.batch.noStatistics", "Analyze a batch to see its statistics.")));
    }

    private void setUpFindingsTable() {
        icsfFindingSeverityColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().severity()));
        icsfFindingCodeColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().code().code()));
        icsfFindingTitleColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().title()));
        icsfFindingCountColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().count()));
        icsfBatchFindingsTable.setItems(findingRows);
        icsfBatchFindingsTable.setPlaceholder(new Label(
                t("icsf.batch.noFindings", "Analyze a batch to see its findings.")));

        icsfBatchFindingsTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> showFindingDetail(selected));
        icsfBatchFindingsTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                FindingRow selected = icsfBatchFindingsTable.getSelectionModel().getSelectedItem();
                if (selected != null) filterByFinding(selected.code());
            }
        });
    }

    private void setUpInventoryTable() {
        // Built from InventoryColumn so the table cannot drift from the model, and so a
        // column added to the core shows up here without editing the FXML.
        icsfBatchInventoryTable.getColumns().clear();
        for (InventoryColumn column : InventoryColumn.values()) {
            TableColumn<InventoryRow, String> tableColumn =
                    new TableColumn<>(IcsfTextResolver.column(column));
            tableColumn.setCellValueFactory(
                    data -> new ReadOnlyStringWrapper(data.getValue().get(column)));
            tableColumn.setPrefWidth(preferredWidth(column));
            tableColumn.setUserData(column);
            icsfBatchInventoryTable.getColumns().add(tableColumn);
        }
        filteredInventory = new FilteredList<>(inventoryRows, row -> true);
        icsfBatchInventoryTable.setItems(filteredInventory);
        icsfBatchInventoryTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        icsfBatchInventoryTable.setPlaceholder(new Label(
                t("icsf.batch.noRows", "Analyze a batch to see its inventory.")));

        icsfBatchFilterField.textProperty().addListener((observable, previous, filter) ->
                filteredInventory.setPredicate(row -> row.matches(filter)));
    }

    private static double preferredWidth(InventoryColumn column) {
        return switch (column) {
            case INDEX, BYTES, WARNINGS -> 60;
            case STATUS, TVV, MKVP -> 90;
            case LABEL, KEY_TYPE, KEY_LENGTH, MATERIAL, WRAPPING -> 130;
            case FINDINGS -> 320;
            default -> 150;
        };
    }

    public void setStatusReporter(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    /** Opens this pane, for navigation from the operation tree. */
    public void expand() {
        if (icsfBatchPane != null) icsfBatchPane.setExpanded(true);
    }

    /** The report currently on screen, or {@code null} before the first analysis. */
    public IcsfBatchReport report() {
        return report;
    }

    // =====================================================================
    // Actions
    // =====================================================================
    @FXML
    private void handleAnalyze() {
        String text = icsfBatchInputArea.getText();
        if (text == null || text.isBlank()) {
            feedback(t("icsf.batch.noInput", "Paste the key tokens to analyse first."), true);
            return;
        }
        Origin origin = icsfBatchOriginCombo.getValue() == null
                ? Origin.INFER : icsfBatchOriginCombo.getValue();
        BatchInputFormat format = icsfBatchFormatCombo.getValue() == null
                ? BatchInputFormat.AUTO : icsfBatchFormatCombo.getValue();

        report = IcsfBatchAnalyzer.analyse(text, format, origin);
        showReport();

        String message = t("icsf.batch.analysed",
                "{0} token(s) read: {1} analysed, {2} with errors, {3} finding code(s).",
                report.total(), report.analysed().size(), report.failed().size(),
                report.findings().size());
        feedback(message, report.failed().isEmpty() ? false : true);
        if (statusReporter != null) statusReporter.updateStatus(message);
    }

    @FXML
    private void handleLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("icsf.batch.loadTitle", "Load a file of key tokens"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(t("icsf.file.text", "Text report"), "*.txt", "*.hex", "*.*"));
        File source = chooser.showOpenDialog(window());
        if (source == null) return;
        try {
            icsfBatchInputArea.setText(Files.readString(source.toPath(), StandardCharsets.UTF_8));
            feedback(t("icsf.batch.loaded", "Loaded {0}.", source.getName()), false);
        } catch (Exception exception) {
            feedback(t("icsf.batch.loadFailed", "Could not read the file: {0}",
                    String.valueOf(exception.getMessage())), true);
        }
    }

    @FXML
    private void handleSaveText() {
        if (report == null) {
            feedback(t("icsf.batch.nothingToSave", "Analyze a batch before saving."), true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("icsf.batch.saveTextTitle", "Save the batch report"));
        chooser.setInitialFileName("icsf-batch-report.txt");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(t("icsf.file.text", "Text report"), "*.txt"));
        File target = chooser.showSaveDialog(window());
        if (target == null) return;
        try {
            boolean detail = icsfBatchDetailCheck.isSelected();
            Files.writeString(target.toPath(), IcsfBatchRenderer.renderFull(report, detail,
                    I18nService.getInstance().getLocale()), StandardCharsets.UTF_8);
            // Say what the file contains, not just that it was written.
            feedback(t("icsf.batch.savedText",
                    "Report written to {0}. It carries the tokens in hexadecimal: handle it like the "
                            + "dump they came from.", target.getName()), false);
        } catch (Exception exception) {
            feedback(t("icsf.batch.saveFailed", "Could not write the file: {0}",
                    String.valueOf(exception.getMessage())), true);
        }
    }

    @FXML
    private void handleSaveCsv() {
        if (report == null) {
            feedback(t("icsf.batch.nothingToSave", "Analyze a batch before saving."), true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("icsf.batch.saveCsvTitle", "Save the inventory"));
        chooser.setInitialFileName("icsf-batch-inventory.csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(t("icsf.file.csv", "CSV inventory"), "*.csv"));
        File target = chooser.showSaveDialog(window());
        if (target == null) return;
        try {
            // UTF-8 with a byte-order mark, so Excel opens it correctly on a double click.
            Files.write(target.toPath(), IcsfBatchRenderer.toCsvBytes(report));
            feedback(t("icsf.batch.savedCsv",
                    "Inventory written to {0}. Every row carries its token in hexadecimal.",
                    target.getName()), false);
        } catch (Exception exception) {
            feedback(t("icsf.batch.saveFailed", "Could not write the file: {0}",
                    String.valueOf(exception.getMessage())), true);
        }
    }

    @FXML
    private void handleClearFilter() {
        icsfBatchFilterField.clear();
    }

    @FXML
    private void handleClear() {
        icsfBatchInputArea.clear();
        icsfBatchReportArea.clear();
        icsfBatchFindingDetailArea.clear();
        icsfBatchFilterField.clear();
        statisticRows.clear();
        findingRows.clear();
        inventoryRows.clear();
        report = null;
        feedback(t("icsf.batch.cleared", "Batch analysis cleared."), false);
    }

    @FXML
    private void handleReset() {
        handleClear();
        icsfBatchOriginCombo.setValue(Origin.INFER);
        icsfBatchFormatCombo.setValue(BatchInputFormat.AUTO);
        icsfBatchDetailCheck.setSelected(true);
        feedback(t("icsf.batch.reset", "Defaults restored."), false);
    }

    // =====================================================================
    // Presentation
    // =====================================================================
    private void showReport() {
        statisticRows.clear();
        for (IcsfBatchReport.Group group : report.statistics()) {
            String dimension = IcsfTextResolver.dimension(group.dimension());
            for (IcsfBatchReport.Group.Value value : group.values()) {
                statisticRows.add(new StatisticRow(dimension,
                        IcsfTextResolver.value(group.dimension(), value.code()),
                        String.valueOf(value.count()),
                        String.format(java.util.Locale.ROOT, "%.1f%%", value.percentage())));
            }
        }

        findingRows.clear();
        for (IcsfBatchReport.AggregatedFinding finding : report.findings()) {
            findingRows.add(new FindingRow(finding.code(),
                    IcsfTextResolver.severity(finding.code().severity()),
                    IcsfTextResolver.findingTitle(finding.code()),
                    String.valueOf(finding.count()),
                    finding.tokens().stream().map(index -> "#" + index)
                            .collect(Collectors.joining(", "))));
        }

        inventoryRows.setAll(report.rows());
        icsfBatchReportArea.setText(IcsfBatchRenderer.renderSummary(report,
                I18nService.getInstance().getLocale()));
        icsfBatchReportArea.positionCaret(0);
        icsfBatchFindingDetailArea.clear();
    }

    private void showFindingDetail(FindingRow selected) {
        if (selected == null || report == null) {
            icsfBatchFindingDetailArea.clear();
            return;
        }
        StringBuilder detail = new StringBuilder();
        detail.append(selected.code().code()).append("  [")
                .append(selected.severity()).append("]").append(System.lineSeparator());
        detail.append(IcsfTextResolver.findingTitle(selected.code())).append(System.lineSeparator())
                .append(System.lineSeparator());
        detail.append(IcsfTextResolver.findingDetail(selected.code())).append(System.lineSeparator())
                .append(System.lineSeparator());
        detail.append(t("icsf.batch.affectedTokens", "Tokens: {0}", selected.tokens()));

        report.findings().stream()
                .filter(finding -> finding.code() == selected.code())
                .findFirst()
                .ifPresent(finding -> {
                    if (finding.notes().isEmpty()) return;
                    detail.append(System.lineSeparator()).append(System.lineSeparator());
                    for (IcsfText note : finding.notes()) {
                        detail.append("  - ")
                                .append(IcsfMessages.resolve(note, I18nService.getInstance().getLocale()))
                                .append(System.lineSeparator());
                    }
                });

        icsfBatchFindingDetailArea.setText(detail.toString());
        icsfBatchFindingDetailArea.positionCaret(0);
    }

    /** Shows only the tokens that raised a given finding, and switches to the inventory. */
    void filterByFinding(FindingCode code) {
        icsfBatchFilterField.setText(code.code());
        if (icsfBatchTabs != null && icsfBatchInventoryTab != null) {
            icsfBatchTabs.getSelectionModel().select(icsfBatchInventoryTab);
        }
        feedback(t("icsf.batch.filteredBy", "Inventory filtered by {0}.", code.code()), false);
    }

    /** Reapplies the text this controller writes itself, which ModuleI18n cannot reach. */
    private void refreshLocalizedRuntimeText() {
        if (icsfBatchInventoryTable == null) return;
        for (TableColumn<InventoryRow, ?> column : icsfBatchInventoryTable.getColumns()) {
            if (column.getUserData() instanceof InventoryColumn inventoryColumn) {
                column.setText(IcsfTextResolver.column(inventoryColumn));
            }
        }
        icsfBatchStatisticsTable.setPlaceholder(new Label(
                t("icsf.batch.noStatistics", "Analyze a batch to see its statistics.")));
        icsfBatchFindingsTable.setPlaceholder(new Label(
                t("icsf.batch.noFindings", "Analyze a batch to see its findings.")));
        icsfBatchInventoryTable.setPlaceholder(new Label(
                t("icsf.batch.noRows", "Analyze a batch to see its inventory.")));

        Origin origin = icsfBatchOriginCombo.getValue();
        icsfBatchOriginCombo.setValue(null);
        icsfBatchOriginCombo.setValue(origin);
        BatchInputFormat format = icsfBatchFormatCombo.getValue();
        icsfBatchFormatCombo.setValue(null);
        icsfBatchFormatCombo.setValue(format);

        if (report != null) showReport();
    }

    private javafx.stage.Window window() {
        return icsfBatchRoot.getScene() == null ? null : icsfBatchRoot.getScene().getWindow();
    }

    private void feedback(String message, boolean problem) {
        if (icsfBatchFeedbackLabel == null) return;
        icsfBatchFeedbackLabel.setText(message);
        icsfBatchFeedbackLabel.getStyleClass().removeAll("error-text", "helper-text");
        icsfBatchFeedbackLabel.getStyleClass().add(problem ? "error-text" : "helper-text");
    }

    /** Bundle lookup with an English fallback, so a missing key never blanks the UI. */
    private static String t(String key, String fallback, Object... arguments) {
        try {
            I18nService i18n = I18nService.getInstance();
            if (i18n.getBundle() != null && i18n.getBundle().containsKey(key)) {
                return i18n.text(key, arguments);
            }
        } catch (RuntimeException ignored) {
            // No bundle yet (headless tests, early startup).
        }
        return arguments == null || arguments.length == 0
                ? fallback : java.text.MessageFormat.format(fallback, arguments);
    }

    /** For tests: the inventory rows currently visible through the filter. */
    List<InventoryRow> visibleInventory() {
        return List.copyOf(filteredInventory);
    }

    /** For tests: the items behind the batch report. */
    List<BatchItem> items() {
        return report == null ? List.of() : report.items();
    }
}
