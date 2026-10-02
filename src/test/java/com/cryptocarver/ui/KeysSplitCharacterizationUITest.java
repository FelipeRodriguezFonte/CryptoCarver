package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.TR31Operations;
import com.cryptocarver.crypto.Tr31TestVectors;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Characterization through production Fxml.loader and the real shell publication pipeline. */
class KeysSplitCharacterizationUITest {
    private static String previousHome;
    private static String previousTestMode;
    private static String senderPrivate;
    private static String receiverPrivate;
    private static String receiverPublic;
    private static String wrongPrivate;
    private static String senderCert;
    private static String receiverCert;
    private ModernMainController shell;
    private KeysController keys;
    private Parent root;
    private Stage stage;

    @BeforeAll
    static void prepare() throws Exception {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Path.of("target/test-home").toAbsolutePath().toString());
        previousTestMode = System.getProperty("test.mode");
        System.setProperty("test.mode", "true");
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException started) {
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair sender = generator.generateKeyPair();
        KeyPair receiver = generator.generateKeyPair();
        senderPrivate = AsymmetricKeyOperations.exportPrivateKeyPEM(sender.getPrivate());
        receiverPrivate = AsymmetricKeyOperations.exportPrivateKeyPEM(receiver.getPrivate());
        receiverPublic = AsymmetricKeyOperations.exportPublicKeyPEM(receiver.getPublic());
        wrongPrivate = AsymmetricKeyOperations.exportPrivateKeyPEM(generator.generateKeyPair().getPrivate());
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic keys split test";
        senderCert = CertificateGenerator.exportCertificatePEM(CertificateGenerator.generateSelfSignedCertificate(sender, config));
        receiverCert = CertificateGenerator.exportCertificatePEM(CertificateGenerator.generateSelfSignedCertificate(receiver, config));
    }

    @AfterAll
    static void restoreHome() {
        System.setProperty("user.home", previousHome);
        if (previousTestMode == null) System.clearProperty("test.mode");
        else System.setProperty("test.mode", previousTestMode);
    }

    @BeforeEach
    void loadShell() throws Exception {
        fx(() -> {
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            root = loader.load();
            shell = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1400, 900));
            stage.getScene().getStylesheets().addAll(
                    getClass().getResource("/css/styles.css").toExternalForm(),
                    getClass().getResource("/css/theme-light.css").toExternalForm());
            shell.navigateToModule("TR-31 Key Blocks");
            keys = shell.getKeysController();
            root.applyCss();
            root.layout();
        });
    }

    @AfterEach
    void releaseShell() throws Exception {
        fx(() -> {
            if (shell != null) shell.shutdown();
            if (stage != null) {
                stage.close();
                stage.setScene(null);
            }
            root = null;
            keys = null;
            shell = null;
            stage = null;
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void tr31WrapParseAndUnwrapMatchPublicVectorAttributes(int vector) throws Exception {
        fx(() -> configureTr31(vector));
        fx(keys::handleTR31Export);
        fx(() -> { });
        String block = new String(snapshot().getOutput(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(Tr31TestVectors.versions[vector], block.charAt(0));
        assertEquals(Tr31TestVectors.usages[vector], block.substring(5, 7));
        assertEquals(Tr31TestVectors.keys[vector], TR31Operations.unwrapKey(Tr31TestVectors.protectionKeys[vector], block));
        fx(() -> {
            text("tr31KbpkImportField", Tr31TestVectors.protectionKeys[vector]);
            text("tr31KeyBlockField", block);
            keys.handleTR31ParseHeader();
        });
        assertTrue(text("tr31ImportResultArea").contains("TR-31 HEADER PARSE"));
        fx(keys::handleTR31Import);
        assertTrue(text("tr31ImportResultArea").contains(Tr31TestVectors.keys[vector]));
    }

    @Test
    void tr31ImportsIndependentPublishedBlock() throws Exception {
        fx(() -> {
            text("tr31KbpkImportField", Tr31TestVectors.protectionKeys[1]);
            text("tr31KeyBlockField", Tr31TestVectors.blocks[1]);
            keys.handleTR31Import();
        });
        assertTrue(text("tr31ImportResultArea").contains(Tr31TestVectors.keys[1]));
    }

    @Test
    void wrongTr31ProtectionKeyReportsLocalizedErrorWithoutPublishing() throws Exception {
        withLanguage("es", () -> {
            text("tr31KbpkImportField", Tr31TestVectors.protectionKeys[0]);
            text("tr31KeyBlockField", Tr31TestVectors.blocks[1]);
            keys.handleTR31Import();
            assertTrue(field(shell, "lastPublishedResultSnapshot") == null);
            assertTrue(((Label) root.lookup("#statusLabel")).getText().contains(I18nService.getInstance().text("module.keys.tr31.status.unwrapFailed")));
            assertEquals(I18nService.getInstance().text("module.keys.tr31.errorTitle"), ((InlineErrorPresenter) field(shell, "inlineErrorPresenter")).getCurrentError().title());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Raw OAEP", "JWE Compact", "CMS EnvelopedData"})
    void rsaExchangeRecoversSymmetricKeyForEachFormat(String format) throws Exception {
        fx(() -> rsaExport(format));
        String output = new String(snapshot().getOutput(), java.nio.charset.StandardCharsets.UTF_8);
        fx(() -> {
            text("rsaKexPrivateKeyArea", receiverPrivate);
            text("rsaKexWrappedDataArea", output);
            select("rsaKexImportProfileCombo", format);
            keys.handleRsaKexImport();
        });
        assertArrayEquals(com.cryptocarver.util.DataConverter.hexToBytes(Tr31TestVectors.keys[1]), snapshot().getOutput());
        assertEquals(com.cryptocarver.model.OperationDetail.Classification.SECRET, snapshot().getOutputClassification());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Raw OAEP", "JWE Compact", "CMS EnvelopedData"})
    void wrongRsaPrivateKeyKeepsPreviousPublishedResult(String format) throws Exception {
        fx(() -> rsaExport(format));
        OperationResult exported = snapshot();
        String output = new String(exported.getOutput(), java.nio.charset.StandardCharsets.UTF_8);
        fx(() -> {
            text("rsaKexPrivateKeyArea", wrongPrivate);
            text("rsaKexWrappedDataArea", output);
            select("rsaKexImportProfileCombo", format);
            keys.handleRsaKexImport();
        });
        assertSame(exported, snapshot());
        assertTrue(((Label) root.lookup("#statusLabel")).getText().contains(I18nService.getInstance().text("module.keys.rsaKex.status.unwrapFailed")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tr34RecoversAndVerifiesBothPassProfiles(boolean twoPass) throws Exception {
        fx(() -> tr34Distribute(twoPass));
        String distributed = new String(snapshot().getOutput(), java.nio.charset.StandardCharsets.UTF_8);
        fx(() -> {
            text("tr34DistributedDataArea", distributed);
            keys.handleTr34Receive();
        });
        assertArrayEquals(com.cryptocarver.util.DataConverter.hexToBytes(Tr31TestVectors.keys[1]), snapshot().getOutput());
        assertTrue(text("tr34ReceiveResultArea").contains("Signature Verified: YES"));
        if (twoPass) assertTrue(text("tr34ReceiveResultArea").contains("Nonce Verified:      YES"));
    }

    @Test
    void manipulatedTr34NonceMarksReceptionUntrusted() throws Exception {
        fx(() -> tr34Distribute(true));
        String distributed = new String(snapshot().getOutput(), java.nio.charset.StandardCharsets.UTF_8);
        fx(() -> {
            text("tr34DistributedDataArea", distributed);
            text("tr34ChallengeNonceField", "00000000000000000000000000000000");
            keys.handleTr34Receive();
        });
        assertTrue(text("tr34ReceiveResultArea").contains("Nonce Verified:      NO"));
        assertTrue(snapshot().getStatusMessage().contains("NOT verified"));
    }

    @Test
    void tr34LaboratoryNoticeIsVisible() throws Exception {
        fx(() -> {
            stage.show();
            shell.navigateToModule("TR-34 Key Distribution");
            ClippedTextAuditTool.settle();
            root.applyCss();
            root.layout();
            var notice = root.lookupAll(".label").stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .filter(label -> label.getText().contains("byte-for-byte"))
                    .findFirst().orElseThrow();
            for (javafx.scene.Node node = notice; node != null; node = node.getParent()) assertTrue(node.isVisible());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"TR-31 Key Blocks", "RSA Key Exchange", "TR-34 Key Distribution"})
    void publicationFeedsInspectorHistoryAndShelf(String route) throws Exception {
        int historyBefore = shell.getHistoryManager().getHistoryItems().size();
        fx(() -> exportRoute(route));
        fx(() -> { });
        OperationResult result = snapshot();
        assertNotNull(result);
        assertEquals(Math.min(historyBefore + 1, 50), shell.getHistoryManager().getHistoryItems().size());
        assertEquals(result.getOperation(), shell.getHistoryManager().getHistoryItems().get(0).getOperation());
        assertEquals(result.getOperation(), field(shell, "lastPublishedOperation"));
        fx(shell::addCurrentResultToShelf);
        String expected = new String(result.getOutput(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(ClipboardShelfManager.getInstance().getEntries().stream().anyMatch(entry -> expected.equals(entry.getValue())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr31", "rsaKex", "tr34"})
    void portableConfigurationRestoresInputsAndHistoryRedactsSecrets(String prefix) throws Exception {
        var settings = AppSettings.getInstance();
        var previous = settings.getSecretVisibilityProfile();
        try {
            settings.setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
            fx(() -> {
                String secret = switch (prefix) {
                    case "tr31" -> "tr31KbpkExportField";
                    case "rsaKex" -> "rsaKexKeyToWrapField";
                    default -> "tr34KeyToDistributeField";
                };
                String portable = switch (prefix) {
                    case "tr31" -> "tr31VersionCombo";
                    case "rsaKex" -> "rsaKexExportProfileCombo";
                    default -> "tr34IncludeEnvelopeCheck";
                };
                text(secret, Tr31TestVectors.keys[1]);
                Map<String, Object> configuration = UiStateSnapshot.capturePortableConfiguration(keys);
                Map<String, Object> history = UiStateSnapshot.captureHistoryRecipe(keys);
                assertEquals("[REDACTED_SECRET]", history.get("KeysController." + secret));
                assertTrue(configuration.containsKey("KeysController." + portable));
                text(secret, "");
                UiStateSnapshot.restore(keys, configuration);
                assertEquals(Tr31TestVectors.keys[1], text(secret));
                UiStateSnapshot.restoreHistoryRecipe(keys, history);
                assertEquals("", text(secret));
                assertFalse(configuration.containsKey("KeysController." + prefix + (prefix.equals("tr34") ? "ReceiveResultArea" : "ImportResultArea")));
            });
        } finally {
            settings.setSecretVisibilityProfile(previous);
        }
    }

    @Test
    void labelsAndValidationFollowLanguageChanges() throws Exception {
        String[] errorKeys = {"module.keys.tr31.required", "module.keys.rsaKex.required", "module.keys.tr34.required"};
        withLanguage("en", () -> {
            var labels = root.lookupAll(".label").stream()
                    .filter(Label.class::isInstance).map(Label.class::cast).toList();
            var before = labels.stream().map(Label::getText).toList();
            I18nService.getInstance().setPreference(com.cryptocarver.model.LanguagePreference.ES);
            assertNotEquals(before, labels.stream().map(Label::getText).toList());
            Runnable[] handlers = {keys::handleTR31Export, keys::handleRsaKexExport, keys::handleTr34Distribute};
            for (int index = 0; index < handlers.length; index++) {
                handlers[index].run();
                UserFacingError error = ((InlineErrorPresenter) field(shell, "inlineErrorPresenter")).getCurrentError();
                assertEquals(I18nService.getInstance().text(errorKeys[index]), error.detail());
            }
            assertNull(snapshot());
        });
    }

    private void exportRoute(String route) throws Exception {
        shell.navigateToModule(route);
        switch (route) {
            case "TR-31 Key Blocks" -> {
                configureTr31(1);
                keys.handleTR31Export();
            }
            case "RSA Key Exchange" -> rsaExport("Raw OAEP");
            default -> tr34Distribute(false);
        }
    }

    private void configureTr31(int vector) {
        text("tr31KbpkExportField", Tr31TestVectors.protectionKeys[vector]);
        text("tr31KeyToWrapField", Tr31TestVectors.keys[vector]);
        selectPrefix("tr31VersionCombo", String.valueOf(Tr31TestVectors.versions[vector]));
        selectPrefix("tr31UsageCombo", Tr31TestVectors.usages[vector]);
        selectPrefix("tr31AlgorithmCombo", String.valueOf(Tr31TestVectors.algorithms[vector]));
        selectPrefix("tr31ModeCombo", String.valueOf(Tr31TestVectors.modes[vector]));
        selectPrefix("tr31ExportabilityCombo", String.valueOf(Tr31TestVectors.exports[vector]));
        text("tr31OptionalBlocksField", Tr31TestVectors.options[vector]);
    }

    private void rsaExport(String format) {
        shell.navigateToModule("RSA Key Exchange");
        text("rsaKexRecipientPemArea", format.equals("CMS EnvelopedData") ? receiverCert : receiverPublic);
        text("rsaKexKeyToWrapField", Tr31TestVectors.keys[1]);
        select("rsaKexExportProfileCombo", format);
        keys.handleRsaKexExport();
    }

    private void tr34Distribute(boolean twoPass) {
        shell.navigateToModule("TR-34 Key Distribution");
        text("tr34SenderPrivateKeyArea", senderPrivate);
        text("tr34SenderCertArea", senderCert);
        text("tr34ReceiverCertArea", receiverCert);
        text("tr34ReceiverPrivateKeyArea", receiverPrivate);
        text("tr34ExpectedSenderCertArea", senderCert);
        text("tr34KeyToDistributeField", Tr31TestVectors.keys[1]);
        text("tr34KeyIdField", "synthetic-key-id");
        if (twoPass) {
            keys.handleTr34GenerateChallenge();
            text("tr34BindingNonceField", text("tr34ChallengeNonceField"));
        }
        keys.handleTr34Distribute();
    }

    private OperationResult snapshot() {
        return field(shell, "lastPublishedResultSnapshot");
    }

    private String text(String id) {
        return ((TextInputControl) root.lookup("#" + id)).getText();
    }

    private void text(String id, String value) {
        ((TextInputControl) root.lookup("#" + id)).setText(value);
    }

    @SuppressWarnings("unchecked")
    private void select(String id, String value) {
        ((ComboBox<String>) root.lookup("#" + id)).setValue(value);
    }

    @SuppressWarnings("unchecked")
    private void selectPrefix(String id, String prefix) {
        var combo = (ComboBox<String>) root.lookup("#" + id);
        combo.setValue(combo.getItems().stream().filter(item -> item.startsWith(prefix)).findFirst().orElseThrow());
    }

    private void withLanguage(String language, FxAction action) throws Exception {
        var previous = AppSettings.getInstance().getLanguagePreference();
        try {
            fx(() -> {
                I18nService.getInstance().setPreference(com.cryptocarver.model.LanguagePreference.valueOf(language.toUpperCase(java.util.Locale.ROOT)));
                action.run();
            });
        } finally {
            fx(() -> I18nService.getInstance().setPreference(previous));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }

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
        assertTrue(done.await(30, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
