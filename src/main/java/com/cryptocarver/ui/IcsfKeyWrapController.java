package com.cryptocarver.ui;

import com.cryptocarver.crypto.icsf.keywrap.ControlVectorDefaults;
import com.cryptocarver.crypto.icsf.keywrap.IcsfKeyWrapService;
import com.cryptocarver.crypto.icsf.keywrap.KeyWrapReport;
import com.cryptocarver.crypto.icsf.keywrap.KeyWrapResult;
import com.cryptocarver.crypto.icsf.keywrap.KeyWrapScheme;
import com.cryptocarver.service.I18nService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * The key export / import view of the ICSF / CCA tooling.
 *
 * <p>A self-contained pane in the manner of the two analyser panes: its own FXML, its own
 * controller and the {@code icsf.keywrap.*} text namespace, included into the Keys module
 * without adding anything to {@code KeysController} beyond the include itself.</p>
 *
 * <p>The four operations are tabs rather than four panes because they share one report and
 * one set of outputs: a user goes Export, then Resolve when the other side disagrees, then
 * Import to confirm, and keeping the result area common is what makes that flow readable.</p>
 */
public final class IcsfKeyWrapController {

    @FXML private TitledPane icsfKeyWrapPane;
    @FXML private VBox icsfKeyWrapRoot;
    @FXML private TabPane icsfKeyWrapTabs;
    @FXML private Label icsfKeyWrapFeedbackLabel;
    @FXML private TextArea icsfKeyWrapReportArea;

    @FXML private TextField icsfExportKeyField;
    @FXML private TextField icsfExportKekField;
    @FXML private ComboBox<String> icsfExportTypeCombo;
    @FXML private TextField icsfExportCvField;
    @FXML private ComboBox<KeyWrapScheme.Variant> icsfExportVariantCombo;
    @FXML private ComboBox<KeyWrapScheme.Mode> icsfExportModeCombo;
    @FXML private TextField icsfExportRnField;
    @FXML private CheckBox icsfExportHostVersionCheck;
    @FXML private CheckBox icsfExportNocvCheck;

    @FXML private TextField icsfImportInputField;
    @FXML private TextField icsfImportKekField;
    @FXML private ComboBox<String> icsfImportTypeCombo;
    @FXML private TextField icsfImportCvField;
    @FXML private ComboBox<KeyWrapScheme.Variant> icsfImportVariantCombo;
    @FXML private ComboBox<KeyWrapScheme.Mode> icsfImportModeCombo;

    @FXML private TextArea icsfInspectInputArea;

    @FXML private TextField icsfResolveInputField;
    @FXML private TextField icsfResolveKekField;
    @FXML private TextField icsfResolveExpectedKeyField;
    @FXML private TextField icsfResolveExpectedKcvField;
    @FXML private ComboBox<String> icsfResolveTypeCombo;

    private StatusReporter statusReporter;
    private ModuleI18n.Binding i18nBinding;

    /** The last result, kept so switching language re-renders without re-running anything. */
    private KeyWrapResult lastResult;

    @FXML
    private void initialize() {
        for (ComboBox<String> combo : java.util.List.of(
                icsfExportTypeCombo, icsfImportTypeCombo, icsfResolveTypeCombo)) {
            combo.setItems(FXCollections.observableArrayList(ControlVectorDefaults.keyTypes()));
        }
        icsfExportTypeCombo.setValue("EXPORTER");
        icsfImportTypeCombo.setValue("EXPORTER");
        icsfResolveTypeCombo.setValue("EXPORTER");

        configureVariantCombo(icsfExportVariantCombo);
        configureVariantCombo(icsfImportVariantCombo);
        configureModeCombo(icsfExportModeCombo);
        configureModeCombo(icsfImportModeCombo);

        icsfExportHostVersionCheck.setSelected(true);

        // Binding the pane, not the inner box: ModuleI18n reaches the content through
        // TitledPane.getContent(), so one binding covers the title and everything below it.
        i18nBinding = ModuleI18n.bind(icsfKeyWrapPane, ModuleTextCatalog.icsf());
        I18nService.getInstance().addLocaleChangeListener(locale -> refreshLocalizedRuntimeText());
    }

    private void configureVariantCombo(ComboBox<KeyWrapScheme.Variant> combo) {
        combo.setItems(FXCollections.observableArrayList(KeyWrapScheme.Variant.values()));
        combo.setValue(KeyWrapScheme.Variant.CV);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(KeyWrapScheme.Variant variant) {
                return variant == null ? "" : t("icsf.keywrap.variant." + variant.name(), variant.name());
            }
            @Override public KeyWrapScheme.Variant fromString(String value) {
                return KeyWrapScheme.Variant.CV;
            }
        });
    }

    private void configureModeCombo(ComboBox<KeyWrapScheme.Mode> combo) {
        combo.setItems(FXCollections.observableArrayList(KeyWrapScheme.Mode.values()));
        combo.setValue(KeyWrapScheme.Mode.ECB);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(KeyWrapScheme.Mode mode) {
                return mode == null ? "" : t("icsf.keywrap.mode." + mode.name(), mode.name());
            }
            @Override public KeyWrapScheme.Mode fromString(String value) {
                return KeyWrapScheme.Mode.ECB;
            }
        });
    }

    public void setStatusReporter(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    /** Opens this pane, for navigation from the operation tree. */
    public void expand() {
        if (icsfKeyWrapPane != null) icsfKeyWrapPane.setExpanded(true);
    }

    private static String t(String key, String fallback) {
        String value = I18nService.getInstance().text(key);
        return value == null || value.equals(key) ? fallback : value;
    }

    // =====================================================================
    // Actions
    // =====================================================================
    @FXML
    private void handleExport() {
        show(IcsfKeyWrapService.export(new IcsfKeyWrapService.ExportRequest(
                icsfExportKeyField.getText(), icsfExportKekField.getText(),
                icsfExportTypeCombo.getValue(), icsfExportCvField.getText(),
                icsfExportVariantCombo.getValue(), icsfExportModeCombo.getValue(),
                icsfExportNocvCheck.isSelected(), icsfExportHostVersionCheck.isSelected(),
                icsfExportRnField.getText())));
    }

    @FXML
    private void handleImport() {
        show(IcsfKeyWrapService.importKey(new IcsfKeyWrapService.ImportRequest(
                icsfImportInputField.getText(), icsfImportKekField.getText(),
                icsfImportCvField.getText(), icsfImportTypeCombo.getValue(),
                icsfImportVariantCombo.getValue(), icsfImportModeCombo.getValue(), "")));
    }

    @FXML
    private void handleInspect() {
        show(IcsfKeyWrapService.inspect(icsfInspectInputArea.getText()));
    }

    @FXML
    private void handleResolve() {
        show(IcsfKeyWrapService.resolve(new IcsfKeyWrapService.ResolveRequest(
                icsfResolveInputField.getText(), icsfResolveKekField.getText(),
                icsfResolveExpectedKeyField.getText(), icsfResolveExpectedKcvField.getText(),
                "", icsfResolveTypeCombo.getValue())));
    }

    private void show(KeyWrapResult result) {
        lastResult = result;
        icsfKeyWrapReportArea.setText(KeyWrapReport.render(result, I18nService.getInstance().getLocale()));
        if (!result.ok()) {
            feedback(t("icsf.keywrap.ui.failed", "The operation could not run; see the report."), true);
            return;
        }
        String message = switch (result.operation()) {
            case EXPORT -> t("icsf.keywrap.ui.exported", "Token produced.");
            case IMPORT -> t("icsf.keywrap.ui.imported", "Key recovered.");
            case INSPECT -> t("icsf.keywrap.ui.inspected", "Token read.");
            case RESOLVE -> t("icsf.keywrap.ui.resolved", "Schemes tried.");
        };
        feedback(message, result.hasNoteAtLeast(KeyWrapResult.Level.CRITICAL));
    }

    private void feedback(String message, boolean problem) {
        icsfKeyWrapFeedbackLabel.setText(message);
        icsfKeyWrapFeedbackLabel.setStyle(problem
                ? "-fx-text-fill: -fx-accent-danger, #b00020;" : "");
        if (statusReporter != null) statusReporter.updateStatus(message);
    }

    @FXML
    private void handleSaveReport() {
        if (lastResult == null) {
            feedback(t("icsf.keywrap.ui.nothingToSave", "Run an operation first."), true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(t("icsf.keywrap.ui.saveTitle", "Save key wrapping report"));
        chooser.setInitialFileName("icsf-keywrap-report.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text", "*.txt"));
        File file = chooser.showSaveDialog(icsfKeyWrapPane.getScene() == null
                ? null : icsfKeyWrapPane.getScene().getWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(),
                    KeyWrapReport.render(lastResult, I18nService.getInstance().getLocale()),
                    StandardCharsets.UTF_8);
            feedback(t("icsf.keywrap.ui.saved", "Report saved.") + " " + file.getName(), false);
        } catch (Exception exc) {
            feedback(t("icsf.keywrap.ui.saveFailed", "Could not save the report:")
                    + " " + exc.getMessage(), true);
        }
    }

    @FXML
    private void handleCopyToken() {
        copyOutput("token", t("icsf.keywrap.ui.noToken", "There is no token in the last result."));
    }

    @FXML
    private void handleCopyKey() {
        copyOutput("key", t("icsf.keywrap.ui.noKey", "There is no recovered key in the last result."));
    }

    private void copyOutput(String name, String absentMessage) {
        String value = lastResult == null ? null : lastResult.outputs().get(name);
        if (value == null || value.isBlank()) {
            feedback(absentMessage, true);
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        feedback(t("icsf.keywrap.ui.copied", "Copied to the clipboard."), false);
    }

    @FXML
    private void handleClearExport() {
        icsfExportKeyField.clear();
        icsfExportKekField.clear();
        icsfExportCvField.clear();
        icsfExportRnField.clear();
        clearResult();
    }

    @FXML
    private void handleClearImport() {
        icsfImportInputField.clear();
        icsfImportKekField.clear();
        icsfImportCvField.clear();
        clearResult();
    }

    @FXML
    private void handleClearInspect() {
        icsfInspectInputArea.clear();
        clearResult();
    }

    @FXML
    private void handleClearResolve() {
        icsfResolveInputField.clear();
        icsfResolveKekField.clear();
        icsfResolveExpectedKeyField.clear();
        icsfResolveExpectedKcvField.clear();
        clearResult();
    }

    @FXML
    private void handleReset() {
        handleClearExport();
        handleClearImport();
        handleClearInspect();
        handleClearResolve();
        icsfExportTypeCombo.setValue("EXPORTER");
        icsfImportTypeCombo.setValue("EXPORTER");
        icsfResolveTypeCombo.setValue("EXPORTER");
        icsfExportVariantCombo.setValue(KeyWrapScheme.Variant.CV);
        icsfImportVariantCombo.setValue(KeyWrapScheme.Variant.CV);
        icsfExportModeCombo.setValue(KeyWrapScheme.Mode.ECB);
        icsfImportModeCombo.setValue(KeyWrapScheme.Mode.ECB);
        icsfExportHostVersionCheck.setSelected(true);
        icsfExportNocvCheck.setSelected(false);
        feedback(t("icsf.keywrap.ui.reset", "Defaults restored."), false);
    }

    private void clearResult() {
        lastResult = null;
        icsfKeyWrapReportArea.clear();
        feedback(t("icsf.keywrap.ui.ready", "Ready"), false);
    }

    /**
     * Reapplies the text this controller writes itself, which ModuleI18n cannot reach.
     *
     * <p>The report is re-rendered from the stored result rather than re-computed: the
     * core holds meaning, not words, which is the whole point of that arrangement.</p>
     */
    private void refreshLocalizedRuntimeText() {
        for (ComboBox<?> combo : java.util.List.of(icsfExportVariantCombo, icsfImportVariantCombo,
                icsfExportModeCombo, icsfImportModeCombo)) {
            Object value = combo.getValue();
            combo.setValue(null);
            @SuppressWarnings("unchecked")
            ComboBox<Object> typed = (ComboBox<Object>) combo;
            typed.setValue(value);
        }
        if (lastResult != null) {
            icsfKeyWrapReportArea.setText(
                    KeyWrapReport.render(lastResult, I18nService.getInstance().getLocale()));
        }
    }
}
