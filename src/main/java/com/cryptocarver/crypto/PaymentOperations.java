package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

/**
 * Payment cryptography operations (PIN blocks, CVV, MAC)
 */
public class PaymentOperations {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentOperations.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    // ==================== PIN BLOCK OPERATIONS ====================

    /** Encode with the format's historical default padding. */
    public static String encodePinBlock(String pin, String pan, String format) throws Exception {
        return encodePinBlock(pin, pan, format, null, PinBlockPadding.secureRandom());
    }

    /** Encode with an explicit fixed nibble, RANDOM_HEX or RANDOM_DECIMAL. */
    public static String encodePinBlock(String pin, String pan, String format, String padding) throws Exception {
        return encodePinBlock(pin, pan, format, padding, PinBlockPadding.secureRandom());
    }

    /** Injects random nibble generation for reproducible vectors. */
    public static String encodePinBlock(String pin, String pan, String format, String padding,
            java.util.function.IntSupplier randomDigit) throws Exception {
        PinBlockFormat selected = PinBlockFormat.fromName(format);
        if (padding != null) selected.validatePadding(padding);
        String effective = padding == null ? selected.defaultPadding() : PinBlockPadding.normalize(padding);
        java.util.function.IntSupplier digits = effective == null ? randomDigit
                : PinBlockPadding.supplier(effective, randomDigit);
        return switch (selected) {
            case ISO0, ANSI, VISA1, ECI1 -> encodePinBlockISO0(pin, pan);
            case ISO1, ECI4 -> encodePinBlockISO1(pin, pan);
            case ISO2 -> encodePinBlockISO2(pin, pan);
            case ISO3 -> encodePinBlockISO3(pin, pan);
            case ISO4 -> encodePinBlockISO4(pin, pan);
            case IBM3624 -> encodePinBlockIBM3624(pin, pan);
            case VISA2 -> encodePinBlockVISA2(pin, pan, digits);
            case VISA3 -> encodePinBlockVISA3(pin, pan, digits);
            case ECI2 -> encodePinBlockECI2(pin, digits);
            case ECI3 -> encodePinBlockECI3(pin, digits);
            case DOCUTEL -> encodePinBlockDocutel(pin, effective.equals(PinBlockPadding.RANDOM_DECIMAL)
                    ? () -> Math.floorMod(randomDigit.getAsInt(), 10) : digits);
            case DIEBOLD -> encodePinBlockDiebold(pin, digits);
            case PLUS, VISA4 -> encodePinBlockPlus(pin, pan);
            case EUROPAY -> encodePinBlockEuropay(pin, pan);
        };
    }

    /** Historical Docutel test hook: supplies decimal padding digits. */
    public static String encodePinBlock(String pin, String pan, String format,
            java.util.function.IntSupplier decimalPaddingDigit) throws Exception {
        if (PinBlockFormat.fromName(format) == PinBlockFormat.DOCUTEL)
            return encodePinBlockDocutel(pin, decimalPaddingDigit);
        return encodePinBlock(pin, pan, format, null, decimalPaddingDigit);
    }

    private static String fill(int count, java.util.function.IntSupplier digits) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            int digit = digits.getAsInt();
            if (digit < 0 || digit > 15) throw new IllegalArgumentException("PIN block padding nibble must be 0..15");
            result.append(Character.toUpperCase(Character.forDigit(digit, 16)));
        }
        return result.toString();
    }

    /** Decode a PIN using a catalogued PIN-block format. */
    public static String decodePinBlock(String pinBlock, String pan, String format) throws Exception {
        PinBlockFormat selected = PinBlockFormat.fromName(format);
        return switch (selected) {
            case ISO0, ANSI, VISA1, ECI1 -> decodePinBlockISO0(pinBlock, pan);
            case ISO1, ECI4 -> decodePinBlockISO1(pinBlock, pan);
            case ISO2 -> decodePinBlockISO2(pinBlock, pan);
            case ISO3 -> decodePinBlockISO3(pinBlock, pan);
            case ISO4 -> decodePinBlockISO4(pinBlock, pan);
            case IBM3624 -> decodePinBlockIBM3624(pinBlock, pan);
            case VISA2 -> decodePinBlockVISA2(pinBlock, pan);
            case VISA3 -> decodePinBlockVISA3(pinBlock, pan);
            case ECI2 -> decodePinBlockECI2(pinBlock);
            case ECI3 -> decodePinBlockECI3(pinBlock);
            case DOCUTEL -> decodePinBlockDocutel(pinBlock);
            case DIEBOLD -> decodePinBlockDiebold(pinBlock);
            case PLUS, VISA4 -> decodePinBlockPlus(pinBlock, pan);
            case EUROPAY -> decodePinBlockEuropay(pinBlock, pan);
        };
    }

    /**
     * ISO Format 0 (ISO 9564-1:2002 Format 0)
     * Structure: 0L PPPP PPPP PPPP FFFF
     * Where:
     * 0 = Format identifier
     * L = PIN length (1 hex digit)
     * P = PIN digits
     * F = Filler (0xF)
     */
    private static String encodePinBlockISO0(String pin, String pan) throws Exception {
        StringBuilder pinBlock = new StringBuilder();

        // Control field: 0 + PIN length
        pinBlock.append("0").append(Integer.toHexString(pin.length()).toUpperCase());

        // PIN digits
        pinBlock.append(pin);

        // Filler (0xF)
        while (pinBlock.length() < 16) {
            pinBlock.append("F");
        }

        // XOR with PAN (12 rightmost digits, excluding check digit)
        String panPart = pan.substring(pan.length() - 13, pan.length() - 1);
        panPart = "0000" + panPart; // Pad to 16 digits

        byte[] pinBlockBytes = DataConverter.hexToBytes(pinBlock.toString());
        byte[] panBytes = DataConverter.hexToBytes(panPart);

        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (byte) (pinBlockBytes[i] ^ panBytes[i]);
        }

        return DataConverter.bytesToHex(result);
    }

    /**
     * Decode ISO Format 0
     */
    private static String decodePinBlockISO0(String pinBlock, String pan) throws Exception {
        // XOR with PAN to get clear PIN block
        String panPart = pan.substring(pan.length() - 13, pan.length() - 1);
        panPart = "0000" + panPart;

        byte[] pinBlockBytes = DataConverter.hexToBytes(pinBlock);
        byte[] panBytes = DataConverter.hexToBytes(panPart);

        byte[] clearBlock = new byte[8];
        for (int i = 0; i < 8; i++) {
            clearBlock[i] = (byte) (pinBlockBytes[i] ^ panBytes[i]);
        }

        String clearPinBlock = DataConverter.bytesToHex(clearBlock);

        // Extract PIN
        int pinLength = Integer.parseInt(clearPinBlock.substring(1, 2), 16);
        String pin = clearPinBlock.substring(2, 2 + pinLength);

        return pin;
    }

    /**
     * ISO Format 1 (ISO 9564-1:2002 Format 1)
     * Structure: 1L PPPP PPPP RRRR RRRR
     * Used for offline PIN verification
     * Uses RANDOM padding (not 0xF)
     */
    private static String encodePinBlockISO1(String pin, String pan) throws Exception {
        StringBuilder pinBlock = new StringBuilder();

        // Control field: 1 + PIN length
        pinBlock.append("1").append(Integer.toHexString(pin.length()).toUpperCase());

        // PIN digits
        pinBlock.append(pin);

        // Random padding (using SecureRandom)
        java.security.SecureRandom random = new java.security.SecureRandom();
        while (pinBlock.length() < 16) {
            pinBlock.append(Integer.toHexString(random.nextInt(16)).toUpperCase());
        }

        return pinBlock.substring(0, 16);
    }

    /**
     * Decode ISO Format 1
     */
    private static String decodePinBlockISO1(String pinBlock, String pan) throws Exception {
        // Extract PIN directly (no XOR with PAN)
        int pinLength = Integer.parseInt(pinBlock.substring(1, 2), 16);
        String pin = pinBlock.substring(2, 2 + pinLength);
        return pin;
    }

    /**
     * ISO Format 2 (ISO 9564-1:2002 Format 2)
     * Structure: 2L PPPP PPPP PPPP FFFF
     * Similar to Format 1 but uses different control field
     * Does NOT use PAN for XOR
     */
    private static String encodePinBlockISO2(String pin, String pan) throws Exception {
        StringBuilder pinBlock = new StringBuilder();

        // Control field: 2 + PIN length
        pinBlock.append("2").append(Integer.toHexString(pin.length()).toUpperCase());

        // PIN digits
        pinBlock.append(pin);

        // Padding with 0xF
        while (pinBlock.length() < 16) {
            pinBlock.append("F");
        }

        return pinBlock.substring(0, 16);
    }

    /**
     * Decode ISO Format 2
     */
    private static String decodePinBlockISO2(String pinBlock, String pan) throws Exception {
        // Extract PIN directly (no XOR with PAN)
        int pinLength = Integer.parseInt(pinBlock.substring(1, 2), 16);
        String pin = pinBlock.substring(2, 2 + pinLength);
        return pin;
    }

    /**
     * ISO Format 3 (ISO 9564-1:2002 Format 3)
     * Similar to Format 0 but with different control field
     */
    private static String encodePinBlockISO3(String pin, String pan) throws Exception {
        StringBuilder pinBlock = new StringBuilder();

        // Control field: 3 + PIN length
        pinBlock.append("3").append(Integer.toHexString(pin.length()).toUpperCase());

        // PIN digits
        pinBlock.append(pin);

        // Random padding
        long random = System.nanoTime();
        String randomHex = Long.toHexString(random).toUpperCase();

        while (pinBlock.length() < 16) {
            pinBlock.append(randomHex.charAt(pinBlock.length() % randomHex.length()));
        }

        // XOR with PAN
        String panPart = pan.substring(pan.length() - 13, pan.length() - 1);
        panPart = "0000" + panPart;

        byte[] pinBlockBytes = DataConverter.hexToBytes(pinBlock.toString());
        byte[] panBytes = DataConverter.hexToBytes(panPart);

        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (byte) (pinBlockBytes[i] ^ panBytes[i]);
        }

        return DataConverter.bytesToHex(result);
    }

    /**
     * Decode ISO Format 3
     */
    private static String decodePinBlockISO3(String pinBlock, String pan) throws Exception {
        // XOR with PAN
        String panPart = pan.substring(pan.length() - 13, pan.length() - 1);
        panPart = "0000" + panPart;

        byte[] pinBlockBytes = DataConverter.hexToBytes(pinBlock);
        byte[] panBytes = DataConverter.hexToBytes(panPart);

        byte[] clearBlock = new byte[8];
        for (int i = 0; i < 8; i++) {
            clearBlock[i] = (byte) (pinBlockBytes[i] ^ panBytes[i]);
        }

        String clearPinBlock = DataConverter.bytesToHex(clearBlock);

        // Extract PIN
        int pinLength = Integer.parseInt(clearPinBlock.substring(1, 2), 16);
        String pin = clearPinBlock.substring(2, 2 + pinLength);

        return pin;
    }

    /**
     * Generate clear PIN field for ISO Format 4 (used for display)
     * Returns the clear PIN field before XOR
     */
    public static String generateClearPinFieldISO4(String pin) {
        StringBuilder clearPinBlock = new StringBuilder();

        // Control field: 4 + PIN length
        clearPinBlock.append("4").append(Integer.toHexString(pin.length()).toUpperCase());

        // PIN digits
        clearPinBlock.append(pin);

        // Fixed padding with 'A' up to position 16 (ISO 9564-1:2017)
        int fixedPaddingCount = 16 - (2 + pin.length());
        for (int i = 0; i < fixedPaddingCount; i++) {
            clearPinBlock.append("A");
        }

        // Random padding from position 16 to 32 (16 random hex digits)
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 16; i++) {
            clearPinBlock.append(Integer.toHexString(random.nextInt(16)).toUpperCase());
        }

        return clearPinBlock.toString();
    }

    /**
     * PAN field for ISO 9564-1 format 4: the first nibble is the PAN length minus 12,
     * then the PAN (left-padded with zeros to 12 digits), right-padded with zeros to 32.
     */
    public static String panFieldISO4(String pan) {
        return DataConverter.bytesToHex(com.cryptocarver.pin.PinBlock.encodePanFieldIso4(pan)).toUpperCase();
    }

    /**
     * Encode PIN block ISO-4 and return both clear fields.
     * Returns: [0] = clear PIN field, [1] = clear PAN field. Format 4 has no
     * PIN-block-in-clear: the PAN field only enters after the first AES
     * encryption, so without a key these two fields are all there is to show.
     */
    public static String[] encodePinBlockISO4WithClear(String pin, String pan) throws Exception {
        return new String[] { generateClearPinFieldISO4(pin), panFieldISO4(pan) };
    }

    /** Enciphers an ISO-4 PIN block: E_K(E_K(PIN field) XOR PAN field), AES (ISO 9564-1:2017, 9.4). */
    public static String encipherPinBlockISO4(byte[] aesKey, String pin, String pan) {
        return DataConverter.bytesToHex(com.cryptocarver.pin.PinBlock.encipherPinblockIso4(aesKey, pin, pan)).toUpperCase();
    }

    /** Reverses {@link #encipherPinBlockISO4} and returns the PIN. */
    public static String decipherPinBlockISO4(byte[] aesKey, String pinBlock, String pan) {
        return com.cryptocarver.pin.PinBlock.decipherPinblockIso4(aesKey, DataConverter.hexToBytes(pinBlock), pan);
    }

    /**
     * ISO Format 4 without a key: the clear PIN field, which is what gets enciphered.
     * The full block needs the AES key ({@link #encipherPinBlockISO4}).
     */
    private static String encodePinBlockISO4(String pin, String pan) throws Exception {
        return generateClearPinFieldISO4(pin);
    }

    /**
     * Decode ISO Format 4 from its clear PIN field. An enciphered block cannot be
     * decoded without the AES key, and XOR with the PAN field alone does not undo it.
     */
    private static String decodePinBlockISO4(String pinBlock, String pan) throws Exception {
        if (!pinBlock.startsWith("4")) {
            throw new IllegalArgumentException("ISO-4 blocks are AES-enciphered: provide the clear PIN field"
                    + " (starting with 4) or decipher it with the AES key");
        }
        return com.cryptocarver.pin.PinBlock.decodePinFieldIso4(DataConverter.hexToBytes(pinBlock));
    }

    /**
     * ANSI X9.8 Format (ECI-1)
     * Structure: 0L PPPP PPPP PPPP FFFF (with XOR)
     * IMPORTANT: Despite the name, ANSI X9.8 DOES XOR with PAN
     * It's essentially identical to ISO-0
     */
    private static String encodePinBlockANSI(String pin, String pan) throws Exception {
        // ANSI X9.8 is identical to ISO-0 (includes XOR with PAN)
        return encodePinBlockISO0(pin, pan);
    }

    /**
     * Decode ANSI X9.8
     */
    private static String decodePinBlockANSI(String pinBlock, String pan) throws Exception {
        // ANSI X9.8 is identical to ISO-0
        return decodePinBlockISO0(pinBlock, pan);
    }

    /**
     * IBM 3624 Format
     * Structure: PPPP PPPP PPPP FFFF
     * No control field, no XOR with PAN
     * Fixed 4-12 digit PIN with 0xF padding
     */
    private static String encodePinBlockIBM3624(String pin, String pan) throws Exception {
        StringBuilder pinBlock = new StringBuilder();

        // PIN digits directly (no control field)
        pinBlock.append(pin);

        // Padding with 0xF
        while (pinBlock.length() < 16) {
            pinBlock.append("F");
        }

        return pinBlock.substring(0, 16);
    }

    /**
     * Decode IBM 3624
     */
    private static String decodePinBlockIBM3624(String pinBlock, String pan) throws Exception {
        // Extract PIN until first 0xF
        int endIndex = pinBlock.indexOf('F');
        if (endIndex == -1) {
            endIndex = pinBlock.indexOf('f');
        }

        if (endIndex == -1) {
            // No padding found, entire block is PIN
            return pinBlock;
        }

        return pinBlock.substring(0, endIndex);
    }

    /**
     * VISA-1 Format (VISA PVV)
     * Structure: Same as ISO-0 but used specifically for VISA PVV verification
     * 0L PPPP PPPP PPPP FFFF XOR with PAN
     */
    private static String encodePinBlockVISA1(String pin, String pan) throws Exception {
        // VISA-1 is identical to ISO-0
        return encodePinBlockISO0(pin, pan);
    }

    /**
     * Decode VISA-1
     */
    private static String decodePinBlockVISA1(String pinBlock, String pan) throws Exception {
        // VISA-1 is identical to ISO-0
        return decodePinBlockISO0(pinBlock, pan);
    }

    /**
     * Visa format 2.  This is the legacy Visa clear PIN block described by the
     * public XFS4IoT pin-pad contract and IBM's PIN profile documentation: a
     * one-nibble length (4..6), the PIN, zero fill to six digits, then one
     * decimal pad digit repeated to the end of the 8-byte block.  It does not
     * bind the block to a PAN.
     */
    private static String encodePinBlockVISA2(String pin, String ignoredPan, java.util.function.IntSupplier digits) {
        requirePin(pin, 4, 6);
        StringBuilder block = new StringBuilder(16);
        block.append(Integer.toHexString(pin.length())).append(pin);
        while (block.length() < 7) block.append('0');
        int pad = digits.getAsInt();
        block.append(fill(16 - block.length(), () -> pad));
        return block.toString().toUpperCase(java.util.Locale.ROOT);
    }

    private static String decodePinBlockVISA2(String pinBlock, String ignoredPan) {
        String block = normalizeLegacyBlock(pinBlock);
        int length = Character.digit(block.charAt(0), 16);
        if (length < 4 || length > 6)
            throw new IllegalArgumentException("VISA-2 PIN length must be 4..6");
        String pin = block.substring(1, 1 + length);
        requirePin(pin, 4, 6);
        // Visa-2 uses decimal padding, and all padding nibbles must agree.
        char pad = block.charAt(7);
        if (pad < '0' || pad > '9')
            throw new IllegalArgumentException("VISA-2 padding must be decimal");
        for (int i = 7; i < block.length(); i++)
            if (block.charAt(i) != pad)
                throw new IllegalArgumentException("VISA-2 padding is inconsistent");
        return pin;
    }

    /** Visa format 3: PIN, F delimiter, then a repeated hexadecimal pad. */
    private static String encodePinBlockVISA3(String pin, String ignoredPan, java.util.function.IntSupplier digits) {
        requirePin(pin, 4, 12);
        int pad = digits.getAsInt();
        return (pin + "F" + fill(15 - pin.length(), () -> pad)).toUpperCase(java.util.Locale.ROOT);
    }

    private static String decodePinBlockVISA3(String pinBlock, String ignoredPan) {
        String block = normalizeLegacyBlock(pinBlock);
        int delimiter = block.indexOf('F', 4);
        if (delimiter < 4 || delimiter > 12)
            throw new IllegalArgumentException("VISA-3 delimiter is missing or out of range");
        String pin = block.substring(0, delimiter);
        requirePin(pin, 4, 12);
        char pad = delimiter + 1 < block.length() ? block.charAt(delimiter + 1) : '0';
        for (int i = delimiter + 1; i < block.length(); i++)
            if (block.charAt(i) != pad)
                throw new IllegalArgumentException("VISA-3 padding is inconsistent");
        return pin;
    }

    /** ECI-2 is the fixed four-digit, left-justified no-PAN form. */
    private static String encodePinBlockECI2(String pin, java.util.function.IntSupplier digits) {
        requirePin(pin, 4, 4);
        return (pin + fill(12, digits)).toUpperCase(java.util.Locale.ROOT);
    }

    private static String decodePinBlockECI2(String pinBlock) {
        String block = normalizeLegacyBlock(pinBlock);
        String pin = block.substring(0, 4);
        requirePin(pin, 4, 4);
        // Encoders fill with F, but the external tool fills with random hex; any padding is accepted.
        return pin;
    }

    /** ECI-3 is the length-prefixed 4..6 digit no-PAN form. */
    private static String encodePinBlockECI3(String pin, java.util.function.IntSupplier digits) {
        requirePin(pin, 4, 6);
        return (Integer.toHexString(pin.length()) + pin + fill(15 - pin.length(), digits))
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static String decodePinBlockECI3(String pinBlock) {
        String block = normalizeLegacyBlock(pinBlock);
        int length = Character.digit(block.charAt(0), 16);
        if (length < 4 || length > 6)
            throw new IllegalArgumentException("ECI-3 PIN length must be 4..6");
        String pin = block.substring(1, 1 + length);
        requirePin(pin, 4, 6);
        // Encoders fill with F, but the external tool fills with random hex; any padding is accepted.
        return pin;
    }

    /**
     * Docutel format 02: nibble 0 is PIN length (4..6); nibbles 1..6 are the PIN
     * left-justified and zero-padded to six digits; nibbles 7..15 are nine
     * user-supplied decimal pad digits. No PAN is involved.
     * Source: payShield Host Programmer's Manual (1270A542-038 v3.5), format 02, p. 171.
     */
    private static String encodePinBlockDocutel(String pin, java.util.function.IntSupplier padding) {
        requirePin(pin, 4, 6);
        StringBuilder block = new StringBuilder(16).append(pin.length()).append(pin);
        while (block.length() < 7) block.append('0');
        while (block.length() < 16) {
            int digit = padding.getAsInt();
            if (digit < 0 || digit > 9) throw new IllegalArgumentException("Docutel padding digit must be 0..9");
            block.append(digit);
        }
        return block.toString();
    }

    private static String decodePinBlockDocutel(String pinBlock) {
        String block = normalizeLegacyBlock(pinBlock);
        int length = Character.digit(block.charAt(0), 16);
        if (length < 4 || length > 6) throw new IllegalArgumentException("Docutel PIN length must be 4..6");
        String pin = block.substring(1, 1 + length);
        requirePin(pin, 4, 6);
        if (!block.substring(1 + length, 7).equals("0".repeat(6 - length)) || !block.substring(7).matches("[0-9]{9}"))
            throw new IllegalArgumentException("Docutel zero fill or decimal padding is invalid");
        return pin;
    }

    /**
     * Diebold format 03: nibbles 0..L-1 contain 4..12 PIN digits; all remaining
     * nibbles through position 15 are F. No PIN-length nibble or PAN is used.
     * Source: payShield Host Programmer's Manual (1270A542-038 v3.5), format 03, p. 171.
     */
    private static String encodePinBlockDiebold(String pin, java.util.function.IntSupplier digits) {
        requirePin(pin, 4, 12);
        return pin + fill(16 - pin.length(), digits);
    }

    private static String decodePinBlockDiebold(String pinBlock) {
        String block = normalizeLegacyBlock(pinBlock);
        int length = block.indexOf('F');
        if (length < 4 || length > 12 || !block.substring(length).equals("F".repeat(16 - length)))
            throw new IllegalArgumentException("Diebold PIN length must be 4..12 with F padding");
        String pin = block.substring(0, length);
        requirePin(pin, 4, 12);
        return pin;
    }

    /**
     * Plus Network format 04: PIN field is nibbles [0]=0, [1]=PIN length
     * (4..12), [2..] PIN digits, then F to nibble 15. PAN field is four zero
     * nibbles followed by the leftmost 12 digits of the PAN (excluding its
     * check digit). The clear block is the nibble-wise XOR of those fields.
     * Source: payShield Host Programmer's Manual (1270A542-038 v3.5), format 04, p. 172.
     */
    private static String encodePinBlockPlus(String pin, String pan) {
        requirePin(pin, 4, 12);
        String field = "0" + Integer.toHexString(pin.length()).toUpperCase(java.util.Locale.ROOT)
                + pin + "F".repeat(14 - pin.length());
        return xorPinFields(field, plusPanField(pan));
    }

    private static String decodePinBlockPlus(String pinBlock, String pan) {
        String field = xorPinFields(normalizeLegacyBlock(pinBlock), plusPanField(pan));
        int length = Character.digit(field.charAt(1), 16);
        if (field.charAt(0) != '0' || length < 4 || length > 12
                || !field.substring(2 + length).equals("F".repeat(14 - length)))
            throw new IllegalArgumentException("Plus Network PIN block has invalid length or padding (expected 4..12)");
        String pin = field.substring(2, 2 + length);
        requirePin(pin, 4, 12);
        return pin;
    }

    /**
     * Europay/MasterCard "Pay Now & Pay Later": ISO-0 with control nibble 2 instead of 0.
     * Checked against external tool captures (docs/CAPTURAS_PIN_BLOCKS_HEREDADOS.md).
     */
    private static String encodePinBlockEuropay(String pin, String pan) throws Exception {
        requirePin(pin, 4, 12);
        String iso0 = encodePinBlockISO0(pin, pan);
        return Integer.toHexString(Character.digit(iso0.charAt(0), 16) ^ 2).toUpperCase(java.util.Locale.ROOT) + iso0.substring(1);
    }

    private static String decodePinBlockEuropay(String pinBlock, String pan) throws Exception {
        String block = normalizeLegacyBlock(pinBlock);
        if (block.charAt(0) != '2') throw new IllegalArgumentException("Europay/MasterCard PIN block must start with control nibble 2");
        String pin = decodePinBlockISO0("0" + block.substring(1), pan);
        requirePin(pin, 4, 12);
        return pin;
    }

    private static String plusPanField(String pan) {
        if (pan == null || !pan.matches("[0-9]{13,19}"))
            throw new IllegalArgumentException("Plus Network requires a 13..19 digit PAN including check digit");
        return "0000" + pan.substring(0, 12);
    }

    private static String xorPinFields(String first, String second) {
        byte[] a = DataConverter.hexToBytes(first);
        byte[] b = DataConverter.hexToBytes(second);
        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) result[i] = (byte) (a[i] ^ b[i]);
        return DataConverter.bytesToHex(result);
    }

    private static String normalizeLegacyBlock(String pinBlock) {
        if (pinBlock == null || !pinBlock.matches("[0-9A-Fa-f]{16}"))
            throw new IllegalArgumentException("PIN block must contain exactly 16 hexadecimal digits");
        return pinBlock.toUpperCase(java.util.Locale.ROOT);
    }

    private static void requirePin(String pin, int min, int max) {
        if (pin == null || pin.length() < min || pin.length() > max || !pin.matches("[0-9]+"))
            throw new IllegalArgumentException("PIN must contain " + min + ".." + max + " decimal digits");
    }

    // ==================== CVV OPERATIONS ====================

    /**
     * Generate CVV (Card Verification Value)
     * Algorithm: Visa/MasterCard CVV generation as per industry standard
     *
     * @param cvkA        CVK Part A (8 bytes / 16 hex characters)
     * @param cvkB        CVK Part B (8 bytes / 16 hex characters)
     * @param pan         Primary Account Number
     * @param expiry      Expiry date in YYMM format
     * @param serviceCode Service code (3 digits)
     * @return CVV value (3 digits)
     *
     *         Notes:
     *         - CVV1: Magnetic stripe (service code from track data)
     *         - CVV2: Card printed (service code 000)
     *         - iCVV: Chip (service code 999)
     */
    /**
     * Verifies a CVV/CVV2/iCVV.
     */
    public static boolean verifyCVV(String cvkA, String cvkB, String pan, String expiryDate, String serviceCode,
            String inputCvv) {
        try {
            String calculatedCvv = generateCVV(cvkA, cvkB, pan, expiryDate, serviceCode);
            return calculatedCvv.equals(inputCvv);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generates a Visa dynamic CVV (dCVV) for contactless magnetic-stripe-data transactions.
     *
     * <p>The card key is derived from the issuer MDK (CVK A || CVK B) with EMV option A
     * over PAN and PAN sequence number. The data are those of the CVV with the ATC
     * overlaid on the leftmost digits of the PAN (US 8387866 B2): ATC || PAN[4..],
     * expiry and service code, zero-padded on the right. Five external tool captures
     * pin it (PaymentControlValuesTest).</p>
     */
    public static String generateDCVV(String mdkA, String mdkB, String pan, String panSeq, String expiryDate,
            String serviceCode, String atc) throws Exception {
        if (atc == null || !atc.matches("[0-9A-Fa-f]{1,4}")) {
            throw new IllegalArgumentException("ATC must be 1 to 4 hexadecimal digits");
        }
        if (pan == null || !pan.matches("\\d{13,19}")) {
            throw new IllegalArgumentException("PAN must be 13 to 19 digits");
        }
        String paddedAtc = "0".repeat(4 - atc.length()) + atc.toUpperCase();
        String psn = panSeq == null || panSeq.isBlank() ? "00" : panSeq;
        String udk = EMVOperations.deriveICCMasterKey(mdkA + mdkB, pan, psn);
        return generateCVV(udk.substring(0, 16), udk.substring(16, 32), paddedAtc + pan.substring(4), expiryDate, serviceCode);
    }

    public static boolean verifyDCVV(String mdkA, String mdkB, String pan, String panSeq, String expiryDate,
            String serviceCode, String atc, String inputCvv) {
        try {
            return generateDCVV(mdkA, mdkB, pan, panSeq, expiryDate, serviceCode, atc).equals(inputCvv);
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateCVV(String cvkA, String cvkB, String pan, String expiry, String serviceCode)
            throws Exception {
        // Validate input lengths
        if (cvkA.length() != 16) {
            throw new IllegalArgumentException("CVK A must be exactly 16 hexadecimal characters (8 bytes)");
        }
        if (cvkB.length() != 16) {
            throw new IllegalArgumentException("CVK B must be exactly 16 hexadecimal characters (8 bytes)");
        }

        // Build block: PAN + Expiry + Service Code, padded to 32 hex characters (16
        // bytes)
        String block = (pan + expiry + serviceCode);
        while (block.length() < 32) {
            block += "0";
        }

        // Convert to bytes
        byte[] block1 = DataConverter.hexToBytes(block.substring(0, 16)); // First 8 bytes
        byte[] block2 = DataConverter.hexToBytes(block.substring(16, 32)); // Second 8 bytes

        // Step 1: Encrypt first block with CVK A (single DES)
        byte[] cvkABytes = DataConverter.hexToBytes(cvkA);
        SecretKeySpec keyA = new SecretKeySpec(cvkABytes, "DES");
        Cipher cipherA = Cipher.getInstance("DES/ECB/NoPadding", "BC");
        cipherA.init(Cipher.ENCRYPT_MODE, keyA);
        byte[] result = cipherA.doFinal(block1);

        // Step 2: XOR result with second block
        for (int i = 0; i < 8; i++) {
            result[i] ^= block2[i];
        }

        // Step 3: Encrypt with Triple DES using CVK A + CVK B
        byte[] cvkFull = new byte[16];
        System.arraycopy(cvkABytes, 0, cvkFull, 0, 8);
        System.arraycopy(DataConverter.hexToBytes(cvkB), 0, cvkFull, 8, 8);

        SecretKeySpec keyFull = new SecretKeySpec(cvkFull, "DESede");
        Cipher cipher3DES = Cipher.getInstance("DESede/ECB/NoPadding", "BC");
        cipher3DES.init(Cipher.ENCRYPT_MODE, keyFull);
        result = cipher3DES.doFinal(result);

        // Step 4: Decimalize - the digits 0-9 left to right, then, if fewer than three,
        // the letters A-F left to right, each minus 10
        String hexResult = DataConverter.bytesToHex(result).toUpperCase();
        StringBuilder digits = new StringBuilder();
        for (char c : hexResult.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
        }
        for (char c : hexResult.toCharArray()) {
            if (!Character.isDigit(c)) digits.append((char) ('0' + (c - 'A')));
        }
        return digits.substring(0, 3);
    }

    // ==================== MAC OPERATIONS ====================

    /**
     * Generate MAC (Message Authentication Code)
     */
    public static String generateMAC(String macKey, String data, String algorithm) throws Exception {
        byte[] keyBytes = DataConverter.hexToBytes(macKey);
        byte[] dataBytes = DataConverter.hexToBytes(data);

        switch (algorithm) {
            case "Retail MAC (ISO 9797-1 Alg 3)":
                return generateRetailMAC(keyBytes, dataBytes);
            case "CBC-MAC (ISO 9797-1 Alg 1)":
                return generateCBCMAC(keyBytes, dataBytes);
            case "CMAC-TDES (ISO 9797-1 Alg 5)":
                return generateCMAC(keyBytes, dataBytes, false);
            case "CMAC-AES (ISO 9797-1 Alg 5)":
            case "CMAC (ISO 9797-1 Alg 5)": // label saved before the cipher was explicit; it always meant AES
                return generateCMAC(keyBytes, dataBytes, true);
            case "HMAC-SHA256":
                return generateHMAC(keyBytes, dataBytes);
            case "ISO-9797-1-ALG2":
            case "ISO-9797-1-ALG4":
            case "ISO-9797-1-ALG6":
                return DataConverter.bytesToHex(MACOperations.generate(dataBytes, keyBytes, algorithm));
            default:
                return generateRetailMAC(keyBytes, dataBytes);
        }
    }

    /**
     * Retail MAC (ISO 9797-1 Algorithm 3)
     * Also known as DES-MAC or Triple-DES MAC with outer CBC-MAC
     */
    private static String generateRetailMAC(byte[] key, byte[] data) throws Exception {
        // Pad data to multiple of 8 bytes (ISO padding)
        byte[] paddedData = padISO(data);

        // Split key into K1 and K2 (first 8 and second 8 bytes)
        byte[] k1 = new byte[8];
        byte[] k2 = new byte[8];
        System.arraycopy(key, 0, k1, 0, 8);
        System.arraycopy(key, 8, k2, 0, 8);

        // CBC-MAC with K1
        SecretKeySpec keySpec1 = new SecretKeySpec(k1, "DES");
        Cipher cipher1 = Cipher.getInstance("DES/CBC/NoPadding", "BC");
        cipher1.init(Cipher.ENCRYPT_MODE, keySpec1, new javax.crypto.spec.IvParameterSpec(new byte[8]));
        byte[] mac1 = cipher1.doFinal(paddedData);

        // Take last block
        byte[] lastBlock = new byte[8];
        System.arraycopy(mac1, mac1.length - 8, lastBlock, 0, 8);

        // Decrypt with K2
        SecretKeySpec keySpec2 = new SecretKeySpec(k2, "DES");
        Cipher cipher2 = Cipher.getInstance("DES/ECB/NoPadding", "BC");
        cipher2.init(Cipher.DECRYPT_MODE, keySpec2);
        byte[] decrypted = cipher2.doFinal(lastBlock);

        // Encrypt again with K1
        cipher1 = Cipher.getInstance("DES/ECB/NoPadding", "BC");
        cipher1.init(Cipher.ENCRYPT_MODE, keySpec1);
        byte[] mac = cipher1.doFinal(decrypted);

        // Return first 4 bytes (8 hex chars)
        return DataConverter.bytesToHex(mac).substring(0, 8);
    }

    /**
     * CBC-MAC (ISO 9797-1 Algorithm 1)
     */
    private static String generateCBCMAC(byte[] key, byte[] data) throws Exception {
        byte[] paddedData = padISO(data);

        SecretKeySpec keySpec = new SecretKeySpec(key, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new javax.crypto.spec.IvParameterSpec(new byte[8]));
        byte[] mac = cipher.doFinal(paddedData);

        // Return last block (first 8 hex chars)
        return DataConverter.bytesToHex(mac).substring(mac.length * 2 - 16, mac.length * 2 - 8);
    }

    /**
     * CMAC (ISO 9797-1 Algorithm 5 / NIST SP 800-38B) with the whole key: AES-128/192/256
     * or two- and three-key TDES. A 16-byte key is valid for both ciphers, so the caller
     * has to say which one; guessing is how a TDES key ended up computing an AES-CMAC.
     */
    private static String generateCMAC(byte[] key, byte[] data, boolean aes) throws Exception {
        if (aes && key.length != 16 && key.length != 24 && key.length != 32)
            throw new IllegalArgumentException("CMAC-AES requires a 16, 24 or 32-byte key. Actual: " + key.length);
        if (!aes && key.length != 16 && key.length != 24)
            throw new IllegalArgumentException("CMAC-TDES requires a 16 or 24-byte key. Actual: " + key.length);
        org.bouncycastle.crypto.macs.CMac cmac = new org.bouncycastle.crypto.macs.CMac(aes
                ? new org.bouncycastle.crypto.engines.AESEngine()
                : new org.bouncycastle.crypto.engines.DESedeEngine());
        cmac.init(new org.bouncycastle.crypto.params.KeyParameter(key));
        cmac.update(data, 0, data.length);
        byte[] result = new byte[cmac.getMacSize()];
        cmac.doFinal(result, 0);

        // Return first 4 bytes (8 hex chars)
        return DataConverter.bytesToHex(result).substring(0, 8);
    }

    /**
     * HMAC-SHA256
     */
    private static String generateHMAC(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(keySpec);
        byte[] result = mac.doFinal(data);

        // Return first 4 bytes (8 hex chars)
        return DataConverter.bytesToHex(result).substring(0, 8);
    }

    /**
     * ISO padding (Method 2): Add 0x80 followed by 0x00
     */
    private static byte[] padISO(byte[] data) {
        int blockSize = 8;
        int paddingLength = blockSize - (data.length % blockSize);

        byte[] padded = new byte[data.length + paddingLength];
        System.arraycopy(data, 0, padded, 0, data.length);

        // Add 0x80
        padded[data.length] = (byte) 0x80;

        // Rest is already 0x00
        return padded;
    }

    // ==================== PIN TRANSLATION ====================

    /**
     * Translate PIN block from one format to another
     *
     * @param pinBlock     Source PIN block (hex)
     * @param pan          Primary Account Number
     * @param sourceFormat Source format name
     * @param targetFormat Target format name
     * @return Translated PIN block in target format
     */
    public static String translatePinBlock(String pinBlock, String pan,
            String sourceFormat, String targetFormat) throws Exception {
        // Step 1: Decode PIN from source format
        String pin = decodePinBlock(pinBlock, pan, sourceFormat);

        // Step 2: Encode PIN to target format
        String translatedBlock = encodePinBlock(pin, pan, targetFormat);

        return translatedBlock;
    }

    /** Translate using an explicit padding selection for the target format. */
    public static String translatePinBlock(String pinBlock, String pan,
            String sourceFormat, String targetFormat, String padding) throws Exception {
        return encodePinBlock(decodePinBlock(pinBlock, pan, sourceFormat), pan, targetFormat, padding);
    }

    /**
     * Get detailed translation information
     */
    public static String getTranslationDetails(String pinBlock, String pan,
            String sourceFormat, String targetFormat) throws Exception {
        StringBuilder result = new StringBuilder();

        result.append("═══ PIN BLOCK TRANSLATION ═══\n\n");
        result.append("Source Format: ").append(sourceFormat).append("\n");
        result.append("Source PIN Block: ").append(pinBlock).append("\n");
        result.append("PAN: ").append(pan).append("\n\n");

        if (doesNotBindPan(sourceFormat) || doesNotBindPan(targetFormat)) {
            result.append("WARNING: ");
            if (doesNotBindPan(sourceFormat))
                result.append(sourceFormat).append(" does not bind the PIN block to the PAN");
            if (doesNotBindPan(sourceFormat) && doesNotBindPan(targetFormat))
                result.append("; ");
            if (doesNotBindPan(targetFormat))
                result.append(targetFormat).append(" does not bind the PIN block to the PAN");
            result.append(". This permits PIN-block relocation between accounts.\n\n");
        }

        // Decode
        String pin = decodePinBlock(pinBlock, pan, sourceFormat);
        result.append("Extracted PIN: ").append(pin).append("\n");
        result.append("PIN Length: ").append(pin.length()).append(" digits\n\n");

        // Encode to target
        String translatedBlock = encodePinBlock(pin, pan, targetFormat);
        result.append("Target Format: ").append(targetFormat).append("\n");
        result.append("Translated PIN Block: ").append(translatedBlock).append("\n\n");

        // Binary representation
        result.append("Source Binary: ").append(hexToBinary(pinBlock)).append("\n");
        result.append("Target Binary: ").append(hexToBinary(translatedBlock)).append("\n");

        return result.toString();
    }

    private static boolean doesNotBindPan(String format) {
        if (format == null) return false;
        return switch (format) {
            case "Format 1 (ISO-1)", "ISO 1 (ANSI X9.8)", "Format 2 (ISO-2)",
                    "ISO 2 (No PAN)", "ECI-2", "ECI-2 (no PAN binding)", "ECI-3",
                    "ECI-3 (no PAN binding)", "ECI-4", "VISA-2", "VISA-3",
                    "IBM 3624" -> true;
            default -> false;
        };
    }

    // ==================== PVV (PIN VERIFICATION VALUE) ====================

    /**
     * Generate PVV (PIN Verification Value) using IBM algorithm
     *
     * PVV Algorithm:
     * 1. Take rightmost 11 digits of PAN (excluding check digit)
     * 2. Pad with 0 on left to make 16 digits
     * 3. Encrypt with PVK (PIN Verification Key) using 3DES
     * 4. Append PIN to encrypted result
     * 5. Encrypt again with PVK
     * 6. Extract chosen digits from result
     *
     * @param pin       PIN (4-12 digits)
     * @param pan       Primary Account Number
     * @param pvk       PIN Verification Key (hex, 16 or 32 bytes for 3DES)
     * @param pvvLength Length of PVV to generate (typically 4)
     * @return PVV value
     */
    public static String generatePVV(String pin, String pan, String pvk, String pvki, int pvvLength) throws Exception {
        // Validate inputs
        if (pin == null || pin.length() != 4) {
            throw new IllegalArgumentException("PIN must be 4 digits");
        }
        if (pan == null || pan.length() < 13) {
            throw new IllegalArgumentException("PAN must be at least 13 digits");
        }
        if (pvki == null || pvki.length() != 1) {
            pvki = "0"; // Default to 0 if null
        }
        if (pvvLength < 4 || pvvLength > 6) {
            pvvLength = 4; // Default to 4
        }

        // Standard VISA PVV Algorithm (Method 1)

        // Step 1: TSP Construction
        // TSP = PAN (rightmost 11 digits excluding check digit) + PVKI (1 digit) + PIN
        // (4 digits)
        String panDigits = pan.replaceAll("[^0-9]", "");
        if (panDigits.length() < 13)
            throw new IllegalArgumentException("Invalid PAN length");

        // Rightmost 11 digits excluding check digit (which is at length-1)
        // start index = length - 12, end index = length - 1
        String pan11 = panDigits.substring(panDigits.length() - 12, panDigits.length() - 1);

        String tspInput = pan11 + pvki + pin; // Should be 16 digits

        // Step 2: Encrypt TSP with PVK (TDES)
        byte[] pvkBytes = DataConverter.hexToBytes(pvk);
        byte[] tspBytes = DataConverter.hexToBytes(tspInput);

        // Use TDES (Key A/B/A if 16 bytes)
        byte[] encrypted = encrypt3DES(tspBytes, pvkBytes);

        // Step 3: Decimalize
        String decimalized = decimalize(encrypted);

        // Step 4: Extract PVV
        if (decimalized.length() < pvvLength) {
            throw new IllegalStateException("Decimalization failed to produce enough digits");
        }

        return decimalized.substring(0, pvvLength);
    }

    /**
     * Verify PVV
     */
    public static boolean verifyPVV(String pin, String pan, String pvk, String pvki,
            String pvvToVerify, int pvvLength) throws Exception {
        String generatedPVV = generatePVV(pin, pan, pvk, pvki, pvvLength);
        return generatedPVV.equals(pvvToVerify);
    }

    /**
     * Get detailed PVV generation information
     */
    public static String getPVVDetails(String pin, String pan, String pvk, String pvki, int pvvLength)
            throws Exception {
        StringBuilder result = new StringBuilder();

        result.append("═══ PVV GENERATION (IBM ALGORITHM) ═══\n\n");

        // Extract PAN processing

        result.append("Input:\n");
        result.append("  PIN: ").append(pin).append(" (").append(pin.length()).append(" digits)\n");
        result.append("  PAN: ").append(pan).append("\n");
        result.append("  PVK: ").append(pvk).append("\n");
        result.append("  PVV Length: ").append(pvvLength).append("\n\n");

        // Re-construct TSP for display
        String panDigits = pan.replaceAll("[^0-9]", "");
        String pan11 = panDigits.substring(panDigits.length() - 12, panDigits.length() - 1);
        String tsp = pan11 + pvki + pin;

        result.append("Processing:\n");
        result.append("  1. PAN (rightmost 11 digits): ").append(pan11).append("\n");
        result.append("  2. PVKI: ").append(pvki).append("\n");
        result.append("  3. TSP (PAN11 + PVKI + PIN): ").append(tsp).append("\n");

        // Generate PVV
        String pvv = generatePVV(pin, pan, pvk, pvki, pvvLength);

        result.append("\nResult:\n");
        result.append("  PVV: ").append(pvv).append("\n");

        return result.toString();
    }

    /**
     * Decimalize - Convert bytes to decimal digits (0-9 only)
     */
    private static String decimalize(byte[] data) {
        StringBuilder result = new StringBuilder();
        String hex = DataConverter.bytesToHex(data);

        // Pass 1: Extract 0-9
        for (char c : hex.toCharArray()) {
            if (c >= '0' && c <= '9') {
                result.append(c);
            }
        }

        // Pass 2: Extract A-FConverted (A=0, B=1, ... F=5)
        for (char c : hex.toCharArray()) {
            if (c >= 'a' && c <= 'f') {
                result.append((char) ('0' + (c - 'a')));
            } else if (c >= 'A' && c <= 'F') { // handle upper case just in case
                result.append((char) ('0' + (c - 'A')));
            }
        }

        return result.toString();
    }

    /**
     * Derive PIN from PVV (Brute Force 0000-9999)
     * Since the PIN is part of the TSP (encryption input), we cannot reverse the
     * operation mathematically.
     * We must try all 10,000 possibilities.
     */
    public static java.util.List<String> derivePinFromPvv(String pan, String pvk, String pvki, String targetPvv,
            int pvvLength) throws Exception {
        java.util.List<String> matches = new java.util.ArrayList<>();

        for (int i = 0; i < 10000; i++) {
            String candidatePin = String.format("%04d", i);
            try {
                String generatedPvv = generatePVV(candidatePin, pan, pvk, pvki, pvvLength);
                if (generatedPvv.equals(targetPvv)) {
                    matches.add(candidatePin);
                }
            } catch (Exception e) {
                // Ignore errors for specific candidates
            }
        }
        return matches;
    }

    // ==================== TRACK DATA OPERATIONS ====================

    /**
     * Encode Track 1 data
     * Track 1 format: %B{PAN}^{NAME}^{EXPIRY}{SERVICE_CODE}{DISCRETIONARY}?
     *
     * @param pan         Primary Account Number (13-19 digits)
     * @param name        Cardholder name (2-26 characters)
     * @param expiry      Expiry date YYMM
     * @param serviceCode Service code (3 digits)
     * @return Track 1 data
     */
    public static String encodeTrack1(String pan, String name, String expiry, String serviceCode) {
        return encodeTrack1(pan, name, expiry, serviceCode, "");
    }

    public static String encodeTrack1(String pan, String name, String expiry,
            String serviceCode, String discretionary) {
        // Format: %B{PAN}^{NAME}^{EXPIRY}{SERVICE_CODE}{DISCRETIONARY}?
        StringBuilder track = new StringBuilder();

        track.append("%B"); // Start sentinel and Format Code
        track.append(pan); // Primary Account Number
        track.append("^"); // Field separator

        // Name (uppercase, surname/firstname format, max 26 chars)
        String formattedName = name.toUpperCase().replace(" ", "/");
        if (formattedName.length() > 26) {
            formattedName = formattedName.substring(0, 26);
        }
        track.append(formattedName);

        track.append("^"); // Field separator
        track.append(expiry); // YYMM
        track.append(serviceCode); // 3 digits

        // Discretionary data (optional)
        if (discretionary != null && !discretionary.isEmpty()) {
            track.append(discretionary);
        }

        track.append("?"); // End sentinel

        return track.toString();
    }

    /**
     * Encode Track 2 data
     * Track 2 format: ;{PAN}={EXPIRY}{SERVICE_CODE}{DISCRETIONARY}?
     *
     * @param pan         Primary Account Number
     * @param expiry      Expiry date YYMM
     * @param serviceCode Service code (3 digits)
     * @return Track 2 data
     */
    public static String encodeTrack2(String pan, String expiry, String serviceCode) {
        return encodeTrack2(pan, expiry, serviceCode, "");
    }

    public static String encodeTrack2(String pan, String expiry, String serviceCode,
            String discretionary) {
        // Format: ;{PAN}={EXPIRY}{SERVICE_CODE}{DISCRETIONARY}?
        StringBuilder track = new StringBuilder();

        track.append(";"); // Start sentinel
        track.append(pan); // Primary Account Number
        track.append("="); // Field separator
        track.append(expiry); // YYMM
        track.append(serviceCode); // 3 digits

        // Discretionary data (optional)
        if (discretionary != null && !discretionary.isEmpty()) {
            track.append(discretionary);
        }

        track.append("?"); // End sentinel

        return track.toString();
    }

    /**
     * Parse Track 1 data
     */
    public static String parseTrack1(String track1) {
        StringBuilder result = new StringBuilder();
        result.append("═══ TRACK 1 DATA ═══\n\n");

        if (!track1.startsWith("%B") || !track1.endsWith("?")) {
            return "Invalid Track 1 format (must start with %B and end with ?)";
        }

        // Remove sentinels
        String data = track1.substring(2, track1.length() - 1);

        // Split by ^
        String[] parts = data.split("\\^");
        if (parts.length < 3) {
            return "Invalid Track 1 format (missing field separators)";
        }

        String pan = parts[0];
        String name = parts[1].replace("/", " ");
        String expiryAndRest = parts[2];

        if (expiryAndRest.length() < 7) {
            return "Invalid Track 1 format (expiry/service code too short)";
        }

        String expiry = expiryAndRest.substring(0, 4);
        String serviceCode = expiryAndRest.substring(4, 7);
        String discretionary = expiryAndRest.length() > 7 ? expiryAndRest.substring(7) : "";

        result.append("Format Code: B (Bank card)\n");
        result.append("PAN: ").append(pan).append("\n");
        result.append("Name: ").append(name).append("\n");
        result.append("Expiry: ").append(expiry).append(" (YY/MM: ")
                .append(expiry.substring(2, 4)).append("/")
                .append(expiry.substring(0, 2)).append(")\n");
        result.append("Service Code: ").append(serviceCode).append("\n");
        result.append("  - Position 1: ").append(getServiceCodePos1(serviceCode.charAt(0))).append("\n");
        result.append("  - Position 2: ").append(getServiceCodePos2(serviceCode.charAt(1))).append("\n");
        result.append("  - Position 3: ").append(getServiceCodePos3(serviceCode.charAt(2))).append("\n");

        if (!discretionary.isEmpty()) {
            result.append("Discretionary Data: ").append(discretionary).append("\n");
        }

        result.append("\nFull Track 1: ").append(track1).append("\n");

        return result.toString();
    }

    /**
     * Parse Track 2 data
     */
    public static String parseTrack2(String track2) {
        StringBuilder result = new StringBuilder();
        result.append("═══ TRACK 2 DATA ═══\n\n");

        if (!track2.startsWith(";") || !track2.endsWith("?")) {
            return "Invalid Track 2 format (must start with ; and end with ?)";
        }

        // Remove sentinels
        String data = track2.substring(1, track2.length() - 1);

        // Split by =
        String[] parts = data.split("=");
        if (parts.length < 2) {
            return "Invalid Track 2 format (missing field separator)";
        }

        String pan = parts[0];
        String expiryAndRest = parts[1];

        if (expiryAndRest.length() < 7) {
            return "Invalid Track 2 format (expiry/service code too short)";
        }

        String expiry = expiryAndRest.substring(0, 4);
        String serviceCode = expiryAndRest.substring(4, 7);
        String discretionary = expiryAndRest.length() > 7 ? expiryAndRest.substring(7) : "";

        result.append("PAN: ").append(pan).append("\n");
        result.append("Expiry: ").append(expiry).append(" (YY/MM: ")
                .append(expiry.substring(2, 4)).append("/")
                .append(expiry.substring(0, 2)).append(")\n");
        result.append("Service Code: ").append(serviceCode).append("\n");
        result.append("  - Position 1: ").append(getServiceCodePos1(serviceCode.charAt(0))).append("\n");
        result.append("  - Position 2: ").append(getServiceCodePos2(serviceCode.charAt(1))).append("\n");
        result.append("  - Position 3: ").append(getServiceCodePos3(serviceCode.charAt(2))).append("\n");

        if (!discretionary.isEmpty()) {
            result.append("Discretionary Data: ").append(discretionary).append("\n");
        }

        result.append("\nFull Track 2: ").append(track2).append("\n");
        result.append("Track 2 Equivalent (hex): ").append(track2ToHex(track2)).append("\n");

        return result.toString();
    }

    /**
     * Convert Track 2 to hex format (used in EMV)
     */
    public static String track2ToHex(String track2) {
        // Remove sentinels ; and ?
        String data = track2.substring(1, track2.length() - 1);

        // Replace = with D (separator in hex)
        data = data.replace('=', 'D');

        // Pad with F if odd length
        if (data.length() % 2 != 0) {
            data += "F";
        }

        return data;
    }

    /**
     * Service Code Position 1 meanings
     */
    private static String getServiceCodePos1(char digit) {
        switch (digit) {
            case '1':
                return "International interchange OK";
            case '2':
                return "International interchange, use IC (chip) where feasible";
            case '3':
                return "National interchange only";
            case '4':
                return "National interchange only, use IC where feasible";
            case '5':
                return "International interchange, use IC (chip) required";
            case '6':
                return "National interchange only, use IC required";
            case '7':
                return "No interchange, IC required";
            default:
                return "Unknown";
        }
    }

    /**
     * Service Code Position 2 meanings
     */
    private static String getServiceCodePos2(char digit) {
        switch (digit) {
            case '0':
                return "Normal authorization";
            case '2':
                return "Contact issuer via online means";
            case '4':
                return "Contact issuer via online means except under bilateral agreement";
            default:
                return "Unknown";
        }
    }

    /**
     * Service Code Position 3 meanings
     */
    private static String getServiceCodePos3(char digit) {
        switch (digit) {
            case '0':
                return "No restrictions, PIN required";
            case '1':
                return "No restrictions";
            case '2':
                return "Goods and services only (no cash)";
            case '3':
                return "ATM only, PIN required";
            case '4':
                return "Cash only";
            case '5':
                return "Goods and services only, PIN required";
            case '6':
                return "No restrictions, use PIN where feasible";
            case '7':
                return "Goods and services only, use PIN where feasible";
            default:
                return "Unknown";
        }
    }

    /**
     * Helper: Convert hex string to binary string (for visualization)
     */
    private static String hexToBinary(String hex) {
        StringBuilder binary = new StringBuilder();
        for (int i = 0; i < hex.length(); i++) {
            String bin = Integer.toBinaryString(Integer.parseInt(hex.substring(i, i + 1), 16));
            binary.append(String.format("%4s", bin).replace(' ', '0'));
            if ((i + 1) % 4 == 0 && i < hex.length() - 1) {
                binary.append(" ");
            }
        }
        return binary.toString();
    }

    /**
     * Helper: 3DES encryption
     */
    /**
     * Helper: 3DES encryption (public for general use)
     */
    public static byte[] encryptDesEcb(byte[] data, byte[] key) throws Exception {
        // Determine algorithm based on key length
        String algorithm = "DESede/ECB/NoPadding";
        String keyAlgorithm = "DESede";

        if (key.length == 8) {
            algorithm = "DES/ECB/NoPadding";
            keyAlgorithm = "DES";
        }

        Cipher cipher = Cipher.getInstance(algorithm, "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, keyAlgorithm);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    /**
     * Helper: 3DES decryption (public for general use)
     */
    public static byte[] decryptDesEcb(byte[] data, byte[] key) throws Exception {
        // Determine algorithm based on key length
        String algorithm = "DESede/ECB/NoPadding";
        String keyAlgorithm = "DESede";

        if (key.length == 8) {
            algorithm = "DES/ECB/NoPadding";
            keyAlgorithm = "DES";
        }

        Cipher cipher = Cipher.getInstance(algorithm, "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, keyAlgorithm);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    // Legacy private method (redirect to public one)
    private static byte[] encrypt3DES(byte[] data, byte[] key) throws Exception {
        return encryptDesEcb(data, key);
    }
    // ==================== OFFSET GENERATION (IBM 3624) ====================

    /**
     * Generate IBM 3624 Offset
     * Offset = (User PIN - Natural PIN) mod 10
     *
     * @param pin      Desired User PIN (4-16 digits)
     * @param pan      Primary Account Number
     * @param pvk      PIN Verification Key (hex)
     * @param decTable Decimalization Table (16 hex digits)
     * @return Offset value
     */
    public static String generateIBM3624Offset(String pin, String pan, String pvk, String decTable) throws Exception {
        // 1. Generate Natural PIN
        // (Using same logic as verification/generation but with offset "0000")
        String naturalPin = generateIBM3624Pin(pan, pvk, decTable, "0000000000000000".substring(0, pin.length()));

        if (naturalPin.length() != pin.length()) {
            throw new IllegalArgumentException("Natural PIN length mismatch");
        }

        // 2. Calculate Offset
        StringBuilder offset = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) {
            int userDigit = Character.getNumericValue(pin.charAt(i));
            int naturalDigit = Character.getNumericValue(naturalPin.charAt(i));

            int diff = (userDigit - naturalDigit);
            if (diff < 0) {
                diff += 10;
            }
            offset.append(diff);
        }

        return offset.toString();
    }

    /**
     * Generate IBM 3624 PIN (Natural PIN + Offset)
     * This method was likely missing or private, ensuring it's available for Offset
     * gen
     */
    public static String generateIBM3624Pin(String pan, String pvk, String decTable, String offset) throws Exception {
        // Validate inputs
        if (pan == null || pan.length() < 13)
            throw new IllegalArgumentException("Invalid PAN");
        if (pvk == null || pvk.length() != 32 && pvk.length() != 16)
            throw new IllegalArgumentException("Invalid PVK (must be 16 or 32 hex chars)");
        if (decTable == null || decTable.length() != 16)
            throw new IllegalArgumentException("Invalid Decimalization Table");

        // 1. Prepare Validation Data (PAN part)
        // Validation data is usually the rightmost 16 digits of PAN excluding check
        // digit
        // If PAN < 16, pad with '0'
        String panDigits = pan.replaceAll("[^0-9]", "");
        String validationData;
        if (panDigits.length() > 12) {
            // Take last 12 digits excluding check digit
            String panPart = panDigits.substring(panDigits.length() - 13, panDigits.length() - 1);
            // Pad with 4 zeros to make 16
            validationData = "0000" + panPart;
        } else {
            throw new IllegalArgumentException("PAN too short");
        }

        // 2. Encrypt Validation Data with PVK
        byte[] keyBytes = DataConverter.hexToBytes(pvk);
        byte[] dataBytes = DataConverter.hexToBytes(validationData);
        byte[] encrypted = encryptDesEcb(dataBytes, keyBytes); // Uses simplified helper

        // 3. Decimalize
        String hexResult = DataConverter.bytesToHex(encrypted);
        StringBuilder decimalized = new StringBuilder();
        for (char c : hexResult.toCharArray()) {
            int val = Character.digit(c, 16);
            decimalized.append(decTable.charAt(val));
        }

        // 4. Apply Offset
        // Cut to offset length (PIN length)
        if (offset == null || offset.isEmpty())
            offset = "0000"; // Default 4
        int pinLength = offset.length();

        StringBuilder pin = new StringBuilder();
        for (int i = 0; i < pinLength; i++) {
            int naturalDigit = Character.getNumericValue(decimalized.charAt(i));
            int offsetDigit = Character.getNumericValue(offset.charAt(i));

            int pinDigit = (naturalDigit + offsetDigit) % 10;
            pin.append(pinDigit);
        }

        return pin.toString();
    }
}
