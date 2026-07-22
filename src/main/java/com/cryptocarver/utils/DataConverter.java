package com.cryptocarver.utils;

/**
 * Utility class for data format conversions and encoding operations.
 *
 * @deprecated Use {@link com.cryptocarver.util.DataConverter} instead.
 */
@Deprecated
public class DataConverter {

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("Hex string cannot be null or empty");
        }
        return com.cryptocarver.util.DataConverter.hexToBytes(hex);
    }

    public static String bytesToHex(byte[] bytes) {
        return com.cryptocarver.util.DataConverter.bytesToHex(bytes);
    }

    public static String hexToBase64(String hex) {
        return com.cryptocarver.util.DataConverter.hexToBase64(hex);
    }

    public static String base64ToHex(String base64) {
        return com.cryptocarver.util.DataConverter.base64ToHex(base64);
    }

    public static String textToHex(String text) {
        return com.cryptocarver.util.DataConverter.textToHex(text);
    }

    public static String hexToText(String hex) {
        return com.cryptocarver.util.DataConverter.hexToText(hex);
    }

    public static String bytesToBinary(byte[] bytes) {
        return com.cryptocarver.util.DataConverter.bytesToBinary(bytes);
    }

    public static byte[] binaryToBytes(String binary) {
        return com.cryptocarver.util.DataConverter.binaryToBytes(binary);
    }

    public static String bytesToCArray(byte[] bytes, int bytesPerLine) {
        return com.cryptocarver.util.DataConverter.bytesToCArray(bytes, bytesPerLine);
    }

    public static String bytesToJavaArray(byte[] bytes) {
        return com.cryptocarver.util.DataConverter.bytesToJavaArray(bytes);
    }

    public static boolean isValidHex(String hex) {
        return com.cryptocarver.util.DataConverter.isValidHex(hex);
    }

    public static String formatHex(String hex, int byteSeparation) {
        return com.cryptocarver.util.DataConverter.formatHex(hex, byteSeparation);
    }

    public static byte[] xor(byte[] a, byte[] b) {
        return com.cryptocarver.util.DataConverter.xor(a, b);
    }

    public static byte[] decodeBase64Flexible(String input) {
        return com.cryptocarver.util.DataConverter.decodeBase64Flexible(input);
    }
}
