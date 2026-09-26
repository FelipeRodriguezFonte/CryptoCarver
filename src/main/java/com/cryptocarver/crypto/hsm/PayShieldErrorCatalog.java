package com.cryptocarver.crypto.hsm;

import java.util.Map;

/**
 * Provisional error meanings and their visible evidence state.
 *
 * <p>Code {@code 00} has a parseable simulator response, but no real payShield
 * capture. The former list of plausible
 * global errors was removed because its cited Core Host Commands manual was not
 * actually available. Unknown codes stay visibly unverified until a captured
 * response or a primary clause can be attached to them.
 */
public final class PayShieldErrorCatalog {
    private static final Map<String, String> KNOWN_ERRORS = Map.of(
            "00", "No error (simulator evidence; real payShield pending)",
            "15", "Invalid input data (label shown by the external HSM console; real payShield pending)",
            "30", "Invalid reference number (label shown by the external HSM console; real payShield pending)");

    private PayShieldErrorCatalog() {
    }

    public static String translate(String code) {
        requireCode(code);
        return KNOWN_ERRORS.getOrDefault(code, "unverified error code (" + code + ")");
    }

    public static boolean isKnown(String code) {
        requireCode(code);
        return KNOWN_ERRORS.containsKey(code);
    }

    public static Map<String, String> catalog() {
        return KNOWN_ERRORS;
    }

    private static void requireCode(String code) {
        if (code == null || !code.matches("[0-9A-Z]{2}")) {
            throw new IllegalArgumentException("error code must be two ASCII characters");
        }
    }
}
