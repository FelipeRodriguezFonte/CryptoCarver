package com.cryptoforge.ui;

import com.cryptoforge.crypto.PaymentOperations;
import com.cryptoforge.crypto.DukptKsn;
import com.cryptoforge.crypto.AesDukpt;
import com.cryptoforge.model.OperationResult;
import com.cryptoforge.util.DataConverter;
import com.cryptoforge.utils.OperationHistory;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for Payments tab
 */
public class PaymentsController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsController.class);

    private StatusReporter mainController;

    // Helper methods to call methods on MainController or ModernMainController
    private void updateStatus(String message) {
        if (mainController != null) mainController.updateStatus(message);
    }

    private void showError(String title, String message) {
        if (mainController != null) mainController.showError(title, message);
    }

    // PIN Block controls
    private TextField pinField;
    private TextField panFieldEncode;
    private TextField pinBlockField;
    private TextField panFieldDecode;
    private ComboBox<String> pinBlockFormatCombo;
    private ComboBox<String> pinBlockFormatDecodeCombo;
    private TextArea pinBlockResultArea;

    // CVV controls
    private TextField cvkAField;
    private TextField cvkBField;
    private TextField panFieldCvv;
    private TextField expiryDateField;
    private TextField serviceCodeField;
    private TextField atcField; // Added for dCVV
    private ComboBox<String> cvvTypeCombo;
    private TextArea cvvResultArea;

    // MAC controls
    private ComboBox<String> macAlgorithmCombo;
    private TextField macKeyField;
    private TextArea macDataField;
    private TextArea macResultArea;

    // PIN Controller for advanced operations
    private PinController pinController;

    // Additional PIN fields for Encrypted PIN Blocks (Generic)
    private ComboBox<String> encPinBlockFormatCombo;
    private TextField encPinField;
    private TextField encPanFieldEncode;
    private TextField encPinBlockKeyField;
    private TextField encPinBlockFieldDecode;
    private TextField encPanFieldDecode;
    private TextField encPinBlockKeyFieldDecode;
    private TextArea encResultArea;

    private TextField ibm3624PvkField;
    private TextField ibm3624ConvTableField;
    private TextField ibm3624OffsetField;
    private TextField ibm3624PanField;
    private TextField ibm3624PinVerifyField;
    private TextArea ibm3624ResultArea;
    private TextField ibm3624StartField;
    private TextField ibm3624LengthField;
    private TextField ibm3624PadField;

    // PIN Generators (Offset & PVV)
    private TextField genOffsetPvkField;
    private TextField genOffsetDecTableField;
    private TextField genOffsetPanField;
    private TextField genOffsetPinField;
    private TextArea genOffsetResultArea;
    private TextField genOffsetStartField;
    private TextField genOffsetLengthField;
    private TextField genOffsetPadField;

    private TextField genPvvPvkField;
    private TextField genPvvPanField;
    private TextField genPvvPinField;
    private TextField genPvvKeyIndexField;
    private TextArea genPvvResultArea;

    // Derive PIN from PVV (VISA)
    private TextField derivePvvPvkField;
    private TextField derivePvvPanField;
    private TextField derivePvvTargetPvvField;
    private TextField derivePvvKeyIndexField;
    private TextArea derivePvvResultArea;
    private TextField dukptBdkField, dukptKsnField, dukptAesPinBlockField;
    private TextArea dukptResultArea;
    private ComboBox<String> dukptSchemeCombo, dukptAesUsageCombo, dukptAesKeyTypeCombo, dukptAesPinOperationCombo;

    public void initializeDukptControls(TextField bdkField, TextField ksnField, TextArea resultArea,
            ComboBox<String> schemeCombo, ComboBox<String> aesUsageCombo, ComboBox<String> aesKeyTypeCombo,
            TextField aesPinBlockField, ComboBox<String> aesPinOperationCombo) {
        this.dukptBdkField = bdkField; this.dukptKsnField = ksnField; this.dukptResultArea = resultArea;
        this.dukptSchemeCombo = schemeCombo; this.dukptAesUsageCombo = aesUsageCombo; this.dukptAesKeyTypeCombo = aesKeyTypeCombo;
        this.dukptAesPinBlockField = aesPinBlockField; this.dukptAesPinOperationCombo = aesPinOperationCombo;
        if (schemeCombo != null) { schemeCombo.getItems().setAll("TDES (legacy, 10-byte KSN)", "AES (X9.24-3, 12-byte KSN)"); schemeCombo.setValue("TDES (legacy, 10-byte KSN)"); }
        if (aesUsageCombo != null) { aesUsageCombo.getItems().setAll("Data encryption (encrypt)", "Data encryption (decrypt)", "PIN encryption", "MAC generation", "MAC verification", "MAC both ways", "Key encryption", "Key derivation"); aesUsageCombo.setValue("Data encryption (encrypt)"); }
        if (aesKeyTypeCombo != null) { aesKeyTypeCombo.getItems().setAll("AES-128", "AES-192", "AES-256"); aesKeyTypeCombo.setValue("AES-128"); }
        if (aesPinOperationCombo != null) { aesPinOperationCombo.getItems().setAll("Encrypt formatted PIN block", "Decrypt encrypted PIN block"); aesPinOperationCombo.setValue("Encrypt formatted PIN block"); }
    }

    public void handleInspectDukpt() {
        try {
            if (dukptSchemeCombo != null && dukptSchemeCombo.getValue().startsWith("AES")) { inspectAesDukpt(); return; }
            DukptKsn.Parsed ksn = DukptKsn.parseTdes(dukptKsnField.getText());
            String result = "--- DUKPT TDES KSN ---\nKSN: " + ksn.ksnHex() + "\nBase KSN: " + ksn.baseKsnHex()
                    + "\nDevice ID: " + ksn.deviceIdentifierHex() + "\nTransaction counter: " + ksn.transactionCounter()
                    + "\nCounter exhausted: " + DukptKsn.isTdesCounterExhausted(ksn.ksnHex())
                    + "\nNext KSN: " + (DukptKsn.isTdesCounterExhausted(ksn.ksnHex()) ? "not available" : DukptKsn.nextTdesKsn(ksn.ksnHex()));
            if (!dukptBdkField.getText().isBlank()) result += "\nIPEK: " + DukptKsn.deriveIpek(dukptBdkField.getText(), ksn.ksnHex());
            dukptResultArea.setText(result); dukptResultArea.setManaged(true); dukptResultArea.setVisible(true);
            updateStatus("DUKPT KSN inspected");
        } catch (Exception e) { showError("DUKPT", e.getMessage()); }
    }

    private void inspectAesDukpt() throws Exception {
        AesDukpt.ParsedKsn ksn = AesDukpt.parseKsn(dukptKsnField.getText());
        String result = "--- AES DUKPT (ANSI X9.24-3) ---\nKSN: " + ksn.ksnHex() + "\nInitial Key ID: " + ksn.initialKeyIdHex()
                + "\nBase KSN: " + ksn.baseKsnHex() + "\nTransaction counter: " + String.format("%08X", ksn.transactionCounter())
                + "\nCounter exhausted: " + AesDukpt.isCounterExhausted(ksn.ksnHex())
                + "\nNext KSN: " + (AesDukpt.isCounterExhausted(ksn.ksnHex()) ? "not available" : AesDukpt.nextKsn(ksn.ksnHex()));
        if (!dukptBdkField.getText().isBlank()) {
            AesDukpt.KeyUsage usage = selectedAesUsage(); AesDukpt.KeyType type = selectedAesKeyType();
            AesDukpt.DerivedKey derived = AesDukpt.deriveWorkingKey(dukptBdkField.getText(), ksn.ksnHex(), usage, type);
            result += "\n\nBDK type: " + AesDukpt.KeyType.fromBytes(dukptBdkField.getText().replaceAll("\\s+", "").length() / 2)
                    + "\nInitial Key: " + derived.initialKeyHex() + "\nIntermediate key: " + derived.intermediateKeyHex()
                    + "\nWorking-key usage: " + usage.label() + "\nWorking-key derivation data: " + derived.derivationDataHex()
                    + "\nDerived " + type + " working key: " + derived.workingKeyHex();
        }
        dukptResultArea.setText(result); dukptResultArea.setManaged(true); dukptResultArea.setVisible(true); updateStatus("AES DUKPT KSN inspected");
    }
    private AesDukpt.KeyUsage selectedAesUsage() {
        String selection = dukptAesUsageCombo == null ? "Data encryption (encrypt)" : dukptAesUsageCombo.getValue();
        return switch (selection) {
            case "Data encryption (decrypt)" -> AesDukpt.KeyUsage.DATA_ENCRYPTION_DECRYPT;
            case "PIN encryption" -> AesDukpt.KeyUsage.PIN_ENCRYPTION;
            case "MAC generation" -> AesDukpt.KeyUsage.MAC_GENERATION;
            case "MAC verification" -> AesDukpt.KeyUsage.MAC_VERIFICATION;
            case "MAC both ways" -> AesDukpt.KeyUsage.MAC_BOTH_WAYS;
            case "Key encryption" -> AesDukpt.KeyUsage.KEY_ENCRYPTION;
            case "Key derivation" -> AesDukpt.KeyUsage.KEY_DERIVATION;
            default -> AesDukpt.KeyUsage.DATA_ENCRYPTION_ENCRYPT;
        };
    }
    private AesDukpt.KeyType selectedAesKeyType() {
        String selection = dukptAesKeyTypeCombo == null ? "AES-128" : dukptAesKeyTypeCombo.getValue();
        return "AES-192".equals(selection) ? AesDukpt.KeyType.AES192 : "AES-256".equals(selection) ? AesDukpt.KeyType.AES256 : AesDukpt.KeyType.AES128;
    }

    public void handleAesDukptPinBlock() {
        try {
            if (dukptSchemeCombo == null || !dukptSchemeCombo.getValue().startsWith("AES")) {
                throw new IllegalArgumentException("Select AES (X9.24-3, 12-byte KSN) before processing an AES PIN block");
            }
            if (dukptBdkField == null || dukptBdkField.getText().isBlank()) throw new IllegalArgumentException("An AES DUKPT BDK is required");
            boolean decrypt = dukptAesPinOperationCombo != null && dukptAesPinOperationCombo.getValue().startsWith("Decrypt");
            AesDukpt.KeyType type = selectedAesKeyType();
            AesDukpt.DerivedKey derived = AesDukpt.deriveWorkingKey(dukptBdkField.getText(), dukptKsnField.getText(), AesDukpt.KeyUsage.PIN_ENCRYPTION, type);
            String output = AesDukpt.cryptPinBlock(dukptBdkField.getText(), dukptKsnField.getText(), type, dukptAesPinBlockField.getText(), decrypt);
            dukptResultArea.setText("--- AES DUKPT PIN block (lab operation) ---\nKSN: " + AesDukpt.parseKsn(dukptKsnField.getText()).ksnHex()
                    + "\nPIN key type: " + type + "\nDerived PIN key: " + derived.workingKeyHex()
                    + "\nInput " + (decrypt ? "encrypted" : "formatted") + " PIN block: " + dukptAesPinBlockField.getText().replaceAll("\\s+", "").toUpperCase()
                    + "\nOutput " + (decrypt ? "formatted" : "encrypted") + " PIN block: " + output
                    + "\n\nNote: this uses AES/ECB/NoPadding over a 16-byte preformatted ISO 9564-4 PIN block.");
            updateStatus("AES DUKPT PIN block " + (decrypt ? "decrypted" : "encrypted"));
        } catch (Exception e) { showError("AES DUKPT PIN block", e.getMessage()); }
    }

    public void initialize(StatusReporter mainController,
            TextField pinField,
            TextField panFieldEncode,
            TextField pinBlockField,
            TextField panFieldDecode,
            ComboBox<String> pinBlockFormatCombo,
            ComboBox<String> pinBlockFormatDecodeCombo,
            TextArea pinBlockResultArea,
            TextField cvkAField,
            TextField cvkBField,
            TextField panFieldCvv,
            TextField expiryDateField,
            TextField serviceCodeField,
            TextField atcField,
            ComboBox<String> cvvTypeCombo,
            TextArea cvvResultArea,
            ComboBox<String> macAlgorithmCombo,
            TextField macKeyField,
            TextArea macDataField,
            TextArea macResultArea,
            // New Encrypted PIN Fields (Generic)
            ComboBox<String> encPinBlockFormatCombo,
            TextField encPinField,
            TextField encPanFieldEncode,
            TextField encPinBlockKeyField,
            TextField encPinBlockFieldDecode,
            TextField encPanFieldDecode,
            TextField encPinBlockKeyFieldDecode,
            TextArea encResultArea,
            // New PIN Generator Fields
            TextField genOffsetPvkField,
            TextField genOffsetDecTableField,
            TextField genOffsetPanField,
            TextField genOffsetPinField,
            TextArea genOffsetResultArea,
            // Offset Config
            TextField genOffsetStartField,
            TextField genOffsetLengthField,
            TextField genOffsetPadField,
            TextField genPvvPvkField,
            TextField genPvvPanField,
            TextField genPvvPinField,
            TextField genPvvKeyIndexField,
            TextArea genPvvResultArea,
            // Derive PIN from PVV Fields
            TextField derivePvvPvkField,
            TextField derivePvvPanField,
            TextField derivePvvTargetPvvField,
            TextField derivePvvKeyIndexField,
            TextArea derivePvvResultArea) {

        this.mainController = mainController;
        this.pinField = pinField;
        this.panFieldEncode = panFieldEncode;
        this.pinBlockField = pinBlockField;
        this.panFieldDecode = panFieldDecode;
        this.pinBlockFormatCombo = pinBlockFormatCombo;
        this.pinBlockFormatDecodeCombo = pinBlockFormatDecodeCombo;
        this.pinBlockResultArea = pinBlockResultArea;
        this.cvkAField = cvkAField;
        this.cvkBField = cvkBField;
        this.panFieldCvv = panFieldCvv;
        this.expiryDateField = expiryDateField;
        this.serviceCodeField = serviceCodeField;
        this.atcField = atcField;
        this.cvvTypeCombo = cvvTypeCombo;
        this.cvvResultArea = cvvResultArea;
        this.macAlgorithmCombo = macAlgorithmCombo;
        this.macKeyField = macKeyField;
        this.macDataField = macDataField;
        this.macResultArea = macResultArea;

        // Assign generic Encrypted PIN fields
        this.encPinBlockFormatCombo = encPinBlockFormatCombo;
        this.encPinField = encPinField;
        this.encPanFieldEncode = encPanFieldEncode;
        this.encPinBlockKeyField = encPinBlockKeyField;
        this.encPinBlockFieldDecode = encPinBlockFieldDecode;
        this.encPanFieldDecode = encPanFieldDecode;
        this.encPinBlockKeyFieldDecode = encPinBlockKeyFieldDecode;
        this.encResultArea = encResultArea;

        // Assign PIN Generator fields
        this.genOffsetPvkField = genOffsetPvkField;
        this.genOffsetDecTableField = genOffsetDecTableField;
        this.genOffsetPanField = genOffsetPanField;
        this.genOffsetPinField = genOffsetPinField;
        this.genOffsetResultArea = genOffsetResultArea;
        this.genOffsetStartField = genOffsetStartField;
        this.genOffsetLengthField = genOffsetLengthField;
        this.genOffsetPadField = genOffsetPadField;
        this.genPvvPvkField = genPvvPvkField;
        this.genPvvPanField = genPvvPanField;
        this.genPvvPinField = genPvvPinField;
        this.genPvvKeyIndexField = genPvvKeyIndexField;
        this.genPvvResultArea = genPvvResultArea;

        // Derive PIN Fields
        this.derivePvvPvkField = derivePvvPvkField;
        this.derivePvvPanField = derivePvvPanField;
        this.derivePvvTargetPvvField = derivePvvTargetPvvField;
        this.derivePvvKeyIndexField = derivePvvKeyIndexField;
        this.derivePvvResultArea = derivePvvResultArea;

        // Only setup controls that are available (not null)
        if (pinBlockFormatCombo != null && pinBlockFormatDecodeCombo != null) {
            setupPinBlockFormats();
        }
        if (cvvTypeCombo != null) {
            setupCvvTypes();
        }
        if (macAlgorithmCombo != null) {
            setupMacAlgorithms();
        }

        // Initialize Encrypted PIN Block Format Combo if available
        if (encPinBlockFormatCombo != null) {
            encPinBlockFormatCombo.getItems().addAll(
                    "Format 0 (ISO-0)",
                    "Format 1 (ISO-1)",
                    "Format 2 (ISO-2)",
                    "Format 3 (ISO-3)");
            encPinBlockFormatCombo.getSelectionModel().selectFirst();
        }
    }

    private void setupPinBlockFormats() {
        if (pinBlockFormatCombo == null || pinBlockFormatDecodeCombo == null) {
            return; // Safety check
        }
        pinBlockFormatCombo.getItems().addAll(
                "Format 0 (ISO-0)",
                "Format 1 (ISO-1)",
                "Format 2 (ISO-2)",
                "Format 3 (ISO-3)",
                "Format 4 (ISO-4)",
                "ANSI X9.8",
                "IBM 3624",
                "VISA-1");
        pinBlockFormatCombo.getSelectionModel().selectFirst();

        pinBlockFormatDecodeCombo.getItems().addAll(pinBlockFormatCombo.getItems());
        pinBlockFormatDecodeCombo.getSelectionModel().selectFirst();
    }

    private void setupCvvTypes() {
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

    private void setupMacAlgorithms() {
        if (macAlgorithmCombo == null) {
            return; // Safety check
        }
        macAlgorithmCombo.getItems().addAll(
                "Retail MAC (ISO 9797-1 Alg 3)",
                "CBC-MAC (ISO 9797-1 Alg 1)",
                "CMAC (ISO 9797-1 Alg 5)",
                "HMAC-SHA256",
                "AS2805.4 (1985)");
        macAlgorithmCombo.getSelectionModel().selectFirst();
    }

    // ==================== PIN BLOCK HANDLERS ====================

    public void handleEncodePinBlock() {
        try {
            String pin = pinField.getText().trim();
            String pan = panFieldEncode.getText().trim().replaceAll("\\s+", "");
            String format = pinBlockFormatCombo.getSelectionModel().getSelectedItem();

            // Validate inputs
            if (pin.isEmpty() || pan.isEmpty()) {
                pinBlockResultArea.setText("Error: PIN and PAN are required");
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            if (!pin.matches("\\d{4,12}")) {
                pinBlockResultArea.setText("Error: PIN must be 4-12 digits");
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            if (!pan.matches("\\d{13,19}")) {
                pinBlockResultArea.setText("Error: PAN must be 13-19 digits");
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
                String[] iso4Result = PaymentOperations.encodePinBlockISO4WithClear(pin, pan);
                clearPinField = iso4Result[0];
                pinBlock = iso4Result[1]; // XOR result (not shown in clear section)

                // Calculate PAN Block Clear for ISO-4
                // Structure: [M][PAN digits][PAD zeros][Trailing zeros]
                // M = PAN_length - 12
                // PAD zeros = pad to 19 digits total (PAN + PAD)
                // Trailing zeros = always 12 zeros
                StringBuilder panBlockBuilder = new StringBuilder();

                // M (1 nibble)
                int m = pan.length() - 12;
                panBlockBuilder.append(Integer.toHexString(m).toUpperCase());

                // PAN digits (all digits)
                panBlockBuilder.append(pan);

                // PAD zeros (to reach 19 digits total)
                int padZeros = 19 - pan.length();
                for (int i = 0; i < padZeros; i++) {
                    panBlockBuilder.append("0");
                }

                // Trailing zeros (always 12)
                panBlockBuilder.append("000000000000");

                clearPanBlock = panBlockBuilder.toString();
            } else {
                pinBlock = PaymentOperations.encodePinBlock(pin, pan, format);
            }

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("PIN BLOCK ENCODING\n");
            result.append("========================================\n\n");
            result.append("Format:    ").append(format).append("\n");
            result.append("PIN:       ").append(pin).append(" (").append(pin.length()).append(" digits)\n");
            result.append("PAN:       ").append(pan).append("\n\n");

            // For ISO-4, show both clear blocks
            if (isISO4) {
                result.append("PIN Block Clear: ").append(clearPinField).append("\n");
                result.append("PAN Block Clear: ").append(clearPanBlock).append("\n");
            } else {
                result.append("PIN Block: ").append(pinBlock).append("\n");
            }
            result.append("========================================\n");

            pinBlockResultArea.setText(result.toString());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN Length", pin.length() + " digits");
            mainController.publish(OperationResult.forOperation("Encode PIN Block")
                    .output(DataConverter.hexToBytes(pinBlock)).details(details)
                    .status("PIN Block encoded successfully").build());

        } catch (Exception e) {
            pinBlockResultArea.setText("Error encoding PIN block: " + e.getMessage());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            updateStatus("Error: " + e.getMessage());
        }
    }

    public void handleDecodePinBlock() {
        try {
            String pinBlock = pinBlockField.getText().trim().replaceAll("\\s+", "");
            String pan = panFieldDecode.getText().trim().replaceAll("\\s+", "");
            String format = pinBlockFormatDecodeCombo.getSelectionModel().getSelectedItem();

            // Validate inputs
            if (pinBlock.isEmpty() || pan.isEmpty()) {
                pinBlockResultArea.setText("Error: PIN Block and PAN are required");
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Validate PIN block length based on format
            boolean isISO4 = format != null && (format.contains("ISO-4") || format.contains("ISO 4"));
            int expectedLength = isISO4 ? 32 : 16;

            if (!pinBlock.matches("[0-9A-Fa-f]{" + expectedLength + "}")) {
                pinBlockResultArea.setText(String.format(
                        "Error: PIN Block must be %d hexadecimal characters for %s\n" +
                                "Current length: %d characters\n" +
                                "Note: ISO Format 4 uses 16-byte blocks (32 hex chars), other formats use 8-byte blocks (16 hex chars)",
                        expectedLength, format, pinBlock.length()));
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Validate PAN format (13-19 digits OR 32 hex chars for ISO-4 block)
            boolean isValidPan = pan.matches("\\d{13,19}");
            boolean isValidIso4PanBlock = isISO4 && pan.matches("[0-9A-Fa-f]{32}");

            if (!isValidPan && !isValidIso4PanBlock) {
                pinBlockResultArea.setText("Error: PAN must be 13-19 digits");
                pinBlockResultArea.setManaged(true);
                pinBlockResultArea.setVisible(true);
                return;
            }

            // Decode PIN block
            String pin = PaymentOperations.decodePinBlock(pinBlock, pan, format);

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("PIN BLOCK DECODING\n");
            result.append("========================================\n\n");
            result.append("Format:     ").append(format).append("\n");
            result.append("PIN Block:  ").append(pinBlock.toUpperCase()).append("\n");
            result.append("PAN:        ").append(pan).append("\n\n");
            result.append("Decoded PIN: ").append(pin).append(" (").append(pin.length()).append(" digits)\n");
            result.append("========================================\n");

            pinBlockResultArea.setText(result.toString());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN Length", pin.length() + " digits");
            mainController.publish(OperationResult.forOperation("Decode PIN Block")
                    .input(DataConverter.hexToBytes(pinBlock))
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("PIN Block decoded successfully").build());

        } catch (Exception e) {
            pinBlockResultArea.setText("Error decoding PIN block: " + e.getMessage());
            pinBlockResultArea.setManaged(true);
            pinBlockResultArea.setVisible(true);
            updateStatus("Error: " + e.getMessage());
        }
    }

    // ==================== CVV HANDLERS ====================

    public void handleGenerateCvv() {
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
                cvvResultArea.setText("Error: All fields are required (ATC optional for static CVV)");
                return;
            }

            if (!cvkA.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText("Error: CVK A must be 16 hexadecimal characters (8 bytes)");
                return;
            }

            if (!cvkB.matches("[0-9A-Fa-f]{16}")) {
                cvvResultArea.setText("Error: CVK B must be 16 hexadecimal characters (8 bytes)");
                return;
            }

            if (!pan.matches("\\d{13,19}")) {
                cvvResultArea.setText("Error: PAN must be 13-19 digits");
                return;
            }

            if (!expiry.matches("\\d{4}")) {
                cvvResultArea.setText("Error: Expiry date must be YYMM format (4 digits)");
                return;
            }

            if (!serviceCode.matches("\\d{3}")) {
                cvvResultArea.setText("Error: Service code must be 3 digits");
                return;
            }

            // Generate CVV
            String cvv;
            String serviceCodeForCalc = serviceCode;
            if (cvvType != null && cvvType.contains("dCVV")) {
                if (atc.isEmpty()) {
                    cvvResultArea.setText("Error: ATC is required for dCVV");
                    return;
                }
                // Use PAN Sequence Number "0" constant as determined by debug match
                cvv = PaymentOperations.generateDCVV(cvkA, cvkB, pan, "0", expiry, atc);
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
            result.append("═══ CVV GENERATION ═══\n\n");
            result.append("Type:         ").append(cvvType);
            if (cvvType != null && cvvType.contains("dCVV")) {
                result.append(" (Visa CVN 10)");
            }
            result.append("\n");

            result.append("CVK A:        ").append(cvkA.toUpperCase()).append("\n");
            result.append("CVK B:        ").append(cvkB.toUpperCase()).append("\n");
            result.append("PAN:          ").append(pan).append("\n");
            result.append("Expiry:       ").append(expiry).append("\n");
            result.append("Expiry:       ").append(expiry).append("\n");

            // Always show Service Code, but note usage
            result.append("Service Code: ").append(serviceCode);
            if (cvvType != null) {
                if (cvvType.contains("CVV2") || cvvType.contains("iCVV")) {
                    result.append(" (Forced to ").append(serviceCodeForCalc).append(" for calculation)");
                } else if (cvvType.contains("dCVV")) {
                    result.append(" (Not used for dCVV)");
                }
            }
            result.append("\n");

            if (!atc.isEmpty() || (cvvType != null && cvvType.contains("dCVV"))) {
                result.append("ATC:          ").append(atc)
                        .append(cvvType.contains("dCVV") ? " (Used for dCVV)" : " (Not used for static)\n");
            }
            result.append("\n");
            result.append("CVV:          ").append(cvv).append("\n");

            cvvResultArea.setText(result.toString());
            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Type", cvvType);
            details.put("PAN", maskPan(pan));
            details.put("Expiry", expiry);
            details.put("Service Code", serviceCode);
            mainController.publish(OperationResult.forOperation("Generate CVV")
                    .output(cvv.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("CVV generated successfully").build());

        } catch (Exception e) {
            cvvResultArea.setText("Error generating CVV: " + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    public void handleVerifyCvv() {
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
                cvvResultArea.setText("Error: Fill all fields to calculate the expected CVV for verification.");
                return;
            }

            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Verify CVV");
            dialog.setHeaderText("Enter CVV to verify:");
            dialog.setContentText("CVV:");

            java.util.Optional<String> outcome = dialog.showAndWait();
            if (outcome.isPresent()) {
                String inputCvv = outcome.get().trim();
                String atc = atcField.getText().trim();

                boolean isValid;
                String calculated;

                if (cvvTypeCombo.getSelectionModel().getSelectedItem() != null &&
                        cvvTypeCombo.getSelectionModel().getSelectedItem().contains("dCVV")) {

                    if (atc.isEmpty()) {
                        cvvResultArea.setText("Error: ATC is required for dCVV verification");
                        return;
                    }
                    isValid = PaymentOperations.verifyDCVV(cvkA, cvkB, pan, "0", expiry, atc, inputCvv);
                    calculated = PaymentOperations.generateDCVV(cvkA, cvkB, pan, "0", expiry, atc);

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
                result.append("═══ CVV VERIFICATION ═══\n\n");
                result.append("Input CVV:    ").append(inputCvv).append("\n");
                result.append("Calculated:   ").append(calculated).append("\n\n");
                result.append("Result:       ").append(isValid ? "✓ MATCH" : "✗ MISMATCH").append("\n");

                cvvResultArea.setText(result.toString());
                java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
                details.put("Type", cvvTypeCombo.getSelectionModel().getSelectedItem());
                details.put("PAN", maskPan(pan));
                details.put("Result", isValid ? "VALID" : "INVALID");
                mainController.publish(OperationResult.forOperation("Verify CVV")
                        .output(calculated.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                        .status(isValid ? "CVV verified: valid" : "CVV verified: invalid").build());
            }

        } catch (Exception e) {
            cvvResultArea.setText("Error verifying CVV: " + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 5) return "[redacted]";
        return "*".repeat(Math.max(0, pan.length() - 4)) + pan.substring(pan.length() - 4);
    }

    // ==================== MAC HANDLERS ====================

    public void handleGenerateMac() {
        try {
            String algorithm = macAlgorithmCombo.getSelectionModel().getSelectedItem();
            String macKey = macKeyField.getText().trim().replaceAll("\\s+", "");
            String data = macDataField.getText().trim().replaceAll("\\s+", "");

            // Validate inputs
            if (macKey.isEmpty() || data.isEmpty()) {
                macResultArea.setText("Error: MAC Key and Data are required");
                return;
            }

            if (!macKey.matches("[0-9A-Fa-f]{32}")) {
                macResultArea.setText("Error: MAC Key must be 32 hexadecimal characters");
                return;
            }

            if (!data.matches("[0-9A-Fa-f]+")) {
                macResultArea.setText("Error: Data must be hexadecimal");
                return;
            }

            // Generate MAC
            String mac = PaymentOperations.generateMAC(macKey, data, algorithm);

            // Display result
            StringBuilder result = new StringBuilder();
            result.append("========================================\n");
            result.append("MAC GENERATION\n");
            result.append("========================================\n\n");
            result.append("Algorithm: ").append(algorithm).append("\n");
            result.append("Key:       ").append(macKey.toUpperCase()).append("\n");
            result.append("Data:      ").append(data.toUpperCase()).append("\n");
            result.append("           (").append(data.length() / 2).append(" bytes)\n\n");
            result.append("MAC:       ").append(mac).append("\n");
            result.append("========================================\n");

            macResultArea.setText(result.toString());
            updateStatus("MAC generated successfully");

        } catch (Exception e) {
            macResultArea.setText("Error generating MAC: " + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    public void handleVerifyMac() {
        macResultArea.setText("MAC Verification - To be implemented");
        updateStatus("Feature coming soon");
    }

    // ==================== NEW ADVANCED FEATURES ====================

    // PIN Translation fields (will be added to FXML)
    private TextField pinTransSourceBlockField;
    private TextField pinTransPanField;
    private ComboBox<String> pinTransSourceFormatCombo;
    private ComboBox<String> pinTransTargetFormatCombo;
    private TextArea pinTransResultArea;

    // PVV fields
    private TextField pvvPinField;
    private TextField pvvPanField;
    private TextField pvvKeyField;
    private TextField pvvLengthField;
    private TextField pvvValueField; // For verification
    private TextArea pvvResultArea;

    // Track Data fields
    private TextField trackPanField;
    private TextField trackNameField;
    private TextField trackExpiryField;
    private TextField trackServiceCodeField;
    private TextField trackDiscretionaryField;
    private TextArea trackDataField; // For parsing
    private TextArea trackResultArea;

    /**
     * Initialize advanced Payments features
     */
    public void initializeAdvancedFeatures(
            // PIN Translation
            TextField pinTransSourceBlockField,
            TextField pinTransPanField,
            ComboBox<String> pinTransSourceFormatCombo,
            ComboBox<String> pinTransTargetFormatCombo,
            TextArea pinTransResultArea,
            // PVV
            TextField pvvPinField,
            TextField pvvPanField,
            TextField pvvKeyField,
            TextField pvvLengthField,
            TextField pvvValueField,
            TextArea pvvResultArea,
            // Track Data
            TextField trackPanField,
            TextField trackNameField,
            TextField trackExpiryField,
            TextField trackServiceCodeField,
            TextField trackDiscretionaryField,
            TextArea trackDataField,
            TextArea trackResultArea) {

        // PIN Translation
        this.pinTransSourceBlockField = pinTransSourceBlockField;
        this.pinTransPanField = pinTransPanField;
        this.pinTransSourceFormatCombo = pinTransSourceFormatCombo;
        this.pinTransTargetFormatCombo = pinTransTargetFormatCombo;
        this.pinTransResultArea = pinTransResultArea;

        // PVV
        this.pvvPinField = pvvPinField;
        this.pvvPanField = pvvPanField;
        this.pvvKeyField = pvvKeyField;
        this.pvvLengthField = pvvLengthField;
        this.pvvValueField = pvvValueField;
        this.pvvResultArea = pvvResultArea;

        // Track Data
        this.trackPanField = trackPanField;
        this.trackNameField = trackNameField;
        this.trackExpiryField = trackExpiryField;
        this.trackServiceCodeField = trackServiceCodeField;
        this.trackDiscretionaryField = trackDiscretionaryField;
        this.trackDataField = trackDataField;
        this.trackResultArea = trackResultArea;

        // Setup combo boxes
        if (pinTransSourceFormatCombo != null) {
            pinTransSourceFormatCombo.getItems().addAll(
                    "Format 0 (ISO-0)", "Format 1 (ISO-1)", "Format 2 (ISO-2)",
                    "Format 3 (ISO-3)", "Format 4 (ISO-4)", "ANSI X9.8",
                    "IBM 3624", "VISA-1");
            pinTransSourceFormatCombo.getSelectionModel().selectFirst();
        }

        if (pinTransTargetFormatCombo != null) {
            pinTransTargetFormatCombo.getItems().addAll(
                    "Format 0 (ISO-0)", "Format 1 (ISO-1)", "Format 2 (ISO-2)",
                    "Format 3 (ISO-3)", "Format 4 (ISO-4)", "ANSI X9.8",
                    "IBM 3624", "VISA-1");
            pinTransTargetFormatCombo.getSelectionModel().select(1); // Default to Format 1
        }

        if (pvvLengthField != null) {
            pvvLengthField.setText("4");
        }
    }

    /**
     * Initialize IBM 3624 PIN Generation controls
     */
    public void initializeIbm3624Controls(
            TextField ibm3624PvkField,
            TextField ibm3624ConvTableField,
            TextField ibm3624OffsetField,
            TextField ibm3624PanField,
            TextField ibm3624PinVerifyField,
            TextArea ibm3624ResultArea,
            TextField ibm3624StartField,
            TextField ibm3624LengthField,
            TextField ibm3624PadField) {

        this.ibm3624PvkField = ibm3624PvkField;
        this.ibm3624ConvTableField = ibm3624ConvTableField;
        this.ibm3624OffsetField = ibm3624OffsetField;
        this.ibm3624PanField = ibm3624PanField;
        this.ibm3624PinVerifyField = ibm3624PinVerifyField;
        this.ibm3624ResultArea = ibm3624ResultArea;
        this.ibm3624StartField = ibm3624StartField;
        this.ibm3624LengthField = ibm3624LengthField;
        this.ibm3624PadField = ibm3624PadField;

        // Set default values if fields are loaded
        if (ibm3624ConvTableField != null) {
            ibm3624ConvTableField.setText("0123456789012345");
        }
    }

    // ==================== PIN TRANSLATION HANDLERS ====================

    public void handleTranslatePinBlock() {
        try {
            String sourceBlock = pinTransSourceBlockField.getText().trim().replaceAll("\\s", "");
            String pan = pinTransPanField.getText().trim().replaceAll("\\s", "");
            String sourceFormat = pinTransSourceFormatCombo.getValue();
            String targetFormat = pinTransTargetFormatCombo.getValue();

            if (sourceBlock.isEmpty() || pan.isEmpty()) {
                pinTransResultArea.setText("Error: Please enter PIN block and PAN");
                return;
            }

            if (sourceFormat.equals(targetFormat)) {
                pinTransResultArea.setText("Warning: Source and target formats are the same");
                return;
            }

            // Perform translation with details
            String details = PaymentOperations.getTranslationDetails(sourceBlock, pan, sourceFormat, targetFormat);
            pinTransResultArea.setText(details);

            // Add to history
            OperationHistory.getInstance().addOperation("PIN Translation",
                    "Translate " + sourceFormat + " → " + targetFormat,
                    "Block: " + sourceBlock + ", PAN: " + pan,
                    PaymentOperations.translatePinBlock(sourceBlock, pan, sourceFormat, targetFormat));

            updateStatus("PIN block translated successfully");

        } catch (Exception e) {
            pinTransResultArea.setText("Error translating PIN block:\n" + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    // ==================== PVV HANDLERS ====================

    public void handleGeneratePVV() {
        try {
            String pin = pvvPinField.getText().trim();
            String pan = pvvPanField.getText().trim().replaceAll("\\s", "");
            String pvk = pvvKeyField.getText().trim().replaceAll("\\s", "");
            String lengthStr = pvvLengthField.getText().trim();

            if (pin.isEmpty() || pan.isEmpty() || pvk.isEmpty()) {
                pvvResultArea.setText("Error: Please enter PIN, PAN, and PVK");
                return;
            }

            int pvvLength = 4; // Default
            if (!lengthStr.isEmpty()) {
                pvvLength = Integer.parseInt(lengthStr);
            }

            // Generate PVV with details
            String details = PaymentOperations.getPVVDetails(pin, pan, pvk, "0", pvvLength);
            pvvResultArea.setText(details);

            // Also set the PVV value for verification
            String pvv = PaymentOperations.generatePVV(pin, pan, pvk, "0", pvvLength);
            pvvValueField.setText(pvv);

            // Add to history
            OperationHistory.getInstance().addOperation("PVV", "Generate PVV",
                    "PIN: [HIDDEN], PAN: " + pan,
                    "PVV: " + pvv);

            updateStatus("PVV generated successfully");

        } catch (Exception e) {
            pvvResultArea.setText("Error generating PVV:\n" + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    public void handleVerifyPVV() {
        try {
            String pin = pvvPinField.getText().trim();
            String pan = pvvPanField.getText().trim().replaceAll("\\s", "");
            String pvk = pvvKeyField.getText().trim().replaceAll("\\s", "");
            String pvvToVerify = pvvValueField.getText().trim();
            String lengthStr = pvvLengthField.getText().trim();

            if (pin.isEmpty() || pan.isEmpty() || pvk.isEmpty() || pvvToVerify.isEmpty()) {
                pvvResultArea.setText("Error: Please enter all fields including PVV to verify");
                return;
            }

            int pvvLength = pvvToVerify.length();
            if (!lengthStr.isEmpty()) {
                pvvLength = Integer.parseInt(lengthStr);
            }

            // Generate and verify
            String generatedPVV = PaymentOperations.generatePVV(pin, pan, pvk, "0", pvvLength);
            boolean isValid = generatedPVV.equals(pvvToVerify);

            StringBuilder result = new StringBuilder();
            result.append("═══ PVV VERIFICATION ═══\n\n");
            result.append("Generated PVV: ").append(generatedPVV).append("\n");
            result.append("Provided PVV:  ").append(pvvToVerify).append("\n\n");
            result.append("Result: ").append(isValid ? "✓ VALID" : "✗ INVALID").append("\n");

            pvvResultArea.setText(result.toString());

            // Add to history
            OperationHistory.getInstance().addOperation("PVV", "Verify PVV",
                    "PAN: " + pan + ", PVV: " + pvvToVerify,
                    isValid ? "VALID" : "INVALID");

            updateStatus(isValid ? "PVV is valid" : "PVV is invalid");

        } catch (Exception e) {
            pvvResultArea.setText("Error verifying PVV:\n" + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    // ==================== TRACK DATA HANDLERS ====================

    public void handleEncodeTrack1() {
        try {
            String pan = trackPanField.getText().trim().replaceAll("\\s", "");
            String name = trackNameField.getText().trim();
            String expiry = trackExpiryField.getText().trim();
            String serviceCode = trackServiceCodeField.getText().trim();
            String discretionary = trackDiscretionaryField.getText().trim();

            if (pan.isEmpty() || name.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                trackResultArea.setText("Error: Please enter PAN, Name, Expiry, and Service Code");
                return;
            }

            String track1 = PaymentOperations.encodeTrack1(pan, name, expiry, serviceCode, discretionary);

            StringBuilder result = new StringBuilder();
            result.append("═══ TRACK 1 ENCODED ═══\n\n");
            result.append("Track 1: ").append(track1).append("\n\n");
            result.append("Length: ").append(track1.length()).append(" characters\n");
            result.append("Format: ISO/IEC 7813 Track 1\n");

            trackResultArea.setText(result.toString());

            // Set to track data field for parsing
            trackDataField.setText(track1);

            // Add to history
            OperationHistory.getInstance().addOperation("Track Data", "Encode Track 1",
                    "PAN: " + pan + ", Name: " + name,
                    track1);

            updateStatus("Track 1 data encoded successfully");

        } catch (Exception e) {
            trackResultArea.setText("Error encoding Track 1:\n" + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    public void handleEncodeTrack2() {
        try {
            String pan = trackPanField.getText().trim().replaceAll("\\s", "");
            String expiry = trackExpiryField.getText().trim();
            String serviceCode = trackServiceCodeField.getText().trim();
            String discretionary = trackDiscretionaryField.getText().trim();

            if (pan.isEmpty() || expiry.isEmpty() || serviceCode.isEmpty()) {
                trackResultArea.setText("Error: Please enter PAN, Expiry, and Service Code");
                return;
            }

            String track2 = PaymentOperations.encodeTrack2(pan, expiry, serviceCode, discretionary);

            StringBuilder result = new StringBuilder();
            result.append("═══ TRACK 2 ENCODED ═══\n\n");
            result.append("Track 2: ").append(track2).append("\n");
            result.append("Track 2 Hex: ").append(PaymentOperations.track2ToHex(track2)).append("\n\n");
            result.append("Length: ").append(track2.length()).append(" characters\n");
            result.append("Format: ISO/IEC 7813 Track 2\n");

            trackResultArea.setText(result.toString());

            // Set to track data field for parsing
            trackDataField.setText(track2);

            // Add to history
            OperationHistory.getInstance().addOperation("Track Data", "Encode Track 2",
                    "PAN: " + pan,
                    track2);

            updateStatus("Track 2 data encoded successfully");

        } catch (Exception e) {
            trackResultArea.setText("Error encoding Track 2:\n" + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    public void handleParseTrackData() {
        try {
            String trackData = trackDataField.getText().trim();

            if (trackData.isEmpty()) {
                trackResultArea.setText("Error: Please enter track data to parse");
                return;
            }

            String result;
            if (trackData.startsWith("%B")) {
                result = PaymentOperations.parseTrack1(trackData);
            } else if (trackData.startsWith(";")) {
                result = PaymentOperations.parseTrack2(trackData);
            } else {
                result = "Error: Unknown track format\n" +
                        "Track 1 must start with %B\n" +
                        "Track 2 must start with ;";
            }

            trackResultArea.setText(result);

            // Add to history
            OperationHistory.getInstance().addOperation("Track Data", "Parse Track Data",
                    trackData.substring(0, Math.min(50, trackData.length())) + "...",
                    "Parsed successfully");

            updateStatus("Track data parsed successfully");

        } catch (Exception e) {
            trackResultArea.setText("Error parsing track data:\n" + e.getMessage());
            updateStatus("Error: " + e.getMessage());
        }
    }

    // ============================================================
    // ENCRYPTED PIN BLOCK OPERATIONS (Generic)
    // ============================================================

    public void handleEncodeEncryptedPinBlock() {
        try {
            if (encPinField == null || encPanFieldEncode == null || encResultArea == null) {
                showError("Configuration Error", "Encrypted PIN controls not initialized");
                return;
            }

            String pin = encPinField.getText().trim();
            String pan = encPanFieldEncode.getText().trim().replaceAll("\\s+", "");
            String keyHex = encPinBlockKeyField != null ? encPinBlockKeyField.getText().trim().replaceAll("\\s+", "")
                    : "";
            String format = encPinBlockFormatCombo.getSelectionModel().getSelectedItem();

            if (pin.isEmpty()) {
                showError("Input Error", "Please enter PIN");
                return;
            }
            // Some formats might not need PAN, but mostly they do for XOR or binding
            if (pan.isEmpty() && (format.contains("ISO-0") || format.contains("ISO-3"))) {
                showError("Input Error", "Please enter PAN for " + format);
                return;
            }

            // 1. Create clear PIN block using PaymentOperations (which supports all
            // formats)
            String clearPinBlock = PaymentOperations.encodePinBlock(pin, pan, format);
            String publishedBlock = clearPinBlock;

            String result = "Format: " + format + "\n";
            result += "Clear PIN Block:\n" + clearPinBlock;

            // 2. Encrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    byte[] clearBytes = DataConverter.hexToBytes(clearPinBlock);

                    // Encrypt with TDES
                    byte[] encrypted = PaymentOperations.encryptDesEcb(clearBytes, key);

                    publishedBlock = DataConverter.bytesToHex(encrypted).toUpperCase();
                    result += "\n\nEncrypted PIN Block:\n" + publishedBlock;
                } catch (Exception e) {
                    result += "\n\nEncryption Error: " + e.getMessage();
                }
            }

            encResultArea.setText(result);
            encResultArea.setManaged(true);
            encResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN", "[not persisted]");
            details.put("Protected", keyHex.isEmpty() ? "No key supplied" : "TDES ECB");
            mainController.publish(OperationResult.forOperation("Encode Encrypted PIN Block")
                    .output(DataConverter.hexToBytes(publishedBlock)).details(details)
                    .status("Encrypted PIN Block encoded successfully").build());

        } catch (Exception e) {
            showError("Encoding Error", "Error encoding Encrypted PIN Block: " + e.getMessage());
            LOG.error("Encrypted PIN block encoding failed", e);
        }
    }

    public void handleDecodeEncryptedPinBlock() {
        try {
            if (encPinBlockFieldDecode == null || encPanFieldDecode == null || encResultArea == null) {
                showError("Configuration Error", "Encrypted PIN decode controls not initialized");
                return;
            }

            String pinBlockHex = encPinBlockFieldDecode.getText().trim().replaceAll("\\s+", "");
            String pan = encPanFieldDecode.getText().trim().replaceAll("\\s+", "");
            String keyHex = encPinBlockKeyFieldDecode != null
                    ? encPinBlockKeyFieldDecode.getText().trim().replaceAll("\\s+", "")
                    : "";
            String format = encPinBlockFormatCombo.getSelectionModel().getSelectedItem();

            if (pinBlockHex.isEmpty()) {
                showError("Input Error", "Please enter PIN Block");
                return;
            }

            String clearPinBlockHex = pinBlockHex;

            // 1. Decrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    byte[] encrypted = DataConverter.hexToBytes(pinBlockHex);

                    // Decrypt with TDES
                    byte[] decrypted = PaymentOperations.decryptDesEcb(encrypted, key);
                    clearPinBlockHex = DataConverter.bytesToHex(decrypted).toUpperCase();
                } catch (Exception e) {
                    showError("Decryption Error", "Error decrypting PIN Block: " + e.getMessage());
                    return;
                }
            }

            // 2. Decode PIN block using PaymentOperations
            String pin = PaymentOperations.decodePinBlock(clearPinBlockHex, pan, format);

            String result = "Format: " + format + "\n";
            result += "Clear PIN Block: " + clearPinBlockHex + "\n\nDecoded PIN: " + pin;

            encResultArea.setText(result);
            encResultArea.setManaged(true);
            encResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Format", format);
            details.put("PAN", maskPan(pan));
            details.put("PIN", "[not persisted]");
            details.put("Protected", keyHex.isEmpty() ? "No key supplied" : "TDES ECB");
            mainController.publish(OperationResult.forOperation("Decode Encrypted PIN Block")
                    .input(DataConverter.hexToBytes(pinBlockHex))
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("Encrypted PIN Block decoded successfully").build());

        } catch (Exception e) {
            showError("Decoding Error", "Error decoding Encrypted PIN Block: " + e.getMessage());
            LOG.error("Encrypted PIN block decoding failed", e);
        }
    }

    // ============================================================
    // IBM 3624 PIN OPERATIONS
    // ============================================================

    public void handleGenerateIbm3624Pin() {
        try {
            if (ibm3624PvkField == null || ibm3624OffsetField == null || ibm3624PanField == null
                    || ibm3624ResultArea == null) {
                showError("Configuration Error", "IBM 3624 controls not initialized");
                return;
            }

            String pvkHex = ibm3624PvkField.getText().trim();
            String convTable = ibm3624ConvTableField != null ? ibm3624ConvTableField.getText().trim()
                    : "0123456789012345";
            String offset = ibm3624OffsetField.getText().trim();
            String pan = ibm3624PanField.getText().trim();

            if (pvkHex.isEmpty() || offset.isEmpty() || pan.isEmpty()) {
                showError("Input Error", "Please enter PVK, Offset, and PAN");
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
                    showError("Invalid Start Position", "Start Position must be a number");
                    return;
                }
            }

            if (ibm3624LengthField != null && !ibm3624LengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(ibm3624LengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError("Invalid Length", "Length must be a number");
                    return;
                }
            }

            if (ibm3624PadField != null && !ibm3624PadField.getText().trim().isEmpty()) {
                padChar = ibm3624PadField.getText().trim().substring(0, 1);
            }

            // Generate PIN using IBM 3624 method
            String pin = com.cryptoforge.pin.Pin.generateIbm3624Pin(
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

            String result = "Generated PIN: " + pin + "\n\n" +
                    "Method: IBM 3624\n" +
                    "PAN: " + pan + "\n" +
                    "Offset: " + offset + "\n" +
                    "Conversion Table: " + convTable + "\n" +
                    "Validation Config: Start " + displayStart + ", Len " + length + ", Pad " + padChar + "\n" +
                    "Validation Data Block (Computed): " + displayVd.toUpperCase();

            ibm3624ResultArea.setText(result);
            ibm3624ResultArea.setManaged(true);
            ibm3624ResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624");
            details.put("PAN", maskPan(pan));
            details.put("Offset", offset);
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Generate PIN (IBM 3624)")
                    .output(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("PIN generated successfully (IBM 3624)").build());

        } catch (Exception e) {
            showError("Generation Error", "Error generating PIN: " + e.getMessage());
            LOG.error("IBM 3624 PIN generation failed", e);
        }
    }

    public void handleVerifyIbm3624Pin() {
        try {
            if (ibm3624PvkField == null || ibm3624PinVerifyField == null || ibm3624PanField == null
                    || ibm3624ResultArea == null) {
                showError("Configuration Error", "IBM 3624 verify controls not initialized");
                return;
            }

            String pvkHex = ibm3624PvkField.getText().trim();
            String convTable = ibm3624ConvTableField != null ? ibm3624ConvTableField.getText().trim()
                    : "0123456789012345";
            String offset = ibm3624OffsetField != null ? ibm3624OffsetField.getText().trim() : "";
            String pan = ibm3624PanField.getText().trim();
            String pinToVerify = ibm3624PinVerifyField.getText().trim();

            if (pvkHex.isEmpty() || pan.isEmpty() || pinToVerify.isEmpty()) {
                showError("Input Error", "Please enter PVK, PAN, and PIN to verify");
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
                    showError("Invalid Start Position", "Start Position must be a number");
                    return;
                }
            }

            if (ibm3624LengthField != null && !ibm3624LengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(ibm3624LengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError("Invalid Length", "Length must be a number");
                    return;
                }
            }

            if (ibm3624PadField != null && !ibm3624PadField.getText().trim().isEmpty()) {
                padChar = ibm3624PadField.getText().trim().substring(0, 1);
            }

            // Generate expected PIN - use static method with all parameters
            String expectedPin = com.cryptoforge.pin.Pin.generateIbm3624Pin(
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

            String result = "PIN Verification: " + (isValid ? "✅ VALID" : "❌ INVALID") + "\n\n" +
                    "Entered PIN: " + pinToVerify + "\n" +
                    "Expected PIN: " + expectedPin + "\n" +
                    "Method: IBM 3624\n" +
                    "PAN: " + pan + "\n" +
                    "Offset: " + offset + "\n" +
                    "Validation Config: Start " + displayStart + ", Len " + length + ", Pad " + padChar + "\n" +
                    "Validation Data Block: " + displayVd.toUpperCase();

            ibm3624ResultArea.setText(result);
            ibm3624ResultArea.setManaged(true);
            ibm3624ResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624");
            details.put("PAN", maskPan(pan));
            details.put("Result", isValid ? "VALID" : "INVALID");
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Verify PIN (IBM 3624)")
                    .output(expectedPin.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("PIN verification: " + (isValid ? "VALID" : "INVALID")).build());

        } catch (Exception e) {
            showError("Verification Error", "Error verifying PIN: " + e.getMessage());
            LOG.error("IBM 3624 PIN verification failed", e);
        }
    }

    // PIN GENERATORS (OFFSET & PVV)
    // ============================================================

    public void handleGenerateOffsetUtility() {
        try {
            if (genOffsetPvkField == null || genOffsetResultArea == null) {
                showError("Configuration Error", "Generator controls not initialized");
                return;
            }

            String pvk = genOffsetPvkField.getText().trim();
            String decTable = genOffsetDecTableField.getText().trim();
            String pan = genOffsetPanField.getText().trim();
            String pin = genOffsetPinField.getText().trim();

            if (pvk.isEmpty() || decTable.isEmpty() || pan.isEmpty() || pin.isEmpty()) {
                showError("Input Error", "Please enter PVK, Decimalization Table, PAN, and Desired PIN");
                return;
            }

            if (decTable.length() != 16) {
                showError("Input Error", "Decimalization Table must be 16 digits");
                return;
            }

            String offset = PaymentOperations.generateIBM3624Offset(pin, pan, pvk, decTable);

            // Reconstruct Validation Data Block for display (This helper uses defaults, so
            // "offset" might be wrong if defaults mismatch)
            // WE MUST RE-CALCULATE using Pin.java directly to support custom config

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
                    showError("Invalid Start Position", "Start Position must be a number");
                    return;
                }
            }

            if (genOffsetLengthField != null && !genOffsetLengthField.getText().trim().isEmpty()) {
                try {
                    length = Integer.parseInt(genOffsetLengthField.getText().trim());
                } catch (NumberFormatException e) {
                    showError("Invalid Length", "Length must be a number");
                    return;
                }
            }

            if (genOffsetPadField != null && !genOffsetPadField.getText().trim().isEmpty()) {
                padChar = genOffsetPadField.getText().trim().substring(0, 1);
            }

            // Generate Offset directly
            offset = com.cryptoforge.pin.Pin.generateIbm3624Offset(
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
            res.append("Generated Offset (IBM 3624):\n").append(offset).append("\n\n");
            res.append("For PIN: ").append(pin).append("\n");
            res.append("Validation Config: Start ").append(displayStart)
                    .append(", Len ").append(length)
                    .append(", Pad ").append(padChar).append("\n");
            res.append("Validation Data Block: ").append(displayVd.toUpperCase());

            genOffsetResultArea.setText(res.toString());
            genOffsetResultArea.setManaged(true);
            genOffsetResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "IBM 3624 offset");
            details.put("PAN", maskPan(pan));
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Generate Offset")
                    .output(offset.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("Offset generated successfully").build());

        } catch (Exception e) {
            showError("Generation Error", "Error generating Offset: " + e.getMessage());
        }
    }

    public void handleGeneratePVVUtility() {
        try {
            if (genPvvPvkField == null || genPvvResultArea == null) {
                showError("Configuration Error", "Generator controls not initialized");
                return;
            }

            String pvk = genPvvPvkField.getText().trim();
            String pan = genPvvPanField.getText().trim();
            String pin = genPvvPinField.getText().trim();
            String keyIndex = genPvvKeyIndexField != null ? genPvvKeyIndexField.getText().trim() : "0";
            if (keyIndex.isEmpty())
                keyIndex = "0";

            if (pvk.isEmpty() || pan.isEmpty() || pin.isEmpty()) {
                showError("Input Error", "Please enter PVK, PAN, and PIN");
                return;
            }

            String pvv = PaymentOperations.generatePVV(pin, pan, pvk, keyIndex, 4);

            StringBuilder res = new StringBuilder();
            res.append("Generated PVV (VISA):\n").append(pvv).append("\n\n");
            res.append("Key Index: ").append(keyIndex).append("\n");

            genPvvResultArea.setText(res.toString());
            genPvvResultArea.setManaged(true);
            genPvvResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "VISA PVV");
            details.put("PAN", maskPan(pan));
            details.put("Key Index", keyIndex);
            details.put("PIN", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Generate PVV")
                    .output(pvv.getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("PVV generated successfully").build());

        } catch (Exception e) {
            showError("Generation Error", "Error generating PVV: " + e.getMessage());
        }
    }

    public void handleDerivePinFromPvvUtility() {
        try {
            if (derivePvvPvkField == null || derivePvvResultArea == null) {
                showError("Configuration Error", "Derive controls not initialized");
                return;
            }

            String pvk = derivePvvPvkField.getText().trim();
            String pan = derivePvvPanField.getText().trim();
            String targetPvv = derivePvvTargetPvvField.getText().trim();
            String keyIndex = derivePvvKeyIndexField != null ? derivePvvKeyIndexField.getText().trim() : "0";
            if (keyIndex.isEmpty())
                keyIndex = "0";

            if (pvk.isEmpty() || pan.isEmpty() || targetPvv.isEmpty()) {
                showError("Input Error", "Please enter PVK, PAN, and Target PVV");
                return;
            }

            java.util.List<String> matches = PaymentOperations.derivePinFromPvv(pan, pvk, keyIndex, targetPvv, 4);

            StringBuilder res = new StringBuilder();
            res.append("Derive PIN Results:\n");
            res.append("-------------------\n");
            res.append("PVK: ").append(pvk).append("\n");
            res.append("PAN: ").append(pan).append("\n");
            res.append("Target PVV: ").append(targetPvv).append("\n");
            res.append("PVKI: ").append(keyIndex).append("\n\n");

            if (matches.isEmpty()) {
                res.append("❌ No PINs found that generate this PVV.");
            } else {
                res.append("✅ Found ").append(matches.size()).append(" match(es):\n\n");
                for (String pin : matches) {
                    res.append("  • PIN: ").append(pin).append("\n");
                }
            }

            derivePvvResultArea.setText(res.toString());
            derivePvvResultArea.setManaged(true);
            derivePvvResultArea.setVisible(true);

            java.util.Map<String, String> details = new java.util.LinkedHashMap<>();
            details.put("Method", "Derive PIN from PVV");
            details.put("PAN", maskPan(pan));
            details.put("PVV", targetPvv);
            details.put("Matches", String.valueOf(matches.size()));
            details.put("PINs", "[not persisted]");
            mainController.publish(OperationResult.forOperation("Derive PIN from PVV")
                    .output(res.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).details(details)
                    .status("PIN derivation completed").build());

        } catch (Exception e) {
            showError("Derivation Error", "Error deriving PIN: " + e.getMessage());
        }
    }
}
