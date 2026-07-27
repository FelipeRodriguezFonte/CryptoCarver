package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(HistoryManager.class);
    private static final String HISTORY_FILE = "history.json";
    private static final int MAX_HISTORY_SIZE = 50;
    private final Gson gson;
    private final Path historyPath;
    private final List<HistoryItem> historyItems;

    public HistoryManager() {
        this(resolveDefaultPath());
    }

    /** Explicit path constructor for isolated tools and tests. */
    public HistoryManager(Path historyPath) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.historyPath = historyPath.toAbsolutePath().normalize();
        ensureParentDirectory(this.historyPath);
        this.historyItems = loadHistory();
    }

    public List<HistoryItem> getHistoryItems() {
        return new ArrayList<>(historyItems);
    }

    public void addHistoryItem(HistoryItem item) {
        historyItems.add(0, item);
        if (historyItems.size() > MAX_HISTORY_SIZE) {
            historyItems.remove(historyItems.size() - 1);
        }
        saveHistory();
    }

    public void clearHistory() {
        historyItems.clear();
        saveHistory();
    }

    private List<HistoryItem> loadHistory() {
        if (!Files.exists(historyPath)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(historyPath)) {
            List<HistoryItem> loaded = gson.fromJson(reader, new TypeToken<List<HistoryItem>>() {
            }.getType());
            return loaded != null ? loaded : new ArrayList<>();
        } catch (IOException | RuntimeException e) {
            LOG.warn("Unable to load history from {}; preserving the unreadable file: {}", historyPath, e.getMessage());
            LOG.debug("History parse failure", e);
            preserveCorruptHistory();
            return new ArrayList<>();
        }
    }

    private void saveHistory() {
        ensureParentDirectory(historyPath);
        Path parent = historyPath.getParent();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent, ".history-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                gson.toJson(historyItems, writer);
            }
            try {
                Files.move(temporary, historyPath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, historyPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Unable to save history atomically to {}", historyPath, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best effort cleanup; the history file itself is already safe.
                }
            }
        }
    }

    private void preserveCorruptHistory() {
        if (!Files.exists(historyPath)) return;
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
        Path recovery = historyPath.resolveSibling("history.corrupt-" + timestamp + ".json");
        try {
            Files.move(historyPath, recovery, StandardCopyOption.REPLACE_EXISTING);
            LOG.warn("Unreadable history preserved at {}", recovery);
        } catch (IOException moveError) {
            LOG.error("Unable to preserve corrupt history {}", historyPath, moveError);
        }
    }

    private static Path resolveDefaultPath() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) userHome = System.getProperty("java.io.tmpdir");
        Path modernDirectory = Paths.get(userHome, ".cryptocarver");
        Path modernHistory = modernDirectory.resolve(HISTORY_FILE);
        ensureParentDirectory(modernHistory);
        if (!Files.exists(modernHistory)) {
            migrateLegacyHistory(modernHistory,
                    Paths.get(userHome, ".cryptocalc", HISTORY_FILE),
                    Paths.get(userHome, ".crypto-calculator", HISTORY_FILE));
        }
        return modernHistory;
    }

    private static void migrateLegacyHistory(Path destination, Path... legacyCandidates) {
        for (Path legacy : legacyCandidates) {
            if (!Files.exists(legacy)) continue;
            try {
                Files.copy(legacy, destination);
                LOG.info("Migrated operation history from {} to {}", legacy, destination);
                return;
            } catch (IOException e) {
                LOG.warn("Unable to migrate operation history from {}", legacy, e);
            }
        }
    }

    private static void ensureParentDirectory(Path file) {
        Path parent = file == null ? null : file.getParent();
        if (parent == null) return;
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            LOG.warn("Unable to create history directory {}", parent, e);
        }
    }
}
