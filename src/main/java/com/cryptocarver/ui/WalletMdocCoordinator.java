package com.cryptocarver.ui;

import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.MdocOperations;
import com.cryptocarver.service.I18nService;
import java.io.ByteArrayInputStream;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Coordinates Wallet operations over the controller-owned FXML controls. */
final class WalletMdocCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(WalletController.class);
    record View(Supplier<TextField> mdocDocTypeField,
            Supplier<ComboBox<String>> mdocDigestCombo,
            Supplier<TextArea> mdocIssuerKeyArea,
            Supplier<TextArea> mdocSignerCertArea,
            Supplier<TextArea> mdocDeviceKeyArea,
            Supplier<TextArea> mdocClaimsArea,
            Supplier<TextField> mdocValidityField,
            Supplier<TextArea> mdocIssueOutputArea,
            Supplier<TextArea> mdocVerifyInputArea,
            Supplier<TextArea> mdocVerifyIssuerKeyArea,
            Supplier<TextArea> mdocVerifyOutputArea) { }
    private final View view;
    private final Supplier<StatusReporter> reporter;

    WalletMdocCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void handleMdocIssue() {
        try {
            String claims = textOf(view.mdocClaimsArea().get());
            String key = textOf(view.mdocIssuerKeyArea().get());
            String certificatePem = textOf(view.mdocSignerCertArea().get());
            if (isBlank(claims)) { showValidation(t("module.wallet.claimsRequired"), "mdocClaimsArea"); return; }
            if (isBlank(key)) { showValidation(t("module.wallet.keyRequired"), "mdocIssuerKeyArea"); return; }
            if (isBlank(certificatePem)) { showValidation(t("module.wallet.certificateRequired"), "mdocSignerCertArea"); return; }

            Instant now = Instant.now();
            long days = parseInt(textOf(view.mdocValidityField().get()), 365);
            String deviceKeyPem = textOf(view.mdocDeviceKeyArea().get());

            byte[] mdoc = MdocOperations.issue(
                    textOf(view.mdocDocTypeField().get()),
                    claims,
                    valueOf(view.mdocDigestCombo().get(), "SHA-256"),
                    new MdocOperations.ValidityInfo(now, now, now.plus(days, ChronoUnit.DAYS), null),
                    AsymmetricKeyOperations.importPrivateKeyPEMAuto(key),
                    parseCertificate(certificatePem),
                    isBlank(deviceKeyPem) ? null : AsymmetricKeyOperations.importPublicKeyPEMAuto(deviceKeyPem));

            String hex = DataConverter.bytesToHex(mdoc).toUpperCase();
            view.mdocIssueOutputArea().get().setText(hex);
            updateStatus(t("module.wallet.status.issued"));
            publish("mdoc Issue", hex, "Document type", textOf(view.mdocDocTypeField().get()));
        } catch (Exception e) {
            fail(e, "mdocClaimsArea", "mdoc issue");
        }
    }

    void handleMdocVerify() {
        runMdocReport(true);
    }

    void handleMdocInspect() {
        runMdocReport(false);
    }

    /**
     * Both buttons produce the same report; they differ only in whether an
     * issuer key is required. Inspecting without one verifies against the
     * certificate the document carries, which the facade reports as a warning
     * rather than presenting as a pass.
     */
    private void runMdocReport(boolean requireIssuerKey) {
        try {
            String hex = textOf(view.mdocVerifyInputArea().get());
            if (isBlank(hex)) { showValidation(t("module.wallet.mdocRequired"), "mdocVerifyInputArea"); return; }
            String issuerKey = textOf(view.mdocVerifyIssuerKeyArea().get());
            if (requireIssuerKey && isBlank(issuerKey)) {
                showValidation(t("module.wallet.keyRequired"), "mdocVerifyIssuerKeyArea");
                return;
            }
            PublicKey key = isBlank(issuerKey) ? null : AsymmetricKeyOperations.importPublicKeyPEMAuto(issuerKey);
            byte[] document = CborInspector.parseHex(hex);
            String report = MdocOperations.describe(document, key,
                    Instant.now(), I18nService.getInstance().getLocale());
            if (!com.cryptocarver.model.AppSettings.isFullLab()) {
                boolean privateMaterial = PrivateKeyMaterialDetector.containsPrivateMaterial(report, 0)
                        || MdocOperations.parse(document).namespaces().values().stream()
                                .flatMap(List::stream).anyMatch(item ->
                                        PrivateKeyMaterialDetector.containsPrivateMaterial(item.valueAsText(), 0));
                if (privateMaterial) report = t("module.wallet.privateJwkHidden");
            }
            view.mdocVerifyOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.verified"));
            publish(requireIssuerKey ? "mdoc Verify" : "mdoc Inspect", report);
        } catch (Exception e) {
            fail(e, "mdocVerifyInputArea", "mdoc verify");
        }
    }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String valueOf(ComboBox<String> combo, String fallback) {
        return combo == null || combo.getValue() == null ? fallback : combo.getValue();
    }

    private static String textOf(TextArea area) {
        return area == null || area.getText() == null ? "" : area.getText().trim();
    }

    private static String textOf(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void publish(String operation, String output, String... details) {
        if (reporter.get() == null) {
            return;
        }
        OperationResult.Builder builder = OperationResult.forOperation(operation)
                .output(output.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i + 1 < details.length; i += 2) {
            builder.detail(details[i], details[i + 1]);
        }
        reporter.get().publish(builder.status(t("module.wallet.status.done")).build());
    }

    private void fail(Exception error, String fieldKey, String operation) {
        showValidation(t("module.wallet.operation", error.getMessage()), fieldKey);
        updateStatus(t("module.wallet.status.failed"));
        logFailure(operation, error);
    }

    private void showValidation(String message, String fieldKey) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.wallet.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (reporter.get() != null) {
            reporter.get().showError(error);
        }
    }

    private void updateStatus(String message) {
        if (reporter.get() != null) reporter.get().updateStatus(message);
    }

    private void logFailure(String operation, Exception error) {
        LOG.error("Wallet {} failed: {}", operation, InlineErrorPresenter.redactSecrets(error.toString()), error);
    }

    private static X509Certificate parseCertificate(String pem) throws Exception {
        String normalized = pem.replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", "");
        byte[] der = java.util.Base64.getDecoder().decode(normalized);
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }
}
