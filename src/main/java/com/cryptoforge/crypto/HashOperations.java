package com.cryptoforge.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Hash calculation operations supporting multiple algorithms
 */
public class HashOperations {

    // Ensure BouncyCastle provider is registered
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Supported hash algorithms
     */
    public static final List<String> SUPPORTED_ALGORITHMS = Arrays.asList(
            "MD5",
            "SHA-1",
            "SHA-224",
            "SHA-256",
            "SHA-384",
            "SHA-512",
            "SHA3-256",
            "SHA3-512"
    );

    /**
     * Calculate hash of data using specified algorithm
     *
     * @param data Data to hash
     * @param algorithm Hash algorithm name
     * @return Hash value as byte array
     * @throws NoSuchAlgorithmException if algorithm is not supported
     */
    public static byte[] calculateHash(byte[] data, String algorithm) throws NoSuchAlgorithmException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        // Special handling for CRC32
        if (algorithm.equalsIgnoreCase("CRC32")) {
            return calculateCRC32(data);
        }

        // Normalize algorithm name
        String normalizedAlgorithm = normalizeAlgorithmName(algorithm);

        MessageDigest digest = MessageDigest.getInstance(normalizedAlgorithm,
                                                         new BouncyCastleProvider());
        return digest.digest(data);
    }

    /**
     * Calculate CRC32 checksum
     *
     * @param data Data to checksum
     * @return CRC32 value as byte array (4 bytes)
     */
    private static byte[] calculateCRC32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        long value = crc.getValue();

        // Convert to 4-byte array
        return new byte[]{
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    /**
     * Normalize algorithm name for Java/BouncyCastle
     *
     * @param algorithm User-provided algorithm name
     * @return Normalized algorithm name
     */
    private static String normalizeAlgorithmName(String algorithm) {
        // Remove hyphens and spaces
        String normalized = algorithm.replaceAll("[-\\s]", "");

        // Handle common variations
        switch (normalized.toUpperCase()) {
            case "SHA1":
                return "SHA-1";
            case "SHA224":
                return "SHA-224";
            case "SHA256":
                return "SHA-256";
            case "SHA384":
                return "SHA-384";
            case "SHA512":
                return "SHA-512";
            case "SHA3256":
                return "SHA3-256";
            case "SHA3512":
                return "SHA3-512";
            default:
                return algorithm;
        }
    }

    /**
     * Get display name for algorithm (with proper formatting)
     *
     * @param algorithm Algorithm name
     * @return Formatted display name
     */
    public static String getDisplayName(String algorithm) {
        return algorithm;
    }

    /**
     * Validate if algorithm is supported
     *
     * @param algorithm Algorithm name to check
     * @return true if supported
     */
    public static boolean isSupported(String algorithm) {
        if (algorithm == null) {
            return false;
        }

        // Check against supported list
        return SUPPORTED_ALGORITHMS.stream()
                .anyMatch(algo -> algo.equalsIgnoreCase(algorithm))
                || algorithm.equalsIgnoreCase("CRC32");
    }
}
