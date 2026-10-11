package com.cryptocarver.model.help;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SymmetricCapabilityPolicyTest {
    @Test void blockModesStreamModesAndAeadExposeStableApplicabilityRules() {
        var gcm = SymmetricCapabilityPolicy.resolve("AES-256", "GCM");
        assertTrue(gcm.modeSelectable());
        assertFalse(gcm.paddingSelectable());
        assertTrue(gcm.ivApplicable());
        assertTrue(gcm.tagApplicable());
        assertTrue(gcm.aadUsedByOperation());
        assertEquals(12, gcm.generatedIvBytes());
        assertEquals("symmetric.authentication.authenticated", gcm.authenticationRuleId());
        assertEquals("symmetric.aad.gcm_used", gcm.aadRuleId());

        var cbc = SymmetricCapabilityPolicy.resolve("AES-256", "CBC");
        assertTrue(cbc.modeSelectable());
        assertTrue(cbc.paddingSelectable());
        assertTrue(cbc.ivApplicable());
        assertFalse(cbc.tagApplicable());
        assertEquals("symmetric.authentication.none", cbc.authenticationRuleId());

        var stream = SymmetricCapabilityPolicy.resolve("ChaCha20-Poly1305", "CBC");
        assertTrue(stream.streamCipher());
        assertFalse(stream.modeSelectable());
        assertFalse(stream.paddingSelectable());
        assertTrue(stream.tagApplicable());
        assertFalse(stream.aadUsedByOperation());
        assertEquals(12, stream.generatedIvBytes());
        assertEquals("symmetric.mode.stream_disabled", stream.modeRuleId());
        assertEquals("symmetric.aad.visible_unused", stream.aadRuleId());
    }
}
