package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.util.DataConverter;

/** Pure parsing, validation, derivation, wrapping and result reports. */
final class KdfKeyWrapLogic {
    private KdfKeyWrapLogic() { }
    record Request(String algorithm, String inputFormat, String saltFormat, String infoFormat,
            String inputText, String saltText, String infoText, String iterationsText, String outputLengthText) { }
    record Derived(byte[] input, byte[] derivedKey, String algorithm, String inputText, String resultInfo) { }
    record Wrapped(byte[] kek, byte[] data, boolean padded, byte[] result, String operation, String report) { }
    static Derived derive(Request request) throws Exception {
        String algorithm = request.algorithm();
        String inputFormat = request.inputFormat();
        String saltFormat = request.saltFormat();
        String infoFormat = request.infoFormat();

        String inputText = request.inputText().trim();
        String saltText = request.saltText().trim();
        String infoText = request.infoText().trim();
        String iterationsText = request.iterationsText().trim();
        String outputLengthText = request.outputLengthText().trim();

        if (inputText.isEmpty()) {
            throw new KeysInputValidation("Input Error", "Enter input key material in the selected " + inputFormat + " format.", "kdfInputField");
        }

        // Parse input according to format
        byte[] input = parseData(inputText, inputFormat);
        if (input == null) {
            throw new KeysInputValidation("Input Error", "Input key material is not valid " + inputFormat + ".", "kdfInputField");
        }

        // Parse salt according to format (NULL if empty - no forced generation!)
        byte[] salt = null;
        if (!saltText.isEmpty()) {
            salt = parseData(saltText, saltFormat);
            if (salt == null) {
                throw new KeysInputValidation("Input Error", "Salt is not valid " + saltFormat + ". For Hex, use pairs of digits 0-9 and A-F.", "kdfSaltField");
            }
        }

        // Parse info according to format
        byte[] info = null;
        if (!infoText.isEmpty()) {
            info = parseData(infoText, infoFormat);
            if (info == null) {
                throw new KeysInputValidation("Input Error", "Info / application context is not valid " + infoFormat + ".", "kdfInfoField");
            }
        }

        // Parse iterations
        int iterations;
        try {
            iterations = Integer.parseInt(iterationsText);
        } catch (Exception e) {
            throw new KeysInputValidation("Input Error", "Iterations must be a positive whole number.", "kdfIterationsField");
        }

        // Parse output length
        int outputLength;
        try {
            outputLength = Integer.parseInt(outputLengthText);
            if (outputLength < 1 || outputLength > 256) {
                throw new KeysInputValidation("Input Error", "Output length must be between 1 and 256 bytes.", "kdfOutputLengthField");
            }
        } catch (KeysInputValidation e) {
            throw e;
        } catch (Exception e) {
            throw new KeysInputValidation("Input Error", "Output length must be a whole number of bytes.", "kdfOutputLengthField");
        }

        // Extract hash algorithm from name (e.g., "HKDF-SHA256" -> "SHA256")
        String hashAlgo = "SHA256"; // default
        if (algorithm.contains("SHA1")) {
            hashAlgo = "SHA1";
        } else if (algorithm.contains("SHA256")) {
            hashAlgo = "SHA256";
        } else if (algorithm.contains("SHA512")) {
            hashAlgo = "SHA512";
        }

        // Derive key based on algorithm
        byte[] derivedKey;
        String resultInfo;

        if (algorithm.startsWith("HKDF")) {
            // HKDF requires digest
            org.bouncycastle.crypto.Digest digest = com.cryptocarver.crypto.KeyDerivation.getDigest(hashAlgo);
            derivedKey = com.cryptocarver.crypto.KeyDerivation.hkdf(input, salt, info, outputLength, digest);
            resultInfo = buildHKDFResult(input, salt, info, outputLength, derivedKey, hashAlgo);
        } else if (algorithm.startsWith("NIST-800-108")) {
            org.bouncycastle.crypto.Digest digest = com.cryptocarver.crypto.KeyDerivation.getDigest(hashAlgo);
            derivedKey = com.cryptocarver.crypto.KeyDerivation.sp800108Counter(input, salt, info, outputLength, digest);
            resultInfo = buildContextKdfResult("NIST SP 800-108 Counter KDF", "Key", input,
                    "Label", salt, "Context", info, outputLength, derivedKey, hashAlgo);
        } else if (algorithm.startsWith("X9.63")) {
            org.bouncycastle.crypto.Digest digest = com.cryptocarver.crypto.KeyDerivation.getDigest(hashAlgo);
            derivedKey = com.cryptocarver.crypto.KeyDerivation.x963(input, info, outputLength, digest);
            resultInfo = buildContextKdfResult("ANSI X9.63 / Concatenation KDF", "Shared secret", input,
                    null, null, "Shared info", info, outputLength, derivedKey, hashAlgo);
        } else if (algorithm.startsWith("PBKDF2")) {
            // PBKDF2 requires salt
            if (salt == null || salt.length == 0) {
                throw new KeysInputValidation("Input Error", "PBKDF2 requires a non-empty salt. Use Generate for a fresh 16-byte salt.", "kdfSaltField");
            }
            derivedKey = com.cryptocarver.crypto.KeyDerivation.pbkdf2(input, salt, iterations, outputLength,
                    hashAlgo);
            resultInfo = buildPBKDF2Result(input, salt, iterations, outputLength, derivedKey, hashAlgo);
        } else if (algorithm.equals("SCrypt")) {
            // SCrypt requires salt
            if (salt == null || salt.length == 0) {
                throw new KeysInputValidation("Input Error", "SCrypt requires a non-empty salt. Use Generate for a fresh 16-byte salt.", "kdfSaltField");
            }
            // N=iterations, r=8, p=1
            derivedKey = com.cryptocarver.crypto.KeyDerivation.scrypt(input, salt, iterations, 8, 1, outputLength);
            resultInfo = buildSCryptResult(input, salt, iterations, 8, 1, outputLength, derivedKey);
        } else if (algorithm.equals("Argon2id")) {
            // Argon2 requires salt
            if (salt == null || salt.length < 8) {
                throw new KeysInputValidation("Input Error", "Argon2id requires a salt of at least 8 bytes. Use Generate for a fresh 16-byte salt.", "kdfSaltField");
            }
            // iterations=time, memory=64MB, parallelism=4
            derivedKey = com.cryptocarver.crypto.KeyDerivation.argon2(input, salt, iterations, 65536, 4,
                    outputLength);
            resultInfo = buildArgon2Result(input, salt, iterations, 65536, 4, outputLength, derivedKey);
        } else {
            throw new KeysInputValidation("Input Error", "Choose a supported KDF algorithm.", "kdfAlgorithmCombo");
        }

        return new Derived(input, derivedKey, algorithm, inputText, resultInfo);
    }

    static Wrapped wrap(String kekText, String dataText, boolean unwrap, String mode) throws Exception {
        byte[] kek = DataConverter.hexToBytes(kekText.replaceAll("\\s+", ""));
        byte[] data = DataConverter.hexToBytes(dataText.replaceAll("\\s+", ""));
        boolean padded = mode.startsWith("RFC 5649");
        byte[] result;
        if (unwrap) {
            result = padded ? KeyWrapOperations.unwrapRfc5649(kek, data) : KeyWrapOperations.unwrapRfc3394(kek, data);
        } else {
            result = padded ? KeyWrapOperations.wrapRfc5649(kek, data) : KeyWrapOperations.wrapRfc3394(kek, data);
        }
        String operation = unwrap ? "UNWRAP" : "WRAP";
        StringBuilder text = new StringBuilder("========================================\nAES KEY ")
                .append(operation).append("\n========================================\n\n")
                .append("Mode: ").append(mode).append("\n")
                .append("KEK: ").append(kek.length * 8).append(" bits\n")
                .append("Input: ").append(data.length).append(" bytes\n")
                .append("Output: ").append(result.length).append(" bytes\n\n")
                .append(unwrap ? "UNWRAPPED:" : "WRAPPED:").append("\n")
                .append(DataConverter.bytesToHex(result)).append("\n\n")
                .append("✓ Integrity is verified during unwrapping.");
        return new Wrapped(kek, data, padded, result, operation, text.toString());
    }

    static byte[] parseData(String text, String format) {
        try {
            switch (format) {
                case "UTF-8":
                    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                case "Hex":
                    return DataConverter.hexToBytes(text.replaceAll("\\s+", ""));
                case "Base64":
                    return java.util.Base64.getDecoder().decode(text.replaceAll("\\s+", ""));
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    static String buildHKDFResult(byte[] input, byte[] salt, byte[] info, int outputLength, byte[] derivedKey,
            String hashAlgo) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("HKDF-").append(hashAlgo).append(" KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: HKDF (RFC 5869) with ").append(hashAlgo).append("\n\n");
        result.append("Input Key Material (").append(input.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(input)).append("\n\n");
        if (salt != null && salt.length > 0) {
            result.append("Salt (").append(salt.length).append(" bytes):\n");
            result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        } else {
            result.append("Salt: (none provided - HKDF will use zeros)\n\n");
        }
        if (info != null && info.length > 0) {
            result.append("Info (").append(info.length).append(" bytes):\n");
            result.append(new String(info, java.nio.charset.StandardCharsets.UTF_8)).append("\n");
            result.append("(hex: ").append(DataConverter.bytesToHex(info)).append(")\n\n");
        }
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ HKDF is deterministic: same inputs always produce same output\n");
        result.append("✓ Used in: TLS 1.3, Signal Protocol, WireGuard\n");
        return result.toString();
    }

    static String buildContextKdfResult(String name, String inputLabel, byte[] input, String firstLabel,
            byte[] firstValue, String secondLabel, byte[] secondValue, int outputLength, byte[] derivedKey,
            String hashAlgorithm) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append(name.toUpperCase()).append("\n");
        result.append("========================================\n\n");
        result.append("Hash/PRF: HMAC-").append(hashAlgorithm).append("\n");
        result.append(inputLabel).append(" (").append(input.length).append(" bytes):\n")
                .append(DataConverter.bytesToHex(input)).append("\n\n");
        appendKdfField(result, firstLabel, firstValue);
        appendKdfField(result, secondLabel, secondValue);
        result.append("Output Length: ").append(outputLength).append(" bytes\n\nDERIVED KEY:\n")
                .append(DataConverter.bytesToHex(derivedKey)).append("\n\n")
                .append("✓ Deterministic: preserve every input to reproduce this result\n");
        return result.toString();
    }

    static void appendKdfField(StringBuilder result, String label, byte[] value) {
        if (label == null) return;
        result.append(label).append(": ");
        if (value == null || value.length == 0) {
            result.append("(empty)\n\n");
        } else {
            result.append(value.length).append(" bytes\n").append(DataConverter.bytesToHex(value)).append("\n\n");
        }
    }

    static String buildPBKDF2Result(byte[] password, byte[] salt, int iterations, int outputLength, byte[] derivedKey,
            String hashAlgo) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("PBKDF2-").append(hashAlgo).append(" KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: PBKDF2 (PKCS #5) with HMAC-").append(hashAlgo).append("\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Iterations: ").append(String.format("%,d", iterations));
        if (iterations < 100000) {
            result.append(" ⚠️ LOW - Recommend 600,000+ (OWASP 2023)");
        } else if (iterations < 600000) {
            result.append(" ⚠️ MEDIUM - Recommend 600,000+ (OWASP 2023)");
        } else {
            result.append(" ✓ GOOD (OWASP 2023 compliant)");
        }
        result.append("\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Standard password-based key derivation\n");
        result.append("✓ Widely supported and battle-tested\n");
        return result.toString();
    }

    static String buildSCryptResult(byte[] password, byte[] salt, int N, int r, int p, int outputLength,
            byte[] derivedKey) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("SCRYPT KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: SCrypt (memory-hard KDF)\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Parameters:\n");
        result.append("  N (CPU/Memory cost): ").append(String.format("%,d", N));
        if (N < 16384) {
            result.append(" ⚠️ LOW");
        } else {
            result.append(" ✓ GOOD");
        }
        result.append("\n");
        result.append("  r (Block size): ").append(r).append("\n");
        result.append("  p (Parallelism): ").append(p).append("\n");
        result.append("  Memory required: ~").append((128 * N * r / 1024)).append(" KB\n\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Memory-hard: resistant to hardware attacks\n");
        result.append("✓ Used in: Litecoin, many password managers\n");
        return result.toString();
    }

    static String buildArgon2Result(byte[] password, byte[] salt, int iterations, int memory, int parallelism,
            int outputLength, byte[] derivedKey) {
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("ARGON2ID KEY DERIVATION\n");
        result.append("========================================\n\n");
        result.append("Algorithm: Argon2id (Password Hashing Competition winner 2015)\n\n");
        result.append("Password/Input (").append(password.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(password)).append("\n\n");
        result.append("Salt (").append(salt.length).append(" bytes):\n");
        result.append(DataConverter.bytesToHex(salt)).append("\n\n");
        result.append("Parameters:\n");
        result.append("  Time cost (iterations): ").append(iterations);
        if (iterations < 3) {
            result.append(" ⚠️ LOW");
        } else {
            result.append(" ✓ GOOD");
        }
        result.append("\n");
        result.append("  Memory cost: ").append(memory).append(" KB (").append(memory / 1024).append(" MB)\n");
        result.append("  Parallelism: ").append(parallelism).append(" threads\n\n");
        result.append("Output Length: ").append(outputLength).append(" bytes\n\n");
        result.append("DERIVED KEY:\n");
        result.append(DataConverter.bytesToHex(derivedKey)).append("\n\n");
        result.append("✓ Most modern and secure password hashing algorithm\n");
        result.append("✓ Combines data-dependent (Argon2i) and data-independent (Argon2d) approaches\n");
        result.append("✓ Recommended for new applications\n");
        return result.toString();
    }
}
