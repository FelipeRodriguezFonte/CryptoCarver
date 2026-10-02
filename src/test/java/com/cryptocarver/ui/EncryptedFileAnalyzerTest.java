package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedFileAnalyzerTest {
    private static final byte[] KEY = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");
    private static final String IV = "0F0E0D0C0B0A09080706050403020100";
    private static final byte[] PLAINTEXT = "Readable text that the analysis should rank first.\n"
            .getBytes(StandardCharsets.US_ASCII);

    @TempDir Path dir;

    @Test
    void ranksTheMatchingCbcCombinationFirstAndWritesTheThreeReports() throws Exception {
        byte[] ciphertext = SymmetricCipher.encrypt(PLAINTEXT, KEY, "AES-128", "CBC", "PKCS5Padding",
                DataConverter.hexToBytes(IV), null);
        Path source = dir.resolve("sample.bin");
        Files.write(source, ciphertext);

        EncryptedFileAnalyzer.Outcome outcome = analyzer(IV, "").analyze(source, KEY,
                EncryptedFileAnalyzer.FileAnalysisOptions.defaults());

        assertTrue(outcome.hasCandidate());
        assertEquals("AES-128", outcome.inspectorDetails().get("Best Algorithm"));
        assertEquals("CBC", outcome.inspectorDetails().get("Best Mode"));
        assertArrayEquals(PLAINTEXT, outcome.inspectorOutput());
        assertEquals("sample.bin (" + ciphertext.length + " bytes)", outcome.historyInput());
        assertEquals(List.of("attempts.csv", "report.html", "report.txt"), reportFiles());
    }

    @Test
    void anEmptyFileReturnsNoOutcome() throws Exception {
        Path source = dir.resolve("empty.bin");
        Files.write(source, new byte[0]);

        assertNull(analyzer(IV, "").analyze(source, KEY, EncryptedFileAnalyzer.FileAnalysisOptions.defaults()));
    }

    @Test
    void aadIsIgnoredWhenItsFieldIsDisabled() throws Exception {
        String nonce = "CAFEBABEFACEDBADDECAF888";
        byte[] sealed = SymmetricCipher.encrypt(PLAINTEXT, KEY, "AES-128", "GCM", "NoPadding",
                DataConverter.hexToBytes(nonce), null);
        Path source = dir.resolve("gcm.bin");
        Files.write(source, sealed);
        EncryptedFileAnalyzer.CipherInputs disabledAad = new EncryptedFileAnalyzer.CipherInputs(
                nonce, "FEEDFACE", false, "");

        EncryptedFileAnalyzer.Outcome outcome = new EncryptedFileAnalyzer(disabledAad).analyze(source, KEY,
                EncryptedFileAnalyzer.FileAnalysisOptions.defaults());

        assertTrue(outcome.hasCandidate());
        assertArrayEquals(PLAINTEXT, outcome.inspectorOutput());
    }

    @Test
    void reportsDoNotContainTheKey() throws Exception {
        byte[] ciphertext = SymmetricCipher.encrypt(PLAINTEXT, KEY, "AES-128", "CBC", "PKCS5Padding",
                DataConverter.hexToBytes(IV), null);
        Path source = dir.resolve("secret.bin");
        Files.write(source, ciphertext);

        EncryptedFileAnalyzer.Outcome outcome = analyzer(IV, "").analyze(source, KEY,
                EncryptedFileAnalyzer.FileAnalysisOptions.defaults());

        String keyHex = DataConverter.bytesToHex(KEY);
        assertFalse(outcome.reportText().toUpperCase().contains(keyHex));
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile).filter(f -> !f.equals(source)).toList()) {
                assertFalse(Files.readString(file).toUpperCase().contains(keyHex), file.toString());
            }
        }
    }

    private static EncryptedFileAnalyzer analyzer(String iv, String aad) {
        return new EncryptedFileAnalyzer(new EncryptedFileAnalyzer.CipherInputs(iv, aad, true, ""));
    }

    private List<String> reportFiles() throws Exception {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !file.getParent().equals(dir))
                    .map(file -> file.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }
}
