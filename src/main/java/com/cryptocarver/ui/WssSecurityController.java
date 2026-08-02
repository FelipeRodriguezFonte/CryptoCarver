package com.cryptocarver.ui;

import com.cryptocarver.crypto.WssSecurityOperations;
import com.cryptocarver.crypto.WssUsernameTokenOperations;
import com.cryptocarver.crypto.WssEncryptionOperations;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Arrays;

public class WssSecurityController {

    private static final Logger LOG = LoggerFactory.getLogger(WssSecurityController.class);

    private StatusReporter statusReporter;

    @FXML private VBox wssSecurityContainer;
    @FXML private Accordion wssAccordion;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    // Sign UI
    @FXML private TextArea wssSignInputArea;
    @FXML private ComboBox<String> wssSignAlgorithmCombo;
    @FXML private CheckBox wssIncludeTimestampCheck;
    @FXML private Spinner<Integer> wssTimestampValiditySpinner;
    @FXML private CheckBox wssSignTimestampCheck;
    @FXML private TextField wssSignKeyPathField;
    @FXML private PasswordField wssSignKeyPasswordField;
    @FXML private ComboBox<String> wssSignKeyAliasCombo;
    @FXML private PasswordField wssSignPrivateKeyPasswordField;
    @FXML private TextArea wssSignOutputArea;

    // Verify UI
    @FXML private TextArea wssVerifyInputArea;
    @FXML private TextField wssVerifyTrustStorePathField;
    @FXML private TextArea wssVerifyReportArea;

    // UsernameToken UI
    @FXML private TextArea wssUsernameCreateInputArea;
    @FXML private TextField wssUsernameCreateNameField;
    @FXML private PasswordField wssUsernameCreatePasswordField;
    @FXML private ComboBox<String> wssUsernamePasswordTypeCombo;
    @FXML private Label wssUsernameWarningLabel;
    @FXML private TextArea wssUsernameCreateOutputArea;
    @FXML private TextArea wssUsernameVerifyInputArea;
    @FXML private TextField wssUsernameExpectedNameField;
    @FXML private PasswordField wssUsernameExpectedPasswordField;
    @FXML private Spinner<Integer> wssUsernameMaxAgeSpinner;
    @FXML private TextArea wssUsernameVerifyReportArea;

    // XML Encryption UI
    @FXML private TextArea wssEncryptInputArea;
    @FXML private TextField wssEncryptCertificatePathField;
    @FXML private ComboBox<String> wssEncryptDataAlgorithmCombo;
    @FXML private ComboBox<String> wssEncryptKeyTransportCombo;
    @FXML private Label wssEncryptAlgorithmWarningLabel;
    @FXML private TextArea wssEncryptOutputArea;
    @FXML private TextArea wssEncryptReportArea;
    @FXML private TextArea wssDecryptInputArea;
    @FXML private TextField wssDecryptKeyStorePathField;
    @FXML private PasswordField wssDecryptKeyStorePasswordField;
    @FXML private PasswordField wssDecryptPrivateKeyPasswordField;
    @FXML private TextArea wssDecryptOutputArea;
    @FXML private TextArea wssDecryptReportArea;

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(wssAccordion, ModuleTextCatalog.wssSecurity());
        for (WssSecurityOperations.WssSignatureAlgorithm algorithm : WssSecurityOperations.WssSignatureAlgorithm.values()) {
            wssSignAlgorithmCombo.getItems().add(algorithm.displayName());
        }
        wssSignAlgorithmCombo.getSelectionModel().selectFirst();
        wssTimestampValiditySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1440, 5));
        wssTimestampValiditySpinner.setEditable(true);
        wssTimestampValiditySpinner.disableProperty().bind(wssIncludeTimestampCheck.selectedProperty().not());
        wssSignTimestampCheck.disableProperty().bind(wssIncludeTimestampCheck.selectedProperty().not());
        for (WssUsernameTokenOperations.PasswordType type : WssUsernameTokenOperations.PasswordType.values()) {
            wssUsernamePasswordTypeCombo.getItems().add(type.displayName());
        }
        wssUsernamePasswordTypeCombo.getSelectionModel().select(
                WssUsernameTokenOperations.PasswordType.PASSWORD_DIGEST.displayName());
        wssUsernamePasswordTypeCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                updateUsernameTokenWarning(newValue));
        updateUsernameTokenWarning(wssUsernamePasswordTypeCombo.getValue());
        wssUsernameMaxAgeSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 86_400, 300));
        wssUsernameMaxAgeSpinner.setEditable(true);
        for (WssEncryptionOperations.DataEncryptionAlgorithm algorithm
                : WssEncryptionOperations.DataEncryptionAlgorithm.values()) {
            wssEncryptDataAlgorithmCombo.getItems().add(algorithm.displayName());
        }
        wssEncryptDataAlgorithmCombo.getSelectionModel().select(
                WssEncryptionOperations.DataEncryptionAlgorithm.AES_256_GCM.displayName());
        for (WssEncryptionOperations.KeyTransportAlgorithm algorithm
                : WssEncryptionOperations.KeyTransportAlgorithm.values()) {
            wssEncryptKeyTransportCombo.getItems().add(algorithm.displayName());
        }
        wssEncryptKeyTransportCombo.getSelectionModel().select(
                WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA256.displayName());
        wssEncryptDataAlgorithmCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                updateEncryptionWarning(newValue));
        updateEncryptionWarning(wssEncryptDataAlgorithmCombo.getValue());
    }

    public void initModule(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    public void expandAccordionPane(String paneName) {
        if (wssAccordion == null) return;
        for (TitledPane pane : wssAccordion.getPanes()) {
            if (ModulePaneMatcher.matches(pane, paneName, ModuleTextCatalog.wssSecurity())) {
                wssAccordion.setExpandedPane(pane);
                break;
            }
        }
    }

    @FXML
    public void handleReset() {
        ModuleResetSupport.clearInputsAndKeepFocus(wssAccordion);
        if (wssIncludeTimestampCheck != null) wssIncludeTimestampCheck.setSelected(false);
        if (wssSignTimestampCheck != null) wssSignTimestampCheck.setSelected(false);
        if (statusReporter != null) statusReporter.updateStatus(t("module.common.resetStatus"));
    }

    @FXML
    private void handleBrowseWssSignInput() {
        File f = chooseFile("Select SOAP XML to Sign");
        if (f != null) {
            try {
                wssSignInputArea.setText(Files.readString(f.toPath()));
            } catch (Exception e) {
                showError("Failed to read file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleBrowseWssKey() {
        File f = chooseFile("Select KeyStore (JKS/PKCS12)");
        if (f != null) {
            wssSignKeyPathField.setText(f.getAbsolutePath());
        }
    }

    @FXML
    private void handleLoadWssKeys() {
        String path = wssSignKeyPathField.getText();
        String pass = wssSignKeyPasswordField.getText();
        if (path == null || path.trim().isEmpty()) {
            showError("KeyStore path is empty.");
            return;
        }

        try {
            KeyStore ks = loadKeyStore(path, pass.toCharArray());
            wssSignKeyAliasCombo.getItems().clear();
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (ks.isKeyEntry(alias)) {
                    wssSignKeyAliasCombo.getItems().add(alias);
                }
            }
            if (!wssSignKeyAliasCombo.getItems().isEmpty()) {
                wssSignKeyAliasCombo.getSelectionModel().selectFirst();
            } else {
                showError("No private keys found in the KeyStore.");
            }
        } catch (Exception e) {
            LOG.error("Failed to load WSS KeyStore", e);
            showError("Failed to load KeyStore: " + e.getMessage());
        }
    }

    @FXML
    private void handleSignWSS() {
        String xml = wssSignInputArea.getText();
        String path = wssSignKeyPathField.getText();
        String ksPass = wssSignKeyPasswordField.getText();
        String alias = wssSignKeyAliasCombo.getValue();
        String keyPassStr = wssSignPrivateKeyPasswordField.getText();

        if (xml == null || xml.trim().isEmpty()) {
            showError(t("module.wss.error.soapToSign"));
            return;
        }
        if (path == null || path.trim().isEmpty() || alias == null) {
            showError(t("module.wss.error.keyStoreAlias"));
            return;
        }

        char[] keyPass = (keyPassStr == null || keyPassStr.isEmpty()) ? ksPass.toCharArray() : keyPassStr.toCharArray();

        try {
            KeyStore ks = loadKeyStore(path, ksPass.toCharArray());
            WssSecurityOperations.WssSignatureAlgorithm algorithm =
                    WssSecurityOperations.WssSignatureAlgorithm.fromDisplayName(wssSignAlgorithmCombo.getValue());
            WssSecurityOperations.WssTimestampOptions timestampOptions = wssIncludeTimestampCheck.isSelected()
                    ? new WssSecurityOperations.WssTimestampOptions(true,
                            wssTimestampValiditySpinner.getValue(), wssSignTimestampCheck.isSelected())
                    : WssSecurityOperations.WssTimestampOptions.disabled();
            String signedXml = WssSecurityOperations.signSoapBody(
                    xml, ks, alias, keyPass, algorithm, timestampOptions);
            wssSignOutputArea.setText(signedXml);
            if (statusReporter != null) {
                statusReporter.updateStatus("WSS Signature applied successfully.");
            }
        } catch (Exception e) {
            LOG.error("WSS Sign Error", e);
            showError("Signature failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveSignedWss() {
        String xml = wssSignOutputArea.getText();
        if (xml == null || xml.isEmpty()) {
            return;
        }
        File f = saveFile("Save Signed SOAP XML");
        if (f != null) {
            try {
                Files.writeString(f.toPath(), xml);
                if (statusReporter != null) {
                    statusReporter.updateStatus("Signed WSS XML saved to " + f.getName());
                }
            } catch (Exception e) {
                showError("Failed to save file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleBrowseWssVerifyInput() {
        File f = chooseFile("Select Signed SOAP XML");
        if (f != null) {
            try {
                wssVerifyInputArea.setText(Files.readString(f.toPath()));
            } catch (Exception e) {
                showError("Failed to read file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleBrowseWssTrustStore() {
        File f = chooseFile("Select Trusted Certificate (PEM/DER)");
        if (f != null) {
            wssVerifyTrustStorePathField.setText(f.getAbsolutePath());
        }
    }

    @FXML
    private void handleVerifyWSS() {
        String xml = wssVerifyInputArea.getText();
        String certPath = wssVerifyTrustStorePathField.getText();

        if (xml == null || xml.trim().isEmpty()) {
            showError("Please provide the signed SOAP XML to verify.");
            return;
        }

        try {
            X509Certificate cert = null;
            if (certPath != null && !certPath.trim().isEmpty()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                try (FileInputStream fis = new FileInputStream(certPath)) {
                    cert = (X509Certificate) cf.generateCertificate(fis);
                }
            }

            WssSecurityOperations.WssVerificationResult res = WssSecurityOperations.verifySoapSignature(xml, cert);

            StringBuilder sb = new StringBuilder();
            sb.append("STATUS: ").append(res.getStatus().name()).append("\n");
            sb.append("Message: ").append(res.getMessage()).append("\n\n");
            if (res.getTechnicalDetails() != null && !res.getTechnicalDetails().isEmpty()) {
                sb.append("Technical Details:\n").append(res.getTechnicalDetails());
            }

            wssVerifyReportArea.setText(sb.toString());

            if (res.getStatus() == WssSecurityOperations.WssVerificationResult.Status.VALID) {
                wssVerifyReportArea.setStyle("-fx-control-inner-background: #e8f5e9; -fx-font-family: 'Monospaced'; -fx-font-size: 10px;");
            } else if (res.getStatus() == WssSecurityOperations.WssVerificationResult.Status.INVALID) {
                wssVerifyReportArea.setStyle("-fx-control-inner-background: #ffebee; -fx-font-family: 'Monospaced'; -fx-font-size: 10px;");
            } else {
                wssVerifyReportArea.setStyle("-fx-control-inner-background: #fff3e0; -fx-font-family: 'Monospaced'; -fx-font-size: 10px;");
            }

            if (statusReporter != null) {
                statusReporter.updateStatus("WSS Verification complete.");
            }

        } catch (Exception e) {
            LOG.error("WSS Verify Error", e);
            showError("Verification failed: " + e.getMessage());
            wssVerifyReportArea.setText(t("module.wss.errorReport", e.getMessage()));
            wssVerifyReportArea.setStyle("-fx-control-inner-background: #fff3e0; -fx-font-family: 'Monospaced'; -fx-font-size: 10px;");
        }
    }

    @FXML
    private void handleBrowseUsernameCreateInput() {
        loadXmlInto("Select SOAP XML", wssUsernameCreateInputArea);
    }

    @FXML
    private void handleBrowseUsernameVerifyInput() {
        loadXmlInto("Select SOAP XML with UsernameToken", wssUsernameVerifyInputArea);
    }

    @FXML
    private void handleAddUsernameToken() {
        char[] password = wssUsernameCreatePasswordField.getText().toCharArray();
        try {
            WssUsernameTokenOperations.PasswordType type =
                    WssUsernameTokenOperations.PasswordType.fromDisplayName(
                            wssUsernamePasswordTypeCombo.getValue());
            String secured = WssUsernameTokenOperations.addUsernameToken(
                    wssUsernameCreateInputArea.getText(), wssUsernameCreateNameField.getText(), password, type);
            wssUsernameCreateOutputArea.setText(secured);
            if (statusReporter != null) statusReporter.updateStatus("WSS UsernameToken added successfully.");
        } catch (Exception e) {
            LOG.error("WSS UsernameToken creation error", e);
            showError("UsernameToken creation failed: " + e.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @FXML
    private void handleSaveUsernameTokenXml() {
        String xml = wssUsernameCreateOutputArea.getText();
        if (xml == null || xml.isBlank()) return;
        File file = saveFile("Save SOAP XML with UsernameToken");
        if (file != null) {
            try {
                Files.writeString(file.toPath(), xml);
                if (statusReporter != null) statusReporter.updateStatus("UsernameToken SOAP XML saved.");
            } catch (Exception e) {
                showError("Failed to save file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleVerifyUsernameToken() {
        char[] password = wssUsernameExpectedPasswordField.getText().toCharArray();
        try {
            WssUsernameTokenOperations.VerificationResult result =
                    WssUsernameTokenOperations.verifyUsernameToken(
                            wssUsernameVerifyInputArea.getText(), wssUsernameExpectedNameField.getText(),
                            password, wssUsernameMaxAgeSpinner.getValue());
            String report = "STATUS: " + result.status() + "\nMessage: " + result.message() + "\n\n"
                    + (result.technicalDetails() == null ? "" : result.technicalDetails());
            wssUsernameVerifyReportArea.setText(report);
            String background = switch (result.status()) {
                case VALID -> "#e8f5e9";
                case INVALID -> "#ffebee";
                case ERROR -> "#fff3e0";
            };
            wssUsernameVerifyReportArea.setStyle("-fx-control-inner-background: " + background
                    + "; -fx-font-family: 'Monospaced'; -fx-font-size: 10px;");
            if (statusReporter != null) statusReporter.updateStatus("WSS UsernameToken verification complete.");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void updateUsernameTokenWarning(String passwordType) {
        boolean text = WssUsernameTokenOperations.PasswordType.PASSWORD_TEXT.displayName().equals(passwordType);
        wssUsernameWarningLabel.setText(text
                ? "PasswordText exposes the password in the SOAP message. Use only over authenticated TLS."
                : "PasswordDigest uses the UsernameToken Profile SHA-1 digest formula with a fresh nonce and Created value.");
        wssUsernameWarningLabel.setStyle(text
                ? "-fx-text-fill: #b71c1c; -fx-font-weight: bold;"
                : "-fx-text-fill: #52657a;");
    }

    @FXML
    private void handleBrowseWssEncryptInput() {
        loadXmlInto("Select SOAP XML to Encrypt", wssEncryptInputArea);
    }

    @FXML
    private void handleBrowseWssEncryptCertificate() {
        File file = chooseFile("Select Recipient X.509 Certificate");
        if (file != null) wssEncryptCertificatePathField.setText(file.getAbsolutePath());
    }

    @FXML
    private void handleEncryptSoapBody() {
        try {
            X509Certificate certificate;
            try (FileInputStream input = new FileInputStream(wssEncryptCertificatePathField.getText())) {
                certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            }
            WssEncryptionOperations.OperationResult result = WssEncryptionOperations.encryptSoapBody(
                    wssEncryptInputArea.getText(), certificate,
                    WssEncryptionOperations.DataEncryptionAlgorithm.fromDisplayName(
                            wssEncryptDataAlgorithmCombo.getValue()),
                    WssEncryptionOperations.KeyTransportAlgorithm.fromDisplayName(
                            wssEncryptKeyTransportCombo.getValue()));
            renderEncryptionResult(result, wssEncryptOutputArea, wssEncryptReportArea);
            if (result.status() == WssEncryptionOperations.OperationResult.Status.SUCCESS && statusReporter != null) {
                statusReporter.updateStatus("WSS SOAP Body encrypted successfully.");
            }
        } catch (Exception e) {
            LOG.error("WSS encryption error", e);
            wssEncryptReportArea.setText(t("module.wss.errorReport", e.getMessage()));
        }
    }

    @FXML
    private void handleSaveEncryptedSoap() {
        saveXmlFrom(wssEncryptOutputArea, "Save Encrypted SOAP XML");
    }

    @FXML
    private void handleBrowseWssDecryptInput() {
        loadXmlInto("Select Encrypted SOAP XML", wssDecryptInputArea);
    }

    @FXML
    private void handleBrowseWssDecryptKeyStore() {
        File file = chooseFile("Select Decryption KeyStore (JKS/PKCS12)");
        if (file != null) wssDecryptKeyStorePathField.setText(file.getAbsolutePath());
    }

    @FXML
    private void handleDecryptSoapBody() {
        char[] storePassword = wssDecryptKeyStorePasswordField.getText().toCharArray();
        String keyPasswordText = wssDecryptPrivateKeyPasswordField.getText();
        char[] keyPassword = keyPasswordText == null || keyPasswordText.isEmpty()
                ? Arrays.copyOf(storePassword, storePassword.length) : keyPasswordText.toCharArray();
        try {
            KeyStore keyStore = loadKeyStore(wssDecryptKeyStorePathField.getText(), storePassword);
            WssEncryptionOperations.OperationResult result = WssEncryptionOperations.decryptSoapBody(
                    wssDecryptInputArea.getText(), keyStore, keyPassword);
            renderEncryptionResult(result, wssDecryptOutputArea, wssDecryptReportArea);
            if (result.status() == WssEncryptionOperations.OperationResult.Status.SUCCESS && statusReporter != null) {
                statusReporter.updateStatus("WSS SOAP Body decrypted successfully.");
            }
        } catch (Exception e) {
            LOG.error("WSS decryption error", e);
            wssDecryptReportArea.setText(t("module.wss.errorReport", e.getMessage()));
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }

    @FXML
    private void handleSaveDecryptedSoap() {
        saveXmlFrom(wssDecryptOutputArea, "Save Decrypted SOAP XML");
    }

    private void updateEncryptionWarning(String algorithmName) {
        WssEncryptionOperations.DataEncryptionAlgorithm algorithm =
                WssEncryptionOperations.DataEncryptionAlgorithm.fromDisplayName(algorithmName);
        wssEncryptAlgorithmWarningLabel.setText(algorithm.authenticated()
                ? "AES-GCM provides authenticated encryption and is recommended."
                : "AES-CBC does not authenticate the ciphertext. Use only for interoperability testing.");
        wssEncryptAlgorithmWarningLabel.setStyle(algorithm.authenticated()
                ? "-fx-text-fill: #52657a;" : "-fx-text-fill: #b71c1c; -fx-font-weight: bold;");
    }

    private void renderEncryptionResult(WssEncryptionOperations.OperationResult result,
                                        TextArea output, TextArea report) {
        output.setText(result.xml() == null ? "" : result.xml());
        report.setText("STATUS: " + result.status() + "\nMessage: " + result.message() + "\n\n"
                + (result.technicalDetails() == null ? "" : result.technicalDetails()));
    }

    private void saveXmlFrom(TextArea source, String title) {
        String xml = source.getText();
        if (xml == null || xml.isBlank()) return;
        File file = saveFile(title);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), xml);
            } catch (Exception e) {
                showError("Failed to save file: " + e.getMessage());
            }
        }
    }

    private void loadXmlInto(String title, TextArea target) {
        File file = chooseFile(title);
        if (file != null) {
            try {
                target.setText(Files.readString(file.toPath()));
            } catch (Exception e) {
                showError("Failed to read file: " + e.getMessage());
            }
        }
    }

    private KeyStore loadKeyStore(String path, char[] password) throws Exception {
        String type = path.toLowerCase().endsWith(".p12") || path.toLowerCase().endsWith(".pfx") ? "PKCS12" : "JKS";
        KeyStore ks = KeyStore.getInstance(type);
        try (FileInputStream fis = new FileInputStream(path)) {
            ks.load(fis, password);
        }
        return ks;
    }

    private File chooseFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        if (wssSecurityContainer != null && wssSecurityContainer.getScene() != null) {
            return chooser.showOpenDialog(wssSecurityContainer.getScene().getWindow());
        }
        return null;
    }

    private File saveFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        if (wssSecurityContainer != null && wssSecurityContainer.getScene() != null) {
            return chooser.showSaveDialog(wssSecurityContainer.getScene().getWindow());
        }
        return null;
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText(t("module.wss.errorTitle"));
        alert.showAndWait();
    }
}
