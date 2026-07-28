package com.cryptocarver.model;

import java.util.*;

/**
 * Registry mapping operation navigation paths to their declarative format profiles.
 */
public class OperationFormatRegistry {

    private static final OperationFormatRegistry INSTANCE = new OperationFormatRegistry();

    private final Map<String, OperationFormatProfile> profiles = new LinkedHashMap<>();

    private final OperationFormatProfile defaultFallbackProfile = new OperationFormatProfile(
            "Fallback",
            List.of(),
            List.of(),
            null,
            null,
            DataType.NONE,
            DataType.NONE,
            "Not applicable"
    );

    public static OperationFormatRegistry getInstance() {
        return INSTANCE;
    }

    private OperationFormatRegistry() {
        // Hashing
        register(new OperationFormatProfile("Hashing",
                List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary"),
                List.of("Hexadecimal", "Base64", "Binary"),
                "Text (UTF-8)", "Hexadecimal",
                DataType.BYTES, DataType.BYTES, "Calculates cryptographic hashes from bytes to bytes."));

        // Manual Conversion
        register(new OperationFormatProfile("Manual Conversion",
                List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary", "Decimal"),
                List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary", "Decimal"),
                "Text (UTF-8)", "Hexadecimal",
                DataType.BYTES, DataType.BYTES, "Manually decodes and encodes bytes using specific text representations."));

        // Symmetric Cipher
        register(new OperationFormatProfile("Symmetric Ciphers",
                List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary"),
                List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary"),
                "Text (UTF-8)", "Hexadecimal",
                DataType.BYTES, DataType.BYTES, "Encrypts or decrypts bytes using a symmetric key algorithm."));

        // File Cipher
        register(new OperationFormatProfile("File Cipher (Streaming)",
                List.of(), List.of(), null, null,
                DataType.FILE, DataType.FILE, "Streaming encryption or decryption of files."));

        // Asymmetric Cipher
        register(new OperationFormatProfile("Asymmetric Ciphers",
                List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary"),
                List.of("Text (UTF-8)", "Hexadecimal", "Base64", "Binary"),
                "Text (UTF-8)", "Hexadecimal",
                DataType.BYTES, DataType.BYTES, "Encrypts or decrypts bytes using an asymmetric key pair."));

        // Digital Signatures
        register(new OperationFormatProfile("Digital Signatures",
                List.of("Text (UTF-8)", "Hexadecimal", "Base64"),
                List.of("Hexadecimal", "Base64"),
                "Text (UTF-8)", "Hexadecimal",
                DataType.BYTES, DataType.BYTES, "Signs or verifies signatures over arbitrary bytes."));

        // MAC
        register(new OperationFormatProfile("Message Authentication Codes",
                List.of("Text (UTF-8)", "Hexadecimal", "Base64"),
                List.of("Hexadecimal", "Base64"),
                "Text (UTF-8)", "Hexadecimal",
                DataType.BYTES, DataType.BYTES, "Computes or verifies a Message Authentication Code over arbitrary bytes."));

        // JOSE - JWE & JWT don't use format combos
        register(new OperationFormatProfile("JWT (Signed)",
                List.of(), List.of(), null, null,
                DataType.STRUCTURE, DataType.STRUCTURE, "Processes JSON Web Tokens."));

        register(new OperationFormatProfile("JWE (Encrypted)",
                List.of(), List.of(), null, null,
                DataType.STRUCTURE, DataType.STRUCTURE, "Processes JSON Web Encryption structures."));

        // ASN.1 - Uses its own local format controls
        register(new OperationFormatProfile("Decode ASN.1",
                List.of(), List.of(), null, null,
                DataType.BYTES, DataType.STRUCTURE, "Parses ASN.1 bytes into a structured human-readable tree."));

        register(new OperationFormatProfile("Encode ASN.1",
                List.of(), List.of(), null, null,
                DataType.TEXT, DataType.BYTES, "Encodes string representations (e.g., OIDs) or primitive types into ASN.1 bytes."));

        // XML Security - Uses no format combos
        register(new OperationFormatProfile("Sign XML (XAdES)",
                List.of(), List.of(), null, null,
                DataType.STRUCTURE, DataType.STRUCTURE, "Applies a digital signature to an XML document."));

        register(new OperationFormatProfile("Verify XML (XAdES)",
                List.of(), List.of(), null, null,
                DataType.STRUCTURE, DataType.STRUCTURE, "Verifies signatures embedded in an XML document."));

        register(new OperationFormatProfile("Sign SOAP (WSS)",
                List.of(), List.of(), null, null,
                DataType.STRUCTURE, DataType.STRUCTURE, "Applies a WS-Security signature to a SOAP message."));

        register(new OperationFormatProfile("Verify SOAP (WSS)",
                List.of(), List.of(), null, null,
                DataType.STRUCTURE, DataType.STRUCTURE, "Verifies WS-Security signatures in a SOAP message."));

        // Random Number Generator
        register(new OperationFormatProfile("Random Number Generator",
                List.of(),
                List.of("Hexadecimal", "Decimal", "Base64", "Binary"),
                null, "Hexadecimal",
                DataType.NONE, DataType.BYTES, "Generates secure random bytes."));
    }

    private void register(OperationFormatProfile profile) {
        profiles.put(profile.operationPath(), profile);
    }

    public OperationFormatProfile getProfile(String operationPath) {
        if (operationPath == null) return defaultFallbackProfile;
        return profiles.getOrDefault(operationPath, defaultFallbackProfile);
    }
}
