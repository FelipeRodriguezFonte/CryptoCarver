package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationControllerMacUiTest {

    @Test
    void profilesExplainModernAndLegacyMacRequirements() {
        var hmac = AuthenticationController.macUiProfile("HMAC-SHA256");
        assertEquals(32, hmac.outputBytes());
        assertTrue(hmac.warning().isBlank());
        assertFalse(hmac.nonceRequired());

        var gmac = AuthenticationController.macUiProfile("GMAC-AES");
        assertEquals(16, gmac.outputBytes());
        assertTrue(gmac.nonceRequired());
        assertTrue(gmac.warning().contains("unique"));

        var poly1305 = AuthenticationController.macUiProfile("Poly1305");
        assertTrue(poly1305.warning().contains("Never reuse"));

        var retail = AuthenticationController.macUiProfile("ANSI-X9.19");
        assertEquals(8, retail.outputBytes());
        assertEquals(4, retail.defaultTruncation());
        assertTrue(retail.warning().contains("payment-specific"));
    }

    @Test
    void aesAndDesCmacExposeTheirNativeTagSizes() {
        assertEquals(16, AuthenticationController.macUiProfile("CMAC-AES").outputBytes());
        assertEquals(8, AuthenticationController.macUiProfile("CMAC-3DES").outputBytes());
    }
}
