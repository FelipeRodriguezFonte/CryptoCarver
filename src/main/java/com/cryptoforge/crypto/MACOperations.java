package com.cryptoforge.crypto;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.DESEngine;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.macs.CBCBlockCipherMac;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.macs.GMac;
import org.bouncycastle.crypto.macs.ISO9797Alg3Mac;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

/**
 * Message Authentication Code (MAC) operations
 * Supports: CBC-MAC, HMAC, CMAC, Retail MAC (ISO 9797-1 Algorithm 3)
 * 
 * @author Felipe
 */
public class MACOperations {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Supported MAC algorithms
     */
    public static final String[] SUPPORTED_ALGORITHMS = {
            "HMAC-SHA1",
            "HMAC-SHA256",
            "HMAC-SHA384",
            "HMAC-SHA512",
            "CMAC-AES",
            "CMAC-3DES",
            "CBC-MAC-DES",
            "CBC-MAC-3DES",
            "CBC-MAC-AES",
            "ISO-9797-1-ALG1",
            "ANSI-X9.9",
            "ANSI-X9.19",
            "AS2805.4.1",
            "Retail-MAC-DES",
            "Retail-MAC-3DES"
    };

    /**
     * Generate MAC for data
     * 
     * @param data      Data to authenticate
     * @param key       MAC key (size depends on algorithm)
     * @param algorithm MAC algorithm
     * @return MAC value
     */
    public static byte[] generate(byte[] data, byte[] key, String algorithm) throws Exception {
        return generate(data, key, algorithm, com.cryptoforge.util.ProgressMonitor.NO_OP);
    }

    public static byte[] generate(byte[] data, byte[] key, String algorithm, com.cryptoforge.util.ProgressMonitor monitor) throws Exception {
        validateKeySize(key, algorithm);

        if (algorithm.startsWith("HMAC-")) {
            return generateHMAC(data, key, algorithm);
        } else if (algorithm.startsWith("CMAC-")) {
            return generateCMAC(data, key, algorithm);
        } else if (algorithm.startsWith("CBC-MAC-")) {
            return generateCBCMAC(data, key, algorithm);
        } else if (algorithm.equals("ISO-9797-1-ALG1")) {
            return generateISO9797Alg1(data, key);
        } else if (algorithm.equals("ANSI-X9.9")) {
            return generateANSIX99(data, key);
        } else if (algorithm.equals("ANSI-X9.19")) {
            return generateANSIX919(data, key);
        } else if (algorithm.equals("AS2805.4.1")) {
            return generateAS2805(data, key);
        } else if (algorithm.startsWith("Retail-MAC-")) {
            return generateRetailMAC(data, key, algorithm);
        } else {
            throw new IllegalArgumentException("Unknown MAC algorithm: " + algorithm);
        }
    }

    /**
     * Generate MAC for a file via streaming
     */
    public static byte[] generate(java.nio.file.Path file, byte[] key, String algorithm) throws Exception {
        return generate(file, key, algorithm, com.cryptoforge.util.ProgressMonitor.NO_OP);
    }

    /**
     * Generate MAC for a file via streaming with progress monitoring
     */
    public static byte[] generate(java.nio.file.Path file, byte[] key, String algorithm, com.cryptoforge.util.ProgressMonitor monitor) throws Exception {
        if (monitor == null) monitor = com.cryptoforge.util.ProgressMonitor.NO_OP;
        validateKeySize(key, algorithm);

        boolean isJceHmac = algorithm.startsWith("HMAC-");
        javax.crypto.Mac jceMac = null;
        org.bouncycastle.crypto.Mac bcMac = null;

        if (isJceHmac) {
            String javaAlgorithm = algorithm;
            jceMac = javax.crypto.Mac.getInstance(javaAlgorithm, "BC");
            SecretKeySpec keySpec = new SecretKeySpec(key, javaAlgorithm);
            jceMac.init(keySpec);
        } else {
            if (algorithm.startsWith("CMAC-")) {
                BlockCipher cipher = algorithm.equals("CMAC-AES") ? new AESEngine() : new DESedeEngine();
                bcMac = new CMac(cipher);
                bcMac.init(new KeyParameter(key));
            } else if (algorithm.startsWith("CBC-MAC-") || algorithm.equals("ISO-9797-1-ALG1") || algorithm.equals("ANSI-X9.9")) {
                BlockCipher cipher;
                String effectiveAlg = algorithm.equals("ISO-9797-1-ALG1") || algorithm.equals("ANSI-X9.9") ? "CBC-MAC-DES" : algorithm;
                if (effectiveAlg.equals("CBC-MAC-DES") || effectiveAlg.equals("Retail-MAC-DES")) cipher = new DESEngine();
                else if (effectiveAlg.equals("CBC-MAC-3DES") || effectiveAlg.equals("Retail-MAC-3DES")) cipher = new DESedeEngine();
                else cipher = new AESEngine();
                bcMac = new CBCBlockCipherMac(cipher);
                bcMac.init(new KeyParameter(key));
            } else if (algorithm.equals("ANSI-X9.19") || algorithm.equals("AS2805.4.1")) {
                bcMac = new ISO9797Alg3Mac(new DESEngine());
                bcMac.init(new KeyParameter(key));
            } else if (algorithm.startsWith("Retail-MAC-")) {
                BlockCipher cipher = algorithm.equals("Retail-MAC-DES") ? new DESEngine() : new DESedeEngine();
                bcMac = new CBCBlockCipherMac(cipher);
                bcMac.init(new KeyParameter(key));
            } else {
                throw new IllegalArgumentException("Unknown MAC algorithm: " + algorithm);
            }
        }

        long bytesProcessed = 0;
        long totalBytes = java.nio.file.Files.size(file);
        try (var input = new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(file), StreamingFileTools.getBufferSize())) {
            byte[] buffer = new byte[StreamingFileTools.getBufferSize()];
            for (int read; (read = input.read(buffer)) != -1;) {
                if (monitor.isCancelled()) throw new java.util.concurrent.CancellationException("MAC operation cancelled");
                if (isJceHmac) {
                    jceMac.update(buffer, 0, read);
                } else {
                    bcMac.update(buffer, 0, read);
                }
                bytesProcessed += read;
                monitor.updateProgress(bytesProcessed, totalBytes);
            }
        }

        if (isJceHmac) {
            return jceMac.doFinal();
        } else {
            byte[] output = new byte[bcMac.getMacSize()];
            bcMac.doFinal(output, 0);
            return output;
        }
    }

    /**
     * Verify MAC value
     * 
     * @param data      Original data
     * @param mac       MAC value to verify
     * @param key       MAC key
     * @param algorithm MAC algorithm
     * @return true if MAC is valid
     */
    public static boolean verify(byte[] data, byte[] mac, byte[] key, String algorithm) throws Exception {
        byte[] computed = generate(data, key, algorithm);
        return constantTimeEquals(computed, mac);
    }

    /** Generates AES-GMAC, authenticating data as AAD without encrypting a payload. */
    public static byte[] generateGmac(byte[] data, byte[] aesKey, byte[] iv) {
        validateAesKey(aesKey, "GMAC");
        if (iv == null || iv.length < 12) {
            throw new IllegalArgumentException("GMAC IV must be at least 12 bytes; use a unique 12-byte nonce by default");
        }
        GMac gmac = new GMac(new GCMBlockCipher(new AESEngine()));
        gmac.init(new ParametersWithIV(new KeyParameter(aesKey), iv));
        if (data != null) gmac.update(data, 0, data.length);
        byte[] output = new byte[gmac.getMacSize()];
        gmac.doFinal(output, 0);
        return output;
    }

    /** Generates a one-time-key Poly1305 tag (RFC 8439). */
    public static byte[] generatePoly1305(byte[] data, byte[] oneTimeKey) {
        if (oneTimeKey == null || oneTimeKey.length != 32) {
            throw new IllegalArgumentException("Poly1305 requires a unique 32-byte one-time key");
        }
        Poly1305 poly1305 = new Poly1305();
        poly1305.init(new KeyParameter(oneTimeKey));
        if (data != null) poly1305.update(data, 0, data.length);
        byte[] output = new byte[poly1305.getMacSize()];
        poly1305.doFinal(output, 0);
        return output;
    }

    /** Constant-time byte comparison, including the length check. */
    public static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if (expected == null || actual == null) return false;
        int result = expected.length ^ actual.length;
        int max = Math.max(expected.length, actual.length);
        for (int i = 0; i < max; i++) {
            byte left = i < expected.length ? expected[i] : 0;
            byte right = i < actual.length ? actual[i] : 0;
            result |= left ^ right;
        }
        return result == 0;
    }

    /**
     * Generate HMAC (Hash-based Message Authentication Code)
     */
    private static byte[] generateHMAC(byte[] data, byte[] key, String algorithm) throws Exception {
        String javaAlgorithm = algorithm; // "HMAC-SHA256" etc.

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance(javaAlgorithm, "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, javaAlgorithm);
        mac.init(keySpec);

        return mac.doFinal(data);
    }

    /**
     * Generate CMAC (Cipher-based Message Authentication Code)
     * CMAC is based on a block cipher (AES or 3DES)
     */
    private static byte[] generateCMAC(byte[] data, byte[] key, String algorithm) throws Exception {
        BlockCipher cipher;

        if (algorithm.equals("CMAC-AES")) {
            cipher = new AESEngine();
        } else if (algorithm.equals("CMAC-3DES")) {
            cipher = new DESedeEngine();
        } else {
            throw new IllegalArgumentException("Unknown CMAC cipher: " + algorithm);
        }

        CMac cmac = new CMac(cipher);
        KeyParameter keyParam = new KeyParameter(key);
        cmac.init(keyParam);

        cmac.update(data, 0, data.length);

        byte[] output = new byte[cmac.getMacSize()];
        cmac.doFinal(output, 0);

        return output;
    }

    /**
     * Generate CBC-MAC (Cipher Block Chaining MAC)
     * CBC-MAC is the last block of CBC encryption
     */
    private static byte[] generateCBCMAC(byte[] data, byte[] key, String algorithm) throws Exception {
        BlockCipher cipher;

        if (algorithm.equals("CBC-MAC-DES")) {
            cipher = new DESEngine();
        } else if (algorithm.equals("CBC-MAC-3DES")) {
            cipher = new DESedeEngine();
        } else if (algorithm.equals("CBC-MAC-AES")) {
            cipher = new AESEngine();
        } else {
            throw new IllegalArgumentException("Unknown CBC-MAC cipher: " + algorithm);
        }

        CBCBlockCipherMac mac = new CBCBlockCipherMac(cipher);
        KeyParameter keyParam = new KeyParameter(key);
        mac.init(keyParam);

        mac.update(data, 0, data.length);

        byte[] output = new byte[mac.getMacSize()];
        mac.doFinal(output, 0);

        return output;
    }

    /**
     * Generate Retail MAC (ISO 9797-1 Algorithm 3)
     * Note: BP-Tools "Retail MAC" with "Finalize: None" is actually just CBC-MAC
     * For compatibility, we implement CBC-MAC here
     */
    private static byte[] generateRetailMAC(byte[] data, byte[] key, String algorithm) throws Exception {
        if (algorithm.equals("Retail-MAC-DES")) {
            return generateCBCMAC(data, key, "CBC-MAC-DES");
        } else if (algorithm.equals("Retail-MAC-3DES")) {
            // Retail MAC with 3DES (Alg 1)
            return generateCBCMAC(data, key, "CBC-MAC-3DES");
        } else {
            throw new IllegalArgumentException("Unknown Retail MAC cipher: " + algorithm);
        }
    }

    /**
     * Generate ANSI X9.9 MAC
     * Financial standard using DES CBC-MAC with ISO 9797-1 padding method 2 (0x80 +
     * 0x00...)
     * Used in financial messages, produces 8-byte MAC
     */
    private static byte[] generateANSIX99(byte[] data, byte[] key) throws Exception {
        // ANSI X9.9 uses DES in CBC mode with ISO 9797-1 padding method 2
        // This is essentially CBC-MAC-DES
        return generateCBCMAC(data, key, "CBC-MAC-DES");
    }

    /**
     * Generate ISO 9797-1 Algorithm 1 (DES only)
     * Standard CBC-MAC using DES
     * Padding: Method 1 (zero padding)
     */
    private static byte[] generateISO9797Alg1(byte[] data, byte[] key) throws Exception {
        // ISO 9797-1 Algorithm 1 with DES
        // Uses CBC-MAC (same as CBC-MAC-DES)
        return generateCBCMAC(data, key, "CBC-MAC-DES");
    }

    /**
     * Generate ANSI X9.19 MAC (Retail MAC - ISO 9797-1 Algorithm 3)
     * Financial standard using DES with encrypt-decrypt-encrypt on final block
     * Uses ISO9797Alg3Mac from BouncyCastle (Retail MAC)
     */
    private static byte[] generateANSIX919(byte[] data, byte[] key) throws Exception {
        // ANSI X9.19 uses ISO 9797-1 Algorithm 3 (Retail MAC)
        // Key format: K || K' (16 bytes total)

        if (key.length != 16 && key.length != 24) {
            throw new IllegalArgumentException("ANSI X9.19 requires 16-byte (2-key) or 24-byte (3-key) key");
        }

        // ISO9797Alg3Mac implements Retail MAC algorithm:
        // 1. CBC-MAC with DES using K
        // 2. Decrypt last block with K'
        // 3. Encrypt with K
        BlockCipher cipher = new DESEngine();
        ISO9797Alg3Mac mac = new ISO9797Alg3Mac(cipher);

        // Initialize with full key (K||K')
        KeyParameter keyParam = new KeyParameter(key);
        mac.init(keyParam);

        // Process data
        mac.update(data, 0, data.length);

        // Get MAC result
        byte[] output = new byte[mac.getMacSize()];
        mac.doFinal(output, 0);

        return output;
    }

    /**
     * Generate AS2805.4.1 MAC
     * Australian Standard for financial transactions
     * Same algorithm as ANSI X9.19
     */
    private static byte[] generateAS2805(byte[] data, byte[] key) throws Exception {
        // AS2805.4.1 uses same algorithm as ANSI X9.19
        return generateANSIX919(data, key);
    }

    /**
     * Validate key size for algorithm
     */
    private static void validateKeySize(byte[] key, String algorithm) {
        int keyBits = key.length * 8;

        if (algorithm.startsWith("HMAC-")) {
            // HMAC accepts any key size, but recommend at least hash size
            if (keyBits < 128) {
                throw new IllegalArgumentException("HMAC key should be at least 128 bits (16 bytes)");
            }
        } else if (algorithm.contains("AES")) {
            if (keyBits != 128 && keyBits != 192 && keyBits != 256) {
                throw new IllegalArgumentException(
                        "AES key must be 128, 192, or 256 bits (16, 24, or 32 bytes). Got: " + keyBits + " bits");
            }
        } else if (algorithm.equals("ISO-9797-1-ALG1")) {
            // ISO 9797-1 Algorithm 1 with DES (standard only defines DES)
            if (keyBits != 64) {
                throw new IllegalArgumentException(
                        "ISO 9797-1 Algorithm 1 key must be 64 bits (8 bytes, DES). Got: " + keyBits + " bits");
            }
        } else if (algorithm.equals("ANSI-X9.9")) {
            // ANSI X9.9 uses DES (single key)
            if (keyBits != 64) {
                throw new IllegalArgumentException(
                        "ANSI X9.9 key must be 64 bits (8 bytes). Got: " + keyBits + " bits");
            }
        } else if (algorithm.equals("ANSI-X9.19")) {
            // ANSI X9.19 uses 3DES (2-key or 3-key)
            if (keyBits != 128 && keyBits != 192) {
                throw new IllegalArgumentException(
                        "ANSI X9.19 key must be 128 bits (16 bytes, 2-key) or 192 bits (24 bytes, 3-key). Got: "
                                + keyBits + " bits");
            }
        } else if (algorithm.equals("AS2805.4.1")) {
            // AS2805.4.1 uses 3DES (2-key or 3-key)
            if (keyBits != 128 && keyBits != 192) {
                throw new IllegalArgumentException(
                        "AS2805.4.1 key must be 128 bits (16 bytes, 2-key) or 192 bits (24 bytes, 3-key). Got: "
                                + keyBits + " bits");
            }
        } else if (algorithm.equals("Retail-MAC-DES")) {
            // Retail-MAC-DES uses CBC-MAC-DES internally
            if (keyBits != 64) {
                throw new IllegalArgumentException(
                        "Retail-MAC-DES key must be 64 bits (8 bytes). Got: " + keyBits + " bits");
            }
        } else if (algorithm.equals("Retail-MAC-3DES")) {
            // Retail-MAC-3DES uses CBC-MAC-3DES internally
            if (keyBits != 128 && keyBits != 192) {
                throw new IllegalArgumentException(
                        "Retail-MAC-3DES key must be 128 bits (16 bytes, 2-key) or 192 bits (24 bytes, 3-key). Got: "
                                + keyBits + " bits");
            }
        } else if (algorithm.contains("3DES")) {
            if (keyBits != 128 && keyBits != 192) {
                throw new IllegalArgumentException(
                        "3DES key must be 128 bits (16 bytes, 2-key) or 192 bits (24 bytes, 3-key). Got: " + keyBits
                                + " bits");
            }
        } else if (algorithm.contains("DES") && !algorithm.contains("3DES")) {
            if (keyBits != 64) {
                throw new IllegalArgumentException("DES key must be 64 bits (8 bytes). Got: " + keyBits + " bits");
            }
        }
    }

    private static void validateAesKey(byte[] key, String operation) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException(operation + " AES key must be 16, 24, or 32 bytes");
        }
    }

    /**
     * Get algorithm information
     */
    public static String getAlgorithmInfo(String algorithm) {
        switch (algorithm) {
            // HMAC
            case "HMAC-SHA1":
                return "HMAC-SHA1 - Hash-based MAC with SHA-1 (legacy, 160-bit)";
            case "HMAC-SHA256":
                return "HMAC-SHA256 - Hash-based MAC with SHA-256 (256-bit, recommended)";
            case "HMAC-SHA384":
                return "HMAC-SHA384 - Hash-based MAC with SHA-384 (384-bit)";
            case "HMAC-SHA512":
                return "HMAC-SHA512 - Hash-based MAC with SHA-512 (512-bit)";

            // CMAC
            case "CMAC-AES":
                return "CMAC-AES - Cipher-based MAC with AES (128-bit output)";
            case "CMAC-3DES":
                return "CMAC-3DES - Cipher-based MAC with 3DES (64-bit output)";

            // CBC-MAC
            case "CBC-MAC-DES":
                return "CBC-MAC-DES - CBC mode MAC with DES (64-bit output)";
            case "CBC-MAC-3DES":
                return "CBC-MAC-3DES - CBC mode MAC with 3DES (64-bit output)";
            case "CBC-MAC-AES":
                return "CBC-MAC-AES - CBC mode MAC with AES (128-bit output)";

            // ISO 9797-1 Standards
            case "ISO-9797-1-ALG1":
                return "ISO 9797-1 Algorithm 1 - Standard CBC-MAC with DES (8-byte output, zero padding)";

            // ANSI Standards
            case "ANSI-X9.9":
                return "ANSI X9.9 - Financial MAC standard using DES CBC-MAC (8-byte output, banking)";
            case "ANSI-X9.19":
                return "ANSI X9.19 - Financial MAC standard using 3DES (8-byte output, banking)";
            case "AS2805.4.1":
                return "AS2805.4.1 - Australian Standard for financial transactions using 3DES (8-byte output)";

            // Retail MAC
            case "Retail-MAC-DES":
                return "Retail-MAC-DES - CBC-MAC with DES (BP-Tools compatible, banking standard)";
            case "Retail-MAC-3DES":
                return "Retail-MAC-3DES - CBC-MAC with 3DES (BP-Tools compatible, banking standard). Note: This is CBC-MAC, not ISO 9797-1 Algorithm 3 with decrypt/encrypt steps.";

            default:
                return "Unknown algorithm";
        }
    }

    /**
     * Get expected key size for algorithm
     */
    public static String getExpectedKeySize(String algorithm) {
        if (algorithm.startsWith("HMAC-")) {
            return "≥16 bytes (any size, recommend ≥hash size)";
        } else if (algorithm.contains("AES")) {
            return "16, 24, or 32 bytes (128, 192, or 256 bits)";
        } else if (algorithm.equals("ISO-9797-1-ALG1")) {
            return "8 bytes (64 bits, DES)";
        } else if (algorithm.equals("ANSI-X9.9")) {
            return "8 bytes (64 bits)";
        } else if (algorithm.equals("ANSI-X9.19")) {
            return "16 or 24 bytes (2-key or 3-key 3DES)";
        } else if (algorithm.equals("AS2805.4.1")) {
            return "16 or 24 bytes (2-key or 3-key 3DES)";
        } else if (algorithm.equals("Retail-MAC-DES")) {
            return "8 bytes (64 bits)";
        } else if (algorithm.equals("Retail-MAC-3DES")) {
            return "16 or 24 bytes (2-key or 3-key 3DES)";
        } else if (algorithm.contains("3DES")) {
            return "16 or 24 bytes (2-key or 3-key 3DES)";
        } else if (algorithm.contains("DES") && !algorithm.contains("3DES")) {
            return "8 bytes (64 bits)";
        }
        return "Unknown";
    }
}
