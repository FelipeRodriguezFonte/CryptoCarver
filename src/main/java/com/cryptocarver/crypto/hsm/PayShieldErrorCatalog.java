package com.cryptocarver.crypto.hsm;

import java.util.Map;

/**
 * Error meanings backed by captured traffic.
 *
 * <p>Only {@code 00} is currently evidenced by the successful NC response kept
 * in {@code PayShieldMessageCodecTest}. The former list of plausible global
 * errors was removed because its cited Core Host Commands manual was not
 * actually available. Unknown codes stay visibly unverified until a captured
 * response or a primary clause can be attached to them.
 */
public final class PayShieldErrorCatalog {
    private static final Map<String, String> VERIFIED_ERRORS = Map.of(
            "00", "No error (verified by captured NC response)");

    private PayShieldErrorCatalog() {
    }

    public static String translate(String code) {
        requireCode(code);
        return VERIFIED_ERRORS.getOrDefault(code, "unverified error code (" + code + ")");
    }

    public static boolean isKnown(String code) {
        requireCode(code);
        return VERIFIED_ERRORS.containsKey(code);
    }

    public static Map<String, String> catalog() {
        return VERIFIED_ERRORS;
    }

    private static void requireCode(String code) {
        if (code == null || !code.matches("[0-9A-Z]{2}")) {
            throw new IllegalArgumentException("error code must be two ASCII characters");
        }
    }
}
