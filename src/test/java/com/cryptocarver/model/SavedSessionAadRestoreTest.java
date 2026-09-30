package com.cryptocarver.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SavedSessionAadRestoreTest {
    private final SavedSessionCodec codec = new SavedSessionCodec();
    private static final String PASSWORD = SavedSessionAadCharacterizationTest.PASSWORD;
    private static final String KEY = SavedSessionAadCharacterizationTest.KEY;
    private static final String MARKER = "[REDACTED_SECRET]";

    private SavedSession stored() {
        return codec.prepareForStorage(SavedSessionAadCharacterizationTest.source(), PASSWORD.toCharArray());
    }

    private SavedSession altered(Consumer<JsonObject> change) {
        var json = JsonParser.parseString(codec.serialize(List.of(stored()))).getAsJsonArray();
        change.accept(json.get(0).getAsJsonObject());
        return codec.deserialize(json.toString()).get(0);
    }

    private void assertAuthenticationFailure(SavedSession session) {
        String before = codec.serialize(List.of(session));
        char[] password = PASSWORD.toCharArray();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> codec.restore(session, password));
        assertEquals("Incorrect password or modified saved session", error.getMessage());
        assertFalse(error.getMessage().contains(PASSWORD));
        assertFalse(error.getMessage().contains(KEY));
        assertFalse(error.getMessage().contains(SavedSessionAadCharacterizationTest.VALUE));
        assertNull(error.getCause());
        assertArrayEquals(new char[password.length], password);
        assertEquals(before, codec.serialize(List.of(session)));
    }

    @Test
    void changingIdFailsAuthentication() {
        SavedSession stored = stored();
        stored.setId("different-invented-id");
        assertAuthenticationFailure(stored);
    }

    @Test
    void changingOperationFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> json.addProperty("operation", "Different operation")));
    }

    @Test
    void removingProtectedKeyFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("uiState").remove(KEY)));
    }

    @Test
    void addingProtectedKeyFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("uiState").addProperty("another.keyField", MARKER)));
    }

    @Test
    void renamingProtectedKeyFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> {
            json.getAsJsonObject("uiState").remove(KEY);
            json.getAsJsonObject("uiState").addProperty("another.keyField", MARKER);
        }));
    }

    @Test
    void replacingProtectedMarkerFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("uiState").addProperty(KEY, "Invented replacement")));
    }

    @Test
    void downgradingAadVersionToZeroFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("protectedFields").addProperty("aadVersion", 0)));
    }

    @Test
    void removingAadVersionFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("protectedFields").remove("aadVersion")));
    }

    @Test
    void unknownAadVersionIsRejectedWithoutSessionData() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("protectedFields").addProperty("aadVersion", 42)));
    }

    @Test
    void negativeAadVersionIsRejectedWithoutSessionData() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("protectedFields").addProperty("aadVersion", -1)));
    }

    @Test
    void changingProtectedTrailFlagFailsAuthentication() {
        assertAuthenticationFailure(altered(json -> json.getAsJsonObject("protectedFields").addProperty("protectedTrail", false)));
    }

    @Test
    void nullIdRoundTripsAndCannotBecomeEmptyId() {
        SavedSession source = SavedSessionAadCharacterizationTest.source();
        source.setId(null);
        SavedSession stored = codec.prepareForStorage(source, PASSWORD.toCharArray());
        SavedSession parsed = codec.deserialize(codec.serialize(List.of(stored))).get(0);
        SavedSession restored = codec.restore(parsed, PASSWORD.toCharArray());
        assertNull(restored.getId());
        assertEquals(source.getUiState(), restored.getUiState());
        assertEquals(JsonParser.parseString(codec.serialize(List.of(source))),
                JsonParser.parseString(codec.serialize(List.of(restored))));
        parsed.setId("");
        assertAuthenticationFailure(parsed);
    }

    @Test
    void secretsWithoutTrailRoundTrip() {
        SavedSession source = new SavedSession("Invented", "Synthetic", Map.of(KEY, "invented-key-42"));
        SavedSession stored = codec.prepareForStorage(source, PASSWORD.toCharArray());
        assertFalse(stored.getProtectedFields().hasProtectedTrail());
        SavedSession restored = codec.restore(stored, PASSWORD.toCharArray());
        assertEquals(source.getUiState(), restored.getUiState());
        assertNull(restored.getOperationLog());
    }

    @Test
    void trailWithoutSecretsRoundTrips() {
        SavedSession source = SavedSessionTrailStorageTest.source();
        SavedSession stored = codec.prepareForStorage(source, PASSWORD.toCharArray());
        SavedSession restored = codec.restore(stored, PASSWORD.toCharArray());
        assertEquals(JsonParser.parseString(codec.serialize(List.of(source))),
                JsonParser.parseString(codec.serialize(List.of(restored))));
    }

    @Test
    void editableMetadataAndKeyOrderRemainCompatible() {
        SavedSession stored = altered(json -> {
            json.addProperty("name", "Renamed");
            json.addProperty("timestamp", "Changed");
            json.addProperty("version", 9);
            json.getAsJsonObject("uiState").addProperty("format", "Base64");
        });
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put(KEY, MARKER);
        reordered.put("format", "Base64");
        stored.setUiState(reordered);
        SavedSession restored = codec.restore(stored, PASSWORD.toCharArray());
        assertEquals("Renamed", restored.getName());
        assertEquals("Changed", restored.getTimestamp());
        assertEquals(9, restored.getVersion());
        assertEquals("Base64", restored.getUiState().get("format"));
        assertEquals(SavedSessionAadCharacterizationTest.VALUE, restored.getUiState().get(KEY));
    }

    @Test
    void legacyLoadDoesNotRewriteAndExplicitSaveUpgradesBinding(@TempDir Path directory) throws Exception {
        String fixture;
        try (var input = getClass().getResourceAsStream("saved-session-no-aad.json")) {
            assertNotNull(input);
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path file = directory.resolve("saved_sessions.json");
        Files.writeString(file, fixture);
        SavedSessionsManager manager = new SavedSessionsManager(file);
        SavedSession legacy = manager.getSessions().get(0);
        assertEquals(0, legacy.getProtectedFields().getAadVersion());
        SavedSession restored = codec.restore(legacy, PASSWORD.toCharArray());
        assertEquals(fixture, Files.readString(file));
        assertEquals(0, legacy.getProtectedFields().getAadVersion());
        SavedSession upgraded = codec.prepareForStorage(restored, PASSWORD.toCharArray());
        assertEquals(1, upgraded.getProtectedFields().getAadVersion());
        manager.addSession(upgraded);
        SavedSession reloaded = new SavedSessionsManager(file).getSessions().get(0);
        assertEquals(codec.serialize(List.of(restored)), codec.serialize(List.of(codec.restore(reloaded, PASSWORD.toCharArray()))));
    }
}
