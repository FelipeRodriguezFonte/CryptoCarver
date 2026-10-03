package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.ShelfPackage;
import com.cryptocarver.util.DataConverter;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Symmetric encrypt/decrypt and the template bar of cipher.fxml, pinned before they move
 * out of CipherController. Each scenario records the output area, status lines, errors,
 * notices and published results; the transcript is fixed with SHA-256.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class SymmetricCipherCharacterizationUITest {
    private static final String KEY_128 = "000102030405060708090A0B0C0D0E0F";
    private static final String KEY_256 = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
    private static final String IV_16 = "0F0E0D0C0B0A09080706050403020100";
    private static final String NONCE_12 = "CAFEBABEFACEDBADDECAF888";
    private static final String NONCE_24 = "000102030405060708090A0B0C0D0E0F1011121314151617";
    private static final String NONCE_8 = "0001020304050607";
    private static final String AAD = "FEEDFACEDEADBEEF";
    private static final String MESSAGE = "Symmetric characterization message";

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
    void blockCipherRoundTripsAreUnchanged() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.select("AES-128", "CBC", "PKCS5Padding");
            panel.material(KEY_128, IV_16, "", "");
            panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
            panel.encrypt();
            String ciphertext = panel.output().getText();
            transcript.add(panel.snapshot("cbc encrypt"));

            panel.inputs("Hexadecimal", ciphertext, "Text (UTF-8)");
            panel.decrypt();
            assertEquals(MESSAGE, panel.output().getText());
            transcript.add(panel.snapshot("cbc decrypt"));

            panel.select("AES-128", "ECB", "PKCS5Padding");
            panel.inputs("Text (UTF-8)", MESSAGE, "Base64");
            panel.encrypt();
            transcript.add(panel.snapshot("ecb encrypt base64"));

            panel.select("AES-256", "CTR", "NoPadding");
            panel.material(KEY_256, IV_16, "", "");
            panel.inputs("Text (UTF-8)", MESSAGE, "Binary");
            panel.encrypt();
            transcript.add(panel.snapshot("ctr encrypt binary"));
            assertNull(panel.controller().createAuthenticatedCipherShelfPackage());
        });
        assertEquals("f7ab18ed3cda438a5f480e4495c851db715a328bc1b7290f4a9134d62736712d", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void gcmResultSplitsTheTagAndFeedsTheShelfPackage() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.select("AES-256", "GCM", "NoPadding");
            panel.material(KEY_256, NONCE_12, "", AAD);
            panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
            panel.encrypt();
            transcript.add(panel.snapshot("gcm encrypt"));
            ShelfPackage shelf = panel.controller().createAuthenticatedCipherShelfPackage();
            transcript.add("shelf " + new TreeMap<>(shelf.getArtifacts()));

            String text = panel.output().getText();
            String ciphertext = text.split("\n")[3];
            String tag = text.split("\n")[6];
            panel.material(KEY_256, NONCE_12, tag, AAD);
            panel.inputs("Hexadecimal", ciphertext, "Text (UTF-8)");
            panel.decrypt();
            transcript.add(panel.snapshot("gcm decrypt"));

            panel.material(KEY_256, NONCE_12, "00".repeat(16), AAD);
            panel.decrypt();
            transcript.add(panel.snapshot("gcm wrong tag"));

            panel.material(KEY_256, NONCE_12, "00", AAD);
            panel.decrypt();
            transcript.add(panel.snapshot("gcm short tag"));

            panel.material(KEY_256, NONCE_12, "", AAD);
            panel.inputs("Text (UTF-8)", MESSAGE, "Base64");
            panel.encrypt();
            transcript.add(panel.snapshot("gcm nonce reuse base64"));
            transcript.add("shelf " + new TreeMap<>(panel.controller().createAuthenticatedCipherShelfPackage().getArtifacts()));
        });
        assertEquals("2381b09b35ffaf756bbea30876f2c76a599df24c5389168db9a6a14b48549d72", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void streamCiphersRoundTripAndRequireTheirTags() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            String[][] cases = {
                    {"ChaCha20", NONCE_12}, {"Salsa20", NONCE_8},
                    {"ChaCha20-Poly1305", NONCE_12}, {"XChaCha20-Poly1305", NONCE_24}};
            for (String[] each : cases) {
                String algorithm = each[0];
                panel.select(algorithm, null, null);
                panel.material(KEY_256, each[1], "", "");
                panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
                panel.encrypt();
                transcript.add(panel.snapshot(algorithm + " encrypt"));
                String text = panel.output().getText();
                boolean aead = algorithm.endsWith("Poly1305");
                String ciphertext = aead ? text.split("\n")[3] : text;
                String tag = aead ? text.split("\n")[6] : "";
                if (aead) {
                    panel.inputs("Hexadecimal", ciphertext, "Text (UTF-8)");
                    panel.decrypt();
                    transcript.add(panel.snapshot(algorithm + " decrypt without tag"));
                }
                panel.material(KEY_256, each[1], tag, "");
                panel.inputs("Hexadecimal", ciphertext, "Text (UTF-8)");
                panel.decrypt();
                transcript.add(panel.snapshot(algorithm + " decrypt"));
            }
        });
        assertEquals("ae6001a3026f74d66c2eef189b932c0ea0514d27f622f71b91f94c7224770380", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void salsa20UsesItsOwnEightByteNonceAndTheNoncePromptMatchesEachCipher() throws Exception {
        withPanel(panel -> {
            panel.select("Salsa20", null, null);
            assertEquals("Hex Nonce (8 bytes recommended for Salsa20)", panel.field("ivField").getPromptText());
            panel.material(KEY_256, NONCE_8, "", "");
            panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
            panel.encrypt();
            try {
                byte[] expected = com.cryptocarver.crypto.SymmetricCipher.encryptSalsa20(
                        MESSAGE.getBytes(StandardCharsets.UTF_8), DataConverter.hexToBytes(KEY_256),
                        DataConverter.hexToBytes(NONCE_8));
                assertEquals(DataConverter.bytesToHex(expected), panel.output().getText());
            } catch (Exception error) {
                throw new AssertionError(error);
            }

            panel.select("ChaCha20", null, null);
            assertEquals("Hex Nonce (12 bytes recommended for ChaCha20)", panel.field("ivField").getPromptText());
        });
    }

    @Test
    void reusingANonceWarnsForEveryKeystreamCipherButNotForCbc() throws Exception {
        String warning = "info Nonce reuse warning: This IV/nonce has already been used with the same key "
                + "in this session. Generate a fresh value before encrypting.";
        String[][] cases = {{"ChaCha20", null, NONCE_12}, {"Salsa20", null, NONCE_8},
                {"ChaCha20-Poly1305", null, NONCE_12}, {"XChaCha20-Poly1305", null, NONCE_24},
                {"AES-256", "CTR", IV_16}, {"AES-256", "CBC", IV_16}};
        for (String[] each : cases) {
            // A fresh panel per cipher: the same key and nonce across ciphers would warn too.
            withPanel(panel -> {
                panel.select(each[0], each[1], each[1] == null ? null : "PKCS5Padding");
                panel.material(KEY_256, each[2], "", "");
                panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
                panel.encrypt();
                assertTrue(!panel.reporter().drain().contains("Nonce reuse"), each[0] + " first use");
                panel.encrypt();
                String second = panel.reporter().drain();
                assertEquals(!"CBC".equals(each[1]), second.contains(warning), each[0] + "/" + each[1] + ": " + second);
            });
        }
    }

    @Test
    void decryptingWithoutAKeySaysTheKeyIsMissing() throws Exception {
        withPanel(panel -> {
            panel.select("AES-128", "CBC", "PKCS5Padding");
            panel.material("", IV_16, "", "");
            panel.inputs("Hexadecimal", "00".repeat(16), "Hexadecimal");
            panel.decrypt();
            assertEquals("error Validation Error: Enter the symmetric key or select a Key Lab entry",
                    panel.reporter().drain());
            // The main window maps messages by their wording; this one must not read as bad hex.
            UserFacingError shown = UserFacingErrorMapper.map("Validation Error",
                    "Enter the symmetric key or select a Key Lab entry", null);
            assertEquals("Validation Error", shown.title());
        });
    }

    @Test
    void missingInputsStopBeforeAnyOperation() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.select("AES-128", "CBC", "PKCS5Padding");
            panel.material(KEY_128, IV_16, "", "");
            panel.inputs("Text (UTF-8)", "", "Hexadecimal");
            panel.encrypt();
            panel.decrypt();
            transcript.add(panel.snapshot("empty input"));

            panel.material("", IV_16, "", "");
            panel.inputs("Text (UTF-8)", MESSAGE, "Hexadecimal");
            panel.encrypt();
            panel.decrypt();
            transcript.add(panel.snapshot("missing key"));

            panel.material(KEY_128, "", "", "");
            panel.encrypt();
            panel.decrypt();
            transcript.add(panel.snapshot("missing iv"));

            panel.material(KEY_128, IV_16, "", "");
            panel.inputs("Hexadecimal", "XYZ", "Hexadecimal");
            panel.encrypt();
            transcript.add(panel.snapshot("bad hex input"));
        });
        assertEquals("60320302bb8af5de8921bca94fb9cbb3bba5f3ba2b9811807baa32a1e687226c", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void builtInTemplatesAndResetSetTheSameControls() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.material(KEY_128, IV_16, "AA", "BB");
            panel.template("AES-256-GCM — Text UTF-8 → Base64");
            transcript.add(panel.snapshot("gcm template"));

            panel.material(KEY_128, IV_16, "AA", "BB");
            panel.template("AES-256-CBC — Hex → Hex");
            transcript.add(panel.snapshot("cbc template"));

            panel.select("DES", "ECB", "NoPadding");
            panel.material(KEY_128, IV_16, "AA", "BB");
            panel.invoke("handleResetCipherDefaults");
            transcript.add(panel.snapshot("reset"));
        });
        assertEquals("24ad825113dd46296a99b924e29fbb5b411fb977a6e00b50eeb8879b78d95852", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void personalTemplatesSetTheToolbarFormats() throws Exception {
        String name = "Cipher formats " + java.util.UUID.randomUUID();
        com.cryptocarver.model.PersonalTemplateStore store = com.cryptocarver.model.PersonalTemplateStore.getInstance();
        com.cryptocarver.model.SafeOperationTemplate saved = new com.cryptocarver.model.SafeOperationTemplate(name, "Cipher", "formats-only",
                java.util.Map.of("symmetricAlgorithmCombo", "AES-128", "inputFormatCombo", "Hexadecimal",
                        "outputFormatCombo", "Base64"));
        store.saveTemplate(saved);
        try {
            withPanel(panel -> {
                panel.inputs("Text (UTF-8)", "", "Hexadecimal");
                panel.template("[My Template] " + name);
                assertEquals("AES-128", panel.combo("symmetricAlgorithmCombo").getValue());
                assertEquals("Hexadecimal", panel.inputFormat().getValue());
                assertEquals("Base64", panel.outputFormat().getValue());
            });
        } finally {
            store.deleteTemplate(saved.getId());
        }
    }

    private static String digest(List<String> transcript) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private void withPanel(Consumer<Panel> body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader("/fxml/cipher.fxml");
                loader.load();
                CipherController controller = loader.getController();
                ComboBox<String> inputFormat = new ComboBox<>();
                inputFormat.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary");
                ComboBox<String> outputFormat = new ComboBox<>();
                outputFormat.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary");
                Recorder reporter = new Recorder(inputFormat, outputFormat);
                controller.initModern(reporter, inputFormat, outputFormat, null);
                body.accept(new Panel(loader, controller, reporter, inputFormat, outputFormat));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private record Panel(FXMLLoader loader, CipherController controller, Recorder reporter,
                         ComboBox<String> inputFormat, ComboBox<String> outputFormat) {
        @SuppressWarnings("unchecked")
        ComboBox<String> combo(String id) { return (ComboBox<String>) loader.getNamespace().get(id); }
        TextField field(String id) { return (TextField) loader.getNamespace().get(id); }
        TextArea input() { return (TextArea) loader.getNamespace().get("cipherInputArea"); }
        TextArea output() { return (TextArea) loader.getNamespace().get("cipherOutputArea"); }

        void select(String algorithm, String mode, String padding) {
            combo("symmetricAlgorithmCombo").setValue(algorithm);
            if (mode != null) combo("cipherModeCombo").setValue(mode);
            if (padding != null) combo("paddingCombo").setValue(padding);
        }

        void material(String key, String iv, String tag, String aad) {
            field("symmetricKeyField").setText(key);
            field("ivField").setText(iv);
            field("gcmTagField").setText(tag);
            field("aadField").setText(aad);
        }

        void inputs(String inputFormatValue, String text, String outputFormatValue) {
            inputFormat.setValue(inputFormatValue);
            outputFormat.setValue(outputFormatValue);
            input().setText(text);
        }

        void encrypt() { controller.handleSymmetricEncrypt(); }

        void decrypt() { controller.handleSymmetricDecrypt(); }

        void template(String name) {
            combo("cipherTemplateCombo").setValue(name);
            invoke("handleApplyCipherTemplate");
        }

        void invoke(String handler) {
            try {
                Method method = CipherController.class.getDeclaredMethod(handler);
                method.setAccessible(true);
                method.invoke(controller);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }

        String snapshot(String step) {
            String controls = String.join(",",
                    combo("symmetricAlgorithmCombo").getValue(),
                    combo("cipherModeCombo").getValue(),
                    combo("paddingCombo").getValue(),
                    combo("symKeySourceCombo").getValue(),
                    inputFormat.getValue(), outputFormat.getValue(),
                    field("symmetricKeyField").getText(), field("ivField").getText(),
                    field("ivField").getPromptText(),
                    field("gcmTagField").getText(), field("aadField").getText());
            String result = "## " + step + "\ncontrols " + controls + "\noutput " + output().getText()
                    + "\n" + reporter.drain();
            return result;
        }
    }

    private static final class Recorder implements StatusReporter {
        private final ComboBox<String> inputFormat;
        private final ComboBox<String> outputFormat;
        private final List<String> lines = new ArrayList<>();

        Recorder(ComboBox<String> inputFormat, ComboBox<String> outputFormat) {
            this.inputFormat = inputFormat;
            this.outputFormat = outputFormat;
        }

        String drain() {
            String text = String.join("\n", lines);
            lines.clear();
            return text;
        }

        @Override
        public void updateStatus(String message) {
            lines.add("status " + message);
        }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
            lines.add("inspector " + operation);
        }

        @Override
        public void showError(String title, String message) {
            lines.add("error " + title + ": " + message);
        }

        @Override
        public void showInfo(String title, String message) {
            lines.add("info " + title + ": " + message);
        }

        @Override
        public void setInputFormat(String format) {
            lines.add("input format " + format);
            inputFormat.setValue(format);
        }

        @Override
        public void setOutputFormat(String format) {
            lines.add("output format " + format);
            outputFormat.setValue(format);
        }

        @Override
        public void publish(OperationResult result) {
            List<String> details = result.getDetails().stream()
                    .map(detail -> detail.name() + "=" + detail.value())
                    .sorted()
                    .toList();
            lines.add("publish " + result.getOperation() + "|" + result.getStatusMessage() + "|"
                    + String.join(",", details) + "|" + DataConverter.bytesToHex(result.getInput())
                    + "|" + DataConverter.bytesToHex(result.getOutput())
                    + "|" + (result.getEnrichedOutput() == null ? "-" : result.getEnrichedOutput().length()));
        }
    }
}
