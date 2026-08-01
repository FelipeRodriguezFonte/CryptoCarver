package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Menu;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Requires the same JavaFX runtime setup as the existing opt-in UI suite. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModernMainControllerI18nUITest {
    private static boolean started;

    @TempDir
    static Path temporaryDirectory;

    @BeforeAll
    static void startJavaFx() throws Exception {
        Path cache = Files.createTempDirectory("cryptocarver-i18n-javafx-cache-");
        System.setProperty("javafx.cachedir", cache.toString());
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX toolkit did not start");
        started = true;
    }

    @BeforeEach
    void isolateSettings() {
        AppSettings.setInstanceForTesting(new AppSettings(temporaryDirectory.resolve("settings.json")));
        I18nService.getInstance().setPreference(LanguagePreference.ES);
    }

    @AfterAll
    static void cleanup() {
        AppSettings.resetInstanceForTesting();
        if (started) Platform.setImplicitExit(false);
    }

    @Test
    void changingLanguageUpdatesLoadedShellImmediately() throws Exception {
        AtomicReference<ModernMainController> controller = new AtomicReference<>();
        AtomicReference<Menu> fileMenu = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                controller.set(loader.getController());
                fileMenu.set(readField(controller.get(), "fileMenu"));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        assertEquals("Archivo", fileMenu.get().getText());

        runAndWait(() -> I18nService.getInstance().setPreference(LanguagePreference.EN));
        assertEquals("File", fileMenu.get().getText());
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    private static void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable throwable) { failure.set(throwable); }
            finally { latch.countDown(); }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
