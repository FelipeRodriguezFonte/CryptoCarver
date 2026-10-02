package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RsaPaddingLimitsTest {

    @Test
    void pkcs1LeavesElevenBytesOfOverhead() {
        assertEquals(245, RsaPaddingLimits.maxPlaintextBytes(2048, "RSA/ECB/PKCS1Padding"));
    }

    @Test
    void oaepOverheadDependsOnTheDigest() {
        assertEquals(214, RsaPaddingLimits.maxPlaintextBytes(2048, "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"));
        assertEquals(190, RsaPaddingLimits.maxPlaintextBytes(2048, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"));
    }

    @Test
    void smallerModulusShrinksTheLimit() {
        assertEquals(117, RsaPaddingLimits.maxPlaintextBytes(1024, "RSA/ECB/PKCS1Padding"));
        assertEquals(62, RsaPaddingLimits.maxPlaintextBytes(1024, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"));
    }
}
