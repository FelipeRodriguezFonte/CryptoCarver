package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ScreenConfiguration;
import com.cryptocarver.model.ScreenConfigurationCodec;
import com.cryptocarver.model.ScreenConfigurationFiles;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Behavior locked before moving portable screen configuration out of ModernMainController. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ScreenConfigurationCharacterizationTest {
    private static boolean toolkitReady;
    @TempDir Path temp;

    @BeforeAll
    static void startToolkit() throws Exception {
        if (toolkitReady) return;
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
            ready.countDown();
        }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        toolkitReady = true;
    }

    @BeforeEach
    void useIsolatedSettings() {
        AppSettings.setInstanceForTesting(new AppSettings(temp.resolve("settings.json")));
    }

    @AfterEach
    void resetSettings() { AppSettings.resetInstanceForTesting(); }

    @AfterAll
    static void stopToolkit() { Platform.exit(); }

    @Test
    void productionFxmlLoadsAndPortableCipherConfigurationCapturesAndApplies() throws Exception {
        ModernMainController controller = loadController();
        assertNull(getField(controller, "cipherContainerController"),
                "Cipher is still unloaded when the main FXML has just been constructed");

        AtomicReference<ScreenConfiguration> captured = new AtomicReference<>();
        onFx(() -> {
            controller.navigateTo("Symmetric Ciphers");
            CipherController cipher = getField(controller, "cipherContainerController");
            ((javafx.scene.control.TextArea) getField(cipher, "cipherInputArea")).setText("synthetic portable input");
            ((javafx.scene.control.TextField) getField(cipher, "symmetricKeyField")).setText("SYNTHETIC-KEY-VALUE");
            ((javafx.scene.control.TextField) getField(cipher, "ivField")).setText("SYNTHETIC-IV");
            setCombo(cipher, "cipherModeCombo", "GCM");
            setCombo(controller, "inputFormatCombo", "Text (UTF-8)");
            setCombo(controller, "outputFormatCombo", "Hexadecimal");
            ((javafx.scene.control.TextArea) getField(cipher, "cipherOutputArea")).setText("SYNTHETIC-DERIVED-OUTPUT");
            captured.set(controller.captureActiveScreenConfiguration());

            ((javafx.scene.control.TextArea) getField(cipher, "cipherInputArea")).clear();
            ((javafx.scene.control.TextField) getField(cipher, "symmetricKeyField")).clear();
            ((javafx.scene.control.TextField) getField(cipher, "ivField")).clear();
            setCombo(cipher, "cipherModeCombo", "CBC");
            setCombo(controller, "inputFormatCombo", "Hexadecimal");
            setCombo(controller, "outputFormatCombo", "Text (UTF-8)");
            controller.navigateTo("Hashing");
            controller.applyScreenConfiguration(captured.get());
            return null;
        });

        CipherController cipher = getField(controller, "cipherContainerController");
        assertEquals("synthetic portable input", ((javafx.scene.control.TextArea) getField(cipher, "cipherInputArea")).getText());
        assertEquals("SYNTHETIC-KEY-VALUE", ((javafx.scene.control.TextField) getField(cipher, "symmetricKeyField")).getText());
        assertEquals("SYNTHETIC-IV", ((javafx.scene.control.TextField) getField(cipher, "ivField")).getText());
        assertEquals("GCM", ((javafx.scene.control.ComboBox<?>) getField(cipher, "cipherModeCombo")).getValue());
        assertEquals("Text (UTF-8)", ((javafx.scene.control.ComboBox<?>) getField(controller, "inputFormatCombo")).getValue());
        assertEquals("Hexadecimal", ((javafx.scene.control.ComboBox<?>) getField(controller, "outputFormatCombo")).getValue());
        assertFalse(captured.get().toState().containsKey("CipherController.cipherOutputArea"));
    }

    @Test
    void rejectsMismatchedModuleAndFieldsOutsideTheActiveScreenBeforeRestoring() throws Exception {
        ModernMainController controller = loadController();
        onFx(() -> {
            controller.navigateTo("Symmetric Ciphers");
            CipherController cipher = getField(controller, "cipherContainerController");
            ScreenConfiguration wrongModule = new ScreenConfiguration("Symmetric Ciphers", "GENERIC",
                    Map.of(), SecretVisibilityProfile.FULL_LAB);
            assertThrows(IllegalArgumentException.class, () -> controller.applyScreenConfiguration(wrongModule));

            ScreenConfiguration outsideScreen = new ScreenConfiguration("Symmetric Ciphers", "CIPHER",
                    Map.of("CipherController.symmetricKeyField", "SYNTHETIC-NO-APPLY",
                            "Unexpected.syntheticField", "SYNTHETIC-UNKNOWN"), SecretVisibilityProfile.FULL_LAB);
            assertThrows(IllegalArgumentException.class, () -> controller.applyScreenConfiguration(outsideScreen));
            assertEquals("", ((javafx.scene.control.TextField) getField(cipher, "symmetricKeyField")).getText());
            return null;
        });
    }

    @Test
    void v1MayRestoreModuleWideFieldsAndLegacyKeyGenerationNeedsGeneratedKey() throws Exception {
        ModernMainController controller = loadController();
        AtomicReference<ScreenConfiguration> legacy = new AtomicReference<>();
        onFx(() -> {
            controller.navigateTo("Key Generation");
            ScreenConfiguration v2 = controller.captureActiveScreenConfiguration();
            com.google.gson.JsonObject document = com.google.gson.JsonParser.parseString(v2.toJson()).getAsJsonObject();
            document.addProperty("version", 1);
            com.google.gson.JsonObject salt = new com.google.gson.JsonObject();
            salt.addProperty("type", "string");
            salt.addProperty("value", "SYNTHETIC-V1-SALT");
            document.getAsJsonObject("values").add("KeysController.kdfSaltField", salt);
            legacy.set(ScreenConfiguration.fromJson(document.toString()));
            controller.applyScreenConfiguration(legacy.get());
            return null;
        });
        KeysController keys = getField(controller, "keysContainerController");
        assertEquals("SYNTHETIC-V1-SALT", ((javafx.scene.control.TextField) getField(keys, "kdfSaltField")).getText());
        assertTrue(ModernMainController.isLegacyKeyGenerationConfiguration(legacy.get()));
    }

    @Test
    void plainAndEncryptedFilesRoundTripWithoutLeakingFailureValues() throws Exception {
        String synthetic = "SYNTHETIC-PORTABLE-SECRET";
        ScreenConfiguration configuration = new ScreenConfiguration("Symmetric Ciphers", "CIPHER",
                Map.of("CipherController.symmetricKeyField", synthetic,
                        "CipherController.cipherInputArea", "synthetic input"), SecretVisibilityProfile.FULL_LAB);

        Path plainFile = temp.resolve("portable.json");
        String plain = ScreenConfigurationCodec.encodePlain(configuration);
        ScreenConfigurationFiles.writeAtomic(plainFile, plain);
        assertFalse(ScreenConfigurationFiles.read(plainFile).contains(synthetic));
        assertEquals("[REDACTED_SECRET]", ScreenConfigurationCodec.decode(
                ScreenConfigurationFiles.read(plainFile), null).toState().get("CipherController.symmetricKeyField"));

        Path encryptedFile = temp.resolve("portable.ccconfig");
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(configuration, "SYNTHETIC-PASSWORD".toCharArray());
        ScreenConfigurationFiles.writeAtomic(encryptedFile, encrypted);
        ScreenConfiguration decoded = ScreenConfigurationCodec.decode(
                ScreenConfigurationFiles.read(encryptedFile), "SYNTHETIC-PASSWORD".toCharArray());
        assertEquals(synthetic, decoded.toState().get("CipherController.symmetricKeyField"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                ScreenConfigurationCodec.decode(ScreenConfigurationFiles.read(encryptedFile), "WRONG-SYNTHETIC".toCharArray()));
        assertFalse(failure.getMessage().contains(synthetic));
        assertFalse(failure.getMessage().contains("WRONG-SYNTHETIC"));
    }

    private ModernMainController loadController() throws Exception {
        return onFx(() -> {
            FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
            loader.load();
            return loader.getController();
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setCombo(Object target, String fieldName, String value) throws Exception {
        ((javafx.scene.control.ComboBox) getField(target, fieldName)).setValue(value);
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.call();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { result.set(work.call()); } catch (Throwable error) { failure.set(error); }
            finally { finished.countDown(); }
        });
        assertTrue(finished.await(30, TimeUnit.SECONDS), "JavaFX work timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }
}
