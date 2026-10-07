package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Complements the existing CBC/Base64/container/GCM coverage with sampling and no candidates. */
class EncryptedFileAnalysisRefactorCharacterizationTest {
    @TempDir Path dir;

    @ParameterizedTest
    @CsvSource({"sampled, 2567baef35d17e726609b22803d7051c23e234f9259178171bb20e79e104e440, 83f2f9deb6881943cc792d97ed0c6fa07fc81b55dc109a9583e222037e1d0269", "no-candidates, fec8a76f10e37d07ffe5859cedd66322a0149622e29cdbbe7f5f9476b5cdf91b, 876798a7bb1d7e8327089f83a8291b9c8c78a5c7a883ebfb119d3251516726fe"})
    void normalizedReportsRemainIdentical(String scenario, String textDigest, String htmlDigest) throws Exception {
        Random random = new Random(7601L);
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        random.nextBytes(key);
        random.nextBytes(iv);
        byte[] plaintext = ("Invented sampling fixture for assignment 76.\n".repeat(6))
                .getBytes(StandardCharsets.US_ASCII);
        byte[] ciphertext = SymmetricCipher.encrypt(plaintext, key, "AES-128", "CBC", "PKCS5Padding", iv, null);
        Path source = dir.resolve(scenario + ".bin");
        Files.write(source, ciphertext);
        boolean sampled = scenario.equals("sampled");
        var options = new EncryptedFileAnalyzer.FileAnalysisOptions(new int[] {64}, true, false, 8,
                EncryptedFileAnalyzer.FileDataEncoding.RAW, sampled ? 32 : 262144);
        byte[] analysisKey = sampled ? key : new byte[] {76};
        var outcome = new EncryptedFileAnalyzer(new EncryptedFileAnalyzer.CipherInputs(
                DataConverter.bytesToHex(iv), "", false, "", false)).analyze(source, analysisKey, options);
        assertEquals(sampled, outcome.hasCandidate());
        assertEquals(sampled ? 32 : ciphertext.length, outcome.analysedBytes().length);
        if (sampled) assertTrue(outcome.reportText().contains(" (sampled)"));
        else assertTrue(outcome.reportText().contains("No valid decryption candidates found."));
        Path html;
        try (Stream<Path> files = Files.walk(dir)) {
            html = files.filter(path -> path.getFileName().toString().equals("report.html"))
                    .findFirst().orElseThrow();
        }
        String text = Files.readString(html.resolveSibling("report.txt"));
        assertEquals(outcome.reportText(), text);
        assertAll(scenario,
                () -> assertEquals(textDigest, digest(text)),
                () -> assertEquals(htmlDigest, digest(Files.readString(html))));
    }

    private String digest(String value) throws Exception {
        String normalized = value.replace(dir.toAbsolutePath().toString(), "<DIR>")
                .replaceAll("analysis_([A-Za-z0-9._-]+?)_\\d{8}_\\d{6}", "analysis_$1_<TS>")
                .replace("\r\n", "\n");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }
}
