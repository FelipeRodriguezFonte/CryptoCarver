package com.cryptocarver.ui;

import com.cryptocarver.crypto.XMLSignatureOperations;
import com.cryptocarver.crypto.TsaDiagnostics;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.TsaUrlSanitizer;
import com.cryptocarver.utils.OperationHistory;
import javafx.stage.FileChooser;
import javafx.scene.control.*;
import javafx.fxml.FXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for XML Security (XAdES) operations
 */
public class XMLSignatureController {

    private static final Logger LOG = LoggerFactory.getLogger(XMLSignatureController.class);

    private StatusReporter statusReporter;

    // Sign UI

    @FXML private javafx.scene.control.RadioButton xmlSignSourceLocalRadio;
    @FXML private javafx.scene.control.RadioButton xmlSignSourcePkcs11Radio;
    @FXML private javafx.scene.control.ToggleGroup xmlSignSourceToggleGroup;
    @FXML private javafx.scene.layout.VBox xmlSignLocalKeyBox;
    @FXML private javafx.scene.control.Label xmlSignKeyPathLabel;
    @FXML private javafx.scene.control.Label xmlSignKeyPasswordLabel;

    @FXML private TextField xmlSignInputPathField;
    @FXML private TextField xmlSignKeyPathField;
    @FXML private PasswordField xmlSignKeyPasswordField;
    @FXML private ComboBox<String> xmlSignKeyAliasCombo;
    @FXML private TextArea xmlSignOutputArea;
    @FXML private ComboBox<String> xmlSignLevelCombo;
    @FXML private ComboBox<String> xmlSignPackagingCombo;
    @FXML private ComboBox<String> xmlSignTsaUrlText;
    @FXML private ComboBox<String> xmlSignTsaProfileCombo;
    @FXML private TextField xmlSignTsaProfileNameField;
    @FXML private ComboBox<String> xmlSignTsaAuthTypeCombo;
    @FXML private TextField xmlSignTsaUserField;
    @FXML private PasswordField xmlSignTsaPasswordField;

    private static final String NO_TSA = "No TSA (XAdES-BASELINE-B)";
    private static final String DIGICERT_TSA = "DigiCert — http://timestamp.digicert.com";
    private static final String FREETSA_TSA = "FreeTSA — https://freetsa.org/tsr";

    // Verify UI
    @FXML private TextArea xmlVerifyInputArea; // Or file path
    @FXML private TextArea xmlVerifyReportArea;
    @FXML private TextField xmlVerifyTrustStorePathField;
    @FXML private PasswordField xmlVerifyTrustStorePasswordField;
    @FXML private ComboBox<String> xmlVerifyTrustStoreProfileCombo;

    // Inspector UI (local structural analysis; no validation is performed)
    @FXML private TextArea xmlInspectInputArea;
    @FXML private TextArea xmlInspectReportArea;

    @FXML private TextField xmlTimestampFileField;
    @FXML private TextField xmlTimestampUrlField;
    @FXML private ComboBox<String> xmlTimestampHashCombo;
    @FXML private TextField xmlTimestampTokenField;
    @FXML private TextArea xmlTimestampReportArea;
    @FXML private TextField xmlTimestampTrustStoreField;
    @FXML private PasswordField xmlTimestampTrustStorePasswordField;

    @FXML private Accordion xmlAccordion;
    @FXML private javafx.scene.layout.VBox xmlSecurityContainer;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private void showValidationError(String title, String message, String fieldKey) {
        if (statusReporter != null) {
            String safeMessage = InlineErrorPresenter.redactSecrets(message);
            statusReporter.showError(new UserFacingError(title, safeMessage, safeMessage, fieldKey));
        }
    }

    private byte[] lastTimestampToken;
    private XmlSignatureSigningCoordinator signingCoordinator;
    private XmlSignatureKeyMaterialCoordinator keyMaterialCoordinator;

    private XmlSignatureKeyMaterialCoordinator keyMaterialCoordinator() {
        if (keyMaterialCoordinator == null) {
            keyMaterialCoordinator = new XmlSignatureKeyMaterialCoordinator(new XmlSignatureKeyMaterialCoordinator.View(
                    () -> xmlSignSourcePkcs11Radio, () -> xmlSignKeyPathField, () -> xmlSignKeyPasswordField,
                    () -> xmlSignKeyAliasCombo, () -> xmlVerifyTrustStorePathField,
                    () -> xmlVerifyTrustStorePasswordField, () -> xmlVerifyTrustStoreProfileCombo,
                    () -> xmlTimestampTrustStoreField, this::chooseFile),
                    () -> statusReporter);
        }
        return keyMaterialCoordinator;
    }

    private XmlSignatureSigningCoordinator signingCoordinator() {
        if (signingCoordinator == null) {
            signingCoordinator = new XmlSignatureSigningCoordinator(new XmlSignatureSigningCoordinator.View(
                    () -> xmlSignInputPathField, () -> xmlSignLevelCombo, () -> xmlSignPackagingCombo,
                    () -> xmlSignSourcePkcs11Radio, () -> xmlSignKeyAliasCombo, () -> xmlSignKeyPathField,
                    () -> xmlSignKeyPasswordField, () -> xmlSignOutputArea, () -> xmlVerifyInputArea,
                    () -> xmlVerifyReportArea, () -> xmlVerifyTrustStorePathField,
                    () -> xmlVerifyTrustStorePasswordField, () -> xmlInspectInputArea, () -> xmlInspectReportArea,
                    this::getTsaUrl, this::isHttpUrl, this::saveCustomTsa, this::publishedTsaUrl,
                    this::getTsaCredentials, this::handleLoadXMLKeys, this::showValidationError),
                    () -> statusReporter);
        }
        return signingCoordinator;
    }

    public XMLSignatureController() {
    }

    public void initModule(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    @FXML
    private void handleXMLSignSourceChanged() {
        boolean isLocal = xmlSignSourceLocalRadio.isSelected();
        xmlSignLocalKeyBox.setVisible(isLocal);
        xmlSignLocalKeyBox.setManaged(isLocal);
        xmlSignKeyPathLabel.setVisible(isLocal);
        xmlSignKeyPathLabel.setManaged(isLocal);
        xmlSignKeyPathField.setVisible(isLocal);
        xmlSignKeyPathField.setManaged(isLocal);

        xmlSignKeyPasswordLabel.setVisible(isLocal);
        xmlSignKeyPasswordLabel.setManaged(isLocal);
        xmlSignKeyPasswordField.setVisible(isLocal);
        xmlSignKeyPasswordField.setManaged(isLocal);

        // Aliases belong to the selected source; never reuse a local entry for a token.
        xmlSignKeyAliasCombo.getItems().clear();
        xmlSignKeyAliasCombo.getSelectionModel().clearSelection();
    }

    public void initialize() {
        moduleI18n = ModuleI18n.bind(xmlSecurityContainer, ModuleTextCatalog.xmlSecurity());
        xmlSignLevelCombo.getItems().addAll("XAdES-BASELINE-B", "XAdES-BASELINE-T", "XAdES-BASELINE-LT", "XAdES-BASELINE-LTA");
        xmlSignLevelCombo.setValue("XAdES-BASELINE-B");
        xmlSignPackagingCombo.getItems().setAll("ENVELOPED", "ENVELOPING", "DETACHED");
        xmlSignPackagingCombo.setValue("ENVELOPED");
        xmlSignTsaUrlText.getItems().setAll(NO_TSA, DIGICERT_TSA, FREETSA_TSA);
        String customTsa = AppSettings.getInstance().getCustomTsaUrl();
        xmlSignTsaUrlText.setValue(customTsa.isBlank() ? NO_TSA : customTsa);
        reloadTsaProfiles();

        if (xmlSignTsaAuthTypeCombo != null) {
            xmlSignTsaAuthTypeCombo.getItems().addAll("NONE", "BASIC", "BEARER");
            xmlSignTsaAuthTypeCombo.setValue("NONE");
        }

        xmlVerifyTrustStoreProfileCombo.getItems().setAll(AppSettings.getInstance().getTrustStoreProfiles().stream()
                .map(AppSettings.TrustStoreProfile::name).sorted(String.CASE_INSENSITIVE_ORDER).toList());

        xmlTimestampHashCombo.getItems().setAll("SHA-256", "SHA-384", "SHA-512");
        xmlTimestampHashCombo.setValue("SHA-256");
        String saved = AppSettings.getInstance().getCustomTsaUrl();
        if (!saved.isBlank()) xmlTimestampUrlField.setText(saved);
        handleXMLSignSourceChanged();
    }

    public void expandAccordionPane(String itemName) {
        if (xmlAccordion == null) return;
        for (TitledPane pane : xmlAccordion.getPanes()) {
            if (ModulePaneMatcher.matches(pane, itemName, ModuleTextCatalog.xmlSecurity())) {
                xmlAccordion.setExpandedPane(pane);
                break;
            }
        }
    }

    public void fillClipboardInput(String value) {
        if (xmlInspectInputArea != null) xmlInspectInputArea.setText(value);
    }

    @FXML
    public void handleReset() {
        ModuleResetPolicy.apply(xmlSecurityContainer, ModuleResetPolicy.Action.RESET_DEFAULTS,
                this::clearModuleData, this::restoreSafeDefaults);
        if (statusReporter != null) statusReporter.updateStatus(t("module.xml.resetStatus"));
    }

    @FXML
    public void handleClear() {
        ModuleResetPolicy.apply(xmlSecurityContainer, ModuleResetPolicy.Action.CLEAR,
                this::clearModuleData, null);
        if (statusReporter != null) statusReporter.updateStatus(t("module.xml.clearStatus"));
    }

    private void clearModuleData() {
        ModuleResetPolicy.clearTextInputs(xmlSecurityContainer);
        lastTimestampToken = null;
    }

    private void restoreSafeDefaults() {
        if (xmlSignLevelCombo != null) xmlSignLevelCombo.setValue("XAdES-BASELINE-B");
        if (xmlSignPackagingCombo != null) xmlSignPackagingCombo.setValue("ENVELOPED");
        if (xmlSignTsaAuthTypeCombo != null) xmlSignTsaAuthTypeCombo.setValue("NONE");
        if (xmlTimestampHashCombo != null) xmlTimestampHashCombo.setValue("SHA-256");
    }

    @FXML
    public void handleBrowseXMLSignInput() {
        File file = chooseFile("Select XML to Sign");
        if (file != null) {
            xmlSignInputPathField.setText(file.getAbsolutePath());
        }
    }

    private com.cryptocarver.model.TsaAuthCredentials getTsaCredentials() {
        if (xmlSignTsaAuthTypeCombo == null) return null;
        String typeStr = xmlSignTsaAuthTypeCombo.getValue();
        if (typeStr == null || "NONE".equals(typeStr)) return null;
        com.cryptocarver.model.TsaAuthCredentials.AuthType type =
            com.cryptocarver.model.TsaAuthCredentials.AuthType.valueOf(typeStr);
        String user = xmlSignTsaUserField != null ? xmlSignTsaUserField.getText() : "";
        String pass = xmlSignTsaPasswordField != null ? xmlSignTsaPasswordField.getText() : "";
        return new com.cryptocarver.model.TsaAuthCredentials(type, user, pass);
    }

    @FXML
    public void handleBrowseXMLKey() { keyMaterialCoordinator().handleBrowseXMLKey(); }

    @FXML
    public void handleLoadXMLKeys() { keyMaterialCoordinator().handleLoadXMLKeys(); }

    @FXML
    public void handleTestTSA() {
        String url = getTsaUrl();
        if (url == null) {
            statusReporter.showError("TSA Test", t("module.xml.tsaRequired", "XAdES"));
            return;
        }
        if (!isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        saveCustomTsa(url);
        statusReporter.updateStatus(t("module.xml.status.testing"));
        com.cryptocarver.model.TsaAuthCredentials auth = getTsaCredentials();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                TsaDiagnostics.TokenResult result = TsaDiagnostics.timestamp(url, "CryptoCarver TSA diagnostic".getBytes(java.nio.charset.StandardCharsets.UTF_8), "SHA-256", 15000, 20000, 1024*1024, auth);
                TsaDiagnostics.Report report = result.report();
                javafx.application.Platform.runLater(() -> statusReporter.showInfo("TSA Test", t("module.xml.status.success")
                        + "\nURL: " + publishedTsaUrl(report.url()) + "\nHTTP: " + report.httpStatus() + "\nLatency: " + report.latencyMs()
                                + " ms\nPolicy: " + report.policyOid() + "\nImprint: " + report.imprintAlgorithmOid()
                                + "\nToken time: " + report.generationTime() + "\nResponse: " + report.responseBytes() + " bytes"));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> statusReporter.showError("TSA Test", t("module.xml.error.generic", e.getMessage())));
            }
        });
    }

    @FXML
    public void handleSaveTSA() {
        String url = getTsaUrl();
        if (url == null) {
            statusReporter.showError("Save TSA", t("module.xml.feedback.tsaRequestRequired"));
            return;
        }
        if (!isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        saveCustomTsa(url);
        statusReporter.showInfo("TSA Saved", t("module.xml.status.success") + "\n\n" + publishedTsaUrl(url));
    }

    @FXML
    public void handleLoadTSASavedProfile() {
        String name = xmlSignTsaProfileCombo.getValue();
        if (name == null || name.isBlank()) {
            statusReporter.showError("TSA Profile", t("module.xml.feedback.tsaProfileRequired"));
            return;
        }
        AppSettings.getInstance().getTsaProfiles().stream()
                .filter(profile -> name.equals(profile.name()))
                .findFirst()
                .ifPresentOrElse(profile -> {
                    xmlSignTsaUrlText.getEditor().setText(profile.url());
                    xmlSignTsaUrlText.setValue(profile.url());
                    xmlSignTsaProfileNameField.setText(profile.name());
                    statusReporter.updateStatus(t("module.xml.status.success") + " (" + profile.name() + ")");
                }, () -> statusReporter.showError("TSA Profile", t("module.xml.profileMissing")));
    }

    @FXML
    public void handleSaveTSASavedProfile() {
        String url = getTsaUrl();
        String name = xmlSignTsaProfileNameField.getText().trim();
        if (name.isEmpty()) {
            statusReporter.showError("TSA Profile", t("module.xml.feedback.tsaProfileNameRequired"));
            return;
        }
        if (url == null || !isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        AppSettings.getInstance().saveTsaProfile(name, url);
        saveCustomTsa(url);
        reloadTsaProfiles();
        xmlSignTsaProfileCombo.setValue(name);
        statusReporter.showInfo("TSA Profile Saved", name + "\n" + publishedTsaUrl(url) + "\n\nOnly the endpoint is saved; no credentials are stored.");
    }

    @FXML
    public void handleDeleteTSASavedProfile() {
        String name = xmlSignTsaProfileCombo.getValue();
        if (name == null || name.isBlank()) {
            statusReporter.showError("TSA Profile", t("module.xml.feedback.tsaProfileRequired"));
            return;
        }
        AppSettings.getInstance().removeTsaProfile(name);
        reloadTsaProfiles();
        xmlSignTsaProfileNameField.clear();
        statusReporter.updateStatus(t("module.xml.status.success") + " (" + name + ")");
    }

    @FXML
    public void handleSignXML() { signingCoordinator().handleSignXML(); }

    @FXML
    public void handleVerifyXML() { signingCoordinator().handleVerifyXML(); }

    @FXML
    public void handleBrowseXMLInspectorInput() {
        File file = chooseFile("Select Signed XML to Inspect");
        if (file == null) return;
        try {
            xmlInspectInputArea.setText(Files.readString(file.toPath()));
            statusReporter.updateStatus(t("module.xml.feedback.statusLoaded", file.getName()));
        } catch (Exception e) {
            statusReporter.showError("XML Inspector", t("module.xml.operationFailed", "XML read", e.getMessage()));
        }
    }

    @FXML
    public void handleInspectSignedXML() { signingCoordinator().handleInspectSignedXML(); }

    @FXML
    public void handleBrowseTimestampFile() {
        File file = chooseFile("Select File to Timestamp");
        if (file != null) xmlTimestampFileField.setText(file.getAbsolutePath());
    }

    @FXML
    public void handleRequestTimestamp() {
        String path = xmlTimestampFileField.getText().trim();
        String url = xmlTimestampUrlField.getText().trim();
        String hash = xmlTimestampHashCombo.getValue();
        if (path.isEmpty() || url.isEmpty()) {
            statusReporter.showError("RFC 3161 Timestamp", t("module.xml.feedback.tsaRequestRequired"));
            return;
        }
        if (!isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", t("module.xml.tsaUrlInvalid"));
            return;
        }
        try {
            byte[] data = Files.readAllBytes(new File(path).toPath());
            saveCustomTsa(url);
            statusReporter.updateStatus(t("module.xml.feedback.timestampRequesting"));
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    TsaDiagnostics.TokenResult result = TsaDiagnostics.timestamp(url, data, hash);
                    javafx.application.Platform.runLater(() -> {
                        lastTimestampToken = result.token();
                        TsaDiagnostics.Report report = result.report();
                        TsaDiagnostics.TokenInspection tokenInfo;
                        try {
                            tokenInfo = TsaDiagnostics.inspectToken(result.token());
                        } catch (Exception ignored) {
                            tokenInfo = null;
                        }
                        String text = "--- RFC 3161 Timestamp ---\nFile: " + path + "\nData bytes: " + data.length
                                + "\n" + hash + ": " + result.dataSha256() + "\nTSA: " + publishedTsaUrl(report.url()) + "\nHTTP: " + report.httpStatus()
                                + "\nLatency: " + report.latencyMs() + " ms\nPolicy: " + report.policyOid()
                                + "\nToken time: " + report.generationTime() + "\nToken bytes: " + report.responseBytes();
                        if (tokenInfo != null) {
                            text += "\nTSA certificate subject: " + tokenInfo.signerSubject()
                                    + "\nTSA certificate issuer: " + tokenInfo.signerIssuer()
                                    + "\nTSA certificate SHA-256: " + tokenInfo.signerSha256();
                        }
                        xmlTimestampReportArea.setText(text);
                        Map<String, String> details = new HashMap<>();
                        details.put("File", path); details.put("Hash", hash); details.put("Imprint", result.dataSha256()); details.put("TSA", publishedTsaUrl(url));
                        details.put("Token bytes", String.valueOf(result.token().length));
                        if (tokenInfo != null) details.put("TSA certificate SHA-256", tokenInfo.signerSha256());
                        statusReporter.publish(OperationResult.forOperation("RFC 3161 Timestamp")
                                .input(data).output(result.token()).details(details)
                                .status(t("module.xml.feedback.timestampReceived")).build());
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> statusReporter.showError("RFC 3161 Timestamp", e.getMessage()));
                }
            });
        } catch (Exception e) { statusReporter.showError("RFC 3161 Timestamp",
                t("module.xml.operationFailed", "Timestamp request", e.getMessage())); }
    }

    @FXML
    public void handleSaveTimestampToken() {
        if (lastTimestampToken == null) { statusReporter.showError("Save Timestamp", t("module.xml.feedback.timestampTokenRequired")); return; }
        FileChooser chooser = new FileChooser(); chooser.setTitle("Save RFC 3161 Timestamp Token"); chooser.setInitialFileName("timestamp.tsr");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Timestamp response", "*.tsr", "*.tst"));
        File file = chooser.showSaveDialog(null); if (file == null) return;
        try { Files.write(file.toPath(), lastTimestampToken); statusReporter.updateStatus(t("module.xml.feedback.timestampSaved", file.getName())); }
        catch (Exception e) { statusReporter.showError("Save Timestamp", t("module.xml.operationFailed", "Timestamp save", e.getMessage())); }
    }

    @FXML
    public void handleBrowseTimestampToken() {
        File file = chooseFile("Select RFC 3161 Timestamp Token");
        if (file != null) xmlTimestampTokenField.setText(file.getAbsolutePath());
    }

    @FXML
    public void handleInspectTimestampToken() {
        String tokenPath = xmlTimestampTokenField.getText().trim();
        if (tokenPath.isEmpty()) { statusReporter.showError("Timestamp Token", t("module.xml.feedback.timestampFileRequired")); return; }
        try {
            byte[] token = Files.readAllBytes(new File(tokenPath).toPath());
            TsaDiagnostics.TokenInspection info = TsaDiagnostics.inspectToken(token);
            String text = "--- Saved RFC 3161 Token ---\nToken: " + tokenPath + "\nBytes: " + info.responseBytes()
                    + "\nPolicy: " + info.policyOid() + "\nImprint algorithm: " + info.imprintAlgorithmOid()
                    + "\nImprint: " + info.imprintHex() + "\nGeneration time: " + info.generationTime()
                    + "\nSerial: " + info.serialNumber() + "\nSigner: " + info.signerId()
                    + "\nCMS Algorithm: " + info.signatureAlgorithm()
                    + "\nTSA certificate subject: " + info.signerSubject() + "\nTSA certificate issuer: " + info.signerIssuer()
                    + "\nTSA certificate SHA-256: " + info.signerSha256()
                    + "\nTSA cert validity: " + info.certNotBefore() + " to " + info.certNotAfter()
                    + "\nTSA timeStamping EKU: " + (info.hasTimeStampingEku() ? "Present" : "Missing")
                    + "\n\n--- Embedded Certificate Chain ---\n" + info.certificateChainInfo();
            String dataPath = xmlTimestampFileField.getText().trim();
            if (!dataPath.isEmpty()) text += "\nMatches selected file: " + (TsaDiagnostics.tokenMatchesData(token, Files.readAllBytes(new File(dataPath).toPath())) ? "YES" : "NO");
            xmlTimestampReportArea.setText(text + "\n\nNote: imprint matching does not validate the TSA certificate chain.");
        } catch (Exception e) { statusReporter.showError("Timestamp Token",
                t("module.xml.operationFailed", "Timestamp inspection", e.getMessage())); }
    }

    @FXML
    public void handleValidateTimestampToken() {
        String tokenPath = xmlTimestampTokenField.getText().trim();
        if (tokenPath.isEmpty()) { statusReporter.showError("Timestamp Token", t("module.xml.feedback.timestampFileRequired")); return; }
        String trustStorePath = xmlTimestampTrustStoreField != null ? xmlTimestampTrustStoreField.getText().trim() : "";
        String trustStorePassword = xmlTimestampTrustStorePasswordField != null ? xmlTimestampTrustStorePasswordField.getText() : "";
        String dataPath = xmlTimestampFileField.getText().trim();
        byte[] data = null;
        try {
            if (!dataPath.isEmpty()) data = Files.readAllBytes(new File(dataPath).toPath());
            byte[] token = Files.readAllBytes(new File(tokenPath).toPath());
            String report = TsaDiagnostics.validateToken(token, data, trustStorePath.isEmpty() ? null : trustStorePath, trustStorePassword);
            xmlTimestampReportArea.setText(report);
            statusReporter.updateStatus(t("module.xml.feedback.timestampValidated"));
        } catch (Exception e) {
            statusReporter.showError("Timestamp Token", t("module.xml.operationFailed", "Timestamp validation", e.getMessage()));
        }
    }

    @FXML
    public void handleBrowseTimestampTrustStore() { keyMaterialCoordinator().handleBrowseTimestampTrustStore(); }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        return fileChooser.showOpenDialog(null); // Should use stage if possible
    }

    @FXML
    public void handleBrowseXMLTrustStore() { keyMaterialCoordinator().handleBrowseXMLTrustStore(); }

    @FXML
    public void handleLoadXMLTrustStoreProfile() { keyMaterialCoordinator().handleLoadXMLTrustStoreProfile(); }

    @FXML
    public void handleSaveSignedXML() { signingCoordinator().handleSaveSignedXML(); }

    private String getTsaUrl() {
        String selected = xmlSignTsaUrlText.getEditor().getText().trim();
        if (selected.isEmpty() || NO_TSA.equals(selected)) {
            return null;
        }
        if (DIGICERT_TSA.equals(selected)) {
            return "http://timestamp.digicert.com";
        }
        if (FREETSA_TSA.equals(selected)) {
            return "https://freetsa.org/tsr";
        }
        return selected;
    }

    private boolean isHttpUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isPresetTsa(String url) {
        return "http://timestamp.digicert.com".equals(url) || "https://freetsa.org/tsr".equals(url);
    }

    private boolean hasTsaUserInfo(String url) {
        try {
            return java.net.URI.create(url).getRawUserInfo() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void saveCustomTsa(String url) {
        if (url != null && !url.isBlank() && !isPresetTsa(url)) {
            if (hasTsaUserInfo(url) && statusReporter != null) {
                statusReporter.showInfo("TSA", t("module.xml.tsaCredentialsNotSaved"));
            }
            AppSettings.getInstance().setCustomTsaUrl(url);
        }
    }

    /** Keep the complete endpoint for the active TSA request; redact only published copies. */
    private String publishedTsaUrl(String url) {
        if (url == null || AppSettings.getInstance().getSecretVisibilityProfile() == SecretVisibilityProfile.FULL_LAB) {
            return url;
        }
        return TsaUrlSanitizer.withoutUserInfo(url);
    }

    private void reloadTsaProfiles() {
        xmlSignTsaProfileCombo.getItems().setAll(AppSettings.getInstance().getTsaProfiles().stream()
                .map(AppSettings.TsaProfile::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
    }
}
