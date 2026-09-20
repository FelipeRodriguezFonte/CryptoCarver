package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy clear-block vectors and round trips.  The Visa-2/Visa-3 vectors are
 * the public examples in the EMV.COOL complete PIN-block table; IBM's public
 * PIN-profile documentation and the XFS4IoT pin-pad contract identify the
 * supported legacy names and their PAN-binding properties.
 */
class PaymentOperationsLegacyPinBlockTest {
    private static final String PAN = "43219876543210987";

    @Test
    void visa2PublicVectorAndRoundTrip() throws Exception {
        assertEquals("4123400555555555", PaymentOperations.encodePinBlock("1234", PAN, "VISA-2"));
        assertEquals("1234", PaymentOperations.decodePinBlock("4123400555555555", PAN, "VISA-2"));
    }

    @Test
    void visa3PublicVectorAndRoundTrip() throws Exception {
        assertEquals("1234F55555555555", PaymentOperations.encodePinBlock("1234", PAN, "VISA-3"));
        assertEquals("1234", PaymentOperations.decodePinBlock("1234F55555555555", PAN, "VISA-3"));
    }

    @Test
    void eciFormatsHaveExpectedLegacyAliases() throws Exception {
        assertEquals(PaymentOperations.encodePinBlock("1234", PAN, "ISO-0"),
                PaymentOperations.encodePinBlock("1234", PAN, "ECI-1"));
        String eci4 = PaymentOperations.encodePinBlock("1234", PAN, "ECI-4");
        assertEquals("1234", PaymentOperations.decodePinBlock(eci4, PAN, "ECI-4"));
        assertEquals("1234FFFFFFFFFFFF", PaymentOperations.encodePinBlock("1234", PAN, "ECI-2"));
        assertEquals("41234" + "F".repeat(11), PaymentOperations.encodePinBlock("1234", PAN, "ECI-3"));
        assertEquals("1234", PaymentOperations.decodePinBlock("1234FFFFFFFFFFFF", PAN, "ECI-2"));
        assertEquals("1234", PaymentOperations.decodePinBlock("41234" + "F".repeat(11), PAN, "ECI-3"));
    }

    @Test
    void translationReportWarnsForNonPanBoundFormats() throws Exception {
        String report = PaymentOperations.getTranslationDetails(
                "4123400555555555", PAN, "VISA-2", "VISA-3");
        assertTrue(report.contains("does not bind the PIN block to the PAN"));
    }

    @Test
    void translatedUiLabelsRemainRecognizedAsNonPanBound() throws Exception {
        String report = PaymentOperations.getTranslationDetails(
                "1234FFFFFFFFFFFF", PAN, "ECI-2 (no PAN binding)", "VISA-3");
        assertTrue(report.contains("does not bind the PIN block to the PAN"));
    }
}
