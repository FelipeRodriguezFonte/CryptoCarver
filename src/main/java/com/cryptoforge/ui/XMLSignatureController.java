package com.cryptoforge.ui;

import com.cryptoforge.crypto.XMLSignatureOperations;
import com.cryptoforge.crypto.TsaDiagnostics;
import com.cryptoforge.model.OperationResult;
import com.cryptoforge.model.AppSettings;
import com.cryptoforge.utils.OperationHistory;
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
    @FXML private ComboBox<String> xmlSignTsaUrlCombo;
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

    private byte[] lastTimestampToken;

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
        xmlSignLevelCombo.getItems().addAll("XAdES-BASELINE-B", "XAdES-BASELINE-T", "XAdES-BASELINE-LT", "XAdES-BASELINE-LTA");
        xmlSignLevelCombo.setValue("XAdES-BASELINE-B");
        xmlSignPackagingCombo.getItems().setAll("ENVELOPED", "ENVELOPING", "DETACHED");
        xmlSignPackagingCombo.setValue("ENVELOPED");
        xmlSignTsaUrlCombo.getItems().setAll(NO_TSA, DIGICERT_TSA, FREETSA_TSA);
        String customTsa = AppSettings.getInstance().getCustomTsaUrl();
        xmlSignTsaUrlCombo.setValue(customTsa.isBlank() ? NO_TSA : customTsa);
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
            if (pane.getText().contains(itemName)) {
                xmlAccordion.setExpandedPane(pane);
                break;
            }
        }
    }

    @FXML
    public void handleBrowseXMLSignInput() {
        File file = chooseFile("Select XML to Sign");
        if (file != null) {
            xmlSignInputPathField.setText(file.getAbsolutePath());
        }
    }

    private com.cryptoforge.model.TsaAuthCredentials getTsaCredentials() {
        if (xmlSignTsaAuthTypeCombo == null) return null;
        String typeStr = xmlSignTsaAuthTypeCombo.getValue();
        if (typeStr == null || "NONE".equals(typeStr)) return null;
        com.cryptoforge.model.TsaAuthCredentials.AuthType type =
            com.cryptoforge.model.TsaAuthCredentials.AuthType.valueOf(typeStr);
        String user = xmlSignTsaUserField != null ? xmlSignTsaUserField.getText() : "";
        String pass = xmlSignTsaPasswordField != null ? xmlSignTsaPasswordField.getText() : "";
        return new com.cryptoforge.model.TsaAuthCredentials(type, user, pass);
    }

    @FXML
    public void handleBrowseXMLKey() {
        File file = chooseFile("Select PKCS#12 KeyStore");
        if (file != null) {
            xmlSignKeyPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void handleLoadXMLKeys() {
        if (xmlSignSourcePkcs11Radio != null && xmlSignSourcePkcs11Radio.isSelected()) {
            try {
                com.cryptoforge.crypto.hsm.Pkcs11Session session = com.cryptoforge.crypto.hsm.Pkcs11SessionManager.getInstance().requireSession();
                if (session == null) {
                    statusReporter.showError("Token Error", "No PKCS#11 token connected. Please connect from the Keys tab first.");
                    return;
                }
                java.util.List<String> aliases = session.listPrivateKeysWithCertificate();
                xmlSignKeyAliasCombo.getItems().setAll(aliases);
                if (!aliases.isEmpty()) {
                    xmlSignKeyAliasCombo.getSelectionModel().selectFirst();
                }
                statusReporter.updateStatus("Loaded " + aliases.size() + " private key aliases from PKCS#11 token.");
            } catch (Exception e) {
                statusReporter.showError("PKCS#11 Error", "Error listing aliases: " + e.getMessage());
            }
            return;
        }

        try {
            String keyPath = xmlSignKeyPathField.getText();
            String password = xmlSignKeyPasswordField.getText();

            if (keyPath.isEmpty() || password.isEmpty()) {
                statusReporter.showError("Input Error", "Please provide KeyStore Path and Password");
                return;
            }

            java.util.List<String> aliases = XMLSignatureOperations.getKeyAliases(keyPath, password);
            xmlSignKeyAliasCombo.getItems().setAll(aliases);

            if (!aliases.isEmpty()) {
                xmlSignKeyAliasCombo.getSelectionModel().select(0);
                statusReporter.updateStatus("Keys loaded: " + aliases.size());
            } else {
                statusReporter.updateStatus("No keys found in keystore");
            }

        } catch (Exception e) {
            statusReporter.showError("Key Load Error", "Error loading keys: " + e.getMessage());
            LOG.error("Unable to load XAdES signing keys", e);
        }
    }

    @FXML
    public void handleTestTSA() {
        String url = getTsaUrl();
        if (url == null) {
            statusReporter.showError("TSA Test", "Select a TSA or enter a corporate TSA URL first");
            return;
        }
        if (!isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", "The TSA URL must start with http:// or https://");
            return;
        }
        saveCustomTsa(url);
        statusReporter.updateStatus("Testing TSA…");
        com.cryptoforge.model.TsaAuthCredentials auth = getTsaCredentials();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                TsaDiagnostics.TokenResult result = TsaDiagnostics.timestamp(url, "CryptoCarver TSA diagnostic".getBytes(java.nio.charset.StandardCharsets.UTF_8), "SHA-256", 15000, 20000, 1024*1024, auth);
                TsaDiagnostics.Report report = result.report();
                javafx.application.Platform.runLater(() -> statusReporter.showInfo("TSA Test Passed",
                        "URL: " + report.url() + "\nHTTP: " + report.httpStatus() + "\nLatency: " + report.latencyMs()
                                + " ms\nPolicy: " + report.policyOid() + "\nImprint: " + report.imprintAlgorithmOid()
                                + "\nToken time: " + report.generationTime() + "\nResponse: " + report.responseBytes() + " bytes"));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> statusReporter.showError("TSA Test Failed", e.getMessage()));
            }
        });
    }

    @FXML
    public void handleSaveTSA() {
        String url = getTsaUrl();
        if (url == null) {
            statusReporter.showError("Save TSA", "Enter a corporate TSA URL first");
            return;
        }
        if (!isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", "The TSA URL must start with http:// or https://");
            return;
        }
        saveCustomTsa(url);
        statusReporter.showInfo("TSA Saved", "The TSA URL will be preselected the next time you open XAdES signing.\n\n" + url);
    }

    @FXML
    public void handleLoadTSASavedProfile() {
        String name = xmlSignTsaProfileCombo.getValue();
        if (name == null || name.isBlank()) {
            statusReporter.showError("TSA Profile", "Select a saved TSA profile first");
            return;
        }
        AppSettings.getInstance().getTsaProfiles().stream()
                .filter(profile -> name.equals(profile.name()))
                .findFirst()
                .ifPresentOrElse(profile -> {
                    xmlSignTsaUrlCombo.getEditor().setText(profile.url());
                    xmlSignTsaUrlCombo.setValue(profile.url());
                    xmlSignTsaProfileNameField.setText(profile.name());
                    statusReporter.updateStatus("Loaded TSA profile: " + profile.name());
                }, () -> statusReporter.showError("TSA Profile", "The selected profile no longer exists"));
    }

    @FXML
    public void handleSaveTSASavedProfile() {
        String url = getTsaUrl();
        String name = xmlSignTsaProfileNameField.getText().trim();
        if (name.isEmpty()) {
            statusReporter.showError("TSA Profile", "Enter a profile name first");
            return;
        }
        if (url == null || !isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", "Enter an http:// or https:// TSA URL first");
            return;
        }
        AppSettings.getInstance().saveTsaProfile(name, url);
        saveCustomTsa(url);
        reloadTsaProfiles();
        xmlSignTsaProfileCombo.setValue(name);
        statusReporter.showInfo("TSA Profile Saved", name + "\n" + url + "\n\nOnly the endpoint is saved; no credentials are stored.");
    }

    @FXML
    public void handleDeleteTSASavedProfile() {
        String name = xmlSignTsaProfileCombo.getValue();
        if (name == null || name.isBlank()) {
            statusReporter.showError("TSA Profile", "Select a saved TSA profile first");
            return;
        }
        AppSettings.getInstance().removeTsaProfile(name);
        reloadTsaProfiles();
        xmlSignTsaProfileNameField.clear();
        statusReporter.updateStatus("Deleted TSA profile: " + name);
    }

    @FXML
    public void handleSignXML() {
        try {
            String inputPath = xmlSignInputPathField.getText();
            if (inputPath.isEmpty()) {
                statusReporter.showError("Input Error", "Please provide an Input File");
                return;
            }

            String xmlContent = Files.readString(new File(inputPath).toPath());
            String level = xmlSignLevelCombo.getValue();
            String packaging = xmlSignPackagingCombo.getValue();
            String tsaUrl = getTsaUrl();

            if (!"XAdES-BASELINE-B".equals(level) && tsaUrl == null) {
                statusReporter.showError("TSA Required", "Select a TSA or enter your corporate TSA URL for " + level);
                return;
            }
            if (tsaUrl != null && !isHttpUrl(tsaUrl)) {
                statusReporter.showError("TSA URL Error", "The TSA URL must start with http:// or https://");
                return;
            }
            saveCustomTsa(tsaUrl);

            String signedXml;
            Map<String, String> details = new HashMap<>();
            details.put("Action", "XAdES Sign");
            details.put("Level", level);
            details.put("Packaging", packaging);
            details.put("Input", inputPath);
            if (tsaUrl != null && !tsaUrl.isEmpty()) {
                details.put("TSA", tsaUrl);
            }

            if (xmlSignSourcePkcs11Radio != null && xmlSignSourcePkcs11Radio.isSelected()) {
                String alias = xmlSignKeyAliasCombo.getValue();
                if (alias == null || alias.isEmpty()) {
                    statusReporter.showError("Key Error", "Please select a key alias to sign with");
                    return;
                }
                signedXml = XMLSignatureOperations.signXAdESWithPkcs11(
                        xmlContent, alias, level, tsaUrl, packaging, getTsaCredentials());
                details.put("Source", "PKCS#11");
                details.put("Alias", alias);
            } else {
                String keyPath = xmlSignKeyPathField.getText();
                String password = xmlSignKeyPasswordField.getText();
                int keyIndex = xmlSignKeyAliasCombo.getSelectionModel().getSelectedIndex();

                if (keyPath.isEmpty() || password.isEmpty()) {
                    statusReporter.showError("Input Error", "Please provide KeyStore and Password");
                    return;
                }
                if (keyIndex < 0) {
                    handleLoadXMLKeys();
                    keyIndex = xmlSignKeyAliasCombo.getSelectionModel().getSelectedIndex();
                    if (keyIndex < 0) {
                        statusReporter.showError("Key Error", "Please select a key to sign with");
                        return;
                    }
                }
                signedXml = XMLSignatureOperations.signXAdES(
                        xmlContent, keyPath, password, keyIndex, level, tsaUrl, packaging, getTsaCredentials());
                details.put("Source", "Local KeyStore");
                details.put("KeyStore", keyPath);
            }

            xmlSignOutputArea.setText(signedXml);
            details.put("Output Size", signedXml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes");
            statusReporter.publish(OperationResult.forOperation("XAdES Sign")
                    .input(xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(signedXml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details)
                    .status("XML signed successfully")
                    .build());

        } catch (Exception e) {
            statusReporter.showError("Signing Error", "Error signing XML: " + e.getMessage());
            LOG.error("XAdES signing failed", e);
        }
    }

    @FXML
    public void handleVerifyXML() {
        try {
            String xmlContent = xmlVerifyInputArea.getText();
            if (xmlContent.isEmpty()) {
                statusReporter.showError("Input Error", "Please paste XML content to verify");
                return;
            }

            String trustStorePath = xmlVerifyTrustStorePathField.getText().trim();
            String trustStorePassword = xmlVerifyTrustStorePasswordField.getText();
            XMLSignatureOperations.VerificationResult result = XMLSignatureOperations.verifyXAdES(xmlContent, trustStorePath, trustStorePassword);

            String report = result.summary();
            xmlVerifyReportArea.setText(report);

            Map<String, String> details = new HashMap<>();
            details.put("Action", "XAdES Verify");
            details.put("Trust Policy", trustStorePath.isBlank() ? "Integrity only (no truststore)" : "Truststore configured");
            String indication = extractReportValue(report, "Indication:");
            if (indication != null) details.put("Indication", indication);
            String subIndication = extractReportValue(report, "SubIndication:");
            if (subIndication != null) details.put("SubIndication", subIndication);
            String status = "TOTAL_PASSED".equals(indication)
                    ? "XML verification: valid"
                    : "XML verification: " + (indication == null ? "completed" : indication);
            statusReporter.publish(OperationResult.forOperation("XAdES Verify")
                    .input(xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details).status(status).build());

            // Prompt to save detailed reports
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle("Save Diagnostic Reports");
            alert.setHeaderText("XAdES verification generated detailed XML reports.");
            alert.setContentText("Do you want to save the Simple, Detailed, and ETSI reports? WARNING: These reports may contain Personal Identifiable Information (PII) from certificates.");
            java.util.Optional<javafx.scene.control.ButtonType> opt = alert.showAndWait();
            if (opt.isPresent() && opt.get() == javafx.scene.control.ButtonType.OK) {
                javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
                dc.setTitle("Select folder to save reports");
                java.io.File dir = dc.showDialog(xmlVerifyReportArea.getScene().getWindow());
                if (dir != null) {
                    if (result.xmlSimpleReport() != null) Files.writeString(new File(dir, "SimpleReport.xml").toPath(), result.xmlSimpleReport());
                    if (result.xmlDetailedReport() != null) Files.writeString(new File(dir, "DetailedReport.xml").toPath(), result.xmlDetailedReport());
                    if (result.xmlEtsiReport() != null) Files.writeString(new File(dir, "ETSIReport.xml").toPath(), result.xmlEtsiReport());
                    statusReporter.updateStatus("Saved 3 reports to " + dir.getAbsolutePath());
                }
            }

        } catch (Exception e) {
            statusReporter.showError("Verification Error", "Error verifying XML: " + e.getMessage());
            LOG.error("XAdES verification failed", e);
        }
    }

    @FXML
    public void handleBrowseXMLInspectorInput() {
        File file = chooseFile("Select Signed XML to Inspect");
        if (file == null) return;
        try {
            xmlInspectInputArea.setText(Files.readString(file.toPath()));
            statusReporter.updateStatus("Loaded XML for inspection: " + file.getName());
        } catch (Exception e) {
            statusReporter.showError("XML Inspector", "Unable to read XML: " + e.getMessage());
        }
    }

    @FXML
    public void handleInspectSignedXML() {
        try {
            String xml = xmlInspectInputArea.getText();
            String report = XMLSignatureOperations.inspectSignedXml(xml);
            xmlInspectReportArea.setText(report);
            Map<String, String> details = new HashMap<>();
            details.put("Action", "Inspect Signed XML");
            details.put("Input bytes", String.valueOf(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
            String signatures = extractReportValue(report, "XMLDSig signatures:");
            if (signatures != null) details.put("Signatures", signatures);
            statusReporter.publish(OperationResult.forOperation("Inspect Signed XML")
                    .input(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details).status("Signed XML inspected locally").build());
        } catch (Exception e) {
            statusReporter.showError("XML Inspector", "Unable to inspect XML: " + e.getMessage());
            LOG.error("Signed XML inspection failed", e);
        }
    }

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
            statusReporter.showError("RFC 3161 Timestamp", "Select a file and enter a TSA URL first");
            return;
        }
        if (!isHttpUrl(url)) {
            statusReporter.showError("TSA URL Error", "The TSA URL must start with http:// or https://");
            return;
        }
        try {
            byte[] data = Files.readAllBytes(new File(path).toPath());
            saveCustomTsa(url);
            statusReporter.updateStatus("Requesting RFC 3161 timestamp…");
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
                                + "\n" + hash + ": " + result.dataSha256() + "\nTSA: " + report.url() + "\nHTTP: " + report.httpStatus()
                                + "\nLatency: " + report.latencyMs() + " ms\nPolicy: " + report.policyOid()
                                + "\nToken time: " + report.generationTime() + "\nToken bytes: " + report.responseBytes();
                        if (tokenInfo != null) {
                            text += "\nTSA certificate subject: " + tokenInfo.signerSubject()
                                    + "\nTSA certificate issuer: " + tokenInfo.signerIssuer()
                                    + "\nTSA certificate SHA-256: " + tokenInfo.signerSha256();
                        }
                        xmlTimestampReportArea.setText(text);
                        Map<String, String> details = new HashMap<>();
                        details.put("File", path); details.put("Hash", hash); details.put("Imprint", result.dataSha256()); details.put("TSA", url);
                        details.put("Token bytes", String.valueOf(result.token().length));
                        if (tokenInfo != null) details.put("TSA certificate SHA-256", tokenInfo.signerSha256());
                        statusReporter.publish(OperationResult.forOperation("RFC 3161 Timestamp")
                                .input(data).output(result.token()).details(details).status("Timestamp token received").build());
                    });
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> statusReporter.showError("RFC 3161 Timestamp", e.getMessage()));
                }
            });
        } catch (Exception e) { statusReporter.showError("RFC 3161 Timestamp", "Unable to read file: " + e.getMessage()); }
    }

    @FXML
    public void handleSaveTimestampToken() {
        if (lastTimestampToken == null) { statusReporter.showError("Save Timestamp", "Request a timestamp token first"); return; }
        FileChooser chooser = new FileChooser(); chooser.setTitle("Save RFC 3161 Timestamp Token"); chooser.setInitialFileName("timestamp.tsr");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Timestamp response", "*.tsr", "*.tst"));
        File file = chooser.showSaveDialog(null); if (file == null) return;
        try { Files.write(file.toPath(), lastTimestampToken); statusReporter.updateStatus("Timestamp token saved: " + file.getName()); }
        catch (Exception e) { statusReporter.showError("Save Timestamp", e.getMessage()); }
    }

    @FXML
    public void handleBrowseTimestampToken() {
        File file = chooseFile("Select RFC 3161 Timestamp Token");
        if (file != null) xmlTimestampTokenField.setText(file.getAbsolutePath());
    }

    @FXML
    public void handleInspectTimestampToken() {
        String tokenPath = xmlTimestampTokenField.getText().trim();
        if (tokenPath.isEmpty()) { statusReporter.showError("Timestamp Token", "Select a .tsr or .tst token first"); return; }
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
        } catch (Exception e) { statusReporter.showError("Timestamp Token", "Unable to inspect token: " + e.getMessage()); }
    }

    @FXML
    public void handleValidateTimestampToken() {
        String tokenPath = xmlTimestampTokenField.getText().trim();
        if (tokenPath.isEmpty()) { statusReporter.showError("Timestamp Token", "Select a .tsr or .tst token first"); return; }
        String trustStorePath = xmlTimestampTrustStoreField != null ? xmlTimestampTrustStoreField.getText().trim() : "";
        String trustStorePassword = xmlTimestampTrustStorePasswordField != null ? xmlTimestampTrustStorePasswordField.getText() : "";
        String dataPath = xmlTimestampFileField.getText().trim();
        byte[] data = null;
        try {
            if (!dataPath.isEmpty()) data = Files.readAllBytes(new File(dataPath).toPath());
            byte[] token = Files.readAllBytes(new File(tokenPath).toPath());
            String report = TsaDiagnostics.validateToken(token, data, trustStorePath.isEmpty() ? null : trustStorePath, trustStorePassword);
            xmlTimestampReportArea.setText(report);
            statusReporter.updateStatus("Timestamp token validated.");
        } catch (Exception e) {
            statusReporter.showError("Timestamp Token", "Validation failed: " + e.getMessage());
        }
    }

    @FXML
    public void handleBrowseTimestampTrustStore() {
        File file = chooseFile("Select TrustStore (PKCS#12 or JKS)");
        if (file != null) {
            if (xmlTimestampTrustStoreField != null) xmlTimestampTrustStoreField.setText(file.getAbsolutePath());
        }
    }

    private File chooseFile(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        return fileChooser.showOpenDialog(null); // Should use stage if possible
    }

    @FXML
    public void handleBrowseXMLTrustStore() {
        File file = chooseFile("Select TrustStore (PKCS#12 or JKS)");
        if (file != null) {
            xmlVerifyTrustStorePathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void handleLoadXMLTrustStoreProfile() {
        String name = xmlVerifyTrustStoreProfileCombo.getValue();
        if (name == null || name.isBlank()) return;
        AppSettings.getInstance().getTrustStoreProfiles().stream().filter(profile -> name.equals(profile.name())).findFirst()
                .ifPresent(profile -> {
                    xmlVerifyTrustStorePathField.setText(profile.path());
                    xmlVerifyTrustStorePasswordField.clear();
                    statusReporter.updateStatus("TrustStore profile loaded; enter password to verify");
                });
    }

    @FXML
    public void handleSaveSignedXML() {
        if (xmlSignOutputArea.getText().isBlank()) {
            statusReporter.showError("Save Error", "Sign an XML document before saving it.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Signed XML");
        chooser.setInitialFileName("signed.xml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File output = chooser.showSaveDialog(null);
        if (output == null) return;
        try {
            Files.writeString(output.toPath(), xmlSignOutputArea.getText());
            Map<String, String> details = new HashMap<>();
            details.put("Action", "Save signed XML");
            details.put("Output", output.getAbsolutePath());
            statusReporter.publish(OperationResult.forOperation("Save XAdES XML")
                    .output(xmlSignOutputArea.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .details(details).status("Signed XML saved: " + output.getName()).build());
        } catch (Exception e) {
            statusReporter.showError("Save Error", "Unable to save signed XML: " + e.getMessage());
        }
    }

    private String getTsaUrl() {
        String selected = xmlSignTsaUrlCombo.getEditor().getText().trim();
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

    private void saveCustomTsa(String url) {
        if (url != null && !url.isBlank() && !isPresetTsa(url)) {
            AppSettings.getInstance().setCustomTsaUrl(url);
        }
    }

    private void reloadTsaProfiles() {
        xmlSignTsaProfileCombo.getItems().setAll(AppSettings.getInstance().getTsaProfiles().stream()
                .map(AppSettings.TsaProfile::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
    }

    private String extractReportValue(String report, String label) {
        for (String line : report.split("\\R")) {
            if (line.startsWith(label)) return line.substring(label.length()).trim();
        }
        return null;
    }
}
