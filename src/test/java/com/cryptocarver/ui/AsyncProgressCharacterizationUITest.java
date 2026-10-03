package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class AsyncProgressCharacterizationUITest {
    @TempDir Path directory;
    private AppSettings previousSettings;
    private List<ClipboardEntry> previousShelf;
    private ModernMainController shell;
    private HBox box;
    private Label label;
    private ProgressBar bar;
    private ProgressIndicator spinner;
    private Button cancel;
    private final List<String> rows = new ArrayList<>();

    @BeforeEach void load() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        previousSettings = AppSettings.getInstance();
        previousShelf = List.copyOf(ClipboardShelfManager.getInstance().getEntries());
        fx(() -> {
            Platform.setImplicitExit(false);
            AppSettings isolated = new AppSettings(directory.resolve("settings.json"));
            isolated.setLanguagePreference(LanguagePreference.EN);
            isolated.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
            AppSettings.setInstanceForTesting(isolated);
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            Parent root = loader.load(); shell = loader.getController();
            assertNotNull(root);
            box = field(shell, "asyncProgressBox"); label = field(shell, "asyncProgressLabel");
            bar = field(shell, "asyncProgressBar"); spinner = field(shell, "asyncProgressIndicator");
            cancel = field(shell, "asyncCancelBtn");
        });
        fx(() -> {}); // drain deferred startup
    }

    @AfterEach void restore() throws Exception {
        fx(() -> {
            if (shell != null) shell.shutdown();
            AppSettings.setInstanceForTesting(previousSettings);
            I18nService.getInstance().refreshFromSettings();
            assertEquals(previousShelf, ClipboardShelfManager.getInstance().getEntries());
        });
    }

    @Test void privacyNeverCopiesFreeTextOrNames() throws Exception {
        fx(() -> {
            for (LanguagePreference locale : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                I18nService.getInstance().setPreference(locale);
                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                    shell.updateAsyncProgressDetails(new OperationExecutor.ProgressDetails("SYNTHETIC_PRIVATE_NAME", 0, 0, 2000, "SYNTHETIC_PRIVATE_PAYLOAD"));
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    shell.showAsyncProgress("SYNTHETIC_PRIVATE_NAME");
                    assertSafe();
                    shell.updateAsyncProgressDetails(new OperationExecutor.ProgressDetails("SYNTHETIC_PRIVATE_NAME", 25, 100, 2000, "SYNTHETIC_PRIVATE_PAYLOAD"));
                    assertSafe();
                    shell.updateAsyncProgressDetails(new OperationExecutor.ProgressDetails("SYNTHETIC_PRIVATE_NAME", 0, 0, 2000, "SYNTHETIC_PRIVATE_PAYLOAD"));
                    assertSafe();
                }
            }
        });
    }

    private void assertSafe() {
        for (String text : Arrays.asList(label.getText(), label.getAccessibleText(), spinner.getAccessibleText(), bar.getAccessibleText())) {
            assertTrue(text == null || !text.contains("SYNTHETIC_PRIVATE"), "Private progress leaked: " + text);
        }
    }

    @Test void nextOperationStartsIndeterminateAfterCompletion() throws Exception {
        fx(() -> {
            shell.updateAsyncProgressDetails(details(100, 100)); shell.hideAsyncProgress();
            assertEquals(1, bar.getProgress()); assertFalse(box.isVisible());
            shell.showAsyncProgress("Next");
            assertFalse(bar.isVisible(), "Previous 100% bar remains visible on next operation");
            assertFalse(bar.isManaged()); assertTrue(spinner.isVisible()); assertTrue(spinner.isManaged());
            assertEquals(-1, spinner.getProgress());
        });
    }

    @Test void transcriptInEnglishAndSpanish() throws Exception {
        for (LanguagePreference locale : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
            fx(() -> {
                I18nService.getInstance().setPreference(locale);
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    shell.showAsyncProgress(""); snapshot(locale + "/" + profile + "/show");
                    shell.updateAsyncProgressDetails(details(25, 100)); snapshot("quarter");
                    shell.updateAsyncProgressDetails(details(150, 100)); snapshot("clamped");
                    shell.updateAsyncProgressDetails(details(0, 0)); snapshot("unknown");
                    shell.updateAsyncProgressDetails(null); snapshot("null");
                    shell.hideAsyncProgress(); snapshot("hidden");
                }
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
            });
            runOutcome("success", false);
            runOutcome("error", false);
            runOutcome("cancel", false);
            runOutcome("success", true);
        }
        String transcript = String.join("\n", rows) + "\n";
        Files.writeString(Path.of("target/async-progress-transcript.txt"), transcript);
        assertEquals("BASELINE_PENDING", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(transcript.getBytes(StandardCharsets.UTF_8))), transcript);
    }

    private void runOutcome(String outcome, boolean commit) throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        OperationExecutor executor = shell.getOperationExecutor();
        Button trigger = new Button();
        fx(() -> {
            String previousMode = System.getProperty("test.mode");
            System.setProperty("test.mode", "false");
            try { executor.executeWithProgress("Fixture", trigger, monitor -> {
            monitor.updateProgress(25, 100); if (commit) assertTrue(executor.enterCommitPhase());
            started.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) {
                if (!outcome.equals("cancel")) throw interrupted;
                assertTrue(release.await(10, TimeUnit.SECONDS));
                throw new CancellationException();
            }
            if (outcome.equals("error")) throw new IllegalStateException("synthetic failure");
            return "done";
        }, value -> finish("success", outcome, trigger, done, callbackError),
           error -> finish("error", outcome, trigger, done, callbackError),
           () -> finish("cancel", outcome, trigger, done, callbackError));
            } finally {
                if (previousMode == null) System.clearProperty("test.mode"); else System.setProperty("test.mode", previousMode);
            }
        });
        assertTrue(started.await(10, TimeUnit.SECONDS));
        try {
            // Observe actual 400ms threshold, not a guessed sleep.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!executor.isThresholdReached() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(executor.isThresholdReached());
            fx(() -> {
                assertTrue(box.isVisible()); assertTrue(trigger.isDisabled());
                // Stabilize snapshot values while retaining executor lifecycle coverage.
                shell.updateAsyncProgressDetails(details(25, 100));
                if (outcome.equals("cancel") || commit) {
                    shell.handleCancelAsyncOperation();
                    assertEquals(I18nService.getInstance().text(commit ? "progress.finishing" : "progress.cancelling"), label.getText());
                    rows.add("request=" + label.getText() + ";disabled=" + cancel.isDisabled());
                    assertEquals(commit, cancel.isDisabled());
                }
            });
        } finally { release.countDown(); }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (callbackError.get() != null) throw new AssertionError(callbackError.get());
        fx(() -> {
            assertEquals(OperationExecutor.State.IDLE, executor.getState());
            assertFalse(box.isVisible()); assertFalse(box.isManaged());
            assertFalse(trigger.isDisabled());
            assertEquals(outcome.equals("success") ? 1 : .25, bar.getProgress());
            rows.add("finish=" + outcome + ";commit=" + commit + ";box=" + box.isVisible() + "/" + box.isManaged()
                    + ";bar=" + bar.getProgress() + "/" + bar.isVisible() + "/" + bar.isManaged() + ";trigger=" + trigger.isDisabled());
            shell.handleCancelAsyncOperation(); assertFalse(box.isVisible());
        });
    }

    private void finish(String actual, String expected, Button trigger, CountDownLatch done, AtomicReference<Throwable> error) {
        try { assertEquals(expected, actual); assertFalse(box.isVisible()); assertFalse(trigger.isDisabled()); }
        catch (Throwable failure) { error.set(failure); } finally { done.countDown(); }
    }

    private OperationExecutor.ProgressDetails details(long processed, long total) {
        return new OperationExecutor.ProgressDetails("Fixture", processed, total, 2000,
                OperationExecutor.formatProgressText("Fixture", processed, total, 2000));
    }
    private void snapshot(String phase) {
        rows.add(phase + "|box=" + box.isVisible() + "/" + box.isManaged() + "|label=" + label.getText()
                + "|accessible=" + label.getAccessibleText() + "|bar=" + bar.getProgress() + "/" + bar.isVisible() + "/" + bar.isManaged()
                + "|bar.accessible=" + bar.getAccessibleText() + "|spinner=" + spinner.getProgress() + "/" + spinner.isVisible() + "/" + spinner.isManaged()
                + "|spinner.accessible=" + spinner.getAccessibleText() + "|cancel=" + cancel.isDisabled());
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) throws Exception {
        var field = ModernMainController.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(object);
    }
    @FunctionalInterface private interface FxAction { void run() throws Exception; }
    private static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); } });
        assertTrue(done.await(30, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
    }
}
