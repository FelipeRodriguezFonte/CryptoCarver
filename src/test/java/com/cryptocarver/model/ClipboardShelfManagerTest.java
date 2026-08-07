package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardShelfManagerTest {

    @Test
    void testPersistenceAndSearch(@TempDir Path tempDir) {
        Path shelfPath = tempDir.resolve("shelf.json");
        ClipboardShelfManager manager = new ClipboardShelfManager(shelfPath);

        ClipboardEntry entry1 = new ClipboardEntry(
                "Entry 1", "Payload A", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.PUBLIC, "Hash", "SHA-256",
                List.of("tag1", "test"), "My first note", false
        );

        ClipboardEntry entry2 = new ClipboardEntry(
                "Entry 2", "Payload B", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.SECRET, "Symmetric Encryption", "AES-256-GCM",
                List.of("tag2", "prod"), "Second note", true
        );

        manager.addEntry(entry1);
        manager.addEntry(entry2);

        assertTrue(Files.exists(shelfPath), "Shelf file must be created on disk");

        // Verify Pinned ordering: Pinned entry2 appears first
        List<ClipboardEntry> all = manager.getEntries();
        assertEquals(2, all.size());
        assertEquals("Entry 2", all.get(0).getLabel(), "Pinned entry must appear first");

        // Search by Tag
        List<ClipboardEntry> searchTag = manager.search("prod", null, null, null, null);
        assertEquals(1, searchTag.size());
        assertEquals("Entry 2", searchTag.get(0).getLabel());

        // Search by Note
        List<ClipboardEntry> searchNote = manager.search("first", null, null, null, null);
        assertEquals(1, searchNote.size());
        assertEquals("Entry 1", searchNote.get(0).getLabel());

        // Test duplicate detection
        Optional<ClipboardEntry> dup = manager.findDuplicate("Payload A", "Hash");
        assertTrue(dup.isPresent());
        assertEquals("Entry 1", dup.get().getLabel());

        // Re-instantiate manager from disk to verify persistence loading
        ClipboardShelfManager loadedManager = new ClipboardShelfManager(shelfPath);
        List<ClipboardEntry> loaded = loadedManager.getEntries();
        assertEquals(2, loaded.size());
        assertEquals("Entry 2", loaded.get(0).getLabel());
        assertEquals("Second note", loaded.get(0).getNote());
    }

    @Test
    void testPinAndTagsMutation(@TempDir Path tempDir) {
        Path shelfPath = tempDir.resolve("shelf.json");
        ClipboardShelfManager manager = new ClipboardShelfManager(shelfPath);

        ClipboardEntry entry = new ClipboardEntry(
                "Item", "123456", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.PUBLIC, "Format"
        );
        manager.addEntry(entry);

        manager.togglePin(entry.getId());
        assertTrue(manager.getEntries().get(0).isPinned());

        manager.updateTagsAndNote(entry.getId(), List.of("tagX"), "Updated Note");
        assertEquals("Updated Note", manager.getEntries().get(0).getNote());
        assertEquals(1, manager.getEntries().get(0).getTags().size());
    }

    @Test
    void testLegacyEntriesAreNormalized(@TempDir Path tempDir) throws Exception {
        Path shelfPath = tempDir.resolve("shelf.json");
        Files.writeString(shelfPath, "[{\"label\":\"Legacy\",\"value\":\"Payload\",\"format\":\"TEXT\",\"classification\":\"PUBLIC\",\"sourceOperation\":\"Legacy\"}]");

        ClipboardShelfManager manager = new ClipboardShelfManager(shelfPath);
        ClipboardEntry migrated = manager.getEntries().get(0);

        assertEquals("Legacy", migrated.getLabel());
        assertNotNull(migrated.getId());
        assertNotNull(migrated.getCreatedAt());
        assertFalse(migrated.isPinned());
        assertTrue(Files.readString(shelfPath).contains("createdAt"));
    }

    @Test
    void shelfMutationsDoNotCreateRecentOperationEvents(@TempDir Path tempDir) {
        ClipboardShelfManager manager = new ClipboardShelfManager(tempDir.resolve("shelf.json"));
        AtomicInteger published = new AtomicInteger();
        manager.setReporter(new com.cryptocarver.ui.StatusReporter() {
            @Override public void updateStatus(String message) { }
            @Override public void updateInspector(String operation, byte[] input, byte[] output,
                                                   List<OperationDetail> details) { }
            @Override public void showError(String title, String message) { }
            @Override public void publish(OperationResult result) { published.incrementAndGet(); }
        });

        manager.addEntry(new ClipboardEntry("Notebook item", "payload", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.PUBLIC, "Hash"));
        assertEquals(0, published.get(), "Shelf mutations must not become execution history entries");
    }
}
