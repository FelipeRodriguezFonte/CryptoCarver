package com.cryptocarver.ui;

import com.cryptocarver.crypto.UUIDGenerator;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.model.help.HelpContext;
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
    private com.cryptocarver.model.help.HelpContextState contextualHelpState;
    private ContextualHelpButtonBinder contextualHelpButtons;
    private java.util.function.Consumer<com.cryptocarver.model.help.HelpContext> contextualHelpOpener = ignored -> { };
    private final java.util.Map<String, com.cryptocarver.model.help.HelpContext> savedHelpContexts = new java.util.HashMap<>();
    private String activeContextualHelpOperation = "";
    private String activeContextualHelpRoute = "";
    private final java.util.Map<String, HelpContextAdapter> contextualHelpAdapters = new java.util.LinkedHashMap<>();

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
    @FXML private TitledPane uuidPane;

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

    @FXML private javafx.scene.control.Button help_handleCalculateHash;
    @FXML private javafx.scene.control.Button help_handleResetHashDefaults;
    @FXML private javafx.scene.control.Button help_handleApplyHashTemplate;
    @FXML private javafx.scene.control.MenuButton hashManageTemplateButton;
    @FXML private javafx.scene.control.MenuButton manualManageTemplateButton;
    @FXML private javafx.scene.control.Button help_handleApplyManualTemplate;
    @FXML private javafx.scene.control.Button help_handleResetManualDefaults;
    @FXML private javafx.scene.control.Button help_handleManualConvert;
    @FXML private javafx.scene.control.Button help_handleEncodeBase64Url;
    @FXML private javafx.scene.control.Button help_handleDecodeBase64Url;
    @FXML private javafx.scene.control.Button help_handleEncodeBase32;
    @FXML private javafx.scene.control.Button help_handleDecodeBase32;
    @FXML private javafx.scene.control.Button help_handleConvertEndian;
    @FXML private javafx.scene.control.Button help_handleEncodeUrl;
    @FXML private javafx.scene.control.Button help_handleDecodeUrl;
    @FXML private javafx.scene.control.Button help_handleCompressData;
    @FXML private javafx.scene.control.Button help_handleDecompressData;
    @FXML private javafx.scene.control.Button help_handleEncodeBcd;
    @FXML private javafx.scene.control.Button help_handleDecodeBcd;
    @FXML private javafx.scene.control.Button help_handleEncodeComp3;
    @FXML private javafx.scene.control.Button help_handleDecodeComp3;
    @FXML private javafx.scene.control.Button help_handleShiftLeft;
    @FXML private javafx.scene.control.Button help_handleShiftRight;
    @FXML private javafx.scene.control.Button help_handleExtractTraceHex;
    @FXML private javafx.scene.control.Button help_handleBrowseBatchInput;
    @FXML private javafx.scene.control.Button help_handleRunBatch;
    @FXML private javafx.scene.control.Button help_handleDryRunBatch;
    @FXML private javafx.scene.control.Button help_handleCancelBatch;
    @FXML private javafx.scene.control.Button help_handleResetBatch;
    @FXML private javafx.scene.control.Button help_handleExportBatchResults;
    @FXML private javafx.scene.control.Button help_handleBrowseInputFile;
    @FXML private javafx.scene.control.Button help_handleBrowseOutputFile;
    @FXML private javafx.scene.control.Button help_handleBrowseCompareFile;
    @FXML private javafx.scene.control.Button help_handleConvertFile;
    @FXML private javafx.scene.control.Button help_handleCompareFiles;
    @FXML private javafx.scene.control.Button help_handleHashFileStreaming;
    @FXML private javafx.scene.control.Button help_handlePreviewFileStreaming;
    @FXML private javafx.scene.control.Button help_handleGenerateRandom;
    @FXML private javafx.scene.control.Button help_handleCalculateCheckDigit;
    @FXML private javafx.scene.control.Button help_handleValidateCheckDigit;
    @FXML private javafx.scene.control.Button help_handleModularCalculate;
    @FXML private javafx.scene.control.Button help_handleGenerateUUID;
    @FXML private javafx.scene.control.Button help_handleCopyUUID;

    /** Connects local help affordances after the shell has created its shared inspector state. */
    public void configureContextualHelp(com.cryptocarver.model.help.HelpContextState state,
            java.util.function.Consumer<com.cryptocarver.model.help.HelpContext> opener) {
        if (state == null || contextualHelpButtons != null) return;
        contextualHelpState = state;
        contextualHelpOpener = opener == null ? ignored -> { } : opener;
        configureGenericHelpAdapters(state);
        contextualHelpButtons = new ContextualHelpButtonBinder(state, this::openGenericHelp);
        if (uuidPane != null) uuidPane.expandedProperty().addListener((obs, wasExpanded, expanded) ->
                setActiveContextualHelpOperation(activeContextualHelpOperation));
        bindGenericHelp("op_gen_hash", hashInputArea, "calculate", "input");
        bindGenericHelp("op_gen_hash", hashAlgorithmCombo, "select_algorithm", "algorithm");
        bindGenericHelp("op_gen_hash", hashOutputArea, "calculate", "output");
        bindGenericHelp("op_gen_hash", hashTemplateCombo, "apply_template", "template");
        bindGenericHelp("op_gen_hash", hashManageTemplateButton, "manage_templates", "template");
        bindGenericHelpById("op_gen_hash", "calculate", "", "help_handleCalculateHash");
        bindGenericHelpById("op_gen_hash", "manage_templates", "template", "help_handleResetHashDefaults");
        bindGenericHelpById("op_gen_hash", "apply_template", "template", "help_handleApplyHashTemplate");
        bindGenericHelpDynamic("op_gen_manual", manualInputArea, "convert", "input");
        bindGenericHelp("op_gen_manual", manualInputFormatCombo, "convert", "input_format");
        bindGenericHelp("op_gen_manual", manualOutputFormatCombo, "convert", "output_format");
        bindGenericHelpDynamic("op_gen_manual", manualOutputArea, "convert", "output");
        bindGenericHelp("op_gen_manual", manualTemplateCombo, "manage_templates", "template");
        bindGenericHelp("op_gen_manual", manualManageTemplateButton, "manage_templates", "template");
        bindGenericHelp("op_gen_manual", ebcdicConversionCheck, "convert", "ebcdic_direction");
        bindGenericHelp("op_gen_manual", ebcdicDirectionCombo, "convert", "ebcdic_direction");
        bindGenericHelp("op_gen_manual", ebcdicCodePageCombo, "convert", "code_page");
        bindGenericHelp("op_gen_manual", endianWordSizeCombo, "endian", "word_size");
        bindGenericHelp("op_gen_manual", compressionFormatCombo, "compress", "compression");
        bindGenericHelp("op_gen_manual", bitShiftBitsField, "shift_left", "shift");
        bindGenericHelpById("op_gen_manual", "manage_templates", "template", "help_handleApplyManualTemplate");
        bindGenericHelpById("op_gen_manual", "manage_templates", "template", "help_handleResetManualDefaults");
        bindGenericHelpById("op_gen_manual", "convert", "", "help_handleManualConvert");
        bindGenericHelpById("op_gen_manual", "base64url_encode", "input", "help_handleEncodeBase64Url");
        bindGenericHelpById("op_gen_manual", "base64url_decode", "input", "help_handleDecodeBase64Url");
        bindGenericHelpById("op_gen_manual", "base32_encode", "input", "help_handleEncodeBase32");
        bindGenericHelpById("op_gen_manual", "base32_decode", "input", "help_handleDecodeBase32");
        bindGenericHelpById("op_gen_manual", "endian", "input", "help_handleConvertEndian");
        bindGenericHelpById("op_gen_manual", "url_encode", "input", "help_handleEncodeUrl");
        bindGenericHelpById("op_gen_manual", "url_decode", "input", "help_handleDecodeUrl");
        bindGenericHelpById("op_gen_manual", "compress", "input", "help_handleCompressData");
        bindGenericHelpById("op_gen_manual", "decompress", "input", "help_handleDecompressData");
        bindGenericHelpById("op_gen_manual", "bcd_encode", "input", "help_handleEncodeBcd");
        bindGenericHelpById("op_gen_manual", "bcd_decode", "input", "help_handleDecodeBcd");
        bindGenericHelpById("op_gen_manual", "comp3_encode", "input", "help_handleEncodeComp3");
        bindGenericHelpById("op_gen_manual", "comp3_decode", "input", "help_handleDecodeComp3");
        bindGenericHelpById("op_gen_manual", "shift_left", "input", "help_handleShiftLeft");
        bindGenericHelpById("op_gen_manual", "shift_right", "input", "help_handleShiftRight");
        bindGenericHelpById("op_gen_manual", "extract_trace_hex", "input", "help_handleExtractTraceHex");
        bindGenericHelp("op_gen_batch", batchOperationCombo, "select_operation", "operation");
        bindGenericHelp("op_gen_batch", batchInputFormatCombo, "load_input", "input_format");
        bindGenericHelp("op_gen_batch", batchColumnField, "run", "source_column");
        bindGenericHelp("op_gen_batch", batchOutputColumnField, "run", "destination_column");
        bindGenericHelp("op_gen_batch", batchAlgorithmCombo, "run", "algorithm");
        bindGenericHelp("op_gen_batch", batchRecordEncodingCombo, "run", "record_encoding");
        bindGenericHelp("op_gen_batch", batchKeyField, "run", "key");
        bindGenericHelp("op_gen_batch", batchIvNonceField, "run", "iv_nonce");
        bindGenericHelp("op_gen_batch", batchAadField, "run", "aad");
        bindGenericHelp("op_gen_batch", batchCharsetCombo, "run", "charset");
        bindGenericHelp("op_gen_batch", batchStopOnErrorCheck, "run", "stop_on_error");
        bindGenericHelp("op_gen_batch", batchCompactModeCheck, "run", "compact");
        bindGenericHelp("op_gen_batch", batchInputArea, "load_input", "input");
        bindGenericHelp("op_gen_batch", batchResultArea, "export", "results");
        bindGenericHelp("op_gen_batch", batchExportFormatCombo, "export", "export_format");
        bindGenericHelp("op_gen_batch", batchProgressBar, "run", "progress");
        bindGenericHelp("op_gen_batch", batchStatusLabel, "run", "status");
        bindGenericHelpById("op_gen_batch", "load_input", "", "help_handleBrowseBatchInput");
        bindGenericHelpById("op_gen_batch", "run", "", "help_handleRunBatch");
        bindGenericHelpById("op_gen_batch", "dry_run", "", "help_handleDryRunBatch");
        bindGenericHelpById("op_gen_batch", "cancel", "progress", "help_handleCancelBatch");
        bindGenericHelpById("op_gen_batch", "reset", "input", "help_handleResetBatch");
        bindGenericHelpById("op_gen_batch", "export", "results", "help_handleExportBatchResults");
        bindGenericHelpDynamic("op_gen_file", fileInputPathField, "choose_source", "source_path");
        bindGenericHelpDynamic("op_gen_file", fileOutputPathField, "choose_destination", "destination_path");
        bindGenericHelpDynamic("op_gen_file", fileComparePathField, "compare", "comparison_path");
        bindGenericHelp("op_gen_file", fileInputFormatCombo, "convert", "input_format");
        bindGenericHelp("op_gen_file", fileOutputFormatCombo, "convert", "output_format");
        bindGenericHelp("op_gen_file", fileEncodingCombo, "convert", "encoding");
        bindGenericHelpDynamic("op_gen_file", fileResultArea, "convert", "result");
        bindGenericHelpById("op_gen_file", "choose_source", "source_path", "help_handleBrowseInputFile");
        bindGenericHelpById("op_gen_file", "choose_destination", "destination_path", "help_handleBrowseOutputFile");
        bindGenericHelpById("op_gen_file", "compare", "comparison_path", "help_handleBrowseCompareFile");
        bindGenericHelpById("op_gen_file", "convert", "", "help_handleConvertFile");
        bindGenericHelpById("op_gen_file", "compare", "", "help_handleCompareFiles");
        bindGenericHelpById("op_gen_file", "hash_file", "source_path", "help_handleHashFileStreaming");
        bindGenericHelpById("op_gen_file", "preview", "result", "help_handlePreviewFileStreaming");
        bindGenericHelp("op_gen_random", randomBytesField, "generate", "byte_count");
        bindGenericHelp("op_gen_random", randomFormatCombo, "select_format", "format");
        bindGenericHelp("op_gen_random", randomOutputArea, "generate", "output");
        bindGenericHelpById("op_gen_random", "generate", "", "help_handleGenerateRandom");
        bindGenericHelpDynamic("op_gen_check_digits", checkDigitInput, "calculate", "input");
        bindGenericHelp("op_gen_check_digits", checkDigitAlgorithmCombo, "select_algorithm", "algorithm");
        bindGenericHelpDynamic("op_gen_check_digits", checkDigitOutput, "calculate", "result");
        bindGenericHelpById("op_gen_check_digits", "calculate", "", "help_handleCalculateCheckDigit");
        bindGenericHelpById("op_gen_check_digits", "validate", "", "help_handleValidateCheckDigit");
        bindGenericHelp("op_gen_mod", modOperationCombo, "select_operation", "operation");
        bindGenericHelp("op_gen_mod", modOperandAField, "calculate", "operand_a");
        bindGenericHelp("op_gen_mod", modOperandBField, "calculate", "operand_b");
        bindGenericHelp("op_gen_mod", modModulusField, "calculate", "modulus");
        bindGenericHelp("op_gen_mod", modResultArea, "calculate", "result");
        bindGenericHelpById("op_gen_mod", "calculate", "", "help_handleModularCalculate");
        bindGenericHelp("aux_uuid", uuidOutputField, "generate", "identifier");
        bindGenericHelp("aux_uuid", uuidOutputField, "copy", "identifier");
        bindGenericHelpById("aux_uuid", "generate", "", "help_handleGenerateUUID");
        bindGenericHelpById("aux_uuid", "copy", "", "help_handleCopyUUID");
        if (keyCertificateWorkbenchController != null)
            keyCertificateWorkbenchController.configureContextualHelp(state, this::openGenericHelp);
        if (cryptoEnvelopeInspectorController != null)
            cryptoEnvelopeInspectorController.configureContextualHelp(state, this::openGenericHelp);
        if (compressedHexPaneController != null)
            compressedHexPaneController.configureContextualHelp(state, this::openGenericHelp);
        setActiveContextualHelpOperation(activeContextualHelpOperation);
    }

    private void bindGenericHelp(String operationId, javafx.scene.Node target, String action, String field) {
        if (contextualHelpButtons != null && target != null)
            contextualHelpButtons.bind(operationId, operationId, action, field, target);
    }

    private void bindGenericHelpDynamic(String operationId, javafx.scene.Node target, String fallbackAction, String field) {
        if (contextualHelpButtons != null && target != null)
            contextualHelpButtons.bind(operationId, operationId, () -> activeHelpAction(operationId, fallbackAction, field), field, target);
    }

    private String activeHelpAction(String operationId, String fallbackAction, String field) {
        if (contextualHelpState == null || !operationId.equals(contextualHelpState.current().operationId())) return fallbackAction;
        String activeAction = contextualHelpState.current().actionId();
        return com.cryptocarver.model.help.OperationHelpRegistry.getInstance().find(operationId)
                .stream().flatMap(definition -> definition.actions().stream())
                .filter(action -> action.id().equals(activeAction))
                .anyMatch(action -> action.fields().stream().anyMatch(candidate -> candidate.id().equals(field)))
                ? activeAction : fallbackAction;
    }

    /** Keep shared-field help aligned with the last real action attempted in the visible operation. */
    private void recordContextualAction(String operationId, String actionId) {
        if (contextualHelpState == null || !operationId.equals(effectiveContextualHelpOperation())) return;
        com.cryptocarver.model.help.HelpContext current = contextualHelpState.current();
        if (!operationId.equals(current.operationId())) return;
        contextualHelpState.setAction(actionId);
        contextualHelpState.setField("");
        savedHelpContexts.put(operationId, contextualHelpState.current());
    }

    private void bindGenericHelpById(String operationId, String action, String field, String targetId) {
        if (genericContainer == null) return;
        javafx.scene.Node target = switch (targetId) {
            case "help_handleCalculateHash" -> help_handleCalculateHash;
            case "help_handleResetHashDefaults" -> help_handleResetHashDefaults;
            case "help_handleApplyHashTemplate" -> help_handleApplyHashTemplate;
            case "hashManageTemplateButton" -> hashManageTemplateButton;
            case "manualManageTemplateButton" -> manualManageTemplateButton;
            case "help_handleApplyManualTemplate" -> help_handleApplyManualTemplate;
            case "help_handleResetManualDefaults" -> help_handleResetManualDefaults;
            case "help_handleManualConvert" -> help_handleManualConvert;
            case "help_handleEncodeBase64Url" -> help_handleEncodeBase64Url;
            case "help_handleDecodeBase64Url" -> help_handleDecodeBase64Url;
            case "help_handleEncodeBase32" -> help_handleEncodeBase32;
            case "help_handleDecodeBase32" -> help_handleDecodeBase32;
            case "help_handleConvertEndian" -> help_handleConvertEndian;
            case "help_handleEncodeUrl" -> help_handleEncodeUrl;
            case "help_handleDecodeUrl" -> help_handleDecodeUrl;
            case "help_handleCompressData" -> help_handleCompressData;
            case "help_handleDecompressData" -> help_handleDecompressData;
            case "help_handleEncodeBcd" -> help_handleEncodeBcd;
            case "help_handleDecodeBcd" -> help_handleDecodeBcd;
            case "help_handleEncodeComp3" -> help_handleEncodeComp3;
            case "help_handleDecodeComp3" -> help_handleDecodeComp3;
            case "help_handleShiftLeft" -> help_handleShiftLeft;
            case "help_handleShiftRight" -> help_handleShiftRight;
            case "help_handleExtractTraceHex" -> help_handleExtractTraceHex;
            case "help_handleBrowseBatchInput" -> help_handleBrowseBatchInput;
            case "help_handleRunBatch" -> help_handleRunBatch;
            case "help_handleDryRunBatch" -> help_handleDryRunBatch;
            case "help_handleCancelBatch" -> help_handleCancelBatch;
            case "help_handleResetBatch" -> help_handleResetBatch;
            case "help_handleExportBatchResults" -> help_handleExportBatchResults;
            case "help_handleBrowseInputFile" -> help_handleBrowseInputFile;
            case "help_handleBrowseOutputFile" -> help_handleBrowseOutputFile;
            case "help_handleBrowseCompareFile" -> help_handleBrowseCompareFile;
            case "help_handleConvertFile" -> help_handleConvertFile;
            case "help_handleCompareFiles" -> help_handleCompareFiles;
            case "help_handleHashFileStreaming" -> help_handleHashFileStreaming;
            case "help_handlePreviewFileStreaming" -> help_handlePreviewFileStreaming;
            case "help_handleGenerateRandom" -> help_handleGenerateRandom;
            case "help_handleCalculateCheckDigit" -> help_handleCalculateCheckDigit;
            case "help_handleValidateCheckDigit" -> help_handleValidateCheckDigit;
            case "help_handleModularCalculate" -> help_handleModularCalculate;
            case "help_handleGenerateUUID" -> help_handleGenerateUUID;
            case "help_handleCopyUUID" -> help_handleCopyUUID;
            default -> genericContainer.lookup("#" + targetId);
        };
        bindGenericHelp(operationId, target, action, field);
    }

    private void openGenericHelp(com.cryptocarver.model.help.HelpContext context) {
        if (context == null || !context.operationId().equals(effectiveContextualHelpOperation())) return;
        savedHelpContexts.put(context.operationId(), context);
        contextualHelpOpener.accept(context);
    }

    public void setActiveContextualHelpOperation(String operationId) {
        String next = operationId == null ? "" : operationId;
        String previousOperation = activeContextualHelpOperation;
        String previousRoute = activeContextualHelpRoute;
        activeContextualHelpOperation = next;
        String effectiveRoute = effectiveContextualHelpOperation();
        boolean changed = !next.equals(previousOperation) || !effectiveRoute.equals(previousRoute);
        activeContextualHelpRoute = effectiveRoute;
        if (contextualHelpButtons != null) {
            contextualHelpButtons.setActiveRoute(effectiveRoute);
            contextualHelpButtons.setEnabled(!next.isBlank()
                    && com.cryptocarver.model.AppSettings.getInstance().isContextualHelpEnabled());
        }
        boolean enabled = !next.isBlank() && com.cryptocarver.model.AppSettings.getInstance().isContextualHelpEnabled();
        if (compressedHexPaneController != null) compressedHexPaneController.setActiveContextualHelpOperation(next, enabled);
        if (keyCertificateWorkbenchController != null) keyCertificateWorkbenchController.setActiveContextualHelpOperation(next, enabled);
        if (cryptoEnvelopeInspectorController != null) cryptoEnvelopeInspectorController.setActiveContextualHelpOperation(next, enabled);
        contextualHelpAdapters.forEach((route, adapter) -> {
            if (route.equals(next)) adapter.activate(); else adapter.deactivate();
        });
        if (!changed || effectiveRoute.isBlank() || contextualHelpState == null) return;
        com.cryptocarver.model.help.HelpContext saved = savedHelpContexts.get(effectiveRoute);
        contextualHelpState.setOperation(effectiveRoute);
        contextualHelpState.setAction(saved == null ? "" : saved.actionId());
        contextualHelpState.setField(saved == null ? "" : saved.fieldId());
        HelpContextAdapter activeAdapter = contextualHelpAdapters.get(effectiveRoute);
        if (activeAdapter != null) activeAdapter.refreshCurrentSelections();
    }

    public String activeContextualHelpRoute() { return effectiveContextualHelpOperation(); }

    private String effectiveContextualHelpOperation() {
        return uuidPane != null && uuidPane.isExpanded() ? "aux_uuid" : activeContextualHelpOperation;
    }

    private void configureGenericHelpAdapters(com.cryptocarver.model.help.HelpContextState state) {
        bindHelpSelections("op_gen_hash", Map.of(HelpContext.Selection.GENERIC_ALGORITHM, hashAlgorithmCombo));
        bindHelpSelections("op_gen_manual", Map.of(HelpContext.Selection.GENERIC_FORMAT, manualInputFormatCombo,
                HelpContext.Selection.GENERIC_ENCODING, ebcdicDirectionCombo));
        bindHelpSelections("op_gen_batch", Map.of(HelpContext.Selection.BATCH_OPERATION, batchOperationCombo,
                HelpContext.Selection.GENERIC_FORMAT, batchInputFormatCombo,
                HelpContext.Selection.GENERIC_ALGORITHM, batchAlgorithmCombo,
                HelpContext.Selection.GENERIC_ENCODING, batchRecordEncodingCombo,
                HelpContext.Selection.FORMAT, batchExportFormatCombo));
        bindHelpSelections("op_gen_file", Map.of(HelpContext.Selection.GENERIC_FORMAT, fileInputFormatCombo,
                HelpContext.Selection.FORMAT, fileOutputFormatCombo,
                HelpContext.Selection.GENERIC_ENCODING, fileEncodingCombo));
        bindHelpSelections("op_gen_random", Map.of(HelpContext.Selection.RANDOM_FORMAT, randomFormatCombo));
        bindHelpSelections("op_gen_check_digits", Map.of(HelpContext.Selection.CHECK_DIGIT_ALGORITHM, checkDigitAlgorithmCombo));
        bindHelpSelections("op_gen_mod", Map.of(HelpContext.Selection.MODULAR_OPERATION, modOperationCombo));
        if (cryptoEnvelopeInspectorController != null && cryptoEnvelopeInspectorController.getUnwrapProfileCombo() != null)
            bindHelpSelections("op_gen_envelope_inspector", Map.of(HelpContext.Selection.ENVELOPE_PROFILE,
                    cryptoEnvelopeInspectorController.getUnwrapProfileCombo()));
    }

    private void bindHelpSelections(String route, Map<HelpContext.Selection, javafx.scene.control.ComboBox<String>> selections) {
        if (selections.values().stream().anyMatch(java.util.Objects::isNull)) return;
        HelpContextAdapter adapter = new HelpContextAdapter(contextualHelpState);
        selections.forEach(adapter::bindSelection);
        contextualHelpAdapters.put(route, adapter);
    }

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

    @FXML public void handleBrowseInputFile() { recordContextualAction("op_gen_file", "choose_source"); fileConversion().handleBrowseInputFile(); }

    @FXML public void handleBrowseOutputFile() { recordContextualAction("op_gen_file", "choose_destination"); fileConversion().handleBrowseOutputFile(); }

    @FXML public void handleBrowseCompareFile() { recordContextualAction("op_gen_file", "compare"); fileConversion().handleBrowseCompareFile(); }

    @FXML public void handleConvertFile() { recordContextualAction("op_gen_file", "convert"); fileConversion().handleConvertFile(); }

    @FXML public void handleCompareFiles() { recordContextualAction("op_gen_file", "compare"); fileConversion().compareFiles(); }
    @FXML public void handleHashFileStreaming() { recordContextualAction("op_gen_file", "hash_file"); fileConversion().hashFileStreaming(); }
    @FXML public void handlePreviewFileStreaming() { recordContextualAction("op_gen_file", "preview"); fileConversion().previewFileStreaming(); }

    @FXML public void handleResetBatch() { batchRunner().handleResetBatch(); }

    @FXML public void handleBrowseBatchInput() { batchRunner().handleBrowseBatchInput(); }

    @FXML public void handleRunBatch() { batchRunner().handleRunBatch(); }

    void selectBatchRecordOperation(String operation) {
        if (batchOperationCombo != null) batchOperationCombo.setValue(operation);
    }

    @FXML public void handleDryRunBatch() { batchRunner().handleDryRunBatch(); }

    @FXML public void handleCancelBatch() { batchRunner().handleCancelBatch(); }

    @FXML public void handleExportBatchResults() { batchRunner().handleExportBatchResults(); }

    @FXML public void handleManualConvert() { recordContextualAction("op_gen_manual", "convert"); manualConversion().handleManualConvert(); }

    @FXML public void handleEncodeBase64Url() { recordContextualAction("op_gen_manual", "base64url_encode"); manualConversion().handleEncodeBase64Url(); }

    @FXML public void handleDecodeBase64Url() { recordContextualAction("op_gen_manual", "base64url_decode"); manualConversion().handleDecodeBase64Url(); }

    @FXML public void handleEncodeBase32() { recordContextualAction("op_gen_manual", "base32_encode"); manualConversion().handleEncodeBase32(); }

    @FXML public void handleDecodeBase32() { recordContextualAction("op_gen_manual", "base32_decode"); manualConversion().handleDecodeBase32(); }

    @FXML public void handleConvertEndian() { recordContextualAction("op_gen_manual", "endian"); manualConversion().handleConvertEndian(); }

    @FXML public void handleEncodeUrl() { recordContextualAction("op_gen_manual", "url_encode"); manualConversion().handleEncodeUrl(); }

    @FXML public void handleDecodeUrl() { recordContextualAction("op_gen_manual", "url_decode"); manualConversion().handleDecodeUrl(); }

    @FXML public void handleCompressData() { recordContextualAction("op_gen_manual", "compress"); manualConversion().handleCompressData(); }

    @FXML public void handleDecompressData() { recordContextualAction("op_gen_manual", "decompress"); manualConversion().handleDecompressData(); }

    @FXML public void handleEncodeBcd() { recordContextualAction("op_gen_manual", "bcd_encode"); manualConversion().handleEncodeBcd(); }

    @FXML public void handleDecodeBcd() { recordContextualAction("op_gen_manual", "bcd_decode"); manualConversion().handleDecodeBcd(); }

    @FXML public void handleEncodeComp3() { recordContextualAction("op_gen_manual", "comp3_encode"); manualConversion().handleEncodeComp3(); }

    @FXML public void handleDecodeComp3() { recordContextualAction("op_gen_manual", "comp3_decode"); manualConversion().handleDecodeComp3(); }

    @FXML public void handleShiftLeft() { recordContextualAction("op_gen_manual", "shift_left"); manualConversion().handleShiftLeft(); }

    @FXML public void handleShiftRight() { recordContextualAction("op_gen_manual", "shift_right"); manualConversion().handleShiftRight(); }

    @FXML public void handleExtractTraceHex() { recordContextualAction("op_gen_manual", "extract_trace_hex"); manualConversion().handleExtractTraceHex(); }
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
            manualInputFormatCombo.getItems().setAll(com.cryptocarver.model.OperationFormatRegistry.getInstance()
                    .getProfile("Manual Conversion").allowedInputFormats());
            manualInputFormatCombo.setValue("Text (UTF-8)");
            manualInputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                    synchronizeToolbarFromManualFormats());
        }
        if (manualOutputFormatCombo != null) {
            manualOutputFormatCombo.getItems().setAll(com.cryptocarver.model.OperationFormatRegistry.getInstance()
                    .getProfile("Manual Conversion").allowedOutputFormats());
            manualOutputFormatCombo.setValue("Text (UTF-8)");
            manualOutputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                    synchronizeToolbarFromManualFormats());
        }
        hashing().configure();
        checkDigits().configure();
        if (randomFormatCombo != null) {
            randomFormatCombo.getItems().addAll(com.cryptocarver.model.OperationFormatRegistry.getInstance()
                    .getProfile("Random Number Generator").allowedOutputFormats());
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
    public void handleCalculateCheckDigit() { recordContextualAction("op_gen_check_digits", "calculate"); checkDigits().handleCalculateCheckDigit(); }

    @FXML
    public void handleValidateCheckDigit() { recordContextualAction("op_gen_check_digits", "validate"); checkDigits().handleValidateCheckDigit(); }

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

    @FXML public void handleCopyUUID() {
        if (uuidOutputField == null || uuidOutputField.getText() == null || uuidOutputField.getText().isBlank()) return;
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(uuidOutputField.getText());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }
    // --- Global Helper Methods ---

    @FXML


    public void handleClear() {
        if (hashInputArea != null) hashInputArea.clear();
        if (hashOutputArea != null) hashOutputArea.clear();
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
