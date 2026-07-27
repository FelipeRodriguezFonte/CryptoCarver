package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoryManagerRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void malformedHistoryIsPreservedAndDoesNotPreventNewOperations() throws Exception {
        Path history = tempDir.resolve("history.json");
        String malformed = "[{\"operation\":\"truncated\"";
        Files.writeString(history, malformed);

        HistoryManager manager = new HistoryManager(history);
        assertTrue(manager.getHistoryItems().isEmpty());
        assertFalse(Files.exists(history));

        List<Path> recoveryFiles;
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            recoveryFiles = files.filter(path -> path.getFileName().toString().startsWith("history.corrupt-"))
                    .toList();
        }
        assertEquals(1, recoveryFiles.size());
        assertEquals(malformed, Files.readString(recoveryFiles.get(0)));

        manager.addHistoryItem(new HistoryItem("Hashing", "{}", Map.of("algorithm", "SHA-256")));
        assertTrue(Files.exists(history));
        HistoryManager reloaded = new HistoryManager(history);
        assertEquals(1, reloaded.getHistoryItems().size());
        assertEquals("Hashing", reloaded.getHistoryItems().get(0).getOperation());
    }

    @Test
    void savedHistoryIsAlwaysValidJsonAndHonorsTheSizeLimit() throws Exception {
        Path history = tempDir.resolve("nested/history.json");
        HistoryManager manager = new HistoryManager(history);
        for (int index = 0; index < 55; index++) {
            manager.addHistoryItem(new HistoryItem("Operation " + index, "{}", Map.of()));
        }

        List<HistoryItem> parsed = new Gson().fromJson(Files.readString(history),
                new TypeToken<List<HistoryItem>>() { }.getType());
        assertEquals(50, parsed.size());
        assertEquals("Operation 54", parsed.get(0).getOperation());
        try (java.util.stream.Stream<Path> files = Files.list(history.getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
