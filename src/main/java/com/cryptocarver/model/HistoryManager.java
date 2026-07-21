package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {

    private static final String HISTORY_FILE = "history.json";
    private static final int MAX_HISTORY_SIZE = 50;
    private final Gson gson;
    private final Path historyPath;
    private final List<HistoryItem> historyItems;

    public HistoryManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        // Save in user home directory under .cryptocalc
        String userHome = System.getProperty("user.home");
        Path configDir = Paths.get(userHome, ".cryptocalc");
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                System.err.println("Could not create config directory: " + e.getMessage());
            }
        }
        this.historyPath = configDir.resolve(HISTORY_FILE);
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
        } catch (IOException e) {
            System.err.println("Error loading history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveHistory() {
        try (Writer writer = Files.newBufferedWriter(historyPath)) {
            gson.toJson(historyItems, writer);
        } catch (IOException e) {
            System.err.println("Error saving history: " + e.getMessage());
        }
    }
}
