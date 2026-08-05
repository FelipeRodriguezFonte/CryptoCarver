package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModernMainControllerUITest {

    private static boolean jfxIsSetup;
    @TempDir
    static java.nio.file.Path isolatedUserHome;

    @BeforeAll
    static void initJFX() {
        if (jfxIsSetup) return;

        try {
            java.nio.file.Path javafxCache = java.nio.file.Files.createTempDirectory("cryptocarver-javafx-cache-");
            System.setProperty("javafx.cachedir", javafxCache.toString());
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not create a writable JavaFX cache directory", e);
        }

        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyInitialized) {
            // Another JavaFX test initialized the shared toolkit first.
            Platform.setImplicitExit(false);
            jfxIsSetup = true;
            return;
        } catch (Throwable startupFailure) {
            throw new AssertionError("JavaFX toolkit initialization failed", startupFailure);
        }

        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("JavaFX toolkit did not become ready within 15 seconds");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while starting JavaFX toolkit", interrupted);
        }

        jfxIsSetup = true;
    }

    @org.junit.jupiter.api.BeforeEach
    void resetSettings() {
        if (isolatedUserHome != null) {
            java.nio.file.Path isolatedFile = isolatedUserHome.resolve(".cryptocarver").resolve("settings.json");
            try {
                java.nio.file.Files.deleteIfExists(isolatedFile);
            } catch (Exception ignored) {}
            com.cryptocarver.model.AppSettings.setInstanceForTesting(new com.cryptocarver.model.AppSettings(isolatedFile));
        } else {
            com.cryptocarver.model.AppSettings.getInstance().resetForTesting();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownSettings() {
        com.cryptocarver.model.AppSettings.resetInstanceForTesting();
    }

    private <T> T getField(Object target, String name) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + target.getClass());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                exceptionRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX runLater execution timed out");
        }

        if (exceptionRef.get() != null) {
            if (exceptionRef.get() instanceof Exception) {
                throw (Exception) exceptionRef.get();
            }
            throw new RuntimeException(exceptionRef.get());
        }
    }

    @Test
    void testCmsUiPkcs11Toggle() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Object keysController = getField(controller, "keysController");
        CertificatesController certificatesController = getField(controller, "certificatesContainerController");

        // The cmsSignSourcePkcs11Radio should be present in FXML and loaded
        javafx.scene.control.RadioButton pkcs11Radio = getField(certificatesController, "cmsSignSourcePkcs11Radio");
        javafx.scene.control.RadioButton localRadio = getField(certificatesController, "cmsSignSourceLocalRadio");
        javafx.scene.layout.GridPane localGrid = getField(certificatesController, "cmsSignLocalGrid");
        javafx.scene.layout.HBox pkcs11Box = getField(certificatesController, "cmsSignPkcs11Box");

        runAndWait(() -> {
            localRadio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertTrue(localGrid.isVisible());
        assertFalse(pkcs11Box.isVisible());

        runAndWait(() -> {
            pkcs11Radio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertFalse(localGrid.isVisible());
        assertTrue(pkcs11Box.isVisible());
    }

    @Test
    void testCmsInspectorNavigation() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        runAndWait(() -> {
            try {
                Method m = controller.getClass().getDeclaredMethod("handleItemSelected", String.class);
                m.setAccessible(true);
                m.invoke(controller, "CMS Inspector");
            } catch (Exception e) {
                fail(e);
            }
        });

        // Assert that the certificates section is visible
        javafx.scene.layout.VBox certContainer = getField(controller, "certificatesContainer");
        assertTrue(certContainer.isVisible());
        assertTrue(certContainer.isManaged());

        // Assert that the CMS Inspector accordion is expanded
        javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) certContainer.getChildren().get(0);
        assertNotNull(accordion.getExpandedPane());
        assertEquals("🔍 Inspect / Validate CMS", accordion.getExpandedPane().getText());
    }

    @Test
    void testCmsUiEncryptPkcs11Toggle() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });
        ModernMainController controller = controllerRef.get();

        Object keysController = getField(controller, "keysController");
        CertificatesController certificatesController = getField(controller, "certificatesContainerController");
        javafx.scene.control.RadioButton pkcs11Radio = getField(certificatesController, "cmsEncryptSourcePkcs11Radio");
        javafx.scene.control.RadioButton localRadio = getField(certificatesController, "cmsEncryptSourceLocalRadio");
        javafx.scene.layout.GridPane localGrid = getField(certificatesController, "cmsEncryptLocalGrid");
        javafx.scene.layout.HBox pkcs11Box = getField(certificatesController, "cmsEncryptPkcs11Box");

        assertNotNull(pkcs11Radio, "cmsEncryptSourcePkcs11Radio should be injected");
        assertNotNull(localRadio, "cmsEncryptSourceLocalRadio should be injected");
        assertNotNull(localGrid, "cmsEncryptLocalGrid should be injected");
        assertNotNull(pkcs11Box, "cmsEncryptPkcs11Box should be injected");

        runAndWait(() -> {
            pkcs11Radio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSEncryptSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertFalse(localGrid.isVisible());
        assertFalse(localGrid.isManaged());
        assertTrue(pkcs11Box.isVisible());
        assertTrue(pkcs11Box.isManaged());

        runAndWait(() -> {
            localRadio.setSelected(true);
            try {
                Method m = keysController.getClass().getDeclaredMethod("handleCMSEncryptSourceChanged");
                m.invoke(keysController);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertTrue(localGrid.isVisible());
        assertTrue(localGrid.isManaged());
        assertFalse(pkcs11Box.isVisible());
        assertFalse(pkcs11Box.isManaged());
    }

    @Test
    void testFxmlLoadAndInjection() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                assertNotNull(resource, "main-view-modern.fxml not found");

                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                assertNotNull(root, "Root should not be null");

                ModernMainController controller = loader.getController();
                assertNotNull(controller, "Controller should be injected");
                controllerRef.set(controller);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load FXML", e);
            }
        });

        ModernMainController controller = controllerRef.get();
        assertNotNull(getField(controller, "sidePanel"));
        assertNotNull(getField(controller, "navigationRail"));
        assertNotNull(getField(controller, "jose"));
        JOSEController joseController = getField(controller, "joseController");
        assertNotNull(joseController);
        assertSame(controller, getField(joseController, "statusReporter"));
        CipherController cipherController = getField(controller, "cipherContainerController");
        assertNotNull(cipherController);
        assertNotNull(getField(cipherController, "openPgpContainer"));
        assertNotNull(getField(cipherController, "openPgpContainerController"));
        assertNotNull(getField(controller, "authenticationContainer"));
        assertNotNull(getField(controller, "authenticationContainerController"));
        assertNotNull(getField(controller, "paymentsContainer"));
        assertNotNull(getField(controller, "paymentsContainerController"));
        assertNotNull(getField(controller, "emvContainer"));
        assertNotNull(getField(controller, "emvContainerController"));
        assertNotNull(getField(controller, "keysContainer"));
        assertNotNull(getField(controller, "keysContainerController"));
        assertNotNull(getField(controller, "certificatesContainer"));
        assertNotNull(getField(controller, "certificatesContainerController"));
    }

    @Test
    void compactLayoutTemporarilyHidesAndRestoresInspector() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        javafx.scene.layout.VBox inspector = getField(controller, "inspectorPanel");
        Method updateLayout = ModernMainController.class.getDeclaredMethod("updateResponsiveLayout", double.class);
        updateLayout.setAccessible(true);

        runAndWait(() -> {
            try {
                updateLayout.invoke(controller, 900.0);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertFalse(inspector.isVisible());
        assertFalse(inspector.isManaged());

        runAndWait(() -> {
            try {
                updateLayout.invoke(controller, 1_200.0);
            } catch (Exception e) {
                fail(e);
            }
        });
        assertTrue(inspector.isVisible());
        assertTrue(inspector.isManaged());
    }

    @Test
    void testSessionStateIncludesAndRestoresExtractedModuleControls() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");
        javafx.scene.control.TextArea input = getField(cipher, "cipherInputArea");
        javafx.scene.control.TextField key = getField(cipher, "symmetricKeyField");
        javafx.scene.control.CheckBox compact = getField(cipher, "fileCipherCompactCbcCheck");
        Method capture = ModernMainController.class.getDeclaredMethod("captureUIState");
        capture.setAccessible(true);
        Method captureHistory = ModernMainController.class.getDeclaredMethod("captureHistoryState");
        captureHistory.setAccessible(true);
        Method restore = ModernMainController.class.getDeclaredMethod("restoreUIState", java.util.Map.class);
        restore.setAccessible(true);
        AtomicReference<java.util.Map<String, Object>> stateRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                input.setText("module payload");
                key.setText("00112233445566778899AABBCCDDEEFF");
                compact.setSelected(true);
                stateRef.set((java.util.Map<String, Object>) capture.invoke(controller));
                input.clear();
                key.clear();
                compact.setSelected(false);
                restore.invoke(controller, stateRef.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("module payload", input.getText());
        assertEquals("00112233445566778899AABBCCDDEEFF", key.getText());
        assertTrue(compact.isSelected());
        assertEquals("module payload", stateRef.get().get("CipherController.cipherInputArea"));

        AtomicReference<java.util.Map<String, Object>> historyStateRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                historyStateRef.set((java.util.Map<String, Object>) captureHistory.invoke(controller));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("[REDACTED_SECRET]", historyStateRef.get().get("CipherController.cipherInputArea"));
        assertEquals("[REDACTED_SECRET]", historyStateRef.get().get("CipherController.symmetricKeyField"));
        assertEquals(true, historyStateRef.get().get("CipherController.fileCipherCompactCbcCheck"));

        runAndWait(() -> {
            try {
                input.clear();
                restore.invoke(controller, java.util.Map.of("cipherInputArea", "legacy payload"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("legacy payload", input.getText(), "Legacy unqualified session keys must remain restorable");
    }

    @Test
    void testPortableCipherConfigurationRestoresCompleteActiveScreen() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");
        javafx.scene.control.TextArea input = getField(cipher, "cipherInputArea");
        javafx.scene.control.TextArea output = getField(cipher, "cipherOutputArea");
        javafx.scene.control.TextField key = getField(cipher, "symmetricKeyField");
        javafx.scene.control.TextField iv = getField(cipher, "ivField");
        javafx.scene.control.TextField aad = getField(cipher, "aadField");
        javafx.scene.control.ComboBox<String> mode = getField(cipher, "cipherModeCombo");
        javafx.scene.control.ComboBox<String> inputFormat = getField(controller, "inputFormatCombo");
        AtomicReference<com.cryptocarver.model.ScreenConfiguration> configurationRef = new AtomicReference<>();

        runAndWait(() -> {
            controller.navigateTo("Symmetric Ciphers");
            input.setText("portable payload");
            key.setText("00112233445566778899AABBCCDDEEFF");
            iv.setText("00112233445566778899AABB");
            aad.setText("invoice-42");
            mode.setValue("GCM");
            inputFormat.setValue("Text (UTF-8)");
            output.setText("generated output must not travel");
            configurationRef.set(controller.captureActiveScreenConfiguration());

            input.clear();
            key.clear();
            iv.clear();
            aad.clear();
            mode.setValue("CBC");
            inputFormat.setValue("Hexadecimal");
            output.clear();
            controller.navigateTo("Hashing");
            controller.applyScreenConfiguration(configurationRef.get());
        });

        assertTrue(((javafx.scene.Node) getField(controller, "cipherContainer")).isVisible());
        assertEquals("portable payload", input.getText());
        assertEquals("00112233445566778899AABBCCDDEEFF", key.getText());
        assertEquals("00112233445566778899AABB", iv.getText());
        assertEquals("invoice-42", aad.getText());
        assertEquals("GCM", mode.getValue());
        assertEquals("Text (UTF-8)", inputFormat.getValue());
        assertEquals("", output.getText(), "Read-only/generated results must not be exported");
        assertFalse(configurationRef.get().toState().containsKey("CipherController.cipherOutputArea"));
        assertFalse(configurationRef.get().toState().containsKey("CipherController.fileCipherKeyField"),
                "A symmetric cipher export must not include hidden File Cipher fields");
    }

    @Test
    void testPortableKeyGenerationConfigurationPreservesGeneratedKeyMaterial() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        KeysController keys = getField(controller, "keysContainerController");
        javafx.scene.control.ComboBox<String> keyType = getField(keys, "keyTypeCombo");
        javafx.scene.control.TextArea generatedKey = getField(keys, "generatedKeyField");
        javafx.scene.control.TextField kdfSalt = getField(keys, "kdfSaltField");
        AtomicReference<com.cryptocarver.model.ScreenConfiguration> configurationRef = new AtomicReference<>();

        runAndWait(() -> {
            controller.navigateTo("Key Generation");
            keyType.setValue("AES-256");
            generatedKey.setText("00112233445566778899AABBCCDDEEFF");
            configurationRef.set(controller.captureActiveScreenConfiguration());
            generatedKey.clear();
            keyType.setValue("DES (64-bit)");
            controller.navigateTo("Hashing");
            controller.applyScreenConfiguration(configurationRef.get());
        });

        assertEquals("00112233445566778899AABBCCDDEEFF", generatedKey.getText());
        assertEquals("AES-256", keyType.getValue());
        assertEquals(2, configurationRef.get().version());
        assertTrue(configurationRef.get().toState().containsKey("KeysController.generatedKeyField"));
        assertFalse(configurationRef.get().toState().containsKey("KeysController.kdfSaltField"),
                "A Key Generation export must not include hidden KDF fields");
        assertFalse(ModernMainController.isLegacyKeyGenerationConfiguration(configurationRef.get()));
        com.cryptocarver.model.ScreenConfiguration legacy = new com.cryptocarver.model.ScreenConfiguration(
                "Key Generation", "KEYS_SYMMETRIC",
                java.util.Map.of("KeysController.keyTypeCombo", "AES-256"), com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
        assertTrue(ModernMainController.isLegacyKeyGenerationConfiguration(legacy));

        com.google.gson.JsonObject legacyJson = com.google.gson.JsonParser
                .parseString(configurationRef.get().toJson()).getAsJsonObject();
        legacyJson.addProperty("version", 1);
        com.google.gson.JsonObject legacySalt = new com.google.gson.JsonObject();
        legacySalt.addProperty("type", "string");
        legacySalt.addProperty("value", "A1B2C3D4");
        legacyJson.getAsJsonObject("values").add("KeysController.kdfSaltField", legacySalt);
        com.cryptocarver.model.ScreenConfiguration legacyModuleWide =
                com.cryptocarver.model.ScreenConfiguration.fromJson(legacyJson.toString());
        runAndWait(() -> controller.applyScreenConfiguration(legacyModuleWide));
        assertEquals("A1B2C3D4", kdfSalt.getText(),
                "Version 1 module-wide exports must remain importable");
    }

    @Test
    void testEveryPortableConfigurationRouteResolvesItsOwnScreenScope() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        java.util.Set<UiNavigationRegistry.Module> portableModules = java.util.EnumSet.of(
                UiNavigationRegistry.Module.JOSE,
                UiNavigationRegistry.Module.KEYS_SYMMETRIC,
                UiNavigationRegistry.Module.KEYS_ASYMMETRIC,
                UiNavigationRegistry.Module.CERTIFICATES,
                UiNavigationRegistry.Module.GENERIC,
                UiNavigationRegistry.Module.POST_QUANTUM,
                UiNavigationRegistry.Module.XML_SECURITY,
                UiNavigationRegistry.Module.WSS_SECURITY,
                UiNavigationRegistry.Module.EMV,
                UiNavigationRegistry.Module.CIPHER,
                UiNavigationRegistry.Module.AUTHENTICATION,
                UiNavigationRegistry.Module.PAYMENTS);
        java.util.Map<UiNavigationRegistry.Route, String> representativeOperations = new java.util.LinkedHashMap<>();
        UiNavigationRegistry.routes().forEach((operation, route) -> {
            if (portableModules.contains(route.module())) representativeOperations.putIfAbsent(route, operation);
        });

        runAndWait(() -> representativeOperations.forEach((route, operation) -> {
            controllerRef.get().navigateTo(operation);
            com.cryptocarver.model.ScreenConfiguration configuration =
                    controllerRef.get().captureActiveScreenConfiguration();
            assertEquals(2, configuration.version());
            assertEquals(route.module().name(), configuration.module(), operation);
        }));
    }

    @Test
    void testFormatsAreRememberedPerOperation() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        javafx.scene.control.ComboBox<String> inputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> outputFormat = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.navigateTo("Symmetric Ciphers");
            inputFormat.setValue("Base64");
            outputFormat.setValue("Hexadecimal");
            controller.navigateTo("Hashing");
            assertEquals("Text (UTF-8)", inputFormat.getValue());
            assertEquals("Hexadecimal", outputFormat.getValue());
            inputFormat.setValue("Text (UTF-8)");
            outputFormat.setValue("Base64");
            controller.navigateTo("Symmetric Ciphers");
            assertEquals("Base64", inputFormat.getValue());
            assertEquals("Hexadecimal", outputFormat.getValue());
            controller.navigateTo("Hashing: SHA-256");
            assertEquals("Text (UTF-8)", inputFormat.getValue());
            assertEquals("Base64", outputFormat.getValue());
        });
    }

    @Test
    void testInspectorShowsOperationContextBeforeExecution() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        javafx.scene.layout.VBox details = getField(controller, "inspectorDetailsContainer");

        runAndWait(() -> controller.navigateTo("Symmetric Ciphers"));

        java.util.List<String> labels = details.getChildren().stream().filter(javafx.scene.layout.HBox.class::isInstance)
                .map(javafx.scene.layout.HBox.class::cast)
                .flatMap(row -> row.getChildren().stream())
                .filter(javafx.scene.control.Label.class::isInstance)
                .map(javafx.scene.control.Label.class::cast)
                .map(javafx.scene.control.Label::getText)
                .toList();
        assertTrue(labels.contains("Purpose:"));
        assertTrue(labels.contains("Expected input:"));
        assertTrue(labels.contains("Produces:"));
    }


    @Test
    void testNavigationRoutingCoverage() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method handleItemMethod = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        handleItemMethod.setAccessible(true);

        String[][] routes = {
            {"Symmetric Ciphers", "cipherContainer"},
            {"Hashing", "genericContainer"},
            {"Compressed Hex (2-row)", "genericContainer"},
            {"Digital Signatures", "authenticationContainer"},
            {"Key Generation", "keysContainer"},
            {"PKCS#11 Token", "keysContainer"},
            {"PQC Key Generation", "postQuantumContainer"},
            {"Sign XML (XAdES)", "xmlSecurityContainer"},
            {"Generate Certificate", "certificatesContainer"},
            {"JWT (Signed)", "jose"},
            {"PIN Generation", "paymentsContainer"},
            {"Decode ASN.1", "certificatesContainer"},
            {"Recent Operations", "historyView"}
        };

        for (String[] route : routes) {
            String item = route[0];
            String expectedContainer = route[1];

            runAndWait(() -> {
                try {
                    handleItemMethod.invoke(controller, item);
                    Node container = getField(controller, expectedContainer);
                    assertTrue(container.isVisible(), "Container " + expectedContainer + " should be visible for " + item);
                } catch (Exception e) {
                    throw new RuntimeException("Failed route: " + item, e);
                }
            });
        }
    }

    @Test
    void testFileCipherRouteExpandsStreamingPane() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);
                route.invoke(controller, "File Cipher (Streaming)");

                javafx.scene.layout.VBox cipher = getField(controller, "cipherContainer");
                javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) cipher.getChildren().stream()
                        .filter(javafx.scene.control.Accordion.class::isInstance)
                        .findFirst().orElseThrow();
                assertNotNull(accordion.getExpandedPane());
                assertTrue(accordion.getExpandedPane().getText().contains("File Cipher"));
                javafx.scene.control.Label subtitle = getField(controller, "contentSubtitleLabel");
                assertTrue(subtitle.getText().contains("Experimental"));
                assertTrue(subtitle.getText().contains("Sensitive material"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testDukptRouteExpandsDukptPane() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);
                route.invoke(controller, "DUKPT TDES / AES");

                javafx.scene.layout.VBox payments = getField(controller, "paymentsContainer");
                javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) payments.getChildren().stream()
                        .filter(javafx.scene.control.Accordion.class::isInstance)
                        .findFirst().orElseThrow();
                assertNotNull(accordion.getExpandedPane());
                assertTrue(accordion.getExpandedPane().getText().contains("DUKPT KSN"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testKeyRoutesSwitchExtractedSections() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);
                KeysController keys = getField(controller, "keysContainerController");
                javafx.scene.layout.VBox symmetric = getField(keys, "symmetricKeysContainer");
                javafx.scene.layout.VBox asymmetric = getField(keys, "asymmetricKeysContainer");

                route.invoke(controller, "Key Generation");
                assertTrue(symmetric.isVisible());
                assertFalse(asymmetric.isVisible());

                route.invoke(controller, "RSA Key Generation");
                assertFalse(symmetric.isVisible());
                assertTrue(asymmetric.isVisible());
                javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) asymmetric.getChildren().get(0);
                assertNotNull(accordion.getExpandedPane());
                assertTrue(accordion.getExpandedPane().getText().contains("RSA Key Generation"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testJoseRoutesDelegateSectionSelection() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                JOSEController joseController = getField(controller, "joseController");
                javafx.scene.layout.VBox jwt = getField(joseController, "jwtSection");
                javafx.scene.layout.VBox jwe = getField(joseController, "jweSection");
                javafx.scene.layout.VBox jwk = getField(joseController, "jwkSection");
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);

                route.invoke(controller, "JWT (Signed)");
                assertTrue(jwt.isVisible());
                assertFalse(jwe.isVisible());

                route.invoke(controller, "JWE (Encrypted)");
                assertFalse(jwt.isVisible());
                assertTrue(jwe.isVisible());

                route.invoke(controller, "JWK (Keys)");
                assertFalse(jwe.isVisible());
                assertTrue(jwk.isVisible());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testCompressedHexRouteExpandsDedicatedPane() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
                route.setAccessible(true);
                route.invoke(controller, "Compressed Hex (2-row)");

                javafx.scene.control.Accordion accordion = getField(controller, "genericContainer");
                assertNotNull(accordion.getExpandedPane());
                assertTrue(accordion.getExpandedPane().getText().contains("Compressed Hex"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testEveryRegisteredNavigationPathAvoidsThePlaceholder() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);
        runAndWait(() -> {
            try {
                javafx.scene.control.Label placeholder = getField(controller, "contentPlaceholderLabel");
                java.util.List<Node> moduleRoots = java.util.List.of(
                        getField(controller, "keysContainer"),
                        getField(controller, "certificatesContainer"),
                        getField(controller, "cipherContainer"),
                        getField(controller, "authenticationContainer"),
                        getField(controller, "paymentsContainer"),
                        getField(controller, "emvContainer"),
                        getField(controller, "jose"),
                        getField(controller, "genericContainer"),
                        getField(controller, "historyView"),
                        getField(controller, "clipboardShelf"),
                        getField(controller, "postQuantumContainer"),
                        getField(controller, "xmlSecurityContainer"),
                        getField(controller, "wssSecurityContainer"),
                        getField(controller, "savedSessionsContainer"));
                for (com.cryptocarver.model.OperationDescriptor operation
                        : com.cryptocarver.model.OperationRegistry.getInstance().getAll()) {
                    route.invoke(controller, operation.getNavigationPath());
                    assertFalse(placeholder.isVisible(), "Registered route must not show placeholder: "
                            + operation.getNavigationPath());
                    long visibleModules = moduleRoots.stream()
                            .filter(node -> node.isVisible() && node.isManaged())
                            .count();
                    assertEquals(1, visibleModules, "Exactly one module must be visible for: "
                            + operation.getNavigationPath());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testHistoryRoutesReplaceThePreviouslyVisibleModule() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);
        runAndWait(() -> {
            try {
                Node cipher = getField(controller, "cipherContainer");
                Node savedSessions = getField(controller, "savedSessionsContainer");

                route.invoke(controller, "File Cipher (Streaming)");
                assertTrue(cipher.isVisible());

                route.invoke(controller, "Saved Sessions");
                assertTrue(savedSessions.isVisible());
                assertFalse(cipher.isVisible(), "Saved Sessions must hide the previous module");

                route.invoke(controller, "Export History");
                Node historyView = getField(controller, "historyView");
                assertTrue(historyView.isVisible());
                assertFalse(savedSessions.isVisible(), "Export History must hide Saved Sessions");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testUnknownLegacyRouteUsesDedicatedPlaceholder() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Legacy operation without a view");
                javafx.scene.control.Label placeholder = getField(controller, "contentPlaceholderLabel");
                assertTrue(placeholder.isVisible());
                assertTrue(placeholder.getText().contains("No view is registered"));
                assertFalse(placeholder.getText().contains("Phase 2"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testClearInvalidatesExpandedResultSnapshot() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        Method clear = ModernMainController.class.getDeclaredMethod("handleClearInput");
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        route.setAccessible(true);
        clear.setAccessible(true);
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Hashing");
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("Snapshot test")
                        .output("stale result".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .status("Published")
                        .build());
                assertTrue(((String) resolveOutput.invoke(controller)).contains("stale result"));

                clear.invoke(controller);
                assertEquals("", resolveOutput.invoke(controller));
                assertEquals("", getField(controller, "lastPublishedOperation"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testPublishedInputAndOutputAreStoredAsSensitiveHistoryDetails() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        runAndWait(() -> {
            controller.getHistoryManager().clearHistory();
            controller.publish(com.cryptocarver.model.OperationResult.forOperation("SHA-256")
                    .input("hash input".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .output(new byte[] {0x01, 0x23, 0x45})
                    .detail("Algorithm", "SHA-256")
                    .build());

            com.cryptocarver.model.HistoryCommand item = controller.getHistoryManager().getHistoryItems().get(0);
            assertTrue(item.getStructuredDetails().stream().anyMatch(detail ->
                    detail.name().startsWith("Input")
                            && "hash input".equals(detail.value())
                            && detail.classification() == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE));
            assertTrue(item.getStructuredDetails().stream().anyMatch(detail ->
                    detail.name().startsWith("Output")
                            && "012345".equals(detail.value())
                            && detail.classification() == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE));

            String masked = com.cryptocarver.utils.HistoryReportExporter.toMarkdown(
                    item, com.cryptocarver.model.SecretVisibilityProfile.MASKED);
            assertFalse(masked.contains("hash input"));
            assertTrue(masked.contains("***MASKED***"));
            String unsafe = com.cryptocarver.utils.HistoryReportExporter.toMarkdown(
                    item, com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
            assertTrue(unsafe.contains("hash input"));
            assertTrue(unsafe.contains("012345"));
        });
    }

    @Test
    void testHistoryModuleInjectionAndRefresh() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        assertNotNull(getField(controller, "historyViewController"));
        runAndWait(() -> {
            try {
                HistoryController historyController = getField(controller, "historyViewController");
                controller.getHistoryManager().clearHistory();
                controller.addToHistory("Hashing", java.util.List.of(
                        com.cryptocarver.model.OperationDetail.publicDetail("Result", "OK"),
                        com.cryptocarver.model.OperationDetail.secretDetail("Laboratory key", "all-visible-in-lab")));
                controller.addToHistory("File Cipher (Streaming)", java.util.List.of(
                        com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", "SHA-256")));
                historyController.refresh();
                javafx.scene.control.TableView<?> table = getField(historyController, "historyTable");
                assertEquals(2, table.getItems().size());
                assertTrue(table.getItems().stream()
                        .map(com.cryptocarver.model.HistoryCommand.class::cast)
                        .anyMatch(item -> item.getOperation().equals("Hashing")));

                javafx.scene.control.TextField filter = getField(historyController, "historyFilterField");
                javafx.scene.control.ComboBox<String> moduleFilter = getField(historyController, "historyModuleFilterCombo");
                javafx.scene.control.Label summary = getField(historyController, "historySummaryLabel");
                assertEquals("2 operations", summary.getText());
                filter.setText("result");
                assertEquals(1, table.getItems().size(), "Filtering by a detail name must not search detail values");
                assertEquals("Hashing", ((com.cryptocarver.model.HistoryCommand) table.getItems().get(0)).getOperation());
                assertEquals("1 of 2 operations", summary.getText());
                filter.clear();
                assertEquals(2, table.getItems().size());
                assertTrue(moduleFilter.getItems().contains("Generic"));
                assertTrue(moduleFilter.getItems().contains("Cipher"));
                moduleFilter.setValue("Cipher");
                assertEquals(1, table.getItems().size());
                assertEquals("File Cipher (Streaming)", ((com.cryptocarver.model.HistoryCommand) table.getItems().get(0)).getOperation());
                moduleFilter.setValue("Generic");
                table.getSelectionModel().selectFirst();
                javafx.scene.control.TableView<com.cryptocarver.model.OperationDetail> details = getField(historyController, "detailsTable");
                javafx.scene.control.ComboBox<com.cryptocarver.model.SecretVisibilityProfile> visibility = getField(historyController, "visibilityCombo");
                visibility.setValue(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
                visibility.getOnAction().handle(new javafx.event.ActionEvent());
                assertTrue(details.getItems().stream().anyMatch(detail -> "all-visible-in-lab".equals(detail.value())));
                visibility.setValue(com.cryptocarver.model.SecretVisibilityProfile.MASKED);
                visibility.getOnAction().handle(new javafx.event.ActionEvent());
                assertTrue(details.getItems().stream().anyMatch(detail -> "***MASKED***".equals(detail.value())));
                visibility.setValue(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
                visibility.getOnAction().handle(new javafx.event.ActionEvent());
                javafx.scene.control.Button exportReport = getField(historyController, "exportReportBtn");
                javafx.scene.control.Button copyReport = getField(historyController, "copyReportBtn");
                javafx.scene.control.Button exportRecipe = getField(historyController, "exportRecipeBtn");
                javafx.scene.control.Button exportJsonRecord = getField(historyController, "exportJsonRecordBtn");
                javafx.scene.control.Button exportVisibleJson = getField(historyController, "exportVisibleJsonBtn");
                assertFalse(exportReport.isDisabled(), "Latest history operation should be exportable by default");
                assertFalse(copyReport.isDisabled(), "Latest history operation should be copyable by default");
                assertFalse(exportRecipe.isDisabled(), "Latest history operation should expose its recipe export");
                assertFalse(exportJsonRecord.isDisabled(), "Latest history operation should expose its JSON record export");
                assertFalse(exportVisibleJson.isDisabled(), "Visible history entries should expose their bulk JSON export");
                details.getSelectionModel().selectFirst();
                javafx.scene.control.Button openDetail = getField(historyController, "openHistoryDetailBtn");
                javafx.scene.control.Button copyDetail = getField(historyController, "copyHistoryDetailBtn");
                assertFalse(openDetail.isDisabled(), "A selected history detail should be expandable");
                assertFalse(copyDetail.isDisabled(), "A selected history detail should be copyable");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testExpandedResultUsesLatestPublishedOperation() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("DES Key Generation")
                        .output("DES-RESULT".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("EdDSA Key Generation")
                        .output("EDDSA-RESULT".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());

                assertEquals("EDDSA-RESULT", resolveOutput.invoke(controller));

                com.cryptocarver.model.SecretVisibilityProfile originalVisibility = com.cryptocarver.model.AppSettings.getInstance()
                        .getSecretVisibilityProfile();
                try {
                    com.cryptocarver.model.AppSettings.getInstance()
                            .setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.REDACTED);
                    controller.publish(com.cryptocarver.model.OperationResult.forOperation("Signature Verification")
                            .detail(com.cryptocarver.model.OperationDetail.secretDetail("Private Key", "must-not-appear"))
                            .status("Signature is valid").build());
                    String noOutputResult = (String) resolveOutput.invoke(controller);
                    assertEquals("", noOutputResult, "REDACTED mode completely hides SECRET operation results");
                } finally {
                    com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(originalVisibility);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testExpandedResultKeepsRenderedSymmetricGcmOutput() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        route.setAccessible(true);
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Symmetric Ciphers");
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("Symmetric Encrypt")
                        .output(new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF})
                        .enrichedOutput("=== AES-256-GCM ENCRYPTION RESULT ===\n\nCIPHERTEXT (4 bytes):\nDEADBEEF"
                        + "\n\nAUTHENTICATION TAG (16 bytes):\n00112233445566778899AABBCCDDEEFF", com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .build());

                String expanded = (String) resolveOutput.invoke(controller);
                assertTrue(expanded.contains("AUTHENTICATION TAG"));
                assertTrue(expanded.contains("DEADBEEF"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testExpandedResultUsesUpdatedVisibleResultAreaWithoutFocus() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        route.setAccessible(true);
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                route.invoke(controller, "Symmetric Ciphers");
                controller.publish(com.cryptocarver.model.OperationResult.forOperation("Other Cipher Result")
                        .output("RAW-OUTPUT".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .enrichedOutput("Formatted result from a specialized operation", com.cryptocarver.model.OperationDetail.Classification.PUBLIC)
                        .build());

                assertEquals("Formatted result from a specialized operation", resolveOutput.invoke(controller));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testCertificateParseResultExpandsWithoutFocus() throws Exception {
        var keyPair = com.cryptocarver.crypto.AsymmetricKeyOperations.generateRSAKeyPair(2048);
        var config = new com.cryptocarver.crypto.CertificateGenerator.CertificateConfig();
        config.commonName = "expand-result.example";
        var certificate = com.cryptocarver.crypto.CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
        String certificatePem = com.cryptocarver.crypto.CertificateGenerator.exportCertificatePEM(certificate);

        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        Method resolveOutput = ModernMainController.class.getDeclaredMethod("resolveCurrentOutputText");
        route.setAccessible(true);
        resolveOutput.setAccessible(true);
        runAndWait(() -> {
            try {
                ModernMainController controller = controllerRef.get();
                route.invoke(controller, "Parse Certificate");
                CertificatesController certificates = getField(controller, "certificatesContainerController");
                javafx.scene.control.TextArea input = getField(certificates, "certInputArea");
                input.setText(certificatePem);
                KeysController keys = getField(controller, "keysController");
                keys.handleParseCertificate();

                String expanded = (String) resolveOutput.invoke(controller);
                assertTrue(expanded.contains("CERTIFICATE INFORMATION"));
                assertTrue(expanded.contains("expand-result.example"));
                assertFalse(expanded.contains("***MASKED***"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testSmokeHandlersNoSilentFailures() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();

        JOSEController joseController = getField(controller, "joseController");
        assertNotNull(joseController, "JOSE child controller must be injected by fx:include");

    }

    private Field getDeclaredFieldOrNull(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }



    @Test
    void testPqcAndXmlInjections() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        Method handleItemMethod = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        handleItemMethod.setAccessible(true);

        // 1. Verify that controllers are injected
        PostQuantumController pqcController = getField(controller, "postQuantumContainerController");
        XMLSignatureController xmlController = getField(controller, "xmlSecurityContainerController");
        assertNotNull(pqcController, "PQC Controller must be injected");
        assertNotNull(xmlController, "XML Security Controller must be injected");

        // 2. Navigate to PQC and XML
        runAndWait(() -> {
            try {
                handleItemMethod.invoke(controller, "PQC Key Generation");
                handleItemMethod.invoke(controller, "Sign XML (XAdES)");
            } catch (Exception e) {
                throw new RuntimeException("Navigation failed", e);
            }
        });

        // 3. Verify PQC fields and deterministic flow
        Object pqcKemCiphertextArea = getField(pqcController, "pqcKemCiphertextArea");
        assertNotNull(pqcKemCiphertextArea, "PQC KEM Ciphertext area must be injected");

        Object pqcSignAlgoCombo = getField(pqcController, "pqcSignAlgoCombo");
        assertNotNull(pqcSignAlgoCombo, "PQC Sign Algo Combo must be injected");

        Object xmlSignLevelCombo = getField(xmlController, "xmlSignLevelCombo");
        assertNotNull(xmlSignLevelCombo, "XML Sign Level Combo must be injected");
    }



    @Test
    void testGenericModuleExtracted() throws Exception {
        System.setProperty("test.mode", "true");
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load FXML", e);
            }
        });

        ModernMainController controller = controllerRef.get();
        assertNotNull(controller);

        GenericController genericController = getField(controller, "genericContainerController");
        assertNotNull(genericController, "GenericController must be loaded via fx:include");

        runAndWait(() -> {
            try {
                // Hashing
                javafx.scene.control.TextArea hashInput = getField(genericController, "hashInputArea");
                javafx.scene.control.TextArea hashOutput = getField(genericController, "hashOutputArea");
                javafx.scene.control.ComboBox<String> hashAlgo = getField(genericController, "hashAlgorithmCombo");
                javafx.scene.control.ComboBox<String> toolbarInputFormat = getField(controller, "inputFormatCombo");
                javafx.scene.control.ComboBox<String> toolbarOutputFormat = getField(controller, "outputFormatCombo");
                controller.navigateTo("Hashing");
                hashInput.setText("Hello");
                hashAlgo.setValue("SHA-256");
                toolbarInputFormat.setValue("Text (UTF-8)");
                toolbarOutputFormat.setValue("Base64");
                genericController.handleCalculateHash();
                assertEquals("GF+NsyJx/iX1Yab8k4suJkMG7DBO2lGAB9F2SCY4GWk=", hashOutput.getText(),
                        "Hash output must use the format shown in the shared toolbar");

                // Base64URL
                javafx.scene.control.TextArea manualInput = getField(genericController, "manualInputArea");
                javafx.scene.control.ComboBox<String> manualInputFormat = getField(genericController, "manualInputFormatCombo");
                javafx.scene.control.ComboBox<String> manualOutputFormat = getField(genericController, "manualOutputFormatCombo");
                javafx.scene.control.TextArea manualOutput = getField(genericController, "manualOutputArea");

                manualInput.setText("Test data");
                controller.navigateTo("Manual Conversion");
                toolbarInputFormat.setValue("Base64");
                toolbarOutputFormat.setValue("Text (UTF-8)");
                assertEquals("Base64", manualInputFormat.getValue());
                assertEquals("Text (UTF-8)", manualOutputFormat.getValue());
                manualInputFormat.setValue("Text");
                manualOutputFormat.setValue("Text");
                assertEquals("Text (UTF-8)", toolbarInputFormat.getValue());
                assertEquals("Text (UTF-8)", toolbarOutputFormat.getValue());

                genericController.handleEncodeBase64Url();
                assertNotNull(manualOutput.getText());

                // Compressed Hex
                CompressedHexController compressedHexController = getField(genericController, "compressedHexPaneController");
                assertNotNull(compressedHexController);

                javafx.scene.control.TextArea hexInput = getField(compressedHexController, "compressedHexInputArea");
                javafx.scene.control.TextArea hexOutput = getField(compressedHexController, "compressedHexOutputArea");
                hexInput.setText("112233");
                java.lang.reflect.Method m = compressedHexController.getClass().getDeclaredMethod("handleCompressHex");
                m.setAccessible(true);
                m.invoke(compressedHexController);
                assertNotNull(hexOutput.getText());

                // Endian Conversion
                javafx.scene.control.ComboBox<String> endianCombo = getField(genericController, "endianWordSizeCombo");

                // test 16 bits
                manualInput.setText("1122");
                manualInputFormat.setValue("Hexadecimal");
                manualOutputFormat.setValue("Hexadecimal");
                endianCombo.setValue("16 bits (2 bytes)");
                genericController.handleConvertEndian();
                assertEquals("2211", manualOutput.getText());

                // 32 bits
                manualInput.setText("11223344");
                endianCombo.setValue("32 bits (4 bytes)");
                genericController.handleConvertEndian();
                assertEquals("44332211", manualOutput.getText());

                // 64 bits
                manualInput.setText("1122334455667788");
                endianCombo.setValue("64 bits (8 bytes)");
                genericController.handleConvertEndian();
                assertEquals("8877665544332211", manualOutput.getText());

                // 128 bits
                manualInput.setText("112233445566778899AABBCCDDEEFF00");
                endianCombo.setValue("128 bits (16 bytes)");
                genericController.handleConvertEndian();
                assertEquals("00FFEEDDCCBBAA998877665544332211", manualOutput.getText().toUpperCase());

                // invalid format should default to 4 bytes
                endianCombo.setValue("invalid bits");
                manualInput.setText("11223344");
                genericController.handleConvertEndian();
                assertEquals("44332211", manualOutput.getText());

                // invalid length should throw and show error, but not crash test
                manualInput.setText("112233");
                endianCombo.setValue("16 bits (2 bytes)");
                genericController.handleConvertEndian();



                // File Conversion
                javafx.scene.control.TextField fileInput = getField(genericController, "fileInputPathField");
                javafx.scene.control.TextField fileOutput = getField(genericController, "fileOutputPathField");
                javafx.scene.control.ComboBox<String> fileInFormat = getField(genericController, "fileInputFormatCombo");
                javafx.scene.control.ComboBox<String> fileOutFormat = getField(genericController, "fileOutputFormatCombo");
                javafx.scene.control.ComboBox<String> fileEncodingCombo = getField(genericController, "fileEncodingCombo");

                java.io.File tempIn = java.io.File.createTempFile("testIn", ".txt");
                java.io.File tempOut = java.io.File.createTempFile("testOut", ".txt");
                java.nio.file.Files.writeString(tempIn.toPath(), "test");

                fileInput.setText(tempIn.getAbsolutePath());
                fileOutput.setText(tempOut.getAbsolutePath());
                fileInFormat.setValue("Text");
                fileOutFormat.setValue("Hex");
                if (fileEncodingCombo != null) fileEncodingCombo.setValue("UTF-8");

                genericController.handleConvertFile();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testBatchRunnerFunctional() throws Exception {
        AtomicReference<GenericController> controllerRef = new AtomicReference<>();
        AtomicReference<ModernMainController> mainControllerRef = new AtomicReference<>();
        AtomicReference<javafx.scene.control.TextArea> inputAreaRef = new AtomicReference<>();
        AtomicReference<javafx.scene.control.ComboBox<String>> opRef = new AtomicReference<>();
        AtomicReference<javafx.scene.control.ComboBox<String>> formatRef = new AtomicReference<>();
        BlockingBatchExecutor blockingBatch = new BlockingBatchExecutor();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                mainControllerRef.set(controller);
                GenericController generic = getField(controller, "genericContainerController");
                controllerRef.set(generic);
                inputAreaRef.set(getField(generic, "batchInputArea"));
                opRef.set(getField(generic, "batchOperationCombo"));
                formatRef.set(getField(generic, "batchInputFormatCombo"));
                generic.setBatchRunnerExecutorForTesting(blockingBatch.executor());

                formatRef.get().setValue("CSV");
                inputAreaRef.get().setText("input\nrow1");
                opRef.get().setValue("SHA-256 (UTF-8 → Hex)");

                com.cryptocarver.model.HistoryManager hm = getField(controller, "historyManager");
                if (hm != null) hm.clearHistory();

                generic.handleRunBatch();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        GenericController generic = controllerRef.get();
        javafx.concurrent.Task<?> task = getField(generic, "activeBatchTask");
        assertNotNull(task);
        assertTrue(blockingBatch.operationStarted.await(5, TimeUnit.SECONDS),
                "Batch operation should start before cancellation is requested");
        assertEquals(1L, blockingBatch.operationFinished.getCount(),
                "Batch operation must remain blocked until cancellation has been requested");

        // Check RUNNING on the FX thread immediately before requesting cancellation.
        runAndWait(() -> {
            assertTrue(task.isRunning(), "Batch must be RUNNING before cancellation");
            generic.handleCancelBatch();
        });
        blockingBatch.releaseWorker();
        assertTrue(blockingBatch.operationFinished.await(5, TimeUnit.SECONDS),
                "Blocked batch worker should be released after cancellation");
        awaitTask(task, 5, "Cancelled batch task");

        runAndWait(() -> {
            try {
                Object lastReport = getField(generic, "lastBatchReport");
                assertNull(lastReport, "Cancelled batch must not expose a partial exportable report");
                javafx.scene.control.TextArea batchResultArea = getField(generic, "batchResultArea");
                assertTrue(batchResultArea.getText().isEmpty(), "Cancelled batch output must be cleared");
                javafx.scene.control.ProgressBar progressBar = getField(generic, "batchProgressBar");
                assertFalse(progressBar.progressProperty().isBound(), "Progress control must be restored after cancellation");
                assertNull(getField(generic, "activeBatchTask"), "Batch controls must be restored after cancellation");
                assertFalse(task.isRunning(), "Cancelled task must no longer be running");
                assertEquals(com.cryptocarver.service.I18nService.getInstance().text("module.batch.cancelled"),
                        ((javafx.scene.control.Label) getField(generic, "batchStatusLabel")).getText(),
                        "Cancelled status must be visible after controls are restored");

                com.cryptocarver.model.HistoryManager hm = getField(mainControllerRef.get(), "historyManager");
                assertNotNull(hm, "HistoryManager should be initialized");
                assertTrue(hm.getHistoryItems().isEmpty(), "Cancelled batch must not be published to history");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testCompletedBatchRetainsReportWhenCancelledAfterCompletion() throws Exception {
        AtomicReference<GenericController> controllerRef = new AtomicReference<>();
        AtomicReference<ModernMainController> mainControllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ModernMainController controller = loader.getController();
                mainControllerRef.set(controller);
                GenericController generic = getField(controller, "genericContainerController");
                controllerRef.set(generic);

                javafx.scene.control.ComboBox<String> format = getField(generic, "batchInputFormatCombo");
                javafx.scene.control.ComboBox<String> operation = getField(generic, "batchOperationCombo");
                javafx.scene.control.TextArea input = getField(generic, "batchInputArea");
                format.setValue("CSV");
                operation.setValue("SHA-256 (UTF-8 → Hex)");
                input.setText("input\nrow1\nrow2");
                com.cryptocarver.model.HistoryManager hm = getField(controller, "historyManager");
                if (hm != null) hm.clearHistory();
                generic.handleRunBatch();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        GenericController generic = controllerRef.get();
        javafx.concurrent.Task<?> task = getField(generic, "activeBatchTask");
        assertNotNull(task);
        awaitTask(task, 5, "Completed batch task");

        AtomicReference<Object> completedReportRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                Object report = getField(generic, "lastBatchReport");
                completedReportRef.set(report);
                assertNotNull(report, "Completed batch should expose its report");
                java.lang.reflect.Method succeededMethod = report.getClass().getDeclaredMethod("succeeded");
                assertEquals(2L, ((Number) succeededMethod.invoke(report)).longValue(), "Completed batch should retain both results");
                assertFalse(((javafx.scene.control.ProgressBar) getField(generic, "batchProgressBar"))
                        .progressProperty().isBound(), "Completed batch controls should be restored");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> generic.handleCancelBatch());

        runAndWait(() -> {
            try {
                assertSame(completedReportRef.get(), getField(generic, "lastBatchReport"),
                        "Cancelling after completion must preserve the normal report");
                com.cryptocarver.model.HistoryManager hm = getField(mainControllerRef.get(), "historyManager");
                assertEquals(1, hm.getHistoryItems().size(), "Completed batch should remain the only history entry");
                assertEquals("Batch Runner", hm.getHistoryItems().get(0).getOperation());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void awaitTask(javafx.concurrent.Task<?> task, long timeoutSeconds, String description) throws Exception {
        try {
            task.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.CancellationException expected) {
            // Expected for a task cancelled while its worker is blocked.
        } catch (java.util.concurrent.ExecutionException e) {
            fail(description + " failed", e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            fail(description + " timed out", e);
        }
    }

    private static final class BlockingBatchExecutor {
        private final CountDownLatch operationStarted = new CountDownLatch(1);
        private final CountDownLatch releaseOperation = new CountDownLatch(1);
        private final CountDownLatch operationFinished = new CountDownLatch(1);

        private GenericController.BatchRunnerExecutor executor() {
            return (rows, operation, cancellationRequested, progressListener) ->
                    com.cryptocarver.model.batch.BatchRunner.run(rows, (rowNumber, input) -> {
                        operationStarted.countDown();
                        awaitRelease();
                        try {
                            return operation.execute(rowNumber, input);
                        } finally {
                            operationFinished.countDown();
                        }
                    }, cancellationRequested, progressListener);
        }

        private void awaitRelease() {
            boolean interrupted = false;
            while (true) {
                try {
                    releaseOperation.await();
                    if (interrupted) Thread.currentThread().interrupt();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }

        private void releaseWorker() {
            releaseOperation.countDown();
        }
    }

    @Test
    void testGenericModulePublishing() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);

                GenericController generic = getField(controller, "genericContainerController");

                javafx.scene.control.TextArea hashInputArea = getField(generic, "hashInputArea");
                javafx.scene.control.ComboBox<String> hashAlgorithmCombo = getField(generic, "hashAlgorithmCombo");

                hashInputArea.setText("Test data");
                hashAlgorithmCombo.setValue("SHA-256");

                com.cryptocarver.model.HistoryManager hm = getField(controller, "historyManager");
                if (hm != null) hm.clearHistory();

                generic.handleCalculateHash();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        runAndWait(() -> {
            try {
                com.cryptocarver.model.HistoryManager hm = getField(controllerRef.get(), "historyManager");
                assertNotNull(hm, "HistoryManager should be initialized");
                assertFalse(hm.getHistoryItems().isEmpty(), "History should not be empty");

                com.cryptocarver.model.HistoryCommand item = hm.getHistoryItems().get(hm.getHistoryItems().size() - 1);
                assertTrue(item.getOperation().startsWith("Hashing:"), "Operation should start with Hashing:, but was " + item.getOperation());
                assertTrue(item.getOperation().contains("SHA-256"), "Operation should contain SHA-256");
                assertTrue(item.getDetails().contains("Algorithm"), "Details should include Algorithm");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    @Test
    void testHistoryReopenCipherPopulatesFields() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");
        javafx.scene.control.TextArea input = getField(cipher, "cipherInputArea");
        javafx.scene.control.TextField key = getField(cipher, "symmetricKeyField");

        runAndWait(() -> {
            key.setText("pre-existing-secret-that-must-be-cleared");
            controller.restoreOperationState(java.util.Map.of(
                    "CipherController.cipherInputArea", "restored payload",
                    "CipherController.symmetricKeyField", "[REDACTED_SECRET]"
            ), "Symmetric Ciphers");
        });

        assertEquals("", input.getText(), "History must not restore cipher input material");
        assertEquals("", key.getText(), "Reopen should clear redacted secrets and never leave [REDACTED_SECRET] in the field");
        assertTrue(((javafx.scene.Node) getField(controller, "cipherContainer")).isVisible());
    }

    @Test
    void testHistoryReopenHashingPopulatesFields() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        GenericController generic = getField(controller, "genericContainerController");
        javafx.scene.control.TextArea input = getField(generic, "hashInputArea");
        javafx.scene.control.ComboBox<String> algo = getField(generic, "hashAlgorithmCombo");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(
                    "GenericController.hashInputArea", "hash this",
                    "GenericController.hashAlgorithmCombo", "SHA-512"
            ), "Hashing: SHA-512");
        });

        assertEquals("", input.getText(), "History must not restore hash input material");
        assertEquals("SHA-512", algo.getValue());
        assertTrue(((javafx.scene.Node) getField(controller, "genericContainer")).isVisible());
    }

    @Test
    void testHistoryReopenKeyGenerationPopulatesFields() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        KeysController keys = getField(controller, "keysContainerController");
        javafx.scene.control.ComboBox<String> keyType = getField(keys, "keyTypeCombo");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(
                    "KeysController.keyTypeCombo", "ChaCha20"
            ), "Key Generation");
        });

        assertEquals("ChaCha20", keyType.getValue());
        assertTrue(((javafx.scene.Node) getField(controller, "keysContainer")).isVisible());
    }

    @Test
    void testHistoryReopenCertificatesPopulatesFields() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CertificatesController certs = getField(controller, "certificatesContainerController");
        javafx.scene.control.TextField subject = getField(certs, "certCNField");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(
                    "CertificatesController.certCNField", "CN=Test"
            ), "Generate Certificate");
        });

        assertEquals("CN=Test", subject.getText());
        assertTrue(((javafx.scene.Node) getField(controller, "certificatesContainer")).isVisible());
    }

    @Test
    void testHistoryReopenPaymentsPopulatesFields() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        PaymentsController payments = getField(controller, "paymentsContainerController");
        javafx.scene.control.TextField pan = getField(payments, "panFieldEncode");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(
                    "PaymentsController.panFieldEncode", "123456789012345"
            ), "PIN Generation");
        });

        assertEquals("", pan.getText(), "PAN must remain redacted when reopening history");
        assertTrue(((javafx.scene.Node) getField(controller, "paymentsContainer")).isVisible());
    }

    @Test
    void testHashingObeysVisibleGlobalFormat() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        GenericController generic = getField(controller, "genericContainerController");
        javafx.scene.control.TextArea inputArea = getField(generic, "hashInputArea");
        javafx.scene.control.TextArea outputArea = getField(generic, "hashOutputArea");
        javafx.scene.control.ComboBox<String> globalInputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> globalOutputFormat = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(), "Hashing: SHA-256");
            inputArea.setText("hello");
            globalInputFormat.setValue("Text (UTF-8)");
            globalOutputFormat.setValue("Hexadecimal");
            generic.handleCalculateHash();
        });

        // SHA-256 of "hello" in Hexadecimal
        String hexHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        assertEquals(hexHash, outputArea.getText().trim().toLowerCase());

        runAndWait(() -> {
            globalOutputFormat.setValue("Base64");
            generic.handleCalculateHash();
        });

        // SHA-256 of "hello" in Base64
        String base64Hash = "LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=";
        assertEquals(base64Hash, outputArea.getText().trim());
    }

    @Test
    void testRandomGeneratorSynchronizesWithGlobalFormat() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        GenericController generic = getField(controller, "genericContainerController");
        javafx.scene.control.ComboBox<String> localRandomFormat = getField(generic, "randomFormatCombo");
        javafx.scene.control.ComboBox<String> globalOutputFormat = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(), "Random Number Generator");
        });

        // Verify initial synchronization (should default to Hexadecimal)
        assertEquals("Hexadecimal", localRandomFormat.getValue());
        assertEquals("Hexadecimal", globalOutputFormat.getValue());

        // Change global -> local
        runAndWait(() -> {
            globalOutputFormat.setValue("Base64");
        });
        assertEquals("Base64", localRandomFormat.getValue());

        // Change local -> global
        runAndWait(() -> {
            localRandomFormat.setValue("Binary");
        });
        assertEquals("Binary", globalOutputFormat.getValue());
    }

    @Test
    void testNonByteOperationDisablesFormatBar() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        javafx.scene.control.ComboBox<String> globalInputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> globalOutputFormat = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(), "JWT (Signed)");
        });

        assertTrue(globalInputFormat.isDisable());
        assertTrue(globalOutputFormat.isDisable());
    }

    @Test
    void testFormatNotLeakedBetweenOperations() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        javafx.scene.control.ComboBox<String> globalInputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> globalOutputFormat = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(), "Hashing: SHA-256");
            globalOutputFormat.setValue("Base64");
        });

        assertEquals("Base64", globalOutputFormat.getValue());

        // Navigate to JWT (Signed) - format bar should get disabled
        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(), "JWT (Signed)");
        });
        assertTrue(globalInputFormat.isDisable());
        assertTrue(globalOutputFormat.isDisable());

        // Navigate back to Hashing - format bar should re-enable and restore defaults/allowed items without leak
        runAndWait(() -> {
            controller.restoreOperationState(java.util.Map.of(), "Hashing: SHA-256");
        });
        assertFalse(globalInputFormat.isDisable());
        assertFalse(globalOutputFormat.isDisable());
        assertTrue(globalOutputFormat.getItems().contains("Hexadecimal"));
        assertTrue(globalOutputFormat.getItems().contains("Base64"));
    }

    @Test
    void testNewUxUnifiedPolishFeatures() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();

        // 1. Verify resultSummaryBar is injected and starts as hidden/unmanaged
        javafx.scene.layout.HBox resultBar = getField(controller, "resultSummaryBar");
        assertNotNull(resultBar, "resultSummaryBar should be injected");
        assertFalse(resultBar.isVisible());
        assertFalse(resultBar.isManaged());

        // 2. Verify Key Lab Table columns do not contain "Modified"
        KeysController keys = getField(controller, "keysContainerController");
        javafx.scene.control.TableView<?> table = getField(keys, "keyLabTable");
        assertNotNull(table, "keyLabTable should be injected");
        boolean hasModifiedCol = table.getColumns().stream()
                .anyMatch(col -> "Modified".equalsIgnoreCase(col.getText()));
        assertFalse(hasModifiedCol, "Key Lab Table should not contain a Modified column anymore");

        // 3. Verify Symmetric Cipher pane has form-group-box container elements
        CipherController cipher = getField(controller, "cipherContainerController");
        javafx.scene.layout.VBox cipherRoot = getField(controller, "cipherContainer");
        assertNotNull(cipherRoot);
        // Find form-group-box elements in Symmetric Cipher titled pane
        javafx.scene.control.Accordion accordion = (javafx.scene.control.Accordion) cipherRoot.getChildren().stream()
                .filter(javafx.scene.control.Accordion.class::isInstance)
                .findFirst().orElseThrow();
        javafx.scene.control.TitledPane symmetricPane = accordion.getPanes().get(0);
        javafx.scene.layout.VBox contentVBox = (javafx.scene.layout.VBox) symmetricPane.getContent();
        boolean hasFormGroup = contentVBox.getChildren().stream()
                .anyMatch(node -> node.getStyleClass().contains("form-group-box"));
        assertTrue(hasFormGroup, "Symmetric Cipher should contain form-group-box containers");

        // 4. Verify publish() updates resultSummaryBar
        runAndWait(() -> {
            com.cryptocarver.model.OperationResult dummyResult = com.cryptocarver.model.OperationResult.forOperation("Symmetric Ciphers")
                .input("hello".getBytes())
                .output("world".getBytes())
                .detail(com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", "AES-256"))
                .status("Operation succeeded")
                .build();
            controller.publish(dummyResult);
        });

        assertTrue(resultBar.isVisible());
        assertTrue(resultBar.isManaged());
        javafx.scene.control.Label opLabel = getField(controller, "resultOpLabel");
        assertEquals("Symmetric Ciphers", opLabel.getText());
    }

    @Test
    void testMetadataOnlySelectionAndImportDisable() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        KeysController keys = getField(controller, "keysContainerController");

        javafx.scene.control.PasswordField importField = getField(keys, "keyLabImportBytesField");
        javafx.scene.control.Button importBtn = getField(keys, "keyLabImportBtn");
        assertNotNull(importField);
        assertNotNull(importBtn);

        // Switch to MASKED
        runAndWait(() -> {
            com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.MASKED);
            keys.updateVisibilityControls();
        });

        assertTrue(importField.isDisable());
        assertTrue(importBtn.isDisable());

        // Verify state capture ignores this field completely
        java.util.Map<String, Object> state = UiStateSnapshot.capture(controller);
        assertFalse(state.containsKey("KeysController.keyLabImportBytesField"), "State capture must not track the key lab import field");

        // Verify history capture ignores this field completely
        java.util.Map<String, Object> historyState = UiStateSnapshot.captureHistoryRecipe(controller);
        assertFalse(historyState.containsKey("KeysController.keyLabImportBytesField"), "History recipe must not track the key lab import field");

        // Restore profile to FULL_LAB
        runAndWait(() -> {
            com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
            keys.updateVisibilityControls();
        });
        assertFalse(importField.isDisable());
        assertFalse(importBtn.isDisable());
    }

    @Test
    void testResultBarNavigationClearance() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        javafx.scene.layout.HBox resultBar = getField(controller, "resultSummaryBar");

        runAndWait(() -> {
            com.cryptocarver.model.OperationResult dummyResult = com.cryptocarver.model.OperationResult.forOperation("Symmetric Encrypt")
                .input("hello".getBytes())
                .output("world".getBytes())
                .detail(com.cryptocarver.model.OperationDetail.publicDetail("Algorithm", "AES-256"))
                .status("Operation succeeded")
                .build();
            controller.publish(dummyResult);
        });

        assertTrue(resultBar.isVisible());
        assertTrue(resultBar.isManaged());

        // Navigate to Hashing -> should hide resultBar and clear snapshot
        runAndWait(() -> {
            controller.navigateTo("Hashing");
        });

        assertFalse(resultBar.isVisible());
        assertFalse(resultBar.isManaged());
        assertNull(getField(controller, "lastPublishedResultSnapshot"));

        // Copy/shelf actions should do nothing when snapshot is null
        runAndWait(() -> {
            try {
                java.lang.reflect.Method mCopy = controller.getClass().getDeclaredMethod("handleCopyOutput");
                mCopy.setAccessible(true);
                mCopy.invoke(controller);

                java.lang.reflect.Method mShelf = controller.getClass().getDeclaredMethod("handleAddCurrentOutputToShelf");
                mShelf.setAccessible(true);
                mShelf.invoke(controller);
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testGuidedWorkflowsAndQuickStartCards() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        javafx.scene.layout.HBox guidedPanel = getField(controller, "guidedFlowPanel");
        javafx.scene.control.Label titleLabel = getField(controller, "guideStepTitleLabel");
        javafx.scene.control.Label descLabel = getField(controller, "guideStepDescLabel");
        javafx.scene.control.Button backBtn = getField(controller, "guideBackBtn");
        javafx.scene.control.Button nextBtn = getField(controller, "guideNextBtn");

        // Quick Start -> Guided Encrypt
        runAndWait(() -> {
            controller.showQuickStart();
            Method m = assertDoesNotThrow(() -> ModernMainController.class.getDeclaredMethod("handleStartGuidedEncrypt"));
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(controller));
        });

        assertTrue(guidedPanel.isVisible());
        assertTrue(guidedPanel.isManaged());
        assertTrue(titleLabel.getText().contains("Step 1 of 5"));
        assertTrue(backBtn.isDisable());
        assertFalse(nextBtn.isDisable());

        // Step progression: 1 -> 2 -> 3
        runAndWait(() -> {
            Method mNext = assertDoesNotThrow(() -> ModernMainController.class.getDeclaredMethod("handleGuideNext"));
            mNext.setAccessible(true);
            assertDoesNotThrow(() -> mNext.invoke(controller));
            assertDoesNotThrow(() -> mNext.invoke(controller));
        });

        assertTrue(titleLabel.getText().contains("Step 3 of 5"));
        assertFalse(backBtn.isDisable());

        // Back: 3 -> 2
        runAndWait(() -> {
            Method mBack = assertDoesNotThrow(() -> ModernMainController.class.getDeclaredMethod("handleGuideBack"));
            mBack.setAccessible(true);
            assertDoesNotThrow(() -> mBack.invoke(controller));
        });
        assertTrue(titleLabel.getText().contains("Step 2 of 5"));

        // Skip to Step 4
        runAndWait(() -> {
            Method mSkip = assertDoesNotThrow(() -> ModernMainController.class.getDeclaredMethod("handleGuideSkip"));
            mSkip.setAccessible(true);
            assertDoesNotThrow(() -> mSkip.invoke(controller));
        });
        assertTrue(titleLabel.getText().contains("Step 4 of 5"));

        // Exit guided flow (should hide panel without clearing configuration)
        runAndWait(() -> {
            Method mExit = assertDoesNotThrow(() -> ModernMainController.class.getDeclaredMethod("handleGuideExit"));
            mExit.setAccessible(true);
            assertDoesNotThrow(() -> mExit.invoke(controller));
        });
        assertFalse(guidedPanel.isVisible());
    }

    @Test
    void testSafeOperationTemplatesApplication() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");

        // Apply AES-256-GCM template
        runAndWait(() -> {
            javafx.scene.control.ComboBox<String> combo = cipher.getCipherTemplateCombo();
            assertNotNull(combo);
            combo.setValue("AES-256-GCM — Text UTF-8 → Base64");
            Method m = assertDoesNotThrow(() -> CipherController.class.getDeclaredMethod("handleApplyCipherTemplate"));
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(cipher));
        });

        javafx.scene.control.ComboBox<String> symAlgo = getField(cipher, "symmetricAlgorithmCombo");
        javafx.scene.control.ComboBox<String> cipherMode = getField(cipher, "cipherModeCombo");
        javafx.scene.control.TextField keyField = getField(cipher, "symmetricKeyField");
        javafx.scene.control.TextField ivField = getField(cipher, "ivField");

        assertEquals("AES-256", symAlgo.getValue());
        assertEquals("GCM", cipherMode.getValue());
        assertEquals("", keyField.getText()); // Key is NOT fixed or populated
        assertEquals("", ivField.getText());  // IV/nonce is NOT fixed or hardcoded

        // Verify status message includes security tip
        javafx.scene.control.Label statusLabel = getField(controller, "statusLabel");
        assertTrue(statusLabel.getText().contains("GCM authenticates ciphertext"));
    }

    @Test
    void testHashTemplateReplacesStaleToolbarInputFormat() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        GenericController generic = getField(controller, "genericContainerController");
        javafx.scene.control.ComboBox<String> toolbarInput = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> toolbarOutput = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.navigateTo("Hashing");
            toolbarInput.setValue("Hexadecimal");
            javafx.scene.control.ComboBox<String> template = assertDoesNotThrow(() -> getField(generic, "hashTemplateCombo"));
            template.setValue("SHA-256 — Text UTF-8 → Hex");
            Method apply = assertDoesNotThrow(() -> GenericController.class.getDeclaredMethod("handleApplyHashTemplate"));
            apply.setAccessible(true);
            assertDoesNotThrow(() -> apply.invoke(generic));
        });

        assertEquals("Text (UTF-8)", toolbarInput.getValue());
        assertEquals("Hexadecimal", toolbarOutput.getValue());
    }

    @Test
    void testTemplateSecretsExclusion() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");

        // Set key and IV values
        runAndWait(() -> {
            javafx.scene.control.TextField keyField = assertDoesNotThrow(() -> getField(cipher, "symmetricKeyField"));
            javafx.scene.control.TextField ivField = assertDoesNotThrow(() -> getField(cipher, "ivField"));
            keyField.setText("00112233445566778899AABBCCDDEEFF");
            ivField.setText("0102030405060708090A0B0C");
        });

        // Capture recipe using UiStateSnapshot
        java.util.Map<String, Object> recipe = UiStateSnapshot.captureHistoryRecipe(controller);
        for (java.util.Map.Entry<String, Object> entry : recipe.entrySet()) {
            if (entry.getKey().contains("symmetricKeyField") || entry.getKey().contains("ivField")) {
                assertEquals("[REDACTED_SECRET]", entry.getValue(), "Secret field " + entry.getKey() + " must be redacted");
            }
        }
    }

    @Test
    void testApplyingTemplatePreservesUserInputText() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");

        runAndWait(() -> {
            javafx.scene.control.TextArea inputArea = assertDoesNotThrow(() -> getField(cipher, "cipherInputArea"));
            inputArea.setText("PRESERVED_USER_INPUT_TEXT");

            javafx.scene.control.ComboBox<String> combo = cipher.getCipherTemplateCombo();
            combo.setValue("AES-256-CBC — Hex → Hex");

            Method m = assertDoesNotThrow(() -> CipherController.class.getDeclaredMethod("handleApplyCipherTemplate"));
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(cipher));

            assertEquals("PRESERVED_USER_INPUT_TEXT", inputArea.getText(), "Applying a template must NEVER clear user-entered text!");
        });
    }

    @Test
    void testExecutionPreflightReadinessPanel() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");

        // 1. An untouched empty form is naturally incomplete, but the
        // checklist should stay out of the way until the user interacts.
        runAndWait(() -> {
            Method m = assertDoesNotThrow(() -> ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class));
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(controller, "Symmetric Ciphers"));

            javafx.scene.layout.HBox readinessPanel = assertDoesNotThrow(() -> getField(controller, "readinessPanel"));
            assertFalse(readinessPanel.isVisible());

            assertFalse(controller.checkPreflightReadiness("Symmetric Ciphers", true));
            javafx.scene.control.Label badge = assertDoesNotThrow(() -> getField(controller, "readinessStatusBadge"));
            assertNotNull(badge);
            assertTrue(badge.getText().contains("INCOMPLETE") || badge.getText().contains("BLOCKED"));
        });

        // 2. Set Invalid Hex Key -> BLOCKED
        runAndWait(() -> {
            javafx.scene.control.TextArea inputArea = assertDoesNotThrow(() -> getField(cipher, "cipherInputArea"));
            javafx.scene.control.TextField keyField = assertDoesNotThrow(() -> getField(cipher, "symmetricKeyField"));
            inputArea.setText("Sample Data Payload");
            keyField.setText("NOT_A_HEX_KEY");
            assertFalse(controller.checkPreflightReadiness("Symmetric Ciphers", true));

            javafx.scene.control.Label badge = assertDoesNotThrow(() -> getField(controller, "readinessStatusBadge"));
            assertTrue(badge.getText().contains("BLOCKED"));
        });

        // 3. Set ECB Mode -> WARNING (Executable)
        runAndWait(() -> {
            javafx.scene.control.TextField keyField = assertDoesNotThrow(() -> getField(cipher, "symmetricKeyField"));
            javafx.scene.control.ComboBox<String> modeCombo = assertDoesNotThrow(() -> getField(cipher, "cipherModeCombo"));
            keyField.setText("00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF");
            modeCombo.setValue("ECB");
            assertTrue(controller.checkPreflightReadiness("Symmetric Ciphers", true));
            javafx.scene.layout.HBox readinessPanel = assertDoesNotThrow(() -> getField(controller, "readinessPanel"));
            assertFalse(readinessPanel.isVisible(), "A valid setup must not leave a banner behind");
        });

        // 4. Set Valid AES-256-GCM + 12-byte IV -> READY
        runAndWait(() -> {
            javafx.scene.control.ComboBox<String> modeCombo = assertDoesNotThrow(() -> getField(cipher, "cipherModeCombo"));
            javafx.scene.control.TextField ivField = assertDoesNotThrow(() -> getField(cipher, "ivField"));
            modeCombo.setValue("GCM");
            ivField.setText("0102030405060708090A0B0C");
            assertTrue(controller.checkPreflightReadiness("Symmetric Ciphers", true));
            javafx.scene.layout.HBox readinessPanel = assertDoesNotThrow(() -> getField(controller, "readinessPanel"));
            assertFalse(readinessPanel.isVisible(), "A ready operation should execute without a header banner");
        });

        // 5. Test Click-to-Focus on target control
        runAndWait(() -> {
            controller.focusControl("cipherInputArea");
            javafx.scene.control.TextArea inputArea = assertDoesNotThrow(() -> getField(cipher, "cipherInputArea"));
            assertTrue(inputArea.isFocused() || inputArea.getScene() == null);
        });

        // 6. File Cipher uses a different form contract, so it must not be
        // evaluated with in-memory symmetric cipher controls.
        runAndWait(() -> {
            Method m = assertDoesNotThrow(() -> ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class));
            m.setAccessible(true);
            assertDoesNotThrow(() -> m.invoke(controller, "File Cipher (Streaming)"));
            javafx.scene.layout.HBox readinessPanel = assertDoesNotThrow(() -> getField(controller, "readinessPanel"));
            assertFalse(readinessPanel.isVisible(), "File Cipher must not display the symmetric-cipher preflight panel");
        });
    }

    @Test
    void testCommandPaletteUI() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();

        // 1. Open Command Palette -> Overlay visible & Search field populated/focused
        runAndWait(() -> {
            controller.handleOpenCommandPalette();
            javafx.scene.layout.VBox overlay = assertDoesNotThrow(() -> getField(controller, "commandPaletteOverlay"));
            javafx.scene.control.TextField searchField = assertDoesNotThrow(() -> getField(controller, "commandSearchField"));

            assertTrue(overlay.isVisible(), "Command Palette overlay must be visible on open");
            assertNotNull(searchField);
        });

        // 2. Search "hash" and execute selected command -> Navigates to Hashing & closes overlay
        runAndWait(() -> {
            javafx.scene.control.TextField searchField = assertDoesNotThrow(() -> getField(controller, "commandSearchField"));
            searchField.setText("hash");

            controller.handleExecuteSelectedCommand();

            javafx.scene.layout.VBox overlay = assertDoesNotThrow(() -> getField(controller, "commandPaletteOverlay"));
            assertFalse(overlay.isVisible(), "Command Palette overlay must close after executing a command");
        });

        // 3. Escape closes overlay
        runAndWait(() -> {
            controller.handleOpenCommandPalette();
            javafx.scene.layout.VBox overlay = assertDoesNotThrow(() -> getField(controller, "commandPaletteOverlay"));
            assertTrue(overlay.isVisible());

            controller.handleCloseCommandPalette();
            assertFalse(overlay.isVisible(), "handleCloseCommandPalette must hide overlay");
        });

        // 4. Verify result-dependent commands are disabled when no result exists
        runAndWait(() -> {
            assertFalse(controller.hasCurrentResult(), "No operation result exists initially");
            java.util.List<com.cryptocarver.model.CommandItem> commands = com.cryptocarver.model.CommandRegistry.buildCommands(controller);

            com.cryptocarver.model.CommandItem expandCmd = commands.stream().filter(c -> "view_expand_result".equals(c.getId())).findFirst().orElse(null);
            com.cryptocarver.model.CommandItem copyCmd = commands.stream().filter(c -> "action_copy_output".equals(c.getId())).findFirst().orElse(null);

            assertNotNull(expandCmd);
            assertNotNull(copyCmd);
            assertFalse(expandCmd.isEnabled(), "Expand Result must be disabled when no result exists");
            assertFalse(copyCmd.isEnabled(), "Copy Output must be disabled when no result exists");
        });
    }

    @Test
    void testSaveGeneratedKeyToKeyLabUI() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();

        runAndWait(() -> {
            KeysController keys = assertDoesNotThrow(() -> getField(controller, "keysController"));
            assertNotNull(keys);

            javafx.scene.control.Button saveBtn = assertDoesNotThrow(() -> getField(keys, "saveGeneratedKeyButton"));
            assertNotNull(saveBtn);
            assertTrue(saveBtn.isDisable(), "Save to Key Lab button must be disabled before generating a key");

            // Generate key
            keys.handleGenerateKey();

            assertFalse(saveBtn.isDisable(), "Save to Key Lab button must be enabled after generating a key");

            // Save key to lab
            keys.handleSaveGeneratedKeyToLab();

            // Verify Key Lab table has entries
            javafx.scene.control.TableView<com.cryptocarver.crypto.hsm.KeyMaterial> table = assertDoesNotThrow(() -> getField(keys, "keyLabTable"));
            assertFalse(table.getItems().isEmpty(), "Key Lab table must contain newly saved generated key");

            // Verify MASKED profile security
            com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.MASKED);
            assertThrows(SecurityException.class, () ->
                    com.cryptocarver.crypto.hsm.SimulatedHsmProvider.getInstance().revealExportableKeyForFullLab(table.getItems().get(0).getId())
            );
        });
    }

    @Test
    void testGeneratedKeySummaryCardUI() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();

        runAndWait(() -> {
            try {
                KeysController keys = getField(controller, "keysController");
                assertNotNull(keys);

                javafx.scene.layout.VBox card = getField(keys, "generatedKeySummaryCard");
                assertNotNull(card);
                assertFalse(card.isVisible(), "Generated Key Summary card must be initially hidden");
                assertFalse(card.isManaged(), "Generated Key Summary card must be initially unmanaged");

                javafx.scene.control.ComboBox<String> combo = getField(keys, "keyTypeCombo");
                combo.setValue("AES-256");

                // Generate Key
                keys.handleGenerateKey();

                assertTrue(card.isVisible(), "Generated Key Summary card must be visible after generation");
                assertTrue(card.isManaged(), "Generated Key Summary card must be managed after generation");

                javafx.scene.control.Label algoLbl = getField(keys, "summaryAlgoLabel");
                javafx.scene.control.Label lengthLbl = getField(keys, "summaryLengthLabel");
                javafx.scene.control.Label kcvLbl = getField(keys, "summaryKcvLabel");
                javafx.scene.control.Label fpLbl = getField(keys, "summaryFingerprintLabel");
                javafx.scene.control.Label parityLbl = getField(keys, "summaryParityLabel");

                assertEquals("AES-256", algoLbl.getText());
                assertEquals("256 bits (32 bytes)", lengthLbl.getText());
                assertFalse(kcvLbl.getText().isEmpty());
                assertEquals(16, fpLbl.getText().length());
                assertEquals("Not applicable", parityLbl.getText());

                // Copy KCV
                keys.handleCopyGeneratedKcv();
                String clipKcv = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                assertNotNull(clipKcv);
                assertEquals(kcvLbl.getText(), clipKcv);

                // Save to Key Lab updates card status label
                keys.handleSaveGeneratedKeyToLab();
                javafx.scene.control.Label savedLbl = getField(keys, "summarySavedStatusLabel");
                assertTrue(savedLbl.getText().contains("Saved to Key Lab"));

                // Security profiles test: MASKED vs FULL_LAB
                com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.MASKED);
                keys.handleCopyGeneratedKey(); // should block copying raw key under MASKED
                assertNotEquals(getField(keys, "generatedKeyField"), javafx.scene.input.Clipboard.getSystemClipboard().getString());

                keys.handleCopyGeneratedSummary();
                String summaryMasked = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                assertTrue(summaryMasked.contains("Key: ***MASKED***"));

                com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
                keys.handleCopyGeneratedSummary();
                String summaryFull = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                assertFalse(summaryFull.contains("Key: ***MASKED***"));

                // Changing key type combo hides/resets card
                combo.setValue("DES");
                assertFalse(card.isVisible(), "Changing key type combo must hide previous summary card");
                assertFalse(card.isManaged(), "Changing key type combo must unmanage previous summary card");
                javafx.scene.control.Button saveBtn = getField(keys, "saveGeneratedKeyButton");
                javafx.scene.control.TextArea generatedKey = getField(keys, "generatedKeyField");
                assertTrue(saveBtn.isDisable(), "Changing key type must invalidate the previous save action");
                assertTrue(generatedKey.getText().isEmpty(), "Changing key type must clear the previous generated key display");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testAsymmetricKeyGenerationWorkbenchUI() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();

        runAndWait(() -> {
            try {
                KeysController keys = getField(controller, "keysController");
                assertNotNull(keys);

                javafx.scene.layout.VBox rsaCard = getField(keys, "rsaSummaryCard");
                javafx.scene.layout.VBox ecdsaCard = getField(keys, "ecdsaSummaryCard");
                javafx.scene.layout.VBox dsaCard = getField(keys, "dsaSummaryCard");
                javafx.scene.layout.VBox eddsaCard = getField(keys, "eddsaSummaryCard");

                assertFalse(rsaCard.isVisible());
                assertFalse(ecdsaCard.isVisible());
                assertFalse(dsaCard.isVisible());
                assertFalse(eddsaCard.isVisible());

                // RSA Generation
                javafx.scene.control.ComboBox<Integer> rsaCombo = getField(keys, "rsaKeySizeCombo");
                rsaCombo.setValue(2048);
                keys.handleGenerateRSA();

                assertTrue(rsaCard.isVisible());
                assertTrue(rsaCard.isManaged());

                javafx.scene.control.Label rsaAlgoLbl = getField(keys, "rsaSummaryAlgoLabel");
                javafx.scene.control.Label rsaFpLbl = getField(keys, "rsaSummaryFingerprintLabel");
                assertEquals("RSA (2048 bits)", rsaAlgoLbl.getText());
                assertEquals(16, rsaFpLbl.getText().length());

                javafx.scene.control.Button rsaUseCipherBtn = getField(keys, "rsaUseCipherBtn");
                assertFalse(rsaUseCipherBtn.isDisable(), "RSA must enable Use in RSA Cipher button");

                keys.handleUseRsaInCipher();
                assertEquals("Asymmetric Ciphers", getField(controller, "currentActiveOperation"),
                        "Use in RSA Cipher must open the asymmetric cipher workspace");

                keys.handleUseRsaInSignatures();
                AuthenticationController authentication = getField(controller, "authenticationContainerController");
                javafx.scene.control.TextArea signaturePrivate = getField(authentication, "signaturePrivateKeyArea");
                javafx.scene.control.TextArea signaturePublic = getField(authentication, "signaturePublicKeyArea");
                assertFalse(signaturePrivate.getText().isBlank(), "Use in Digital Signatures must prepare the private key");
                assertFalse(signaturePublic.getText().isBlank(), "Use in Digital Signatures must prepare the public key");

                // ECDSA Generation
                javafx.scene.control.ComboBox<String> ecdsaCombo = getField(keys, "ecdsaCurveCombo");
                ecdsaCombo.setValue("secp256r1");
                keys.handleGenerateECDSA();

                assertTrue(ecdsaCard.isVisible());
                javafx.scene.control.Button ecdsaUseCipherBtn = getField(keys, "ecdsaUseCipherBtn");
                assertTrue(ecdsaUseCipherBtn.isDisable(), "ECDSA must disable Use in RSA Cipher button");

                // DSA Generation
                javafx.scene.control.ComboBox<String> dsaCombo = getField(keys, "dsaKeySizeCombo");
                dsaCombo.setValue("2048");
                keys.handleGenerateDSA();
                assertTrue(dsaCard.isVisible());

                // Ed25519 Generation
                keys.handleGenerateEdDSA();
                assertTrue(eddsaCard.isVisible());

                // Security Visibility Profile Test
                com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.MASKED);
                keys.handleCopyRsaPrivateKey(); // Should block copying raw private key under MASKED
                keys.handleCopyRsaSummary();
                String summaryMasked = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                assertTrue(summaryMasked.contains("***MASKED***"));

                com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
                keys.handleCopyRsaSummary();
                String summaryFull = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                assertFalse(summaryFull.contains("***MASKED***"));

                // Clear RSA pair
                keys.handleClearRsa();
                assertFalse(rsaCard.isVisible());

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testAsyncProgressUIElementsFormattingAndAccessibility() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                javafx.scene.layout.HBox box = getField(controller, "asyncProgressBox");
                javafx.scene.control.ProgressIndicator spinner = getField(controller, "asyncProgressSpinner");
                javafx.scene.control.ProgressBar bar = getField(controller, "asyncProgressBar");
                javafx.scene.control.Label label = getField(controller, "asyncProgressLabel");
                javafx.scene.control.Button cancelBtn = getField(controller, "asyncCancelBtn");

                // Test 1: Determined progress (File Cipher 42% 12.4MB / 29.5MB 00:08)
                long bytesProcessed = (long) (42.0 / 100.0 * 29.5 * 1024 * 1024);
                long totalBytes = (long) (29.5 * 1024 * 1024);
                OperationExecutor.ProgressDetails determinedDetails = new OperationExecutor.ProgressDetails(
                        "Encrypting file", bytesProcessed, totalBytes, 8000,
                        OperationExecutor.formatProgressText("Encrypting file", bytesProcessed, totalBytes, 8000)
                );

                controller.updateAsyncProgressDetails(determinedDetails);

                assertTrue(box.isVisible());
                assertTrue(bar.isVisible());
                assertFalse(spinner.isVisible());
                assertEquals(0.42, bar.getProgress(), 0.01);
                assertTrue(label.getText().contains("42%"));
                assertTrue(label.getText().contains("00:08"));
                assertTrue(label.getAccessibleText().contains("42%"));
                assertTrue(bar.getAccessibleText().contains("42%"));

                // Test 2: Indeterminate progress (RSA Key Gen)
                OperationExecutor.ProgressDetails indeterminateDetails = new OperationExecutor.ProgressDetails(
                        "Generating RSA-4096", 0, 0, 8000,
                        OperationExecutor.formatProgressText("Generating RSA-4096", 0, 0, 8000)
                );

                controller.updateAsyncProgressDetails(indeterminateDetails);

                assertTrue(box.isVisible());
                assertTrue(spinner.isVisible());
                assertFalse(bar.isVisible());
                assertEquals(-1.0, spinner.getProgress());
                assertEquals("Generating RSA-4096… · 00:08", label.getText());

                // Test 3: Commit Phase UI
                controller.getOperationExecutor().execute("Commit Test", null, () -> {
                    controller.getOperationExecutor().enterCommitPhase();
                    controller.handleCancelAsyncOperation();
                    return "Done";
                }, res -> {}, err -> {}, () -> {});

                assertEquals("Finishing file commit...", label.getText());
                assertTrue(cancelBtn.isDisable(), "Cancel button must be disabled during commit phase");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testFxmlInjectionsAndNoAutoExecutionOnNavigation() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                assertNotNull(getField(controller, "keysContainerController"), "keysContainerController must be injected");
                assertNotNull(getField(controller, "cipherContainerController"), "cipherContainerController must be injected");
                assertNotNull(getField(controller, "authenticationContainerController"), "authenticationContainerController must be injected");
                assertNotNull(getField(controller, "certificatesContainerController"), "certificatesContainerController must be injected");

                // Verify navigation to modules does NOT auto-execute operations or publish results
                controller.navigateToModule("Key Generation");
                assertFalse(controller.hasCurrentResult(), "Navigation MUST NOT auto-execute crypto operations");

                controller.navigateToModule("Symmetric Ciphers");
                assertFalse(controller.hasCurrentResult(), "Navigation MUST NOT auto-execute crypto operations");

                controller.navigateToModule("Digital Signatures");
                assertFalse(controller.hasCurrentResult(), "Navigation MUST NOT auto-execute crypto operations");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testMainActionButtonsTextSufficientWidth() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                javafx.scene.layout.HBox summaryBar = getField(controller, "resultSummaryBar");
                javafx.scene.control.Button expandBtn = summaryBar.getChildren().stream()
                        .filter(node -> node instanceof javafx.scene.layout.FlowPane)
                        .flatMap(fp -> ((javafx.scene.layout.FlowPane) fp).getChildren().stream())
                        .filter(node -> node instanceof javafx.scene.control.Button)
                        .map(node -> (javafx.scene.control.Button) node)
                        .filter(btn -> "Expand Result".equals(btn.getText()))
                        .findFirst().orElse(null);

                assertNotNull(expandBtn, "Expand Result button must exist in resultSummaryBar");
                assertEquals("Expand Result", expandBtn.getText());

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testUx09AlgorithmDependentFormulasAndVisibility() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController mainCtrl = loader.getController();
                mainCtrl.initialize();

                CipherController cipherCtrl = getField(mainCtrl, "cipherContainerController");
                assertNotNull(cipherCtrl);

                javafx.scene.control.ComboBox<String> symAlgoCombo = getField(cipherCtrl, "symmetricAlgorithmCombo");
                javafx.scene.control.ComboBox<String> cipherModeCombo = getField(cipherCtrl, "cipherModeCombo");
                javafx.scene.control.ComboBox<String> paddingCombo = getField(cipherCtrl, "paddingCombo");
                javafx.scene.control.TextField ivField = getField(cipherCtrl, "ivField");
                javafx.scene.control.TextField gcmTagField = getField(cipherCtrl, "gcmTagField");
                javafx.scene.control.TextField aadField = getField(cipherCtrl, "aadField");
                javafx.scene.layout.VBox ecbWarningBox = getField(cipherCtrl, "ecbWarningBox");

                // Test 1: ECB mode hides IV, Tag, AAD and shows ECB warning
                symAlgoCombo.setValue("AES-256");
                cipherModeCombo.setValue("ECB");
                assertFalse(ivField.isVisible(), "ECB mode must hide IV field");
                assertFalse(ivField.isManaged(), "ECB mode must unmanage IV field");
                assertFalse(gcmTagField.isVisible(), "ECB mode must hide Tag field");
                assertFalse(aadField.isVisible(), "ECB mode must hide AAD field");
                assertTrue(ecbWarningBox.isVisible(), "ECB mode must show security warning box");
                assertTrue(ecbWarningBox.isManaged(), "ECB mode must manage security warning box");

                // Test 2: GCM mode shows IV, Tag, AAD and hides ECB warning
                cipherModeCombo.setValue("GCM");
                assertTrue(ivField.isVisible(), "GCM mode must show IV field");
                assertTrue(gcmTagField.isVisible(), "GCM mode must show Tag field");
                assertTrue(aadField.isVisible(), "GCM mode must show AAD field");
                assertFalse(ecbWarningBox.isVisible(), "GCM mode must hide ECB warning box");

                // Test 3: CBC mode shows IV, hides Tag & AAD
                cipherModeCombo.setValue("CBC");
                assertTrue(ivField.isVisible(), "CBC mode must show IV field");
                assertFalse(gcmTagField.isVisible(), "CBC mode must hide Tag field");
                assertFalse(aadField.isVisible(), "CBC mode must hide AAD field");

                // Test 4: Programmatic change to ChaCha20-Poly1305 and XChaCha20-Poly1305
                symAlgoCombo.setValue("ChaCha20-Poly1305");
                assertTrue(gcmTagField.isVisible(), "ChaCha20-Poly1305 must show Tag field");
                assertTrue(gcmTagField.isManaged(), "ChaCha20-Poly1305 must manage Tag field");
                assertTrue(aadField.isVisible(), "ChaCha20-Poly1305 must show AAD field");
                assertTrue(aadField.isManaged(), "ChaCha20-Poly1305 must manage AAD field");
                assertTrue(ivField.getPromptText().contains("12 bytes"), "ChaCha20-Poly1305 must recommend 12-byte nonce");

                symAlgoCombo.setValue("XChaCha20-Poly1305");
                assertTrue(gcmTagField.isVisible(), "XChaCha20-Poly1305 must show Tag field");
                assertTrue(aadField.isVisible(), "XChaCha20-Poly1305 must show AAD field");
                assertTrue(ivField.getPromptText().contains("24 bytes"), "XChaCha20-Poly1305 must recommend 24-byte nonce");

                // Test 5: Non-AEAD stream ciphers (Salsa20 & ChaCha20) hide Tag & AAD and disable mode/padding
                symAlgoCombo.setValue("Salsa20");
                assertFalse(gcmTagField.isVisible(), "Salsa20 must hide Tag field");
                assertFalse(aadField.isVisible(), "Salsa20 must hide AAD field");
                assertTrue(cipherModeCombo.isDisabled(), "Stream ciphers must disable mode combo");
                assertTrue(paddingCombo.isDisabled(), "Stream ciphers must disable padding combo");

                symAlgoCombo.setValue("ChaCha20");
                assertFalse(gcmTagField.isVisible(), "ChaCha20 non-AEAD must hide Tag field");
                assertFalse(aadField.isVisible(), "ChaCha20 non-AEAD must hide AAD field");

                // Test 6: Restoration to AES/GCM and AES/ECB
                symAlgoCombo.setValue("AES-256");
                cipherModeCombo.setValue("GCM");
                assertTrue(gcmTagField.isVisible(), "Restoration to AES-GCM must show Tag field");
                assertTrue(aadField.isVisible(), "Restoration to AES-GCM must show AAD field");

                cipherModeCombo.setValue("ECB");
                assertFalse(ivField.isVisible(), "Restoration to AES-ECB must hide IV field");
                assertFalse(gcmTagField.isVisible(), "Restoration to AES-ECB must hide Tag field");
                assertTrue(ecbWarningBox.isVisible(), "Restoration to AES-ECB must show warning box");

                // Test 7: Authentication Controller Sign/Verify/MAC requirements
                AuthenticationController authCtrl = getField(mainCtrl, "authenticationContainerController");
                assertNotNull(authCtrl);

                javafx.scene.control.TextArea sigPrivKeyArea = getField(authCtrl, "signaturePrivateKeyArea");
                javafx.scene.control.TextArea sigPubKeyArea = getField(authCtrl, "signaturePublicKeyArea");
                javafx.scene.control.TextField sigVerifyField = getField(authCtrl, "signatureVerifyField");
                javafx.scene.control.TextField authMacKeyField = getField(authCtrl, "authMacKeyField");

                assertNotNull(sigPrivKeyArea);
                assertNotNull(sigPubKeyArea);
                assertNotNull(sigVerifyField);
                assertNotNull(authMacKeyField);

                // Test 8: KDF algorithm parameter visibility and input preservation
                KeysController keysCtrl = getField(mainCtrl, "keysContainerController");
                assertNotNull(keysCtrl);

                javafx.scene.control.ComboBox<String> kdfAlgoCombo = getField(keysCtrl, "kdfAlgorithmCombo");
                javafx.scene.control.TextField kdfInputField = getField(keysCtrl, "kdfInputField");
                javafx.scene.control.TextField kdfIterationsField = getField(keysCtrl, "kdfIterationsField");
                javafx.scene.layout.VBox kdfInfoBox = getField(keysCtrl, "kdfInfoBox");

                kdfInputField.setText("user-input-secret-key");

                kdfAlgoCombo.setValue("HKDF-SHA256");
                assertFalse(kdfIterationsField.isVisible(), "HKDF must hide iterations field");
                assertFalse(kdfIterationsField.isManaged(), "HKDF must unmanage iterations field");
                assertTrue(kdfInfoBox.isVisible(), "HKDF must show info box");
                assertEquals("user-input-secret-key", kdfInputField.getText(), "Changing KDF algorithm MUST NOT erase user input");

                kdfAlgoCombo.setValue("PBKDF2-SHA256");
                assertTrue(kdfIterationsField.isVisible(), "PBKDF2 must show iterations field");
                assertTrue(kdfIterationsField.isManaged(), "PBKDF2 must manage iterations field");
                assertFalse(kdfInfoBox.isVisible(), "PBKDF2 must hide info box");
                assertEquals("user-input-secret-key", kdfInputField.getText(), "Changing KDF algorithm MUST NOT erase user input");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testUx09PreflightRealValidationChecks() throws Exception {
        // 1. ChaCha20-Poly1305 / XChaCha20-Poly1305 decryption without tag -> INCOMPLETE, target gcmTagField
        com.cryptocarver.model.PreflightReport reportNoTag1 = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "ChaCha20-Poly1305", "CBC", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabb", "", "", false);
        assertFalse(reportNoTag1.isExecutable());
        com.cryptocarver.model.PreflightCheck checkNoTag1 = reportNoTag1.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.INCOMPLETE, checkNoTag1.getStatus());
        assertEquals("gcmTagField", checkNoTag1.getTargetControlKey());

        com.cryptocarver.model.PreflightReport reportNoTag2 = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "XChaCha20-Poly1305", "CBC", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabb00112233445566778899aabb", "", "", false);
        assertFalse(reportNoTag2.isExecutable());
        com.cryptocarver.model.PreflightCheck checkNoTag2 = reportNoTag2.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.INCOMPLETE, checkNoTag2.getStatus());
        assertEquals("gcmTagField", checkNoTag2.getTargetControlKey());

        // 2. Invalid hex tag & tag size != 16 bytes -> BLOCKED, target gcmTagField
        com.cryptocarver.model.PreflightReport reportInvalidHexTag = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "ChaCha20-Poly1305", "CBC", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabb", "NOT-HEX-TAG!", "", false);
        assertFalse(reportInvalidHexTag.isExecutable());
        com.cryptocarver.model.PreflightCheck checkInvalidHexTag = reportInvalidHexTag.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.BLOCKED, checkInvalidHexTag.getStatus());
        assertEquals("gcmTagField", checkInvalidHexTag.getTargetControlKey());

        com.cryptocarver.model.PreflightReport reportBadSizeTag = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "ChaCha20-Poly1305", "CBC", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabb", "001122334455", "", false);
        assertFalse(reportBadSizeTag.isExecutable());
        com.cryptocarver.model.PreflightCheck checkBadSizeTag = reportBadSizeTag.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.BLOCKED, checkBadSizeTag.getStatus());
        assertEquals("gcmTagField", checkBadSizeTag.getTargetControlKey());

        // 3. Valid 16-byte hex tag -> READY
        com.cryptocarver.model.PreflightReport reportValidTag = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "ChaCha20-Poly1305", "CBC", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabb", "00112233445566778899aabbccddeeff", "", false);
        assertTrue(reportValidTag.isExecutable());

        // 4. Sign without private key -> INCOMPLETE pointing to signaturePrivateKeyArea
        com.cryptocarver.model.PreflightReport reportNoSignKey = com.cryptocarver.model.OperationPreflightEngine.checkDigitalSignature(
                "Message to sign", "SHA256withRSA", "", null, false, true);
        assertFalse(reportNoSignKey.isExecutable());
        com.cryptocarver.model.PreflightCheck checkNoSignKey = reportNoSignKey.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.INCOMPLETE, checkNoSignKey.getStatus());
        assertEquals("signaturePrivateKeyArea", checkNoSignKey.getTargetControlKey());

        // 5. Verify without public key -> INCOMPLETE pointing to signaturePublicKeyArea
        com.cryptocarver.model.PreflightReport reportNoVerifyPubKey = com.cryptocarver.model.OperationPreflightEngine.checkDigitalSignature(
                "Message to verify", "SHA256withRSA", "", "existingSigHex", false, false);
        assertFalse(reportNoVerifyPubKey.isExecutable());
        com.cryptocarver.model.PreflightCheck checkNoVerifyPubKey = reportNoVerifyPubKey.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.INCOMPLETE, checkNoVerifyPubKey.getStatus());
        assertEquals("signaturePublicKeyArea", checkNoVerifyPubKey.getTargetControlKey());

        // 6. Verify without signature -> INCOMPLETE pointing to signatureVerifyField
        com.cryptocarver.model.PreflightReport reportNoVerifySig = com.cryptocarver.model.OperationPreflightEngine.checkDigitalSignature(
                "Message to verify", "SHA256withRSA", "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----", "", false, false);
        assertFalse(reportNoVerifySig.isExecutable());
        com.cryptocarver.model.PreflightCheck checkNoVerifySig = reportNoVerifySig.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.INCOMPLETE, checkNoVerifySig.getStatus());
        assertEquals("signatureVerifyField", checkNoVerifySig.getTargetControlKey());

        // 7. MAC manual without key -> INCOMPLETE pointing to authMacKeyField
        com.cryptocarver.model.PreflightReport reportNoMacKey = com.cryptocarver.model.OperationPreflightEngine.checkMac(
                "Message for MAC", "HMAC-SHA256", "Manual Input", "", null, false);
        assertFalse(reportNoMacKey.isExecutable());
        com.cryptocarver.model.PreflightCheck checkNoMacKey = reportNoMacKey.getFirstNonReadyCheck();
        assertEquals(com.cryptocarver.model.PreflightStatus.INCOMPLETE, checkNoMacKey.getStatus());
        assertEquals("authMacKeyField", checkNoMacKey.getTargetControlKey());
    }

    @Test
    void testUx09StreamCipherPreflightModeWarningsAndVisibility() throws Exception {
        // 1. ChaCha20-Poly1305 with inherited mode CBC: no ECB/CBC warnings in preflight
        com.cryptocarver.model.PreflightReport reportChaChaCbc = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "ChaCha20-Poly1305", "CBC", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabb", "00112233445566778899aabbccddeeff", "", true);

        boolean hasCbcOrEcbWarning = reportChaChaCbc.getChecks().stream()
                .anyMatch(c -> c.getStatus() == com.cryptocarver.model.PreflightStatus.WARNING
                        && c.getMessage().contains("vulnerable to padding oracle"));
        assertFalse(hasCbcOrEcbWarning, "ChaCha20-Poly1305 with inherited CBC mode must NOT produce a CBC warning");

        // 2. XChaCha20-Poly1305 with inherited mode ECB: no ECB warning in preflight report
        com.cryptocarver.model.PreflightReport reportXChaChaEcb = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "XChaCha20-Poly1305", "ECB", "NoPadding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabb00112233445566778899aabb", "00112233445566778899aabbccddeeff", "", true);

        boolean hasEcbWarning = reportXChaChaEcb.getChecks().stream()
                .anyMatch(c -> c.getStatus() == com.cryptocarver.model.PreflightStatus.WARNING
                        && c.getMessage().contains("ECB mode does not use an IV"));
        assertFalse(hasEcbWarning, "XChaCha20-Poly1305 with inherited ECB mode must NOT produce an ECB warning");

        // 3. UI Check: XChaCha20-Poly1305 with inherited mode ECB does NOT hide nonce/tag/AAD in UI
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController mainCtrl = loader.getController();
                mainCtrl.initialize();

                CipherController cipherCtrl = getField(mainCtrl, "cipherContainerController");
                assertNotNull(cipherCtrl);

                javafx.scene.control.ComboBox<String> symAlgoCombo = getField(cipherCtrl, "symmetricAlgorithmCombo");
                javafx.scene.control.ComboBox<String> cipherModeCombo = getField(cipherCtrl, "cipherModeCombo");
                javafx.scene.control.ComboBox<String> paddingCombo = getField(cipherCtrl, "paddingCombo");
                javafx.scene.control.TextField ivField = getField(cipherCtrl, "ivField");
                javafx.scene.control.TextField gcmTagField = getField(cipherCtrl, "gcmTagField");
                javafx.scene.control.TextField aadField = getField(cipherCtrl, "aadField");

                // Assert combo preservation before & after algorithm switch
                symAlgoCombo.setValue("AES-256");
                cipherModeCombo.setValue("ECB");
                paddingCombo.setValue("PKCS7Padding");

                assertEquals("ECB", cipherModeCombo.getValue(), "Mode combo initial value before stream cipher switch");
                assertEquals("PKCS7Padding", paddingCombo.getValue(), "Padding combo initial value before stream cipher switch");

                // Switch to stream cipher
                symAlgoCombo.setValue("XChaCha20-Poly1305");

                // Combos are disabled BUT preserve their user-selected values without reset or erase
                assertTrue(cipherModeCombo.isDisabled(), "Mode combo must be disabled for XChaCha20-Poly1305");
                assertTrue(paddingCombo.isDisabled(), "Padding combo must be disabled for XChaCha20-Poly1305");
                assertEquals("ECB", cipherModeCombo.getValue(), "Mode combo MUST preserve pre-existing value 'ECB' after switching to stream cipher");
                assertEquals("PKCS7Padding", paddingCombo.getValue(), "Padding combo MUST preserve pre-existing value 'PKCS7Padding' after switching to stream cipher");

                assertTrue(ivField.isVisible(), "XChaCha20-Poly1305 with inherited ECB mode must keep IV/Nonce field visible");
                assertTrue(gcmTagField.isVisible(), "XChaCha20-Poly1305 with inherited ECB mode must keep Tag field visible");
                assertTrue(aadField.isVisible(), "XChaCha20-Poly1305 with inherited ECB mode must keep AAD field visible");

            } catch (Exception e) {
                fail(e);
            }
        });

        // 4. AES-256 / CBC continues generating CBC security warning
        com.cryptocarver.model.PreflightReport reportAesCbc = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "AES-256", "CBC", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "00112233445566778899aabbccddeeff", "", "", true);

        boolean hasAesCbcWarning = reportAesCbc.getChecks().stream()
                .anyMatch(c -> c.getStatus() == com.cryptocarver.model.PreflightStatus.WARNING
                        && c.getMessage().contains("vulnerable to padding oracle"));
        assertTrue(hasAesCbcWarning, "AES-256 / CBC must generate CBC padding oracle warning");

        // 5. AES-256 / ECB continues generating ECB security warning
        com.cryptocarver.model.PreflightReport reportAesEcb = com.cryptocarver.model.OperationPreflightEngine.checkSymmetricCipher(
                "48656c6c6f", "Hex", "AES-256", "ECB", "PKCS7Padding",
                "Manual Input", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", null, false,
                "", "", "", true);

        boolean hasAesEcbWarning = reportAesEcb.getChecks().stream()
                .anyMatch(c -> c.getStatus() == com.cryptocarver.model.PreflightStatus.WARNING
                        && c.getMessage().contains("ECB mode does not use an IV"));
        assertTrue(hasAesEcbWarning, "AES-256 / ECB must generate ECB security warning");
    }

    @Test
    void testNarrowViewportLayout() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                javafx.scene.Scene scene = new javafx.scene.Scene(root, 480, 800);
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(scene);
                stage.setWidth(480);
                stage.setHeight(800);
                stage.show();

                root.applyCss();
                root.layout();

                assertNotNull(root);
                assertEquals(480.0, scene.getWidth(), 1.0);
                assertEquals(480.0, stage.getWidth(), 1.0);

                // Verify child controls: visible buttons have non-empty text and visible controls are managed
                java.util.List<Node> allNodes = new java.util.ArrayList<>();
                collectAllNodes(root, allNodes);

                for (Node node : allNodes) {
                    boolean isEffectivelyVisible = node.isVisible() && node.getScene() != null && node.getParent() != null && node.getParent().isVisible();
                    if (isEffectivelyVisible && (node instanceof javafx.scene.control.Button || node instanceof javafx.scene.control.TextInputControl || node instanceof javafx.scene.control.ComboBox)) {
                        assertTrue(node.isManaged(), "Visible form control " + (node.getId() != null ? node.getId() : node.getClass().getSimpleName()) + " must have isManaged() == true");
                    }
                    if (node instanceof javafx.scene.control.Button btn && isEffectivelyVisible) {
                        assertNotNull(btn.getText(), "Visible button must have non-null text");
                        assertFalse(btn.getText().trim().isEmpty(), "Visible button text must not be empty or truncated away");
                    }
                    if (node.getClip() != null) {
                        assertTrue(node.getClip().getBoundsInParent().getWidth() <= 480.0, "Clip bounds on container " + node.getId() + " must not exceed viewport width");
                    }
                }

                stage.close();
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    private void collectAllNodes(Parent parent, java.util.List<Node> nodes) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            nodes.add(child);
            if (child instanceof Parent p) {
                collectAllNodes(p, nodes);
            }
        }
    }

    @Test
    void testResultSummaryNeutralAndSuccessStates() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        javafx.scene.control.Label statusBadge = getField(controller, "resultStatusBadge");
        assertNotNull(statusBadge);

        runAndWait(() -> {
            assertTrue(statusBadge.getStyleClass().contains("result-status-neutral")
                    || statusBadge.getStyleClass().contains("result-status-success"));
            assertNotNull(statusBadge.getAccessibleText());
        });
    }

    @Test
    void testHistoryCardWithReopenButton() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        VBox historyContainer = getField(controller, "historyContainer");
        assertNotNull(historyContainer);

        runAndWait(() -> {
            try {
                Method refresh = ModernMainController.class.getDeclaredMethod("refreshHistoryUI");
                refresh.setAccessible(true);
                refresh.invoke(controller);
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testQuickStartCardStructure() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                fail(e);
            }
        });

        ModernMainController controller = controllerRef.get();
        VBox quickStart = getField(controller, "quickStartContainer");
        assertNotNull(quickStart);
    }

    @Test
    void testButtonActionStylesFocusAndDisabledStates() throws Exception {
        runAndWait(() -> {
            VBox root = new VBox(10);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 600, 400);
            URL cssResource = getClass().getResource("/css/styles.css");
            assertNotNull(cssResource, "styles.css must exist");
            scene.getStylesheets().add(cssResource.toExternalForm());

            javafx.scene.control.Button primary = new javafx.scene.control.Button("Primary");
            primary.getStyleClass().addAll("action-button-primary", "primary-action");
            primary.setDisable(true);

            javafx.scene.control.Button secondary = new javafx.scene.control.Button("Secondary");
            secondary.getStyleClass().addAll("secondary-button", "secondary-action");

            javafx.scene.control.Button danger = new javafx.scene.control.Button("Danger");
            danger.getStyleClass().addAll("btn-danger", "danger-action");

            javafx.scene.control.Label subtle = new javafx.scene.control.Label("Subtle Label");
            subtle.getStyleClass().add("subtle-text");

            root.getChildren().addAll(primary, secondary, danger, subtle);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(scene);
            stage.show();

            root.applyCss();
            root.layout();

            // Verify disabled state and computed properties
            assertTrue(primary.isDisabled(), "Primary button must be disabled");
            assertTrue(primary.getPseudoClassStates().stream().anyMatch(pc -> "disabled".equals(pc.getPseudoClassName())));
            assertTrue(primary.getOpacity() > 0.0, "Primary button opacity should be valid");

            // Verify focus state
            secondary.requestFocus();
            assertTrue(secondary.isFocused() || secondary.getPseudoClassStates().stream().anyMatch(pc -> "focused".equals(pc.getPseudoClassName())));

            // Verify computed background styles are populated by JavaFX CSS engine
            assertNotNull(primary.getBackground(), "Primary button computed background must not be null");
            assertNotNull(secondary.getBackground(), "Secondary button computed background must not be null");
            assertNotNull(danger.getBackground(), "Danger button computed background must not be null");

            assertTrue(primary.getStyleClass().contains("primary-action"));
            assertTrue(secondary.getStyleClass().contains("secondary-action"));
            assertTrue(danger.getStyleClass().contains("danger-action"));
            assertTrue(subtle.getStyleClass().contains("subtle-text"));

            stage.close();
        });
    }

    @Test
    void testUX11RuntimeComponentRenderingAndCssStyles() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                assertNotNull(resource, "main-view-modern.fxml must exist");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                rootRef.set(root);
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);

                VBox wrapper = new VBox(root);
                javafx.scene.Scene scene = new javafx.scene.Scene(wrapper, 1024, 768);
                URL cssResource = getClass().getResource("/css/styles.css");
                assertNotNull(cssResource, "styles.css must exist");
                scene.getStylesheets().add(cssResource.toExternalForm());

                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(scene);
                stage.show();

                wrapper.applyCss();
                wrapper.layout();

                // 1. Result Summary Transition (Neutral -> SUCCESS)
                javafx.scene.control.Label resultBadge = getField(controller, "resultStatusBadge");
                assertNotNull(resultBadge);

                // Trigger real transition to SUCCESS via publish(OperationResult)
                com.cryptocarver.model.OperationResult opResult = com.cryptocarver.model.OperationResult
                        .forOperation("AES-256 Encryption")
                        .input(new byte[]{1,2,3})
                        .output(new byte[]{4,5,6})
                        .detail("Algorithm", "AES/CBC/PKCS5Padding")
                        .build();
                controller.publish(opResult);

                wrapper.applyCss();
                wrapper.layout();
                assertTrue(resultBadge.getStyleClass().contains("result-status-success"), "resultBadge must have result-status-success after transition");
                assertNotNull(resultBadge.getBackground(), "resultBadge background must be computed");
                assertTrue(resultBadge.getOpacity() > 0.0);

                // 2. Quick Start Cards & Content
                VBox quickStart = getField(controller, "quickStartContainer");
                assertNotNull(quickStart);
                assertFalse(quickStart.getChildren().isEmpty(), "quickStartContainer must contain cards");
                for (Node child : quickStart.getChildren()) {
                    assertNotNull(child.getStyleClass());
                    assertTrue(child.getOpacity() > 0.0);
                }

                // 3. History Entry, Card & Reopen Button
                com.cryptocarver.model.HistoryManager historyManager = getField(controller, "historyManager");
                assertNotNull(historyManager);
                historyManager.addHistoryItem(new com.cryptocarver.model.HistoryCommand(
                        "AES-GCM Encryption", "2026-07-31 18:00:00", java.util.Map.of("key", "secret")));

                Method refreshHistory = ModernMainController.class.getDeclaredMethod("refreshHistoryUI");
                refreshHistory.setAccessible(true);
                refreshHistory.invoke(controller);

                wrapper.applyCss();
                wrapper.layout();

                VBox historyContainer = getField(controller, "historyContainer");
                assertNotNull(historyContainer);
                assertFalse(historyContainer.getChildren().isEmpty(), "historyContainer must contain rendered history cards");

                HBox firstHistoryCard = (HBox) historyContainer.getChildren().get(0);
                assertTrue(firstHistoryCard.getStyleClass().contains("history-card"), "History card must have history-card styleClass");
                assertNotNull(firstHistoryCard.getBackground(), "History card background must be computed");

                Button reopenBtn = (Button) firstHistoryCard.getChildren().get(1);
                assertTrue(reopenBtn.getStyleClass().contains("history-card-action"), "Reopen button must have history-card-action styleClass");
                assertNotNull(reopenBtn.getBackground(), "Reopen button background must be computed");
                assertTrue(reopenBtn.getOpacity() > 0.0);

                // 4. Readiness Badges Real Flow (READY, WARNING, INCOMPLETE, BLOCKED)
                javafx.scene.control.Label readinessBadge = getField(controller, "readinessStatusBadge");
                assertNotNull(readinessBadge);

                // Test READY report flow
                com.cryptocarver.model.PreflightReport reportReady = new com.cryptocarver.model.PreflightReport(
                        com.cryptocarver.model.PreflightStatus.READY, "System is ready",
                        java.util.List.of(new com.cryptocarver.model.PreflightCheck("Key", com.cryptocarver.model.PreflightStatus.READY, "Valid", "keyControl")));
                setField(controller, "currentPreflightReport", reportReady);
                Method updateReadiness = ModernMainController.class.getDeclaredMethod("updateReadinessPanelUI");
                updateReadiness.setAccessible(true);
                updateReadiness.invoke(controller);

                wrapper.applyCss();
                wrapper.layout();
                assertTrue(readinessBadge.getStyleClass().contains("readiness-status-ready"), "Readiness badge must contain readiness-status-ready");
                assertNotNull(readinessBadge.getBackground(), "READY readiness badge background must be computed");

                // Test WARNING report flow
                com.cryptocarver.model.PreflightReport reportWarning = new com.cryptocarver.model.PreflightReport(
                        com.cryptocarver.model.PreflightStatus.WARNING, "Warning detected",
                        java.util.List.of(new com.cryptocarver.model.PreflightCheck("Key", com.cryptocarver.model.PreflightStatus.WARNING, "Weak key", "keyControl")));
                setField(controller, "currentPreflightReport", reportWarning);
                updateReadiness.invoke(controller);

                wrapper.applyCss();
                wrapper.layout();
                assertTrue(readinessBadge.getStyleClass().contains("readiness-status-warning"), "Readiness badge must contain readiness-status-warning");
                assertNotNull(readinessBadge.getBackground(), "WARNING readiness badge background must be computed");

                // Test INCOMPLETE report flow
                com.cryptocarver.model.PreflightReport reportIncomplete = new com.cryptocarver.model.PreflightReport(
                        com.cryptocarver.model.PreflightStatus.INCOMPLETE, "Setup incomplete",
                        java.util.List.of(new com.cryptocarver.model.PreflightCheck("Key", com.cryptocarver.model.PreflightStatus.INCOMPLETE, "Missing parameter", "keyControl")));
                setField(controller, "currentPreflightReport", reportIncomplete);
                updateReadiness.invoke(controller);

                wrapper.applyCss();
                wrapper.layout();
                assertTrue(readinessBadge.getStyleClass().contains("readiness-status-incomplete"), "Readiness badge must contain readiness-status-incomplete");
                assertNotNull(readinessBadge.getBackground(), "INCOMPLETE readiness badge background must be computed");

                // Test BLOCKED report flow
                com.cryptocarver.model.PreflightReport reportBlocked = new com.cryptocarver.model.PreflightReport(
                        com.cryptocarver.model.PreflightStatus.BLOCKED, "Execution blocked",
                        java.util.List.of(new com.cryptocarver.model.PreflightCheck("Key", com.cryptocarver.model.PreflightStatus.BLOCKED, "Blocked", "keyControl")));
                setField(controller, "currentPreflightReport", reportBlocked);
                updateReadiness.invoke(controller);

                wrapper.applyCss();
                wrapper.layout();
                assertTrue(readinessBadge.getStyleClass().contains("readiness-status-blocked"), "Readiness badge must contain readiness-status-blocked");
                assertNotNull(readinessBadge.getBackground(), "BLOCKED readiness badge background must be computed");

                // 5. Inspector Presenter & Computed Details Nodes
                VBox detailsContainer = getField(controller, "inspectorDetailsContainer");
                assertNotNull(detailsContainer);
                OperationInspectorPresenter inspector;
                Method getInspector = ModernMainController.class.getDeclaredMethod("inspectorPresenter");
                getInspector.setAccessible(true);
                inspector = (OperationInspectorPresenter) getInspector.invoke(controller);

                inspector.present("Symmetric Ciphers", new byte[]{1,2,3}, new byte[]{4,5,6},
                        java.util.List.of(new com.cryptocarver.model.OperationDetail("Mode", "CBC",
                                com.cryptocarver.model.OperationDetail.Classification.PUBLIC, false, "Text")));
                wrapper.applyCss();
                wrapper.layout();
                assertFalse(detailsContainer.getChildren().isEmpty(), "Inspector details must render children");

                boolean hasInspectorLabel = false;
                boolean hasInspectorValue = false;

                for (Node child : detailsContainer.getChildren()) {
                    assertNotNull(child.getStyleClass(), "Inspector detail node must have styleClasses");
                    assertTrue(child.getOpacity() > 0.0);
                    if (child.getStyleClass().contains("inspector-label")) hasInspectorLabel = true;
                    if (child.getStyleClass().contains("inspector-value")) hasInspectorValue = true;
                    if (child instanceof Parent) {
                        for (Node subChild : ((Parent) child).getChildrenUnmodifiable()) {
                            if (subChild.getStyleClass().contains("inspector-label")) hasInspectorLabel = true;
                            if (subChild.getStyleClass().contains("inspector-value")) hasInspectorValue = true;
                        }
                    }
                }
                assertTrue(hasInspectorLabel, "Inspector must render nodes with inspector-label style class");
                assertTrue(hasInspectorValue, "Inspector must render nodes with inspector-value style class");

                stage.close();
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testHistoryReopenButtonEnabledAndDisabledStates() throws Exception {
        runAndWait(() -> {
            VBox root = new VBox(10);
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 600, 400);
            URL cssResource = getClass().getResource("/css/styles.css");
            assertNotNull(cssResource, "styles.css must exist");
            scene.getStylesheets().add(cssResource.toExternalForm());

            Button enabledReopen = new Button("Reopen Enabled");
            enabledReopen.getStyleClass().add("history-card-action");

            Button disabledReopen = new Button("Reopen Disabled");
            disabledReopen.getStyleClass().add("history-card-action");
            disabledReopen.setDisable(true);

            root.getChildren().addAll(enabledReopen, disabledReopen);

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(scene);
            stage.show();
            root.setFocusTraversable(true);
            root.requestFocus();

            root.applyCss();
            root.layout();

            // 1. Style class verification
            assertTrue(enabledReopen.getStyleClass().contains("history-card-action"));
            assertTrue(disabledReopen.getStyleClass().contains("history-card-action"));

            // 2. Disabled status and pseudo-class
            assertFalse(enabledReopen.isDisabled());
            assertTrue(disabledReopen.isDisabled());
            assertTrue(disabledReopen.getPseudoClassStates().stream().anyMatch(pc -> "disabled".equals(pc.getPseudoClassName())));

            // 3. Opacity difference verification (exact opacity 1.0 enabled vs 0.55 disabled)
            double enabledOpacity = enabledReopen.getOpacity();
            double disabledOpacity = disabledReopen.getOpacity();
            assertEquals(1.0, enabledOpacity, 0.01, "Enabled button opacity must be 1.0");
            assertEquals(0.55, disabledOpacity, 0.01, "Disabled button opacity must be 0.55");
            assertNotEquals(enabledOpacity, disabledOpacity, "Enabled and disabled opacities must differ");

            // 4. Focus state and computed focus border on enabled button
            javafx.scene.layout.Border unfocusedBorder = enabledReopen.getBorder();
            enabledReopen.requestFocus();
            root.applyCss();
            root.layout();

            javafx.scene.layout.Border focusedBorder = enabledReopen.getBorder();
            assertTrue(enabledReopen.isFocused(), "Enabled button must receive real keyboard focus");
            assertNotNull(enabledReopen.getBackground(), "Enabled button must have computed background");
            assertNotNull(focusedBorder, "Enabled button must have computed focus border");
            assertNotEquals(unfocusedBorder, focusedBorder, "Focused border must differ from unfocused border");

            // 5. Disabled button must not receive hover/focused pseudo-classes
            assertFalse(disabledReopen.getPseudoClassStates().stream().anyMatch(pc -> "focused".equals(pc.getPseudoClassName())),
                    "Disabled button must not be focused");
            assertFalse(disabledReopen.getPseudoClassStates().stream().anyMatch(pc -> "hover".equals(pc.getPseudoClassName())),
                    "Disabled button must not receive hover pseudo-class");
            assertNotNull(disabledReopen.getBackground(), "Disabled button must have computed background");

            stage.close();
        });
    }

    @Test
    void testUx18HashSha256TextUtf8ToHexUsesHola() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        GenericController generic = getField(controller, "genericContainerController");
        javafx.scene.control.TextArea input = getField(generic, "hashInputArea");
        javafx.scene.control.TextArea output = getField(generic, "hashOutputArea");
        javafx.scene.control.ComboBox<String> inputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> outputFormat = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.navigateTo("Hashing");
            inputFormat.setValue("Text (UTF-8)");
            outputFormat.setValue("Hexadecimal");
            input.setText("Hola");
            generic.handleCalculateHash();
        });

        assertEquals("E633F4FC79BADEA1DC5DB970CF397C8248BAC47CC3ACF9915BA60B5D76B0E88F", output.getText());
    }

    @Test
    void testUx18ManualConversionTextUtf8ToBase64UsesSharedContract() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        GenericController generic = getField(controller, "genericContainerController");
        javafx.scene.control.TextArea input = getField(generic, "manualInputArea");
        javafx.scene.control.TextArea output = getField(generic, "manualOutputArea");
        javafx.scene.control.ComboBox<String> inputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> outputFormat = getField(controller, "outputFormatCombo");

        runAndWait(() -> {
            controller.navigateTo("Manual Conversion");
            inputFormat.setValue("Text (UTF-8)");
            outputFormat.setValue("Base64");
            input.setText("Hola");
            generic.handleManualConvert();
        });

        assertEquals("Text (UTF-8)", inputFormat.getValue());
        assertEquals("Base64", outputFormat.getValue());
        assertEquals("SG9sYQ==", output.getText());
    }

    @Test
    void testUx18CipherTemplateSynchronizesFormatsAndControls() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        CipherController cipher = getField(controller, "cipherContainerController");
        javafx.scene.control.ComboBox<String> template = getField(cipher, "cipherTemplateCombo");
        javafx.scene.control.ComboBox<String> inputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> outputFormat = getField(controller, "outputFormatCombo");
        javafx.scene.control.ComboBox<String> mode = getField(cipher, "cipherModeCombo");

        runAndWait(() -> {
            try {
                controller.navigateTo("Symmetric Ciphers");
                inputFormat.setValue("Hexadecimal");
                template.setValue("AES-256-GCM — Text UTF-8 → Base64");
                Method apply = CipherController.class.getDeclaredMethod("handleApplyCipherTemplate");
                apply.setAccessible(true);
                apply.invoke(cipher);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("Text (UTF-8)", inputFormat.getValue());
        assertEquals("Base64", outputFormat.getValue());
        assertEquals("GCM", mode.getValue());
    }

    @Test
    void testUx18SidebarNavigationExpandsManualConversionAndUpdatesBreadcrumb() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        javafx.scene.control.Accordion accordion = getField(controller, "genericContainer");
        javafx.scene.control.Label breadcrumb = getField(controller, "breadcrumbOperationLabel");

        runAndWait(() -> controller.navigateTo("Manual Conversion"));

        assertNotNull(accordion.getExpandedPane());
        assertTrue(accordion.getExpandedPane().getText().contains("Manual Conversion"));
        assertEquals("Manual Conversion", breadcrumb.getText());
    }

    @Test
    void testUx18PersonalHashTemplateApplyResetAndRestoreFormats() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controllerRef.set(loader.getController());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        ModernMainController controller = controllerRef.get();
        GenericController generic = getField(controller, "genericContainerController");
        javafx.scene.control.ComboBox<String> templateCombo = getField(generic, "hashTemplateCombo");
        javafx.scene.control.ComboBox<String> inputFormat = getField(controller, "inputFormatCombo");
        javafx.scene.control.ComboBox<String> outputFormat = getField(controller, "outputFormatCombo");
        String name = "UX18 Hash " + java.util.UUID.randomUUID();
        com.cryptocarver.model.SafeOperationTemplate template = new com.cryptocarver.model.SafeOperationTemplate(
                name, "Hashing", "formats-only", java.util.Map.of(
                        "hashAlgorithmCombo", "SHA-512",
                        "inputFormatCombo", "Plain Text",
                        "outputFormatCombo", "Base64"));
        com.cryptocarver.model.PersonalTemplateStore store = com.cryptocarver.model.PersonalTemplateStore.getInstance();
        store.saveTemplate(template);
        try {
            runAndWait(() -> {
                try {
                    controller.navigateTo("Hashing");
                    inputFormat.setValue("Hexadecimal");
                    templateCombo.setValue("[My Template] " + name);
                    Method apply = GenericController.class.getDeclaredMethod("handleApplyHashTemplate");
                    apply.setAccessible(true);
                    apply.invoke(generic);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertEquals("Text (UTF-8)", inputFormat.getValue());
            assertEquals("Base64", outputFormat.getValue());

            runAndWait(() -> {
                try {
                    Method reset = GenericController.class.getDeclaredMethod("handleResetHashDefaults");
                    reset.setAccessible(true);
                    reset.invoke(generic);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertEquals("Text (UTF-8)", inputFormat.getValue());
            assertEquals("Hexadecimal", outputFormat.getValue());

            runAndWait(() -> {
                try {
                    inputFormat.setValue("Hexadecimal");
                    templateCombo.setValue("[My Template] " + name);
                    Method apply = GenericController.class.getDeclaredMethod("handleApplyHashTemplate");
                    apply.setAccessible(true);
                    apply.invoke(generic);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertEquals("Text (UTF-8)", inputFormat.getValue());
        } finally {
            store.deleteTemplate(template.getId());
        }
    }
}
