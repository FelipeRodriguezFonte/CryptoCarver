package com.cryptocarver.model;

import com.cryptocarver.util.DataConverter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure inspection engine for execution preflight readiness checks.
 *
 * <p>This engine never mutates input fields, auto-generates keys/IVs, or executes
 * cryptographic operations. It strictly inspects current UI parameter state.</p>
 */
public final class OperationPreflightEngine {

    private OperationPreflightEngine() {}

    /**
     * Inspects Symmetric Cipher parameters (Encrypt or Decrypt).
     */
    public static PreflightReport checkSymmetricCipher(
            String inputData,
            String inputFormat,
            String algorithm,
            String mode,
            String padding,
            String keySource,
            String manualKeyHex,
            String hsmKeyId,
            boolean isHsmKeyMetadataOnly,
            String ivHex,
            String gcmTagHex,
            String aadHex,
            boolean isEncrypt
    ) {
        List<PreflightCheck> checks = new ArrayList<>();

        // 1. Input Data Check
        if (inputData == null || inputData.trim().isEmpty()) {
            checks.add(new PreflightCheck("Input Data", PreflightStatus.INCOMPLETE, "Input payload is empty. Enter or paste data to process.", "cipherInputArea"));
        } else {
            if ("Hexadecimal".equalsIgnoreCase(inputFormat) || "Hex".equalsIgnoreCase(inputFormat)) {
                String cleanHex = inputData.replaceAll("\\s+", "");
                if (!isValidHex(cleanHex)) {
                    checks.add(new PreflightCheck("Input Format", PreflightStatus.BLOCKED, "Input is set to Hexadecimal but contains invalid characters.", "cipherInputArea"));
                } else if (cleanHex.length() % 2 != 0) {
                    checks.add(new PreflightCheck("Input Format", PreflightStatus.BLOCKED, "Hexadecimal input must have an even number of hex characters.", "cipherInputArea"));
                } else {
                    checks.add(new PreflightCheck("Input Format", PreflightStatus.READY, "Hexadecimal input format valid.", "cipherInputArea"));
                }
            } else if ("Base64".equalsIgnoreCase(inputFormat)) {
                if (!isValidBase64(inputData.trim())) {
                    checks.add(new PreflightCheck("Input Format", PreflightStatus.BLOCKED, "Input is set to Base64 but contains invalid characters or padding.", "cipherInputArea"));
                } else {
                    checks.add(new PreflightCheck("Input Format", PreflightStatus.READY, "Base64 input format valid.", "cipherInputArea"));
                }
            } else {
                checks.add(new PreflightCheck("Input Data", PreflightStatus.READY, "Input data payload present.", "cipherInputArea"));
            }
        }

        // 2. Algorithm & Security Policy Checks
        if (algorithm == null || algorithm.trim().isEmpty()) {
            checks.add(new PreflightCheck("Algorithm", PreflightStatus.INCOMPLETE, "Cipher algorithm is not selected.", "symmetricAlgorithmCombo"));
        } else {
            String algoUpper = algorithm.toUpperCase();
            if (algoUpper.contains("DES") && !algoUpper.contains("3DES") && !algoUpper.contains("TRIPLEDES")) {
                checks.add(new PreflightCheck("Security Policy", PreflightStatus.WARNING, "DES (56-bit) is legacy and cryptographically broken. Use AES-256.", "symmetricAlgorithmCombo"));
            } else if (algoUpper.contains("3DES") || algoUpper.contains("TRIPLEDES")) {
                checks.add(new PreflightCheck("Security Policy", PreflightStatus.WARNING, "3DES is deprecated by NIST. Upgrade to AES-256.", "symmetricAlgorithmCombo"));
            } else {
                checks.add(new PreflightCheck("Algorithm", PreflightStatus.READY, "Algorithm selected: " + algorithm, "symmetricAlgorithmCombo"));
            }
        }

        // 3. Mode & Padding Checks
        if (mode == null || mode.trim().isEmpty()) {
            checks.add(new PreflightCheck("Cipher Mode", PreflightStatus.INCOMPLETE, "Cipher mode is not selected.", "cipherModeCombo"));
        } else {
            String modeUpper = mode.toUpperCase();
            if ("ECB".equals(modeUpper)) {
                checks.add(new PreflightCheck("Security Policy", PreflightStatus.WARNING, "ECB mode does not use an IV and exposes patterns in ciphertext. Use CBC or GCM.", "cipherModeCombo"));
            } else if ("CBC".equals(modeUpper)) {
                checks.add(new PreflightCheck("Security Policy", PreflightStatus.WARNING, "Unauthenticated CBC mode is vulnerable to padding oracle attacks. Consider GCM.", "cipherModeCombo"));
            } else {
                checks.add(new PreflightCheck("Cipher Mode", PreflightStatus.READY, "Mode selected: " + mode, "cipherModeCombo"));
            }
        }

        // 4. Key Check
        if ("Simulated HSM".equalsIgnoreCase(keySource) || "Lab Cache".equalsIgnoreCase(keySource)) {
            if (hsmKeyId == null || hsmKeyId.trim().isEmpty()) {
                checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.INCOMPLETE, "No key selected from Key Lab cache.", "symHsmKeyCombo"));
            } else if (isHsmKeyMetadataOnly) {
                checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.BLOCKED, "Selected key is a metadata-only reference without secret bytes. Re-import key in Key Lab.", "symHsmKeyCombo"));
            } else {
                checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.READY, "Selected Key Lab key: " + hsmKeyId, "symHsmKeyCombo"));
            }
        } else {
            if (manualKeyHex == null || manualKeyHex.trim().isEmpty()) {
                checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.INCOMPLETE, "Manual symmetric key in hex is required.", "symmetricKeyField"));
            } else {
                String cleanKey = manualKeyHex.replaceAll("\\s+", "");
                if (!isValidHex(cleanKey)) {
                    checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.BLOCKED, "Symmetric key contains non-hexadecimal characters.", "symmetricKeyField"));
                } else {
                    int byteLen = cleanKey.length() / 2;
                    String algoUpper = algorithm != null ? algorithm.toUpperCase() : "";
                    if (algoUpper.contains("AES-256") && byteLen != 32) {
                        checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.BLOCKED, "AES-256 requires a 32-byte (64 hex char) key. Current length: " + byteLen + " bytes.", "symmetricKeyField"));
                    } else if (algoUpper.contains("AES-192") && byteLen != 24) {
                        checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.BLOCKED, "AES-192 requires a 24-byte (48 hex char) key. Current length: " + byteLen + " bytes.", "symmetricKeyField"));
                    } else if (algoUpper.contains("AES-128") && byteLen != 16) {
                        checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.BLOCKED, "AES-128 requires a 16-byte (32 hex char) key. Current length: " + byteLen + " bytes.", "symmetricKeyField"));
                    } else if (algoUpper.contains("DES") && !algoUpper.contains("3DES") && !algoUpper.contains("TRIPLEDES") && byteLen != 8) {
                        checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.BLOCKED, "DES requires an 8-byte (16 hex char) key. Current length: " + byteLen + " bytes.", "symmetricKeyField"));
                    } else if ((algoUpper.contains("3DES") || algoUpper.contains("TRIPLEDES")) && byteLen != 16 && byteLen != 24) {
                        checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.BLOCKED, "3DES requires a 16- or 24-byte (32 or 48 hex char) key. Current length: " + byteLen + " bytes.", "symmetricKeyField"));
                    } else {
                        checks.add(new PreflightCheck("Symmetric Key", PreflightStatus.READY, "Symmetric key valid (" + byteLen + " bytes).", "symmetricKeyField"));
                    }
                }
            }
        }

        // 5. IV / Nonce Check
        boolean requiresIv = mode != null && !"ECB".equalsIgnoreCase(mode);
        if (requiresIv) {
            if (ivHex == null || ivHex.trim().isEmpty()) {
                checks.add(new PreflightCheck("IV / Nonce", PreflightStatus.INCOMPLETE, mode + " mode requires an Initialization Vector (IV/nonce).", "ivField"));
            } else {
                String cleanIv = ivHex.replaceAll("\\s+", "");
                if (!isValidHex(cleanIv)) {
                    checks.add(new PreflightCheck("IV / Nonce", PreflightStatus.BLOCKED, "IV/nonce contains non-hexadecimal characters.", "ivField"));
                } else {
                    int ivBytes = cleanIv.length() / 2;
                    if ("GCM".equalsIgnoreCase(mode)) {
                        if (ivBytes != 12) {
                            checks.add(new PreflightCheck("IV / Nonce", PreflightStatus.WARNING, "NIST SP 800-38D recommends a 12-byte (24 hex char) IV/nonce for GCM. Current: " + ivBytes + " bytes.", "ivField"));
                        } else {
                            checks.add(new PreflightCheck("IV / Nonce", PreflightStatus.READY, "GCM IV/nonce valid (12 bytes).", "ivField"));
                        }
                    } else {
                        checks.add(new PreflightCheck("IV / Nonce", PreflightStatus.READY, "IV valid (" + ivBytes + " bytes).", "ivField"));
                    }
                }
            }
        }

        // 6. AEAD GCM Tag & AAD Checks
        if ("GCM".equalsIgnoreCase(mode) && !isEncrypt) {
            if (gcmTagHex == null || gcmTagHex.trim().isEmpty()) {
                checks.add(new PreflightCheck("GCM Auth Tag", PreflightStatus.INCOMPLETE, "GCM decryption requires an Authentication Tag.", "gcmTagField"));
            } else {
                String cleanTag = gcmTagHex.replaceAll("\\s+", "");
                if (!isValidHex(cleanTag)) {
                    checks.add(new PreflightCheck("GCM Auth Tag", PreflightStatus.BLOCKED, "GCM Auth Tag contains non-hexadecimal characters.", "gcmTagField"));
                } else {
                    checks.add(new PreflightCheck("GCM Auth Tag", PreflightStatus.READY, "GCM Auth Tag valid.", "gcmTagField"));
                }
            }
        }

        if (aadHex != null && !aadHex.trim().isEmpty()) {
            String cleanAad = aadHex.replaceAll("\\s+", "");
            if (!isValidHex(cleanAad)) {
                checks.add(new PreflightCheck("AAD Data", PreflightStatus.WARNING, "AAD is not hexadecimal; ASCII bytes will be used.", "aadField"));
            } else {
                checks.add(new PreflightCheck("AAD Data", PreflightStatus.READY, "AAD hex valid.", "aadField"));
            }
        }

        return buildReport(checks);
    }

    /**
     * Inspects Hashing parameters.
     */
    public static PreflightReport checkHashing(String inputData, String inputFormat, String algorithm) {
        List<PreflightCheck> checks = new ArrayList<>();

        if (inputData == null || inputData.trim().isEmpty()) {
            checks.add(new PreflightCheck("Input Payload", PreflightStatus.INCOMPLETE, "Input payload is empty. Enter text to hash.", "hashInputArea"));
        } else {
            checks.add(new PreflightCheck("Input Payload", PreflightStatus.READY, "Input payload present.", "hashInputArea"));
        }

        if (algorithm == null || algorithm.trim().isEmpty()) {
            checks.add(new PreflightCheck("Digest Algorithm", PreflightStatus.INCOMPLETE, "Digest algorithm is not selected.", "hashAlgorithmCombo"));
        } else {
            String algoUpper = algorithm.toUpperCase();
            if ("MD5".equals(algoUpper) || "SHA-1".equals(algoUpper) || "SHA1".equals(algoUpper)) {
                checks.add(new PreflightCheck("Digest Security", PreflightStatus.WARNING, algorithm + " is cryptographically weak. Use SHA-256 or SHA-512.", "hashAlgorithmCombo"));
            } else {
                checks.add(new PreflightCheck("Digest Algorithm", PreflightStatus.READY, "Algorithm selected: " + algorithm, "hashAlgorithmCombo"));
            }
        }

        return buildReport(checks);
    }

    /**
     * Inspects Digital Signature parameters.
     */
    public static PreflightReport checkDigitalSignature(
            String inputData,
            String algorithm,
            String keyText,
            boolean isKeyMetadataOnly,
            boolean isSignMode
    ) {
        List<PreflightCheck> checks = new ArrayList<>();

        if (inputData == null || inputData.trim().isEmpty()) {
            checks.add(new PreflightCheck("Signature Input", PreflightStatus.INCOMPLETE, "Input data to " + (isSignMode ? "sign" : "verify") + " is empty.", "authInputArea"));
        } else {
            checks.add(new PreflightCheck("Signature Input", PreflightStatus.READY, "Input payload present.", "authInputArea"));
        }

        if (algorithm == null || algorithm.trim().isEmpty()) {
            checks.add(new PreflightCheck("Signature Algorithm", PreflightStatus.INCOMPLETE, "Signature algorithm is not selected.", "signatureAlgorithmCombo"));
        } else {
            checks.add(new PreflightCheck("Signature Algorithm", PreflightStatus.READY, "Algorithm selected: " + algorithm, "signatureAlgorithmCombo"));
        }

        String keyFieldTarget = isSignMode ? "signaturePrivateKeyArea" : "signaturePublicKeyArea";
        if (keyText == null || keyText.trim().isEmpty()) {
            checks.add(new PreflightCheck("Signature Key", PreflightStatus.INCOMPLETE, (isSignMode ? "Private" : "Public") + " key is missing.", keyFieldTarget));
        } else if (isKeyMetadataOnly) {
            checks.add(new PreflightCheck("Signature Key", PreflightStatus.BLOCKED, "Selected key is metadata-only without key material.", keyFieldTarget));
        } else {
            checks.add(new PreflightCheck("Signature Key", PreflightStatus.READY, (isSignMode ? "Private" : "Public") + " key present.", keyFieldTarget));
        }

        return buildReport(checks);
    }

    /**
     * Inspects MAC parameters.
     */
    public static PreflightReport checkMac(String inputData, String algorithm, String keyHex, boolean isMetadataOnly) {
        return checkMac(inputData, algorithm, "Manual Input", keyHex, null, isMetadataOnly);
    }

    /**
     * Inspects MAC parameters while respecting the selected key source.
     */
    public static PreflightReport checkMac(
            String inputData,
            String algorithm,
            String keySource,
            String manualKeyHex,
            String selectedKeyReference,
            boolean isMetadataOnly
    ) {
        List<PreflightCheck> checks = new ArrayList<>();

        if (inputData == null || inputData.trim().isEmpty()) {
            checks.add(new PreflightCheck("MAC Input", PreflightStatus.INCOMPLETE, "Input payload for MAC calculation is empty.", "macInputArea"));
        } else {
            checks.add(new PreflightCheck("MAC Input", PreflightStatus.READY, "Input payload present.", "macInputArea"));
        }

        if (algorithm == null || algorithm.trim().isEmpty()) {
            checks.add(new PreflightCheck("MAC Algorithm", PreflightStatus.INCOMPLETE, "MAC algorithm is not selected.", "macAlgorithmCombo"));
        } else {
            checks.add(new PreflightCheck("MAC Algorithm", PreflightStatus.READY, "MAC algorithm selected: " + algorithm, "macAlgorithmCombo"));
        }

        boolean externalKeySource = "Simulated HSM".equalsIgnoreCase(keySource)
                || "PKCS#11 Token".equalsIgnoreCase(keySource);
        if (externalKeySource) {
            if (selectedKeyReference == null || selectedKeyReference.trim().isEmpty()) {
                checks.add(new PreflightCheck("MAC Key", PreflightStatus.INCOMPLETE,
                        "Select a MAC key from the configured key source.", "macHsmKeyCombo"));
            } else if (isMetadataOnly) {
                checks.add(new PreflightCheck("MAC Key", PreflightStatus.BLOCKED,
                        "MAC key reference is metadata-only without key material. Re-import it in Key Lab.", "macHsmKeyCombo"));
            } else {
                checks.add(new PreflightCheck("MAC Key", PreflightStatus.READY,
                        "Selected external MAC key: " + selectedKeyReference, "macHsmKeyCombo"));
            }
        } else if (manualKeyHex == null || manualKeyHex.trim().isEmpty()) {
            checks.add(new PreflightCheck("MAC Key", PreflightStatus.INCOMPLETE, "MAC key is required.", "authMacKeyField"));
        } else if (!isValidHex(manualKeyHex.replaceAll("\\s+", ""))) {
            checks.add(new PreflightCheck("MAC Key", PreflightStatus.BLOCKED,
                    "Manual MAC key contains non-hexadecimal characters.", "authMacKeyField"));
        } else {
            checks.add(new PreflightCheck("MAC Key", PreflightStatus.READY, "Manual MAC key present.", "authMacKeyField"));
        }

        return buildReport(checks);
    }

    /**
     * Inspects Asymmetric RSA Cipher parameters.
     */
    public static PreflightReport checkAsymmetricCipher(
            String inputData,
            String keyPem,
            boolean isMetadataOnly,
            String paddingScheme,
            boolean isEncrypt
    ) {
        List<PreflightCheck> checks = new ArrayList<>();

        if (inputData == null || inputData.trim().isEmpty()) {
            checks.add(new PreflightCheck("Asymmetric Input", PreflightStatus.INCOMPLETE, "Input data payload is empty.", "cipherInputArea"));
        } else {
            checks.add(new PreflightCheck("Asymmetric Input", PreflightStatus.READY, "Input payload present.", "cipherInputArea"));
        }

        String keyTarget = isEncrypt ? "publicKeyArea" : "privateKeyArea";
        if (keyPem == null || keyPem.trim().isEmpty()) {
            checks.add(new PreflightCheck("RSA Key", PreflightStatus.INCOMPLETE, (isEncrypt ? "Public" : "Private") + " RSA key in PEM format is required.", keyTarget));
        } else if (isMetadataOnly) {
            checks.add(new PreflightCheck("RSA Key", PreflightStatus.BLOCKED, "Selected RSA key reference has no secret/public key bytes (Metadata-Only).", keyTarget));
        } else {
            checks.add(new PreflightCheck("RSA Key", PreflightStatus.READY, (isEncrypt ? "Public" : "Private") + " RSA key loaded.", keyTarget));
        }

        if (paddingScheme == null || paddingScheme.trim().isEmpty()) {
            checks.add(new PreflightCheck("RSA Padding", PreflightStatus.INCOMPLETE, "RSA padding scheme is not selected.", "rsaPaddingCombo"));
        } else {
            if (paddingScheme.contains("PKCS1Padding")) {
                checks.add(new PreflightCheck("RSA Padding", PreflightStatus.WARNING, "PKCS1Padding is vulnerable to Bleichenbacher oracle attacks. Use OAEP padding.", "rsaPaddingCombo"));
            } else if (paddingScheme.contains("NoPadding")) {
                checks.add(new PreflightCheck("RSA Padding", PreflightStatus.WARNING, "Raw RSA (NoPadding) is insecure against textbook RSA attacks. Use OAEP padding.", "rsaPaddingCombo"));
            } else {
                checks.add(new PreflightCheck("RSA Padding", PreflightStatus.READY, "RSA padding selected: " + paddingScheme, "rsaPaddingCombo"));
            }
        }

        return buildReport(checks);
    }

    private static PreflightReport buildReport(List<PreflightCheck> checks) {
        PreflightStatus highestStatus = PreflightStatus.READY;
        int nonReadyCount = 0;

        for (PreflightCheck check : checks) {
            PreflightStatus status = check.getStatus();
            if (status == PreflightStatus.BLOCKED) {
                highestStatus = PreflightStatus.BLOCKED;
                nonReadyCount++;
            } else if (status == PreflightStatus.INCOMPLETE && highestStatus != PreflightStatus.BLOCKED) {
                highestStatus = PreflightStatus.INCOMPLETE;
                nonReadyCount++;
            } else if (status == PreflightStatus.WARNING && highestStatus != PreflightStatus.BLOCKED && highestStatus != PreflightStatus.INCOMPLETE) {
                highestStatus = PreflightStatus.WARNING;
                nonReadyCount++;
            }
        }

        String summary;
        if (highestStatus == PreflightStatus.READY) {
            summary = "✔ Preflight Passed — Ready for safe execution.";
        } else if (highestStatus == PreflightStatus.BLOCKED) {
            summary = "⛔ Execution Blocked — " + nonReadyCount + " critical issue(s) detected.";
        } else if (highestStatus == PreflightStatus.INCOMPLETE) {
            summary = "⚠️ Input Incomplete — " + nonReadyCount + " required field(s) missing.";
        } else {
            summary = "💡 Security Recommendation — " + nonReadyCount + " warning(s) detected (Executable).";
        }

        return new PreflightReport(highestStatus, summary, checks);
    }

    private static boolean isValidHex(String input) {
        if (input == null) return false;
        return input.matches("^[0-9a-fA-F]*$");
    }

    private static boolean isValidBase64(String input) {
        if (input == null) return false;
        String clean = input.replaceAll("\\s+", "");
        try {
            java.util.Base64.getDecoder().decode(clean);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
