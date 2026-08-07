package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CryptoEnvelopeInspector;
import com.cryptocarver.crypto.RsaKeyWrapOperations;
import com.cryptocarver.model.CryptoEnvelope;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;

import java.security.PrivateKey;
import java.util.Base64;

/**
 * Read-only decoding of a {@link CryptoEnvelope} plus an explicit, opt-in unwrap action — the
 * envelope counterpart to {@link CmsInspectorController}, following the same layout and error
 * conventions (inline validation, {@link ModuleResetPolicy}, redacted logging).
 */
public class CryptoEnvelopeInspectorController {

    @FXML private TitledPane cryptoEnvelopeInspectorRoot;
    @FXML private TextArea envelopeInputArea;
    @FXML private TextArea envelopeReportArea;
    @FXML private ComboBox<String> envelopeUnwrapProfileCombo;
    @FXML private TextArea envelopePrivateKeyArea;
    @FXML private TextArea envelopeUnwrappedKeyArea;

    private StatusReporter statusReporter;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    @FXML
    public void initialize() {
        ModuleI18n.bind(cryptoEnvelopeInspectorRoot, ModuleTextCatalog.cryptoEnvelopeInspector());
        IngestionUIHelper.bindField(envelopeInputArea, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64);
        envelopeUnwrapProfileCombo.getItems().setAll("Raw OAEP", "JWE Compact", "CMS EnvelopedData");
        envelopeUnwrapProfileCombo.setValue("Raw OAEP");
    }

    public void setStatusReporter(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    @FXML
    void handleInspect(ActionEvent event) {
        try {
            String text = envelopeInputArea.getText();
            if (text == null || text.isBlank()) {
                InlineValidationSupport.show(statusReporter, t("preflight.title"),
                        t("module.envelope.inputRequired"), t("preflight.remedy.input"),
                        "envelopeInputArea", null);
                return;
            }

            CryptoEnvelopeInspector.InspectionResult result = CryptoEnvelopeInspector.inspect(text);
            String report = formatReport(result);
            envelopeReportArea.setText(report);

            CryptoEnvelope envelope = result.getEnvelope();
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("Crypto Envelope Inspector")
                        .input(text.getBytes())
                        .enrichedOutput(report, OperationDetail.Classification.PUBLIC)
                        .detail("Algorithm", envelope.getAlg())
                        .detail("Key ID", envelope.getKid() == null ? "-" : envelope.getKid())
                        .detail("Ciphertext Size", result.getCiphertextLengthBytes() + " bytes")
                        .status(t("module.envelope.feedback.statusInspected", envelope.getAlg())).build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Envelope Error", t("module.envelope.inspectFailed", e.getMessage()));
            envelopeReportArea.setText(t("module.envelope.errorGeneric", e.getMessage()));
        }
    }

    @FXML
    void handleUnwrap(ActionEvent event) {
        try {
            String text = envelopeInputArea.getText();
            if (text == null || text.isBlank()) {
                InlineValidationSupport.show(statusReporter, t("preflight.title"),
                        t("module.envelope.inputRequired"), t("preflight.remedy.input"),
                        "envelopeInputArea", null);
                return;
            }
            String privateKeyPem = envelopePrivateKeyArea.getText();
            if (privateKeyPem == null || privateKeyPem.isBlank()) {
                InlineValidationSupport.show(statusReporter, t("preflight.title"),
                        t("module.envelope.privateKeyRequired"), t("preflight.remedy.input"),
                        "envelopePrivateKeyArea", null);
                return;
            }

            CryptoEnvelopeInspector.InspectionResult result = CryptoEnvelopeInspector.inspect(text);
            byte[] ciphertext = Base64.getDecoder().decode(result.getEnvelope().getCiphertextB64());
            PrivateKey privateKey = AsymmetricKeyOperations.importPrivateKeyPEMAuto(privateKeyPem);
            RsaKeyWrapOperations.WrapProfile profile = profileFromComboValue(envelopeUnwrapProfileCombo.getValue());

            byte[] recovered = RsaKeyWrapOperations.unwrap(ciphertext, privateKey, profile);
            String recoveredHex = DataConverter.bytesToHex(recovered);
            envelopeUnwrappedKeyArea.setText(recoveredHex);

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("Crypto Envelope Unwrap")
                        .input(ciphertext)
                        .output(recovered, OperationDetail.Classification.SECRET)
                        .detail(OperationDetail.secretDetail("Recovered Key (hex)", recoveredHex))
                        .detail("Profile", envelopeUnwrapProfileCombo.getValue())
                        .status(t("module.envelope.feedback.statusUnwrapped")).build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Envelope Error", t("module.envelope.unwrapFailed", e.getMessage()));
        }
    }

    private static RsaKeyWrapOperations.WrapProfile profileFromComboValue(String value) {
        if (value == null) return RsaKeyWrapOperations.WrapProfile.RAW_OAEP;
        return switch (value) {
            case "JWE Compact" -> RsaKeyWrapOperations.WrapProfile.JWE_COMPACT;
            case "CMS EnvelopedData" -> RsaKeyWrapOperations.WrapProfile.CMS_ENVELOPED;
            default -> RsaKeyWrapOperations.WrapProfile.RAW_OAEP;
        };
    }

    private String formatReport(CryptoEnvelopeInspector.InspectionResult result) {
        CryptoEnvelope envelope = result.getEnvelope();
        StringBuilder sb = new StringBuilder();
        sb.append("== Crypto Envelope ==\n");
        sb.append("Envelope Version: ").append(envelope.getEnvVersion()).append("\n");
        sb.append("Algorithm:        ").append(envelope.getAlg()).append("\n");
        sb.append("Key ID:           ").append(envelope.getKid() == null ? "-" : envelope.getKid()).append("\n");
        sb.append("Key Version:      ").append(envelope.getKeyVersion() == null ? "-" : envelope.getKeyVersion()).append("\n");
        sb.append("KCV:              ").append(envelope.getKcv() == null ? "-" : envelope.getKcv()).append("\n");
        sb.append("Created At:       ").append(envelope.getCreatedAt() == null ? "-" : envelope.getCreatedAt());
        if (result.getAge() != null) {
            sb.append("  (").append(formatAge(result.getAge())).append(" ago)");
        }
        sb.append("\n");
        sb.append("IV / Nonce:       ").append(envelope.getIvNonceHex() == null ? "-" : envelope.getIvNonceHex()).append("\n");
        sb.append("AAD:              ").append(envelope.getAadHex() == null ? "-" : envelope.getAadHex()).append("\n");
        sb.append("Ciphertext Size:  ").append(result.getCiphertextLengthBytes()).append(" bytes\n");
        if (!envelope.getExtensions().isEmpty()) {
            sb.append("Extensions:\n");
            envelope.getExtensions().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }

    private static String formatAge(java.time.Duration age) {
        long days = age.toDays();
        if (days > 0) return days + "d";
        long hours = age.toHours();
        if (hours > 0) return hours + "h";
        long minutes = age.toMinutes();
        if (minutes > 0) return minutes + "m";
        return Math.max(age.toSeconds(), 0) + "s";
    }

    @FXML
    void handleClear(ActionEvent event) {
        ModuleResetPolicy.apply(cryptoEnvelopeInspectorRoot, ModuleResetPolicy.Action.CLEAR,
                this::clearModuleData, null);
        if (statusReporter != null) statusReporter.updateStatus(t("module.envelope.clearStatus"));
    }

    @FXML
    void handleReset(ActionEvent event) {
        ModuleResetPolicy.apply(cryptoEnvelopeInspectorRoot, ModuleResetPolicy.Action.RESET_DEFAULTS,
                this::clearModuleData, this::restoreSafeDefaults);
        if (statusReporter != null) statusReporter.updateStatus(t("module.common.resetStatus"));
    }

    private void clearModuleData() {
        if (envelopeInputArea != null) envelopeInputArea.clear();
        if (envelopeReportArea != null) envelopeReportArea.clear();
        if (envelopePrivateKeyArea != null) envelopePrivateKeyArea.clear();
        if (envelopeUnwrappedKeyArea != null) envelopeUnwrappedKeyArea.clear();
    }

    private void restoreSafeDefaults() {
        if (envelopeUnwrapProfileCombo != null) envelopeUnwrapProfileCombo.setValue("Raw OAEP");
    }
}
