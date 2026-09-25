package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Visa HCE against the external tool's worked example (EMV, HCE, Visa, default values,
 * captured 2026-09-25) and the second example its vendor publishes.
 */
class VisaHceOperationsTest {

    private static final String LUK = "D144CA8CBB4BD463C8EDD5761BF1770E";

    @Test
    void theCardKeyIsOptionAWithOddParity() {
        // MDK single-length in double form, 17-digit PAN, PSN 01.
        assertEquals("3DEF38543BDFAEC42A2C49FECDBC6B92", EmvSecureMessaging.mastercardUdk(
                "0123456789ABCDEF0123456789ABCDEF", "1987654321098701"));
    }

    @Test
    void theLimitedUseKeyReproducesBothExamples() {
        assertEquals(LUK, VisaHceOperations.limitedUseKey("94E3194C02105E3B153438D562D5A49D", "26", "6431", "01"));
        assertEquals("3EA78DED27B7BB01F9069B8E34C443BC",
                VisaHceOperations.limitedUseKey("C8B507136D921FD05864C81F79F2D30B", "0", "5702", "01"));
    }

    /** The ATC takes the place of the device type's first 4 digits, as in a dCVV. */
    @Test
    void theMsdVerificationValueReproducesAllThreeExamples() {
        assertEquals("634", VisaHceOperations.msdVerificationValue(LUK, "0001", "AAAA000000000001"));
        assertEquals("385", VisaHceOperations.msdVerificationValue(LUK, "0001", "AAAA0000000000F1"));
        assertEquals("675", VisaHceOperations.msdVerificationValue("3EA78DED27B7BB01F9069B8E34C443BC",
                "0001", "AAAA000000000001"));
    }

    @Test
    void theQvsdcCryptogramIsAlgorithm3OverTerminalAndIccData() throws Exception {
        String terminal = "000000001000" + "000000000000" + "0710" + "0000000000" + "0710" + "130205" + "00" + "30901B6A";
        String icc = "3C00" + "0055" + "03A4A082";
        assertEquals("42A0254F47679C5A", VisaHceOperations.qvsdcCryptogram(LUK, terminal, icc));
    }
}
