package com.cryptocarver.ui;

import com.cryptocarver.model.OperationResult;
import javafx.scene.control.CheckBox;
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

/** Owns rsaKex controls and presentation; dependencies are resolved at use time. */
final class RsaKeyExchangeCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(RsaKeyExchangeCoordinator.class);
    record View(
            TextArea rsaKexRecipientPemArea,
            TextField rsaKexKeyToWrapField,
            ComboBox<String> rsaKexExportProfileCombo,
            CheckBox rsaKexIncludeEnvelopeCheck,
            HBox rsaKexEnvelopeFieldsBox,
            TextField rsaKexKidField,
            TextField rsaKexKeyVersionField,
            TextArea rsaKexExportResultArea,
            TextArea rsaKexPrivateKeyArea,
            TextArea rsaKexWrappedDataArea,
            ComboBox<String> rsaKexImportProfileCombo,
            TextArea rsaKexImportResultArea) { }
    private final Supplier<View> views;
    private final Supplier<StatusReporter> reporter;
    private final Consumer<String> status;
    private final BiFunction<String, Object[], String> text;

    RsaKeyExchangeCoordinator(Supplier<View> views, Supplier<StatusReporter> reporter,
            Consumer<String> status, BiFunction<String, Object[], String> text) {
        this.views = views;
        this.reporter = reporter;
        this.status = status;
        this.text = text;
    }

    private String t(String key, Object... args) { return text.apply(key, args); }
    private void updateStatus(String message) { status.accept(message); }

    void initializeRsaKexControls() {
        View view = views.get();
        if (view.rsaKexExportProfileCombo() != null) {
            view.rsaKexExportProfileCombo().getItems().setAll("Raw OAEP", "JWE Compact", "CMS EnvelopedData");
            view.rsaKexExportProfileCombo().setValue("Raw OAEP");
        }
        if (view.rsaKexImportProfileCombo() != null) {
            view.rsaKexImportProfileCombo().getItems().setAll("Raw OAEP", "JWE Compact", "CMS EnvelopedData");
            view.rsaKexImportProfileCombo().setValue("Raw OAEP");
        }
    }

    public void handleRsaKexEnvelopeToggle() {
        View view = views.get();
        boolean selected = view.rsaKexIncludeEnvelopeCheck() != null && view.rsaKexIncludeEnvelopeCheck().isSelected();
        if (view.rsaKexEnvelopeFieldsBox() != null) {
            view.rsaKexEnvelopeFieldsBox().setVisible(selected);
            view.rsaKexEnvelopeFieldsBox().setManaged(selected);
        }
    }

    public void handleRsaKexExport() {
        View view = views.get();
        try {
            var outcome = RsaKeyExchangeLogic.export(view.rsaKexRecipientPemArea().getText(), view.rsaKexKeyToWrapField().getText(), view.rsaKexExportProfileCombo().getValue(), view.rsaKexIncludeEnvelopeCheck() != null && view.rsaKexIncludeEnvelopeCheck().isSelected(), view.rsaKexKidField() == null ? "" : view.rsaKexKidField().getText(), view.rsaKexKeyVersionField() == null ? "" : view.rsaKexKeyVersionField().getText());
            var keyToWrap = outcome.keyToWrap();
            var keyHex = outcome.keyHex();
            var profile = outcome.profile();
            var wrapResult = outcome.wrapResult();
            var outputText = outcome.outputText();
            String report = outcome.report();

            view.rsaKexExportResultArea().setText(report);
            updateStatus(t("module.keys.rsaKex.status.wrapped"));

            if (reporter.get() != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Profile", profile.name()));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", wrapResult.getAlgorithm()));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Key to Wrap", keyHex));
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Output", outputText));
                reporter.get().publish(OperationResult.forOperation("RSA Key Exchange Export")
                        .input(keyToWrap)
                        .output(outputText.getBytes(StandardCharsets.UTF_8))
                        .details(details)
                        .status("RSA key wrapped successfully (" + profile + ")")
                        .build());
            }
        } catch (KeyDistributionValidation validation) {
            showRsaKexValidation(t(validation.messageKey()), validation.fieldKey(), view.rsaKexExportResultArea()::setText);
        } catch (Exception e) {
            showRsaKexValidation(t("module.keys.rsaKex.operation", e.getMessage()), "rsaKexKeyToWrapField", view.rsaKexExportResultArea()::setText);
            updateStatus(t("module.keys.rsaKex.status.wrapFailed"));
            logRsaKexFailure("wrap", e);
        }
    }

    public void handleRsaKexImport() {
        View view = views.get();
        try {
            var outcome = RsaKeyExchangeLogic.importKey(view.rsaKexPrivateKeyArea().getText(), view.rsaKexWrappedDataArea().getText(), view.rsaKexImportProfileCombo().getValue());
            var wrapped = outcome.wrapped();
            var recovered = outcome.recovered();
            var recoveredHex = outcome.recoveredHex();
            var profile = outcome.profile();
            String report = outcome.report();

            view.rsaKexImportResultArea().setText(report);
            updateStatus(t("module.keys.rsaKex.status.unwrapped"));

            if (reporter.get() != null) {
                List<com.cryptocarver.model.OperationDetail> details = new ArrayList<>();
                details.add(com.cryptocarver.model.OperationDetail.publicDetail("Profile", profile.name()));
                details.add(com.cryptocarver.model.OperationDetail.secretDetail("Recovered Key (hex)", recoveredHex));
                reporter.get().publish(OperationResult.forOperation("RSA Key Exchange Import")
                        .input(wrapped)
                        .output(recovered, com.cryptocarver.model.OperationDetail.Classification.SECRET)
                        .details(details)
                        .status("RSA key unwrapped successfully (" + profile + ")")
                        .build());
            }
        } catch (KeyDistributionValidation validation) {
            showRsaKexValidation(t(validation.messageKey()), validation.fieldKey(), view.rsaKexImportResultArea()::setText);
        } catch (Exception e) {
            showRsaKexValidation(t("module.keys.rsaKex.operation", e.getMessage()), "rsaKexWrappedDataArea", view.rsaKexImportResultArea()::setText);
            updateStatus(t("module.keys.rsaKex.status.unwrapFailed"));
            logRsaKexFailure("unwrap", e);
        }
    }

    public void handleRsaKexClear() {
        clearRsaKexFields();
        if (reporter.get() != null) reporter.get().updateStatus(t("module.keys.rsaKex.clearStatus"));
    }

    public void handleRsaKexReset() {
        View view = views.get();
        clearRsaKexFields();
        if (view.rsaKexExportProfileCombo() != null) view.rsaKexExportProfileCombo().setValue("Raw OAEP");
        if (view.rsaKexImportProfileCombo() != null) view.rsaKexImportProfileCombo().setValue("Raw OAEP");
        if (view.rsaKexIncludeEnvelopeCheck() != null) view.rsaKexIncludeEnvelopeCheck().setSelected(false);
        if (view.rsaKexEnvelopeFieldsBox() != null) {
            view.rsaKexEnvelopeFieldsBox().setVisible(false);
            view.rsaKexEnvelopeFieldsBox().setManaged(false);
        }
        if (reporter.get() != null) reporter.get().updateStatus(t("module.keys.rsaKex.resetStatus"));
    }

    private void clearRsaKexFields() {
        View view = views.get();
        if (view.rsaKexRecipientPemArea() != null) view.rsaKexRecipientPemArea().clear();
        if (view.rsaKexKeyToWrapField() != null) view.rsaKexKeyToWrapField().clear();
        if (view.rsaKexKidField() != null) view.rsaKexKidField().clear();
        if (view.rsaKexKeyVersionField() != null) view.rsaKexKeyVersionField().clear();
        if (view.rsaKexExportResultArea() != null) view.rsaKexExportResultArea().clear();
        if (view.rsaKexPrivateKeyArea() != null) view.rsaKexPrivateKeyArea().clear();
        if (view.rsaKexWrappedDataArea() != null) view.rsaKexWrappedDataArea().clear();
        if (view.rsaKexImportResultArea() != null) view.rsaKexImportResultArea().clear();
    }

    private void showRsaKexValidation(String message, String fieldKey, Consumer<String> feedbackTarget) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.keys.rsaKex.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (reporter.get() != null) {
            reporter.get().showError(error);
        } else if (feedbackTarget != null) {
            feedbackTarget.accept(safeMessage);
        }
    }

    private void logRsaKexFailure(String operation, Exception error) {
        LOG.error("RSA Key Exchange {} failed: {}", operation, InlineErrorPresenter.redactSecrets(error.toString()), error);
    }
}
