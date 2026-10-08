package com.cryptocarver.ui;

import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import com.cryptocarver.crypto.JOSEService;
import com.nimbusds.jose.JWSAlgorithm;
import java.util.Arrays;
import com.cryptocarver.crypto.SdJwtOperations;
import com.cryptocarver.service.I18nService;
import java.util.ArrayList;

/** Coordinates Wallet operations over the controller-owned FXML controls. */
final class WalletSdJwtCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(WalletController.class);
    record View(Supplier<ComboBox<String>> sdJwtAlgoCombo,
            Supplier<TextArea> sdJwtIssuerKeyArea,
            Supplier<TextArea> sdJwtClaimsArea,
            Supplier<TextArea> sdJwtDisclosableArea,
            Supplier<TextField> sdJwtVctField,
            Supplier<TextField> sdJwtDecoyField,
            Supplier<TextArea> sdJwtIssueOutputArea,
            Supplier<TextArea> sdJwtPresentInputArea,
            Supplier<TextArea> sdJwtRevealArea,
            Supplier<TextField> sdJwtAudienceField,
            Supplier<TextField> sdJwtNonceField,
            Supplier<TextArea> sdJwtHolderKeyArea,
            Supplier<TextArea> sdJwtPresentOutputArea,
            Supplier<TextArea> sdJwtVerifyInputArea,
            Supplier<TextArea> sdJwtVerifyIssuerKeyArea,
            Supplier<TextArea> sdJwtVerifyHolderKeyArea,
            Supplier<TextField> sdJwtVerifyAudienceField,
            Supplier<TextField> sdJwtVerifyNonceField,
            Supplier<TextArea> sdJwtVerifyOutputArea,
            Supplier<TextArea> sdJwtInspectInputArea,
            Supplier<TextArea> sdJwtInspectOutputArea) { }
    private final View view;
    private final Supplier<StatusReporter> reporter;

    WalletSdJwtCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void handleSdJwtIssue() {
        try {
            String claims = textOf(view.sdJwtClaimsArea().get());
            String key = textOf(view.sdJwtIssuerKeyArea().get());
            if (isBlank(claims)) { showValidation(t("module.wallet.claimsRequired"), "sdJwtClaimsArea"); return; }
            if (isBlank(key)) { showValidation(t("module.wallet.keyRequired"), "sdJwtIssuerKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(view.sdJwtAlgoCombo().get(), "ES256"));
            List<String> paths = lines(textOf(view.sdJwtDisclosableArea().get()));
            int decoys = parseInt(textOf(view.sdJwtDecoyField().get()), 0);
            String vct = textOf(view.sdJwtVctField().get());

            SdJwtOperations.IssuedSdJwt issued = isBlank(vct)
                    ? SdJwtOperations.issue(claims, paths, decoys,
                            SdJwtOperations.HashAlgorithm.SHA_256, algorithm,
                            JOSEService.createSigner(algorithm, key), null)
                    : SdJwtOperations.issueVerifiableCredential(claims, vct, null, null, null, paths,
                            decoys, SdJwtOperations.HashAlgorithm.SHA_256, algorithm,
                            JOSEService.createSigner(algorithm, key));

            view.sdJwtIssueOutputArea().get().setText(issued.serialized());
            updateStatus(t("module.wallet.status.issued"));
            publish("SD-JWT Issue", issued.serialized(),
                    "Disclosures", String.valueOf(issued.disclosures().size()));
        } catch (Exception e) {
            fail(e, "sdJwtClaimsArea", "sd-jwt issue");
        }
    }

    void handleSdJwtPresent() {
        try {
            String serialized = textOf(view.sdJwtPresentInputArea().get());
            if (isBlank(serialized)) { showValidation(t("module.wallet.sdJwtRequired"), "sdJwtPresentInputArea"); return; }

            SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(serialized);
            List<String> wanted = lines(textOf(view.sdJwtRevealArea().get()));
            List<String> digests = new ArrayList<>();
            for (SdJwtOperations.Disclosure disclosure : parsed.disclosures()) {
                if (wanted.isEmpty() || wanted.contains(disclosure.claimName())
                        || wanted.contains(disclosure.label())) {
                    digests.add(disclosure.digest());
                }
            }

            String audience = textOf(view.sdJwtAudienceField().get());
            String nonce = textOf(view.sdJwtNonceField().get());
            String holderKey = textOf(view.sdJwtHolderKeyArea().get());
            SdJwtOperations.KeyBinding binding = null;
            if (!isBlank(audience) || !isBlank(nonce) || !isBlank(holderKey)) {
                // Half a key binding produces a presentation that looks bound and
                // is not, so all three are demanded together.
                if (isBlank(audience) || isBlank(nonce) || isBlank(holderKey)) {
                    showValidation(t("module.wallet.keyBindingIncomplete"), "sdJwtAudienceField");
                    return;
                }
                JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(view.sdJwtAlgoCombo().get(), "ES256"));
                binding = new SdJwtOperations.KeyBinding(audience, nonce, algorithm,
                        JOSEService.createSigner(algorithm, holderKey));
            }

            SdJwtOperations.IssuedSdJwt reconstructed = new SdJwtOperations.IssuedSdJwt(
                    serialized.split("~", -1)[0], parsed.disclosures(), serialized,
                    SdJwtOperations.HashAlgorithm.SHA_256);
            String presentation = SdJwtOperations.present(reconstructed, digests, binding);

            view.sdJwtPresentOutputArea().get().setText(presentation);
            updateStatus(t("module.wallet.status.presented"));
            publish("SD-JWT Present", presentation, "Disclosures revealed", String.valueOf(digests.size()));
        } catch (Exception e) {
            fail(e, "sdJwtPresentInputArea", "sd-jwt present");
        }
    }

    void handleSdJwtVerify() {
        try {
            String presentation = textOf(view.sdJwtVerifyInputArea().get());
            String issuerKey = textOf(view.sdJwtVerifyIssuerKeyArea().get());
            if (isBlank(presentation)) { showValidation(t("module.wallet.sdJwtRequired"), "sdJwtVerifyInputArea"); return; }
            if (isBlank(issuerKey)) { showValidation(t("module.wallet.keyRequired"), "sdJwtVerifyIssuerKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(view.sdJwtAlgoCombo().get(), "ES256"));
            String holderKey = textOf(view.sdJwtVerifyHolderKeyArea().get());
            SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(presentation,
                    JOSEService.createVerifier(algorithm, issuerKey),
                    isBlank(holderKey) ? null : JOSEService.createVerifier(algorithm, holderKey),
                    blankToNull(textOf(view.sdJwtVerifyAudienceField().get())),
                    blankToNull(textOf(view.sdJwtVerifyNonceField().get())));

            StringBuilder report = new StringBuilder(verified.claimsJson());
            if (!verified.notes().isEmpty()) {
                report.append("\n\n");
                verified.notes().forEach(note -> report.append("- ").append(note).append('\n'));
            }
            String visibleReport = sdJwtReportForDisplay(presentation, report.toString());
            view.sdJwtVerifyOutputArea().get().setText(visibleReport);
            updateStatus(t("module.wallet.status.verified"));
            publish("SD-JWT Verify", visibleReport,
                    "Key binding", verified.keyBindingPresent() ? "present" : "absent");
        } catch (Exception e) {
            fail(e, "sdJwtVerifyInputArea", "sd-jwt verify");
        }
    }

    void handleSdJwtInspect() {
        try {
            String serialized = textOf(view.sdJwtInspectInputArea().get());
            if (isBlank(serialized)) { showValidation(t("module.wallet.sdJwtRequired"), "sdJwtInspectInputArea"); return; }
            String report = sdJwtReportForDisplay(serialized,
                    SdJwtOperations.describe(serialized, I18nService.getInstance().getLocale()));
            view.sdJwtInspectOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("SD-JWT Inspect", report);
        } catch (Exception e) {
            fail(e, "sdJwtInspectInputArea", "sd-jwt inspect");
        }
    }

    /** Keep private material out of both the local result and every published shell surface. */
    private String sdJwtReportForDisplay(String token, String report) throws Exception {
        if (com.cryptocarver.model.AppSettings.isFullLab()) return report;
        boolean privateMaterial = PrivateKeyMaterialDetector.containsPrivateMaterial(report, 0)
                || PrivateKeyMaterialDetector.containsPrivateMaterial(token, 0);
        if (!privateMaterial) {
            SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(token);
            privateMaterial = PrivateKeyMaterialDetector.containsPrivateMaterial(parsed.header().toString(), 0)
                    || PrivateKeyMaterialDetector.containsPrivateMaterial(parsed.payload().toString(), 0)
                    || PrivateKeyMaterialDetector.containsPrivateMaterial(
                            parsed.keyBindingClaims() == null ? null : parsed.keyBindingClaims().toString(), 0)
                    || parsed.disclosures().stream().anyMatch(disclosure ->
                            PrivateKeyMaterialDetector.containsPrivateMaterial(disclosure.value().toString(), 0));
        }
        return privateMaterial ? t("module.wallet.privateJwkHidden") : report;
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

    private static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\r\\n,]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
