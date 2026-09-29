package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ScreenConfiguration;
import com.cryptocarver.model.ScreenConfigurationCodec;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ScreenConfigurationImportUiCharacterizationTest {
    private static boolean toolkitReady;
    private String previousHome;
    private String previousTestMode;
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
    void isolateSettingsAndTestMode() {
        previousHome = System.getProperty("user.home");
        previousTestMode = System.getProperty("test.mode");
        System.setProperty("user.home", Path.of("target", "test-home").toAbsolutePath().toString());
        System.setProperty("test.mode", "true");
        Path settingsFile = Path.of(System.getProperty("user.home"), ".cryptocarver", "settings.json");
        AppSettings.setInstanceForTesting(new AppSettings(settingsFile));
    }

    @AfterEach
    void restoreSettingsAndProperties() {
        AppSettings.resetInstanceForTesting();
        restoreProperty("user.home", previousHome);
        restoreProperty("test.mode", previousTestMode);
    }

    @Test
    void wrongPasswordForValidEncryptedFileShowsExistingGenericAlert() throws Exception {
        ScreenConfiguration configuration = sample();
        Path file = write("valid.ccconfig", ScreenConfigurationCodec.encodeEncrypted(configuration, "SYNTHETIC-PASSWORD".toCharArray()));
        String output = importAndCapture(file, "SYNTHETIC-WRONG");
        assertGenericError(output);
    }

    @Test
    void modifiedEncryptedFileShowsExistingGenericAlert() throws Exception {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray());
        Path file = write("modified.ccconfig", modifyCiphertext(encrypted));
        String output = importAndCapture(file, "SYNTHETIC-PASSWORD");
        assertGenericError(output);
    }

    @Test
    void nonJsonFileShowsExistingGenericAlert() throws Exception {
        Path file = write("text.json", "not json");
        assertGenericError(importAndCapture(file, null));
    }

    @Test
    void unrelatedJsonShowsExistingGenericAlert() throws Exception {
        Path file = write("other.json", "{\"hello\":\"world\"}");
        assertGenericError(importAndCapture(file, null));
    }

    @Test
    void unsupportedVersionShowsExistingGenericAlert() throws Exception {
        String json = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 99");
        assertGenericError(importAndCapture(write("future.json", json), null));
    }

    @Test
    void emptyFileShowsExistingGenericAlert() throws Exception {
        assertGenericError(importAndCapture(write("empty.json", ""), null));
    }

    @Test
    void validPlainAndEncryptedDocumentsReachExistingReviewDialog() throws Exception {
        Path plain = write("plain.json", ScreenConfigurationCodec.encodePlain(sample()));
        Path encrypted = write("encrypted.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray()));
        String plainOutput = importAndCapture(plain, null);
        assertTrue(plainOutput.contains("SHOW_DIALOG:"), plainOutput);
        String encryptedOutput = importAndCapture(encrypted, "SYNTHETIC-PASSWORD");
        assertTrue(encryptedOutput.contains("SHOW_DIALOG:"), encryptedOutput);
    }

    private String importAndCapture(Path file, String password) throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            onFx(() -> {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                loader.load();
                ModernMainController main = loader.getController();
                ScreenConfigurationCoordinator coordinator = main.screenConfigurationCoordinator();
                coordinator.setImportFileSupplierForTesting(() -> file.toFile());
                coordinator.setPasswordPromptForTesting(confirmation -> Optional.ofNullable(password)
                        .map(String::toCharArray));
                coordinator.importScreenConfiguration();
                return null;
            });
        } finally {
            System.setOut(previous);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    private void assertGenericError(String output) {
        assertTrue(output.contains("SHOW_ERROR: Configuration Import - The screen configuration could not be imported."), output);
    }

    private Path write(String name, String contents) throws Exception {
        Path path = temp.resolve(name);
        Files.writeString(path, contents);
        return path;
    }

    private ScreenConfiguration sample() {
        return new ScreenConfiguration("Symmetric Ciphers", "CIPHER", Map.of(), SecretVisibilityProfile.FULL_LAB);
    }

    private String modifyCiphertext(String encrypted) {
        String marker = "\"ciphertext\": \"";
        int start = encrypted.indexOf(marker) + marker.length();
        char original = encrypted.charAt(start);
        return encrypted.substring(0, start) + (original == 'A' ? 'B' : 'A') + encrypted.substring(start + 1);
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
        assertTrue(finished.await(30, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
