package com.cryptocarver.model;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SavedSessionAadCharacterizationTest {
    static final String PASSWORD = "invented-password-42";
    static final String KEY = "CipherController.symmetricKeyField";
    static final String VALUE = "invented-value-42";
    private final SavedSessionCodec codec = new SavedSessionCodec();

    static SavedSession source() {
        SavedSession session = SavedSessionTrailStorageTest.source();
        session.setId("invented-session-42");
        session.setTimestamp("2026-01-01 00:00:00");
        session.setUiState(Map.of(KEY, VALUE, "format", "Hex"));
        return session;
    }

    @Test
    void correctPasswordRestoresSecretsAndCompleteTrail() {
        SavedSession original = source();
        SavedSession stored = codec.prepareForStorage(original, PASSWORD.toCharArray());
        SavedSession restored = codec.restore(stored, PASSWORD.toCharArray());
        assertEquals(JsonParser.parseString(codec.serialize(List.of(original))),
                JsonParser.parseString(codec.serialize(List.of(restored))));
    }

    @Test
    void incorrectPasswordFailsWithGenericAuthenticationMessage() {
        SavedSession stored = codec.prepareForStorage(source(), PASSWORD.toCharArray());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> codec.restore(stored, "invented-wrong-password-42".toCharArray()));
        assertEquals("Incorrect password or modified saved session", error.getMessage());
        assertFalse(error.getMessage().contains("invented-wrong-password-42"));
        assertFalse(error.getMessage().contains(PASSWORD));
        assertNull(error.getCause());
    }

    @Test
    void transplantedProtectedFieldsAreRejectedWithSessionBinding() {
        SavedSession donor = codec.prepareForStorage(source(), PASSWORD.toCharArray());
        SavedSession recipient = new SavedSession("Invented recipient", "Different operation",
                Map.of(KEY, "[REDACTED_SECRET]"));
        recipient.setProtectedFields(donor.getProtectedFields());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> codec.restore(recipient, PASSWORD.toCharArray()));
        assertEquals("Incorrect password or modified saved session", error.getMessage());
    }

    @Test
    void legacyCiphertextFixtureRestoresSecretsAndTrail() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("saved-session-no-aad.json")) {
            assertNotNull(input);
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        SavedSession restored = codec.restore(codec.deserialize(json).get(0), PASSWORD.toCharArray());
        assertEquals(VALUE, restored.getUiState().get(KEY));
        assertEquals("invented-session-42", restored.getId());
        assertEquals("Synthetic", restored.getOperation());
        assertEquals(1, restored.getOperationLog().size());
        assertEquals("invented-input-41", restored.getOperationLog().getSteps().get(0).getInputText());
        assertTrue(restored.getOperationLog().verifyChain());
    }
}
