package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoEnvelopeCodecTest {

    private static CryptoEnvelope sampleEnvelope() {
        return CryptoEnvelope.forAlgorithm("RSA-OAEP-256")
                .kid("kek-2026-01")
                .keyVersion(3)
                .kcv("A1B2C3")
                .ivNonceHex("0102030405060708090a0b0c")
                .aadHex("deadbeef")
                .ciphertext(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
                .extension("profile", "CMS_ENVELOPED")
                .build();
    }

    @Test
    void jsonRoundTripPreservesAllFields() {
        CryptoEnvelope original = sampleEnvelope();
        String json = CryptoEnvelopeCodec.serializeJson(original);

        assertFalse(json.toLowerCase().contains("\"key\""), "Envelope JSON must never contain a 'key' field");

        CryptoEnvelope restored = CryptoEnvelopeCodec.deserializeJson(json);
        assertEquals(original, restored);
        assertEquals("RSA-OAEP-256", restored.getAlg());
        assertEquals("kek-2026-01", restored.getKid());
        assertEquals(3, restored.getKeyVersion());
        assertEquals("A1B2C3", restored.getKcv());
        assertEquals("CMS_ENVELOPED", restored.getExtensions().get("profile"));
    }

    @Test
    void compactRoundTripPreservesAllFields() {
        CryptoEnvelope original = sampleEnvelope();
        String compact = CryptoEnvelopeCodec.serializeCompact(original);

        assertTrue(compact.startsWith("CCE1."));
        assertEquals(2, compact.substring("CCE1.".length()).split("\\.", -1).length);

        CryptoEnvelope restored = CryptoEnvelopeCodec.deserializeCompact(compact);
        assertEquals(original, restored);
    }

    @Test
    void deserializeAutoDetectsBothProfiles() {
        CryptoEnvelope original = sampleEnvelope();
        assertEquals(original, CryptoEnvelopeCodec.deserializeAuto(CryptoEnvelopeCodec.serializeJson(original)));
        assertEquals(original, CryptoEnvelopeCodec.deserializeAuto(CryptoEnvelopeCodec.serializeCompact(original)));
    }

    @Test
    void looksLikeEnvelopeRecognizesBothProfilesAndRejectsPlainText() {
        CryptoEnvelope original = sampleEnvelope();
        assertTrue(CryptoEnvelopeCodec.looksLikeEnvelope(CryptoEnvelopeCodec.serializeJson(original)));
        assertTrue(CryptoEnvelopeCodec.looksLikeEnvelope(CryptoEnvelopeCodec.serializeCompact(original)));
        assertFalse(CryptoEnvelopeCodec.looksLikeEnvelope(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})));
        assertFalse(CryptoEnvelopeCodec.looksLikeEnvelope(null));
    }

    @Test
    void rejectsUnsupportedVersion() {
        String json = "{\"envVersion\":\"CCE-99\",\"alg\":\"RSA-OAEP-256\",\"ciphertextB64\":\"AQID\"}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CryptoEnvelopeCodec.deserializeJson(json));
        assertTrue(ex.getMessage().contains("Unsupported envelope version"));
    }

    @Test
    void rejectsUnknownAlgorithm() {
        String json = "{\"envVersion\":\"CCE-1\",\"alg\":\"ROT13\",\"ciphertextB64\":\"AQID\"}";
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelopeCodec.deserializeJson(json));
    }

    @Test
    void rejectsForbiddenSecretKeyField() {
        String json = "{\"envVersion\":\"CCE-1\",\"alg\":\"RSA-OAEP-256\",\"ciphertextB64\":\"AQID\","
                + "\"extensions\":{\"privateKey\":\"deadbeef\"}}";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CryptoEnvelopeCodec.deserializeJson(json));
        assertTrue(ex.getMessage().contains("Security violation"));
    }

    @Test
    void rejectsUnknownRootKey() {
        String json = "{\"envVersion\":\"CCE-1\",\"alg\":\"RSA-OAEP-256\",\"ciphertextB64\":\"AQID\",\"unexpected\":\"x\"}";
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelopeCodec.deserializeJson(json));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelopeCodec.deserializeJson("{not json"));
    }

    @Test
    void rejectsMissingCiphertext() {
        String json = "{\"envVersion\":\"CCE-1\",\"alg\":\"RSA-OAEP-256\"}";
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelopeCodec.deserializeJson(json));
    }

    @Test
    void rejectsBadHexFields() {
        String json = "{\"envVersion\":\"CCE-1\",\"alg\":\"RSA-OAEP-256\",\"ciphertextB64\":\"AQID\",\"ivNonceHex\":\"zz\"}";
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelopeCodec.deserializeJson(json));
    }

    @Test
    void rejectsCompactEnvelopeWithoutPrefix() {
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelopeCodec.deserializeCompact("not-an-envelope"));
    }

    @Test
    void builderDefaultsCreatedAtAndVersion() {
        CryptoEnvelope envelope = CryptoEnvelope.forAlgorithm("RSA-OAEP-256")
                .ciphertext(new byte[]{1})
                .build();
        assertEquals("CCE-1", envelope.getEnvVersion());
        assertNotNull(envelope.getCreatedAt());
        java.time.Instant.parse(envelope.getCreatedAt()); // must not throw
    }

    @Test
    void builderRequiresCiphertext() {
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelope.forAlgorithm("RSA-OAEP-256").build());
    }
}
