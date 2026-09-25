package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;

/**
 * Key Operations - Key generation, validation, and KCV calculation
 */
public class KeyOperations {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Parity types
     */
    public enum ParityType {
        ODD("Odd"),
        EVEN("Even"),
        NONE("No parity");

        private final String displayName;

        ParityType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Generate a random key with odd parity
     * @param keyType DES (8 bytes), 3DES (16 or 24 bytes), AES-128/192/256 (16/24/32 bytes)
     * @return Random key bytes with odd parity (for DES/3DES)
     */
    public static byte[] generateKey(String keyType) {
        return generateKey(keyType, true); // Default: force odd parity
    }

    public static byte[] generateKey(String keyType, boolean forceOddParity) {
        SecureRandom random = new SecureRandom();
        int keyLength;

        switch (keyType.toUpperCase()) {
            case "DES":
                keyLength = 8;
                break;
            case "3DES":
            case "TRIPLE DES":
            case "3DES-2KEY":
                keyLength = 16;
                break;
            case "3DES-3KEY":
                keyLength = 24;
                break;
            case "AES-128":
                keyLength = 16;
                break;
            case "AES-192":
                keyLength = 24;
                break;
            case "AES-256":
                keyLength = 32;
                break;
            default:
                throw new IllegalArgumentException("Unsupported key type: " + keyType);
        }

        byte[] key = new byte[keyLength];
        random.nextBytes(key);

        // Apply odd parity for DES/3DES keys if requested
        if (forceOddParity && keyType.toUpperCase().contains("DES") && !keyType.toUpperCase().contains("AES")) {
            applyOddParity(key);
        }

        return key;
    }

    /**
     * Apply odd parity to a key (for DES/3DES)
     */
    public static void applyOddParity(byte[] key) {
        for (int i = 0; i < key.length; i++) {
            int b = key[i] & 0xFF;
            int parity = 0;
            for (int j = 0; j < 8; j++) {
                parity ^= (b >> j) & 1;
            }
            // If parity is even, flip the LSB
            if (parity == 0) {
                key[i] ^= 1;
            }
        }
    }

    /**
     * Detect parity type of a key
     */
    public static ParityType detectParity(byte[] key) {
        boolean hasOddParity = true;
        boolean hasEvenParity = true;

        for (byte b : key) {
            int value = b & 0xFF;
            int parity = 0;
            for (int j = 0; j < 8; j++) {
                parity ^= (value >> j) & 1;
            }

            if (parity == 1) {
                hasEvenParity = false;
            } else {
                hasOddParity = false;
            }
        }

        if (hasOddParity) {
            return ParityType.ODD;
        } else if (hasEvenParity) {
            return ParityType.EVEN;
        } else {
            return ParityType.NONE;
        }
    }

    /**
     * Calculate KCV (Key Check Value) - VISA method
     * For DES/3DES keys: Encrypts 8 zero bytes and returns first 3 bytes
     * For AES keys (32 bytes only): Use AES encryption
     *
     * IMPORTANT: Applies odd parity for DES/3DES keys before calculation
     */
    public static byte[] calculateKCV_VISA(byte[] key) throws Exception {
        return calculateKCV_VISA(key, 3);
    }

    /**
     * Calculate a VISA KCV with a caller-selected truncation length.
     */
    public static byte[] calculateKCV_VISA(byte[] key, int kcvLength) throws Exception {
        // Apply odd parity for DES/3DES keys
        byte[] workingKey = key;
        if (key.length == 8 || key.length == 16 || key.length == 24) {
            // DES/3DES - make a copy and apply odd parity
            workingKey = new byte[key.length];
            System.arraycopy(key, 0, workingKey, 0, key.length);
            applyOddParity(workingKey);
        }

        // Only use AES for 32-byte keys (AES-256)
        // 16 and 24 byte keys are ambiguous (could be 3DES or AES), so use 3DES
        if (key.length == 32) {
            return calculateKCV_Generic(workingKey, kcvLength, "AES");
        }
        // For 8, 16, 24 byte keys, use DES/3DES
        return calculateKCV_Generic(workingKey, kcvLength, "DES");
    }

    /**
     * Calculate KCV - IBM method
     *
     * NOTE: IBM KCV algorithm varies by implementation and HSM vendor.
     * This implementation uses standard 3DES-EDE encryption of zero block
     * and returns the first 2 bytes, which matches some IBM implementations
     * but may differ from external tools that use a proprietary CKCV method.
     *
     * For 8-byte keys: Single DES encryption
     * For 16/24-byte keys: 3DES-EDE encryption
     * Returns: First 2 bytes (16 bits) of encrypted result
     *
     * Reference: IBM CCA (Controlled Cryptographic Access) documentation
     */
    public static byte[] calculateKCV_IBM(byte[] key) throws Exception {
        byte[] zeroBlock = new byte[8];
        byte[] encrypted;

        if (key.length == 8) {
            // Single DES - just encrypt
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding", "BC");
            SecretKeySpec keySpec = new SecretKeySpec(key, "DES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            encrypted = cipher.doFinal(zeroBlock);
        } else if (key.length == 16 || key.length == 24) {
            // 3DES - use DESede which does EDE automatically
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding", "BC");
            SecretKeySpec keySpec = new SecretKeySpec(key, "DESede");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            encrypted = cipher.doFinal(zeroBlock);
        } else {
            throw new UnsupportedOperationException("IBM KCV requires DES/3DES key (8, 16, or 24 bytes)");
        }

        // Return first 2 bytes (IBM CCA standard)
        byte[] kcv = new byte[2];
        System.arraycopy(encrypted, 0, kcv, 0, 2);
        return kcv;
    }

    /**
     * Calculate KCV - ATALLA method (same as VISA - 3 bytes)
     */
    public static byte[] calculateKCV_ATALLA(byte[] key) throws Exception {
        return calculateKCV_VISA(key);
    }

    public static byte[] calculateKCV_ATALLA(byte[] key, int kcvLength) throws Exception {
        return calculateKCV_VISA(key, kcvLength);
    }

    /**
     * Calculate KCV - FUTUREX method: the whole DES/TDES key encrypts the block
     * 0123456789ABCDEF and the first 2 bytes are kept. Taken from an external tool
     * capture (KcvCaptureTest); the previous "bytes 2 and 4 of E(K1, 0)" was a guess
     * from one sample and did not reproduce it.
     */
    public static byte[] calculateKCV_FUTUREX(byte[] key) throws Exception {
        if (key.length != 8 && key.length != 16 && key.length != 24) {
            throw new UnsupportedOperationException("FUTUREX KCV applies to DES/TDES keys only");
        }
        byte[] tdesKey = key.length == 16 ? concat(key, java.util.Arrays.copyOf(key, 8))
                : key.length == 8 ? concat(concat(key, key), key) : key;
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding", "BC");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(tdesKey, "DESede"));
        byte[] encrypted = cipher.doFinal(new byte[] {
                0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF });
        return java.util.Arrays.copyOf(encrypted, 2);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * Calculate KCV - ATALLA R method
     * Uses first 8 bytes of key only, then takes bytes 0 and 5 of encrypted block
     * For AES keys, not applicable
     */
    public static byte[] calculateKCV_ATALLA_R(byte[] key) throws Exception {
        // ATALLA R method only makes sense for DES/3DES keys
        if (key.length > 24) {
            throw new UnsupportedOperationException("ATALLA R KCV not applicable to AES-256 keys");
        }
        // Use only first 8 bytes for ATALLA R method
        byte[] key8 = getFirst8Bytes(key);
        byte[] encrypted = encryptZeroBlockWith8ByteKey(key8);
        return new byte[]{encrypted[0], encrypted[5]};
    }

    /**
     * Get first 8 bytes of a key (for methods that use only single DES)
     */
    private static byte[] getFirst8Bytes(byte[] key) {
        if (key.length <= 8) {
            return key;
        }
        byte[] key8 = new byte[8];
        System.arraycopy(key, 0, key8, 0, 8);
        return key8;
    }

    /**
     * Encrypt zero block with 8-byte key (single DES)
     */
    private static byte[] encryptZeroBlockWith8ByteKey(byte[] key8) throws Exception {
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key8, "DES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] zeroBlock = new byte[8];
        return cipher.doFinal(zeroBlock);
    }

    /**
     * Calculate KCV - SHA256 method
     * SHA256 hash of the key, first 3 bytes by default
     */
    public static byte[] calculateKCV_SHA256(byte[] key) throws Exception {
        return calculateKCV_SHA256(key, 3);
    }

    public static byte[] calculateKCV_SHA256(byte[] key, int kcvLength) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key);
        return truncateKcv(hash, kcvLength);
    }

    /**
     * Calculate KCV - CMAC method (AES-CMAC)
     * External tools use CMAC over the EMPTY INPUT (not 16 zeros)
     * Most implementations use a zero block; external tools use the empty input
     */
    public static byte[] calculateKCV_CMAC(byte[] key) throws Exception {
        return calculateKCV_CMAC(key, 3);
    }

    public static byte[] calculateKCV_CMAC(byte[] key, int kcvLength) throws Exception {
        byte[] aesKey;

        if (key.length == 16 || key.length == 24 || key.length == 32) {
            // The whole key: truncating AES-192/256 to 128 bits gave the KCV of another key.
            aesKey = key.clone();
        } else if (key.length == 8) {
            // Single DES - duplicate to make 16 bytes
            aesKey = new byte[16];
            System.arraycopy(key, 0, aesKey, 0, 8);
            System.arraycopy(key, 0, aesKey, 8, 8);
        } else {
            throw new IllegalArgumentException("Invalid key length for CMAC: " + key.length);
        }

        // CMAC of the EMPTY INPUT, as external tools compute it
        Mac mac = Mac.getInstance("AESCMAC", "BC");
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        mac.init(keySpec);
        byte[] cmac = mac.doFinal(new byte[0]);  // Empty input - this is the key!

        return truncateKcv(cmac, kcvLength);
    }

    /**
     * CMAC-based KCV for TDES keys (ANSI X9.24-1:2017): TDES-CMAC over one block
     * of zeros, leftmost 5 bytes. Matches the external tool's "CKCV (TDEA)".
     */
    public static byte[] calculateCKCV_TDEA(byte[] key) {
        if (key.length != 16 && key.length != 24) {
            throw new IllegalArgumentException("CKCV (TDEA) requires a 16 or 24-byte TDES key");
        }
        return ckcv(new org.bouncycastle.crypto.engines.DESedeEngine(), key, 8);
    }

    /**
     * CMAC-based KCV for AES keys (ANSI X9.24-1:2017): AES-CMAC over one block
     * of zeros, leftmost 5 bytes. Matches the external tool's "CKCV (AES)".
     */
    public static byte[] calculateCKCV_AES(byte[] key) {
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("CKCV (AES) requires a 16, 24 or 32-byte AES key");
        }
        return ckcv(new org.bouncycastle.crypto.engines.AESEngine(), key, 16);
    }

    private static byte[] ckcv(org.bouncycastle.crypto.BlockCipher engine, byte[] key, int blockSize) {
        org.bouncycastle.crypto.macs.CMac cmac = new org.bouncycastle.crypto.macs.CMac(engine);
        cmac.init(new org.bouncycastle.crypto.params.KeyParameter(key));
        cmac.update(new byte[blockSize], 0, blockSize);
        byte[] out = new byte[cmac.getMacSize()];
        cmac.doFinal(out, 0);
        return java.util.Arrays.copyOf(out, 5);
    }

    /**
     * Calculate KCV - AES method
     * AES encryption of 16 zero bytes, first 3 bytes by default
     */
    public static byte[] calculateKCV_AES(byte[] key) throws Exception {
        return calculateKCV_AES(key, 3);
    }

    public static byte[] calculateKCV_AES(byte[] key, int kcvLength) throws Exception {
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("AES KCV requires AES key (16, 24, or 32 bytes)");
        }
        return calculateKCV_Generic(key, kcvLength, "AES");
    }

    /**
     * Calculate full zero-block KCV encryption bytes for a given key and algorithm
     */
    public static byte[] calculateFullZeroBlockKCV(byte[] key, String algorithm) throws Exception {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        String algo = algorithm != null ? algorithm.toUpperCase() : "";
        if (algo.contains("DES") || algo.contains("3DES")) {
            byte[] workingKey = key;
            if (key.length == 8 || key.length == 16 || key.length == 24) {
                workingKey = new byte[key.length];
                System.arraycopy(key, 0, workingKey, 0, key.length);
                applyOddParity(workingKey);
            }
            return encryptZeroBlock(workingKey, key.length == 32 ? "AES" : "DES");
        } else {
            return encryptZeroBlock(key, "AES");
        }
    }

    /**
     * Generic KCV calculation
     */
    private static byte[] calculateKCV_Generic(byte[] key, int kcvLength, String algorithm) throws Exception {
        byte[] encrypted = encryptZeroBlock(key, algorithm);
        return truncateKcv(encrypted, kcvLength);
    }

    private static byte[] truncateKcv(byte[] source, int kcvLength) {
        if (kcvLength < 1 || kcvLength > source.length) {
            throw new IllegalArgumentException("KCV length must be between 1 and " + source.length + " bytes");
        }
        byte[] kcv = new byte[kcvLength];
        System.arraycopy(source, 0, kcv, 0, kcvLength);
        return kcv;
    }

    /**
     * Encrypt a zero block with the given key
     */
    private static byte[] encryptZeroBlock(byte[] key, String algorithm) throws Exception {
        String transformation;
        int blockSize;

        if (algorithm.equalsIgnoreCase("DES")) {
            if (key.length == 8) {
                transformation = "DES/ECB/NoPadding";
                blockSize = 8;
            } else {
                // 3DES
                transformation = "DESede/ECB/NoPadding";
                blockSize = 8;
            }
        } else if (algorithm.equalsIgnoreCase("AES")) {
            transformation = "AES/ECB/NoPadding";
            blockSize = 16;
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }

        Cipher cipher = Cipher.getInstance(transformation, "BC");
        SecretKeySpec keySpec;

        if (key.length == 16 || key.length == 24) {
            if (algorithm.equalsIgnoreCase("DES")) {
                keySpec = new SecretKeySpec(key, "DESede");
            } else {
                keySpec = new SecretKeySpec(key, "AES");
            }
        } else if (key.length == 8) {
            keySpec = new SecretKeySpec(key, "DES");
        } else {
            keySpec = new SecretKeySpec(key, "AES");
        }

        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] zeroBlock = new byte[blockSize];
        return cipher.doFinal(zeroBlock);
    }

    /**
     * Validate key length
     */
    public static boolean isValidKeyLength(byte[] key) {
        int length = key.length;
        return length == 8 || length == 16 || length == 24 || length == 32;
    }

    /**
     * Get key type from length
     */
    public static String getKeyType(byte[] key) {
        switch (key.length) {
            case 8:
                return "DES (56-bit)";
            case 16:
                return "3DES 2-key (112-bit) or AES-128";
            case 24:
                return "3DES 3-key (168-bit) or AES-192";
            case 32:
                return "AES-256";
            default:
                return "Unknown/Invalid";
        }
    }

    /**
     * XOR two byte arrays
     */
    public static byte[] xor(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /**
     * Split key into components (key sharing)
     * @param key Original key
     * @param numComponents Number of components to split into (2-5)
     * @return Array of key components that XOR to the original key
     */
    public static byte[][] splitKey(byte[] key, int numComponents) {
        if (numComponents < 2 || numComponents > 5) {
            throw new IllegalArgumentException("Number of components must be between 2 and 5");
        }

        byte[][] components = new byte[numComponents][key.length];
        SecureRandom random = new SecureRandom();

        // Generate random components for n-1 shares
        for (int i = 0; i < numComponents - 1; i++) {
            random.nextBytes(components[i]);
            // Apply odd parity for DES keys
            if (key.length == 8 || key.length == 16 || key.length == 24) {
                applyOddParity(components[i]);
            }
        }

        // Last component is XOR of all previous components with the original key
        // CRITICAL: Do NOT apply parity to this component, as it must be exactly
        // the XOR result to preserve the mathematical property: C0 ⊕ C1 ⊕ C2 = K
        components[numComponents - 1] = key.clone();
        for (int i = 0; i < numComponents - 1; i++) {
            components[numComponents - 1] = xor(components[numComponents - 1], components[i]);
        }

        // NOTE: We do NOT call applyOddParity() here on the last component
        // because it would break the XOR property and combineKeyComponents()
        // would not return the original key

        return components;
    }

    /**
     * Combine key components back into original key
     */
    public static byte[] combineKeyComponents(byte[][] components) {
        if (components == null || components.length == 0) {
            throw new IllegalArgumentException("Components array cannot be empty");
        }

        byte[] result = components[0].clone();
        for (int i = 1; i < components.length; i++) {
            result = xor(result, components[i]);
        }

        return result;
    }
}
