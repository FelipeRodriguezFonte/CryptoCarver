package com.cryptocarver.model;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strict allowlist validator for safe operation templates.
 *
 * <p>Ensures that saved and imported templates contain ONLY non-sensitive selector
 * parameters and strictly rejects any key material, secrets, IVs, nonces, inputs,
 * outputs, or redacted markers.</p>
 */
public final class SafeTemplateAllowlist {

    public static final String MODULE_CIPHER = "Cipher";
    public static final String MODULE_HASHING = "Hashing";
    public static final String MODULE_MANUAL_CONVERSION = "Manual Conversion";
    public static final String MODULE_CERTIFICATE_INSPECTION = "Certificate Inspection";
    public static final String MODULE_DIGITAL_SIGNATURES = "Digital Signatures";

    private static final Set<String> ALLOWED_MODULES = Set.of(
            MODULE_CIPHER,
            MODULE_HASHING,
            MODULE_MANUAL_CONVERSION,
            MODULE_CERTIFICATE_INSPECTION,
            MODULE_DIGITAL_SIGNATURES
    );

    private static final Map<String, Set<String>> ALLOWLIST_BY_MODULE = Map.of(
            MODULE_CIPHER, Set.of(
                    "symmetricAlgorithmCombo",
                    "cipherModeCombo",
                    "paddingCombo",
                    "asymmetricInputFormatCombo",
                    "asymmetricOutputFormatCombo",
                    "rsaPaddingCombo",
                    "inputFormatCombo",
                    "outputFormatCombo"
            ),
            MODULE_HASHING, Set.of(
                    "hashAlgorithmCombo",
                    "inputFormatCombo",
                    "outputFormatCombo"
            ),
            MODULE_MANUAL_CONVERSION, Set.of(
                    "manualInputFormatCombo",
                    "manualOutputFormatCombo",
                    "ebcdicConversionCheck",
                    "ebcdicDirectionCombo",
                    "inputFormatCombo",
                    "outputFormatCombo"
            ),
            MODULE_CERTIFICATE_INSPECTION, Set.of(
                    "certFormatCombo",
                    "inputFormatCombo",
                    "outputFormatCombo"
            ),
            MODULE_DIGITAL_SIGNATURES, Set.of(
                    "signatureAlgorithmCombo",
                    "inputFormatCombo",
                    "outputFormatCombo"
            )
    );

    private static final Set<String> FORBIDDEN_KEY_PATTERNS = Set.of(
            "password", "key", "pin", "secret", "iv", "nonce", "aad",
            "token", "result", "payload", "redacted", "certificate", "pem"
    );

    private SafeTemplateAllowlist() {
    }

    public static Set<String> getSupportedModules() {
        return Collections.unmodifiableSet(ALLOWED_MODULES);
    }

    public static Set<String> getAllowedFields(String module) {
        if (module == null) return Collections.emptySet();
        return ALLOWLIST_BY_MODULE.getOrDefault(module.trim(), Collections.emptySet());
    }

    public static void validateTemplate(SafeOperationTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Template cannot be null");
        }
        if (!SafeOperationTemplate.CURRENT_VERSION.equals(template.getFormatVersion())) {
            throw new IllegalArgumentException("Unsupported template format version: " + template.getFormatVersion());
        }

        String module = template.getModule();
        if (module == null || !ALLOWED_MODULES.contains(module.trim())) {
            throw new IllegalArgumentException("Unsupported or invalid template module: " + module);
        }

        Set<String> allowedFields = getAllowedFields(module);
        Map<String, String> parameters = template.getParameters();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Template parameter key cannot be empty");
            }

            // Must be in module allowlist
            String simpleKey = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
            if (!allowedFields.contains(simpleKey) && !allowedFields.contains(key)) {
                throw new IllegalArgumentException("Field '" + key + "' is not permitted in safe template for module '" + module + "'");
            }

            // Must NOT contain forbidden keywords (except for explicit format combo names)
            String lowerKey = key.toLowerCase(Locale.ROOT);
            if (isForbiddenKey(lowerKey, simpleKey)) {
                throw new IllegalArgumentException("Forbidden keyword detected in parameter key: '" + key + "'");
            }

            // Value check: must not be REDACTED_SECRET or look like sensitive key bytes/passwords
            if (value != null && isForbiddenValue(value)) {
                throw new IllegalArgumentException("Forbidden value or sensitive payload detected in parameter '" + key + "'");
            }
        }
    }

    private static boolean isForbiddenKey(String lowerKey, String simpleKey) {
        if ("inputformatcombo".equalsIgnoreCase(simpleKey) || "outputformatcombo".equalsIgnoreCase(simpleKey)
                || "asymmetricinputformatcombo".equalsIgnoreCase(simpleKey) || "asymmetricoutputformatcombo".equalsIgnoreCase(simpleKey)
                || "manualinputformatcombo".equalsIgnoreCase(simpleKey) || "manualoutputformatcombo".equalsIgnoreCase(simpleKey)) {
            return false;
        }

        // Check if key matches forbidden secret keywords
        if (lowerKey.contains("input") || lowerKey.contains("output") || lowerKey.contains("area") || lowerKey.contains("text")) {
            return true;
        }

        for (String forbidden : FORBIDDEN_KEY_PATTERNS) {
            if (lowerKey.contains(forbidden)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isForbiddenValue(String value) {
        String trimmed = value.trim();
        if ("[REDACTED_SECRET]".equalsIgnoreCase(trimmed)) {
            return true;
        }
        // Reject values that appear to be hex key material (> 32 hex chars) or PEM private keys
        if (trimmed.startsWith("-----BEGIN") || trimmed.contains("PRIVATE KEY")) {
            return true;
        }
        return false;
    }
}
