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
    void knownErrorMeaningKeepsItsPendingEvidenceVisible() {
        assertTrue(PayShieldErrorCatalog.translate("00").startsWith("No error"));
        assertTrue(PayShieldErrorCatalog.translate("00").contains("simulator evidence; real payShield pending"));
        assertTrue(PayShieldErrorCatalog.translate("15").startsWith("Invalid input data"));
        assertTrue(PayShieldErrorCatalog.translate("30").startsWith("Invalid reference number"));
        assertFalse(PayShieldErrorCatalog.isKnown("14"));
        assertTrue(PayShieldErrorCatalog.translate("14").startsWith("unverified"));
    }
}
