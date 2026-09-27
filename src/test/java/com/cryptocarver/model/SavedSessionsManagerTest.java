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
}
