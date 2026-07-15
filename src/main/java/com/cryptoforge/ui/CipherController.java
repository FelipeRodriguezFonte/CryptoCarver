package com.cryptoforge.ui;

import com.cryptoforge.crypto.AsymmetricCipher;
import com.cryptoforge.crypto.AsymmetricKeyOperations;
import com.cryptoforge.crypto.SymmetricCipher;
import com.cryptoforge.crypto.StreamingCipher;
import com.cryptoforge.model.OperationResult;
import com.cryptoforge.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Controller for Cipher operations
 */
public class CipherController {

    private final TextArea inputArea;
    private final TextArea outputArea;
    private final ComboBox<String> inputFormatCombo;
    private final ComboBox<String> outputFormatCombo;
    private final StatusReporter statusReporter;

    // Symmetric cipher UI components
    private ComboBox<String> symmetricAlgorithmCombo;
    private ComboBox<String> cipherModeCombo;
    private ComboBox<String> paddingCombo;
    private ComboBox<String> symKeySourceCombo;
    private ComboBox<String> symHsmKeyCombo;
    private TextField symmetricKeyField;
    private TextField ivField;
    private TextField gcmTagField;
    private TextField aadField; // Added AAD field

    // Streaming file cipher UI components
    private ComboBox<String> fileCipherAlgorithmCombo;
    private TextField fileCipherSourceField;
    private TextField fileCipherDestinationField;
    private TextField fileCipherTagField;
    private TextField fileCipherKeyField;
    private TextField fileCipherNonceField;
    private TextField fileCipherAadField;
    private TextArea fileCipherResultArea;

    // Asymmetric cipher UI components
    private ComboBox<String> rsaPaddingCombo;
    private ComboBox<String> asymmetricInputFormatCombo;
    private ComboBox<String> asymmetricOutputFormatCombo;
    private PublicKey currentPublicKey;
    private PrivateKey currentPrivateKey;

    // Key Input Areas (Manual Loading)
    private TextArea publicKeyArea;
    private TextArea privateKeyArea;
    private final java.util.Set<String> usedAeadNonces = new java.util.HashSet<>();

    public CipherController(StatusReporter statusReporter,
            TextArea inputArea,
            TextArea outputArea,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo) {
        this(statusReporter, inputArea, outputArea, inputFormatCombo, outputFormatCombo, null, null);
    }

    public CipherController(StatusReporter statusReporter,
            TextArea inputArea,
            TextArea outputArea,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo,
            TextArea publicKeyArea,
            TextArea privateKeyArea) {
        this.statusReporter = statusReporter;
        this.inputArea = inputArea;
        this.outputArea = outputArea;
        this.inputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;
        this.publicKeyArea = publicKeyArea;
        this.privateKeyArea = privateKeyArea;
    }

    /**
     * Set public key directly
     */
    public void setPublicKey(PublicKey key) {
        this.currentPublicKey = key;
        statusReporter.updateStatus("Public key loaded from memory");
    }

    /**
     * Set private key directly
     */
    public void setPrivateKey(PrivateKey key) {
        this.currentPrivateKey = key;
        statusReporter.updateStatus("Private key loaded from memory");
    }

    /**
     * Handle manual Public Key loading
     */
    public void handleLoadPublicKey() {
        if (publicKeyArea == null)
            return;

        String keyText = publicKeyArea.getText().trim();
        if (keyText.isEmpty()) {
            statusReporter.showError("Key Error", "Please enter a public key (PEM or Hex)");
            return;
        }

        try {
            // Try PEM format first
            if (keyText.contains("-----BEGIN PUBLIC KEY-----")) {
                currentPublicKey = AsymmetricKeyOperations.importPublicKeyPEM(keyText);
                statusReporter.updateStatus("Public Key loaded from PEM");
            } else {
                // Try Hex format (requires reconstructing key spec, which is complex for
                // generic Hex)
                // For now, let's assume if it's not PEM, it might be Hex of DER encoding
                // This simplifcation assumes DER encoded key in Hex
                try {
                    byte[] keyBytes = DataConverter.hexToBytes(keyText);
                    java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
                    java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA", "BC");
                    currentPublicKey = kf.generatePublic(spec);
                    statusReporter.updateStatus("Public Key loaded from Hex (DER)");
                } catch (Exception e) {
                    // Try converting from Base64 if Hex fails, just in case
                    try {
                        byte[] keyBytes = org.apache.commons.codec.binary.Base64.decodeBase64(keyText);
                        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(
                                keyBytes);
                        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA", "BC");
                        currentPublicKey = kf.generatePublic(spec);
                        statusReporter.updateStatus("Public Key loaded from Base64 (DER)");
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Unknown key format. Please use PEM or Hex/Base64 DER.");
                    }
                }
            }
        } catch (Exception e) {
            statusReporter.showError("Load Error", "Failed to load Public Key: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle manual Private Key loading
     */
    public void handleLoadPrivateKey() {
        if (privateKeyArea == null)
            return;

        String keyText = privateKeyArea.getText().trim();
        if (keyText.isEmpty()) {
            statusReporter.showError("Key Error", "Please enter a private key (PEM or Hex)");
            return;
        }

        try {
            // Try PEM format first
            if (keyText.contains("-----BEGIN PRIVATE KEY-----")) {
                currentPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEM(keyText);
                statusReporter.updateStatus("Private Key loaded from PEM");
            } else {
                // Try Hex/Base64 DER
                try {
                    byte[] keyBytes = DataConverter.hexToBytes(keyText);
                    java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
                    java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA", "BC");
                    currentPrivateKey = kf.generatePrivate(spec);
                    statusReporter.updateStatus("Private Key loaded from Hex (DER)");
                } catch (Exception e) {
                    try {
                        byte[] keyBytes = org.apache.commons.codec.binary.Base64.decodeBase64(keyText);
                        java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(
                                keyBytes);
                        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA", "BC");
                        currentPrivateKey = kf.generatePrivate(spec);
                        statusReporter.updateStatus("Private Key loaded from Base64 (DER)");
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Unknown key format. Please use PEM or Hex/Base64 DER.");
                    }
                }
            }
        } catch (Exception e) {
            statusReporter.showError("Load Error", "Failed to load Private Key: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Set symmetric algorithm ComboBox
     */
    public void setSymmetricAlgorithmCombo(ComboBox<String> combo) {
        this.symmetricAlgorithmCombo = combo;
        symmetricAlgorithmCombo.getItems().addAll(SymmetricCipher.SUPPORTED_ALGORITHMS);
        symmetricAlgorithmCombo.setValue("AES-256");

        // Add listener to handle stream ciphers (Salsa20, ChaCha20-Poly1305)
        symmetricAlgorithmCombo.setOnAction(e -> updateStreamCipherState());
    }

    /** Connects the independent file-cipher panel. */
    public void setFileCipherFields(ComboBox<String> algorithmCombo, TextField sourceField, TextField destinationField,
            TextField tagField, TextField keyField, TextField nonceField, TextField aadField, TextArea resultArea) {
        this.fileCipherAlgorithmCombo = algorithmCombo;
        this.fileCipherSourceField = sourceField;
        this.fileCipherDestinationField = destinationField;
        this.fileCipherTagField = tagField;
        this.fileCipherKeyField = keyField;
        this.fileCipherNonceField = nonceField;
        this.fileCipherAadField = aadField;
        this.fileCipherResultArea = resultArea;
        algorithmCombo.getItems().setAll("AES-256-GCM", "AES-256-CTR", "AES-256-CBC", "ChaCha20-Poly1305");
        algorithmCombo.setValue("AES-256-GCM");
    }

    public void handleFileCipherEncrypt() {
        executeFileCipher(true);
    }

    public void handleFileCipherDecrypt() {
        executeFileCipher(false);
    }

    public void chooseFileCipherSource() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select source file");
        java.io.File selected = chooser.showOpenDialog(null);
        if (selected != null) fileCipherSourceField.setText(selected.getAbsolutePath());
    }

    public void chooseFileCipherDestination() {
        chooseSavePath(fileCipherDestinationField, "Save encrypted/decrypted file", "output.bin");
    }

    public void chooseFileCipherTag() {
        chooseSavePath(fileCipherTagField, "Save/load detached AEAD tag", "output.tag");
    }

    public void generateFileCipherNonce() {
        if (fileCipherAlgorithmCombo == null) return;
        String selected = fileCipherAlgorithmCombo.getValue();
        int length = selected != null && (selected.contains("GCM") || selected.startsWith("ChaCha")) ? 12 : 16;
        byte[] nonce = new byte[length];
        new java.security.SecureRandom().nextBytes(nonce);
        fileCipherNonceField.setText(DataConverter.bytesToHex(nonce));
        statusReporter.updateStatus("Generated fresh " + length + "-byte IV/nonce for file cipher");
    }

    private void executeFileCipher(boolean encrypt) {
        try {
            FileCipherParameters parameters = readFileCipherParameters();
            java.nio.file.Path source = java.nio.file.Path.of(fileCipherSourceField.getText().trim());
            java.nio.file.Path destination = java.nio.file.Path.of(fileCipherDestinationField.getText().trim());
            if (!java.nio.file.Files.isRegularFile(source)) throw new IllegalArgumentException("Source file does not exist or is not a regular file");
            if (source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Source and output file must be different");
            }
            java.nio.file.Path tag = parameters.aead ? requiredPath(fileCipherTagField.getText(), "Tag file path") : null;
            if (!encrypt && parameters.aead && !java.nio.file.Files.isRegularFile(tag)) {
                throw new IllegalArgumentException("Detached tag file does not exist");
            }
            if (encrypt && parameters.aead) warnIfNonceReused(parameters.algorithm, parameters.mode, parameters.key, parameters.nonce);
            StreamingCipher.Result result = encrypt
                    ? StreamingCipher.encrypt(source, destination, parameters.key, parameters.algorithm, parameters.mode,
                            parameters.nonce, parameters.aad, tag, com.cryptoforge.util.ProgressMonitor.NO_OP)
                    : StreamingCipher.decrypt(source, destination, parameters.key, parameters.algorithm, parameters.mode,
                            parameters.nonce, parameters.aad, tag, com.cryptoforge.util.ProgressMonitor.NO_OP);
            String operation = encrypt ? "encrypted" : "decrypted";
            fileCipherResultArea.setText("File " + operation + " successfully\nAlgorithm: " + fileCipherAlgorithmCombo.getValue()
                    + "\nInput: " + result.inputBytes() + " bytes\nOutput: " + result.outputBytes() + " bytes"
                    + (parameters.aead ? "\nAEAD tag: " + tag : ""));
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", fileCipherAlgorithmCombo.getValue());
            details.put("Input bytes", Long.toString(result.inputBytes()));
            details.put("Output bytes", Long.toString(result.outputBytes()));
            details.put("Authenticated", Boolean.toString(result.authenticated()));
            statusReporter.publish(OperationResult.forOperation("File " + (encrypt ? "Encrypt" : "Decrypt"))
                    .details(details).status("File " + operation + " using " + fileCipherAlgorithmCombo.getValue()).build());
        } catch (Exception e) {
            statusReporter.showError("File Cipher", "Cannot process file: " + e.getMessage());
        }
    }

    private FileCipherParameters readFileCipherParameters() {
        String selected = fileCipherAlgorithmCombo.getValue();
        String algorithm = selected.startsWith("ChaCha") ? "ChaCha20-Poly1305" : "AES-256";
        String mode = selected.contains("GCM") ? "GCM" : selected.contains("CTR") ? "CTR" : selected.contains("CBC") ? "CBC" : "";
        byte[] key = requiredHex(fileCipherKeyField.getText(), "Key");
        byte[] nonce = requiredHex(fileCipherNonceField.getText(), "IV / nonce");
        byte[] aad = optionalHex(fileCipherAadField.getText(), "AAD");
        return new FileCipherParameters(algorithm, mode, key, nonce, aad, "GCM".equals(mode) || "ChaCha20-Poly1305".equals(algorithm));
    }

    private byte[] requiredHex(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return DataConverter.hexToBytes(value.replaceAll("\\s+", ""));
    }

    private byte[] optionalHex(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try { return DataConverter.hexToBytes(value.replaceAll("\\s+", "")); }
        catch (Exception e) { throw new IllegalArgumentException(label + " must be hexadecimal"); }
    }

    private java.nio.file.Path requiredPath(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return java.nio.file.Path.of(value.trim());
    }

    private void chooseSavePath(TextField target, String title, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialFileName);
        java.io.File selected = chooser.showSaveDialog(null);
        if (selected != null) target.setText(selected.getAbsolutePath());
    }

    private record FileCipherParameters(String algorithm, String mode, byte[] key, byte[] nonce, byte[] aad, boolean aead) { }

    /**
     * Set cipher mode ComboBox
     */
    public void setCipherModeCombo(ComboBox<String> combo) {
        this.cipherModeCombo = combo;
        cipherModeCombo.getItems().addAll(SymmetricCipher.SUPPORTED_MODES);
        cipherModeCombo.setValue("CBC");

        // Add listener to update IV field and GCM Tag field requirement
        // Use valueProperty listener to catch programmatic changes (e.g. Restore UI)
        cipherModeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateIVFieldState();
            updateGcmTagFieldState(); // Handles both GCM Tag and AAD
            updatePaddingFieldState();
        });

        // Also keep action handler just in case
        cipherModeCombo.setOnAction(e -> {
            updateIVFieldState();
            updateGcmTagFieldState(); // Handles both GCM Tag and AAD
            updatePaddingFieldState();
        });
    }

    /**
     * Set padding ComboBox
     */
    public void setPaddingCombo(ComboBox<String> combo) {
        this.paddingCombo = combo;
        paddingCombo.getItems().addAll(SymmetricCipher.SUPPORTED_PADDINGS);
        paddingCombo.setValue("PKCS7Padding");

        // Add listener to disable padding for modes that don't support it
        cipherModeCombo.setOnAction(e -> updatePaddingFieldState());
    }

    /**
     * Set symmetric key TextField
     */
    public void setSymmetricKeyField(TextField field) {
        this.symmetricKeyField = field;
        symmetricKeyField.setPromptText("Key (Hex) - e.g., for AES-256: 64 hex characters");
    }

    public void setSymKeySourceCombo(ComboBox<String> combo) {
        this.symKeySourceCombo = combo;
        combo.getItems().addAll("Manual Input", "Simulated HSM");
        combo.setValue("Manual Input");
        combo.setOnAction(e -> updateKeySourceVisibility());
    }

    public void setSymHsmKeyCombo(ComboBox<String> combo) {
        this.symHsmKeyCombo = combo;
        refreshHsmKeys();
    }

    private void updateKeySourceVisibility() {
        boolean isHsm = "Simulated HSM".equals(symKeySourceCombo.getValue());
        if (symmetricKeyField != null) {
            symmetricKeyField.setVisible(!isHsm);
            symmetricKeyField.setManaged(!isHsm);
        }
        if (symHsmKeyCombo != null) {
            symHsmKeyCombo.setVisible(isHsm);
            symHsmKeyCombo.setManaged(isHsm);
        }
    }

    public void refreshHsmKeys() {
        if (symHsmKeyCombo != null) {
            String current = symHsmKeyCombo.getValue();
            symHsmKeyCombo.getItems().clear();
            symHsmKeyCombo.getItems().addAll(com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().listKeyIds());
            if (current != null && symHsmKeyCombo.getItems().contains(current)) {
                symHsmKeyCombo.setValue(current);
            } else if (!symHsmKeyCombo.getItems().isEmpty()) {
                symHsmKeyCombo.setValue(symHsmKeyCombo.getItems().get(0));
            }
        }
    }

    public void saveCurrentKeyToHsm() {
        try {
            if (symmetricKeyField == null || symmetricKeyField.getText().trim().isEmpty()) {
                statusReporter.showError("Save Error", "No key to save");
                return;
            }
            byte[] keyBytes = DataConverter.hexToBytes(symmetricKeyField.getText().trim());
            String algo = symmetricAlgorithmCombo.getValue();
            if (algo == null) algo = "AES";
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, algo);
            com.cryptoforge.crypto.hsm.KeyMaterial km = com.cryptoforge.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                null, secretKey, 
                com.cryptoforge.crypto.hsm.KeyExportability.NON_EXPORTABLE, 
                java.util.Set.of(com.cryptoforge.crypto.hsm.KeyUsage.ENCRYPT, com.cryptoforge.crypto.hsm.KeyUsage.DECRYPT)
            );
            com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            statusReporter.showInfo("Success", "Key saved to Lab Cache as " + km.getId());
            refreshHsmKeys();
            symKeySourceCombo.setValue("Simulated HSM");
        } catch (Exception e) {
            statusReporter.showError("Save Error", "Failed to save key: " + e.getMessage());
        }
    }

    /**
     * Set IV TextField
     */
    public void setIVField(TextField field) {
        this.ivField = field;
        ivField.setPromptText("IV (Hex) - required for CBC, CTR, GCM, etc.");
    }

    /**
     * Generate IV based on current algorithm
     */
    public void generateIV() {
        if (ivField == null)
            return;

        String algorithm = symmetricAlgorithmCombo.getValue();
        String mode = cipherModeCombo.getValue();
        int ivLength;

        // Determine correct IV length
        if (algorithm.equals("ChaCha20") || algorithm.equals("ChaCha20-Poly1305")) {
            ivLength = 12; // 96 bits for ChaCha20 (RFC 7539 standard)
        } else if (algorithm.equals("XChaCha20-Poly1305")) {
            ivLength = 24; // 192 bits for XChaCha20
        } else if (mode.equalsIgnoreCase("GCM")) {
            ivLength = 12; // 96 bits recommended for GCM
        } else {
            ivLength = 16; // Default to 128 bits (16 bytes) for AES blocks etc.
        }

        byte[] iv = new byte[ivLength];
        new java.security.SecureRandom().nextBytes(iv);
        ivField.setText(DataConverter.bytesToHex(iv));
    }

    public void setGcmTagField(TextField field) {
        this.gcmTagField = field;
        updateGcmTagFieldState();
    }

    public void setAADField(TextField field) {
        this.aadField = field;
        aadField.setPromptText("AAD (Hex) - for GCM/Poly1305");
        updateGcmTagFieldState();
    }

    /**
     * Set RSA combos (padding and formats)
     */
    public void setRSACombos(ComboBox<String> paddingCombo,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo) {
        this.rsaPaddingCombo = paddingCombo;
        // RSA now uses the same toolbar combos
        this.asymmetricInputFormatCombo = inputFormatCombo;
        this.asymmetricOutputFormatCombo = outputFormatCombo;

        // Populate padding schemes (RSA specific)
        rsaPaddingCombo.getItems().addAll(
                "RSA/ECB/PKCS1Padding",
                "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
                "RSA/ECB/NoPadding");
        rsaPaddingCombo.setValue("RSA/ECB/PKCS1Padding");
    }

    private byte[] getSymmetricKey(com.cryptoforge.crypto.hsm.KeyUsage usage) {
        if (symKeySourceCombo != null && "Simulated HSM".equals(symKeySourceCombo.getValue())) {
            String keyId = symHsmKeyCombo.getValue();
            if (keyId == null || keyId.isEmpty()) {
                throw new IllegalArgumentException("Please select a key from the Lab Cache");
            }
            java.security.Key k = com.cryptoforge.crypto.hsm.SimulatedHsmProvider.getInstance().getRawKeyForInternalUse(keyId, usage);
            return k.getEncoded();
        } else {
            String keyHex = symmetricKeyField.getText().trim();
            if (keyHex.isEmpty()) {
                throw new IllegalArgumentException("Please enter symmetric key in hexadecimal");
            }
            return DataConverter.hexToBytes(keyHex);
        }
    }

    /**
     * Handle symmetric encryption
     */
    public void handleSymmetricEncrypt() {
        try {
            // Get inputs
            byte[] plaintext = getInputDataAsBytes();
            if (plaintext == null || plaintext.length == 0) {
                statusReporter.showError("Input Error", "Please enter data to encrypt");
                return;
            }

            String algorithm = symmetricAlgorithmCombo.getValue();
            String mode = cipherModeCombo.getValue();
            String padding = paddingCombo.getValue();

            // Get key
            byte[] key = getSymmetricKey(com.cryptoforge.crypto.hsm.KeyUsage.ENCRYPT);

            // Handle stream ciphers separately
            if (algorithm.equals("Salsa20")) {
                handleSalsa20Encrypt(plaintext, key);
                return;
            } else if (algorithm.equals("ChaCha20")) {
                handleChaCha20Encrypt(plaintext, key);
                return;
            } else if (algorithm.equals("ChaCha20-Poly1305")) {
                handleChaCha20Poly1305Encrypt(plaintext, key);
                return;
            } else if (algorithm.equals("XChaCha20-Poly1305")) {
                handleXChaCha20Poly1305Encrypt(plaintext, key);
                return;
            }

            // Get IV if required for block ciphers
            byte[] iv = null;
            if (SymmetricCipher.requiresIV(mode)) {
                String ivHex = ivField.getText().trim();
                if (ivHex.isEmpty()) {
                    statusReporter.showError("IV Error",
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
                    // Try Hex first
                    aadBytes = DataConverter.hexToBytes(aadText);
                } catch (Exception e) {
                    // Fallback to ASCII bytes (useful for pasting JWE Header string directly)
                    aadBytes = aadText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                }
            }

            warnIfNonceReused(algorithm, mode, key, iv);

            // Encrypt with block cipher
            byte[] ciphertext = SymmetricCipher.encrypt(plaintext, key, algorithm, mode, padding, iv, aadBytes);

            // Special handling for GCM - extract and show TAG separately
            if (mode.equalsIgnoreCase("GCM")) {
                displayGCMResult(ciphertext, true);
            } else {
                // Display normal result
                setOutputData(ciphertext);
            }

            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            if (symmetricKeyField != null) {
                details.put("Key Size", (symmetricKeyField.getText().trim().length() * 4) + " bits");
            }
            statusReporter.publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext).output(ciphertext).details(details)
                    .status(String.format("Encrypted using %s/%s/%s", algorithm, mode, padding)).build());

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Encryption Error",
                    "Error encrypting data: " + e.getMessage());
        }
    }

    private void warnIfNonceReused(String algorithm, String mode, byte[] key, byte[] iv) {
        boolean aead = "GCM".equalsIgnoreCase(mode)
                || "ChaCha20-Poly1305".equals(algorithm)
                || "XChaCha20-Poly1305".equals(algorithm);
        if (!aead || iv == null) return;
        try {
            byte[] fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(
                    java.nio.ByteBuffer.allocate(key.length + iv.length).put(key).put(iv).array());
            String id = DataConverter.bytesToHex(fingerprint);
            if (!usedAeadNonces.add(id)) {
                statusReporter.showInfo("Nonce reuse warning",
                        "This IV/nonce has already been used with the same key in this session. Generate a fresh value before encrypting.");
            }
        } catch (java.security.NoSuchAlgorithmException ignored) {
            // SHA-256 is mandatory in the Java runtime; no warning is preferable to blocking encryption.
        }
    }

    /**
     * Handle symmetric decryption
     */
    public void handleSymmetricDecrypt() {
        try {
            // Get inputs
            byte[] ciphertext = getInputDataAsBytes();
            if (ciphertext == null || ciphertext.length == 0) {
                statusReporter.showError("Input Error", "Please enter data to decrypt");
                return;
            }

            String algorithm = symmetricAlgorithmCombo.getValue();
            String mode = cipherModeCombo.getValue();
            String padding = paddingCombo.getValue();

            // Get key
            byte[] key = getSymmetricKey(com.cryptoforge.crypto.hsm.KeyUsage.DECRYPT);

            // Handle stream ciphers separately
            if (algorithm.equals("Salsa20")) {
                handleSalsa20Decrypt(ciphertext, key);
                return;
            } else if (algorithm.equals("ChaCha20")) {
                handleChaCha20Decrypt(ciphertext, key);
                return;
            } else if (algorithm.equals("ChaCha20-Poly1305")) {
                handleChaCha20Poly1305Decrypt(ciphertext, key);
                return;
            } else if (algorithm.equals("XChaCha20-Poly1305")) {
                handleXChaCha20Poly1305Decrypt(ciphertext, key);
                return;
            }

            // Get IV if required for block ciphers
            byte[] iv = null;
            if (SymmetricCipher.requiresIV(mode)) {
                String ivHex = ivField.getText().trim();
                if (ivHex.isEmpty()) {
                    statusReporter.showError("IV Error",
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
                    // Try Hex first
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
                    statusReporter.showError("Tag Error", "GCM Tag must be 16 bytes (32 hex chars)");
                    return;
                }

                // Append tag to ciphertext if provided separately
                byte[] combined = new byte[ciphertext.length + tag.length];
                System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
                System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);

                plaintext = SymmetricCipher.decrypt(combined, key, algorithm, mode, padding, iv, aadBytes);
            } else {
                plaintext = SymmetricCipher.decrypt(ciphertext, key, algorithm, mode, padding, iv, aadBytes);
            }

            // Special handling for GCM - show TAG verification message
            if (mode.equalsIgnoreCase("GCM")) {
                displayGCMResult(plaintext, false);
            } else {
                // Display normal result
                setOutputData(plaintext);
            }

            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            statusReporter.publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(ciphertext).output(plaintext).details(details)
                    .status(String.format("Decrypted using %s/%s/%s", algorithm, mode, padding)).build());

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (javax.crypto.AEADBadTagException e) {
            statusReporter.showError("Authentication Error",
                    "GCM TAG verification failed! The data has been modified or the key/IV is incorrect.");
        } catch (Exception e) {
            statusReporter.showError("Decryption Error",
                    "Error decrypting data: " + e.getMessage());
        }
    }

    /**
     * Handle RSA encryption
     */
    public void handleAsymmetricEncrypt() {
        try {
            if (currentPublicKey == null) {
                statusReporter.showError("Key Error",
                        "Please load a public key first");
                return;
            }

            String padding = rsaPaddingCombo.getValue();
            String inputFormat = asymmetricInputFormatCombo.getValue();
            String outputFormat = asymmetricOutputFormatCombo.getValue();

            if (padding == null || inputFormat == null || outputFormat == null) {
                statusReporter.showError("Configuration Error",
                        "Please select padding scheme and data formats");
                return;
            }

            // Get input data based on format
            String inputText = inputArea.getText().trim();
            if (inputText.isEmpty()) {
                statusReporter.showError("Input Error", "Please enter data to encrypt");
                return;
            }

            byte[] plaintext;
            switch (inputFormat) {
                case "Text (UTF-8)":
                case "UTF-8":
                    plaintext = inputText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Hexadecimal":
                case "Hex":
                    plaintext = DataConverter.hexToBytes(inputText.replaceAll("\\s+", ""));
                    break;
                case "Base64":
                    plaintext = DataConverter.decodeBase64Flexible(inputText);
                    break;
                case "Binary":
                    plaintext = DataConverter.binaryToBytes(inputText.replaceAll("\\s+", ""));
                    break;
                default:
                    statusReporter.showError("Format Error", "Unknown input format: " + inputFormat);
                    return;
            }

            // Check data size for padded modes
            if (!padding.contains("NoPadding")) {
                int keySize = ((java.security.interfaces.RSAPublicKey) currentPublicKey).getModulus().bitLength();
                int maxSize = (keySize / 8) - 11; // PKCS1 overhead
                if (padding.contains("OAEP")) {
                    maxSize = (keySize / 8) - 42; // OAEP with SHA-1 overhead
                    if (padding.contains("SHA-256")) {
                        maxSize = (keySize / 8) - 66; // OAEP with SHA-256 overhead
                    }
                }

                if (plaintext.length > maxSize) {
                    statusReporter.showError("Data Size Error",
                            String.format(
                                    "Maximum plaintext size for this key and padding: %d bytes. Your data: %d bytes.",
                                    maxSize, plaintext.length));
                    return;
                }
            }

            // Encrypt
            byte[] ciphertext = AsymmetricCipher.encrypt(plaintext, currentPublicKey, padding);

            // Format output
            String output;
            switch (outputFormat) {
                case "Text (UTF-8)":
                case "UTF-8":
                    output = new String(ciphertext, java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Hexadecimal":
                case "Hex":
                    output = DataConverter.bytesToHex(ciphertext);
                    break;
                case "Base64":
                    output = java.util.Base64.getEncoder().encodeToString(ciphertext);
                    break;
                case "Binary":
                    output = DataConverter.bytesToBinary(ciphertext);
                    break;
                default:
                    statusReporter.showError("Format Error", "Unknown output format: " + outputFormat);
                    return;
            }

            outputArea.setText(output);

            // Update Inspector
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", "RSA");
            details.put("Padding", padding);
            if (currentPublicKey != null) {
                details.put("Key Size",
                        ((java.security.interfaces.RSAPublicKey) currentPublicKey).getModulus().bitLength() + " bits");
            }
            statusReporter.publish(OperationResult.forOperation("Asymmetric Encrypt")
                    .input(plaintext).output(ciphertext).details(details)
                    .status("RSA encryption successful (" + padding + ")").build());

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Encryption Error",
                    "Error encrypting data: " + e.getMessage());
        }
    }

    /**
     * Handle RSA decryption
     */
    public void handleAsymmetricDecrypt() {
        try {
            if (currentPrivateKey == null) {
                statusReporter.showError("Key Error",
                        "Please load a private key first");
                return;
            }

            String padding = rsaPaddingCombo.getValue();
            String inputFormat = asymmetricInputFormatCombo.getValue();
            String outputFormat = asymmetricOutputFormatCombo.getValue();

            if (padding == null || inputFormat == null || outputFormat == null) {
                statusReporter.showError("Configuration Error",
                        "Please select padding scheme and data formats");
                return;
            }

            // Get input data based on format
            String inputText = inputArea.getText().trim();
            if (inputText.isEmpty()) {
                statusReporter.showError("Input Error", "Please enter data to decrypt");
                return;
            }

            byte[] ciphertext;
            switch (inputFormat) {
                case "Text (UTF-8)":
                case "UTF-8":
                    ciphertext = inputText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Hexadecimal":
                case "Hex":
                    ciphertext = DataConverter.hexToBytes(inputText.replaceAll("\\s+", ""));
                    break;
                case "Base64":
                    ciphertext = DataConverter.decodeBase64Flexible(inputText);
                    break;
                case "Binary":
                    ciphertext = DataConverter.binaryToBytes(inputText.replaceAll("\\s+", ""));
                    break;
                default:
                    statusReporter.showError("Format Error", "Unknown input format: " + inputFormat);
                    return;
            }

            // Decrypt
            byte[] plaintext = AsymmetricCipher.decrypt(ciphertext, currentPrivateKey, padding);

            // Format output
            String output;
            switch (outputFormat) {
                case "Text (UTF-8)":
                case "UTF-8":
                    output = new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Hexadecimal":
                case "Hex":
                    output = DataConverter.bytesToHex(plaintext);
                    break;
                case "Base64":
                    output = java.util.Base64.getEncoder().encodeToString(plaintext);
                    break;
                case "Binary":
                    output = DataConverter.bytesToBinary(plaintext);
                    break;
                default:
                    statusReporter.showError("Format Error", "Unknown output format: " + outputFormat);
                    return;
            }

            outputArea.setText(output);
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", "RSA");
            details.put("Padding", padding);
            if (currentPrivateKey != null && currentPrivateKey instanceof java.security.interfaces.RSAPrivateKey) {
                details.put("Key Size",
                        ((java.security.interfaces.RSAPrivateKey) currentPrivateKey).getModulus().bitLength()
                                + " bits");
            }
            statusReporter.publish(OperationResult.forOperation("Asymmetric Decrypt")
                    .input(ciphertext).output(plaintext).details(details)
                    .status("RSA decryption successful (" + padding + ")").build());

        } catch (Exception e) {
            statusReporter.showError("Decryption Error",
                    "Error decrypting data: " + e.getMessage());
        }
    }

    /**
     * Load public key from PEM file
     */
    public void handleLoadPublicKey(String filePath, javafx.scene.control.Label statusLabel) {
        try {
            String pem = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath));
            currentPublicKey = AsymmetricKeyOperations.importPublicKeyPEM(pem);

            String status = "✓ Public key loaded";
            if (currentPrivateKey != null) {
                status += ", Private key loaded";
            }
            statusLabel.setText(status);
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");

            statusReporter.updateStatus("Public key loaded from: " + filePath);
            outputArea.setText("PUBLIC KEY LOADED SUCCESSFULLY\n\n" +
                    "File: " + filePath + "\n" +
                    "Algorithm: RSA\n" +
                    "Ready for encryption.\n\n" +
                    (currentPrivateKey != null ? "Both keys loaded - ready for encryption and decryption."
                            : "Load private key to enable decryption."));

        } catch (Exception e) {
            statusReporter.showError("Load Error", "Error loading public key: " + e.getMessage());
            statusLabel.setText("✗ Error loading public key");
            statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 10px;");
        }
    }

    /**
     * Load private key from PEM file
     */
    public void handleLoadPrivateKey(String filePath, javafx.scene.control.Label statusLabel) {
        try {
            String pem = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath));
            currentPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEM(pem);

            String status = "";
            if (currentPublicKey != null) {
                status = "✓ Public key loaded, ";
            }
            status += "✓ Private key loaded";
            statusLabel.setText(status);
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");

            statusReporter.updateStatus("Private key loaded from: " + filePath);
            outputArea.setText("PRIVATE KEY LOADED SUCCESSFULLY\n\n" +
                    "File: " + filePath + "\n" +
                    "Algorithm: RSA\n" +
                    "Ready for decryption.\n\n" +
                    (currentPublicKey != null ? "Both keys loaded - ready for encryption and decryption."
                            : "Load public key to enable encryption."));

        } catch (Exception e) {
            statusReporter.showError("Load Error", "Error loading private key: " + e.getMessage());
            statusLabel.setText("✗ Error loading private key");
            statusLabel.setStyle("-fx-text-fill: red; -fx-font-size: 10px;");
        }
    }

    /**
     * Update IV field state based on selected mode
     */
    private void updateIVFieldState() {
        if (ivField != null && cipherModeCombo != null) {
            String mode = cipherModeCombo.getValue();
            boolean requiresIV = SymmetricCipher.requiresIV(mode);

            if (requiresIV) {
                ivField.setDisable(false);
                ivField.setStyle("-fx-opacity: 1.0;");
            } else {
                ivField.setDisable(true);
                ivField.setStyle("-fx-opacity: 0.5;");
                ivField.clear();
            }
        }
    }

    /**
     * Update GCM Tag field state based on selected mode
     */
    private void updateGcmTagFieldState() {
        if (gcmTagField != null && cipherModeCombo != null) {
            String mode = cipherModeCombo.getValue();
            boolean isGCM = mode != null && mode.toUpperCase().contains("GCM");

            if (isGCM) {
                gcmTagField.setDisable(false);
                gcmTagField.setStyle("-fx-opacity: 1.0; -fx-font-family: 'Monospaced';");
                if (aadField != null) {
                    aadField.setDisable(false);
                    aadField.setStyle("-fx-opacity: 1.0; -fx-font-family: 'Monospaced';");
                }
            } else {
                gcmTagField.setDisable(true);
                gcmTagField.setStyle("-fx-opacity: 0.5; -fx-font-family: 'Monospaced';");
                gcmTagField.clear();
                if (aadField != null) {
                    aadField.setDisable(true);
                    aadField.setStyle("-fx-opacity: 0.5; -fx-font-family: 'Monospaced';");
                    aadField.clear();
                }
            }
        }
    }

    /**
     * Update padding field state based on selected mode
     */
    private void updatePaddingFieldState() {
        if (paddingCombo != null && cipherModeCombo != null) {
            String mode = cipherModeCombo.getValue();
            boolean supportsPadding = SymmetricCipher.supportsPadding(mode);

            if (supportsPadding) {
                paddingCombo.setDisable(false);
                paddingCombo.setStyle("-fx-opacity: 1.0;");
            } else {
                paddingCombo.setDisable(true);
                paddingCombo.setStyle("-fx-opacity: 0.5;");
                paddingCombo.setValue("NoPadding");
            }
        }
    }

    /**
     * Update UI state for stream ciphers (Salsa20, ChaCha20-Poly1305)
     * Stream ciphers don't use modes or padding
     */
    private void updateStreamCipherState() {
        if (symmetricAlgorithmCombo == null)
            return;

        String algorithm = symmetricAlgorithmCombo.getValue();
        boolean isStreamCipher = SymmetricCipher.isStreamCipher(algorithm);

        if (isStreamCipher) {
            // Disable mode and padding for stream ciphers
            if (cipherModeCombo != null) {
                cipherModeCombo.setDisable(true);
                cipherModeCombo.setStyle("-fx-opacity: 0.5;");
                cipherModeCombo.setValue("N/A");
            }
            if (paddingCombo != null) {
                paddingCombo.setDisable(true);
                paddingCombo.setStyle("-fx-opacity: 0.5;");
                paddingCombo.setValue("N/A");
            }
            // IV field used for nonce
            if (ivField != null) {
                ivField.setDisable(false);
                ivField.setStyle("-fx-opacity: 1.0;");
                if (algorithm.equals("Salsa20") || algorithm.equals("ChaCha20")) {
                    ivField.setPromptText("Nonce (8 bytes hex)");
                } else {
                    ivField.setPromptText("Nonce (12 bytes hex)");
                }
            }

            // Specific handling for ChaCha20-Poly1305 which is AEAD
            if (algorithm.equals("ChaCha20-Poly1305")) {
                if (gcmTagField != null) {
                    gcmTagField.setDisable(false);
                    gcmTagField.setStyle("-fx-opacity: 1.0; -fx-font-family: 'Monospaced';");
                }
                if (aadField != null) {
                    aadField.setDisable(false);
                    aadField.setStyle("-fx-opacity: 1.0; -fx-font-family: 'Monospaced';");
                }
            } else {
                // Ensure they are disabled for pure stream ciphers like Salsa20/ChaCha20
                if (gcmTagField != null) {
                    gcmTagField.setDisable(true);
                    gcmTagField.setStyle("-fx-opacity: 0.5;");
                    gcmTagField.clear();
                }
                if (aadField != null) {
                    aadField.setDisable(true);
                    aadField.setStyle("-fx-opacity: 0.5;");
                    aadField.clear();
                }
            }
        } else {
            // Re-enable mode and padding for Block Ciphers
            if (cipherModeCombo != null) {
                cipherModeCombo.setDisable(false);
                cipherModeCombo.setStyle("-fx-opacity: 1.0;");
                if ("N/A".equals(cipherModeCombo.getValue())) {
                    cipherModeCombo.setValue("CBC");
                }
            }
            if (paddingCombo != null) {
                paddingCombo.setDisable(false);
                paddingCombo.setStyle("-fx-opacity: 1.0;");
                if ("N/A".equals(paddingCombo.getValue())) {
                    paddingCombo.setValue("PKCS7Padding");
                }
            }
            if (ivField != null) {
                ivField.setPromptText("IV (Hex) - required for CBC, CTR, GCM, etc.");
                updateIVFieldState();
            }
            // Tag/AAD state is handled by the mode listener (e.g. GCM check)
            updateGcmTagFieldState();
        }
    }

    /**
     * Get input data as bytes
     */
    private byte[] getInputDataAsBytes() {
        String input = inputArea.getText().trim();
        if (input.isEmpty()) {
            return null;
        }

        String format = inputFormatCombo.getValue();
        if (format == null)
            format = "Hexadecimal";

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
        String format = outputFormatCombo.getValue();
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

        outputArea.setText(output);
    }

    /**
     * Display GCM encryption/decryption result with TAG shown separately
     * In GCM, the last 16 bytes are the authentication TAG (only for encryption)
     */
    private void displayGCMResult(byte[] data, boolean isEncryption) {
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
                return;
            }

            // GCM TAG is 16 bytes (128 bits) at the end
            int tagLength = 16;
            byte[] ciphertext = new byte[data.length - tagLength];
            byte[] tag = new byte[tagLength];

            System.arraycopy(data, 0, ciphertext, 0, ciphertext.length);
            System.arraycopy(data, ciphertext.length, tag, 0, tagLength);

            // Format output based on selected format
            String format = outputFormatCombo.getValue();
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

            outputArea.setText(output.toString());

        } else {
            // For decryption: just show the plaintext with verification message
            String format = outputFormatCombo.getValue();
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

            outputArea.setText(output.toString());
        }
    }

    // Helpers to support Salsa20 and ChaCha20
    private void handleChaCha20Encrypt(byte[] plaintext, byte[] key) {
        try {
            String algorithm = "ChaCha20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] ciphertext = SymmetricCipher.encryptChaCha20(plaintext, key, iv);
            setOutputData(ciphertext);
            statusReporter.updateStatus("Encrypted using ChaCha20");

        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
        }
    }

    private void handleChaCha20Decrypt(byte[] ciphertext, byte[] key) {
        try {
            String algorithm = "ChaCha20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] plaintext = SymmetricCipher.decryptChaCha20(ciphertext, key, iv);
            setOutputData(plaintext);
            statusReporter.updateStatus("Decrypted using ChaCha20");
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
        }
    }

    private void handleSalsa20Encrypt(byte[] plaintext, byte[] key) {
        try {
            String algorithm = "Salsa20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] ciphertext = SymmetricCipher.encrypt(plaintext, key, algorithm, "None", "NoPadding", iv);
            setOutputData(ciphertext);
            statusReporter.updateStatus("Encrypted using Salsa20");

        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
        }
    }

    private void handleChaCha20Poly1305Encrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());

            // Use specialized method which handles 12-byte nonce
            byte[] combined = SymmetricCipher.encryptChaCha20Poly1305(plaintext, key, iv);

            // Split for display (last 16 bytes are tag)
            displayGCMResult(combined, true);
            statusReporter.updateStatus("Encrypted using ChaCha20-Poly1305");
        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
        }
    }

    private void handleSalsa20Decrypt(byte[] ciphertext, byte[] key) {
        try {
            String algorithm = "Salsa20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] plaintext = SymmetricCipher.decrypt(ciphertext, key, algorithm, "None", "NoPadding", iv);
            setOutputData(plaintext);
            statusReporter.updateStatus("Decrypted using Salsa20");
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
        }
    }

    private void handleChaCha20Poly1305Decrypt(byte[] ciphertext, byte[] key) {
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

            byte[] plaintext = SymmetricCipher.decryptChaCha20Poly1305(combined, key, iv);

            displayGCMResult(plaintext, false);
            statusReporter.updateStatus("Decrypted using ChaCha20-Poly1305");
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
        }
    }

    // --- XChaCha20-Poly1305 Handlers ---

    private void handleXChaCha20Poly1305Encrypt(byte[] plaintext, byte[] key) {
        try {
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());

            // XChaCha20-Poly1305 Encryption
            byte[] combined = SymmetricCipher.encryptXChaCha20Poly1305(plaintext, key, iv);

            // Split for display (last 16 bytes are tag)
            displayGCMResult(combined, true);
            statusReporter.updateStatus("Encrypted using XChaCha20-Poly1305");
        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
        }
    }

    private void handleXChaCha20Poly1305Decrypt(byte[] ciphertext, byte[] key) {
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

            byte[] plaintext = SymmetricCipher.decryptXChaCha20Poly1305(combined, key, iv);

            displayGCMResult(plaintext, false);
            statusReporter.updateStatus("Decrypted using XChaCha20-Poly1305");
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
        }
    }

    // --- Helper Methods for Global Toolbar ---

    public void handleClear() {
        if (inputArea != null)
            inputArea.clear();
        if (outputArea != null)
            outputArea.clear();
        if (symmetricKeyField != null)
            symmetricKeyField.clear();
        if (ivField != null)
            ivField.clear();
        if (aadField != null)
            aadField.clear();
        if (gcmTagField != null)
            gcmTagField.clear();
    }

    public String getOutputText() {
        return outputArea != null ? outputArea.getText() : "";
    }

}
