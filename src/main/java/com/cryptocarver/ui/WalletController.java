package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.AdesValidationOperations;
import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.EidasCertificateInspector;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.MdocOperations;
import com.cryptocarver.crypto.SdJwtOperations;
import com.cryptocarver.crypto.StatusListOperations;
import com.cryptocarver.crypto.OpenId4VpInspector;
import com.cryptocarver.crypto.TrustedListInspector;
import com.cryptocarver.crypto.TrustedEntityListJsonInspector;
import com.cryptocarver.crypto.Ts12ScaOperations;
import com.cryptocarver.model.OperationResult;
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

    // ---------------------------------------------------------------- SD-JWT

    @FXML
    private void handleSdJwtIssue() {
        try {
            String claims = textOf(sdJwtClaimsArea);
            String key = textOf(sdJwtIssuerKeyArea);
            if (isBlank(claims)) { showValidation(t("module.wallet.claimsRequired"), "sdJwtClaimsArea"); return; }
            if (isBlank(key)) { showValidation(t("module.wallet.keyRequired"), "sdJwtIssuerKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(sdJwtAlgoCombo, "ES256"));
            List<String> paths = lines(textOf(sdJwtDisclosableArea));
            int decoys = parseInt(textOf(sdJwtDecoyField), 0);
            String vct = textOf(sdJwtVctField);

            SdJwtOperations.IssuedSdJwt issued = isBlank(vct)
                    ? SdJwtOperations.issue(claims, paths, decoys,
                            SdJwtOperations.HashAlgorithm.SHA_256, algorithm,
                            JOSEService.createSigner(algorithm, key), null)
                    : SdJwtOperations.issueVerifiableCredential(claims, vct, null, null, null, paths,
                            decoys, SdJwtOperations.HashAlgorithm.SHA_256, algorithm,
                            JOSEService.createSigner(algorithm, key));

            sdJwtIssueOutputArea.setText(issued.serialized());
            updateStatus(t("module.wallet.status.issued"));
            publish("SD-JWT Issue", issued.serialized(),
                    "Disclosures", String.valueOf(issued.disclosures().size()));
        } catch (Exception e) {
            fail(e, "sdJwtClaimsArea", "sd-jwt issue");
        }
    }

    @FXML
    private void handleSdJwtPresent() {
        try {
            String serialized = textOf(sdJwtPresentInputArea);
            if (isBlank(serialized)) { showValidation(t("module.wallet.sdJwtRequired"), "sdJwtPresentInputArea"); return; }

            SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(serialized);
            List<String> wanted = lines(textOf(sdJwtRevealArea));
            List<String> digests = new ArrayList<>();
            for (SdJwtOperations.Disclosure disclosure : parsed.disclosures()) {
                if (wanted.isEmpty() || wanted.contains(disclosure.claimName())
                        || wanted.contains(disclosure.label())) {
                    digests.add(disclosure.digest());
                }
            }

            String audience = textOf(sdJwtAudienceField);
            String nonce = textOf(sdJwtNonceField);
            String holderKey = textOf(sdJwtHolderKeyArea);
            SdJwtOperations.KeyBinding binding = null;
            if (!isBlank(audience) || !isBlank(nonce) || !isBlank(holderKey)) {
                // Half a key binding produces a presentation that looks bound and
                // is not, so all three are demanded together.
                if (isBlank(audience) || isBlank(nonce) || isBlank(holderKey)) {
                    showValidation(t("module.wallet.keyBindingIncomplete"), "sdJwtAudienceField");
                    return;
                }
                JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(sdJwtAlgoCombo, "ES256"));
                binding = new SdJwtOperations.KeyBinding(audience, nonce, algorithm,
                        JOSEService.createSigner(algorithm, holderKey));
            }

            SdJwtOperations.IssuedSdJwt reconstructed = new SdJwtOperations.IssuedSdJwt(
                    serialized.split("~", -1)[0], parsed.disclosures(), serialized,
                    SdJwtOperations.HashAlgorithm.SHA_256);
            String presentation = SdJwtOperations.present(reconstructed, digests, binding);

            sdJwtPresentOutputArea.setText(presentation);
            updateStatus(t("module.wallet.status.presented"));
            publish("SD-JWT Present", presentation, "Disclosures revealed", String.valueOf(digests.size()));
        } catch (Exception e) {
            fail(e, "sdJwtPresentInputArea", "sd-jwt present");
        }
    }

    @FXML
    private void handleSdJwtVerify() {
        try {
            String presentation = textOf(sdJwtVerifyInputArea);
            String issuerKey = textOf(sdJwtVerifyIssuerKeyArea);
            if (isBlank(presentation)) { showValidation(t("module.wallet.sdJwtRequired"), "sdJwtVerifyInputArea"); return; }
            if (isBlank(issuerKey)) { showValidation(t("module.wallet.keyRequired"), "sdJwtVerifyIssuerKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(sdJwtAlgoCombo, "ES256"));
            String holderKey = textOf(sdJwtVerifyHolderKeyArea);
            SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(presentation,
                    JOSEService.createVerifier(algorithm, issuerKey),
                    isBlank(holderKey) ? null : JOSEService.createVerifier(algorithm, holderKey),
                    blankToNull(textOf(sdJwtVerifyAudienceField)),
                    blankToNull(textOf(sdJwtVerifyNonceField)));

            StringBuilder report = new StringBuilder(verified.claimsJson());
            if (!verified.notes().isEmpty()) {
                report.append("\n\n");
                verified.notes().forEach(note -> report.append("- ").append(note).append('\n'));
            }
            sdJwtVerifyOutputArea.setText(report.toString());
            updateStatus(t("module.wallet.status.verified"));
            publish("SD-JWT Verify", report.toString(),
                    "Key binding", verified.keyBindingPresent() ? "present" : "absent");
        } catch (Exception e) {
            fail(e, "sdJwtVerifyInputArea", "sd-jwt verify");
        }
    }

    @FXML
    private void handleSdJwtInspect() {
        try {
            String serialized = textOf(sdJwtInspectInputArea);
            if (isBlank(serialized)) { showValidation(t("module.wallet.sdJwtRequired"), "sdJwtInspectInputArea"); return; }
            String report = SdJwtOperations.describe(serialized, Locale.getDefault());
            sdJwtInspectOutputArea.setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("SD-JWT Inspect", report);
        } catch (Exception e) {
            fail(e, "sdJwtInspectInputArea", "sd-jwt inspect");
        }
    }

    // ------------------------------------------------------------------ mdoc

    @FXML
    private void handleMdocIssue() {
        try {
            String claims = textOf(mdocClaimsArea);
            String key = textOf(mdocIssuerKeyArea);
            String certificatePem = textOf(mdocSignerCertArea);
            if (isBlank(claims)) { showValidation(t("module.wallet.claimsRequired"), "mdocClaimsArea"); return; }
            if (isBlank(key)) { showValidation(t("module.wallet.keyRequired"), "mdocIssuerKeyArea"); return; }
            if (isBlank(certificatePem)) { showValidation(t("module.wallet.certificateRequired"), "mdocSignerCertArea"); return; }

            Instant now = Instant.now();
            long days = parseInt(textOf(mdocValidityField), 365);
            String deviceKeyPem = textOf(mdocDeviceKeyArea);

            byte[] mdoc = MdocOperations.issue(
                    textOf(mdocDocTypeField),
                    claims,
                    valueOf(mdocDigestCombo, "SHA-256"),
                    new MdocOperations.ValidityInfo(now, now, now.plus(days, ChronoUnit.DAYS), null),
                    AsymmetricKeyOperations.importPrivateKeyPEMAuto(key),
                    parseCertificate(certificatePem),
                    isBlank(deviceKeyPem) ? null : AsymmetricKeyOperations.importPublicKeyPEMAuto(deviceKeyPem));

            String hex = DataConverter.bytesToHex(mdoc).toUpperCase();
            mdocIssueOutputArea.setText(hex);
            updateStatus(t("module.wallet.status.issued"));
            publish("mdoc Issue", hex, "Document type", textOf(mdocDocTypeField));
        } catch (Exception e) {
            fail(e, "mdocClaimsArea", "mdoc issue");
        }
    }

    @FXML
    private void handleMdocVerify() {
        runMdocReport(true);
    }

    @FXML
    private void handleMdocInspect() {
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
            String hex = textOf(mdocVerifyInputArea);
            if (isBlank(hex)) { showValidation(t("module.wallet.mdocRequired"), "mdocVerifyInputArea"); return; }
            String issuerKey = textOf(mdocVerifyIssuerKeyArea);
            if (requireIssuerKey && isBlank(issuerKey)) {
                showValidation(t("module.wallet.keyRequired"), "mdocVerifyIssuerKeyArea");
                return;
            }
            PublicKey key = isBlank(issuerKey) ? null : AsymmetricKeyOperations.importPublicKeyPEMAuto(issuerKey);
            String report = MdocOperations.describe(CborInspector.parseHex(hex), key,
                    Instant.now(), Locale.getDefault());
            mdocVerifyOutputArea.setText(report);
            updateStatus(t("module.wallet.status.verified"));
            publish(requireIssuerKey ? "mdoc Verify" : "mdoc Inspect", report);
        } catch (Exception e) {
            fail(e, "mdocVerifyInputArea", "mdoc verify");
        }
    }

    // ----------------------------------------------------------- status list

    @FXML
    private void handleStatusListIssue() {
        try {
            String statuses = textOf(statusListStatusesArea);
            String uri = textOf(statusListUriField);
            String key = textOf(statusListKeyArea);
            if (isBlank(statuses)) { showValidation(t("module.wallet.statusesRequired"), "statusListStatusesArea"); return; }
            if (isBlank(uri)) { showValidation(t("module.wallet.uriRequired"), "statusListUriField"); return; }
            if (isBlank(key)) { showValidation(t("module.wallet.keyRequired"), "statusListKeyArea"); return; }

            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(statusListAlgoCombo, "ES256"));
            String token = StatusListOperations.issueStatusListToken(
                    parseStatuses(statuses), parseInt(valueOf(statusListBitsCombo, "1"), 1), uri,
                    Instant.now(), null, -1, algorithm, JOSEService.createSigner(algorithm, key));

            statusListOutputArea.setText(token);
            updateStatus(t("module.wallet.status.issued"));
            publish("Status List Issue", token, "URI", uri);
        } catch (Exception e) {
            fail(e, "statusListStatusesArea", "status list issue");
        }
    }

    @FXML
    private void handleStatusListResolve() {
        try {
            String token = textOf(statusListTokenArea);
            String index = textOf(statusListIndexField);
            if (isBlank(token)) { showValidation(t("module.wallet.tokenRequired"), "statusListTokenArea"); return; }
            if (isBlank(index)) { showValidation(t("module.wallet.indexRequired"), "statusListIndexField"); return; }

            // The subject check inside resolve() needs the URI the credential
            // points at; here the list's own subject is used, because a lone
            // index has no credential to take it from.
            String uri = subjectOf(token);
            String verifyKey = textOf(statusListVerifyKeyArea);
            JWSAlgorithm algorithm = JWSAlgorithm.parse(valueOf(statusListAlgoCombo, "ES256"));

            StatusListOperations.StatusLookup lookup = StatusListOperations.resolve(
                    StatusListOperations.statusClaim(uri, parseInt(index, 0)), token,
                    isBlank(verifyKey) ? null : JOSEService.createVerifier(algorithm, verifyKey));

            String report = "index " + lookup.index() + " -> " + lookup.status()
                    + " (" + lookup.description() + ")\n"
                    + (isBlank(verifyKey)
                            ? "The token's signature was not verified: no key was supplied.\n"
                            : "");
            statusListResolveOutputArea.setText(report);
            updateStatus(t("module.wallet.status.resolved"));
            publish("Status List Resolve", report, "Index", index);
        } catch (Exception e) {
            fail(e, "statusListTokenArea", "status list resolve");
        }
    }

    @FXML
    private void handleStatusListDescribe() {
        try {
            String token = textOf(statusListTokenArea);
            if (isBlank(token)) { showValidation(t("module.wallet.tokenRequired"), "statusListTokenArea"); return; }
            String report = StatusListOperations.describe(token);
            statusListResolveOutputArea.setText(report);
            updateStatus(t("module.wallet.status.inspected"));
            publish("Status List Describe", report);
        } catch (Exception e) {
            fail(e, "statusListTokenArea", "status list describe");
        }
    }

    // ------------------------------------------------------ eIDAS certificate

    @FXML
    private void handleEidasCertInspect() {
        try {
            String pem = textOf(eidasCertArea);
            if (isBlank(pem)) { showValidation(t("module.wallet.certificateRequired"), "eidasCertArea"); return; }
            String report = EidasCertificateInspector.describe(parseCertificate(pem), Locale.getDefault());
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
            String report = TrustedListInspector.describe(xml, Locale.getDefault());
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
            String report = TrustedEntityListJsonInspector.describe(json.getBytes(StandardCharsets.UTF_8),
                    Locale.getDefault(), signer.isBlank() ? null : parseCertificate(signer),
                    search.isBlank() ? null : parseCertificate(search));
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
            trustedListOutputArea.setText(report.toString());
            updateStatus(t("module.wallet.status.verified"));
            publish("Trusted List Verify", report.toString());
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
            trustedListOutputArea.setText(report.toString());
            updateStatus(t("module.wallet.status.inspected"));
            publish("Trusted List Find Certificate", report.toString(),
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
            String report = switch (valueOf(cborViewCombo, "tree")) {
                case "diagnostic" -> CborInspector.diagnostic(cbor);
                case "summary" -> CborInspector.summary(cbor);
                default -> CborInspector.tree(cbor);
            };
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
            String json = CborInspector.toJson(CborInspector.parseHex(hex));
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
            String hex = DataConverter.bytesToHex(CborInspector.fromJson(json)).toUpperCase();
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
            scaEntryArea.setText(entry);
            // The entry is also what the verifying pane consumes, so it is put
            // there too rather than asking the user to copy it across.
            if (scaTransactionDataArea != null && isBlank(textOf(scaTransactionDataArea))) {
                scaTransactionDataArea.setText(entry);
            }
            updateStatus(t("module.wallet.status.issued"));
            publish("SCA Transaction Data", entry);
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

            String text = Ts12ScaOperations.describe(report, Locale.getDefault());
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
            String report = OpenId4VpInspector.describe(request,
                    isBlank(key) ? null
                            : JOSEService.createVerifier(JWSAlgorithm.parse(valueOf(sdJwtAlgoCombo, "ES256")), key),
                    Locale.getDefault());
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
            String text = etsiReport
                    ? result.etsiValidationReportXml()
                    : AdesValidationOperations.describe(result, Locale.getDefault());
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

    private static int[] parseStatuses(String raw) {
        List<String> values = lines(raw.replace(" ", "\n"));
        int[] statuses = new int[values.size()];
        for (int i = 0; i < statuses.length; i++) {
            statuses[i] = Integer.parseInt(values.get(i));
        }
        return statuses;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Reads the {@code sub} of a Status List Token without verifying it: the
     *  resolve path needs the URI the list claims, and at this point there is no
     *  credential to take it from. */
    private static String subjectOf(String token) {
        String payload = token.split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        return com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("sub").getAsString();
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
