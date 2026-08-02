package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux16AccessibilityLiveUITest {
    @TempDir
    static Path temporaryDirectory;

    @BeforeAll
    static void startJavaFx() throws Exception {
        System.setProperty("javafx.cachedir", temporaryDirectory.resolve("javafx-cache").toString());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> startupFailure = new AtomicReference<>();
        Thread startupThread = new Thread(() -> {
            try {
                Platform.startup(latch::countDown);
            } catch (IllegalStateException alreadyStarted) {
                latch.countDown();
            } catch (Throwable unavailable) {
                startupFailure.set(unavailable);
                latch.countDown();
            }
        }, "ux16-javafx-startup");
        startupThread.setDaemon(true);
        startupThread.start();
        if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX toolkit did not start");
        Assumptions.assumeTrue(startupFailure.get() == null,
                "JavaFX graphics toolkit unavailable: " + startupFailure.get());
        Assumptions.assumeTrue(!Screen.getScreens().isEmpty(), "No graphical screen is available");
    }

    @BeforeEach
    void isolateSettings() throws Exception {
        AppSettings.setInstanceForTesting(new AppSettings(temporaryDirectory.resolve("settings.json")));
        runAndWait(() -> I18nService.getInstance().setPreference(LanguagePreference.EN));
    }

    @AfterAll
    static void restoreSettings() {
        AppSettings.resetInstanceForTesting();
    }

    @Test
    void accessibleTextAndHelpRefreshInEnglishAndSpanish() throws Exception {
        AtomicReference<Button> button = new AtomicReference<>();
        AtomicReference<TextField> field = new AtomicReference<>();
        runAndWait(() -> {
            Button apply = new Button("Apply");
            apply.setAccessibleText("Apply");
            apply.setAccessibleHelp("Apply");
            TextField input = new TextField();
            input.setAccessibleText("Input:");
            input.setAccessibleHelp("Input:");
            VBox root = new VBox(apply, input);
            new Scene(root);
            button.set(apply);
            field.set(input);
            ModuleI18n.bind(root, java.util.Map.of(
                    "Apply", "module.common.apply",
                    "Input:", "module.common.input"));
        });

        assertEquals("Apply", button.get().getAccessibleText());
        assertEquals("Apply", button.get().getAccessibleHelp());
        runAndWait(() -> I18nService.getInstance().setPreference(LanguagePreference.ES));
        assertEquals("Aplicar", button.get().getAccessibleText());
        assertEquals("Aplicar", button.get().getAccessibleHelp());
        assertEquals("Entrada:", field.get().getAccessibleText());
    }

    @Test
    void languageRefreshPreservesFocusAndDialogDetail() throws Exception {
        AtomicReference<TextField> field = new AtomicReference<>();
        AtomicReference<Scene> scene = new AtomicReference<>();
        runAndWait(() -> {
            TextField input = new TextField();
            input.setAccessibleText("Input:");
            VBox root = new VBox(input);
            Scene created = new Scene(root);
            scene.set(created);
            field.set(input);
            ModuleI18n.bind(root, java.util.Map.of("Input:", "module.common.input"));
            input.requestFocus();
        });
        assertSame(field.get(), scene.get().getFocusOwner());

        runAndWait(() -> I18nService.getInstance().setPreference(LanguagePreference.ES));
        runAndWait(() -> { });
        assertSame(field.get(), scene.get().getFocusOwner());

        AtomicReference<Alert> alert = new AtomicReference<>();
        AtomicReference<FileChooser> chooser = new AtomicReference<>();
        runAndWait(() -> {
            String technical = "AEADBadTagException: Tag mismatch";
            alert.set(LocalizedDialogSupport.alert(Alert.AlertType.ERROR,
                    "dialog.validation.title", null, technical, ButtonType.OK));
            chooser.set(LocalizedDialogSupport.fileChooser(
                    "dialog.file.load", "dialog.exportHistory.filter", "JSON Files", "*.json"));
        });
        assertEquals("Error de validación", alert.get().getTitle());
        assertEquals("AEADBadTagException: Tag mismatch", alert.get().getContentText());
        assertEquals("Cargar archivo", chooser.get().getTitle());
        assertEquals("Archivos JSON", chooser.get().getExtensionFilters().get(0).getDescription());
    }

    private static void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
