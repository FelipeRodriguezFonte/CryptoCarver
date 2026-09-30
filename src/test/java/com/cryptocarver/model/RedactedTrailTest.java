package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RedactedTrailTest {
    @Test
    void removesNonPublicPayloadsParametersLengthsAndFingerprints() {
        OperationSessionLog original = SavedSessionTrailStorageTest.source().getOperationLog();
        SessionOperationStep safe = RedactedTrail.from(original).getSteps().get(0);
        assertTrue(safe.isInputPresent());
        assertTrue(safe.isOutputPresent());
        assertNull(safe.getInputText());
        assertNull(safe.getInputHex());
        assertNull(safe.getInputFingerprint());
        assertEquals(0, safe.getInputLength());
        assertNull(safe.getOutputText());
        assertNull(safe.getOutputHex());
        assertNull(safe.getOutputFingerprint());
        assertEquals(0, safe.getOutputLength());
        assertNull(safe.getEnrichedOutput());
        assertNull(safe.getEnrichedOutputFingerprint());
        assertTrue(safe.getParameters().isEmpty());
        assertEquals(List.of(OperationDetail.publicDetail("Public", "public-detail-41")), safe.getDetails());
        assertNotNull(original.getSteps().get(0).getInputText());
    }

    @Test
    void preservesMetadataPublicOutputsAndRebuildsAnIdempotentChain() {
        OperationSessionLog original = SavedSessionTrailStorageTest.source().getOperationLog();
        original.add(OperationResult.forOperation("Public operation")
                .output("public-output-41".getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.PUBLIC)
                .enrichedOutput("public-enriched-41", OperationDetail.Classification.PUBLIC)
                .status("Complete").build(), "User title", List.of("User tag"), Map.of());
        OperationSessionLog safe = RedactedTrail.from(original);
        SessionOperationStep before = original.getSteps().get(1);
        SessionOperationStep after = safe.getSteps().get(1);
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getTimestamp(), after.getTimestamp());
        assertEquals(before.getTitle(), after.getTitle());
        assertEquals(before.getTags(), after.getTags());
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getOperation(), after.getOperation());
        assertEquals(before.getOutputText(), after.getOutputText());
        assertEquals(before.getOutputHex(), after.getOutputHex());
        assertEquals(before.getEnrichedOutput(), after.getEnrichedOutput());
        assertTrue(safe.verifyChain());
        assertTrue(original.verifyChain());
        assertNotEquals(original.getSteps().get(0).getEntryHash(), safe.getSteps().get(0).getEntryHash());
        com.google.gson.Gson gson = new com.google.gson.Gson();
        assertEquals(gson.toJson(safe), gson.toJson(RedactedTrail.from(safe)));
    }

    @Test
    void emptyAndAbsentTrailsRemainEmptyAndAbsent() {
        assertNull(RedactedTrail.from(null));
        assertTrue(RedactedTrail.from(new OperationSessionLog()).isEmpty());
    }
}
