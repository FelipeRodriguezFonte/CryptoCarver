package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricCipher;
import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.utils.DataConverter;
import com.cryptocarver.utils.OperationHistory;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private TextField symmetricKeyField;
    private TextField ivField;
    private TextField gcmTagField;
    private TextField aadField; // Added AAD field

    // Asymmetric cipher UI components
    private ComboBox<String> rsaPaddingCombo;
    private ComboBox<String> asymmetricInputFormatCombo;
    private ComboBox<String> asymmetricOutputFormatCombo;
    private PublicKey currentPublicKey;
    private PrivateKey currentPrivateKey;

    // Key Input Areas (Manual Loading)
    private TextArea publicKeyArea;
    private TextArea privateKeyArea;

    private static final byte[] INDEPENDENT_BLOCK_MAGIC = "CFXBI1".getBytes(StandardCharsets.US_ASCII);
    private static final int DEFAULT_EXPERT_BLOCK_SIZE = 4096;
    private static final int[] DEFAULT_ANALYSIS_BLOCK_SIZES = new int[] { 64, 128, 256, 512, 1024, 2048, 4096 };
    private static final Charset CHARSET_EBCDIC_CP037 = Charset.forName("Cp037");
    private static final Charset CHARSET_EBCDIC_CP500 = Charset.forName("Cp500");

    public enum FileProcessingMode {
        FULL_CONTENT,
        INDEPENDENT_BLOCKS
    }

    public enum FileDataEncoding {
        RAW,
        HEX,
        BASE64,
        UTF8,
        EBCDIC_CP037,
        EBCDIC_CP500
    }

    public static class ExpertFileOptions {
        private final FileProcessingMode processingMode;
        private final int blockSizeBytes;
        private final FileDataEncoding inputEncoding;
        private final FileDataEncoding outputEncoding;

        public ExpertFileOptions(FileProcessingMode processingMode,
                int blockSizeBytes,
                FileDataEncoding inputEncoding,
                FileDataEncoding outputEncoding) {
            this.processingMode = processingMode;
            this.blockSizeBytes = blockSizeBytes;
            this.inputEncoding = inputEncoding;
            this.outputEncoding = outputEncoding;
        }

        public static ExpertFileOptions defaults() {
            return new ExpertFileOptions(
                    FileProcessingMode.FULL_CONTENT,
                    DEFAULT_EXPERT_BLOCK_SIZE,
                    FileDataEncoding.RAW,
                    FileDataEncoding.RAW);
        }

        public FileProcessingMode getProcessingMode() {
            return processingMode;
        }

        public int getBlockSizeBytes() {
            return blockSizeBytes;
        }

        public FileDataEncoding getInputEncoding() {
            return inputEncoding;
        }

        public FileDataEncoding getOutputEncoding() {
            return outputEncoding;
        }
    }

    public static class FileAnalysisOptions {
        private final int[] candidateBlockSizes;
        private final boolean testFullContent;
        private final boolean testIndependentBlocks;
        private final int maxResults;
        private final FileDataEncoding forcedInputEncoding;
        private final int sampleSizeBytes;

        public FileAnalysisOptions(int[] candidateBlockSizes,
                boolean testFullContent,
                boolean testIndependentBlocks,
                int maxResults,
                FileDataEncoding forcedInputEncoding,
                int sampleSizeBytes) {
            this.candidateBlockSizes = candidateBlockSizes;
            this.testFullContent = testFullContent;
            this.testIndependentBlocks = testIndependentBlocks;
            this.maxResults = maxResults;
            this.forcedInputEncoding = forcedInputEncoding;
            this.sampleSizeBytes = sampleSizeBytes;
        }

        public static FileAnalysisOptions defaults() {
            return new FileAnalysisOptions(
                    Arrays.copyOf(DEFAULT_ANALYSIS_BLOCK_SIZES, DEFAULT_ANALYSIS_BLOCK_SIZES.length),
                    true,
                    true,
                    8,
                    null,
                    262144);
        }

        public int[] getCandidateBlockSizes() {
            return candidateBlockSizes;
        }

        public boolean isTestFullContent() {
            return testFullContent;
        }

        public boolean isTestIndependentBlocks() {
            return testIndependentBlocks;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public FileDataEncoding getForcedInputEncoding() {
            return forcedInputEncoding;
        }

        public int getSampleSizeBytes() {
            return sampleSizeBytes;
        }
    }

    private static class AnalysisCandidate {
        private final String algorithm;
        private final String mode;
        private final String padding;
        private final String processing;
        private final int blockSize;
        private final FileDataEncoding inputEncoding;
        private final byte[] plaintext;
        private final int baseScore;
        private final int paddingAdjustment;
        private final int score;
        private final String inferredPlainEncoding;
        private final String qualitySummary;
        private final String preview;
        private final String paddingEvidence;
        private double confidencePercent;

        private AnalysisCandidate(String algorithm,
                String mode,
                String padding,
                String processing,
                int blockSize,
                FileDataEncoding inputEncoding,
                byte[] plaintext,
                int baseScore,
                int paddingAdjustment,
                int score,
                String inferredPlainEncoding,
                String qualitySummary,
                String preview,
                String paddingEvidence) {
            this.algorithm = algorithm;
            this.mode = mode;
            this.padding = padding;
            this.processing = processing;
            this.blockSize = blockSize;
            this.inputEncoding = inputEncoding;
            this.plaintext = plaintext;
            this.baseScore = baseScore;
            this.paddingAdjustment = paddingAdjustment;
            this.score = score;
            this.inferredPlainEncoding = inferredPlainEncoding;
            this.qualitySummary = qualitySummary;
            this.preview = preview;
            this.paddingEvidence = paddingEvidence;
            this.confidencePercent = 0.0;
        }
    }

    private static class CipherCombination {
        private final String algorithm;
        private final String mode;
        private final String padding;

        private CipherCombination(String algorithm, String mode, String padding) {
            this.algorithm = algorithm;
            this.mode = mode;
            this.padding = padding;
        }
    }

    private static class AnalysisAttempt {
        private final int index;
        private final String algorithm;
        private final String mode;
        private final String padding;
        private final String processing;
        private final int blockSize;
        private final FileDataEncoding inputEncoding;
        private final boolean success;
        private final int score;
        private final String inferredPlainEncoding;
        private final String preview;
        private final String error;

        private AnalysisAttempt(int index,
                String algorithm,
                String mode,
                String padding,
                String processing,
                int blockSize,
                FileDataEncoding inputEncoding,
                boolean success,
                int score,
                String inferredPlainEncoding,
                String preview,
                String error) {
            this.index = index;
            this.algorithm = algorithm;
            this.mode = mode;
            this.padding = padding;
            this.processing = processing;
            this.blockSize = blockSize;
            this.inputEncoding = inputEncoding;
            this.success = success;
            this.score = score;
            this.inferredPlainEncoding = inferredPlainEncoding;
            this.preview = preview;
            this.error = error;
        }
    }

    private static class PlaintextQuality {
        private final int score;
        private final String inferredEncoding;
        private final String summary;
        private final String preview;

        private PlaintextQuality(int score, String inferredEncoding, String summary, String preview) {
            this.score = score;
            this.inferredEncoding = inferredEncoding;
            this.summary = summary;
            this.preview = preview;
        }
    }

    private static class PaddingEvidence {
        private final int adjustment;
        private final String summary;

        private PaddingEvidence(int adjustment, String summary) {
            this.adjustment = adjustment;
            this.summary = summary;
        }
    }

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
            String keyHex = symmetricKeyField.getText().trim();
            if (keyHex.isEmpty()) {
                statusReporter.showError("Key Error", "Please enter encryption key in hexadecimal");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);

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

            // Encrypt with block cipher
            byte[] ciphertext = SymmetricCipher.encrypt(plaintext, key, algorithm, mode, padding, iv, aadBytes);

            // Special handling for GCM - extract and show TAG separately
            if (mode.equalsIgnoreCase("GCM")) {
                displayGCMResult(ciphertext, true);
            } else {
                // Display normal result
                setOutputData(ciphertext);
            }

            statusReporter.updateStatus(String.format("Encrypted using %s/%s/%s",
                    algorithm, mode, padding));

            // Update Inspector
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            if (symmetricKeyField != null) {
                details.put("Key Size", (symmetricKeyField.getText().trim().length() * 4) + " bits");
            }
            statusReporter.updateInspector("Symmetric Encrypt", plaintext, ciphertext, details);

            // Add to history
            String output = mode.equalsIgnoreCase("GCM")
                    ? outputArea.getText().substring(0, Math.min(100, outputArea.getText().length()))
                    : DataConverter.bytesToHex(ciphertext).substring(0,
                            Math.min(100, DataConverter.bytesToHex(ciphertext).length()));

            OperationHistory.getInstance().addOperation(
                    "Cipher",
                    "Encrypt - " + algorithm + "/" + mode,
                    DataConverter.bytesToHex(plaintext).substring(0,
                            Math.min(50, DataConverter.bytesToHex(plaintext).length())),
                    output);

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Encryption Error",
                    "Error encrypting data: " + e.getMessage());
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
            String keyHex = symmetricKeyField.getText().trim();
            if (keyHex.isEmpty()) {
                statusReporter.showError("Key Error", "Please enter decryption key in hexadecimal");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);

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

            statusReporter.updateStatus(String.format("Decrypted using %s/%s/%s",
                    algorithm, mode, padding));

            // Update Inspector
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            statusReporter.updateInspector("Symmetric Decrypt", ciphertext, plaintext, details);

            // Add to history
            String output = mode.equalsIgnoreCase("GCM")
                    ? outputArea.getText().substring(0, Math.min(100, outputArea.getText().length()))
                    : DataConverter.bytesToHex(plaintext).substring(0,
                            Math.min(100, DataConverter.bytesToHex(plaintext).length()));

            OperationHistory.getInstance().addOperation(
                    "Cipher",
                    "Decrypt - " + algorithm + "/" + mode,
                    DataConverter.bytesToHex(ciphertext).substring(0,
                            Math.min(50, DataConverter.bytesToHex(ciphertext).length())),
                    output);

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
     * Handle symmetric file encryption
     */
    public void handleSymmetricEncryptFile(Path inputFile, Path outputFile) {
        handleSymmetricEncryptFile(inputFile, outputFile, ExpertFileOptions.defaults());
    }

    /**
     * Handle symmetric file encryption with expert options
     */
    public void handleSymmetricEncryptFile(Path inputFile, Path outputFile, ExpertFileOptions options) {
        try {
            if (inputFile == null || outputFile == null) {
                statusReporter.showError("File Error", "Please select input and output files");
                return;
            }

            ExpertFileOptions effectiveOptions = normalizeExpertOptions(options);

            byte[] fileInput = Files.readAllBytes(inputFile);
            if (fileInput.length == 0) {
                statusReporter.showError("Input Error", "Input file is empty");
                return;
            }

            String algorithm = symmetricAlgorithmCombo.getValue();
            String mode = cipherModeCombo.getValue();
            String padding = paddingCombo.getValue();
            byte[] key = getSymmetricKeyBytes("encryption");

            byte[] decodedInput = decodeFileData(fileInput, effectiveOptions.getInputEncoding());
            byte[] processed;
            if (effectiveOptions.getProcessingMode() == FileProcessingMode.INDEPENDENT_BLOCKS) {
                processed = encryptIndependentBlocks(
                        decodedInput,
                        key,
                        algorithm,
                        mode,
                        padding,
                        effectiveOptions.getBlockSizeBytes());
            } else {
                processed = encryptSymmetricBytes(decodedInput, key, algorithm, mode, padding);
            }

            byte[] outputData = encodeFileData(processed, effectiveOptions.getOutputEncoding());
            Files.write(outputFile, outputData);

            outputArea.setText(
                    "EXPERT FILE ENCRYPTION SUCCESS\n\n" +
                            "Input File: " + inputFile + "\n" +
                            "Output File: " + outputFile + "\n" +
                            "Algorithm: " + algorithm + "\n" +
                            "Mode: " + mode + "\n" +
                            "Padding: " + padding + "\n" +
                            "Processing: " + effectiveOptions.getProcessingMode() + "\n" +
                            "Block Size: " + effectiveOptions.getBlockSizeBytes() + " bytes\n" +
                            "Input Encoding: " + effectiveOptions.getInputEncoding() + "\n" +
                            "Output Encoding: " + effectiveOptions.getOutputEncoding() + "\n" +
                            "Decoded Input Size: " + decodedInput.length + " bytes\n" +
                            "Processed Size: " + processed.length + " bytes\n" +
                            "Written Size: " + outputData.length + " bytes");

            statusReporter.updateStatus("File encrypted: " + inputFile.getFileName() + " → " + outputFile.getFileName());

            Map<String, String> details = new HashMap<>();
            details.put("Type", "File Encryption");
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            details.put("Processing", effectiveOptions.getProcessingMode().name());
            details.put("Block Size", effectiveOptions.getBlockSizeBytes() + " bytes");
            details.put("Input Encoding", effectiveOptions.getInputEncoding().name());
            details.put("Output Encoding", effectiveOptions.getOutputEncoding().name());
            details.put("Input File", inputFile.toString());
            details.put("Output File", outputFile.toString());
            details.put("Decoded Input Size", decodedInput.length + " bytes");
            details.put("Processed Size", processed.length + " bytes");
            details.put("Written Size", outputData.length + " bytes");
            statusReporter.updateInspector("Symmetric File Encrypt (Expert)", decodedInput, processed, details);

            OperationHistory.getInstance().addOperation(
                    "Cipher",
                    "Encrypt File Expert - " + algorithm + "/" + mode,
                    inputFile.getFileName() + " (" + decodedInput.length + " bytes, "
                            + effectiveOptions.getInputEncoding().name() + ")",
                    outputFile.getFileName() + " (" + outputData.length + " bytes, "
                            + effectiveOptions.getOutputEncoding().name() + ")");

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Encryption Error",
                    "Error encrypting file: " + e.getMessage());
        }
    }

    /**
     * Handle symmetric file decryption
     */
    public void handleSymmetricDecryptFile(Path inputFile, Path outputFile) {
        handleSymmetricDecryptFile(inputFile, outputFile, ExpertFileOptions.defaults());
    }

    /**
     * Handle symmetric file decryption with expert options
     */
    public void handleSymmetricDecryptFile(Path inputFile, Path outputFile, ExpertFileOptions options) {
        try {
            if (inputFile == null || outputFile == null) {
                statusReporter.showError("File Error", "Please select input and output files");
                return;
            }

            ExpertFileOptions effectiveOptions = normalizeExpertOptions(options);

            byte[] fileInput = Files.readAllBytes(inputFile);
            if (fileInput.length == 0) {
                statusReporter.showError("Input Error", "Input file is empty");
                return;
            }

            String algorithm = symmetricAlgorithmCombo.getValue();
            String mode = cipherModeCombo.getValue();
            String padding = paddingCombo.getValue();
            byte[] key = getSymmetricKeyBytes("decryption");

            byte[] decodedInput = decodeFileData(fileInput, effectiveOptions.getInputEncoding());
            byte[] processed;
            if (effectiveOptions.getProcessingMode() == FileProcessingMode.INDEPENDENT_BLOCKS) {
                processed = decryptIndependentBlocks(
                        decodedInput,
                        key,
                        algorithm,
                        mode,
                        padding,
                        effectiveOptions.getBlockSizeBytes());
            } else {
                processed = decryptSymmetricBytes(decodedInput, key, algorithm, mode, padding);
            }

            byte[] outputData = encodeFileData(processed, effectiveOptions.getOutputEncoding());
            Files.write(outputFile, outputData);

            outputArea.setText(
                    "EXPERT FILE DECRYPTION SUCCESS\n\n" +
                            "Input File: " + inputFile + "\n" +
                            "Output File: " + outputFile + "\n" +
                            "Algorithm: " + algorithm + "\n" +
                            "Mode: " + mode + "\n" +
                            "Padding: " + padding + "\n" +
                            "Processing: " + effectiveOptions.getProcessingMode() + "\n" +
                            "Block Size: " + effectiveOptions.getBlockSizeBytes() + " bytes\n" +
                            "Input Encoding: " + effectiveOptions.getInputEncoding() + "\n" +
                            "Output Encoding: " + effectiveOptions.getOutputEncoding() + "\n" +
                            "Decoded Input Size: " + decodedInput.length + " bytes\n" +
                            "Processed Size: " + processed.length + " bytes\n" +
                            "Written Size: " + outputData.length + " bytes");

            statusReporter.updateStatus("File decrypted: " + inputFile.getFileName() + " → " + outputFile.getFileName());

            Map<String, String> details = new HashMap<>();
            details.put("Type", "File Decryption");
            details.put("Algorithm", algorithm);
            details.put("Mode", mode);
            details.put("Padding", padding);
            details.put("Processing", effectiveOptions.getProcessingMode().name());
            details.put("Block Size", effectiveOptions.getBlockSizeBytes() + " bytes");
            details.put("Input Encoding", effectiveOptions.getInputEncoding().name());
            details.put("Output Encoding", effectiveOptions.getOutputEncoding().name());
            details.put("Input File", inputFile.toString());
            details.put("Output File", outputFile.toString());
            details.put("Decoded Input Size", decodedInput.length + " bytes");
            details.put("Processed Size", processed.length + " bytes");
            details.put("Written Size", outputData.length + " bytes");
            statusReporter.updateInspector("Symmetric File Decrypt (Expert)", decodedInput, processed, details);

            OperationHistory.getInstance().addOperation(
                    "Cipher",
                    "Decrypt File Expert - " + algorithm + "/" + mode,
                    inputFile.getFileName() + " (" + decodedInput.length + " bytes, "
                            + effectiveOptions.getInputEncoding().name() + ")",
                    outputFile.getFileName() + " (" + outputData.length + " bytes, "
                            + effectiveOptions.getOutputEncoding().name() + ")");

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (javax.crypto.AEADBadTagException e) {
            statusReporter.showError("Authentication Error",
                    "GCM/TAG verification failed for file decryption.");
        } catch (Exception e) {
            statusReporter.showError("Decryption Error",
                    "Error decrypting file: " + e.getMessage());
        }
    }

    /**
     * Analyze encrypted file with default options
     */
    public void handleAnalyzeEncryptedFile(Path inputFile) {
        handleAnalyzeEncryptedFile(inputFile, FileAnalysisOptions.defaults());
    }

    /**
     * Analyze encrypted file using brute-force strategy against candidate combinations.
     */
    public void handleAnalyzeEncryptedFile(Path inputFile, FileAnalysisOptions options) {
        try {
            if (inputFile == null) {
                statusReporter.showError("File Error", "Please select an encrypted file");
                return;
            }

            FileAnalysisOptions effectiveOptions = normalizeFileAnalysisOptions(options);
            byte[] key = getSymmetricKeyBytes("analysis");
            Path analysisDirectory = createAnalysisDirectory(inputFile);

            byte[] fileBytes = Files.readAllBytes(inputFile);
            if (fileBytes.length == 0) {
                statusReporter.showError("Input Error", "Input file is empty");
                return;
            }

            byte[] analysisBytes = fileBytes;
            boolean sampled = false;
            if (fileBytes.length > effectiveOptions.getSampleSizeBytes()) {
                analysisBytes = Arrays.copyOf(fileBytes, effectiveOptions.getSampleSizeBytes());
                sampled = true;
            }

            List<FileDataEncoding> encodingsToTest = resolveInputEncodings(effectiveOptions.getForcedInputEncoding());
            List<String> algorithms = getAlgorithmCandidatesByKeyLength(key.length);
            List<CipherCombination> combinations = buildCipherCombinations(algorithms);

            int attempts = 0;
            int successes = 0;
            int attemptIndex = 0;
            List<AnalysisCandidate> candidates = new ArrayList<>();
            List<AnalysisAttempt> attemptLog = new ArrayList<>();

            for (FileDataEncoding inputEncoding : encodingsToTest) {
                byte[] decodedCiphertext;
                try {
                    decodedCiphertext = decodeFileData(analysisBytes, inputEncoding);
                } catch (Exception decodeError) {
                    continue;
                }

                if (decodedCiphertext == null || decodedCiphertext.length == 0) {
                    continue;
                }

                if (effectiveOptions.isTestFullContent()) {
                    for (CipherCombination combo : combinations) {
                        attemptIndex++;
                        attempts++;
                        try {
                            byte[] plaintext = decryptSymmetricBytes(
                                    decodedCiphertext,
                                    key,
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding);
                            PaddingEvidence paddingEvidence = computePaddingEvidence(
                                    combo,
                                    "FULL_CONTENT",
                                    0,
                                    decodedCiphertext,
                                    key);
                            AnalysisCandidate candidate = buildAnalysisCandidate(
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding,
                                    "FULL_CONTENT",
                                    0,
                                    inputEncoding,
                                    plaintext,
                                    paddingEvidence);
                            candidates.add(candidate);
                            attemptLog.add(new AnalysisAttempt(
                                    attemptIndex,
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding,
                                    "FULL_CONTENT",
                                    0,
                                    inputEncoding,
                                    true,
                                    candidate.score,
                                    candidate.inferredPlainEncoding,
                                    candidate.preview,
                                    ""));
                            successes++;
                        } catch (Exception error) {
                            attemptLog.add(new AnalysisAttempt(
                                    attemptIndex,
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding,
                                    "FULL_CONTENT",
                                    0,
                                    inputEncoding,
                                    false,
                                    0,
                                    "",
                                    "",
                                    safeErrorMessage(error)));
                        }
                    }
                }

                if (effectiveOptions.isTestIndependentBlocks()) {
                    int structuredBlockSize = extractStructuredBlockSize(decodedCiphertext);
                    // 1) Structured independent-block format (native CryptoCarver expert mode).
                    for (CipherCombination combo : combinations) {
                        attemptIndex++;
                        attempts++;
                        try {
                            byte[] plaintext = decryptIndependentBlocks(
                                    decodedCiphertext,
                                    key,
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding,
                                    0);
                            PaddingEvidence paddingEvidence = computePaddingEvidence(
                                    combo,
                                    "INDEPENDENT_BLOCKS_STRUCTURED",
                                    structuredBlockSize,
                                    decodedCiphertext,
                                    key);
                            AnalysisCandidate candidate = buildAnalysisCandidate(
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding,
                                    "INDEPENDENT_BLOCKS_STRUCTURED",
                                    structuredBlockSize,
                                    inputEncoding,
                                    plaintext,
                                    paddingEvidence);
                            candidates.add(candidate);
                            attemptLog.add(new AnalysisAttempt(
                                    attemptIndex,
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding,
                                    "INDEPENDENT_BLOCKS_STRUCTURED",
                                    structuredBlockSize,
                                    inputEncoding,
                                    true,
                                    candidate.score,
                                    candidate.inferredPlainEncoding,
                                    candidate.preview,
                                    ""));
                            successes++;
                        } catch (Exception error) {
                            attemptLog.add(new AnalysisAttempt(
                                    attemptIndex,
                                    combo.algorithm,
                                    combo.mode,
                                    combo.padding,
                                    "INDEPENDENT_BLOCKS_STRUCTURED",
                                    structuredBlockSize,
                                    inputEncoding,
                                    false,
                                    0,
                                    "",
                                    "",
                                    safeErrorMessage(error)));
                        }
                    }

                    // 2) Heuristic independent-block mode on raw chunks with user-provided sizes.
                    for (int blockSize : effectiveOptions.getCandidateBlockSizes()) {
                        for (CipherCombination combo : combinations) {
                            attemptIndex++;
                            attempts++;
                            try {
                                byte[] plaintext = decryptIndependentBlocksRawGuess(
                                        decodedCiphertext,
                                        key,
                                        combo.algorithm,
                                        combo.mode,
                                        combo.padding,
                                        blockSize);
                                PaddingEvidence paddingEvidence = computePaddingEvidence(
                                        combo,
                                        "INDEPENDENT_BLOCKS_GUESS",
                                        blockSize,
                                        decodedCiphertext,
                                        key);
                                AnalysisCandidate candidate = buildAnalysisCandidate(
                                        combo.algorithm,
                                        combo.mode,
                                        combo.padding,
                                        "INDEPENDENT_BLOCKS_GUESS",
                                        blockSize,
                                        inputEncoding,
                                        plaintext,
                                        paddingEvidence);
                                candidates.add(candidate);
                                attemptLog.add(new AnalysisAttempt(
                                        attemptIndex,
                                        combo.algorithm,
                                        combo.mode,
                                        combo.padding,
                                        "INDEPENDENT_BLOCKS_GUESS",
                                        blockSize,
                                        inputEncoding,
                                        true,
                                        candidate.score,
                                        candidate.inferredPlainEncoding,
                                        candidate.preview,
                                        ""));
                                successes++;
                            } catch (Exception error) {
                                attemptLog.add(new AnalysisAttempt(
                                        attemptIndex,
                                        combo.algorithm,
                                        combo.mode,
                                        combo.padding,
                                        "INDEPENDENT_BLOCKS_GUESS",
                                        blockSize,
                                        inputEncoding,
                                        false,
                                        0,
                                        "",
                                        "",
                                        safeErrorMessage(error)));
                            }
                        }
                    }
                }
            }

            Path attemptsLogPath = analysisDirectory.resolve("attempts.csv");
            writeAttemptLog(attemptsLogPath, attemptLog);

            if (candidates.isEmpty()) {
                StringBuilder noResult = new StringBuilder();
                noResult.append("=== ENCRYPTED FILE ANALYSIS REPORT ===\n\n");
                noResult.append("No valid decryption candidates found.\n\n");
                noResult.append("File: ").append(inputFile).append("\n");
                noResult.append("File size: ").append(fileBytes.length).append(" bytes\n");
                noResult.append("Tested sample: ").append(analysisBytes.length).append(" bytes");
                if (sampled) {
                    noResult.append(" (sampled)");
                }
                noResult.append("\n");
                noResult.append("Attempts: ").append(attempts).append("\n");
                noResult.append("Successes: ").append(successes).append("\n\n");
                noResult.append("Analysis Directory: ").append(analysisDirectory).append("\n");
                noResult.append("Attempt Log: ").append(attemptsLogPath).append("\n\n");
                noResult.append("Tips:\n");
                noResult.append("- Verify key and IV/Nonce.\n");
                noResult.append("- Provide GCM/Auth TAG if needed.\n");
                noResult.append("- Try enabling more input encodings and chunk sizes.\n");
                outputArea.setText(noResult.toString());
                Files.writeString(analysisDirectory.resolve("report.txt"), noResult.toString(), StandardCharsets.UTF_8);
                writeHtmlReport(
                        analysisDirectory.resolve("report.html"),
                        inputFile,
                        fileBytes.length,
                        analysisBytes.length,
                        sampled,
                        attempts,
                        successes,
                        List.of(),
                        List.of(),
                        attemptsLogPath);
                statusReporter.updateStatus("Encrypted file analysis finished: no matches");
                return;
            }

            List<AnalysisCandidate> topCandidates = selectTopCandidates(candidates, effectiveOptions.getMaxResults());
            assignConfidencePercentages(topCandidates);
            List<AnalysisCandidate> probableCandidates = selectProbableCandidates(topCandidates);
            AnalysisCandidate best = topCandidates.get(0);

            String reportText = formatAnalysisReport(
                    inputFile,
                    fileBytes.length,
                    analysisBytes.length,
                    sampled,
                    attempts,
                    successes,
                    topCandidates,
                    probableCandidates,
                    analysisDirectory,
                    attemptsLogPath);
            outputArea.setText(reportText);
            Files.writeString(analysisDirectory.resolve("report.txt"), reportText, StandardCharsets.UTF_8);
            writeHtmlReport(
                    analysisDirectory.resolve("report.html"),
                    inputFile,
                    fileBytes.length,
                    analysisBytes.length,
                    sampled,
                    attempts,
                    successes,
                    topCandidates,
                    probableCandidates,
                    attemptsLogPath);

            Map<String, String> details = new HashMap<>();
            details.put("File", inputFile.toString());
            details.put("Attempts", String.valueOf(attempts));
            details.put("Successful Candidates", String.valueOf(successes));
            details.put("Best Algorithm", best.algorithm);
            details.put("Best Mode", best.mode);
            details.put("Best Padding", best.padding);
            details.put("Best Processing", best.processing);
            details.put("Best Input Encoding", best.inputEncoding.name());
            details.put("Best Inferred Plain Encoding", best.inferredPlainEncoding);
            details.put("Best Score", String.valueOf(best.score));
            details.put("Best Confidence", formatPercent(best.confidencePercent));
            details.put("Analysis Directory", analysisDirectory.toString());
            details.put("Attempt Log", attemptsLogPath.toString());
            statusReporter.updateInspector(
                    "Encrypted File Analysis",
                    analysisBytes,
                    best.plaintext.length > 4096 ? Arrays.copyOf(best.plaintext, 4096) : best.plaintext,
                    details);

            OperationHistory.getInstance().addOperation(
                    "Cipher",
                    "Analyze Encrypted File",
                    inputFile.getFileName() + " (" + fileBytes.length + " bytes)",
                    best.algorithm + "/" + best.mode + "/" + best.padding + " [" + best.processing + "]"
                            + " -> " + analysisDirectory.getFileName());

            statusReporter.updateStatus("Encrypted file analysis finished: best candidate "
                    + best.algorithm + "/" + best.mode + "/" + best.padding
                    + " | report: " + analysisDirectory.resolve("report.html"));

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Analysis Error", "Error analyzing encrypted file: " + e.getMessage());
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
            outputArea.setText(output);
            statusReporter.updateStatus("RSA encryption successful (" + padding + ")");

            // Update Inspector
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", "RSA");
            details.put("Padding", padding);
            if (currentPublicKey != null) {
                details.put("Key Size",
                        ((java.security.interfaces.RSAPublicKey) currentPublicKey).getModulus().bitLength() + " bits");
            }
            statusReporter.updateInspector("Asymmetric Encrypt", plaintext, ciphertext, details);

            OperationHistory.getInstance().addOperation(
                    "Cipher",
                    "RSA Encrypt - " + padding,
                    inputFormat + ": " + inputText.substring(0, Math.min(30, inputText.length())),
                    outputFormat + ": " + output.substring(0, Math.min(50, output.length())));

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
            statusReporter.updateStatus("RSA decryption successful (" + padding + ")");

            // Update Inspector
            java.util.Map<String, String> details = new java.util.HashMap<>();
            details.put("Algorithm", "RSA");
            details.put("Padding", padding);
            if (currentPrivateKey != null && currentPrivateKey instanceof java.security.interfaces.RSAPrivateKey) {
                details.put("Key Size",
                        ((java.security.interfaces.RSAPrivateKey) currentPrivateKey).getModulus().bitLength()
                                + " bits");
            }
            statusReporter.updateInspector("Asymmetric Decrypt", ciphertext, plaintext, details);

            // Add to history
            OperationHistory.getInstance().addOperation(
                    "Cipher",
                    "RSA Decrypt - " + padding,
                    inputFormat + ": " + inputText.substring(0, Math.min(30, inputText.length())),
                    outputFormat + ": " + output.substring(0, Math.min(50, output.length())));

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

    private ExpertFileOptions normalizeExpertOptions(ExpertFileOptions options) {
        if (options == null) {
            return ExpertFileOptions.defaults();
        }

        FileProcessingMode processingMode = options.getProcessingMode() != null
                ? options.getProcessingMode()
                : FileProcessingMode.FULL_CONTENT;
        int blockSize = options.getBlockSizeBytes() > 0
                ? options.getBlockSizeBytes()
                : DEFAULT_EXPERT_BLOCK_SIZE;
        FileDataEncoding inputEncoding = options.getInputEncoding() != null
                ? options.getInputEncoding()
                : FileDataEncoding.RAW;
        FileDataEncoding outputEncoding = options.getOutputEncoding() != null
                ? options.getOutputEncoding()
                : FileDataEncoding.RAW;

        return new ExpertFileOptions(processingMode, blockSize, inputEncoding, outputEncoding);
    }

    private byte[] decodeFileData(byte[] fileData, FileDataEncoding encoding) {
        switch (encoding) {
            case RAW:
                return fileData;
            case HEX: {
                String hex = new String(fileData, StandardCharsets.UTF_8).replaceAll("\\s+", "");
                return DataConverter.hexToBytes(hex);
            }
            case BASE64: {
                String b64 = new String(fileData, StandardCharsets.UTF_8).trim();
                return DataConverter.decodeBase64Flexible(b64);
            }
            case UTF8:
                return new String(fileData, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            case EBCDIC_CP037:
                return new String(fileData, CHARSET_EBCDIC_CP037).getBytes(StandardCharsets.UTF_8);
            case EBCDIC_CP500:
                return new String(fileData, CHARSET_EBCDIC_CP500).getBytes(StandardCharsets.UTF_8);
            default:
                throw new IllegalArgumentException("Unsupported input encoding: " + encoding);
        }
    }

    private byte[] encodeFileData(byte[] data, FileDataEncoding encoding) {
        switch (encoding) {
            case RAW:
                return data;
            case HEX:
                return DataConverter.bytesToHex(data).getBytes(StandardCharsets.UTF_8);
            case BASE64:
                return Base64.getEncoder().encode(data);
            case UTF8:
                return new String(data, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            case EBCDIC_CP037:
                return new String(data, StandardCharsets.UTF_8).getBytes(CHARSET_EBCDIC_CP037);
            case EBCDIC_CP500:
                return new String(data, StandardCharsets.UTF_8).getBytes(CHARSET_EBCDIC_CP500);
            default:
                throw new IllegalArgumentException("Unsupported output encoding: " + encoding);
        }
    }

    private byte[] encryptIndependentBlocks(byte[] data,
            byte[] key,
            String algorithm,
            String mode,
            String padding,
            int blockSize) throws Exception {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Block size must be greater than 0");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Input data cannot be empty");
        }

        if (SymmetricCipher.requiresIV(mode) || SymmetricCipher.isStreamCipher(algorithm)) {
            statusReporter.updateStatus(
                    "Expert block mode: current IV/Nonce is reused per independent block.");
        }

        int blockCount = (data.length + blockSize - 1) / blockSize;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(INDEPENDENT_BLOCK_MAGIC);
        output.write(ByteBuffer.allocate(4).putInt(blockSize).array());
        output.write(ByteBuffer.allocate(4).putInt(blockCount).array());

        for (int offset = 0; offset < data.length; offset += blockSize) {
            int end = Math.min(offset + blockSize, data.length);
            byte[] chunk = Arrays.copyOfRange(data, offset, end);
            byte[] encryptedChunk = encryptSymmetricBytes(chunk, key, algorithm, mode, padding);

            output.write(ByteBuffer.allocate(4).putInt(encryptedChunk.length).array());
            output.write(encryptedChunk);
        }

        return output.toByteArray();
    }

    private byte[] decryptIndependentBlocks(byte[] data,
            byte[] key,
            String algorithm,
            String mode,
            String padding,
            int expectedBlockSize) throws Exception {
        int minHeader = INDEPENDENT_BLOCK_MAGIC.length + 8;
        if (data.length < minHeader) {
            throw new IllegalArgumentException("Input too short for independent-block encrypted format");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] magic = new byte[INDEPENDENT_BLOCK_MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(magic, INDEPENDENT_BLOCK_MAGIC)) {
            throw new IllegalArgumentException(
                    "Invalid independent-block format header. Use full-content mode for raw ciphertext files.");
        }

        int storedBlockSize = buffer.getInt();
        int blockCount = buffer.getInt();
        if (blockCount < 0) {
            throw new IllegalArgumentException("Invalid block count in encrypted file");
        }
        if (expectedBlockSize > 0 && storedBlockSize != expectedBlockSize) {
            statusReporter.updateStatus("Note: configured block size (" + expectedBlockSize
                    + ") differs from file block size (" + storedBlockSize + "). Using file metadata.");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < blockCount; i++) {
            if (buffer.remaining() < 4) {
                throw new IllegalArgumentException("Corrupted block stream: missing block length for block " + (i + 1));
            }

            int encryptedLength = buffer.getInt();
            if (encryptedLength < 0 || encryptedLength > buffer.remaining()) {
                throw new IllegalArgumentException("Corrupted block stream: invalid block length at block " + (i + 1));
            }

            byte[] encryptedChunk = new byte[encryptedLength];
            buffer.get(encryptedChunk);
            byte[] plainChunk = decryptSymmetricBytes(encryptedChunk, key, algorithm, mode, padding);
            output.write(plainChunk);
        }

        if (buffer.hasRemaining()) {
            throw new IllegalArgumentException("Corrupted block stream: trailing bytes detected after last block");
        }

        return output.toByteArray();
    }

    private byte[] decryptIndependentBlocksRawGuess(byte[] data,
            byte[] key,
            String algorithm,
            String mode,
            String padding,
            int blockSize) throws Exception {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Block size must be greater than 0");
        }
        if (data.length < blockSize) {
            throw new IllegalArgumentException("Not enough data for configured block size");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int offset = 0; offset < data.length; offset += blockSize) {
            int end = Math.min(offset + blockSize, data.length);
            byte[] encryptedChunk = Arrays.copyOfRange(data, offset, end);
            byte[] plainChunk = decryptSymmetricBytes(encryptedChunk, key, algorithm, mode, padding);
            output.write(plainChunk);
        }

        return output.toByteArray();
    }

    private FileAnalysisOptions normalizeFileAnalysisOptions(FileAnalysisOptions options) {
        if (options == null) {
            return FileAnalysisOptions.defaults();
        }

        int[] blockSizes = options.getCandidateBlockSizes();
        if (blockSizes == null || blockSizes.length == 0) {
            blockSizes = new int[] { 512, 1024, 2048, 4096 };
        }

        List<Integer> normalizedSizes = new ArrayList<>();
        for (int size : blockSizes) {
            if (size > 0) {
                normalizedSizes.add(size);
            }
        }
        if (normalizedSizes.isEmpty()) {
            normalizedSizes.add(4096);
        }

        int[] cleanSizes = normalizedSizes.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
        int maxResults = options.getMaxResults() > 0 ? options.getMaxResults() : 8;
        int sampleSize = options.getSampleSizeBytes() > 0 ? options.getSampleSizeBytes() : 262144;
        boolean testFull = options.isTestFullContent();
        boolean testBlocks = options.isTestIndependentBlocks();

        if (!testFull && !testBlocks) {
            testFull = true;
        }

        return new FileAnalysisOptions(
                cleanSizes,
                testFull,
                testBlocks,
                maxResults,
                options.getForcedInputEncoding(),
                sampleSize);
    }

    private List<FileDataEncoding> resolveInputEncodings(FileDataEncoding forcedInputEncoding) {
        if (forcedInputEncoding != null) {
            return List.of(forcedInputEncoding);
        }
        return Arrays.asList(FileDataEncoding.values());
    }

    private List<String> getAlgorithmCandidatesByKeyLength(int keyLength) {
        List<String> candidates = new ArrayList<>();

        for (String algorithm : SymmetricCipher.SUPPORTED_ALGORITHMS) {
            if (algorithm.equals("DES") && keyLength == 8) {
                candidates.add(algorithm);
            } else if (algorithm.contains("3DES") && keyLength == 24) {
                candidates.add(algorithm);
            } else if (algorithm.equals("AES-128") && keyLength == 16) {
                candidates.add(algorithm);
            } else if (algorithm.equals("AES-192") && keyLength == 24) {
                candidates.add(algorithm);
            } else if (algorithm.equals("AES-256") && keyLength == 32) {
                candidates.add(algorithm);
            } else if (SymmetricCipher.isStreamCipher(algorithm) && keyLength == 32) {
                candidates.add(algorithm);
            }
        }

        if (candidates.isEmpty()) {
            candidates.addAll(SymmetricCipher.SUPPORTED_ALGORITHMS);
        }

        return candidates;
    }

    private List<CipherCombination> buildCipherCombinations(List<String> algorithms) {
        List<CipherCombination> combinations = new ArrayList<>();
        for (String algorithm : algorithms) {
            if (SymmetricCipher.isStreamCipher(algorithm)) {
                combinations.add(new CipherCombination(algorithm, "STREAM", "NoPadding"));
                continue;
            }

            for (String mode : SymmetricCipher.SUPPORTED_MODES) {
                if (SymmetricCipher.supportsPadding(mode)) {
                    for (String padding : SymmetricCipher.SUPPORTED_PADDINGS) {
                        // PKCS7 is normalized to PKCS5 in this app/provider stack, avoid duplicate attempts.
                        if ("PKCS7Padding".equals(padding)) {
                            continue;
                        }
                        combinations.add(new CipherCombination(algorithm, mode, padding));
                    }
                } else {
                    combinations.add(new CipherCombination(algorithm, mode, "NoPadding"));
                }
            }
        }
        return combinations;
    }

    private AnalysisCandidate buildAnalysisCandidate(String algorithm,
            String mode,
            String padding,
            String processing,
            int blockSize,
            FileDataEncoding inputEncoding,
            byte[] plaintext,
            PaddingEvidence paddingEvidence) {
        PlaintextQuality quality = evaluatePlaintextQuality(plaintext);
        int padAdj = paddingEvidence != null ? paddingEvidence.adjustment : 0;
        int totalScore = quality.score + padAdj;
        String qualitySummary = quality.summary;
        if (paddingEvidence != null && paddingEvidence.summary != null && !paddingEvidence.summary.isBlank()) {
            qualitySummary = qualitySummary + ", " + paddingEvidence.summary;
        }
        return new AnalysisCandidate(
                algorithm,
                mode,
                padding,
                processing,
                blockSize,
                inputEncoding,
                plaintext,
                quality.score,
                padAdj,
                totalScore,
                quality.inferredEncoding,
                qualitySummary,
                quality.preview,
                paddingEvidence != null ? paddingEvidence.summary : "");
    }

    private PaddingEvidence computePaddingEvidence(CipherCombination combo,
            String processing,
            int blockSize,
            byte[] decodedCiphertext,
            byte[] key) {
        try {
            if (combo == null || decodedCiphertext == null || decodedCiphertext.length == 0) {
                return new PaddingEvidence(0, "padAdj=0");
            }

            if (SymmetricCipher.isStreamCipher(combo.algorithm) || !SymmetricCipher.supportsPadding(combo.mode)) {
                return new PaddingEvidence(0, "padAdj=0 (mode without padding semantics)");
            }

            if (!"CBC".equalsIgnoreCase(combo.mode) && !"ECB".equalsIgnoreCase(combo.mode)) {
                return new PaddingEvidence(0, "padAdj=0 (padding evidence not applicable)");
            }

            List<byte[]> chunks = switch (processing) {
                case "FULL_CONTENT" -> List.of(decodedCiphertext);
                case "INDEPENDENT_BLOCKS_STRUCTURED" -> extractStructuredCipherChunks(decodedCiphertext);
                case "INDEPENDENT_BLOCKS_GUESS" -> splitCipherChunks(decodedCiphertext, blockSize);
                default -> List.of();
            };

            if (chunks.isEmpty()) {
                return new PaddingEvidence(-8, "padAdj=-8 (no chunks to validate)");
            }

            int blockCipherSize = getBlockCipherSize(combo.algorithm);
            int totalAdj = 0;
            int pkcsPatternCount = 0;
            int checked = 0;

            for (byte[] chunk : chunks) {
                if (chunk.length == 0 || chunk.length % blockCipherSize != 0) {
                    totalAdj -= 12;
                    continue;
                }

                byte[] rawPadded = decryptBlockChunkNoPadding(chunk, key, combo.algorithm, combo.mode);
                checked++;

                boolean pkcsPattern = isPkcsPaddingPattern(rawPadded, blockCipherSize);
                boolean iso7816Pattern = isIso7816PaddingPattern(rawPadded, blockCipherSize);
                int trailingZeros = countTrailingZeroBytes(rawPadded);
                if (pkcsPattern) {
                    pkcsPatternCount++;
                }

                switch (combo.padding) {
                    case "PKCS5Padding":
                    case "PKCS7Padding":
                        totalAdj += pkcsPattern ? 18 : -35;
                        break;
                    case "ISO10126Padding":
                        if (!hasValidPadLength(rawPadded, blockCipherSize)) {
                            totalAdj -= 30;
                        } else if (pkcsPattern) {
                            totalAdj -= 18;
                        } else {
                            totalAdj += 8;
                        }
                        break;
                    case "ISO7816-4Padding":
                        totalAdj += iso7816Pattern ? 16 : -24;
                        break;
                    case "ZeroBytePadding":
                        totalAdj += trailingZeros > 0 ? Math.min(8, trailingZeros) : -14;
                        break;
                    case "NoPadding":
                        totalAdj += pkcsPattern ? -10 : 2;
                        break;
                    default:
                        break;
                }
            }

            int adjustment = checked > 0 ? Math.round((float) totalAdj / (float) checked) : totalAdj;
            String summary = "padAdj=" + adjustment + " (" + pkcsPatternCount + "/" + Math.max(checked, 1)
                    + " pkcs-like blocks)";
            return new PaddingEvidence(adjustment, summary);

        } catch (Exception e) {
            return new PaddingEvidence(-6, "padAdj=-6 (" + safeErrorMessage(e) + ")");
        }
    }

    private List<byte[]> extractStructuredCipherChunks(byte[] data) {
        try {
            int minHeader = INDEPENDENT_BLOCK_MAGIC.length + 8;
            if (data.length < minHeader) {
                return List.of();
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] magic = new byte[INDEPENDENT_BLOCK_MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, INDEPENDENT_BLOCK_MAGIC)) {
                return List.of();
            }

            buffer.getInt(); // stored block size
            int blockCount = buffer.getInt();
            if (blockCount < 0) {
                return List.of();
            }

            List<byte[]> chunks = new ArrayList<>();
            for (int i = 0; i < blockCount; i++) {
                if (buffer.remaining() < 4) {
                    return List.of();
                }
                int len = buffer.getInt();
                if (len <= 0 || len > buffer.remaining()) {
                    return List.of();
                }
                byte[] chunk = new byte[len];
                buffer.get(chunk);
                chunks.add(chunk);
            }
            return chunks;
        } catch (Exception e) {
            return List.of();
        }
    }

    private int extractStructuredBlockSize(byte[] data) {
        try {
            int minHeader = INDEPENDENT_BLOCK_MAGIC.length + 8;
            if (data == null || data.length < minHeader) {
                return 0;
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] magic = new byte[INDEPENDENT_BLOCK_MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, INDEPENDENT_BLOCK_MAGIC)) {
                return 0;
            }

            int storedBlockSize = buffer.getInt();
            int blockCount = buffer.getInt();
            if (storedBlockSize <= 0 || blockCount < 0) {
                return 0;
            }
            return storedBlockSize;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<byte[]> splitCipherChunks(byte[] data, int blockSize) {
        if (data == null || data.length == 0 || blockSize <= 0) {
            return List.of();
        }
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += blockSize) {
            int end = Math.min(offset + blockSize, data.length);
            chunks.add(Arrays.copyOfRange(data, offset, end));
        }
        return chunks;
    }

    private int getBlockCipherSize(String algorithm) {
        if (algorithm != null && (algorithm.equals("DES") || algorithm.contains("3DES") || algorithm.contains("Triple"))) {
            return 8;
        }
        return 16;
    }

    private byte[] decryptBlockChunkNoPadding(byte[] chunk,
            byte[] key,
            String algorithm,
            String mode) throws Exception {
        byte[] iv = getIVForMode(mode);
        return SymmetricCipher.decrypt(chunk, key, algorithm, mode, "NoPadding", iv, null);
    }

    private boolean hasValidPadLength(byte[] rawPadded, int blockSize) {
        if (rawPadded == null || rawPadded.length == 0) {
            return false;
        }
        int padLen = rawPadded[rawPadded.length - 1] & 0xFF;
        return padLen >= 1 && padLen <= blockSize && padLen <= rawPadded.length;
    }

    private boolean isPkcsPaddingPattern(byte[] rawPadded, int blockSize) {
        if (!hasValidPadLength(rawPadded, blockSize)) {
            return false;
        }
        int padLen = rawPadded[rawPadded.length - 1] & 0xFF;
        for (int i = rawPadded.length - padLen; i < rawPadded.length; i++) {
            if ((rawPadded[i] & 0xFF) != padLen) {
                return false;
            }
        }
        return true;
    }

    private boolean isIso7816PaddingPattern(byte[] rawPadded, int blockSize) {
        if (!hasValidPadLength(rawPadded, blockSize)) {
            return false;
        }
        int markerIndex = -1;
        for (int i = rawPadded.length - 1; i >= Math.max(0, rawPadded.length - blockSize); i--) {
            int v = rawPadded[i] & 0xFF;
            if (v == 0x80) {
                markerIndex = i;
                break;
            }
            if (v != 0x00) {
                return false;
            }
        }
        return markerIndex >= 0;
    }

    private int countTrailingZeroBytes(byte[] data) {
        int count = 0;
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == 0x00) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private PlaintextQuality evaluatePlaintextQuality(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            return new PlaintextQuality(-100, "EMPTY", "Empty plaintext", "");
        }

        boolean utf8Valid = isValidUtf8(plaintext);
        String utf8Text = utf8Valid ? new String(plaintext, StandardCharsets.UTF_8) : "";
        double utf8Printable = utf8Valid ? computePrintableRatio(utf8Text) : 0.0;

        String cp037Text = new String(plaintext, CHARSET_EBCDIC_CP037);
        String cp500Text = new String(plaintext, CHARSET_EBCDIC_CP500);
        double cp037Printable = computePrintableRatio(cp037Text);
        double cp500Printable = computePrintableRatio(cp500Text);
        double bestEbcdic = Math.max(cp037Printable, cp500Printable);
        String bestEbcdicName = cp037Printable >= cp500Printable ? "EBCDIC_CP037_TEXT" : "EBCDIC_CP500_TEXT";

        boolean likelyHex = false;
        boolean likelyBase64 = false;
        if (utf8Valid) {
            String compact = utf8Text.replaceAll("\\s+", "");
            likelyHex = compact.length() >= 16 && compact.length() % 2 == 0
                    && compact.matches("[0-9A-Fa-f]+");
            likelyBase64 = compact.length() >= 16 && compact.length() % 4 == 0
                    && compact.matches("[A-Za-z0-9+/=]+");
            if (likelyBase64) {
                try {
                    Base64.getDecoder().decode(compact);
                } catch (Exception e) {
                    likelyBase64 = false;
                }
            }
        }

        double controlRatio = computeControlByteRatio(plaintext);
        int score = 0;
        if (utf8Valid) {
            score += (int) Math.round(utf8Printable * 60.0);
        }
        if (likelyHex) {
            score += 35;
        }
        if (likelyBase64) {
            score += 28;
        }
        if (bestEbcdic > 0.65) {
            score += (int) Math.round((bestEbcdic - 0.65) * 80.0);
        }
        score -= (int) Math.round(controlRatio * 35.0);

        String inferredEncoding;
        String preview;
        if (likelyHex) {
            inferredEncoding = "HEX_TEXT";
            preview = safePreview(utf8Text);
        } else if (likelyBase64) {
            inferredEncoding = "BASE64_TEXT";
            preview = safePreview(utf8Text);
        } else if (utf8Valid && utf8Printable >= 0.72) {
            inferredEncoding = "UTF8_TEXT";
            preview = safePreview(utf8Text);
        } else if (bestEbcdic >= 0.72) {
            inferredEncoding = bestEbcdicName;
            preview = safePreview(cp037Printable >= cp500Printable ? cp037Text : cp500Text);
        } else {
            inferredEncoding = "BINARY_OR_UNKNOWN";
            preview = DataConverter.bytesToHex(Arrays.copyOf(plaintext, Math.min(64, plaintext.length)));
        }

        String summary = String.format(
                Locale.ROOT,
                "utf8Printable=%.2f, ebcdicPrintable=%.2f, controlRatio=%.2f",
                utf8Printable,
                bestEbcdic,
                controlRatio);

        return new PlaintextQuality(score, inferredEncoding, summary, preview);
    }

    private boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private double computePrintableRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        int printable = 0;
        int total = text.length();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean isPrintable = ch == '\n' || ch == '\r' || ch == '\t'
                    || (ch >= 32 && ch <= 126)
                    || Character.isLetterOrDigit(ch)
                    || Character.isSpaceChar(ch);
            if (isPrintable) {
                printable++;
            }
        }
        return total == 0 ? 0.0 : (double) printable / (double) total;
    }

    private double computeControlByteRatio(byte[] data) {
        if (data == null || data.length == 0) {
            return 1.0;
        }
        int controls = 0;
        for (byte b : data) {
            int value = b & 0xFF;
            if (value < 9 || (value > 13 && value < 32) || value == 127) {
                controls++;
            }
        }
        return (double) controls / (double) data.length;
    }

    private String safePreview(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        if (compact.length() > 140) {
            return compact.substring(0, 140) + "...";
        }
        return compact;
    }

    private List<AnalysisCandidate> selectTopCandidates(List<AnalysisCandidate> candidates, int maxResults) {
        int limit = maxResults > 0 ? maxResults : 8;
        candidates.sort(Comparator.comparingInt((AnalysisCandidate c) -> c.score).reversed());

        Map<String, AnalysisCandidate> unique = new java.util.LinkedHashMap<>();
        List<AnalysisCandidate> ordered = new ArrayList<>();
        for (AnalysisCandidate candidate : candidates) {
            String signature = candidate.algorithm + "|" + candidate.mode + "|" + canonicalPaddingName(candidate.padding) + "|"
                    + candidate.processing + "|" + candidate.blockSize + "|" + candidate.inputEncoding + "|"
                    + candidate.inferredPlainEncoding;
            if (!unique.containsKey(signature)) {
                unique.put(signature, candidate);
                ordered.add(candidate);
                if (ordered.size() >= limit) {
                    break;
                }
            }
        }
        ordered.sort(Comparator.comparingInt((AnalysisCandidate c) -> c.score).reversed());
        return ordered;
    }

    private String canonicalPaddingName(String padding) {
        if ("PKCS5Padding".equals(padding) || "PKCS7Padding".equals(padding)) {
            return "PKCS5_OR_PKCS7";
        }
        return padding;
    }

    private void assignConfidencePercentages(List<AnalysisCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        int maxScore = candidates.stream().mapToInt(c -> c.score).max().orElse(0);
        final double temperature = 6.0;
        double[] weights = new double[candidates.size()];
        double sum = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            double exponent = ((double) candidates.get(i).score - (double) maxScore) / temperature;
            exponent = Math.max(-60.0, Math.min(60.0, exponent));
            double weight = Math.exp(exponent);
            weights[i] = weight;
            sum += weight;
        }

        if (sum <= 0.0 || Double.isNaN(sum) || Double.isInfinite(sum)) {
            double fallback = 100.0 / candidates.size();
            for (AnalysisCandidate candidate : candidates) {
                candidate.confidencePercent = fallback;
            }
            return;
        }

        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).confidencePercent = (weights[i] * 100.0) / sum;
        }
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private List<AnalysisCandidate> selectProbableCandidates(List<AnalysisCandidate> topCandidates) {
        if (topCandidates == null || topCandidates.isEmpty()) {
            return List.of();
        }

        AnalysisCandidate best = topCandidates.get(0);
        int threshold = Math.max(best.score - 5, 40);
        List<AnalysisCandidate> probable = new ArrayList<>();
        for (AnalysisCandidate candidate : topCandidates) {
            if (candidate.score >= threshold) {
                probable.add(candidate);
            }
            if (probable.size() >= 3) {
                break;
            }
        }
        return probable;
    }

    private String formatAnalysisReport(Path inputFile,
            int originalFileSize,
            int analyzedBytes,
            boolean sampled,
            int attempts,
            int successes,
            List<AnalysisCandidate> topCandidates,
            List<AnalysisCandidate> probableCandidates,
            Path analysisDirectory,
            Path attemptsLogPath) {
        StringBuilder report = new StringBuilder();
        report.append("=== ENCRYPTED FILE ANALYSIS REPORT ===\n\n");
        report.append("File: ").append(inputFile).append("\n");
        report.append("File size: ").append(originalFileSize).append(" bytes\n");
        report.append("Analyzed bytes: ").append(analyzedBytes).append(" bytes");
        if (sampled) {
            report.append(" (sampled)");
        }
        report.append("\n");
        report.append("Total attempts: ").append(attempts).append("\n");
        report.append("Successful decryptions: ").append(successes).append("\n\n");
        report.append("Analysis Directory: ").append(analysisDirectory).append("\n");
        report.append("Attempt Log: ").append(attemptsLogPath).append("\n\n");

        AnalysisCandidate best = topCandidates.get(0);
        report.append("Best candidate:\n");
        report.append("Algorithm: ").append(best.algorithm).append("\n");
        report.append("Mode: ").append(best.mode).append("\n");
        report.append("Padding: ").append(best.padding).append("\n");
        report.append("Processing: ").append(best.processing).append("\n");
        if (best.blockSize > 0) {
            report.append("Block size: ").append(best.blockSize).append(" bytes\n");
        }
        report.append("Encrypted file input encoding: ").append(best.inputEncoding).append("\n");
        report.append("Inferred plaintext encoding: ").append(best.inferredPlainEncoding).append("\n");
        report.append("Score: ").append(best.score).append("\n");
        report.append("Confidence: ").append(formatPercent(best.confidencePercent)).append("\n");
        report.append("Score breakdown: base=").append(best.baseScore)
                .append(" + paddingAdj=").append(best.paddingAdjustment)
                .append(" => ").append(best.score).append("\n");
        report.append("Quality: ").append(best.qualitySummary).append("\n");
        report.append("Preview: ").append(best.preview).append("\n\n");

        report.append("Most probable candidates:\n");
        int probableRank = 1;
        for (AnalysisCandidate candidate : probableCandidates) {
            report.append(probableRank).append(". ");
            report.append(candidate.algorithm).append("/").append(candidate.mode).append("/").append(candidate.padding);
            report.append(" | ").append(candidate.processing);
            if (candidate.blockSize > 0) {
                report.append(" (block=").append(candidate.blockSize).append(" bytes)");
            }
            report.append(" | enc=").append(candidate.inputEncoding);
            report.append(" | plain=").append(candidate.inferredPlainEncoding);
            report.append(" | score=").append(candidate.score)
                    .append(" (base=").append(candidate.baseScore)
                    .append(", padAdj=").append(candidate.paddingAdjustment)
                    .append(")")
                    .append(" | conf=").append(formatPercent(candidate.confidencePercent))
                    .append("\n");
            report.append("   preview: ").append(candidate.preview).append("\n");
            probableRank++;
        }
        report.append("\n");

        report.append("Top candidates:\n");
        int rank = 1;
        for (AnalysisCandidate candidate : topCandidates) {
            report.append(rank).append(". ");
            report.append(candidate.algorithm).append("/").append(candidate.mode).append("/").append(candidate.padding);
            report.append(" | ").append(candidate.processing);
            if (candidate.blockSize > 0) {
                report.append(" (block=").append(candidate.blockSize).append(" bytes)");
            }
            report.append(" | enc=").append(candidate.inputEncoding);
            report.append(" | plain=").append(candidate.inferredPlainEncoding);
            report.append(" | score=").append(candidate.score)
                    .append(" (base=").append(candidate.baseScore)
                    .append(", padAdj=").append(candidate.paddingAdjustment)
                    .append(")")
                    .append(" | conf=").append(formatPercent(candidate.confidencePercent))
                    .append("\n");
            report.append("   quality: ").append(candidate.qualitySummary).append("\n");
            report.append("   preview: ").append(candidate.preview).append("\n");
            rank++;
        }

        report.append("\nNotes:\n");
        report.append("- INDEPENDENT_BLOCKS_STRUCTURED means CryptoCarver expert block container matched.\n");
        report.append("- INDEPENDENT_BLOCKS_GUESS means heuristic split-by-size decryption.\n");
        report.append("- Score formula: final = base plaintext quality + padding adjustment.\n");
        report.append("- Confidence % is a softmax normalization over displayed top candidates (temperature=6).\n");
        report.append("- Confidence % is comparative, not an absolute proof of correctness.\n");
        report.append("- PKCS5Padding and PKCS7Padding are equivalent in this Java/provider setup and are grouped.\n");
        report.append("- Candidate chunk sizes in options are interpreted as BYTES by default.\n");
        report.append("- Chunk size is file splitting size for independent processing, not cipher primitive block size.\n");
        report.append("- Ranking is heuristic and should be validated with domain context.\n");
        return report.toString();
    }

    private Path createAnalysisDirectory(Path inputFile) throws Exception {
        Path parent = inputFile.toAbsolutePath().getParent();
        if (parent == null) {
            parent = Path.of(".");
        }

        String rawName = inputFile.getFileName() != null ? inputFile.getFileName().toString() : "encrypted_file";
        String safeName = rawName.replaceAll("[^A-Za-z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path analysisDir = parent.resolve("analysis_" + safeName + "_" + timestamp);
        Files.createDirectories(analysisDir);
        return analysisDir;
    }

    private String safeErrorMessage(Exception error) {
        if (error == null || error.getMessage() == null) {
            return "Unknown error";
        }
        return error.getClass().getSimpleName() + ": " + error.getMessage().replace("\n", " ").replace("\r", " ");
    }

    private void writeAttemptLog(Path csvPath, List<AnalysisAttempt> attempts) throws Exception {
        StringBuilder csv = new StringBuilder();
        csv.append("index,algorithm,mode,padding,processing,block_size,input_encoding,success,score,inferred_plain_encoding,preview,error\n");
        for (AnalysisAttempt attempt : attempts) {
            csv.append(attempt.index).append(",");
            csv.append(csvEscape(attempt.algorithm)).append(",");
            csv.append(csvEscape(attempt.mode)).append(",");
            csv.append(csvEscape(attempt.padding)).append(",");
            csv.append(csvEscape(attempt.processing)).append(",");
            csv.append(attempt.blockSize).append(",");
            csv.append(csvEscape(attempt.inputEncoding != null ? attempt.inputEncoding.name() : "")).append(",");
            csv.append(attempt.success).append(",");
            csv.append(attempt.score).append(",");
            csv.append(csvEscape(attempt.inferredPlainEncoding)).append(",");
            csv.append(csvEscape(attempt.preview)).append(",");
            csv.append(csvEscape(attempt.error)).append("\n");
        }
        Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void writeHtmlReport(Path htmlPath,
            Path inputFile,
            int originalFileSize,
            int analyzedBytes,
            boolean sampled,
            int attempts,
            int successes,
            List<AnalysisCandidate> topCandidates,
            List<AnalysisCandidate> probableCandidates,
            Path attemptsLogPath) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        html.append("<title>CryptoCarver Analysis Report</title>");
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;margin:24px;background:#f8fafc;color:#0f172a;}");
        html.append("h1,h2{margin:0 0 12px 0;} .card{background:#fff;border:1px solid #e2e8f0;border-radius:10px;padding:16px;margin-bottom:16px;}");
        html.append("table{width:100%;border-collapse:collapse;} th,td{border:1px solid #e2e8f0;padding:8px;vertical-align:top;text-align:left;}");
        html.append("th{background:#f1f5f9;} .probable{border:2px solid #16a34a;} code{background:#f1f5f9;padding:2px 4px;border-radius:4px;}");
        html.append("</style></head><body>");
        html.append("<h1>Encrypted File Analysis Report</h1>");

        html.append("<div class=\"card\">");
        html.append("<p><strong>File:</strong> ").append(htmlEscape(String.valueOf(inputFile))).append("</p>");
        html.append("<p><strong>File size:</strong> ").append(originalFileSize).append(" bytes</p>");
        html.append("<p><strong>Analyzed bytes:</strong> ").append(analyzedBytes).append(" bytes");
        if (sampled) {
            html.append(" (sampled)");
        }
        html.append("</p>");
        html.append("<p><strong>Total attempts:</strong> ").append(attempts).append("</p>");
        html.append("<p><strong>Successful decryptions:</strong> ").append(successes).append("</p>");
        html.append("<p><strong>Attempt log CSV:</strong> ").append(htmlEscape(String.valueOf(attemptsLogPath))).append("</p>");
        html.append("</div>");

        if (topCandidates.isEmpty()) {
            html.append("<div class=\"card\"><h2>No candidates found</h2><p>No valid decryption candidates were produced.</p></div>");
        } else {
            AnalysisCandidate best = topCandidates.get(0);
            html.append("<div class=\"card probable\">");
            html.append("<h2>Most probable result</h2>");
            html.append("<p><strong>Algorithm:</strong> ").append(htmlEscape(best.algorithm)).append("</p>");
            html.append("<p><strong>Mode/Padding:</strong> ").append(htmlEscape(best.mode)).append(" / ")
                    .append(htmlEscape(best.padding)).append("</p>");
            html.append("<p><strong>Processing:</strong> ").append(htmlEscape(best.processing)).append("</p>");
            if (best.blockSize > 0) {
                html.append("<p><strong>Block size:</strong> ").append(best.blockSize).append(" bytes</p>");
            }
            html.append("<p><strong>Input encoding:</strong> ").append(htmlEscape(best.inputEncoding.name())).append("</p>");
            html.append("<p><strong>Inferred plaintext encoding:</strong> ").append(htmlEscape(best.inferredPlainEncoding))
                    .append("</p>");
            html.append("<p><strong>Score:</strong> ").append(best.score).append("</p>");
            html.append("<p><strong>Confidence:</strong> ").append(formatPercent(best.confidencePercent)).append("</p>");
            html.append("<p><strong>Score breakdown:</strong> base=").append(best.baseScore)
                    .append(" + paddingAdj=").append(best.paddingAdjustment)
                    .append(" =&gt; ").append(best.score).append("</p>");
            html.append("<p><strong>Quality:</strong> ").append(htmlEscape(best.qualitySummary)).append("</p>");
            html.append("<p><strong>Preview:</strong> <code>").append(htmlEscape(best.preview)).append("</code></p>");
            html.append("</div>");

            html.append("<div class=\"card\">");
            html.append("<h2>Most probable candidates</h2>");
            html.append("<table><thead><tr><th>#</th><th>Candidate</th><th>Processing</th><th>Input Enc.</th><th>Plain Enc.</th><th>Score</th><th>Confidence</th><th>Preview</th></tr></thead><tbody>");
            int i = 1;
            for (AnalysisCandidate candidate : probableCandidates) {
                html.append("<tr>");
                html.append("<td>").append(i++).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.algorithm + "/" + candidate.mode + "/" + candidate.padding))
                        .append("</td>");
                html.append("<td>").append(htmlEscape(candidate.processing));
                if (candidate.blockSize > 0) {
                    html.append(" (block=").append(candidate.blockSize).append(" bytes)");
                }
                html.append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inputEncoding.name())).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inferredPlainEncoding)).append("</td>");
                html.append("<td>").append(candidate.score)
                        .append(" (").append(candidate.baseScore)
                        .append(" + ").append(candidate.paddingAdjustment)
                        .append(")</td>");
                html.append("<td>").append(formatPercent(candidate.confidencePercent)).append("</td>");
                html.append("<td><code>").append(htmlEscape(candidate.preview)).append("</code></td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
            html.append("</div>");

            html.append("<div class=\"card\">");
            html.append("<h2>Top candidates</h2>");
            html.append("<table><thead><tr><th>#</th><th>Candidate</th><th>Processing</th><th>Input Enc.</th><th>Plain Enc.</th><th>Score</th><th>Confidence</th><th>Quality</th><th>Preview</th></tr></thead><tbody>");
            int rank = 1;
            for (AnalysisCandidate candidate : topCandidates) {
                html.append("<tr>");
                html.append("<td>").append(rank++).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.algorithm + "/" + candidate.mode + "/" + candidate.padding))
                        .append("</td>");
                html.append("<td>").append(htmlEscape(candidate.processing));
                if (candidate.blockSize > 0) {
                    html.append(" (block=").append(candidate.blockSize).append(" bytes)");
                }
                html.append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inputEncoding.name())).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.inferredPlainEncoding)).append("</td>");
                html.append("<td>").append(candidate.score)
                        .append(" (").append(candidate.baseScore)
                        .append(" + ").append(candidate.paddingAdjustment)
                        .append(")</td>");
                html.append("<td>").append(formatPercent(candidate.confidencePercent)).append("</td>");
                html.append("<td>").append(htmlEscape(candidate.qualitySummary)).append("</td>");
                html.append("<td><code>").append(htmlEscape(candidate.preview)).append("</code></td>");
                html.append("</tr>");
            }
            html.append("</tbody></table>");
            html.append("</div>");

            html.append("<div class=\"card\">");
            html.append("<h2>Scoring notes</h2>");
            html.append("<ul>");
            html.append("<li>Final score = base plaintext quality + padding adjustment.</li>");
            html.append("<li>Confidence % is a softmax normalization over displayed top candidates (temperature=6).</li>");
            html.append("<li>Confidence % is comparative, not an absolute proof of correctness.</li>");
            html.append("<li>PKCS5Padding and PKCS7Padding are equivalent in this Java/provider setup and are grouped.</li>");
            html.append("<li>Candidate chunk sizes are interpreted as bytes by default in the analyzer dialog.</li>");
            html.append("<li>Chunk size is file splitting size for independent processing, not cipher primitive block size.</li>");
            html.append("</ul>");
            html.append("</div>");
        }

        html.append("</body></html>");
        Files.writeString(htmlPath, html.toString(), StandardCharsets.UTF_8);
    }

    private String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private byte[] getSymmetricKeyBytes(String operation) {
        String keyHex = symmetricKeyField != null ? symmetricKeyField.getText().trim() : "";
        if (keyHex.isEmpty()) {
            throw new IllegalArgumentException("Please enter " + operation + " key in hexadecimal");
        }
        return DataConverter.hexToBytes(keyHex);
    }

    private byte[] getNonceBytes(String algorithm) {
        String ivHex = ivField != null ? ivField.getText().trim() : "";
        if (ivHex.isEmpty()) {
            throw new IllegalArgumentException(algorithm + " requires an IV/Nonce");
        }
        return DataConverter.hexToBytes(ivHex);
    }

    private byte[] getIVForMode(String mode) {
        if (!SymmetricCipher.requiresIV(mode)) {
            return null;
        }

        String ivHex = ivField != null ? ivField.getText().trim() : "";
        if (ivHex.isEmpty()) {
            throw new IllegalArgumentException(mode + " mode requires an Initialization Vector (IV)");
        }

        return DataConverter.hexToBytes(ivHex);
    }

    private byte[] getAADBytes() {
        if (aadField == null || aadField.getText().isEmpty() || aadField.isDisabled()) {
            return null;
        }

        String aadText = aadField.getText().trim();
        try {
            return DataConverter.hexToBytes(aadText);
        } catch (Exception e) {
            return aadText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    private byte[] getTagBytes(boolean required, String algorithmLabel) {
        String tagHex = gcmTagField != null ? gcmTagField.getText().trim() : "";
        if (tagHex.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(algorithmLabel + " requires an Auth Tag for decryption");
            }
            return null;
        }

        byte[] tag = DataConverter.hexToBytes(tagHex);
        if (tag.length != 16) {
            throw new IllegalArgumentException("Auth Tag must be 16 bytes (32 hex chars)");
        }
        return tag;
    }

    private byte[] appendTag(byte[] ciphertext, byte[] tag) {
        if (tag == null) {
            return ciphertext;
        }
        byte[] combined = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
        System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
        return combined;
    }

    private byte[] encryptSymmetricBytes(byte[] plaintext, byte[] key, String algorithm, String mode, String padding)
            throws Exception {
        if ("Salsa20".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.encrypt(plaintext, key, algorithm, "None", "NoPadding", nonce);
        }
        if ("ChaCha20".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.encryptChaCha20(plaintext, key, nonce);
        }
        if ("ChaCha20-Poly1305".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.encryptChaCha20Poly1305(plaintext, key, nonce);
        }
        if ("XChaCha20-Poly1305".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.encryptXChaCha20Poly1305(plaintext, key, nonce);
        }

        byte[] iv = getIVForMode(mode);
        byte[] aadBytes = getAADBytes();
        return SymmetricCipher.encrypt(plaintext, key, algorithm, mode, padding, iv, aadBytes);
    }

    private byte[] decryptSymmetricBytes(byte[] ciphertext, byte[] key, String algorithm, String mode, String padding)
            throws Exception {
        if ("Salsa20".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.decrypt(ciphertext, key, algorithm, "None", "NoPadding", nonce);
        }
        if ("ChaCha20".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            return SymmetricCipher.decryptChaCha20(ciphertext, key, nonce);
        }
        if ("ChaCha20-Poly1305".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            byte[] tag = getTagBytes(false, "ChaCha20-Poly1305");
            byte[] combined = tag != null ? SymmetricCipher.combineChaCha20CiphertextAndTag(ciphertext, tag) : ciphertext;
            return SymmetricCipher.decryptChaCha20Poly1305(combined, key, nonce);
        }
        if ("XChaCha20-Poly1305".equals(algorithm)) {
            byte[] nonce = getNonceBytes(algorithm);
            byte[] tag = getTagBytes(false, "XChaCha20-Poly1305");
            byte[] combined = tag != null ? SymmetricCipher.combineChaCha20CiphertextAndTag(ciphertext, tag) : ciphertext;
            return SymmetricCipher.decryptXChaCha20Poly1305(combined, key, nonce);
        }

        byte[] iv = getIVForMode(mode);
        byte[] aadBytes = getAADBytes();
        if ("GCM".equalsIgnoreCase(mode)) {
            byte[] optionalTag = getTagBytes(false, "GCM");
            ciphertext = appendTag(ciphertext, optionalTag);
        }
        return SymmetricCipher.decrypt(ciphertext, key, algorithm, mode, padding, iv, aadBytes);
    }

}
