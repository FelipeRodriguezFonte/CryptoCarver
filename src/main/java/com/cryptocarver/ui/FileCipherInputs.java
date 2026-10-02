package com.cryptocarver.ui;

import com.cryptocarver.util.DataConverter;

import java.nio.file.Path;

/** Parsing of the file-cipher panel's text fields, without JavaFX. */
final class FileCipherInputs {

    /** Algorithm family and mode behind a panel choice such as {@code AES-256-GCM}, with the decoded material. */
    record Parameters(String algorithm, String mode, byte[] key, byte[] nonce, byte[] aad, boolean aead) {
    }

    private FileCipherInputs() {
    }

    static Parameters parameters(String selected, String keyText, boolean lineMode, String nonceText, String aadText) {
        String algorithm = selected.startsWith("ChaCha") ? "ChaCha20-Poly1305" : "AES-256";
        String mode = selected.contains("GCM") ? "GCM" : selected.contains("CTR") ? "CTR" : selected.contains("CBC") ? "CBC" : "";
        byte[] key = requiredHex(keyText, "Key");
        byte[] nonce = lineMode && !"AES-256-CBC".equals(selected) ? null : requiredHex(nonceText, "IV / nonce");
        byte[] aad = optionalHex(aadText, "AAD");
        return new Parameters(algorithm, mode, key, nonce, aad, "GCM".equals(mode) || "ChaCha20-Poly1305".equals(algorithm));
    }

    static byte[] requiredHex(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return DataConverter.hexToBytes(value.replaceAll("\\s+", ""));
    }

    static byte[] optionalHex(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return DataConverter.hexToBytes(value.replaceAll("\\s+", ""));
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " must be hexadecimal");
        }
    }

    static Path requiredPath(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return Path.of(value.trim());
    }
}
