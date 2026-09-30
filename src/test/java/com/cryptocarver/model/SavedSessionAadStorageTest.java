package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SavedSessionAadStorageTest {
    private final SavedSessionCodec codec = new SavedSessionCodec();

    @Test
    void newStorageUsesVersionOneAndAuthenticatedTrailPresence() throws Exception {
        SavedSession stored = codec.prepareForStorage(SavedSessionAadCharacterizationTest.source(),
                SavedSessionAadCharacterizationTest.PASSWORD.toCharArray());
        SavedSession parsed = codec.deserialize(codec.serialize(List.of(stored))).get(0);
        SavedSession.ProtectedFields fields = parsed.getProtectedFields();
        assertEquals(1, fields.getAadVersion());
        assertTrue(fields.hasProtectedTrail());
        byte[] plain = PasswordFieldCipher.decrypt(SavedSessionAadCharacterizationTest.PASSWORD.toCharArray(),
                Base64.getDecoder().decode(fields.getSalt()), Base64.getDecoder().decode(fields.getNonce()),
                Base64.getDecoder().decode(fields.getCiphertext()), fields.getIterations(), SavedSessionAad.encode(parsed, true));
        try {
            String payload = new String(plain, StandardCharsets.UTF_8);
            assertTrue(payload.contains(SavedSessionAadCharacterizationTest.VALUE));
            assertTrue(payload.contains("operationLog"));
        } finally {
            java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    @Test
    void existingConstructorKeepsLegacyVersionAndAbsentTrailFlag() {
        SavedSession.ProtectedFields fields = new SavedSession.ProtectedFields("Invented", 0, "", "", "");
        assertEquals(0, fields.getAadVersion());
        assertFalse(fields.hasProtectedTrail());
    }
}
