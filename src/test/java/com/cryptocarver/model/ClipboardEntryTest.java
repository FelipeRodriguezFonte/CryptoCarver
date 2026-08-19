package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardEntryTest {

    @Test
    void testDefaultsAndSanitization() {
        ClipboardEntry entry = new ClipboardEntry(
                "My Label",
                "00112233",
                ClipboardEntry.Format.HEX,
                OperationDetail.Classification.SECRET,
                "Symmetric Encryption"
        );

        assertEquals("My Label", entry.getLabel());
        assertEquals("00112233", entry.getValue());
        assertEquals(ClipboardEntry.Format.HEX, entry.getFormat());
        assertEquals(OperationDetail.Classification.SECRET, entry.getClassification());
        assertEquals("Symmetric Encryption", entry.getSourceOperation());
        assertNull(entry.getAlgorithm());
        assertTrue(entry.getTags().isEmpty());
        assertEquals("", entry.getNote());
        assertFalse(entry.isPinned());
    }

    @Test
    void testTagLimitEnforced() {
        List<String> rawTags = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            rawTags.add("tag" + i);
        }

        List<String> cleanTags = ClipboardEntry.sanitizeTags(rawTags);
        assertEquals(12, cleanTags.size(), "Tags list must be capped at 12");
        assertEquals("tag1", cleanTags.get(0));
        assertEquals("tag12", cleanTags.get(11));
    }

    @Test
    void testNoteLengthLimitEnforced() {
        String longNote = "A".repeat(1500);
        String cleanNote = ClipboardEntry.sanitizeNote(longNote);
        assertEquals(1000, cleanNote.length(), "Note length must be capped at 1000 characters");
    }

    @Test
    void testMutators() {
        ClipboardEntry entry = new ClipboardEntry(
                "Initial",
                "Test Payload",
                ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.PUBLIC,
                "Hash"
        );

        ClipboardEntry pinned = entry.withPinned(true);
        assertTrue(pinned.isPinned());
        assertEquals(entry.getId(), pinned.getId());

        ClipboardEntry updated = pinned.withTagsAndNote(List.of("tagA", "tagB"), "Laboratory note");
        assertEquals(2, updated.getTags().size());
        assertEquals("Laboratory note", updated.getNote());
        assertTrue(updated.isPinned());
    }

    @Test
    void testBase64AndBase64UrlByteLengthsUseDecodedBytes() {
        ClipboardEntry base64 = new ClipboardEntry("Base64", "AQ==", ClipboardEntry.Format.BASE64,
                OperationDetail.Classification.PUBLIC, "Test");
        ClipboardEntry base64Url = new ClipboardEntry("Base64URL", "AQ", ClipboardEntry.Format.BASE64URL,
                OperationDetail.Classification.PUBLIC, "Test");

        assertEquals(1, base64.getByteLength());
        assertEquals(1, base64Url.getByteLength());
    }
}
