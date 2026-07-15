package com.cryptoforge.utils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Manages operation history for CryptoCarver.
 */
public class OperationHistory {

    private static final OperationHistory instance = new OperationHistory();
    private final List<OperationEntry> history = new ArrayList<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private int maxEntries = 1000;
    private Path historyFilePath;

    private OperationHistory() {
        try {
            // Get user home directory safely
            String userHome = System.getProperty("user.home");
            if (userHome == null || userHome.isEmpty()) {
                userHome = System.getProperty("java.io.tmpdir");
            }

            Path configDir = Paths.get(userHome, ".cryptocarver");
            historyFilePath = configDir.resolve("history.json");
            Path legacyFile = Paths.get(userHome, ".crypto-calculator", "history.json");
            if (!Files.exists(historyFilePath) && Files.exists(legacyFile)) {
                Files.createDirectories(configDir);
                Files.copy(legacyFile, historyFilePath);
            }

            loadHistory();
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize history file: " + e.getMessage());
            // Continue without persistence
        }
    }

    public static OperationHistory getInstance() {
        return instance;
    }

    /**
     * Add a new operation to history
     */
    public void addOperation(String category, String operation, String input, String output) {
        OperationEntry entry = new OperationEntry(
                LocalDateTime.now(),
                category,
                operation,
                input,
                output);

        history.add(0, entry); // Add to beginning

        // Limit history size
        if (history.size() > maxEntries) {
            history.remove(history.size() - 1);
        }

        // Auto-save
        saveHistory();
    }

    /**
     * Get all history entries
     */
    public List<OperationEntry> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Get filtered history by category
     */
    public List<OperationEntry> getHistory(String category) {
        if (category == null || category.equals("All")) {
            return getHistory();
        }
        return history.stream()
                .filter(e -> e.getCategory().equals(category))
                .toList();
    }

    /**
     * Search history by text
     */
    public List<OperationEntry> searchHistory(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return getHistory();
        }

        String search = searchText.toLowerCase();
        return history.stream()
                .filter(e -> e.getCategory().toLowerCase().contains(search) ||
                        e.getOperation().toLowerCase().contains(search) ||
                        e.getInput().toLowerCase().contains(search) ||
                        e.getOutput().toLowerCase().contains(search))
                .toList();
    }

    /**
     * Clear all history
     */
    public void clearHistory() {
        history.clear();
        saveHistory();
    }

    /**
     * Save history to file (JSON)
     */
    public void saveHistory() {
        if (historyFilePath == null) {
            return; // Persistence disabled
        }

        try {
            Files.createDirectories(historyFilePath.getParent());

            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .create();

            String json = gson.toJson(history);
            Files.writeString(historyFilePath, json);

        } catch (IOException e) {
            System.err.println("Warning: Error saving history: " + e.getMessage());
        }
    }

    /**
     * Load history from file
     */
    public void loadHistory() {
        if (historyFilePath == null) {
            return; // Persistence disabled
        }

        try {
            if (!Files.exists(historyFilePath)) {
                return; // No history file yet
            }

            String json = Files.readString(historyFilePath);

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .create();

            TypeToken<List<OperationEntry>> token = new TypeToken<>() {
            };
            List<OperationEntry> loaded = gson.fromJson(json, token.getType());

            if (loaded != null) {
                history.clear();
                history.addAll(loaded);
            }

        } catch (IOException e) {
            System.err.println("Warning: Error loading history: " + e.getMessage());
        }
    }

    /**
     * Export history to text format
     */
    public String exportToText() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("CRYPTOCARVER - OPERATION HISTORY\n");
        sb.append("========================================\n\n");

        for (OperationEntry entry : history) {
            sb.append(entry.toFormattedString()).append("\n");
            sb.append("----------------------------------------\n");
        }

        return sb.toString();
    }

    /**
     * Export history to CSV format
     */
    public String exportToCSV() {
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp,Category,Operation,Input,Output\n");

        for (OperationEntry entry : history) {
            sb.append("\"").append(entry.getTimestamp().format(formatter)).append("\",");
            sb.append("\"").append(escapeCsv(entry.getCategory())).append("\",");
            sb.append("\"").append(escapeCsv(entry.getOperation())).append("\",");
            sb.append("\"").append(escapeCsv(entry.getInput())).append("\",");
            sb.append("\"").append(escapeCsv(entry.getOutput())).append("\"\n");
        }

        return sb.toString();
    }

    /**
     * Export history to JSON format
     */
    public String exportToJSON() {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();

        return gson.toJson(history);
    }

    private String escapeCsv(String str) {
        if (str == null)
            return "";
        return str.replace("\"", "\"\"");
    }

    /**
     * Represents a single operation entry
     */
    public static class OperationEntry {
        private final LocalDateTime timestamp;
        private final String category;
        private final String operation;
        private final String input;
        private final String output;

        public OperationEntry(LocalDateTime timestamp, String category,
                String operation, String input, String output) {
            this.timestamp = timestamp;
            this.category = category;
            this.operation = operation;
            this.input = input;
            this.output = output;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getCategory() {
            return category;
        }

        public String getOperation() {
            return operation;
        }

        public String getInput() {
            return input;
        }

        public String getOutput() {
            return output;
        }

        public String toFormattedString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return String.format(
                    "[%s] %s - %s\nInput:  %s\nOutput: %s",
                    timestamp.format(formatter),
                    category,
                    operation,
                    truncate(input, 100),
                    truncate(output, 100));
        }

        private String truncate(String str, int maxLength) {
            if (str == null)
                return "N/A";
            if (str.length() <= maxLength)
                return str;
            return str.substring(0, maxLength) + "...";
        }
    }
}
