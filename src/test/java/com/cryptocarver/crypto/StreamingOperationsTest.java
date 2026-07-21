package com.cryptocarver.crypto;

import com.cryptocarver.util.ProgressMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StreamingOperationsTest {
    private Path tempDir;
    private Path largeFile;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("crypto_streaming_test");
        largeFile = tempDir.resolve("large_64MiB.bin");

        // Generate a 64 MiB file quickly
        try (var out = new BufferedOutputStream(Files.newOutputStream(largeFile))) {
            byte[] chunk = new byte[1024 * 1024]; // 1 MiB chunk
            for (int i = 0; i < 64; i++) {
                out.write(chunk);
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var stream = Files.walk(tempDir)) {
            stream.map(Path::toFile).forEach(java.io.File::delete);
        }
    }

    @Test
    void testHashProgressAndCancellation() {
        assertThrows(CancellationException.class, () -> {
            StreamingFileTools.hash(largeFile, "SHA-256", new ProgressMonitor() {
                @Override
                public void updateProgress(long bytesProcessed, long totalBytes) { }

                @Override
                public boolean isCancelled() {
                    return true; // Cancel immediately
                }
            });
        });
    }

    @Test
    void testEncryptionProgressAndCancellation() throws Exception {
        Path dest = tempDir.resolve("encrypted.bin");
        Path tag = tempDir.resolve("tag.bin");
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];

        assertThrows(CancellationException.class, () -> {
            StreamingCipher.encrypt(largeFile, dest, key, "AES-256", "GCM", nonce, null, tag, new ProgressMonitor() {
                long processed = 0;
                @Override
                public void updateProgress(long bytesProcessed, long totalBytes) {
                    processed = bytesProcessed;
                }

                @Override
                public boolean isCancelled() {
                    return processed > 1024 * 1024; // Cancel after 1 MiB
                }
            });
        });

        assertFalse(Files.exists(dest), "Destination file should be cleaned up upon cancellation");
        assertFalse(Files.exists(tag), "Tag file should be cleaned up upon cancellation");
    }

    @Test
    void testHashCompletesFor64MiB() throws Exception {
        // Just verify it doesn't OOM and runs to completion
        String hash = StreamingFileTools.hash(largeFile, "SHA-256", ProgressMonitor.NO_OP);
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void testCancelPreservesExistingDestination() throws Exception {
        Path destFile = tempDir.resolve("existing_dest.bin");
        Files.writeString(destFile, "ORIGINAL_CONTENT");

        ProgressMonitor cancelMonitor = new ProgressMonitor() {
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) {}

            @Override
            public boolean isCancelled() { return true; } // cancel immediately
        };

        byte[] key = new byte[16]; // AES-128 key
        assertThrows(CancellationException.class, () ->
            StreamingCipher.encrypt(largeFile, destFile, key, "AES", "CBC", new byte[16], null, null, cancelMonitor)
        );

        // Check destination wasn't overwritten
        assertEquals("ORIGINAL_CONTENT", Files.readString(destFile));
    }

    @Test
    void testStreamingMACEquivalence() throws Exception {
        byte[] key16 = new byte[16];
        byte[] key32 = new byte[32];
        for (int i = 0; i < 16; i++) { key16[i] = (byte) i; }
        for (int i = 0; i < 32; i++) { key32[i] = (byte) i; }

        Path smallFile = tempDir.resolve("small.bin");
        byte[] data = "Hello, world! This is a test string to verify MAC equivalence.".getBytes(StandardCharsets.UTF_8);
        Files.write(smallFile, data);

        // HMAC
        assertArrayEquals(MACOperations.generate(data, key32, "HMAC-SHA256"),
                          MACOperations.generate(smallFile, key32, "HMAC-SHA256"));

        // CMAC-AES
        assertArrayEquals(MACOperations.generate(data, key16, "CMAC-AES"),
                          MACOperations.generate(smallFile, key16, "CMAC-AES"));

        // Retail MAC (Payment)
        assertArrayEquals(MACOperations.generate(data, key16, "Retail-MAC-3DES"),
                          MACOperations.generate(smallFile, key16, "Retail-MAC-3DES"));
    }

    @Test
    void testMacCancellationAndTempFiles() throws Exception {
        byte[] key = new byte[16];
        ProgressMonitor cancelMonitor = new ProgressMonitor() {
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) {}

            @Override
            public boolean isCancelled() { return true; }
        };

        assertThrows(CancellationException.class, () ->
            MACOperations.generate(largeFile, key, "CMAC-AES", cancelMonitor)
        );

        // Temporaries verify
        try (var stream = Files.list(tempDir)) {
            long tmpFiles = stream.filter(p -> p.getFileName().toString().contains(".cryptocarver-")).count();
            assertEquals(0, tmpFiles, "No temporary files should be left behind");
        }
    }

    @Test
    void testStreamingConversionAndCancellation() throws Exception {
        Path textFile = tempDir.resolve("text.txt");
        Files.writeString(textFile, "Hello, world!");

        Path destFile = tempDir.resolve("text.utf16.txt");
        StreamingFileTools.convertCharset(textFile, destFile, StandardCharsets.UTF_8, StandardCharsets.UTF_16);

        String result = Files.readString(destFile, StandardCharsets.UTF_16);
        assertEquals("Hello, world!", result);

        // Test cancellation
        ProgressMonitor cancelMonitor = new ProgressMonitor() {
            @Override
            public void updateProgress(long bytesProcessed, long totalBytes) {}

            @Override
            public boolean isCancelled() { return true; }
        };

        Path destFile2 = tempDir.resolve("text2.utf16.txt");
        assertThrows(CancellationException.class, () ->
            StreamingFileTools.convertCharset(textFile, destFile2, StandardCharsets.UTF_8, StandardCharsets.UTF_16, cancelMonitor)
        );

        assertTrue(Files.notExists(destFile2));

        // Temporaries verify
        try (var stream = Files.list(tempDir)) {
            long tmpFiles = stream.filter(p -> p.getFileName().toString().contains(".cryptocarver-")).count();
            assertEquals(0, tmpFiles, "No temporary files should be left behind");
        }
    }

    @Test
    void testMalformedConversionCleansUp() throws Exception {
        Path invalidUtf8 = tempDir.resolve("invalid.bin");
        Files.write(invalidUtf8, new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00});

        Path destFile = tempDir.resolve("should_not_exist.txt");
        Files.writeString(destFile, "preexisting content");

        assertThrows(java.nio.charset.MalformedInputException.class, () ->
            StreamingFileTools.convertCharset(invalidUtf8, destFile, StandardCharsets.UTF_8, StandardCharsets.UTF_16)
        );

        assertEquals("preexisting content", Files.readString(destFile));

        try (var stream = Files.list(tempDir)) {
            long tmpFiles = stream.filter(p -> p.getFileName().toString().contains(".cryptocarver-")).count();
            assertEquals(0, tmpFiles, "No temporary files should be left behind");
        }
    }

    @Test
    void testBoundaryMultibyteConversion() throws Exception {
        int bufSize = StreamingFileTools.getBufferSize();
        Path source = tempDir.resolve("boundary.txt");
        Path dest = tempDir.resolve("boundary.utf16");

        // 1. Create a large file
        byte[] a = "A".getBytes(StandardCharsets.UTF_8);
        byte[] emoji = "€".getBytes(StandardCharsets.UTF_8); // 3 bytes: E2 82 AC

        // Write exactly (bufSize - 1) bytes of 'A', so the emoji spans the boundary
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(source))) {
            for (int i = 0; i < bufSize - 1; i++) {
                out.write(a);
            }
            out.write(emoji);
            for (int i = 0; i < bufSize; i++) {
                out.write(a);
            }
        }

        StreamingFileTools.convertCharset(source, dest, StandardCharsets.UTF_8, StandardCharsets.UTF_16);
        String result = Files.readString(dest, StandardCharsets.UTF_16);

        assertEquals('A', result.charAt(bufSize - 2));
        assertEquals('€', result.charAt(bufSize - 1));
        assertEquals('A', result.charAt(bufSize));
    }
}
