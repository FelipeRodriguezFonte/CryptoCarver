package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingCipherTest {
    @TempDir Path directory;

    @Test
    void aesGcmRoundTripsWithSeparateTagAndAad() throws Exception {
        byte[] plain = new byte[150_000];
        new java.security.SecureRandom().nextBytes(plain);
        Path source = directory.resolve("source.bin");
        Path ciphertext = directory.resolve("cipher.bin");
        Path tag = directory.resolve("cipher.tag");
        Path restored = directory.resolve("restored.bin");
        Files.write(source, plain);
        byte[] key = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");
        byte[] nonce = DataConverter.hexToBytes("101112131415161718191A1B");
        byte[] aad = "CryptoCarver test".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        StreamingCipher.Result encrypted = StreamingCipher.encrypt(source, ciphertext, key, "AES-128", "GCM", nonce, aad, tag, com.cryptocarver.util.ProgressMonitor.NO_OP);
        assertTrue(encrypted.authenticated());
        assertTrue(Files.size(tag) == 16);
        StreamingCipher.decrypt(ciphertext, restored, key, "AES-128", "GCM", nonce, aad, tag, com.cryptocarver.util.ProgressMonitor.NO_OP);
        assertArrayEquals(plain, Files.readAllBytes(restored));
    }

    @Test
    void authenticatedDecryptionDoesNotLeaveCompletedOutputWhenTagIsInvalid() throws Exception {
        Path source = directory.resolve("source.bin");
        Path ciphertext = directory.resolve("cipher.bin");
        Path tag = directory.resolve("cipher.tag");
        Path restored = directory.resolve("restored.bin");
        Files.write(source, "authenticated payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        StreamingCipher.encrypt(source, ciphertext, key, "AES-256", "GCM", nonce, null, tag, com.cryptocarver.util.ProgressMonitor.NO_OP);
        byte[] invalidTag = Files.readAllBytes(tag);
        invalidTag[0] ^= 1;
        Files.write(tag, invalidTag);
        assertThrows(Exception.class, () -> StreamingCipher.decrypt(ciphertext, restored, key, "AES-256", "GCM", nonce, null, tag, com.cryptocarver.util.ProgressMonitor.NO_OP));
        assertFalse(Files.exists(restored));
    }

    @Test
    void aesCtrStreamsWithoutTag() throws Exception {
        Path source = directory.resolve("source.txt");
        Path ciphertext = directory.resolve("cipher.bin");
        Path restored = directory.resolve("restored.txt");
        byte[] data = "CTR can process arbitrary file sizes without padding".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source, data);
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        StreamingCipher.encrypt(source, ciphertext, key, "AES-128", "CTR", iv, null, null, com.cryptocarver.util.ProgressMonitor.NO_OP);
        StreamingCipher.decrypt(ciphertext, restored, key, "AES-128", "CTR", iv, null, null, com.cryptocarver.util.ProgressMonitor.NO_OP);
        assertArrayEquals(data, Files.readAllBytes(restored));
    }

    @Test
    void chacha20Poly1305RoundTripsWithDetachedTag() throws Exception {
        Path source = directory.resolve("source.bin");
        Path ciphertext = directory.resolve("cipher.bin");
        Path tag = directory.resolve("cipher.tag");
        Path restored = directory.resolve("restored.bin");
        byte[] data = new byte[70_000];
        Arrays.fill(data, (byte) 0x5a);
        Files.write(source, data);
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        nonce[11] = 1;
        StreamingCipher.encrypt(source, ciphertext, key, "ChaCha20-Poly1305", "", nonce, null, tag, com.cryptocarver.util.ProgressMonitor.NO_OP);
        StreamingCipher.decrypt(ciphertext, restored, key, "ChaCha20-Poly1305", "", nonce, null, tag, com.cryptocarver.util.ProgressMonitor.NO_OP);
        assertArrayEquals(data, Files.readAllBytes(restored));
    }
}
