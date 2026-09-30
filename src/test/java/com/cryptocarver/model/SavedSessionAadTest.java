package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SavedSessionAadTest {
    private static final String MARKER = "[REDACTED_SECRET]";

    private SavedSession session(String id, String operation, Map<String, Object> state) {
        SavedSession session = new SavedSession("Invented", operation, state);
        session.setId(id);
        return session;
    }

    @Test
    void keyOrderDoesNotAffectEncoding() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("é", MARKER);
        first.put("a", MARKER);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", MARKER);
        second.put("é", MARKER);
        assertArrayEquals(SavedSessionAad.encode(session("id", "op", first), true),
                SavedSessionAad.encode(session("id", "op", second), true));
    }

    @Test
    void lengthPrefixesPreventConcatenationAmbiguity() {
        assertFalse(java.util.Arrays.equals(SavedSessionAad.encode(session("ab", "c", Map.of()), false),
                SavedSessionAad.encode(session("a", "bc", Map.of()), false)));
    }

    @Test
    void nullIdentityIsDifferentFromEmptyIdentity() {
        assertFalse(java.util.Arrays.equals(SavedSessionAad.encode(session(null, "op", Map.of()), false),
                SavedSessionAad.encode(session("", "op", Map.of()), false)));
    }

    @Test
    void protectedTrailFlagChangesEncoding() {
        SavedSession source = session("id", "op", Map.of());
        assertFalse(java.util.Arrays.equals(SavedSessionAad.encode(source, true), SavedSessionAad.encode(source, false)));
    }

    @Test
    void editableMetadataAndSafeValuesDoNotAffectEncoding() {
        SavedSession first = session("id", "op", Map.of("key", MARKER, "format", "Hex"));
        SavedSession second = new SavedSession("Renamed", "op", Map.of("key", MARKER, "format", "Base64"));
        second.setId("id");
        second.setTimestamp("Changed");
        second.setVersion(7);
        assertArrayEquals(SavedSessionAad.encode(first, false), SavedSessionAad.encode(second, false));
    }

    @Test
    void nullStateAndNullKeyHaveUnambiguousEncoding() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(null, MARKER);
        assertFalse(java.util.Arrays.equals(SavedSessionAad.encode(session(null, null, null), false),
                SavedSessionAad.encode(session(null, null, state), false)));
    }
}
