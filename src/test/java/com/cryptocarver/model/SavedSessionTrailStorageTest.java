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
    void passwordlessStorageRestoresAPublicOnlyRedactedTrail() {
        SavedSession stored = codec.prepareForStorage(source(), null);
        assertTrue(stored.isTrailRedacted());
        SavedSession restored = codec.restore(codec.deserialize(codec.serialize(List.of(stored))).get(0), null);
        assertTrue(restored.isTrailRedacted());
        assertEquals(1, restored.getOperationLog().size());
        assertEquals("Synthetic step", restored.getOperationLog().getSteps().get(0).getTitle());
        assertTrue(restored.getOperationLog().verifyChain());
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
        assertFalse(restored.isTrailRedacted());
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

    @Test
    void absentAndEmptyTrailsDoNotAddRedactedLogs() {
        SavedSession absent = codec.prepareForStorage(new SavedSession("Empty", "Synthetic", Map.of()), null);
        SavedSession empty = codec.prepareForStorage(new SavedSession("Empty", "Synthetic", Map.of(),
                new OperationSessionLog()), null);
        assertNull(absent.getOperationLog());
        assertNull(empty.getOperationLog());
        assertFalse(absent.isTrailRedacted());
        assertFalse(empty.isTrailRedacted());
    }

    @Test
    void preparingAnAlreadyRedactedSessionPreservesItsLogAndMarker() {
        SavedSession once = codec.prepareForStorage(source(), null);
        SavedSession twice = codec.prepareForStorage(once, null);
        assertEquals(codec.serialize(List.of(once)), codec.serialize(List.of(twice)));
        assertEquals(1, twice.getVersion());
    }

    @Test
    void explicitNullLegacyLogRemainsAbsentAndUnredacted() {
        SavedSession stored = codec.deserialize("[{\"name\":\"Old\",\"version\":1,\"operationLog\":null}]").get(0);
        assertNull(codec.restore(stored, null).getOperationLog());
        assertFalse(stored.isTrailRedacted());
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
