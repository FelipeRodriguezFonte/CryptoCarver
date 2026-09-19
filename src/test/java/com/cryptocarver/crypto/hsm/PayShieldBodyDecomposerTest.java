package com.cryptocarver.crypto.hsm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayShieldBodyDecomposerTest {
    private static final byte[] EMPTY = new byte[0];

    @Test
    void registryMakesFieldOffsetsTypesAndEvidenceVisible() {
        PayShieldBodySchema schema = PayShieldBodySchemas.responses("ND", "00").get(0);

        assertEquals(25, schema.bodyLength());
        assertEquals(PayShieldBodySchema.EvidenceStatus.PENDING_CAPTURE,
                schema.evidenceStatus());
        assertEquals("NC-00", schema.evidenceId());
        assertEquals(new PayShieldBodySchema.Field(
                "lmkCheckValue", "LMK check value", 16, PayShieldBodySchema.FieldType.HEX),
                schema.fields().get(0));
        assertEquals(new PayShieldBodySchema.Field(
                "firmwareVersion", "Firmware version", 9,
                PayShieldBodySchema.FieldType.PRINTABLE_ASCII),
                schema.fields().get(1));
    }

    @Test
    void exactNcShapeIsDecomposedByTheGenericEngine() {
        PayShieldResponse response = response("7B44AC1DDEE2A94B0007-E000");

        PayShieldBodyDecomposer.Decomposition decomposition =
                PayShieldBodyDecomposer.decompose(response).orElseThrow();

        assertEquals("7B44AC1DDEE2A94B", decomposition.value("lmkCheckValue").orElseThrow());
        assertEquals("0007-E000", decomposition.value("firmwareVersion").orElseThrow());
    }

    @Test
    void differentLengthIsLeftOpaque() {
        assertFalse(PayShieldBodyDecomposer.decompose(response("SHORT")).isPresent());
    }

    @Test
    void invalidDeclaredTypeIsLeftOpaque() {
        assertFalse(PayShieldBodyDecomposer.decompose(
                response("7B44AC1DDEE2A94G0007-E000")).isPresent());
    }

    @Test
    void unknownResponseHasNoInferredSchema() {
        PayShieldResponse response = new PayShieldResponse(
                "0000", "A1", "00", "OPAQUE".getBytes(StandardCharsets.US_ASCII), EMPTY);

        assertFalse(PayShieldBodyDecomposer.decompose(response).isPresent());
    }

    @Test
    void emptyNcCommandMatchesItsDeclaredPendingShape() {
        PayShieldMessage command = new PayShieldMessage("0000", "NC", EMPTY, EMPTY);

        PayShieldBodyDecomposer.Decomposition decomposition =
                PayShieldBodyDecomposer.decompose(command).orElseThrow();

        assertTrue(decomposition.fields().isEmpty());
        assertEquals("NC-00", decomposition.schema().evidenceId());
    }

    private static PayShieldResponse response(String data) {
        return new PayShieldResponse(
                "0000", "ND", "00", data.getBytes(StandardCharsets.US_ASCII), EMPTY);
    }
}
