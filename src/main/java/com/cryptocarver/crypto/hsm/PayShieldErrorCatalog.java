package com.cryptocarver.crypto.hsm;

import java.util.Map;

/**
 * Provisional error meanings and their visible evidence state.
 *
 * <p>The available NC response has no recorded provenance, so even {@code 00}
 * remains pending until capture {@code NC-00}. The former list of plausible
 * global errors was removed because its cited Core Host Commands manual was not
 * actually available. Unknown codes stay visibly unverified until a captured
 * response or a primary clause can be attached to them.
 */
public final class PayShieldErrorCatalog {
    private static final Map<String, String> KNOWN_ERRORS = Map.of(
            "00", "No error (pending independent capture NC-00)");

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
