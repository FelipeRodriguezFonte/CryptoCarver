package com.cryptocarver.ui;

import com.cryptocarver.crypto.icsf.Diagnostic;
import com.cryptocarver.crypto.icsf.IcsfMessages;
import com.cryptocarver.crypto.icsf.IcsfHex;
import com.cryptocarver.crypto.icsf.IcsfTokenParser;
import com.cryptocarver.crypto.icsf.IcsfTokenReport;
import com.cryptocarver.crypto.icsf.Origin;
import com.cryptocarver.crypto.icsf.ParseResult;
import com.cryptocarver.crypto.icsf.SummaryKey;
import com.cryptocarver.crypto.icsf.SummaryValue;
import com.cryptocarver.service.I18nService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * The single-token view of the ICSF / CCA analyser.
 *
 * <p>A self-contained pane in the manner of the PKCS#11 profiles view: its own
 * FXML, its own controller and its own {@code icsf.*} text namespace, included
 * into the Keys module without adding anything to {@code KeysController}.</p>
 *
 * <p>These are CCA native key tokens, deliberately kept apart from the TR-31 key
 * block pane: the two are different formats and the verbs that handle them
 * (CSNBKEX / CSNBKIM versus TR-31 export/import) are different services.</p>
 */
public final class IcsfTokenController {

    /** One line of the summary card. */
    public record SummaryRow(String field, String value, String detail) { }

    @FXML private TitledPane icsfTokenPane;
    @FXML private VBox icsfTokenRoot;
    @FXML private ComboBox<Origin> icsfTokenOriginCombo;
    @FXML private ComboBox<InputShape> icsfTokenFormatCombo;
    @FXML private TextArea icsfTokenInputArea;
    @FXML private Label icsfTokenFeedbackLabel;
    @FXML private TableView<SummaryRow> icsfTokenSummaryTable;
    @FXML private TableColumn<SummaryRow, String> icsfTokenSummaryFieldColumn;
    @FXML private TableColumn<SummaryRow, String> icsfTokenSummaryValueColumn;
    @FXML private TableColumn<SummaryRow, String> icsfTokenSummaryDetailColumn;
    @FXML private TextArea icsfTokenDetailArea;

    private final ObservableList<SummaryRow> summaryRows = FXCollections.observableArrayList();
    private StatusReporter statusReporter;
    private ModuleI18n.Binding i18nBinding;

    /** How the pasted token is laid out. */
    public enum InputShape {
        /** Linear hexadecimal, with or without separators. */
        LINEAR,
        /** A host dump in two rows: high digits above, low digits below. */
        TWO_ROW
    }

    @FXML
    private void initialize() {
        icsfTokenOriginCombo.setItems(FXCollections.observableArrayList(Origin.values()));
        icsfTokenOriginCombo.setValue(Origin.INFER);
        icsfTokenOriginCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Origin origin) {
                return origin == null ? "" : t("icsf.origin." + origin.name(), origin.value());
            }
            @Override public Origin fromString(String value) {
                return Origin.INFER;
            }
        });

        icsfTokenFormatCombo.setItems(FXCollections.observableArrayList(InputShape.values()));
        icsfTokenFormatCombo.setValue(InputShape.LINEAR);
        icsfTokenFormatCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(InputShape shape) {
                return shape == null ? "" : t("icsf.shape." + shape.name(), shape.name());
            }
            @Override public InputShape fromString(String value) {
                return InputShape.LINEAR;
            }
        });

        icsfTokenSummaryFieldColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().field()));
        icsfTokenSummaryValueColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().value()));
        icsfTokenSummaryDetailColumn.setCellValueFactory(
                data -> new ReadOnlyStringWrapper(data.getValue().detail()));
        icsfTokenSummaryTable.setItems(summaryRows);
        icsfTokenSummaryTable.setPlaceholder(new Label(t("icsf.token.noAnalysis",
                "Paste a key token and press Analyze.")));

        // Binding the pane, not the inner box: ModuleI18n reaches the content through
        // TitledPane.getContent(), so one binding covers the title and everything below it.
        i18nBinding = ModuleI18n.bind(icsfTokenPane, ModuleTextCatalog.icsf());
        I18nService.getInstance().addLocaleChangeListener(locale -> refreshLocalizedRuntimeText());
    }

    public void setStatusReporter(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    /** Opens this pane, for navigation from the operation tree. */
    public void expand() {
        if (icsfTokenPane != null) icsfTokenPane.setExpanded(true);
    }

    // =====================================================================
    // Actions
    // =====================================================================
    @FXML
    private void handleAnalyze() {
        String raw = icsfTokenInputArea.getText();
        if (raw == null || raw.isBlank()) {
            feedback(t("icsf.token.inputRequired", "Paste a key token first."), true);
            return;
        }
        byte[] token;
        try {
            token = icsfTokenFormatCombo.getValue() == InputShape.TWO_ROW
                    ? IcsfHex.deinterleaveTwoRows(raw)
                    : IcsfHex.clean(raw);
        } catch (IllegalArgumentException exception) {
            summaryRows.clear();
            icsfTokenDetailArea.clear();
            feedback(exception.getMessage(), true);
            return;
        }

        Origin origin = icsfTokenOriginCombo.getValue() == null
                ? Origin.INFER : icsfTokenOriginCombo.getValue();
        ParseResult result = IcsfTokenParser.parse(token, origin);

        showSummary(result);
        icsfTokenDetailArea.setText(IcsfTokenReport.renderText(result, origin, token,
                I18nService.getInstance().getLocale()));
        icsfTokenDetailArea.positionCaret(0);

        if (!result.isOk()) {
            feedback(result.error(), true);
            return;
        }
        String message = t("icsf.token.analysed", "Token analysed: {0}, {1} bytes, {2} warning(s).",
                result.tokenFamily().code(), token.length, result.warnings().size());
        feedback(message, false);
        if (statusReporter != null) statusReporter.updateStatus(message);
    }

    @FXML
    private void handleSaveReport() {
        String report = icsfTokenDetailArea.getText();
        if (report == null || report.isBlank()) {
            feedback(t("icsf.token.nothingToSave", "Analyze a token before saving the report."), true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("icsf.token.saveTitle", "Save key token analysis"));
        chooser.setInitialFileName("icsf-token-analysis.txt");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(t("icsf.file.text", "Text report"), "*.txt"));
        File target = chooser.showSaveDialog(icsfTokenRoot.getScene() == null
                ? null : icsfTokenRoot.getScene().getWindow());
        if (target == null) return;
        try {
            Files.writeString(target.toPath(), report, StandardCharsets.UTF_8);
            // The saved file carries the token in full, so say so rather than
            // reporting a bare success.
            feedback(t("icsf.token.saved",
                    "Report written to {0}. It carries the token in hexadecimal: handle it like the "
                            + "dump it came from.", target.getName()), false);
        } catch (Exception exception) {
            feedback(t("icsf.token.saveFailed", "Could not write the report: {0}",
                    String.valueOf(exception.getMessage())), true);
        }
    }

    @FXML
    private void handleClear() {
        icsfTokenInputArea.clear();
        icsfTokenDetailArea.clear();
        summaryRows.clear();
        feedback(t("icsf.token.cleared", "Key token analyzer cleared."), false);
    }

    @FXML
    private void handleReset() {
        handleClear();
        icsfTokenOriginCombo.setValue(Origin.INFER);
        icsfTokenFormatCombo.setValue(InputShape.LINEAR);
        feedback(t("icsf.token.reset", "Defaults restored."), false);
    }

    // =====================================================================
    // Presentation
    // =====================================================================
    private void showSummary(ParseResult result) {
        summaryRows.clear();
        for (Map.Entry<SummaryKey, SummaryValue> entry : result.summary().entrySet()) {
            SummaryKey key = entry.getKey();
            SummaryValue value = entry.getValue();
            summaryRows.add(new SummaryRow(IcsfTextResolver.dimension(key),
                    IcsfTextResolver.value(key, value.code()),
                    IcsfMessages.resolve(value.detail(), I18nService.getInstance().getLocale())));
        }
        for (Diagnostic warning : result.warnings()) {
            summaryRows.add(new SummaryRow(t("icsf.token.warning", "Warning"),
                    warning.code().name(),
                    IcsfMessages.resolve(warning.message(), I18nService.getInstance().getLocale())));
        }
    }

    /** Reapplies the text this controller writes itself, which ModuleI18n cannot reach. */
    private void refreshLocalizedRuntimeText() {
        if (icsfTokenSummaryTable == null) return;
        icsfTokenSummaryTable.setPlaceholder(new Label(t("icsf.token.noAnalysis",
                "Paste a key token and press Analyze.")));
        // Force the combo converters to run again against the new locale.
        Origin origin = icsfTokenOriginCombo.getValue();
        icsfTokenOriginCombo.setValue(null);
        icsfTokenOriginCombo.setValue(origin);
        InputShape shape = icsfTokenFormatCombo.getValue();
        icsfTokenFormatCombo.setValue(null);
        icsfTokenFormatCombo.setValue(shape);
    }

    private void feedback(String message, boolean problem) {
        if (icsfTokenFeedbackLabel == null) return;
        icsfTokenFeedbackLabel.setText(message);
        icsfTokenFeedbackLabel.getStyleClass().removeAll("error-text", "helper-text");
        icsfTokenFeedbackLabel.getStyleClass().add(problem ? "error-text" : "helper-text");
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
}
