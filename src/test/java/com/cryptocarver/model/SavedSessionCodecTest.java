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
    void defaultStorageRedactsSensitiveUiFields() {
        String secret = "00112233445566778899AABBCCDDEEFF";
        String json = codec.serialize(List.of(codec.prepareForStorage(new SavedSession("Lab", "Cipher",
                Map.of("CipherController.symmetricKeyField", secret, "CipherController.modeCombo", "GCM")), null)));
        assertFalse(json.contains(secret));
        SavedSession loaded = codec.deserialize(json).get(0);
        assertEquals("[REDACTED_SECRET]", loaded.getUiState().get("CipherController.symmetricKeyField"));
        assertEquals("GCM", loaded.getUiState().get("CipherController.modeCombo"));
    }

    @Test
    void storesSelectorAndRedactsOnlyTheKey() {
        String key = "invented-test-key-1234567890";
        SavedSession saved = codec.prepareForStorage(new SavedSession("Lab", "Cipher",
                Map.of("CipherController.symKeySourceCombo", "Manual Input",
                        "CipherController.symmetricKeyField", key)), null);
        SavedSession loaded = codec.deserialize(codec.serialize(List.of(saved))).get(0);
        assertEquals("Manual Input", loaded.getUiState().get("CipherController.symKeySourceCombo"));
        assertEquals("[REDACTED_SECRET]", loaded.getUiState().get("CipherController.symmetricKeyField"));
        assertFalse(codec.serialize(List.of(saved)).contains(key));
    }

    @Test
    void encryptedSecretsRoundTripAndRejectWrongPasswordOrTampering() {
        String secret = "invented-test-key-987654321";
        OperationSessionLog trail = new OperationSessionLog();
        trail.add(OperationResult.forOperation("Cipher").input(secret.getBytes()).build(), "Step", List.of());
        SavedSession stored = codec.prepareForStorage(new SavedSession("Lab", "Cipher",
                Map.of("CipherController.symmetricKeyField", secret, "CipherController.modeCombo", "GCM"), trail),
                "correct horse".toCharArray());
        String json = codec.serialize(List.of(stored));
        assertFalse(json.contains(secret));
        SavedSession parsed = codec.deserialize(json).get(0);
        SavedSession restored = codec.restore(parsed, "correct horse".toCharArray());
        assertEquals(secret, restored.getUiState().get("CipherController.symmetricKeyField"));
        assertEquals(1, restored.getOperationLog().size());
        assertThrows(IllegalArgumentException.class, () -> codec.restore(parsed, "wrong horse".toCharArray()));
        int at = json.indexOf("ciphertext");
        int valueAt = json.indexOf('"', at + 12) + 1;
        char original = json.charAt(valueAt);
        String altered = json.substring(0, valueAt) + (original == 'A' ? 'B' : 'A') + json.substring(valueAt + 1);
        SavedSession tampered = codec.deserialize(altered).get(0);
        assertThrows(IllegalArgumentException.class, () -> codec.restore(tampered, "correct horse".toCharArray()));
        assertEquals("[REDACTED_SECRET]", parsed.getUiState().get("CipherController.symmetricKeyField"));
    }

    @Test
    void legacyPlaintextSessionCanBeSanitized() {
        String secret = "invented-legacy-secret";
        String legacy = "[{\"name\":\"Old\",\"operation\":\"Cipher\",\"uiState\":{" +
                "\"CipherController.passwordField\":\"" + secret + "\"}}]";
        SavedSession old = codec.deserialize(legacy).get(0);
        assertEquals(secret, old.getUiState().get("CipherController.passwordField"));
        String clean = codec.serialize(codec.redactLegacyPlaintext(List.of(old)));
        assertFalse(clean.contains(secret));
        assertTrue(clean.contains("[REDACTED_SECRET]"));
    }

    @Test
    void passwordArraysAreClearedAfterEncryptionAndFailedDecryption() {
        char[] encryptionPassword = "correct horse".toCharArray();
        SavedSession encrypted = codec.prepareForStorage(new SavedSession("Lab", "Cipher",
                Map.of("CipherController.keyField", "invented-key")), encryptionPassword);
        assertTrue(java.util.Arrays.equals(new char[encryptionPassword.length], encryptionPassword));
        char[] wrongPassword = "wrong horse".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> codec.restore(encrypted, wrongPassword));
        assertTrue(java.util.Arrays.equals(new char[wrongPassword.length], wrongPassword));
    }
}
