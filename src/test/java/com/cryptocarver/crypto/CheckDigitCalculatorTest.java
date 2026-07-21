package com.cryptocarver.crypto;

import com.cryptocarver.crypto.CheckDigitCalculator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CheckDigitCalculator
 */
class CheckDigitCalculatorTest {

    @Test
    void testLuhnCheckDigit() {
        // Test credit card number without check digit
        String cardNumber = "123456789012345";
        int checkDigit = CheckDigitCalculator.calculateCheckDigit(cardNumber, "Luhn (Mod 10)");

        assertTrue(checkDigit >= 0 && checkDigit <= 9);

        // Validate the complete number
        String complete = cardNumber + checkDigit;
        assertTrue(CheckDigitCalculator.validateCheckDigit(complete, "Luhn (Mod 10)"));
    }

    @Test
    void testVerhoeffCheckDigit() {
        String data = "12345";
        int checkDigit = CheckDigitCalculator.calculateCheckDigit(data, "Verhoeff");

        assertTrue(checkDigit >= 0 && checkDigit <= 9);

        // Validate
        String complete = data + checkDigit;
        assertTrue(CheckDigitCalculator.validateCheckDigit(complete, "Verhoeff"));
    }

    @Test
    void testDammCheckDigit() {
        String data = "572";
        int checkDigit = CheckDigitCalculator.calculateCheckDigit(data, "Damm");

        assertEquals(4, checkDigit); // Known Damm check digit for "572"

        // Validate
        assertTrue(CheckDigitCalculator.validateCheckDigit("5724", "Damm"));
    }

    @Test
    void testFormatWithCheckDigit() {
        String data = "12345";
        String result = CheckDigitCalculator.formatWithCheckDigit(data, "Luhn (Mod 10)");

        assertEquals(6, result.length());
        assertTrue(result.startsWith("12345"));
    }

    @Test
    void testInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> {
            CheckDigitCalculator.calculateCheckDigit(null, "Luhn (Mod 10)");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            CheckDigitCalculator.calculateCheckDigit("", "Luhn (Mod 10)");
        });
    }

    @Test
    void testInvalidAlgorithm() {
        assertThrows(IllegalArgumentException.class, () -> {
            CheckDigitCalculator.calculateCheckDigit("12345", "Invalid Algorithm");
        });
    }
}
