package com.cryptocarver.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScreenConfigurationFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReplacesConfigurationAtomically() throws Exception {
        Path file = tempDir.resolve("screen.ccconfig");
        ScreenConfigurationFiles.writeAtomic(file, "first document");
        assertEquals("first document", ScreenConfigurationFiles.read(file));

        ScreenConfigurationFiles.writeAtomic(file, "replacement document");
        assertEquals("replacement document", ScreenConfigurationFiles.read(file));
        try {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(file));
        } catch (UnsupportedOperationException ignored) {
            // Expected on non-POSIX platforms.
        }
        try (var files = Files.list(tempDir)) {
            assertEquals(1, files.count(), "Temporary files must not remain after a successful move");
        }
    }

    @Test
    void rejectsMissingAndOversizedDocuments() throws Exception {
        assertThrows(java.io.IOException.class,
                () -> ScreenConfigurationFiles.read(tempDir.resolve("missing.ccconfig")));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationFiles.writeAtomic(tempDir.resolve("empty.ccconfig"), ""));
        String oversized = "x".repeat((int) ScreenConfigurationFiles.MAX_DOCUMENT_BYTES + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationFiles.writeAtomic(tempDir.resolve("large.ccconfig"), oversized));
        assertFalse(Files.exists(tempDir.resolve("large.ccconfig")));
    }
}
