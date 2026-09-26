package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LegacyPinBlockFormatTest {
    private static final String PAN = "4111111111111111";

    @Test void docutelUsesInjectedDecimalPaddingAndRoundTrips() throws Exception {
        String block = PaymentOperations.encodePinBlock("1234", PAN, "Docutel (Format 02)", () -> 7);
        assertEquals("4123400777777777", block);
        assertEquals("1234", PaymentOperations.decodePinBlock(block, PAN, "Docutel"));
        assertTrue(PinBlockFormat.DOCUTEL.randomPadding());
        assertFalse(PinBlockFormat.DOCUTEL.usesPan());
        IllegalArgumentException shortPin = assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.encodePinBlock("123", PAN, "Docutel"));
        assertTrue(shortPin.getMessage().contains("4..6"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.encodePinBlock("1234567", PAN, "Docutel"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.encodePinBlock("1234", PAN, "Docutel", () -> 10));
    }

    @Test void dieboldUsesFPaddingAndRoundTrips() throws Exception {
        assertEquals("1234FFFFFFFFFFFF", PaymentOperations.encodePinBlock("1234", PAN, "Diebold"));
        assertEquals("1234", PaymentOperations.decodePinBlock("1234FFFFFFFFFFFF", PAN, "Diebold (Format 03)"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.encodePinBlock("1234567890123", PAN, "Diebold"));
    }

    @Test void plusUsesLeftmostTwelvePanDigitsAndRoundTrips() throws Exception {
        assertEquals("041275EEEEEEEEEE", PaymentOperations.encodePinBlock("1234", PAN, "Plus Network"));
        assertEquals("1234", PaymentOperations.decodePinBlock("041275EEEEEEEEEE", PAN, "PLUS"));
        // Published format-04 example: PIN 92389, PAN 2283400000123456.
        assertEquals("05921A1CBFFFFFED", PaymentOperations.encodePinBlock("92389", "2283400000123456", "Plus Network"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.encodePinBlock("123", PAN, "Plus Network"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.encodePinBlock("1234", "123", "Plus Network"));
    }

    @Test void unknownFormatFailsOnEncode() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.encodePinBlock("1234", PAN, "mystery format"));
        assertTrue(error.getMessage().contains("mystery format"));
    }

    @Test void unknownFormatFailsOnDecode() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PaymentOperations.decodePinBlock("041225EEEEEEEEEE", PAN, "mystery format"));
        assertTrue(error.getMessage().contains("mystery format"));
    }

    @Test void oldNamesAndUnverifiedEquivalencesStayCompatible() throws Exception {
        String iso0 = PaymentOperations.encodePinBlock("1234", PAN, "Format 0 (ISO-0)");
        assertEquals(iso0, PaymentOperations.encodePinBlock("1234", PAN, "ISO 0 (ANSI X9.8)"));
        assertEquals(iso0, PaymentOperations.encodePinBlock("1234", PAN, "ECI-1"));
        assertEquals(iso0, PaymentOperations.encodePinBlock("1234", PAN, "VISA-4"));
        assertEquals("1234", PaymentOperations.decodePinBlock(iso0, PAN, "ECI-1"));
        assertEquals("1234", PaymentOperations.decodePinBlock(iso0, PAN, "VISA-4"));
        assertTrue(PinBlockFormat.ECI1.unverifiedEquivalence());
        assertTrue(PinBlockFormat.ECI4.unverifiedEquivalence());
        assertTrue(PinBlockFormat.VISA4.unverifiedEquivalence());
    }
}
