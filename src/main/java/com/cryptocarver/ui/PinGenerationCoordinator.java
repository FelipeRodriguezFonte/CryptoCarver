package com.cryptocarver.ui;

import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * PIN generation and verification on the payments screen: IBM 3624 natural PIN plus offset
 * (with a configurable validation-data window), the offset generator for a chosen PIN, VISA PVV
 * generation and the search for PINs that produce a given PVV. PANs are masked and PINs are
 * marked as not persisted in published details.
 */
final class PinGenerationCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(PinGenerationCoordinator.class);
    private static final String DEFAULT_CONVERSION_TABLE = "0123456789012345";

    /** The panels' controls, injected into PaymentsController from payments.fxml. */
    record View(TextField ibm3624Pvk,
            TextField ibm3624ConversionTable,
            TextField ibm3624Offset,
            TextField ibm3624Pan,
            TextField ibm3624PinToVerify,
            TextArea ibm3624Result,
            TextField ibm3624Start,
            TextField ibm3624Length,
            TextField ibm3624Pad,
            TextField offsetPvk,
            TextField offsetDecimalizationTable,
            TextField offsetPan,
            TextField offsetPin,
            TextArea offsetResult,
            TextField offsetStart,
            TextField offsetLength,
            TextField offsetPad,
            TextField pvvPvk,
            TextField pvvPan,
            TextField pvvPin,
            TextField pvvKeyIndex,
            TextArea pvvResult,
            TextField derivePvk,
            TextField derivePan,
            TextField deriveTargetPvv,
            TextField deriveKeyIndex,
            TextArea deriveResult) {
    }

    private final TextField ibm3624PvkField;
    private final TextField ibm3624ConvTableField;
    private final TextField ibm3624OffsetField;
    private final TextField ibm3624PanField;
    private final TextField ibm3624PinVerifyField;
    private final TextArea ibm3624ResultArea;
    private final TextField ibm3624StartField;
    private final TextField ibm3624LengthField;
    private final TextField ibm3624PadField;
    private final TextField genOffsetPvkField;
    private final TextField genOffsetDecTableField;
    private final TextField genOffsetPanField;
    private final TextField genOffsetPinField;
    private final TextArea genOffsetResultArea;
    private final TextField genOffsetStartField;
    private final TextField genOffsetLengthField;
    private final TextField genOffsetPadField;
    private final TextField genPvvPvkField;
    private final TextField genPvvPanField;
    private final TextField genPvvPinField;
    private final TextField genPvvKeyIndexField;
    private final TextArea genPvvResultArea;
    private final TextField derivePvvPvkField;
    private final TextField derivePvvPanField;
    private final TextField derivePvvTargetPvvField;
    private final TextField derivePvvKeyIndexField;
    private final TextArea derivePvvResultArea;
    private final Supplier<StatusReporter> reporter;

    PinGenerationCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.ibm3624PvkField = view.ibm3624Pvk();
        this.ibm3624ConvTableField = view.ibm3624ConversionTable();
        this.ibm3624OffsetField = view.ibm3624Offset();
        this.ibm3624PanField = view.ibm3624Pan();
        this.ibm3624PinVerifyField = view.ibm3624PinToVerify();
        this.ibm3624ResultArea = view.ibm3624Result();
        this.ibm3624StartField = view.ibm3624Start();
        this.ibm3624LengthField = view.ibm3624Length();
        this.ibm3624PadField = view.ibm3624Pad();
        this.genOffsetPvkField = view.offsetPvk();
        this.genOffsetDecTableField = view.offsetDecimalizationTable();
        this.genOffsetPanField = view.offsetPan();
        this.genOffsetPinField = view.offsetPin();
        this.genOffsetResultArea = view.offsetResult();
        this.genOffsetStartField = view.offsetStart();
        this.genOffsetLengthField = view.offsetLength();
        this.genOffsetPadField = view.offsetPad();
        this.genPvvPvkField = view.pvvPvk();
        this.genPvvPanField = view.pvvPan();
        this.genPvvPinField = view.pvvPin();
        this.genPvvKeyIndexField = view.pvvKeyIndex();
        this.genPvvResultArea = view.pvvResult();
        this.derivePvvPvkField = view.derivePvk();
        this.derivePvvPanField = view.derivePan();
        this.derivePvvTargetPvvField = view.deriveTargetPvv();
        this.derivePvvKeyIndexField = view.deriveKeyIndex();
        this.derivePvvResultArea = view.deriveResult();
        this.reporter = reporter;
    }

    /** Fills the IBM 3624 conversion table with the identity table most test vectors use. */
    void configure() {
        if (ibm3624ConvTableField != null) {
            ibm3624ConvTableField.setText(DEFAULT_CONVERSION_TABLE);
        }
    }

    private StatusReporter reporter() {
        return reporter.get();
    }

    private static String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }

    private void showError(String title, String message) {
        showError(title, message, null);
    }

    private void showError(String title, String message, String fieldKey) {
        if (reporter() != null) {
            String safeMessage = InlineErrorPresenter.redactSecrets(message);
            reporter().showError(new UserFacingError(title, safeMessage, safeMessage, fieldKey));
        }
    }

    // ============================================================
    // IBM 3624 PIN OPERATIONS
    // ============================================================

    void handleGenerateIbm3624Pin() {
        try {
            if (ibm3624PvkField == null || ibm3624OffsetField == null || ibm3624PanField == null
                    || ibm3624ResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "IBM 3624"));
                return;
            }

            String pvkHex = ibm3624PvkField.getText().trim();
            String convTable = ibm3624ConvTableField != null ? ibm3624ConvTableField.getText().trim()
                    : "0123456789012345";
            String offset = ibm3624OffsetField.getText().trim();
            String pan = ibm3624PanField.getText().trim();

            if (pvkHex.isEmpty() || offset.isEmpty() || pan.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkOffsetPanRequired"), "ibm3624PvkField");
                return;
            }

            // Convert PVK to bytes
            byte[] pvk = DataConverter.hexToBytes(pvkHex);

            // Parse configuration
            int startPos = 0;
            int length = 12; // Default for simpler IBM 3624
            String padChar = "0";

            if (ibm3624StartField != null && !ibm3624StartField.getText().trim().isEmpty()) {
                try {
                    startPos = Integer.parseInt(ibm3624StartField.getText().trim());
                    // Convert 1-based start position to 0-based index
                    if (startPos > 0)
                        startPos--;
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidStartPosition"), "ibm3624StartField");
                    return;
                }
            }

            if (ibm3624LengthField != null && !ibm3624LengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(ibm3624LengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidLength"), "ibm3624LengthField");
                    return;
                }
            }

            if (ibm3624PadField != null && !ibm3624PadField.getText().trim().isEmpty()) {
                padChar = ibm3624PadField.getText().trim().substring(0, 1);
            }

            // Generate PIN using IBM 3624 method
            String pin = com.cryptocarver.pin.Pin.generateIbm3624Pin(
                    pvk,
                    convTable,
                    offset,
                    pan,
                    startPos,
                    length,
                    padChar);

            // Reconstruct Validation Data Block for display (Debugging feedback)
            String rawVd = "";
            try {
                if (pan.length() >= startPos + length) {
                    rawVd = pan.substring(startPos, startPos + length);
                } else {
                    rawVd = "Error: bounds";
                }
            } catch (Exception e) {
                rawVd = "Error";
            }

            // Pad if necessary (Display logic only, Pin.java handles actual logic)
            String displayVd = rawVd;
            if (!rawVd.startsWith("Error")) {
                if (displayVd.length() > 16)
                    displayVd = displayVd.substring(0, 16);
                while (displayVd.length() < 16)
                    displayVd += padChar;
            }

            // Show User's Start Input (startPos + 1) for clarity
            int displayStart = startPos + 1;

            String result = t("module.payments.result.pin") + " " + pin + "\n\n" +
                    t("module.payments.result.method") + " IBM 3624\n" +
                    t("module.payments.result.pan") + " " + pan + "\n" +
                    "Offset: " + offset + "\n" +
                    "Conversion Table: " + convTable + "\n" +
                    t("module.payments.result.validationConfig", displayStart, length, padChar) + "\n" +
                    t("module.payments.result.validationDataBlock", " (Computed)") + " " + displayVd.toUpperCase();

            ibm3624ResultArea.setText(result);
            ibm3624ResultArea.setManaged(true);
            ibm3624ResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624");
            details.put("PAN", PanMask.mask(pan));
            details.put("Offset", offset);
            details.put("PIN", "[not persisted]");
            reporter().publish(OperationResult.forOperation("Generate PIN (IBM 3624)")
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.generationTitle"), t("module.payments.error.operation", "IBM 3624", e.getMessage()));
            LOG.error("IBM 3624 PIN generation failed", e);
        }
    }

    void handleVerifyIbm3624Pin() {
        try {
            if (ibm3624PvkField == null || ibm3624PinVerifyField == null || ibm3624PanField == null
                    || ibm3624ResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "IBM 3624 verify"));
                return;
            }

            String pvkHex = ibm3624PvkField.getText().trim();
            String convTable = ibm3624ConvTableField != null ? ibm3624ConvTableField.getText().trim()
                    : "0123456789012345";
            String offset = ibm3624OffsetField != null ? ibm3624OffsetField.getText().trim() : "";
            String pan = ibm3624PanField.getText().trim();
            String pinToVerify = ibm3624PinVerifyField.getText().trim();

            if (pvkHex.isEmpty() || pan.isEmpty() || pinToVerify.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkPanPinRequired"), "ibm3624PvkField");
                return;
            }

            // Convert PVK to bytes
            byte[] pvk = DataConverter.hexToBytes(pvkHex);

            // Parse configuration (Same as Generation)
            int startPos = 0;
            int length = 12; // Default
            String padChar = "0";

            if (ibm3624StartField != null && !ibm3624StartField.getText().trim().isEmpty()) {
                try {
                    startPos = Integer.parseInt(ibm3624StartField.getText().trim());
                    if (startPos > 0)
                        startPos--; // 1-based to 0-based
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidStartPosition"), "ibm3624StartField");
                    return;
                }
            }

            if (ibm3624LengthField != null && !ibm3624LengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(ibm3624LengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidLength"), "ibm3624LengthField");
                    return;
                }
            }

            if (ibm3624PadField != null && !ibm3624PadField.getText().trim().isEmpty()) {
                padChar = ibm3624PadField.getText().trim().substring(0, 1);
            }

            // Generate expected PIN - use static method with all parameters
            String expectedPin = com.cryptocarver.pin.Pin.generateIbm3624Pin(
                    pvk,
                    convTable,
                    offset,
                    pan,
                    startPos,
                    length,
                    padChar);

            // Reconstruct Validation Data Block for display (Debugging feedback)
            String rawVd = "";
            try {
                if (pan.length() >= startPos + length) {
                    rawVd = pan.substring(startPos, startPos + length);
                } else {
                    rawVd = "Error: bounds";
                }
            } catch (Exception e) {
                rawVd = "Error";
            }

            String displayVd = rawVd;
            if (!rawVd.startsWith("Error")) {
                if (displayVd.length() > 16)
                    displayVd = displayVd.substring(0, 16);
                while (displayVd.length() < 16)
                    displayVd += padChar;
            }
            int displayStart = startPos + 1;

            boolean isValid = expectedPin.equals(pinToVerify);

            String result = t("module.payments.result.pinVerification") + " " + t(isValid ? "module.payments.result.validSymbol" : "module.payments.result.invalidSymbol") + "\n\n" +
                    t("module.payments.result.enteredPin") + " " + pinToVerify + "\n" +
                    t("module.payments.result.expectedPin") + " " + expectedPin + "\n" +
                    t("module.payments.result.method") + " IBM 3624\n" +
                    t("module.payments.result.pan") + " " + pan + "\n" +
                    "Offset: " + offset + "\n" +
                    t("module.payments.result.validationConfig", displayStart, length, padChar) + "\n" +
                    t("module.payments.result.validationDataBlock", "") + " " + displayVd.toUpperCase();

            ibm3624ResultArea.setText(result);
            ibm3624ResultArea.setManaged(true);
            ibm3624ResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624");
            details.put("PAN", PanMask.mask(pan));
            details.put("Result", isValid ? "VALID" : "INVALID");
            details.put("PIN", "[not persisted]");
            reporter().publish(OperationResult.forOperation("Verify PIN (IBM 3624)")
                    .output(expectedPin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t(isValid ? "module.payments.status.valid" : "module.payments.status.invalid")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.verificationTitle"), t("module.payments.error.operation", "IBM 3624", e.getMessage()));
            LOG.error("IBM 3624 PIN verification failed", e);
        }
    }

    // PIN GENERATORS (OFFSET & PVV)
    // ============================================================

    void handleGenerateOffsetUtility() {
        try {
            if (genOffsetPvkField == null || genOffsetResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "PIN generator"));
                return;
            }

            String pvk = genOffsetPvkField.getText().trim();
            String decTable = genOffsetDecTableField.getText().trim();
            String pan = genOffsetPanField.getText().trim();
            String pin = genOffsetPinField.getText().trim();

            if (pvk.isEmpty() || decTable.isEmpty() || pan.isEmpty() || pin.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkPanPin"), "genOffsetPvkField");
                return;
            }

            if (decTable.length() != 16) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.decimalizationTable"), "genOffsetDecTableField");
                return;
            }

            // Convert PVK to bytes
            byte[] pvkBytes = DataConverter.hexToBytes(pvk);

            // Parse configuration
            int startPos = 0;
            int length = 12; // Default
            String padChar = "0";

            if (genOffsetStartField != null && !genOffsetStartField.getText().trim().isEmpty()) {
                try {
                    startPos = Integer.parseInt(genOffsetStartField.getText().trim());
                    if (startPos > 0)
                        startPos--; // 1-based to 0-based
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidStartPosition"), "genOffsetStartField");
                    return;
                }
            }

            if (genOffsetLengthField != null && !genOffsetLengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(genOffsetLengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError(t("module.payments.error.inputTitle"), t("module.payments.error.invalidLength"), "genOffsetLengthField");
                    return;
                }
            }

            if (genOffsetPadField != null && !genOffsetPadField.getText().trim().isEmpty()) {
                padChar = genOffsetPadField.getText().trim().substring(0, 1);
            }

            // Generate Offset directly
            String offset = com.cryptocarver.pin.Pin.generateIbm3624Offset(
                    pvkBytes,
                    decTable,
                    pin,
                    pan,
                    startPos,
                    length,
                    padChar);

            // Reconstruct Validation Data Block for display
            String rawVd = "";
            try {
                if (pan.length() >= startPos + length) {
                    rawVd = pan.substring(startPos, startPos + length);
                } else {
                    rawVd = "Error: bounds";
                }
            } catch (Exception e) {
                rawVd = "Error";
            }

            String displayVd = rawVd;
            if (!rawVd.startsWith("Error")) {
                if (displayVd.length() > 16)
                    displayVd = displayVd.substring(0, 16);
                while (displayVd.length() < 16)
                    displayVd += padChar;
            }
            int displayStart = startPos + 1;

            StringBuilder res = new StringBuilder();
            res.append(t("module.payments.result.generatedOffset")).append("\n").append(offset).append("\n\n");
            res.append(t("module.payments.result.forPin")).append(" ").append(pin).append("\n");
            res.append(t("module.payments.result.validationConfig", displayStart, length, padChar)).append("\n");
            res.append(t("module.payments.result.validationDataBlock", "")).append(" ").append(displayVd.toUpperCase());

            genOffsetResultArea.setText(res.toString());
            genOffsetResultArea.setManaged(true);
            genOffsetResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624 offset");
            details.put("PAN", PanMask.mask(pan));
            details.put("PIN", "[not persisted]");
            reporter().publish(OperationResult.forOperation("Generate Offset")
                    .output(offset.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.generationTitle"), t("module.payments.error.operation", "Offset", e.getMessage()));
        }
    }

    void handleGeneratePVVUtility() {
        try {
            if (genPvvPvkField == null || genPvvResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "PVV generator"));
                return;
            }

            String pvk = genPvvPvkField.getText().trim();
            String pan = genPvvPanField.getText().trim();
            String pin = genPvvPinField.getText().trim();
            String keyIndex = genPvvKeyIndexField != null ? genPvvKeyIndexField.getText().trim() : "0";
            if (keyIndex.isEmpty())
                keyIndex = "0";

            if (pvk.isEmpty() || pan.isEmpty() || pin.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvkPanPin"), "genPvvPvkField");
                return;
            }

            String pvv = PaymentOperations.generatePVV(pin, pan, pvk, keyIndex, 4);

            StringBuilder res = new StringBuilder();
            res.append(t("module.payments.result.generatedPvv")).append(" ").append(pvv).append("\n\n");
            res.append(t("module.payments.result.keyIndex")).append(" ").append(keyIndex).append("\n");

            genPvvResultArea.setText(res.toString());
            genPvvResultArea.setManaged(true);
            genPvvResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "VISA PVV");
            details.put("PAN", PanMask.mask(pan));
            details.put("Key Index", keyIndex);
            details.put("PIN", "[not persisted]");
            reporter().publish(OperationResult.forOperation("Generate PVV")
                    .output(pvv.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.generationTitle"), t("module.payments.error.operation", "PVV", e.getMessage()));
        }
    }

    void handleDerivePinFromPvvUtility() {
        try {
            if (derivePvvPvkField == null || derivePvvResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "PVV derivation"));
                return;
            }

            String pvk = derivePvvPvkField.getText().trim();
            String pan = derivePvvPanField.getText().trim();
            String targetPvv = derivePvvTargetPvvField.getText().trim();
            String keyIndex = derivePvvKeyIndexField != null ? derivePvvKeyIndexField.getText().trim() : "0";
            if (keyIndex.isEmpty())
                keyIndex = "0";

            if (pvk.isEmpty() || pan.isEmpty() || targetPvv.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.pvvTargetRequired"), "derivePvvTargetPvvField");
                return;
            }

            java.util.List<String> matches = PaymentOperations.derivePinFromPvv(pan, pvk, keyIndex, targetPvv, 4);

            StringBuilder res = new StringBuilder();
            res.append("Derive PIN Results:\n");
            res.append("-------------------\n");
            res.append("PVK: ").append(pvk).append("\n");
            res.append(t("module.payments.result.pan")).append(" ").append(pan).append("\n");
            res.append(t("module.payments.result.targetPvv")).append(" ").append(targetPvv).append("\n");
            res.append(t("module.payments.result.pvki")).append(" ").append(keyIndex).append("\n\n");

            if (matches.isEmpty()) {
                res.append(t("module.payments.result.noPinsFound"));
            } else {
                res.append(t("module.payments.result.foundMatches", matches.size())).append("\n\n");
                for (String pin : matches) {
                    res.append("  • ").append(t("module.payments.result.pin")).append(" ").append(pin).append("\n");
                }
            }

            derivePvvResultArea.setText(res.toString());
            derivePvvResultArea.setManaged(true);
            derivePvvResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "Derive PIN from PVV");
            details.put("PAN", PanMask.mask(pan));
            details.put("PVV", targetPvv);
            details.put("Matches", String.valueOf(matches.size()));
            details.put("PINs", "[not persisted]");
            reporter().publish(OperationResult.forOperation("Derive PIN from PVV")
                    // Only the candidate PINs: the on-screen report also echoes the PVK.
                    .output(String.join(", ", matches).getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.derivationTitle"), t("module.payments.error.operation", "PVV", e.getMessage()));
        }
    }
}
