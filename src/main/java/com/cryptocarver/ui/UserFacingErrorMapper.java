package com.cryptocarver.ui;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;

/**
 * Translates low-level exceptions and status messages into clean, actionable UserFacingError instances.
 */
public final class UserFacingErrorMapper {

    private UserFacingErrorMapper() {}

    public static UserFacingError map(Throwable cause, String contextTitle, String fieldKey) {
        if (cause == null) {
            return map(contextTitle, "An unknown error occurred.", fieldKey);
        }

        Throwable root = getRootCause(cause);
        String rootMsg = root.getMessage() == null ? "" : root.getMessage().trim();
        String causeMsg = cause.getMessage() == null ? "" : cause.getMessage().trim();
        String combined = (causeMsg + " " + rootMsg).toLowerCase();

        // 1. AEAD Bad Tag Verification Failure
        if (cause instanceof AEADBadTagException
                || root instanceof AEADBadTagException
                || combined.contains("aead")
                || combined.contains("tag mismatch")
                || combined.contains("mac check in gcm failed")
                || combined.contains("bad tag")) {
            return new UserFacingError(
                    "Authentication Tag Verification Failed",
                    "The authentication tag does not match the ciphertext, key, or associated data (AAD).",
                    "Check that the key, IV/nonce, AAD and authentication tag match the encryption operation.",
                    fieldKey != null ? fieldKey : "tag",
                    cause
            );
        }

        // 2. Bad Padding / Decryption Corruption
        if (cause instanceof BadPaddingException
                || root instanceof BadPaddingException
                || combined.contains("pad block corrupted")
                || combined.contains("given final block not properly padded")
                || combined.contains("wrong final block length")) {
            return new UserFacingError(
                    "Decryption / Padding Error",
                    "The ciphertext could not be unpadded cleanly during decryption.",
                    "Verify that the correct key, IV/nonce and padding mode were used for decryption.",
                    fieldKey != null ? fieldKey : "key",
                    cause
            );
        }

        // 3. Invalid Key Format / Size
        if (cause instanceof InvalidKeyException
                || root instanceof InvalidKeyException
                || cause instanceof InvalidKeySpecException
                || root instanceof InvalidKeySpecException
                || combined.contains("invalid aes key")
                || combined.contains("illegal key size")
                || combined.contains("invalid key length")
                || combined.contains("key size")) {
            return new UserFacingError(
                    "Invalid Key Parameter",
                    "The provided key length or key structure is invalid for the selected cipher algorithm.",
                    "Check key format and length (e.g. 128, 192, or 256 bits / 16, 24, or 32 hex bytes for AES).",
                    fieldKey != null ? fieldKey : "key",
                    cause
            );
        }

        // 4. Input Format Validation Errors (Hex / Base64 / Encoding)
        if (cause instanceof IllegalArgumentException || root instanceof IllegalArgumentException) {
            if (combined.contains("hex") || combined.contains("hexadecimal") || combined.contains("odd number of digits")) {
                return new UserFacingError(
                        "Invalid Hexadecimal Format",
                        "The input string contains non-hexadecimal characters or an odd number of digits.",
                        "Ensure the value contains only hexadecimal characters (0-9, A-F) and has an even digit count.",
                        fieldKey != null ? fieldKey : "input",
                        cause
                );
            }
            if (combined.contains("base64")) {
                return new UserFacingError(
                        "Invalid Base64 Format",
                        "The input string contains invalid Base64 characters or improper padding.",
                        "Ensure the input is a valid Base64 string with proper padding.",
                        fieldKey != null ? fieldKey : "input",
                        cause
                );
            }
            return new UserFacingError(
                    "Invalid Input Parameter",
                    cleanMessage(causeMsg.isEmpty() ? rootMsg : causeMsg),
                    "Check input formatting and required field parameters.",
                    fieldKey != null ? fieldKey : "input",
                    cause
            );
        }

        // 5. Certificate / KeyStore / PEM Errors
        if (cause instanceof CertificateException
                || root instanceof CertificateException
                || combined.contains("certificate")
                || combined.contains("x.509")
                || combined.contains("pem")
                || combined.contains("pkcs")
                || combined.contains("keystore")) {
            return new UserFacingError(
                    "Invalid Certificate or Key Format",
                    "The certificate, CSR or private key data could not be parsed.",
                    "Ensure the input is valid X.509 PEM or DER encoded certificate/key data.",
                    fieldKey != null ? fieldKey : "cert",
                    cause
            );
        }

        // 6. TSA Timeout / Connectivity Failure
        if (cause instanceof SocketTimeoutException
                || root instanceof SocketTimeoutException
                || combined.contains("tsa")
                || combined.contains("timestamp authority")
                || combined.contains("timestamp server")) {
            return new UserFacingError(
                    "Timestamp Authority Error",
                    "Connection to the Timestamp Authority (TSA) server timed out or failed.",
                    "Check network connectivity to the TSA server or verify the TSA server URL.",
                    "tsaUrl",
                    cause
            );
        }

        // 7. General Safe Fallback
        String fallbackTitle = (contextTitle != null && !contextTitle.isBlank()) ? contextTitle : "Operation Failed";
        String fallbackDetail = cleanMessage(causeMsg.isEmpty() ? (rootMsg.isEmpty() ? cause.getClass().getSimpleName() : rootMsg) : causeMsg);
        return new UserFacingError(
                fallbackTitle,
                fallbackDetail,
                "Review the parameters and technical details to correct the error.",
                fieldKey,
                cause
        );
    }

    public static UserFacingError map(String title, String message, String fieldKey) {
        String safeTitle = (title != null && !title.isBlank()) ? title : "Operation Failed";
        String safeMsg = message == null ? "" : message.trim();
        String combined = (safeTitle + " " + safeMsg).toLowerCase();

        if (combined.contains("aead") || combined.contains("tag mismatch") || combined.contains("gcm tag")) {
            return new UserFacingError(
                    "Authentication Tag Verification Failed",
                    "The authentication tag does not match the ciphertext or key.",
                    "Check that the key, IV/nonce, AAD and authentication tag match the encryption operation.",
                    fieldKey != null ? fieldKey : "tag"
            );
        }

        if (combined.contains("padding") || combined.contains("pad block")) {
            return new UserFacingError(
                    "Decryption / Padding Error",
                    "The ciphertext could not be unpadded cleanly.",
                    "Verify that the correct key, IV/nonce and padding mode were used for decryption.",
                    fieldKey != null ? fieldKey : "key"
            );
        }

        if (combined.contains("hex") || combined.contains("hexadecimal")) {
            return new UserFacingError(
                    "Invalid Hexadecimal Format",
                    "The input string contains non-hexadecimal characters or an odd number of digits.",
                    "Ensure the value contains only hexadecimal characters (0-9, A-F) and an even digit count.",
                    fieldKey != null ? fieldKey : "input"
            );
        }

        if (combined.contains("base64")) {
            return new UserFacingError(
                    "Invalid Base64 Format",
                    "The input string contains invalid Base64 characters or padding.",
                    "Ensure the input is a valid Base64 string with proper padding.",
                    fieldKey != null ? fieldKey : "input"
            );
        }

        if (combined.contains("certificate") || combined.contains("pem")) {
            return new UserFacingError(
                    "Invalid Certificate or Key Format",
                    "The certificate or key data could not be parsed.",
                    "Ensure the input is valid X.509 PEM or DER encoded data.",
                    fieldKey != null ? fieldKey : "cert"
            );
        }

        return new UserFacingError(
                safeTitle,
                cleanMessage(safeMsg),
                "Review the parameters and technical details to correct the error.",
                fieldKey
        );
    }

    private static Throwable getRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static String cleanMessage(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?i)^(java\\.[a-z0-9_.]+|javax\\.[a-z0-9_.]+|org\\.[a-z0-9_.]+Exception):\\s*", "").trim();
    }
}
