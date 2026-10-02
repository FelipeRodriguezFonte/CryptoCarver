package com.cryptocarver.ui;

/** Shared hexadecimal input predicate, independent of UI controls. */
final class KeysHexValidation {
    private KeysHexValidation() { }

    static boolean isValidHex(String value) {
        if (value == null) return false;
        return value.matches("^[0-9a-fA-F]*$");
    }
}
