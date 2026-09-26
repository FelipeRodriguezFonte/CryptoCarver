package com.cryptocarver.crypto;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/** Padding selection for legacy clear PIN blocks. A fixed value is one hexadecimal nibble. */
public final class PinBlockPadding {
    public static final String DEFAULT = "DEFAULT";
    public static final String RANDOM_HEX = "RANDOM_HEX";
    public static final String RANDOM_DECIMAL = "RANDOM_DECIMAL";
    public static final List<String> OPTIONS = java.util.stream.Stream.concat(
            "0123456789ABCDEF".chars().mapToObj(c -> String.valueOf((char) c)),
            java.util.stream.Stream.of(RANDOM_HEX, RANDOM_DECIMAL)).toList();

    public static final List<String> NODE_OPTIONS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(DEFAULT), OPTIONS.stream()).toList();

    private PinBlockPadding() { }

    public static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!OPTIONS.contains(normalized))
            throw new IllegalArgumentException("PIN block padding must be one hexadecimal digit (0–F), RANDOM_HEX or RANDOM_DECIMAL");
        return normalized;
    }

    static IntSupplier supplier(String value, IntSupplier randomDigit) {
        String padding = normalize(value);
        if (padding.length() == 1) {
            int fixed = Character.digit(padding.charAt(0), 16);
            return () -> fixed;
        }
        return () -> {
            int digit = randomDigit.getAsInt();
            int limit = RANDOM_DECIMAL.equals(padding) ? 10 : 16;
            return Math.floorMod(digit, limit);
        };
    }

    static IntSupplier secureRandom() {
        SecureRandom random = new SecureRandom();
        return random::nextInt;
    }
}
