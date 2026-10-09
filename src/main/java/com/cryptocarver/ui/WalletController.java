package com.cryptocarver.ui;

import com.cryptocarver.crypto.MdocOperations;
import com.cryptocarver.crypto.Ts12ScaOperations;
import com.cryptocarver.service.I18nService;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
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
    private WalletScaCoordinator walletScaCoordinator;
    private WalletCborCoordinator walletCborCoordinator;
    private WalletTrustCoordinator walletTrustCoordinator;
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

    private WalletTrustCoordinator trustCoordinator() {
        if (walletTrustCoordinator == null) {
            walletTrustCoordinator = new WalletTrustCoordinator(new WalletTrustCoordinator.View(
                    () -> eidasCertArea,
                    () -> eidasCertOutputArea,
                    () -> trustedListXmlArea,
                    () -> trustedListCertArea,
                    () -> trustedListOutputArea,
                    () -> trustedEntityListJsonArea,
                    () -> trustedEntityListSignerCertArea,
                    () -> trustedEntityListSearchCertArea), () -> statusReporter);
        }
        return walletTrustCoordinator;
    }

    private WalletCborCoordinator cborCoordinator() {
        if (walletCborCoordinator == null) {
            walletCborCoordinator = new WalletCborCoordinator(new WalletCborCoordinator.View(
                    () -> cborInputArea,
                    () -> cborViewCombo,
                    () -> cborOutputArea,
                    () -> cborJsonArea,
                    () -> cborFromJsonOutputArea), () -> statusReporter);
        }
        return walletCborCoordinator;
    }

    private WalletScaCoordinator scaCoordinator() {
        if (walletScaCoordinator == null) {
            walletScaCoordinator = new WalletScaCoordinator(new WalletScaCoordinator.View(
                    () -> sdJwtAlgoCombo,
                    () -> scaTypeCombo,
                    () -> scaCredentialIdsField,
                    () -> scaPayloadArea,
                    () -> scaEntryArea,
                    () -> scaPresentationArea,
                    () -> scaTransactionDataArea,
                    () -> scaIssuerKeyArea,
                    () -> scaHolderKeyArea,
                    () -> scaAudienceField,
                    () -> scaNonceField,
                    () -> scaResponseModeField,
                    () -> scaOutputArea,
                    () -> oid4vpRequestArea,
                    () -> oid4vpKeyArea,
                    () -> oid4vpOutputArea,
                    () -> adesFileNameField,
                    () -> adesDocumentArea,
                    () -> adesOutputArea), () -> statusReporter);
        }
        return walletScaCoordinator;
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
    private void handleEidasCertInspect() { trustCoordinator().handleEidasCertInspect(); }

    // --------------------------------------------------------- trusted list

    @FXML
    private void handleTrustedListInspect() { trustCoordinator().handleTrustedListInspect(); }

    @FXML
    private void handleTrustedEntityListJsonInspect() { trustCoordinator().handleTrustedEntityListJsonInspect(); }

    @FXML
    private void handleTrustedListVerify() { trustCoordinator().handleTrustedListVerify(); }

    @FXML
    private void handleTrustedListFind() { trustCoordinator().handleTrustedListFind(); }

    // ------------------------------------------------------------------ CBOR

    @FXML
    private void handleCborInspect() { cborCoordinator().handleCborInspect(); }

    @FXML
    private void handleCborToJson() { cborCoordinator().handleCborToJson(); }

    @FXML
    private void handleCborFromJson() { cborCoordinator().handleCborFromJson(); }

    // ------------------------------------------------------ SCA / OpenID4VP

    @FXML
    private void handleScaBuild() { scaCoordinator().handleScaBuild(); }

    @FXML
    private void handleScaVerify() { scaCoordinator().handleScaVerify(); }

    @FXML
    private void handleOid4vpInspect() { scaCoordinator().handleOid4vpInspect(); }

    // ------------------------------------------------------ AdES validation

    @FXML
    private void handleAdesValidate() { scaCoordinator().handleAdesValidate(); }

    @FXML
    private void handleAdesEtsiReport() { scaCoordinator().handleAdesEtsiReport(); }

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

    private void updateStatus(String message) {
        if (statusReporter != null) statusReporter.updateStatus(message);
    }
}
