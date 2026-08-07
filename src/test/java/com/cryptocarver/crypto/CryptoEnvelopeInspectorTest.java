package com.cryptocarver.crypto;

import com.cryptocarver.model.CryptoEnvelope;
import com.cryptocarver.model.CryptoEnvelopeCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoEnvelopeInspectorTest {

    @Test
    void inspectsAJsonEnvelopeAndComputesAge() throws InterruptedException {
        CryptoEnvelope envelope = CryptoEnvelope.forAlgorithm("RSA-OAEP-256")
                .kid("kek-1")
                .ciphertext(new byte[]{1, 2, 3, 4, 5})
                .build();
        Thread.sleep(5);

        CryptoEnvelopeInspector.InspectionResult result =
                CryptoEnvelopeInspector.inspect(CryptoEnvelopeCodec.serializeJson(envelope));

        assertEquals("RSA-OAEP-256", result.getEnvelope().getAlg());
        assertEquals("kek-1", result.getEnvelope().getKid());
        assertEquals(5, result.getCiphertextLengthBytes());
        assertNotNull(result.getAge());
        assertFalse(result.getAge().isNegative());
    }

    @Test
    void inspectsACompactEnvelope() {
        CryptoEnvelope envelope = CryptoEnvelope.forAlgorithm("RSA-OAEP-256")
                .ciphertext(new byte[]{9, 9, 9})
                .build();

        CryptoEnvelopeInspector.InspectionResult result =
                CryptoEnvelopeInspector.inspect(CryptoEnvelopeCodec.serializeCompact(envelope));

        assertEquals(3, result.getCiphertextLengthBytes());
    }

    @Test
    void rejectsTextThatIsNotAnEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> CryptoEnvelopeInspector.inspect("not an envelope"));
    }
}
