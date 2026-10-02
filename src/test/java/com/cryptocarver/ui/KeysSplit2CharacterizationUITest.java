package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import com.cryptocarver.util.DataConverter;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Locks phase-two behavior through the production shell and lazily loaded FXML modules. */
class KeysSplit2CharacterizationUITest {
    private static String previousHome;
    private static String previousMode;
    private static KeyPair signer;
    private static X509Certificate signerCert;
    private static X509Certificate otherCert;
    private static String validChain;
    private static String incompleteChain;
    private static String expiredChain;
    private ModernMainController shell;
    private KeysController keys;
    private Parent root;
    private Stage stage;

    @BeforeAll
    static void prepareGeneratedMaterial() throws Exception {
        previousHome = System.getProperty("user.home");
        previousMode = System.getProperty("test.mode");
        System.setProperty("user.home", Path.of("target/test-home").toAbsolutePath().toString());
        System.setProperty("test.mode", "true");
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException started) {
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signer = generator.generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic phase two signer";
        signerCert = CertificateGenerator.generateSelfSignedCertificate(signer, config);
        config.commonName = "Synthetic different certificate";
        otherCert = CertificateGenerator.generateSelfSignedCertificate(generator.generateKeyPair(), config);
        var caPair = generator.generateKeyPair();
        config.commonName = "Synthetic phase two root";
        var ca = CertificateGenerator.generateRootCA(caPair, config, 1);
        config.commonName = "Synthetic phase two leaf";
        String csrPem = CertificateGenerator.generateCSR(generator.generateKeyPair(), config);
        var csr = new PKCS10CertificationRequest(Base64.getDecoder().decode(csrPem.replaceAll("-----[^-]+-----|\\s", "")));
        var leaf = CertificateAuthorityOperations.issueFromCsr(csr, ca, caPair.getPrivate(), 30, "SHA256withRSA");
        incompleteChain = CertificateGenerator.exportCertificatePEM(leaf);
        validChain = incompleteChain + CertificateGenerator.exportCertificatePEM(ca);
        config.validityDays = -1;
        expiredChain = CertificateGenerator.exportCertificatePEM(CertificateGenerator.generateRootCA(generator.generateKeyPair(), config, 1));
    }

    @AfterAll
    static void restoreProperties() {
        System.setProperty("user.home", previousHome);
        if (previousMode == null) System.clearProperty("test.mode");
        else System.setProperty("test.mode", previousMode);
    }

    @BeforeEach
    void loadProductionShellWithoutEagerModules() throws Exception {
        fx(() -> {
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            root = loader.load();
            shell = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1400, 900));
            stage.getScene().getStylesheets().addAll(getClass().getResource("/css/styles.css").toExternalForm(),
                    getClass().getResource("/css/theme-light.css").toExternalForm());
        });
    }

    @AfterEach
    void releaseProductionShell() throws Exception {
        fx(() -> {
            if (shell != null) shell.shutdown();
            if (stage != null) {
                stage.close();
                stage.setScene(null);
            }
            keys = null;
            shell = null;
            root = null;
            stage = null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"RSA", "DSA", "ECDSA", "EdDSA"})
    void generatedPairPublishesPemToInspectorHistoryAndShelf(String algorithm) throws Exception {
        var settings = AppSettings.getInstance();
        var previous = settings.getSecretVisibilityProfile();
        try {
            settings.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
            int[] beforeLab = new int[1];
            fx(() -> {
                navigate(algorithm + " Key Generation");
                beforeLab[0] = ((TableView<?>) root.lookup("#keyLabTable")).getItems().size();
                switch (algorithm) {
                    case "RSA" -> {
                        var size = combo("rsaKeySizeCombo");
                        size.getSelectionModel().select(size.getItems().stream().map(Integer.class::cast).min(Integer::compare).orElseThrow());
                        keys.handleGenerateRSA();
                    }
                    case "DSA" -> {
                        var size = combo("dsaKeySizeCombo");
                        size.getSelectionModel().select(0);
                        keys.handleGenerateDSA();
                    }
                    case "ECDSA" -> keys.handleGenerateECDSA();
                    default -> keys.handleGenerateEdDSA();
                }
            });
            awaitCompletedPublication();
            var result = snapshot();
            String publicPem = new String(result.getOutput(), StandardCharsets.UTF_8);
            assertTrue(publicPem.contains("BEGIN PUBLIC KEY"));
            assertTrue(result.getEnrichedOutput().contains("BEGIN PRIVATE KEY"));
            assertEquals(result.getOperation(), shell.getHistoryManager().getHistoryItems().get(0).getOperation());
            assertNotNull(keys.getLastGeneratedKeyPair());
            fx(() -> {
                assertEquals(beforeLab[0], ((TableView<?>) root.lookup("#keyLabTable")).getItems().size());
                shell.addCurrentResultToShelf();
            });
            assertTrue(ClipboardShelfManager.getInstance().getEntries().stream().anyMatch(entry -> publicPem.equals(entry.getValue())));
            assertNotNull(AsymmetricKeyOperations.importPublicKeyPEMAuto(publicPem));
        } finally {
            settings.setSecretVisibilityProfile(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HKDF-SHA1", "HKDF-SHA256", "HKDF-SHA512", "NIST-800-108-SHA256", "X9.63-SHA256",
            "PBKDF2-SHA1", "PBKDF2-SHA256", "PBKDF2-SHA512", "SCrypt", "Argon2id"})
    void eachKdfMatchesFixedReferenceInputs(String algorithm) throws Exception {
        var vector = KeysSplit2Vectors.kdf(algorithm);
        fx(() -> {
            navigate("Key Derivation (KDF)");
            combo("kdfAlgorithmCombo").setValue(algorithm);
            combo("kdfInputFormatCombo").setValue("Hex");
            combo("kdfSaltFormatCombo").setValue("Hex");
            combo("kdfInfoFormatCombo").setValue("Hex");
            text("kdfInputField", vector.input());
            text("kdfSaltField", vector.salt());
            text("kdfInfoField", vector.info());
            text("kdfIterationsField", vector.iterations());
            text("kdfOutputLengthField", vector.length());
            keys.handleDeriveKey();
        });
        assertArrayEquals(vector.expected(), snapshot().getOutput());
        assertTrue(text("kdfResultArea").contains(DataConverter.bytesToHex(vector.expected())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aesWrapAndUnwrapMatchExistingVectors(boolean padded) throws Exception {
        fx(() -> {
            configureWrap(padded);
            keys.handleKeyWrap();
        });
        String wrapped = text("keyWrapResultArea").split("WRAPPED:\\n", 2)[1].split("\\n", 2)[0];
        if (!padded) assertEquals(KdfWrapTestVectors.HEX_8, wrapped);
        fx(() -> {
            ((CheckBox) root.lookup("#keyWrapUnwrapCheck")).setSelected(true);
            text("keyWrapDataField", wrapped);
            keys.handleKeyWrap();
        });
        assertTrue(text("keyWrapResultArea").contains(padded ? KdfWrapTestVectors.HEX_9 : KdfWrapTestVectors.HEX_6));
    }

    @Test
    void wrongKekReportsErrorWithoutReplacingWrappedResult() throws Exception {
        fx(() -> {
            configureWrap(false);
            keys.handleKeyWrap();
        });
        String wrapped = text("keyWrapResultArea").split("WRAPPED:\\n", 2)[1].split("\\n", 2)[0];
        OperationResult previous = snapshot();
        fx(() -> {
            text("keyWrapDataField", wrapped);
            text("keyWrapKekField", "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
            ((CheckBox) root.lookup("#keyWrapUnwrapCheck")).setSelected(true);
            keys.handleKeyWrap();
            assertNotNull(((InlineErrorPresenter) field(shell, "inlineErrorPresenter")).getCurrentError());
        });
        assertSame(previous, snapshot());
    }

    @Test
    void cmsSignAndVerifyPublishesContentAndInspectorDetails() throws Exception {
        fx(() -> {
            configureCms();
            text("cmsInputArea", "Synthetic phase two CMS payload");
            keys.handleCMSSign();
        });
        awaitCompletedPublication();
        String signed = text("cmsOutputArea");
        fx(() -> {
            text("cmsInputArea", signed);
            keys.handleCMSVerify();
        });
        assertTrue(text("cmsOutputArea").contains("VALID"));
        assertEquals("Synthetic phase two CMS payload", new String(snapshot().getOutput(), StandardCharsets.UTF_8));
        assertTrue(snapshot().getDetails().stream().anyMatch(detail -> "Result".equals(detail.name()) && "VALID".equals(detail.value())));
    }

    @Test
    void cmsEncryptAndDecryptRecoverGeneratedPayload() throws Exception {
        fx(() -> {
            configureCms();
            text("cmsInputArea", "Synthetic phase two encryption payload");
            keys.handleCMSEncrypt();
        });
        String encrypted = text("cmsOutputArea");
        fx(() -> {
            text("cmsInputArea", encrypted);
            keys.handleCMSDecrypt();
        });
        assertEquals("Synthetic phase two encryption payload", text("cmsOutputArea"));
        assertEquals("CMS Decrypt (EnvelopedData)", snapshot().getOperation());
    }

    @Test
    void cmsVerificationRejectsSignatureBoundToDifferentCertificate() throws Exception {
        byte[] invalid = CMSOperations.generateSignedData("Synthetic mismatched signer".getBytes(StandardCharsets.UTF_8),
                otherCert, signer.getPrivate(), null, false);
        fx(() -> {
            configureCms();
            text("cmsInputArea", Base64.getEncoder().encodeToString(invalid));
            keys.handleCMSVerify();
        });
        assertTrue(text("cmsOutputArea").contains("INVALID") || text("cmsOutputArea").startsWith("Verification Failed:"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "missing-root", "expired"})
    void chainValidationDistinguishesValidIncompleteAndExpiredMaterial(String kind) throws Exception {
        fx(() -> {
            navigate("Validate Chain");
            text("chainInputArea", switch (kind) {
                case "valid" -> validChain;
                case "missing-root" -> incompleteChain;
                default -> expiredChain;
            });
            keys.handleValidateCertificateChain();
        });
        assertTrue(text("chainResultArea").contains(kind.equals("valid") ? "✅ VALID" : "❌ INVALID"));
        assertEquals("Validate Chain", snapshot().getOperation());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RSA Key Generation", "Key Derivation (KDF)", "CMS Sign", "Validate Chain"})
    void portableFieldsRestoreAndHistoryRedactsMaterialOnLazilyLoadedScreens(String route) throws Exception {
        var settings = AppSettings.getInstance();
        var previous = settings.getSecretVisibilityProfile();
        try {
            settings.setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
            fx(() -> {
                navigate(route);
                Object owner = route.startsWith("CMS") || route.equals("Validate Chain")
                        ? field(shell, "certificatesContainerController") : keys;
                String name = switch (route) {
                    case "RSA Key Generation" -> "rsaKeySizeCombo";
                    case "Key Derivation (KDF)" -> "kdfInputField";
                    case "CMS Sign" -> "cmsSignKeyArea";
                    default -> "chainInputArea";
                };
                if (route.startsWith("RSA")) {
                    var size = combo(name);
                    size.setValue(2048);
                    Map<String, Object> state = UiStateSnapshot.capturePortableConfiguration(owner);
                    size.setValue(4096);
                    UiStateSnapshot.restore(owner, state);
                    assertEquals(2048, size.getValue());
                } else {
                    text(name, "synthetic private material");
                    Map<String, Object> state = UiStateSnapshot.capturePortableConfiguration(owner);
                    Map<String, Object> history = UiStateSnapshot.captureHistoryRecipe(owner);
                    text(name, "");
                    UiStateSnapshot.restore(owner, state);
                    assertEquals("synthetic private material", text(name));
                    assertEquals("[REDACTED_SECRET]", history.get(owner.getClass().getSimpleName() + "." + name));
                    UiStateSnapshot.restoreHistoryRecipe(owner, history);
                    assertEquals("", text(name));
                }
            });
        } finally {
            settings.setSecretVisibilityProfile(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RSA Key Generation", "Key Derivation (KDF)", "CMS Sign", "Validate Chain"})
    void labelsRespondToLanguageChangesOnEachScreen(String route) throws Exception {
        var previous = AppSettings.getInstance().getLanguagePreference();
        try {
            fx(() -> {
                I18nService.getInstance().setPreference(LanguagePreference.EN);
                navigate(route);
                var labels = root.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast).toList();
                var before = labels.stream().map(Label::getText).toList();
                I18nService.getInstance().setPreference(LanguagePreference.ES);
                assertNotEquals(before, labels.stream().map(Label::getText).toList());
            });
        } finally {
            fx(() -> I18nService.getInstance().setPreference(previous));
        }
    }

    private void configureWrap(boolean padded) {
        navigate("AES Key Wrap");
        combo("keyWrapModeCombo").setValue(padded ? "RFC 5649 - AES Key Wrap with Padding" : "RFC 3394 - AES Key Wrap");
        text("keyWrapKekField", KdfWrapTestVectors.HEX_7);
        text("keyWrapDataField", padded ? KdfWrapTestVectors.HEX_9 : KdfWrapTestVectors.HEX_6);
    }

    private void configureCms() throws Exception {
        navigate("CMS Sign");
        text("cmsSignCertArea", CertificateGenerator.exportCertificatePEM(signerCert));
        text("cmsEncryptCertArea", CertificateGenerator.exportCertificatePEM(signerCert));
        text("cmsSignKeyArea", AsymmetricKeyOperations.exportPrivateKeyPEM(signer.getPrivate()));
        text("cmsDecryptKeyArea", AsymmetricKeyOperations.exportPrivateKeyPEM(signer.getPrivate()));
    }

    private void navigate(String route) {
        shell.navigateToModule(route);
        keys = shell.getKeysController();
        root.applyCss();
        root.layout();
    }

    private void awaitCompletedPublication() throws Exception {
        // test.mode runs the actual operation synchronously; flushing FX completes its callbacks.
        fx(() -> { });
        assertEquals(OperationExecutor.State.IDLE, shell.getOperationExecutor().getState());
        assertNotNull(snapshot());
    }

    private OperationResult snapshot() { return field(shell, "lastPublishedResultSnapshot"); }
    private String text(String id) { return ((TextInputControl) root.lookup("#" + id)).getText(); }
    private void text(String id, String value) { ((TextInputControl) root.lookup("#" + id)).setText(value); }
    @SuppressWarnings("unchecked")
    private ComboBox<Object> combo(String id) { return (ComboBox<Object>) root.lookup("#" + id); }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            var f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(owner);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface FxAction { void run() throws Exception; }
    private static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(45, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
