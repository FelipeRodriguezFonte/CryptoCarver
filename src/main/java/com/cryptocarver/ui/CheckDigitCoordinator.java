package com.cryptocarver.ui;

import com.cryptocarver.crypto.CheckDigitCalculator;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;

import java.util.function.Supplier;

/** The Check Digits pane: calculates or validates Luhn, AMEX SE, Verhoeff and Damm check digits. */
final class CheckDigitCoordinator {

    /** The pane's controls, injected into GenericController from generic.fxml. */
    record View(TextField input,
            ComboBox<String> algorithm,
            TextField output) {
    }

    private final TextField checkDigitInput;
    private final ComboBox<String> checkDigitAlgorithmCombo;
    private final TextField checkDigitOutput;
    private final Supplier<StatusReporter> reporter;

    CheckDigitCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.checkDigitInput = view.input();
        this.checkDigitAlgorithmCombo = view.algorithm();
        this.checkDigitOutput = view.output();
        this.reporter = reporter;
    }

    /** Fills the pane's choices. */
    void configure() {
        if (checkDigitAlgorithmCombo != null) {
            checkDigitAlgorithmCombo.getItems().addAll(CheckDigitCalculator.SUPPORTED_ALGORITHMS);
            checkDigitAlgorithmCombo.setValue("Luhn (Mod 10)");
        }
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    /**
     * Calculate check digit
     */
    void calculateCheckDigit(String input, String algorithm, TextInputControl targetOutputArea) {
        try {
            if (input == null || input.isEmpty()) {
                reporter().showError("Input Error", "Please enter numeric data");
                return;
            }
            if (algorithm == null || algorithm.isEmpty()) {
                reporter().showError("Algorithm Error", "Please select a check digit algorithm");
                return;
            }

            int checkDigit = CheckDigitCalculator.calculateCheckDigit(input, algorithm);
            String result = CheckDigitCalculator.formatWithCheckDigit(input, algorithm);

            targetOutputArea.setText("Check Digit: " + checkDigit + " - Complete: " + result);
            reporter().publish(OperationResult.forOperation("Check Digits")
                    .input(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(result.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Algorithm", algorithm).detail("Mode", "Calculate")
                    .detail("Check Digit", String.valueOf(checkDigit))
                    .status("Check digit calculated using " + algorithm).build());

        } catch (Exception e) {
            reporter().showError("Check Digit Error", "Error calculating check digit: " + e.getMessage());
        }
    }

    void handleCalculateCheckDigit() {
        if (checkDigitInput != null && checkDigitAlgorithmCombo != null && checkDigitOutput != null) {
            calculateCheckDigit(checkDigitInput.getText(), checkDigitAlgorithmCombo.getValue(), checkDigitOutput);
        }
    }

    void validateCheckDigit(String input, String algorithm, TextInputControl targetOutputArea) {
        try {
            if (input == null || input.isEmpty()) {
                reporter().showError("Input Error", "Please enter data with check digit");
                return;
            }
            if (algorithm == null || algorithm.isEmpty()) {
                reporter().showError("Algorithm Error", "Please select a check digit algorithm");
                return;
            }

            boolean isValid = CheckDigitCalculator.validateCheckDigit(input, algorithm);
            String resultText = isValid ? "✅ VALID" : "❌ INVALID";

            targetOutputArea.setText("Validation Result: " + resultText);
            reporter().publish(OperationResult.forOperation("Check Digits")
                    .input(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(resultText.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .detail("Algorithm", algorithm).detail("Mode", "Validate")
                    .detail("Result", isValid ? "VALID" : "INVALID")
                    .status("Check digit validation: " + resultText).build());

        } catch (Exception e) {
            reporter().showError("Validation Error", "Error validating: " + e.getMessage());
        }
    }

    void handleValidateCheckDigit() {
        if (checkDigitInput != null && checkDigitAlgorithmCombo != null && checkDigitOutput != null) {
            validateCheckDigit(checkDigitInput.getText(), checkDigitAlgorithmCombo.getValue(), checkDigitOutput);
        }
    }
}
