package com.cryptocarver.crypto.smartcard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApduStatusTest {
    @Test
    void interpretsResponseAvailableAndWrongLengthRanges() {
        assertEquals(ApduStatus.Category.RESPONSE_AVAILABLE, ApduStatus.parse("61 10").category());
        assertEquals(ApduStatus.Category.WRONG_LENGTH, ApduStatus.parse("6C 00").category());
        assertEquals("Response bytes available; SW2 is the byte count (00 means 256)",
                ApduStatus.parse("6110").meaning());
    }

    @Test
    void keepsConservativeExactAndRangeMeanings() {
        assertEquals("File or application not found", ApduStatus.parse("6A82").meaning());
        assertEquals(ApduStatus.Category.WARNING, ApduStatus.parse("6285").category());
        assertEquals(ApduStatus.Category.UNKNOWN, ApduStatus.parse("7000").category());
        assertEquals("9000", ApduStatus.parse("9000").hex());
    }
}
