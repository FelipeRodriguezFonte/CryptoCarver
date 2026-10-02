package com.cryptocarver.ui;

import com.cryptocarver.crypto.PaymentOperations;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * The PIN block panels of the payments screen: encoding and decoding clear PIN blocks in every
 * supported format (with the padding choices each one allows) and protecting them with TDES, or
 * AES for ISO format 4. PANs are masked in published details and PINs are never persisted.
 */
final class PinBlockCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(PinBlockCoordinator.class);

    /** The panels' controls, injected into PaymentsController from payments.fxml. */
    record View(TextField pin,
            TextField panEncode,
            TextField pinBlock,
            TextField panDecode,
            ComboBox<String> format,
            ComboBox<String> formatDecode,
            ComboBox<String> padding,
            Label paddingLabel,
            TextArea result,
            ComboBox<String> encFormat,
            TextField encPin,
            TextField encPanEncode,
            TextField encKey,
            TextField encPinBlockDecode,
            TextField encPanDecode,
            TextField encKeyDecode,
            TextArea encResult) {
    }

    private final TextField pinField;
    private final TextField panFieldEncode;
    private final TextField pinBlockField;
    private final TextField panFieldDecode;
    private final ComboBox<String> pinBlockFormatCombo;
    private final ComboBox<String> pinBlockFormatDecodeCombo;
    private final ComboBox<String> pinBlockPaddingCombo;
    private final Label pinBlockPaddingLabel;
    private final TextArea pinBlockResultArea;
    private final ComboBox<String> encPinBlockFormatCombo;
    private final TextField encPinField;
    private final TextField encPanFieldEncode;
    private final TextField encPinBlockKeyField;
    private final TextField encPinBlockFieldDecode;
    private final TextField encPanFieldDecode;
    private final TextField encPinBlockKeyFieldDecode;
    private final TextArea encResultArea;
    private final Supplier<StatusReporter> reporter;

    PinBlockCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.pinField = view.pin();
        this.panFieldEncode = view.panEncode();
        this.pinBlockField = view.pinBlock();
        this.panFieldDecode = view.panDecode();
        this.pinBlockFormatCombo = view.format();
        this.pinBlockFormatDecodeCombo = view.formatDecode();
        this.pinBlockPaddingCombo = view.padding();
        this.pinBlockPaddingLabel = view.paddingLabel();
        this.pinBlockResultArea = view.result();
        this.encPinBlockFormatCombo = view.encFormat();
        this.encPinField = view.encPin();
        this.encPanFieldEncode = view.encPanEncode();
        this.encPinBlockKeyField = view.encKey();
        this.encPinBlockFieldDecode = view.encPinBlockDecode();
        this.encPanFieldDecode = view.encPanDecode();
        this.encPinBlockKeyFieldDecode = view.encKeyDecode();
        this.encResultArea = view.encResult();
        this.reporter = reporter;
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

    private void showError(String title, String message) {
        showError(title, message, null);
    }

    private void showError(String title, String message, String fieldKey) {
        if (reporter() != null) {
            String safeMessage = InlineErrorPresenter.redactSecrets(message);
            reporter().showError(new UserFacingError(title, safeMessage, safeMessage, fieldKey));
        }
    }

    private void setupPaddingSelection() {
        if (pinBlockPaddingCombo == null || pinBlockFormatCombo == null) return;
        if (pinBlockPaddingLabel != null) pinBlockPaddingLabel.setText(t("module.payments.padding"));
        pinBlockPaddingCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String value) {
                if (value == null) return "";
                if (value.equals(com.cryptocarver.crypto.PinBlockPadding.RANDOM_HEX))
                    return t("module.payments.padding.randomHex");
                if (value.equals(com.cryptocarver.crypto.PinBlockPadding.RANDOM_DECIMAL))
                    return t("module.payments.padding.randomDecimal");
                return value;
            }
            @Override public String fromString(String value) { return value; }
        });
        pinBlockFormatCombo.valueProperty().addListener((obs, oldValue, newValue) -> updatePaddingSelection());
        updatePaddingSelection();
    }

    private void updatePaddingSelection() {
        if (pinBlockPaddingCombo == null || pinBlockFormatCombo.getValue() == null) return;
        com.cryptocarver.crypto.PinBlockFormat format =
                com.cryptocarver.crypto.PinBlockFormat.fromName(pinBlockFormatCombo.getValue());
        pinBlockPaddingCombo.getItems().setAll(format.paddingOptions());
        pinBlockPaddingCombo.setDisable(format.paddingOptions().isEmpty());
        pinBlockPaddingCombo.setValue(format.defaultPadding());
    }

    void setupPinBlockFormats() {
        if (pinBlockFormatCombo == null || pinBlockFormatDecodeCombo == null) {
            return; // Safety check
        }
        pinBlockFormatCombo.getItems().addAll(com.cryptocarver.crypto.PinBlockFormat.displayNames());
        pinBlockFormatCombo.getSelectionModel().selectFirst();

        pinBlockFormatDecodeCombo.getItems().addAll(pinBlockFormatCombo.getItems());
        pinBlockFormatDecodeCombo.getSelectionModel().selectFirst();
        setupPaddingSelection();
    }

    // ==================== PIN BLOCK HANDLERS ====================

    void handleEncodePinBlock() {
        try {
            String pin = pinField.getText().trim();
            String pan = panFieldEncode.getText().trim().replaceAll("\\s+", "");
            String format = pinBlockFormatCombo.getSelectionModel().getSelectedItem();
            com.cryptocarver.crypto.PinBlockFormat selectedFormat = com.cryptocarver.crypto.PinBlockFormat.fromName(format);

            // Validate inputs
            if (pin.isEmpty() || (selectedFormat.usesPan() && pan.isEmpty())) {
                pinBlockResultArea.setText(t("module.payments.error.pinPanRequired"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            if (!pin.matches("\\d{4,12}")) {
                pinBlockResultArea.setText(t("module.payments.error.pinLength"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            if (!pan.isEmpty() && !pan.matches("\\d{13,19}")) {
                pinBlockResultArea.setText(t("module.payments.error.panInvalid"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // For ISO-4, use special method that returns both clear field and PIN block
            String clearPinField = null;
            String clearPanBlock = null;
            String pinBlock;

            boolean isISO4 = format.contains("ISO 4") || format.contains("ISO-4");

            if (isISO4) {
                // Format 4 has no PIN block in clear: without the AES key the two
                // fields are the result, and the PIN field is what gets enciphered.
                String[] iso4Result = PaymentOperations.encodePinBlockISO4WithClear(pin, pan);
                clearPinField = iso4Result[0];
                clearPanBlock = iso4Result[1];
                pinBlock = clearPinField;
            } else {
                pinBlock = pinBlockPaddingCombo == null || pinBlockPaddingCombo.isDisabled()
                        ? PaymentOperations.encodePinBlock(pin, pan, format)
                        : PaymentOperations.encodePinBlock(pin, pan, format, pinBlockPaddingCombo.getValue());
            }

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append(t("module.payments.result.pinBlockEncodingTitle")).append("\n");
            result.append("========================================\n\n");
            result.append(t("module.payments.result.format")).append("    ").append(format).append("\n");
            result.append(t("module.payments.result.pin")).append("       ").append(pin).append(" (").append(t("module.payments.result.pinLength", pin.length())).append(")\n");
            result.append(t("module.payments.result.pan")).append("       ").append(pan).append("\n\n");

            // For ISO-4, show both clear blocks
            if (isISO4) {
                result.append(t("module.payments.result.pinBlockClear")).append(" ").append(clearPinField).append("\n");
                result.append(t("module.payments.result.panBlockClear")).append(" ").append(clearPanBlock).append("\n");
            } else {
                result.append(t("module.payments.result.pinBlock")).append(" ").append(pinBlock).append("\n");
            }
            result.append("========================================\n");

            pinBlockResultArea.setText(result.toString());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", PanMask.mask(pan));
            details.put("PIN Length", pin.length() + " digits");
            reporter().publish(OperationResult.forOperation("Encode PIN Block")
                    .output(DataConverter.hexToBytes(pinBlock)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            pinBlockResultArea.setText(t("module.payments.error.operation", t("module.payments.result.pinBlockEncodingTitle"), e.getMessage()));
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            updateStatus(t("module.payments.error.operation", t("module.payments.result.pinBlockEncodingTitle"), e.getMessage()));
        }
    }

    void handleDecodePinBlock() {
        try {
            String pinBlock = pinBlockField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldDecode.getText().trim().replaceAll("\\s+", "");
            String format = pinBlockFormatDecodeCombo.getSelectionModel().getSelectedItem();
            com.cryptocarver.crypto.PinBlockFormat selectedFormat = com.cryptocarver.crypto.PinBlockFormat.fromName(format);

            // Validate inputs
            if (pinBlock.isEmpty() || (selectedFormat.usesPan() && pan.isEmpty())) {
                pinBlockResultArea.setText(t("module.payments.error.pinPanRequired"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Validate PIN block length based on format
            boolean isISO4 = selectedFormat == com.cryptocarver.crypto.PinBlockFormat.ISO4;
            int expectedLength = isISO4 ? 32 : 16;

            if (!pinBlock.matches("[0-9A-Fa-f]{" + expectedLength + "}")) {
                pinBlockResultArea.setText(t("module.payments.error.pinBlockInvalid",
                        expectedLength, format, pinBlock.length()));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Validate PAN format (13-19 digits OR 32 hex chars for ISO-4 block)
            boolean isValidPan = pan.matches("\\d{13,19}");
            boolean isValidIso4PanBlock = isISO4 && pan.matches("[0-9A-Fa-f]{32}");

            if ((selectedFormat.usesPan() || !pan.isEmpty()) && !isValidPan && !isValidIso4PanBlock) {
                pinBlockResultArea.setText(t("module.payments.error.panInvalid"));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Decode PIN block
            String pin = PaymentOperations.decodePinBlock(pinBlock, pan, format);

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append(t("module.payments.result.pinBlockDecodingTitle")).append("\n");
            result.append("========================================\n\n");
            result.append(t("module.payments.result.format")).append("     ").append(format).append("\n");
            result.append(t("module.payments.result.pinBlock")).append("  ").append(pinBlock.toUpperCase()).append("\n");
            result.append(t("module.payments.result.pan")).append("        ").append(pan).append("\n\n");
            result.append(t("module.payments.result.decodedPin")).append(" ").append(pin).append(" (").append(t("module.payments.result.pinLength", pin.length())).append(")\n");
            result.append("========================================\n");

            pinBlockResultArea.setText(result.toString());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", PanMask.mask(pan));
            details.put("PIN Length", pin.length() + " digits");
            reporter().publish(OperationResult.forOperation("Decode PIN Block")
                    .input(DataConverter.hexToBytes(pinBlock))
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            pinBlockResultArea.setText(t("module.payments.error.operation", t("module.payments.result.pinBlockDecodingTitle"), e.getMessage()));
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            updateStatus(t("module.payments.error.operation", t("module.payments.result.pinBlockDecodingTitle"), e.getMessage()));
        }
    }

    // ============================================================
    // ENCRYPTED PIN BLOCK OPERATIONS (Generic)
    // ============================================================

    private static boolean isIso4(String format) {
        return format != null && (format.contains("ISO-4") || format.contains("ISO 4"));
    }

    void handleEncodeEncryptedPinBlock() {
        try {
            if (encPinField == null || encPanFieldEncode == null || encResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "Encrypted PIN"));
                return;
            }

            String pin = encPinField.getText().trim();
            String pan = encPanFieldEncode.getText().trim().replaceAll("\\s+", "");
            String keyHex = encPinBlockKeyField != null ? encPinBlockKeyField.getText().trim().replaceAll("\\s+", "")
                    : "";
            String format = encPinBlockFormatCombo.getSelectionModel().getSelectedItem();

            if (pin.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.enterValue", "PIN"), "encPinField");
                return;
            }
            // Some formats might not need PAN, but mostly they do for XOR or binding
            if (pan.isEmpty() && com.cryptocarver.crypto.PinBlockFormat.fromName(format).usesPan()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.enterPanForFormat", format), "encPanFieldEncode");
                return;
            }

            // 1. Create clear PIN block using PaymentOperations (which supports all
            // formats)
            String clearPinBlock = PaymentOperations.encodePinBlock(pin, pan, format);
            String publishedBlock = clearPinBlock;

            String result = t("module.payments.result.format") + " " + format + "\n";
            result += t("module.payments.result.clearPinBlock") + "\n" + clearPinBlock;

            // 2. Encrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    if (isIso4(format)) {
                        // Format 4 is AES and binds the PAN between two encryptions.
                        publishedBlock = PaymentOperations.encipherPinBlockISO4(key, pin, pan);
                    } else {
                        byte[] clearBytes = DataConverter.hexToBytes(clearPinBlock);
                        byte[] encrypted = PaymentOperations.encryptDesEcb(clearBytes, key);
                        publishedBlock = DataConverter.bytesToHex(encrypted).toUpperCase();
                    }
                    result += "\n\n" + t("module.payments.result.encryptedPinBlock") + "\n" + publishedBlock;
                } catch (Exception e) {
                    // Never publish the clear block as if it had been protected.
                    showError(t("module.payments.error.encodingTitle"), t("module.payments.error.operation",
                            t("module.payments.operation.encryptedPinBlock"), e.getMessage()), "encPinBlockKeyField");
                    return;
                }
            }

            encResultArea.setText(result);
            encResultArea.setManaged(true);
            encResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", PanMask.mask(pan));
            details.put("PIN", "[not persisted]");
            details.put("Protected", keyHex.isEmpty() ? "No key supplied" : isIso4(format) ? "AES (ISO 9564-1 format 4)" : "TDES ECB");
            reporter().publish(OperationResult.forOperation("Encode Encrypted PIN Block")
                    .output(DataConverter.hexToBytes(publishedBlock)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.encodingTitle"), t("module.payments.error.operation", t("module.payments.result.encryptedPinBlock"), e.getMessage()));
            LOG.error("Encrypted PIN block encoding failed", e);
        }
    }

    void handleDecodeEncryptedPinBlock() {
        try {
            if (encPinBlockFieldDecode == null || encPanFieldDecode == null || encResultArea == null) {
                showError(t("module.payments.error.configurationTitle"), t("module.payments.error.controlsNotInitialized", "Encrypted PIN decode"));
                return;
            }

            String pinBlockHex = encPinBlockFieldDecode.getText().trim().replaceAll("\\s+", "");
            String pan = encPanFieldDecode.getText().trim().replaceAll("\\s+", "");
            String keyHex = encPinBlockKeyFieldDecode != null
                    ? encPinBlockKeyFieldDecode.getText().trim().replaceAll("\\s+", "")
                    : "";
            String format = encPinBlockFormatCombo.getSelectionModel().getSelectedItem();

            if (pinBlockHex.isEmpty()) {
                showError(t("module.payments.error.inputTitle"), t("module.payments.error.enterValue", "PIN Block"), "encPinBlockFieldDecode");
                return;
            }

            String clearPinBlockHex = pinBlockHex;

            if (isIso4(format) && !keyHex.isEmpty()) {
                String pin;
                try {
                    pin = PaymentOperations.decipherPinBlockISO4(DataConverter.hexToBytes(keyHex), pinBlockHex, pan);
                } catch (Exception e) {
                    showError(t("module.payments.error.decryptionTitle"), t("module.payments.error.operation", t("module.payments.operation.encryptedPinBlock"), e.getMessage()));
                    return;
                }
                encResultArea.setText(t("module.payments.result.format") + " " + format + "\n\n"
                        + t("module.payments.result.decodedPinLine") + " " + pin);
                encResultArea.setManaged(true);
                encResultArea.setVisible(true);
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                details.put("Format", format);
                details.put("PAN", PanMask.mask(pan));
                details.put("PIN", "[not persisted]");
                details.put("Protected", "AES (ISO 9564-1 format 4)");
                reporter().publish(OperationResult.forOperation("Decode Encrypted PIN Block")
                        .input(DataConverter.hexToBytes(pinBlockHex))
                        .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                        .status(t("module.payments.status.success")).build());
                return;
            }

            // 1. Decrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    byte[] encrypted = DataConverter.hexToBytes(pinBlockHex);

                    // Decrypt with TDES
                    byte[] decrypted = PaymentOperations.decryptDesEcb(encrypted, key);
                    clearPinBlockHex = DataConverter.bytesToHex(decrypted).toUpperCase();
                } catch (Exception e) {
                    showError(t("module.payments.error.decryptionTitle"), t("module.payments.error.operation", t("module.payments.operation.encryptedPinBlock"), e.getMessage()));
                    return;
                }
            }

            // 2. Decode PIN block using PaymentOperations
            String pin = PaymentOperations.decodePinBlock(clearPinBlockHex, pan, format);

            String result = t("module.payments.result.format") + " " + format + "\n";
            result += t("module.payments.result.clearPinBlock") + " " + clearPinBlockHex + "\n\n" + t("module.payments.result.decodedPinLine") + " " + pin;

            encResultArea.setText(result);
            encResultArea.setManaged(true);
            encResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", PanMask.mask(pan));
            details.put("PIN", "[not persisted]");
            details.put("Protected", keyHex.isEmpty() ? "No key supplied" : "TDES ECB");
            reporter().publish(OperationResult.forOperation("Decode Encrypted PIN Block")
                    .input(DataConverter.hexToBytes(pinBlockHex))
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status(t("module.payments.status.success")).build());

        } catch (Exception e) {
            showError(t("module.payments.error.decodingTitle"), t("module.payments.error.operation", t("module.payments.operation.encryptedPinBlock"), e.getMessage()));
            LOG.error("Encrypted PIN block decoding failed", e);
        }
    }
}
