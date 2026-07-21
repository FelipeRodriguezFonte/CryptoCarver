package com.cryptocarver.ui;

import com.cryptocarver.crypto.CMSOperations;
import com.cryptocarver.util.DataConverter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * Controller for CMS/PKCS#7 operations
 */
public class CMSController {

    private MainController mainController;

    // Generate Section
    @FXML
    private TextArea generateDataArea;
    @FXML
    private TextArea generateCertArea;
    @FXML
    private TextArea generatePrivateKeyArea;
    @FXML
    private ComboBox<String> pkcs7TypeCombo;
    @FXML
    private TextArea associatedDataArea;
    @FXML
    private TextArea generateResultArea;

    // Verify Section
    @FXML
    private TextArea verifyPkcs7Area;
    @FXML
    private TextArea verifyCertArea;
    @FXML
    private TextArea verifyPrivateKeyArea;
    @FXML
    private TextArea verifyResultArea;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Initialize controller with FXML fields from MainController
     */
    public void initialize(MainController mainController,
            TextArea generateDataArea,
            TextArea generateCertArea,
            TextArea generatePrivateKeyArea,
            ComboBox<String> pkcs7TypeCombo,
            TextArea associatedDataArea,
            TextArea generateResultArea,
            TextArea verifyPkcs7Area,
            TextArea verifyCertArea,
            TextArea verifyPrivateKeyArea,
            TextArea verifyResultArea) {
        this.mainController = mainController;
        this.generateDataArea = generateDataArea;
        this.generateCertArea = generateCertArea;
        this.generatePrivateKeyArea = generatePrivateKeyArea;
        this.pkcs7TypeCombo = pkcs7TypeCombo;
        this.associatedDataArea = associatedDataArea;
        this.generateResultArea = generateResultArea;
        this.verifyPkcs7Area = verifyPkcs7Area;
        this.verifyCertArea = verifyCertArea;
        this.verifyPrivateKeyArea = verifyPrivateKeyArea;
        this.verifyResultArea = verifyResultArea;

        // Initialize combo
        if (pkcs7TypeCombo != null) {
            pkcs7TypeCombo.getItems().clear();
            pkcs7TypeCombo.getItems().addAll(
                    "Signed Data (Encapsulated)",
                    "Signed Data (Detached)",
                    "Enveloped Data");
            pkcs7TypeCombo.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void initialize() {
        // JavaFX auto-initialization (not used, fields injected manually)
    }

    /**
     * Generate PKCS#7
     */
    @FXML
    public void handleGeneratePKCS7() {
        try {
            String data = generateDataArea.getText().trim();
            String certData = generateCertArea.getText().trim();
            String keyData = generatePrivateKeyArea.getText().trim();
            String type = pkcs7TypeCombo.getSelectionModel().getSelectedItem();
            String assocData = associatedDataArea.getText().trim();

            if (data.isEmpty()) {
                generateResultArea.setText("Error: Data is required");
                return;
            }

            if (certData.isEmpty()) {
                generateResultArea.setText("Error: Certificate is required");
                return;
            }

            // Parse certificate
            X509Certificate cert = CMSOperations.parseCertificate(certData);

            byte[] dataBytes = data.getBytes("UTF-8");
            byte[] pkcs7;
            boolean isDetached = type.contains("Detached");
            Map<String, String> assocMap = null;
            List<String> attrWarnings = new ArrayList<>();

            if (type.startsWith("Signed Data")) {
                // Signed Data requires private key
                if (keyData.isEmpty()) {
                    generateResultArea.setText("Error: Private Key is required for Signed Data");
                    return;
                }

                PrivateKey privateKey = parsePrivateKey(keyData);

                // Parse associated data
                if (!assocData.isEmpty()) {
                    assocMap = parseAssociatedData(assocData);
                    if (assocMap.isEmpty() && !assocData.trim().isEmpty()) {
                        generateResultArea.setText("Error: Associated Data format is invalid.\n\n" +
                                "Expected format (one per line):\n" +
                                "OID=value\n\n" +
                                "IMPORTANT: OIDs must be NUMERIC only!\n\n" +
                                "Examples:\n" +
                                "1.2.3.4.5.6=MyValue\n" +
                                "1.3.6.1.4.1.99999.1=CompanyName\n" +
                                "1.2.840.113549.1.9.2=Document ID 12345\n\n" +
                                "AVOID these (auto-added by CMS):\n" +
                                "✗ 1.2.840.113549.1.9.3 (contentType)\n" +
                                "✗ 1.2.840.113549.1.9.4 (messageDigest)\n" +
                                "✗ 1.2.840.113549.1.9.5 (signingTime)\n\n" +
                                "INVALID (OIDs cannot have letters):\n" +
                                "✗ custom.test.nombre=value\n" +
                                "✗ my.company.attr=value\n\n" +
                                "Your input did not contain any valid 'OID=value' lines.");
                        return;
                    }
                }

                // Generate SignedData with warnings
                if (assocMap != null && !assocMap.isEmpty()) {
                    CMSOperations.SignedDataResult signedResult = CMSOperations
                            .generateSignedDataWithWarnings(dataBytes, cert, privateKey, assocMap, isDetached);
                    pkcs7 = signedResult.pkcs7;
                    attrWarnings = signedResult.attributeWarnings;
                } else {
                    pkcs7 = CMSOperations.generateSignedData(dataBytes, cert, privateKey, assocMap, isDetached);
                }

            } else { // Enveloped Data
                pkcs7 = CMSOperations.generateEnvelopedData(dataBytes, cert);
            }

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("PKCS#7 GENERATED\n");
            result.append("========================================\n\n");
            result.append("Type: ").append(type).append("\n");
            result.append("Data size: ").append(dataBytes.length).append(" bytes\n");

            if (isDetached) {
                result.append("\nNOTE: This is a DETACHED signature.\n");
                result.append("The original data is NOT included in the PKCS#7.\n");
                result.append("You must provide the original data separately when verifying.\n\n");
            }

            // Show associated data processing results
            if (!attrWarnings.isEmpty()) {
                result.append("\n📋 Associated Data Processing:\n");
                for (String warning : attrWarnings) {
                    result.append("  ").append(warning).append("\n");
                }
                result.append("\n");
            }

            // Show associated data if present
            if (assocMap != null && !assocMap.isEmpty()) {
                result.append("\nAssociated Data Included (Signed Attributes):\n");
                for (Map.Entry<String, String> entry : assocMap.entrySet()) {
                    result.append("  • ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
                }
                result.append("\n");
            }

            result.append("PKCS#7 size: ").append(pkcs7.length).append(" bytes\n\n");
            result.append("PKCS#7 (Base64):\n");
            result.append(Base64.getEncoder().encodeToString(pkcs7)).append("\n\n");
            result.append("PKCS#7 (Hex):\n");
            result.append(DataConverter.bytesToHex(pkcs7)).append("\n");
            result.append("========================================\n");

            generateResultArea.setText(result.toString());
            mainController.updateStatus("PKCS#7 generated successfully");
            if (mainController instanceof StatusReporter) {
                ((StatusReporter)mainController).publish(com.cryptocarver.model.OperationResult.forOperation("Generate PKCS#7")
                    .output(pkcs7)
                    .status("PKCS#7 generated successfully")
                    .build());
            }

        } catch (Exception e) {
            generateResultArea.setText("Error generating PKCS#7:\n" + e.getMessage());
            e.printStackTrace();
            mainController.updateStatus("Error: " + e.getMessage());
        }
    }

    /**
     * Verify/Decrypt PKCS#7
     */
    @FXML
    public void handleVerifyPKCS7() {
        try {
            String pkcs7Data = verifyPkcs7Area.getText().trim();
            String certData = verifyCertArea.getText().trim();
            String keyData = verifyPrivateKeyArea.getText().trim();

            if (pkcs7Data.isEmpty()) {
                verifyResultArea.setText("Error: PKCS#7 data is required");
                return;
            }

            // Parse PKCS#7
            byte[] pkcs7Bytes;
            if (pkcs7Data.matches("[0-9A-Fa-f]+")) {
                pkcs7Bytes = DataConverter.hexToBytes(pkcs7Data);
            } else {
                pkcs7Bytes = Base64.getDecoder().decode(pkcs7Data.replaceAll("\\s+", ""));
            }

            // Try to determine type
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("PKCS#7 VERIFICATION\n");
            result.append("========================================\n\n");

            // Try SignedData first
            try {
                X509Certificate cert = certData.isEmpty() ? null : CMSOperations.parseCertificate(certData);
                CMSOperations.VerificationResult verResult = CMSOperations.verifySignedData(pkcs7Bytes, cert);

                result.append("Type: Signed Data\n");
                result.append("Verification: ").append(verResult.verified ? "✓ VALID" : "✗ INVALID").append("\n\n");

                if (verResult.content != null) {
                    result.append("Original Data:\n");
                    result.append(new String(verResult.content, "UTF-8")).append("\n\n");
                }

                if (!verResult.associatedData.isEmpty()) {
                    result.append("Associated Data:\n");
                    for (Map.Entry<String, String> entry : verResult.associatedData.entrySet()) {
                        result.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    }
                    result.append("\n");
                }

            } catch (Exception e1) {
                // Try EnvelopedData
                try {
                    if (keyData.isEmpty()) {
                        result.append("Type: Enveloped Data\n");
                        result.append("Error: Private Key is required for decryption\n");
                    } else {
                        PrivateKey privateKey = parsePrivateKey(keyData);
                        X509Certificate cert = certData.isEmpty() ? null : CMSOperations.parseCertificate(certData);

                        byte[] decrypted = CMSOperations.decryptEnvelopedData(pkcs7Bytes, privateKey);

                        result.append("Type: Enveloped Data\n");
                        result.append("Decryption: ✓ SUCCESS\n\n");
                        result.append("Decrypted Data:\n");
                        result.append(new String(decrypted, "UTF-8")).append("\n\n");
                    }
                } catch (Exception e2) {
                    result.append("Error: Could not process as SignedData or EnvelopedData\n");
                    result.append("SignedData error: ").append(e1.getMessage()).append("\n");
                    result.append("EnvelopedData error: ").append(e2.getMessage()).append("\n");
                }
            }

            result.append("========================================\n");
            verifyResultArea.setText(result.toString());
            mainController.updateStatus("PKCS#7 processed");
            if (mainController instanceof StatusReporter) {
                ((StatusReporter)mainController).publish(com.cryptocarver.model.OperationResult.forOperation("Verify PKCS#7")
                    .input(pkcs7Bytes)
                    .status("PKCS#7 processed")
                    .build());
            }

        } catch (Exception e) {
            verifyResultArea.setText("Error verifying PKCS#7:\n" + e.getMessage());
            e.printStackTrace();
            mainController.updateStatus("Error: " + e.getMessage());
        }
    }

    /**
     * Load certificate from file
     */
    @FXML
    public void handleLoadCertGenerate() {
        loadCertificate(generateCertArea);
    }

    @FXML
    public void handleLoadCertVerify() {
        loadCertificate(verifyCertArea);
    }

    /**
     * Load private key from file
     */
    @FXML
    public void handleLoadKeyGenerate() {
        loadPrivateKey(generatePrivateKeyArea);
    }

    @FXML
    public void handleLoadKeyVerify() {
        loadPrivateKey(verifyPrivateKeyArea);
    }

    private void loadCertificate(TextArea targetArea) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Certificate");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificate Files", "*.pem", "*.crt", "*.cer", "*.der"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                targetArea.setText(content);
                mainController.updateStatus("Certificate loaded: " + file.getName());
            } catch (Exception e) {
                mainController.updateStatus("Error loading certificate: " + e.getMessage());
            }
        }
    }

    private void loadPrivateKey(TextArea targetArea) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Private Key");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Key Files", "*.pem", "*.key", "*.der"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                targetArea.setText(content);
                mainController.updateStatus("Private key loaded: " + file.getName());
            } catch (Exception e) {
                mainController.updateStatus("Error loading private key: " + e.getMessage());
            }
        }
    }

    /**
     * Parse private key from PEM or DER
     */
    private PrivateKey parsePrivateKey(String keyData) throws Exception {
        byte[] keyBytes;

        if (keyData.contains("BEGIN PRIVATE KEY") || keyData.contains("BEGIN RSA PRIVATE KEY")) {
            // PEM format
            keyData = keyData.replaceAll("-----BEGIN.*PRIVATE KEY-----", "")
                    .replaceAll("-----END.*PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            keyBytes = Base64.getDecoder().decode(keyData);
        } else {
            // Try as base64 or hex
            try {
                keyBytes = Base64.getDecoder().decode(keyData.replaceAll("\\s+", ""));
            } catch (Exception e) {
                keyBytes = DataConverter.hexToBytes(keyData.replaceAll("\\s+", ""));
            }
        }

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Parse associated data from text
     * Format: "oid=value" one per line
     */
    private Map<String, String> parseAssociatedData(String text) {
        Map<String, String> result = new HashMap<>();

        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || !line.contains("="))
                continue;

            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                result.put(parts[0].trim(), parts[1].trim());
            }
        }

        return result;
    }
}
