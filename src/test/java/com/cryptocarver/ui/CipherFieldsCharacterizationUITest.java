package com.cryptocarver.ui;

import com.cryptocarver.crypto.hsm.KeyExportability;
import com.cryptocarver.crypto.hsm.KeyMaterial;
import com.cryptocarver.crypto.hsm.KeyMaterialFactory;
import com.cryptocarver.crypto.hsm.KeyUsage;
import com.cryptocarver.crypto.hsm.SimulatedHsmProvider;
import com.cryptocarver.model.OperationDetail;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The symmetric panel's field states (visibility per algorithm and mode, material badges,
 * padding lock), the key source and Key Lab selection, IV generation and FPE, pinned before
 * they move out of CipherController. Each transcript is fixed with SHA-256.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class CipherFieldsCharacterizationUITest {
    private static final String KEY_128 = "000102030405060708090A0B0C0D0E0F";
    private static final String KEY_256 = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

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

    @BeforeEach
    void isolateKeyLab() throws Exception {
        Files.createDirectories(Path.of("target/test-home"));
        SimulatedHsmProvider.getInstance().resetForTest(
                Files.createTempFile(Path.of("target/test-home"), "cipher-fields-lab-", ".json"));
    }

    @AfterEach
    void clearKeyLab() {
        SimulatedHsmProvider.getInstance().clear();
    }

    @Test
    void fieldStatesFollowAlgorithmAndMode() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add(panel.fields("initial"));
            String[][] cases = {
                    {"AES-128", "ECB"}, {"AES-128", "CBC"}, {"AES-192", "CTR"}, {"AES-256", "GCM"},
                    {"DES", "CBC"}, {"3DES (Triple DES)", "OFB"}, {"Salsa20", null}, {"ChaCha20", null},
                    {"ChaCha20-Poly1305", null}, {"XChaCha20-Poly1305", null}, {"AES-256", "CFB"}};
            for (String[] each : cases) {
                panel.combo("symmetricAlgorithmCombo").setValue(each[0]);
                if (each[1] != null) panel.combo("cipherModeCombo").setValue(each[1]);
                transcript.add(panel.fields(each[0] + "/" + each[1]));
            }
        });
        assertEquals("703db4ddb5a2d2d7a0d5311a569f4e22ac0122d2896648da05adf7e59710ad26", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void badgesReflectMaterialLengths() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            panel.combo("symmetricAlgorithmCombo").setValue("AES-128");
            panel.combo("cipherModeCombo").setValue("CBC");
            panel.field("symmetricKeyField").setText(KEY_128);
            panel.field("ivField").setText("00");
            transcript.add(panel.fields("aes128 key ok iv short"));
            panel.combo("symmetricAlgorithmCombo").setValue("AES-256");
            transcript.add(panel.fields("aes256 with 16-byte key"));
            panel.combo("symmetricAlgorithmCombo").setValue("3DES (Triple DES)");
            transcript.add(panel.fields("3des with 16-byte key"));
            panel.combo("symmetricAlgorithmCombo").setValue("AES-256");
            panel.combo("cipherModeCombo").setValue("GCM");
            panel.field("symmetricKeyField").setText(KEY_256);
            panel.field("ivField").setText("CAFEBABEFACEDBADDECAF888");
            panel.field("gcmTagField").setText("00");
            panel.field("aadField").setText("hello");
            transcript.add(panel.fields("gcm material"));
            panel.field("symmetricKeyField").setText("XYZ");
            transcript.add(panel.fields("bad hex key"));
        });
        assertEquals("6472afcd8b2070c55011e5d504c957a3cc72e15e9597e4244343362c054460dc", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void generatedIvsUseTheRecommendedLength() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            String[][] cases = {{"AES-128", "CBC"}, {"AES-256", "GCM"}, {"DES", "CBC"}, {"AES-128", "ECB"},
                    {"Salsa20", null}, {"ChaCha20", null}, {"XChaCha20-Poly1305", null}};
            for (String[] each : cases) {
                panel.combo("symmetricAlgorithmCombo").setValue(each[0]);
                if (each[1] != null) panel.combo("cipherModeCombo").setValue(each[1]);
                panel.field("ivField").setText("");
                panel.controller().generateIV();
                transcript.add(each[0] + "/" + each[1] + " iv bytes " + panel.field("ivField").getText().length() / 2);
            }
        });
        assertEquals("19b6c86845b300b6c50d337f24e5542220ccb2f121f831f8efbf704c2c13a70c", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void streamCiphersGetTheirNonceEvenAfterEcbWasSelected() throws Exception {
        withPanel(panel -> {
            panel.combo("symmetricAlgorithmCombo").setValue("AES-128");
            panel.combo("cipherModeCombo").setValue("ECB");
            panel.combo("symmetricAlgorithmCombo").setValue("ChaCha20");
            assertEquals("Hex Nonce (12 bytes recommended for ChaCha20)", panel.field("ivField").getPromptText());
            panel.controller().generateIV();
            assertEquals(24, panel.field("ivField").getText().length());
            panel.combo("symmetricAlgorithmCombo").setValue("Salsa20");
            panel.controller().generateIV();
            assertEquals(16, panel.field("ivField").getText().length());
        });
    }

    @Test
    void leavingAModeWithoutPaddingGivesTheChosenPaddingBack() throws Exception {
        withPanel(panel -> {
            panel.combo("cipherModeCombo").setValue("CBC");
            panel.combo("paddingCombo").setValue("PKCS5Padding");
            panel.combo("cipherModeCombo").setValue("GCM");
            assertEquals("NoPadding", panel.combo("paddingCombo").getValue());
            panel.combo("cipherModeCombo").setValue("CBC");
            assertEquals("PKCS5Padding", panel.combo("paddingCombo").getValue());

            panel.combo("cipherModeCombo").setValue("CTR");
            panel.combo("cipherModeCombo").setValue("ECB");
            panel.combo("paddingCombo").setValue("ISO10126Padding");
            panel.combo("cipherModeCombo").setValue("CBC");
            assertEquals("ISO10126Padding", panel.combo("paddingCombo").getValue());
        });
    }

    @Test
    void asciiAadIsNotMarkedAsAnError() throws Exception {
        withPanel(panel -> {
            panel.combo("cipherModeCombo").setValue("GCM");
            panel.field("aadField").setText("header.v1");
            assertTrue(!panel.field("aadField").getStyleClass().contains("field-error"));
            panel.field("ivField").setText("XYZ");
            assertTrue(panel.field("ivField").getStyleClass().contains("field-error"));
        });
    }

    @Test
    void keySourceAndKeyLabSelection() throws Exception {
        List<String> transcript = new ArrayList<>();
        KeyMaterial aes = labKey("lab-aes", "Lab AES", "AES", 32, Set.of(KeyUsage.ENCRYPT, KeyUsage.DECRYPT));
        KeyMaterial mac = labKey("lab-mac", "Lab MAC", "AES", 16, Set.of(KeyUsage.MAC));
        withPanel(panel -> {
            panel.controller().refreshHsmKeys();
            transcript.add("hsm items " + panel.combo("symHsmKeyCombo").getItems()
                    + " shown " + panel.combo("symHsmKeyCombo").getConverter().toString(aes.getId()));
            panel.combo("symKeySourceCombo").setValue("Simulated HSM");
            panel.combo("symKeySourceCombo").getOnAction().handle(null);
            transcript.add(panel.fields("hsm source"));
            panel.combo("symKeySourceCombo").setValue("Manual Input");
            panel.combo("symKeySourceCombo").getOnAction().handle(null);
            transcript.add(panel.fields("manual source"));

            panel.combo("symmetricAlgorithmCombo").setValue("DES");
            panel.controller().selectLabKey(aes.getId());
            transcript.add(panel.fields("selected lab key"));
            for (String id : List.of(mac.getId(), "missing")) {
                try {
                    panel.controller().selectLabKey(id);
                    transcript.add("select " + id + " accepted");
                } catch (IllegalArgumentException error) {
                    transcript.add("select " + id + " -> " + error.getMessage());
                }
            }

            panel.invoke("handleInspectKey");
            panel.combo("symKeySourceCombo").setValue("Manual Input");
            panel.field("symmetricKeyField").setText("");
            panel.controller().saveCurrentKeyToHsm();
            transcript.add(panel.reporter().drain());
        });
        assertEquals("d23f76b8c5be5698f361c041249a51e95dee31532cff4fecfe1a04907ccbe888", digest(transcript), String.join("\n", transcript));
    }

    @Test
    void formatPreservingEncryptionRoundTrips() throws Exception {
        List<String> transcript = new ArrayList<>();
        withPanel(panel -> {
            transcript.add("presets " + panel.combo("fpeAlphabetPresetCombo").getItems()
                    + " alphabet " + panel.field("fpeAlphabetField").getText());
            panel.combo("fpeAlphabetPresetCombo").setValue("Alphanumeric");
            transcript.add("alphanumeric " + panel.field("fpeAlphabetField").getText());
            panel.combo("fpeAlphabetPresetCombo").setValue("ASCII printable");
            transcript.add("ascii " + panel.field("fpeAlphabetField").getText());
            panel.combo("fpeAlphabetPresetCombo").setValue("Custom");
            transcript.add("custom " + panel.field("fpeAlphabetField").getText());
            panel.combo("fpeAlphabetPresetCombo").setValue("Decimal (0-9)");

            panel.field("fpeKeyField").setText(KEY_128);
            panel.field("fpeTweakField").setText("");
            panel.area("fpeInputArea").setText("0123456789");
            panel.controller().handleFpe();
            String ciphertext = panel.area("fpeOutputArea").getText();
            transcript.add("ff1 encrypt " + ciphertext);
            panel.combo("fpeOperationCombo").setValue("DECRYPT");
            panel.area("fpeInputArea").setText(ciphertext);
            panel.controller().handleFpe();
            transcript.add("ff1 decrypt " + panel.area("fpeOutputArea").getText());

            panel.combo("fpeAlgorithmCombo").setValue("FF3_1");
            panel.combo("fpeOperationCombo").setValue("ENCRYPT");
            panel.field("fpeTweakField").setText("00112233445566");
            panel.area("fpeInputArea").setText("4000123412341234");
            panel.controller().handleFpe();
            transcript.add("ff3-1 encrypt " + panel.area("fpeOutputArea").getText());

            panel.field("fpeKeyField").setText("");
            panel.controller().handleFpe();
            transcript.add(panel.reporter().drain());
        });
        assertEquals("ee46cf95db1d39ce1d932d476a759495bfbb7307d978a97fd0ee56e78336f6da", digest(transcript), String.join("\n", transcript));
    }

    private static KeyMaterial labKey(String id, String name, String algorithm, int bytes, Set<KeyUsage> usages) {
        byte[] secret = new byte[bytes];
        for (int i = 0; i < bytes; i++) secret[i] = (byte) (i * 7 + id.length());
        KeyMaterial material = KeyMaterialFactory.fromSecretKey(id, new SecretKeySpec(secret, algorithm),
                KeyExportability.EXPORTABLE, usages);
        material.setName(name);
        SimulatedHsmProvider.getInstance().importKey(material);
        return material;
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
                inputFormat.setValue("Text (UTF-8)");
                ComboBox<String> outputFormat = new ComboBox<>();
                outputFormat.getItems().setAll("Text (UTF-8)", "Hexadecimal", "Base64", "Binary");
                outputFormat.setValue("Hexadecimal");
                Recorder reporter = new Recorder();
                controller.initModern(reporter, inputFormat, outputFormat, null);
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
        ComboBox<String> combo(String id) { return (ComboBox<String>) loader.getNamespace().get(id); }
        TextField field(String id) { return (TextField) loader.getNamespace().get(id); }
        TextArea area(String id) { return (TextArea) loader.getNamespace().get(id); }

        void invoke(String handler) {
            try {
                Method method = CipherController.class.getDeclaredMethod(handler);
                method.setAccessible(true);
                method.invoke(controller);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }

        String fields(String step) {
            StringBuilder out = new StringBuilder("## ").append(step);
            for (String id : List.of("ivLabel", "ivContainer", "ivField", "gcmTagLabel", "gcmTagField", "aadLabel",
                    "aadField", "ecbWarningBox", "aeadNoteLabel", "cipherModeCombo", "paddingCombo",
                    "symmetricKeyField", "symHsmKeyCombo", "inspectKeyBtn", "saveToLabBtn",
                    "symKeyBadgeLabel", "ivBadgeLabel", "gcmTagBadgeLabel", "aadBadgeLabel")) {
                Object value = loader.getNamespace().get(id);
                if (!(value instanceof Node node)) continue;
                out.append("\n").append(id).append(" v=").append(node.isVisible()).append(" m=").append(node.isManaged())
                        .append(" d=").append(node.isDisable());
                if (node.getStyle() != null && !node.getStyle().isEmpty()) out.append(" style=").append(node.getStyle());
                if (node instanceof TextField text) out.append(" prompt=").append(text.getPromptText());
                if (node instanceof ComboBox<?> box) out.append(" value=").append(box.getValue());
                if (node instanceof Label label) out.append(" text=").append(label.getText())
                        .append(" class=").append(label.getStyleClass());
                if (node instanceof Control control && control.getStyleClass().contains("field-error")) {
                    out.append(" field-error");
                }
                if (node instanceof Button) out.append(" button");
            }
            out.append("\nalgorithm=").append(combo("symmetricAlgorithmCombo").getValue())
                    .append(" source=").append(combo("symKeySourceCombo").getValue())
                    .append(" hsm=").append(combo("symHsmKeyCombo").getValue());
            return out.toString();
        }
    }

    private static final class Recorder implements StatusReporter {
        private final List<String> lines = new ArrayList<>();

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
    }
}
