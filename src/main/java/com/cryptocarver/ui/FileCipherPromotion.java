package com.cryptocarver.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Package-private transactional file promotion helper for AEAD and standard file ciphers.
 * Handles atomic moves, backups, automatic rollback on failure, and clean file staging.
 */
class FileCipherPromotion {

    @FunctionalInterface
    interface FileMover {
        void move(Path source, Path target) throws IOException;
    }

    private static final FileMover DEFAULT_MOVER = (src, dst) -> {
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    };

    static void promote(
            Path stagingDest,
            Path stagingTag,
            Path destination,
            Path tag,
            boolean encrypt,
            String sessionUuid,
            BooleanSupplier enterCommitPhaseCheck
    ) throws Exception {
        promote(stagingDest, stagingTag, destination, tag, encrypt, sessionUuid, enterCommitPhaseCheck, DEFAULT_MOVER);
    }

    static void promote(
            Path stagingDest,
            Path stagingTag,
            Path destination,
            Path tag,
            boolean encrypt,
            String sessionUuid,
            BooleanSupplier enterCommitPhaseCheck,
            FileMover fileMover
    ) throws Exception {
        Objects.requireNonNull(stagingDest, "stagingDest must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(fileMover, "fileMover must not be null");

        // Verify pre-commit atomic transition: if enterCommitPhase returns false or cancel won, abort BEFORE moving files
        if (enterCommitPhaseCheck != null && !enterCommitPhaseCheck.getAsBoolean()) {
            cleanupStaging(stagingDest, stagingTag);
            throw new CancellationException("File cipher operation cancelled before commit");
        }

        Path destBackup = null;
        Path tagBackup = null;

        try {
            // Backup pre-existing files
            if (Files.exists(destination)) {
                destBackup = destination.resolveSibling("." + destination.getFileName() + ".bak." + sessionUuid);
                fileMover.move(destination, destBackup);
            }
            if (encrypt && tag != null && Files.exists(tag)) {
                tagBackup = tag.resolveSibling("." + tag.getFileName() + ".bak." + sessionUuid);
                fileMover.move(tag, tagBackup);
            }

            // Promote staging files to final destinations
            try {
                fileMover.move(stagingDest, destination);
                if (encrypt && stagingTag != null && tag != null) {
                    fileMover.move(stagingTag, tag);
                }
            } catch (Throwable promoteErr) {
                // Rollback transaction if tag promotion fails after destination promotion
                rollback(destination, tag, destBackup, tagBackup, encrypt, fileMover);
                throw promoteErr;
            }

            // Clean up backups on success
            if (destBackup != null) Files.deleteIfExists(destBackup);
            if (tagBackup != null) Files.deleteIfExists(tagBackup);

        } catch (Throwable t) {
            cleanupStaging(stagingDest, stagingTag);
            if (destBackup != null && Files.exists(destBackup)) {
                if (Files.exists(destination)) Files.deleteIfExists(destination);
                fileMover.move(destBackup, destination);
            } else if (destBackup == null && Files.exists(destination)) {
                Files.deleteIfExists(destination);
            }
            if (tagBackup != null && Files.exists(tagBackup)) {
                if (tag != null && Files.exists(tag)) Files.deleteIfExists(tag);
                fileMover.move(tagBackup, tag);
            } else if (tagBackup == null && encrypt && tag != null && Files.exists(tag)) {
                Files.deleteIfExists(tag);
            }
            throw t;
        }
    }

    static void cleanupStaging(Path stagingDest, Path stagingTag) {
        try {
            if (stagingDest != null) Files.deleteIfExists(stagingDest);
            if (stagingTag != null) Files.deleteIfExists(stagingTag);
        } catch (Exception ignored) {}
    }

    private static void rollback(
            Path destination,
            Path tag,
            Path destBackup,
            Path tagBackup,
            boolean encrypt,
            FileMover fileMover
    ) {
        try {
            if (Files.exists(destination)) {
                Files.deleteIfExists(destination);
            }
            if (destBackup != null && Files.exists(destBackup)) {
                fileMover.move(destBackup, destination);
            }
            if (encrypt && tag != null && Files.exists(tag)) {
                Files.deleteIfExists(tag);
            }
            if (tagBackup != null && Files.exists(tagBackup)) {
                fileMover.move(tagBackup, tag);
            }
        } catch (Exception ignored) {}
    }
}
