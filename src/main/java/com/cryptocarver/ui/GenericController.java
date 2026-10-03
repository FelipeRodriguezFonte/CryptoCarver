package com.cryptocarver.ui;

import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.crypto.HashOperations;
import com.cryptocarver.crypto.UUIDGenerator;
import com.cryptocarver.crypto.ByteStatistics;
import com.cryptocarver.crypto.BitShifter;
import com.cryptocarver.crypto.HexInspector;
import com.cryptocarver.crypto.CompressionCodec;
import com.cryptocarver.crypto.CharsetInspector;
import com.cryptocarver.crypto.TraceHexExtractor;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecRegistry;
import com.cryptocarver.codec.CodecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Accordion;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.CheckBox;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Controller for Generic cryptography operations - Enhanced
 *
 * @author Felipe
 */
public class GenericController {

    private static final Logger LOG = LoggerFactory.getLogger(GenericController.class);

    @FunctionalInterface
    interface BatchRunnerExecutor {
        com.cryptocarver.model.batch.BatchRunner.Report run(
                java.util.List<java.util.Map<String, String>> rows,
                com.cryptocarver.model.batch.BatchRunner.RowOperation operation,
                java.util.function.BooleanSupplier cancellationRequested,
                com.cryptocarver.model.batch.BatchRunner.ProgressListener progressListener);
    }

    private ComboBox<String> inputFormatCombo;
    private ComboBox<String> outputFormatCombo;
    /** The operation whose format contract is currently presented in the shell toolbar. */
    private String activeFormatContractOperation;
    private boolean synchronizingFormatControls;
    private StatusReporter statusReporter;
    private boolean preflightListenersInstalled;
    @FXML private Accordion genericContainer;
    private ModuleI18n.Binding moduleI18n;

    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }
    @FXML private TextArea hashInputArea;
    @FXML private TextArea hashOutputArea;
    @FXML private ResultPanel genericResultPanel;

    @FXML private ComboBox<String> batchInputFormatCombo;
    @FXML private ComboBox<String> batchOperationCombo;
    @FXML private TextField batchColumnField;
    @FXML private ComboBox<String> batchAlgorithmCombo;
    @FXML private ComboBox<String> batchRecordEncodingCombo;
    @FXML private javafx.scene.control.PasswordField batchKeyField;
    @FXML private TextField batchIvNonceField;
    @FXML private TextField batchAadField;
    @FXML private ComboBox<String> batchCharsetCombo;
    @FXML private CheckBox batchStopOnErrorCheck;
    @FXML private CheckBox batchCompactModeCheck;
    @FXML private TextField batchOutputColumnField;
    @FXML private javafx.scene.layout.VBox batchCryptoConfigBox;

    @FXML private TextArea batchInputArea;
    @FXML private ComboBox<String> batchExportFormatCombo;
    @FXML private javafx.scene.control.ProgressBar batchProgressBar;
    @FXML private javafx.scene.control.Label batchStatusLabel;
    @FXML private TextArea batchResultArea;

    @FXML private CheckBox ebcdicConversionCheck;
    @FXML private ComboBox<String> ebcdicDirectionCombo;
    @FXML private ComboBox<String> ebcdicCodePageCombo;
    @FXML private ComboBox<String> endianWordSizeCombo;
    @FXML private ComboBox<String> compressionFormatCombo;
    @FXML private TextField bitShiftBitsField;

    @FXML private TextField checkDigitInput;
    @FXML private TextField checkDigitOutput;

    @FXML private TitledPane compressedHexPane;
    @FXML private CompressedHexController compressedHexPaneController;

    @FXML private KeyCertificateWorkbenchController keyCertificateWorkbenchController;
    @FXML private javafx.scene.layout.VBox keyCertificateWorkbench;

    public KeyCertificateWorkbenchController getKeyCertificateWorkbenchController() {
        return keyCertificateWorkbenchController;
    }

    @FXML private CryptoEnvelopeInspectorController cryptoEnvelopeInspectorController;
    @FXML private TitledPane cryptoEnvelopeInspector;

    public CryptoEnvelopeInspectorController getCryptoEnvelopeInspectorController() {
        return cryptoEnvelopeInspectorController;
    }


    // UI Components for Generic tab
    @FXML private ComboBox<String> hashTemplateCombo;
    @FXML private ComboBox<String> hashAlgorithmCombo;
    @FXML private ComboBox<String> checkDigitAlgorithmCombo;
    @FXML private javafx.scene.control.TextField randomBytesField;
    @FXML private ComboBox<String> randomFormatCombo;

    // Modular Arithmetic components
    @FXML private ComboBox<String> modOperationCombo;
    @FXML private TextField modOperandAField;
    @FXML private TextField modOperandBField;
    @FXML private TextField modModulusField;
    @FXML private TextArea modResultArea;

    // File Converter components
    @FXML private TextField fileInputPathField;
    @FXML private TextField fileOutputPathField;
    @FXML private ComboBox<String> fileInputFormatCombo;
    @FXML private ComboBox<String> fileOutputFormatCombo;
    @FXML private ComboBox<String> fileEncodingCombo;
    // UUID components
    @FXML private TextField uuidOutputField;
    // Specific Output Areas
    @FXML private TextArea randomOutputArea;
    @FXML private TextInputControl checkDigitOutputArea;

    @FXML private TextArea fileResultArea;
    @FXML private TextField fileComparePathField;

    public void fillHashInput(String text) {
        if (hashInputArea != null) {
            hashInputArea.setText(text);
        }
    }

    public void fillHashInput(String text, com.cryptocarver.model.ClipboardEntry.Format format) {
        fillHashInput(text);
        if (statusReporter != null) statusReporter.setInputFormat(clipboardFormatName(format));
    }

    private String clipboardFormatName(com.cryptocarver.model.ClipboardEntry.Format format) {
        return switch (format == null ? com.cryptocarver.model.ClipboardEntry.Format.UNKNOWN : format) {
            case HEX -> "Hexadecimal";
            case BASE64 -> "Base64";
            case BASE64URL -> "Base64URL";
            default -> "Text (UTF-8)";
        };
    }

    // Manual Conversion Components
    @FXML private ComboBox<String> manualTemplateCombo;
    @FXML private TextArea manualInputArea;
    @FXML private TextArea manualOutputArea;
    @FXML private ComboBox<String> manualInputFormatCombo;
    @FXML private ComboBox<String> manualOutputFormatCombo;


    /**
     * Convert hex string to byte array (replacement for
     * DatatypeConverter.parseHexBinary)
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert byte array to hex string (replacement for
     * DatatypeConverter.printHexBinary)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }


    public void setStatusReporter(StatusReporter reporter) {
        this.statusReporter = reporter;
        if (compressedHexPaneController != null) {
            compressedHexPaneController.setReporter(reporter);
        }
        installPreflightListeners();
    }

    private void installPreflightListeners() {
        if (preflightListenersInstalled) return;
        preflightListenersInstalled = true;
        if (hashInputArea != null) hashInputArea.textProperty().addListener((obs, previous, value) -> refreshPreflight());
        if (hashAlgorithmCombo != null) hashAlgorithmCombo.valueProperty().addListener((obs, previous, value) -> refreshPreflight());
    }

    private void refreshPreflight() {
        if (statusReporter instanceof ModernMainController modern) modern.updateReadinessPanel();
    }

    public GenericController() {}

    /** Test seam for controlling batch execution without changing production timing. */
    void setBatchRunnerExecutorForTesting(BatchRunnerExecutor executor) {
        batchRunner().setExecutor(executor);
    }

    /** The running batch task, or null; read by tests. */
    javafx.concurrent.Task<com.cryptocarver.model.batch.BatchRunner.Report> activeBatchTask() {
        return batchRunner().activeTask();
    }

    /** The last completed batch report, or null; read by tests. */
    com.cryptocarver.model.batch.BatchRunner.Report lastBatchReport() {
        return batchRunner().lastReport();
    }

    private CheckDigitCoordinator checkDigits;
    private ModularArithmeticCoordinator modularArithmetic;

    private CheckDigitCoordinator checkDigits() {
        if (checkDigits == null) {
            checkDigits = new CheckDigitCoordinator(new CheckDigitCoordinator.View(checkDigitInput,
                    checkDigitAlgorithmCombo, checkDigitOutput), () -> statusReporter);
        }
        return checkDigits;
    }

    private ModularArithmeticCoordinator modularArithmetic() {
        if (modularArithmetic == null) {
            modularArithmetic = new ModularArithmeticCoordinator(new ModularArithmeticCoordinator.View(modOperationCombo,
                    modOperandAField, modOperandBField, modModulusField, modResultArea), () -> statusReporter);
        }
        return modularArithmetic;
    }

    private BatchRunnerCoordinator batchRunner;
    private FileConversionCoordinator fileConversion;

    private BatchRunnerCoordinator batchRunner() {
        if (batchRunner == null) {
            batchRunner = new BatchRunnerCoordinator(new BatchRunnerCoordinator.View(batchInputFormatCombo,
                    batchOperationCombo, batchColumnField, batchAlgorithmCombo, batchRecordEncodingCombo, batchKeyField,
                    batchIvNonceField, batchAadField, batchCharsetCombo, batchStopOnErrorCheck, batchCompactModeCheck,
                    batchOutputColumnField, batchCryptoConfigBox, batchInputArea, batchExportFormatCombo,
                    batchProgressBar, batchStatusLabel, batchResultArea),
                    () -> statusReporter, this::ownerWindow);
        }
        return batchRunner;
    }

    private FileConversionCoordinator fileConversion() {
        if (fileConversion == null) {
            fileConversion = new FileConversionCoordinator(new FileConversionCoordinator.View(fileInputPathField,
                    fileOutputPathField, fileComparePathField, fileInputFormatCombo, fileOutputFormatCombo,
                    fileEncodingCombo, fileResultArea), () -> statusReporter);
        }
        return fileConversion;
    }

    private javafx.stage.Window ownerWindow() {
        return genericContainer == null || genericContainer.getScene() == null ? null : genericContainer.getScene().getWindow();
    }

    @FXML public void handleBrowseInputFile() { fileConversion().handleBrowseInputFile(); }

    @FXML public void handleBrowseOutputFile() { fileConversion().handleBrowseOutputFile(); }

    @FXML public void handleBrowseCompareFile() { fileConversion().handleBrowseCompareFile(); }

    @FXML public void handleConvertFile() { fileConversion().handleConvertFile(); }

    @FXML public void handleCompareFiles() { fileConversion().compareFiles(); }
    @FXML public void handleHashFileStreaming() { fileConversion().hashFileStreaming(); }
    @FXML public void handlePreviewFileStreaming() { fileConversion().previewFileStreaming(); }

    @FXML public void handleResetBatch() { batchRunner().handleResetBatch(); }

    @FXML public void handleBrowseBatchInput() { batchRunner().handleBrowseBatchInput(); }

    @FXML public void handleRunBatch() { batchRunner().handleRunBatch(); }

    @FXML public void handleDryRunBatch() { batchRunner().handleDryRunBatch(); }

    @FXML public void handleCancelBatch() { batchRunner().handleCancelBatch(); }

    @FXML public void handleExportBatchResults() { batchRunner().handleExportBatchResults(); }

    private String getManualInputFormat() {
        return manualInputFormatCombo != null && manualInputFormatCombo.getValue() != null ? manualInputFormatCombo.getValue() : "Text";
    }

    private String getManualOutputFormat() {
        return manualOutputFormatCombo != null && manualOutputFormatCombo.getValue() != null ? manualOutputFormatCombo.getValue() : "Text";
    }

    @FXML public void handleManualConvert() {
        if (ebcdicConversionCheck != null && ebcdicConversionCheck.isSelected()) {
            convertEBCDIC(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(),
                    ebcdicDirectionCombo.getValue(), ebcdicCodePageCombo.getValue(), manualOutputArea);
        } else {
            convert(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), manualOutputArea);
        }
    }

    @FXML public void handleEncodeBase64Url() { convertBase64Url(manualInputArea.getText(), true, manualOutputArea); }
    @FXML public void handleDecodeBase64Url() { convertBase64Url(manualInputArea.getText(), false, manualOutputArea); }
    @FXML public void handleEncodeBase32() { convertBase32(manualInputArea.getText(), true, manualOutputArea); }
    @FXML public void handleDecodeBase32() { convertBase32(manualInputArea.getText(), false, manualOutputArea); }
    @FXML public void handleConvertEndian() {
        int wordSize = 4;
        if (endianWordSizeCombo != null && endianWordSizeCombo.getValue() != null) {
            try {
                wordSize = Integer.parseInt(endianWordSizeCombo.getValue().split(" ")[0]) / 8;
            } catch (Exception e) {
                wordSize = 4;
            }
        }
        convertEndian(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), wordSize, manualOutputArea);
    }
    @FXML public void handleEncodeUrl() { convertUrlEncoding(manualInputArea.getText(), true, manualOutputArea); }
    @FXML public void handleDecodeUrl() { convertUrlEncoding(manualInputArea.getText(), false, manualOutputArea); }
    @FXML public void handleCompressData() { convertCompression(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), compressionFormatCombo.getValue(), true, manualOutputArea); }
    @FXML public void handleDecompressData() { convertCompression(manualInputArea.getText(), getManualInputFormat(), getManualOutputFormat(), compressionFormatCombo.getValue(), false, manualOutputArea); }
    @FXML public void handleEncodeBcd() { convertPackedDecimal(manualInputArea.getText(), false, true, manualOutputArea); }
    @FXML public void handleDecodeBcd() { convertPackedDecimal(manualInputArea.getText(), false, false, manualOutputArea); }
    @FXML public void handleEncodeComp3() { convertPackedDecimal(manualInputArea.getText(), true, true, manualOutputArea); }
    @FXML public void handleDecodeComp3() { convertPackedDecimal(manualInputArea.getText(), true, false, manualOutputArea); }
    @FXML public void handleShiftLeft() { shiftManualBits(true); }
    @FXML public void handleShiftRight() { shiftManualBits(false); }
    @FXML public void handleExtractTraceHex() {
        try {
            TraceHexExtractor.Extraction result = TraceHexExtractor.extract(manualInputArea.getText());
            manualOutputArea.setText(result.hex());
            if (statusReporter != null) statusReporter.updateStatus("Extracted " + result.byteCount() + " trace bytes");
        } catch (IllegalArgumentException e) {
            manualOutputArea.setText("Error: " + e.getMessage());
        }
    }
    private void shiftManualBits(boolean left) {
        try {
            int bits = Integer.parseInt(bitShiftBitsField == null ? "1" : bitShiftBitsField.getText().trim());
            byte[] input = DataConverter.hexToBytes(manualInputArea.getText().replaceAll("\\s+", ""));
            manualOutputArea.setText(DataConverter.bytesToHex(left ? BitShifter.left(input, bits) : BitShifter.right(input, bits)));
        } catch (IllegalArgumentException e) {
            manualOutputArea.setText("Error: " + e.getMessage());
        }
    }
    @FXML public void handleLaunchProcessDesigner() {
        if (statusReporter instanceof ModernMainController modern) {
            modern.navigateTo("Process Designer");
        } else {
            ProcessDesignerWindow.open();
        }
    }

    @FXML public void initialize() {
        javafx.scene.Node[] excluded = genericContainer == null ? new javafx.scene.Node[0]
                : genericContainer.getPanes().stream()
                        .filter(pane -> pane.getText() != null && pane.getText().contains("Process Designer"))
                        .toArray(javafx.scene.Node[]::new);
        moduleI18n = ModuleI18n.bind(genericContainer, ModuleTextCatalog.generic(), excluded);
        if (genericResultPanel != null) {
            genericResultPanel.connectTo(() -> statusReporter);
            // The module has one result surface but several operations feeding it, so each
            // binding also says where "use as input" should put the value. Modular arithmetic
            // and file operations have no single field to chain into, so they pass none and
            // the button stays hidden while their result is the one on screen.
            bindResult(genericResultPanel, hashOutputArea, "Generic operation",
                    hashInputArea == null ? null : hashInputArea::setText);
            bindResult(genericResultPanel, modResultArea, "Modular arithmetic", null);
            bindResult(genericResultPanel, fileResultArea, "File operation", null);
        }
        batchRunner().configure();
        if (manualInputFormatCombo != null) {
            manualInputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Base94", "Binary", "Decimal");
            manualInputFormatCombo.setValue("Text (UTF-8)");
            manualInputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                    synchronizeToolbarFromManualFormats());
        }
        if (manualOutputFormatCombo != null) {
            manualOutputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Base94", "Binary", "Decimal");
            manualOutputFormatCombo.setValue("Text (UTF-8)");
            manualOutputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                    synchronizeToolbarFromManualFormats());
        }
        if (hashAlgorithmCombo != null) {
            hashAlgorithmCombo.getItems().addAll(HashOperations.SUPPORTED_ALGORITHMS);
            hashAlgorithmCombo.getItems().add("CRC32");
            for (HashOperations.Crc32Variant variant : HashOperations.Crc32Variant.values()) hashAlgorithmCombo.getItems().add(variant.displayName());
            hashAlgorithmCombo.setValue("SHA-256");
        }
        checkDigits().configure();
        if (randomFormatCombo != null) {
            randomFormatCombo.getItems().addAll("Hexadecimal", "Decimal", "Base64", "Binary");
            randomFormatCombo.setValue("Hexadecimal");
            setupRandomFormatComboListener();
        }
        if (ebcdicCodePageCombo != null) {
            ebcdicCodePageCombo.getItems().setAll(EBCDICConverter.supportedCodePages().keySet());
            AppSettings settings = AppSettings.getInstance();
            String savedCodePage = settings.getEBCDICCodePage();
            ebcdicCodePageCombo.setValue(EBCDICConverter.supportedCodePages().containsKey(savedCodePage)
                    ? savedCodePage : "IBM037 — US/Canada");
        }
        if (ebcdicDirectionCombo != null) {
            ebcdicDirectionCombo.getItems().setAll("Decode EBCDIC → UTF-8", "Encode UTF-8 → EBCDIC");
            AppSettings settings = AppSettings.getInstance();
            String savedDirection = settings.getEBCDICDirection();
            ebcdicDirectionCombo.setValue(ebcdicDirectionCombo.getItems().contains(savedDirection)
                    ? savedDirection : "Decode EBCDIC → UTF-8");
        }
        if (endianWordSizeCombo != null) {
            endianWordSizeCombo.getItems().setAll("16 bits (2 bytes)", "32 bits (4 bytes)", "64 bits (8 bytes)", "128 bits (16 bytes)");
            endianWordSizeCombo.setValue("32 bits (4 bytes)");
        }
        if (compressionFormatCombo != null) {
            compressionFormatCombo.getItems().setAll("gzip", "zlib", "deflate");
            compressionFormatCombo.setValue("gzip");
        }
        modularArithmetic().configure();
        fileConversion().configure();

        refreshHashTemplateCombo();
        refreshManualTemplateCombo();

        initializeEBCDICConverter();
    }

    /**
     * Routes one operation's result area into the shared result surface.
     *
     * <p>{@code chainTarget} is where "use as input" should put the value while this operation's
     * result is the one shown; a null one hides the action rather than leaving it inert.
     */
    private static void bindResult(ResultPanel panel, TextArea area, String operation,
                                   java.util.function.Consumer<String> chainTarget) {
        if (panel == null || area == null) return;
        area.textProperty().addListener((obs, oldValue, value) -> {
            panel.showText(operation, value);
            panel.setChainHandler(chainTarget);
        });
    }

    private void refreshHashTemplateCombo() {
        SafeTemplateUIHelper.populateTemplateCombo(
                hashTemplateCombo,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING,
                List.of("SHA-256 — Text UTF-8 → Hex", "SHA-512 — Text UTF-8 → Base64")
        );
    }

    private void refreshManualTemplateCombo() {
        SafeTemplateUIHelper.populateTemplateCombo(
                manualTemplateCombo,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION,
                List.of("Convert Text UTF-8 → Base64", "Convert Hex → Text UTF-8")
        );
    }

    @FXML
    private void handleApplyHashTemplate() {
        String template = hashTemplateCombo != null ? hashTemplateCombo.getValue() : null;
        if (template == null) return;

        Map<String, java.util.function.Consumer<String>> setters = Map.of(
                "hashAlgorithmCombo", v -> { if (hashAlgorithmCombo != null) hashAlgorithmCombo.setValue(v); },
                "inputFormatCombo", this::setSharedInputFormat,
                "outputFormatCombo", this::setSharedOutputFormat
        );

        SafeTemplateUIHelper.applySelectedTemplate(
                template,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING,
                () -> {
                    if (template.contains("SHA-256")) {
                        hashAlgorithmCombo.setValue("SHA-256");
                        if (statusReporter != null) {
                            statusReporter.setInputFormat("Text (UTF-8)");
                            statusReporter.setOutputFormat("Hexadecimal");
                            statusReporter.updateStatus("Template Applied: SHA-256 — Text UTF-8 → Hex. A hash is one-way; it cannot be decrypted.");
                        }
                    } else if (template.contains("SHA-512")) {
                        hashAlgorithmCombo.setValue("SHA-512");
                        if (statusReporter != null) {
                            statusReporter.setInputFormat("Text (UTF-8)");
                            statusReporter.setOutputFormat("Base64");
                            statusReporter.updateStatus("Template Applied: SHA-512 — Text UTF-8 → Base64. A hash is one-way; it cannot be decrypted.");
                        }
                    }
                },
                setters,
                statusReporter
        );
    }

    @FXML
    private void handleSaveHashTemplate() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (hashAlgorithmCombo != null && hashAlgorithmCombo.getValue() != null) params.put("hashAlgorithmCombo", hashAlgorithmCombo.getValue());
        if (inputFormatCombo != null && inputFormatCombo.getValue() != null) params.put("inputFormatCombo", inputFormatCombo.getValue());
        if (outputFormatCombo != null && outputFormatCombo.getValue() != null) params.put("outputFormatCombo", outputFormatCombo.getValue());
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.saveCurrentAsTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, params, this::refreshHashTemplateCombo, statusReporter);
    }

    @FXML
    private void handleExportHashTemplate() {
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.exportSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, hashTemplateCombo, statusReporter);
    }

    @FXML
    private void handleImportHashTemplate() {
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.importTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, this::refreshHashTemplateCombo, statusReporter);
    }

    @FXML
    private void handleDeleteHashTemplate() {
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.deleteSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, hashTemplateCombo, this::refreshHashTemplateCombo, statusReporter);
    }

    @FXML
    private void handleResetHashDefaults() {
        hashAlgorithmCombo.setValue("SHA-256");
        if (statusReporter != null) {
            statusReporter.setInputFormat("Text (UTF-8)");
            statusReporter.setOutputFormat("Hexadecimal");
            statusReporter.updateStatus("Hash form reset to default");
        }
    }

    @FXML
    private void handleApplyManualTemplate() {
        String template = manualTemplateCombo != null ? manualTemplateCombo.getValue() : null;
        if (template == null) return;

        Map<String, java.util.function.Consumer<String>> setters = Map.of(
                "manualInputFormatCombo", v -> selectIfSupported(manualInputFormatCombo, normalizeFormatName(v)),
                "manualOutputFormatCombo", v -> selectIfSupported(manualOutputFormatCombo, normalizeFormatName(v)),
                "inputFormatCombo", this::setSharedInputFormat,
                "outputFormatCombo", this::setSharedOutputFormat,
                "ebcdicDirectionCombo", v -> { if (ebcdicDirectionCombo != null) ebcdicDirectionCombo.setValue(v); }
        );

        SafeTemplateUIHelper.applySelectedTemplate(
                template,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION,
                () -> {
                    if (template.contains("Base64")) {
                        manualInputFormatCombo.setValue("Text (UTF-8)");
                        manualOutputFormatCombo.setValue("Base64");
                        if (statusReporter != null) {
                            statusReporter.setInputFormat("Text (UTF-8)");
                            statusReporter.setOutputFormat("Base64");
                            statusReporter.updateStatus("Template Applied: Convert Text UTF-8 → Base64");
                        }
                    } else if (template.contains("Hex")) {
                        manualInputFormatCombo.setValue("Hexadecimal");
                        manualOutputFormatCombo.setValue("Text (UTF-8)");
                        if (statusReporter != null) {
                            statusReporter.setInputFormat("Hexadecimal");
                            statusReporter.setOutputFormat("Text (UTF-8)");
                            statusReporter.updateStatus("Template Applied: Convert Hex → Text UTF-8");
                        }
                    }
                },
                setters,
                statusReporter
        );
    }

    @FXML
    private void handleSaveManualTemplate() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (manualInputFormatCombo != null && manualInputFormatCombo.getValue() != null) params.put("manualInputFormatCombo", manualInputFormatCombo.getValue());
        if (manualOutputFormatCombo != null && manualOutputFormatCombo.getValue() != null) params.put("manualOutputFormatCombo", manualOutputFormatCombo.getValue());
        if (inputFormatCombo != null && inputFormatCombo.getValue() != null) params.put("inputFormatCombo", inputFormatCombo.getValue());
        if (outputFormatCombo != null && outputFormatCombo.getValue() != null) params.put("outputFormatCombo", outputFormatCombo.getValue());
        if (ebcdicDirectionCombo != null && ebcdicDirectionCombo.getValue() != null) params.put("ebcdicDirectionCombo", ebcdicDirectionCombo.getValue());
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.saveCurrentAsTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, params, this::refreshManualTemplateCombo, statusReporter);
    }

    @FXML
    private void handleExportManualTemplate() {
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.exportSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, manualTemplateCombo, statusReporter);
    }

    @FXML
    private void handleImportManualTemplate() {
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.importTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, this::refreshManualTemplateCombo, statusReporter);
    }

    @FXML
    private void handleDeleteManualTemplate() {
        javafx.stage.Window owner = manualTemplateCombo != null && manualTemplateCombo.getScene() != null ? manualTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.deleteSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_MANUAL_CONVERSION, manualTemplateCombo, this::refreshManualTemplateCombo, statusReporter);
    }

    @FXML
    private void handleResetManualDefaults() {
        manualInputFormatCombo.setValue("Text (UTF-8)");
        manualOutputFormatCombo.setValue("Text (UTF-8)");
        manualInputArea.setText("");
        manualOutputArea.setText("");
        if (statusReporter != null) {
            statusReporter.setInputFormat("Text (UTF-8)");
            statusReporter.setOutputFormat("Text (UTF-8)");
            statusReporter.updateStatus("Manual conversion form reset to default");
        }
    }

    /**
     * Connects the shared format toolbar to Generic's operation-specific controls.
     *
     * <p>The Generic module is FXML-included, so its local hash and conversion
     * controls are independent of the shell toolbar. Keeping this connection
     * explicit prevents the toolbar from advertising a format which the
     * operation then ignores.</p>
     */
    public void setFormatControls(ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo) {
        this.inputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;

        inputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                synchronizeManualFormatsFromToolbar());
        outputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                synchronizeManualFormatsFromToolbar());
    }

    /** Selects which Generic sub-operation owns the shared format toolbar. */
    public void setActiveFormatContractOperation(String operation) {
        activeFormatContractOperation = operation;
        synchronizeManualFormatsFromToolbar();
    }

    private boolean isManualConversionContractActive() {
        return "Manual Conversion".equals(activeFormatContractOperation);
    }

    private boolean isRandomGeneratorContractActive() {
        return "Random Number Generator".equals(activeFormatContractOperation);
    }

    private void synchronizeManualFormatsFromToolbar() {
        if (synchronizingFormatControls) return;

        if (isManualConversionContractActive()) {
            synchronizingFormatControls = true;
            try {
                selectIfSupported(manualInputFormatCombo, inputFormatCombo == null ? null : inputFormatCombo.getValue());
                selectIfSupported(manualOutputFormatCombo, outputFormatCombo == null ? null : outputFormatCombo.getValue());
            } finally {
                synchronizingFormatControls = false;
            }
        } else if (isRandomGeneratorContractActive()) {
            synchronizingFormatControls = true;
            try {
                selectIfSupported(randomFormatCombo, outputFormatCombo == null ? null : outputFormatCombo.getValue());
            } finally {
                synchronizingFormatControls = false;
            }
        }
    }

    private void synchronizeToolbarFromManualFormats() {
        if (!isManualConversionContractActive() || synchronizingFormatControls) return;

        synchronizingFormatControls = true;
        try {
            selectIfSupported(inputFormatCombo, normalizeFormatName(manualInputFormatCombo == null ? null : manualInputFormatCombo.getValue()));
            selectIfSupported(outputFormatCombo, normalizeFormatName(manualOutputFormatCombo == null ? null : manualOutputFormatCombo.getValue()));
        } finally {
            synchronizingFormatControls = false;
        }
    }

    private void synchronizeToolbarFromRandomFormats() {
        if (!isRandomGeneratorContractActive() || synchronizingFormatControls) return;

        synchronizingFormatControls = true;
        try {
            selectIfSupported(outputFormatCombo, randomFormatCombo == null ? null : randomFormatCombo.getValue());
        } finally {
            synchronizingFormatControls = false;
        }
    }

    private void setupRandomFormatComboListener() {
        if (randomFormatCombo != null) {
            randomFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                    synchronizeToolbarFromRandomFormats());
        }
    }

    private static String normalizeFormatName(String format) {
        return "Text".equalsIgnoreCase(format) || "Plain Text".equalsIgnoreCase(format) ? "Text (UTF-8)" : format;
    }

    private void setSharedInputFormat(String format) {
        if (statusReporter != null) {
            statusReporter.setInputFormat(normalizeFormatName(format));
        } else {
            selectIfSupported(inputFormatCombo, normalizeFormatName(format));
        }
    }

    private void setSharedOutputFormat(String format) {
        if (statusReporter != null) {
            statusReporter.setOutputFormat(normalizeFormatName(format));
        } else {
            selectIfSupported(outputFormatCombo, normalizeFormatName(format));
        }
    }

    private static void selectIfSupported(ComboBox<String> combo, String value) {
        if (combo == null) return;
        if (value == null) {
            combo.setValue(null);
        } else if (combo.getItems().contains(value)) {
            combo.setValue(value);
        } else {
            // ComboBox#setValue may retain its previous selection for a value
            // outside the active contract; clearing is safer than executing
            // with stale format state.
            combo.setValue(null);
        }
    }

    public void setHashAlgorithmCombo(ComboBox<String> combo) {
        this.hashAlgorithmCombo = combo;
        hashAlgorithmCombo.getItems().addAll(HashOperations.SUPPORTED_ALGORITHMS);
        hashAlgorithmCombo.getItems().add("CRC32");
        for (HashOperations.Crc32Variant variant : HashOperations.Crc32Variant.values()) hashAlgorithmCombo.getItems().add(variant.displayName());
        hashAlgorithmCombo.setValue("SHA-256");
    }



    public void initializeEBCDICConverter() {
        if (ebcdicCodePageCombo != null && ebcdicDirectionCombo != null && ebcdicConversionCheck != null) {
            ebcdicCodePageCombo.getItems().setAll(EBCDICConverter.supportedCodePages().keySet());
            AppSettings settings = AppSettings.getInstance();
            String savedCodePage = settings.getEBCDICCodePage();
            ebcdicCodePageCombo.setValue(EBCDICConverter.supportedCodePages().containsKey(savedCodePage)
                    ? savedCodePage : "IBM037 — US/Canada");
            ebcdicDirectionCombo.getItems().setAll("Decode EBCDIC → UTF-8", "Encode UTF-8 → EBCDIC");
            String savedDirection = settings.getEBCDICDirection();
            ebcdicDirectionCombo.setValue(ebcdicDirectionCombo.getItems().contains(savedDirection)
                    ? savedDirection : "Decode EBCDIC → UTF-8");

            ebcdicCodePageCombo.valueProperty().addListener((observable, previous, selected) -> settings.setEBCDICCodePage(selected));
            ebcdicDirectionCombo.valueProperty().addListener((observable, previous, selected) -> settings.setEBCDICDirection(selected));
            ebcdicDirectionCombo.disableProperty().bind(ebcdicConversionCheck.selectedProperty().not());
            ebcdicCodePageCombo.disableProperty().bind(ebcdicConversionCheck.selectedProperty().not());
        }
    }

    public void convertEBCDIC(String input, String inputFormat, String outputFormat, String direction, String codePage,
                              TextInputControl targetOutputArea) {
        try {
            byte[] sourceBytes = parseInput(input, inputFormat);
            if (sourceBytes == null) throw new IllegalArgumentException("Input cannot be empty");
            boolean encoding = "Encode UTF-8 → EBCDIC".equals(direction);
            String operation = encoding ? "UTF-8 → EBCDIC Conversion" : "EBCDIC → UTF-8 Conversion";
            byte[] resultBytes;
            String result;

            if (encoding) {
                if ("Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)) {
                    throw new IllegalArgumentException("Choose Hexadecimal, Base64, Binary or Decimal to represent EBCDIC bytes");
                }
                resultBytes = EBCDICConverter.encode(DataConverter.utf8BytesToString(sourceBytes), codePage);
                result = formatBytes(resultBytes, outputFormat);
            } else {
                String decodedText = EBCDICConverter.decode(sourceBytes, codePage);
                resultBytes = decodedText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                result = "Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)
                        ? decodedText : formatBytes(resultBytes, outputFormat);
            }
            targetOutputArea.setText(result);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Input Format", inputFormat);
            details.put("Output Format", outputFormat);
            details.put("EBCDIC Code Page", codePage);
            details.put("Direction", direction);
            statusReporter.publish(OperationResult.forOperation(operation)
                    .input(sourceBytes).output(resultBytes).details(details)
                    .status(operation + " using " + codePage).build());
        } catch (Exception e) {
            statusReporter.showError("EBCDIC Conversion Error", e.getMessage());
        }
    }

    private String formatBytes(byte[] bytes, String outputFormat) {
        try {
            return CodecRegistry.getInstance().encode(bytes, ByteFormat.fromDisplayName(outputFormat));
        } catch (IllegalArgumentException | CodecException e) {
            throw new IllegalArgumentException("Unsupported or invalid byte output format: " + outputFormat, e);
        }
    }

    /** Explicit Base64URL text conversion for JOSE-style payloads. */
    public void convertBase64Url(String input, boolean encode, TextInputControl targetOutputArea) {
        try {
            byte[] inputBytes;
            byte[] outputBytes;
            String output;
            if (encode) {
                inputBytes = CodecRegistry.getInstance().decode(input, ByteFormat.TEXT_UTF8);
                output = CodecRegistry.getInstance().encode(inputBytes, ByteFormat.BASE64_URL);
                outputBytes = CodecRegistry.getInstance().decode(output, ByteFormat.TEXT_ASCII);
            } else {
                inputBytes = CodecRegistry.getInstance().decode(input, ByteFormat.BASE64_URL);
                output = CodecRegistry.getInstance().encode(inputBytes, ByteFormat.TEXT_UTF8);
                outputBytes = inputBytes;
            }
            targetOutputArea.setText(output);
            String operation = encode ? "UTF-8 → Base64URL" : "Base64URL → UTF-8";
            statusReporter.publish(OperationResult.forOperation(operation)
                    .input(encode ? inputBytes : input.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .output(outputBytes).detail("Padding", "None (RFC 4648 / JOSE)")
                    .status(operation + " conversion completed").build());
        } catch (Exception e) {
            statusReporter.showError("Base64URL Conversion Error", e.getMessage());
        }
    }

    /** Explicit Base32 conversion for RFC 4648 interoperability. */
    public void convertBase32(String input, boolean encode, TextInputControl targetOutputArea) {
        try {
            byte[] decoded;
            String output;
            if (encode) {
                decoded = CodecRegistry.getInstance().decode(input, ByteFormat.TEXT_UTF8);
                output = CodecRegistry.getInstance().encode(decoded, ByteFormat.BASE32);
            } else {
                decoded = CodecRegistry.getInstance().decode(input, ByteFormat.BASE32);
                output = CodecRegistry.getInstance().encode(decoded, ByteFormat.TEXT_UTF8);
            }
            targetOutputArea.setText(output);
            String operation = encode ? "UTF-8 → Base32" : "Base32 → UTF-8";
            statusReporter.publish(OperationResult.forOperation(operation)
                    .input(encode ? input.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                            : input.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .output(encode ? output.getBytes(java.nio.charset.StandardCharsets.US_ASCII) : decoded)
                    .detail("Standard", "RFC 4648 Base32")
                    .status(operation + " conversion completed").build());
        } catch (Exception e) {
            statusReporter.showError("Base32 Conversion Error", e.getMessage());
        }
    }

    /** Reverses byte order inside each fixed-width integer (16/32/64/128 bits). */
    public void convertEndian(String input, String inputFormat, String outputFormat, int wordBytes,
                              TextInputControl targetOutputArea) {
        try {
            byte[] source = parseInput(input, inputFormat);
            if (source == null || source.length == 0) throw new IllegalArgumentException("Input cannot be empty");
            if (wordBytes != 2 && wordBytes != 4 && wordBytes != 8 && wordBytes != 16) {
                throw new IllegalArgumentException("Word size must be 2, 4, 8 or 16 bytes");
            }
            if (source.length % wordBytes != 0) {
                throw new IllegalArgumentException("Input length must be a multiple of " + wordBytes + " bytes");
            }
            byte[] converted = source.clone();
            for (int offset = 0; offset < converted.length; offset += wordBytes) {
                for (int left = offset, right = offset + wordBytes - 1; left < right; left++, right--) {
                    byte temporary = converted[left];
                    converted[left] = converted[right];
                    converted[right] = temporary;
                }
            }
            String output = "Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)
                    ? DataConverter.utf8BytesToString(converted) : formatBytes(converted, outputFormat);
            targetOutputArea.setText(output);
            statusReporter.publish(OperationResult.forOperation("Endian Conversion")
                    .input(source).output(converted)
                    .detail("Word Size", (wordBytes * 8) + " bits")
                    .detail("Input Format", inputFormat).detail("Output Format", outputFormat)
                    .status("Byte order converted for " + (wordBytes * 8) + "-bit words").build());
        } catch (Exception e) {
            statusReporter.showError("Endian Conversion Error", e.getMessage());
        }
    }

    public void analyzeBytes(String input, String inputFormat, TextInputControl targetOutputArea) {
        try {
            byte[] bytes = parseInput(input, inputFormat);
            String result = ByteStatistics.analyze(bytes);
            targetOutputArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Byte Statistics")
                    .input(bytes).output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Input Format", inputFormat).status("Byte statistics calculated").build());
        } catch (Exception e) {
            statusReporter.showError("Byte Analysis Error", e.getMessage());
        }
    }

    public void convertUrlEncoding(String input, boolean encode, TextInputControl targetOutputArea) {
        try {
            String result = encode
                    ? java.net.URLEncoder.encode(input, java.nio.charset.StandardCharsets.UTF_8)
                    : java.net.URLDecoder.decode(input, java.nio.charset.StandardCharsets.UTF_8);
            targetOutputArea.setText(result);
            String operation = encode ? "UTF-8 → URL Encoding" : "URL Encoding → UTF-8";
            statusReporter.publish(OperationResult.forOperation(operation)
                    .input(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .status(operation + " completed").build());
        } catch (Exception e) {
            statusReporter.showError("URL Encoding Error", e.getMessage());
        }
    }

    public void xorBuffers(String left, String right, String inputFormat, String outputFormat,
                           TextInputControl targetOutputArea) {
        try {
            byte[] leftBytes = parseInput(left, inputFormat);
            byte[] rightBytes = parseInput(right, inputFormat);
            byte[] result = DataConverter.xor(leftBytes, rightBytes);
            String rendered = "Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)
                    ? DataConverter.utf8BytesToString(result) : formatBytes(result, outputFormat);
            targetOutputArea.setText(rendered);
            statusReporter.publish(OperationResult.forOperation("XOR Buffers")
                    .input(leftBytes).output(result)
                    .detail("Input Format", inputFormat).detail("Output Format", outputFormat)
                    .detail("Second Buffer Bytes", String.valueOf(rightBytes.length))
                    .status("XOR completed for " + result.length + " bytes").build());
        } catch (Exception e) {
            statusReporter.showError("XOR Error", e.getMessage());
        }
    }

    public void compareBuffers(String left, String right, String inputFormat, TextInputControl targetOutputArea) {
        try {
            byte[] leftBytes = parseInput(left, inputFormat);
            byte[] rightBytes = parseInput(right, inputFormat);
            int limit = Math.min(leftBytes.length, rightBytes.length);
            int firstDifference = -1;
            for (int i = 0; i < limit; i++) {
                if (leftBytes[i] != rightBytes[i]) { firstDifference = i; break; }
            }
            boolean equal = firstDifference < 0 && leftBytes.length == rightBytes.length;
            String result;
            if (equal) {
                result = "Buffers are identical (" + leftBytes.length + " bytes).";
            } else if (firstDifference >= 0) {
                result = String.format("Buffers differ at offset %d (0x%X): left=%02X, right=%02X", firstDifference,
                        firstDifference, leftBytes[firstDifference] & 0xFF, rightBytes[firstDifference] & 0xFF);
            } else {
                result = "Buffers match for " + limit + " bytes but lengths differ: left=" + leftBytes.length
                        + ", right=" + rightBytes.length;
            }
            targetOutputArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Compare Buffers")
                    .input(leftBytes).output(rightBytes)
                    .detail("Input Format", inputFormat).detail("Left Length", String.valueOf(leftBytes.length))
                    .detail("Right Length", String.valueOf(rightBytes.length))
                    .detail("Result", equal ? "IDENTICAL" : "DIFFERENT")
                    .status(equal ? "Buffers are identical" : "Buffers differ").build());
        } catch (Exception e) {
            statusReporter.showError("Buffer Comparison Error", e.getMessage());
        }
    }

    public void visualizeControlCharacters(String input, String inputFormat, TextInputControl targetOutputArea) {
        try {
            byte[] bytes = parseInput(input, inputFormat);
            String result = DataConverter.visualizeBytes(bytes);
            targetOutputArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Visualize Control Characters")
                    .input(bytes).output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Input Format", inputFormat).status("Control characters visualized").build());
        } catch (Exception e) {
            statusReporter.showError("Byte Visualization Error", e.getMessage());
        }
    }

    public void inspectHex(String input, String inputFormat, int offset, int length, TextInputControl targetOutputArea) {
        inspectHex(input, inputFormat, offset, length, -1, 0, targetOutputArea);
    }

    public void inspectHex(String input, String inputFormat, int offset, int length, int selectionOffset, int selectionLength, TextInputControl targetOutputArea) {
        try {
            byte[] bytes = parseInput(input, inputFormat);
            String result = HexInspector.render(bytes, offset, length, selectionOffset, selectionLength);
            targetOutputArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Hexadecimal Inspector")
                    .input(bytes).output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Offset", String.valueOf(offset)).detail("Length", String.valueOf(length))
                    .detail("Selection", selectionOffset < 0 ? "None" : selectionOffset + "+" + selectionLength)
                    .status("Hexadecimal view rendered").build());
        } catch (Exception e) {
            statusReporter.showError("Hex Inspector Error", e.getMessage());
        }
    }

    public void convertCompression(String input, String inputFormat, String outputFormat, String format, boolean compress,
                                  TextInputControl targetOutputArea) {
        try {
            byte[] source = parseInput(input, inputFormat);
            byte[] converted = compress ? CompressionCodec.compress(source, format) : CompressionCodec.decompress(source, format);
            String output = "Text".equals(outputFormat) || "Text (UTF-8)".equals(outputFormat)
                    ? DataConverter.utf8BytesToString(converted) : formatBytes(converted, outputFormat);
            targetOutputArea.setText(output);
            String operation = (compress ? "Compress " : "Decompress ") + format;
            statusReporter.publish(OperationResult.forOperation(operation)
                    .input(source).output(converted).detail("Format", format)
                    .detail("Input Bytes", String.valueOf(source.length)).detail("Output Bytes", String.valueOf(converted.length))
                    .status(operation + " completed").build());
        } catch (Exception e) {
            statusReporter.showError("Compression Error", e.getMessage());
        }
    }

    public void compareCharsets(String input, String inputFormat, String ebcdicCodePage, TextInputControl targetOutputArea) {
        try {
            byte[] bytes = parseInput(input, inputFormat);
            String result = CharsetInspector.compare(bytes, ebcdicCodePage);
            targetOutputArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Charset Comparison")
                    .input(bytes).output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("EBCDIC Code Page", ebcdicCodePage).status("Charset interpretations generated").build());
        } catch (Exception e) { statusReporter.showError("Charset Comparison Error", e.getMessage()); }
    }

    public void convertPackedDecimal(String input, boolean comp3, boolean encode, TextInputControl targetOutputArea) {
        try {
            byte[] bytes;
            String output;
            if (encode) {
                bytes = comp3 ? DataConverter.decimalToComp3(input) : DataConverter.decimalToPackedBcd(input);
                output = DataConverter.bytesToHex(bytes);
            } else {
                bytes = DataConverter.hexToBytes(input);
                output = comp3 ? DataConverter.comp3ToDecimal(bytes) : DataConverter.packedBcdToDecimal(bytes);
            }
            targetOutputArea.setText(output);
            String name = comp3 ? "COMP-3" : "Packed BCD";
            String operation = encode ? "Decimal → " + name : name + " → Decimal";
            statusReporter.publish(OperationResult.forOperation(operation)
                    .input(encode ? input.getBytes(java.nio.charset.StandardCharsets.US_ASCII) : bytes)
                    .output(encode ? bytes : output.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    .status(operation + " conversion completed").build());
        } catch (Exception e) { statusReporter.showError("Packed Decimal Error", e.getMessage()); }
    }

    /**
     * Calculate hash of input data
     *
     * @param input            The input string
     * @param algorithm        The hash algorithm
     * @param targetOutputArea The TextArea to display the result
     */
    /**
     * Calculate hash of input data with specified format
     *
     * @param input            The input string
     * @param inputFormat      The format of the input string
     * @param algorithm        The hash algorithm
     * @param targetOutputArea The TextArea to display the result
     */
    public void calculateHash(String input, String inputFormat, String algorithm, TextInputControl targetOutputArea) {
        calculateHash(input, inputFormat, "Hexadecimal", algorithm, targetOutputArea);
    }

    /** Calculates a hash and serializes its bytes using the selected output format. */
    public void calculateHash(String input, String inputFormat, String outputFormat,
            String algorithm, TextInputControl targetOutputArea) {
        try {
            inputFormat = normalizeFormatName(inputFormat);
            outputFormat = normalizeFormatName(outputFormat);
            if (input == null || input.isEmpty()) {
                statusReporter.showError("Input Error", "Please enter data to hash");
                return;
            }

            if (algorithm == null || algorithm.isEmpty()) {
                statusReporter.showError("Algorithm Error", "Please select a hash algorithm");
                return;
            }

            // Parse input based on format
            byte[] inputData;
            try {
                inputData = parseInput(input, inputFormat);
            } catch (IllegalArgumentException e) {
                statusReporter.showError("Input Error", e.getMessage());
                return;
            }

            // Calculate hash
            byte[] hash = HashOperations.calculateHash(inputData, algorithm);
            String formattedHash = formatBytes(hash, outputFormat);

            // Display result
            targetOutputArea.setText(formattedHash);
            statusReporter.publish(OperationResult.forOperation("Hashing: " + algorithm)
                    .input(inputData).output(hash)
                    .detail("Algorithm", algorithm).detail("Input Format", inputFormat)
                    .detail("Output Format", outputFormat)
                    .status("Hash calculated using " + algorithm).build());

        } catch (NoSuchAlgorithmException e) {
            statusReporter.showError("Algorithm Error", "Algorithm not supported: " + e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Hash Error", "Error calculating hash: " + e.getMessage());
        }
    }

    @FXML

    public void handleCalculateHash() {
        if (statusReporter != null && !statusReporter.checkPreflightReadiness("Hashing", true)) {
            return;
        }
        if (hashInputArea != null && hashAlgorithmCombo != null && hashOutputArea != null) {
            calculateHash(hashInputArea.getText(),
                    selectedFormatOrDefault(inputFormatCombo, "Text (UTF-8)"),
                    selectedFormatOrDefault(outputFormatCombo, "Hexadecimal"),
                    hashAlgorithmCombo.getValue(),
                    hashOutputArea);
        }
    }

    private static String selectedFormatOrDefault(ComboBox<String> combo, String defaultFormat) {
        return combo != null && combo.getValue() != null ? combo.getValue() : defaultFormat;
    }

    /**
     * Universal conversion
     */
    public void fillManualConversionInput(String value, com.cryptocarver.model.ClipboardEntry.Format format) {
        String targetFormat = "Text (UTF-8)";
        switch (format) {
            case HEX: targetFormat = "Hexadecimal"; break;
            case BASE64: targetFormat = "Base64"; break;
            case BASE64URL: targetFormat = "Base64URL"; break;
            default: break;
        }

        if (manualInputFormatCombo != null && !manualInputFormatCombo.getItems().contains(targetFormat)) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("Format Not Supported");
            alert.setHeaderText("Incompatible Format");
            alert.setContentText("The format " + format + " is not supported by Manual Conversion.");
            alert.showAndWait();
            return;
        }

        if (manualInputArea != null) {
            manualInputArea.setText(value);
        }
        if (manualInputFormatCombo != null) {
            manualInputFormatCombo.setValue(targetFormat);
        }
    }

    public void convert(String input, String inputFormat, String outputFormat, TextInputControl targetOutputArea) {
        try {
            inputFormat = normalizeFormatName(inputFormat);
            outputFormat = normalizeFormatName(outputFormat);
            if (input == null || input.isEmpty()) {
                statusReporter.showError("Input Error", "Please enter data to convert");
                return;
            }
            if (inputFormat == null || outputFormat == null) {
                statusReporter.showError("Format Error", "Please select both input and output formats");
                return;
            }

            try {
                com.cryptocarver.util.InputValidator.validateInput(input, inputFormat);
            } catch (IllegalArgumentException e) {
                statusReporter.showError("Format Error", e.getMessage());
                return;
            }

            // Parse Input
            byte[] inputData;
            switch (inputFormat) {
                case "Hexadecimal":
                    String cleanHex = input.replaceAll("\\s+", "");
                    if (!DataConverter.isValidHex(cleanHex)) {
                        statusReporter.showError("Input Error", "Invalid hexadecimal input. Use 0-9, A-F.");
                        return;
                    }
                    inputData = DataConverter.hexToBytes(cleanHex);
                    break;
                case "Base64":
                    inputData = DataConverter.decodeBase64Flexible(input);
                    break;
                case "Base64URL":
                    inputData = DataConverter.decodeBase64Url(input);
                    break;
                case "Text (UTF-8)":
                case "Text":
                    inputData = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Binary":
                    try {
                        inputData = DataConverter.binaryToBytes(input.replaceAll("\\s+", ""));
                    } catch (IllegalArgumentException e) {
                        statusReporter.showError("Input Error", "Invalid binary input: " + e.getMessage());
                        return;
                    }
                    break;
                case "Decimal":
                    inputData = DataConverter.decimalToBytes(input);
                    break;
                default:
                    statusReporter.showError("Format Error", "Unsupported input format: " + inputFormat);
                    return;
            }

            // Format Output
            String outputResult;
            switch (outputFormat) {
                case "Hexadecimal":
                    outputResult = bytesToHex(inputData);
                    break;
                case "Base64":
                    outputResult = java.util.Base64.getEncoder().encodeToString(inputData);
                    break;
                case "Base64URL":
                    outputResult = DataConverter.bytesToBase64Url(inputData);
                    break;
                case "Text (UTF-8)":
                case "Text":
                    outputResult = new String(inputData, java.nio.charset.StandardCharsets.UTF_8);
                    break;
                case "Binary":
                    outputResult = DataConverter.bytesToBinary(inputData);
                    break;
                case "Decimal":
                    outputResult = DataConverter.bytesToDecimal(inputData);
                    break;
                default:
                    statusReporter.showError("Format Error", "Unsupported output format: " + outputFormat);
                    return;
            }

            targetOutputArea.setText(outputResult);
            statusReporter.publish(OperationResult.forOperation("Manual Conversion")
                    .input(inputData).output(inputData)
                    .detail("Input Format", inputFormat).detail("Output Format", outputFormat)
                    .status(String.format("Converted from %s to %s", inputFormat, outputFormat)).build());

        } catch (Exception e) {
            statusReporter.showError("Conversion Error", "Error converting data: " + e.getMessage());
        }
    }

    @FXML
    public void handleCalculateCheckDigit() { checkDigits().handleCalculateCheckDigit(); }

    @FXML
    public void handleValidateCheckDigit() { checkDigits().handleValidateCheckDigit(); }

    /** Parse input string based on format. */
    public static byte[] parseInput(String input, String format) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        if (format == null) {
            format = "Hexadecimal";
        }

        try {
            ByteFormat byteFormat = ByteFormat.fromDisplayName(format);
            return CodecRegistry.getInstance().decode(input, byteFormat);
        } catch (CodecException e) {
            throw new IllegalArgumentException("Error parsing " + format + " input: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // Default fallback if format unknown but we try to be safe
            return CodecRegistry.getInstance().decode(input, ByteFormat.HEX);
        }
    }

    /**
     * Generate random bytes
     */
    @FXML

    public void handleGenerateRandom() {
        try {
            String bytesStr = randomBytesField.getText().trim();
            if (bytesStr.isEmpty()) {
                statusReporter.showError("Input Error", "Please enter the number of bytes to generate");
                return;
            }

            int numBytes = Integer.parseInt(bytesStr);
            if (numBytes <= 0 || numBytes > 1024) {
                statusReporter.showError("Input Error", "Number of bytes must be between 1 and 1024");
                return;
            }

            String format = randomFormatCombo.getValue();
            if (format == null) {
                statusReporter.showError("Format Error", "Please select an output format");
                return;
            }

            // Generate random bytes
            java.security.SecureRandom random = new java.security.SecureRandom();
            byte[] randomBytes = new byte[numBytes];
            random.nextBytes(randomBytes);

            // Format output
            String output;
            switch (format) {
                case "Hexadecimal":
                    output = DataConverter.bytesToHex(randomBytes);
                    break;
                case "Decimal":
                    StringBuilder decimal = new StringBuilder();
                    for (byte b : randomBytes) {
                        decimal.append(String.format("%03d ", b & 0xFF));
                    }
                    output = decimal.toString().trim();
                    break;
                case "Base64":
                    output = org.apache.commons.codec.binary.Base64.encodeBase64String(randomBytes);
                    break;
                case "Binary":
                    output = DataConverter.bytesToBinary(randomBytes);
                    break;
                default:
                    output = DataConverter.bytesToHex(randomBytes);
            }

            if (randomOutputArea != null) {
                randomOutputArea.setText(output);
            } else {
                statusReporter.showError("System Error", "No output area defined for random generator");
            }
            statusReporter.publish(OperationResult.forOperation("Random Generation")
                    .output(randomBytes).detail("Format", format)
                    .detail("Requested Bytes", String.valueOf(numBytes))
                    .status("Generated " + numBytes + " random bytes").build());

        } catch (NumberFormatException e) {
            statusReporter.showError("Input Error", "Please enter a valid number");
        } catch (Exception e) {
            statusReporter.showError("Generation Error", "Error generating random bytes: " + e.getMessage());
        }
    }

    @FXML
    public void handleModularCalculate() { modularArithmetic().handleModularCalculate(); }

    /**
     * Generate UUID
     */
    @FXML

    public void handleGenerateUUID() {
        try {
            String uuid = UUIDGenerator.generateUUID();
            if (uuidOutputField != null) {
                uuidOutputField.setText(uuid);
                // Output is reported below through OperationResult.
            }

            statusReporter.publish(OperationResult.forOperation("UUID Generation")
                    .output(uuid.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Type", "UUID v4").status("Generated UUID v4").build());
        } catch (Exception e) {
            statusReporter.showError("UUID Error", "Error generating UUID: " + e.getMessage());
        }
    }
    // --- Global Helper Methods ---

    @FXML


    public void handleClear() {
        // Clear Manual Conversion
        if (manualInputArea != null)
            manualInputArea.clear();
        if (manualOutputArea != null)
            manualOutputArea.clear();

        // Clear Random
        if (randomBytesField != null)
            randomBytesField.clear();
        if (randomOutputArea != null)
            randomOutputArea.clear();

        // Clear Modular Arithmetic
        if (modOperandAField != null)
            modOperandAField.clear();
        if (modOperandBField != null)
            modOperandBField.clear();
        if (modModulusField != null)
            modModulusField.clear();
        if (modResultArea != null)
            modResultArea.clear();

        // Clear UUID
        if (uuidOutputField != null)
            uuidOutputField.clear();

        // Clear Check Digit
        if (checkDigitOutputArea != null)
            checkDigitOutputArea.clear();

        // Clear File Converter
        if (fileInputPathField != null)
            fileInputPathField.clear();
        if (fileOutputPathField != null)
            fileOutputPathField.clear();
        if (fileResultArea != null)
            fileResultArea.clear();
    }

    public String getOutputText() {
        // Check output areas in priority order or all of them

        if (manualOutputArea != null && !manualOutputArea.getText().isEmpty()) {
            return manualOutputArea.getText();
        }

        if (randomOutputArea != null && !randomOutputArea.getText().isEmpty()) {
            return randomOutputArea.getText();
        }

        if (modResultArea != null && !modResultArea.getText().isEmpty()) {
            return modResultArea.getText();
        }

        if (uuidOutputField != null && !uuidOutputField.getText().isEmpty()) {
            return uuidOutputField.getText();
        }

        if (checkDigitOutputArea != null && !checkDigitOutputArea.getText().isEmpty()) {
            return checkDigitOutputArea.getText();
        }

        if (fileResultArea != null && !fileResultArea.getText().isEmpty()) {
            return fileResultArea.getText();
        }

        return "";
    }
}
