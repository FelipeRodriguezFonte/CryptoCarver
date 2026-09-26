package com.cryptocarver.crypto.hsm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayShieldBodyDecomposerTest {
    private static final byte[] EMPTY = new byte[0];

    @Test
    void registryMakesFieldOffsetsTypesAndEvidenceVisible() {
        PayShieldBodySchema schema = PayShieldBodySchemas.responses("ND", "00").get(0);

        assertEquals(25, schema.bodyLength());
        assertEquals(PayShieldBodySchema.EvidenceStatus.THIRD_PARTY_SIMULATOR,
                schema.evidenceStatus());
        assertEquals("SIM-ND-01", schema.evidenceId());
        assertEquals(new PayShieldBodySchema.Field(
                "lmkCheckValue", "lmkCheckValue", 16, PayShieldBodySchema.FieldType.HEX),
                schema.fields().get(0));
        assertEquals(new PayShieldBodySchema.Field(
                "firmwareVersion", "firmwareVersion", 9,
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
                "0000", "ZZ", "00", "OPAQUE".getBytes(StandardCharsets.US_ASCII), EMPTY);

        assertFalse(PayShieldBodyDecomposer.decompose(response).isPresent());
    }

    @Test
    void emptyNcCommandMatchesItsCapturedShape() {
        PayShieldMessage command = new PayShieldMessage("0000", "NC", EMPTY, EMPTY);

        PayShieldBodyDecomposer.Decomposition decomposition =
                PayShieldBodyDecomposer.decompose(command).orElseThrow();

        assertTrue(decomposition.fields().isEmpty());
        assertEquals("SIM-NC-01", decomposition.schema().evidenceId());
    }

    @Test
    void everyVerifiedSchemaDecomposesItsCapturedBody() {
        List<PayShieldBodySchema> verifiedSchemas = PayShieldBodySchemas.all().stream()
                .filter(schema -> schema.evidenceStatus()
                        == PayShieldBodySchema.EvidenceStatus.VERIFIED)
                .toList();

        assertEquals(0, verifiedSchemas.size(),
                "verified schemas count must match recognized captured evidence count");

        verifiedSchemas.forEach(PayShieldBodyDecomposerTest::assertDecomposesCapturedBody);
    }

    @Test
    void syntheticVerifiedSchemaDecomposesItsCapturedBody() {
        byte[] sample = "0123456789ABCDEF0007-E000".getBytes(StandardCharsets.US_ASCII);
        PayShieldBodySchema synthetic = new PayShieldBodySchema(
                PayShieldBodySchema.Direction.RESPONSE,
                "ND",
                "00",
                PayShieldBodySchema.EvidenceStatus.VERIFIED,
                "SYNTHETIC-01",
                sample,
                List.of(
                        new PayShieldBodySchema.Field("lmkCheckValue", "LMK check value", 16, PayShieldBodySchema.FieldType.HEX),
                        new PayShieldBodySchema.Field("firmwareVersion", "Firmware version", 9, PayShieldBodySchema.FieldType.PRINTABLE_ASCII)));

        assertDecomposesCapturedBody(synthetic);
    }

    @Test
    void verifiedSchemaFailsWhenSampleLengthOrFieldsMismatch() {
        PayShieldBodySchema wrongLength = new PayShieldBodySchema(
                PayShieldBodySchema.Direction.RESPONSE,
                "ND",
                "00",
                PayShieldBodySchema.EvidenceStatus.VERIFIED,
                "SYNTHETIC-LENGTH-MISMATCH",
                "SHORT".getBytes(StandardCharsets.US_ASCII),
                List.of(
                        new PayShieldBodySchema.Field("lmkCheckValue", "LMK check value", 16, PayShieldBodySchema.FieldType.HEX),
                        new PayShieldBodySchema.Field("firmwareVersion", "Firmware version", 9, PayShieldBodySchema.FieldType.PRINTABLE_ASCII)));

        assertFalse(PayShieldBodyDecomposer.decompose(wrongLength, wrongLength.capturedBody()).isPresent());
        assertThrows(AssertionError.class, () -> assertDecomposesCapturedBody(wrongLength));

        PayShieldBodySchema wrongFields = new PayShieldBodySchema(
                PayShieldBodySchema.Direction.RESPONSE,
                "ND",
                "00",
                PayShieldBodySchema.EvidenceStatus.VERIFIED,
                "SYNTHETIC-FIELD-MISMATCH",
                "0123456789ABCDEG0007-E000".getBytes(StandardCharsets.US_ASCII),
                List.of(
                        new PayShieldBodySchema.Field("lmkCheckValue", "LMK check value", 16, PayShieldBodySchema.FieldType.HEX),
                        new PayShieldBodySchema.Field("firmwareVersion", "Firmware version", 9, PayShieldBodySchema.FieldType.PRINTABLE_ASCII)));

        assertFalse(PayShieldBodyDecomposer.decompose(wrongFields, wrongFields.capturedBody()).isPresent());
        assertThrows(AssertionError.class, () -> assertDecomposesCapturedBody(wrongFields));
    }

    @Test
    void verifiedSchemaCannotOmitItsCapturedBody() {
        assertThrows(IllegalArgumentException.class, () -> new PayShieldBodySchema(
                PayShieldBodySchema.Direction.COMMAND, "ZZ", null,
                PayShieldBodySchema.EvidenceStatus.VERIFIED, "CAP-01", null, List.of()));
    }

    @Test
    void validationRejectsAmbiguousSchemasWithSameDirectionCodeErrorAndLength() {
        PayShieldBodySchema schema1 = new PayShieldBodySchema(
                PayShieldBodySchema.Direction.RESPONSE, "ND", "00",
                PayShieldBodySchema.EvidenceStatus.PENDING_CAPTURE, "NC-00", null,
                List.of(new PayShieldBodySchema.Field("lmkCheckValue", "LMK check value", 16, PayShieldBodySchema.FieldType.HEX)));

        PayShieldBodySchema schema2 = new PayShieldBodySchema(
                PayShieldBodySchema.Direction.RESPONSE, "ND", "00",
                PayShieldBodySchema.EvidenceStatus.PENDING_CAPTURE, "NC-01", null,
                List.of(new PayShieldBodySchema.Field("otherValue", "Other value", 16, PayShieldBodySchema.FieldType.PRINTABLE_ASCII)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PayShieldBodySchemas.validate(List.of(schema1, schema2)));
        assertTrue(ex.getMessage().contains("ND:00:16"));
    }

    private static void assertDecomposesCapturedBody(PayShieldBodySchema schema) {
        byte[] capturedBody = schema.capturedBody();
        assertNotNull(capturedBody, schema.evidenceId());
        PayShieldBodyDecomposer.Decomposition decomposition =
                PayShieldBodyDecomposer.decompose(schema, capturedBody)
                        .orElseThrow(() -> new AssertionError(
                                "Schema " + schema.evidenceId() + " does not decompose its captured body"));
        assertEquals(schema, decomposition.schema());
        assertEquals(new String(capturedBody, StandardCharsets.US_ASCII),
                decomposition.fields().stream()
                        .map(PayShieldBodyDecomposer.DecodedField::value)
                        .collect(Collectors.joining()));
    }

    private static PayShieldResponse response(String data) {
        return new PayShieldResponse(
                "0000", "ND", "00", data.getBytes(StandardCharsets.US_ASCII), EMPTY);
    }
}
