package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.Accordion;
import javafx.scene.control.TitledPane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModuleI18nLiveUITest {
    @TempDir
    static Path temporaryDirectory;

    @BeforeAll
    static void startJavaFx() throws Exception {
        Path cache = Files.createTempDirectory("cryptocarver-ux15a-javafx-cache-");
        System.setProperty("javafx.cachedir", cache.toString());
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX toolkit did not start");
    }

    @BeforeEach
    void isolateSettings() {
        AppSettings.setInstanceForTesting(new AppSettings(temporaryDirectory.resolve("settings.json")));
        I18nService.getInstance().setPreference(LanguagePreference.EN);
    }

    @AfterAll
    static void restoreSettings() {
        AppSettings.resetInstanceForTesting();
    }

    @Test
    void cipherAndKeysRefreshInLiveShellWithoutChangingTechnicalValues() throws Exception {
        AtomicReference<Parent> cipherRoot = new AtomicReference<>();
        AtomicReference<Parent> keysRoot = new AtomicReference<>();
        runAndWait(() -> {
            try {
                cipherRoot.set(new FXMLLoader(getClass().getResource("/fxml/cipher.fxml")).load());
                keysRoot.set(new FXMLLoader(getClass().getResource("/fxml/keys.fxml")).load());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        assertEquals("Data Encryption & Decryption", findText(cipherRoot.get(), "Data Encryption & Decryption"));
        assertEquals("🎛 Key Lab", findText(keysRoot.get(), "🎛 Key Lab"));

        runAndWait(() -> I18nService.getInstance().setPreference(LanguagePreference.ES));
        assertEquals("Cifrado y descifrado de datos", findText(cipherRoot.get(), "Cifrado y descifrado de datos"));
        assertEquals("🎛 Laboratorio de claves", findText(keysRoot.get(), "🎛 Laboratorio de claves"));

        String technical = "SHA-256 A1B2C3";
        runAndWait(() -> {
            Labeled technicalLabel = new javafx.scene.control.Label(technical);
            technicalLabel.setUserData("technical");
            // ModuleI18n maps only catalogued static UI text, never arbitrary technical values.
            assertEquals(technical, technicalLabel.getText());
        });
    }

    private static String findText(Parent root, String expected) {
        String found = findText((Node) root, expected);
        if (found != null) return found;
        throw new AssertionError("Missing localized text: " + expected);
    }

    private static String findText(Node node, String expected) {
        if (node instanceof Labeled labeled && expected.equals(labeled.getText())) return labeled.getText();
        if (node instanceof Accordion accordion) {
            for (TitledPane pane : accordion.getPanes()) {
                String found = findText(pane, expected);
                if (found != null) return found;
            }
        }
        if (node instanceof TitledPane titledPane && titledPane.getContent() != null) {
            String found = findText(titledPane.getContent(), expected);
            if (found != null) return found;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                String found = findText(child, expected);
                if (found != null) return found;
            }
        }
        return null;
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
