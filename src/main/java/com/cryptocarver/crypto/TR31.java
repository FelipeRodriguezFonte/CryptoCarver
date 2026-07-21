package com.cryptocarver.crypto;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.util.Arrays;

/**
 * TR-31 Key Block Implementation (2018 standard)
 * Supports versions B (TDES) and D (AES)
 */
public class TR31 {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final byte[] kbpk;

    public TR31(byte[] kbpk) {
        this.kbpk = kbpk;
    }

    public TR31(String kbpkHex) {
        this.kbpk = hexToBytes(kbpkHex);
    }

    // Métodos de conversión hex compatibles Java 8+
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Wrap a key into TR-31 key block
     */
    public String wrap(String header, byte[] key) throws Exception {
        return wrap(header, key, null);
    }

    public String wrap(String header, String keyHex) throws Exception {
        return wrap(header, hexToBytes(keyHex), null);
    }

    /**
     * Validates TR-31 combinations, throwing IllegalArgumentException for impossible combinations.
     * Logs or warns for legacy but permitted combinations.
     */
    public static void validateMatrix(char version, char algorithm, String usage, char mode, char exportability) {
        // Asymmetric modes on symmetric algorithms
        if ((algorithm == 'T' || algorithm == 'A' || algorithm == 'D') && (mode == 'S' || mode == 'V')) {
            throw new IllegalArgumentException("Impossible combination: Symmetric algorithm (" + algorithm + ") cannot use asymmetric signature mode (" + mode + ")");
        }
        // Symmetric modes on asymmetric algorithms
        if ((algorithm == 'R' || algorithm == 'E' || algorithm == 'S') && (mode == 'X' || mode == 'Y')) {
            throw new IllegalArgumentException("Impossible combination: Asymmetric algorithm (" + algorithm + ") cannot use symmetric derivation/MAC mode (" + mode + ")");
        }
        // TDES versions with AES algorithm
        if ((version == 'A' || version == 'B' || version == 'C') && algorithm == 'A') {
            throw new IllegalArgumentException("Impossible combination: AES algorithm ('A') cannot be wrapped using legacy TDES versions ('A', 'B', 'C')");
        }
        // DUKPT usage should typically be derivation (X)
        if (("B0".equals(usage) || "B1".equals(usage)) && mode != 'X' && mode != 'N' && mode != 'E' && mode != 'G') {
            System.err.println("WARNING: Legacy DUKPT combination permitted but strictly speaking mode should be 'X'. Mode used: " + mode);
        }
    }

    public String wrap(String header, byte[] key, Integer maskedKeyLen) throws Exception {
        char version = header.charAt(0);
        char algorithm = header.charAt(11);
        String usage = header.substring(5, 7);
        char mode = header.charAt(12);
        char exportability = header.charAt(14);
        validateMatrix(version, algorithm, usage, mode, exportability);

        switch (version) {
            case 'A':
            case 'C':
                return wrapVersionC(header, key, maskedKeyLen);
            case 'B':
                return wrapVersionB(header, key, maskedKeyLen);
            case 'D':
                return wrapVersionD(header, key, maskedKeyLen);
            default:
                throw new IllegalArgumentException("Unsupported version: " + version);
        }
    }

    /**
     * Unwrap a key from TR-31 key block
     */
    public UnwrapResult unwrap(String keyBlock) throws Exception {
        char version = keyBlock.charAt(0);
        char algorithm = keyBlock.charAt(11);
        String usage = keyBlock.substring(5, 7);
        char mode = keyBlock.charAt(12);
        char exportability = keyBlock.charAt(14);
        validateMatrix(version, algorithm, usage, mode, exportability);

        switch (version) {
            case 'A':
            case 'C':
                return unwrapVersionC(keyBlock);
            case 'B':
                return unwrapVersionB(keyBlock);
            case 'D':
                return unwrapVersionD(keyBlock);
            default:
                throw new IllegalArgumentException("Unsupported version: " + version);
        }
    }

    // ==================== Version A/C (TDES Variant Binding) ====================

    private String wrapVersionC(String header, byte[] key, Integer maskedKeyLen) throws Exception {
        // Derive keys using variant method
        KeyPair keys = deriveKeysVersionC();

        // Format key data: 2 bytes length (in bits) + key + padding to 8-byte boundary
        int keyLengthBits = key.length * 8;
        int padLen = 8 - ((2 + key.length) % 8);
        if (padLen == 8)
            padLen = 0;

        byte[] clearKeyData = new byte[2 + key.length + padLen];
        clearKeyData[0] = (byte) (keyLengthBits >> 8);
        clearKeyData[1] = (byte) keyLengthBits;
        System.arraycopy(key, 0, clearKeyData, 2, key.length);
        // Padding is zeros

        // UPDATE HEADER LENGTH FIRST (before encryption and MAC)
        String fullHeader = updateHeaderLength(header, clearKeyData.length);

        // Encrypt using first 8 bytes of FULL header as IV
        byte[] headerIv = fullHeader.substring(0, 8).getBytes("ASCII");
        byte[] encryptedKey = encryptCBC(keys.kbek, headerIv, clearKeyData, "DESede");

        // Generate MAC over FULL header + encrypted key (4 bytes)
        byte[] mac = generateMacVersionC(keys.kbak, fullHeader, encryptedKey);

        // Return FULL header + encrypted + mac
        return fullHeader + bytesToHex(encryptedKey) + bytesToHex(mac);
    }

    private UnwrapResult unwrapVersionC(String keyBlock) throws Exception {
        // Parse the fixed header plus any authenticated optional blocks.
        int headerLength = headerLength(keyBlock);
        String header = keyBlock.substring(0, headerLength);
        int length = Integer.parseInt(keyBlock.substring(1, 5));

        // Extract encrypted key and MAC (4 bytes = 8 chars)
        String encryptedKeyHex = keyBlock.substring(headerLength, length - 8);
        String macHex = keyBlock.substring(length - 8);

        byte[] encryptedKey = hexToBytes(encryptedKeyHex);
        byte[] receivedMac = hexToBytes(macHex);

        // Derive keys using variant method
        KeyPair keys = deriveKeysVersionC();

        // Validate MAC over header + encrypted key
        byte[] calculatedMac = generateMacVersionC(keys.kbak, header, encryptedKey);

        if (!Arrays.equals(receivedMac, calculatedMac)) {
            throw new SecurityException("MAC verification failed");
        }

        // Decrypt using first 8 bytes of header as IV
        byte[] headerIv = header.substring(0, 8).getBytes("ASCII");
        byte[] clearKeyData = decryptCBC(keys.kbek, headerIv, encryptedKey, "DESede");

        // Extract key from clear data: 2 bytes length (in bits) + key + padding
        int keyLengthBits = ((clearKeyData[0] & 0xFF) << 8) | (clearKeyData[1] & 0xFF);
        int keyLengthBytes = keyLengthBits / 8;
        byte[] key = Arrays.copyOfRange(clearKeyData, 2, 2 + keyLengthBytes);

        return new UnwrapResult(header, key);
    }

    private KeyPair deriveKeysVersionC() throws Exception {
        // Version A/C uses simple XOR variants (no derivation)
        byte[] kbek = xor(kbpk, createByteArray(kbpk.length, (byte) 0x45));
        byte[] kbak = xor(kbpk, createByteArray(kbpk.length, (byte) 0x4D));

        // CRITICAL: Adjust parity for DES keys
        adjustParity(kbek);
        adjustParity(kbak);

        return new KeyPair(kbek, kbak);
    }

    private byte[] generateMacVersionC(byte[] kbak, String header, byte[] encryptedKey) throws Exception {
        // MAC for version A/C: Standard CBC-MAC with TDES (not CMAC)
        byte[] headerBytes = header.getBytes("ASCII");
        byte[] macInput = new byte[headerBytes.length + encryptedKey.length];
        System.arraycopy(headerBytes, 0, macInput, 0, headerBytes.length);
        System.arraycopy(encryptedKey, 0, macInput, headerBytes.length, encryptedKey.length);

        // Pad to 8-byte boundary if needed
        int padLen = (8 - (macInput.length % 8)) % 8;
        if (padLen > 0) {
            byte[] padded = new byte[macInput.length + padLen];
            System.arraycopy(macInput, 0, padded, 0, macInput.length);
            macInput = padded;
        }

        // Standard CBC-MAC with TDES
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(kbak, "DESede");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[8]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(macInput);

        // Return first 4 bytes of last block (TR-31 spec for version C)
        byte[] lastBlock = Arrays.copyOfRange(encrypted, encrypted.length - 8, encrypted.length);
        return Arrays.copyOf(lastBlock, 4);
    }

    // ==================== Version B (TDES Key Derivation) ====================

    private String wrapVersionB(String header, byte[] key, Integer maskedKeyLen) throws Exception {
        // Derive keys
        KeyPair keys = deriveKeysVersionB();

        // Format key data: 2 bytes length (in bits) + key + padding to 8-byte boundary
        int keyLengthBits = key.length * 8;
        int padLen = 8 - ((2 + key.length) % 8);
        if (padLen == 8)
            padLen = 0;

        byte[] clearKeyData = new byte[2 + key.length + padLen];
        clearKeyData[0] = (byte) (keyLengthBits >> 8);
        clearKeyData[1] = (byte) keyLengthBits;
        System.arraycopy(key, 0, clearKeyData, 2, key.length);
        // Padding is zeros

        // Update header length FIRST
        String fullHeader = updateHeaderLength(header, clearKeyData.length);

        // Generate MAC over fullHeader + clear key data
        byte[] mac = generateMacVersionB(keys.kbak, fullHeader, clearKeyData);

        // Encrypt using MAC as IV
        byte[] encryptedKey = encryptCBC(keys.kbek, mac, clearKeyData, "DESede");

        return fullHeader + bytesToHex(encryptedKey) + bytesToHex(mac);
    }

    private UnwrapResult unwrapVersionB(String keyBlock) throws Exception {
        // Parse the fixed header plus any authenticated optional blocks.
        int headerLength = headerLength(keyBlock);
        String header = keyBlock.substring(0, headerLength);
        int length = Integer.parseInt(keyBlock.substring(1, 5));

        // Extract encrypted key and MAC (8 bytes = 16 chars)
        String encryptedKeyHex = keyBlock.substring(headerLength, length - 16);
        String macHex = keyBlock.substring(length - 16);

        byte[] encryptedKey = hexToBytes(encryptedKeyHex);
        byte[] receivedMac = hexToBytes(macHex);

        // Derive keys
        KeyPair keys = deriveKeysVersionB();

        // Decrypt using MAC as IV
        byte[] clearKeyData = decryptCBC(keys.kbek, receivedMac, encryptedKey, "DESede");

        // Validate MAC over header + decrypted data
        byte[] calculatedMac = generateMacVersionB(keys.kbak, header, clearKeyData);

        if (!Arrays.equals(receivedMac, calculatedMac)) {
            throw new SecurityException("MAC verification failed");
        }

        // Extract key from clear data: 2 bytes length (in bits) + key + padding
        int keyLengthBits = ((clearKeyData[0] & 0xFF) << 8) | (clearKeyData[1] & 0xFF);
        int keyLengthBytes = keyLengthBits / 8;
        byte[] key = Arrays.copyOfRange(clearKeyData, 2, 2 + keyLengthBytes);

        return new UnwrapResult(header, key);
    }

    private byte[] generateMacVersionB(byte[] kbak, String header, byte[] keyData) throws Exception {
        // DES-CMAC for version B: XOR last block with K1/K2, then CBC-MAC
        byte[][] subkeys = deriveDESCMACSubkey(kbak);
        byte[] km1 = subkeys[0];
        byte[] km2 = subkeys[1];

        // Build MAC input: header (ASCII) + key data
        byte[] headerBytes = header.getBytes("ASCII");
        byte[] macInput = new byte[headerBytes.length + keyData.length];
        System.arraycopy(headerBytes, 0, macInput, 0, headerBytes.length);
        System.arraycopy(keyData, 0, macInput, headerBytes.length, keyData.length);

        int blockSize = 8;
        boolean isCompleteBlock = (macInput.length > 0 && macInput.length % blockSize == 0);

        byte[] paddedData;
        byte[] activeKey;
        if (isCompleteBlock) {
            paddedData = macInput;
            activeKey = km1;
        } else {
            int padLen = blockSize - (macInput.length % blockSize);
            paddedData = new byte[macInput.length + padLen];
            System.arraycopy(macInput, 0, paddedData, 0, macInput.length);
            paddedData[macInput.length] = (byte) 0x80;
            activeKey = km2;
        }

        int lastBlockStart = paddedData.length - blockSize;
        byte[] lastBlock = Arrays.copyOfRange(paddedData, lastBlockStart, paddedData.length);
        byte[] xoredLastBlock = xor(lastBlock, activeKey);
        System.arraycopy(xoredLastBlock, 0, paddedData, lastBlockStart, blockSize);

        // Generate CBC-MAC with TDES
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(kbak, "DESede");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[blockSize]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(paddedData);

        // Return last 8 bytes
        return Arrays.copyOfRange(encrypted, encrypted.length - blockSize, encrypted.length);
    }

    private KeyPair deriveKeysVersionB() throws Exception {
        // Derive DES CMAC subkey K1
        byte[][] subkeys = deriveDESCMACSubkey(kbpk);
        byte[] k1 = subkeys[0];

        // Key Derivation data per TR-31 for version B
        // byte 0 = counter (starts at 1)
        // bytes 1-2 = key usage indicator (0x0000 for encryption, 0x0001 for MAC)
        // byte 3 = separator (0x00)
        // bytes 4-5 = algorithm indicator (0x0000 for 2-key, 0x0001 for 3-key TDES)
        // bytes 6-7 = key length in bits (0x0080 for 2-key, 0x00C0 for 3-key)

        byte[] kdInput = new byte[] {
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80
        };

        // Determine number of calls based on KBPK length
        int[] calls;
        if (kbpk.length == 16) {
            // 2-key TDES
            kdInput[4] = 0x00;
            kdInput[5] = 0x00;
            kdInput[6] = 0x00;
            kdInput[7] = (byte) 0x80;
            calls = new int[] { 1, 2 };
        } else {
            // 3-key TDES (24 bytes)
            kdInput[4] = 0x00;
            kdInput[5] = 0x01;
            kdInput[6] = 0x00;
            kdInput[7] = (byte) 0xC0;
            calls = new int[] { 1, 2, 3 };
        }

        byte[] kbek = new byte[calls.length * 8];
        byte[] kbak = new byte[calls.length * 8];

        // Derive keys
        for (int i = 0; i < calls.length; i++) {
            kdInput[0] = (byte) calls[i];

            // Derive KBEK (key usage = 0x0000)
            kdInput[1] = 0x00;
            kdInput[2] = 0x00;
            byte[] xored = xor(kdInput, k1);
            byte[] mac = generateCBCMAC_DES(kbpk, xored, 8);
            System.arraycopy(mac, 0, kbek, i * 8, 8);

            // Derive KBAK (key usage = 0x0001)
            kdInput[1] = 0x00;
            kdInput[2] = 0x01;
            xored = xor(kdInput, k1);
            mac = generateCBCMAC_DES(kbpk, xored, 8);
            System.arraycopy(mac, 0, kbak, i * 8, 8);
        }

        return new KeyPair(kbek, kbak);
    }

    private byte[] generateCBCMAC_DES(byte[] key, byte[] data, int macLen) throws Exception {
        // CBC-MAC with DES - data is already 8 bytes, no padding needed
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "DESede");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[8]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(data);
        return Arrays.copyOfRange(encrypted, encrypted.length - macLen, encrypted.length);
    }

    // ==================== Version D (AES) ====================

    private String wrapVersionD(String header, byte[] key, Integer maskedKeyLen) throws Exception {
        // Derive keys (always AES for version D)
        KeyPair keys = deriveKeysVersionD();

        // Format key data: 2 bytes length (in bits) + key + padding
        // IMPORTANT: padding is ALWAYS to 16-byte boundary in version D
        int keyLengthBits = key.length * 8;
        int padLen = 16 - ((2 + key.length) % 16);
        if (padLen == 16)
            padLen = 0;

        byte[] clearKeyData = new byte[2 + key.length + padLen];
        clearKeyData[0] = (byte) (keyLengthBits >> 8);
        clearKeyData[1] = (byte) keyLengthBits;
        System.arraycopy(key, 0, clearKeyData, 2, key.length);
        // Padding is zeros (could be random)

        // Update header length FIRST
        String fullHeader = updateHeaderLength(header, clearKeyData.length);

        // Generate MAC over fullHeader + clear key data
        byte[] mac = generateMacVersionD(keys.kbak, fullHeader, clearKeyData);

        // Encrypt using MAC as IV - VERSION D ALWAYS USES AES FOR WRAPPER
        byte[] encryptedKey = encryptCBC(keys.kbek, mac, clearKeyData, "AES");

        return fullHeader + bytesToHex(encryptedKey) + bytesToHex(mac);
    }

    private UnwrapResult unwrapVersionD(String keyBlock) throws Exception {
        // Parse the fixed header plus any authenticated optional blocks.
        int headerLength = headerLength(keyBlock);
        String header = keyBlock.substring(0, headerLength);
        int length = Integer.parseInt(keyBlock.substring(1, 5));

        // Version D ALWAYS uses 16-byte MAC
        int macLenChars = 32;

        // Extract encrypted key and MAC
        String encryptedKeyHex = keyBlock.substring(headerLength, length - macLenChars);
        String macHex = keyBlock.substring(length - macLenChars);

        byte[] encryptedKey = hexToBytes(encryptedKeyHex);
        byte[] receivedMac = hexToBytes(macHex);

        // Derive keys (always AES derivation for version D)
        KeyPair keys = deriveKeysVersionD();

        // Decrypt using MAC as IV - VERSION D ALWAYS USES AES FOR WRAPPER
        byte[] clearKeyData = decryptCBC(keys.kbek, receivedMac, encryptedKey, "AES");

        // Generate MAC over header + decrypted data
        byte[] calculatedMac = generateMacVersionD(keys.kbak, header, clearKeyData);

        if (!Arrays.equals(receivedMac, calculatedMac)) {
            throw new SecurityException("MAC verification failed");
        }

        // Extract key from clear data: 2 bytes length (in bits) + key + padding
        int keyLengthBits = ((clearKeyData[0] & 0xFF) << 8) | (clearKeyData[1] & 0xFF);
        int keyLengthBytes = keyLengthBits / 8;
        byte[] key = Arrays.copyOfRange(clearKeyData, 2, 2 + keyLengthBytes);

        return new UnwrapResult(header, key);
    }

    private KeyPair deriveKeysVersionD() throws Exception {
        byte[] kbek = new byte[kbpk.length];
        byte[] kbak = new byte[kbpk.length];

        int blockSize = 16;
        byte[][] subkeys = deriveCMACSubkey(kbpk);
        byte[] k1 = subkeys[0];
        byte[] k2 = subkeys[1];

        // Key Derivation data per TR-31 standard
        // byte 0 = counter (starts at 1)
        // bytes 1-2 = key usage indicator (0x0000 for encryption, 0x0001 for MAC)
        // byte 3 = separator (0x00)
        // bytes 4-5 = algorithm indicator (depends on KBPK length)
        // bytes 6-7 = key length in bits
        // bytes 8-15 = padding (0x80 followed by zeros)

        byte[] kdInput = new byte[] {
                0x01, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, (byte) 0x80,
                (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        // Adjust algorithm indicator based on KBPK length
        if (kbpk.length == 16) {
            // AES-128
            kdInput[4] = 0x00;
            kdInput[5] = 0x02;
            kdInput[6] = 0x00;
            kdInput[7] = (byte) 0x80;
        } else if (kbpk.length == 24) {
            // AES-192
            kdInput[4] = 0x00;
            kdInput[5] = 0x03;
            kdInput[6] = 0x00;
            kdInput[7] = (byte) 0xC0;
        } else if (kbpk.length == 32) {
            // AES-256
            kdInput[4] = 0x00;
            kdInput[5] = 0x04;
            kdInput[6] = 0x01;
            kdInput[7] = 0x00;
        }

        // Derive KBEK (key usage = 0x0000)
        kdInput[0] = 0x01; // counter
        kdInput[1] = 0x00;
        kdInput[2] = 0x00;
        for (int i = 0; i < kbek.length / blockSize; i++) {
            kdInput[0] = (byte) (0x01 + i);
            byte[] xored = xor(kdInput, k2);
            byte[] mac = generateCBCMACAES(kbpk, xored, 1, blockSize);
            System.arraycopy(mac, 0, kbek, i * blockSize, Math.min(blockSize, kbek.length - i * blockSize));
        }

        // Derive KBAK (key usage = 0x0001)
        kdInput[0] = 0x01; // counter
        kdInput[1] = 0x00;
        kdInput[2] = 0x01;
        for (int i = 0; i < kbak.length / blockSize; i++) {
            kdInput[0] = (byte) (0x01 + i);
            byte[] xored = xor(kdInput, k2);
            byte[] mac = generateCBCMACAES(kbpk, xored, 1, blockSize);
            System.arraycopy(mac, 0, kbak, i * blockSize, Math.min(blockSize, kbak.length - i * blockSize));
        }

        return new KeyPair(kbek, kbak);
    }

    // ==================== Crypto Primitives ====================

    private byte[] encryptCBC(byte[] key, byte[] iv, byte[] data, String algorithm) throws Exception {
        // Truncate key if needed for TDES
        byte[] useKey = algorithm.equals("DESede") && key.length > 24 ? Arrays.copyOf(key, 24) : key;

        // Truncate IV for TDES (8 bytes) vs AES (16 bytes)
        byte[] useIv = algorithm.equals("DESede") ? Arrays.copyOf(iv, 8) : iv;

        Cipher cipher = Cipher.getInstance(algorithm + "/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(useKey, algorithm);
        IvParameterSpec ivSpec = new IvParameterSpec(useIv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    private byte[] decryptCBC(byte[] key, byte[] iv, byte[] data, String algorithm) throws Exception {
        // Truncate key if needed for TDES
        byte[] useKey = algorithm.equals("DESede") && key.length > 24 ? Arrays.copyOf(key, 24) : key;

        // Truncate IV for TDES (8 bytes) vs AES (16 bytes)
        byte[] useIv = algorithm.equals("DESede") ? Arrays.copyOf(iv, 8) : iv;

        Cipher cipher = Cipher.getInstance(algorithm + "/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(useKey, algorithm);
        IvParameterSpec ivSpec = new IvParameterSpec(useIv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    private byte[] generateMacVersionD(byte[] kbak, String header, byte[] keyData) throws Exception {
        // CMAC for version D: XOR last block with K1/K2, then CBC-MAC
        byte[][] subkeys = deriveCMACSubkey(kbak);
        byte[] km1 = subkeys[0];
        byte[] km2 = subkeys[1];

        // Build MAC input: header (ASCII) + key data
        byte[] headerBytes = header.getBytes("ASCII");
        byte[] macInput = new byte[headerBytes.length + keyData.length];
        System.arraycopy(headerBytes, 0, macInput, 0, headerBytes.length);
        System.arraycopy(keyData, 0, macInput, headerBytes.length, keyData.length);

        int blockSize = 16;
        boolean isCompleteBlock = (macInput.length > 0 && macInput.length % blockSize == 0);

        byte[] paddedData;
        byte[] activeKey;
        if (isCompleteBlock) {
            paddedData = macInput;
            activeKey = km1;
        } else {
            int padLen = blockSize - (macInput.length % blockSize);
            paddedData = new byte[macInput.length + padLen];
            System.arraycopy(macInput, 0, paddedData, 0, macInput.length);
            paddedData[macInput.length] = (byte) 0x80;
            activeKey = km2;
        }

        // XOR last 16 bytes with KM1 or KM2
        int lastBlockStart = paddedData.length - blockSize;
        byte[] lastBlock = Arrays.copyOfRange(paddedData, lastBlockStart, paddedData.length);
        byte[] xoredLastBlock = xor(lastBlock, activeKey);
        System.arraycopy(xoredLastBlock, 0, paddedData, lastBlockStart, blockSize);

        // Generate CBC-MAC with AES
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(kbak, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[blockSize]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(paddedData);

        // Return last 16 bytes
        return Arrays.copyOfRange(encrypted, encrypted.length - blockSize, encrypted.length);
    }

    private byte[] encryptTDES(byte[] key, byte[] data) throws Exception {
        // If key is AES length (32 bytes), truncate to TDES length (24 bytes)
        byte[] tdesKey = (key.length > 24) ? Arrays.copyOf(key, 24) : key;

        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(tdesKey, "DESede");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[8]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    private byte[] decryptTDES(byte[] key, byte[] data) throws Exception {
        // If key is AES length (32 bytes), truncate to TDES length (24 bytes)
        byte[] tdesKey = (key.length > 24) ? Arrays.copyOf(key, 24) : key;

        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(tdesKey, "DESede");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[8]);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    private byte[] encryptAES(byte[] key, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    private byte[] decryptAES(byte[] key, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    private byte[] generateCBCMACAES(byte[] key, byte[] data, int blockCount, int macLen) throws Exception {
        // CBC-MAC for AES
        byte[] paddedData = padData(data, 16);
        // Only process the specified number of blocks
        int dataLen = Math.min(blockCount * 16, paddedData.length);
        byte[] dataToMac = Arrays.copyOf(paddedData, dataLen);

        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(dataToMac);
        return Arrays.copyOfRange(encrypted, encrypted.length - macLen, encrypted.length);
    }

    private byte[] generateCMAC(byte[] key, byte[] km1, byte[] data) throws Exception {
        // CMAC (AES-CMAC) for version D
        byte[] paddedData = padData(data, 16);

        // XOR last block with KM1
        int lastBlockStart = paddedData.length - 16;
        byte[] lastBlock = Arrays.copyOfRange(paddedData, lastBlockStart, paddedData.length);
        byte[] xoredLastBlock = xor(lastBlock, km1);
        System.arraycopy(xoredLastBlock, 0, paddedData, lastBlockStart, 16);

        // Generate CBC-MAC
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(paddedData);
        return Arrays.copyOfRange(encrypted, encrypted.length - 16, encrypted.length);
    }

    private byte[][] deriveCMACSubkey(byte[] key) throws Exception {
        // Derive K1 and K2 for AES-CMAC
        byte[] rb = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x87 };

        // Encrypt zero block
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] l = cipher.doFinal(new byte[16]);

        // Derive K1
        byte[] k1 = shiftLeft(l);
        if ((l[0] & 0x80) != 0) {
            k1 = xor(k1, rb);
        }

        // Derive K2
        byte[] k2 = shiftLeft(k1);
        if ((k1[0] & 0x80) != 0) {
            k2 = xor(k2, rb);
        }

        return new byte[][] { k1, k2 };
    }

    private byte[][] deriveDESCMACSubkey(byte[] key) throws Exception {
        // Derive K1 and K2 for DES-CMAC (8 bytes each)
        byte[] r64 = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1B };

        // Encrypt zero block with TDES
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "DESede");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] s = cipher.doFinal(new byte[8]);

        // Derive K1
        byte[] k1 = shiftLeft8(s);
        if ((s[0] & 0x80) != 0) {
            k1 = xor(k1, r64);
        }

        // Derive K2
        byte[] k2 = shiftLeft8(k1);
        if ((k1[0] & 0x80) != 0) {
            k2 = xor(k2, r64);
        }

        return new byte[][] { k1, k2 };
    }

    private byte[] shiftLeft8(byte[] data) {
        // Shift left for 8-byte array
        byte[] result = new byte[8];
        int carry = 0;

        for (int i = 7; i >= 0; i--) {
            int val = (data[i] & 0xFF) << 1;
            result[i] = (byte) (val | carry);
            carry = (val & 0x100) >> 8;
        }

        return result;
    }

    // ==================== Utility Methods ====================

    private byte[] shiftLeft(byte[] data) {
        byte[] result = new byte[data.length];
        int carry = 0;

        for (int i = data.length - 1; i >= 0; i--) {
            int val = (data[i] & 0xFF) << 1;
            result[i] = (byte) (val | carry);
            carry = (val & 0x100) >> 8;
        }

        return result;
    }

    private byte[] xor(byte[] a, byte[] b) {
        byte[] result = new byte[Math.min(a.length, b.length)];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    private byte[] createByteArray(int length, byte value) {
        byte[] result = new byte[length];
        Arrays.fill(result, value);
        return result;
    }

    private void adjustParity(byte[] key) {
        // Adjust parity bits for DES keys (LSB of each byte)
        for (int i = 0; i < key.length; i++) {
            int b = key[i] & 0xFE; // Clear parity bit
            int parity = 0;
            for (int k = 0; k < 8; k++) {
                if (((b >> k) & 1) == 1)
                    parity++;
            }
            if ((parity % 2) == 0)
                b |= 1; // Set parity bit for odd parity
            key[i] = (byte) b;
        }
    }

    private byte[] padData(byte[] data, int blockSize) {
        int padLen = (blockSize - (data.length % blockSize)) % blockSize;
        if (padLen == 0 && data.length > 0) {
            return data;
        }
        byte[] padded = new byte[data.length + padLen];
        System.arraycopy(data, 0, padded, 0, data.length);
        return padded;
    }

    private String updateHeaderLength(String header, int keyDataLen) {
        char version = header.charAt(0);

        // Determine MAC length based on version
        int macLen;
        if (version == 'A' || version == 'C') {
            macLen = 4; // 4 bytes for A/C
        } else if (version == 'B') {
            macLen = 8; // 8 bytes for B
        } else {
            macLen = 16; // 16 bytes for D
        }

        // Include the fixed header and its optional blocks in the protected total.
        int totalLenChars = header.length() + (keyDataLen * 2) + (macLen * 2);

        // Update length in header
        String lengthStr = String.format("%04d", totalLenChars);
        return header.charAt(0) + lengthStr + header.substring(5);
    }

    private String updateHeaderLength(String header, int keyDataLen, char algorithm) {
        // Version D always uses 16-byte MAC regardless of algorithm
        return updateHeaderLength(header, keyDataLen);
    }

    /** Returns the ASCII-header length, including compact optional blocks. */
    private static int headerLength(String keyBlock) {
        if (keyBlock == null || keyBlock.length() < 16) throw new IllegalArgumentException("TR-31 key block too short for its fixed header");
        final int optionalCount;
        try {
            optionalCount = Integer.parseInt(keyBlock.substring(12, 14), 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("TR-31 optional-block count is not hexadecimal", e);
        }
        int position = 16;
        for (int index = 0; index < optionalCount; index++) {
            if (position + 4 > keyBlock.length()) throw new IllegalArgumentException("TR-31 optional block " + (index + 1) + " header is truncated");
            final int dataBytes;
            try {
                dataBytes = Integer.parseInt(keyBlock.substring(position + 2, position + 4), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("TR-31 optional block length is not hexadecimal", e);
            }
            position += 4 + (dataBytes * 2);
            if (position > keyBlock.length()) throw new IllegalArgumentException("TR-31 optional block " + (index + 1) + " is truncated");
        }
        return position;
    }

    // ==================== Helper Classes ====================

    private static class KeyPair {
        final byte[] kbek;
        final byte[] kbak;

        KeyPair(byte[] kbek, byte[] kbak) {
            this.kbek = kbek;
            this.kbak = kbak;
        }
    }

    public static class UnwrapResult {
        public final String header;
        public final byte[] key;

        public UnwrapResult(String header, byte[] key) {
            this.header = header;
            this.key = key;
        }
    }

    // ==================== Test Main ====================

    public static void main(String[] args) {
        try {
            System.out.println("=== TR-31 Test Suite ===\n");

            // Test 1: Version D - AES Encryption
            System.out.println("Test 1: Version D - AES Cipher Mode");
            String kbpk1 = "ae81a9085ee10c51f52e91ef432bedb025463d38cfb8a0e578a69ee72311d843";
            String key1 = "FDE3B6FE76CDCD92D6A4D0205BF26E974F5E51F7310E570EB546A22C915220B5";
            String header1 = "D0144D0AB00E0000";

            TR31 tr31_1 = new TR31(kbpk1);
            String keyBlock1 = tr31_1.wrap(header1, key1);
            System.out.println("Key Block: " + keyBlock1);
            System.out.println(
                    "Expected:  D0144D0AB00E00006070296D0BDFA232B8982189D19B170E3E8B3D3C6199FE8B0FB54D8CCCC63FCBBC7CB8AAD6C8D2A205A7A68FD90784C7B9F75B7BA4E32185A50B6DC39A75B98A");
            System.out.println("Match: " + keyBlock1.equalsIgnoreCase(
                    "D0144D0AB00E00006070296D0BDFA232B8982189D19B170E3E8B3D3C6199FE8B0FB54D8CCCC63FCBBC7CB8AAD6C8D2A205A7A68FD90784C7B9F75B7BA4E32185A50B6DC39A75B98A"));

            // Unwrap test
            UnwrapResult result1 = tr31_1.unwrap(keyBlock1);
            System.out.println("Unwrapped key: " + bytesToHex(result1.key));
            System.out.println();

            // Test 2: Version D - HMAC Key
            System.out.println("Test 2: Version D - HMAC Key");
            String header2 = "D0144M7HN00E0000";

            TR31 tr31_2 = new TR31(kbpk1);
            String keyBlock2 = tr31_2.wrap(header2, key1);
            System.out.println("Key Block: " + keyBlock2);
            System.out.println(
                    "Expected:  D0144M7HN00E0000C9A999EB8A2B5D4FF2F6EEF9B4FAAFDD90D80AC7AF4A28B8B4F17619C454441398332A6CC6D60E091BAF3CC71E917DE098933880CDE7F1A59D5E92A0204233A4");
            System.out.println("Match: " + keyBlock2.equalsIgnoreCase(
                    "D0144M7HN00E0000C9A999EB8A2B5D4FF2F6EEF9B4FAAFDD90D80AC7AF4A28B8B4F17619C454441398332A6CC6D60E091BAF3CC71E917DE098933880CDE7F1A59D5E92A0204233A4"));
            System.out.println();

            // Test 3: Version B - CVV Key (3DES)
            System.out.println("Test 3: Version B - CVV Key (3DES)");
            String key3 = "32C245C7194C2919E6755734F2CD7ACB73E6BCC8C198DFF4";
            String header3 = "D0112C0TC00E0000";

            TR31 tr31_3 = new TR31(kbpk1);
            String keyBlock3 = tr31_3.wrap(header3, key3);
            System.out.println("Key Block: " + keyBlock3);
            System.out.println(
                    "Expected:  D0112C0TC00E00005EF9F2FB0318E99E15C37837EB7D905B1C6C1CDB11177911E70B14E2B23244961429E2FAC2F5EF39D63121BE38EBC499");
            System.out.println("Match: " + keyBlock3.equalsIgnoreCase(
                    "D0112C0TC00E00005EF9F2FB0318E99E15C37837EB7D905B1C6C1CDB11177911E70B14E2B23244961429E2FAC2F5EF39D63121BE38EBC499"));

            // Unwrap test
            UnwrapResult result3 = tr31_3.unwrap(keyBlock3);
            System.out.println("Unwrapped key: " + bytesToHex(result3.key));
            System.out.println();

            // Test 4: Version D - Cipher Key with TDES algorithm
            System.out.println("Test 4: Version D - Cipher Key (TDES algorithm)");
            String header4 = "D0112D0TB00E0000";

            TR31 tr31_4 = new TR31(kbpk1);
            String keyBlock4 = tr31_4.wrap(header4, key3);
            System.out.println("Key Block: " + keyBlock4);

            // Round-trip test
            UnwrapResult result4 = tr31_4.unwrap(keyBlock4);
            boolean match4 = bytesToHex(result4.key).toUpperCase().startsWith(key3.toUpperCase());
            System.out.println("Round-trip test: " + (match4 ? "PASS" : "FAIL"));
            System.out.println();

            // Test 5: Version D with TDES - different KBPK
            System.out.println("Test 5: Version D - TDES Algorithm");
            String kbpk5 = "BF8C91EF86648ADA8329089EE5800EE6A44C4F49B557FDDA";
            String header5 = "D0112D0TB00E0000";
            String key5 = "32C245C7194C2919E6755734F2CD7ACB73E6BCC8C198DFF4";

            TR31 tr31_5 = new TR31(kbpk5);
            String keyBlock5 = tr31_5.wrap(header5, key5);
            System.out.println("Key Block: " + keyBlock5);

            // Unwrap to verify
            UnwrapResult result5 = tr31_5.unwrap(keyBlock5);
            System.out.println("Unwrapped key: " + bytesToHex(result5.key));
            boolean match5 = bytesToHex(result5.key).toUpperCase().startsWith(key5.toUpperCase());
            System.out.println("Round-trip test: " + (match5 ? "PASS" : "FAIL"));
            System.out.println();

            // Test 6: Version C (deprecated but supported)
            System.out.println("Test 6: Version C - Key Variant Binding (deprecated)");
            String kbpk6 = "89E88CF7931444F334BD7547FC3F380C";
            String header6 = "C0000P0TE00N0000";
            String key6 = "3F419E1CB7079442AA37474C2EFBF8B8";

            TR31 tr31_6 = new TR31(kbpk6);
            String keyBlock6 = tr31_6.wrap(header6, key6);
            System.out.println("Key Block: " + keyBlock6);

            // Unwrap to verify
            UnwrapResult result6 = tr31_6.unwrap(keyBlock6);
            System.out.println("Unwrapped key: " + bytesToHex(result6.key));
            boolean match6 = bytesToHex(result6.key).toUpperCase().startsWith(key6.toUpperCase());
            System.out.println("Round-trip test: " + (match6 ? "PASS" : "FAIL"));
            System.out.println();

            System.out.println("=== All Tests Completed ===");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
