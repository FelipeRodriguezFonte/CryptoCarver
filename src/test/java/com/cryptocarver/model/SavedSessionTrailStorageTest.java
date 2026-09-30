package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SavedSessionTrailStorageTest {
    private final SavedSessionCodec codec = new SavedSessionCodec();
    private static final List<String> SYNTHETIC_VALUES = List.of(
            "invented-input-41", "invented-parameter-41", "invented-detail-41",
            "invented-output-41", "invented-enriched-41", "invented-sensitive-detail-41");

    @Test
    void passwordlessStorageCurrentlyDiscardsTheTrail() {
        SavedSession stored = codec.prepareForStorage(source(), null);
        assertNull(stored.getOperationLog());
        assertNull(codec.restore(stored, null).getOperationLog());
    }

    @Test
    void passwordProtectedTrailRoundTripsEveryField() {
        SavedSession original = source();
        SavedSession stored = codec.prepareForStorage(original, "invented-password-41".toCharArray());
        assertNull(stored.getOperationLog());
        assertNotNull(stored.getProtectedFields());
        SavedSession parsed = codec.deserialize(codec.serialize(List.of(stored))).get(0);
        SavedSession restored = codec.restore(parsed, "invented-password-41".toCharArray());
        assertEquals(codec.serialize(List.of(original)), codec.serialize(List.of(restored)));
    }

    @Test
    void versionOneSessionWithoutAnOperationLogStillLoads() {
        SavedSession stored = codec.deserialize("[{\"name\":\"Old\",\"operation\":\"Synthetic\","
                + "\"version\":1,\"uiState\":{\"format\":\"Hex\"}}]").get(0);
        SavedSession restored = codec.restore(stored, null);
        assertNull(restored.getOperationLog());
        assertEquals("Hex", restored.getUiState().get("format"));
    }

    @Test
    void passwordlessJsonContainsNoSyntheticSensitiveValuesOrTheirHexEncoding() {
        String json = codec.serialize(List.of(codec.prepareForStorage(source(), null)));
        for (String value : SYNTHETIC_VALUES) {
            assertFalse(json.contains(value), "Synthetic sensitive value leaked");
            assertFalse(json.contains(java.util.HexFormat.of().withUpperCase()
                    .formatHex(value.getBytes(StandardCharsets.UTF_8))), "Synthetic sensitive bytes leaked");
        }
    }

    static SavedSession source() {
        OperationSessionLog trail = new OperationSessionLog();
        trail.add(OperationResult.forOperation("Synthetic")
                .input(SYNTHETIC_VALUES.get(0).getBytes(StandardCharsets.UTF_8))
                .output(SYNTHETIC_VALUES.get(3).getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET)
                .enrichedOutput(SYNTHETIC_VALUES.get(4), OperationDetail.Classification.SENSITIVE)
                .detail(OperationDetail.secretDetail("Secret", SYNTHETIC_VALUES.get(2)))
                .detail(OperationDetail.sensitiveDetail("Sensitive", SYNTHETIC_VALUES.get(5)))
                .detail(OperationDetail.publicDetail("Public", "public-detail-41"))
                .status("Complete").build(), "Synthetic step", List.of("synthetic"),
                Map.of("parameter", SYNTHETIC_VALUES.get(1)));
        return new SavedSession("Synthetic session", "Synthetic", Map.of("format", "Hex"), trail);
    }
}
