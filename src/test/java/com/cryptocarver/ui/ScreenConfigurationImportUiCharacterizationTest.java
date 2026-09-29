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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ScreenConfigurationImportUiCharacterizationTest {
    private static boolean toolkitReady;
    private String previousHome;
    private String previousTestMode;
    private AppSettings previousSettings;
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
        previousSettings = AppSettings.getInstance();
        System.setProperty("user.home", Path.of("target", "test-home").toAbsolutePath().toString());
        System.setProperty("test.mode", "true");
        Path settingsFile = Path.of(System.getProperty("user.home"), ".cryptocarver", "settings.json");
        AppSettings.setInstanceForTesting(new AppSettings(settingsFile));
    }

    @AfterEach
    void restoreSettingsAndProperties() {
        AppSettings.setInstanceForTesting(previousSettings);
        restoreProperty("user.home", previousHome);
        restoreProperty("test.mode", previousTestMode);
    }

    @Test
    void wrongPasswordForValidEncryptedFileShowsSpecificRetryNotice() throws Exception {
        ScreenConfiguration configuration = sample();
        Path file = write("valid.ccconfig", ScreenConfigurationCodec.encodeEncrypted(configuration, "SYNTHETIC-PASSWORD".toCharArray()));
        String output = importAndCapture(file, "SYNTHETIC-WRONG");
        assertSpecific(output, "dialog.configuration.importFailure.wrongPasswordOrTampered");
    }

    @Test
    void modifiedEncryptedFileShowsSpecificRetryNotice() throws Exception {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray());
        Path file = write("modified.ccconfig", modifyCiphertext(encrypted));
        String output = importAndCapture(file, "SYNTHETIC-PASSWORD");
        assertSpecific(output, "dialog.configuration.importFailure.wrongPasswordOrTampered");
    }

    @Test
    void nonJsonFileShowsSpecificFailureNotice() throws Exception {
        Path file = write("text.json", "not json");
        assertSpecific(importAndCapture(file, null), "dialog.configuration.importFailure.notAConfiguration");
    }

    @Test
    void unrelatedJsonShowsSpecificFailureNotice() throws Exception {
        Path file = write("other.json", "{\"hello\":\"world\"}");
        assertSpecific(importAndCapture(file, null), "dialog.configuration.importFailure.notAConfiguration");
    }

    @Test
    void unsupportedVersionShowsSpecificFailureNotice() throws Exception {
        String json = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 99");
        assertSpecific(importAndCapture(write("future.json", json), null), "dialog.configuration.importFailure.unsupportedVersion");
    }

    @Test
    void emptyFileShowsSpecificFailureNotice() throws Exception {
        assertSpecific(importAndCapture(write("empty.json", ""), null), "dialog.configuration.importFailure.empty");
    }

    @Test
    void unsupportedEncryptedVersionIsReportedBeforePasswordPrompt() throws Exception {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray())
                .replaceFirst("\"version\": 1", "\"version\": 99");
        AtomicInteger prompts = new AtomicInteger();
        ImportOutcome outcome = runImport(write("future.ccconfig", encrypted), confirmation -> {
            prompts.incrementAndGet();
            return Optional.of("SYNTHETIC-PASSWORD".toCharArray());
        }, buttons -> Optional.empty());
        assertEquals(0, prompts.get());
        assertSpecific(String.join("\n", outcome.events()), "dialog.configuration.importFailure.unsupportedVersion");
    }

    @Test
    void validPlainDocumentReachesExistingReviewDialog() throws Exception {
        Path plain = write("plain.json", ScreenConfigurationCodec.encodePlain(sample()));
        assertTrue(importAndCapture(plain, null).contains("SHOW_DIALOG:"));
    }

    @Test
    void validEncryptedDocumentReachesExistingReviewDialog() throws Exception {
        Path encrypted = write("encrypted.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray()));
        assertTrue(importAndCapture(encrypted, "SYNTHETIC-PASSWORD").contains("SHOW_DIALOG:"));
    }

    @Test
    void wrongPasswordCanBeRetriedWithTheSameSelectedFile() throws Exception {
        Path file = write("retry.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray()));
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger responses = new AtomicInteger();
        ImportOutcome outcome = runImport(file, confirmation -> Optional.of(
                        (prompts.getAndIncrement() == 0 ? "SYNTHETIC-WRONG" : "SYNTHETIC-PASSWORD").toCharArray()),
                buttons -> responses.getAndIncrement() == 0 ? Optional.of(buttons[1]) : Optional.empty());
        assertEquals(2, prompts.get());
        assertEquals(2, responses.get());
        assertEquals(outcome.activeOperationBefore(), outcome.activeOperationAfter());
        assertEquals(2, outcome.events().stream().filter(event -> event.startsWith("SHOW_DIALOG:")).count());
    }

    @Test
    void wrongPasswordStopsAfterThreeAttempts() throws Exception {
        Path file = write("three-attempts.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray()));
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger responses = new AtomicInteger();
        ImportOutcome outcome = runImport(file, confirmation -> {
            prompts.incrementAndGet();
            return Optional.of("SYNTHETIC-WRONG".toCharArray());
        }, buttons -> responses.getAndIncrement() < 2 ? Optional.of(buttons[1]) : Optional.empty());
        assertEquals(3, prompts.get());
        assertEquals(2, responses.get());
        assertTrue(outcome.events().stream().anyMatch(event -> event.startsWith("SHOW_ERROR:")));
        assertEquals(outcome.activeOperationBefore(), outcome.activeOperationAfter());
    }

    @Test
    void validPlainImportAppliesConfigurationAfterReview() throws Exception {
        ScreenConfiguration configuration = configurationWithMode("GCM");
        Path file = write("apply-plain.json", ScreenConfigurationCodec.encodePlain(configuration));
        ImportOutcome outcome = runImport(file, confirmation -> Optional.empty(), buttons -> Optional.of(buttons[1]));
        assertEquals("Symmetric Ciphers", outcome.activeOperationAfter());
        assertEquals("GCM", outcome.cipherModeAfter());
        assertEquals(1, outcome.fileSelections());
    }

    @Test
    void validEncryptedImportAppliesConfigurationAfterReview() throws Exception {
        ScreenConfiguration configuration = configurationWithMode("CTR");
        Path file = write("apply-encrypted.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(configuration, "SYNTHETIC-PASSWORD".toCharArray()));
        ImportOutcome outcome = runImport(file, confirmation -> Optional.of("SYNTHETIC-PASSWORD".toCharArray()),
                buttons -> Optional.of(buttons[1]));
        assertEquals("Symmetric Ciphers", outcome.activeOperationAfter());
        assertEquals("CTR", outcome.cipherModeAfter());
        assertEquals(1, outcome.fileSelections());
    }

    @Test
    void successfulRetryAppliesTheSelectedFileAfterReview() throws Exception {
        ScreenConfiguration configuration = configurationWithMode("OFB");
        Path file = write("retry-apply.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(configuration, "SYNTHETIC-PASSWORD".toCharArray()));
        AtomicInteger prompts = new AtomicInteger();
        AtomicInteger selections = new AtomicInteger();
        ImportOutcome outcome = runImport(file, confirmation -> Optional.of(
                        (prompts.getAndIncrement() == 0 ? "SYNTHETIC-WRONG" : "SYNTHETIC-PASSWORD").toCharArray()),
                buttons -> selections.getAndIncrement() == 0 ? Optional.of(buttons[1]) : Optional.of(buttons[1]));
        assertEquals(2, prompts.get());
        assertEquals(2, selections.get());
        assertEquals("Symmetric Ciphers", outcome.activeOperationAfter());
        assertEquals("OFB", outcome.cipherModeAfter());
        assertEquals(1, outcome.fileSelections());
    }

    @Test
    void invalidFieldsFromAnotherRouteDoNotNavigateOrRestoreAnything() throws Exception {
        onFx(() -> {
            FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
            loader.load();
            ModernMainController main = loader.getController();
            String before = (String) readField(main, "currentActiveOperation");
            ScreenConfigurationCoordinator coordinator = main.screenConfigurationCoordinator();
            ScreenConfiguration invalid = new ScreenConfiguration("Symmetric Ciphers", "CIPHER",
                    Map.of("CipherController.cipherInputArea", "SYNTHETIC-MUST-NOT-APPLY",
                            "Unexpected.syntheticField", "SYNTHETIC-UNKNOWN"), SecretVisibilityProfile.FULL_LAB);
            assertThrows(IllegalArgumentException.class, () -> coordinator.applyScreenConfiguration(invalid));
            assertEquals(before, readField(main, "currentActiveOperation"));
            return null;
        });
    }

    @Test
    void cancellingWrongPasswordNoticeEndsImportWithoutChanges() throws Exception {
        Path file = write("cancel-notice.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray()));
        AtomicInteger prompts = new AtomicInteger();
        ImportOutcome outcome = runImport(file, confirmation -> {
            prompts.incrementAndGet();
            return Optional.of("SYNTHETIC-WRONG".toCharArray());
        }, buttons -> Optional.empty());
        assertEquals(1, prompts.get());
        assertEquals(1, outcome.events().size());
        assertEquals(outcome.activeOperationBefore(), outcome.activeOperationAfter());
    }

    @Test
    void cancellingPasswordPromptEndsImportWithoutChanges() throws Exception {
        Path file = write("cancel-password.ccconfig",
                ScreenConfigurationCodec.encodeEncrypted(sample(), "SYNTHETIC-PASSWORD".toCharArray()));
        ImportOutcome outcome = runImport(file, confirmation -> Optional.empty(), buttons -> Optional.empty());
        assertTrue(outcome.events().isEmpty());
        assertEquals(outcome.activeOperationBefore(), outcome.activeOperationAfter());
    }

    @Test
    void cancellingReviewDialogDoesNotApplyConfiguration() throws Exception {
        Path file = write("cancel-review.json", ScreenConfigurationCodec.encodePlain(sample()));
        ImportOutcome outcome = runImport(file, confirmation -> Optional.empty(), buttons -> Optional.empty());
        assertEquals(1, outcome.events().size());
        assertTrue(outcome.events().get(0).startsWith("SHOW_DIALOG:"));
        assertEquals(outcome.activeOperationBefore(), outcome.activeOperationAfter());
    }

    private String importAndCapture(Path file, String password) throws Exception {
        return String.join("\n", runImport(file, confirmation -> Optional.ofNullable(password)
                .map(String::toCharArray), buttons -> Optional.empty()).events());
    }

    private ImportOutcome runImport(Path file, Function<Boolean, Optional<char[]>> passwordPrompt,
                                    Function<javafx.scene.control.ButtonType[], Optional<javafx.scene.control.ButtonType>> selection)
            throws Exception {
        List<String> events = new ArrayList<>();
        AtomicReference<String> before = new AtomicReference<>();
        AtomicReference<String> after = new AtomicReference<>();
        AtomicReference<String> cipherInput = new AtomicReference<>();
        AtomicInteger fileSelections = new AtomicInteger();
        onFx(() -> {
            FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
            loader.load();
            ModernMainController main = loader.getController();
            before.set((String) readField(main, "currentActiveOperation"));
            ScreenConfigurationCoordinator coordinator = main.screenConfigurationCoordinator();
            coordinator.setImportFileSupplierForTesting(() -> {
                fileSelections.incrementAndGet();
                return file.toFile();
            });
            coordinator.setPasswordPromptForTesting(passwordPrompt);
            coordinator.setDialogObserverForTesting(events::add);
            coordinator.setDialogSelectionForTesting(selection);
            coordinator.importScreenConfiguration();
            after.set((String) readField(main, "currentActiveOperation"));
            Object cipher = readField(main, "cipherContainerController");
            if (cipher != null) {
                javafx.scene.control.ComboBox<?> mode = (javafx.scene.control.ComboBox<?>) readField(cipher, "cipherModeCombo");
                cipherInput.set((String) mode.getValue());
            }
            return null;
        });
        return new ImportOutcome(List.copyOf(events), before.get(), after.get(), cipherInput.get(), fileSelections.get());
    }


    private Object readField(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private ScreenConfiguration configurationWithMode(String mode) {
        return new ScreenConfiguration("Symmetric Ciphers", "CIPHER",
                Map.of("CipherController.cipherModeCombo", mode), SecretVisibilityProfile.FULL_LAB);
    }

    private record ImportOutcome(List<String> events, String activeOperationBefore, String activeOperationAfter,
                                 String cipherModeAfter, int fileSelections) { }

    private void assertSpecific(String output, String key) {
        assertTrue(output.contains(com.cryptocarver.service.I18nService.getInstance().text(key)), output);
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
