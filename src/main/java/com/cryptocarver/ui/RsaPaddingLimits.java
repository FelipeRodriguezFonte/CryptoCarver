package com.cryptocarver.ui;

/** Largest RSA plaintext a padding scheme admits for a modulus size, as the cipher screen checks it. */
final class RsaPaddingLimits {

    private RsaPaddingLimits() {
    }

    /** PKCS#1 v1.5 needs 11 bytes, OAEP 42 with SHA-1 and 66 with SHA-256. Not used for NoPadding. */
    static int maxPlaintextBytes(int modulusBits, String padding) {
        int modulusBytes = modulusBits / 8;
        String normalized = padding == null ? "" : padding.toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("OAEP")) {
            return normalized.contains("SHA-256") || normalized.contains("SHA_256")
                    ? modulusBytes - 66 : modulusBytes - 42;
        }
        return modulusBytes - 11;
    }
}
