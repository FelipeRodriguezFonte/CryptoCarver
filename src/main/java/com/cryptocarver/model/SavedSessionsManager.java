package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SavedSessionsManager {
    private static final SavedSessionsManager instance = new SavedSessionsManager();
    private final List<SavedSession> savedSessions = new ArrayList<>();
    private Path sessionsFilePath;

    private SavedSessionsManager() {
        try {
            // Get user home directory safely - aligning with OperationHistory location
            String userHome = System.getProperty("user.home");
            if (userHome == null || userHome.isEmpty()) {
                userHome = System.getProperty("java.io.tmpdir");
            }

            Path configDir = Paths.get(userHome, ".cryptocarver");
            sessionsFilePath = configDir.resolve("saved_sessions.json");
            Path legacyFile = Paths.get(userHome, ".crypto-calculator", "saved_sessions.json");
            if (!Files.exists(sessionsFilePath) && Files.exists(legacyFile)) {
                Files.createDirectories(configDir);
                Files.copy(legacyFile, sessionsFilePath);
            }

            loadSessions();
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize saved sessions file: " + e.getMessage());
        }
    }

    public static SavedSessionsManager getInstance() {
        return instance;
    }

    public void addSession(SavedSession session) {
        if (session != null) {
            savedSessions.add(0, session); // Add to top
            saveSessions();
        }
    }

    public void removeSession(SavedSession session) {
        if (session != null) {
            savedSessions.remove(session);
            saveSessions();
        }
    }

    public List<SavedSession> getSessions() {
        return new ArrayList<>(savedSessions);
    }

    private void saveSessions() {
        if (sessionsFilePath == null)
            return;

        try {
            if (!Files.exists(sessionsFilePath.getParent())) {
                Files.createDirectories(sessionsFilePath.getParent());
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(savedSessions);
            Files.writeString(sessionsFilePath, json);

        } catch (IOException e) {
            System.err.println("Warning: Error saving sessions: " + e.getMessage());
        }
    }

    private void loadSessions() {
        if (sessionsFilePath == null)
            return;

        try {
            if (!Files.exists(sessionsFilePath))
                return;

            String json = Files.readString(sessionsFilePath);
            Gson gson = new Gson();
            TypeToken<List<SavedSession>> token = new TypeToken<>() {
            };
            List<SavedSession> loaded = gson.fromJson(json, token.getType());

            if (loaded != null) {
                savedSessions.clear();
                savedSessions.addAll(loaded);
            }

        } catch (IOException e) {
            System.err.println("Warning: Error loading sessions: " + e.getMessage());
        }
    }
}
