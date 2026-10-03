package com.cryptocarver.ui;

import com.cryptocarver.crypto.UUIDGenerator;
import com.cryptocarver.model.OperationResult;
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
import javafx.scene.control.TitledPane;
import javafx.scene.control.Accordion;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.CheckBox;

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

    @FXML private TextArea fileResultArea;
    @FXML private TextField fileComparePathField;

    public void fillHashInput(String text) { hashing().fillHashInput(text); }

    public void fillHashInput(String text, com.cryptocarver.model.ClipboardEntry.Format format) {
        hashing().fillHashInput(text, format);
    }

    // Manual Conversion Components
    @FXML private ComboBox<String> manualTemplateCombo;
    @FXML private TextArea manualInputArea;
    @FXML private TextArea manualOutputArea;
    @FXML private ComboBox<String> manualInputFormatCombo;
    @FXML private ComboBox<String> manualOutputFormatCombo;

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

    private HashingCoordinator hashing;
    private ManualConversionCoordinator manualConversion;

    private HashingCoordinator hashing() {
        if (hashing == null) {
            hashing = new HashingCoordinator(new HashingCoordinator.View(hashTemplateCombo, hashAlgorithmCombo,
                    hashInputArea, hashOutputArea), () -> statusReporter, () -> inputFormatCombo,
                    () -> outputFormatCombo, this::setSharedInputFormat, this::setSharedOutputFormat);
        }
        return hashing;
    }

    private ManualConversionCoordinator manualConversion() {
        if (manualConversion == null) {
            manualConversion = new ManualConversionCoordinator(new ManualConversionCoordinator.View(manualTemplateCombo,
                    manualInputArea, manualOutputArea, manualInputFormatCombo, manualOutputFormatCombo,
                    ebcdicConversionCheck, ebcdicDirectionCombo, ebcdicCodePageCombo, endianWordSizeCombo,
                    compressionFormatCombo, bitShiftBitsField), () -> statusReporter, () -> inputFormatCombo,
                    () -> outputFormatCombo, this::setSharedInputFormat, this::setSharedOutputFormat);
        }
        return manualConversion;
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

    @FXML public void handleManualConvert() { manualConversion().handleManualConvert(); }

    @FXML public void handleEncodeBase64Url() { manualConversion().handleEncodeBase64Url(); }

    @FXML public void handleDecodeBase64Url() { manualConversion().handleDecodeBase64Url(); }

    @FXML public void handleEncodeBase32() { manualConversion().handleEncodeBase32(); }

    @FXML public void handleDecodeBase32() { manualConversion().handleDecodeBase32(); }

    @FXML public void handleConvertEndian() { manualConversion().handleConvertEndian(); }

    @FXML public void handleEncodeUrl() { manualConversion().handleEncodeUrl(); }

    @FXML public void handleDecodeUrl() { manualConversion().handleDecodeUrl(); }

    @FXML public void handleCompressData() { manualConversion().handleCompressData(); }

    @FXML public void handleDecompressData() { manualConversion().handleDecompressData(); }

    @FXML public void handleEncodeBcd() { manualConversion().handleEncodeBcd(); }

    @FXML public void handleDecodeBcd() { manualConversion().handleDecodeBcd(); }

    @FXML public void handleEncodeComp3() { manualConversion().handleEncodeComp3(); }

    @FXML public void handleDecodeComp3() { manualConversion().handleDecodeComp3(); }

    @FXML public void handleShiftLeft() { manualConversion().handleShiftLeft(); }

    @FXML public void handleShiftRight() { manualConversion().handleShiftRight(); }

    @FXML public void handleExtractTraceHex() { manualConversion().handleExtractTraceHex(); }
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
        hashing().configure();
        checkDigits().configure();
        if (randomFormatCombo != null) {
            randomFormatCombo.getItems().addAll("Hexadecimal", "Decimal", "Base64", "Binary");
            randomFormatCombo.setValue("Hexadecimal");
            setupRandomFormatComboListener();
        }
        manualConversion().configure();
        modularArithmetic().configure();
        fileConversion().configure();

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

    @FXML
    private void handleApplyHashTemplate() { hashing().handleApplyHashTemplate(); }

    @FXML
    private void handleSaveHashTemplate() { hashing().handleSaveHashTemplate(); }

    @FXML
    private void handleExportHashTemplate() { hashing().handleExportHashTemplate(); }

    @FXML
    private void handleImportHashTemplate() { hashing().handleImportHashTemplate(); }

    @FXML
    private void handleDeleteHashTemplate() { hashing().handleDeleteHashTemplate(); }

    @FXML
    private void handleResetHashDefaults() { hashing().handleResetHashDefaults(); }

    @FXML
    private void handleApplyManualTemplate() { manualConversion().handleApplyManualTemplate(); }

    @FXML
    private void handleSaveManualTemplate() { manualConversion().handleSaveManualTemplate(); }

    @FXML
    private void handleExportManualTemplate() { manualConversion().handleExportManualTemplate(); }

    @FXML
    private void handleImportManualTemplate() { manualConversion().handleImportManualTemplate(); }

    @FXML
    private void handleDeleteManualTemplate() { manualConversion().handleDeleteManualTemplate(); }

    @FXML
    private void handleResetManualDefaults() { manualConversion().handleResetManualDefaults(); }

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

    static String normalizeFormatName(String format) {
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

    static void selectIfSupported(ComboBox<String> combo, String value) {
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

    static String formatBytes(byte[] bytes, String outputFormat) {
        try {
            return CodecRegistry.getInstance().encode(bytes, ByteFormat.fromDisplayName(outputFormat));
        } catch (IllegalArgumentException | CodecException e) {
            throw new IllegalArgumentException("Unsupported or invalid byte output format: " + outputFormat, e);
        }
    }

    @FXML public void handleCalculateHash() { hashing().handleCalculateHash(); }

    public void fillManualConversionInput(String value, com.cryptocarver.model.ClipboardEntry.Format format) {
        manualConversion().fillManualConversionInput(value, format);
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
        if (checkDigitOutput != null)
            checkDigitOutput.clear();

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

        if (checkDigitOutput != null && !checkDigitOutput.getText().isEmpty()) {
            return checkDigitOutput.getText();
        }

        if (fileResultArea != null && !fileResultArea.getText().isEmpty()) {
            return fileResultArea.getText();
        }

        return "";
    }
}
