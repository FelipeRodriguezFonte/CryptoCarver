package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Required privacy contract; reproduces a pre-existing defect before refactoring (assignment 76). */
class EncryptedFileAnalysisPrivacyReproductionTest {
    @TempDir Path dir;

    @ParameterizedTest
    @EnumSource(value = SecretVisibilityProfile.class, names = {"MASKED", "REDACTED"})
    void restrictedReportsMustNotExposeRecoveredText(SecretVisibilityProfile profile) throws Exception {
        AppSettings previous = AppSettings.getInstance();
        AppSettings isolated = new AppSettings(dir.resolve("settings.json"));
        Random random = new Random(76L);
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        random.nextBytes(key);
        random.nextBytes(iv);
        String marker = "Invented confidential recovered text for assignment 76.";
        byte[] plaintext = (marker + "\n" + marker + "\n").getBytes(StandardCharsets.US_ASCII);
        try {
            AppSettings.setInstanceForTesting(isolated);
            isolated.setSecretVisibilityProfile(profile);
            Path source = dir.resolve("privacy.bin");
            Files.write(source, SymmetricCipher.encrypt(plaintext, key, "AES-128", "CBC", "PKCS5Padding", iv, null));
            var options = new EncryptedFileAnalyzer.FileAnalysisOptions(new int[] {64}, true, false, 8,
                    EncryptedFileAnalyzer.FileDataEncoding.RAW, 262144);
            var outcome = new EncryptedFileAnalyzer(new EncryptedFileAnalyzer.CipherInputs(
                    DataConverter.bytesToHex(iv), "", false, "", AppSettings.isFullLab())).analyze(source, key, options);
            assertTrue(outcome.hasCandidate());
            Path htmlPath;
            Path textPath;
            try (Stream<Path> files = Files.walk(dir)) {
                htmlPath = files.filter(path -> path.getFileName().toString().equals("report.html"))
                        .findFirst().orElseThrow();
            }
            textPath = htmlPath.resolveSibling("report.txt");
            String text = Files.readString(textPath);
            String html = Files.readString(htmlPath);
            String csv = Files.readString(htmlPath.resolveSibling("attempts.csv"));
            String keyHex = DataConverter.bytesToHex(key);
            assertAll(profile.name(),
                    () -> assertFalse(csv.contains(keyHex), "attempts.csv exposes invented key"),
                    () -> assertFalse(csv.contains(marker), "attempts.csv preview exposes recovered text"),
                    () -> assertFalse(text.contains(keyHex), "report.txt exposes invented key"),
                    () -> assertFalse(html.contains(keyHex), "report.html exposes invented key"),
                    () -> assertFalse(text.contains(marker), "report.txt exposes recovered text"),
                    () -> assertFalse(html.contains(marker), "report.html exposes recovered text"),
                    () -> assertEquals("9c8ab4ca13ab56bd3c38b8d97fa09a7ac6f2a53a37f892cb3093a7eb46b33d4e", digest(text)),
                    () -> assertEquals("9ba6bb0a128385ce1d07ceba171bb3782ee23b9acaa55fd42b7e9feba47c4c41", digest(html)));
        } finally {
            // No controller, history, Shelf or status service is instantiated or mutated.
            AppSettings.setInstanceForTesting(previous);
            // @TempDir removes input, settings and all generated reports, also on assertion failure.
        }
    }
    @Test
    void fullLabKeepsTheOriginalReportBytesAndPreview() throws Exception {
        Random random = new Random(76L);
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        random.nextBytes(key);
        random.nextBytes(iv);
        String marker = "Invented confidential recovered text for assignment 76.";
        byte[] plaintext = (marker + "\n" + marker + "\n").getBytes(StandardCharsets.US_ASCII);
        Path source = dir.resolve("privacy.bin");
        Files.write(source, SymmetricCipher.encrypt(plaintext, key, "AES-128", "CBC", "PKCS5Padding", iv, null));
        var options = new EncryptedFileAnalyzer.FileAnalysisOptions(new int[] {64}, true, false, 8,
                EncryptedFileAnalyzer.FileDataEncoding.RAW, 262144);
        var outcome = new EncryptedFileAnalyzer(new EncryptedFileAnalyzer.CipherInputs(
                DataConverter.bytesToHex(iv), "", false, "", true)).analyze(source, key, options);
        Path htmlPath;
        try (Stream<Path> files = Files.walk(dir)) {
            htmlPath = files.filter(path -> path.getFileName().toString().equals("report.html"))
                    .findFirst().orElseThrow();
        }
        String text = Files.readString(htmlPath.resolveSibling("report.txt"));
        String html = Files.readString(htmlPath);
        String csv = Files.readString(htmlPath.resolveSibling("attempts.csv"));
        assertTrue(text.contains(marker));
        assertTrue(html.contains(marker));
        assertTrue(csv.contains(marker));
        assertEquals(outcome.reportText(), text);
        assertAll(
                () -> assertEquals("2adf3008b59fdb915e72e81b388fbc758dc5a92a8c346bff2a75aa390ff97f7b", digest(text)),
                () -> assertEquals("112d49157698153337d875683c0cb20b3b0383b36f65bc9bbe460ead3f64e1fb", digest(html)));
    }

    @ParameterizedTest
    @EnumSource(value = SecretVisibilityProfile.class, names = {"MASKED", "REDACTED"})
    void restrictedOutcomeSurfacesMustNotExposeRecoveredTextOrKey(SecretVisibilityProfile profile) throws Exception {
        AppSettings previous = AppSettings.getInstance();
        AppSettings isolated = new AppSettings(dir.resolve("settings.json"));
        Random random = new Random(76L);
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        random.nextBytes(key);
        random.nextBytes(iv);
        String marker = "Invented confidential recovered text for assignment 76.";
        byte[] plaintext = (marker + "\n" + marker + "\n").getBytes(StandardCharsets.US_ASCII);
        try {
            AppSettings.setInstanceForTesting(isolated);
            isolated.setSecretVisibilityProfile(profile);
            Path source = dir.resolve("privacy.bin");
            Files.write(source, SymmetricCipher.encrypt(plaintext, key, "AES-128", "CBC", "PKCS5Padding", iv, null));
            var options = new EncryptedFileAnalyzer.FileAnalysisOptions(new int[] {64}, true, false, 8,
                    EncryptedFileAnalyzer.FileDataEncoding.RAW, 262144);
            var outcome = new EncryptedFileAnalyzer(new EncryptedFileAnalyzer.CipherInputs(
                    DataConverter.bytesToHex(iv), "", false, "", AppSettings.isFullLab()))
                    .analyze(source, key, options);
            assertTrue(outcome.hasCandidate());
            String keyHex = DataConverter.bytesToHex(key);
            assertAll(profile.name(),
                    () -> assertNoSecret("Outcome.reportText", outcome.reportText(), marker, keyHex),
                    () -> assertNoSecret("Outcome.status", outcome.status(), marker, keyHex),
                    () -> assertNoSecret("Outcome.inspectorDetails", outcome.inspectorDetails().toString(), marker, keyHex),
                    () -> assertNoSecret("Outcome.historyInput", outcome.historyInput(), marker, keyHex),
                    () -> assertNoSecret("Outcome.historyResult", outcome.historyResult(), marker, keyHex));
        } finally {
            // No live inspector, history, Shelf or status service is created or changed.
            AppSettings.setInstanceForTesting(previous);
        }
    }

    private static void assertNoSecret(String surface, String value, String marker, String keyHex) {
        assertAll(surface,
                () -> assertFalse(value.contains(marker), surface + " exposes recovered text"),
                () -> assertFalse(value.toUpperCase(java.util.Locale.ROOT).contains(keyHex.toUpperCase(java.util.Locale.ROOT)),
                        surface + " exposes invented key"));
    }

    private String digest(String value) throws Exception {
        String normalized = value.replace(dir.toAbsolutePath().toString(), "<DIR>")
                .replaceAll("analysis_privacy\\.bin_\\d{8}_\\d{6}", "analysis_privacy.bin_<TS>")
                .replace("\r\n", "\n");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

}
