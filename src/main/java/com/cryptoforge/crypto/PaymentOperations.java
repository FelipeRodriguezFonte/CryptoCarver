package com.cryptoforge.crypto;

import com.cryptoforge.util.DataConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

/**
 * Payment cryptography operations (PIN blocks, CVV, MAC)
 */
public class PaymentOperations {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    // ==================== PIN BLOCK OPERATIONS ====================

    /**
     * Encode PIN into PIN block format
     */
    public static String encodePinBlock(String pin, String pan, String format) throws Exception {
        switch (format) {
            case "Format 0 (ISO-0)":
            case "ISO 0 (ANSI X9.8)":
                return encodePinBlockISO0(pin, pan);
            case "Format 1 (ISO-1)":
            case "ISO 1 (ANSI X9.8)":
                return encodePinBlockISO1(pin, pan);
            case "Format 2 (ISO-2)":
            case "ISO 2 (No PAN)":
                return encodePinBlockISO2(pin, pan);
            case "Format 3 (ISO-3)":
            case "ISO 3 (EMV)":
                return encodePinBlockISO3(pin, pan);
            case "Format 4 (ISO-4)":
            case "ISO 4 (EMV 2000)":
                return encodePinBlockISO4(pin, pan);
            case "ANSI X9.8":
                return encodePinBlockANSI(pin, pan);
            case "IBM 3624":
                return encodePinBlockIBM3624(pin, pan);
            case "VISA-1":
                return encodePinBlockVISA1(pin, pan);
            default:
                return encodePinBlockISO0(pin, pan); // Default to ISO-0
        }
    }

    /**
     * Decode PIN from PIN block
     */
    public static String decodePinBlock(String pinBlock, String pan, String format) throws Exception {
        switch (format) {
            case "Format 0 (ISO-0)":
            case "ISO 0 (ANSI X9.8)":
                return decodePinBlockISO0(pinBlock, pan);
            case "Format 1 (ISO-1)":
            case "ISO 1 (ANSI X9.8)":
                return decodePinBlockISO1(pinBlock, pan);
            case "Format 2 (ISO-2)":
            case "ISO 2 (No PAN)":
                return decodePinBlockISO2(pinBlock, pan);
            case "Format 3 (ISO-3)":
            case "ISO 3 (EMV)":
                return decodePinBlockISO3(pinBlock, pan);
            case "Format 4 (ISO-4)":
            case "ISO 4 (EMV 2000)":
                return decodePinBlockISO4(pinBlock, pan);
            case "ANSI X9.8":
                return decodePinBlockANSI(pinBlock, pan);
            case "IBM 3624":
                return decodePinBlockIBM3624(pinBlock, pan);
            case "VISA-1":
                return decodePinBlockVISA1(pinBlock, pan);
            default:
                return decodePinBlockISO0(pinBlock, pan); // Default to ISO-0
        }
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
     * Encode PIN block ISO-4 and return both clear field and result
     * Returns: [0] = clear PIN field, [1] = PIN block (after XOR)
     */
    public static String[] encodePinBlockISO4WithClear(String pin, String pan) throws Exception {
        // Generate clear PIN field
        String clearPinBlock = generateClearPinFieldISO4(pin);

        // Build PAN block
        StringBuilder panBlock = new StringBuilder();
        panBlock.append("4");
        panBlock.append(pan);
        while (panBlock.length() < 32) {
            panBlock.append("0");
        }

        // XOR
        byte[] clearBytes = DataConverter.hexToBytes(clearPinBlock);
        byte[] panBytes = DataConverter.hexToBytes(panBlock.substring(0, 32));

        byte[] result = new byte[16];
        for (int i = 0; i < 16; i++) {
            result[i] = (byte) (clearBytes[i] ^ panBytes[i]);
        }

        String pinBlockResult = DataConverter.bytesToHex(result);

        return new String[] { clearPinBlock, pinBlockResult };
    }

    /**
     * ISO Format 4 (ISO 9564-1:2002 Format 4)
     * Structure: 4L PPPP PPPP RRRR RRRR RRRR RRRR (32 hex chars = 16 bytes before
     * XOR)
     * Uses random padding and XOR with PAN
     * Designed for use with AES (128-bit block)
     */
    private static String encodePinBlockISO4(String pin, String pan) throws Exception {
        // Generate clear PIN field
        String clearPinBlock = generateClearPinFieldISO4(pin);

        // Build PAN block: "4" + PAN (padded to 32 hex chars)
        StringBuilder panBlock = new StringBuilder();
        panBlock.append("4");
        panBlock.append(pan);
        while (panBlock.length() < 32) {
            panBlock.append("0");
        }

        // XOR clear PIN block with PAN block
        byte[] clearBytes = DataConverter.hexToBytes(clearPinBlock);
        byte[] panBytes = DataConverter.hexToBytes(panBlock.substring(0, 32));

        byte[] result = new byte[16];
        for (int i = 0; i < 16; i++) {
            result[i] = (byte) (clearBytes[i] ^ panBytes[i]);
        }

        return DataConverter.bytesToHex(result);
    }

    /**
     * Decode ISO Format 4
     */
    private static String decodePinBlockISO4(String pinBlock, String pan) throws Exception {
        // Determine PAN block bytes
        byte[] panBytes;
        if (pan.length() == 32) {
            // Assume input IS the PAN Block (as requested by user)
            panBytes = DataConverter.hexToBytes(pan);
        } else {
            // Construct PAN Block from PAN
            StringBuilder panBlock = new StringBuilder();
            panBlock.append("4");
            panBlock.append(pan);
            while (panBlock.length() < 32) {
                panBlock.append("0");
            }
            panBytes = DataConverter.hexToBytes(panBlock.substring(0, 32));
        }

        // Check if input is already a Clear PIN Block (Heuristic for ISO-4)
        // ISO-4 Clear Block must start with '4'.
        // Encoded ISO-4 Block must start with '0' (because 4^4 = 0).
        // If input starts with '4', we assume it's already clear and skip XOR.
        byte[] clearBlock;
        if (pinBlock.startsWith("4")) {
            clearBlock = DataConverter.hexToBytes(pinBlock);
        } else {
            // XOR with PAN block
            byte[] pinBlockBytes = DataConverter.hexToBytes(pinBlock);
            clearBlock = new byte[16];
            for (int i = 0; i < 16; i++) {
                clearBlock[i] = (byte) (pinBlockBytes[i] ^ panBytes[i]);
            }
        }

        String clearPinBlock = DataConverter.bytesToHex(clearBlock);

        // Extract PIN
        int pinLength = Integer.parseInt(clearPinBlock.substring(1, 2), 16);
        String pin = clearPinBlock.substring(2, 2 + pinLength);

        return pin;
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
     * Generates a Dynamic CVV (dCVV) for Contactless transactions (Visa Mode A).
     * Data Block: PAN + PAN Sequence Number + Expiry Date + ATC (3 digits)
     */
    public static String generateDCVV(String cvkA, String cvkB, String pan, String panSeq, String expiryDate,
            String atc) {
        try {
            // Data Block Construction
            // Note: ATC should be truncated/padded to 3 digits?
            // Based on reverse engineering: Use first 3 digits of ATC string.
            String atcToken = atc;
            if (atcToken.length() > 3) {
                atcToken = atcToken.substring(0, 3);
            }

            // Allow override of PAN Seq, default to "0" if empty
            if (panSeq == null || panSeq.isEmpty()) {
                panSeq = "0";
            }

            // Construct Data Block: PAN + PanSeq + Expiry + ATC
            String data = pan + panSeq + expiryDate + atcToken;

            // Pad to 32 hex digits (16 bytes) with trailing zeros
            StringBuilder paddedData = new StringBuilder(data);
            while (paddedData.length() < 32) {
                paddedData.append("0");
            }
            // Ensure exactly 32 chars
            String blockString = paddedData.toString().substring(0, 32);

            byte[] blockBytes = DataConverter.hexToBytes(blockString);

            // Block 1: First 8 bytes
            byte[] block1 = new byte[8];
            System.arraycopy(blockBytes, 0, block1, 0, 8);

            // Block 2: Next 8 bytes
            byte[] block2 = new byte[8];
            System.arraycopy(blockBytes, 8, block2, 0, 8);

            // Key A and B
            byte[] keyA = DataConverter.hexToBytes(cvkA);
            byte[] keyB = DataConverter.hexToBytes(cvkB);

            // Step 1: Encrypt Block 1 with Key A (DES)
            org.bouncycastle.crypto.engines.DESEngine desEngine = new org.bouncycastle.crypto.engines.DESEngine();
            desEngine.init(true, new org.bouncycastle.crypto.params.KeyParameter(keyA));
            byte[] step1Result = new byte[8];
            desEngine.processBlock(block1, 0, step1Result, 0);

            // Step 2: XOR result with Block 2
            byte[] xorResult = new byte[8];
            for (int i = 0; i < 8; i++) {
                xorResult[i] = (byte) (step1Result[i] ^ block2[i]);
            }

            // Step 3: Encrypt result with Key A then Decrypt with Key B then Encrypt with
            // Key A (3DES)
            // But standard CVV uses 3DES EDE with K1=A, K2=B, K3=A
            byte[] key3Des = new byte[24];
            System.arraycopy(keyA, 0, key3Des, 0, 8);
            System.arraycopy(keyB, 0, key3Des, 8, 8);
            System.arraycopy(keyA, 0, key3Des, 16, 8);

            org.bouncycastle.crypto.engines.DESedeEngine tdesEngine = new org.bouncycastle.crypto.engines.DESedeEngine();
            tdesEngine.init(true, new org.bouncycastle.crypto.params.KeyParameter(key3Des));
            byte[] step3Result = new byte[8];
            tdesEngine.processBlock(xorResult, 0, step3Result, 0);

            // Step 4: Extract digits
            String hexResult = DataConverter.bytesToHex(step3Result).toUpperCase();
            StringBuilder digits = new StringBuilder();
            for (char c : hexResult.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                }
            }

            // Return first 3 digits
            if (digits.length() < 3)
                return "ERR";
            return digits.toString().substring(0, 3);

        } catch (Exception e) {
            e.printStackTrace();
            return "ERR";
        }
    }

    public static boolean verifyDCVV(String cvkA, String cvkB, String pan, String panSeq, String expiryDate, String atc,
            String inputCvv) {
        try {
            String calculated = generateDCVV(cvkA, cvkB, pan, panSeq, expiryDate, atc);
            return calculated.equals(inputCvv);
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

        // Step 4: Decimalize - extract only digits 0-9 from hex result
        String hexResult = DataConverter.bytesToHex(result);
        StringBuilder digits = new StringBuilder();
        for (char c : hexResult.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
                if (digits.length() == 3) {
                    break;
                }
            }
        }

        // If we don't have 3 digits, this shouldn't happen in practice,
        // but handle it by padding with zeros
        while (digits.length() < 3) {
            digits.append("0");
        }

        return digits.toString();
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
            case "CMAC (ISO 9797-1 Alg 5)":
                return generateCMAC(keyBytes, dataBytes);
            case "HMAC-SHA256":
                return generateHMAC(keyBytes, dataBytes);
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
     * CMAC (ISO 9797-1 Algorithm 5)
     */
    private static String generateCMAC(byte[] key, byte[] data) throws Exception {
        // Use first 16 bytes as AES key
        byte[] aesKey = new byte[16];
        System.arraycopy(key, 0, aesKey, 0, 16);

        Mac mac = Mac.getInstance("AESCMAC", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        mac.init(keySpec);
        byte[] result = mac.doFinal(data);

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
