package com.cryptocarver.crypto.hsm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayShieldCatalogTest {
    @Test
    void commandCatalogContainsTheExposedOpaqueCommands() {
        assertEquals(16, PayShieldCommand.values().length);
        assertEquals("ND", PayShieldCommand.NC.expectedResponseCode());
    }

    @Test
    void onlyCapturedErrorMeaningsAreClaimedAsVerified() {
        assertTrue(PayShieldErrorCatalog.translate("00").startsWith("No error"));
        assertFalse(PayShieldErrorCatalog.isKnown("14"));
        assertTrue(PayShieldErrorCatalog.translate("14").startsWith("unverified"));
    }
}
