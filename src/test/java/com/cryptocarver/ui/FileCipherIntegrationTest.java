package com.cryptocarver.ui;

import com.cryptocarver.crypto.LineFileCipher;
import com.cryptocarver.crypto.StreamingCipher;
import com.cryptocarver.util.ProgressMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class FileCipherIntegrationTest {

    @Test
    void testStreamingGcmEncryptAndDecryptWithSeparatedTag(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("plain.bin");
        Path destination = tempDir.resolve("cipher.bin");
        Path tag = tempDir.resolve("auth.tag");

        byte[] plainData = new byte[1024 * 1024]; // 1MB
        new SecureRandom().nextBytes(plainData);
        Files.write(source, plainData);

        byte[] key = new byte[32]; // AES-256
        byte[] iv = new byte[12];  // GCM 96-bit nonce
        byte[] aad = "test-header".getBytes(StandardCharsets.UTF_8);
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);

        // Encrypt
        StreamingCipher.Result encResult = StreamingCipher.encrypt(
                source, destination, key, "AES-256", "GCM", iv, aad, tag, ProgressMonitor.NO_OP
        );
        assertTrue(Files.exists(destination));
        assertTrue(Files.exists(tag));
        assertEquals(plainData.length, encResult.inputBytes());

        // Decrypt using original input tag file and cipher destination
        Path decrypted = tempDir.resolve("decrypted.bin");
        StreamingCipher.Result decResult = StreamingCipher.decrypt(
                destination, decrypted, key, "AES-256", "GCM", iv, aad, tag, ProgressMonitor.NO_OP
        );
        assertTrue(Files.exists(decrypted));
        assertArrayEquals(plainData, Files.readAllBytes(decrypted));

        // Original input tag file must be untouched and intact
        assertTrue(Files.exists(tag));
        assertNoStagingOrBackupFiles(tempDir);
    }

    @Test
    void testStreamingCipherCancellationLeavesNoPartialFiles(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source.bin");
        Path destination = tempDir.resolve("output.bin");
        Path tag = tempDir.resolve("output.tag");

        byte[] data = new byte[5 * 1024 * 1024]; // 5MB
        Files.write(source, data);

        byte[] key = new byte[32];
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);

        ProgressMonitor cancellingMonitor = new ProgressMonitor() {
            private int calls = 0;
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) { calls++; }

            @Override
            public boolean isCancelled() { return calls > 2; }
        };

        assertThrows(CancellationException.class, () ->
                StreamingCipher.encrypt(source, destination, key, "AES-256", "GCM", iv, null, tag, cancellingMonitor)
        );

        assertFalse(Files.exists(destination), "Destination file must not exist on cancellation");
        assertFalse(Files.exists(tag), "Tag file must not exist on cancellation");
        assertNoStagingOrBackupFiles(tempDir);
    }

    @Test
    void testLineFileCipherCancellationLeavesNoPartialFiles(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("lines.txt");
        Path destination = tempDir.resolve("encrypted_lines.txt");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("Line ").append(i).append(" - Sample Data Record\n");
        }
        Files.writeString(source, sb.toString());

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        ProgressMonitor cancellingMonitor = new ProgressMonitor() {
            private int lines = 0;
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) { lines++; }

            @Override
            public boolean isCancelled() { return lines > 10; }
        };

        assertThrows(CancellationException.class, () ->
                LineFileCipher.encrypt(source, destination, key, "AES-256-GCM", null,
                        LineFileCipher.Encoding.BASE64URL, null, StandardCharsets.UTF_8, false, cancellingMonitor)
        );

        assertFalse(Files.exists(destination), "Destination file must not exist on cancellation");
        assertNoStagingOrBackupFiles(tempDir);
    }

    @Test
    void testPreexistingDestinationPreservedOnFailureOrCancellation(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source.txt");
        Path destination = tempDir.resolve("preexisting.txt");
        Path tag = tempDir.resolve("preexisting.tag");

        Files.writeString(source, "New content");
        Files.writeString(destination, "PREEXISTING USER CONTENT DO NOT DELETE");

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        ProgressMonitor immediateCancel = new ProgressMonitor() {
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) {}

            @Override
            public boolean isCancelled() { return true; }
        };

        assertThrows(CancellationException.class, () ->
                StreamingCipher.encrypt(source, destination, key, "AES-256", "GCM", new byte[12], null, tag, immediateCancel)
        );

        assertTrue(Files.exists(destination), "Pre-existing destination file must be preserved");
        assertEquals("PREEXISTING USER CONTENT DO NOT DELETE", Files.readString(destination));
        assertFalse(Files.exists(tag), "Tag should not be created on immediate cancellation");
        assertNoStagingOrBackupFiles(tempDir);
    }

    @Test
    void testCancelWinsRightBeforeEnterCommitPhase(@TempDir Path tempDir) throws Exception {
        Path stagingDest = tempDir.resolve("stage.dest");
        Path stagingTag = tempDir.resolve("stage.tag");
        Path destination = tempDir.resolve("preexisting.dest");
        Path tag = tempDir.resolve("preexisting.tag");

        Files.writeString(stagingDest, "New Staging Dest");
        Files.writeString(stagingTag, "New Staging Tag");
        Files.writeString(destination, "Original Pre-existing Destination");
        Files.writeString(tag, "Original Pre-existing Tag");

        // Simulate Cancel winning the race (enterCommitPhase returns false)
        assertThrows(CancellationException.class, () ->
                FileCipherPromotion.promote(stagingDest, stagingTag, destination, tag, true, "uuid-123", () -> false)
        );

        assertFalse(Files.exists(stagingDest), "Staging dest must be cleaned up");
        assertFalse(Files.exists(stagingTag), "Staging tag must be cleaned up");
        assertEquals("Original Pre-existing Destination", Files.readString(destination));
        assertEquals("Original Pre-existing Tag", Files.readString(tag));
        assertNoStagingOrBackupFiles(tempDir);
    }

    @Test
    void testCommitWinsBeforeCancel() throws Exception {
        OperationExecutor executor = new OperationExecutor();
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicBoolean commitPhaseVerified = new AtomicBoolean(false);
        AtomicBoolean wasNotCancelled = new AtomicBoolean(false);

        executor.execute(
                "Commit Test",
                null,
                () -> {
                    boolean commitEntered = executor.enterCommitPhase();
                    if (commitEntered && executor.isInCommitPhase()) {
                        commitPhaseVerified.set(true);
                    }
                    boolean cancelResult = executor.cancelCurrentOperation();
                    if (!cancelResult && !executor.isCancelled()) {
                        wasNotCancelled.set(true);
                    }
                    return "Completed";
                },
                res -> doneLatch.countDown(),
                err -> fail("Should not fail: " + err.getMessage()),
                () -> fail("Should not be cancelled after entering commit phase")
        );

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertTrue(commitPhaseVerified.get(), "enterCommitPhase must return true when RUNNING");
        assertTrue(wasNotCancelled.get(), "cancelCurrentOperation must return false and isCancelled must be false while in commit phase");
        executor.shutdown();
    }

    @Test
    void testPromotionTagMoveFailureRollbackWithPreexistingFiles(@TempDir Path tempDir) throws Exception {
        Path stagingDest = tempDir.resolve("stage.dest");
        Path stagingTag = tempDir.resolve("stage.tag");
        Path destination = tempDir.resolve("dest.txt");
        Path tag = tempDir.resolve("dest.tag");

        Files.writeString(stagingDest, "Staged Destination Content");
        Files.writeString(stagingTag, "Staged Tag Content");
        Files.writeString(destination, "Original Dest Content");
        Files.writeString(tag, "Original Tag Content");

        // Custom mover that fails when moving stagingTag to tag
        FileCipherPromotion.FileMover failingMover = (src, dst) -> {
            if (src.equals(stagingTag)) {
                throw new IOException("Simulated disk error moving staging tag");
            }
            Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        };

        assertThrows(IOException.class, () ->
                FileCipherPromotion.promote(stagingDest, stagingTag, destination, tag, true, "uuid-456", () -> true, failingMover)
        );

        assertEquals("Original Dest Content", Files.readString(destination), "Original destination must be restored from backup");
        assertEquals("Original Tag Content", Files.readString(tag), "Original tag must be restored from backup");
        assertNoStagingOrBackupFiles(tempDir);
    }

    @Test
    void testPromotionTagMoveFailureRollbackWithoutPreexistingFiles(@TempDir Path tempDir) throws Exception {
        Path stagingDest = tempDir.resolve("stage.dest");
        Path stagingTag = tempDir.resolve("stage.tag");
        Path destination = tempDir.resolve("new.dest");
        Path tag = tempDir.resolve("new.tag");

        Files.writeString(stagingDest, "Staged Destination Content");
        Files.writeString(stagingTag, "Staged Tag Content");

        // Custom mover that fails when moving stagingTag to tag
        FileCipherPromotion.FileMover failingMover = (src, dst) -> {
            if (dst.equals(tag)) {
                throw new IOException("Simulated disk error moving tag");
            }
            Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        };

        assertThrows(IOException.class, () ->
                FileCipherPromotion.promote(stagingDest, stagingTag, destination, tag, true, "uuid-789", () -> true, failingMover)
        );

        assertFalse(Files.exists(destination), "Partial destination must be deleted on tag move failure");
        assertFalse(Files.exists(tag), "Partial tag must be deleted on tag move failure");
        assertNoStagingOrBackupFiles(tempDir);
    }

    private void assertNoStagingOrBackupFiles(Path dir) throws Exception {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                assertFalse(name.contains(".stage.") || name.contains(".bak.") || name.endsWith(".tmp"),
                        "No staging or backup file should remain: " + name);
            }
        }
    }

    @Test
    void testRealProgressTrackingFromZeroToTotal(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("plain_5mb.bin");
        Path destination = tempDir.resolve("cipher_5mb.bin");
        Path tag = tempDir.resolve("auth_5mb.tag");

        byte[] plainData = new byte[2 * 1024 * 1024]; // 2MB
        new SecureRandom().nextBytes(plainData);
        Files.write(source, plainData);

        byte[] key = new byte[32];
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);

        java.util.concurrent.atomic.AtomicLong maxReportedBytes = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong totalReportedBytes = new java.util.concurrent.atomic.AtomicLong(0);

        ProgressMonitor progressMonitor = new ProgressMonitor() {
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) {
                maxReportedBytes.accumulateAndGet(bytesProcessed, Math::max);
                totalReportedBytes.set(totalBytes);
            }

            @Override
            public boolean isCancelled() { return false; }
        };

        StreamingCipher.encrypt(source, destination, key, "AES-256", "GCM", iv, null, tag, progressMonitor);

        assertEquals(plainData.length, totalReportedBytes.get(), "Total reported bytes must equal source file size");
        assertEquals(plainData.length, maxReportedBytes.get(), "Final reported progress must reach 100% of source bytes");
    }

    @Test
    void testIntermediateCancellationDoesNotPublish100Percent(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("plain_10mb.bin");
        Path destination = tempDir.resolve("cipher_10mb.bin");
        Path tag = tempDir.resolve("auth_10mb.tag");

        byte[] plainData = new byte[5 * 1024 * 1024]; // 5MB
        new SecureRandom().nextBytes(plainData);
        Files.write(source, plainData);

        byte[] key = new byte[32];
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);

        java.util.concurrent.atomic.AtomicLong maxReportedBytes = new java.util.concurrent.atomic.AtomicLong(0);

        ProgressMonitor cancellingMonitor = new ProgressMonitor() {
            private int chunks = 0;
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) {
                chunks++;
                maxReportedBytes.set(bytesProcessed);
            }

            @Override
            public boolean isCancelled() { return chunks > 3; }
        };

        assertThrows(CancellationException.class, () ->
                StreamingCipher.encrypt(source, destination, key, "AES-256", "GCM", iv, null, tag, cancellingMonitor)
        );

        assertTrue(maxReportedBytes.get() < plainData.length, "Cancelled operation must never reach 100% total bytes");
        assertFalse(Files.exists(destination));
    }
}
