package com.cryptocarver.model.help;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Safe, stable selection state for help resolution. Values are option codes or
 * derived flags/sizes; raw text fields and secret-bearing controls have no API here.
 */
public record HelpContext(String operationId, String actionId, String variantId,
                          Locale locale, String fieldId,
                          Map<Selection, PublicSelection> selections) {
    public enum Selection {
        ALGORITHM, MODE, PADDING, FORMAT, KEY_SOURCE, PROFILE,
        JWE_KEY_ALGORITHM, JWE_CONTENT_ENCRYPTION, JWE_SERIALIZATION,
        JWE_KEY_FORMAT, JWE_DECRYPT_KEY_FORMAT, JWE_COMPRESSION,
        JWE_AAD_PRESENT, JWE_PUBLIC_MATERIAL_PRESENT, JWE_PRIVATE_MATERIAL_PRESENT, JWE_PAYLOAD_PRESENT,
        CLEAR_PIN_ENCODE_FORMAT, CLEAR_PIN_DECODE_FORMAT, CLEAR_PIN_PADDING, ENCRYPTED_PIN_FORMAT,
        ENCRYPT_PIN_KEY_PRESENT, DECRYPT_PIN_KEY_PRESENT,
        MATERIAL_PRESENT, MATERIAL_BYTES, PREFLIGHT_STATUS, RULE_ID,
        FPE_OPERATION, FPE_ALGORITHM, FPE_PRESET, FPE_RADIX, FPE_KEY_BYTES, FPE_TWEAK_BYTES,
        FILE_PROFILE, FILE_LINES, FILE_LINE_ENCODING, FILE_CHARSET, FILE_COMPACT_CBC,
        RSA_PADDING, RSA_INPUT_FORMAT, RSA_OUTPUT_FORMAT, RSA_PUBLIC_KEY_PRESENT, RSA_PRIVATE_KEY_PRESENT,
        RSA_MODULUS_BITS, OPENPGP_PUBLIC_KEY_PRESENT, OPENPGP_SECRET_KEY_PRESENT, OPENPGP_SIGNATURE_PRESENT,
        OPENPGP_INPUT_PRESENT, OPENPGP_PASSPHRASE_PRESENT, OPENPGP_GNUPG_AVAILABLE,
        ANALYSIS_FORMAT, ANALYSIS_MODE, ANALYSIS_PADDING, ANALYSIS_HAS_KEY, ANALYSIS_HAS_IV,
        ANALYSIS_HAS_TAG, ANALYSIS_HAS_AAD,
        GENERIC_ALGORITHM, GENERIC_OPERATION, GENERIC_FORMAT, GENERIC_ENCODING,
        BATCH_OPERATION, PROCESS_NODE_TYPE, PROCESS_NODE_PARAMETER, PROCESS_NODE_OPTION,
        CHECK_DIGIT_ALGORITHM, MODULAR_OPERATION, RANDOM_FORMAT, ENVELOPE_PROFILE
    }

    /** Canonical code copied from an enumerated selector, never from free-form input. */
    public record PublicSelection(String code) {
        public PublicSelection {
            if (code == null || !code.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}")) {
                throw new IllegalArgumentException("Expected a public enumerated option code");
            }
        }
        public static PublicSelection of(String code) { return new PublicSelection(code); }
        public static PublicSelection flag(boolean value) { return new PublicSelection(value ? "true" : "false"); }
        public static PublicSelection byteSize(int value) {
            if (value < 0) throw new IllegalArgumentException("Size cannot be negative");
            return new PublicSelection(Integer.toString(value));
        }
    }

    public HelpContext {
        operationId = id(operationId, "operation ID");
        actionId = optionalId(actionId, "action ID");
        variantId = optionalId(variantId, "variant ID");
        fieldId = optionalId(fieldId, "field ID");
        locale = locale == null ? Locale.ENGLISH : locale;
        selections = selections == null ? Map.of() : Map.copyOf(selections);
        selections.forEach((key, value) -> Objects.requireNonNull(value, "selection value"));
    }

    public static HelpContext empty() { return new HelpContext("unknown", "", "", Locale.ENGLISH, "", Map.of()); }

    public HelpContext withOperation(String id) {
        String clean = id(id, "operation ID");
        return new HelpContext(clean, clean.equals(operationId) ? actionId : "",
                clean.equals(operationId) ? variantId : "", locale,
                clean.equals(operationId) ? fieldId : "", clean.equals(operationId) ? selections : Map.of());
    }
    public HelpContext withAction(String id) { return new HelpContext(operationId, id, variantId, locale, fieldId, selections); }
    public HelpContext withLocale(Locale value) { return new HelpContext(operationId, actionId, variantId, value, fieldId, selections); }
    public HelpContext withField(String id) { return new HelpContext(operationId, actionId, variantId, locale, id, selections); }
    public HelpContext withSelection(Selection key, PublicSelection value) {
        var copy = new java.util.EnumMap<Selection, PublicSelection>(Selection.class);
        copy.putAll(selections);
        if (value == null) copy.remove(key); else copy.put(Objects.requireNonNull(key), value);
        return new HelpContext(operationId, actionId, variantId, locale, fieldId, copy);
    }
    private static String id(String value, String label) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return value;
    }
    private static String optionalId(String value, String label) {
        return value == null || value.isBlank() ? "" : id(value, label);
    }
}
