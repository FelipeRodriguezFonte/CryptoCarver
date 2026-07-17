package com.cryptoforge.ui;

import com.cryptoforge.crypto.SignatureOperations;
import com.cryptoforge.crypto.AsymmetricKeyOperations;
import com.cryptoforge.crypto.MACOperations;
import com.cryptoforge.util.DataConverter;
import com.cryptoforge.model.OperationResult;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.ECKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Authentication operations (Digital Signatures and MAC)
 * Designed for Modern UI
 *
 * @author Felipe
 */
public class AuthenticationController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationController.class);

    private final StatusReporter mainController;

    // Shared UI components
    private TextArea inputArea;
    private TextArea outputArea;
    private ComboBox<String> inputFormatCombo;
    private ComboBox<String> outputFormatCombo;

    // Digital Signatures UI
    private ComboBox<String> signatureAlgorithmCombo;
    private Label signatureKeyStatusLabel;
    private TextField signatureVerifyField;

    // Digital Signatures Keys
    private PrivateKey currentPrivateKey;
    private PublicKey currentPublicKey;
    private TextArea signaturePrivateKeyArea;
    private TextArea signaturePublicKeyArea;

    // MAC UI
    private ComboBox<String> authMacAlgorithmCombo;
    private TextField authMacKeyField;
    private Label authMacKeyInfoLabel;
    private ComboBox<String> authMacTruncationCombo;
    private TextField authMacVerifyField;
    private TextField authMacNonceField;
    private ComboBox<String> macKeySourceCombo;
    private ComboBox<String> macHsmKeyCombo;

    /**
     * Constructor
     */
    public AuthenticationController(StatusReporter mainController,
            TextArea inputArea,
            TextArea outputArea,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo) {
        this.mainController = mainController;
        this.inputArea = inputArea;
        this.outputArea = outputArea;
        this.inputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;
    }

    /**
     * Initialize Digital Signatures components
     */
    public void initializeSignatures(ComboBox<String> algorithmCombo,
            Label statusLabel,
            TextField verifyField,
            TextArea privateKeyArea,
            TextArea publicKeyArea) {
        this.signatureAlgorithmCombo = algorithmCombo;
        this.signatureKeyStatusLabel = statusLabel;
        this.signatureVerifyField = verifyField;
        this.signaturePrivateKeyArea = privateKeyArea;
        this.signaturePublicKeyArea = publicKeyArea;

        // Populate signature algorithms
        signatureAlgorithmCombo.getItems().addAll(SignatureOperations.SUPPORTED_ALGORITHMS);
        signatureAlgorithmCombo.setValue("RSA-SHA256-PKCS1");
    }

    /**
     * Initialize MAC components
     */
    public void initializeMAC(ComboBox<String> algorithmCombo,
            TextField keyField,
            Label keyInfoLabel,
            ComboBox<String> truncationCombo,
            TextField verifyField,
            TextField nonceField,
            ComboBox<String> keySourceCombo,
            ComboBox<String> hsmKeyCombo) {
        this.authMacAlgorithmCombo = algorithmCombo;
        this.authMacKeyField = keyField;
        this.authMacKeyInfoLabel = keyInfoLabel;
        this.authMacTruncationCombo = truncationCombo;
        this.authMacVerifyField = verifyField;
        this.authMacNonceField = nonceField;
        this.macKeySourceCombo = keySourceCombo;
        this.macHsmKeyCombo = hsmKeyCombo;

        if (macKeySourceCombo != null) {
            macKeySourceCombo.getItems().addAll("Manual Input", "Simulated HSM");
            macKeySourceCombo.setValue("Manual Input");
            macKeySourceCombo.setOnAction(e -> updateMacKeySourceVisibility());
        }

        refreshHsmKeys();

        // Populate MAC algorithms
        authMacAlgorithmCombo.getItems().addAll(MACOperations.SUPPORTED_ALGORITHMS);
        authMacAlgorithmCombo.getItems().addAll("GMAC-AES", "Poly1305");
        authMacAlgorithmCombo.setValue("HMAC-SHA256");

        // Populate truncation options
        authMacTruncationCombo.getItems().addAll(
                "0 (full)", "4", "8", "16", "20", "32", "48", "64");
        authMacTruncationCombo.setValue("0 (full)");

        // Update key info and truncation on algorithm change
        authMacAlgorithmCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateMacKeyInfo(newVal);
                updateDefaultTruncation(newVal);
            }
        });

        updateMacKeyInfo("HMAC-SHA256");
        updateDefaultTruncation("HMAC-SHA256");
    }

    private void updateDefaultTruncation(String algorithm) {
        String defaultValue;
        switch (algorithm) {
            case "HMAC-SHA1":
            case "HMAC-SHA256":
            case "HMAC-SHA384":
            case "HMAC-SHA512":
            case "CMAC-AES":
                defaultValue = "0 (full)";
                break;
            case "CMAC-3DES":
            case "CBC-MAC-AES":
                defaultValue = "8";
                break;
            case "CBC-MAC-DES":
            case "CBC-MAC-3DES":
            case "ISO-9797-1-ALG1":
            case "ANSI-X9.9":
            case "ANSI-X9.19":
            case "AS2805.4.1":
            case "Retail-MAC-DES":
            case "Retail-MAC-3DES":
                defaultValue = "4";
                break;
            default:
                defaultValue = "0 (full)";
                break;
        }

        // Find matching item in combo
        for (String item : authMacTruncationCombo.getItems()) {
            if (item.startsWith(defaultValue)) {
                authMacTruncationCombo.setValue(item);
                return;
            }
        }
        // Fallback
        if (defaultValue.equals("4"))
            authMacTruncationCombo.setValue("4");
        else if (defaultValue.equals("8"))
            authMacTruncationCombo.setValue("8");
        else
            authMacTruncationCombo.setValue("0 (full)");
    }

    // ============================================================
    // DIGITAL SIGNATURES OPERATIONS
    // ============================================================

    /**
     * Handle load private key
     */
    public void handleLoadSignPrivateKey() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Private Key for Signing (PEM)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.key"));

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                if (signaturePrivateKeyArea != null) {
                    signaturePrivateKeyArea.setText(content);
                }
                loadPrivateKey(content);
            } catch (Exception e) {
                mainController.showError("Load Error", "Failed to read file: " + e.getMessage());
            }
        }
    }

    /**
     * Handle load public key
     */
    public void handleLoadSignPublicKey() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Public Key for Verification (PEM)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PEM Files", "*.pem", "*.pub", "*.key"));

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                if (signaturePublicKeyArea != null) {
                    signaturePublicKeyArea.setText(content);
                }
                loadPublicKey(content);
            } catch (Exception e) {
                mainController.showError("Load Error", "Failed to read file: " + e.getMessage());
            }
        }
    }

    /**
     * Load private key from PEM file
     */
    /**
     * Load private key from PEM file
     */
    private void loadPrivateKey(String pem) {
        try {
            // Determine expected algorithm
            String selectedAlgo = signatureAlgorithmCombo.getValue();
            if (selectedAlgo == null)
                selectedAlgo = "RSA"; // Default

            if (selectedAlgo.contains("Ed25519")) {
                currentPrivateKey = AsymmetricKeyOperations.importEd25519PrivateKeyPEM(pem);
            } else if (selectedAlgo.contains("ECDSA")) {
                currentPrivateKey = AsymmetricKeyOperations.importECPrivateKeyPEM(pem);
            } else {
                // Default to RSA/Generic
                currentPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEM(pem);
            }

            String keyType = currentPrivateKey.getAlgorithm();
            int keySize = getKeySize(currentPrivateKey);

            signatureKeyStatusLabel.setText(String.format("Private: %s %d bits", keyType, keySize));
            signatureKeyStatusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");

            mainController.updateStatus("Private key loaded: " + keyType);

        } catch (Exception e) {
            currentPrivateKey = null;
            signatureKeyStatusLabel.setText("Error loading private key");
            signatureKeyStatusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 10px;");

            String help = "";
            String selectedAlgo = signatureAlgorithmCombo.getValue();
            if (selectedAlgo != null && selectedAlgo.contains("Ed25519")) {
                help = "\n\nEnsure you are loading a valid Ed25519 PKCS#8 private key.";
            } else if (e.getMessage().contains("RSA")) {
                help = "\n\nHint: Ensure the selected algorithm matches the key type.";
            }

            mainController.showError("Key Error", "Error loading private key: " + e.getMessage() + help);
        }
    }

    /**
     * Load public key from PEM file
     */
    /**
     * Load public key from PEM file
     */
    private void loadPublicKey(String pem) {
        try {
            // Determine expected algorithm
            String selectedAlgo = signatureAlgorithmCombo.getValue();
            if (selectedAlgo == null)
                selectedAlgo = "RSA"; // Default

            if (selectedAlgo.contains("Ed25519")) {
                currentPublicKey = AsymmetricKeyOperations.importEd25519PublicKeyPEM(pem);
            } else if (selectedAlgo.contains("ECDSA")) {
                currentPublicKey = AsymmetricKeyOperations.importECPublicKeyPEM(pem);
            } else {
                // Default to RSA/Generic
                currentPublicKey = AsymmetricKeyOperations.importPublicKeyPEM(pem);
            }

            String keyType = currentPublicKey.getAlgorithm();
            int keySize = getKeySize(currentPublicKey);

            String currentText = signatureKeyStatusLabel.getText();
            if (currentText.contains("Private")) {
                signatureKeyStatusLabel.setText(String.format("%s | Public: %s %d bits",
                        currentText, keyType, keySize));
            } else {
                signatureKeyStatusLabel.setText(String.format("Public: %s %d bits", keyType, keySize));
            }
            signatureKeyStatusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");

            mainController.updateStatus("Public key loaded: " + keyType);

        } catch (Exception e) {
            currentPublicKey = null;
            signatureKeyStatusLabel.setText("Error loading public key");
            signatureKeyStatusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 10px;");

            String help = "";
            String selectedAlgo = signatureAlgorithmCombo.getValue();
            if (selectedAlgo != null && selectedAlgo.contains("Ed25519")) {
                help = "\n\nEnsure you are loading a valid Ed25519 public key.";
            } else if (e.getMessage().contains("RSA")) {
                help = "\n\nHint: Ensure the selected algorithm matches the key type.";
            }

            mainController.showError("Key Error", "Error loading public key: " + e.getMessage() + help);
        }
    }

    /**
     * Get key size in bits
     */
    private int getKeySize(Object key) {
        try {
            if (key instanceof RSAKey) {
                return ((RSAKey) key).getModulus().bitLength();
            } else if (key instanceof ECKey) {
                return ((ECKey) key).getParams().getOrder().bitLength();
            } else {
                return 0; // Unknown
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Handle sign operation
     */
    public void handleSign() {
        try {
            if (currentPrivateKey == null) {
                mainController.showError("Key Error", "Please load a private key first");
                return;
            }

            String algorithm = signatureAlgorithmCombo.getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a signature algorithm");
                return;
            }

            // Get data to sign
            byte[] data = getInputDataAsBytes();
            if (data == null || data.length == 0) {
                mainController.showError("Input Error", "Please enter data to sign");
                return;
            }

            // Verify key type matches algorithm
            String expectedKeyType = SignatureOperations.getExpectedKeyType(algorithm);
            String actualKeyType = currentPrivateKey.getAlgorithm();
            if (!actualKeyType.equals(expectedKeyType)) {
                mainController.showError("Key Mismatch",
                        String.format("Algorithm %s requires %s key, but loaded key is %s",
                                algorithm, expectedKeyType, actualKeyType));
                return;
            }

            // Ensure key is current from TextArea if possible
            if (signaturePrivateKeyArea != null && !signaturePrivateKeyArea.getText().trim().isEmpty()) {
                try {
                    String pem = signaturePrivateKeyArea.getText().trim();
                    if (algorithm.contains("Ed25519")) {
                        currentPrivateKey = AsymmetricKeyOperations.importEd25519PrivateKeyPEM(pem);
                    } else if (algorithm.contains("ECDSA")) {
                        currentPrivateKey = AsymmetricKeyOperations.importECPrivateKeyPEM(pem);
                    } else {
                        currentPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEM(pem);
                    }
                } catch (Exception e) {
                    // Ignore, rely on loaded key or fail
                    mainController.showError("Key Parse Error",
                            "Could not parse private key from text area: " + e.getMessage());
                    return;
                }
            }

            // Sign
            byte[] signature = SignatureOperations.sign(data, currentPrivateKey, algorithm);

            // Format output
            setOutputData(signature);

            mainController.showInfo("Success",
                    String.format("Signature created successfully!\nAlgorithm: %s\nSignature size: %d bytes",
                            algorithm, signature.length));

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Data Size", data.length + " bytes");
            details.put("Signature Size", signature.length + " bytes");
            details.put("Key Type", currentPrivateKey != null ? currentPrivateKey.getAlgorithm() : "Unknown");
            mainController.publish(OperationResult.forOperation("Data Signed")
                    .input(data).output(signature).details(details)
                    .status("Signature created with " + algorithm).build());

        } catch (Exception e) {
            mainController.showError("Signature Error",
                    "Error creating signature: " + e.getMessage());
            LOG.error("Digital signature creation failed", e);
        }
    }

    /**
     * Handle verify signature operation
     */
    public void handleVerify() {
        try {
            if (currentPublicKey == null) {
                mainController.showError("Key Error", "Please load a public key first");
                return;
            }

            String algorithm = signatureAlgorithmCombo.getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a signature algorithm");
                return;
            }

            // Ensure key is current from TextArea if possible
            if (signaturePublicKeyArea != null && !signaturePublicKeyArea.getText().trim().isEmpty()) {
                try {
                    String pem = signaturePublicKeyArea.getText().trim();
                    if (algorithm.contains("Ed25519")) {
                        currentPublicKey = AsymmetricKeyOperations.importEd25519PublicKeyPEM(pem);
                    } else if (algorithm.contains("ECDSA")) {
                        currentPublicKey = AsymmetricKeyOperations.importECPublicKeyPEM(pem);
                    } else {
                        currentPublicKey = AsymmetricKeyOperations.importPublicKeyPEM(pem);
                    }
                } catch (Exception e) {
                    mainController.showError("Key Parse Error",
                            "Could not parse public key from text area: " + e.getMessage());
                    return;
                }
            }

            // Get signature from verify field
            String signatureText = signatureVerifyField.getText().trim();
            if (signatureText.isEmpty()) {
                mainController.showError("Signature Error",
                        "Please paste the signature in the verification field");
                return;
            }

            byte[] signature = parseDataWithFormat(signatureText, outputFormatCombo.getValue());
            if (signature == null || signature.length == 0) {
                mainController.showError("Signature Error", "Invalid signature format");
                return;
            }

            // Get original data from input area
            byte[] data = getInputDataAsBytes();
            if (data == null || data.length == 0) {
                mainController.showError("Data Error",
                        "Please enter the original data that was signed");
                return;
            }

            // Verify
            boolean valid = SignatureOperations.verify(data, signature, currentPublicKey, algorithm);

            if (valid) {
                mainController.showInfo("Verification Success",
                        "✅ Signature is VALID!\n\nThe data has not been tampered with.");
            } else {
                mainController.showError("Verification Failed",
                        "❌ Signature is INVALID!\n\nThe data may have been tampered with or the wrong key was used.");
            }

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Result", valid ? "VALID" : "INVALID");
            details.put("Data Size", data.length + " bytes");
            details.put("Key Type", currentPublicKey != null ? currentPublicKey.getAlgorithm() : "Unknown");
            mainController.publish(OperationResult.forOperation("Signature Verified")
                    .input(data).output(signature).details(details)
                    .status("Signature verification: " + (valid ? "VALID" : "INVALID")).build());

        } catch (Exception e) {
            mainController.showError("Verification Error",
                    "Error verifying signature: " + e.getMessage());
            LOG.error("Digital signature verification failed", e);
        }
    }

    // ============================================================
    // MAC OPERATIONS
    // ============================================================

    /**
     * Update MAC key info label
     */
    private void updateMacKeyInfo(String algorithm) {
        if ("GMAC-AES".equals(algorithm)) {
            authMacKeyInfoLabel.setText("GMAC: AES key 16/24/32 bytes; nonce must be unique (12 bytes recommended)");
            authMacNonceField.setDisable(false);
        } else if ("Poly1305".equals(algorithm)) {
            authMacKeyInfoLabel.setText("Poly1305: a unique one-time 32-byte key is required for every message");
            authMacNonceField.clear();
            authMacNonceField.setDisable(true);
        } else {
            String info = MACOperations.getExpectedKeySize(algorithm);
            authMacKeyInfoLabel.setText("Expected key size: " + info);
            authMacNonceField.clear();
            authMacNonceField.setDisable(true);
        }

        boolean isGmac = "GMAC-AES".equals(algorithm);
        if (authMacNonceField != null) {
            authMacNonceField.setVisible(isGmac);
            authMacNonceField.setManaged(isGmac);
        }
    }

    private void updateMacKeySourceVisibility() {
        boolean isHsm = "Simulated HSM".equals(macKeySourceCombo.getValue());
        if (authMacKeyField != null) {
            authMacKeyField.setVisible(!isHsm);
            authMacKeyField.setManaged(!isHsm);
        }
        if (macHsmKeyCombo != null) {
            macHsmKeyCombo.setVisible(isHsm);
            macHsmKeyCombo.setManaged(isHsm);
        }
    }

    public void refreshHsmKeys() {
        if (macHsmKeyCombo != null) {
            String current = macHsmKeyCombo.getValue();
            macHsmKeyCombo.getItems().clear();
            macHsmKeyCombo.getItems().addAll(com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().listKeyIds(com.cryptoforge.crypto.hsm.KeyUsage.MAC));
            if (current != null && macHsmKeyCombo.getItems().contains(current)) {
                macHsmKeyCombo.setValue(current);
            } else if (!macHsmKeyCombo.getItems().isEmpty()) {
                macHsmKeyCombo.setValue(macHsmKeyCombo.getItems().get(0));
            }
        }
    }

    public void saveCurrentKeyToHsm() {
        try {
            if (authMacKeyField == null || authMacKeyField.getText().trim().isEmpty()) {
                mainController.showError("Save Error", "No key to save");
                return;
            }
            byte[] keyBytes = DataConverter.hexToBytes(authMacKeyField.getText().trim());
            String algo = authMacAlgorithmCombo.getValue();
            if (algo == null) algo = "HMAC";
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, algo);
            com.cryptoforge.crypto.hsm.KeyMaterial km = com.cryptoforge.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                null, secretKey,
                com.cryptoforge.crypto.hsm.KeyExportability.NON_EXPORTABLE,
                java.util.Set.of(com.cryptoforge.crypto.hsm.KeyUsage.MAC)
            );
            com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            mainController.showInfo("Success", "Key saved to Lab Cache as " + km.getId());
            refreshHsmKeys();
            macKeySourceCombo.setValue("Simulated HSM");
        } catch (Exception e) {
            mainController.showError("Save Error", "Failed to save key: " + e.getMessage());
        }
    }

    private String getHsmMacKeyId() {
        if (macKeySourceCombo != null && "Simulated HSM".equals(macKeySourceCombo.getValue())) {
            String keyId = macHsmKeyCombo.getValue();
            if (keyId == null || keyId.isEmpty()) {
                throw new IllegalArgumentException("Please select a key from the Lab Cache");
            }
            return keyId;
        }
        return null;
    }

    private byte[] getManualMacKey() {
        String keyHex = authMacKeyField.getText().trim();
        if (keyHex.isEmpty()) {
            throw new IllegalArgumentException("Please enter MAC key in hexadecimal");
        }
        return DataConverter.hexToBytes(keyHex);
    }

    /**
     * Handle generate MAC
     */
    public void handleGenerateMAC() {
        try {
            String algorithm = authMacAlgorithmCombo.getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a MAC algorithm");
                return;
            }

            // Get MAC key
            String hsmKeyId = getHsmMacKeyId();
            byte[] manualKey = hsmKeyId == null ? getManualMacKey() : null;

            // Get data
            byte[] data = getInputDataAsBytes();
            if (data == null || data.length == 0) {
                mainController.showError("Input Error", "Please enter data to MAC");
                return;
            }

            // Get truncation
            int truncation = getTruncationBytes();

            // Generate MAC
            byte[] mac = generateMac(data, hsmKeyId, manualKey, algorithm);

            // Truncate if needed
            if (truncation > 0 && truncation < mac.length) {
                byte[] truncatedMac = new byte[truncation];
                System.arraycopy(mac, 0, truncatedMac, 0, truncation);
                mac = truncatedMac;
            }

            // Format output
            setOutputData(mac);

            mainController.showInfo("Success",
                    String.format("MAC generated successfully!\nAlgorithm: %s\nMAC size: %d bytes",
                            algorithm, mac.length));

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Data Size", data.length + " bytes");
            details.put("MAC Size", mac.length + " bytes");
            details.put("Truncation", truncation > 0 ? truncation + " bytes" : "None");
            // Add MAC Output preview
            details.put("Output", DataConverter.bytesToHex(mac));

            mainController.publish(OperationResult.forOperation("MAC Generated")
                    .input(data).output(mac).details(details)
                    .status("MAC generated with " + algorithm).build());

        } catch (Exception e) {
            mainController.showError("MAC Error",
                    "Error generating MAC: " + e.getMessage());
            LOG.error("MAC generation failed", e);
        }
    }

    /**
     * Handle verify MAC
     */
    public void handleVerifyMAC() {
        try {
            String algorithm = authMacAlgorithmCombo.getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a MAC algorithm");
                return;
            }

            // Get MAC key
            String hsmKeyId = getHsmMacKeyId();
            byte[] manualKey = hsmKeyId == null ? getManualMacKey() : null;

            // Get MAC from verify field
            String macText = authMacVerifyField.getText().trim();
            if (macText.isEmpty()) {
                mainController.showError("MAC Error",
                        "Please paste the MAC in the verification field");
                return;
            }

            byte[] providedMac = parseDataWithFormat(macText, outputFormatCombo.getValue());
            if (providedMac == null || providedMac.length == 0) {
                mainController.showError("MAC Error", "Invalid MAC format");
                return;
            }

            // Get original data
            byte[] data = getInputDataAsBytes();
            if (data == null || data.length == 0) {
                mainController.showError("Data Error",
                        "Please enter the original data that was MACed");
                return;
            }

            // Generate MAC to compare
            byte[] calculatedMac = generateMac(data, hsmKeyId, manualKey, algorithm);

            // Truncate if needed (match provided MAC length)
            if (providedMac.length < calculatedMac.length) {
                byte[] truncatedMac = new byte[providedMac.length];
                System.arraycopy(calculatedMac, 0, truncatedMac, 0, providedMac.length);
                calculatedMac = truncatedMac;
            }

            // Verify
            boolean valid = MACOperations.constantTimeEquals(calculatedMac, providedMac);

            if (valid) {
                mainController.showInfo("Verification Success",
                        "✅ MAC is VALID!\n\nThe data has not been tampered with.");
            } else {
                mainController.showError("Verification Failed",
                        "❌ MAC is INVALID!\n\nThe data may have been tampered with or the wrong key was used.");
            }

            Map<String, String> details = new HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Result", valid ? "VALID" : "INVALID");
            details.put("Data Size", data.length + " bytes");
            details.put("Truncation", providedMac.length + " bytes (provided)");
            mainController.publish(OperationResult.forOperation("MAC Verified")
                    .input(data).output(providedMac).details(details)
                    .status("MAC verification: " + (valid ? "VALID" : "INVALID")).build());

        } catch (Exception e) {
            mainController.showError("Verification Error",
                    "Error verifying MAC: " + e.getMessage());
            LOG.error("MAC verification failed", e);
        }
    }

    /**
     * Get truncation value in bytes from combo (0 = full MAC)
     */
    private int getTruncationBytes() {
        String value = authMacTruncationCombo.getValue();
        if (value == null || value.startsWith("0")) {
            return 0; // Full MAC, no truncation
        }
        try {
            // Extract number from "4 (standard)" etc.
            if (value.contains(" ")) {
                return Integer.parseInt(value.split(" ")[0]);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private byte[] generateMac(byte[] data, String hsmKeyId, byte[] manualKey, String algorithm) throws Exception {
        if ("GMAC-AES".equals(algorithm)) {
            String nonceHex = authMacNonceField.getText().trim();
            if (nonceHex.isEmpty()) {
                throw new IllegalArgumentException("GMAC requires a unique nonce/IV in hexadecimal");
            }
            byte[] nonce = DataConverter.hexToBytes(nonceHex.replaceAll("\\s+", ""));
            if (hsmKeyId != null) {
                return com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().generateGmac(hsmKeyId, data, nonce);
            }
            return MACOperations.generateGmac(data, manualKey, nonce);
        }
        if ("Poly1305".equals(algorithm)) {
            if (hsmKeyId != null) {
                return com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().generatePoly1305(hsmKeyId, data);
            }
            return MACOperations.generatePoly1305(data, manualKey);
        }
        if (hsmKeyId != null) {
            return com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().generateMac(hsmKeyId, data, algorithm);
        }
        return MACOperations.generate(data, manualKey, algorithm);
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Get input data as bytes based on input format
     */
    private byte[] getInputDataAsBytes() {
        if (inputArea == null) {
            // No input area available, show error
            mainController.showError("Configuration Error",
                    "Input area not available. Authentication operations require proper UI setup.");
            return null;
        }

        String input = inputArea.getText().trim();
        if (input.isEmpty()) {
            return null;
        }

        String format = inputFormatCombo.getValue();
        return parseDataWithFormat(input, format);
    }

    /**
     * Set output data based on output format
     */
    private void setOutputData(byte[] data) {
        if (outputArea == null) {
            // No output area available, show data in popup
            String format = outputFormatCombo != null ? outputFormatCombo.getValue() : "Hexadecimal";
            String output = formatDataWithFormat(data, format);
            mainController.showInfo("Output", output);
            return;
        }

        String format = outputFormatCombo.getValue();
        String output = formatDataWithFormat(data, format);
        outputArea.setText(output);
    }

    /**
     * Parse data with specified format
     */
    private byte[] parseDataWithFormat(String data, String format) {
        try {
            if (format == null)
                format = "Hexadecimal";

            switch (format) {
                case "Hexadecimal":
                    return DataConverter.hexToBytes(data);
                case "Base64":
                    return Base64.getDecoder().decode(data.replaceAll("\\s", ""));
                case "Text (UTF-8)":
                    return data.getBytes("UTF-8");
                default:
                    return data.getBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Format data with specified format
     */
    private String formatDataWithFormat(byte[] data, String format) {
        try {
            if (format == null)
                format = "Hexadecimal";

            switch (format) {
                case "Hexadecimal":
                    return DataConverter.bytesToHex(data);
                case "Base64":
                    return Base64.getEncoder().encodeToString(data);
                case "Text (UTF-8)":
                    return new String(data, "UTF-8");
                default:
                    return DataConverter.bytesToHex(data);
            }
        } catch (Exception e) {
            return "";
        }
    }
}
