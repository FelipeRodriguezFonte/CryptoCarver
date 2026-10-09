package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.AdesValidationOperations;
import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.EidasCertificateInspector;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.MdocOperations;
import com.cryptocarver.crypto.OpenId4VpInspector;
import com.cryptocarver.crypto.TrustedListInspector;
import com.cryptocarver.crypto.TrustedEntityListJsonInspector;
import com.cryptocarver.crypto.Ts12ScaOperations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.service.I18nService;
import com.cryptocarver.util.DataConverter;
import com.nimbusds.jose.JWSAlgorithm;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Controller for the Wallet / eIDAS pane: SD-JWT VC, mdoc / mDL, Token Status
 * List, eIDAS certificate profiles, Trusted Lists and CBOR.
 *
 * <p>Follows {@link COSEController}'s shape exactly — validate, delegate to the
 * crypto facade, publish the result — so the crypto stays in
 * {@code com.cryptocarver.crypto} and this file only moves text around. Each
 * section corresponds to one facade and nothing here reimplements any of it.</p>
 *
 * <p>Every operation is local. No issuer metadata, JWKS, status list or trusted
 * list is fetched: whatever an operation needs is pasted in, which is the same
 * boundary the rest of the application keeps.</p>
 */
public class WalletController implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(WalletController.class);

    @FXML private VBox walletContainer;
    @FXML private VBox sdJwtSection;
    @FXML private VBox mdocSection;
    @FXML private VBox statusListSection;
    @FXML private VBox eidasCertSection;
    @FXML private VBox trustedListSection;
    @FXML private TextArea trustedEntityListJsonArea;
    @FXML private TextArea trustedEntityListSignerCertArea;
    @FXML private TextArea trustedEntityListSearchCertArea;
    @FXML private VBox cborSection;
    @FXML private VBox scaSection;
    @FXML private VBox adesSection;

    // SD-JWT
    @FXML private ComboBox<String> sdJwtAlgoCombo;
    @FXML private TextArea sdJwtIssuerKeyArea;
    @FXML private TextArea sdJwtClaimsArea;
    @FXML private TextArea sdJwtDisclosableArea;
    @FXML private TextField sdJwtVctField;
    @FXML private TextField sdJwtDecoyField;
    @FXML private TextArea sdJwtIssueOutputArea;
    @FXML private TextArea sdJwtPresentInputArea;
    @FXML private TextArea sdJwtRevealArea;
    @FXML private TextField sdJwtAudienceField;
    @FXML private TextField sdJwtNonceField;
    @FXML private TextArea sdJwtHolderKeyArea;
    @FXML private TextArea sdJwtPresentOutputArea;
    @FXML private TextArea sdJwtVerifyInputArea;
    @FXML private TextArea sdJwtVerifyIssuerKeyArea;
    @FXML private TextArea sdJwtVerifyHolderKeyArea;
    @FXML private TextField sdJwtVerifyAudienceField;
    @FXML private TextField sdJwtVerifyNonceField;
    @FXML private TextArea sdJwtVerifyOutputArea;
    @FXML private TextArea sdJwtInspectInputArea;
    @FXML private TextArea sdJwtInspectOutputArea;

    // mdoc
    @FXML private TextField mdocDocTypeField;
    @FXML private ComboBox<String> mdocDigestCombo;
    @FXML private TextArea mdocIssuerKeyArea;
    @FXML private TextArea mdocSignerCertArea;
    @FXML private TextArea mdocDeviceKeyArea;
    @FXML private TextArea mdocClaimsArea;
    @FXML private TextField mdocValidityField;
    @FXML private TextArea mdocIssueOutputArea;
    @FXML private TextArea mdocVerifyInputArea;
    @FXML private TextArea mdocVerifyIssuerKeyArea;
    @FXML private TextArea mdocVerifyOutputArea;

    // Status list
    @FXML private ComboBox<String> statusListBitsCombo;
    @FXML private TextArea statusListStatusesArea;
    @FXML private TextField statusListUriField;
    @FXML private ComboBox<String> statusListAlgoCombo;
    @FXML private TextArea statusListKeyArea;
    @FXML private TextArea statusListOutputArea;
    @FXML private TextArea statusListTokenArea;
    @FXML private TextField statusListIndexField;
    @FXML private TextArea statusListVerifyKeyArea;
    @FXML private TextArea statusListResolveOutputArea;

    // eIDAS certificate
    @FXML private TextArea eidasCertArea;
    @FXML private TextArea eidasCertOutputArea;

    // Trusted list
    @FXML private TextArea trustedListXmlArea;
    @FXML private TextArea trustedListCertArea;
    @FXML private TextArea trustedListOutputArea;

    // CBOR
    @FXML private TextArea cborInputArea;
    @FXML private ComboBox<String> cborViewCombo;
    @FXML private TextArea cborOutputArea;
    @FXML private TextArea cborJsonArea;
    @FXML private TextArea cborFromJsonOutputArea;

    // SCA / OpenID4VP
    @FXML private ComboBox<String> scaTypeCombo;
    @FXML private TextField scaCredentialIdsField;
    @FXML private TextArea scaPayloadArea;
    @FXML private TextArea scaEntryArea;
    @FXML private TextArea scaPresentationArea;
    @FXML private TextArea scaTransactionDataArea;
    @FXML private TextArea scaIssuerKeyArea;
    @FXML private TextArea scaHolderKeyArea;
    @FXML private TextField scaAudienceField;
    @FXML private TextField scaNonceField;
    @FXML private TextField scaResponseModeField;
    @FXML private TextArea scaOutputArea;
    @FXML private TextArea oid4vpRequestArea;
    @FXML private TextArea oid4vpKeyArea;
    @FXML private TextArea oid4vpOutputArea;

    // AdES validation
    @FXML private TextField adesFileNameField;
    @FXML private TextArea adesDocumentArea;
    @FXML private TextArea adesOutputArea;

    private StatusReporter statusReporter;
    private WalletStatusListCoordinator walletStatusListCoordinator;
    private WalletMdocCoordinator walletMdocCoordinator;
    private WalletSdJwtCoordinator walletSdJwtCoordinator;
    private ModuleI18n.Binding moduleI18n;

    /** Required by FXMLLoader when this controller is used from an fx:include. */
    public WalletController() {
    }

    public WalletController(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    public void setReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        moduleI18n = ModuleI18n.bind(walletContainer, ModuleTextCatalog.wallet());

        fill(sdJwtAlgoCombo, "ES256", "ES384", "ES512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512");
        fill(statusListAlgoCombo, "ES256", "ES384", "ES512", "RS256", "RS384", "RS512");
        // ISO/IEC 18013-5 Table 24 allows exactly these three.
        fill(mdocDigestCombo, "SHA-256", "SHA-384", "SHA-512");
        // The specification defines 1, 2, 4 and 8 and nothing else.
        fill(statusListBitsCombo, "1", "2", "4", "8");
        fill(cborViewCombo, "tree", "diagnostic", "summary");
        if (scaTypeCombo != null && scaTypeCombo.getItems().isEmpty()) {
            for (Ts12ScaOperations.TransactionType type : Ts12ScaOperations.TransactionType.values()) {
                scaTypeCombo.getItems().add(type.urn());
            }
            scaTypeCombo.getSelectionModel().selectFirst();
        }

        if (mdocDocTypeField != null && isBlank(textOf(mdocDocTypeField))) {
            mdocDocTypeField.setText(MdocOperations.MDL_DOCTYPE);
        }
    }

    private static void fill(ComboBox<String> combo, String... values) {
        if (combo != null && combo.getItems().isEmpty()) {
            combo.getItems().addAll(values);
            combo.getSelectionModel().selectFirst();
        }
    }

    /** Shows the section matching {@code sectionName}, hiding the others — the
     *  same pattern {@link COSEController#showSection} uses. */
    public void showSection(String sectionName) {
        if (walletContainer != null) {
            walletContainer.setManaged(true);
            walletContainer.setVisible(true);
        }
        hide(sdJwtSection, mdocSection, statusListSection, eidasCertSection, trustedListSection,
                cborSection, scaSection, adesSection);
        if (sectionName == null) {
            show(sdJwtSection);
            return;
        }
        if (sectionName.startsWith("SD-JWT")) {
            show(sdJwtSection);
        } else if (sectionName.startsWith("mdoc")) {
            show(mdocSection);
        } else if (sectionName.startsWith("Status List")) {
            show(statusListSection);
        } else if (sectionName.startsWith("eIDAS Certificate")) {
            show(eidasCertSection);
        } else if (sectionName.startsWith("Trusted List") || sectionName.startsWith("Trusted Entity List")) {
            show(trustedListSection);
        } else if (sectionName.startsWith("CBOR")) {
            show(cborSection);
        } else if (sectionName.startsWith("SCA")) {
            show(scaSection);
        } else if (sectionName.startsWith("AdES")) {
            show(adesSection);
        } else {
            show(sdJwtSection);
        }
    }

    private static void hide(VBox... sections) {
        for (VBox section : sections) {
            if (section != null) {
                section.setManaged(false);
                section.setVisible(false);
            }
        }
    }

    private static void show(VBox section) {
        if (section != null) {
            section.setManaged(true);
            section.setVisible(true);
        }
    }

    private WalletSdJwtCoordinator sdJwtCoordinator() {
        if (walletSdJwtCoordinator == null) {
            walletSdJwtCoordinator = new WalletSdJwtCoordinator(new WalletSdJwtCoordinator.View(
                    () -> sdJwtAlgoCombo,
                    () -> sdJwtIssuerKeyArea,
                    () -> sdJwtClaimsArea,
                    () -> sdJwtDisclosableArea,
                    () -> sdJwtVctField,
                    () -> sdJwtDecoyField,
                    () -> sdJwtIssueOutputArea,
                    () -> sdJwtPresentInputArea,
                    () -> sdJwtRevealArea,
                    () -> sdJwtAudienceField,
                    () -> sdJwtNonceField,
                    () -> sdJwtHolderKeyArea,
                    () -> sdJwtPresentOutputArea,
                    () -> sdJwtVerifyInputArea,
                    () -> sdJwtVerifyIssuerKeyArea,
                    () -> sdJwtVerifyHolderKeyArea,
                    () -> sdJwtVerifyAudienceField,
                    () -> sdJwtVerifyNonceField,
                    () -> sdJwtVerifyOutputArea,
                    () -> sdJwtInspectInputArea,
                    () -> sdJwtInspectOutputArea), () -> statusReporter);
        }
        return walletSdJwtCoordinator;
    }

    private WalletMdocCoordinator mdocCoordinator() {
        if (walletMdocCoordinator == null) {
            walletMdocCoordinator = new WalletMdocCoordinator(new WalletMdocCoordinator.View(
                    () -> mdocDocTypeField,
                    () -> mdocDigestCombo,
                    () -> mdocIssuerKeyArea,
                    () -> mdocSignerCertArea,
                    () -> mdocDeviceKeyArea,
                    () -> mdocClaimsArea,
                    () -> mdocValidityField,
                    () -> mdocIssueOutputArea,
                    () -> mdocVerifyInputArea,
                    () -> mdocVerifyIssuerKeyArea,
                    () -> mdocVerifyOutputArea), () -> statusReporter);
        }
        return walletMdocCoordinator;
    }

    private WalletStatusListCoordinator statusListCoordinator() {
        if (walletStatusListCoordinator == null) {
            walletStatusListCoordinator = new WalletStatusListCoordinator(new WalletStatusListCoordinator.View(
                    () -> statusListBitsCombo,
                    () -> statusListStatusesArea,
                    () -> statusListUriField,
                    () -> statusListAlgoCombo,
                    () -> statusListKeyArea,
                    () -> statusListOutputArea,
                    () -> statusListTokenArea,
                    () -> statusListIndexField,
                    () -> statusListVerifyKeyArea,
                    () -> statusListResolveOutputArea), () -> statusReporter);
        }
        return walletStatusListCoordinator;
    }

    // ---------------------------------------------------------------- SD-JWT

    @FXML
    private void handleSdJwtIssue() { sdJwtCoordinator().handleSdJwtIssue(); }

    @FXML
    private void handleSdJwtPresent() { sdJwtCoordinator().handleSdJwtPresent(); }

    @FXML
    private void handleSdJwtVerify() { sdJwtCoordinator().handleSdJwtVerify(); }

    @FXML
    private void handleSdJwtInspect() { sdJwtCoordinator().handleSdJwtInspect(); }



    // ------------------------------------------------------------------ mdoc

    @FXML
    private void handleMdocIssue() { mdocCoordinator().handleMdocIssue(); }

    @FXML
    private void handleMdocVerify() { mdocCoordinator().handleMdocVerify(); }

    @FXML
    private void handleMdocInspect() { mdocCoordinator().handleMdocInspect(); }



    // ----------------------------------------------------------- status list

    @FXML
    private void handleStatusListIssue() { statusListCoordinator().handleStatusListIssue(); }

    @FXML
    private void handleStatusListResolve() { statusListCoordinator().handleStatusListResolve(); }

    @FXML
    private void handleStatusListDescribe() { statusListCoordinator().handleStatusListDescribe(); }

    // ------------------------------------------------------ eIDAS certificate

    @FXML
    private void handleEidasCertInspect() {
        try {
            String pem = textOf(eidasCertArea);
            if (isBlank(pem)) { showValidation(t("module.wallet.certificateRequired"), "eidasCertArea"); return; }
            String report = WalletPrivateMaterialPolicy.forDisplay(
                    EidasCertificateInspector.describe(parseCertificate(pem), I18nService.getInstance().getLocale()));
            eidasCertOutputArea.setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("eIDAS Certificate Inspect", report);
        } catch (Exception e) {
            fail(e, "eidasCertArea", "eidas certificate inspect");
        }
    }

    // --------------------------------------------------------- trusted list

    @FXML
    private void handleTrustedListInspect() {
        try {
            byte[] xml = trustedListXml();
            if (xml == null) return;
            String report = WalletPrivateMaterialPolicy.forDisplay(
                    TrustedListInspector.describe(xml, I18nService.getInstance().getLocale()));
            trustedListOutputArea.setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Trusted List Inspect", report);
        } catch (Exception e) {
            fail(e, "trustedListXmlArea", "trusted list inspect");
        }
    }

    @FXML
    private void handleTrustedEntityListJsonInspect() {
        try {
            String json = textOf(trustedEntityListJsonArea);
            if (isBlank(json)) { showValidation(t("module.wallet.trustedEntityListRequired"), "trustedEntityListJsonArea"); return; }
            String signer = textOf(trustedEntityListSignerCertArea);
            String search = textOf(trustedEntityListSearchCertArea);
            String report = WalletPrivateMaterialPolicy.forDisplay(TrustedEntityListJsonInspector.describe(json.getBytes(StandardCharsets.UTF_8),
                    I18nService.getInstance().getLocale(), signer.isBlank() ? null : parseCertificate(signer),
                    search.isBlank() ? null : parseCertificate(search)), json);
            trustedListOutputArea.setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Trusted Entity List JSON Inspect", report);
        } catch (Exception e) { fail(e, "trustedEntityListJsonArea", "trusted entity list JSON inspect"); }
    }

    @FXML
    private void handleTrustedListVerify() {
        try {
            byte[] xml = trustedListXml();
            if (xml == null) return;
            TrustedListInspector.SignatureResult result = TrustedListInspector.verifySignature(xml);
            StringBuilder report = new StringBuilder();
            report.append("signature: ").append(result.signatureValid() ? "valid" : "INVALID").append('\n');
            if (result.signingCertificate() != null) {
                report.append("signed by: ")
                        .append(result.signingCertificate().getSubjectX500Principal()).append('\n');
            }
            report.append('\n').append(result.trustNote()).append('\n');
            String text = WalletPrivateMaterialPolicy.forDisplay(report.toString());
            trustedListOutputArea.setText(text);
            updateStatus(t("module.wallet.status.verified"));
            publish("Trusted List Verify", text);
        } catch (Exception e) {
            fail(e, "trustedListXmlArea", "trusted list verify");
        }
    }

    @FXML
    private void handleTrustedListFind() {
        try {
            byte[] xml = trustedListXml();
            if (xml == null) return;
            String pem = textOf(trustedListCertArea);
            if (isBlank(pem)) { showValidation(t("module.wallet.certificateRequired"), "trustedListCertArea"); return; }

            List<TrustedListInspector.Match> matches = TrustedListInspector.findCertificate(
                    TrustedListInspector.parse(xml), parseCertificate(pem));

            StringBuilder report = new StringBuilder();
            if (matches.isEmpty()) {
                report.append(t("module.wallet.notInTrustedList")).append('\n');
            }
            for (TrustedListInspector.Match match : matches) {
                report.append(match.service().providerName())
                        .append(" / ").append(match.service().serviceName())
                        .append("\n  status   : ").append(match.service().statusLabel())
                        .append("\n  matched  : ").append(match.matchedBy()).append('\n');
                for (String qualifier : match.service().qualifiers()) {
                    report.append("  qualifier: ").append(qualifier).append('\n');
                }
            }
            String text = WalletPrivateMaterialPolicy.forDisplay(report.toString());
            trustedListOutputArea.setText(text);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Trusted List Find Certificate", text,
                    "Matches", String.valueOf(matches.size()));
        } catch (Exception e) {
            fail(e, "trustedListCertArea", "trusted list find");
        }
    }

    private byte[] trustedListXml() {
        String xml = textOf(trustedListXmlArea);
        if (isBlank(xml)) {
            showValidation(t("module.wallet.trustedListRequired"), "trustedListXmlArea");
            return null;
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ CBOR

    @FXML
    private void handleCborInspect() {
        try {
            String hex = textOf(cborInputArea);
            if (isBlank(hex)) { showValidation(t("module.wallet.cborRequired"), "cborInputArea"); return; }
            byte[] cbor = CborInspector.parseHex(hex);
            String report = WalletPrivateMaterialPolicy.forDisplay(switch (valueOf(cborViewCombo, "tree")) {
                case "diagnostic" -> CborInspector.diagnostic(cbor);
                case "summary" -> CborInspector.summary(cbor);
                default -> CborInspector.tree(cbor);
            }, WalletPrivateMaterialPolicy.cborAsJson(cbor));
            cborOutputArea.setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("CBOR Inspect", report);
        } catch (Exception e) {
            fail(e, "cborInputArea", "cbor inspect");
        }
    }

    @FXML
    private void handleCborToJson() {
        try {
            String hex = textOf(cborInputArea);
            if (isBlank(hex)) { showValidation(t("module.wallet.cborRequired"), "cborInputArea"); return; }
            String json = WalletPrivateMaterialPolicy.forDisplay(CborInspector.toJson(CborInspector.parseHex(hex)));
            cborOutputArea.setText(json);
            updateStatus(t("module.wallet.status.converted"));
            publish("CBOR to JSON", json);
        } catch (Exception e) {
            fail(e, "cborInputArea", "cbor to json");
        }
    }

    @FXML
    private void handleCborFromJson() {
        try {
            String json = textOf(cborJsonArea);
            if (isBlank(json)) { showValidation(t("module.wallet.jsonRequired"), "cborJsonArea"); return; }
            String hex = WalletPrivateMaterialPolicy.forDisplay(
                    DataConverter.bytesToHex(CborInspector.fromJson(json)).toUpperCase(), json);
            cborFromJsonOutputArea.setText(hex);
            updateStatus(t("module.wallet.status.converted"));
            publish("JSON to CBOR", hex);
        } catch (Exception e) {
            fail(e, "cborJsonArea", "cbor from json");
        }
    }


    // ------------------------------------------------------ SCA / OpenID4VP

    @FXML
    private void handleScaBuild() {
        try {
            String payload = textOf(scaPayloadArea);
            if (isBlank(payload)) { showValidation(t("module.wallet.claimsRequired"), "scaPayloadArea"); return; }
            String entry = Ts12ScaOperations.encodeTransactionData(
                    Ts12ScaOperations.TransactionType.fromUrn(valueOf(scaTypeCombo,
                            Ts12ScaOperations.TransactionType.PAYMENT.urn())),
                    lines(textOf(scaCredentialIdsField)),
                    payload, "sha-256");
            String shown = WalletPrivateMaterialPolicy.forDisplay(entry, payload);
            scaEntryArea.setText(shown);
            // The entry is also what the verifying pane consumes, so it is put
            // there too rather than asking the user to copy it across. A hidden
            // entry is not copied: the notice is not transaction data.
            if (shown.equals(entry) && scaTransactionDataArea != null && isBlank(textOf(scaTransactionDataArea))) {
                scaTransactionDataArea.setText(entry);
            }
            updateStatus(t("module.wallet.status.issued"));
            publish("SCA Transaction Data", shown);
        } catch (Exception e) {
            fail(e, "scaPayloadArea", "sca transaction data");
        }
    }

    @FXML
    private void handleScaVerify() {
        try {
            String presentation = textOf(scaPresentationArea);
            String issuerKey = textOf(scaIssuerKeyArea);
            if (isBlank(presentation)) { showValidation(t("module.wallet.sdJwtRequired"), "scaPresentationArea"); return; }
            if (isBlank(issuerKey)) { showValidation(t("module.wallet.keyRequired"), "scaIssuerKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(sdJwtAlgoCombo, "ES256"));
            String holderKey = textOf(scaHolderKeyArea);
            Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(
                    presentation,
                    lines(textOf(scaTransactionDataArea)),
                    JOSEService.createVerifier(algorithm, issuerKey),
                    isBlank(holderKey) ? null : JOSEService.createVerifier(algorithm, holderKey),
                    blankToNull(textOf(scaAudienceField)),
                    blankToNull(textOf(scaNonceField)),
                    blankToNull(textOf(scaResponseModeField)));

            String text = WalletPrivateMaterialPolicy.forDisplay(
                    Ts12ScaOperations.describe(report, I18nService.getInstance().getLocale()),
                    WalletPrivateMaterialPolicy.sdJwtAsJson(presentation), textOf(scaTransactionDataArea));
            scaOutputArea.setText(text);
            updateStatus(t("module.wallet.status.verified"));
            publish("SCA Verify", text, "Dynamic link", report.acceptable() ? "holds" : "does not hold");
        } catch (Exception e) {
            fail(e, "scaPresentationArea", "sca verify");
        }
    }

    @FXML
    private void handleOid4vpInspect() {
        try {
            String request = textOf(oid4vpRequestArea);
            if (isBlank(request)) { showValidation(t("module.wallet.requestRequired"), "oid4vpRequestArea"); return; }
            String key = textOf(oid4vpKeyArea);
            String report = WalletPrivateMaterialPolicy.forDisplay(OpenId4VpInspector.describe(request,
                    isBlank(key) ? null
                            : JOSEService.createVerifier(JWSAlgorithm.parse(valueOf(sdJwtAlgoCombo, "ES256")), key),
                    I18nService.getInstance().getLocale()), request);
            oid4vpOutputArea.setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("OpenID4VP Request Inspect", report);
        } catch (Exception e) {
            fail(e, "oid4vpRequestArea", "openid4vp inspect");
        }
    }

    // ------------------------------------------------------ AdES validation

    @FXML
    private void handleAdesValidate() {
        runAdes(false);
    }

    @FXML
    private void handleAdesEtsiReport() {
        runAdes(true);
    }

    private void runAdes(boolean etsiReport) {
        try {
            String document = textOf(adesDocumentArea);
            if (isBlank(document)) { showValidation(t("module.wallet.documentRequired"), "adesDocumentArea"); return; }
            AdesValidationOperations.Result result = AdesValidationOperations.validate(
                    decodeDocument(document), textOf(adesFileNameField), null, null);
            String text = WalletPrivateMaterialPolicy.forDisplay(etsiReport
                    ? result.etsiValidationReportXml()
                    : AdesValidationOperations.describe(result, I18nService.getInstance().getLocale()));
            adesOutputArea.setText(text);
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

    // --------------------------------------------------------------- toolbar

    @FXML
    private void handleClear() {
        clear(sdJwtIssuerKeyArea, sdJwtClaimsArea, sdJwtDisclosableArea, sdJwtIssueOutputArea,
                sdJwtPresentInputArea, sdJwtRevealArea, sdJwtHolderKeyArea, sdJwtPresentOutputArea,
                sdJwtVerifyInputArea, sdJwtVerifyIssuerKeyArea, sdJwtVerifyHolderKeyArea, sdJwtVerifyOutputArea,
                sdJwtInspectInputArea, sdJwtInspectOutputArea,
                mdocIssuerKeyArea, mdocSignerCertArea, mdocDeviceKeyArea, mdocClaimsArea, mdocIssueOutputArea,
                mdocVerifyInputArea, mdocVerifyIssuerKeyArea, mdocVerifyOutputArea,
                statusListStatusesArea, statusListKeyArea, statusListOutputArea,
                statusListTokenArea, statusListVerifyKeyArea, statusListResolveOutputArea,
                eidasCertArea, eidasCertOutputArea,
                trustedListXmlArea, trustedEntityListJsonArea, trustedEntityListSignerCertArea,
                trustedEntityListSearchCertArea, trustedListCertArea, trustedListOutputArea,
                cborInputArea, cborOutputArea, cborJsonArea, cborFromJsonOutputArea,
                scaPayloadArea, scaEntryArea, scaPresentationArea, scaTransactionDataArea,
                scaIssuerKeyArea, scaHolderKeyArea, scaOutputArea,
                oid4vpRequestArea, oid4vpKeyArea, oid4vpOutputArea,
                adesDocumentArea, adesOutputArea);
        clear(sdJwtVctField, sdJwtAudienceField, sdJwtNonceField,
                sdJwtVerifyAudienceField, sdJwtVerifyNonceField,
                statusListUriField, statusListIndexField,
                scaAudienceField, scaNonceField);
        if (sdJwtDecoyField != null) sdJwtDecoyField.setText("0");
        if (mdocValidityField != null) mdocValidityField.setText("365");
        updateStatus(t("module.wallet.status.cleared"));
    }

    /** Fills the panes with laboratory material so the module can be tried
     *  without hunting for a credential first. No key material is included:
     *  a key has to be generated in the Keys module and pasted. */
    @FXML
    private void handleLoadExample() {
        setText(sdJwtClaimsArea, """
                {
                  "iss": "https://issuer.lab.invalid",
                  "given_name": "John",
                  "family_name": "Doe",
                  "birthdate": "1940-01-01",
                  "address": { "locality": "Vigo", "country": "ES" },
                  "nationalities": ["ES", "PT"]
                }""");
        setText(sdJwtDisclosableArea, "given_name\nfamily_name\nbirthdate\naddress.locality\nnationalities[]");
        setText(sdJwtVctField, "urn:eudi:pid:1");
        setText(mdocClaimsArea, """
                {"org.iso.18013.5.1": {
                  "family_name": "Doe",
                  "given_name": "John",
                  "document_number": "ES-1234567",
                  "issuing_country": "ES"
                }}""");
        setText(statusListStatusesArea, "0,0,1,0,2,0,0,0");
        setText(statusListUriField, "https://issuer.lab.invalid/statuslists/1");
        setText(statusListIndexField, "2");
        setText(cborInputArea, "a26161016162820203");
        setText(cborJsonArea, "{\"a\": 1, \"b\": [2, 3]}");
        updateStatus(t("module.wallet.status.exampleLoaded"));
    }

    // --------------------------------------------------------------- helpers

    private static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\r\\n,]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }







    private static X509Certificate parseCertificate(String pem) throws Exception {
        String normalized = pem.replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", "");
        byte[] der = java.util.Base64.getDecoder().decode(normalized);
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
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

    private static void setText(TextArea area, String value) {
        if (area != null) area.setText(value);
    }

    private static void setText(TextField field, String value) {
        if (field != null) field.setText(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static void clear(TextArea... areas) {
        for (TextArea area : areas) {
            if (area != null) area.clear();
        }
    }

    private static void clear(TextField... fields) {
        for (TextField field : fields) {
            if (field != null) field.clear();
        }
    }

    private void publish(String operation, String output, String... details) {
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

    private void fail(Exception error, String fieldKey, String operation) {
        showValidation(t("module.wallet.operation", error.getMessage()), fieldKey);
        updateStatus(t("module.wallet.status.failed"));
        logFailure(operation, error);
    }

    private void showValidation(String message, String fieldKey) {
        String safeMessage = InlineErrorPresenter.redactSecrets(message);
        UserFacingError error = new UserFacingError(t("module.wallet.errorTitle"), safeMessage, safeMessage, fieldKey);
        if (statusReporter != null) {
            statusReporter.showError(error);
        }
    }

    private void updateStatus(String message) {
        if (statusReporter != null) statusReporter.updateStatus(message);
    }

    private void logFailure(String operation, Exception error) {
        LOG.error("Wallet {} failed: {}", operation, InlineErrorPresenter.redactSecrets(error.toString()), error);
    }
}
