package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Integrated Data Storage against Book C-2 section 8.2 and the external tool's worked
 * example (EMV, Data Storage Partial Key, MasterCard, default values, 2026-09-26).
 */
class MastercardDataStorageTest {

    private static final String DS_ID = "5168624300900697";

    @Test
    void thePartialKeyReadsEachByteAsTwoDecimalDigitsTimesTwo() {
        assertEquals("66887C5600B47C5600B40CC2", MastercardDataStorage.partialKey(DS_ID));
    }

    /** The tool prints X = OID XOR PD = 93BAB6DCD5FEFC10, KL = …8399 and KR = …8499 on the way. */
    @Test
    void owhf2GivesTheToolsDigest() {
        assertEquals("659C8EBFAA816DB5",
                MastercardDataStorage.owhf2(DS_ID, "8199829983998499", "1223344556677889"));
    }
}
