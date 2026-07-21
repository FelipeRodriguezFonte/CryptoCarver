package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EMVOperationsValidationTest {
    private static final String SESSION_KEY = "0123456789ABCDEFFEDCBA9876543210";

    @Test
    void verifiesWithThePaddingMethodThatWasActuallyUsed() throws Exception {
        String data = "0011223344556677"; // one 8-byte block: methods 1 and 2 diverge
        String methodOneArqc = EMVOperations.generateARQC(SESSION_KEY, data, 1);

        assertTrue(EMVOperations.verifyARQC(SESSION_KEY, methodOneArqc, data, 1));
        assertFalse(EMVOperations.verifyARQC(SESSION_KEY, methodOneArqc, data, 2));
    }

    @Test
    void rejectsMalformedStructuredArqcFieldsBeforeMacCalculation() {
        assertThrows(IllegalArgumentException.class, () -> EMVOperations.buildARQCData(
                "0000000001", "000000000000", "0724", "0000000000", "0978", "251207", "00", "12345678"));
        assertThrows(IllegalArgumentException.class, () -> EMVOperations.generateARQC(SESSION_KEY, "XYZ", 2));
    }
}
