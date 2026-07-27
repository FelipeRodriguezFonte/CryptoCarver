package com.cryptocarver.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Safe filesystem boundary for portable screen configurations. */
public final class ScreenConfigurationFiles {

    public static final long MAX_DOCUMENT_BYTES = 12_000_000;
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private ScreenConfigurationFiles() {
    }

    public static String read(Path file) throws IOException {
        if (file == null) throw new IllegalArgumentException("Configuration file is required");
        if (!Files.isRegularFile(file)) throw new IOException("Configuration path is not a regular file");
        long size = Files.size(file);
        if (size > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Configuration file exceeds 12 MB");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static void writeAtomic(Path destination, String document) throws IOException {
        if (destination == null) throw new IllegalArgumentException("Configuration destination is required");
        if (document == null || document.isBlank()) throw new IllegalArgumentException("Configuration document is empty");
        byte[] encoded = document.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Configuration document exceeds 12 MB");
        }

        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Configuration destination directory does not exist");
        }
        Path temporary = Files.createTempFile(parent, ".cryptocarver-config-", ".tmp");
        boolean moved = false;
        try {
            applyOwnerOnlyPermissions(temporary);
            Files.write(temporary, encoded);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            applyOwnerOnlyPermissions(absolute);
        } finally {
            java.util.Arrays.fill(encoded, (byte) 0);
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static void applyOwnerOnlyPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, OWNER_ONLY);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX file stores use their native ACL defaults.
        }
    }
}
