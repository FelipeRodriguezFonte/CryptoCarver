package com.cryptocarver.ui;

import com.cryptocarver.crypto.PostQuantumOperations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Controller for Post-Quantum Cryptography operations
 */
public class PostQuantumController {

    private static final Logger LOG = LoggerFactory.getLogger(PostQuantumController.class);

    private StatusReporter statusReporter;

    @FXML
    private Accordion pqcAccordion;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    // UI Components - Key Gen
    @FXML
    private ComboBox<String> pqcAlgorithmCombo;
    @FXML
    private Button pqcGenerateKeyBtn;
    @FXML
    private TextArea pqcPublicKeyArea;
    @FXML
    private TextArea pqcPrivateKeyArea;
    @FXML
    private TextArea pqcKeyDetailsArea;
    @FXML
    private Label pqcKeyStatusLabel;

    // UI Components - Sign/Verify
    @FXML
    private ComboBox<String> pqcSignAlgoCombo;
    @FXML
    private TextArea pqcSignInputArea;
    @FXML
    private TextArea pqcSignOutputArea; // Signature
    @FXML
    private TextField pqcVerifySignatureField;

    // UI Components - KEM
    @FXML private ComboBox<String> pqcKemAlgoCombo;
    @FXML private TextArea pqcKemCiphertextArea;
    @FXML private TextField pqcKemSharedSecretField;
    @FXML private TextField pqcAliceSecretField;
    @FXML private Label pqcKemStatusLabel;
    @FXML private MenuButton pqcKeyShelfMenu;
    @FXML private MenuButton pqcVerifyShelfMenu;

    // Benchmark
    @FXML private ComboBox<String> pqcBenchmarkAlgoCombo;
    @FXML private Button pqcBenchmarkBtn;
    @FXML private ProgressIndicator pqcBenchmarkProgress;
    @FXML private TextArea pqcBenchmarkArea;
    private com.cryptocarver.crypto.pqc.PQCBenchmark activeBenchmarkTask;


    // Internal state
    private PublicKey currentPublicKey;
    private PrivateKey currentPrivateKey;
    private byte[] bobSecret;

    PublicKey getCurrentPublicKey() { return currentPublicKey; }
    PrivateKey getCurrentPrivateKey() { return currentPrivateKey; }

    public PostQuantumController() {
    }

    public void initModule(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(pqcAccordion, ModuleTextCatalog.pqc());
        com.cryptocarver.service.I18nService.getInstance().addLocaleChangeListener(locale -> {
            if (pqcKeyStatusLabel != null && currentPublicKey == null) pqcKeyStatusLabel.setText(t("status.ready"));
        });
        IngestionUIHelper.bindField(pqcSignInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(pqcVerifySignatureField, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
        IngestionUIHelper.bindField(pqcPublicKeyArea, pqcKeyStatusLabel, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY);
        IngestionUIHelper.bindField(pqcPrivateKeyArea, pqcKeyStatusLabel, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);

        // Populate Key Gen combo
        pqcAlgorithmCombo.getItems().addAll("--- Key Encapsulation (KEM) ---");
        pqcAlgorithmCombo.getItems().addAll(PostQuantumOperations.ML_KEM_ALGORITHMS);
        pqcAlgorithmCombo.getItems().addAll("--- Digital Signatures ---");
        pqcAlgorithmCombo.getItems().addAll(PostQuantumOperations.ML_DSA_ALGORITHMS);
        pqcAlgorithmCombo.getItems().addAll(PostQuantumOperations.SLH_DSA_ALGORITHMS);
        pqcAlgorithmCombo.setValue(PostQuantumOperations.ML_DSA_ALGORITHMS.get(0)); // Default Dilithium2

        // Populate Sign/Verify combo
        pqcSignAlgoCombo.getItems().addAll(PostQuantumOperations.ML_DSA_ALGORITHMS);
        pqcSignAlgoCombo.getItems().addAll(PostQuantumOperations.SLH_DSA_ALGORITHMS);
        pqcSignAlgoCombo.setValue(PostQuantumOperations.ML_DSA_ALGORITHMS.get(0));

        if (pqcKemAlgoCombo != null) {
            pqcKemAlgoCombo.getItems().setAll(PostQuantumOperations.ML_KEM_ALGORITHMS);
            pqcKemAlgoCombo.setValue("ML-KEM-512");
        }

        if (pqcBenchmarkAlgoCombo != null) {
            pqcBenchmarkAlgoCombo.getItems().addAll(PostQuantumOperations.ML_KEM_ALGORITHMS);
            pqcBenchmarkAlgoCombo.getItems().addAll(PostQuantumOperations.ML_DSA_ALGORITHMS);
            pqcBenchmarkAlgoCombo.getItems().addAll(PostQuantumOperations.SLH_DSA_ALGORITHMS);
            pqcBenchmarkAlgoCombo.setValue("ML-KEM-512");
        }
    }

    public void expandAccordionPane(String itemName) {
        if (pqcAccordion == null) return;
        for (TitledPane pane : pqcAccordion.getPanes()) {
            if (pane.getText().contains(itemName)) {
                pqcAccordion.setExpandedPane(pane);
                break;
            }
        }
    }

    @FXML
    public void handleGeneratePQCKeyPair() {
        try {
            String algo = pqcAlgorithmCombo.getValue();
            if (algo == null || algo.startsWith("---")) {
                if (statusReporter != null) statusReporter.showError("Algorithm Error", "Please select a valid algorithm");
                return;
            }

            Callable<KeyPair> task = () -> PostQuantumOperations.generateKeyPair(algo);

            Consumer<KeyPair> onSuccess = kp -> {
                try {
                    currentPublicKey = kp.getPublic();
                    currentPrivateKey = kp.getPrivate();

                    String pubHex = DataConverter.bytesToHex(currentPublicKey.getEncoded());
                    String privHex = DataConverter.bytesToHex(currentPrivateKey.getEncoded());

                    try {
                        pqcPublicKeyArea.setText("-----BEGIN PUBLIC KEY-----\n" +
                            java.util.Base64.getEncoder().encodeToString(currentPublicKey.getEncoded()) +
                            "\n-----END PUBLIC KEY-----");

                        pqcPrivateKeyArea.setText("-----BEGIN PRIVATE KEY-----\n" +
                            java.util.Base64.getEncoder().encodeToString(currentPrivateKey.getEncoded()) +
                            "\n-----END PRIVATE KEY-----");
                    } catch (Exception e) {
                        pqcPublicKeyArea.setText(pubHex);
                        pqcPrivateKeyArea.setText(privHex);
                    }

                    if (pqcKeyStatusLabel != null) {
                        pqcKeyStatusLabel.setText("Generated " + algo + " Key Pair");
                    }
                    java.util.List<com.cryptocarver.model.OperationDetail> details = describeKeyPair(algo, "Generated");
                    if (pqcKeyDetailsArea != null) pqcKeyDetailsArea.setText(formatDetails(details));
                    if (statusReporter != null) {
                        statusReporter.publish(OperationResult.forOperation("PQC Key Generation")
                                .output(currentPublicKey.getEncoded())
                                .details(detailsWithKeyMaterial(details))
                                .status("Generated " + algo + " Key Pair")
                                .build());
                    }
                } catch (Exception e) {
                    if (statusReporter != null) statusReporter.showError("Generation Error", "Error generating key: " + e.getMessage());
                }
            };

            Consumer<Throwable> onFailure = err -> {
                if (statusReporter != null) statusReporter.showError("Generation Error", "Error generating key: " + (err != null ? err.getMessage() : "Unknown error"));
            };

            Runnable onCancelled = () -> {
                if (pqcKeyStatusLabel != null) pqcKeyStatusLabel.setText(t("module.pqc.cancelled"));
                if (statusReporter != null) statusReporter.updateStatus(t("module.pqc.cancelled"));
            };

            if (statusReporter != null && statusReporter.getOperationExecutor() != null) {
                statusReporter.getOperationExecutor().execute("PQC-" + algo + " Key Generation", pqcGenerateKeyBtn, task, onSuccess, onFailure, onCancelled);
            } else {
                KeyPair kp = task.call();
                onSuccess.accept(kp);
            }

        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Generation Error", "Error generating key: " + e.getMessage());
            LOG.error("PQC key generation failed", e);
        }
    }

    @FXML
    public void handleImportPQCKeys() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PQC public and/or private PEM keys");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM files", "*.pem", "*.pub", "*.key"));
        java.util.List<File> files = chooser.showOpenMultipleDialog(null);
        if (files == null || files.isEmpty()) return;

        java.util.List<String> pems = new java.util.ArrayList<>();
        try {
            for (File f : files) {
                pems.add(Files.readString(f.toPath(), StandardCharsets.US_ASCII));
            }
            importKeysFromContents(pems);
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Import Error", "Failed to load keys: " + e.getMessage());
        }
    }

    public void importKeysFromContents(java.util.List<String> pems) throws Exception {
        String lastDetectedAlgorithm = null;
        PublicKey tempPubKey = null;
        PrivateKey tempPrivKey = null;
        String tempPubKeyStr = null;
        String tempPrivKeyStr = null;

        for (String pem : pems) {
            byte[] encoded = decodePem(pem);
            boolean isPublic = pem.contains("BEGIN PUBLIC KEY");
            boolean isPrivate = pem.contains("BEGIN PRIVATE KEY");
            if (!isPublic && !isPrivate) {
                throw new IllegalArgumentException("Content is not a PKCS#8 or X.509 PEM key");
            }

            com.cryptocarver.crypto.PostQuantumOperations.PqcAlgorithmDetectionResult result = PostQuantumOperations.detectAlgorithmFromEncoded(encoded, isPublic);
            if (result == null || !result.isSupported()) {
                String oid = result != null ? result.originalOid() : "Unknown";
                throw new IllegalArgumentException("Cannot detect PQC algorithm from PEM (OID: " + oid + "). Ensure you are using a supported NIST parameter set.");
            }

            String detectedAlgorithm = result.nistName();
            if (lastDetectedAlgorithm != null && !lastDetectedAlgorithm.equals(detectedAlgorithm)) {
                throw new IllegalArgumentException("Mismatched keys: You are trying to load a " + lastDetectedAlgorithm + " key and a " + detectedAlgorithm + " key simultaneously.");
            }
            lastDetectedAlgorithm = detectedAlgorithm;

            if (isPublic) {
                tempPubKey = PostQuantumOperations.importPublicKey(detectedAlgorithm, encoded);
                tempPubKeyStr = pem.trim();
            } else {
                tempPrivKey = PostQuantumOperations.importPrivateKey(detectedAlgorithm, encoded);
                tempPrivKeyStr = pem.trim();
            }
        }

        // All files verified successfully, apply state
        if (tempPubKey != null) {
            currentPublicKey = tempPubKey;
            if (pqcPublicKeyArea != null) pqcPublicKeyArea.setText(tempPubKeyStr);
        }
        if (tempPrivKey != null) {
            currentPrivateKey = tempPrivKey;
            if (pqcPrivateKeyArea != null) pqcPrivateKeyArea.setText(tempPrivKeyStr);
        }

        // Update combo if it's one of the known primary names
        if (lastDetectedAlgorithm != null && pqcAlgorithmCombo != null) {
            if (PostQuantumOperations.ML_KEM_ALGORITHMS.contains(lastDetectedAlgorithm)) {
                pqcAlgorithmCombo.setValue(lastDetectedAlgorithm);
                if (pqcKemAlgoCombo != null) pqcKemAlgoCombo.setValue(lastDetectedAlgorithm);
            } else if (PostQuantumOperations.ML_DSA_ALGORITHMS.contains(lastDetectedAlgorithm) || PostQuantumOperations.SLH_DSA_ALGORITHMS.contains(lastDetectedAlgorithm)) {
                pqcAlgorithmCombo.setValue(lastDetectedAlgorithm);
                if (pqcSignAlgoCombo != null) pqcSignAlgoCombo.setValue(lastDetectedAlgorithm);
            }
        }

        if (lastDetectedAlgorithm != null) {
            if (pqcKeyStatusLabel != null) pqcKeyStatusLabel.setText("Imported PQC key material for " + lastDetectedAlgorithm);
            java.util.List<com.cryptocarver.model.OperationDetail> details = describeKeyPair(lastDetectedAlgorithm, "Imported");
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PQC Import")
                        .details(details).status("Success").build());
            }
        }
    }

    @FXML
    public void handleExportPQCPublicKey() {
        exportKey(currentPublicKey, "pqc-public-key.pem", "PUBLIC KEY");
    }

    @FXML
    public void handleExportPQCPrivateKey() {
        exportKey(currentPrivateKey, "pqc-private-key.pem", "PRIVATE KEY");
    }

    private void exportKey(java.security.Key key, String filename, String pemType) {
        if (key == null) {
            if (statusReporter != null) statusReporter.showError("PQC Export Error", "Generate or import a key first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PQC " + pemType);
        chooser.setInitialFileName(filename);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM files", "*.pem"));
        File file = chooser.showSaveDialog(null);
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), toPem(pemType, key.getEncoded()), StandardCharsets.US_ASCII);
            java.util.List<com.cryptocarver.model.OperationDetail> details = java.util.List.of(
                com.cryptocarver.model.OperationDetail.publicDetail("Key Type", pemType),
                com.cryptocarver.model.OperationDetail.publicDetail("Output", file.getAbsolutePath())
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PQC Key Export")
                        .output(key.getEncoded())
                        .details(details)
                        .status("PQC key exported: " + file.getName())
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("PQC Export Error", "Unable to export key: " + e.getMessage());
        }
    }

    private String toPem(String type, byte[] encoded) {
        String base64 = java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    private byte[] decodePem(String pem) {
        String normalized = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return java.util.Base64.getDecoder().decode(normalized);
    }

    private java.util.List<com.cryptocarver.model.OperationDetail> describeKeyPair(String selectedAlgorithm, String source) {
        java.util.List<com.cryptocarver.model.OperationDetail> details = new java.util.ArrayList<>();
        details.add(com.cryptocarver.model.OperationDetail.publicDetail("Operation", source + " PQC key pair"));
        details.add(com.cryptocarver.model.OperationDetail.publicDetail("Parameter Set", selectedAlgorithm));
        details.add(com.cryptocarver.model.OperationDetail.publicDetail("Purpose", isKemAlgorithm(selectedAlgorithm) ? "Key encapsulation (ML-KEM)" : "Digital signatures"));
        if (currentPublicKey != null) {
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key Algorithm", currentPublicKey.getAlgorithm()));
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key Format", safeValue(currentPublicKey.getFormat())));
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key Size", currentPublicKey.getEncoded().length + " bytes"));
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key SHA-256", fingerprint(currentPublicKey.getEncoded())));
        } else {
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key", "Not available"));
        }
        if (currentPrivateKey != null) {
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Private Key Algorithm", currentPrivateKey.getAlgorithm()));
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Private Key Format", safeValue(currentPrivateKey.getFormat())));
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Private Key Size", currentPrivateKey.getEncoded().length + " bytes"));
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Private Key Handling", "Held in memory; never recorded in history"));
        } else {
            details.add(com.cryptocarver.model.OperationDetail.publicDetail("Private Key", "Not available"));
        }
        return details;
    }

    private String formatDetails(java.util.List<com.cryptocarver.model.OperationDetail> details) {
        StringBuilder result = new StringBuilder("PQC KEY PAIR DETAILS\n\n");
        for (com.cryptocarver.model.OperationDetail d : details) {
            result.append(d.name()).append(": ").append(d.value()).append('\n');
        }
        result.append("\nSecurity note: export a private key only to protected storage.");
        return result.toString();
    }

    private java.util.List<com.cryptocarver.model.OperationDetail> detailsWithKeyMaterial(java.util.List<com.cryptocarver.model.OperationDetail> details) {
        java.util.List<com.cryptocarver.model.OperationDetail> historyDetails = new java.util.ArrayList<>(details);
        if (currentPublicKey != null) {
            historyDetails.add(com.cryptocarver.model.OperationDetail.publicDetail("Public Key PEM", toPem("PUBLIC KEY", currentPublicKey.getEncoded())));
        }
        if (currentPrivateKey != null) {
            historyDetails.add(com.cryptocarver.model.OperationDetail.secretDetail("Private Key PEM", toPem("PRIVATE KEY", currentPrivateKey.getEncoded())));
        }
        return historyDetails;
    }

    private String fingerprint(byte[] encoded) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(encoded);
            String hex = DataConverter.bytesToHex(hash);
            return hex.substring(0, 16) + "…";
        } catch (Exception e) {
            return "Unavailable";
        }
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "Unspecified" : value;
    }

    @FXML
    public void handlePQCSign() {
        try {
            String algo = pqcSignAlgoCombo.getValue();
            String inputData = pqcSignInputArea.getText();

            if (currentPrivateKey == null) {
                if (statusReporter != null) statusReporter.showError("Key Error", "Please generate a key pair first (Load from file not yet implemented for PQC)");
                return;
            }
            if (!PostQuantumOperations.areAlgorithmsCompatible(algo, currentPrivateKey.getAlgorithm())) {
                if (statusReporter != null) statusReporter.showError("Key Parameter Error", "The selected signature parameter set (" + algo
                        + ") does not match the loaded private key (" + currentPrivateKey.getAlgorithm() + ").");
                return;
            }

            if (inputData.isEmpty()) {
                if (statusReporter != null) statusReporter.showError("Input Error", "Please enter data to sign");
                return;
            }

            byte[] data = inputData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] signature = PostQuantumOperations.sign(currentPrivateKey, data, algo);

            pqcSignOutputArea.setText(DataConverter.bytesToHex(signature));

            String kpDescription = currentPrivateKey.getAlgorithm();
            java.util.List<com.cryptocarver.model.OperationDetail> details = java.util.List.of(
                com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", algo),
                com.cryptocarver.model.OperationDetail.publicDetail("Key Pair", kpDescription),
                com.cryptocarver.model.OperationDetail.publicDetail("Data Size", data.length + " bytes"),
                com.cryptocarver.model.OperationDetail.publicDetail("Signature Size", signature.length + " bytes")
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PQC Sign")
                        .input(data).output(signature).details(details)
                        .status("PQC signature generated")
                        .build());
            }

        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Signing Error", "Error signing data: " + e.getMessage());
        }
    }

    @FXML
    public void handlePQCVerify() {
        try {
            String algo = pqcSignAlgoCombo.getValue();
            String inputData = pqcSignInputArea.getText();
            String signatureHex = pqcVerifySignatureField.getText();

            if (currentPublicKey == null) {
                if (statusReporter != null) statusReporter.showError("Key Error", "Please generate a key pair first");
                return;
            }
            if (!PostQuantumOperations.areAlgorithmsCompatible(algo, currentPublicKey.getAlgorithm())) {
                if (statusReporter != null) statusReporter.showError("Key Parameter Error", "The selected signature parameter set (" + algo
                        + ") does not match the loaded public key (" + currentPublicKey.getAlgorithm() + ").");
                return;
            }

            if (inputData.isEmpty() || signatureHex.isEmpty()) {
                if (statusReporter != null) statusReporter.showError("Input Error", "Please enter data and signature");
                return;
            }

            byte[] data = inputData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] signature = DataConverter.hexToBytes(signatureHex);

            boolean verified = PostQuantumOperations.verify(currentPublicKey, data, signature, algo);

            if (verified) {
                if (statusReporter != null) statusReporter.showInfo("Verification Result", "✓ Signature is VALID");
            } else {
                if (statusReporter != null) statusReporter.showError("Verification Result", "✗ Signature is INVALID");
            }
            String kpDescription = currentPublicKey.getAlgorithm();
            java.util.List<com.cryptocarver.model.OperationDetail> details = java.util.List.of(
                com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", algo),
                com.cryptocarver.model.OperationDetail.publicDetail("Key Pair", kpDescription),
                com.cryptocarver.model.OperationDetail.publicDetail("Result", verified ? "VALID" : "INVALID"),
                com.cryptocarver.model.OperationDetail.publicDetail("Data Size", data.length + " bytes")
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PQC Verify")
                        .input(data).output(signature).details(details)
                        .status(verified ? "PQC signature is valid" : "PQC signature is invalid")
                        .build());
            }

        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("Verification Error", "Error verifying: " + e.getMessage());
        }
    }

    @FXML
    public void handlePQCEncapsulate() {
        try {
            requireKemKeyPair();
            String selectedAlgorithm = pqcKemAlgoCombo.getValue();
            if (selectedAlgorithm == null || !PostQuantumOperations.areAlgorithmsCompatible(selectedAlgorithm, currentPublicKey.getAlgorithm())) {
                if (statusReporter != null) statusReporter.showError("KEM Algorithm Error", "Generate a key pair for the selected ML-KEM/Kyber algorithm first.");
                return;
            }
            PostQuantumOperations.KEMResult result = PostQuantumOperations.encapsulate(currentPublicKey, selectedAlgorithm);
            pqcKemCiphertextArea.setText(DataConverter.bytesToHex(result.encapsulation()));
            pqcKemSharedSecretField.setText(DataConverter.bytesToHex(result.sharedSecret()));
            bobSecret = result.sharedSecret();
            if (pqcAliceSecretField != null) pqcAliceSecretField.clear();
            pqcKemStatusLabel.setText(t("module.pqc.encapsulated"));
            pqcKemStatusLabel.setStyle("");
            java.util.List<com.cryptocarver.model.OperationDetail> details = java.util.List.of(
                com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", selectedAlgorithm),
                com.cryptocarver.model.OperationDetail.publicDetail("Ciphertext Size", result.encapsulation().length + " bytes"),
                com.cryptocarver.model.OperationDetail.secretDetail("Secret Size", result.sharedSecret().length + " bytes")
            );
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("ML-KEM Encapsulate")
                        .output(result.encapsulation()).details(details)
                        .status("ML-KEM encapsulation completed")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("KEM Error", "Unable to encapsulate: " + e.getMessage());
        }
    }

    @FXML
    public void handlePQCDecapsulate() {
        try {
            requireKemKeyPair();
            String ciphertextHex = pqcKemCiphertextArea.getText().trim();
            if (ciphertextHex.isEmpty()) {
                if (statusReporter != null) statusReporter.showError("KEM Input Error", "Encapsulate first or paste an encapsulation in hexadecimal.");
                return;
            }
            String selectedAlgorithm = pqcKemAlgoCombo.getValue();
            byte[] secret = PostQuantumOperations.decapsulate(currentPrivateKey, DataConverter.hexToBytes(ciphertextHex), selectedAlgorithm);
            if (pqcAliceSecretField != null) pqcAliceSecretField.setText(DataConverter.bytesToHex(secret));
            if (bobSecret != null) {
                boolean match = java.security.MessageDigest.isEqual(bobSecret, secret);
                if (match) {
                    pqcKemStatusLabel.setText(t("module.pqc.match"));
                    pqcKemStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    pqcKemStatusLabel.setText(t("module.pqc.mismatch"));
                    pqcKemStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            } else {
                pqcKemStatusLabel.setText(t("module.pqc.bobSecretUnknown"));
            }
            Map<String, String> legacyDetails = new HashMap<>();
            legacyDetails.put("Algorithm", pqcKemAlgoCombo.getValue());
            legacyDetails.put("Encapsulation Length", ciphertextHex.length() / 2 + " bytes");
            legacyDetails.put("Shared Secret", "Recovered (not displayed in history)");

            java.util.List<com.cryptocarver.model.OperationDetail> details = java.util.List.of(
                com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", pqcKemAlgoCombo.getValue()),
                com.cryptocarver.model.OperationDetail.publicDetail("Encapsulation Length", ciphertextHex.length() / 2 + " bytes"),
                com.cryptocarver.model.OperationDetail.publicDetail("Shared Secret", "Recovered (not displayed in history)")
            );

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("ML-KEM Decapsulate")
                        .input(DataConverter.hexToBytes(ciphertextHex)).output(secret).details(details)
                        .status("ML-KEM decapsulation completed")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("KEM Error", "Unable to decapsulate: " + e.getMessage());
        }
    }

    private void requireKemKeyPair() {
        if (currentPublicKey == null || currentPrivateKey == null || !isKemAlgorithm(currentPublicKey.getAlgorithm())) {
            throw new IllegalStateException("Generate an ML-KEM/Kyber key pair first.");
        }
    }

    private boolean isKemAlgorithm(String algorithm) {
        return algorithm != null && (algorithm.startsWith("Kyber") || algorithm.startsWith("ML-KEM"));
    }

    @FXML
    public void handlePQCBenchmark() {
        String algo = pqcBenchmarkAlgoCombo.getValue();
        if (algo == null) {
            if (statusReporter != null) statusReporter.showError("Benchmark Error", "Select an algorithm to benchmark");
            return;
        }

        if (pqcBenchmarkProgress != null) pqcBenchmarkProgress.setVisible(true);
        if (pqcBenchmarkArea != null) pqcBenchmarkArea.setText(t("module.pqc.benchmarking", algo));

        Callable<String> task = () -> {
            com.cryptocarver.crypto.pqc.PQCBenchmark bench = new com.cryptocarver.crypto.pqc.PQCBenchmark(algo, 1000);
            bench.run();
            return bench.getValue();
        };

        Consumer<String> onSuccess = resultText -> {
            if (pqcBenchmarkArea != null) pqcBenchmarkArea.setText(resultText);
            if (pqcBenchmarkProgress != null) pqcBenchmarkProgress.setVisible(false);
        };

        Consumer<Throwable> onFailure = err -> {
            if (pqcBenchmarkArea != null) pqcBenchmarkArea.setText(t("module.pqc.benchmarkFailed", err != null ? err.getMessage() : t("error.unknown")));
            if (pqcBenchmarkProgress != null) pqcBenchmarkProgress.setVisible(false);
            if (statusReporter != null) statusReporter.showError("Benchmark Error", err != null ? err.getMessage() : "Unknown error");
        };

        Runnable onCancelled = () -> {
            if (pqcBenchmarkArea != null) pqcBenchmarkArea.setText(t("module.pqc.benchmarkCancelled"));
            if (pqcBenchmarkProgress != null) pqcBenchmarkProgress.setVisible(false);
        };

        if (statusReporter != null && statusReporter.getOperationExecutor() != null) {
            statusReporter.getOperationExecutor().execute("PQC Benchmark (" + algo + ")", pqcBenchmarkBtn, task, onSuccess, onFailure, onCancelled);
        } else {
            try {
                String res = task.call();
                onSuccess.accept(res);
            } catch (Exception e) {
                onFailure.accept(e);
            }
        }
    }

    @FXML
    public void handlePopulatePqcKeyShelf() {
        IngestionUIHelper.populateShelfMenu(pqcKeyShelfMenu, pqcPublicKeyArea, pqcKeyStatusLabel, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);
    }

    @FXML
    public void handlePastePqcVerifySignature() {
        IngestionUIHelper.pasteFromClipboard(pqcVerifySignatureField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    public void handlePopulatePqcVerifyShelf() {
        IngestionUIHelper.populateShelfMenu(pqcVerifyShelfMenu, pqcVerifySignatureField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }
}
