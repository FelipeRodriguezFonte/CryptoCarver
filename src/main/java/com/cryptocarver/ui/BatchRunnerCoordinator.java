package com.cryptocarver.ui;

import com.cryptocarver.model.OperationResult;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Supplier;

/**
 * The Batch Runner pane: parses CSV or JSON Lines rows, runs one catalogue operation (or the
 * record cipher) over a column on a background task with progress and cancellation, previews
 * the plan in a dry run and exports the per-row report. The key field is wiped after each run.
 */
final class BatchRunnerCoordinator {

    /** The pane's controls, injected into GenericController from generic.fxml. */
    record View(ComboBox<String> inputFormat,
            ComboBox<String> operation,
            TextField sourceColumn,
            ComboBox<String> algorithm,
            ComboBox<String> recordEncoding,
            PasswordField key,
            TextField ivNonce,
            TextField aad,
            ComboBox<String> charset,
            CheckBox stopOnError,
            CheckBox compactMode,
            TextField outputColumn,
            VBox cryptoConfig,
            TextArea input,
            ComboBox<String> exportFormat,
            ProgressBar progress,
            Label status,
            TextArea result) {
    }

    private final ComboBox<String> batchInputFormatCombo;
    private final ComboBox<String> batchOperationCombo;
    private final TextField batchColumnField;
    private final ComboBox<String> batchAlgorithmCombo;
    private final ComboBox<String> batchRecordEncodingCombo;
    private final PasswordField batchKeyField;
    private final TextField batchIvNonceField;
    private final TextField batchAadField;
    private final ComboBox<String> batchCharsetCombo;
    private final CheckBox batchStopOnErrorCheck;
    private final CheckBox batchCompactModeCheck;
    private final TextField batchOutputColumnField;
    private final VBox batchCryptoConfigBox;
    private final TextArea batchInputArea;
    private final ComboBox<String> batchExportFormatCombo;
    private final ProgressBar batchProgressBar;
    private final Label batchStatusLabel;
    private final TextArea batchResultArea;
    private final Supplier<StatusReporter> reporter;
    private final Supplier<javafx.stage.Window> owner;

    BatchRunnerCoordinator(View view, Supplier<StatusReporter> reporter, Supplier<javafx.stage.Window> owner) {
        this.batchInputFormatCombo = view.inputFormat();
        this.batchOperationCombo = view.operation();
        this.batchColumnField = view.sourceColumn();
        this.batchAlgorithmCombo = view.algorithm();
        this.batchRecordEncodingCombo = view.recordEncoding();
        this.batchKeyField = view.key();
        this.batchIvNonceField = view.ivNonce();
        this.batchAadField = view.aad();
        this.batchCharsetCombo = view.charset();
        this.batchStopOnErrorCheck = view.stopOnError();
        this.batchCompactModeCheck = view.compactMode();
        this.batchOutputColumnField = view.outputColumn();
        this.batchCryptoConfigBox = view.cryptoConfig();
        this.batchInputArea = view.input();
        this.batchExportFormatCombo = view.exportFormat();
        this.batchProgressBar = view.progress();
        this.batchStatusLabel = view.status();
        this.batchResultArea = view.result();
        this.reporter = reporter;
        this.owner = owner;
    }

    /** Fills the pane's choices. */
    void configure() {
        localeChangeListener = locale -> {
            if (batchStatusLabel == null) return;
            if (activeBatchTask != null && activeBatchTask.isRunning()) {
                batchStatusLabel.setText(t("module.batch.processing", batchInputArea == null ? 0 : batchInputArea.getParagraphs().size()));
            }
        };
        com.cryptocarver.service.I18nService.getInstance().addLocaleChangeListener(localeChangeListener);
        if (batchInputFormatCombo != null) {
            batchInputFormatCombo.getItems().setAll("CSV", "JSON Lines (.jsonl)");
            batchInputFormatCombo.setValue("CSV");
        }

        if (batchOperationCombo != null) {
            // Batch files are deliberately data-only. Secret/key-bearing
            // crypto operations remain available in their dedicated modules.
            batchOperationCombo.getItems().setAll(com.cryptocarver.model.batch.BatchOperationCatalog.getAvailableOperations());
            if (!batchOperationCombo.getItems().isEmpty()) {
                batchOperationCombo.setValue(batchOperationCombo.getItems().get(0));
            }
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
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private static String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    void setExecutor(GenericController.BatchRunnerExecutor executor) {
        this.batchRunnerExecutor = java.util.Objects.requireNonNull(executor, "Batch runner executor is required");
    }

    javafx.concurrent.Task<com.cryptocarver.model.batch.BatchRunner.Report> activeTask() {
        return activeBatchTask;
    }

    com.cryptocarver.model.batch.BatchRunner.Report lastReport() {
        return lastBatchReport;
    }

    /** Held so the locale listener stays registered: I18nService keeps only a weak reference. */
    /** Held so the locale listener stays registered: I18nService keeps only a weak reference. */
    private java.util.function.Consumer<java.util.Locale> localeChangeListener;

    // Batch State
    private javafx.concurrent.Task<com.cryptocarver.model.batch.BatchRunner.Report> activeBatchTask;
    private com.cryptocarver.model.batch.BatchRunner.Report lastBatchReport;
    private GenericController.BatchRunnerExecutor batchRunnerExecutor = (rows, operation, cancellationRequested, progressListener) ->
            com.cryptocarver.model.batch.BatchRunner.run(rows, operation, cancellationRequested, progressListener);

    private boolean isCsvBatchFormat(String format) { return "CSV".equals(format); }

    void handleResetBatch() {
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

    void handleBrowseBatchInput() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Load Batch Input");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Batch data", "*.csv", "*.jsonl", "*.ndjson", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showOpenDialog(owner.get());
        if (file == null) return;
        try {
            batchInputArea.setText(java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            String lower = file.getName().toLowerCase(java.util.Locale.ROOT);
            batchInputFormatCombo.setValue(lower.endsWith(".csv") ? "CSV" : "JSON Lines (.jsonl)");
            batchStatusLabel.setText(t("module.batch.loaded", file.getName()));
        } catch (java.io.IOException e) {
            if (reporter() != null) reporter().showError(t("module.batch.inputErrorTitle"), "Unable to read file: " + e.getMessage());
        }
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

    void handleRunBatch() {
        if (activeBatchTask != null && activeBatchTask.isRunning()) {
            if (reporter() != null) reporter().showError(t("module.batch.errorTitle"), t("module.batch.alreadyRunning"));
            return;
        }
        final java.util.List<java.util.Map<String, String>> rows;
        final String srcCol = batchColumnField.getText().trim();
        final String outCol = batchOutputColumnField.getText().trim();
        if (srcCol.isEmpty() || outCol.isEmpty()) {
            if (reporter() != null) reporter().showError(t("module.batch.errorTitle"), t("module.batch.columnsRequired"));
            return;
        }
        try {
            rows = isCsvBatchFormat(batchInputFormatCombo.getValue())
                    ? com.cryptocarver.model.batch.BatchInputCodec.parseCsv(batchInputArea.getText())
                    : com.cryptocarver.model.batch.BatchInputCodec.parseJsonLines(batchInputArea.getText());
            if (rows.isEmpty()) throw new IllegalArgumentException("No batch rows found");
            if (rows.stream().anyMatch(row -> !row.containsKey(srcCol))) throw new IllegalArgumentException("Every row must contain the field: " + srcCol);
        } catch (Exception e) {
            if (reporter() != null) reporter().showError(t("module.batch.inputErrorTitle"), e.getMessage());
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

                if (isEncrypt && "AES-256-CBC".equals(alg) && iv != null
                        && LabPrompt.CBC_IV_REUSE.shouldShow()) {
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
                if (reporter() != null) reporter().showError(t("module.batch.errorTitle"), "Invalid crypto parameters: " + e.getMessage());
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
                    return com.cryptocarver.model.batch.BatchOperationCatalog.execute(operation, row, srcCol, outCol);
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
            if (reporter() != null) {
                java.util.Map<String, String> batchDetails = new java.util.LinkedHashMap<>();
                batchDetails.put("Operation", operation);
                batchDetails.put("Rows", String.valueOf(lastBatchReport.results().size()));
                batchDetails.put("Succeeded", String.valueOf(lastBatchReport.succeeded()));
                batchDetails.put("Failed", String.valueOf(lastBatchReport.failed()));
                reporter().publish(OperationResult.forOperation("Batch Runner").details(batchDetails).build());
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

    void handleDryRunBatch() {
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

    void handleCancelBatch() {
        if (activeBatchTask != null && activeBatchTask.isRunning()) {
            batchStatusLabel.setText(t("module.batch.cancelling"));
            activeBatchTask.cancel();
        } else {
            batchStatusLabel.setText(t("module.batch.notRunning"));
        }
    }

    void handleExportBatchResults() {
        if (lastBatchReport == null) {
            if (reporter() != null) reporter().showError(t("module.batch.exportErrorTitle"), t("module.batch.noResults"));
            return;
        }
        boolean csv = isCsvBatchFormat(batchExportFormatCombo.getValue());
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser(); chooser.setTitle(t("module.batch.exportTitle"));
        chooser.setInitialFileName(csv ? "cryptocarver-batch-results.csv" : "cryptocarver-batch-results.jsonl");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(csv ? "CSV" : "JSON Lines", csv ? "*.csv" : "*.jsonl"));
        java.io.File file = chooser.showSaveDialog(owner.get());
        if (file == null) return;
        try {
            String output = csv ? com.cryptocarver.model.batch.BatchOutputCodec.toCsv(lastBatchReport)
                    : com.cryptocarver.model.batch.BatchOutputCodec.toJsonLines(lastBatchReport);
            java.nio.file.Files.writeString(file.toPath(), output, java.nio.charset.StandardCharsets.UTF_8);
            batchStatusLabel.setText(t("module.batch.exported", file.getName()));
        } catch (java.io.IOException e) {
            if (reporter() != null) reporter().showError(t("module.batch.exportErrorTitle"), "Unable to save results: " + e.getMessage());
        }
    }
}
