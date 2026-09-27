package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SavedSessionsManagerTest {
    @TempDir Path temp;

    @Test
    void writesOwnerOnlyDirectoryAndFilePermissionsWhenPosixIsAvailable() throws Exception {
        assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path dir = temp.resolve("private");
        Path file = dir.resolve("saved_sessions.json");
        SavedSessionsManager manager = new SavedSessionsManager(file);
        manager.addSession(new SavedSession("safe", "Hash", Map.of("inputField", "abc")));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(file));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE), Files.getPosixFilePermissions(dir));
    }

    @Test
    void legacySecretPurgeRewritesFileWithoutSecretText() throws Exception {
        String secret = "invented-purge-secret";
        Path file = temp.resolve("sessions.json");
        Files.writeString(file, "[{\"name\":\"legacy\",\"operation\":\"Cipher\",\"uiState\":{" +
                "\"CipherController.passwordField\":\"" + secret + "\"}}]");
        SavedSessionsManager manager = new SavedSessionsManager(file);
        assertTrue(manager.hasLegacyPlaintextSecrets());
        manager.removeLegacyPlaintextSecrets();
        String result = Files.readString(file);
        assertFalse(result.contains(secret));
        assertTrue(result.contains("[REDACTED_SECRET]"));
    }

    @Test
    void legacySecretPurgeKeepsEncryptedSessionsIntact() throws Exception {
        Path file = temp.resolve("mixed.json");
        Files.writeString(file, "[{\"name\":\"legacy\",\"operation\":\"Cipher\",\"uiState\":{"
                + "\"CipherController.passwordField\":\"invented-legacy-secret\"}}]");
        SavedSessionsManager manager = new SavedSessionsManager(file);
        SavedSessionCodec codec = new SavedSessionCodec();
        SavedSession encrypted = codec.prepareForStorage(new SavedSession("protected", "Cipher",
                Map.of("CipherController.passwordField", "invented-protected-secret")), "long enough".toCharArray());
        manager.addSession(encrypted);

        manager.removeLegacyPlaintextSecrets();

        SavedSession kept = manager.getSessions().stream()
                .filter(session -> "protected".equals(session.getName())).findFirst().orElseThrow();
        assertNotNull(kept.getProtectedFields());
        assertEquals("invented-protected-secret", codec.restore(kept, "long enough".toCharArray())
                .getUiState().get("CipherController.passwordField"));
        assertFalse(Files.readString(file).contains("invented-legacy-secret"));
    }
}
