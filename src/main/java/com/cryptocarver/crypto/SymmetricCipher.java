package com.cryptocarver.crypto;

import com.cryptocarver.utils.PaddingUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.util.Arrays;
import java.util.List;

/**
 * Symmetric encryption/decryption operations
 * Supports DES, 3DES, AES with multiple modes and padding schemes
 */
public class SymmetricCipher {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Supported algorithms
     */
    public static final List<String> SUPPORTED_ALGORITHMS = Arrays.asList(
            "DES",
            "3DES (Triple DES)",
            "AES-128",
            "AES-192",
            "AES-256",
            "Salsa20",
            "ChaCha20",
            "ChaCha20-Poly1305",
            "XChaCha20-Poly1305");

    /**
     * Supported cipher modes
     */
    public static final List<String> SUPPORTED_MODES = Arrays.asList(
            "ECB",
            "CBC",
            "CTR",
            "GCM",
            "CFB",
            "OFB");

    /**
     * Supported padding schemes
     */
    public static final List<String> SUPPORTED_PADDINGS = Arrays.asList(
            "NoPadding",
            "PKCS5Padding",
            "PKCS7Padding",
            "ISO10126Padding",
            "ISO7816-4Padding",
            "ZeroBytePadding");

    /**
     * Encrypt data using symmetric cipher (with AAD support)
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key, String algorithm,
            String mode, String padding, byte[] iv, byte[] aad) throws Exception {

        // Validate inputs
        validateInputs(plaintext, key, algorithm, mode, padding, iv);

        // Apply padding if needed
        byte[] paddedData = applyCustomPadding(plaintext, algorithm, mode, padding);

        // Get cipher transformation
        String transformation = buildTransformation(algorithm, mode, padding);

        // Create cipher instance
        Cipher cipher = Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = createSecretKey(key, algorithm);

        // Initialize cipher
        if (mode.equalsIgnoreCase("ECB")) {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        } else if (mode.equalsIgnoreCase("GCM")) {
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
        } else {
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        }

        return cipher.doFinal(paddedData);
    }

    public static byte[] encrypt(byte[] plaintext, byte[] key, String algorithm,
            String mode, String padding, byte[] iv) throws Exception {
        return encrypt(plaintext, key, algorithm, mode, padding, iv, null);
    }

    /**
     * Decrypt data using symmetric cipher (with AAD support)
     */
    public static byte[] decrypt(byte[] ciphertext, byte[] key, String algorithm,
            String mode, String padding, byte[] iv, byte[] aad) throws Exception {

        validateInputs(ciphertext, key, algorithm, mode, padding, iv);
        String transformation = buildTransformation(algorithm, mode, padding);
        Cipher cipher = Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = createSecretKey(key, algorithm);

        if (mode.equalsIgnoreCase("ECB")) {
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
        } else if (mode.equalsIgnoreCase("GCM")) {
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
        } else {
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        }

        byte[] decrypted = cipher.doFinal(ciphertext);
        return removeCustomPadding(decrypted, algorithm, mode, padding);
    }

    public static byte[] decrypt(byte[] ciphertext, byte[] key, String algorithm,
            String mode, String padding, byte[] iv) throws Exception {
        return decrypt(ciphertext, key, algorithm, mode, padding, iv, null);
    }

    /**
     * Build cipher transformation string
     */
    private static String buildTransformation(String algorithm, String mode, String padding) {
        String algo = normalizeAlgorithm(algorithm);
        String pad = normalizePadding(padding);

        return algo + "/" + mode + "/" + pad;
    }

    /**
     * Normalize algorithm name
     */
    private static String normalizeAlgorithm(String algorithm) {
        if (algorithm.startsWith("AES")) {
            return "AES";
        } else if (algorithm.contains("3DES") || algorithm.contains("Triple")) {
            return "DESede"; // Java name for 3DES
        } else if (algorithm.equals("DES")) {
            return "DES";
        }
        return algorithm;
    }

    /**
     * Normalize padding name for Java Cipher
     */
    private static String normalizePadding(String padding) {
        switch (padding) {
            case "PKCS7Padding":
                return "PKCS5Padding"; // Java uses PKCS5 for PKCS7
            case "ISO7816-4Padding":
                return "ISO7816-4Padding";
            case "ZeroBytePadding":
                return "NoPadding"; // Handle manually
            default:
                return padding;
        }
    }

    /**
     * Create SecretKey from byte array
     */
    private static SecretKey createSecretKey(byte[] key, String algorithm) {
        String algo = normalizeAlgorithm(algorithm);
        return new SecretKeySpec(key, algo);
    }

    /**
     * Apply custom padding (for schemes not supported by Java Cipher)
     */
    private static byte[] applyCustomPadding(byte[] data, String algorithm,
            String mode, String padding) {
        // Don't pad for GCM (authenticated encryption)
        if (mode.equalsIgnoreCase("GCM")) {
            return data;
        }

        // Don't pad for stream ciphers
        if (mode.equalsIgnoreCase("CTR") || mode.equalsIgnoreCase("CFB") ||
                mode.equalsIgnoreCase("OFB")) {
            return data;
        }

        // Apply custom padding if needed
        if (padding.equals("ZeroBytePadding")) {
            int blockSize = getBlockSize(algorithm);
            return PaddingUtil.addPadding(data, blockSize, PaddingUtil.PaddingType.ZERO);
        }

        return data;
    }

    /**
     * Remove custom padding after decryption
     */
    private static byte[] removeCustomPadding(byte[] data, String algorithm,
            String mode, String padding) {
        // Don't remove padding for GCM
        if (mode.equalsIgnoreCase("GCM")) {
            return data;
        }

        // Don't remove padding for stream ciphers
        if (mode.equalsIgnoreCase("CTR") || mode.equalsIgnoreCase("CFB") ||
                mode.equalsIgnoreCase("OFB")) {
            return data;
        }

        // Remove custom padding if needed
        if (padding.equals("ZeroBytePadding")) {
            return PaddingUtil.removePadding(data, PaddingUtil.PaddingType.ZERO);
        }

        return data;
    }

    /**
     * Get block size for algorithm
     */
    private static int getBlockSize(String algorithm) {
        if (algorithm.startsWith("AES")) {
            return 16; // AES block size
        } else if (algorithm.contains("DES")) {
            return 8; // DES/3DES block size
        }
        return 16; // Default
    }

    /**
     * Validate inputs
     */
    private static void validateInputs(byte[] data, byte[] key, String algorithm,
            String mode, String padding, byte[] iv) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        // Validate key length
        validateKeyLength(key, algorithm);

        // Validate IV for modes that require it
        // Validate IV for modes that require it
        if (!mode.equalsIgnoreCase("ECB")) {
            // ChaCha20 requires IV but is stream cipher, handled differently
            if (algorithm.startsWith("ChaCha20")) {
                if (iv == null || iv.length == 0) {
                    throw new IllegalArgumentException("ChaCha20 requires an IV/Nonce");
                }
                // Allow 8 (legacy) or 12 (RFC7539)
                if (iv.length != 8 && iv.length != 12) {
                    throw new IllegalArgumentException("ChaCha20 requires 8-byte (Legacy) or 12-byte (RFC 7539) Nonce");
                }
                return;
            }

            if (iv == null || iv.length == 0) {
                throw new IllegalArgumentException(mode + " mode requires an IV");
            }
            validateIVLength(iv, algorithm, mode);
        }
    }

    /**
     * Validate key length
     */
    private static void validateKeyLength(byte[] key, String algorithm) {
        int expectedLength = 0;

        if (algorithm.equals("DES")) {
            expectedLength = 8; // 64 bits
        } else if (algorithm.contains("3DES") || algorithm.contains("Triple")) {
            expectedLength = 24; // 192 bits (3 x 64)
        } else if (algorithm.equals("AES-128")) {
            expectedLength = 16; // 128 bits
        } else if (algorithm.equals("AES-192")) {
            expectedLength = 24; // 192 bits
        } else if (algorithm.equals("AES-256")) {
            expectedLength = 32; // 256 bits
        }

        if (expectedLength > 0 && key.length != expectedLength) {
            throw new IllegalArgumentException(
                    String.format("%s requires a %d-byte (%d-bit) key, got %d bytes",
                            algorithm, expectedLength, expectedLength * 8, key.length));
        }
    }

    /**
     * Validate IV length
     */
    private static void validateIVLength(byte[] iv, String algorithm, String mode) {
        // Special case for ChaCha20-Poly1305 (12 bytes)
        if (algorithm.equals("ChaCha20-Poly1305") && iv.length == 12) {
            return;
        }
        // Special case for XChaCha20-Poly1305 (24 bytes)
        if (algorithm.equals("XChaCha20-Poly1305") && iv.length == 24) {
            return;
        }

        int expectedLength = getBlockSize(algorithm);

        if (mode.equalsIgnoreCase("GCM")) {
            // GCM can use 8, 12 (recommended), or 16 bytes
            // 12 bytes (96 bits) is the NIST recommended size
            if (iv.length != 8 && iv.length != 12 && iv.length != 16) {
                throw new IllegalArgumentException(
                        "GCM mode requires an 8, 12, or 16-byte IV (12 bytes recommended), got " + iv.length
                                + " bytes");
            }
            return;
        }

        if (iv.length != expectedLength) {
            throw new IllegalArgumentException(
                    String.format("%s requires a %d-byte IV, got %d bytes",
                            mode, expectedLength, iv.length));
        }
    }

    /**
     * Get required key length in bytes for algorithm
     */
    public static int getKeyLength(String algorithm) {
        if (algorithm.equals("DES"))
            return 8;
        if (algorithm.contains("3DES"))
            return 24;
        if (algorithm.equals("AES-128"))
            return 16;
        if (algorithm.equals("AES-192"))
            return 24;
        if (algorithm.equals("AES-256"))
            return 32;
        return 16; // Default
    }

    /**
     * Check if mode requires IV
     */
    public static boolean requiresIV(String mode) {
        return !mode.equalsIgnoreCase("ECB");
    }

    /**
     * Check if mode supports padding
     */
    public static boolean supportsPadding(String mode) {
        // Stream ciphers and GCM don't use padding
        return !mode.equalsIgnoreCase("CTR") &&
                !mode.equalsIgnoreCase("CFB") &&
                !mode.equalsIgnoreCase("OFB") &&
                !mode.equalsIgnoreCase("GCM");
    }

    /**
     * Check if algorithm is a stream cipher
     */
    public static boolean isStreamCipher(String algorithm) {
        return algorithm.equals("Salsa20") ||
                algorithm.equals("ChaCha20") ||
                algorithm.equals("ChaCha20-Poly1305") ||
                algorithm.equals("XChaCha20-Poly1305");
    }

    /**
     * Encrypt with Salsa20 stream cipher
     */
    public static byte[] encryptSalsa20(byte[] plaintext, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32) {
            throw new IllegalArgumentException("Salsa20 requires 256-bit (32 byte) key");
        }
        if (nonce.length != 8) {
            throw new IllegalArgumentException("Salsa20 requires 64-bit (8 byte) nonce");
        }

        Cipher cipher = Cipher.getInstance("Salsa20", BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, "Salsa20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypt with Salsa20 stream cipher
     */
    public static byte[] decryptSalsa20(byte[] ciphertext, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32) {
            throw new IllegalArgumentException("Salsa20 requires 256-bit (32 byte) key");
        }
        if (nonce.length != 8) {
            throw new IllegalArgumentException("Salsa20 requires 64-bit (8 byte) nonce");
        }

        Cipher cipher = Cipher.getInstance("Salsa20", BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, "Salsa20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(ciphertext);
    }

    /**
     * Encrypt with ChaCha20 stream cipher
     */
    public static byte[] encryptChaCha20(byte[] plaintext, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32) {
            throw new IllegalArgumentException("ChaCha20 requires 256-bit (32 byte) key");
        }

        String cipherName;
        if (nonce.length == 12) {
            cipherName = "ChaCha20"; // RFC 7539
        } else if (nonce.length == 8) {
            cipherName = "ChaCha"; // Legacy (DJB)
        } else {
            throw new IllegalArgumentException("ChaCha20 requires 12-byte (RFC 7539) or 8-byte (Legacy) nonce");
        }

        Cipher cipher = Cipher.getInstance(cipherName, BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, "ChaCha20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypt with ChaCha20 stream cipher
     */
    public static byte[] decryptChaCha20(byte[] ciphertext, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32) {
            throw new IllegalArgumentException("ChaCha20 requires 256-bit (32 byte) key");
        }

        String cipherName;
        if (nonce.length == 12) {
            cipherName = "ChaCha20"; // RFC 7539
        } else if (nonce.length == 8) {
            cipherName = "ChaCha"; // Legacy (DJB)
        } else {
            throw new IllegalArgumentException("ChaCha20 requires 12-byte (RFC 7539) or 8-byte (Legacy) nonce");
        }

        Cipher cipher = Cipher.getInstance(cipherName, BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, "ChaCha20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        return cipher.doFinal(ciphertext);
    }

    /**
     * Encrypt with ChaCha20-Poly1305 AEAD
     * Returns ciphertext with authentication tag appended
     */
    public static byte[] encryptChaCha20Poly1305(byte[] plaintext, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 requires 256-bit (32 byte) key");
        }
        if (nonce.length != 12) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 requires 96-bit (12 byte) nonce");
        }

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305", BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, "ChaCha20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        // Returns ciphertext + 16-byte authentication tag
        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypt with ChaCha20-Poly1305 AEAD
     * Expects ciphertext with authentication tag appended
     */
    public static byte[] decryptChaCha20Poly1305(byte[] ciphertextWithTag, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 requires 256-bit (32 byte) key");
        }
        if (nonce.length != 12) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 requires 96-bit (12 byte) nonce");
        }
        if (ciphertextWithTag.length < 16) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 ciphertext too short (must include 16-byte tag)");
        }

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305", BouncyCastleProvider.PROVIDER_NAME);
        SecretKey secretKey = new SecretKeySpec(key, "ChaCha20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        // Automatically verifies and removes the 16-byte authentication tag
        return cipher.doFinal(ciphertextWithTag);
    }

    /**
     * Extract authentication tag from ChaCha20-Poly1305 output
     * Last 16 bytes are the Poly1305 authentication tag
     */
    public static byte[] extractChaCha20Tag(byte[] ciphertextWithTag) {
        if (ciphertextWithTag.length < 16) {
            throw new IllegalArgumentException("Data too short to contain authentication tag");
        }
        byte[] tag = new byte[16];
        System.arraycopy(ciphertextWithTag, ciphertextWithTag.length - 16, tag, 0, 16);
        return tag;
    }

    /**
     * Extract ciphertext (without tag) from ChaCha20-Poly1305 output
     */
    public static byte[] extractChaCha20Ciphertext(byte[] ciphertextWithTag) {
        if (ciphertextWithTag.length < 16) {
            throw new IllegalArgumentException("Data too short to contain ciphertext and tag");
        }
        byte[] ciphertext = new byte[ciphertextWithTag.length - 16];
        System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, ciphertext.length);
        return ciphertext;
    }

    /**
     * Combine ciphertext and tag for ChaCha20-Poly1305
     */
    public static byte[] combineChaCha20CiphertextAndTag(byte[] ciphertext, byte[] tag) {
        if (tag.length != 16) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 tag must be 16 bytes");
        }
        byte[] combined = new byte[ciphertext.length + 16];
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
        System.arraycopy(tag, 0, combined, ciphertext.length, 16);
        return combined;
    }

    /**
     * Encrypt with XChaCha20-Poly1305
     * Uses HChaCha20 to derive subkey, then ChaCha20-Poly1305
     */
    public static byte[] encryptXChaCha20Poly1305(byte[] plaintext, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32)
            throw new IllegalArgumentException("XChaCha20-Poly1305 requires 32-byte key");
        if (nonce.length != 24)
            throw new IllegalArgumentException("XChaCha20-Poly1305 requires 24-byte nonce");

        // 1. Derive subkey using HChaCha20
        byte[] subKey = hChaCha20(key, Arrays.copyOfRange(nonce, 0, 16));

        // 2. Prepare new nonce (4 bytes zero + last 8 bytes of original nonce)
        byte[] newNonce = new byte[12];
        System.arraycopy(nonce, 16, newNonce, 4, 8); // First 4 bytes remain 0, last 8 from nonce

        // 3. Encrypt using standard ChaCha20-Poly1305 with subkey and new nonce
        return encryptChaCha20Poly1305(plaintext, subKey, newNonce);
    }

    /**
     * Decrypt with XChaCha20-Poly1305
     */
    public static byte[] decryptXChaCha20Poly1305(byte[] ciphertextWithTag, byte[] key, byte[] nonce) throws Exception {
        if (key.length != 32)
            throw new IllegalArgumentException("XChaCha20-Poly1305 requires 32-byte key");
        if (nonce.length != 24)
            throw new IllegalArgumentException("XChaCha20-Poly1305 requires 24-byte nonce");

        // 1. Derive subkey using HChaCha20
        byte[] subKey = hChaCha20(key, Arrays.copyOfRange(nonce, 0, 16));

        // 2. Prepare new nonce
        byte[] newNonce = new byte[12];
        System.arraycopy(nonce, 16, newNonce, 4, 8);

        // 3. Decrypt
        return decryptChaCha20Poly1305(ciphertextWithTag, subKey, newNonce);
    }

    /**
     * HChaCha20 Key Derivation Function
     * Inputs: Key (32 bytes), Nonce (16 bytes)
     * Output: Derived Key (32 bytes)
     */
    private static byte[] hChaCha20(byte[] key, byte[] nonce) {
        int[] state = new int[16];
        int[] k = toIntArray(key);
        int[] n = toIntArray(nonce);

        // Constants "expand 32-byte k"
        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;

        // Key
        System.arraycopy(k, 0, state, 4, 8);

        // Nonce
        System.arraycopy(n, 0, state, 12, 4);

        // 20 Rounds
        for (int i = 0; i < 10; i++) {
            quarterRound(state, 0, 4, 8, 12);
            quarterRound(state, 1, 5, 9, 13);
            quarterRound(state, 2, 6, 10, 14);
            quarterRound(state, 3, 7, 11, 15);
            quarterRound(state, 0, 5, 10, 15);
            quarterRound(state, 1, 6, 11, 12);
            quarterRound(state, 2, 7, 8, 13);
            quarterRound(state, 3, 4, 9, 14);
        }

        // Output: State[0-3] + State[12-15]
        byte[] out = new byte[32];
        int[] resultInts = new int[8];
        System.arraycopy(state, 0, resultInts, 0, 4);
        System.arraycopy(state, 12, resultInts, 4, 4);

        for (int i = 0; i < 8; i++) {
            intToBytes(resultInts[i], out, i * 4);
        }
        return out;
    }

    private static void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] += x[b];
        x[d] = rotateLeft(x[d] ^ x[a], 16);
        x[c] += x[d];
        x[b] = rotateLeft(x[b] ^ x[c], 12);
        x[a] += x[b];
        x[d] = rotateLeft(x[d] ^ x[a], 8);
        x[c] += x[d];
        x[b] = rotateLeft(x[b] ^ x[c], 7);
    }

    private static int rotateLeft(int i, int distance) {
        return (i << distance) | (i >>> (32 - distance));
    }

    private static int[] toIntArray(byte[] input) {
        int[] res = new int[input.length / 4];
        for (int i = 0; i < res.length; i++) {
            res[i] = ((input[i * 4] & 0xFF)) |
                    ((input[i * 4 + 1] & 0xFF) << 8) |
                    ((input[i * 4 + 2] & 0xFF) << 16) |
                    ((input[i * 4 + 3] & 0xFF) << 24);
        }
        return res;
    }

    private static void intToBytes(int i, byte[] output, int offset) {
        output[offset] = (byte) (i & 0xFF);
        output[offset + 1] = (byte) ((i >> 8) & 0xFF);
        output[offset + 2] = (byte) ((i >> 16) & 0xFF);
        output[offset + 3] = (byte) ((i >> 24) & 0xFF);
    }
}
