package com.cryptocarver.ui;

import com.cryptocarver.pin.Pin;
import com.cryptocarver.pin.PinBlock;
import com.cryptocarver.util.DataConverter;
import com.cryptocarver.utils.OperationHistory;
import javafx.scene.control.*;

/**
 * Controller for PIN operations
 * Handles PIN block encoding/decoding and PIN verification operations
 */
public class PinController {

    private MainController mainController;
    private StatusReporter statusReporter;

    public void setStatusReporter(StatusReporter statusReporter) {
        this.statusReporter = statusReporter;
    }

    // PIN Block ISO 0 controls
    private TextField iso0PinField;
    private TextField iso0PanFieldEncode;
    private TextField iso0PinBlockKeyField;
    private TextArea iso0ResultArea;

    private TextField iso0PinBlockFieldDecode;
    private TextField iso0PanFieldDecode;
    private TextField iso0PinBlockKeyFieldDecode;
    private TextArea iso0DecodeResultArea;

    // PIN Block ISO 2 controls
    private TextField iso2PinField;
    private TextField iso2PinBlockKeyField;
    private TextArea iso2ResultArea;

    private TextField iso2PinBlockFieldDecode;
    private TextField iso2PinBlockKeyFieldDecode;
    private TextArea iso2DecodeResultArea;

    // PIN Block ISO 3 controls
    private TextField iso3PinField;
    private TextField iso3PanFieldEncode;
    private TextField iso3PinBlockKeyField;
    private TextArea iso3ResultArea;

    private TextField iso3PinBlockFieldDecode;
    private TextField iso3PanFieldDecode;
    private TextField iso3PinBlockKeyFieldDecode;
    private TextArea iso3DecodeResultArea;

    // PIN Block ISO 4 controls
    private TextField iso4PinField;
    private TextField iso4PanFieldEncode;
    private TextField iso4PinBlockKeyField;
    private TextArea iso4ResultArea;

    private TextField iso4PinBlockFieldDecode;
    private TextField iso4PanFieldDecode;
    private TextField iso4PinBlockKeyFieldDecode;
    private TextArea iso4DecodeResultArea;

    // IBM 3624 controls
    private TextField ibm3624PvkField;
    private TextField ibm3624ConvTableField;
    private TextField ibm3624OffsetField;
    private TextField ibm3624PanField;
    private TextField ibm3624PanOffsetField;
    private TextField ibm3624PanLengthField;
    private TextField ibm3624PanPadField;
    private TextArea ibm3624ResultArea;

    private TextField ibm3624PvkFieldOffset;
    private TextField ibm3624ConvTableFieldOffset;
    private TextField ibm3624PinFieldOffset;
    private TextField ibm3624PanFieldOffset;
    private TextField ibm3624PanOffsetFieldOffset;
    private TextField ibm3624PanLengthFieldOffset;
    private TextField ibm3624PanPadFieldOffset;
    private TextArea ibm3624OffsetResultArea;

    // VISA PVV controls
    private TextField visaPvvPvkField;
    private TextField visaPvvPvkiField;
    private TextField visaPvvPinField;
    private TextField visaPvvPanField;
    private TextArea visaPvvResultArea;

    public void initialize(
            MainController mainController,
                          // ISO 0 Encode
                          TextField iso0PinField,
                          TextField iso0PanFieldEncode,
                          TextField iso0PinBlockKeyField,
                          TextArea iso0ResultArea,
                          // ISO 0 Decode
                          TextField iso0PinBlockFieldDecode,
                          TextField iso0PanFieldDecode,
                          TextField iso0PinBlockKeyFieldDecode,
                          TextArea iso0DecodeResultArea,
                          // ISO 2 Encode
                          TextField iso2PinField,
                          TextField iso2PinBlockKeyField,
                          TextArea iso2ResultArea,
                          // ISO 2 Decode
                          TextField iso2PinBlockFieldDecode,
                          TextField iso2PinBlockKeyFieldDecode,
                          TextArea iso2DecodeResultArea,
                          // ISO 3 Encode
                          TextField iso3PinField,
                          TextField iso3PanFieldEncode,
                          TextField iso3PinBlockKeyField,
                          TextArea iso3ResultArea,
                          // ISO 3 Decode
                          TextField iso3PinBlockFieldDecode,
                          TextField iso3PanFieldDecode,
                          TextField iso3PinBlockKeyFieldDecode,
                          TextArea iso3DecodeResultArea,
                          // ISO 4 Encode
                          TextField iso4PinField,
                          TextField iso4PanFieldEncode,
                          TextField iso4PinBlockKeyField,
                          TextArea iso4ResultArea,
                          // ISO 4 Decode
                          TextField iso4PinBlockFieldDecode,
                          TextField iso4PanFieldDecode,
                          TextField iso4PinBlockKeyFieldDecode,
                          TextArea iso4DecodeResultArea,
                          // IBM 3624 Generate PIN
                          TextField ibm3624PvkField,
                          TextField ibm3624ConvTableField,
                          TextField ibm3624OffsetField,
                          TextField ibm3624PanField,
                          TextField ibm3624PanOffsetField,
                          TextField ibm3624PanLengthField,
                          TextField ibm3624PanPadField,
                          TextArea ibm3624ResultArea,
                          // IBM 3624 Generate Offset
                          TextField ibm3624PvkFieldOffset,
                          TextField ibm3624ConvTableFieldOffset,
                          TextField ibm3624PinFieldOffset,
                          TextField ibm3624PanFieldOffset,
                          TextField ibm3624PanOffsetFieldOffset,
                          TextField ibm3624PanLengthFieldOffset,
                          TextField ibm3624PanPadFieldOffset,
                          TextArea ibm3624OffsetResultArea,
                          // VISA PVV
                          TextField visaPvvPvkField,
                          TextField visaPvvPvkiField,
                          TextField visaPvvPinField,
                          TextField visaPvvPanField,
                          TextArea visaPvvResultArea
    ) {
        this.mainController = mainController;
        this.statusReporter = (mainController instanceof StatusReporter) ? (StatusReporter) mainController : null;

        // ISO 0
        this.iso0PinField = iso0PinField;
        this.iso0PanFieldEncode = iso0PanFieldEncode;
        this.iso0PinBlockKeyField = iso0PinBlockKeyField;
        this.iso0ResultArea = iso0ResultArea;
        this.iso0PinBlockFieldDecode = iso0PinBlockFieldDecode;
        this.iso0PanFieldDecode = iso0PanFieldDecode;
        this.iso0PinBlockKeyFieldDecode = iso0PinBlockKeyFieldDecode;
        this.iso0DecodeResultArea = iso0DecodeResultArea;

        // ISO 2
        this.iso2PinField = iso2PinField;
        this.iso2PinBlockKeyField = iso2PinBlockKeyField;
        this.iso2ResultArea = iso2ResultArea;
        this.iso2PinBlockFieldDecode = iso2PinBlockFieldDecode;
        this.iso2PinBlockKeyFieldDecode = iso2PinBlockKeyFieldDecode;
        this.iso2DecodeResultArea = iso2DecodeResultArea;

        // ISO 3
        this.iso3PinField = iso3PinField;
        this.iso3PanFieldEncode = iso3PanFieldEncode;
        this.iso3PinBlockKeyField = iso3PinBlockKeyField;
        this.iso3ResultArea = iso3ResultArea;
        this.iso3PinBlockFieldDecode = iso3PinBlockFieldDecode;
        this.iso3PanFieldDecode = iso3PanFieldDecode;
        this.iso3PinBlockKeyFieldDecode = iso3PinBlockKeyFieldDecode;
        this.iso3DecodeResultArea = iso3DecodeResultArea;

        // ISO 4
        this.iso4PinField = iso4PinField;
        this.iso4PanFieldEncode = iso4PanFieldEncode;
        this.iso4PinBlockKeyField = iso4PinBlockKeyField;
        this.iso4ResultArea = iso4ResultArea;
        this.iso4PinBlockFieldDecode = iso4PinBlockFieldDecode;
        this.iso4PanFieldDecode = iso4PanFieldDecode;
        this.iso4PinBlockKeyFieldDecode = iso4PinBlockKeyFieldDecode;
        this.iso4DecodeResultArea = iso4DecodeResultArea;

        // IBM 3624
        this.ibm3624PvkField = ibm3624PvkField;
        this.ibm3624ConvTableField = ibm3624ConvTableField;
        this.ibm3624OffsetField = ibm3624OffsetField;
        this.ibm3624PanField = ibm3624PanField;
        this.ibm3624PanOffsetField = ibm3624PanOffsetField;
        this.ibm3624PanLengthField = ibm3624PanLengthField;
        this.ibm3624PanPadField = ibm3624PanPadField;
        this.ibm3624ResultArea = ibm3624ResultArea;

        this.ibm3624PvkFieldOffset = ibm3624PvkFieldOffset;
        this.ibm3624ConvTableFieldOffset = ibm3624ConvTableFieldOffset;
        this.ibm3624PinFieldOffset = ibm3624PinFieldOffset;
        this.ibm3624PanFieldOffset = ibm3624PanFieldOffset;
        this.ibm3624PanOffsetFieldOffset = ibm3624PanOffsetFieldOffset;
        this.ibm3624PanLengthFieldOffset = ibm3624PanLengthFieldOffset;
        this.ibm3624PanPadFieldOffset = ibm3624PanPadFieldOffset;
        this.ibm3624OffsetResultArea = ibm3624OffsetResultArea;

        // VISA PVV
        this.visaPvvPvkField = visaPvvPvkField;
        this.visaPvvPvkiField = visaPvvPvkiField;
        this.visaPvvPinField = visaPvvPinField;
        this.visaPvvPanField = visaPvvPanField;
        this.visaPvvResultArea = visaPvvResultArea;

        setupDefaults();
    }

    private void setupDefaults() {
        // Set default conversion table for IBM 3624
        if (ibm3624ConvTableField != null) {
            ibm3624ConvTableField.setText("0123456789012345");
        }
        if (ibm3624ConvTableFieldOffset != null) {
            ibm3624ConvTableFieldOffset.setText("0123456789012345");
        }

        // Set default PAN pad
        if (ibm3624PanPadField != null) {
            ibm3624PanPadField.setText("0");
        }
        if (ibm3624PanPadFieldOffset != null) {
            ibm3624PanPadFieldOffset.setText("0");
        }

        // Set default PAN offset and length
        if (ibm3624PanOffsetField != null) {
            ibm3624PanOffsetField.setText("0");
        }
        if (ibm3624PanLengthField != null) {
            ibm3624PanLengthField.setText("12");
        }
        if (ibm3624PanOffsetFieldOffset != null) {
            ibm3624PanOffsetFieldOffset.setText("0");
        }
        if (ibm3624PanLengthFieldOffset != null) {
            ibm3624PanLengthFieldOffset.setText("12");
        }
    }

    // ==================== ISO 0 HANDLERS ====================

    public void handleEncodeIso0() {
        try {
            String pin = iso0PinField.getText().trim();
            String pan = iso0PanFieldEncode.getText().trim().replaceAll("\\s+", "");
            String keyHex = iso0PinBlockKeyField.getText().trim().replaceAll("\\s+", "");

            if (pin.isEmpty() || pan.isEmpty()) {
                iso0ResultArea.setText("❌ Error: PIN and PAN are required");
                return;
            }

            // Encode PIN block (clear)
            byte[] pinBlockClear = PinBlock.encodePinblockIso0(pin, pan);
            String pinBlockClearHex = DataConverter.bytesToHex(pinBlockClear).toUpperCase();

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 0 (ANSI X9.8)\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("PIN:     ").append(pin).append("\n");
            result.append("PAN:     ").append(pan).append("\n");
            result.append("Clear PIN Block: ").append(pinBlockClearHex).append("\n\n");

            // Encrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    byte[] encrypted = encryptPinBlock(key, pinBlockClear);
                    String encryptedHex = DataConverter.bytesToHex(encrypted).toUpperCase();
                    result.append("Encryption Key:  ").append(keyHex.toUpperCase()).append("\n");
                    result.append("Encrypted PIN Block: ").append(encryptedHex).append("\n");
                } catch (Exception e) {
                    result.append("⚠️  Encryption failed: ").append(e.getMessage()).append("\n");
                }
            }

            iso0ResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 0 - Encode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN: " + pin + ", PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", pinBlockClearHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso0ResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    public void handleDecodeIso0() {
        try {
            String pinBlockHex = iso0PinBlockFieldDecode.getText().trim().replaceAll("\\s+", "");
            String pan = iso0PanFieldDecode.getText().trim().replaceAll("\\s+", "");
            String keyHex = iso0PinBlockKeyFieldDecode.getText().trim().replaceAll("\\s+", "");

            if (pinBlockHex.isEmpty() || pan.isEmpty()) {
                iso0DecodeResultArea.setText("❌ Error: PIN Block and PAN are required");
                return;
            }

            byte[] pinBlock = DataConverter.hexToBytes(pinBlockHex);

            // Decrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    pinBlock = decryptPinBlock(key, pinBlock);
                } catch (Exception e) {
                    iso0DecodeResultArea.setText("❌ Decryption failed: " + e.getMessage());
                    return;
                }
            }

            // Decode PIN block
            String pin = PinBlock.decodePinblockIso0(pinBlock, pan);

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 0 - Decoded\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("Encrypted PIN Block: ").append(pinBlockHex.toUpperCase()).append("\n");
            if (!keyHex.isEmpty()) {
                result.append("Decryption Key:      ").append(keyHex.toUpperCase()).append("\n");
                result.append("Clear PIN Block:     ").append(DataConverter.bytesToHex(pinBlock).toUpperCase()).append("\n");
            }
            result.append("PAN:                 ").append(pan).append("\n");
            result.append("\n🔐 Decoded PIN: ").append(pin).append("\n");

            iso0DecodeResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 0 - Decode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN Block: " + pinBlockHex + ", PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "PIN: " + pin, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso0DecodeResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    // ==================== ISO 2 HANDLERS ====================

    public void handleEncodeIso2() {
        try {
            String pin = iso2PinField.getText().trim();
            String keyHex = iso2PinBlockKeyField.getText().trim().replaceAll("\\s+", "");

            if (pin.isEmpty()) {
                iso2ResultArea.setText("❌ Error: PIN is required");
                return;
            }

            // Encode PIN block (clear)
            byte[] pinBlockClear = PinBlock.encodePinblockIso2(pin);
            String pinBlockClearHex = DataConverter.bytesToHex(pinBlockClear).toUpperCase();

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 2\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("PIN:     ").append(pin).append("\n");
            result.append("Clear PIN Block: ").append(pinBlockClearHex).append("\n\n");

            // Encrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    byte[] encrypted = encryptPinBlock(key, pinBlockClear);
                    String encryptedHex = DataConverter.bytesToHex(encrypted).toUpperCase();
                    result.append("Encryption Key:  ").append(keyHex.toUpperCase()).append("\n");
                    result.append("Encrypted PIN Block: ").append(encryptedHex).append("\n");
                } catch (Exception e) {
                    result.append("⚠️  Encryption failed: ").append(e.getMessage()).append("\n");
                }
            }

            iso2ResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 2 - Encode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN: " + pin, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", pinBlockClearHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso2ResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    public void handleDecodeIso2() {
        try {
            String pinBlockHex = iso2PinBlockFieldDecode.getText().trim().replaceAll("\\s+", "");
            String keyHex = iso2PinBlockKeyFieldDecode.getText().trim().replaceAll("\\s+", "");

            if (pinBlockHex.isEmpty()) {
                iso2DecodeResultArea.setText("❌ Error: PIN Block is required");
                return;
            }

            byte[] pinBlock = DataConverter.hexToBytes(pinBlockHex);

            // Decrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    pinBlock = decryptPinBlock(key, pinBlock);
                } catch (Exception e) {
                    iso2DecodeResultArea.setText("❌ Decryption failed: " + e.getMessage());
                    return;
                }
            }

            // Decode PIN block
            String pin = PinBlock.decodePinblockIso2(pinBlock);

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 2 - Decoded\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("Encrypted PIN Block: ").append(pinBlockHex.toUpperCase()).append("\n");
            if (!keyHex.isEmpty()) {
                result.append("Decryption Key:      ").append(keyHex.toUpperCase()).append("\n");
                result.append("Clear PIN Block:     ").append(DataConverter.bytesToHex(pinBlock).toUpperCase()).append("\n");
            }
            result.append("\n🔐 Decoded PIN: ").append(pin).append("\n");

            iso2DecodeResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 2 - Decode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN Block: " + pinBlockHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "PIN: " + pin, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso2DecodeResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    // ==================== ISO 3 HANDLERS ====================

    public void handleEncodeIso3() {
        try {
            String pin = iso3PinField.getText().trim();
            String pan = iso3PanFieldEncode.getText().trim().replaceAll("\\s+", "");
            String keyHex = iso3PinBlockKeyField.getText().trim().replaceAll("\\s+", "");

            if (pin.isEmpty() || pan.isEmpty()) {
                iso3ResultArea.setText("❌ Error: PIN and PAN are required");
                return;
            }

            // Encode PIN block (clear)
            byte[] pinBlockClear = PinBlock.encodePinblockIso3(pin, pan);
            String pinBlockClearHex = DataConverter.bytesToHex(pinBlockClear).toUpperCase();

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 3\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("PIN:     ").append(pin).append("\n");
            result.append("PAN:     ").append(pan).append("\n");
            result.append("Clear PIN Block: ").append(pinBlockClearHex).append("\n");
            result.append("ℹ️  Note: Uses random padding (A-F)\n\n");

            // Encrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    byte[] encrypted = encryptPinBlock(key, pinBlockClear);
                    String encryptedHex = DataConverter.bytesToHex(encrypted).toUpperCase();
                    result.append("Encryption Key:  ").append(keyHex.toUpperCase()).append("\n");
                    result.append("Encrypted PIN Block: ").append(encryptedHex).append("\n");
                } catch (Exception e) {
                    result.append("⚠️  Encryption failed: ").append(e.getMessage()).append("\n");
                }
            }

            iso3ResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 3 - Encode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN: " + pin + ", PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", pinBlockClearHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso3ResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    public void handleDecodeIso3() {
        try {
            String pinBlockHex = iso3PinBlockFieldDecode.getText().trim().replaceAll("\\s+", "");
            String pan = iso3PanFieldDecode.getText().trim().replaceAll("\\s+", "");
            String keyHex = iso3PinBlockKeyFieldDecode.getText().trim().replaceAll("\\s+", "");

            if (pinBlockHex.isEmpty() || pan.isEmpty()) {
                iso3DecodeResultArea.setText("❌ Error: PIN Block and PAN are required");
                return;
            }

            byte[] pinBlock = DataConverter.hexToBytes(pinBlockHex);

            // Decrypt if key provided
            if (!keyHex.isEmpty()) {
                try {
                    byte[] key = DataConverter.hexToBytes(keyHex);
                    pinBlock = decryptPinBlock(key, pinBlock);
                } catch (Exception e) {
                    iso3DecodeResultArea.setText("❌ Decryption failed: " + e.getMessage());
                    return;
                }
            }

            // Decode PIN block
            String pin = PinBlock.decodePinblockIso3(pinBlock, pan);

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 3 - Decoded\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("Encrypted PIN Block: ").append(pinBlockHex.toUpperCase()).append("\n");
            if (!keyHex.isEmpty()) {
                result.append("Decryption Key:      ").append(keyHex.toUpperCase()).append("\n");
                result.append("Clear PIN Block:     ").append(DataConverter.bytesToHex(pinBlock).toUpperCase()).append("\n");
            }
            result.append("PAN:                 ").append(pan).append("\n");
            result.append("\n🔐 Decoded PIN: ").append(pin).append("\n");

            iso3DecodeResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 3 - Decode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN Block: " + pinBlockHex + ", PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "PIN: " + pin, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso3DecodeResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    // ==================== ISO 4 HANDLERS ====================

    public void handleEncodeIso4() {
        try {
            String pin = iso4PinField.getText().trim();
            String pan = iso4PanFieldEncode.getText().trim().replaceAll("\\s+", "");
            String keyHex = iso4PinBlockKeyField.getText().trim().replaceAll("\\s+", "");

            if (pin.isEmpty() || pan.isEmpty() || keyHex.isEmpty()) {
                iso4ResultArea.setText("❌ Error: PIN, PAN and Encryption Key are required for ISO 4");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);

            // Generate clear PIN field first (for debugging/educational purposes)
            byte[] pinField = PinBlock.encodePinFieldIso4(pin);
            String pinFieldHex = DataConverter.bytesToHex(pinField).toUpperCase();

            // Encipher PIN block (ISO 4 requires AES encryption)
            byte[] encryptedPinBlock = PinBlock.encipherPinblockIso4(key, pin, pan);
            String encryptedHex = DataConverter.bytesToHex(encryptedPinBlock).toUpperCase();

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 4 (AES)\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("PIN:          ").append(pin).append("\n");
            result.append("PAN:          ").append(pan).append("\n");
            result.append("AES Key:      ").append(keyHex.toUpperCase()).append("\n\n");
            result.append("Clear PIN Field:  ").append(pinFieldHex).append("\n");
            result.append("  └─ Starts with '4' (format identifier)\n");
            result.append("  └─ 16 bytes (32 hex chars)\n\n");
            result.append("Encrypted PIN Block: ").append(encryptedHex).append("\n");
            result.append("  └─ Double AES encryption\n");
            result.append("  └─ 16 bytes (32 hex chars)\n");
            result.append("  └─ Use this for decode\n");

            iso4ResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 4 - Encode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN: " + pin + ", PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", encryptedHex, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso4ResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    public void handleDecodeIso4() {
        try {
            String pinBlockHex = iso4PinBlockFieldDecode.getText().trim().replaceAll("\\s+", "");
            String pan = iso4PanFieldDecode.getText().trim().replaceAll("\\s+", "");
            String keyHex = iso4PinBlockKeyFieldDecode.getText().trim().replaceAll("\\s+", "");

            if (pinBlockHex.isEmpty() || pan.isEmpty() || keyHex.isEmpty()) {
                iso4DecodeResultArea.setText("❌ Error: PIN Block, PAN and Decryption Key are required for ISO 4");
                return;
            }

            // Validate PIN block length (must be 32 hex chars = 16 bytes)
            if (pinBlockHex.length() != 32) {
                iso4DecodeResultArea.setText("❌ Error: ISO 4 PIN Block must be exactly 32 hexadecimal characters (16 bytes)\n" +
                                           "Current length: " + pinBlockHex.length() + " characters\n\n" +
                                           "Note: ISO 4 uses 16-byte blocks, unlike ISO 0/2/3 which use 8-byte blocks.");
                return;
            }

            byte[] key = DataConverter.hexToBytes(keyHex);
            byte[] pinBlock = DataConverter.hexToBytes(pinBlockHex);

            // Decipher PIN block (ISO 4 requires AES decryption)
            String pin = PinBlock.decipherPinblockIso4(key, pinBlock, pan);

            StringBuilder result = new StringBuilder();
            result.append("✅ PIN Block ISO Format 4 - Decoded\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("Encrypted PIN Block: ").append(pinBlockHex.toUpperCase()).append("\n");
            result.append("  └─ 16 bytes (32 hex chars)\n\n");
            result.append("Decryption Key:      ").append(keyHex.toUpperCase()).append("\n");
            result.append("PAN:                 ").append(pan).append("\n");
            result.append("\n🔐 Decoded PIN: ").append(pin).append("\n");
            result.append("\nℹ️  The decrypted PIN field starts with '4' (format identifier)\n");

            iso4DecodeResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("PIN Block ISO 4 - Decode")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PIN Block: " + pinBlockHex + ", PAN: " + pan, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "PIN: " + pin, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            iso4DecodeResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    // ==================== IBM 3624 HANDLERS ====================

    public void handleGenerateIbm3624Pin() {
        try {
            String pvkHex = ibm3624PvkField.getText().trim().replaceAll("\\s+", "");
            String convTable = ibm3624ConvTableField.getText().trim();
            String offset = ibm3624OffsetField.getText().trim();
            String pan = ibm3624PanField.getText().trim().replaceAll("\\s+", "");
            String panOffsetStr = ibm3624PanOffsetField.getText().trim();
            String panLengthStr = ibm3624PanLengthField.getText().trim();
            String panPad = ibm3624PanPadField.getText().trim();

            if (pvkHex.isEmpty() || convTable.isEmpty() || offset.isEmpty() || pan.isEmpty()) {
                ibm3624ResultArea.setText("❌ Error: PVK, Conversion Table, Offset and PAN are required");
                return;
            }

            byte[] pvk = DataConverter.hexToBytes(pvkHex);
            int panOffset = Integer.parseInt(panOffsetStr.isEmpty() ? "0" : panOffsetStr);
            int panLength = Integer.parseInt(panLengthStr.isEmpty() ? "12" : panLengthStr);
            if (panPad.isEmpty()) panPad = "0";

            // Generate PIN
            String pin = Pin.generateIbm3624Pin(pvk, convTable, offset, pan, panOffset, panLength, panPad);

            StringBuilder result = new StringBuilder();
            result.append("✅ IBM 3624 PIN Generated\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("PVK (Hex):         ").append(pvkHex.toUpperCase()).append("\n");
            result.append("Conversion Table:  ").append(convTable).append("\n");
            result.append("Offset:            ").append(offset).append("\n");
            result.append("PAN:               ").append(pan).append("\n");
            result.append("PAN Verify Offset: ").append(panOffset).append("\n");
            result.append("PAN Verify Length: ").append(panLength).append("\n");
            result.append("PAN Pad:           ").append(panPad).append("\n");
            result.append("\n🔐 Generated PIN: ").append(pin).append("\n");

            ibm3624ResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("IBM 3624 - Generate PIN")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PAN: " + pan + ", Offset: " + offset, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "PIN: " + pin, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            ibm3624ResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    public void handleGenerateIbm3624Offset() {
        try {
            String pvkHex = ibm3624PvkFieldOffset.getText().trim().replaceAll("\\s+", "");
            String convTable = ibm3624ConvTableFieldOffset.getText().trim();
            String pin = ibm3624PinFieldOffset.getText().trim();
            String pan = ibm3624PanFieldOffset.getText().trim().replaceAll("\\s+", "");
            String panOffsetStr = ibm3624PanOffsetFieldOffset.getText().trim();
            String panLengthStr = ibm3624PanLengthFieldOffset.getText().trim();
            String panPad = ibm3624PanPadFieldOffset.getText().trim();

            if (pvkHex.isEmpty() || convTable.isEmpty() || pin.isEmpty() || pan.isEmpty()) {
                ibm3624OffsetResultArea.setText("❌ Error: PVK, Conversion Table, PIN and PAN are required");
                return;
            }

            byte[] pvk = DataConverter.hexToBytes(pvkHex);
            int panOffset = Integer.parseInt(panOffsetStr.isEmpty() ? "0" : panOffsetStr);
            int panLength = Integer.parseInt(panLengthStr.isEmpty() ? "12" : panLengthStr);
            if (panPad.isEmpty()) panPad = "0";

            // Generate Offset
            String offset = Pin.generateIbm3624Offset(pvk, convTable, pin, pan, panOffset, panLength, panPad);

            StringBuilder result = new StringBuilder();
            result.append("✅ IBM 3624 Offset Generated\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("PVK (Hex):         ").append(pvkHex.toUpperCase()).append("\n");
            result.append("Conversion Table:  ").append(convTable).append("\n");
            result.append("PIN:               ").append(pin).append("\n");
            result.append("PAN:               ").append(pan).append("\n");
            result.append("PAN Verify Offset: ").append(panOffset).append("\n");
            result.append("PAN Verify Length: ").append(panLength).append("\n");
            result.append("PAN Pad:           ").append(panPad).append("\n");
            result.append("\n🔑 Generated Offset: ").append(offset).append("\n");

            ibm3624OffsetResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("IBM 3624 - Generate Offset")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PAN: " + pan + ", PIN: " + pin, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "Offset: " + offset, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            ibm3624OffsetResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    // ==================== VISA PVV HANDLER ====================

    public void handleGenerateVisaPvv() {
        try {
            String pvkHex = visaPvvPvkField.getText().trim().replaceAll("\\s+", "");
            String pvki = visaPvvPvkiField.getText().trim();
            String pin = visaPvvPinField.getText().trim();
            String pan = visaPvvPanField.getText().trim().replaceAll("\\s+", "");

            if (pvkHex.isEmpty() || pvki.isEmpty() || pin.isEmpty() || pan.isEmpty()) {
                visaPvvResultArea.setText("❌ Error: All fields are required");
                return;
            }

            byte[] pvk = DataConverter.hexToBytes(pvkHex);

            // Generate PVV
            String pvv = Pin.generateVisaPvv(pvk, pvki, pin, pan);

            StringBuilder result = new StringBuilder();
            result.append("✅ VISA PVV Generated\n");
            result.append("─".repeat(50)).append("\n\n");
            result.append("PVK (Hex): ").append(pvkHex.toUpperCase()).append("\n");
            result.append("PVKI:      ").append(pvki).append("\n");
            result.append("PIN:       ").append(pin).append(" (must be 4 digits)\n");
            result.append("PAN:       ").append(pan).append("\n");
            result.append("\n🔐 PVV: ").append(pvv).append("\n");
            result.append("\nℹ️  The PVV is used to verify the PIN at the issuer");

            visaPvvResultArea.setText(result.toString());

            // Add to history
            if (statusReporter != null) {
                statusReporter.publish(com.cryptocarver.model.OperationResult.forOperation("VISA PVV - Generate")
                    .details(java.util.List.of(
                        new com.cryptocarver.model.OperationDetail("Input Parameters", "PAN: " + pan + ", PIN: " + pin + ", PVKI: " + pvki, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null),
                        new com.cryptocarver.model.OperationDetail("Output", "PVV: " + pvv, com.cryptocarver.model.OperationDetail.Classification.SECRET, false, null)
                    ))
                    .build());
            }

        } catch (Exception e) {
            visaPvvResultArea.setText("❌ Error: " + e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Encrypts a PIN block using TDES ECB
     */
    private byte[] encryptPinBlock(byte[] key, byte[] pinBlock) throws Exception {
        if (key.length == 8 || key.length == 16 || key.length == 24) {
            // Use TDES
            return com.cryptocarver.pin.TDes.encryptEcbNoPadding(key, pinBlock);
        } else if (key.length == 16 || key.length == 24 || key.length == 32) {
            // Might be AES key
            throw new IllegalArgumentException("For AES encryption, use ISO 4 format");
        } else {
            throw new IllegalArgumentException("Invalid key length: " + key.length + " bytes");
        }
    }

    /**
     * Decrypts a PIN block using TDES ECB
     */
    private byte[] decryptPinBlock(byte[] key, byte[] encryptedPinBlock) throws Exception {
        if (key.length == 8 || key.length == 16 || key.length == 24) {
            // Use TDES
            return com.cryptocarver.pin.TDes.decryptEcbNoPadding(key, encryptedPinBlock);
        } else if (key.length == 16 || key.length == 24 || key.length == 32) {
            // Might be AES key
            throw new IllegalArgumentException("For AES decryption, use ISO 4 format");
        } else {
            throw new IllegalArgumentException("Invalid key length: " + key.length + " bytes");
        }
    }
}
