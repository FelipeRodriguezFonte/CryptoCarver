package com.cryptocarver.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.Map;

/**
 * FXML boundary for the certificate, CRL and CMS laboratory.
 *
 * <p>The cryptographic state remains in the shared {@link KeysController}, so
 * certificates can consume a key pair generated in the Keys module without
 * duplicating or exporting private material.</p>
 */
public class CertificatesController {
    @FXML private VBox certificatesContainer;

    @FXML private TextField certCNField, certOrgField, certOUField, certCountryField;
    @FXML private TextField certStateField, certLocalityField, certEmailField, certValidityField;
    @FXML private ComboBox<String> certKeyTypeCombo, certSignAlgoCombo;
    @FXML private TextArea certOutputArea;
    @FXML private TextField certSanDnsField, certSanIpField;
    @FXML private CheckBox certRootCaCheck;

    @FXML private TextArea certInputArea, certParseResultArea;
    @FXML private Label certBadgeLabel;
    private com.cryptocarver.ui.component.MaterialFieldBadge certBadge;
    @FXML private ComboBox<String> certTemplateCombo;
    private StatusReporter statusReporter;
    @FXML private TextArea certCompareLeftArea, certCompareRightArea, certCompareResultArea;
    @FXML private TextArea certIssueCsrArea, certIssueCaCertArea, certIssueCaKeyArea, certIssueResultArea;
    @FXML private TextField certIssueValidityField, certIssueSignatureField, certIssuePathLengthField;
    @FXML private ComboBox<String> certIssueProfileCombo;

    @FXML private TextArea valCertInput, valIssuerInput, valResultArea;
    @FXML private TextArea chainInputArea, chainCrlInputArea, chainResultArea;
    @FXML private TextArea crlIssuerCertArea, crlIssuerKeyArea, crlExistingCrlArea, crlResultArea;
    @FXML private TextField crlRevokeSerialField;
    @FXML private ComboBox<String> crlRevokeReasonCombo;

    @FXML private TextArea cmsInputArea, cmsOutputArea, cmsVerifyDataArea;
    @FXML private CheckBox cmsDetachedCheck, cmsCadesBesCheck, cmsCadesTCheck;
    @FXML private TextField cmsCadesTsaUrlField;
    @FXML private HBox cmsCadesTsaBox;
    @FXML private TextArea cmsSignCertArea, cmsSignKeyArea, cmsEncryptCertArea, cmsDecryptKeyArea;
    @FXML private ToggleGroup cmsSignSourceToggleGroup, cmsEncryptSourceToggleGroup;
    @FXML private RadioButton cmsSignSourceLocalRadio, cmsSignSourcePkcs11Radio;
    @FXML private RadioButton cmsEncryptSourceLocalRadio, cmsEncryptSourcePkcs11Radio;
    @FXML private GridPane cmsSignLocalGrid, cmsEncryptLocalGrid;
    @FXML private HBox cmsSignPkcs11Box, cmsEncryptPkcs11Box;
    @FXML private ComboBox<String> cmsSignKeyAliasCombo, cmsEncryptKeyAliasCombo;

    @FXML private VBox padesContainer;
    @FXML private PadesController padesContainerController;
    @FXML private VBox asicContainer;
    @FXML private AsicController asicContainerController;
    @FXML private TitledPane cmsInspector;
    @FXML private CmsInspectorController cmsInspectorController;
    @FXML private TitledPane asn1;
    @FXML private ASN1Controller asn1Controller;

    private KeysController keysController;

    public void init(StatusReporter reporter, KeysController sharedKeysController) {
        this.statusReporter = reporter;
        this.keysController = sharedKeysController;
        refreshCertTemplateCombo();
        if (keysController == null) return;

        keysController.initializeCertificateGen(
                certCNField, certOrgField, certOUField, certLocalityField, certStateField, certCountryField,
                certEmailField, certValidityField, certKeyTypeCombo, certSignAlgoCombo, certOutputArea,
                certSanDnsField, certSanIpField, certRootCaCheck);
        keysController.initializeCertificateChain(chainInputArea, chainCrlInputArea, chainResultArea);
        keysController.initializeCertificateParse(certInputArea, certParseResultArea);
        keysController.initializeCertificateComparator(certCompareLeftArea, certCompareRightArea, certCompareResultArea);
        keysController.initializeCertificateIssuer(certIssueCsrArea, certIssueCaCertArea, certIssueCaKeyArea,
                certIssueValidityField, certIssueSignatureField, certIssueResultArea, certIssueProfileCombo,
                certIssuePathLengthField);
        keysController.initializeCrlManagement(crlIssuerCertArea, crlIssuerKeyArea, crlExistingCrlArea,
                crlRevokeSerialField, crlRevokeReasonCombo, crlResultArea);
        keysController.initializeValidateCertificate(valCertInput, valIssuerInput, valResultArea);
        keysController.initializeCMS(cmsInputArea, cmsOutputArea, cmsDetachedCheck, cmsCadesBesCheck,
                cmsCadesTCheck, cmsCadesTsaUrlField, cmsCadesTsaBox, cmsSignCertArea, cmsSignKeyArea,
                cmsEncryptCertArea, cmsDecryptKeyArea, cmsSignSourcePkcs11Radio, cmsSignLocalGrid,
                cmsSignPkcs11Box, cmsSignKeyAliasCombo, cmsVerifyDataArea, cmsEncryptSourcePkcs11Radio,
                cmsEncryptLocalGrid, cmsEncryptPkcs11Box, cmsEncryptKeyAliasCombo);

        IngestionUIHelper.bindField(certInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);

        if (certBadgeLabel != null && certBadge == null) {
            certBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Certificate");
            certBadge.attach(certInputArea, "PEM / DER");
            certBadge.textProperty().addListener((obs, oldVal, newVal) -> certBadgeLabel.setText(newVal));
            certBadge.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> {
                certBadgeLabel.getStyleClass().setAll(certBadge.getStyleClass());
            });
        }
        IngestionUIHelper.bindField(certIssueCsrArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CSR);
        IngestionUIHelper.bindField(certIssueCaCertArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(certIssueCaKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);
        IngestionUIHelper.bindField(certCompareLeftArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(certCompareRightArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(valCertInput, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(valIssuerInput, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(chainInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(chainCrlInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CRL);
        IngestionUIHelper.bindField(cmsInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(cmsSignCertArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(cmsSignKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);
        IngestionUIHelper.bindField(cmsEncryptCertArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
        IngestionUIHelper.bindField(cmsDecryptKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);

        if (padesContainerController != null) padesContainerController.setStatusReporter(reporter);
        if (asicContainerController != null) asicContainerController.setStatusReporter(reporter);
        if (cmsInspectorController != null) cmsInspectorController.init(reporter);
        if (asn1Controller != null) asn1Controller.init(reporter);
    }

    public void expandPane(String paneName) {
        if (paneName == null || paneName.isBlank() || certificatesContainer == null) return;
        Accordion accordion = certificatesContainer.getChildren().stream()
                .filter(Accordion.class::isInstance).map(Accordion.class::cast).findFirst().orElse(null);
        if (accordion == null) return;
        if ("CMS Inspector".equals(paneName) && cmsInspector != null) {
            accordion.setExpandedPane(cmsInspector);
            return;
        }
        accordion.getPanes().stream()
                .filter(pane -> pane.getText().contains(paneName)
                        || paneName.contains(stripEmoji(pane.getText())))
                .findFirst().ifPresent(accordion::setExpandedPane);
    }

    public Accordion getAccordion() {
        if (certificatesContainer == null) return null;
        return certificatesContainer.getChildren().stream()
                .filter(Accordion.class::isInstance).map(Accordion.class::cast).findFirst().orElse(null);
    }

    public void selectAsn1DecodeTab() {
        if (asn1Controller != null) asn1Controller.selectDecodeTab();
    }

    public void selectAsn1EncodeTab() {
        if (asn1Controller != null) asn1Controller.selectEncodeTab();
    }

    private String stripEmoji(String text) {
        return text.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "").trim();
    }

    @FXML private void handleGenerateCertificate() { keysController.handleGenerateCertificate(); }
    @FXML private void handleGenerateCSR() { keysController.handleGenerateCSR(); }
    @FXML private void handleCompareCertificates() { keysController.handleCompareCertificates(); }
    @FXML private void handleIssueCertificateFromCsr() { keysController.handleIssueCertificateFromCsr(); }
    @FXML private void handleGenerateCrl() { keysController.handleGenerateCrl(); }
    @FXML private void handleRevokeCrl() { keysController.handleRevokeCrl(); }
    @FXML private void handleValidateCertificateChain() { keysController.handleValidateCertificateChain(); }
    @FXML private void handleValidateCertificate() { keysController.handleValidateCertificate(); }
    @FXML private void handleParseCertificate() { keysController.handleParseCertificate(); }
    @FXML private void handleCMSSign() { keysController.handleCMSSign(); }
    @FXML private void handleUpgradeCadesLt() { keysController.handleUpgradeCadesLt(); }
    @FXML private void handleCMSourceChanged() { keysController.handleCMSourceChanged(); }
    @FXML private void handleCadesTimestampOptionChanged() { keysController.handleCadesTimestampOptionChanged(); }
    @FXML private void handleCMSEncryptSourceChanged() { keysController.handleCMSEncryptSourceChanged(); }
    @FXML private void handleLoadCMSKeys() { keysController.handleLoadCMSKeys(); }
    @FXML private void handleLoadCMSEncryptKeys() { keysController.handleLoadCMSEncryptKeys(); }
    @FXML private void handleCMSVerify() { keysController.handleCMSVerify(); }
    @FXML private void handleCMSEncrypt() { keysController.handleCMSEncrypt(); }
    @FXML private void handleCMSDecrypt() { keysController.handleCMSDecrypt(); }

    private void refreshCertTemplateCombo() {
        SafeTemplateUIHelper.populateTemplateCombo(
                certTemplateCombo,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CERTIFICATE_INSPECTION,
                List.of("Certificate inspection — PEM")
        );
    }

    @FXML
    private void handleApplyCertTemplate() {
        String template = certTemplateCombo != null ? certTemplateCombo.getValue() : null;
        if (template == null) return;

        Map<String, java.util.function.Consumer<String>> setters = Map.of(
                "certFormatCombo", v -> { if (certTemplateCombo != null) certTemplateCombo.setValue(v); }
        );

        SafeTemplateUIHelper.applySelectedTemplate(
                template,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CERTIFICATE_INSPECTION,
                () -> {
                    if (template.contains("PEM")) {
                        if (certInputArea != null) {
                            certInputArea.setPromptText("Paste certificate in PEM format... (PEM Certificate template)");
                        }
                        if (statusReporter != null) {
                            statusReporter.setInputFormat("Plain Text");
                            statusReporter.updateStatus("Template Applied: Certificate inspection — PEM");
                        }
                    }
                },
                setters,
                statusReporter
        );
    }

    @FXML
    private void handleSaveCertTemplate() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (certTemplateCombo != null && certTemplateCombo.getValue() != null) params.put("certFormatCombo", certTemplateCombo.getValue());
        javafx.stage.Window owner = certTemplateCombo != null && certTemplateCombo.getScene() != null ? certTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.saveCurrentAsTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CERTIFICATE_INSPECTION, params, this::refreshCertTemplateCombo, statusReporter);
    }

    @FXML
    private void handleExportCertTemplate() {
        javafx.stage.Window owner = certTemplateCombo != null && certTemplateCombo.getScene() != null ? certTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.exportSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CERTIFICATE_INSPECTION, certTemplateCombo, statusReporter);
    }

    @FXML
    private void handleImportCertTemplate() {
        javafx.stage.Window owner = certTemplateCombo != null && certTemplateCombo.getScene() != null ? certTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.importTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CERTIFICATE_INSPECTION, this::refreshCertTemplateCombo, statusReporter);
    }

    @FXML
    private void handleDeleteCertTemplate() {
        javafx.stage.Window owner = certTemplateCombo != null && certTemplateCombo.getScene() != null ? certTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.deleteSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CERTIFICATE_INSPECTION, certTemplateCombo, this::refreshCertTemplateCombo, statusReporter);
    }

    @FXML
    private void handleResetCertDefaults() {
        certInputArea.setText("");
        certParseResultArea.setText("");
        if (statusReporter != null) {
            statusReporter.setInputFormat("Plain Text");
            statusReporter.updateStatus("Certificate parsing form reset to default");
        }
    }
}
