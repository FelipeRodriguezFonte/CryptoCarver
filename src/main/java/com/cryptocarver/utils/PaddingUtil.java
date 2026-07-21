package com.cryptocarver.utils;

import java.util.Arrays;

/**
 * Padding utility class supporting multiple padding standards
 */
public class PaddingUtil {

    public enum PaddingType {
        PKCS5,           // PKCS#5 padding (8-byte blocks)
        PKCS7,           // PKCS#7 padding (variable block size)
        ISO_9797_M1,     // ISO 9797-1 Method 1 (Zero padding)
        ISO_9797_M2,     // ISO 9797-1 Method 2 (0x80 + zeros)
        ISO_7816_4,      // ISO 7816-4 (0x80 + zeros)
        ZERO,            // Zero byte padding
        NONE             // No padding
    }

    /**
     * Add padding to data
     *
     * @param data Data to pad
     * @param blockSize Block size in bytes
     * @param paddingType Type of padding to apply
     * @return Padded data
     */
    public static byte[] addPadding(byte[] data, int blockSize, PaddingType paddingType) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        if (blockSize <= 0 || blockSize > 256) {
            throw new IllegalArgumentException("Invalid block size: " + blockSize);
        }

        switch (paddingType) {
            case PKCS5:
                return addPKCS5Padding(data);
            case PKCS7:
                return addPKCS7Padding(data, blockSize);
            case ISO_9797_M1:
                return addISO9797Method1(data, blockSize);
            case ISO_9797_M2:
                return addISO9797Method2(data, blockSize);
            case ISO_7816_4:
                return addISO7816_4(data, blockSize);
            case ZERO:
                return addZeroPadding(data, blockSize);
            case NONE:
                return data;
            default:
                throw new IllegalArgumentException("Unsupported padding type: " + paddingType);
        }
    }

    /**
     * Remove padding from data
     *
     * @param paddedData Padded data
     * @param paddingType Type of padding to remove
     * @return Data with padding removed
     */
    public static byte[] removePadding(byte[] paddedData, PaddingType paddingType) {
        if (paddedData == null || paddedData.length == 0) {
            throw new IllegalArgumentException("Padded data cannot be null or empty");
        }

        switch (paddingType) {
            case PKCS5:
            case PKCS7:
                return removePKCS7Padding(paddedData);
            case ISO_9797_M1:
            case ZERO:
                return removeZeroPadding(paddedData);
            case ISO_9797_M2:
            case ISO_7816_4:
                return removeISO7816_4Padding(paddedData);
            case NONE:
                return paddedData;
            default:
                throw new IllegalArgumentException("Unsupported padding type: " + paddingType);
        }
    }

    /**
     * PKCS#5 Padding (always 8-byte blocks)
     */
    private static byte[] addPKCS5Padding(byte[] data) {
        return addPKCS7Padding(data, 8);
    }

    /**
     * PKCS#7 Padding
     * Each padding byte contains the number of padding bytes added
     */
    private static byte[] addPKCS7Padding(byte[] data, int blockSize) {
        int paddingLength = blockSize - (data.length % blockSize);
        byte[] padded = Arrays.copyOf(data, data.length + paddingLength);

        // Fill with padding byte value
        Arrays.fill(padded, data.length, padded.length, (byte) paddingLength);

        return padded;
    }

    private static byte[] removePKCS7Padding(byte[] paddedData) {
        if (paddedData.length == 0) {
            throw new IllegalArgumentException("Cannot remove padding from empty data");
        }

        int paddingLength = paddedData[paddedData.length - 1] & 0xFF;

        // Validate padding
        if (paddingLength < 1 || paddingLength > paddedData.length) {
            throw new IllegalArgumentException("Invalid PKCS#7 padding");
        }

        // Verify all padding bytes have the same value
        for (int i = paddedData.length - paddingLength; i < paddedData.length; i++) {
            if ((paddedData[i] & 0xFF) != paddingLength) {
                throw new IllegalArgumentException("Invalid PKCS#7 padding");
            }
        }

        return Arrays.copyOf(paddedData, paddedData.length - paddingLength);
    }

    /**
     * ISO 9797-1 Method 1 - Zero padding
     */
    private static byte[] addISO9797Method1(byte[] data, int blockSize) {
        return addZeroPadding(data, blockSize);
    }

    /**
     * ISO 9797-1 Method 2 - Bit padding (0x80 followed by zeros)
     */
    private static byte[] addISO9797Method2(byte[] data, int blockSize) {
        int paddingLength = blockSize - (data.length % blockSize);
        byte[] padded = Arrays.copyOf(data, data.length + paddingLength);

        // Add 0x80 byte
        padded[data.length] = (byte) 0x80;

        // Rest is already zero (copyOf fills with zeros)
        return padded;
    }

    /**
     * ISO 7816-4 Padding (same as ISO 9797-1 Method 2)
     */
    private static byte[] addISO7816_4(byte[] data, int blockSize) {
        return addISO9797Method2(data, blockSize);
    }

    private static byte[] removeISO7816_4Padding(byte[] paddedData) {
        // Find the 0x80 byte from the end
        int i = paddedData.length - 1;

        // Skip trailing zeros
        while (i >= 0 && paddedData[i] == 0) {
            i--;
        }

        // Check for 0x80 byte
        if (i < 0 || paddedData[i] != (byte) 0x80) {
            throw new IllegalArgumentException("Invalid ISO 7816-4 padding");
        }

        return Arrays.copyOf(paddedData, i);
    }

    /**
     * Zero Padding
     */
    private static byte[] addZeroPadding(byte[] data, int blockSize) {
        int remainder = data.length % blockSize;
        if (remainder == 0) {
            return data; // No padding needed
        }

        int paddingLength = blockSize - remainder;
        byte[] padded = Arrays.copyOf(data, data.length + paddingLength);
        // copyOf automatically fills with zeros

        return padded;
    }

    private static byte[] removeZeroPadding(byte[] paddedData) {
        // Find the last non-zero byte
        int i = paddedData.length - 1;
        while (i >= 0 && paddedData[i] == 0) {
            i--;
        }

        return Arrays.copyOf(paddedData, i + 1);
    }

    /**
     * Check if data needs padding for given block size
     *
     * @param dataLength Length of data
     * @param blockSize Block size
     * @return true if padding is needed
     */
    public static boolean needsPadding(int dataLength, int blockSize) {
        return dataLength % blockSize != 0;
    }

    /**
     * Calculate how many padding bytes are needed
     *
     * @param dataLength Length of data
     * @param blockSize Block size
     * @return Number of padding bytes needed
     */
    public static int getPaddingLength(int dataLength, int blockSize) {
        int remainder = dataLength % blockSize;
        return remainder == 0 ? 0 : blockSize - remainder;
    }

    /**
     * Validate if data has correct padding for given type
     *
     * @param data Data to validate
     * @param blockSize Block size
     * @param paddingType Padding type
     * @return true if padding is valid
     */
    public static boolean isValidPadding(byte[] data, int blockSize, PaddingType paddingType) {
        if (data == null || data.length == 0 || data.length % blockSize != 0) {
            return false;
        }

        try {
            removePadding(data, paddingType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
