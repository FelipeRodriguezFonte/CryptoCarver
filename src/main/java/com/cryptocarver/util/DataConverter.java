package com.cryptocarver.util;

/**
 * Utility class for data conversion between different formats
 */
public class DataConverter {
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    public static boolean isValidHex(String value) {
        if (value == null) return false;
        String normalized = value.replaceAll("[\\s:-]", "");
        return !normalized.isEmpty() && normalized.length() % 2 == 0 && normalized.matches("[0-9A-Fa-f]+");
    }

    /**
     * Convert hexadecimal string to byte array
     * @param hex Hexadecimal string (e.g., "48656C6C6F")
     * @return Byte array
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }

        // Keep parsing aligned with isValidHex: accept whitespace, ':' and '-'.
        hex = hex.replaceAll("[\\s:-]", "");

        // Check if valid hex
        if (!hex.matches("[0-9A-Fa-f]+")) {
            throw new IllegalArgumentException("Invalid hexadecimal string");
        }

        // Ensure even length
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hexadecimal string must have even length");
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }

        return data;
    }

    public static byte[] decodeBase64Flexible(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Base64 value cannot be empty");
        }
        String normalized = value.replaceAll("-----BEGIN [A-Z0-9 ]+-----", "")
                .replaceAll("-----END [A-Z0-9 ]+-----", "")
                .replaceAll("\\s", "");
        int remainder = normalized.length() % 4;
        if (remainder != 0) normalized += "=".repeat(4 - remainder);
        try {
            return java.util.Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException e) {
            return java.util.Base64.getUrlDecoder().decode(normalized);
        }
    }

    /** Decodes RFC 4648 Base64URL, with or without trailing padding. */
    public static byte[] decodeBase64Url(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Base64URL value cannot be empty");
        }
        String normalized = value.replaceAll("\\s", "");
        int remainder = normalized.length() % 4;
        if (remainder != 0) normalized += "=".repeat(4 - remainder);
        try {
            return java.util.Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64URL value", e);
        }
    }

    /** Encodes bytes using unpadded RFC 4648 Base64URL, suitable for JOSE. */
    public static String bytesToBase64Url(byte[] bytes) {
        if (bytes == null) return "";
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** RFC 4648 Base32 encoding for diagnostic and interoperability workflows. */
    public static String bytesToBase32(byte[] bytes) {
        if (bytes == null) return "";
        return new org.apache.commons.codec.binary.Base32().encodeToString(bytes);
    }

    /** Decodes RFC 4648 Base32, accepting optional whitespace and padding. */
    public static byte[] decodeBase32(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Base32 value cannot be empty");
        org.apache.commons.codec.binary.Base32 codec = new org.apache.commons.codec.binary.Base32();
        String normalized = value.replaceAll("\\s", "");
        if (!codec.isInAlphabet(normalized.replace("=", ""))) {
            throw new IllegalArgumentException("Invalid Base32 value");
        }
        return codec.decode(normalized);
    }

    /** XORs equal-length byte arrays, avoiding silent truncation. */
    public static byte[] xor(byte[] left, byte[] right) {
        if (left == null || right == null || left.length != right.length) {
            throw new IllegalArgumentException("XOR requires two buffers with the same length");
        }
        byte[] result = new byte[left.length];
        for (int i = 0; i < left.length; i++) result[i] = (byte) (left[i] ^ right[i]);
        return result;
    }

    /** Renders control and non-printable bytes explicitly for diagnostics. */
    public static String visualizeBytes(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            switch (unsigned) {
                case 0x00 -> result.append("<NUL>");
                case 0x09 -> result.append("<TAB>");
                case 0x0A -> result.append("<LF>\n");
                case 0x0D -> result.append("<CR>");
                default -> {
                    if (unsigned >= 0x20 && unsigned <= 0x7E) result.append((char) unsigned);
                    else result.append(String.format("<0x%02X>", unsigned));
                }
            }
        }
        return result.toString();
    }

    /** Encodes decimal digits as packed BCD (two digits per byte). */
    public static byte[] decimalToPackedBcd(String digits) {
        if (digits == null || digits.isBlank() || !digits.matches("\\d+")) {
            throw new IllegalArgumentException("BCD input must contain decimal digits only");
        }
        String normalized = digits.length() % 2 == 0 ? digits : "0" + digits;
        byte[] result = new byte[normalized.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) ((normalized.charAt(i * 2) - '0') << 4 | (normalized.charAt(i * 2 + 1) - '0'));
        }
        return result;
    }

    public static String packedBcdToDecimal(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("BCD data cannot be empty");
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int high = (value >>> 4) & 0x0F;
            int low = value & 0x0F;
            if (high > 9 || low > 9) throw new IllegalArgumentException("Invalid packed BCD nibble");
            result.append(high).append(low);
        }
        return result.toString();
    }

    /** Encodes a signed integer using COBOL COMP-3 packed decimal. */
    public static byte[] decimalToComp3(String value) {
        if (value == null || !value.matches("[+-]?\\d+")) throw new IllegalArgumentException("COMP-3 input must be a signed decimal integer");
        boolean negative = value.startsWith("-");
        String digits = value.replaceFirst("^[+-]", "");
        if (digits.length() % 2 == 0) digits = "0" + digits;
        byte[] result = new byte[(digits.length() + 1) / 2];
        for (int i = 0; i < result.length - 1; i++) {
            result[i] = (byte) ((digits.charAt(i * 2) - '0') << 4 | (digits.charAt(i * 2 + 1) - '0'));
        }
        int finalDigit = digits.charAt(digits.length() - 1) - '0';
        result[result.length - 1] = (byte) ((finalDigit << 4) | (negative ? 0x0D : 0x0C));
        return result;
    }

    public static String comp3ToDecimal(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("COMP-3 data cannot be empty");
        StringBuilder digits = new StringBuilder(bytes.length * 2 - 1);
        for (int i = 0; i < bytes.length - 1; i++) {
            int high = (bytes[i] >>> 4) & 0x0F, low = bytes[i] & 0x0F;
            if (high > 9 || low > 9) throw new IllegalArgumentException("Invalid COMP-3 digit");
            digits.append(high).append(low);
        }
        int last = bytes[bytes.length - 1] & 0xFF;
        int digit = last >>> 4, sign = last & 0x0F;
        if (digit > 9 || (sign != 0x0C && sign != 0x0D && sign != 0x0F)) throw new IllegalArgumentException("Invalid COMP-3 sign");
        digits.append(digit);
        String value = digits.toString().replaceFirst("^0+(?!$)", "");
        return sign == 0x0D ? "-" + value : value;
    }

    public static String bytesToQuotedPrintable(byte[] bytes) {
        if (bytes == null) return "";
        return new String(new org.apache.commons.codec.net.QuotedPrintableCodec().encode(bytes), java.nio.charset.StandardCharsets.US_ASCII);
    }

    public static byte[] decodeQuotedPrintable(String value) {
        try {
            return new org.apache.commons.codec.net.QuotedPrintableCodec().decode((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (org.apache.commons.codec.DecoderException e) {
            throw new IllegalArgumentException("Invalid Quoted-Printable value", e);
        }
    }

    public static String bytesToBase58(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        java.math.BigInteger number = new java.math.BigInteger(1, bytes);
        StringBuilder result = new StringBuilder();
        java.math.BigInteger base = java.math.BigInteger.valueOf(58);
        while (number.signum() > 0) {
            java.math.BigInteger[] quotient = number.divideAndRemainder(base);
            result.append(BASE58_ALPHABET.charAt(quotient[1].intValue()));
            number = quotient[0];
        }
        for (byte value : bytes) { if (value == 0) result.append('1'); else break; }
        return result.reverse().toString();
    }

    public static byte[] decodeBase58(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Base58 value cannot be empty");
        java.math.BigInteger number = java.math.BigInteger.ZERO;
        for (char character : value.toCharArray()) {
            int digit = BASE58_ALPHABET.indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("Invalid Base58 character: " + character);
            number = number.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit));
        }
        byte[] raw = number.toByteArray();
        if (raw.length > 0 && raw[0] == 0) raw = java.util.Arrays.copyOfRange(raw, 1, raw.length);
        int leading = 0;
        while (leading < value.length() && value.charAt(leading) == '1') leading++;
        byte[] result = new byte[leading + raw.length];
        System.arraycopy(raw, 0, result, leading, raw.length);
        return result;
    }

    public static String bytesToBase58Check(byte[] payload) {
        if (payload == null) throw new IllegalArgumentException("Payload cannot be null");
        byte[] checksum = sha256d(payload);
        byte[] complete = java.util.Arrays.copyOf(payload, payload.length + 4);
        System.arraycopy(checksum, 0, complete, payload.length, 4);
        return bytesToBase58(complete);
    }

    public static byte[] decodeBase58Check(String value) {
        byte[] complete = decodeBase58(value);
        if (complete.length < 5) throw new IllegalArgumentException("Base58Check value is too short");
        byte[] payload = java.util.Arrays.copyOf(complete, complete.length - 4);
        byte[] checksum = java.util.Arrays.copyOfRange(complete, complete.length - 4, complete.length);
        if (!java.util.Arrays.equals(checksum, java.util.Arrays.copyOf(sha256d(payload), 4))) {
            throw new IllegalArgumentException("Invalid Base58Check checksum");
        }
        return payload;
    }

    private static byte[] sha256d(byte[] value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return digest.digest(digest.digest(value));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String bytesToBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder result = new StringBuilder(bytes.length * 9);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) result.append(' ');
            result.append(String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF)).replace(' ', '0'));
        }
        return result.toString();
    }

    public static byte[] binaryToBytes(String binary) {
        if (binary == null) throw new IllegalArgumentException("Binary value cannot be null");
        String normalized = binary.replaceAll("[\\s:-]", "");
        if (normalized.isEmpty() || normalized.length() % 8 != 0 || !normalized.matches("[01]+")) {
            throw new IllegalArgumentException("Binary value must contain complete bytes (0 and 1)");
        }
        byte[] bytes = new byte[normalized.length() / 8];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(normalized.substring(i * 8, i * 8 + 8), 2);
        }
        return bytes;
    }

    /** Parses comma- or whitespace-separated unsigned byte values (0 to 255). */
    public static byte[] decimalToBytes(String decimal) {
        if (decimal == null || decimal.isBlank()) {
            throw new IllegalArgumentException("Decimal value cannot be empty");
        }
        String[] values = decimal.trim().split("[,\\s]+");
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            try {
                int value = Integer.parseInt(values[i]);
                if (value < 0 || value > 255) {
                    throw new IllegalArgumentException("Decimal byte " + value + " is outside 0..255");
                }
                bytes[i] = (byte) value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid decimal byte: " + values[i], e);
            }
        }
        return bytes;
    }

    /** Formats bytes as unsigned decimal values separated by spaces. */
    public static String bytesToDecimal(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder result = new StringBuilder(bytes.length * 4);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) result.append(' ');
            result.append(bytes[i] & 0xFF);
        }
        return result.toString();
    }

    /** Decodes UTF-8 bytes without silently replacing malformed byte sequences. */
    public static String utf8BytesToString(byte[] bytes) {
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IllegalArgumentException("Input bytes are not valid UTF-8", e);
        }
    }

    public static String bytesToCArray(byte[] bytes, int bytesPerLine) {
        if (bytes == null || bytes.length == 0) return "";
        int perLine = Math.max(1, bytesPerLine);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) result.append(i % perLine == 0 ? ",\n" : ", ");
            result.append(String.format("0x%02X", bytes[i] & 0xFF));
        }
        return result.toString();
    }

    /**
     * Convert byte array to hexadecimal string
     * @param bytes Byte array
     * @return Hexadecimal string (uppercase, no spaces)
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }

        return sb.toString();
    }

    /**
     * Convert byte array to hexadecimal string with spaces
     * @param bytes Byte array
     * @return Hexadecimal string with spaces (e.g., "48 65 6C 6C 6F")
     */
    public static String bytesToHexWithSpaces(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(String.format("%02X", bytes[i]));
        }

        return sb.toString();
    }

    /**
     * Convert hexadecimal string to Base64
     *
     * @param hex Hexadecimal string
     * @return Base64 encoded string
     */
    public static String hexToBase64(String hex) {
        byte[] bytes = hexToBytes(hex);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Convert Base64 to hexadecimal string
     *
     * @param base64 Base64 encoded string
     * @return Uppercase hexadecimal string
     */
    public static String base64ToHex(String base64) {
        byte[] bytes = decodeBase64Flexible(base64);
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
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
        return utf8BytesToString(bytes);
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
     * Format hex string with spaces every N bytes
     *
     * @param hex            Hexadecimal string
     * @param byteSeparation Number of bytes before inserting space
     * @return Formatted hex string
     */
    public static String formatHex(String hex, int byteSeparation) {
        if (byteSeparation <= 0) {
            throw new IllegalArgumentException("byteSeparation must be greater than 0");
        }
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
}
