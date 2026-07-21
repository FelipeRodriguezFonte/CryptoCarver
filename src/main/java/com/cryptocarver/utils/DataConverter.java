package com.cryptocarver.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for data format conversions and encoding operations
 */
public class DataConverter {

    /**
     * Convert hexadecimal string to byte array
     *
     * @param hex Hexadecimal string (e.g., "48656C6C6F")
     * @return Byte array
     * @throws IllegalArgumentException if hex string is invalid
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("Hex string cannot be null or empty");
        }

        // Remove spaces and common separators
        hex = hex.replaceAll("[\\s:-]", "");

        try {
            return Hex.decodeHex(hex.toCharArray());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid hexadecimal string: " + hex, e);
        }
    }

    /**
     * Convert byte array to hexadecimal string
     *
     * @param bytes Byte array
     * @return Uppercase hexadecimal string
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        return Hex.encodeHexString(bytes).toUpperCase();
    }

    /**
     * Convert hexadecimal string to Base64
     *
     * @param hex Hexadecimal string
     * @return Base64 encoded string
     */
    public static String hexToBase64(String hex) {
        byte[] bytes = hexToBytes(hex);
        return Base64.encodeBase64String(bytes);
    }

    /**
     * Convert Base64 to hexadecimal string
     *
     * @param base64 Base64 encoded string
     * @return Uppercase hexadecimal string
     */
    public static String base64ToHex(String base64) {
        byte[] bytes = Base64.decodeBase64(base64);
        return bytesToHex(bytes);
    }

    /**
     * Convert text to hexadecimal string
     *
     * @param text Text string
     * @return Uppercase hexadecimal string
     */
    public static String textToHex(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return bytesToHex(bytes);
    }

    /**
     * Convert hexadecimal string to text
     *
     * @param hex Hexadecimal string
     * @return UTF-8 decoded text
     */
    public static String hexToText(String hex) {
        byte[] bytes = hexToBytes(hex);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Convert byte array to binary string representation
     *
     * @param bytes Byte array
     * @return Binary string (e.g., "01001000 01100101")
     */
    public static String bytesToBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder binary = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                binary.append(" ");
            }
            binary.append(String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF))
                    .replace(' ', '0'));
        }
        return binary.toString();
    }

    /**
     * Convert binary string to byte array
     *
     * @param binary Binary string (e.g., "01001000 01100101" or "0100100001100101")
     * @return Byte array
     * @throws IllegalArgumentException if binary string is invalid
     */
    public static byte[] binaryToBytes(String binary) {
        if (binary == null || binary.isEmpty()) {
            throw new IllegalArgumentException("Binary string cannot be null or empty");
        }

        // Remove spaces and common separators
        binary = binary.replaceAll("[\\s:-]", "");

        // Check if length is multiple of 8
        if (binary.length() % 8 != 0) {
            throw new IllegalArgumentException("Binary string length must be multiple of 8");
        }

        // Validate binary characters
        if (!binary.matches("[01]+")) {
            throw new IllegalArgumentException("Binary string must contain only 0 and 1");
        }

        byte[] bytes = new byte[binary.length() / 8];
        for (int i = 0; i < bytes.length; i++) {
            String byteStr = binary.substring(i * 8, (i + 1) * 8);
            bytes[i] = (byte) Integer.parseInt(byteStr, 2);
        }

        return bytes;
    }

    /**
     * Convert byte array to C array format
     *
     * @param bytes        Byte array
     * @param bytesPerLine Number of bytes per line
     * @return C array string (e.g., "0x48, 0x65, 0x6C, 0x6C, 0x6F")
     */
    public static String bytesToCArray(byte[] bytes, int bytesPerLine) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                result.append(", ");
                if (i % bytesPerLine == 0) {
                    result.append("\n");
                }
            }
            result.append(String.format("0x%02X", bytes[i]));
        }
        return result.toString();
    }

    /**
     * Convert byte array to Java byte array format
     *
     * @param bytes Byte array
     * @return Java byte array string
     */
    public static String bytesToJavaArray(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "new byte[] {}";
        }

        StringBuilder result = new StringBuilder("new byte[] {\n    ");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                result.append(", ");
                if (i % 12 == 0) {
                    result.append("\n    ");
                }
            }
            result.append(String.format("(byte)0x%02X", bytes[i]));
        }
        result.append("\n}");
        return result.toString();
    }

    /**
     * Validate if a string is valid hexadecimal
     *
     * @param hex String to validate
     * @return true if valid hexadecimal, false otherwise
     */
    public static boolean isValidHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return false;
        }

        // Remove common separators
        hex = hex.replaceAll("[\\s:-]", "");

        // Check if even length and contains only hex characters
        return hex.length() % 2 == 0 && hex.matches("[0-9A-Fa-f]+");
    }

    /**
     * Format hex string with spaces every N bytes
     *
     * @param hex            Hexadecimal string
     * @param byteSeparation Number of bytes before inserting space
     * @return Formatted hex string
     */
    public static String formatHex(String hex, int byteSeparation) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }

        hex = hex.replaceAll("[\\s:-]", "");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < hex.length(); i += byteSeparation * 2) {
            if (i > 0) {
                formatted.append(" ");
            }
            int end = Math.min(i + byteSeparation * 2, hex.length());
            formatted.append(hex.substring(i, end));
        }

        return formatted.toString();
    }

    /**
     * XOR two byte arrays
     *
     * @param a First byte array
     * @param b Second byte array
     * @return XOR result (length = min(a.length, b.length))
     */
    public static byte[] xor(byte[] a, byte[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Arrays cannot be null");
        }

        int length = Math.min(a.length, b.length);
        byte[] result = new byte[length];

        for (int i = 0; i < length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }

        return result;
    }

    /**
     * Decode Base64 string with URL-safe replacement and padding fix.
     * Also strips whitespace.
     */
    public static byte[] decodeBase64Flexible(String input) {
        if (input == null || input.isEmpty())
            return new byte[0];
        // Strip whitespace
        String clean = input.replaceAll("\\s+", "");
        // Normalize URL-safe chars
        clean = clean.replace('-', '+').replace('_', '/');
        // Fix padding
        int pad = clean.length() % 4;
        if (pad > 0) {
            for (int i = 0; i < 4 - pad; i++) {
                clean += "=";
            }
        }
        return Base64.decodeBase64(clean);
    }
}
