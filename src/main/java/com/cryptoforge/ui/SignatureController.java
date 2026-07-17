package com.cryptoforge.ui;

import com.cryptoforge.crypto.SignatureOperations;
import com.cryptoforge.crypto.AsymmetricKeyOperations;
import com.cryptoforge.util.DataConverter;
import com.cryptoforge.utils.OperationHistory;
import javafx.scene.control.*;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Controller for digital signature operations
 *
 * @author Felipe
 */
public class SignatureController {

    private MainController mainController;

    // UI components
    private ComboBox<String> signatureAlgorithmCombo;
    private ComboBox<String> inputFormatCombo;
    private ComboBox<String> outputFormatCombo;
    private TextArea inputArea;
    private TextArea outputArea;
    private Label signatureKeyStatusLabel;
    private TextField signatureVerifyField;

    // Keys
    private PrivateKey currentPrivateKey;
    private PublicKey currentPublicKey;

    public SignatureController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Initialize signature components
     */
    public void initialize(ComboBox<String> algorithmCombo,
                          ComboBox<String> inputFormatCombo,
                          ComboBox<String> outputFormatCombo,
                          TextArea inputArea,
                          TextArea outputArea,
                          Label statusLabel,
                          TextField signatureVerifyField) {
        this.signatureAlgorithmCombo = algorithmCombo;
        this.inputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;
        this.inputArea = inputArea;
        this.outputArea = outputArea;
        this.signatureKeyStatusLabel = statusLabel;
        this.signatureVerifyField = signatureVerifyField;

        // Populate algorithms
        signatureAlgorithmCombo.getItems().addAll(SignatureOperations.SUPPORTED_ALGORITHMS);
        signatureAlgorithmCombo.setValue("RSA-SHA256-PKCS1");  // Classic, widely compatible
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

            // Sign
            byte[] signature = SignatureOperations.sign(data, currentPrivateKey, algorithm);

            // Format output
            setOutputData(signature);

            mainController.updateStatus("Signature created with " + algorithm);

            // Add to history
            OperationHistory.getInstance().addOperation(
                "Authentication",
                "Sign - " + algorithm,
                "Data: " + data.length + " bytes",
                "Signature: " + signature.length + " bytes"
            );

        } catch (Exception e) {
            mainController.showError("Signature Error",
                "Error creating signature: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle verify operation
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

            // Get signature from signatureVerifyField - use OUTPUT format (signature was generated in Output)
            String signatureText = signatureVerifyField.getText().trim();
            if (signatureText.isEmpty()) {
                mainController.showError("Signature Error",
                    "Please paste the signature in the 'Signature' field");
                return;
            }

            byte[] signature = parseDataWithFormat(signatureText, outputFormatCombo.getValue());
            if (signature == null || signature.length == 0) {
                mainController.showError("Signature Error", "Invalid signature format");
                return;
            }

            // Get original data from input area
            String dataText = inputArea.getText().trim();
            if (dataText.isEmpty()) {
                mainController.showError("Data Error",
                    "Please enter the original data in the Input area");
                return;
            }

            byte[] data = parseDataWithFormat(dataText, inputFormatCombo.getValue());
            if (data == null || data.length == 0) {
                mainController.showError("Data Error", "Invalid data format");
                return;
            }

            // Verify key type matches algorithm
            String expectedKeyType = SignatureOperations.getExpectedKeyType(algorithm);
            String actualKeyType = currentPublicKey.getAlgorithm();
            if (!actualKeyType.equals(expectedKeyType)) {
                mainController.showError("Key Mismatch",
                    String.format("Algorithm %s requires %s key, but loaded key is %s",
                    algorithm, expectedKeyType, actualKeyType));
                return;
            }

            // Verify
            boolean valid = SignatureOperations.verify(data, signature, currentPublicKey, algorithm);

            // Display result
            if (valid) {
                mainController.showInfo("Signature Verification",
                    "✓ SIGNATURE VALID\n\n" +
                    "The signature is authentic and the data has not been modified.\n" +
                    "Algorithm: " + algorithm);
            } else {
                mainController.showError("Signature Verification",
                    "✗ SIGNATURE INVALID\n\n" +
                    "The signature does not match the data. The data may have been modified " +
                    "or the signature was created with a different key.\n" +
                    "Algorithm: " + algorithm);
            }

            mainController.updateStatus("Signature " + (valid ? "VALID" : "INVALID") + " - " + algorithm);

            // Add to history
            OperationHistory.getInstance().addOperation(
                "Authentication",
                "Verify - " + algorithm,
                "Signature: " + signature.length + " bytes",
                "Result: " + (valid ? "VALID ✓" : "INVALID ✗")
            );

        } catch (Exception e) {
            mainController.showError("Verification Error",
                "Error verifying signature: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load private key for signing
     */
    public void handleLoadPrivateKey(String filePath) {
        try {
            String pem = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath));

            // Try different key types
            try {
                currentPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEM(pem);
            } catch (Exception e) {
                // Try Ed25519
                try {
                    currentPrivateKey = AsymmetricKeyOperations.importEd25519PrivateKeyPEM(pem);
                } catch (Exception e2) {
                    // Try ECDSA
                    currentPrivateKey = AsymmetricKeyOperations.importECPrivateKeyPEM(pem);
                }
            }

            // Get detailed key info
            String keyInfo = getKeyInfo(currentPrivateKey);

            String status = "✓ Private key loaded (" + keyInfo + ")";
            if (currentPublicKey != null) {
                String pubKeyInfo = getKeyInfo(currentPublicKey);
                status += ", Public key loaded (" + pubKeyInfo + ")";
            }
            signatureKeyStatusLabel.setText(status);
            signatureKeyStatusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");

            mainController.updateStatus("Private key loaded: " + keyInfo);
            outputArea.setText("PRIVATE KEY LOADED SUCCESSFULLY\n\n" +
                             "File: " + filePath + "\n" +
                             "Key: " + keyInfo + "\n" +
                             "Ready for signing.");

        } catch (Exception e) {
            mainController.showError("Key Loading Error",
                "Error loading private key: " + e.getMessage() + "\n\n" +
                "Supported formats: RSA, Ed25519, ECDSA in PEM format");
        }
    }

    /**
     * Load public key for verification
     */
    public void handleLoadPublicKey(String filePath) {
        try {
            String pem = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath));

            // Try different key types
            try {
                currentPublicKey = AsymmetricKeyOperations.importPublicKeyPEM(pem);
            } catch (Exception e) {
                // Try Ed25519
                try {
                    currentPublicKey = AsymmetricKeyOperations.importEd25519PublicKeyPEM(pem);
                } catch (Exception e2) {
                    // Try ECDSA
                    currentPublicKey = AsymmetricKeyOperations.importECPublicKeyPEM(pem);
                }
            }

            // Get detailed key info
            String keyInfo = getKeyInfo(currentPublicKey);

            String status = "✓ Public key loaded (" + keyInfo + ")";
            if (currentPrivateKey != null) {
                String privKeyInfo = getKeyInfo(currentPrivateKey);
                status = "✓ Private key loaded (" + privKeyInfo + "), " + status;
            }
            signatureKeyStatusLabel.setText(status);
            signatureKeyStatusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");

            mainController.updateStatus("Public key loaded: " + keyInfo);
            outputArea.setText("PUBLIC KEY LOADED SUCCESSFULLY\n\n" +
                             "File: " + filePath + "\n" +
                             "Key: " + keyInfo + "\n" +
                             "Ready for verification.");

        } catch (Exception e) {
            mainController.showError("Key Loading Error",
                "Error loading public key: " + e.getMessage() + "\n\n" +
                "Supported formats: RSA, Ed25519, ECDSA in PEM format");
        }
    }

    /**
     * Get input data as bytes (uses toolbar format combo)
     */
    private byte[] getInputDataAsBytes() {
        String input = inputArea.getText().trim();
        if (input.isEmpty()) {
            return null;
        }
        return parseDataWithFormat(input, inputFormatCombo.getValue());
    }

    /**
     * Parse data with given format
     */
    private byte[] parseDataWithFormat(String data, String format) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        if (format == null) format = "Hexadecimal";

        try {
            switch (format) {
                case "Hexadecimal":
                    return DataConverter.hexToBytes(data);
                case "Base64":
                    return org.apache.commons.codec.binary.Base64.decodeBase64(data);
                case "Text (UTF-8)":
                    return data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                case "Binary":
                    return DataConverter.binaryToBytes(data);
                default:
                    return DataConverter.hexToBytes(data);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing data: " + e.getMessage());
        }
    }

    /**
     * Set output data (uses toolbar format combo)
     */
    private void setOutputData(byte[] data) {
        String format = outputFormatCombo.getValue();
        if (format == null) format = "Hexadecimal";

        String output;
        switch (format) {
            case "Hexadecimal":
                output = DataConverter.bytesToHex(data);
                break;
            case "Base64":
                output = org.apache.commons.codec.binary.Base64.encodeBase64String(data);
                break;
            case "Text (UTF-8)":
                output = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                break;
            case "Binary":
                output = DataConverter.bytesToBinary(data);
                break;
            default:
                output = DataConverter.bytesToHex(data);
        }

        outputArea.setText(output);
    }

    /**
     * Get detailed key information (algorithm + size for RSA/ECDSA)
     */
    private String getKeyInfo(java.security.Key key) {
        String algorithm = key.getAlgorithm();

        if (algorithm.equals("RSA")) {
            if (key instanceof java.security.interfaces.RSAPublicKey) {
                int bits = ((java.security.interfaces.RSAPublicKey) key).getModulus().bitLength();
                return "RSA-" + bits;
            } else if (key instanceof java.security.interfaces.RSAPrivateKey) {
                int bits = ((java.security.interfaces.RSAPrivateKey) key).getModulus().bitLength();
                return "RSA-" + bits;
            }
            return "RSA";
        } else if (algorithm.equals("EC")) {
            // Try to get curve name for ECDSA
            try {
                if (key instanceof java.security.interfaces.ECKey) {
                    java.security.spec.ECParameterSpec spec = ((java.security.interfaces.ECKey) key).getParams();
                    int fieldSize = spec.getCurve().getField().getFieldSize();
                    return "ECDSA-P" + fieldSize;
                }
            } catch (Exception e) {
                // Ignore
            }
            return "ECDSA";
        } else if (algorithm.equals("Ed25519")) {
            return "Ed25519";
        }

        return algorithm;
    }
}
