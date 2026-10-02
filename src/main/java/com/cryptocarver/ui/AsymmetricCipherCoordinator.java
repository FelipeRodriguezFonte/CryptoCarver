package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricCipher;
import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.function.Supplier;

/**
 * The RSA panel of the cipher screen: loading public and private keys (PEM, hex or Base64 DER),
 * the padding choice with its warning, and encryption/decryption of the shared input area.
 * Keys loaded here take precedence; otherwise the key pair generated in the Keys module is used.
 */
final class AsymmetricCipherCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(AsymmetricCipherCoordinator.class);

    /** The panel's controls, injected into CipherController from cipher.fxml. */
    record View(ComboBox<String> padding,
            ComboBox<String> inputFormat,
            ComboBox<String> outputFormat,
            Label paddingWarning,
            Label paddingHelp,
            TextArea publicKey,
            TextArea privateKey,
            TextArea input,
            TextArea output) {
    }

    private final ComboBox<String> rsaPaddingCombo;
    private final ComboBox<String> asymmetricInputFormatCombo;
    private final ComboBox<String> asymmetricOutputFormatCombo;
    private final Label rsaPaddingWarningLabel;
    private final Label rsaPaddingHelpLabel;
    private final TextArea publicKeyArea;
    private final TextArea privateKeyArea;
    private final TextArea cipherInputArea;
    private final TextArea cipherOutputArea;
    private final Supplier<StatusReporter> reporter;
    private final Supplier<Supplier<KeyPair>> sharedKeyPairs;
    private PublicKey currentPublicKey;
    private PrivateKey currentPrivateKey;

    AsymmetricCipherCoordinator(View view, Supplier<StatusReporter> reporter, Supplier<Supplier<KeyPair>> sharedKeyPairs) {
        this.rsaPaddingCombo = view.padding();
        this.asymmetricInputFormatCombo = view.inputFormat();
        this.asymmetricOutputFormatCombo = view.outputFormat();
        this.rsaPaddingWarningLabel = view.paddingWarning();
        this.rsaPaddingHelpLabel = view.paddingHelp();
        this.publicKeyArea = view.publicKey();
        this.privateKeyArea = view.privateKey();
        this.cipherInputArea = view.input();
        this.cipherOutputArea = view.output();
        this.reporter = reporter;
        this.sharedKeyPairs = sharedKeyPairs;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private Supplier<KeyPair> sharedKeyPairSupplier() {
        return sharedKeyPairs.get();
    }


    private void syncSharedKeyPair() {
        if (sharedKeyPairSupplier() == null) return;
        java.security.KeyPair pair = sharedKeyPairSupplier().get();
        if (pair != null) {
            currentPublicKey = pair.getPublic();
            currentPrivateKey = pair.getPrivate();
        }
    }

    /** Returns whether the selected asymmetric operation has key material available without mutating state. */
    boolean hasAsymmetricKeyAvailable(boolean forEncryption) {
        if (forEncryption && currentPublicKey != null) return true;
        if (!forEncryption && currentPrivateKey != null) return true;
        if (sharedKeyPairSupplier() == null) return false;
        java.security.KeyPair pair = sharedKeyPairSupplier().get();
        return pair != null && (forEncryption ? pair.getPublic() != null : pair.getPrivate() != null);
    }

    /**
     * Handle manual Public Key loading
     */
    void handleLoadPublicKey() {
        if (publicKeyArea == null)
            return;

        String keyText = publicKeyArea.getText().trim();
        if (keyText.isEmpty()) {
            reporter().showError("Key Error", "Please enter a public key (PEM or Hex)");
            return;
        }

        try {
            // Try PEM format firs
            if (keyText.contains("-----BEGIN PUBLIC KEY-----")) {
                currentPublicKey = AsymmetricKeyOperations.importPublicKeyPEM(keyText);
                reporter().updateStatus("Public Key loaded from PEM");
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
                    reporter().updateStatus("Public Key loaded from Hex (DER)");
                } catch (Exception e) {
                    // Try converting from Base64 if Hex fails, just in case
                    try {
                        byte[] keyBytes = org.apache.commons.codec.binary.Base64.decodeBase64(keyText);
                        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(
                                keyBytes);
                        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA", "BC");
                        currentPublicKey = kf.generatePublic(spec);
                        reporter().updateStatus("Public Key loaded from Base64 (DER)");
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Unknown key format. Please use PEM or Hex/Base64 DER.");
                    }
                }
            }
        } catch (Exception e) {
            reporter().showError("Load Error", "Failed to load Public Key: " + e.getMessage());
            LOG.warn("Unable to load public key", e);
        }
    }

    /**
     * Handle manual Private Key loading
     */
    void handleLoadPrivateKey() {
        if (privateKeyArea == null)
            return;

        String keyText = privateKeyArea.getText().trim();
        if (keyText.isEmpty()) {
            reporter().showError("Key Error", "Please enter a private key (PEM or Hex)");
            return;
        }

        try {
            // Try PEM format firs
            if (keyText.contains("-----BEGIN PRIVATE KEY-----")) {
                currentPrivateKey = AsymmetricKeyOperations.importPrivateKeyPEM(keyText);
                reporter().updateStatus("Private Key loaded from PEM");
            } else {
                // Try Hex/Base64 DER
                try {
                    byte[] keyBytes = DataConverter.hexToBytes(keyText);
                    java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
                    java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA", "BC");
                    currentPrivateKey = kf.generatePrivate(spec);
                    reporter().updateStatus("Private Key loaded from Hex (DER)");
                } catch (Exception e) {
                    try {
                        byte[] keyBytes = org.apache.commons.codec.binary.Base64.decodeBase64(keyText);
                        java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(
                                keyBytes);
                        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA", "BC");
                        currentPrivateKey = kf.generatePrivate(spec);
                        reporter().updateStatus("Private Key loaded from Base64 (DER)");
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Unknown key format. Please use PEM or Hex/Base64 DER.");
                    }
                }
            }
        } catch (Exception e) {
            reporter().showError("Load Error", "Failed to load Private Key: " + e.getMessage());
            LOG.warn("Unable to load private key", e);
        }
    }

    void readPublicKeyFile() {
        readKeyFile("Read Public Key File", publicKeyArea, true, "*.pem", "*.key", "*.pub", "*.txt");
    }

    void readPrivateKeyFile() {
        readKeyFile("Read Private Key File", privateKeyArea, false, "*.pem", "*.key", "*.txt");
    }

    private void readKeyFile(String title, TextArea target, boolean publicKey, String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM Files", extensions));
        java.io.File file = chooser.showOpenDialog(target.getScene() == null ? null : target.getScene().getWindow());
        if (file == null) return;
        try {
            target.setText(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (publicKey) handleLoadPublicKey(); else handleLoadPrivateKey();
        } catch (Exception e) {
            reporter().showError("Read Error", "Failed to read file: " + e.getMessage());
        }
    }

    /** Fills the padding schemes and keeps the padding warning in step with the selection. */
    void configure() {
        // Populate padding schemes (RSA specific)
        rsaPaddingCombo.getItems().addAll(
                "RSA/ECB/PKCS1Padding",
                "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
                "RSA/ECB/NoPadding");
        rsaPaddingCombo.setValue("RSA/ECB/PKCS1Padding");
        rsaPaddingCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateRsaPaddingHelpAndWarning());
        updateRsaPaddingHelpAndWarning();
    }

    private void updateRsaPaddingHelpAndWarning() {
        if (rsaPaddingCombo == null) return;
        String padding = rsaPaddingCombo.getValue();
        boolean isInsecure = padding != null && (padding.contains("PKCS1Padding") || padding.contains("NoPadding"));
        if (rsaPaddingWarningLabel != null) {
            rsaPaddingWarningLabel.setVisible(isInsecure);
            rsaPaddingWarningLabel.setManaged(isInsecure);
        }
        if (rsaPaddingHelpLabel != null) {
            rsaPaddingHelpLabel.setVisible(!isInsecure);
            rsaPaddingHelpLabel.setManaged(!isInsecure);
        }
    }

    /**
     * Handle RSA encryption
     */
    void handleAsymmetricEncrypt() {
        if (reporter() != null && !reporter().checkPreflightReadiness("Asymmetric Ciphers", true)) {
            return;
        }
        try {
            syncSharedKeyPair();
            if (currentPublicKey == null) {
                reporter().showError("Key Error",
                        "Please load a public key first");
                return;
            }

            String padding = rsaPaddingCombo.getValue();
            String inputFormat = asymmetricInputFormatCombo.getValue();
            String outputFormat = asymmetricOutputFormatCombo.getValue();

            if (padding == null || inputFormat == null || outputFormat == null) {
                reporter().showError("Configuration Error",
                        "Please select padding scheme and data formats");
                return;
            }

            // Get input data based on forma
            String inputText = cipherInputArea.getText().trim();
            if (inputText.isEmpty()) {
                reporter().showError("Input Error", "Please enter data to encrypt");
                return;
            }

            try {
                com.cryptocarver.util.InputValidator.validateInput(inputText, inputFormat);
            } catch (IllegalArgumentException e) {
                reporter().showError("Format Error", e.getMessage());
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
                    reporter().showError("Format Error", "Unknown input format: " + inputFormat);
                    return;
            }

            // Check data size for padded modes
            if (!padding.contains("NoPadding")) {
                int keySize = ((java.security.interfaces.RSAPublicKey) currentPublicKey).getModulus().bitLength();
                int maxSize = RsaPaddingLimits.maxPlaintextBytes(keySize, padding);

                if (plaintext.length > maxSize) {
                    reporter().showError("Data Size Error",
                            String.format(
                                    "Maximum plaintext size for this key and padding: %d bytes. Your data: %d bytes.",
                                    maxSize, plaintext.length));
                    return;
                }
            }

            // Encryp
            byte[] ciphertext = AsymmetricCipher.encrypt(plaintext, currentPublicKey, padding);

            // Format outpu
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
                    reporter().showError("Format Error", "Unknown output format: " + outputFormat);
                    return;
            }

            cipherOutputArea.setText(output);

            // Update Inspector
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", "RSA");
            details.put("Padding", padding);
            if (currentPublicKey != null) {
                details.put("Key Size",
                        ((java.security.interfaces.RSAPublicKey) currentPublicKey).getModulus().bitLength() + " bits");
            }
            reporter().publish(OperationResult.forOperation("Asymmetric Encrypt")
                    .input(plaintext).output(ciphertext).details(details)
                    .status("RSA encryption successful (" + padding + ")").build());

        } catch (IllegalArgumentException e) {
            reporter().showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            reporter().showError("Encryption Error",
                    "Error encrypting data: " + e.getMessage());
        }
    }

    /**
     * Handle RSA decryption
     */
    void handleAsymmetricDecrypt() {
        if (reporter() != null && !reporter().checkPreflightReadiness("Asymmetric Ciphers", false)) {
            return;
        }
        try {
            syncSharedKeyPair();
            if (currentPrivateKey == null) {
                reporter().showError("Key Error",
                        "Please load a private key first");
                return;
            }

            String padding = rsaPaddingCombo.getValue();
            String inputFormat = asymmetricInputFormatCombo.getValue();
            String outputFormat = asymmetricOutputFormatCombo.getValue();

            if (padding == null || inputFormat == null || outputFormat == null) {
                reporter().showError("Configuration Error",
                        "Please select padding scheme and data formats");
                return;
            }

            // Get input data based on format
            String inputText = cipherInputArea.getText().trim();
            if (inputText.isEmpty()) {
                reporter().showError("Input Error", "Please enter data to decrypt");
                return;
            }

            try {
                com.cryptocarver.util.InputValidator.validateInput(inputText, inputFormat);
            } catch (IllegalArgumentException e) {
                reporter().showError("Format Error", e.getMessage());
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
                    reporter().showError("Format Error", "Unknown input format: " + inputFormat);
                    return;
            }

            // Decryp
            byte[] plaintext = AsymmetricCipher.decrypt(ciphertext, currentPrivateKey, padding);

            // Format outpu
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
                    reporter().showError("Format Error", "Unknown output format: " + outputFormat);
                    return;
            }

            cipherOutputArea.setText(output);
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", "RSA");
            details.put("Padding", padding);
            if (currentPrivateKey != null && currentPrivateKey instanceof java.security.interfaces.RSAPrivateKey) {
                details.put("Key Size",
                        ((java.security.interfaces.RSAPrivateKey) currentPrivateKey).getModulus().bitLength()
                                + " bits");
            }
            reporter().publish(OperationResult.forOperation("Asymmetric Decrypt")
                    .input(ciphertext).output(plaintext).details(details)
                    .status("RSA decryption successful (" + padding + ")").build());

        } catch (Exception e) {
            reporter().showError("Decryption Error",
                    "Error decrypting data: " + e.getMessage());
        }
    }
}
