package com.cryptocarver.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

public class SavedSessionsManager {
    private static final SavedSessionsManager instance = new SavedSessionsManager();
    private final List<SavedSession> savedSessions = new ArrayList<>();
    private final SavedSessionCodec codec = new SavedSessionCodec();
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
                applyPermissions(configDir, "rwx------");
                writeOwnerOnly(sessionsFilePath, Files.readString(legacyFile));
            }

            loadSessions();
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize saved sessions file: " + e.getMessage());
        }
    }

    SavedSessionsManager(Path sessionsFilePath) {
        this.sessionsFilePath = sessionsFilePath;
        loadSessions();
    }

    public static SavedSessionsManager getInstance() {
        return instance;
    }

    public void addSession(SavedSession session) {
        if (session != null) {
            SavedSession prepared = session.getProtectedFields() == null
                    ? codec.prepareForStorage(session, null) : session;
            savedSessions.add(0, prepared); // Add to top
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

    public boolean hasLegacyPlaintextSecrets() {
        return savedSessions.stream().anyMatch(session -> session.getVersion() == 0
                && (session.getOperationLog() != null || (session.getUiState() != null && session.getUiState().entrySet().stream().anyMatch(entry ->
                        com.cryptocarver.ui.UiStateSnapshot.holdsSecretValue(entry.getKey(), entry.getValue())))));
    }

    public void removeLegacyPlaintextSecrets() {
        List<SavedSession> cleaned = codec.redactLegacyPlaintext(savedSessions);
        savedSessions.clear();
        savedSessions.addAll(cleaned);
        saveSessions();
    }

    private void saveSessions() {
        if (sessionsFilePath == null)
            return;

        try {
            Files.createDirectories(sessionsFilePath.getParent());
            applyPermissions(sessionsFilePath.getParent(), "rwx------");

            String json = codec.serialize(savedSessions);
            writeOwnerOnly(sessionsFilePath, json);

        } catch (IOException e) {
            System.err.println("Warning: Error saving sessions: " + e.getMessage());
        }
    }

    private static void writeOwnerOnly(Path destination, String contents) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".saved-sessions-", ".tmp");
        boolean moved = false;
        try {
            applyPermissions(temporary, "rw-------");
            Files.writeString(temporary, contents);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            applyPermissions(destination, "rw-------");
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static void applyPermissions(Path path, String permissions) {
        try {
            Set<PosixFilePermission> set = new java.util.HashSet<>();
            if (permissions.contains("r")) set.add(PosixFilePermission.OWNER_READ);
            if (permissions.contains("w")) set.add(PosixFilePermission.OWNER_WRITE);
            if (permissions.contains("x")) set.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, set);
        } catch (UnsupportedOperationException | IOException ignored) { }
    }

    private void loadSessions() {
        if (sessionsFilePath == null)
            return;

        try {
            if (!Files.exists(sessionsFilePath))
                return;

            String json = Files.readString(sessionsFilePath);
            savedSessions.clear();
            savedSessions.addAll(codec.deserialize(json));

        } catch (IOException e) {
            System.err.println("Warning: Error loading sessions: " + e.getMessage());
        }
    }
}
