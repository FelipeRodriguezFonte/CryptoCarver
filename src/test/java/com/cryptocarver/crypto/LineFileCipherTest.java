package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineFileCipherTest {
    @TempDir Path directory;

    @Test
    void aesGcmRoundTripsEachLineWithItsOwnRecord() throws Exception {
        Path source = directory.resolve("source.txt");
        Path encrypted = directory.resolve("encrypted.lines");
        Path restored = directory.resolve("restored.txt");
        Files.writeString(source, "first\n\nthird", StandardCharsets.UTF_8);
        byte[] key = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F");

        LineFileCipher.Result encryption = LineFileCipher.encrypt(source, encrypted, key, "AES-256-GCM", null);
        LineFileCipher.Result decryption = LineFileCipher.decrypt(encrypted, restored, key, "AES-256-GCM", null);

        assertEquals(3, encryption.lines());
        assertEquals(3, decryption.lines());
        assertEquals("first\n\nthird", Files.readString(restored, StandardCharsets.UTF_8));
        String[] records = Files.readString(encrypted, StandardCharsets.UTF_8).split("\\n");
        assertEquals(3, records.length);
        assertFalse(records[0].equals(records[1]), "Every record must use a fresh nonce");
    }

    @Test
    void authenticatedFailureDoesNotLeaveDestinationFile() throws Exception {
        Path source = directory.resolve("source.txt");
        Path encrypted = directory.resolve("encrypted.lines");
        Path restored = directory.resolve("restored.txt");
        Files.writeString(source, "confidential", StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        LineFileCipher.encrypt(source, encrypted, key, "ChaCha20-Poly1305", "context".getBytes(StandardCharsets.UTF_8));
        String tampered = Files.readString(encrypted, StandardCharsets.UTF_8);
        int ciphertextStart = tampered.lastIndexOf('.') + 1;
        char replacement = tampered.charAt(ciphertextStart) == 'A' ? 'B' : 'A';
        Files.writeString(encrypted, tampered.substring(0, ciphertextStart) + replacement + tampered.substring(ciphertextStart + 1), StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> LineFileCipher.decrypt(encrypted, restored, key, "ChaCha20-Poly1305", "context".getBytes(StandardCharsets.UTF_8)));
        assertFalse(Files.exists(restored));
    }

    @Test
    void hexadecimalRecordsAreSelfDescribingAndRoundTrip() throws Exception {
        Path source = directory.resolve("source.txt");
        Path encrypted = directory.resolve("encrypted.hex-lines");
        Path restored = directory.resolve("restored.txt");
        Files.writeString(source, "uno\ndos", StandardCharsets.UTF_8);
        byte[] key = new byte[32];

        LineFileCipher.encrypt(source, encrypted, key, "AES-256-GCM", null, LineFileCipher.Encoding.HEXADECIMAL);
        LineFileCipher.decrypt(encrypted, restored, key, "AES-256-GCM", null);

        assertEquals("uno\ndos", Files.readString(restored, StandardCharsets.UTF_8));
        assertEquals("CF-LINE-1-HEX", Files.readString(encrypted, StandardCharsets.UTF_8).split("\\.", 2)[0]);
    }

    @Test
    void cbcLineRecordsUseTheProvidedIvAndRoundTrip() throws Exception {
        Path source = directory.resolve("source.txt");
        Path encrypted = directory.resolve("encrypted.cbc-lines");
        Path restored = directory.resolve("restored.txt");
        Files.writeString(source, "uno\ndos", StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        byte[] iv = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");

        LineFileCipher.encrypt(source, encrypted, key, "AES-256-CBC", null, LineFileCipher.Encoding.HEXADECIMAL, iv);
        LineFileCipher.decrypt(encrypted, restored, key, "AES-256-CBC", null, iv);

        assertEquals("uno\ndos", Files.readString(restored, StandardCharsets.UTF_8));
        assertEquals("CF-LINE-1-CBC-HEX", Files.readString(encrypted, StandardCharsets.UTF_8).split("\\.", 2)[0]);
    }

    @Test
    void cbcAcceptsCompactHexadecimalRecordsForCompatibility() throws Exception {
        Path source = directory.resolve("source.txt");
        Path encrypted = directory.resolve("encrypted.cbc-lines");
        Path compact = directory.resolve("encrypted.compact-hex");
        Path restored = directory.resolve("restored.txt");
        Files.writeString(source, "uno\ndos", StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        byte[] iv = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");
        LineFileCipher.encrypt(source, encrypted, key, "AES-256-CBC", null, LineFileCipher.Encoding.HEXADECIMAL, iv);
        String compactRecords = Files.readString(encrypted, StandardCharsets.UTF_8)
                .replaceAll("CF-LINE-1-CBC-HEX\\.", "");
        Files.writeString(compact, compactRecords, StandardCharsets.UTF_8);

        LineFileCipher.decrypt(compact, restored, key, "AES-256-CBC", null, iv, LineFileCipher.Encoding.HEXADECIMAL);
        assertEquals("uno\ndos", Files.readString(restored, StandardCharsets.UTF_8));
    }

    @Test
    void cbcCanWriteCompactEBCDICRecordsAndRestoreText() throws Exception {
        Path source = directory.resolve("source.ebcdic");
        Path encrypted = directory.resolve("encrypted.compact-hex");
        Path restored = directory.resolve("restored.ebcdic");
        java.nio.charset.Charset ebcdic = java.nio.charset.Charset.forName("Cp037");
        Files.writeString(source, "hola\nadios", ebcdic);
        byte[] key = new byte[32];
        byte[] iv = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");

        LineFileCipher.encrypt(source, encrypted, key, "AES-256-CBC", null, LineFileCipher.Encoding.HEXADECIMAL, iv, ebcdic, true);
        LineFileCipher.decrypt(encrypted, restored, key, "AES-256-CBC", null, iv, LineFileCipher.Encoding.HEXADECIMAL, ebcdic);

        assertEquals("hola\nadios", Files.readString(restored, ebcdic));
        assertFalse(Files.readString(encrypted, StandardCharsets.US_ASCII).startsWith("CF-LINE"));
    }

    @Test
    void gcmCanWriteCompactHexadecimalRecordsAndRestoreText() throws Exception {
        Path source = directory.resolve("source.txt");
        Path encrypted = directory.resolve("encrypted.compact-hex");
        Path restored = directory.resolve("restored.txt");
        Files.writeString(source, "uno\ndos", StandardCharsets.UTF_8);
        byte[] key = new byte[32];

        LineFileCipher.encrypt(source, encrypted, key, "AES-256-GCM", null, LineFileCipher.Encoding.HEXADECIMAL,
                null, StandardCharsets.UTF_8, true);
        LineFileCipher.decrypt(encrypted, restored, key, "AES-256-GCM", null, null,
                LineFileCipher.Encoding.HEXADECIMAL, StandardCharsets.UTF_8);

        assertEquals("uno\ndos", Files.readString(restored, StandardCharsets.UTF_8));
        assertFalse(Files.readString(encrypted, StandardCharsets.US_ASCII).startsWith("CF-LINE"));
    }
}
