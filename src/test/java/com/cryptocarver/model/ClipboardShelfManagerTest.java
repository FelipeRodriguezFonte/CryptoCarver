package com.cryptocarver.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardShelfManagerTest {

    private ClipboardShelfManager manager;

    @BeforeEach
    void setUp() {
        manager = ClipboardShelfManager.getInstance();
        manager.clear();
    }

    @Test
    void testSingletonInstance() {
        ClipboardShelfManager instance1 = ClipboardShelfManager.getInstance();
        ClipboardShelfManager instance2 = ClipboardShelfManager.getInstance();
        assertSame(instance1, instance2, "Instances should be the exact same object");
    }

    @Test
    void testAddEntry() {
        ClipboardEntry entry = new ClipboardEntry("Test", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.PUBLIC, "Source");
        manager.addEntry(entry);

        assertEquals(1, manager.getEntries().size());
        assertEquals("Test", manager.getEntries().get(0).getLabel());
    }

    @Test
    void testFifoLimit() {
        // Add 105 entries
        for (int i = 0; i < 105; i++) {
            ClipboardEntry entry = new ClipboardEntry("Test " + i, "Value " + i, ClipboardEntry.Format.TEXT, OperationDetail.Classification.PUBLIC, "Source");
            manager.addEntry(entry);
        }

        assertEquals(100, manager.getEntries().size(), "Manager should not exceed 100 entries");

        // The first 5 entries (0 to 4) should have been evicted.
        // The newest entry (104) is at index 0, and the oldest kept (5) is at index 99.
        assertEquals("Test 104", manager.getEntries().get(0).getLabel());
        assertEquals("Test 5", manager.getEntries().get(99).getLabel());
    }

    @Test
    void testRenameEntry() {
        ClipboardEntry entry = new ClipboardEntry("Test", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.PUBLIC, "Source");
        manager.addEntry(entry);

        boolean renamed = manager.renameEntry(entry.getId(), "New Label");
        assertTrue(renamed);
        assertEquals("New Label", manager.getEntries().get(0).getLabel());
    }

    @Test
    void testRemoveEntry() {
        ClipboardEntry entry = new ClipboardEntry("Test", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.PUBLIC, "Source");
        manager.addEntry(entry);
        assertEquals(1, manager.getEntries().size());

        manager.removeEntry(entry.getId());
        assertEquals(0, manager.getEntries().size());
    }

    @Test
    void testInferFormat() {
        assertEquals(ClipboardEntry.Format.UNKNOWN, ClipboardEntry.Format.inferFormat(""));
        assertEquals(ClipboardEntry.Format.UNKNOWN, ClipboardEntry.Format.inferFormat("   "));
        assertEquals(ClipboardEntry.Format.PEM, ClipboardEntry.Format.inferFormat("-----BEGIN CERTIFICATE-----\nMIIB... \n-----END CERTIFICATE-----"));

        // Strict JSON tests (Gson parser should accept these)
        assertEquals(ClipboardEntry.Format.JSON, ClipboardEntry.Format.inferFormat("{\"key\":\"value\"}"));
        assertEquals(ClipboardEntry.Format.JSON, ClipboardEntry.Format.inferFormat("[1, 2, 3]"));

        // HEX tests
        assertEquals(ClipboardEntry.Format.HEX, ClipboardEntry.Format.inferFormat("0A1B2C"));
        assertEquals(ClipboardEntry.Format.HEX, ClipboardEntry.Format.inferFormat("0a 1b 2c"));

        // BASE64 tests
        assertEquals(ClipboardEntry.Format.BASE64, ClipboardEntry.Format.inferFormat("YWJjZGU=")); // "abcde"
        assertEquals(ClipboardEntry.Format.BASE64, ClipboardEntry.Format.inferFormat("YWJjZGVm")); // "abcdef" (could also be base64url, but base64 matches first)

        // BASE64URL tests (no padding, contains - or _)
        assertEquals(ClipboardEntry.Format.BASE64URL, ClipboardEntry.Format.inferFormat("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9_--_"));
        assertEquals(ClipboardEntry.Format.BASE64URL, ClipboardEntry.Format.inferFormat("a-b_cdefghijklmnop"));

        assertEquals(ClipboardEntry.Format.TEXT, ClipboardEntry.Format.inferFormat("Just some plain text that is not hex or b64"));
    }

    @Test
    void testImmutabilityOnRename() {
        ClipboardEntry original = new ClipboardEntry("Original", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.PUBLIC, "Source");
        ClipboardEntry renamed = original.withLabel("Renamed");

        assertNotSame(original, renamed, "Rename should create a new instance");
        assertEquals("Original", original.getLabel(), "Original should not be mutated");
        assertEquals("Renamed", renamed.getLabel(), "New instance should have new label");
        assertEquals(original.getId(), renamed.getId(), "ID should remain the same");
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int entriesPerThread = 50;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < entriesPerThread; j++) {
                    ClipboardEntry entry = new ClipboardEntry("T" + threadId + "-" + j, "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.PUBLIC, "Source");
                    manager.addEntry(entry);
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        // Should not exceed max capacity
        assertEquals(100, manager.getEntries().size());
    }

    @Test
    void testChangeListenerReceivesShelfMutationsAndCanBeRemoved() {
        java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        manager.addChangeListener(listener);

        ClipboardEntry entry = new ClipboardEntry("Live", "Value", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.PUBLIC, "Source");
        manager.addEntry(entry);
        manager.renameEntry(entry.getId(), "Renamed");
        manager.removeEntry(entry.getId());
        assertEquals(3, changes.get());

        manager.removeChangeListener(listener);
        manager.addEntry(entry);
        assertEquals(3, changes.get());
    }

    @Test
    void testReportingDoesNotLeakValue() {
        final java.util.List<OperationResult> publishedResults = new java.util.ArrayList<>();
        manager.setReporter(new com.cryptocarver.ui.StatusReporter() {
            @Override
            public void updateStatus(String message) {}
            @Override
            public void updateInspector(String operation, byte[] input, byte[] output, java.util.List<com.cryptocarver.model.OperationDetail> details) {}
            @Override
            public void showError(String title, String message) {}
            @Override
            public void publish(OperationResult result) {
                publishedResults.add(result);
            }
        });

        ClipboardEntry entry = new ClipboardEntry("MyLabel", "SUPER_SECRET_VALUE", ClipboardEntry.Format.TEXT, OperationDetail.Classification.SECRET, "Source");
        manager.addEntry(entry);
        manager.renameEntry(entry.getId(), "New Label");
        manager.removeEntry(entry.getId());
        manager.addEntry(entry);
        manager.clear();

        assertEquals(5, publishedResults.size());

        for (OperationResult res : publishedResults) {
            assertNull(res.getInput(), "Input should be null");
            assertNull(res.getOutput(), "Output should be null");
            assertTrue(res.getDetails() == null || res.getDetails().isEmpty(), "Details should be empty");
            assertFalse(res.getStatusMessage().contains("SUPER_SECRET_VALUE"), "Status message must not leak value");
        }
    }
}
