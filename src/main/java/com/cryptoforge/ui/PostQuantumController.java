package com.cryptoforge.ui;

import com.cryptoforge.crypto.PostQuantumOperations;
import com.cryptoforge.model.OperationResult;
import com.cryptoforge.util.DataConverter;
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

/**
 * Controller for Post-Quantum Cryptography operations
 */
public class PostQuantumController {

    private static final Logger LOG = LoggerFactory.getLogger(PostQuantumController.class);

    private StatusReporter statusReporter;

    @FXML
    private Accordion pqcAccordion;

    // UI Components - Key Gen
    @FXML
    private ComboBox<String> pqcAlgorithmCombo;
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
    @FXML
    private ComboBox<String> pqcKemAlgoCombo;
    @FXML
    private TextArea pqcKemCiphertextArea; // Result
    @FXML
    private TextField pqcKemSharedSecretField; // Result
    @FXML
    private Label pqcKemStatusLabel;
    
    // Internal state
    private PrivateKey currentPrivateKey;
    private PublicKey currentPublicKey;

    public PostQuantumController() {
    }

    public void initModule(StatusReporter reporter) {
        this.statusReporter = reporter;
    }

    @FXML
    public void initialize() {
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

        // Populate KEM combo
        pqcKemAlgoCombo.getItems().setAll(PostQuantumOperations.ML_KEM_ALGORITHMS);
        pqcKemAlgoCombo.setValue(PostQuantumOperations.ML_KEM_ALGORITHMS.get(0));
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

            KeyPair kp = PostQuantumOperations.generateKeyPair(algo);
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
            java.util.List<com.cryptoforge.model.OperationDetail> details = describeKeyPair(algo, "Generated");
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
            LOG.error("PQC key generation failed", e);
        }
    }

    @FXML
    public void handleImportPQCKeys() {
        String algorithm = pqcAlgorithmCombo.getValue();
        if (algorithm == null || algorithm.startsWith("---")) {
            if (statusReporter != null) statusReporter.showError("Algorithm Error", "Select the algorithm that matches the PEM keys.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select PQC public and/or private PEM keys");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PEM files", "*.pem", "*.pub", "*.key"));
        java.util.List<File> files = chooser.showOpenMultipleDialog(null);
        if (files == null || files.isEmpty()) return;
        try {
            for (File file : files) {
                String pem = Files.readString(file.toPath(), StandardCharsets.US_ASCII);
                byte[] encoded = decodePem(pem);
                if (pem.contains("BEGIN PUBLIC KEY")) {
                    currentPublicKey = PostQuantumOperations.importPublicKey(algorithm, encoded);
                    pqcPublicKeyArea.setText(pem.trim());
                } else if (pem.contains("BEGIN PRIVATE KEY")) {
                    currentPrivateKey = PostQuantumOperations.importPrivateKey(algorithm, encoded);
                    pqcPrivateKeyArea.setText(pem.trim());
                } else {
                    throw new IllegalArgumentException(file.getName() + " is not a PKCS#8 or X.509 PEM key");
                }
            }
            pqcKeyStatusLabel.setText("Imported PQC key material for " + algorithm);
            java.util.List<com.cryptoforge.model.OperationDetail> details = describeKeyPair(algorithm, "Imported");
            if (pqcKeyDetailsArea != null) pqcKeyDetailsArea.setText(formatDetails(details));
            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("PQC Key Import")
                        .details(detailsWithKeyMaterial(details))
                        .status("PQC key material imported")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("PQC Import Error", "Unable to import keys: " + e.getMessage());
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
            java.util.List<com.cryptoforge.model.OperationDetail> details = java.util.List.of(
                com.cryptoforge.model.OperationDetail.publicDetail("Key Type", pemType),
                com.cryptoforge.model.OperationDetail.publicDetail("Output", file.getAbsolutePath())
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

    private java.util.List<com.cryptoforge.model.OperationDetail> describeKeyPair(String selectedAlgorithm, String source) {
        java.util.List<com.cryptoforge.model.OperationDetail> details = new java.util.ArrayList<>();
        details.add(com.cryptoforge.model.OperationDetail.publicDetail("Operation", source + " PQC key pair"));
        details.add(com.cryptoforge.model.OperationDetail.publicDetail("Parameter Set", selectedAlgorithm));
        details.add(com.cryptoforge.model.OperationDetail.publicDetail("Purpose", isKemAlgorithm(selectedAlgorithm) ? "Key encapsulation (ML-KEM)" : "Digital signatures"));
        if (currentPublicKey != null) {
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key Algorithm", currentPublicKey.getAlgorithm()));
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key Format", safeValue(currentPublicKey.getFormat())));
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key Size", currentPublicKey.getEncoded().length + " bytes"));
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key SHA-256", fingerprint(currentPublicKey.getEncoded())));
        } else {
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key", "Not available"));
        }
        if (currentPrivateKey != null) {
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Private Key Algorithm", currentPrivateKey.getAlgorithm()));
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Private Key Format", safeValue(currentPrivateKey.getFormat())));
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Private Key Size", currentPrivateKey.getEncoded().length + " bytes"));
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Private Key Handling", "Held in memory; never recorded in history"));
        } else {
            details.add(com.cryptoforge.model.OperationDetail.publicDetail("Private Key", "Not available"));
        }
        return details;
    }

    private String formatDetails(java.util.List<com.cryptoforge.model.OperationDetail> details) {
        StringBuilder result = new StringBuilder("PQC KEY PAIR DETAILS\n\n");
        for (com.cryptoforge.model.OperationDetail d : details) {
            result.append(d.name()).append(": ").append(d.value()).append('\n');
        }
        result.append("\nSecurity note: export a private key only to protected storage.");
        return result.toString();
    }

    private java.util.List<com.cryptoforge.model.OperationDetail> detailsWithKeyMaterial(java.util.List<com.cryptoforge.model.OperationDetail> details) {
        java.util.List<com.cryptoforge.model.OperationDetail> historyDetails = new java.util.ArrayList<>(details);
        if (currentPublicKey != null) {
            historyDetails.add(com.cryptoforge.model.OperationDetail.publicDetail("Public Key PEM", toPem("PUBLIC KEY", currentPublicKey.getEncoded())));
        }
        if (currentPrivateKey != null) {
            historyDetails.add(com.cryptoforge.model.OperationDetail.secretDetail("Private Key PEM", toPem("PRIVATE KEY", currentPrivateKey.getEncoded())));
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
            java.util.List<com.cryptoforge.model.OperationDetail> details = java.util.List.of(
                com.cryptoforge.model.OperationDetail.publicDetail("Algorithm", algo),
                com.cryptoforge.model.OperationDetail.publicDetail("Key Pair", kpDescription),
                com.cryptoforge.model.OperationDetail.publicDetail("Data Size", data.length + " bytes"),
                com.cryptoforge.model.OperationDetail.publicDetail("Signature Size", signature.length + " bytes")
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
            java.util.List<com.cryptoforge.model.OperationDetail> details = java.util.List.of(
                com.cryptoforge.model.OperationDetail.publicDetail("Algorithm", algo),
                com.cryptoforge.model.OperationDetail.publicDetail("Key Pair", kpDescription),
                com.cryptoforge.model.OperationDetail.publicDetail("Result", verified ? "VALID" : "INVALID"),
                com.cryptoforge.model.OperationDetail.publicDetail("Data Size", data.length + " bytes")
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
            PostQuantumOperations.KEMResult result = PostQuantumOperations.encapsulate(currentPublicKey);
            pqcKemCiphertextArea.setText(DataConverter.bytesToHex(result.encapsulation()));
            pqcKemSharedSecretField.setText(DataConverter.bytesToHex(result.sharedSecret()));
            pqcKemStatusLabel.setText("Shared secret encapsulated successfully.");
            java.util.List<com.cryptoforge.model.OperationDetail> details = java.util.List.of(
                com.cryptoforge.model.OperationDetail.publicDetail("Algorithm", selectedAlgorithm),
                com.cryptoforge.model.OperationDetail.publicDetail("Ciphertext Size", result.encapsulation().length + " bytes"),
                com.cryptoforge.model.OperationDetail.secretDetail("Secret Size", result.sharedSecret().length + " bytes")
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
            byte[] secret = PostQuantumOperations.decapsulate(currentPrivateKey, DataConverter.hexToBytes(ciphertextHex));
            pqcKemSharedSecretField.setText(DataConverter.bytesToHex(secret));
            pqcKemStatusLabel.setText("Shared secret decapsulated successfully.");
            Map<String, String> legacyDetails = new HashMap<>();
            legacyDetails.put("Algorithm", pqcKemAlgoCombo.getValue());
            legacyDetails.put("Encapsulation Length", ciphertextHex.length() / 2 + " bytes");
            legacyDetails.put("Shared Secret", "Recovered (not displayed in history)");
            
            java.util.List<com.cryptoforge.model.OperationDetail> details = java.util.List.of(
                com.cryptoforge.model.OperationDetail.publicDetail("Algorithm", pqcKemAlgoCombo.getValue()),
                com.cryptoforge.model.OperationDetail.publicDetail("Encapsulation Length", ciphertextHex.length() / 2 + " bytes"),
                com.cryptoforge.model.OperationDetail.publicDetail("Shared Secret", "Recovered (not displayed in history)")
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
}
