package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.crypto.FormatPreservingEncryption;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.OperationHistory;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for Cipher operations
 */
public class CipherController {

    private static final Logger LOG = LoggerFactory.getLogger(CipherController.class);
    private final DialogService dialogService = new DialogService();

    @FXML private VBox cipherRoot;
    private ModuleI18n.Binding moduleI18n;
    @FXML private javafx.scene.layout.GridPane cipherWorkbench;
    @FXML private VBox symmetricConfig;
    @FXML private VBox cipherIoCard;
    @FXML private javafx.scene.layout.GridPane cipherIoGrid;
    @FXML private javafx.scene.control.Accordion cipherAccordion;
    @FXML private javafx.scene.layout.FlowPane symmetricActions;
    private final javafx.beans.property.BooleanProperty symmetricWorkspace =
            new javafx.beans.property.SimpleBooleanProperty(true);

    public javafx.beans.property.ReadOnlyBooleanProperty symmetricWorkspaceProperty() {
        return symmetricWorkspace;
    }

    public javafx.scene.layout.FlowPane detachSymmetricActions() {
        cipherRoot.getChildren().remove(symmetricActions);
        return symmetricActions;
    }

    public void showSymmetricWorkspace(boolean active) {
        symmetricWorkspace.set(active);
        symmetricConfig.setVisible(active);
        symmetricConfig.setManaged(active);
        cipherAccordion.setVisible(!active);
        cipherAccordion.setManaged(!active);
        javafx.scene.layout.ColumnConstraints inputColumn = new javafx.scene.layout.ColumnConstraints();
        inputColumn.setPercentWidth(active ? 100 : 50);
        cipherIoGrid.getColumnConstraints().setAll(inputColumn);
        if (!active) {
            javafx.scene.layout.ColumnConstraints outputColumn = new javafx.scene.layout.ColumnConstraints();
            outputColumn.setPercentWidth(50);
            cipherIoGrid.getColumnConstraints().add(outputColumn);
        }
        javafx.scene.layout.GridPane.setConstraints(cipherIoGrid.getChildren().get(1), active ? 0 : 1, active ? 1 : 0);
        layoutCipherWorkbench();
    }

    private void layoutCipherWorkbench() {
        boolean wide = symmetricWorkspace.get() && cipherWorkbench.getWidth() >= 740;
        javafx.scene.layout.ColumnConstraints first = new javafx.scene.layout.ColumnConstraints();
        first.setPercentWidth(wide ? 48 : 100);
        first.setMinWidth(0);
        cipherWorkbench.getColumnConstraints().setAll(first);
        if (wide) {
            javafx.scene.layout.ColumnConstraints second = new javafx.scene.layout.ColumnConstraints();
            second.setPercentWidth(52);
            second.setMinWidth(0);
            cipherWorkbench.getColumnConstraints().add(second);
        }
        javafx.scene.layout.GridPane.setConstraints(symmetricConfig, 0, 0);
        javafx.scene.layout.GridPane.setConstraints(cipherIoCard, wide ? 1 : 0,
                wide || !symmetricWorkspace.get() ? 0 : 1);
        javafx.scene.layout.GridPane.setValignment(cipherIoCard, javafx.geometry.VPos.TOP);
    }


    @FXML private TextArea cipherInputArea;
    @FXML private TextArea cipherOutputArea;
    @FXML private Button cipherCopyResultButton;
    @FXML private Button cipherShelfResultButton;
    @FXML private Button cipherExpandResultButton;
    @FXML private Button cipherSaveResultButton;
    @FXML private Button cipherUseResultButton;
    private ComboBox<String> cipherInputFormatCombo;
    private ComboBox<String> outputFormatCombo;
    private StatusReporter statusReporter;
    private java.util.function.Supplier<java.security.KeyPair> sharedKeyPairSupplier;
    private boolean preflightListenersInstalled;

    // Symmetric cipher UI components
    @FXML private ComboBox<String> cipherTemplateCombo;
    @FXML private ComboBox<String> symmetricAlgorithmCombo;
    @FXML private ComboBox<String> cipherModeCombo;
    @FXML private ComboBox<String> paddingCombo;
    @FXML private ComboBox<String> symKeySourceCombo;
    @FXML private ComboBox<String> symHsmKeyCombo;
    @FXML private TextField symmetricKeyField;
    @FXML private TextField ivField;
    @FXML private TextField gcmTagField;
    @FXML private TextField aadField;
    @FXML private Button inspectKeyBtn;
    @FXML private Button saveToLabBtn;
    @FXML private MenuButton symKeyShelfMenu;
    @FXML private MenuButton cipherPubKeyShelfMenu;
    @FXML private MenuButton cipherPrivKeyShelfMenu;
    @FXML private Label ivLabel;
    @FXML private HBox ivContainer;
    @FXML private Label gcmTagLabel;
    @FXML private Label aadLabel;
    @FXML private VBox ecbWarningBox;
    @FXML private Label aeadNoteLabel;
    @FXML private Label rsaPaddingWarningLabel;
    @FXML private Label rsaPaddingHelpLabel;
    @FXML private Label symKeyBadgeLabel;
    @FXML private Label ivBadgeLabel;
    @FXML private Label gcmTagBadgeLabel;
    @FXML private Label aadBadgeLabel;

    // Format-preserving encryption workbench
    @FXML private ComboBox<String> fpeOperationCombo;
    @FXML private ComboBox<String> fpeAlgorithmCombo;
    @FXML private ComboBox<String> fpeAlphabetPresetCombo;
    @FXML private TextField fpeKeyField;
    @FXML private TextField fpeTweakField;
    @FXML private TextField fpeAlphabetField;
    @FXML private TextArea fpeInputArea;
    @FXML private TextArea fpeOutputArea;

    // Last AEAD encryption components, kept separately from the rendered result.
    private String lastAeadCiphertext;
    private String lastAeadTag;

    private com.cryptocarver.ui.component.MaterialFieldBadge symKeyBadge;
    private com.cryptocarver.ui.component.MaterialFieldBadge ivBadge;
    private com.cryptocarver.ui.component.MaterialFieldBadge tagBadge;
    private com.cryptocarver.ui.component.MaterialFieldBadge aadBadge;

    // Streaming file cipher UI components
    @FXML private ComboBox<String> fileCipherAlgorithmCombo;
    @FXML private TextField fileCipherSourceField;
    @FXML private TextField fileCipherDestinationField;
    @FXML private TextField fileCipherTagField;
    @FXML private TextField fileCipherKeyField;
    @FXML private TextField fileCipherNonceField;
    @FXML private TextField fileCipherAadField;
    @FXML private TextArea fileCipherResultArea;
    @FXML private CheckBox fileCipherLinesCheck;
    @FXML private ComboBox<String> fileCipherLineEncodingCombo;
    @FXML private ComboBox<String> fileCipherLineCharsetCombo;
    @FXML private CheckBox fileCipherCompactCbcCheck;
    @FXML private Button fileCipherEncryptBtn;
    @FXML private Button fileCipherDecryptBtn;
    @FXML private Button fileCipherAnalyzeBtn;
    private FileCipherCoordinator fileCipher;
    private AsymmetricCipherCoordinator rsa;

    // Asymmetric cipher UI components
    @FXML private ComboBox<String> rsaPaddingCombo;
    @FXML private ComboBox<String> asymmetricInputFormatCombo;
    @FXML private ComboBox<String> asymmetricOutputFormatCombo;

    // Key Input Areas (Manual Loading)
    @FXML private TextArea publicKeyArea;
    @FXML private TextArea privateKeyArea;
    @FXML private javafx.scene.layout.VBox openPgpContainer;
    @FXML private OpenPgpController openPgpContainerController;
    private final java.util.Set<String> usedAeadNonces = new java.util.HashSet<>();

    public CipherController() {
    }

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(cipherRoot, ModuleTextCatalog.cipher());
        setSymmetricAlgorithmCombo(symmetricAlgorithmCombo);
        setCipherModeCombo(cipherModeCombo);
        setPaddingCombo(paddingCombo);
        setSymmetricKeyField(symmetricKeyField);
        setSymKeySourceCombo(symKeySourceCombo);
        setSymHsmKeyCombo(symHsmKeyCombo);
        setIVField(ivField);
        setGcmTagField(gcmTagField);
        setAADField(aadField);
        fileCipher().configure();
        rsa().configure();

        if (fpeOperationCombo != null) fpeOperationCombo.getItems().setAll("ENCRYPT", "DECRYPT");
        if (fpeAlgorithmCombo != null) fpeAlgorithmCombo.getItems().setAll("FF1", "FF3_1");
        if (fpeOperationCombo != null) fpeOperationCombo.setValue("ENCRYPT");
        if (fpeAlgorithmCombo != null) fpeAlgorithmCombo.setValue("FF1");
        if (fpeAlphabetPresetCombo != null) {
            fpeAlphabetPresetCombo.getItems().setAll("Decimal (0-9)", "Alphanumeric", "ASCII printable", "Custom");
            fpeAlphabetPresetCombo.setValue("Decimal (0-9)");
            fpeAlphabetField.setText("0123456789");
            fpeAlphabetPresetCombo.valueProperty().addListener((obs, oldValue, value) -> {
                if ("Decimal (0-9)".equals(value)) fpeAlphabetField.setText("0123456789");
                else if ("Alphanumeric".equals(value)) fpeAlphabetField.setText("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
                else if ("ASCII printable".equals(value)) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0x20; i <= 0x7e; i++) b.append((char) i);
                    fpeAlphabetField.setText(b.toString());
                } else fpeAlphabetField.clear();
            });
        }
        setupHexValidation(fpeKeyField);
        setupHexValidation(fpeTweakField);

        setupHexValidation(symmetricKeyField);
        setupHexValidation(ivField);
        setupHexValidation(gcmTagField);
        setupHexValidation(aadField);

        IngestionUIHelper.bindField(symmetricKeyField, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
        IngestionUIHelper.bindField(ivField, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
        IngestionUIHelper.bindField(publicKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
        IngestionUIHelper.bindField(privateKeyArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
        IngestionUIHelper.bindField(cipherInputArea, null, com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN, com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX, com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64);

        symmetricActions.visibleProperty().bind(symmetricWorkspace);
        symmetricActions.managedProperty().bind(symmetricActions.visibleProperty());
        cipherWorkbench.widthProperty().addListener((obs, before, after) -> layoutCipherWorkbench());
        // Hide the whole row, including badges and spacing, when a mode does not use it.
        for (javafx.scene.Node field : java.util.List.of(ivContainer, gcmTagField, aadField)) {
            field.getParent().visibleProperty().bind(field.visibleProperty());
            field.getParent().managedProperty().bind(field.visibleProperty());
        }
        showSymmetricWorkspace(true);
        refreshCipherTemplateCombo();
        updateModeAndAlgorithmVisibility();
    }

    @FXML
    private void handleCopyCipherResult() {
        if (statusReporter != null) {
            statusReporter.copyCurrentResult();
        } else {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(cipherOutputArea.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        }
    }

    @FXML
    private void handleShelfCipherResult() {
        if (statusReporter != null) statusReporter.addCurrentResultToShelf();
    }

    @FXML
    private void handleExpandCipherResult() {
        if (statusReporter != null) statusReporter.expandCurrentResult();
    }

    @FXML
    private void handleSaveCipherResult() {
        if (statusReporter != null) statusReporter.saveCurrentResultAsSessionStep();
    }

    @FXML
    private void handleUseCipherResultAsInput() {
        cipherInputArea.setText(cipherOutputArea.getText());
        if (statusReporter instanceof ModernMainController modern) modern.revealCipherEditor(cipherInputArea);
    }

    @FXML
    public void handleFpe() {
        try {
            String input = fpeInputArea == null ? "" : fpeInputArea.getText();
            String alphabet = fpeAlphabetField == null ? "" : fpeAlphabetField.getText();
            byte[] key = DataConverter.hexToBytes(fpeKeyField.getText().trim());
            byte[] tweak = fpeTweakField.getText().trim().isEmpty()
                    ? new byte[0] : DataConverter.hexToBytes(fpeTweakField.getText().trim());
            FormatPreservingEncryption.Algorithm algorithm = FormatPreservingEncryption.Algorithm.valueOf(fpeAlgorithmCombo.getValue());
            boolean encrypt = "ENCRYPT".equals(fpeOperationCombo.getValue());
            String result = encrypt
                    ? FormatPreservingEncryption.encrypt(algorithm, input, key, alphabet, tweak)
                    : FormatPreservingEncryption.decrypt(algorithm, input, key, alphabet, tweak);
            fpeOutputArea.setText(result);
            if (statusReporter != null) statusReporter.updateStatus("FPE " + (encrypt ? "encryption" : "decryption") + " completed");
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError("FPE Error", e.getMessage());
            else if (fpeOutputArea != null) fpeOutputArea.setText("Error: " + e.getMessage());
            LOG.warn("FPE operation failed", e);
        }
    }

    @FXML
    public void handlePasteSymKey() {
        IngestionUIHelper.pasteFromClipboard(symmetricKeyField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handleLoadSymKey() {
        IngestionUIHelper.loadFile(symmetricKeyField != null && symmetricKeyField.getScene() != null ? symmetricKeyField.getScene().getWindow() : null,
                symmetricKeyField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handlePopulateSymKeyShelf() {
        IngestionUIHelper.populateShelfMenu(symKeyShelfMenu, symmetricKeyField, null, null,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.BASE64,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.TEXT_UNKNOWN);
    }

    @FXML
    public void handlePastePublicKey() {
        IngestionUIHelper.pasteFromClipboard(publicKeyArea, null, () -> handleLoadPublicKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    public void handlePopulateCipherPubKeyShelf() {
        IngestionUIHelper.populateShelfMenu(cipherPubKeyShelfMenu, publicKeyArea, null, () -> handleLoadPublicKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_CERTIFICATE,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    public void handlePastePrivateKey() {
        IngestionUIHelper.pasteFromClipboard(privateKeyArea, null, () -> handleLoadPrivateKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    @FXML
    public void handlePopulateCipherPrivKeyShelf() {
        IngestionUIHelper.populateShelfMenu(cipherPrivKeyShelfMenu, privateKeyArea, null, () -> handleLoadPrivateKey(),
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY,
                com.cryptocarver.model.MaterialDetectionResult.MaterialType.HEX);
    }

    private void refreshCipherTemplateCombo() {
        SafeTemplateUIHelper.populateTemplateCombo(
                cipherTemplateCombo,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER,
                List.of("AES-256-GCM — Text UTF-8 → Base64", "AES-256-CBC — Hex → Hex")
        );
    }

    @FXML
    private void handleApplyCipherTemplate() {
        String template = cipherTemplateCombo != null ? cipherTemplateCombo.getValue() : null;
        if (template == null) return;

        Map<String, java.util.function.Consumer<String>> setters = Map.of(
                "symmetricAlgorithmCombo", v -> { if (symmetricAlgorithmCombo != null) symmetricAlgorithmCombo.setValue(v); },
                "cipherModeCombo", v -> { if (cipherModeCombo != null) cipherModeCombo.setValue(v); },
                "paddingCombo", v -> { if (paddingCombo != null) paddingCombo.setValue(v); },
                "asymmetricInputFormatCombo", v -> { if (asymmetricInputFormatCombo != null) asymmetricInputFormatCombo.setValue(v); },
                "asymmetricOutputFormatCombo", v -> { if (asymmetricOutputFormatCombo != null) asymmetricOutputFormatCombo.setValue(v); },
                "rsaPaddingCombo", v -> { if (rsaPaddingCombo != null) rsaPaddingCombo.setValue(v); },
                "inputFormatCombo", v -> { if (statusReporter != null) statusReporter.setInputFormat(v); },
                "outputFormatCombo", v -> { if (statusReporter != null) statusReporter.setOutputFormat(v); }
        );

        SafeTemplateUIHelper.applySelectedTemplate(
                template,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER,
                () -> {
                    if (template.contains("AES-256-GCM")) {
                        symmetricAlgorithmCombo.setValue("AES-256");
                        cipherModeCombo.setValue("GCM");
                        paddingCombo.setValue("NoPadding");
                        symKeySourceCombo.setValue("Manual Input");
                        symmetricKeyField.setText("");
                        symmetricKeyField.setPromptText("Enter key in hex (Select a Key)");
                        ivField.setText("");
                        ivField.setPromptText("Click Generate for fresh GCM nonce...");
                        gcmTagField.setText("");
                        aadField.setText("");
                        if (statusReporter != null) {
                            statusReporter.setInputFormat("Text (UTF-8)");
                            statusReporter.setOutputFormat("Base64");
                            statusReporter.updateStatus("Template Applied: AES-256-GCM — Text UTF-8 → Base64. GCM authenticates ciphertext; use a fresh nonce for every encryption.");
                        }
                    } else if (template.contains("AES-256-CBC")) {
                        symmetricAlgorithmCombo.setValue("AES-256");
                        cipherModeCombo.setValue("CBC");
                        paddingCombo.setValue("PKCS5Padding");
                        symKeySourceCombo.setValue("Manual Input");
                        symmetricKeyField.setText("");
                        ivField.setText("");
                        gcmTagField.setText("");
                        aadField.setText("");
                        if (statusReporter != null) {
                            statusReporter.setInputFormat("Hexadecimal");
                            statusReporter.setOutputFormat("Hexadecimal");
                            statusReporter.updateStatus("Template Applied: AES-256-CBC — Hex → Hex");
                        }
                    }
                },
                setters,
                statusReporter
        );
    }

    @FXML
    private void handleSaveCipherTemplate() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (symmetricAlgorithmCombo != null && symmetricAlgorithmCombo.getValue() != null) params.put("symmetricAlgorithmCombo", symmetricAlgorithmCombo.getValue());
        if (cipherModeCombo != null && cipherModeCombo.getValue() != null) params.put("cipherModeCombo", cipherModeCombo.getValue());
        if (paddingCombo != null && paddingCombo.getValue() != null) params.put("paddingCombo", paddingCombo.getValue());
        if (rsaPaddingCombo != null && rsaPaddingCombo.getValue() != null) params.put("rsaPaddingCombo", rsaPaddingCombo.getValue());
        if (asymmetricInputFormatCombo != null && asymmetricInputFormatCombo.getValue() != null) params.put("asymmetricInputFormatCombo", asymmetricInputFormatCombo.getValue());
        if (asymmetricOutputFormatCombo != null && asymmetricOutputFormatCombo.getValue() != null) params.put("asymmetricOutputFormatCombo", asymmetricOutputFormatCombo.getValue());
        if (cipherInputFormatCombo != null && cipherInputFormatCombo.getValue() != null) params.put("inputFormatCombo", cipherInputFormatCombo.getValue());
        if (outputFormatCombo != null && outputFormatCombo.getValue() != null) params.put("outputFormatCombo", outputFormatCombo.getValue());

        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.saveCurrentAsTemplate(
                owner,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER,
                params,
                this::refreshCipherTemplateCombo,
                statusReporter
        );
    }

    @FXML
    private void handleExportCipherTemplate() {
        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.exportSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER, cipherTemplateCombo, statusReporter);
    }

    @FXML
    private void handleImportCipherTemplate() {
        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.importTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER, this::refreshCipherTemplateCombo, statusReporter);
    }

    @FXML
    private void handleDeleteCipherTemplate() {
        javafx.stage.Window owner = cipherTemplateCombo != null && cipherTemplateCombo.getScene() != null ? cipherTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.deleteSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_CIPHER, cipherTemplateCombo, this::refreshCipherTemplateCombo, statusReporter);
    }

    @FXML
    private void handleResetCipherDefaults() {
        symmetricAlgorithmCombo.setValue("AES-256");
        cipherModeCombo.setValue("CBC");
        paddingCombo.setValue("PKCS5Padding");
        symKeySourceCombo.setValue("Manual Input");
        symmetricKeyField.setText("");
        ivField.setText("");
        gcmTagField.setText("");
        aadField.setText("");
        if (statusReporter != null) {
            statusReporter.setInputFormat("Text (UTF-8)");
            statusReporter.setOutputFormat("Hexadecimal");
            statusReporter.updateStatus(com.cryptocarver.service.I18nService.getInstance().text("module.cipher.reset"));
        }
    }

    public void initModern(StatusReporter reporter,
            ComboBox<String> globalInputFormatCombo,
            ComboBox<String> globalOutputFormatCombo,
            java.util.function.Supplier<java.security.KeyPair> keyPairSupplier) {
        this.statusReporter = reporter;
        this.cipherInputFormatCombo = globalInputFormatCombo;
        this.outputFormatCombo = globalOutputFormatCombo;
        this.sharedKeyPairSupplier = keyPairSupplier;
        asymmetricInputFormatCombo.valueProperty().bindBidirectional(globalInputFormatCombo.valueProperty());
        asymmetricOutputFormatCombo.valueProperty().bindBidirectional(globalOutputFormatCombo.valueProperty());
        // Same choices as the toolbar the panel's format selectors are bound to.
        asymmetricInputFormatCombo.setItems(globalInputFormatCombo.getItems());
        asymmetricOutputFormatCombo.setItems(globalOutputFormatCombo.getItems());
        if (openPgpContainerController != null) {
            openPgpContainerController.setStatusReporter(reporter);
        }
        installPreflightListeners();
    }

    private void installPreflightListeners() {
        if (preflightListenersInstalled) return;
        preflightListenersInstalled = true;
        observePreflight(cipherInputArea);
        observePreflight(symmetricKeyField);
        observePreflight(ivField);
        observePreflight(gcmTagField);
        observePreflight(aadField);
        observePreflight(publicKeyArea);
        observePreflight(privateKeyArea);
        observePreflight(symmetricAlgorithmCombo);
        observePreflight(cipherModeCombo);
        observePreflight(paddingCombo);
        observePreflight(symKeySourceCombo);
        observePreflight(symHsmKeyCombo);
        observePreflight(rsaPaddingCombo);
    }

    private void observePreflight(javafx.scene.control.TextInputControl control) {
        if (control != null) control.textProperty().addListener((obs, previous, value) -> refreshPreflight());
    }

    private void observePreflight(ComboBox<?> control) {
        if (control != null) control.valueProperty().addListener((obs, previous, value) -> refreshPreflight());
    }

    private void refreshPreflight() {
        if (statusReporter instanceof ModernMainController modern) modern.updateReadinessPanel();
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
        this.cipherInputArea = inputArea;
        this.cipherOutputArea = outputArea;
        this.cipherInputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;
        this.publicKeyArea = publicKeyArea;
        this.privateKeyArea = privateKeyArea;
    }

    @FXML
    private void handleReadPublicKeyFile() {
        rsa().readPublicKeyFile();
    }

    @FXML
    private void handleReadPrivateKeyFile() {
        rsa().readPrivateKeyFile();
    }

    /**
     * Set symmetric algorithm ComboBox
     */
    public void setSymmetricAlgorithmCombo(ComboBox<String> combo) {
        this.symmetricAlgorithmCombo = combo;
        symmetricAlgorithmCombo.getItems().addAll(SymmetricCipher.SUPPORTED_ALGORITHMS);
        symmetricAlgorithmCombo.setValue("AES-256");

        // Add listener to update algorithm and mode UI visibility
        symmetricAlgorithmCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateModeAndAlgorithmVisibility());
        symmetricAlgorithmCombo.setOnAction(e -> updateModeAndAlgorithmVisibility());
    }

    private FileCipherCoordinator fileCipher() {
        if (fileCipher == null) {
            fileCipher = new FileCipherCoordinator(new FileCipherCoordinator.View(fileCipherAlgorithmCombo,
                    fileCipherSourceField, fileCipherDestinationField, fileCipherTagField, fileCipherKeyField,
                    fileCipherNonceField, fileCipherAadField, fileCipherResultArea, fileCipherLinesCheck,
                    fileCipherLineEncodingCombo, fileCipherLineCharsetCombo, fileCipherCompactCbcCheck,
                    fileCipherEncryptBtn, fileCipherDecryptBtn), () -> statusReporter, dialogService);
        }
        return fileCipher;
    }

    public void handleFileCipherEncrypt() { fileCipher().handleFileCipherEncrypt(); }

    public void handleFileCipherDecrypt() { fileCipher().handleFileCipherDecrypt(); }

    public void handleExportFileCipherRecipe() { fileCipher().handleExportFileCipherRecipe(); }

    public void handleImportFileCipherRecipe() { fileCipher().handleImportFileCipherRecipe(); }

    public void chooseFileCipherSource() { fileCipher().chooseFileCipherSource(); }

    public void chooseFileCipherDestination() { fileCipher().chooseFileCipherDestination(); }

    public void chooseFileCipherTag() { fileCipher().chooseFileCipherTag(); }

    public void generateFileCipherNonce() { fileCipher().generateFileCipherNonce(); }

    private AsymmetricCipherCoordinator rsa() {
        if (rsa == null) {
            rsa = new AsymmetricCipherCoordinator(new AsymmetricCipherCoordinator.View(rsaPaddingCombo,
                    asymmetricInputFormatCombo, asymmetricOutputFormatCombo, rsaPaddingWarningLabel,
                    rsaPaddingHelpLabel, publicKeyArea, privateKeyArea, cipherInputArea, cipherOutputArea),
                    () -> statusReporter, () -> sharedKeyPairSupplier);
        }
        return rsa;
    }

    /** Returns whether the selected asymmetric operation has key material available without mutating state. */
    public boolean hasAsymmetricKeyAvailable(boolean forEncryption) { return rsa().hasAsymmetricKeyAvailable(forEncryption); }

    public void handleLoadPublicKey() { rsa().handleLoadPublicKey(); }

    public void handleLoadPrivateKey() { rsa().handleLoadPrivateKey(); }

    public void handleAsymmetricEncrypt() { rsa().handleAsymmetricEncrypt(); }

    public void handleAsymmetricDecrypt() { rsa().handleAsymmetricDecrypt(); }

    /**
     * Runs the encrypted-file analyser using the material shown in the File Cipher
     * panel.  The analyser itself is shared with the expert Cipher workflow, so
     * its key/IV/AAD/tag controls are kept in sync immediately before execution.
     * This lets an operator investigate the file they have just encrypted or
     * received without manually duplicating the same material in another panel.
     */
    @FXML
    public void handleAnalyzeFileCipher() {
        try {
            Path source = FileCipherInputs.requiredPath(fileCipherSourceField == null ? null : fileCipherSourceField.getText(),
                    "Source file path");
            if (!Files.isRegularFile(source)) {
                throw new IllegalArgumentException("Source file does not exist or is not a regular file");
            }

            byte[] key = FileCipherInputs.requiredHex(fileCipherKeyField == null ? null : fileCipherKeyField.getText(), "Key");
            if (symmetricKeyField != null) {
                symmetricKeyField.setText(DataConverter.bytesToHex(key));
            }
            if (ivField != null && fileCipherNonceField != null) {
                ivField.setText(fileCipherNonceField.getText().trim());
            }
            if (aadField != null && fileCipherAadField != null) {
                aadField.setText(fileCipherAadField.getText().trim());
            }

            // Streaming AEAD stores the authentication tag separately.  The
            // analysis engine expects it in the common GCM/tag field.
            if (gcmTagField != null && fileCipherTagField != null && !fileCipherTagField.getText().isBlank()) {
                Path tagPath = Path.of(fileCipherTagField.getText().trim());
                if (!Files.isRegularFile(tagPath)) {
                    // Fail loudly instead of silently keeping whatever tag was left over
                    // from a previous, unrelated run: a stale tag makes every AEAD
                    // combination fail authentication with no indication why.
                    throw new IllegalArgumentException("Tag file does not exist or is not a regular file: " + tagPath);
                }
                gcmTagField.setText(DataConverter.bytesToHex(Files.readAllBytes(tagPath)));
            }

            handleAnalyzeEncryptedFile(source);
            if (cipherOutputArea != null && fileCipherResultArea != null) {
                fileCipherResultArea.setText(cipherOutputArea.getText());
            }
        } catch (IllegalArgumentException e) {
            if (statusReporter != null) statusReporter.showError("Encrypted File Analysis", e.getMessage());
        } catch (Exception e) {
            if (statusReporter != null) {
                statusReporter.showError("Encrypted File Analysis", "Cannot analyse file: " + e.getMessage());
            }
        }
    }

    /**
     * Set cipher mode ComboBox
     */
    public void setCipherModeCombo(ComboBox<String> combo) {
        this.cipherModeCombo = combo;
        cipherModeCombo.getItems().addAll(SymmetricCipher.SUPPORTED_MODES);
        cipherModeCombo.setValue("CBC");

        // Add listener to update IV field and GCM Tag field requiremen
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

        // Add listener to disable padding for modes that don't support i
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
        configureHsmKeyCombo(combo);
        if (combo != null) {
            combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(newVal);
                    if (km != null && !km.hasKeyMaterial()) {
                        if (!combo.getStyleClass().contains("field-error")) {
                            combo.getStyleClass().add("field-error");
                        }
                    } else {
                        combo.getStyleClass().remove("field-error");
                    }
                } else {
                    combo.getStyleClass().remove("field-error");
                }
                updateModeAndAlgorithmVisibility();
            });
        }
        refreshHsmKeys();
    }

    private void configureHsmKeyCombo(ComboBox<String> combo) {
        if (combo == null) return;
        combo.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(item);
                    if (km != null) {
                        String kcvShort = km.getKcv() != null && km.getKcv().length() >= 6 ? km.getKcv().substring(0, 6) : km.getKcv();
                        String prefix = km.hasKeyMaterial() ? "" : "[Metadata-only] ";
                        setText(prefix + km.getName() + " — " + km.getAlgorithm() + " — KCV " + kcvShort);
                    } else {
                        setText(item);
                    }
                }
            }
        });
        combo.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String item) {
                if (item == null) return "";
                var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(item);
                if (km != null) {
                    String kcvShort = km.getKcv() != null && km.getKcv().length() >= 6 ? km.getKcv().substring(0, 6) : km.getKcv();
                    String prefix = km.hasKeyMaterial() ? "" : "[Metadata-only] ";
                    return prefix + km.getName() + " — " + km.getAlgorithm() + " — KCV " + kcvShort;
                }
                return item;
            }
            @Override
            public String fromString(String string) {
                return string;
            }
        });
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
        if (inspectKeyBtn != null) {
            inspectKeyBtn.setVisible(isHsm);
            inspectKeyBtn.setManaged(isHsm);
        }
        if (saveToLabBtn != null) {
            saveToLabBtn.setVisible(!isHsm);
            saveToLabBtn.setManaged(!isHsm);
        }
    }

    @FXML
    private void handleInspectKey() {
        if (symHsmKeyCombo == null) return;
        String keyId = symHsmKeyCombo.getValue();
        if (keyId == null || keyId.isEmpty()) {
            statusReporter.showError("Inspect Error", "No HSM key is currently selected to inspect.");
            return;
        }
        if (statusReporter instanceof ModernMainController) {
            ModernMainController mmc = (ModernMainController) statusReporter;
            mmc.navigateTo("Key Lab");
            if (mmc.getKeysController() != null) {
                mmc.getKeysController().selectKeyInKeyLab(keyId);
            }
        }
    }

    public void refreshHsmKeys() {
        if (symHsmKeyCombo != null) {
            String current = symHsmKeyCombo.getValue();
            symHsmKeyCombo.getItems().clear();
            symHsmKeyCombo.getItems().addAll(com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().listKeyIds(
                    com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT));
            if (current != null && symHsmKeyCombo.getItems().contains(current)) {
                symHsmKeyCombo.setValue(current);
            } else if (!symHsmKeyCombo.getItems().isEmpty()) {
                symHsmKeyCombo.setValue(symHsmKeyCombo.getItems().get(0));
            }
        }
    }

    /** Selects a usable Key Lab key for symmetric operations without revealing its bytes. */
    public void selectLabKey(String keyId) {
        var provider = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance();
        var km = provider.getKeyMetadata(keyId);
        if (km == null) throw new IllegalArgumentException("Key Lab entry was not found: " + keyId);
        if (km.getType() != com.cryptocarver.crypto.hsm.KeyType.SYMMETRIC) {
            throw new IllegalArgumentException("Symmetric Cipher requires a symmetric Key Lab entry");
        }
        if ("ARCHIVED".equalsIgnoreCase(km.getStatus())) {
            throw new IllegalArgumentException("Restore the archived Key Lab entry before using it");
        }
        if (!km.hasKeyMaterial()) {
            throw new IllegalArgumentException("The selected Key Lab entry contains metadata only; re-import or regenerate its key material");
        }
        if (!km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT)
                && !km.getUsages().contains(com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT)) {
            throw new IllegalArgumentException("The selected Key Lab entry is not authorized for encryption or decryption");
        }
        symKeySourceCombo.setValue("Simulated HSM");
        refreshHsmKeys();
        if (!symHsmKeyCombo.getItems().contains(keyId)) {
            throw new IllegalArgumentException("The selected key is not available to the Symmetric Cipher workspace");
        }
        symHsmKeyCombo.setValue(keyId);
        selectAlgorithmForLabKey(km);
        updateKeySourceVisibility();
        updateModeAndAlgorithmVisibility();
    }

    private void selectAlgorithmForLabKey(com.cryptocarver.crypto.hsm.KeyMaterial km) {
        if (symmetricAlgorithmCombo == null || km.getAlgorithm() == null) return;
        String stored = km.getAlgorithm().toUpperCase(java.util.Locale.ROOT);
        String target = null;
        if (stored.equals("AES") || stored.startsWith("AES-")) {
            target = "AES-" + km.getSize();
        } else if (stored.equals("3DES") || stored.equals("DESEDE") || stored.contains("TRIPLE DES")) {
            target = "3DES (Triple DES)";
        } else if (stored.equals("DES")) {
            target = "DES";
        } else if (stored.contains("XCHACHA20")) {
            target = "XChaCha20-Poly1305";
        } else if (stored.contains("CHACHA20")) {
            target = "ChaCha20";
        }
        if (target != null && symmetricAlgorithmCombo.getItems().contains(target)) {
            symmetricAlgorithmCombo.setValue(target);
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

            // Prompt user for key name
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("Key-" + algo + "-" + (keyBytes.length * 8));
            dialog.setTitle("Save Key to Lab");
            dialog.setHeaderText("Specify a name for this key in Key Lab:");
            dialog.setContentText("Name:");
            java.util.Optional<String> nameResult = dialog.showAndWait();
            if (!nameResult.isPresent()) {
                return; // User cancelled
            }
            String name = nameResult.get().trim();
            if (name.isEmpty()) name = "Unnamed Key";

            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, algo);
            String id = java.util.UUID.randomUUID().toString();
            com.cryptocarver.crypto.hsm.KeyMaterial km = com.cryptocarver.crypto.hsm.KeyMaterialFactory.fromSecretKey(
                id, secretKey,
                com.cryptocarver.crypto.hsm.KeyExportability.EXPORTABLE,
                java.util.Set.of(com.cryptocarver.crypto.hsm.KeyUsage.ENCRYPT, com.cryptocarver.crypto.hsm.KeyUsage.DECRYPT, com.cryptocarver.crypto.hsm.KeyUsage.MAC)
            );
            km.setName(name);

            // Check duplicate by fingerprint
            var existing = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().findKeyByFingerprint(km.getFingerprint());
            if (existing != null) {
                statusReporter.showError("Save Error", "A key with this fingerprint already exists in the Lab: " + existing.getName() + " (" + existing.getId() + ")");
                return;
            }

            com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().importKey(km);
            refreshHsmKeys();

            if (symHsmKeyCombo != null) {
                symHsmKeyCombo.setValue(km.getId());
            }
            symKeySourceCombo.setValue("Simulated HSM");

            String kcvShort = km.getKcv() != null && km.getKcv().length() >= 6 ? km.getKcv().substring(0, 6) : km.getKcv();
            statusReporter.showInfo("Success", "Key \"" + name + "\" saved to Lab. KCV: " + kcvShort);
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
        if (ivField == null || symmetricAlgorithmCombo == null || cipherModeCombo == null)
            return;

        String algorithm = symmetricAlgorithmCombo.getValue();
        String mode = cipherModeCombo.getValue();
        int ivLength = SymmetricCipher.getRecommendedIvLength(algorithm, mode);
        if (ivLength == 0) return;

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
    public void handleSymmetricEncrypt() {
        lastAeadCiphertext = null;
        lastAeadTag = null;
        if (statusReporter != null && !statusReporter.checkPreflightReadiness("Symmetric Cipher", true)) {
            return;
        }
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
        if (statusReporter != null && !statusReporter.checkPreflightReadiness("Symmetric Cipher", false)) {
            return;
        }
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
            String hsmKeyId = getHsmKeyId();
            byte[] manualKey = hsmKeyId == null ? getManualSymmetricKey() : null;

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
                    statusReporter.showError("Tag Error", "GCM Tag must be 16 bytes (32 hex chars)");
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
            statusReporter.publish(b.build());

        } catch (IllegalArgumentException e) {
            statusReporter.showError(e, "Validation Error", "cipherInputArea");
        } catch (javax.crypto.AEADBadTagException e) {
            statusReporter.showError(e, "Authentication Error", "gcmTagField");
        } catch (Exception e) {
            statusReporter.showError(e, "Decryption Error", "cipherInputArea");
        }
    }

    /**
     * Analyze encrypted file with default options
     */
    public void handleAnalyzeEncryptedFile(Path inputFile) {
        handleAnalyzeEncryptedFile(inputFile, EncryptedFileAnalyzer.FileAnalysisOptions.defaults());
    }

    /**
     * Analyze encrypted file using brute-force strategy against candidate combinations.
     */
    public void handleAnalyzeEncryptedFile(Path inputFile, EncryptedFileAnalyzer.FileAnalysisOptions options) {
        try {
            if (inputFile == null) {
                statusReporter.showError("File Error", "Please select an encrypted file");
                return;
            }

            byte[] key = getSymmetricKeyBytes("analysis");
            EncryptedFileAnalyzer.Outcome outcome = new EncryptedFileAnalyzer(analysisInputs())
                    .analyze(inputFile, key, options);
            if (outcome == null) {
                statusReporter.showError("Input Error", "Input file is empty");
                return;
            }

            cipherOutputArea.setText(outcome.reportText());
            if (outcome.hasCandidate()) {
                statusReporter.updateInspector(
                        "Encrypted File Analysis",
                        outcome.analysedBytes(),
                        outcome.inspectorOutput(),
                        outcome.inspectorDetails());
                OperationHistory.getInstance().addOperation(
                        "Cipher",
                        "Analyze Encrypted File",
                        outcome.historyInput(),
                        outcome.historyResult());
            }
            statusReporter.updateStatus(outcome.status());

        } catch (IllegalArgumentException e) {
            statusReporter.showError("Validation Error", e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Analysis Error", "Error analyzing encrypted file: " + e.getMessage());
        }
    }

    /**
     * Update IV field state based on selected mode
     */
    private void updateIVFieldState() {
        updateModeAndAlgorithmVisibility();
    }

    /**
     * Update GCM Tag field state based on selected mode
     */
    private void updateGcmTagFieldState() {
        updateModeAndAlgorithmVisibility();
    }

    private void updateModeAndAlgorithmVisibility() {
        if (symmetricAlgorithmCombo == null) return;
        String algo = symmetricAlgorithmCombo.getValue() != null ? symmetricAlgorithmCombo.getValue() : "AES-256";
        String mode = cipherModeCombo != null && cipherModeCombo.getValue() != null ? cipherModeCombo.getValue() : "CBC";

        boolean isStreamCipher = SymmetricCipher.isStreamCipher(algo);
        boolean isGCM = !isStreamCipher && mode.equalsIgnoreCase("GCM");
        boolean isECB = !isStreamCipher && mode.equalsIgnoreCase("ECB");
        boolean isChaChaPoly = algo.equalsIgnoreCase("ChaCha20-Poly1305");
        boolean isXChaChaPoly = algo.equalsIgnoreCase("XChaCha20-Poly1305");
        boolean isAEAD = isGCM || isChaChaPoly || isXChaChaPoly;

        // Mode & Padding combo disabled state for Stream Ciphers
        if (isStreamCipher) {
            if (cipherModeCombo != null) { cipherModeCombo.setDisable(true); cipherModeCombo.setStyle("-fx-opacity: 0.5;"); }
            if (paddingCombo != null) { paddingCombo.setDisable(true); paddingCombo.setStyle("-fx-opacity: 0.5;"); }
        } else {
            if (cipherModeCombo != null) { cipherModeCombo.setDisable(false); cipherModeCombo.setStyle("-fx-opacity: 1.0;"); }
            if (paddingCombo != null) {
                boolean supportsPadding = SymmetricCipher.supportsPadding(mode);
                paddingCombo.setDisable(!supportsPadding);
                paddingCombo.setStyle(supportsPadding ? "-fx-opacity: 1.0;" : "-fx-opacity: 0.5;");
            }
        }

        if (isECB) {
            if (ivLabel != null) { ivLabel.setVisible(false); ivLabel.setManaged(false); }
            if (ivContainer != null) { ivContainer.setVisible(false); ivContainer.setManaged(false); }
            if (ivField != null) { ivField.setVisible(false); ivField.setManaged(false); }
            if (gcmTagLabel != null) { gcmTagLabel.setVisible(false); gcmTagLabel.setManaged(false); }
            if (gcmTagField != null) { gcmTagField.setVisible(false); gcmTagField.setManaged(false); }
            if (aadLabel != null) { aadLabel.setVisible(false); aadLabel.setManaged(false); }
            if (aadField != null) { aadField.setVisible(false); aadField.setManaged(false); }
            if (ecbWarningBox != null) { ecbWarningBox.setVisible(true); ecbWarningBox.setManaged(true); }
            if (aeadNoteLabel != null) { aeadNoteLabel.setVisible(false); aeadNoteLabel.setManaged(false); }
        } else if (isAEAD) {
            if (ivLabel != null) { ivLabel.setVisible(true); ivLabel.setManaged(true); }
            if (ivContainer != null) { ivContainer.setVisible(true); ivContainer.setManaged(true); }
            if (ivField != null) {
                ivField.setVisible(true);
                ivField.setManaged(true);
                ivField.setDisable(false);
                if (isXChaChaPoly) {
                    ivField.setPromptText("Hex Nonce (24 bytes recommended for XChaCha20-Poly1305)");
                } else if (isChaChaPoly) {
                    ivField.setPromptText("Hex Nonce (12 bytes recommended for ChaCha20-Poly1305)");
                } else {
                    ivField.setPromptText("Hex Nonce (12 bytes recommended for GCM)");
                }
            }
            if (gcmTagLabel != null) { gcmTagLabel.setVisible(true); gcmTagLabel.setManaged(true); }
            if (gcmTagField != null) {
                gcmTagField.setVisible(true);
                gcmTagField.setManaged(true);
                gcmTagField.setDisable(false);
                gcmTagField.setPromptText("Hex Tag (16 bytes; required for AEAD Decryption)");
            }
            if (aadLabel != null) { aadLabel.setVisible(true); aadLabel.setManaged(true); }
            if (aadField != null) {
                aadField.setVisible(true);
                aadField.setManaged(true);
                aadField.setDisable(false);
            }
            if (ecbWarningBox != null) { ecbWarningBox.setVisible(false); ecbWarningBox.setManaged(false); }
            if (aeadNoteLabel != null) { aeadNoteLabel.setVisible(true); aeadNoteLabel.setManaged(true); }
        } else {
            // CBC, CTR, CFB, OFB, Salsa20, ChaCha20
            if (ivLabel != null) { ivLabel.setVisible(true); ivLabel.setManaged(true); }
            if (ivContainer != null) { ivContainer.setVisible(true); ivContainer.setManaged(true); }
            if (ivField != null) {
                ivField.setVisible(true);
                ivField.setManaged(true);
                ivField.setDisable(false);
                if (isStreamCipher) {
                    ivField.setPromptText("Hex Nonce (8 bytes recommended for " + algo + ")");
                } else {
                    ivField.setPromptText("Hex IV (required for " + mode + " mode)...");
                }
            }
            if (gcmTagLabel != null) { gcmTagLabel.setVisible(false); gcmTagLabel.setManaged(false); }
            if (gcmTagField != null) { gcmTagField.setVisible(false); gcmTagField.setManaged(false); }
            if (aadLabel != null) { aadLabel.setVisible(false); aadLabel.setManaged(false); }
            if (aadField != null) { aadField.setVisible(false); aadField.setManaged(false); }
            if (ecbWarningBox != null) { ecbWarningBox.setVisible(false); ecbWarningBox.setManaged(false); }
            if (aeadNoteLabel != null) { aeadNoteLabel.setVisible(false); aeadNoteLabel.setManaged(false); }
        }

        updateMaterialBadges(algo, mode, isStreamCipher, isAEAD, isXChaChaPoly, isChaChaPoly, isGCM);
    }

    private void initMaterialBadges() {
        if (symKeyBadgeLabel != null && symKeyBadge == null) {
            symKeyBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("Manual Key");
            symKeyBadge.attach(symmetricKeyField, "Hex");
            mirrorMaterialBadge(symKeyBadge, symKeyBadgeLabel);
        }
        if (ivBadgeLabel != null && ivBadge == null) {
            ivBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("IV / Nonce");
            ivBadge.attach(ivField, "Hex");
            mirrorMaterialBadge(ivBadge, ivBadgeLabel);
        }
        if (gcmTagBadgeLabel != null && tagBadge == null) {
            tagBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("AEAD Tag");
            tagBadge.attach(gcmTagField, "Hex");
            mirrorMaterialBadge(tagBadge, gcmTagBadgeLabel);
        }
        if (aadBadgeLabel != null && aadBadge == null) {
            aadBadge = new com.cryptocarver.ui.component.MaterialFieldBadge("AAD");
            aadBadge.attach(aadField, "Hex / ASCII");
            mirrorMaterialBadge(aadBadge, aadBadgeLabel);
        }
    }

    private static void mirrorMaterialBadge(
            com.cryptocarver.ui.component.MaterialFieldBadge source,
            Label target) {
        Runnable sync = () -> {
            target.setText(source.getText());
            target.getStyleClass().setAll(source.getStyleClass());
            boolean hasUsefulStatus = source.getCurrentStatus()
                    != com.cryptocarver.ui.component.MaterialFieldBadge.Status.EMPTY;
            boolean show = hasUsefulStatus && source.isVisible() && source.isManaged();
            target.setVisible(show);
            target.setManaged(show);
        };
        source.textProperty().addListener((obs, oldVal, newVal) -> sync.run());
        source.visibleProperty().addListener((obs, oldVal, newVal) -> sync.run());
        source.managedProperty().addListener((obs, oldVal, newVal) -> sync.run());
        source.getStyleClass().addListener((javafx.collections.ListChangeListener<String>) c -> sync.run());
        sync.run();
    }

    private void updateMaterialBadges(String algo, String mode, boolean isStreamCipher, boolean isAEAD, boolean isXChaChaPoly, boolean isChaChaPoly, boolean isGCM) {
        initMaterialBadges();
        if (symmetricAlgorithmCombo == null) return;

        String algoUpper = algo.toUpperCase();
        int expectedKeyBytes = 32;
        if (algoUpper.contains("AES-192")) expectedKeyBytes = 24;
        else if (algoUpper.contains("AES-128")) expectedKeyBytes = 16;
        else if (algoUpper.contains("3DES") || algoUpper.contains("TRIPLEDES")) expectedKeyBytes = 24;
        else if (algoUpper.contains("DES")) expectedKeyBytes = 8;

        boolean isHsm = symKeySourceCombo != null && ("Simulated HSM".equalsIgnoreCase(symKeySourceCombo.getValue()) || "Lab Cache".equalsIgnoreCase(symKeySourceCombo.getValue()));
        if (isHsm) {
            String selectedKey = symHsmKeyCombo != null ? symHsmKeyCombo.getValue() : null;
            if (selectedKey != null && !selectedKey.isEmpty()) {
                var km = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().getKeyMetadata(selectedKey);
                boolean available = km != null && km.hasKeyMaterial();
                String keyAlgo = km != null && km.getAlgorithm() != null ? km.getAlgorithm() : algo;
                String kcv = km != null ? km.getKcv() : null;
                if (symKeyBadge != null) symKeyBadge.updateStateKeyReference(selectedKey, keyAlgo, kcv, available);
            } else if (symKeyBadge != null) {
                symKeyBadge.updateStateIncomplete("Select HSM Key from Lab");
            }
        } else if (symKeyBadge != null) {
            if (algoUpper.contains("3DES") || algoUpper.contains("TRIPLEDES")) {
                symKeyBadge.setAcceptedByteLengths(16, 24);
            } else {
                symKeyBadge.setExpectedBytes(expectedKeyBytes);
            }
            symKeyBadge.updateState();
        }

        int expectedNonceBytes = SymmetricCipher.getRecommendedIvLength(algo, mode);
        if (ivBadge != null) {
            if (expectedNonceBytes > 0) ivBadge.setExpectedBytes(expectedNonceBytes);
            ivBadge.updateState();
        }
        if (tagBadge != null) {
            tagBadge.setExpectedBytes(16);
            tagBadge.updateState();
        }
        if (aadBadge != null) {
            aadBadge.updateState();
        }

        setBadgeVisibility(symKeyBadgeLabel, symKeyBadge != null
                && symKeyBadge.getCurrentStatus() != com.cryptocarver.ui.component.MaterialFieldBadge.Status.EMPTY);
        setBadgeVisibility(ivBadgeLabel, expectedNonceBytes > 0 && ivBadge != null
                && ivBadge.getCurrentStatus() != com.cryptocarver.ui.component.MaterialFieldBadge.Status.EMPTY);
        setBadgeVisibility(gcmTagBadgeLabel, isAEAD && tagBadge != null
                && tagBadge.getCurrentStatus() != com.cryptocarver.ui.component.MaterialFieldBadge.Status.EMPTY);
        setBadgeVisibility(aadBadgeLabel, isAEAD && aadBadge != null
                && aadBadge.getCurrentStatus() != com.cryptocarver.ui.component.MaterialFieldBadge.Status.EMPTY);
    }

    private static void setBadgeVisibility(Label badgeLabel, boolean visible) {
        if (badgeLabel != null) {
            badgeLabel.setVisible(visible);
            badgeLabel.setManaged(visible);
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
        updateModeAndAlgorithmVisibility();
    }

    /**
     * Get input data as bytes
     */
    private byte[] getInputDataAsBytes() {
        String input = cipherInputArea.getText().trim();
        if (input.isEmpty()) {
            return null;
        }

        String format = cipherInputFormatCombo.getValue();
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
            statusReporter.updateStatus("Encrypted using ChaCha20");
            statusReporter.publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(ciphertext)
                    .detail("Algorithm", "ChaCha20")
                    .status("Encrypted using ChaCha20")
                    .build());

        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
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
            statusReporter.updateStatus("Decrypted using ChaCha20");
            statusReporter.publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(ciphertext)
                    .output(plaintext)
                    .detail("Algorithm", "ChaCha20")
                    .status("Decrypted using ChaCha20")
                    .build());
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
        }
    }

    private void handleSalsa20Encrypt(byte[] plaintext, String hsmKeyId, byte[] manualKey) {
        try {
            String algorithm = "Salsa20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] ciphertext;
            if (hsmKeyId != null) {
                ciphertext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().encryptSymmetric(hsmKeyId, plaintext, algorithm, "None", "NoPadding", iv);
            } else {
                ciphertext = SymmetricCipher.encrypt(plaintext, manualKey, algorithm, "None", "NoPadding", iv);
            }
            setOutputData(ciphertext);
            statusReporter.updateStatus("Encrypted using Salsa20");
            statusReporter.publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(ciphertext)
                    .detail("Algorithm", "Salsa20")
                    .status("Encrypted using Salsa20")
                    .build());

        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
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
            statusReporter.updateStatus("Encrypted using ChaCha20-Poly1305");
            statusReporter.publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(combined)
                    .enrichedOutput(enriched)
                    .detail("Algorithm", "ChaCha20-Poly1305")
                    .status("Encrypted using ChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
        }
    }

    private void handleSalsa20Decrypt(byte[] ciphertext, String hsmKeyId, byte[] manualKey) {
        try {
            String algorithm = "Salsa20";
            byte[] iv = DataConverter.hexToBytes(ivField.getText().trim());
            byte[] plaintext;
            if (hsmKeyId != null) {
                plaintext = com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().decryptSymmetric(hsmKeyId, ciphertext, algorithm, "None", "NoPadding", iv);
            } else {
                plaintext = SymmetricCipher.decrypt(ciphertext, manualKey, algorithm, "None", "NoPadding", iv);
            }
            setOutputData(plaintext);
            statusReporter.updateStatus("Decrypted using Salsa20");
            statusReporter.publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(ciphertext)
                    .output(plaintext)
                    .detail("Algorithm", "Salsa20")
                    .status("Decrypted using Salsa20")
                    .build());
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
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
            statusReporter.updateStatus("Decrypted using ChaCha20-Poly1305");
            statusReporter.publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(combined)
                    .output(plaintext)
                    .detail("Algorithm", "ChaCha20-Poly1305")
                    .status("Decrypted using ChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
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
            statusReporter.updateStatus("Encrypted using XChaCha20-Poly1305");
            statusReporter.publish(OperationResult.forOperation("Symmetric Encrypt")
                    .input(plaintext)
                    .output(combined)
                    .enrichedOutput(enriched)
                    .detail("Algorithm", "XChaCha20-Poly1305")
                    .status("Encrypted using XChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            statusReporter.showError("Encryption Error", e.getMessage());
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
            statusReporter.updateStatus("Decrypted using XChaCha20-Poly1305");
            statusReporter.publish(OperationResult.forOperation("Symmetric Decrypt")
                    .input(combined)
                    .output(plaintext)
                    .detail("Algorithm", "XChaCha20-Poly1305")
                    .status("Decrypted using XChaCha20-Poly1305")
                    .build());
        } catch (Exception e) {
            statusReporter.showError("Decryption Error", e.getMessage());
        }
    }

    // --- Helper Methods for Global Toolbar ---

    public void handleClear() {
        if (cipherInputArea != null)
            cipherInputArea.clear();
        if (cipherOutputArea != null)
            cipherOutputArea.clear();
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
        return cipherOutputArea != null ? cipherOutputArea.getText() : "";
    }

    public TextArea getOutputArea() {
        return cipherOutputArea;
    }

    public TextArea getFileResultArea() {
        return fileCipherResultArea;
    }

    public boolean isPrimaryOutput(TextArea area) {
        return area != null && area == cipherOutputArea;
    }

    public String getAuthenticationTagText() {
        return gcmTagField == null ? "" : gcmTagField.getText();
    }

    private EncryptedFileAnalyzer.CipherInputs analysisInputs() {
        return new EncryptedFileAnalyzer.CipherInputs(
                ivField != null ? ivField.getText() : "",
                aadField != null ? aadField.getText() : "",
                aadField != null && !aadField.isDisabled(),
                gcmTagField != null ? gcmTagField.getText() : "");
    }

    private byte[] getSymmetricKeyBytes(String operation) {
        String keyHex = symmetricKeyField != null ? symmetricKeyField.getText().trim() : "";
        if (keyHex.isEmpty()) {
            throw new IllegalArgumentException("Please enter " + operation + " key in hexadecimal");
        }
        return DataConverter.hexToBytes(keyHex);
    }

    public void fillSymmetricCipherInput(String value, com.cryptocarver.model.ClipboardEntry.Format format) {
        String targetFormat = "Text (UTF-8)";
        switch (format) {
            case HEX: targetFormat = "Hexadecimal"; break;
            case BASE64: targetFormat = "Base64"; break;
            case BASE64URL: targetFormat = "Base64URL"; break;
            default: break;
        }

        if (cipherInputFormatCombo != null && !cipherInputFormatCombo.getItems().contains(targetFormat)) {
            dialogService.warning("Format Not Supported", "Incompatible Format\n\nThe format " + format
                    + " is not supported by Symmetric Cipher Input.");
            return;
        }

        if (cipherInputArea != null) {
            cipherInputArea.setText(value);
        }
        if (cipherInputFormatCombo != null) {
            cipherInputFormatCombo.setValue(targetFormat);
        }
    }

    /**
     * Rehydrates a structured Shelf package without touching the user's key.
     * The package is intentionally limited to public operation artefacts.
     */
    public void fillSymmetricCipherPackage(com.cryptocarver.model.ShelfPackage packageData) {
        if (packageData == null || !com.cryptocarver.model.ShelfPackage.AUTHENTICATED_CIPHER
                .equals(packageData.getType())) {
            throw new IllegalArgumentException("Unsupported Symmetric Cipher shelf package");
        }
        String format = packageData.artifact("format");
        com.cryptocarver.model.ClipboardEntry.Format entryFormat = switch (format) {
            case "Hexadecimal" -> com.cryptocarver.model.ClipboardEntry.Format.HEX;
            case "Base64" -> com.cryptocarver.model.ClipboardEntry.Format.BASE64;
            case "Base64URL" -> com.cryptocarver.model.ClipboardEntry.Format.BASE64URL;
            default -> com.cryptocarver.model.ClipboardEntry.Format.TEXT;
        };
        fillSymmetricCipherInput(packageData.artifact("ciphertext"), entryFormat);
        if (symmetricAlgorithmCombo != null) symmetricAlgorithmCombo.setValue(packageData.artifact("algorithm"));
        if (cipherModeCombo != null) cipherModeCombo.setValue(packageData.artifact("mode"));
        if (paddingCombo != null) paddingCombo.setValue(packageData.artifact("padding"));
        if (ivField != null) ivField.setText(packageData.artifact("nonce"));
        if (gcmTagField != null) gcmTagField.setText(packageData.artifact("authTag"));
        if (aadField != null) aadField.setText(packageData.artifact("aad") == null ? "" : packageData.artifact("aad"));
    }

    public com.cryptocarver.model.ShelfPackage createAuthenticatedCipherShelfPackage() {
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
        String selectedFormat = outputFormatCombo == null || outputFormatCombo.getValue() == null
                ? "Hexadecimal" : outputFormatCombo.getValue();
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

    private void setupHexValidation(TextField field) {
        if (field == null) return;
        field.textProperty().addListener((obs, old, val) -> {
            if (val != null && !val.trim().isEmpty() && !isValidHex(val.trim())) {
                if (!field.getStyleClass().contains("field-error")) {
                    field.getStyleClass().add("field-error");
                }
            } else {
                field.getStyleClass().remove("field-error");
            }
        });
    }

    private boolean isValidHex(String value) {
        if (value == null) return false;
        return value.matches("^[0-9a-fA-F]*$");
    }

    public ComboBox<String> getSymmetricAlgorithmCombo() { return symmetricAlgorithmCombo; }
    public ComboBox<String> getSymKeySourceCombo() { return symKeySourceCombo; }
    public TextField getIvField() { return ivField; }
    public ComboBox<String> getCipherTemplateCombo() { return cipherTemplateCombo; }
}
