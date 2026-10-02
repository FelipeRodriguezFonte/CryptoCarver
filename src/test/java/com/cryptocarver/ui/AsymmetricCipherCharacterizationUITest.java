package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.util.DataConverter;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RSA encryption panel of cipher.fxml, pinned before it moves to its own coordinator. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class AsymmetricCipherCharacterizationUITest {
    private static KeyPair pair;
    private static KeyPair otherPair;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        pair = generator.generateKeyPair();
        otherPair = generator.generateKeyPair();
    }

    @Test
    void paddingListDefaultsToPkcs1AndShowsTheWarningOnlyForUnsafeSchemes() throws Exception {
        withPanel(null, panel -> {
            assertEquals(List.of("RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
                    "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "RSA/ECB/NoPadding"), panel.padding().getItems());
            assertEquals("RSA/ECB/PKCS1Padding", panel.padding().getValue());
            assertTrue(panel.warning().isVisible());
            assertFalse(panel.help().isVisible());

            panel.padding().setValue("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            assertFalse(panel.warning().isVisible());
            assertTrue(panel.help().isVisible());

            panel.padding().setValue("RSA/ECB/NoPadding");
            assertTrue(panel.warning().isVisible());
        });
    }

    @Test
    void pemKeysEncryptAndDecryptWithOaepSha256() throws Exception {
        withPanel(null, panel -> {
            panel.padding().setValue("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            panel.publicKey().setText(pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            panel.controller().handleLoadPublicKey();
            panel.input().setText("rsa characterization");
            panel.controller().handleAsymmetricEncrypt();
            String ciphertext = panel.output().getText();
            assertEquals(256, ciphertext.length(), "1024-bit modulus as hexadecimal");

            panel.privateKey().setText(pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            panel.controller().handleLoadPrivateKey();
            panel.inputFormat().setValue("Hexadecimal");
            panel.outputFormat().setValue("Text (UTF-8)");
            panel.input().setText(ciphertext);
            panel.controller().handleAsymmetricDecrypt();

            assertEquals("rsa characterization", panel.output().getText());
            assertEquals(List.of("Public Key loaded from PEM", "Private Key loaded from PEM"), panel.reporter().statuses);
            assertEquals(List.of(), panel.reporter().errors);
            assertEquals(List.of(
                    "Asymmetric Encrypt|RSA encryption successful (RSA/ECB/OAEPWithSHA-256AndMGF1Padding)"
                            + "|Algorithm=RSA,Key Size=1024 bits,Padding=RSA/ECB/OAEPWithSHA-256AndMGF1Padding|20|128",
                    "Asymmetric Decrypt|RSA decryption successful (RSA/ECB/OAEPWithSHA-256AndMGF1Padding)"
                            + "|Algorithm=RSA,Key Size=1024 bits,Padding=RSA/ECB/OAEPWithSHA-256AndMGF1Padding|128|20"),
                    panel.reporter().published);
        });
    }

    @Test
    void hexAndBase64DerKeysAreAccepted() throws Exception {
        withPanel(null, panel -> {
            panel.publicKey().setText(DataConverter.bytesToHex(pair.getPublic().getEncoded()));
            panel.controller().handleLoadPublicKey();
            panel.privateKey().setText(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            panel.controller().handleLoadPrivateKey();
            assertEquals(List.of("Public Key loaded from Hex (DER)", "Private Key loaded from Base64 (DER)"),
                    panel.reporter().statuses);
        });
    }

    @Test
    void sharedKeyPairIsUsedWhenNoKeyWasLoaded() throws Exception {
        withPanel(() -> pair, panel -> {
            assertTrue(panel.controller().hasAsymmetricKeyAvailable(true));
            assertTrue(panel.controller().hasAsymmetricKeyAvailable(false));
            panel.input().setText("from the shared pair");
            panel.controller().handleAsymmetricEncrypt();
            assertEquals(List.of(), panel.reporter().errors);
            assertEquals(1, panel.reporter().published.size());
        });
    }

    @Test
    void errorsKeepTheirTitlesAndMessages() throws Exception {
        withPanel(null, panel -> {
            assertFalse(panel.controller().hasAsymmetricKeyAvailable(true));
            panel.input().setText("data");
            panel.controller().handleAsymmetricEncrypt();
            panel.controller().handleAsymmetricDecrypt();

            panel.controller().handleLoadPublicKey();
            panel.publicKey().setText("not a key");
            panel.controller().handleLoadPublicKey();

            panel.publicKey().setText(pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            panel.controller().handleLoadPublicKey();
            panel.input().setText("");
            panel.controller().handleAsymmetricEncrypt();
            panel.input().setText("x".repeat(118));
            panel.controller().handleAsymmetricEncrypt();

            panel.privateKey().setText(pem("PRIVATE KEY", otherPair.getPrivate().getEncoded()));
            panel.controller().handleLoadPrivateKey();
            panel.inputFormat().setValue("Hexadecimal");
            panel.input().setText("ZZ");
            panel.controller().handleAsymmetricDecrypt();

            assertEquals(List.of(
                    "Key Error: Please load a public key first",
                    "Key Error: Please load a private key first",
                    "Key Error: Please enter a public key (PEM or Hex)",
                    "Load Error: Failed to load Public Key: Unknown key format. Please use PEM or Hex/Base64 DER.",
                    "Input Error: Please enter data to encrypt",
                    "Data Size Error: Maximum plaintext size for this key and padding: 117 bytes. Your data: 118 bytes.",
                    "Format Error: " + formatError(panel)), panel.reporter().errors);
        });
    }

    private static String formatError(Panel panel) {
        try {
            com.cryptocarver.util.InputValidator.validateInput("ZZ", "Hexadecimal");
            return "";
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        }
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private void withPanel(java.util.function.Supplier<KeyPair> shared, Consumer<Panel> body) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader("/fxml/cipher.fxml");
                loader.load();
                CipherController controller = loader.getController();
                Recorder reporter = new Recorder();
                ComboBox<String> inputFormat = new ComboBox<>();
                inputFormat.setValue("Text (UTF-8)");
                ComboBox<String> outputFormat = new ComboBox<>();
                outputFormat.setValue("Hexadecimal");
                controller.initModern(reporter, inputFormat, outputFormat, shared);
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
        ComboBox<String> padding() { return (ComboBox<String>) loader.getNamespace().get("rsaPaddingCombo"); }
        @SuppressWarnings("unchecked")
        ComboBox<String> inputFormat() { return (ComboBox<String>) loader.getNamespace().get("asymmetricInputFormatCombo"); }
        @SuppressWarnings("unchecked")
        ComboBox<String> outputFormat() { return (ComboBox<String>) loader.getNamespace().get("asymmetricOutputFormatCombo"); }
        Label warning() { return (Label) loader.getNamespace().get("rsaPaddingWarningLabel"); }
        Label help() { return (Label) loader.getNamespace().get("rsaPaddingHelpLabel"); }
        TextArea publicKey() { return (TextArea) loader.getNamespace().get("publicKeyArea"); }
        TextArea privateKey() { return (TextArea) loader.getNamespace().get("privateKeyArea"); }
        TextArea input() { return (TextArea) loader.getNamespace().get("cipherInputArea"); }
        TextArea output() { return (TextArea) loader.getNamespace().get("cipherOutputArea"); }
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
            published.add(result.getOperation() + "|" + result.getStatusMessage() + "|" + String.join(",", details)
                    + "|" + result.getInput().length + "|" + result.getOutput().length);
        }
    }
}
