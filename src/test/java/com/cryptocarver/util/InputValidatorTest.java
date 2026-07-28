package com.cryptocarver.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputValidatorTest {

    @Test
    void acceptsWellFormedRepresentations() {
        assertDoesNotThrow(() -> InputValidator.validateInput("0011 aaFF", "Hexadecimal"));
        assertDoesNotThrow(() -> InputValidator.validateInput("TWFu", "Base64"));
        assertDoesNotThrow(() -> InputValidator.validateInput("SGVsbG8", "Base64URL"));
    }

    @Test
    void rejectsMalformedHexAndBase64BeforeExecution() {
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInput("ABC", "Hexadecimal"));
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInput("TWF=", "Base64"));
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInput("T-W+", "Base64"));
    }
}
