package com.cryptocarver.ui;

import com.cryptocarver.crypto.AdesValidationOperations;
import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.OpenId4VpInspector;
import com.cryptocarver.crypto.Ts12ScaOperations;
import com.cryptocarver.service.I18nService;
import com.nimbusds.jose.JWSAlgorithm;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import java.util.function.Supplier;

/** Coordinates TS12 SCA transaction data, OpenID4VP request inspection and AdES validation over the controller-owned FXML controls. */
final class WalletScaCoordinator extends WalletCoordinatorBase {
    record View(Supplier<ComboBox<String>> sdJwtAlgoCombo,
            Supplier<ComboBox<String>> scaTypeCombo,
            Supplier<TextField> scaCredentialIdsField,
            Supplier<TextArea> scaPayloadArea,
            Supplier<TextArea> scaEntryArea,
            Supplier<TextArea> scaPresentationArea,
            Supplier<TextArea> scaTransactionDataArea,
            Supplier<TextArea> scaIssuerKeyArea,
            Supplier<TextArea> scaHolderKeyArea,
            Supplier<TextField> scaAudienceField,
            Supplier<TextField> scaNonceField,
            Supplier<TextField> scaResponseModeField,
            Supplier<TextArea> scaOutputArea,
            Supplier<TextArea> oid4vpRequestArea,
            Supplier<TextArea> oid4vpKeyArea,
            Supplier<TextArea> oid4vpOutputArea,
            Supplier<TextField> adesFileNameField,
            Supplier<TextArea> adesDocumentArea,
            Supplier<TextArea> adesOutputArea) { }

    private final View view;

    WalletScaCoordinator(View view, Supplier<StatusReporter> reporter) {
        super(reporter);
        this.view = view;
    }

    void handleScaBuild() {
        try {
            String payload = textOf(view.scaPayloadArea().get());
            if (isBlank(payload)) { showValidation(t("module.wallet.claimsRequired"), "scaPayloadArea"); return; }
            String entry = Ts12ScaOperations.encodeTransactionData(
                    Ts12ScaOperations.TransactionType.fromUrn(valueOf(view.scaTypeCombo().get(),
                            Ts12ScaOperations.TransactionType.PAYMENT.urn())),
                    lines(textOf(view.scaCredentialIdsField().get())),
                    payload, "sha-256");
            String shown = WalletPrivateMaterialPolicy.forDisplay(entry, payload);
            view.scaEntryArea().get().setText(shown);
            // The entry is also what the verifying pane consumes, so it is put
            // there too rather than asking the user to copy it across. A hidden
            // entry is not copied: the notice is not transaction data.
            if (shown.equals(entry) && view.scaTransactionDataArea().get() != null && isBlank(textOf(view.scaTransactionDataArea().get()))) {
                view.scaTransactionDataArea().get().setText(entry);
            }
            updateStatus(t("module.wallet.status.issued"));
            publish("SCA Transaction Data", shown);
        } catch (Exception e) {
            fail(e, "scaPayloadArea", "sca transaction data");
        }
    }

    void handleScaVerify() {
        try {
            String presentation = textOf(view.scaPresentationArea().get());
            String issuerKey = textOf(view.scaIssuerKeyArea().get());
            if (isBlank(presentation)) { showValidation(t("module.wallet.sdJwtRequired"), "scaPresentationArea"); return; }
            if (isBlank(issuerKey)) { showValidation(t("module.wallet.keyRequired"), "scaIssuerKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(view.sdJwtAlgoCombo().get(), "ES256"));
            String holderKey = textOf(view.scaHolderKeyArea().get());
            Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(
                    presentation,
                    lines(textOf(view.scaTransactionDataArea().get())),
                    JOSEService.createVerifier(algorithm, issuerKey),
                    isBlank(holderKey) ? null : JOSEService.createVerifier(algorithm, holderKey),
                    blankToNull(textOf(view.scaAudienceField().get())),
                    blankToNull(textOf(view.scaNonceField().get())),
                    blankToNull(textOf(view.scaResponseModeField().get())));

            String text = WalletPrivateMaterialPolicy.forDisplay(
                    Ts12ScaOperations.describe(report, I18nService.getInstance().getLocale()),
                    WalletPrivateMaterialPolicy.sdJwtAsJson(presentation), textOf(view.scaTransactionDataArea().get()));
            view.scaOutputArea().get().setText(text);
            updateStatus(t("module.wallet.status.verified"));
            publish("SCA Verify", text, "Dynamic link", report.acceptable() ? "holds" : "does not hold");
        } catch (Exception e) {
            fail(e, "scaPresentationArea", "sca verify");
        }
    }

    void handleOid4vpInspect() {
        try {
            String request = textOf(view.oid4vpRequestArea().get());
            if (isBlank(request)) { showValidation(t("module.wallet.requestRequired"), "oid4vpRequestArea"); return; }
            String key = textOf(view.oid4vpKeyArea().get());
            String report = WalletPrivateMaterialPolicy.forDisplay(OpenId4VpInspector.describe(request,
                    isBlank(key) ? null
                            : JOSEService.createVerifier(JWSAlgorithm.parse(valueOf(view.sdJwtAlgoCombo().get(), "ES256")), key),
                    I18nService.getInstance().getLocale()), request);
            view.oid4vpOutputArea().get().setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("OpenID4VP Request Inspect", report);
        } catch (Exception e) {
            fail(e, "oid4vpRequestArea", "openid4vp inspect");
        }
    }

    void handleAdesValidate() {
        runAdes(false);
    }

    void handleAdesEtsiReport() {
        runAdes(true);
    }

    private void runAdes(boolean etsiReport) {
        try {
            String document = textOf(view.adesDocumentArea().get());
            if (isBlank(document)) { showValidation(t("module.wallet.documentRequired"), "adesDocumentArea"); return; }
            AdesValidationOperations.Result result = AdesValidationOperations.validate(
                    decodeDocument(document), textOf(view.adesFileNameField().get()), null, null);
            String text = WalletPrivateMaterialPolicy.forDisplay(etsiReport
                    ? result.etsiValidationReportXml()
                    : AdesValidationOperations.describe(result, I18nService.getInstance().getLocale()));
            view.adesOutputArea().get().setText(text);
            updateStatus(t("module.wallet.status.verified"));
            publish(etsiReport ? "AdES ETSI Report" : "AdES Validate", text,
                    "Signatures", String.valueOf(result.signatures().size()));
        } catch (Exception e) {
            fail(e, "adesDocumentArea", "ades validate");
        }
    }

    /** A signed document is pasted as base64 or as hexadecimal; both are met in
     *  practice and telling them apart is cheaper than making the user say. */
    private static byte[] decodeDocument(String value) {
        String cleaned = value.replaceAll("\\s", "");
        if (cleaned.matches("(?i)[0-9a-f]+") && cleaned.length() % 2 == 0) {
            return CborInspector.parseHex(cleaned);
        }
        return java.util.Base64.getMimeDecoder().decode(cleaned);
    }
}
