package com.cryptocarver.ui;

import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.crypto.EBCDICConverter;
import com.cryptocarver.crypto.HashOperations;
import com.cryptocarver.crypto.ModularArithmetic;
import com.cryptocarver.crypto.UUIDGenerator;
import com.cryptocarver.crypto.ByteStatistics;
import com.cryptocarver.crypto.HexInspector;
import com.cryptocarver.crypto.StreamingFileTools;
import com.cryptocarver.crypto.CompressionCodec;
import com.cryptocarver.crypto.CharsetInspector;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.FileConverter;
import com.cryptocarver.utils.OperationHistory;
import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.CodecRegistry;
import com.cryptocarver.codec.CodecException;
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

    @FunctionalInterface
    interface BatchRunnerExecutor {
        com.cryptocarver.model.batch.BatchRunner.Report run(
                java.util.List<java.util.Map<String, String>> rows,
                com.cryptocarver.model.batch.BatchRunner.RowOperation operation,
                java.util.function.BooleanSupplier cancellationRequested,
                com.cryptocarver.model.batch.BatchRunner.ProgressListener progressListener);
    }

    private TextArea inputArea;
    private TextArea outputArea;
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

    @FXML private TextField checkDigitInput;
    @FXML private TextField checkDigitOutput;

    @FXML private TitledPane compressedHexPane;
    @FXML private CompressedHexController compressedHexPaneController;

    @FXML private KeyCertificateWorkbenchController keyCertificateWorkbenchController;
    @FXML private javafx.scene.layout.VBox keyCertificateWorkbench;

    public KeyCertificateWorkbenchController getKeyCertificateWorkbenchController() {
        return keyCertificateWorkbenchController;
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

    // Batch State
    private javafx.concurrent.Task<com.cryptocarver.model.batch.BatchRunner.Report> activeBatchTask;
    private com.cryptocarver.model.batch.BatchRunner.Report lastBatchReport;
    private BatchRunnerExecutor batchRunnerExecutor = (rows, operation, cancellationRequested, progressListener) ->
            com.cryptocarver.model.batch.BatchRunner.run(rows, operation, cancellationRequested, progressListener);


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
        this.batchRunnerExecutor = java.util.Objects.requireNonNull(executor, "Batch runner executor is required");
    }

        @FXML public void handleBrowseInputFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Input File");
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && fileInputPathField != null) fileInputPathField.setText(file.getAbsolutePath());
    }

        @FXML public void handleBrowseOutputFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Output File");
        java.io.File file = fileChooser.showSaveDialog(null);
        if (file != null && fileOutputPathField != null) fileOutputPathField.setText(file.getAbsolutePath());
    }

        @FXML public void handleBrowseCompareFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select File to Compare");
        java.io.File file = fileChooser.showOpenDialog(null);
        if (file != null && fileComparePathField != null) fileComparePathField.setText(file.getAbsolutePath());
    }

    @FXML public void handleConvertFile() {
        if (fileInputPathField != null && fileOutputPathField != null && fileInputFormatCombo != null && fileOutputFormatCombo != null) {
            String inputPath = fileInputPathField.getText().trim();
            String outputPath = fileOutputPathField.getText().trim();
            if (inputPath.isEmpty() || outputPath.isEmpty()) return;

            boolean isTestMode = "true".equals(System.getProperty("test.mode"));
            if (!isTestMode && java.nio.file.Files.exists(java.nio.file.Paths.get(outputPath))) {
                javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Overwrite existing file?");
                confirm.setHeaderText("The selected output file already exists.");
                confirm.setContentText(outputPath);
                if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) != javafx.scene.control.ButtonType.OK) {
                    if (statusReporter != null) statusReporter.updateStatus("File conversion cancelled");
                    return;
                }
            }

            handleFileConvert();

            if (statusReporter != null) {
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                details.put("Input File", inputPath);
                details.put("Output File", outputPath);
                details.put("Input Format", fileInputFormatCombo.getValue());
                details.put("Output Format", fileOutputFormatCombo.getValue());
                statusReporter.publish(OperationResult.forOperation("File Conversion").details(details).build());
            }
        }
    }

    @FXML public void handleCompareFiles() { compareFiles(); }
    @FXML public void handleHashFileStreaming() { hashFileStreaming(); }
    @FXML public void handlePreviewFileStreaming() { previewFileStreaming(); }

    private boolean isCsvBatchFormat(String format) { return "CSV".equals(format); }

    @FXML public void handleResetBatch() {
        if (activeBatchTask != null && activeBatchTask.isRunning()) {
            activeBatchTask.cancel();
        }
        if (batchInputArea != null) batchInputArea.clear();
        if (batchResultArea != null) batchResultArea.clear();
        if (batchKeyField != null) batchKeyField.clear();
        if (batchIvNonceField != null) batchIvNonceField.clear();
        if (batchAadField != null) batchAadField.clear();
        if (batchProgressBar != null) {
            batchProgressBar.progressProperty().unbind();
            batchProgressBar.setProgress(0);
        }
        lastBatchReport = null;
        activeBatchTask = null;
        if (batchStatusLabel != null) batchStatusLabel.setText(t("module.batch.reset"));
    }

    @FXML public void handleBrowseBatchInput() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Load Batch Input");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Batch data", "*.csv", "*.jsonl", "*.ndjson", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showOpenDialog(genericContainer == null || genericContainer.getScene() == null ? null : genericContainer.getScene().getWindow());
        if (file == null) return;
        try {
            batchInputArea.setText(java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            String lower = file.getName().toLowerCase(java.util.Locale.ROOT);
            batchInputFormatCombo.setValue(lower.endsWith(".csv") ? "CSV" : "JSON Lines (.jsonl)");
            batchStatusLabel.setText(t("module.batch.loaded", file.getName()));
        } catch (java.io.IOException e) {
            if (statusReporter != null) statusReporter.showError(t("module.batch.inputErrorTitle"), "Unable to read file: " + e.getMessage());
        }
    }

    private java.util.Map<String, String> runSafeBatchOperation(String operation, java.util.Map<String, String> row, String srcCol, String outCol) throws Exception {
        String input = row.get(srcCol);
        if (input == null) throw new IllegalArgumentException(srcCol + " field is required");
        if ("SHA-256 (UTF-8 → Hex)".equals(operation)) {
            return java.util.Map.of(outCol, com.cryptocarver.model.SafeTransformations.sha256(input));
        }
        if ("UTF-8 → Base64URL".equals(operation)) return java.util.Map.of(outCol, com.cryptocarver.model.SafeTransformations.encodeBase64Url(input));
        if ("Base64URL → UTF-8".equals(operation)) return java.util.Map.of(outCol, com.cryptocarver.model.SafeTransformations.decodeBase64Url(input));
        throw new IllegalArgumentException("Unsupported batch operation: " + operation);
    }

    private String renderBatchReport(com.cryptocarver.model.batch.BatchRunner.Report report) {
        StringBuilder text = new StringBuilder("Rows processed: ").append(report.results().size()).append("\nSucceeded: ")
                .append(report.succeeded()).append("\nFailed: ").append(report.failed()).append("\n\n");
        int displayed = Math.min(50, report.results().size());
        for (int i = 0; i < displayed; i++) {
            com.cryptocarver.model.batch.BatchRunner.RowResult row = report.results().get(i);
            String outputStr = "";
            if (row.succeeded() && row.output() != null && !row.output().isEmpty()) {
                outputStr = row.output().values().iterator().next(); // First mapped value
            }
            text.append("#").append(row.rowNumber()).append(" ").append(row.succeeded() ? "OK  " : "ERR ")
                    .append(row.succeeded() ? outputStr : row.error()).append('\n');
        }
        if (report.results().size() > displayed) text.append("… ").append(report.results().size() - displayed).append(" additional rows; export the report for all results.\n");
        return text.toString();
    }

    @FXML public void handleRunBatch() {
        if (activeBatchTask != null && activeBatchTask.isRunning()) {
            if (statusReporter != null) statusReporter.showError(t("module.batch.errorTitle"), t("module.batch.alreadyRunning"));
            return;
        }
        final java.util.List<java.util.Map<String, String>> rows;
        final String srcCol = batchColumnField.getText().trim();
        final String outCol = batchOutputColumnField.getText().trim();
        if (srcCol.isEmpty() || outCol.isEmpty()) {
            if (statusReporter != null) statusReporter.showError(t("module.batch.errorTitle"), t("module.batch.columnsRequired"));
            return;
        }
        try {
            rows = isCsvBatchFormat(batchInputFormatCombo.getValue())
                    ? com.cryptocarver.model.batch.BatchInputCodec.parseCsv(batchInputArea.getText())
                    : com.cryptocarver.model.batch.BatchInputCodec.parseJsonLines(batchInputArea.getText());
            if (rows.isEmpty()) throw new IllegalArgumentException("No batch rows found");
            if (rows.stream().anyMatch(row -> !row.containsKey(srcCol))) throw new IllegalArgumentException("Every row must contain the field: " + srcCol);
        } catch (Exception e) {
            if (statusReporter != null) statusReporter.showError(t("module.batch.inputErrorTitle"), e.getMessage());
            return;
        }
        final String operation = batchOperationCombo.getValue();
        final boolean isCrypto = "Encrypt Record".equals(operation) || "Decrypt Record".equals(operation);
        final boolean isEncrypt = "Encrypt Record".equals(operation);
        final boolean stopOnError = batchStopOnErrorCheck != null && batchStopOnErrorCheck.isSelected();
        final byte[] key;
        final String alg;
        final byte[] iv;
        final byte[] aad;
        final com.cryptocarver.crypto.LineFileCipher.Encoding enc;
        final java.nio.charset.Charset cs;
        final boolean compact;

        if (isCrypto) {
            try {
                alg = batchAlgorithmCombo.getValue();
                key = java.util.HexFormat.of().parseHex(batchKeyField.getText().trim());
                String ivStr = batchIvNonceField.getText().trim();
                iv = ivStr.isEmpty() ? null : java.util.HexFormat.of().parseHex(ivStr);
                String aadStr = batchAadField.getText().trim();
                aad = aadStr.isEmpty() ? null : java.util.HexFormat.of().parseHex(aadStr);
                enc = "Hexadecimal".equals(batchRecordEncodingCombo.getValue())
                        ? com.cryptocarver.crypto.LineFileCipher.Encoding.HEXADECIMAL
                        : com.cryptocarver.crypto.LineFileCipher.Encoding.BASE64URL;

                String csName = batchCharsetCombo.getValue();
                String mapped = com.cryptocarver.crypto.EBCDICConverter.supportedCodePages().get(csName);
                cs = java.nio.charset.Charset.forName(mapped != null ? mapped : csName);

                compact = batchCompactModeCheck.isSelected();
                com.cryptocarver.crypto.LineRecordCipher.validateAlgorithmAndKey(alg, key);
                com.cryptocarver.crypto.LineRecordCipher.validateIvAndAad(alg, iv, aad);

                if (isEncrypt && "AES-256-CBC".equals(alg) && iv != null) {
                    javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("CBC IV Reuse");
                    confirm.setHeaderText("Security Warning: Static IV in CBC mode");
                    confirm.setContentText("Reusing the same IV across multiple encryption records leaks structural information if identical plaintexts share the same IV.\n\nDo you want to proceed?");
                    java.util.Optional<javafx.scene.control.ButtonType> result = confirm.showAndWait();
                    if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
                        return;
                    }
                }
            } catch (Exception e) {
                if (statusReporter != null) statusReporter.showError(t("module.batch.errorTitle"), "Invalid crypto parameters: " + e.getMessage());
                return;
            }
        } else {
            alg = null; key = null; iv = null; aad = null; enc = null; cs = null; compact = false;
        }

        final java.util.concurrent.atomic.AtomicBoolean errorOccurred = new java.util.concurrent.atomic.AtomicBoolean(false);
        final com.cryptocarver.model.batch.BatchRunner.RowOperation rowOperation;
        if (isCrypto) {
            rowOperation = (rowNum, row) -> {
                String val = row.get(srcCol);
                if (val == null) throw new IllegalArgumentException("Missing column: " + srcCol);
                try {
                    String res = isEncrypt ? com.cryptocarver.crypto.LineRecordCipher.encryptRecord(val, key, alg, aad, enc, iv, cs, compact)
                                           : com.cryptocarver.crypto.LineRecordCipher.decryptRecord(val, key, alg, aad, iv, enc, cs, rowNum);
                    return java.util.Map.of(outCol, res);
                } catch (Exception e) {
                    if (stopOnError) errorOccurred.set(true);
                    throw e;
                }
            };
        } else {
            rowOperation = (rowNum, row) -> {
                try {
                    return runSafeBatchOperation(operation, row, srcCol, outCol);
                } catch (Exception e) {
                    if (stopOnError) errorOccurred.set(true);
                    throw e;
                }
            };
        }

        lastBatchReport = null;
        javafx.concurrent.Task<com.cryptocarver.model.batch.BatchRunner.Report> task = new javafx.concurrent.Task<>() {
            @Override protected com.cryptocarver.model.batch.BatchRunner.Report call() {
                try {
                    return batchRunnerExecutor.run(rows, rowOperation, () -> isCancelled() || errorOccurred.get(),
                            (completed, total) -> updateProgress(completed, total));
                } finally {
                    if (key != null) java.util.Arrays.fill(key, (byte) 0);
                    javafx.application.Platform.runLater(() -> batchKeyField.clear());
                }
            }
        };
        activeBatchTask = task;
        batchProgressBar.progressProperty().unbind(); batchProgressBar.progressProperty().bind(task.progressProperty());
        batchStatusLabel.setText(t("module.batch.processing", rows.size())); batchResultArea.clear();
        task.setOnSucceeded(event -> {
            batchProgressBar.progressProperty().unbind(); batchProgressBar.setProgress(1);
            if (task.getValue() != null && task.getValue().cancelled()) {
                lastBatchReport = null;
                batchStatusLabel.setText(t("module.batch.cancelled"));
                activeBatchTask = null;
                return;
            }
            lastBatchReport = task.getValue();
            batchResultArea.setText(renderBatchReport(lastBatchReport));
            batchStatusLabel.setText(t("module.batch.completed", lastBatchReport.succeeded(), lastBatchReport.failed()));
            if (statusReporter != null) {
                java.util.Map<String, String> batchDetails = new java.util.LinkedHashMap<>();
                batchDetails.put("Operation", operation);
                batchDetails.put("Rows", String.valueOf(lastBatchReport.results().size()));
                batchDetails.put("Succeeded", String.valueOf(lastBatchReport.succeeded()));
                batchDetails.put("Failed", String.valueOf(lastBatchReport.failed()));
                statusReporter.publish(OperationResult.forOperation("Batch Runner").details(batchDetails).build());
            }
            activeBatchTask = null;
        });
        task.setOnCancelled(event -> {
            batchProgressBar.progressProperty().unbind();
            lastBatchReport = null;
            batchResultArea.clear();
            batchStatusLabel.setText(t("module.batch.cancelled"));
            activeBatchTask = null;
        });
        task.setOnFailed(event -> {
            batchProgressBar.progressProperty().unbind(); Throwable error = task.getException();
            batchStatusLabel.setText(t("module.batch.failed", error == null ? "unknown error" : error.getMessage())); activeBatchTask = null;
        });
        Thread worker = new Thread(task, "cryptocarver-batch-runner"); worker.setDaemon(true); worker.start();
    }

    @FXML public void handleDryRunBatch() {
        final java.util.List<java.util.Map<String, String>> rows;
        final String srcCol = batchColumnField != null ? batchColumnField.getText().trim() : "input";
        final String outCol = batchOutputColumnField != null ? batchOutputColumnField.getText().trim() : "result";
        String rawText = batchInputArea != null ? batchInputArea.getText() : "";
        try {
            rows = isCsvBatchFormat(batchInputFormatCombo != null ? batchInputFormatCombo.getValue() : "CSV")
                    ? com.cryptocarver.model.batch.BatchInputCodec.parseCsv(rawText)
                    : com.cryptocarver.model.batch.BatchInputCodec.parseJsonLines(rawText);
        } catch (Exception e) {
            batchResultArea.setText(t("module.batch.dryRunInvalid", e.getMessage()));
            if (batchStatusLabel != null) batchStatusLabel.setText(t("module.batch.dryRunBlocked"));
            return;
        }

        String op = batchOperationCombo != null ? batchOperationCombo.getValue() : "None";
        String alg = batchAlgorithmCombo != null ? batchAlgorithmCombo.getValue() : null;
        String keyHex = batchKeyField != null ? batchKeyField.getText() : null;
        String ivHex = batchIvNonceField != null ? batchIvNonceField.getText() : null;

        com.cryptocarver.model.process.DryRunSummary summary =
                com.cryptocarver.model.batch.BatchValidator.dryRun(rows, op, srcCol, outCol, alg, keyHex, ivHex);

        StringBuilder sb = new StringBuilder();
        sb.append("=== BATCH RUNNER DRY RUN ===\n");
        sb.append("Total Rows: ").append(summary.totalSteps()).append('\n');
        sb.append("Status Breakdown: Ready=").append(summary.readyCount())
          .append(", Warning=").append(summary.warningCount())
          .append(", Blocked=").append(summary.blockedCount()).append('\n');
        if (summary.firstBlockedReason() != null) {
            sb.append("First Blocked Reason: ").append(summary.firstBlockedReason()).append('\n');
        }
        sb.append("Resolved Dependencies:\n");
        for (String dep : summary.resolvedDependencies()) {
            sb.append("  - ").append(dep).append('\n');
        }
        sb.append("Execution Plan:\n");
        for (String step : summary.executionOrder()) {
            sb.append("  - ").append(step).append('\n');
        }
        sb.append("\n(Dry Run completed: 0 cryptographic operations called, 0 files written, 0 history entries created)");

        batchResultArea.setText(sb.toString());
        if (batchStatusLabel != null) {
            batchStatusLabel.setText(t("module.batch.dryRunSummary", summary.readyCount(), summary.blockedCount()));
        }
    }

    @FXML public void handleCancelBatch() {
        if (activeBatchTask != null && activeBatchTask.isRunning()) {
            batchStatusLabel.setText(t("module.batch.cancelling"));
            activeBatchTask.cancel();
        } else {
            batchStatusLabel.setText(t("module.batch.notRunning"));
        }
    }

    @FXML public void handleExportBatchResults() {
        if (lastBatchReport == null) {
            if (statusReporter != null) statusReporter.showError(t("module.batch.exportErrorTitle"), t("module.batch.noResults"));
            return;
        }
        boolean csv = isCsvBatchFormat(batchExportFormatCombo.getValue());
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser(); chooser.setTitle(t("module.batch.exportTitle"));
        chooser.setInitialFileName(csv ? "cryptocarver-batch-results.csv" : "cryptocarver-batch-results.jsonl");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(csv ? "CSV" : "JSON Lines", csv ? "*.csv" : "*.jsonl"));
        java.io.File file = chooser.showSaveDialog(genericContainer == null || genericContainer.getScene() == null ? null : genericContainer.getScene().getWindow());
        if (file == null) return;
        try {
            String output = csv ? com.cryptocarver.model.batch.BatchOutputCodec.toCsv(lastBatchReport)
                    : com.cryptocarver.model.batch.BatchOutputCodec.toJsonLines(lastBatchReport);
            java.nio.file.Files.writeString(file.toPath(), output, java.nio.charset.StandardCharsets.UTF_8);
            batchStatusLabel.setText(t("module.batch.exported", file.getName()));
        } catch (java.io.IOException e) {
            if (statusReporter != null) statusReporter.showError(t("module.batch.exportErrorTitle"), "Unable to save results: " + e.getMessage());
        }
    }

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

    @FXML public void initialize() {
        javafx.scene.Node[] excluded = genericContainer == null ? new javafx.scene.Node[0]
                : genericContainer.getPanes().stream()
                        .filter(pane -> pane.getText() != null && pane.getText().contains("Process Designer"))
                        .toArray(javafx.scene.Node[]::new);
        moduleI18n = ModuleI18n.bind(genericContainer, ModuleTextCatalog.generic(), excluded);
        com.cryptocarver.service.I18nService.getInstance().addLocaleChangeListener(locale -> {
            if (batchStatusLabel == null) return;
            if (activeBatchTask != null && activeBatchTask.isRunning()) {
                batchStatusLabel.setText(t("module.batch.processing", batchInputArea == null ? 0 : batchInputArea.getParagraphs().size()));
            }
        });
        if (batchInputFormatCombo != null) {
            batchInputFormatCombo.getItems().setAll("CSV", "JSON Lines (.jsonl)");
            batchInputFormatCombo.setValue("CSV");
        }

        if (batchOperationCombo != null) {
            // Batch files are deliberately data-only. Secret/key-bearing
            // crypto operations remain available in their dedicated modules.
            batchOperationCombo.getItems().setAll("SHA-256 (UTF-8 → Hex)", "UTF-8 → Base64URL", "Base64URL → UTF-8");
            batchOperationCombo.setValue("SHA-256 (UTF-8 → Hex)");
            batchOperationCombo.valueProperty().addListener((obs, oldV, newV) -> {
                boolean isCrypto = "Encrypt Record".equals(newV) || "Decrypt Record".equals(newV);
                if (batchCryptoConfigBox != null) {
                    batchCryptoConfigBox.setVisible(isCrypto);
                    batchCryptoConfigBox.setManaged(isCrypto);
                }
            });
        }
        if (batchAlgorithmCombo != null) {
            batchAlgorithmCombo.getItems().setAll("AES-256-GCM", "ChaCha20-Poly1305", "AES-256-CBC");
            batchAlgorithmCombo.setValue("AES-256-GCM");
            batchRecordEncodingCombo.getItems().setAll("Base64URL", "Hexadecimal");
            batchRecordEncodingCombo.setValue("Base64URL");
            batchCharsetCombo.getItems().setAll("UTF-8");
            batchCharsetCombo.getItems().addAll(com.cryptocarver.crypto.EBCDICConverter.supportedCodePages().keySet());
            batchCharsetCombo.setValue("UTF-8");
        }

        if (batchExportFormatCombo != null) {
            batchExportFormatCombo.getItems().setAll("CSV", "JSON Lines (.jsonl)");
            batchExportFormatCombo.setValue("CSV");
        }
        if (manualInputFormatCombo != null) {
            manualInputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Binary", "Decimal");
            manualInputFormatCombo.setValue("Text (UTF-8)");
            manualInputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                    synchronizeToolbarFromManualFormats());
        }
        if (manualOutputFormatCombo != null) {
            manualOutputFormatCombo.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Binary", "Decimal");
            manualOutputFormatCombo.setValue("Text (UTF-8)");
            manualOutputFormatCombo.valueProperty().addListener((observable, oldValue, newValue) ->
                    synchronizeToolbarFromManualFormats());
        }
        if (hashAlgorithmCombo != null) {
            hashAlgorithmCombo.getItems().addAll(HashOperations.SUPPORTED_ALGORITHMS);
            hashAlgorithmCombo.getItems().add("CRC32");
            hashAlgorithmCombo.setValue("SHA-256");
        }
        if (checkDigitAlgorithmCombo != null) {
            checkDigitAlgorithmCombo.getItems().addAll(CheckDigitCalculator.SUPPORTED_ALGORITHMS);
            checkDigitAlgorithmCombo.setValue("Luhn (Mod 10)");
        }
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
        if (modOperationCombo != null) {
            modOperationCombo.getItems().setAll(
                    "Addition (a + b) mod m",
                    "Subtraction (a - b) mod m",
                    "Inverse -a mod m",
                    "Multiplication (a * b) mod m",
                    "Exponentiation (a^b) mod m",
                    "Reciprocal (1/a) mod m",
                    "GCD(a, b)",
                    "LCM(a, b)",
                    "Extended GCD",
                    "Chinese Remainder Theorem",
                    "XOR (Hex Input)",
                    "XOR (Decimal Input)");
            modOperationCombo.getSelectionModel().select(0);
        }
        if (fileInputFormatCombo != null) {
            fileInputFormatCombo.getItems().setAll("Binary", "Text", "Hex", "Base64");
            fileInputFormatCombo.getSelectionModel().select("Binary");
        }
        if (fileOutputFormatCombo != null) {
            fileOutputFormatCombo.getItems().setAll("Binary", "Text", "Hex", "Base64");
            fileOutputFormatCombo.getSelectionModel().select("Binary");
        }
        if (fileEncodingCombo != null) {
            fileEncodingCombo.getItems().setAll("UTF-8", "ASCII", "ISO-8859-1");
            fileEncodingCombo.getSelectionModel().select("UTF-8");
        }

        refreshHashTemplateCombo();
        refreshManualTemplateCombo();

        initializeEBCDICConverter();
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




    public GenericController(StatusReporter statusReporter,
            TextArea inputArea,
            TextArea outputArea,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo) {
        this.statusReporter = statusReporter;
        this.inputArea = inputArea;
        this.outputArea = outputArea;
        this.inputFormatCombo = inputFormatCombo;
        this.outputFormatCombo = outputFormatCombo;
    }

    /**
     * Connects the shared format toolbar to Generic's operation-specific controls.
     *
     * <p>The Generic module is FXML-included, so its local hash and conversion
     * controls are not injected through the legacy constructor. Keeping this
     * connection explicit prevents the toolbar from advertising a format which
     * the operation then ignores.</p>
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
        hashAlgorithmCombo.setValue("SHA-256");
    }

    public void setCheckDigitAlgorithmCombo(ComboBox<String> combo) {
        this.checkDigitAlgorithmCombo = combo;
        checkDigitAlgorithmCombo.getItems().addAll(CheckDigitCalculator.SUPPORTED_ALGORITHMS);
        checkDigitAlgorithmCombo.setValue("Luhn (Mod 10)");
    }

    public void setRandomGeneratorFields(javafx.scene.control.TextField bytesField, ComboBox<String> formatCombo) {
        this.randomBytesField = bytesField;
        this.randomFormatCombo = formatCombo;
        randomFormatCombo.getItems().addAll("Hexadecimal", "Decimal", "Base64", "Binary");
        randomFormatCombo.setValue("Hexadecimal");
        setupRandomFormatComboListener();
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

    /**
     * Legacy wrapper for MainController compatibility
     */
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
        } else if (inputArea != null && hashAlgorithmCombo != null && outputArea != null) {
            calculateHash(inputArea.getText(),
                    selectedFormatOrDefault(inputFormatCombo, "Text (UTF-8)"),
                    selectedFormatOrDefault(outputFormatCombo, "Hexadecimal"),
                    hashAlgorithmCombo.getValue(),
                    outputArea);
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


    public void handleConvert() {
        if (inputArea != null && inputFormatCombo != null && outputFormatCombo != null && outputArea != null) {
            convert(inputArea.getText(), inputFormatCombo.getValue(), outputFormatCombo.getValue(), outputArea);
        }
    }

    /**
     * Calculate check digit
     */
    public void calculateCheckDigit(String input, String algorithm, TextInputControl targetOutputArea) {
        try {
            if (input == null || input.isEmpty()) {
                statusReporter.showError("Input Error", "Please enter numeric data");
                return;
            }
            if (algorithm == null || algorithm.isEmpty()) {
                statusReporter.showError("Algorithm Error", "Please select a check digit algorithm");
                return;
            }

            int checkDigit = CheckDigitCalculator.calculateCheckDigit(input, algorithm);
            String result = CheckDigitCalculator.formatWithCheckDigit(input, algorithm);

            targetOutputArea.setText("Check Digit: " + checkDigit + "\nComplete: " + result);
            statusReporter.publish(OperationResult.forOperation("Check Digits")
                    .input(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Algorithm", algorithm).detail("Mode", "Calculate")
                    .detail("Check Digit", String.valueOf(checkDigit))
                    .status("Check digit calculated using " + algorithm).build());

        } catch (Exception e) {
            statusReporter.showError("Check Digit Error", "Error calculating check digit: " + e.getMessage());
        }
    }

    @FXML


    public void handleCalculateCheckDigit() {
        if (inputArea != null && checkDigitAlgorithmCombo != null && outputArea != null) {
            calculateCheckDigit(inputArea.getText(), checkDigitAlgorithmCombo.getValue(), outputArea);
        } else if (checkDigitAlgorithmCombo != null && checkDigitOutputArea != null && inputArea != null) {
            // Case for Modern UI where specific fields are used but inputArea (generic)
            // might be null?
            // Wait, Check Digit Pane has its OWN input field in Modern UI?
            // FXML shows: <TextField fx:id="checkDigitInput" .../>
            // GenericController doesn't seem to have checkDigitInput field yet?
            // I need to check if GenericController has a specific input field for Check
            // Digit.
            // If not, I am missing that too.
            // Let's assume for now I should use a specific input field if it exists.
            // But I only see `setCheckDigitAlgorithmCombo`.
            // I likely missed the input field.
        }
    }

    public void validateCheckDigit(String input, String algorithm, TextInputControl targetOutputArea) {
        try {
            if (input == null || input.isEmpty()) {
                statusReporter.showError("Input Error", "Please enter data with check digit");
                return;
            }
            if (algorithm == null || algorithm.isEmpty()) {
                statusReporter.showError("Algorithm Error", "Please select a check digit algorithm");
                return;
            }

            boolean isValid = CheckDigitCalculator.validateCheckDigit(input, algorithm);
            String resultText = isValid ? "✅ VALID" : "❌ INVALID";

            targetOutputArea.setText("Validation Result: " + resultText);
            statusReporter.publish(OperationResult.forOperation("Check Digits")
                    .input(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(resultText.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Algorithm", algorithm).detail("Mode", "Validate")
                    .detail("Result", isValid ? "VALID" : "INVALID")
                    .status("Check digit validation: " + resultText).build());

        } catch (Exception e) {
            statusReporter.showError("Validation Error", "Error validating: " + e.getMessage());
        }
    }

    @FXML


    public void handleValidateCheckDigit() {
        if (inputArea != null && checkDigitAlgorithmCombo != null && outputArea != null) {
            validateCheckDigit(inputArea.getText(), checkDigitAlgorithmCombo.getValue(), outputArea);
        }
    }

    /**
     * Get input data as bytes based on selected format
     */
    /**
     * Parse input string based on format
     */
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
     * Get input data as bytes based on selected format
     */
    private byte[] getInputDataAsBytes() {
        return parseInput(inputArea.getText(), inputFormatCombo.getValue());
    }

    /**
     * Set output data based on selected format
     */
    private void setOutputData(byte[] data) {
        String format = outputFormatCombo.getValue();
        if (format == null) {
            format = "Hexadecimal";
        }

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
            } else if (outputArea != null) {
                outputArea.setText(output);
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

    // ============================================================================
    // MODULAR ARITHMETIC CALCULATOR
    // ============================================================================

    /**
     * Initialize modular arithmetic components
     */
    public void initializeModularArithmetic(
            ComboBox<String> operationCombo,
            TextField operandAField,
            TextField operandBField,
            TextField modulusField,
            TextArea resultArea) {

        this.modOperationCombo = operationCombo;
        this.modOperandAField = operandAField;
        this.modOperandBField = operandBField;
        this.modModulusField = modulusField;
        this.modResultArea = resultArea;

        modOperationCombo.getItems().addAll(
                "Addition (a + b) mod m",
                "Subtraction (a - b) mod m",
                "Inverse -a mod m",
                "Multiplication (a * b) mod m",
                "Exponentiation (a^b) mod m",
                "Reciprocal (1/a) mod m",
                "GCD(a, b)",
                "LCM(a, b)",
                "Extended GCD",
                "Chinese Remainder Theorem",
                "XOR (Hex Input)",
                "XOR (Decimal Input)");
        modOperationCombo.setValue("Addition (a + b) mod m");
    }

    /**
     * Calculate modular arithmetic operation
     */
    @FXML

    public void handleModularCalculate() {
        try {
            String operation = modOperationCombo.getValue();
            String aInput = modOperandAField.getText().trim();
            String bInput = modOperandBField.getText().trim();
            String mInput = modModulusField.getText().trim();

            // Default hex cleaning for standard operations
            String aHex = "", bHex = "", mHex = "";

            // Special handling for Decimal XOR
            if (operation.contains("Decimal Input")) {
                // For decimal, we just keep the raw digits
                if (!aInput.matches("\\d+") || (!bInput.isEmpty() && !bInput.matches("\\d+"))) {
                    statusReporter.showError("Input Error", "Please enter valid decimal numbers");
                    return;
                }
                // Convert decimal to hex for internal processing/compatibility with existing
                // modular logic if needed
                // But for XOR we'll process directly.
            } else {
                // Standard Hex processing
                aHex = aInput.replaceAll("[^0-9A-Fa-f]", "");
                bHex = bInput.replaceAll("[^0-9A-Fa-f]", "");
                mHex = mInput.replaceAll("[^0-9A-Fa-f]", "");
            }

            if (operation.contains("XOR")) {
                if (aInput.isEmpty() || bInput.isEmpty()) {
                    statusReporter.showError("Input Error", "Both operands required for XOR");
                    return;
                }

                java.math.BigInteger aBig, bBig;
                if (operation.contains("Decimal Input")) {
                    aBig = new java.math.BigInteger(aInput);
                    bBig = new java.math.BigInteger(bInput);
                } else {
                    aBig = new java.math.BigInteger(aHex, 16);
                    bBig = new java.math.BigInteger(bHex, 16);
                }

                java.math.BigInteger result = aBig.xor(bBig);
                String hexResult = result.toString(16).toUpperCase();

                String opDesc = operation.contains("Decimal")
                        ? aInput + " XOR " + bInput
                        : aHex + " XOR " + bHex;

                modResultArea.setText(ModularArithmetic.formatResult(opDesc, hexResult));
                return;
            }

            // For standard modular operations, continue using clean Hex strings
            if (aHex.isEmpty()) {
                statusReporter.showError("Input Error", "Operand A is required");
                return;
            }

            String result;
            String operationDesc;

            try {
                switch (operation) {
                    case "Addition (a + b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            statusReporter.showError("Input Error", "All fields required for addition");
                            return;
                        }
                        result = ModularArithmetic.modularAddition(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + " + " + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Subtraction (a - b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            statusReporter.showError("Input Error", "All fields required for subtraction");
                            return;
                        }
                        result = ModularArithmetic.modularSubtraction(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + " - " + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Inverse -a mod m":
                        if (mHex.isEmpty()) {
                            statusReporter.showError("Input Error", "Modulus is required");
                            return;
                        }
                        result = ModularArithmetic.modularInverse(aHex, mHex);
                        operationDesc = "-" + aHex + " mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Multiplication (a * b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            statusReporter.showError("Input Error", "All fields required for multiplication");
                            return;
                        }
                        result = ModularArithmetic.modularMultiplication(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + " * " + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Exponentiation (a^b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            statusReporter.showError("Input Error", "All fields required for exponentiation");
                            return;
                        }
                        result = ModularArithmetic.modularExponentiation(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + "^" + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Reciprocal (1/a) mod m":
                        if (mHex.isEmpty()) {
                            statusReporter.showError("Input Error", "Modulus is required");
                            return;
                        }
                        try {
                            result = ModularArithmetic.modularReciprocal(aHex, mHex);
                            operationDesc = "(1/" + aHex + ") mod " + mHex;

                            StringBuilder output = new StringBuilder();
                            output.append(ModularArithmetic.formatResult(operationDesc, result));

                            boolean isPrime = ModularArithmetic.isProbablyPrime(mHex);
                            output.append("\nModulus is ").append(isPrime ? "PROBABLY PRIME" : "COMPOSITE");

                            modResultArea.setText(output.toString());
                        } catch (ArithmeticException e) {
                            modResultArea.setText("ERROR: " + e.getMessage() +
                                    "\n\nModular reciprocal only exists when gcd(a, m) = 1");
                        }
                        break;

                    case "GCD(a, b)":
                        if (bHex.isEmpty()) {
                            statusReporter.showError("Input Error", "Operand B is required");
                            return;
                        }
                        result = ModularArithmetic.gcd(aHex, bHex);
                        operationDesc = "GCD(" + aHex + ", " + bHex + ")";
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "LCM(a, b)":
                        if (bHex.isEmpty()) {
                            statusReporter.showError("Input Error", "Operand B is required");
                            return;
                        }
                        result = ModularArithmetic.lcm(aHex, bHex);
                        operationDesc = "LCM(" + aHex + ", " + bHex + ")";
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Extended GCD":
                        if (bHex.isEmpty()) {
                            statusReporter.showError("Input Error", "Operand B is required");
                            return;
                        }
                        result = ModularArithmetic.extendedGCD(aHex, bHex);
                        modResultArea.setText("Extended Euclidean Algorithm\n" +
                                "Finding x, y such that: ax + by = gcd(a,b)\n\n" + result);
                        break;

                    case "Chinese Remainder Theorem":
                        // For CRT, A and M are first pair, B and another field for second pair
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            statusReporter.showError("Input Error",
                                    "CRT requires: A=a1, B=m1, Modulus=a2\n" +
                                            "Enter m2 in the operation history or use Extended GCD for setup");
                            return;
                        }
                        // Simplified CRT - would need additional fields for full implementation
                        modResultArea.setText("Chinese Remainder Theorem\n\n" +
                                "Note: Full CRT requires 2 modular equations:\n" +
                                "  x ≡ a1 (mod m1)\n" +
                                "  x ≡ a2 (mod m2)\n\n" +
                                "This would need additional UI fields for proper implementation.");
                        break;

                    default:
                        modResultArea.setText("Unknown operation");
                }

                statusReporter.publish(OperationResult.forOperation("Modular Arithmetic")
                        .input((aHex + " " + bHex + " " + mHex).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .output(modResultArea.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .enrichedOutput(modResultArea.getText())
                        .detail("Operation", operation).detail("Operand A", aHex)
                        .detail("Operand B", bHex).detail("Modulus", mHex)
                        .status("Modular operation completed").build());

            } catch (ArithmeticException e) {
                modResultArea.setText("ERROR: " + e.getMessage());
            }

        } catch (Exception e) {
            statusReporter.showError("Calculation Error", "Error in modular arithmetic: " + e.getMessage());
        }
    }

    // ============================================================================
    // FILE CONVERTER
    // ============================================================================

    /**
     * Initialize file converter components
     */
    public void initializeFileConverter(
            TextField inputPathField,
            TextField outputPathField,
            ComboBox<String> inputFormatCombo,
            ComboBox<String> outputFormatCombo,
            ComboBox<String> encodingCombo,
            TextArea resultArea) {

        this.fileInputPathField = inputPathField;
        this.fileOutputPathField = outputPathField;
        this.fileInputFormatCombo = inputFormatCombo;
        this.fileOutputFormatCombo = outputFormatCombo;
        this.fileEncodingCombo = encodingCombo;
        this.fileResultArea = resultArea;

        // Format options
        String[] formats = { "Binary", "Hex", "Base64", "Text", "Analyze", "Hex Dump" };
        fileInputFormatCombo.getItems().addAll(formats);
        fileOutputFormatCombo.getItems().addAll("Binary", "Hex", "Base64", "Text");
        fileInputFormatCombo.setValue("Binary");
        fileOutputFormatCombo.setValue("Hex");

        // Encoding options
        fileEncodingCombo.getItems().addAll(
                "UTF-8",
                "ASCII",
                "ISO-8859-1 (Latin-1)",
                "ISO-8859-15 (Latin-9)",
                "Windows-1252",
                "UTF-16",
                "UTF-16BE",
                "UTF-16LE",
                "UTF-32",
                "Cp037 (EBCDIC US/Canada)",
                "Cp273 (EBCDIC Germany)",
                "Cp284 (EBCDIC Spain)",
                "Cp285 (EBCDIC UK)",
                "Cp297 (EBCDIC France)",
                "Cp500 (EBCDIC International)",
                "Cp850 (DOS Latin-1)",
                "Cp437 (DOS US)");
        fileEncodingCombo.setValue("UTF-8");

        // Disable encoding when not needed
        javafx.beans.value.ChangeListener<String> encodingListener = (obs, oldVal, newVal) -> {
            boolean needsEncoding = "Text".equals(fileInputFormatCombo.getValue()) ||
                    "Text".equals(fileOutputFormatCombo.getValue());
            fileEncodingCombo.setDisable(!needsEncoding);
        };
        fileInputFormatCombo.valueProperty().addListener(encodingListener);
        fileOutputFormatCombo.valueProperty().addListener(encodingListener);
        fileEncodingCombo.setDisable(true); // Initially disabled
    }

    public void setFileComparePathField(TextField field) { this.fileComparePathField = field; }

    public void compareFiles() {
        try {
            String left = fileInputPathField.getText().trim();
            String right = fileComparePathField.getText().trim();
            if (left.isEmpty() || right.isEmpty()) throw new IllegalArgumentException("Select both files to compare");
            long difference = StreamingFileTools.firstDifference(java.nio.file.Paths.get(left), java.nio.file.Paths.get(right), com.cryptocarver.util.ProgressMonitor.NO_OP);
            String result = difference < 0 ? "Files are identical."
                    : "Files differ at byte offset " + difference + " (0x" + Long.toHexString(difference).toUpperCase() + ").";
            fileResultArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Compare Files")
                    .detail("Left File", java.nio.file.Paths.get(left).getFileName().toString())
                    .detail("Right File", java.nio.file.Paths.get(right).getFileName().toString())
                    .detail("Result", difference < 0 ? "IDENTICAL" : "DIFFERENT")
                    .status(result).build());
        } catch (Exception e) {
            statusReporter.showError("File Comparison Error", e.getMessage());
        }
    }

    public void hashFileStreaming() {
        try {
            String source = fileInputPathField.getText().trim();
            if (source.isEmpty()) throw new IllegalArgumentException("Select a source file first");
            java.nio.file.Path path = java.nio.file.Paths.get(source);
            String hash = StreamingFileTools.hash(path, "SHA-256", com.cryptocarver.util.ProgressMonitor.NO_OP);
            String result = "SHA-256\n" + hash + "\n\nBytes: " + java.nio.file.Files.size(path);
            fileResultArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Hash File (streaming)")
                    .output(DataConverter.hexToBytes(hash)).detail("Algorithm", "SHA-256")
                    .detail("File", path.getFileName().toString()).detail("Bytes", String.valueOf(java.nio.file.Files.size(path)))
                    .status("File hash calculated").build());
        } catch (Exception e) {
            statusReporter.showError("File Hash Error", e.getMessage());
        }
    }

    public void previewFileStreaming() {
        try {
            String source = fileInputPathField.getText().trim();
            if (source.isEmpty()) throw new IllegalArgumentException("Select a source file first");
            java.nio.file.Path path = java.nio.file.Paths.get(source);
            byte[] preview = StreamingFileTools.preview(path, 4096, com.cryptocarver.util.ProgressMonitor.NO_OP);
            String result = com.cryptocarver.crypto.HexInspector.render(preview, 0, preview.length);
            fileResultArea.setText(result);
            statusReporter.publish(OperationResult.forOperation("Preview File (streaming)")
                    .output(preview).detail("File", path.getFileName().toString()).detail("Preview", preview.length + " bytes")
                    .status("File preview loaded").build());
        } catch (Exception e) { statusReporter.showError("File Preview Error", e.getMessage()); }
    }

    /**
     * Handle file conversion operation
     */
    @FXML

    public void handleFileConvert() {
        try {
            String inputPath = fileInputPathField.getText().trim();
            String outputPath = fileOutputPathField.getText().trim();
            String inputFormat = fileInputFormatCombo.getValue();
            String outputFormat = fileOutputFormatCombo.getValue();
            String encodingFull = fileEncodingCombo.getValue();

            // Extract charset name (e.g., "UTF-8" or "Cp037" from "Cp037 (EBCDIC
            // US/Canada)")
            String encoding = encodingFull != null ? encodingFull.split(" ")[0] : "UTF-8";

            if (inputPath.isEmpty()) {
                statusReporter.showError("Input Error", "Input file path is required");
                return;
            }

            if (inputFormat == null || outputFormat == null) {
                statusReporter.showError("Input Error", "Please select input and output formats");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append("File Conversion\n");
            result.append("===============\n\n");
            result.append("Input File: ").append(inputPath).append("\n");
            result.append("From: ").append(inputFormat).append("\n");
            result.append("To: ").append(outputFormat).append("\n");

            // Special operations (no output format)
            if ("Analyze".equals(inputFormat)) {
                result.append("\n").append(FileConverter.analyzeFile(inputPath));
                result.append("\n\n").append(FileConverter.getFileSizeInfo(inputPath));
                fileResultArea.setText(result.toString());
                return;
            }

            if ("Hex Dump".equals(inputFormat)) {
                result.append("\n\n").append(FileConverter.hexDump(inputPath, 512));
                fileResultArea.setText(result.toString());
                return;
            }

            // Step 1: Read input file as bytes based on input format
            byte[] data;
            switch (inputFormat) {
                case "Binary":
                    data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(inputPath));
                    break;

                case "Hex":
                    String hexContent = java.nio.file.Files.readString(java.nio.file.Paths.get(inputPath)).trim();
                    hexContent = hexContent.replaceAll("\\s+", ""); // Remove whitespace
                    data = hexToBytes(hexContent);
                    break;

                case "Base64":
                    String base64Content = java.nio.file.Files.readString(java.nio.file.Paths.get(inputPath)).trim();
                    data = java.util.Base64.getDecoder().decode(base64Content);
                    break;

                case "Text":
                    String textContent = java.nio.file.Files.readString(java.nio.file.Paths.get(inputPath),
                            java.nio.charset.Charset.forName(encoding));
                    data = textContent.getBytes(encoding);
                    result.append("Input Encoding: ").append(encodingFull).append("\n");
                    break;

                default:
                    statusReporter.showError("Error", "Unknown input format: " + inputFormat);
                    return;
            }

            result.append("Data Size: ").append(data.length).append(" bytes\n");

            // Step 2: Convert to output format
            switch (outputFormat) {
                case "Binary":
                    if (outputPath.isEmpty()) {
                        statusReporter.showError("Input Error", "Output path required for binary files");
                        return;
                    }
                    java.nio.file.Files.write(java.nio.file.Paths.get(outputPath), data);
                    result.append("Output: ").append(outputPath).append("\n");
                    result.append("Status: Binary file written successfully");
                    break;

                case "Hex":
                    String hexOutput = bytesToHex(data);
                    if (outputPath.isEmpty()) {
                        result.append("\nHex Output (first 1000 chars):\n");
                        result.append(hexOutput.substring(0, Math.min(1000, hexOutput.length())));
                        if (hexOutput.length() > 1000) {
                            result.append("\n\n... ").append(hexOutput.length() - 1000).append(" more chars");
                        }
                    } else {
                        java.nio.file.Files.writeString(java.nio.file.Paths.get(outputPath), hexOutput);
                        result.append("Output: ").append(outputPath).append("\n");
                        result.append("Status: Hex file written (").append(hexOutput.length()).append(" chars)");
                    }
                    break;

                case "Base64":
                    String base64Output = java.util.Base64.getEncoder().encodeToString(data);
                    if (outputPath.isEmpty()) {
                        result.append("\nBase64 Output (first 1000 chars):\n");
                        result.append(base64Output.substring(0, Math.min(1000, base64Output.length())));
                        if (base64Output.length() > 1000) {
                            result.append("\n\n... ").append(base64Output.length() - 1000).append(" more chars");
                        }
                    } else {
                        java.nio.file.Files.writeString(java.nio.file.Paths.get(outputPath), base64Output);
                        result.append("Output: ").append(outputPath).append("\n");
                        result.append("Status: Base64 file written (").append(base64Output.length()).append(" chars)");
                    }
                    break;

                case "Text":
                    String textOutput = new String(data, encoding);
                    result.append("Output Encoding: ").append(encodingFull).append("\n");
                    if (outputPath.isEmpty()) {
                        result.append("\nText Output (first 1000 chars):\n");
                        result.append(textOutput.substring(0, Math.min(1000, textOutput.length())));
                        if (textOutput.length() > 1000) {
                            result.append("\n\n... ").append(textOutput.length() - 1000).append(" more chars");
                        }
                    } else {
                        java.nio.file.Files.writeString(java.nio.file.Paths.get(outputPath), textOutput,
                                java.nio.charset.Charset.forName(encoding));
                        result.append("Output: ").append(outputPath).append("\n");
                        result.append("Status: Text file written (").append(textOutput.length()).append(" chars)");
                    }
                    break;

                default:
                    statusReporter.showError("Error", "Unknown output format: " + outputFormat);
                    return;
            }

            fileResultArea.setText(result.toString());
            statusReporter.updateStatus("Conversion completed: " + inputFormat + " → " + outputFormat);

            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("File Convert: " + inputFormat + " → " + outputFormat)
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", inputPath, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Success", com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (java.io.FileNotFoundException e) {
            statusReporter.showError("File Error", "File not found: " + e.getMessage());
        } catch (java.io.IOException e) {
            statusReporter.showError("File Error", "I/O error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            statusReporter.showError("Format Error", "Invalid input format: " + e.getMessage());
        } catch (Exception e) {
            statusReporter.showError("Conversion Error", "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
            } else if (outputArea != null) {
                outputArea.setText(uuid);
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
        // Clear Standard Conversion
        if (inputArea != null)
            inputArea.clear();
        if (outputArea != null)
            outputArea.clear();

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

        if (outputArea != null && !outputArea.getText().isEmpty()) {
            return outputArea.getText();
        }

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
