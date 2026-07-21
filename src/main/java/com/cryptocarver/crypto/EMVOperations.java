package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

/**
 * EMV cryptographic operations for chip card transactions
 * Implements ARQC/ARPC, session key derivation, and related EMV functions
 *
 * @author Felipe
 */
public class EMVOperations {

    // ============================================================================
    // SESSION KEY DERIVATION
    // ============================================================================

    /**
     * Derive ICC Master Key from Issuer Master Key (IMK) and PAN
     * Uses ANSI X9.24 method (Option A)
     *
     * /**
     * Derive ICC Master Key from Issuer Master Key (IMK) and PAN
     * Uses EMV Option A Method (Master Key Derivation)
     * Generates a 16-byte (Double Length) UDK
     *
     * @param imk         Issuer Master Key (16 bytes hex)
     * @param pan         Primary Account Number
     * @param panSequence PAN Sequence Number (00-99)
     * @return ICC Master Key (32 hex characters)
     */
    public static String deriveICCMasterKey(String imk, String pan, String panSequence) throws Exception {
        // 1. Prepare Diversification Data (16 digits / 8 bytes)
        // EMV Option A: Concatenate PAN and PAN Sequence, then take rightmost 16
        // digits.
        String panData = pan.replaceAll("[^0-9]", "");
        String panSeq = (panSequence == null || panSequence.isEmpty()) ? "00" : panSequence;

        // Concatenate first
        String concat = panData + panSeq;

        // Ensure 16 digits (Pad left with '0' if short, truncate left if long)
        String divString = concat;
        if (divString.length() < 16) {
            divString = String.format("%16s", divString).replace(' ', '0');
        } else if (divString.length() > 16) {
            divString = divString.substring(divString.length() - 16);
        }

        byte[] divData = DataConverter.hexToBytes(divDataString(divString));

        // 2. Encrypt to get Left Half (UDK-A)
        byte[] imkBytes = DataConverter.hexToBytes(imk);

        // Handle 16-byte vs 24-byte key for Java DESede
        byte[] tdesKey = new byte[24];
        if (imkBytes.length == 16) {
            System.arraycopy(imkBytes, 0, tdesKey, 0, 16);
            System.arraycopy(imkBytes, 0, tdesKey, 16, 8);
        } else {
            tdesKey = imkBytes;
        }

        SecretKeySpec key = new SecretKeySpec(tdesKey, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] leftHalf = cipher.doFinal(divData);

        // 3. Encrypt Inverted Data to get Right Half (UDK-B) - for Double Length Key
        byte[] divDataInv = new byte[8];
        for (int i = 0; i < 8; i++) {
            divDataInv[i] = (byte) ~divData[i];
        }

        byte[] rightHalf = cipher.doFinal(divDataInv);

        // Return concatenated key (16 bytes) - Raw (matches BP Tools "None forced")
        return (DataConverter.bytesToHex(leftHalf) + DataConverter.bytesToHex(rightHalf)).toUpperCase();
    }

    // Helper to ensure string is treated as hex digits for BCD conversion
    private static String divDataString(String input) {
        return input;
    }

    // Helper to adjust byte array to Odd Parity
    public static byte[] adjustParity(byte[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            int b = in[i] & 0xFF;
            int bits = Integer.bitCount(b);
            if ((bits % 2) == 0) {
                b = b ^ 1;
            }
            out[i] = (byte) b;
        }
        return out;
    }

    /**
     * Derive Application Cryptogram Session Key (SK)
     * Method: EMV Book 2 - Common Session Key Derivation (CSK)
     * Generates a 16-byte (Double Length) Session Key
     *
     * @param mkac Master Key for Application Cryptogram (16 bytes hex)
     * @param atc  Application Transaction Counter (2 bytes hex)
     * @param un   Unpredictable Number from terminal (4 bytes hex, optional -
     *             unused for standard SK derivation)
     * @return Session Key (32 hex characters)
     */
    public static String deriveSessionKey(String mkac, String atc, String un) throws Exception {
        byte[] mkacBytes = DataConverter.hexToBytes(mkac);

        // Handle 16-byte vs 24-byte key for Java DESede
        byte[] tdesKey = new byte[24];
        if (mkacBytes.length == 16) {
            System.arraycopy(mkacBytes, 0, tdesKey, 0, 16);
            System.arraycopy(mkacBytes, 0, tdesKey, 16, 8);
        } else {
            tdesKey = mkacBytes;
        }

        SecretKeySpec key = new SecretKeySpec(tdesKey, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        // 1. Left Half (SK-A): R = ATC || F0 || 00...
        String rStrLeft = atc + "F0";
        while (rStrLeft.length() < 16)
            rStrLeft += "0";
        if (rStrLeft.length() > 16)
            rStrLeft = rStrLeft.substring(0, 16);

        byte[] leftHalf = cipher.doFinal(DataConverter.hexToBytes(rStrLeft));

        // 2. Right Half (SK-B): R = ATC || 0F || 00...
        String rStrRight = atc + "0F";
        while (rStrRight.length() < 16)
            rStrRight += "0";
        if (rStrRight.length() > 16)
            rStrRight = rStrRight.substring(0, 16);

        byte[] rightHalf = cipher.doFinal(DataConverter.hexToBytes(rStrRight));

        // Return with Parity Adjustment (matches BP Tools output)
        return (DataConverter.bytesToHex(adjustParity(leftHalf)) + DataConverter.bytesToHex(adjustParity(rightHalf)))
                .toUpperCase();
    }

    // ============================================================================
    // ARQC GENERATION
    // ============================================================================

    /**
     * Generate ARQC (Application Request Cryptogram)
     * EMV Book 2 - Section 8.1
     *
     * @param sk              Session Key (16 bytes hex)
     * @param transactionData Transaction data for MAC (hex)
     * @param paddingMethod   Padding method (1 for ISO 9797-1 Method 1, 2 for ISO
     *                        9797-1 Method 2)
     * @return ARQC (16 hex characters)
     */
    public static String generateARQC(String sk, String transactionData, int paddingMethod) throws Exception {
        sk = normalizeHex(sk, "Session Key", 16);
        transactionData = normalizeHex(transactionData, "ARQC transaction data", -1);
        if (transactionData.isEmpty()) throw new IllegalArgumentException("ARQC transaction data must not be empty");
        if (paddingMethod != 1 && paddingMethod != 2) throw new IllegalArgumentException("ARQC padding method must be 1 or 2");
        // Use MAC ISO 9797-1 Algorithm 3 (DES Retail MAC)
        byte[] skBytes = DataConverter.hexToBytes(sk);
        byte[] data = DataConverter.hexToBytes(transactionData);
        byte[] paddedData;

        if (paddingMethod == 2) {
            // Padding Method 2 (ISO 9797-1): Append 0x80, then 0x00s to next 8-byte
            // boundary
            int paddingLen = 8 - (data.length % 8);
            paddedData = new byte[data.length + paddingLen];
            System.arraycopy(data, 0, paddedData, 0, data.length);
            paddedData[data.length] = (byte) 0x80;
            // The rest are 0x00 by default initialization
        } else {
            // Padding Method 1 (ISO 9797-1): Append 0x00s only if necessary
            int paddingNeeded = 8 - (data.length % 8);
            if (paddingNeeded != 8) { // If data is not already a multiple of 8
                paddedData = new byte[data.length + paddingNeeded];
                System.arraycopy(data, 0, paddedData, 0, data.length);
                // Rest are 0x00 by default initialization
            } else {
                paddedData = data; // Already aligned, no padding needed
            }
        }

        // Split Session Key (16 bytes) into K1 (Left) and K2 (Right)
        if (skBytes.length != 16) {
            throw new IllegalArgumentException("Session Key must be 16 bytes for Retail MAC");
        }
        byte[] key1 = Arrays.copyOfRange(skBytes, 0, 8);
        byte[] key2 = Arrays.copyOfRange(skBytes, 8, 16);

        // Step 1: DES-CBC with Key1 (IV = 0)
        SecretKeySpec keySpec1 = new SecretKeySpec(key1, "DES");
        Cipher cbcCipher = Cipher.getInstance("DES/CBC/NoPadding");
        cbcCipher.init(Cipher.ENCRYPT_MODE, keySpec1, new IvParameterSpec(new byte[8]));

        byte[] macResult = cbcCipher.doFinal(paddedData); // Encrypt whole chain
        // Take last 8 bytes (last block)
        byte[] lastBlock = Arrays.copyOfRange(macResult, macResult.length - 8, macResult.length);

        // Step 2: DES-ECB Decrypt with Key2
        SecretKeySpec keySpec2 = new SecretKeySpec(key2, "DES");
        Cipher decCipher = Cipher.getInstance("DES/ECB/NoPadding");
        decCipher.init(Cipher.DECRYPT_MODE, keySpec2);
        byte[] step2Result = decCipher.doFinal(lastBlock);

        // Step 3: DES-ECB Encrypt with Key1
        Cipher encCipher = Cipher.getInstance("DES/ECB/NoPadding");
        encCipher.init(Cipher.ENCRYPT_MODE, keySpec1);
        byte[] finalMac = encCipher.doFinal(step2Result);

        return DataConverter.bytesToHex(finalMac).toUpperCase();
    }

    /**
     * Build transaction data for ARQC from EMV fields
     * Structure matches observed BP Tools CDOL1:
     * Amount (6) + Amount Other (6) + Country (2) + TVR (5) + Currency (2) + Date
     * (3) + Type (1) + UN (4)
     *
     * @param amount      Transaction amount (6 bytes BCD)
     * @param amountOther Amount Other (6 bytes BCD)
     * @param country     Country code (2 bytes)
     * @param tvr         Terminal Verification Results (5 bytes)
     * @param currency    Currency code (2 bytes)
     * @param txDate      Transaction date YYMMDD (3 bytes)
     * @param txType      Transaction type (1 byte)
     * @param un          Unpredictable Number (4 bytes)
     * @return Transaction data for ARQC calculation (hex)
     */
    public static String buildARQCData(String amount, String amountOther, String country,
            String tvr, String currency, String txDate,
            String txType, String un) {
        amount = normalizeHex(amount, "Amount", 6);
        amountOther = normalizeHex(amountOther, "Amount Other", 6);
        country = normalizeHex(country, "Terminal Country Code", 2);
        tvr = normalizeHex(tvr, "TVR", 5);
        currency = normalizeHex(currency, "Transaction Currency Code", 2);
        txDate = normalizeHex(txDate, "Transaction Date", 3);
        txType = normalizeHex(txType, "Transaction Type", 1);
        un = normalizeHex(un, "Unpredictable Number", 4);
        StringBuilder data = new StringBuilder();
        data.append(amount); // 6 bytes
        data.append(amountOther); // 6 bytes
        data.append(country); // 2 bytes
        data.append(tvr); // 5 bytes
        data.append(currency); // 2 bytes
        data.append(txDate); // 3 bytes
        data.append(txType); // 1 byte
        data.append(un); // 4 bytes

        return data.toString().toUpperCase();
    }

    // ============================================================================
    // ARPC GENERATION
    // ============================================================================

    /**
     * Generate ARPC Method 1 (EMV 4.3 specification)
     * ARPC = Encrypt(ARQC XOR ARC)
     *
     * @param sk   Session Key (16 bytes hex)
     * @param arqc ARQC from card (8 bytes hex)
     * @param arc  Authorization Response Code (2 bytes hex)
     * @return ARPC (16 hex characters)
     */
    public static String generateARPC_Method1(String sk, String arqc, String arc) throws Exception {
        byte[] skBytes = DataConverter.hexToBytes(sk);
        byte[] arqcBytes = DataConverter.hexToBytes(arqc);
        // Treat ARC as ASCII characters (Tag 8A format is 'an')
        byte[] arcBytes = arc.getBytes(StandardCharsets.US_ASCII);

        // Pad ARC to 8 bytes with zeros
        byte[] arcPadded = new byte[8];
        System.arraycopy(arcBytes, 0, arcPadded, 0, arcBytes.length);

        // XOR ARQC with padded ARC
        byte[] xorResult = new byte[8];
        for (int i = 0; i < 8; i++) {
            xorResult[i] = (byte) (arqcBytes[i] ^ arcPadded[i]);
        }

        // Handle 16-byte vs 24-byte key for Java DESede
        byte[] tdesKey = new byte[24];
        if (skBytes.length == 16) {
            System.arraycopy(skBytes, 0, tdesKey, 0, 16);
            System.arraycopy(skBytes, 0, tdesKey, 16, 8);
        } else {
            tdesKey = skBytes;
        }

        // Encrypt with Session Key
        SecretKeySpec key = new SecretKeySpec(tdesKey, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] arpc = cipher.doFinal(xorResult);
        return DataConverter.bytesToHex(arpc).toUpperCase();
    }

    /**
     * Generate ARPC Method 2 (EMV 4.3 specification)
     * ARPC = Encrypt(ARC || Proprietary Authentication Data)
     *
     * @param sk  Session Key (16 bytes hex)
     * @param arc Authorization Response Code (2 chars alphanumeric)
     * @param csu Card Status Update (4 bytes hex, optional)
     * @return ARPC (16 hex characters)
     */
    public static String generateARPC_Method2(String sk, String arc, String csu) throws Exception {
        byte[] skBytes = DataConverter.hexToBytes(sk);

        // Build 8-byte data: ARC (ASCII HEX) + CSU (4 bytes) + padding
        // Convert ARC characters to their Hex representation (EMV uses ASCII values for
        // Tag 8A in input to crypto)
        String arcHex = DataConverter.bytesToHex(arc.getBytes(StandardCharsets.US_ASCII));

        String data = arcHex;
        if (csu != null && !csu.isEmpty()) {
            data += csu;
        }

        // Pad to 8 bytes (16 hex chars)
        while (data.length() < 16) {
            data += "00";
        }

        // Truncate if too long (though assuming correct input sizes)
        if (data.length() > 16) {
            data = data.substring(0, 16);
        }

        byte[] dataBytes = DataConverter.hexToBytes(data);

        // Handle 16-byte vs 24-byte key for Java DESede
        byte[] tdesKey = new byte[24];
        if (skBytes.length == 16) {
            System.arraycopy(skBytes, 0, tdesKey, 0, 16);
            System.arraycopy(skBytes, 0, tdesKey, 16, 8);
        } else {
            tdesKey = skBytes;
        }

        // Encrypt with Session Key
        SecretKeySpec key = new SecretKeySpec(tdesKey, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] arpc = cipher.doFinal(dataBytes);
        return DataConverter.bytesToHex(arpc).toUpperCase();
    }

    /**
     * Verify ARQC received from card
     *
     * @param sk              Session Key (16 bytes hex)
     * @param arqcReceived    ARQC from card (8 bytes hex)
     * @param transactionData Transaction data used for ARQC (hex)
     * @return true if ARQC is valid
     */
    public static boolean verifyARQC(String sk, String arqcReceived, String transactionData) throws Exception {
        return verifyARQC(sk, arqcReceived, transactionData, 2);
    }

    /** Verifies using the same ISO 9797-1 padding method used during generation. */
    public static boolean verifyARQC(String sk, String arqcReceived, String transactionData, int paddingMethod) throws Exception {
        arqcReceived = normalizeHex(arqcReceived, "ARQC", 8);
        String arqcCalculated = generateARQC(sk, transactionData, paddingMethod);
        return arqcCalculated.equalsIgnoreCase(arqcReceived);
    }

    private static String normalizeHex(String input, String label, int expectedBytes) {
        String value = input == null ? "" : input.replaceAll("\\s+", "").toUpperCase();
        if (!value.matches("[0-9A-F]*") || (value.length() & 1) != 0) throw new IllegalArgumentException(label + " must be even-length hexadecimal");
        if (expectedBytes >= 0 && value.length() != expectedBytes * 2) throw new IllegalArgumentException(label + " must be exactly " + expectedBytes + " bytes");
        return value;
    }

    // ============================================================================
    // SCRIPT PROCESSING
    // ============================================================================

    /**
     * Generate Script MAC for Issuer Script
     *
     * @param sk     Session Key for Script (16 bytes hex)
     * @param script Script data (hex)
     * @return Script MAC (8 bytes hex)
     */
    public static String generateScriptMAC(String sk, String script) throws Exception {
        // Use same MAC algorithm as ARQC (DES Retail MAC), typically Method 2
        return generateARQC(sk, script, 2).substring(0, 16);
    }

    /**
     * Build Issuer Script Command
     *
     * @param scriptId Script identifier (71 or 72)
     * @param command  APDU command (hex)
     * @return Script command with MAC (hex)
     */
    public static String buildScriptCommand(String scriptId, String command) {
        // Format: Script ID (1 byte) + Length + Command
        int length = command.length() / 2;
        String lengthHex = String.format("%02X", length);
        return scriptId + lengthHex + command;
    }

    // ============================================================================
    // TRACK DATA
    // ============================================================================

    /**
     * Encode Track 2 data
     * Format: PAN, Separator, Expiry, Service Code, Discretionary Data
     *
     * @param pan               Primary Account Number
     * @param expiry            Expiration date YYMM
     * @param serviceCode       Service code (3 digits)
     * @param discretionaryData Optional discretionary data
     * @return Track 2 equivalent data (hex)
     */
    public static String encodeTrack2(String pan, String expiry, String serviceCode,
            String discretionaryData) {
        StringBuilder track2 = new StringBuilder();
        track2.append(pan);
        track2.append("D"); // Field separator
        track2.append(expiry);
        track2.append(serviceCode);

        if (discretionaryData != null && !discretionaryData.isEmpty()) {
            track2.append(discretionaryData);
        }

        // Pad to even length with 'F' if needed
        if (track2.length() % 2 != 0) {
            track2.append("F");
        }

        // Convert to BCD hex
        return track2.toString().toUpperCase();
    }

    /**
     * Decode Track 2 data
     *
     * @param track2Data Track 2 data in hex
     * @return Decoded track 2 information
     */
    public static String decodeTrack2(String track2Data) {
        // Replace 'D' separator with '='
        String decoded = track2Data.replace("D", "=").replace("F", "");

        StringBuilder result = new StringBuilder();
        result.append("Track 2 Data Analysis:\n");
        result.append("═══════════════════════\n");

        String[] parts = decoded.split("=");
        if (parts.length >= 2) {
            result.append("PAN: ").append(parts[0]).append("\n");

            String remainder = parts[1];
            if (remainder.length() >= 4) {
                result.append("Expiry Date: ").append(remainder.substring(0, 4)).append("\n");
            }
            if (remainder.length() >= 7) {
                result.append("Service Code: ").append(remainder.substring(4, 7)).append("\n");
            }
            if (remainder.length() > 7) {
                result.append("Discretionary Data: ").append(remainder.substring(7)).append("\n");
            }
        }

        return result.toString();
    }

    // ============================================================================
    // CRYPTOGRAM TYPES
    // ============================================================================

    /**
     * Get cryptogram type description
     *
     * @param cryptogramType Cryptogram Information Data byte
     * @return Description of cryptogram type
     */
    public static String getCryptogramTypeDescription(String cryptogramType) {
        int type = Integer.parseInt(cryptogramType, 16) & 0xC0;

        switch (type >> 6) {
            case 0:
                return "AAC (Application Authentication Cryptogram) - Transaction Declined";
            case 1:
                return "TC (Transaction Certificate) - Transaction Approved Offline";
            case 2:
                return "ARQC (Authorization Request Cryptogram) - Online Authorization Requested";
            case 3:
                return "RFU (Reserved for Future Use)";
            default:
                return "Unknown";
        }
    }

    // ============================================================================
    // ATC (APPLICATION TRANSACTION COUNTER)
    // ============================================================================

    /**
     * Format ATC for display
     *
     * @param atc ATC value (2 bytes hex)
     * @return Formatted ATC information
     */
    public static String formatATC(String atc) {
        int atcValue = Integer.parseInt(atc, 16);
        return String.format("ATC: %s (Decimal: %d transactions)", atc, atcValue);
    }
}
