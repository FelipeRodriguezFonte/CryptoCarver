package com.cryptocarver.util;

public class InputValidator {

    public static void validateInput(String input, String format) {
        if (input == null || input.isEmpty()) return;
        if (format == null) return;

        String normalized = input.replaceAll("\\s+", "");
        switch (format) {
            case "Hexadecimal":
            case "Hex":
                if (!normalized.matches("^[0-9a-fA-F]*$")) {
                    throw new IllegalArgumentException("Input contains invalid characters for Hexadecimal format.");
                }
                if ((normalized.length() & 1) != 0) {
                    throw new IllegalArgumentException("Hexadecimal input must contain a whole number of bytes (an even number of digits).");
                }
                break;
            case "Base64":
                validateBase64(normalized, false);
                break;
            case "Base64URL":
                validateBase64(normalized, true);
                break;
            case "Base64 (PEM)":
                if (!input.matches("^[a-zA-Z0-9+/=\\s\\n\\r-]*$")) {
                    throw new IllegalArgumentException("Input contains invalid characters for PEM/Base64 format.");
                }
                break;
            case "Binary":
                if (!input.matches("^[01\\s]*$")) {
                    throw new IllegalArgumentException("Input contains invalid characters for Binary format (only 0 and 1 allowed).");
                }
                break;
            case "Decimal":
                if (!input.matches("^[0-9\\s]*$")) {
                    throw new IllegalArgumentException("Input contains invalid characters for Decimal format.");
                }
                break;
        }
    }

    private static void validateBase64(String value, boolean urlSafe) {
        String alphabet = urlSafe ? "[A-Za-z0-9_-]" : "[A-Za-z0-9+/]";
        if (urlSafe && value.matches("^" + alphabet + "*$") && value.length() % 4 != 1) {
            return; // Unpadded Base64URL is a valid JOSE representation.
        }
        if (!value.matches("^(?:" + alphabet + "{4})*(?:" + alphabet + "{2}==|" + alphabet + "{3}=)?$")) {
            throw new IllegalArgumentException(urlSafe
                    ? "Input is not valid Base64URL (characters or padding are invalid)."
                    : "Input is not valid Base64 (characters or padding are invalid).");
        }
        if (!urlSafe) {
            try {
                String canonical = java.util.Base64.getEncoder().encodeToString(
                        java.util.Base64.getDecoder().decode(value));
                if (!canonical.equals(value)) {
                    throw new IllegalArgumentException("Input is not valid Base64 (padding bits are not canonical).");
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Input is not valid Base64 (characters or padding are invalid).", invalid);
            }
        }
    }
}
