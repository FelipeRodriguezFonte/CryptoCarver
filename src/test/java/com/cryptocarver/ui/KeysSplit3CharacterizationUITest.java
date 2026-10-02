package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.crypto.hsm.*;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class KeysSplit3CharacterizationUITest {
    private ModernMainController shell;
    private KeysController keys;
    private Parent root;
    private Stage stage;
    private SecretVisibilityProfile previousVisibility;
    private LanguagePreference previousLanguage;
    private String previousRoute;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException started) {
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }

    @BeforeEach
    void loadProductionShell() throws Exception {
        var settings = AppSettings.getInstance();
        previousVisibility = settings.getSecretVisibilityProfile();
        previousLanguage = settings.getLanguagePreference();
        previousRoute = settings.getLastRoute();
        settings.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        settings.setLastRoute("");
        fx(() -> {
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            root = loader.load();
            shell = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1400, 900));
        });
        SimulatedHsmProvider.getInstance().resetForTest(Files.createTempFile(Path.of("target/test-home"), "split3-lab-", ".json"));
        ClipboardShelfManager.getInstance().clear();
    }

    @AfterEach
    void releaseShellAndRestoreSettings() throws Exception {
        fx(() -> {
            if (shell != null) shell.shutdown();
            if (stage != null) {
                stage.close();
                stage.setScene(null);
            }
            AppSettings.getInstance().setSecretVisibilityProfile(previousVisibility);
            I18nService.getInstance().setPreference(previousLanguage);
            AppSettings.getInstance().setLastRoute(previousRoute);
        });
        SimulatedHsmProvider.getInstance().clear();
        ClipboardShelfManager.getInstance().clear();
    }

    @Test
    void variantLmkRoundTripAndWrongKeyChangesCheckValue() throws Exception {
        fx(() -> {
            navigate("Variant LMK");
            keys.handleThalesLoadExample();
            String clear = text("thalesClearKeyField");
            String kcv = text("thalesCheckValueField");
            keys.handleThalesEncrypt();
            keys.handleThalesDecrypt();
            assertEquals(clear, text("thalesClearKeyField"));
            assertEquals(kcv, text("thalesCheckValueField"));
            text("thalesLmkField", "0123456789ABCDEFFEDCBA9876543210");
            keys.handleThalesDecrypt();
            assertNotEquals(kcv, text("thalesCheckValueField"));
        });
    }

    @Test
    void keyBlockLmkUnwrapsPublishedVector() throws Exception {
        fx(() -> {
            navigate("Variant LMK");
            keys.handleKeyBlockExample();
            keys.handleKeyBlockUnwrap();
            assertFalse(text("keyBlockResultArea").startsWith("Error"));
            assertTrue(text("keyBlockResultArea").contains("Key"));
        });
    }

    @Test
    void keyBlockLmkRejectsWrongLmk() throws Exception {
        fx(() -> {
            navigate("Variant LMK");
            keys.handleKeyBlockExample();
            text("keyBlockLmkField", "0123456789ABCDEF8080808080808080FEDCBA9876543212");
            keys.handleKeyBlockUnwrap();
            assertTrue(text("keyBlockResultArea").toLowerCase().contains("does not match"));
        });
    }

    @Test
    void thirdPaymentFormatRoundTripsAndSynchronizesHeader() throws Exception {
        fx(() -> {
            navigate("AKB");
            keys.handleAtallaExample();
            keys.handleAtallaGenerate();
            String report = text("atallaResultArea");
            assertTrue(report.startsWith("AKB: "));
            text("atallaBlockArea", report.split("\n")[0].substring(5));
            keys.handleAtallaUnwrap();
            assertTrue(text("atallaResultArea").contains(text("atallaKeyField")));
            assertEquals("1PUNE000", text("atallaHeaderField"));
            combo("atalla0Combo").getSelectionModel().select(1);
            assertNotEquals("1PUNE000", text("atallaHeaderField"));
            text("atallaHeaderField", "1PUNE000");
            assertEquals('1', ((AtallaAkbHeader.Option) combo("atalla0Combo").getValue()).code());
            combo("atallaTemplateCombo").getSelectionModel().selectFirst();
            assertEquals(((AtallaAkbHeader.Template) combo("atallaTemplateCombo").getValue()).header(), text("atallaHeaderField"));
        });
    }

    @Test
    void thirdPaymentFormatRejectsWrongMasterKey() throws Exception {
        fx(() -> {
            navigate("AKB");
            keys.handleAtallaExample();
            text("atallaMfkField", "0123456789ABCDEF8080808080808080FEDCBA9876543212");
            keys.handleAtallaUnwrap();
            assertTrue(text("atallaResultArea").contains("DOES NOT MATCH"));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"AES-128", "3DES-2KEY"})
    void symmetricGenerationPublishesKeySummaryKcvAndShelf(String algorithm) throws Exception {
        fx(() -> {
            navigate("Key Generation");
            combo("keyTypeCombo").setValue(algorithm);
            keys.handleGenerateKey();
            byte[] bytes = DataConverter.hexToBytes(text("generatedKeyField"));
            assertArrayEquals(bytes, snapshot().getOutput());
            assertEquals(algorithm, ((Label) root.lookup("#summaryAlgoLabel")).getText());
            byte[] kcv = algorithm.startsWith("AES") ? KeyOperations.calculateKCV_AES(bytes, 4) : KeyOperations.calculateKCV_VISA(bytes, 4);
            assertTrue(((Label) root.lookup("#summaryKcvLabel")).getText().contains(DataConverter.bytesToHex(kcv)));
            keys.handleGlobalSymmetricShelfAction();
            assertTrue(ClipboardShelfManager.getInstance().getEntries().stream().anyMatch(e -> e.getValue().equals(text("generatedKeyField"))));
            keys.handleOpenValidationAndKcv();
            keys.handleValidateKey();
            assertTrue(text("validationResultArea").contains(DataConverter.bytesToHex(kcv)));
        });
    }

    @Test
    void changingAlgorithmInvalidatesGeneratedSummary() throws Exception {
        fx(() -> {
            navigate("Key Generation");
            keys.handleGenerateKey();
            combo("keyTypeCombo").setValue("AES-256");
            assertEquals("", text("generatedKeyField"));
            assertFalse(root.lookup("#generatedKeySummaryCard").isVisible());
        });
    }

    @Test
    void xorComponentsRecombineToOriginalKey() throws Exception {
        fx(() -> {
            navigate("Split Key");
            String clear = "00112233445566778899AABBCCDDEEFF";
            text("keyToSplitField", clear);
            combo("numComponentsCombo").setValue("3");
            keys.handleSplitKey();
            assertFalse(text("component1Field").isBlank());
            keys.handleCombineComponents();
            assertTrue(text("componentResultsArea").contains("Combined Key: " + clear));
        });
    }

    @Test
    void invalidKeyDoesNotReplaceValidationReport() throws Exception {
        fx(() -> {
            navigate("Validation & KCV");
            text("keyInputField", "00112233445566778899AABBCCDDEEFF");
            keys.handleValidateKey();
            String validReport = text("validationResultArea");
            text("keyInputField", "0011");
            keys.handleValidateKey();
            assertEquals(validReport, text("validationResultArea"));
        });
    }

    @Test
    void keyLabGenerationSearchAndStatusFilterRetainDetails() throws Exception {
        fx(() -> {
            navigate("Key Lab");
            text("keyLabNewNameField", "Synthetic inventory entry");
            combo("keyLabNewAlgoCombo").setValue("DES");
            invoke("handleKeyLabGenerate");
            TableView<KeyMaterial> table = labTable();
            assertEquals(1, table.getItems().size());
            KeyMaterial material = table.getItems().get(0);
            assertEquals(material.getId(), text("keyLabDetailIdField"));
            text("keyLabSearchField", "not-present");
            assertTrue(table.getItems().isEmpty());
            text("keyLabSearchField", "Synthetic");
            assertEquals(1, table.getItems().size());
            SimulatedHsmProvider.getInstance().archiveKey(material.getId());
            combo("keyLabStatusFilterCombo").setValue("Active Only");
            keys.refreshKeyLabTable();
            assertTrue(table.getItems().isEmpty());
            keys.selectKeyInKeyLab(material.getId());
            assertEquals(material.getId(), table.getSelectionModel().getSelectedItem().getId());
        });
    }

    @ParameterizedTest
    @EnumSource(SecretVisibilityProfile.class)
    void keyLabRevealRespectsVisibilityProfile(SecretVisibilityProfile profile) throws Exception {
        fx(() -> {
            navigate("Key Lab");
            text("keyLabNewNameField", "Synthetic reveal entry");
            combo("keyLabNewAlgoCombo").setValue("DES");
            invoke("handleKeyLabGenerate");
            KeyMaterial material = labTable().getItems().get(0);
            String expected = DataConverter.bytesToHex(SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab(material.getId()));
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            keys.updateVisibilityControls();
            keys.selectKeyInKeyLab(material.getId());
            invoke("handleKeyLabReveal");
            if (profile == SecretVisibilityProfile.FULL_LAB) {
                assertEquals(expected, text("keyLabDetailValueField"));
            } else {
                assertNotEquals(expected, text("keyLabDetailValueField"));
                assertTrue(((Button) root.lookup("#keyLabRevealBtn")).isDisabled());
            }
        });
    }

    @Test
    void keyLabMetadataRoundTripExcludesSecretMaterial() throws Exception {
        Path file = Files.createTempFile(Path.of("target/test-home"), "split3-metadata-", ".json");
        fx(() -> {
            navigate("Key Lab");
            text("keyLabNewNameField", "Synthetic metadata entry");
            combo("keyLabNewAlgoCombo").setValue("DES");
            invoke("handleKeyLabGenerate");
            KeyMaterial material = labTable().getItems().get(0);
            String secret = DataConverter.bytesToHex(SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab(material.getId()));
            SimulatedHsmProvider.getInstance().exportMetadata(file.toFile());
            String manifest = Files.readString(file);
            assertFalse(manifest.contains(secret));
            assertFalse(manifest.contains("keyMaterialHex"));
            SimulatedHsmProvider.getInstance().clear();
            SimulatedHsmProvider.getInstance().importMetadata(file.toFile());
            keys.refreshKeyLabTable();
            assertEquals(1, labTable().getItems().size());
            assertFalse(labTable().getItems().get(0).hasKeyMaterial());
        });
        Files.delete(file);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pkcs12ListsEntriesAndOnlyExtractsWhenRequested(boolean unsafe) throws Exception {
        Path path = Files.createTempFile(Path.of("target/test-home"), "split3-store-", ".p12");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic store certificate";
        X509Certificate cert = CertificateGenerator.generateSelfSignedCertificate(pair, config);
        KeyStore store = KeyStore.getInstance("PKCS12");
        char[] password = "synthetic-test-password".toCharArray();
        store.load(null, password);
        store.setKeyEntry("synthetic", pair.getPrivate(), password, new java.security.cert.Certificate[]{cert});
        try (var out = Files.newOutputStream(path)) {
            store.store(out, password);
        }
        fx(() -> {
            navigate("KeyStore Inspector");
            text("keyStorePathField", path.toString());
            text("keyStorePasswordField", new String(password));
            ((CheckBox) root.lookup("#keyStoreUnsafeExtractCheck")).setSelected(unsafe);
            keys.handleInspectKeyStore();
            assertTrue(text("keyStoreReportArea").contains("Alias: synthetic"));
            assertEquals(unsafe, text("keyStoreReportArea").contains("EXPORTED KEY (HEX):"));
        });
        java.util.Arrays.fill(password, '\0');
        Files.delete(path);
    }

    @Test
    void lazyKeysModuleLoadsIncludedControllersAndHandlesNoToken() throws Exception {
        fx(() -> {
            navigate("Hashing");
            navigate("Key Lab");
            for (String name : List.of("pkcs11ProfilesController", "icsfTokenPaneController", "icsfBatchPaneController", "icsfKeyWrapPaneController")) {
                assertNotNull(field(keys, name));
            }
            keys.disconnectPkcs11();
            keys.refreshPkcs11SigningKeys();
            keys.refreshPkcs11CertificateAliases();
            keys.refreshPkcs11WrapKeyAliases();
            assertTrue(combo("pkcs11SigningKeyCombo").getItems().isEmpty());
        });
    }

    @Test
    void generatedCertificateAndCsrKeepSharedPairContract() throws Exception {
        fx(() -> {
            navigate("RSA Key Generation");
            combo("rsaKeySizeCombo").setValue(2048);
            keys.handleGenerateRSA();
            KeyPair generated = keys.getLastGeneratedKeyPair();
            assertNotNull(generated);
            navigate("Generate Certificate");
            keys = shell.getKeysController();
            text("certCNField", "Synthetic phase three certificate");
            keys.handleGenerateCertificate();
            assertSame(generated, keys.getLastGeneratedKeyPair());
            String output = text("certOutputArea");
            assertTrue(output.contains("BEGIN CERTIFICATE"));
            var cert = CertificateGenerator.parseCertificateChain(output.substring(output.indexOf("-----BEGIN CERTIFICATE-----"), output.indexOf("-----END CERTIFICATE-----") + "-----END CERTIFICATE-----".length())).get(0);
            cert.verify(cert.getPublicKey());
            keys.handleGenerateCSR();
            assertTrue(text("certOutputArea").contains("BEGIN CERTIFICATE REQUEST"));
        });
    }

    @Test
    void keyLabAesGenerationReportsExistingUnsupportedType() throws Exception {
        fx(() -> {
            navigate("Key Lab");
            text("keyLabNewNameField", "Synthetic AES entry");
            combo("keyLabNewAlgoCombo").setValue("AES");
            invoke("handleKeyLabGenerate");
            assertTrue(labTable().getItems().isEmpty());
            assertNotNull(((InlineErrorPresenter) field(shell, "inlineErrorPresenter")).getCurrentError());
        });
    }

    @Test
    void keyLabImportAcceptsSyntheticMaterialInFullLab() throws Exception {
        fx(() -> {
            navigate("Key Lab");
            text("keyLabNewNameField", "Synthetic imported entry");
            combo("keyLabNewAlgoCombo").setValue("AES");
            text("keyLabImportBytesField", "00112233445566778899AABBCCDDEEFF");
            invoke("handleKeyLabImport");
            assertEquals(1, labTable().getItems().size());
            assertEquals("Synthetic imported entry", labTable().getItems().get(0).getName());
        });
    }

    @Test
    void crlGenerationPublishesSignedEmptyCrl() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic CRL root";
        var cert = CertificateGenerator.generateRootCA(pair, config, 1);
        fx(() -> {
            navigate("Generate Certificate");
            text("crlIssuerCertArea", CertificateGenerator.exportCertificatePEM(cert));
            text("crlIssuerKeyArea", AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate()));
            keys.handleGenerateCrl();
            String pem = text("crlResultArea");
            assertTrue(pem.contains("BEGIN X509 CRL"));
            var factory = java.security.cert.CertificateFactory.getInstance("X.509");
            var crl = (java.security.cert.X509CRL) factory.generateCRL(new java.io.ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            crl.verify(pair.getPublic());
            assertNull(crl.getRevokedCertificates());
            assertEquals("Generate CRL", snapshot().getOperation());
        });
    }

    @Test
    void individualCertificateValidationPublishesReport() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic validation root";
        var cert = CertificateGenerator.generateRootCA(pair, config, 1);
        fx(() -> {
            navigate("Validate Certificate");
            text("valCertInput", CertificateGenerator.exportCertificatePEM(cert));
            text("valIssuerInput", CertificateGenerator.exportCertificatePEM(cert));
            keys.handleValidateCertificate();
            assertFalse(text("valResultArea").isBlank());
            assertFalse(text("valResultArea").startsWith("Error"));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Key Lab", "Key Generation", "Validation & KCV", "Split Key", "KeyStore Inspector", "Generate Certificate", "Variant LMK", "AKB"})
    void screenConfigurationRedactsSecretsAndSurvivesLanguageSwitch(String route) throws Exception {
        fx(() -> {
            navigate(route);
            Object owner = route.equals("Generate Certificate") ? field(shell, "certificatesContainerController") : keys;
            String input = switch (route) {
                case "Key Lab" -> "keyLabImportBytesField";
                case "Key Generation" -> "generatedKeyField";
                case "Validation & KCV" -> "keyInputField";
                case "Split Key" -> "keyToSplitField";
                case "KeyStore Inspector" -> "keyStorePasswordField";
                case "Generate Certificate" -> "certCNField";
                case "Variant LMK" -> "thalesClearKeyField";
                default -> "atallaKeyField";
            };
            String synthetic = input.equals("certCNField") ? "Synthetic portable CN" : "00112233445566778899AABBCCDDEEFF";
            text(input, synthetic);
            var snapshot = UiStateSnapshot.capturePortableConfiguration(owner);
            text(input, "");
            I18nService.getInstance().setPreference(LanguagePreference.ES);
            UiStateSnapshot.restore(owner, snapshot);
            assertEquals(route.equals("Key Lab") ? "" : synthetic, text(input));
            AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
            var history = UiStateSnapshot.captureHistoryRecipe(owner);
            assertEquals(route.equals("Generate Certificate"), history.containsValue(synthetic));
            assertNotNull(shell.getKeysController());
        });
    }

    private void invoke(String name) throws Exception {
        var method = KeysController.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(keys);
    }

    @SuppressWarnings("unchecked")
    private TableView<KeyMaterial> labTable() {
        return (TableView<KeyMaterial>) root.lookup("#keyLabTable");
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
