package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.function.Supplier;

/**
 * Symmetric encryption and decryption of the cipher screen's shared input area: block ciphers
 * with their modes and paddings, the stream ciphers (ChaCha20, Salsa20) and the AEAD ones
 * (GCM, ChaCha20-Poly1305, XChaCha20-Poly1305), whose results show ciphertext and tag apart.
 * The key comes from the manual field or from a Key Lab entry. It remembers the last AEAD
 * result for the Shelf package and warns when an IV/nonce is reused with the same key.
 */
final class SymmetricCipherCoordinator {

    /** The controls it reads, injected into CipherController from cipher.fxml. */
    record View(ComboBox<String> algorithm,
            ComboBox<String> mode,
            ComboBox<String> padding,
            ComboBox<String> keySource,
            ComboBox<String> hsmKey,
            TextField key,
            TextField iv,
            TextField tag,
            TextField aad,
            TextArea input,
            TextArea output) {
    }

    private final ComboBox<String> symmetricAlgorithmCombo;
    private final ComboBox<String> cipherModeCombo;
    private final ComboBox<String> paddingCombo;
    private final ComboBox<String> symKeySourceCombo;
    private final ComboBox<String> symHsmKeyCombo;
    private final TextField symmetricKeyField;
    private final TextField ivField;
    private final TextField gcmTagField;
    private final TextField aadField;
    private final TextArea cipherInputArea;
    private final TextArea cipherOutputArea;
    private final Supplier<StatusReporter> reporter;
    /** The toolbar's format selectors, handed to the controller after the panel is built. */
    private final Supplier<ComboBox<String>> inputFormat;
    private final Supplier<ComboBox<String>> outputFormat;

    SymmetricCipherCoordinator(View view, Supplier<StatusReporter> reporter,
            Supplier<ComboBox<String>> inputFormat, Supplier<ComboBox<String>> outputFormat) {
        this.symmetricAlgorithmCombo = view.algorithm();
        this.cipherModeCombo = view.mode();
        this.paddingCombo = view.padding();
        this.symKeySourceCombo = view.keySource();
        this.symHsmKeyCombo = view.hsmKey();
        this.symmetricKeyField = view.key();
        this.ivField = view.iv();
        this.gcmTagField = view.tag();
        this.aadField = view.aad();
        this.cipherInputArea = view.input();
        this.cipherOutputArea = view.output();
        this.reporter = reporter;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private ComboBox<String> inputFormat() {
        return inputFormat.get();
    }

    private ComboBox<String> outputFormat() {
        return outputFormat.get();
    }

    // Last AEAD encryption components, kept separately from the rendered result.
    private String lastAeadCiphertext;
    private String lastAeadTag;
    private final java.util.Set<String> usedAeadNonces = new java.util.HashSet<>();

    private String getHsmKeyId() {
        if (symKeySourceCombo != null && "Simulated HSM".equals(symKeySourceCombo.getValue())) {
            String keyId = symHsmKeyCombo.getValue();
            if (keyId == null || keyId.isEmpty()) {
                throw new IllegalArgumentException("Please select a key from the Lab Cache");
            }
            var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(keyId);
            if (km != null && !km.hasKeyMaterial()) {
                throw new IllegalStateException("Selected key material is not available (metadata-only reference). Please re-import or regenerate the key bytes.");
            }
            return keyId;
        }
        return null;
    }

    private byte[] getManualSymmetricKey() {
        String keyHex = symmetricKeyField.getText().trim();
        if (keyHex.isEmpty()) {
            throw new IllegalArgumentException("Please enter symmetric key in hexadecimal");
        }
        return DataConverter.hexToBytes(keyHex);
    }

    /**
     * Handle symmetric encryption
     */
    void handleSymmetricEncrypt() {
        lastAeadCiphertext = null;
        lastAeadTag = null;
        if (reporter() != null && !reporter().checkPreflightReadiness("Symmetric Cipher", true)) {
            return;
        }
        try {
            // Get inputs
            byte[] plaintext = getInputDataAsBytes();
            if (plaintext == null || plaintext.length == 0) {
                reporter().showError("Input Error", "Please enter data to encrypt");
                return;
            }

            String algorithm = symmetricAlgorithmCombo.getValue();
            String mode = cipherModeCombo.getValue();
            String padding = paddingCombo.getValue();

            // Get key
            String hsmKeyId = getHsmKeyId();
            byte[] manualKey = hsmKeyId == null ? getManualSymmetricKey() : null;

            // Handle stream ciphers separately
            if (algorithm.equals("Salsa20")) {
                handleSalsa20Encrypt(plaintext, hsmKeyId, manualKey);
                return;
            } else if (algorithm.equals("ChaCha20")) {
                handleChaCha20Encrypt(plaintext, hsmKeyId, manualKey);
                return;
            } else if (algorithm.equals("ChaCha20-Poly1305")) {
                handleChaCha20Poly1305Encrypt(plaintext, hsmKeyId, manualKey);
                return;
            } else if (algorithm.equals("XChaCha20-Poly1305")) {
                handleXChaCha20Poly1305Encrypt(plaintext, hsmKeyId, manualKey);
                return;
            }

            // Get IV if required for block ciphers
            byte[] iv = null;
            if (SymmetricCipher.requiresIV(mode)) {
                String ivHex = ivField.getText().trim();
                if (ivHex.isEmpty()) {
                    reporter().showError("IV Error",
                            mode + " mode requires an Initialization Vector (IV)");
                    return;
                }
                iv = DataConverter.hexToBytes(ivHex);
            }

            // Get AAD if required for AEAD modes
            byte[] aadBytes = null;
            if (aadField != null && !aadField.getText().isEmpty() && !aadField.isDisabled()) {
                String aadText = aadField.getText().trim();
                try {
                    // Try Hex firs
                    aadBytes = DataConverter.hexToBytes(aadText);
                } catch (Exception e) {
                    // Fallback to ASCII bytes (useful for pasting JWE Header string directly)
                    aadBytes = aadText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                }
            }

            warnIfNonceReused(algorithm, mode, hsmKeyId, manualKey, iv);

            // Encrypt with block cipher
            byte[] ciphertext;
            if (hsmKeyId != null) {
                ciphertext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().encryptSymmetric(hsmKeyId, plaintext, algorithm, mode, padding, iv, aadBytes);
            } else {
                ciphertext = SymmetricCipher.encrypt(plaintext, manualKey, algorithm, mode, padding, iv, aadBytes);
            }

            // Special handling for GCM - extract and show TAG separately
            if (mode.equalsIgnoreCase("GCM")) {
                displayGCMResult(ciphertext, true);
            } else {
                // Display normal resul
                setOutputData(ciphertext);
            }

            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            if (symmetricKeyField != null) {
                details.put("Key Size", (symmetricKeyField.getText().trim().length() * 4) + " bits");
            }
            reporter().publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext).output(ciphertext).details(details)
                    .status(String.format("Encrypted using %s/%s/%s", algorithm, mode, padding)).build());

        } catch (IllegalArgumentException e) {
            reporter().showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            reporter().showError("Encryption Error",
                    "Error encrypting data: " + e.getMessage());
        }
    }

    private void warnIfNonceReused(String algorithm, String mode, String hsmKeyId, byte[] manualKey, byte[] iv) {
        boolean aead = "GCM".equalsIgnoreCase(mode)
                || "ChaCha20-Poly1305".equals(algorithm)
                || "XChaCha20-Poly1305".equals(algorithm);
        if (!aead || iv == null) return;
        try {
            byte[] keyToHash = hsmKeyId != null ? hsmKeyId.getBytes(java.nio.charset.StandardCharsets.UTF_8) : manualKey;
            byte[] fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(
                    java.nio.ByteBuffer.allocate(keyToHash.length + iv.length).put(keyToHash).put(iv).array());
            String id = DataConverter.bytesToHex(fingerprint);
            if (!usedAeadNonces.add(id)) {
                reporter().showInfo("Nonce reuse warning",
                        "This IV/nonce has already been used with the same key in this session. Generate a fresh value before encrypting.");
            }
        } catch (java.security.NoSuchAlgorithmException ignored) {
            // SHA-256 is mandatory in the Java runtime; no warning is preferable to blocking encryption.
        }
    }

    /**
     * Handle symmetric decryption
     */
    void handleSymmetricDecrypt() {
        if (reporter() != null && !reporter().checkPreflightReadiness("Symmetric Cipher", false)) {
            return;
        }
        try {
            // Get inputs
            byte[] ciphertext = getInputDataAsBytes();
            if (ciphertext == null || ciphertext.length == 0) {
                reporter().showError("Input Error", "Please enter data to decrypt");
                return;
            }

            String algorithm = symmetricAlgorithmCombo.getValue();
            String mode = cipherModeCombo.getValue();
            String padding = paddingCombo.getValue();

            // Get key
            // Checked apart so a missing key reads as such, not as malformed input.
            String hsmKeyId;
            byte[] manualKey;
            try {
                hsmKeyId = getHsmKeyId();
                manualKey = hsmKeyId == null ? getManualSymmetricKey() : null;
            } catch (IllegalArgumentException e) {
                reporter().showError("Validation Error", e.getMessage());
                return;
            }

            // Handle stream ciphers separately
            if (algorithm.equals("Salsa20")) {
                handleSalsa20Decrypt(ciphertext, hsmKeyId, manualKey);
                return;
            } else if (algorithm.equals("ChaCha20")) {
                handleChaCha20Decrypt(ciphertext, hsmKeyId, manualKey);
                return;
            } else if (algorithm.equals("ChaCha20-Poly1305")) {
                handleChaCha20Poly1305Decrypt(ciphertext, hsmKeyId, manualKey);
                return;
            } else if (algorithm.equals("XChaCha20-Poly1305")) {
                handleXChaCha20Poly1305Decrypt(ciphertext, hsmKeyId, manualKey);
                return;
            }

            // Get IV if required for block ciphers
            byte[] iv = null;
            if (SymmetricCipher.requiresIV(mode)) {
                String ivHex = ivField.getText().trim();
                if (ivHex.isEmpty()) {
                    reporter().showError("IV Error",
                            mode + " mode requires an Initialization Vector (IV)");
                    return;
                }
                iv = DataConverter.hexToBytes(ivHex);
            }

            // Get AAD if required for AEAD modes
            byte[] aadBytes = null;
            if (aadField != null && !aadField.getText().isEmpty() && !aadField.isDisabled()) {
                String aadText = aadField.getText().trim();
                try {
                    // Try Hex firs
                    aadBytes = DataConverter.hexToBytes(aadText);
                } catch (Exception e) {
                    // Fallback to ASCII bytes (useful for pasting JWE Header string directly)
                    aadBytes = aadText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                }
            }

            // Decrypt with block cipher
            byte[] plaintext;

            // Handle GCM Tag for decryption
            if (mode.equalsIgnoreCase("GCM") && gcmTagField != null && !gcmTagField.getText().trim().isEmpty()) {
                String tagHex = gcmTagField.getText().trim();
                byte[] tag = DataConverter.hexToBytes(tagHex);
                if (tag.length != 16) {
                    reporter().showError("Tag Error", "GCM Tag must be 16 bytes (32 hex chars)");
                    return;
                }

                // Append tag to ciphertext if provided separately
                byte[] combined = new byte[ciphertext.length + tag.length];
                System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
                System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);

                if (hsmKeyId != null) {
                    plaintext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().decryptSymmetric(hsmKeyId, combined, algorithm, mode, padding, iv, aadBytes);
                } else {
                    plaintext = SymmetricCipher.decrypt(combined, manualKey, algorithm, mode, padding, iv, aadBytes);
                }
            } else {
                if (hsmKeyId != null) {
                    plaintext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().decryptSymmetric(hsmKeyId, ciphertext, algorithm, mode, padding, iv, aadBytes);
                } else {
                    plaintext = SymmetricCipher.decrypt(ciphertext, manualKey, algorithm, mode, padding, iv, aadBytes);
                }
            }

            // Special handling for GCM - show TAG verification message
            String enriched = null;
            if (mode.equalsIgnoreCase("GCM")) {
                enriched = displayGCMResult(plaintext, false);
            } else {
                // Display normal resul
                setOutputData(plaintext);
            }

            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            OperationResult.Builder b = OperationResult.forOperation("Symmetric Decrypt")
                    .input(ciphertext).output(plaintext).details(details)
                    .status(String.format("Decrypted using %s/%s/%s", algorithm, mode, padding));
            if (enriched != null) b.enrichedOutput(enriched);
            reporter().publish(b.build());

        } catch (IllegalArgumentException e) {
            reporter().showError(e, "Validation Error", "cipherInputArea");
        } catch (javax.crypto.AEADBadTagException e) {
            reporter().showError(e, "Authentication Error", "gcmTagField");
        } catch (Exception e) {
            reporter().showError(e, "Decryption Error", "cipherInputArea");
        }
    }

    /**
     * Get input data as bytes
     */
    private byte[] getInputDataAsBytes() {
        String input = cipherInputArea.getText().trim();
        if (input.isEmpty()) {
            return null;
        }

        String format = inputFormat().getValue();
        if (format == null)
            format = "Hexadecimal";

        com.cryptocarver.util.InputValidator.validateInput(input, format);

        try {
            switch (format) {
                case "Hexadecimal":
                    return DataConverter.hexToBytes(input);
                case "Base64":
                    return org.apache.commons.codec.binary.Base64.decodeBase64(input);
                case "Text (UTF-8)":
                    return input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                case "Binary":
                    return DataConverter.binaryToBytes(input);
                default:
                    return DataConverter.hexToBytes(input);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing input: " + e.getMessage());
        }
    }

    /**
     * Set output data
     */
    private void setOutputData(byte[] data) {
        String format = outputFormat().getValue();
        if (format == null)
            format = "Hexadecimal";

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
            case "C Array":
                output = DataConverter.bytesToCArray(data, 12);
                break;
            default:
                output = DataConverter.bytesToHex(data);
        }

        cipherOutputArea.setText(output);
    }

    /**
     * Display GCM encryption/decryption result with TAG shown separately
     * In GCM, the last 16 bytes are the authentication TAG (only for encryption)
     */
    private String displayGCMResult(byte[] data, boolean isEncryption) {
        String algorithm = symmetricAlgorithmCombo.getValue();
        String mode = cipherModeCombo.getValue();
        String label = algorithm;

        // Adjust label for AES-GCM vs Poly1305 variants
        if (mode != null && mode.equalsIgnoreCase("GCM") && !algorithm.contains("Poly1305")) {
            label = algorithm + "-GCM";
        }

        if (isEncryption) {
            // For encryption: separate ciphertext and TAG
            if (data.length < 16) {
                setOutputData(data);
                return "";
            }

            // GCM TAG is 16 bytes (128 bits) at the end
            int tagLength = 16;
            byte[] ciphertext = new byte[data.length - tagLength];
            byte[] tag = new byte[tagLength];

            System.arraycopy(data, 0, ciphertext, 0, ciphertext.length);
            System.arraycopy(data, ciphertext.length, tag, 0, tagLength);

            // Format output based on selected forma
            String format = outputFormat().getValue();
            if (format == null)
                format = "Hexadecimal";

            String ciphertextStr;
            String tagStr;
            String fullDataStr;

            switch (format) {
                case "Hexadecimal":
                    ciphertextStr = DataConverter.bytesToHex(ciphertext);
                    tagStr = DataConverter.bytesToHex(tag);
                    fullDataStr = DataConverter.bytesToHex(data);
                    break;
                case "Base64":
                    ciphertextStr = org.apache.commons.codec.binary.Base64.encodeBase64String(ciphertext);
                    tagStr = org.apache.commons.codec.binary.Base64.encodeBase64String(tag);
                    fullDataStr = org.apache.commons.codec.binary.Base64.encodeBase64String(data);
                    break;
                default:
                    ciphertextStr = DataConverter.bytesToHex(ciphertext);
                    tagStr = DataConverter.bytesToHex(tag);
                    fullDataStr = DataConverter.bytesToHex(data);
            }

            lastAeadCiphertext = ciphertextStr;
            lastAeadTag = tagStr;

            // Build formatted output for ENCRYPTION
            StringBuilder output = new StringBuilder();
            output.append("=== ").append(label).append(" ENCRYPTION RESULT ===\n\n");
            output.append("CIPHERTEXT (").append(ciphertext.length).append(" bytes):\n");
            output.append(ciphertextStr).append("\n\n");
            output.append("AUTHENTICATION TAG (").append(tagLength).append(" bytes):\n");
            output.append(tagStr).append("\n\n");
            output.append("FULL OUTPUT (Ciphertext + TAG, ").append(data.length).append(" bytes):\n");
            output.append(fullDataStr).append("\n\n");
            output.append("ℹ️  Note: For decryption, enter the Ciphertext and TAG separately.\n");
            output.append("ℹ️  The TAG provides authentication - it must match exactly.");

            cipherOutputArea.setText(output.toString());
            return output.toString();

        } else {
            // For decryption: just show the plaintext with verification message
            String format = outputFormat().getValue();
            if (format == null)
                format = "Hexadecimal";

            String plaintextStr;
            switch (format) {
                case "Hexadecimal":
                    plaintextStr = DataConverter.bytesToHex(data);
                    break;
                case "Base64":
                    plaintextStr = org.apache.commons.codec.binary.Base64.encodeBase64String(data);
                    break;
                case "Text (UTF-8)":
                    plaintextStr = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Binary":
                    plaintextStr = DataConverter.bytesToBinary(data);
                    break;
                case "C Array":
                    plaintextStr = DataConverter.bytesToCArray(data, 12);
                    break;
                default:
                    plaintextStr = DataConverter.bytesToHex(data);
            }

            // Build formatted output for DECRYPTION
            StringBuilder output = new StringBuilder();
            output.append("=== ").append(label).append(" DECRYPTION RESULT ===\n\n");
            output.append("PLAINTEXT (").append(data.length).append(" bytes):\n");
            output.append(plaintextStr).append("\n\n");
            output.append("✅ TAG VERIFIED - Integrity Confirmed\n");

            cipherOutputArea.setText(output.toString());
            return output.toString();
        }
    }

    // Helpers to support Salsa20 and ChaCha20
    private void handleChaCha20Encrypt(byte[] plaintext, String hsmKeyId, byte[] manualKey) {
        try {
            String algorithm = "ChaCha20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] ciphertext;
            if (hsmKeyId != null) {
                ciphertext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().encryptChaCha20(hsmKeyId, plaintext, iv);
            } else {
                ciphertext = SymmetricCipher.encryptChaCha20(plaintext, manualKey, iv);
            }
            setOutputData(ciphertext);
            reporter().updateStatus("Encrypted using ChaCha20");
            reporter().publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(ciphertext)
                    .detail("Algorithm", "ChaCha20")
                    .status("Encrypted using ChaCha20")
                    .build());

        } catch (Exception e) {
            reporter().showError("Encryption Error", e.getMessage());
        }
    }

    private void handleChaCha20Decrypt(byte[] ciphertext, String hsmKeyId, byte[] manualKey) {
        try {
            String algorithm = "ChaCha20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] plaintext;
            if (hsmKeyId != null) {
                plaintext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().decryptChaCha20(hsmKeyId, ciphertext, iv);
            } else {
                plaintext = SymmetricCipher.decryptChaCha20(ciphertext, manualKey, iv);
            }
            setOutputData(plaintext);
            reporter().updateStatus("Decrypted using ChaCha20");
            reporter().publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(ciphertext)
                    .output(plaintext)
                    .detail("Algorithm", "ChaCha20")
                    .status("Decrypted using ChaCha20")
                    .build());
        } catch (Exception e) {
            reporter().showError("Decryption Error", e.getMessage());
        }
    }

    /** Key Lab keys cannot run Salsa20: the lab only offers the generic and ChaCha20 operations. */
    private static byte[] salsa20Key(String hsmKeyId, byte[] manualKey) {
        if (hsmKeyId != null) {
            throw new IllegalArgumentException("Salsa20 is not available for Key Lab keys; use a manual key");
        }
        return manualKey;
    }

    private void handleSalsa20Encrypt(byte[] plaintext, String hsmKeyId, byte[] manualKey) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] ciphertext = SymmetricCipher.encryptSalsa20(plaintext, salsa20Key(hsmKeyId, manualKey), iv);
            setOutputData(ciphertext);
            reporter().updateStatus("Encrypted using Salsa20");
            reporter().publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(ciphertext)
                    .detail("Algorithm", "Salsa20")
                    .status("Encrypted using Salsa20")
                    .build());

        } catch (Exception e) {
            reporter().showError("Encryption Error", e.getMessage());
        }
    }

    private void handleChaCha20Poly1305Encrypt(byte[] plaintext, String hsmKeyId, byte[] manualKey) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());

            byte[] combined;
            if (hsmKeyId != null) {
                combined = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().encryptChaCha20Poly1305(hsmKeyId, plaintext, iv);
            } else {
                combined = SymmetricCipher.encryptChaCha20Poly1305(plaintext, manualKey, iv);
            }

            // Split for display (last 16 bytes are tag)
            String enriched = displayGCMResult(combined, true);
            reporter().updateStatus("Encrypted using ChaCha20-Poly1305");
            reporter().publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(combined)
                    .enrichedOutput(enriched)
                    .detail("Algorithm", "ChaCha20-Poly1305")
                    .status("Encrypted using ChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            reporter().showError("Encryption Error", e.getMessage());
        }
    }

    private void handleSalsa20Decrypt(byte[] ciphertext, String hsmKeyId, byte[] manualKey) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] plaintext = SymmetricCipher.decryptSalsa20(ciphertext, salsa20Key(hsmKeyId, manualKey), iv);
            setOutputData(plaintext);
            reporter().updateStatus("Decrypted using Salsa20");
            reporter().publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(ciphertext)
                    .output(plaintext)
                    .detail("Algorithm", "Salsa20")
                    .status("Decrypted using Salsa20")
                    .build());
        } catch (Exception e) {
            reporter().showError("Decryption Error", e.getMessage());
        }
    }

    private void handleChaCha20Poly1305Decrypt(byte[] ciphertext, String hsmKeyId, byte[] manualKey) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());

            // Get Auth Tag - REQUIRED for Poly1305 decryption
            String tagHex = gcmTagField.getText().trim();
            if (tagHex.isEmpty()) {
                throw new IllegalArgumentException("ChaCha20-Poly1305 requires an Auth Tag for decryption");
            }
            byte[] tag = DataConverter.hexToBytes(tagHex);

            // Combine ciphertext + tag (SymmetricCipher expects combined)
            byte[] combined = SymmetricCipher.combineChaCha20CiphertextAndTag(ciphertext, tag);

            byte[] plaintext;
            if (hsmKeyId != null) {
                plaintext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().decryptChaCha20Poly1305(hsmKeyId, combined, iv);
            } else {
                plaintext = SymmetricCipher.decryptChaCha20Poly1305(combined, manualKey, iv);
            }

            displayGCMResult(plaintext, false);
            reporter().updateStatus("Decrypted using ChaCha20-Poly1305");
            reporter().publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(combined)
                    .output(plaintext)
                    .detail("Algorithm", "ChaCha20-Poly1305")
                    .status("Decrypted using ChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            reporter().showError("Decryption Error", e.getMessage());
        }
    }

    // --- XChaCha20-Poly1305 Handlers ---

    private void handleXChaCha20Poly1305Encrypt(byte[] plaintext, String hsmKeyId, byte[] manualKey) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());

            // XChaCha20-Poly1305 Encryption
            byte[] combined;
            if (hsmKeyId != null) {
                combined = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().encryptXChaCha20Poly1305(hsmKeyId, plaintext, iv);
            } else {
                combined = SymmetricCipher.encryptXChaCha20Poly1305(plaintext, manualKey, iv);
            }

            // Split for display (last 16 bytes are tag)
            String enriched = displayGCMResult(combined, true);
            reporter().updateStatus("Encrypted using XChaCha20-Poly1305");
            reporter().publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(combined)
                    .enrichedOutput(enriched)
                    .detail("Algorithm", "XChaCha20-Poly1305")
                    .status("Encrypted using XChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            reporter().showError("Encryption Error", e.getMessage());
        }
    }

    private void handleXChaCha20Poly1305Decrypt(byte[] ciphertext, String hsmKeyId, byte[] manualKey) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());

            // Get Auth Tag
            String tagHex = gcmTagField.getText().trim();
            if (tagHex.isEmpty()) {
                throw new IllegalArgumentException("XChaCha20-Poly1305 requires an Auth Tag for decryption");
            }
            byte[] tag = DataConverter.hexToBytes(tagHex);

            // Combine ciphertext + tag
            byte[] combined = SymmetricCipher.combineChaCha20CiphertextAndTag(ciphertext, tag);

            byte[] plaintext;
            if (hsmKeyId != null) {
                plaintext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().decryptXChaCha20Poly1305(hsmKeyId, combined, iv);
            } else {
                plaintext = SymmetricCipher.decryptXChaCha20Poly1305(combined, manualKey, iv);
            }

            displayGCMResult(plaintext, false);
            reporter().updateStatus("Decrypted using XChaCha20-Poly1305");
            reporter().publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(combined)
                    .output(plaintext)
                    .detail("Algorithm", "XChaCha20-Poly1305")
                    .status("Decrypted using XChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            reporter().showError("Decryption Error", e.getMessage());
        }
    }

    com.cryptocarver.model.ShelfPackage createAuthenticatedCipherShelfPackage() {
        if (lastAeadCiphertext == null || lastAeadTag == null || symmetricAlgorithmCombo == null) return null;
        String algorithm = symmetricAlgorithmCombo.getValue();
        String mode = cipherModeCombo == null ? "" : cipherModeCombo.getValue();
        boolean supported = "GCM".equalsIgnoreCase(mode)
                || "ChaCha20-Poly1305".equalsIgnoreCase(algorithm)
                || "XChaCha20-Poly1305".equalsIgnoreCase(algorithm);
        if (!supported || ivField == null || ivField.getText().isBlank()) return null;
        java.util.Map<String, String> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("ciphertext", lastAeadCiphertext);
        artifacts.put("algorithm", algorithm);
        artifacts.put("mode", mode);
        artifacts.put("padding", paddingCombo == null || paddingCombo.getValue() == null ? "NoPadding" : paddingCombo.getValue());
        String selectedFormat = outputFormat() == null || outputFormat().getValue() == null
                ? "Hexadecimal" : outputFormat().getValue();
        // The rendered AEAD splitter emits hexadecimal for unsupported display
        // formats (Text/Binary/C Array), so persist the actual representation.
        artifacts.put("format", "Base64".equals(selectedFormat) || "Hexadecimal".equals(selectedFormat)
                ? selectedFormat : "Hexadecimal");
        artifacts.put("authTag", "Base64".equals(artifacts.get("format"))
                ? DataConverter.bytesToHex(org.apache.commons.codec.binary.Base64.decodeBase64(lastAeadTag))
                : lastAeadTag);
        artifacts.put("nonce", ivField.getText().trim());
        if (aadField != null && !aadField.getText().isBlank()) artifacts.put("aad", aadField.getText().trim());
        return com.cryptocarver.model.ShelfPackage.authenticatedCipher(artifacts);
    }
}
