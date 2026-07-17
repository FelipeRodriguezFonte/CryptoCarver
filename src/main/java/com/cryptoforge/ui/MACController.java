package com.cryptoforge.ui;

import com.cryptoforge.crypto.MACOperations;
import com.cryptoforge.util.DataConverter;
import com.cryptoforge.utils.OperationHistory;
import javafx.scene.control.*;

/**
 * Controller for Message Authentication Code (MAC) operations
 *
 * @author Felipe
 */
public class MACController {

    private MainController mainController;

    // UI components
    private ComboBox<String> macAlgorithmCombo;
    private ComboBox<String> inputFormatCombo;
    private ComboBox<String> outputFormatCombo;
    private TextArea inputArea;
    private TextArea outputArea;
    private TextField macKeyField;
    private Label macKeyInfoLabel;
    private TextField macKeyK;
    private TextField macKeyKPrime;
    private ComboBox<String> macTruncationCombo;
    private TextField macVerifyField;

    public MACController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Initialize MAC components
     */
    public void initialize(ComboBox<String> algorithmCombo,
                          ComboBox<String> inputFormatCombo,
                          ComboBox<String> outputFormatCombo,
                          TextArea inputArea,
                          TextArea outputArea,
                          TextField keyField,
                          Label keyInfoLabel,
                          TextField keyK,
                          TextField keyKPrime,
                          ComboBox<String> truncationCombo,
                          TextField verifyField) {
        this.macAlgorithmCombo = algorithmCombo;
        this.inputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;
        this.inputArea = inputArea;
        this.outputArea = outputArea;
        this.macKeyField = keyField;
        this.macKeyInfoLabel = keyInfoLabel;
        this.macKeyK = keyK;
        this.macKeyKPrime = keyKPrime;
        this.macTruncationCombo = truncationCombo;
        this.macVerifyField = verifyField;

        // Populate algorithms
        macAlgorithmCombo.getItems().addAll(MACOperations.SUPPORTED_ALGORITHMS);
        macAlgorithmCombo.setValue("HMAC-SHA256");

        // Populate truncation options (0 = full MAC)
        macTruncationCombo.getItems().addAll(
            "0 (full)",
            "4",
            "8",
            "16",
            "20",
            "32",
            "48",
            "64"
        );

        // Add listener to update key info and truncation when algorithm changes
        macAlgorithmCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateKeyInfo(newVal);
                updateDefaultTruncation(newVal);
            }
        });

        // Set initial values
        updateKeyInfo("HMAC-SHA256");
        updateDefaultTruncation("HMAC-SHA256");
    }

    /**
     * Get MAC key (either from K||K' fields or from main key field)
     */
    private byte[] getMacKey() throws IllegalArgumentException {
        String kText = macKeyK.getText().trim();
        String kPrimeText = macKeyKPrime.getText().trim();

        if (!kText.isEmpty() && !kPrimeText.isEmpty()) {
            // Use K and K' fields - concatenate them
            try {
                byte[] k = DataConverter.hexToBytes(kText);
                byte[] kPrime = DataConverter.hexToBytes(kPrimeText);

                // Concatenate K||K'
                byte[] key = new byte[k.length + kPrime.length];
                System.arraycopy(k, 0, key, 0, k.length);
                System.arraycopy(kPrime, 0, key, k.length, kPrime.length);

                return key;
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid key format in K or K' fields. Please enter hexadecimal (e.g., 0123456789ABCDEF)");
            }
        } else {
            // Use main key field
            String keyHex = macKeyField.getText().trim();
            if (keyHex.isEmpty()) {
                throw new IllegalArgumentException("Please enter a MAC key in hexadecimal (or use K and K' fields)");
            }

            try {
                return DataConverter.hexToBytes(keyHex);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid key format. Please enter hexadecimal (e.g., 0123456789ABCDEF)");
            }
        }
    }

    /**
     * Handle generate MAC operation
     */
    public void handleGenerateMAC() {
        try {
            String algorithm = macAlgorithmCombo.getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a MAC algorithm");
                return;
            }

            // Get MAC key (from K||K' fields or main key field)
            byte[] key;
            try {
                key = getMacKey();
            } catch (IllegalArgumentException e) {
                mainController.showError("Key Error", e.getMessage());
                return;
            }

            // Get data
            byte[] data = getInputDataAsBytes();
            if (data == null || data.length == 0) {
                mainController.showError("Input Error", "Please enter data in the Input area");
                return;
            }

            // Generate MAC
            byte[] mac = MACOperations.generate(data, key, algorithm);

            // Apply truncation if needed
            int truncationBytes = getTruncationBytes();
            byte[] finalMac = mac;
            String truncationInfo = "";

            if (truncationBytes > 0 && truncationBytes < mac.length) {
                finalMac = new byte[truncationBytes];
                System.arraycopy(mac, 0, finalMac, 0, truncationBytes);
                truncationInfo = " (truncated to " + truncationBytes + " bytes)";
            }

            // Format output
            setOutputData(finalMac);

            mainController.updateStatus("MAC generated with " + algorithm + " (" + finalMac.length + " bytes)" + truncationInfo);

            // Add to history
            OperationHistory.getInstance().addOperation(
                "Authentication",
                "Generate MAC - " + algorithm,
                "Data: " + data.length + " bytes, Key: " + (key.length * 8) + " bits",
                "MAC: " + finalMac.length + " bytes" + truncationInfo
            );

        } catch (IllegalArgumentException e) {
            mainController.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            mainController.showError("MAC Error",
                "Error generating MAC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle verify MAC operation
     */
    public void handleVerifyMAC() {
        try {
            String algorithm = macAlgorithmCombo.getValue();
            if (algorithm == null) {
                mainController.showError("Algorithm Error", "Please select a MAC algorithm");
                return;
            }

            // Get MAC key (from K||K' fields or main key field)
            byte[] key;
            try {
                key = getMacKey();
            } catch (IllegalArgumentException e) {
                mainController.showError("Key Error", e.getMessage());
                return;
            }

            // Get MAC value from verify field - use OUTPUT format (MAC was generated in Output)
            String macText = macVerifyField.getText().trim();
            if (macText.isEmpty()) {
                mainController.showError("MAC Error",
                    "Please paste the MAC value in the 'MAC value' field");
                return;
            }

            byte[] mac = parseDataWithFormat(macText, outputFormatCombo.getValue());
            if (mac == null || mac.length == 0) {
                mainController.showError("MAC Error", "Invalid MAC format");
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

            // Generate MAC with same algorithm
            byte[] generatedMac = MACOperations.generate(data, key, algorithm);

            // Apply same truncation as used in generation
            int truncationBytes = getTruncationBytes();
            if (truncationBytes > 0 && truncationBytes < generatedMac.length) {
                byte[] truncatedMac = new byte[truncationBytes];
                System.arraycopy(generatedMac, 0, truncatedMac, 0, truncationBytes);
                generatedMac = truncatedMac;
            }

            // Verify: compare generated MAC with provided MAC
            boolean valid = (mac.length == generatedMac.length) &&
                           java.util.Arrays.equals(mac, generatedMac);

            // Display result
            if (valid) {
                mainController.showInfo("MAC Verification",
                    "✓ MAC VALID\n\n" +
                    "The MAC is correct and the data has not been modified.\n" +
                    "Algorithm: " + algorithm +
                    (truncationBytes > 0 ? "\nTruncation: " + truncationBytes + " bytes" : ""));
            } else {
                mainController.showError("MAC Verification",
                    "✗ MAC INVALID\n\n" +
                    "The MAC does not match the data. The data may have been modified " +
                    "or the wrong key was used.\n" +
                    "Algorithm: " + algorithm +
                    (truncationBytes > 0 ? "\nTruncation: " + truncationBytes + " bytes" : ""));
            }

            mainController.updateStatus("MAC " + (valid ? "VALID" : "INVALID") + " - " + algorithm);

            // Add to history
            OperationHistory.getInstance().addOperation(
                "Authentication",
                "Verify MAC - " + algorithm,
                "MAC: " + mac.length + " bytes, Key: " + (key.length * 8) + " bits",
                "Result: " + (valid ? "VALID ✓" : "INVALID ✗")
            );

        } catch (IllegalArgumentException e) {
            mainController.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            mainController.showError("Verification Error",
                "Error verifying MAC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update key info label based on selected algorithm
     */
    private void updateKeyInfo(String algorithm) {
        String expectedSize = MACOperations.getExpectedKeySize(algorithm);
        macKeyInfoLabel.setText("Expected key size: " + expectedSize);
    }

    /**
     * Update truncation combo to default value based on algorithm
     */
    private void updateDefaultTruncation(String algorithm) {
        String defaultValue;

        switch (algorithm) {
            case "HMAC-SHA1":
                defaultValue = "0 (full)";  // 20 bytes
                break;
            case "HMAC-SHA256":
                defaultValue = "0 (full)";  // 32 bytes
                break;
            case "HMAC-SHA384":
                defaultValue = "0 (full)";  // 48 bytes
                break;
            case "HMAC-SHA512":
                defaultValue = "0 (full)";  // 64 bytes
                break;
            case "CMAC-AES":
                defaultValue = "0 (full)";  // 16 bytes
                break;
            case "CMAC-3DES":
                defaultValue = "8";  // 8 bytes
                break;
            case "CBC-MAC-DES":
                defaultValue = "4";  // 4 bytes (banking standard)
                break;
            case "CBC-MAC-3DES":
                defaultValue = "4";  // 4 bytes (banking standard, BP-Tools style)
                break;
            case "CBC-MAC-AES":
                defaultValue = "8";  // 8 bytes
                break;
            case "ISO-9797-1-ALG1":
                defaultValue = "4";  // 4 bytes (standard)
                break;
            case "ANSI-X9.9":
                defaultValue = "4";  // 4 bytes (banking standard)
                break;
            case "ANSI-X9.19":
                defaultValue = "4";  // 4 bytes (banking standard)
                break;
            case "AS2805.4.1":
                defaultValue = "4";  // 4 bytes (banking standard)
                break;
            case "Retail-MAC-DES":
                defaultValue = "4";  // 4 bytes (banking standard)
                break;
            case "Retail-MAC-3DES":
                defaultValue = "4";  // 4 bytes (banking standard)
                break;
            default:
                defaultValue = "0 (full)";
                break;
        }

        macTruncationCombo.setValue(defaultValue);
    }

    /**
     * Get truncation value in bytes from combo (0 = full MAC)
     */
    private int getTruncationBytes() {
        String value = macTruncationCombo.getValue();
        if (value == null || value.startsWith("0")) {
            return 0;  // Full MAC, no truncation
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
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
}
