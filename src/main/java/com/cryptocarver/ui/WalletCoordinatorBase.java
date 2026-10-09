package com.cryptocarver.ui;

import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** Shared reporting and input helpers of the Wallet coordinators extracted in assignment 80. */
abstract class WalletCoordinatorBase {
    private static final Logger LOG = LoggerFactory.getLogger(WalletController.class);

    private final Supplier<StatusReporter> reporter;

    WalletCoordinatorBase(Supplier<StatusReporter> reporter) {
        this.reporter = reporter;
    }

    protected String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    protected static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\r\\n,]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    protected static X509Certificate parseCertificate(String pem) throws Exception {
        String normalized = pem.replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", "");
        byte[] der = java.util.Base64.getDecoder().decode(normalized);
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    protected static String valueOf(ComboBox<String> combo, String fallback) {
        return combo == null || combo.getValue() == null ? fallback : combo.getValue();
    }

    protected static String textOf(TextArea area) {
        return area == null || area.getText() == null ? "" : area.getText().trim();
    }

    protected static String textOf(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    protected static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    protected static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    protected void publish(String operation, String output, String... details) {
        StatusReporter statusReporter = reporter.get();
        if (statusReporter == null) {
            return;
        }
        OperationResult.Builder builder = OperationResult.forOperation(operation)
                .output(output.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i + 1 < details.length; i += 2) {
            builder.detail(details[i], details[i + 1]);
        }
        statusReporter.publish(builder.status(t("module.wallet.status.done")).build());
    }

    protected void fail(Exception error, String fieldKey, String operation) {
        showValidation(t("module.wallet.operation", error.getMessage()), fieldKey);
        updateStatus(t("module.wallet.status.failed"));
        logFailure(operation, error);
    }

    protected void showValidation(String message, String fieldKey) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.wallet.errorTitle"), safeMessage, safeMessage, fieldKey);
        StatusReporter statusReporter = reporter.get();
        if (statusReporter != null) {
            statusReporter.showError(error);
        }
    }

    protected void updateStatus(String message) {
        StatusReporter statusReporter = reporter.get();
        if (statusReporter != null) statusReporter.updateStatus(message);
    }

    private void logFailure(String operation, Exception error) {
        LOG.error("Wallet {} failed: {}", operation, InlineErrorPresenter.redactSecrets(error.toString()), error);
    }
}
