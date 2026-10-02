package com.cryptocarver.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class KeysHexValidationTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {" ", " 001122 ", "GG", "00 11"})
    void rejectsNullAndCharactersOutsideHexadecimalAlphabet(String text) {
        assertFalse(KeysHexValidation.isValidHex(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "001", "aBcD", "00"})
    void acceptsPartialHexadecimalWhileTheUserIsTyping(String text) {
        assertTrue(KeysHexValidation.isValidHex(text));
    }
}
