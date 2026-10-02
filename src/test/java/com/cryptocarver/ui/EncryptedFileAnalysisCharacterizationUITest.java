package com.cryptocarver.ui;

import com.cryptocarver.crypto.SymmetricCipher;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.utils.DataConverter;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the encrypted-file analysis report, attempt log and inspector output byte for byte
 * (after normalising paths and timestamps) so the analysis engine can move out of
 * CipherController without changing what the user sees or what lands on disk.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EncryptedFileAnalysisCharacterizationUITest {
    private static final String KEY_128 = "000102030405060708090A0B0C0D0E0F";
    private static final String IV_128 = "0F0E0D0C0B0A09080706050403020100";
    private static final String GCM_NONCE = "CAFEBABEFACEDBADDECAF888";
    private static final String AAD = "FEEDFACEDEADBEEF";
    private static final byte[] PLAINTEXT = ("Encrypted file analysis characterization: plain ASCII text "
            + "long enough to span several AES blocks.\n").getBytes(StandardCharsets.US_ASCII);

    @TempDir Path dir;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test
    void rawCbcCiphertextReportIsUnchanged() throws Exception {
        byte[] ciphertext = encrypt("AES-128", "CBC", "PKCS5Padding", IV_128, null);
        Path source = write("cbc.bin", ciphertext);

        Run run = analyse(source, KEY_128, IV_128, "", null);

        assertTrue(run.result.contains("=== ENCRYPTED FILE ANALYSIS REPORT ==="), run.result);
        assertTrue(run.result.contains("AES-128"), run.result);
        assertEquals("d735fff3cbd37e16850546f3dfa3b89239abfb0226c7e471c1fdf956b160e1c3", run.digest(), run.dump());
    }

    @Test
    void base64EncodedCiphertextReportIsUnchanged() throws Exception {
        byte[] ciphertext = encrypt("AES-128", "CBC", "PKCS5Padding", IV_128, null);
        Path source = write("cbc.b64", Base64.getEncoder().encode(ciphertext));

        Run run = analyse(source, KEY_128, IV_128, "", null);

        assertTrue(run.result.contains("BASE64"), run.result);
        assertEquals("afac2d1cd206da235e1fed6b740a70424ebeee91e94dbf4dce6b0a6c64a60ca5", run.digest(), run.dump());
    }

    @Test
    void independentBlockContainerReportIsUnchanged() throws Exception {
        Path source = write("blocks.bin", independentBlocks(64));

        Run run = analyse(source, KEY_128, "", "", null);

        assertTrue(run.result.contains("INDEPENDENT"), run.result);
        assertEquals("42af07e8226e587d542e396961466b894832a6519be3458fc4b786d981def79d", run.digest(), run.dump());
    }

    @Test
    void gcmCiphertextWithDetachedTagReportIsUnchanged() throws Exception {
        byte[] sealed = encrypt("AES-128", "GCM", "NoPadding", GCM_NONCE, DataConverter.hexToBytes(AAD));
        byte[] ciphertext = java.util.Arrays.copyOf(sealed, sealed.length - 16);
        byte[] tag = java.util.Arrays.copyOfRange(sealed, sealed.length - 16, sealed.length);
        Path source = write("gcm.bin", ciphertext);
        Path tagFile = write("gcm.tag", tag);

        Run run = analyse(source, KEY_128, GCM_NONCE, AAD, tagFile);

        assertTrue(run.result.contains("GCM"), run.result);
        assertEquals("42fa342b6af73c6110624ae90de08e73ed1024ef5c170d4acc17062bc093d3c2", run.digest(), run.dump());
    }

    @Test
    void missingTagFileIsReportedWithoutRunningTheAnalysis() throws Exception {
        Path source = write("cbc.bin", encrypt("AES-128", "CBC", "PKCS5Padding", IV_128, null));

        Run run = analyse(source, KEY_128, IV_128, "", dir.resolve("absent.tag"));

        assertEquals(List.of("Encrypted File Analysis: Tag file does not exist or is not a regular file: <DIR>/absent.tag"),
                run.errors);
        assertTrue(run.files.isEmpty(), "No analysis directory is created");
    }

    @Test
    void missingKeyIsReportedWithoutRunningTheAnalysis() throws Exception {
        Path source = write("cbc.bin", encrypt("AES-128", "CBC", "PKCS5Padding", IV_128, null));

        Run run = analyse(source, "", IV_128, "", null);

        assertEquals(1, run.errors.size(), run.dump());
        assertTrue(run.errors.get(0).startsWith("Encrypted File Analysis: "), run.dump());
        assertTrue(run.files.isEmpty(), "No analysis directory is created");
    }

    private byte[] encrypt(String algorithm, String mode, String padding, String iv, byte[] aad) throws Exception {
        byte[] ivBytes = iv.isEmpty() ? null : DataConverter.hexToBytes(iv);
        return SymmetricCipher.encrypt(PLAINTEXT, DataConverter.hexToBytes(KEY_128), algorithm, mode, padding, ivBytes, aad);
    }

    /** The CFXBI1 container the analysis recognises: magic, block size, count, then length-prefixed chunks. */
    private byte[] independentBlocks(int blockSize) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = (PLAINTEXT.length + blockSize - 1) / blockSize;
        out.write("CFXBI1".getBytes(StandardCharsets.US_ASCII));
        out.write(ByteBuffer.allocate(4).putInt(blockSize).array());
        out.write(ByteBuffer.allocate(4).putInt(count).array());
        for (int offset = 0; offset < PLAINTEXT.length; offset += blockSize) {
            byte[] chunk = java.util.Arrays.copyOfRange(PLAINTEXT, offset, Math.min(offset + blockSize, PLAINTEXT.length));
            byte[] encrypted = SymmetricCipher.encrypt(chunk, DataConverter.hexToBytes(KEY_128), "AES-128", "ECB",
                    "PKCS5Padding", null, null);
            out.write(ByteBuffer.allocate(4).putInt(encrypted.length).array());
            out.write(encrypted);
        }
        return out.toByteArray();
    }

    private Path write(String name, byte[] bytes) throws Exception {
        Path file = dir.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    private Run analyse(Path source, String key, String nonce, String aad, Path tag) throws Exception {
        Run run = new Run();
        onFxThread(() -> {
            FXMLLoader loader = UiTestFxml.loader("/fxml/cipher.fxml");
            try {
                loader.load();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            CipherController controller = loader.getController();
            ComboBox<String> outputFormat = new ComboBox<>();
            outputFormat.setValue("Hexadecimal");
            controller.initModern(run, new ComboBox<>(), outputFormat, null);
            text(loader, "fileCipherSourceField").setText(source.toString());
            text(loader, "fileCipherKeyField").setText(key);
            text(loader, "fileCipherNonceField").setText(nonce);
            text(loader, "fileCipherAadField").setText(aad);
            text(loader, "fileCipherTagField").setText(tag == null ? "" : tag.toString());
            controller.handleAnalyzeFileCipher();
            run.result = ((TextArea) loader.getNamespace().get("fileCipherResultArea")).getText();
        });
        run.collect(dir);
        return run;
    }

    private static TextField text(FXMLLoader loader, String id) {
        return (TextField) loader.getNamespace().get(id);
    }

    private static void onFxThread(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(120, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private final class Run implements StatusReporter {
        private final List<String> statuses = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> inspector = new ArrayList<>();
        private final List<String> files = new ArrayList<>();
        private String result = "";

        @Override
        public void updateStatus(String message) {
            statuses.add(normalise(message));
        }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
            inspector.add(operation);
            inspector.add(HexFormat.of().formatHex(input));
            inspector.add(HexFormat.of().formatHex(output));
            details.stream()
                    .map(detail -> detail.name() + "=" + normalise(detail.value()))
                    .sorted()
                    .forEach(inspector::add);
        }

        @Override
        public void showError(String title, String message) {
            errors.add(normalise(title + ": " + message));
        }

        private void collect(Path root) throws Exception {
            result = normalise(result);
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList())) {
                    if (file.getParent().equals(root)) continue;
                    files.add(normalise(root.relativize(file).toString()));
                    files.add(normalise(Files.readString(file, StandardCharsets.UTF_8)));
                }
            }
        }

        private String normalise(String value) {
            if (value == null) return "";
            String withoutDir = value.replace(dir.toAbsolutePath().toString(), "<DIR>").replace(dir.toString(), "<DIR>");
            return withoutDir.replaceAll("analysis_([A-Za-z0-9._-]+?)_\\d{8}_\\d{6}", "analysis_$1_<TS>");
        }

        private String dump() {
            return String.join("\n", List.of(String.join("\n", statuses), String.join("\n", errors),
                    String.join("\n", inspector), result, String.join("\n", files)));
        }

        private String digest() throws Exception {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(dump().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
    }
}
