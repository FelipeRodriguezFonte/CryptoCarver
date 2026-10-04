package com.cryptocarver.ui;

import com.cryptocarver.crypto.PostQuantumOperations;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;
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
    private PostQuantumKemCoordinator kemCoordinator;
    private PostQuantumBenchmarkCoordinator benchmarkCoordinator;

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

    private PostQuantumKemCoordinator kemCoordinator() {
        if (kemCoordinator == null) {
            PostQuantumKemCoordinator.View view = new PostQuantumKemCoordinator.View(
                    pqcKemAlgoCombo, pqcKemCiphertextArea, pqcKemSharedSecretField,
                    pqcAliceSecretField, pqcKemStatusLabel, keyState);
            kemCoordinator = new PostQuantumKemCoordinator(view, coordinatorStatusReporter::get);
        }
        return kemCoordinator;
    }

    private PostQuantumBenchmarkCoordinator benchmarkCoordinator() {
        if (benchmarkCoordinator == null) {
            PostQuantumBenchmarkCoordinator.View view = new PostQuantumBenchmarkCoordinator.View(
                    pqcBenchmarkAlgoCombo, pqcBenchmarkBtn, pqcBenchmarkProgress, pqcBenchmarkArea);
            benchmarkCoordinator = new PostQuantumBenchmarkCoordinator(view, coordinatorStatusReporter::get);
        }
        return benchmarkCoordinator;
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
    public void handlePQCEncapsulate() { kemCoordinator().handlePQCEncapsulate(); }

    @FXML
    public void handlePQCDecapsulate() { kemCoordinator().handlePQCDecapsulate(); }

    @FXML
    public void handlePQCBenchmark() { benchmarkCoordinator().handlePQCBenchmark(); }

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
