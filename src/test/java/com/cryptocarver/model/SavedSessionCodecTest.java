package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SavedSessionCodecTest {
    private final SavedSessionCodec codec = new SavedSessionCodec();

    @Test
    void savedSessionRoundTripsWorkspaceAndOperationTrail() {
        OperationSessionLog trail = new OperationSessionLog();
        trail.add(OperationResult.forOperation("Hashing").input("abc".getBytes()).output(
                "digest".getBytes(), OperationDetail.Classification.PUBLIC).build(), "Hash", List.of("test"));
        SavedSession original = new SavedSession("Example", "Hashing", Map.of("format", "Hex"), trail);

        SavedSession restored = codec.deserialize(codec.serialize(List.of(original))).get(0);

        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getOperation(), restored.getOperation());
        assertEquals(original.getUiState(), restored.getUiState());
        assertEquals(trail.getSteps().get(0).getEntryHash(), restored.getOperationLog().getSteps().get(0).getEntryHash());
    }

    @Test
    void malformedJsonPropagatesLikeTheExistingPersistenceManager() {
        assertThrows(RuntimeException.class, () -> codec.deserialize("{broken"));
    }

    @Test
    void currentAndLegacyUnversionedDocumentsAreAccepted() {
        String legacy = "[{\"id\":\"legacy-id\",\"name\":\"Old\",\"timestamp\":\"2020-01-01 00:00:00\","
                + "\"operation\":\"Hashing\",\"uiState\":{\"format\":\"Hex\"}}]";
        SavedSession restored = codec.deserialize(legacy).get(0);
        assertEquals("Old", restored.getName());
        assertEquals("Hashing", restored.getOperation());
        assertEquals("Hex", restored.getUiState().get("format"));
    }

    @Test
    void serializationDoesNotAddOrStripExistingSecretFields() {
        String secret = "00112233445566778899AABBCCDDEEFF";
        String json = codec.serialize(List.of(new SavedSession("Lab", "Cipher",
                Map.of("CipherController.symmetricKeyField", secret))));
        assertTrue(json.contains(secret));
        assertEquals(secret, codec.deserialize(json).get(0).getUiState().get("CipherController.symmetricKeyField"));
    }
}
