package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.io.TempDir;
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
                    DataConverter.bytesToHex(iv), "", false, "")).analyze(source, key, options);
            assertTrue(outcome.hasCandidate());
            assertArrayEquals(plaintext, outcome.inspectorOutput());
            Path htmlPath;
            Path textPath;
            try (Stream<Path> files = Files.walk(dir)) {
                htmlPath = files.filter(path -> path.getFileName().toString().equals("report.html"))
                        .findFirst().orElseThrow();
            }
            textPath = htmlPath.resolveSibling("report.txt");
            String text = Files.readString(textPath);
            String html = Files.readString(htmlPath);
            String keyHex = DataConverter.bytesToHex(key);
            assertAll(profile.name(),
                    () -> assertFalse(text.contains(keyHex), "report.txt exposes invented key"),
                    () -> assertFalse(html.contains(keyHex), "report.html exposes invented key"),
                    () -> assertFalse(text.contains(marker), "report.txt exposes recovered text"),
                    () -> assertFalse(html.contains(marker), "report.html exposes recovered text"));
        } finally {
            // No controller, history, Shelf or status service is instantiated or mutated.
            AppSettings.setInstanceForTesting(previous);
            // @TempDir removes input, settings and all generated reports, also on assertion failure.
        }
    }
}
