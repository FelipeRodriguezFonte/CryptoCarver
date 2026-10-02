package com.cryptocarver.ui;

import com.cryptocarver.crypto.TR31Operations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns tr31 controls and presentation; dependencies are resolved at use time. */
final class Tr31Coordinator {
    private static final Logger LOG = LoggerFactory.getLogger(Tr31Coordinator.class);
    record View(
            TextField tr31KbpkExportField,
            TextField tr31KeyToWrapField,
            ComboBox<String> tr31UsageCombo,
            ComboBox<String> tr31AlgorithmCombo,
            ComboBox<String> tr31ModeCombo,
            ComboBox<String> tr31VersionCombo,
            ComboBox<String> tr31ExportabilityCombo,
            TextField tr31OptionalBlocksField,
            ComboBox<String> tr31OptionalBlockCombo,
            TextArea tr31ExportResultArea,
            TextField tr31KbpkImportField,
            TextArea tr31KeyBlockField,
            TextField tr31KeyLengthField,
            TextArea tr31ImportResultArea) { }
    private final Supplier<View> views;
    private final Supplier<StatusReporter> reporter;
    private final Consumer<String> status;
    private final BiFunction<String, Object[], String> text;

    Tr31Coordinator(Supplier<View> views, Supplier<StatusReporter> reporter,
            Consumer<String> status, BiFunction<String, Object[], String> text) {
        this.views = views;
        this.reporter = reporter;
        this.status = status;
        this.text = text;
    }

    private String t(String key, Object... args) { return text.apply(key, args); }
    private void updateStatus(String message) { status.accept(message); }

    public void fillTR31KeyBlockInput(String value) {
        View view = views.get();
        if (view.tr31KeyBlockField() != null) view.tr31KeyBlockField().setText(value);
    }

    public void handleTR31Clear() {
        clearTR31Fields();
        if (reporter.get() != null) reporter.get().updateStatus(t("module.keys.tr31ClearStatus"));
    }

    public void handleTR31Reset() {
        View view = views.get();
        clearTR31Fields();
        if (view.tr31VersionCombo() != null) view.tr31VersionCombo().setValue("B - TDES Key Derivation Binding");
        if (view.tr31UsageCombo() != null) view.tr31UsageCombo().getSelectionModel().selectFirst();
        if (view.tr31AlgorithmCombo() != null) view.tr31AlgorithmCombo().getSelectionModel().selectFirst();
        if (view.tr31ModeCombo() != null) view.tr31ModeCombo().getSelectionModel().selectFirst();
        if (view.tr31ExportabilityCombo() != null) view.tr31ExportabilityCombo().getSelectionModel().selectFirst();
        if (reporter.get() != null) reporter.get().updateStatus(t("module.keys.tr31ResetStatus"));
    }

    private void clearTR31Fields() {
        View view = views.get();
        if (view.tr31KbpkExportField() != null) view.tr31KbpkExportField().clear();
        if (view.tr31KeyToWrapField() != null) view.tr31KeyToWrapField().clear();
        if (view.tr31OptionalBlocksField() != null) view.tr31OptionalBlocksField().clear();
        if (view.tr31KbpkImportField() != null) view.tr31KbpkImportField().clear();
        if (view.tr31KeyBlockField() != null) view.tr31KeyBlockField().clear();
        if (view.tr31KeyLengthField() != null) view.tr31KeyLengthField().clear();
        if (view.tr31ExportResultArea() != null) view.tr31ExportResultArea().clear();
        if (view.tr31ImportResultArea() != null) {
            view.tr31ImportResultArea().clear();
            view.tr31ImportResultArea().setManaged(false);
            view.tr31ImportResultArea().setVisible(false);
        }
    }

    private void showTR31Validation(String message, String fieldKey, TextArea feedbackArea) {
        showTR31Validation(message, fieldKey, feedbackArea == null ? null : safeMessage -> {
            feedbackArea.setText(safeMessage);
            feedbackArea.setVisible(true);
            feedbackArea.setManaged(true);
        });
    }

    private void showTR31Validation(String message, String fieldKey, TR31FeedbackTarget feedbackTarget) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.keys.tr31.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (reporter.get() != null) {
            reporter.get().showError(error);
        } else if (feedbackTarget != null) {
            feedbackTarget.present(safeMessage);
        }
    }

    private void logTR31Failure(String operation, Exception error) {
        LOG.error("TR-31 {} failed: {}", operation, InlineErrorPresenter.redactSecrets(error.toString()), error);
    }

    private void setupTR31Combos() {
        View view = views.get();
        if (view.tr31OptionalBlockCombo() != null) {
            view.tr31OptionalBlockCombo().setPromptText(t("module.keys.tr31.optionalBlockPrompt"));
            TR31Operations.OPTIONAL_BLOCKS.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> view.tr31OptionalBlockCombo().getItems().add(entry.getKey() + " - " + entry.getValue()));
        }
        if (view.tr31OptionalBlocksField() != null) {
            view.tr31OptionalBlocksField().setPromptText(t("module.keys.tr31.optionalBlockFormat"));
        }
        if (view.tr31VersionCombo() != null) {
            view.tr31VersionCombo().getItems().addAll(
                    "A - DES Key Variant Binding (deprecated)",
                    "B - TDES Key Derivation Binding",
                    "C - TDES Key Variant Binding (deprecated)",
                    "D - AES Key Derivation Binding");
            view.tr31VersionCombo().getSelectionModel().select(1); // Default to B
        }

        if (view.tr31UsageCombo() != null) {
            TR31Operations.KEY_USAGES.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> view.tr31UsageCombo().getItems().add(entry.getKey() + " - " + entry.getValue()));
            view.tr31UsageCombo().getSelectionModel().selectFirst();
        }

        if (view.tr31AlgorithmCombo() != null) {
            view.tr31AlgorithmCombo().getItems().addAll(
                    "T - Triple DES",
                    "A - AES",
                    "D - DES (single)",
                    "H - HMAC",
                    "R - RSA",
                    "S - DSA",
                    "E - Elliptic Curve");
            view.tr31AlgorithmCombo().getSelectionModel().selectFirst();
        }

        if (view.tr31ModeCombo() != null) {
            TR31Operations.MODES.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> view.tr31ModeCombo().getItems().add(entry.getKey() + " - " + entry.getValue()));
            view.tr31ModeCombo().getItems().add("T - Both sign & key transport (legacy compatibility)");
            view.tr31ModeCombo().getSelectionModel().selectFirst(); // "B - Both"
        }

        if (view.tr31ExportabilityCombo() != null) {
            view.tr31ExportabilityCombo().getItems().addAll(
                    "E - Exportable",
                    "N - Non-exportable",
                    "S - Sensitive");
            view.tr31ExportabilityCombo().getSelectionModel().selectFirst(); // "E - Exportable"
        }
    }

    public void handleTR31Export() {
        View view = views.get();
        try {
            updateStatus(t("module.keys.tr31.status.starting"));
            var outcome = Tr31Logic.export(view.tr31KbpkExportField().getText(), view.tr31KeyToWrapField().getText(), view.tr31VersionCombo().getValue(), view.tr31UsageCombo().getValue(), view.tr31AlgorithmCombo().getValue(), view.tr31ModeCombo().getValue(), view.tr31ExportabilityCombo().getValue(), view.tr31OptionalBlocksField() == null ? "" : view.tr31OptionalBlocksField().getText(), key -> t(key));
            var kbpk = outcome.kbpk();
            var key = outcome.key();
            var usage = outcome.usage();
            var keyBlock = outcome.keyBlock();
            var header = outcome.header();
            String report = outcome.report();

            javafx.application.Platform.runLater(() -> {
                view.tr31ExportResultArea().setVisible(true);
                view.tr31ExportResultArea().setManaged(true);
                view.tr31ExportResultArea().setText(report);

                // Force layout update specifically for VBox parent
                if (view.tr31ExportResultArea().getParent() != null) {
                    view.tr31ExportResultArea().getParent().requestLayout();
                    // If parent is VBox/HBox/Grid, this helps trigger resize
                    view.tr31ExportResultArea().getParent().layout();
                }
            });

            updateStatus(t("module.keys.tr31.status.wrapped"));

            // Delegate to ModernMainController history if available
            if (reporter.get() != null) {
                try {
                    java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Version", header.versionId));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Usage", usage));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("KBPK", kbpk));
                    details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key to Wrap", key));
                    details.add(com.cryptocarver.model.OperationDetail.publicDetail("Key Block", keyBlock));

                    reporter.get().publish(OperationResult.forOperation("TR-31 Export")
                            .input(DataConverter.hexToBytes(key))
                            .output(keyBlock.getBytes(StandardCharsets.UTF_8))
                            .details(details)
                            .status("TR-31 key wrapped successfully")
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to add to history: " + e.getMessage());
                }
            }

        } catch (KeyDistributionValidation validation) {
            showTR31Validation(t(validation.messageKey()), validation.fieldKey(), view.tr31ExportResultArea());
        } catch (Exception e) {
            showTR31Validation(t("module.keys.tr31.operation", e.getMessage()), "tr31KeyToWrapField", view.tr31ExportResultArea());
            updateStatus(t("module.keys.tr31.status.wrapFailed"));
            logTR31Failure("wrap", e);
        }
    }

    public void handleTR31Import() {
        View view = views.get();
        try {
            var outcome = Tr31Logic.importBlock(view.tr31KbpkImportField().getText(), view.tr31KeyBlockField().getText());
            var header = outcome.header();
            var unwrappedKey = outcome.unwrappedKey();
            String report = outcome.report();

            view.tr31ImportResultArea().setText(report);
            view.tr31ImportResultArea().setVisible(true);
            view.tr31ImportResultArea().setManaged(true);
            updateStatus(t("module.keys.tr31.status.unwrapped"));

            if (reporter.get() != null) {
                reporter.get().publish(com.cryptocarver.model.OperationResult.forOperation("Unwrap Key - " + TR31Operations.getKeyUsageDescription(header.keyUsage))
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "Version " + header.versionId, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Key Length: " + (unwrappedKey.length() / 2) + " bytes", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (KeyDistributionValidation validation) {
            showTR31Validation(t(validation.messageKey()), validation.fieldKey(), view.tr31ImportResultArea());
        } catch (Exception e) {
            showTR31Validation(t("module.keys.tr31.operation", e.getMessage()), "tr31KeyBlockField", view.tr31ImportResultArea());
            updateStatus(t("module.keys.tr31.status.unwrapFailed"));
            logTR31Failure("unwrap", e);
        }
    }

    public void handleTR31ParseHeader() {
        View view = views.get();
        try {
            var outcome = Tr31Logic.parseHeader(view.tr31KeyBlockField().getText(), key -> t(key));
            String report = outcome.report();

            view.tr31ImportResultArea().setText(report);
            view.tr31ImportResultArea().setVisible(true);
            view.tr31ImportResultArea().setManaged(true);
            updateStatus(t("module.keys.tr31.status.headerParsed"));

            if (reporter.get() != null) {
                reporter.get().publish(com.cryptocarver.model.OperationResult.forOperation("TR-31 Header Parse")
                        .enrichedOutput(report, com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .status("TR-31 header parsed successfully")
                        .build());
            }

        } catch (KeyDistributionValidation validation) {
            showTR31Validation(t(validation.messageKey()), validation.fieldKey(), view.tr31ImportResultArea());
        } catch (Exception e) {
            showTR31Validation(t("module.keys.tr31.operation", e.getMessage()), "tr31KeyBlockField", view.tr31ImportResultArea());
            updateStatus(t("module.keys.tr31.status.parseFailed"));
            logTR31Failure("header parse", e);
        }
    }

    public void loadProfile(com.cryptocarver.model.payments.PaymentProfile p) {
        View view = views.get();
        if (p.getType() == com.cryptocarver.model.payments.PaymentProfile.ProfileType.TR31) {
            java.util.Map<String, String> params = p.getParameters();
            if (view.tr31VersionCombo() != null && params.containsKey("version")) {
                for (String item : view.tr31VersionCombo().getItems()) {
                    if (item.startsWith(params.get("version").substring(0, 1))) {
                        view.tr31VersionCombo().setValue(item);
                        break;
                    }
                }
            }
            if (view.tr31AlgorithmCombo() != null && params.containsKey("algorithm")) {
                for (String item : view.tr31AlgorithmCombo().getItems()) {
                    if (item.startsWith(params.get("algorithm").substring(0, 1))) {
                        view.tr31AlgorithmCombo().setValue(item);
                        break;
                    }
                }
            }
            if (view.tr31UsageCombo() != null && params.containsKey("usage")) {
                for (String item : view.tr31UsageCombo().getItems()) {
                    if (item.startsWith(params.get("usage").substring(0, 2))) {
                        view.tr31UsageCombo().setValue(item);
                        break;
                    }
                }
            }
            if (view.tr31ModeCombo() != null && params.containsKey("mode")) {
                for (String item : view.tr31ModeCombo().getItems()) {
                    if (item.startsWith(params.get("mode").substring(0, 1))) {
                        view.tr31ModeCombo().setValue(item);
                        break;
                    }
                }
            }
            if (view.tr31ExportabilityCombo() != null && params.containsKey("exportability")) {
                for (String item : view.tr31ExportabilityCombo().getItems()) {
                    if (item.startsWith(params.get("exportability").substring(0, 1))) {
                        view.tr31ExportabilityCombo().setValue(item);
                        break;
                    }
                }
            }
            if (view.tr31KbpkExportField() != null && p.getInputs().containsKey("kbpk")) {
                view.tr31KbpkExportField().setText(p.getInputs().get("kbpk"));
            }
            if (view.tr31KeyToWrapField() != null && p.getInputs().containsKey("keyToWrap")) {
                view.tr31KeyToWrapField().setText(p.getInputs().get("keyToWrap"));
            }
            if (view.tr31OptionalBlocksField() != null && p.getInputs().containsKey("optionalBlocks")) {
                view.tr31OptionalBlocksField().setText(p.getInputs().get("optionalBlocks"));
            }
            updateStatus("Loaded TR-31 profile: " + p.getName());
        }
    }

    void initialize() { setupTR31Combos(); }

    @FunctionalInterface
    interface TR31FeedbackTarget {
        void present(String safeMessage);
    }
}
