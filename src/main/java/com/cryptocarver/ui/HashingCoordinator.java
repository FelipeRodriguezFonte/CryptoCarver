package com.cryptocarver.ui;

import com.cryptocarver.crypto.HashOperations;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Hashing pane: hashes the input in the toolbar's input format and prints it in the
 * toolbar's output format, with the built-in and personal hashing templates.
 */
final class HashingCoordinator {

    /** The pane's controls, injected into GenericController from generic.fxml. */
    record View(ComboBox<String> template,
            ComboBox<String> algorithm,
            TextArea input,
            TextArea output) {
    }

    private final ComboBox<String> hashTemplateCombo;
    private final ComboBox<String> hashAlgorithmCombo;
    private final TextArea hashInputArea;
    private final TextArea hashOutputArea;
    private final Supplier<StatusReporter> reporter;
    /** The shell toolbar's format selectors, handed to the controller after the panes load. */
    private final Supplier<ComboBox<String>> toolbarInput;
    private final Supplier<ComboBox<String>> toolbarOutput;
    /** Sets the toolbar formats through the shell, as the controller does for every pane. */
    private final Consumer<String> sharedInputFormat;
    private final Consumer<String> sharedOutputFormat;

    HashingCoordinator(View view, Supplier<StatusReporter> reporter, Supplier<ComboBox<String>> toolbarInput,
            Supplier<ComboBox<String>> toolbarOutput, Consumer<String> sharedInputFormat,
            Consumer<String> sharedOutputFormat) {
        this.hashTemplateCombo = view.template();
        this.hashAlgorithmCombo = view.algorithm();
        this.hashInputArea = view.input();
        this.hashOutputArea = view.output();
        this.reporter = reporter;
        this.toolbarInput = toolbarInput;
        this.toolbarOutput = toolbarOutput;
        this.sharedInputFormat = sharedInputFormat;
        this.sharedOutputFormat = sharedOutputFormat;
    }

    /** Fills the pane's choices and its template list. */
    void configure() {
        if (hashAlgorithmCombo != null) {
            hashAlgorithmCombo.getItems().addAll(HashOperations.SUPPORTED_ALGORITHMS);
            hashAlgorithmCombo.getItems().add("CRC32");
            for (HashOperations.Crc32Variant variant : HashOperations.Crc32Variant.values()) hashAlgorithmCombo.getItems().add(variant.displayName());
            hashAlgorithmCombo.setValue("SHA-256");
        }
        refreshHashTemplateCombo();
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private ComboBox<String> toolbarInput() {
        return toolbarInput.get();
    }

    private ComboBox<String> toolbarOutput() {
        return toolbarOutput.get();
    }

    void fillHashInput(String text) {
        if (hashInputArea != null) {
            hashInputArea.setText(text);
        }
    }

    void fillHashInput(String text, com.cryptocarver.model.ClipboardEntry.Format format) {
        fillHashInput(text);
        if (reporter() != null) reporter().setInputFormat(clipboardFormatName(format));
    }

    private String clipboardFormatName(com.cryptocarver.model.ClipboardEntry.Format format) {
        return switch (format == null ? com.cryptocarver.model.ClipboardEntry.Format.UNKNOWN : format) {
            case HEX -> "Hexadecimal";
            case BASE64 -> "Base64";
            case BASE64URL -> "Base64URL";
            default -> "Text (UTF-8)";
        };
    }

    void refreshHashTemplateCombo() {
        SafeTemplateUIHelper.populateTemplateCombo(
                hashTemplateCombo,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING,
                List.of("SHA-256 — Text UTF-8 → Hex", "SHA-512 — Text UTF-8 → Base64")
        );
    }

    void handleApplyHashTemplate() {
        String template = hashTemplateCombo != null ? hashTemplateCombo.getValue() : null;
        if (template == null) return;

        Map<String, java.util.function.Consumer<String>> setters = Map.of(
                "hashAlgorithmCombo", v -> { if (hashAlgorithmCombo != null) hashAlgorithmCombo.setValue(v); },
                "inputFormatCombo", sharedInputFormat,
                "outputFormatCombo", sharedOutputFormat
        );

        SafeTemplateUIHelper.applySelectedTemplate(
                template,
                com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING,
                () -> {
                    if (template.contains("SHA-256")) {
                        hashAlgorithmCombo.setValue("SHA-256");
                        if (reporter() != null) {
                            reporter().setInputFormat("Text (UTF-8)");
                            reporter().setOutputFormat("Hexadecimal");
                            reporter().updateStatus("Template Applied: SHA-256 — Text UTF-8 → Hex. A hash is one-way; it cannot be decrypted.");
                        }
                    } else if (template.contains("SHA-512")) {
                        hashAlgorithmCombo.setValue("SHA-512");
                        if (reporter() != null) {
                            reporter().setInputFormat("Text (UTF-8)");
                            reporter().setOutputFormat("Base64");
                            reporter().updateStatus("Template Applied: SHA-512 — Text UTF-8 → Base64. A hash is one-way; it cannot be decrypted.");
                        }
                    }
                },
                setters,
                reporter()
        );
    }

    void handleSaveHashTemplate() {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (hashAlgorithmCombo != null && hashAlgorithmCombo.getValue() != null) params.put("hashAlgorithmCombo", hashAlgorithmCombo.getValue());
        if (toolbarInput() != null && toolbarInput().getValue() != null) params.put("inputFormatCombo", toolbarInput().getValue());
        if (toolbarOutput() != null && toolbarOutput().getValue() != null) params.put("outputFormatCombo", toolbarOutput().getValue());
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.saveCurrentAsTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, params, this::refreshHashTemplateCombo, reporter());
    }

    void handleExportHashTemplate() {
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.exportSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, hashTemplateCombo, reporter());
    }

    void handleImportHashTemplate() {
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.importTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, this::refreshHashTemplateCombo, reporter());
    }

    void handleDeleteHashTemplate() {
        javafx.stage.Window owner = hashTemplateCombo != null && hashTemplateCombo.getScene() != null ? hashTemplateCombo.getScene().getWindow() : null;
        SafeTemplateUIHelper.deleteSelectedTemplate(owner, com.cryptocarver.model.SafeTemplateAllowlist.MODULE_HASHING, hashTemplateCombo, this::refreshHashTemplateCombo, reporter());
    }

    void handleResetHashDefaults() {
        hashAlgorithmCombo.setValue("SHA-256");
        if (reporter() != null) {
            reporter().setInputFormat("Text (UTF-8)");
            reporter().setOutputFormat("Hexadecimal");
            reporter().updateStatus("Hash form reset to default");
        }
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
    void calculateHash(String input, String inputFormat, String algorithm, TextInputControl targetOutputArea) {
        calculateHash(input, inputFormat, "Hexadecimal", algorithm, targetOutputArea);
    }

    /** Calculates a hash and serializes its bytes using the selected output format. */
    void calculateHash(String input, String inputFormat, String outputFormat,
            String algorithm, TextInputControl targetOutputArea) {
        try {
            inputFormat = GenericController.normalizeFormatName(inputFormat);
            outputFormat = GenericController.normalizeFormatName(outputFormat);
            if (input == null || input.isEmpty()) {
                reporter().showError("Input Error", "Please enter data to hash");
                return;
            }

            if (algorithm == null || algorithm.isEmpty()) {
                reporter().showError("Algorithm Error", "Please select a hash algorithm");
                return;
            }

            // Parse input based on format
            byte[] inputData;
            try {
                inputData = GenericController.parseInput(input, inputFormat);
            } catch (IllegalArgumentException e) {
                reporter().showError("Input Error", e.getMessage());
                return;
            }

            // Calculate hash
            byte[] hash = HashOperations.calculateHash(inputData, algorithm);
            String formattedHash = GenericController.formatBytes(hash, outputFormat);

            // Display result
            targetOutputArea.setText(formattedHash);
            reporter().publish(OperationResult.forOperation("Hashing: " + algorithm)
                    .input(inputData).output(hash)
                    .detail("Algorithm", algorithm).detail("Input Format", inputFormat)
                    .detail("Output Format", outputFormat)
                    .status("Hash calculated using " + algorithm).build());

        } catch (NoSuchAlgorithmException e) {
            reporter().showError("Algorithm Error", "Algorithm not supported: " + e.getMessage());
        } catch (Exception e) {
            reporter().showError("Hash Error", "Error calculating hash: " + e.getMessage());
        }
    }

    void handleCalculateHash() {
        if (reporter() != null && !reporter().checkPreflightReadiness("Hashing", true)) {
            return;
        }
        if (hashInputArea != null && hashAlgorithmCombo != null && hashOutputArea != null) {
            calculateHash(hashInputArea.getText(),
                    selectedFormatOrDefault(toolbarInput(), "Text (UTF-8)"),
                    selectedFormatOrDefault(toolbarOutput(), "Hexadecimal"),
                    hashAlgorithmCombo.getValue(),
                    hashOutputArea);
        }
    }

    private static String selectedFormatOrDefault(ComboBox<String> combo, String defaultFormat) {
        return combo != null && combo.getValue() != null ? combo.getValue() : defaultFormat;
    }
}
