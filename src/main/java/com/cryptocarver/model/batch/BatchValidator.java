package com.cryptocarver.model.batch;

import com.cryptocarver.model.process.DryRunSummary;
import com.cryptocarver.model.process.StepValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validates batch input data, column requirements, and cryptographic parameters for Batch Runner. */
public final class BatchValidator {
    private BatchValidator() { }

    public static DryRunSummary dryRun(
            List<Map<String, String>> rows,
            String operation,
            String inputColumn,
            String outputColumn,
            String algorithm,
            String keyHex,
            String ivHex
    ) {
        List<StepValidationResult> validations = new ArrayList<>();

        if (rows == null || rows.isEmpty()) {
            validations.add(StepValidationResult.blocked("batch_input", "batchInputArea", "Batch input dataset is empty or unparseable"));
            return new DryRunSummary(0, 0, 0, 0, 1, "Batch input dataset is empty", List.of(), List.of(), validations);
        }

        if (inputColumn == null || inputColumn.isBlank()) {
            validations.add(StepValidationResult.incomplete("batch_column", "batchColumnField", "Input column name is required"));
        } else {
            boolean colFound = rows.stream().anyMatch(r -> r.containsKey(inputColumn));
            if (!colFound) {
                validations.add(StepValidationResult.blocked("batch_column", "batchColumnField", "Specified input column '" + inputColumn + "' not found in batch rows"));
            }
        }

        if (operation == null || operation.isBlank()) {
            validations.add(StepValidationResult.incomplete("batch_op", "batchOperationCombo", "Batch operation not selected"));
        } else if (!isSupportedOperation(operation)) {
            validations.add(StepValidationResult.blocked("batch_op", "batchOperationCombo", "Unknown batch operation: " + operation));
        }

        if (isCipherOperation(operation)) {
            if (algorithm == null || algorithm.isBlank()) {
                validations.add(StepValidationResult.incomplete("batch_algo", "batchAlgorithmCombo", "Cipher algorithm not selected"));
            }
            if (keyHex == null || keyHex.isBlank()) {
                validations.add(StepValidationResult.incomplete("batch_key", "batchKeyField", "Key is required for cipher operation"));
            } else if (!isValidHex(keyHex)) {
                validations.add(StepValidationResult.blocked("batch_key", "batchKeyField", "Invalid Hex key format (must be even length hex string)"));
            } else {
                int len = keyHex.trim().length();
                if (len != 32 && len != 48 && len != 64) {
                    validations.add(StepValidationResult.warning("batch_key", "batchKeyField", "Key length is " + len + " hex chars (expected 32, 48, or 64)"));
                }
            }
            if (ivHex != null && !ivHex.isBlank() && !isValidHex(ivHex)) {
                validations.add(StepValidationResult.blocked("batch_iv", "batchIvNonceField", "Invalid Hex IV/Nonce format"));
            } else if (algorithm != null && algorithm.contains("CBC") && (ivHex == null || ivHex.isBlank())) {
                validations.add(StepValidationResult.warning("batch_iv", "batchIvNonceField", "IV/Nonce hex is recommended for CBC mode"));
            }
        }

        boolean hasBlockedConfig = validations.stream().anyMatch(v -> v.status() == StepValidationResult.Status.BLOCKED);

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String rowId = "Row " + (i + 1);
            if (hasBlockedConfig) {
                validations.add(StepValidationResult.blocked(rowId, inputColumn != null ? inputColumn : "input", "Row blocked due to configuration error"));
            } else if (inputColumn != null && !row.containsKey(inputColumn)) {
                validations.add(StepValidationResult.blocked(rowId, inputColumn, "Row missing column '" + inputColumn + "'"));
            } else {
                validations.add(StepValidationResult.ready(rowId, "Row ready for processing"));
            }
        }

        int ready = 0;
        int warning = 0;
        int incomplete = 0;
        int blocked = 0;
        String firstBlocked = null;

        for (StepValidationResult v : validations) {
            switch (v.status()) {
                case READY -> ready++;
                case WARNING -> warning++;
                case INCOMPLETE -> incomplete++;
                case BLOCKED -> {
                    blocked++;
                    if (firstBlocked == null) firstBlocked = "[" + v.targetNodeId() + "] " + v.message();
                }
                case NOT_APPLICABLE -> {}
            }
        }

        List<String> resolvedDeps = List.of(
            "Batch Format: " + (rows.size() > 0 ? "CSV/JSONL (" + rows.size() + " rows)" : "Empty"),
            "Operation: " + (operation != null ? operation : "None"),
            "Input Col: " + (inputColumn != null ? inputColumn : "input"),
            "Output Col: " + (outputColumn != null ? outputColumn : "result")
        );

        return new DryRunSummary(
            validations.size(),
            ready,
            warning,
            incomplete,
            blocked,
            firstBlocked,
            List.of("Execute batch rows 1 to " + rows.size()),
            resolvedDeps,
            validations
        );
    }

    private static boolean isSupportedOperation(String op) {
        if (op == null) return false;
        return BatchOperationCatalog.isSupportedOperation(op) || isCipherOperation(op);
    }

    private static boolean isCipherOperation(String op) {
        if (op == null) return false;
        String lower = op.trim().toLowerCase();
        return lower.contains("cipher") || lower.contains("encrypt") || lower.contains("decrypt");
    }

    private static boolean isValidHex(String str) {
        if (str == null) return false;
        String s = str.trim();
        return !s.isEmpty() && s.length() % 2 == 0 && s.matches("[0-9a-fA-F]+");
    }
}
