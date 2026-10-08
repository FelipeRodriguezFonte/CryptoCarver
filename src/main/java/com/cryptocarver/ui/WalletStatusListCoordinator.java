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
import com.cryptocarver.crypto.StatusListOperations;
import java.time.Instant;

/** Coordinates Wallet operations over the controller-owned FXML controls. */
final class WalletStatusListCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(WalletController.class);
    record View(Supplier<ComboBox<String>> statusListBitsCombo,
            Supplier<TextArea> statusListStatusesArea,
            Supplier<TextField> statusListUriField,
            Supplier<ComboBox<String>> statusListAlgoCombo,
            Supplier<TextArea> statusListKeyArea,
            Supplier<TextArea> statusListOutputArea,
            Supplier<TextArea> statusListTokenArea,
            Supplier<TextField> statusListIndexField,
            Supplier<TextArea> statusListVerifyKeyArea,
            Supplier<TextArea> statusListResolveOutputArea) { }
    private final View view;
    private final Supplier<StatusReporter> reporter;

    WalletStatusListCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    void handleStatusListIssue() {
        try {
            String statuses = textOf(view.statusListStatusesArea().get());
            String uri = textOf(view.statusListUriField().get());
            String key = textOf(view.statusListKeyArea().get());
            if (isBlank(statuses)) { showValidation(t("module.wallet.statusesRequired"), "statusListStatusesArea"); return; }
            if (isBlank(uri)) { showValidation(t("module.wallet.uriRequired"), "statusListUriField"); return; }
            if (isBlank(key)) { showValidation(t("module.wallet.keyRequired"), "statusListKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(view.statusListAlgoCombo().get(), "ES256"));
            String token = StatusListOperations.issueStatusListToken(
                    parseStatuses(statuses), parseInt(valueOf(view.statusListBitsCombo().get(), "1"), 1), uri,
                    Instant.now(), null, -1, algorithm, JOSEService.createSigner(algorithm, key));

            view.statusListOutputArea().get().setText(token);
            updateStatus(t("module.wallet.status.issued"));
            publish("Status List Issue", token, "URI", uri);
        } catch (Exception e) {
            fail(e, "statusListStatusesArea", "status list issue");
        }
    }

    void handleStatusListResolve() {
        try {
            String token = textOf(view.statusListTokenArea().get());
            String index = textOf(view.statusListIndexField().get());
            if (isBlank(token)) { showValidation(t("module.wallet.tokenRequired"), "statusListTokenArea"); return; }
            if (isBlank(index)) { showValidation(t("module.wallet.indexRequired"), "statusListIndexField"); return; }

            // The subject check inside resolve() needs the URI the credential
            // points at; here the list's own subject is used, because a lone
            // index has no credential to take it from.
            String uri = subjectOf(token);
            String verifyKey = textOf(view.statusListVerifyKeyArea().get());
            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(view.statusListAlgoCombo().get(), "ES256"));

            StatusListOperations.StatusLookup lookup = StatusListOperations.resolve(
                    StatusListOperations.statusClaim(uri, parseInt(index, 0)), token,
                    isBlank(verifyKey) ? null : JOSEService.createVerifier(algorithm, verifyKey));

            String report = "index " + lookup.index() + " -> " + lookup.status()
                    + " (" + lookup.description() + ")\n"
                    + (isBlank(verifyKey)
                            ? "The token's signature was not verified: no key was supplied.\n"
                            : "");
            view.statusListResolveOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.resolved"));
            publish("Status List Resolve", report, "Index", index);
        } catch (Exception e) {
            fail(e, "statusListTokenArea", "status list resolve");
        }
    }

    void handleStatusListDescribe() {
        try {
            String token = textOf(view.statusListTokenArea().get());
            if (isBlank(token)) { showValidation(t("module.wallet.tokenRequired"), "statusListTokenArea"); return; }
            String report = StatusListOperations.describe(token);
            if (!com.cryptocarver.model.AppSettings.isFullLab()
                    && (PrivateKeyMaterialDetector.containsPrivateMaterial(token, 0)
                        || PrivateKeyMaterialDetector.containsPrivateMaterial(report, 0))) {
                report = t("module.wallet.privateJwkHidden");
            }
            view.statusListResolveOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Status List Describe", report);
        } catch (Exception e) {
            fail(e, "statusListTokenArea", "status list describe");
        }
    }

    private static int[] parseStatuses(String raw) {
        List<String> values = lines(raw.replace(" ", "\n"));
        int[] statuses = new int[values.size()];
        for (int i = 0; i < statuses.length; i++) {
            statuses[i] = Integer.parseInt(values.get(i));
        }
        return statuses;
    }

    /** Reads the {@code sub} of a Status List Token without verifying it: the
     *  resolve path needs the URI the list claims, and at this point there is no
     *  credential to take it from. */
    private static String subjectOf(String token) {
        String payload = token.split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        return com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("sub").getAsString();
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
}
