package com.cryptocarver.crypto;

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
            "MD4",
            "WHIRLPOOL",
            "TIGER-192",
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
        if (algorithm != null && algorithm.toUpperCase(java.util.Locale.ROOT).startsWith("CRC-32/")) {
            return calculateCrc32(data, Crc32Variant.fromDisplayName(algorithm));
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
     * Parameterised CRC-32 variants, expressed as width-32 normal polynomials.
     * Values follow the published CRC catalogue convention: poly, init,
     * refin/refout and xorout.  This explicit form prevents a label such as
     * "CRC32" from silently selecting the wrong wire checksum.
     */
    public enum Crc32Variant {
        ISO_HDLC("CRC-32/ISO-HDLC", 0x04C11DB7, 0xFFFFFFFF, true, true, 0xFFFFFFFF),
        BZIP2("CRC-32/BZIP2", 0x04C11DB7, 0xFFFFFFFF, false, false, 0xFFFFFFFF),
        MPEG2("CRC-32/MPEG-2", 0x04C11DB7, 0xFFFFFFFF, false, false, 0x00000000),
        POSIX("CRC-32/POSIX", 0x04C11DB7, 0x00000000, false, false, 0xFFFFFFFF),
        JAMCRC("CRC-32/JAMCRC", 0x04C11DB7, 0xFFFFFFFF, true, true, 0x00000000),
        CASTAGNOLI("CRC-32/ISCSI", 0x1EDC6F41, 0xFFFFFFFF, true, true, 0xFFFFFFFF);

        private final String displayName;
        private final int polynomial, initial, xorOut;
        private final boolean reflectInput, reflectOutput;

        Crc32Variant(String displayName, int polynomial, int initial,
                     boolean reflectInput, boolean reflectOutput, int xorOut) {
            this.displayName = displayName;
            this.polynomial = polynomial;
            this.initial = initial;
            this.reflectInput = reflectInput;
            this.reflectOutput = reflectOutput;
            this.xorOut = xorOut;
        }

        public String displayName() { return displayName; }
        static Crc32Variant fromDisplayName(String value) {
            return Arrays.stream(values()).filter(v -> v.displayName.equalsIgnoreCase(value))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unsupported CRC-32 variant: " + value));
        }
    }

    /** Calculates a named CRC-32 variant and returns its conventional big-endian checksum bytes. */
    public static byte[] calculateCrc32(byte[] data, Crc32Variant variant) {
        if (data == null || variant == null) throw new IllegalArgumentException("CRC data and variant are required");
        int crc = variant.initial;
        for (byte input : data) {
            int value = input & 0xFF;
            if (variant.reflectInput) value = Integer.reverse(value) >>> 24;
            crc ^= value << 24;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ variant.polynomial : crc << 1;
            }
        }
        if (variant.reflectOutput) crc = Integer.reverse(crc);
        crc ^= variant.xorOut;
        return new byte[] { (byte) (crc >>> 24), (byte) (crc >>> 16), (byte) (crc >>> 8), (byte) crc };
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
            case "TIGER192":
                return "TIGER";
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
                || algorithm.equalsIgnoreCase("CRC32")
                || Arrays.stream(Crc32Variant.values()).anyMatch(v -> v.displayName().equalsIgnoreCase(algorithm));
    }
}
