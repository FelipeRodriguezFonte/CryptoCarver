package com.cryptocarver.ui;

import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The CVV panel of the payments screen: CVV, CVV2 and iCVV from the CVK pair (with the service
 * code each type forces) and dynamic dCVV with an ATC. Verification asks for the value to check.
 */
final class CvvCoordinator {

    /** The panel's controls, injected into PaymentsController from payments.fxml. */
    record View(TextField cvkA,
            TextField cvkB,
            TextField pan,
            TextField expiry,
            TextField serviceCode,
            TextField atc,
            ComboBox<String> type,
            TextArea result) {
    }

    private final TextField cvkAField;
    private final TextField cvkBField;
    private final TextField panFieldCvv;
    private final TextField expiryDateField;
    private final TextField serviceCodeField;
    private final TextField atcField;
    private final ComboBox<String> cvvTypeCombo;
    private final TextArea cvvResultArea;
    private final Supplier<StatusReporter> reporter;
    /** Asks for the CVV to verify; the controller shows a dialog, tests answer directly. */
    private final Supplier<Optional<String>> askCvv;

    CvvCoordinator(View view, Supplier<StatusReporter> reporter, Supplier<Optional<String>> askCvv) {
        this.cvkAField = view.cvkA();
        this.cvkBField = view.cvkB();
        this.panFieldCvv = view.pan();
        this.expiryDateField = view.expiry();
        this.serviceCodeField = view.serviceCode();
        this.atcField = view.atc();
        this.cvvTypeCombo = view.type();
        this.cvvResultArea = view.result();
        this.reporter = reporter;
        this.askCvv = askCvv;
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private static String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private void updateStatus(String message) {
        if (reporter() != null) reporter().updateStatus(message);
    }


    void configure() {
        if (cvvTypeCombo == null) {
            return; // Safety check
        }
        cvvTypeCombo.getItems().addAll(
                "CVV (Magnetic Stripe)",
                "CVV2 (Card Printed)",
                "iCVV (Chip)",
                "dCVV (Dynamic)");
        cvvTypeCombo.getSelectionModel().selectFirst();
    }

    // ==================== CVV HANDLERS ====================

    void handleGenerateCvv() {
        try {
            String cvkA = cvkAField.getText().trim().replaceAll("\\s+", "");
            String cvkB = cvkBField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldCvv.getText().trim().replaceAll("\\s+", "");
            String expiry = expiryDateField.getText().trim();
            String serviceCode = serviceCodeField.getText().trim();
            String atc = atcField.getText().trim();
            String cvvType = cvvTypeCombo.getSelectionModel().getSelectedItem();

            // Auto-populate Service Code if empty based on type
            if (serviceCode.isEmpty()) {
                if (cvvType != null) {
                    if (cvvType.contains("CVV2")) {
                        serviceCode = "000";
                        serviceCodeField.setText("000");
                    } else if (cvvType.contains("iCVV")) {
                        serviceCode = "000"; // Display 000 as per user preference/expert tool
                        serviceCodeField.setText("000");
                    }
                }
            }

            // Validate inputs
            if (cvkA.isEmpty() || cvkB.isEmpty() || pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                cvvResultArea.setText(t("module.payments.error.cvvRequired"));
                return;
            }

            if (!cvkA.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkAInvalid"));
                return;
            }

            if (!cvkB.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkBInvalid"));
                return;
            }

            if (!pan.matches("\\d{13,19}")) {
                cvvResultArea.setText(t("module.payments.error.panInvalid"));
                return;
            }

            if (!expiry.matches("\\d{4}")) {
                cvvResultArea.setText(t("module.payments.error.expiryInvalid"));
                return;
            }

            if (!serviceCode.matches("\\d{3}")) {
                cvvResultArea.setText(t("module.payments.error.serviceCodeInvalid"));
                return;
            }

            // Generate CVV
            String cvv;
            String serviceCodeForCalc = serviceCode;
            if (cvvType != null && cvvType.contains("dCVV")) {
                if (atc.isEmpty()) {
                    cvvResultArea.setText(t("module.payments.error.atcRequired"));
                    return;
                }
                if (!atc.matches("[0-9A-Fa-f]{1,4}")) {
                    cvvResultArea.setText(t("module.payments.error.atcInvalid"));
                    return;
                }
                // CVK A || CVK B is the issuer MDK; the card key is derived with PSN 00.
                cvv = PaymentOperations.generateDCVV(cvkA, cvkB, pan, "00", expiry, serviceCode, atc);
            } else { // Standard CVV, CVV2, iCVV
                if (cvvType != null && cvvType.contains("iCVV")) {
                    // iCVV always uses 999 for calculation, regardless of magnetic stripe service
                    // code
                    serviceCodeForCalc = "999";
                } else if (cvvType != null && cvvType.contains("CVV2")) {
                    // CVV2 always uses 000 for calculation
                    serviceCodeForCalc = "000";
                }
                cvv = PaymentOperations.generateCVV(cvkA, cvkB, pan, expiry, serviceCodeForCalc);
            }

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("═══ ").append(t("module.payments.result.cvvGenerationTitle")).append(" ═══\n\n");
            result.append(t("module.payments.result.type")).append("         ").append(cvvType);
            if (cvvType != null && cvvType.contains("dCVV")) {
                result.append(" (Visa CVN 10)");
            }
            result.append("\n");

            result.append(t("module.payments.result.cvkA")).append("        ").append(cvkA.toUpperCase()).append("\n");
            result.append(t("module.payments.result.cvkB")).append("        ").append(cvkB.toUpperCase()).append("\n");
            result.append(t("module.payments.result.pan")).append("          ").append(pan).append("\n");
            result.append(t("module.payments.result.expiry")).append("       ").append(expiry).append("\n");

            // Always show Service Code, but note usage
            result.append(t("module.payments.result.serviceCode")).append(" ").append(serviceCode);
            if (cvvType != null) {
                if (cvvType.contains("CVV2") || cvvType.contains("iCVV")) {
                    result.append(" ").append(t("module.payments.result.forcedCalculation", serviceCodeForCalc));
                } else if (cvvType.contains("dCVV")) {
                    result.append(" ").append(t("module.payments.result.notUsedDcvv"));
                }
            }
            result.append("\n");

            if (!atc.isEmpty() || (cvvType != null && cvvType.contains("dCVV"))) {
                result.append(t("module.payments.result.atc")).append("          ").append(atc)
                        .append(cvvType.contains("dCVV") ? " " + t("module.payments.result.usedDcvv") : " " + t("module.payments.result.notUsedStatic") + "\n");
            }
            result.append("\n");
            result.append(t("module.payments.result.cvv")).append("          ").append(cvv).append("\n");

            cvvResultArea.setText(result.toString());
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Type", cvvType);
            details.put("PAN", PanMask.mask(pan));
            details.put("Expiry", expiry);
            details.put("Service Code", serviceCode);
            reporter().publish(OperationResult.forOperation("Generate CVV")
                    .output(cvv.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            cvvResultArea.setText(t("module.payments.error.operation", t("module.payments.result.cvvGenerationTitle"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.result.cvvGenerationTitle"), e.getMessage()));
        }
    }

    void handleVerifyCvv() {
        try {
            String cvkA = cvkAField.getText().trim().replaceAll("\\s+", "");
            String cvkB = cvkBField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldCvv.getText().trim().replaceAll("\\s+", "");
            String expiry = expiryDateField.getText().trim();
            String serviceCode = serviceCodeField.getText().trim();

            // Use the result area text as "input" CVV if it looks like a CVV,
            // otherwise prompt or expect user to put it somewhere?
            // For now, let's assume verification matches the Generated one re-calculated.
            // Better: Add a dialog or assume the user compares it visually?
            // "Verify" usually implies taking an input CVV and checking it.
            // But we don't have a specific "Input CVV to Verify" field.
            // We can add a TextInputDialog.

            if (cvkA.isEmpty() || cvkB.isEmpty() || pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                cvvResultArea.setText(t("module.payments.error.cvvRequired"));
                return;
            }
            if (!cvkA.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkAInvalid"));
                return;
            }
            if (!cvkB.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText(t("module.payments.error.cvkBInvalid"));
                return;
            }
            if (!pan.matches("\\d{13,19}")) {
                cvvResultArea.setText(t("module.payments.error.panInvalid"));
                return;
            }
            if (!expiry.matches("\\d{4}")) {
                cvvResultArea.setText(t("module.payments.error.expiryInvalid"));
                return;
            }
            if (!serviceCode.matches("\\d{3}")) {
                cvvResultArea.setText(t("module.payments.error.serviceCodeInvalid"));
                return;
            }

            java.util.Optional<String> outcome = askCvv.get();
            if (outcome.isPresent()) {
                String inputCvv = outcome.get().trim();
                String atc = atcField.getText().trim();

                boolean isValid;
                String calculated;

                if (cvvTypeCombo.getSelectionModel().getSelectedItem() != null &&
                        cvvTypeCombo.getSelectionModel().getSelectedItem().contains("dCVV")) {

                    if (atc.isEmpty()) {
                        cvvResultArea.setText(t("module.payments.error.atcRequired"));
                        return;
                    }
                    if (!atc.matches("[0-9A-Fa-f]{1,4}")) {
                        cvvResultArea.setText(t("module.payments.error.atcInvalid"));
                        return;
                    }
                    isValid = PaymentOperations.verifyDCVV(cvkA, cvkB, pan, "00", expiry, serviceCode, atc, inputCvv);
                    calculated = PaymentOperations.generateDCVV(cvkA, cvkB, pan, "00", expiry, serviceCode, atc);

                } else {
                    String serviceCodeForCalc = serviceCode;
                    if (cvvTypeCombo.getSelectionModel().getSelectedItem() != null &&
                            cvvTypeCombo.getSelectionModel().getSelectedItem().contains("iCVV")) {
                        serviceCodeForCalc = "999";
                    } else if (cvvTypeCombo.getSelectionModel().getSelectedItem() != null &&
                            cvvTypeCombo.getSelectionModel().getSelectedItem().contains("CVV2")) {
                        serviceCodeForCalc = "000";
                    }

                    isValid = PaymentOperations.verifyCVV(cvkA, cvkB, pan, expiry, serviceCodeForCalc, inputCvv);
                    calculated = PaymentOperations.generateCVV(cvkA, cvkB, pan, expiry, serviceCodeForCalc);
                }

                StringBuilder result = new StringBuilder();
                result.append("═══ ").append(t("module.payments.result.cvvVerificationTitle")).append(" ═══\n\n");
                result.append(t("module.payments.result.inputCvv")).append("    ").append(inputCvv).append("\n");
                result.append(t("module.payments.result.calculated")).append("   ").append(calculated).append("\n\n");
                result.append(t("module.payments.result.result")).append("       ").append(t(isValid ? "module.payments.result.matchSymbol" : "module.payments.result.mismatchSymbol")).append("\n");

                cvvResultArea.setText(result.toString());
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                details.put("Type", cvvTypeCombo.getSelectionModel().getSelectedItem());
                details.put("PAN", PanMask.mask(pan));
                details.put("Result", isValid ? "VALID" : "INVALID");
                reporter().publish(OperationResult.forOperation("Verify CVV")
                        .output(calculated.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                        .status(t(isValid ? "module.payments.status.cvvValid" : "module.payments.status.cvvInvalid")).build());
            }

        } catch (Exception e) {
            cvvResultArea.setText(t("module.payments.error.operation", t("module.payments.result.cvvVerificationTitle"), e.getMessage()));
            updateStatus(t("module.payments.error.operation", t("module.payments.result.cvvVerificationTitle"), e.getMessage()));
        }
    }
}
