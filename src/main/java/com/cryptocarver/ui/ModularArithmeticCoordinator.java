package com.cryptocarver.ui;

import com.cryptocarver.crypto.ModularArithmetic;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.function.Supplier;

/**
 * The Modular Arithmetic pane: addition, subtraction, negation, multiplication, exponentiation
 * and inverse modulo m, GCD, LCM, extended GCD and XOR, on hex (or decimal for XOR) operands.
 */
final class ModularArithmeticCoordinator {

    /** The pane's controls, injected into GenericController from generic.fxml. */
    record View(ComboBox<String> operation,
            TextField operandA,
            TextField operandB,
            TextField modulus,
            TextArea result) {
    }

    private final ComboBox<String> modOperationCombo;
    private final TextField modOperandAField;
    private final TextField modOperandBField;
    private final TextField modModulusField;
    private final TextArea modResultArea;
    private final Supplier<StatusReporter> reporter;

    ModularArithmeticCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.modOperationCombo = view.operation();
        this.modOperandAField = view.operandA();
        this.modOperandBField = view.operandB();
        this.modModulusField = view.modulus();
        this.modResultArea = view.result();
        this.reporter = reporter;
    }

    /** Fills the pane's choices. */
    void configure() {
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
                    "XOR (Hex Input)",
                    "XOR (Decimal Input)");
            modOperationCombo.getSelectionModel().select(0);
        }
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    /**
     * Calculate modular arithmetic operation
     */
    void handleModularCalculate() {
        // A rejected input must not leave the previous result on screen.
        modResultArea.clear();
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
                    reporter().showError("Input Error", "Please enter valid decimal numbers");
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
                    reporter().showError("Input Error", "Both operands required for XOR");
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
                reporter().showError("Input Error", "Operand A is required");
                return;
            }

            String result;
            String operationDesc;

            try {
                switch (operation) {
                    case "Addition (a + b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            reporter().showError("Input Error", "All fields required for addition");
                            return;
                        }
                        result = ModularArithmetic.modularAddition(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + " + " + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Subtraction (a - b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            reporter().showError("Input Error", "All fields required for subtraction");
                            return;
                        }
                        result = ModularArithmetic.modularSubtraction(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + " - " + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Inverse -a mod m":
                        if (mHex.isEmpty()) {
                            reporter().showError("Input Error", "Modulus is required");
                            return;
                        }
                        result = ModularArithmetic.modularInverse(aHex, mHex);
                        operationDesc = "-" + aHex + " mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Multiplication (a * b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            reporter().showError("Input Error", "All fields required for multiplication");
                            return;
                        }
                        result = ModularArithmetic.modularMultiplication(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + " * " + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Exponentiation (a^b) mod m":
                        if (bHex.isEmpty() || mHex.isEmpty()) {
                            reporter().showError("Input Error", "All fields required for exponentiation");
                            return;
                        }
                        result = ModularArithmetic.modularExponentiation(aHex, bHex, mHex);
                        operationDesc = "(" + aHex + "^" + bHex + ") mod " + mHex;
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Reciprocal (1/a) mod m":
                        if (mHex.isEmpty()) {
                            reporter().showError("Input Error", "Modulus is required");
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
                            reporter().showError("Input Error", "Operand B is required");
                            return;
                        }
                        result = ModularArithmetic.gcd(aHex, bHex);
                        operationDesc = "GCD(" + aHex + ", " + bHex + ")";
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "LCM(a, b)":
                        if (bHex.isEmpty()) {
                            reporter().showError("Input Error", "Operand B is required");
                            return;
                        }
                        result = ModularArithmetic.lcm(aHex, bHex);
                        operationDesc = "LCM(" + aHex + ", " + bHex + ")";
                        modResultArea.setText(ModularArithmetic.formatResult(operationDesc, result));
                        break;

                    case "Extended GCD":
                        if (bHex.isEmpty()) {
                            reporter().showError("Input Error", "Operand B is required");
                            return;
                        }
                        result = ModularArithmetic.extendedGCD(aHex, bHex);
                        modResultArea.setText("Extended Euclidean Algorithm\n" +
                                "Finding x, y such that: ax + by = gcd(a,b)\n\n" + result);
                        break;

                    default:
                        modResultArea.setText("Unknown operation");
                }

                reporter().publish(OperationResult.forOperation("Modular Arithmetic")
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
            reporter().showError("Calculation Error", "Error in modular arithmetic: " + e.getMessage());
        }
    }
}
