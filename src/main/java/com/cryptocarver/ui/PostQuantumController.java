package com.cryptocarver.ui;

import com.cryptocarver.crypto.PostQuantumOperations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller for Post-Quantum Cryptography operations
 */
public class PostQuantumController {
    /** Held so the locale listener stays registered: I18nService keeps only a weak reference. */
    private java.util.function.Consumer<java.util.Locale> localeChangeListener;

    private StatusReporter statusReporter;
    private final AtomicReference<StatusReporter> coordinatorStatusReporter = new AtomicReference<>();
    private PostQuantumKeyCoordinator keyCoordinator;
    private PostQuantumSignatureCoordinator signatureCoordinator;

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
    private final PostQuantumKeyState keyState = new PostQuantumKeyState();

    PublicKey getCurrentPublicKey() { return keyState.publicKey(); }
    PrivateKey getCurrentPrivateKey() { return keyState.privateKey(); }

    public PostQuantumController() {
    }

    public void initModule(StatusReporter reporter) {
        this.statusReporter = reporter;
        this.coordinatorStatusReporter.set(reporter);
    }

    private PostQuantumKeyCoordinator keyCoordinator() {
        if (keyCoordinator == null) {
            PostQuantumKeyCoordinator.View view = new PostQuantumKeyCoordinator.View(
                    pqcAlgorithmCombo, pqcGenerateKeyBtn, pqcPublicKeyArea, pqcPrivateKeyArea,
                    pqcKeyDetailsArea, pqcKeyStatusLabel, pqcKemAlgoCombo, pqcSignAlgoCombo, keyState);
            keyCoordinator = new PostQuantumKeyCoordinator(view, coordinatorStatusReporter::get);
        }
        return keyCoordinator;
    }

    private PostQuantumSignatureCoordinator signatureCoordinator() {
        if (signatureCoordinator == null) {
            PostQuantumSignatureCoordinator.View view = new PostQuantumSignatureCoordinator.View(
                    () -> pqcSignAlgoCombo, () -> pqcSignInputArea, () -> pqcSignOutputArea,
                    () -> pqcVerifySignatureField, () -> keyState);
            signatureCoordinator = new PostQuantumSignatureCoordinator(view, coordinatorStatusReporter::get);
        }
        return signatureCoordinator;
    }

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(pqcAccordion, ModuleTextCatalog.pqc());
        localeChangeListener = locale -> {
            if (pqcKeyStatusLabel != null && keyState.publicKey() == null) pqcKeyStatusLabel.setText(t("status.ready"));
        };
        com.cryptocarver.service.I18nService.getInstance().addLocaleChangeListener(localeChangeListener);
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
            if (ModulePaneMatcher.matches(pane, itemName, ModuleTextCatalog.pqc())) {
                pqcAccordion.setExpandedPane(pane);
                break;
            }
        }
    }

    @FXML
    public void handleGeneratePQCKeyPair() { keyCoordinator().handleGeneratePQCKeyPair(); }

    @FXML
    public void handleImportPQCKeys() { keyCoordinator().handleImportPQCKeys(); }
    public void importKeysFromContents(java.util.List<String> pems) throws Exception {
        keyCoordinator().importKeysFromContents(pems);
    }

    /** Imports one or both unencrypted PQC key files. Each file must contain one PEM or DER key. */
    public void importKeysFromFiles(java.util.List<File> files) throws Exception {
        keyCoordinator().importKeysFromFiles(files);
    }

    @FXML
    public void handleExportPQCPublicKey() { keyCoordinator().handleExportPQCPublicKey(); }

    @FXML
    public void handleExportPQCPrivateKey() { keyCoordinator().handleExportPQCPrivateKey(); }

    @FXML
    public void handlePQCSign() { signatureCoordinator().handlePQCSign(); }

    @FXML
    public void handlePQCVerify() { signatureCoordinator().handlePQCVerify(); }

    @FXML
    public void handlePQCEncapsulate() {
        try {
            requireKemKeyPair();
            String selectedAlgorithm = pqcKemAlgoCombo.getValue();
            if (selectedAlgorithm == null || !PostQuantumOperations.areAlgorithmsCompatible(selectedAlgorithm, keyState.publicKey().getAlgorithm())) {
                if (statusReporter != null) statusReporter.showError("KEM Algorithm Error", "Generate a key pair for the selected ML-KEM/Kyber algorithm first.");
                return;
            }
            PostQuantumOperations.KEMResult result = PostQuantumOperations.encapsulate(keyState.publicKey(), selectedAlgorithm);
            pqcKemCiphertextArea.setText(DataConverter.bytesToHex(result.encapsulation()));
            pqcKemSharedSecretField.setText(DataConverter.bytesToHex(result.sharedSecret()));
            keyState.setBobSecret(result.sharedSecret());
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
            byte[] secret = PostQuantumOperations.decapsulate(keyState.privateKey(), DataConverter.hexToBytes(ciphertextHex), selectedAlgorithm);
            if (pqcAliceSecretField != null) pqcAliceSecretField.setText(DataConverter.bytesToHex(secret));
            if (keyState.bobSecret() != null) {
                boolean match = java.security.MessageDigest.isEqual(keyState.bobSecret(), secret);
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
                com.cryptocarver.model.OperationDetail.secretDetail("Shared Secret", "Recovered (not displayed in history)")
            );

            if (statusReporter != null) {
                statusReporter.publish(OperationResult.forOperation("ML-KEM Decapsulate")
                        .input(DataConverter.hexToBytes(ciphertextHex))
                        .output(secret, com.cryptocarver.model.OperationDetail.Classification.SECRET).details(details)
                        .status("ML-KEM decapsulation completed")
                        .build());
            }
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("KEM Error", "Unable to decapsulate: " + e.getMessage());
        }
    }

    private void requireKemKeyPair() {
        if (keyState.publicKey() == null || keyState.privateKey() == null || !isKemAlgorithm(keyState.publicKey().getAlgorithm())) {
            throw new IllegalStateException("Generate an ML-KEM/Kyber key pair first.");
        }
    }
    private boolean isKemAlgorithm(String algorithm) { return PostQuantumKeyCoordinator.isKemAlgorithm(algorithm); }

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
