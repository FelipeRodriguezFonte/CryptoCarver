package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour of the streaming file-cipher panel in cipher.fxml, pinned before it moves to its own coordinator. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class FileCipherPanelCharacterizationUITest {
    private static final String KEY_256 = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
    private static final String GCM_NONCE = "CAFEBABEFACEDBADDECAF888";
    private static final String CBC_IV = "0F0E0D0C0B0A09080706050403020100";
    private static final String AAD = "FEEDFACEDEADBEEF";

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
    void panelStartsWithTheFourAlgorithmsAndLineModeOff() throws Exception {
        withPanel(panel -> {
            assertEquals(List.of("AES-256-GCM", "AES-256-CTR", "AES-256-CBC", "ChaCha20-Poly1305"),
                    panel.algorithm().getItems());
            assertEquals("AES-256-GCM", panel.algorithm().getValue());
            assertEquals(List.of("Base64URL", "Hexadecimal"), panel.lineEncoding().getItems());
            assertEquals("Base64URL", panel.lineEncoding().getValue());
            assertEquals("UTF-8", panel.lineCharset().getItems().get(0));
            assertEquals("UTF-8", panel.lineCharset().getValue());
            assertTrue(panel.lineCharset().getItems().size() > 1, "EBCDIC code pages are offered");
            assertFalse(panel.nonce().isDisabled());
            assertFalse(panel.tag().isDisabled());
            assertTrue(panel.lineEncoding().isDisabled());
            assertTrue(panel.lineCharset().isDisabled());
            assertTrue(panel.compact().isDisabled());
        });
    }

    @Test
    void lineModeDisablesTheNonceExceptForCbcAndAlwaysDisablesTheTag() throws Exception {
        withPanel(panel -> {
            panel.lines().setSelected(true);
            assertTrue(panel.nonce().isDisabled());
            assertTrue(panel.tag().isDisabled());
            assertFalse(panel.lineEncoding().isDisabled());
            assertFalse(panel.lineCharset().isDisabled());
            assertFalse(panel.compact().isDisabled());

            panel.algorithm().setValue("AES-256-CBC");
            assertFalse(panel.nonce().isDisabled());
            assertTrue(panel.tag().isDisabled());

            panel.lines().setSelected(false);
            assertFalse(panel.nonce().isDisabled());
            assertFalse(panel.tag().isDisabled());
            assertTrue(panel.compact().isDisabled());
        });
    }

    @Test
    void generatedNonceIsTwelveBytesForAeadAndSixteenForCbc() throws Exception {
        withPanel(panel -> {
            panel.controller().generateFileCipherNonce();
            assertEquals(24, panel.nonce().getText().length());
            panel.algorithm().setValue("AES-256-CBC");
            panel.controller().generateFileCipherNonce();
            assertEquals(32, panel.nonce().getText().length());
            assertEquals(List.of("Generated fresh 12-byte IV/nonce for file cipher",
                    "Generated fresh 16-byte IV/nonce for file cipher"), panel.reporter().statuses);
        });
    }

    @Test
    void gcmFileRoundTripsWithDetachedTagAndPublishesBothResults() throws Exception {
        byte[] plain = "Streaming file cipher characterization payload.\n".repeat(40).getBytes(StandardCharsets.UTF_8);
        Path source = write("plain.txt", plain);
        Path encrypted = dir.resolve("plain.enc");
        Path tag = dir.resolve("plain.tag");
        Path decrypted = dir.resolve("plain.dec");

        withPanel(panel -> {
            panel.fill(source, encrypted, tag, KEY_256, GCM_NONCE, AAD);
            panel.controller().handleFileCipherEncrypt();
            assertEquals("File encrypted successfully\nAlgorithm: AES-256-GCM\nInput: " + plain.length
                    + " bytes\nOutput: " + plain.length + " bytes\nAEAD tag: " + tag, panel.result().getText());

            panel.source().setText(encrypted.toString());
            panel.destination().setText(decrypted.toString());
            panel.controller().handleFileCipherDecrypt();
            assertEquals("File decrypted successfully\nAlgorithm: AES-256-GCM\nInput: " + plain.length
                    + " bytes\nOutput: " + plain.length + " bytes\nAEAD tag: " + tag, panel.result().getText());

            assertEquals(List.of(), panel.reporter().errors);
            assertEquals(List.of(
                    "File Encrypt|File encrypted using AES-256-GCM|Algorithm=AES-256-GCM,Authenticated=true,Input bytes="
                            + plain.length + ",Output bytes=" + plain.length,
                    "File Decrypt|File decrypted using AES-256-GCM|Algorithm=AES-256-GCM,Authenticated=true,Input bytes="
                            + plain.length + ",Output bytes=" + plain.length), panel.reporter().published);
        });

        assertTrue(Files.isRegularFile(tag));
        assertEquals(16, Files.size(tag));
        assertArrayEquals(plain, Files.readAllBytes(decrypted));
    }

    @Test
    void cbcLineModeRoundTripsEachRecordInHexadecimal() throws Exception {
        String lines = "first record\nsecond record\nthird record\n";
        Path source = write("records.txt", lines.getBytes(StandardCharsets.UTF_8));
        Path encrypted = dir.resolve("records.enc");
        Path decrypted = dir.resolve("records.dec");

        withPanel(panel -> {
            panel.algorithm().setValue("AES-256-CBC");
            panel.lines().setSelected(true);
            panel.lineEncoding().setValue("Hexadecimal");
            panel.fill(source, encrypted, null, KEY_256, CBC_IV, "");
            panel.controller().handleFileCipherEncrypt();
            assertTrue(panel.result().getText().startsWith("File encrypted successfully\nAlgorithm: AES-256-CBC\n"),
                    panel.result().getText());
            assertTrue(panel.result().getText().endsWith("\nRecords: 3 (independently authenticated)"),
                    panel.result().getText());

            panel.source().setText(encrypted.toString());
            panel.destination().setText(decrypted.toString());
            panel.controller().handleFileCipherDecrypt();
            assertEquals(List.of(), panel.reporter().errors);
            assertTrue(panel.reporter().published.get(1).contains("Records=3"), panel.reporter().published.toString());
        });

        // Current behaviour: records come back joined by newlines, without the final one.
        assertEquals(lines.stripTrailing(), Files.readString(decrypted));
        for (String record : Files.readAllLines(encrypted)) {
            assertTrue(record.matches("CF-LINE-1-CBC-HEX\\.[0-9a-f]+"), record);
        }
    }

    @Test
    void invalidInputsAreReportedWithTheSameMessages() throws Exception {
        Path source = write("plain.txt", "data".getBytes(StandardCharsets.UTF_8));

        withPanel(panel -> {
            panel.fill(source, dir.resolve("out.bin"), dir.resolve("out.tag"), "", GCM_NONCE, "");
            panel.controller().handleFileCipherEncrypt();

            panel.fill(source, source, dir.resolve("out.tag"), KEY_256, GCM_NONCE, "");
            panel.controller().handleFileCipherEncrypt();

            panel.fill(dir.resolve("missing.bin"), dir.resolve("out.bin"), dir.resolve("out.tag"), KEY_256, GCM_NONCE, "");
            panel.controller().handleFileCipherEncrypt();

            panel.fill(source, dir.resolve("out.bin"), dir.resolve("absent.tag"), KEY_256, GCM_NONCE, "");
            panel.controller().handleFileCipherDecrypt();

            panel.fill(source, dir.resolve("out.bin"), dir.resolve("out.tag"), KEY_256, GCM_NONCE, "zz");
            panel.controller().handleFileCipherEncrypt();

            assertEquals(List.of(
                    "File Cipher: Cannot process file: Key is required",
                    "File Cipher: Cannot process file: Source and output file must be different",
                    "File Cipher: Cannot process file: Source file does not exist or is not a regular file",
                    "File Cipher: Cannot process file: Detached tag file does not exist",
                    "File Cipher: Cannot process file: AAD must be hexadecimal"), panel.reporter().errors);
        });
    }

    private Path write(String name, byte[] bytes) throws Exception {
        Path file = dir.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    private void withPanel(Consumer<Panel> body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader("/fxml/cipher.fxml");
                loader.load();
                CipherController controller = loader.getController();
                Recorder reporter = new Recorder();
                ComboBox<String> outputFormat = new ComboBox<>();
                outputFormat.setValue("Hexadecimal");
                controller.initModern(reporter, new ComboBox<>(), outputFormat, null);
                body.accept(new Panel(loader, controller, reporter));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private record Panel(FXMLLoader loader, CipherController controller, Recorder reporter) {
        @SuppressWarnings("unchecked")
        ComboBox<String> algorithm() { return (ComboBox<String>) loader.getNamespace().get("fileCipherAlgorithmCombo"); }
        @SuppressWarnings("unchecked")
        ComboBox<String> lineEncoding() { return (ComboBox<String>) loader.getNamespace().get("fileCipherLineEncodingCombo"); }
        @SuppressWarnings("unchecked")
        ComboBox<String> lineCharset() { return (ComboBox<String>) loader.getNamespace().get("fileCipherLineCharsetCombo"); }
        TextField source() { return (TextField) loader.getNamespace().get("fileCipherSourceField"); }
        TextField destination() { return (TextField) loader.getNamespace().get("fileCipherDestinationField"); }
        TextField tag() { return (TextField) loader.getNamespace().get("fileCipherTagField"); }
        TextField key() { return (TextField) loader.getNamespace().get("fileCipherKeyField"); }
        TextField nonce() { return (TextField) loader.getNamespace().get("fileCipherNonceField"); }
        TextField aad() { return (TextField) loader.getNamespace().get("fileCipherAadField"); }
        CheckBox lines() { return (CheckBox) loader.getNamespace().get("fileCipherLinesCheck"); }
        CheckBox compact() { return (CheckBox) loader.getNamespace().get("fileCipherCompactCbcCheck"); }
        TextArea result() { return (TextArea) loader.getNamespace().get("fileCipherResultArea"); }

        void fill(Path sourcePath, Path destinationPath, Path tagPath, String keyHex, String nonceHex, String aadHex) {
            source().setText(sourcePath.toString());
            destination().setText(destinationPath.toString());
            tag().setText(tagPath == null ? "" : tagPath.toString());
            key().setText(keyHex);
            nonce().setText(nonceHex);
            aad().setText(aadHex);
        }
    }

    private static final class Recorder implements StatusReporter {
        private final List<String> statuses = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> published = new ArrayList<>();

        @Override
        public void updateStatus(String message) {
            statuses.add(message);
        }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
        }

        @Override
        public void showError(String title, String message) {
            errors.add(title + ": " + message);
        }

        @Override
        public void publish(OperationResult result) {
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + detail.value())
                    .sorted()
                    .toList();
            published.add(result.getOperation() + "|" + result.getStatusMessage() + "|" + String.join(",", details));
        }
    }
}
