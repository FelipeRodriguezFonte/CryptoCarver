package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.CommandItem;
import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Characterizes Batch Runner record operations through the real application shell. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class BatchRecordUiCharacterizationUITest {
    private static final String KEY = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
    private static final String PLAIN_ONE = "toy-record-one-731";
    private static final String PLAIN_TWO = "toy-record-two-946";

    @TempDir Path tempDir;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @Test
    void recordOperationsAreReachableAndSafeThroughTheRealShell() throws Exception {
        AppSettings settings = AppSettings.getInstance();
        SecretVisibilityProfile oldVisibility = settings.getSecretVisibilityProfile();
        LanguagePreference oldLanguage = settings.getLanguagePreference();
        String oldRoute = settings.getLastRoute();
        ClipboardShelfManager shelf = ClipboardShelfManager.getInstance();
        List<ClipboardEntry> oldShelf = shelf.getEntries();
        List<String> transcript = new ArrayList<>();
        AtomicReference<ModernMainController> shellRef = new AtomicReference<>();
        AtomicReference<GenericController> genericRef = new AtomicReference<>();
        AtomicReference<CapturingReporter> reporterRef = new AtomicReference<>();
        HistoryManager isolatedHistory = new HistoryManager(tempDir.resolve("batch-history.json"));

        try {
            onFx(() -> {
                try {
                    FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                    Parent root = loader.load();
                    ModernMainController shell = loader.getController();
                    setField(shell, "historyManager", isolatedHistory);
                    GenericController generic = field(shell, "genericContainerController", GenericController.class);
                    CapturingReporter reporter = new CapturingReporter(shell);
                    generic.setStatusReporter(reporter);
                    javafx.stage.Stage stage = new javafx.stage.Stage();
                    stage.setScene(new javafx.scene.Scene(root));
                    stage.show();
                    shellRef.set(shell);
                    genericRef.set(generic);
                    reporterRef.set(reporter);
                } catch (Exception error) { throw new RuntimeException(error); }
            });

            ModernMainController shell = shellRef.get();
            GenericController generic = genericRef.get();
            onFx(() -> {
                TextField search = field(shell, "commandSearchField", TextField.class);
                ListView<CommandItem> results = field(shell, "commandResultsListView", ListView.class);
                openPaletteWithShortcut(search);
                search.setText("Encrypt Record (Batch)");
                CommandItem encrypt = results.getItems().stream()
                        .filter(item -> "batch_encrypt_record".equals(item.getId())).findFirst().orElseThrow();
                transcript.add("palette encrypt " + encrypt.getTitle());
                pressEnter(search);
                transcript.add("selected " + field(generic, "batchOperationCombo", ComboBox.class).getValue());

                openPaletteWithShortcut(search);
                search.setText("Decrypt Record (Batch)");
                CommandItem decrypt = results.getItems().stream()
                        .filter(item -> "batch_decrypt_record".equals(item.getId())).findFirst().orElseThrow();
                transcript.add("palette decrypt " + decrypt.getTitle());
                pressEnter(search);
                transcript.add("selected " + field(generic, "batchOperationCombo", ComboBox.class).getValue());
                generic.setStatusReporter(reporterRef.get());
            });

            onFx(() -> run(generic, "Encrypt Record", "{\"input\":\"" + PLAIN_ONE + "\"}\n{\"input\":\"" + PLAIN_TWO + "\"}\n", KEY));
            awaitBatch(generic);
            AtomicReference<List<String>> encryptedRef = new AtomicReference<>();
            onFx(() -> {
                var report = generic.lastBatchReport();
                assertNotNull(report);
                assertEquals(2, report.succeeded());
                List<String> encrypted = report.results().stream()
                        .map(row -> row.output().get("result")).toList();
                encryptedRef.set(encrypted);
                transcript.add("encrypt 2 records; random outputs normalized");
            });

            onFx(() -> run(generic, "Decrypt Record", jsonLines(encryptedRef.get()), KEY));
            awaitBatch(generic);
            onFx(() -> {
                var report = generic.lastBatchReport();
                assertNotNull(report);
                assertEquals(2, report.succeeded());
                assertEquals(List.of(PLAIN_ONE, PLAIN_TWO), report.results().stream()
                        .map(row -> row.output().get("result")).toList());
                transcript.add("decrypt round trip " + PLAIN_ONE + "," + PLAIN_TWO);
            });

            for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                settings.setLanguagePreference(language);
                I18nService.getInstance().refreshFromSettings();
                onFx(() -> run(generic, "Decrypt Record", "{\"input\":\"\"}\n", KEY));
                awaitBatch(generic);
                onFx(() -> {
                    var row = generic.lastBatchReport().results().get(0);
                    String error = row.error();
                    String expected = I18nService.getInstance().text("module.batch.invalidRecord", 1);
                    assertEquals(expected, error);
                    transcript.add(language + " empty record: " + error);
                });

                onFx(() -> run(generic, "Decrypt Record", "{\"input\":\"not-used\"}\n", "00"));
                onFx(() -> {
                    String error = reporterRef.get().lastError;
                    String expected = I18nService.getInstance().text("module.batch.invalidCryptoParameters",
                            I18nService.getInstance().text("module.batch.keyLengthInvalid"));
                    assertEquals(expected, error);
                    transcript.add(language + " invalid key: " + error);
                });
            }

            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                settings.setSecretVisibilityProfile(profile);
                isolatedHistory.clearHistory();
                onFx(() -> run(generic, "Decrypt Record", jsonLines(encryptedRef.get()), KEY));
                awaitBatch(generic);
                onFx(() -> {
                    String status = field(shell, "statusLabel", javafx.scene.control.Label.class).getText();
                    String published = reporterRef.get().publishedText();
                    String history = historyText(isolatedHistory.getHistoryItems());
                    String shelfText = shelfText(shelf.getEntries());
                    if (profile != SecretVisibilityProfile.FULL_LAB) {
                        for (String secret : List.of(KEY, PLAIN_ONE, PLAIN_TWO)) {
                            assertFalse(status.contains(secret), profile + " status leaked a secret");
                            assertFalse(published.contains(secret), profile + " publication leaked a secret");
                            assertFalse(history.contains(secret), profile + " history leaked '" + secret + "': " + history);
                            assertFalse(shelfText.contains(secret), profile + " shelf leaked a secret");
                        }
                    }
                    transcript.add(profile + " metadata-only history/status; shelf unchanged");
                });
            }

            assertEquals(oldShelf, shelf.getEntries(), "Batch record operations must not modify the Shelf");
            String sha = digest(transcript);
            assertEquals("f267c69e17352e3284685ca7e637812d220e48d259c311bb82e9631241fe272b", sha,
                    String.join("\n", transcript));
        } finally {
            if (shellRef.get() != null) onFx(() -> {
                javafx.stage.Window window = field(shellRef.get(), "rootStackPane", javafx.scene.Node.class).getScene().getWindow();
                window.hide();
            });
            isolatedHistory.clearHistory();
            // The isolated history only contains test records, but explicitly clear the Shelf snapshot
            // check prevents a future implementation from writing there as a side effect.
            settings.setSecretVisibilityProfile(oldVisibility);
            settings.setLanguagePreference(oldLanguage);
            I18nService.getInstance().refreshFromSettings();
            settings.setLastRoute(oldRoute);
        }
    }

    private static void run(GenericController generic, String operation, String input, String key) {
        field(generic, "batchOperationCombo", ComboBox.class).setValue(operation);
        field(generic, "batchInputFormatCombo", ComboBox.class).setValue("JSON Lines (.jsonl)");
        field(generic, "batchInputArea", TextArea.class).setText(input);
        field(generic, "batchColumnField", TextField.class).setText("input");
        field(generic, "batchOutputColumnField", TextField.class).setText("result");
        field(generic, "batchKeyField", javafx.scene.control.PasswordField.class).setText(key);
        field(generic, "batchAlgorithmCombo", ComboBox.class).setValue("AES-256-GCM");
        field(generic, "batchStopOnErrorCheck", javafx.scene.control.CheckBox.class).setSelected(false);
        field(generic, "batchIvNonceField", TextField.class).clear();
        field(generic, "batchAadField", TextField.class).clear();
        generic.handleRunBatch();
    }

    private static String jsonLines(List<String> inputs) {
        return inputs.stream().map(value -> "{\"input\":\"" + value + "\"}").reduce((a, b) -> a + "\n" + b).orElse("") + "\n";
    }

    private static void openPaletteWithShortcut(TextField search) {
        javafx.scene.Scene scene = search.getScene();
        Runnable shortcut = scene.getAccelerators().get(
                new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN));
        assertNotNull(shortcut, "The real ⌘K / Ctrl+K palette shortcut must be installed");
        shortcut.run();
    }

    private static void pressEnter(TextField search) {
        search.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                false, false, false, false));
    }

    private static String historyText(List<HistoryCommand> history) {
        return history.stream().map(item -> item.getOperation() + "|" + item.getDetails() + "|"
                + item.getParameters() + "|" + item.getStructuredDetails()).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String shelfText(List<ClipboardEntry> entries) {
        return entries.stream().map(item -> item.getLabel() + "|" + item.getValue() + "|" + item.getNote())
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static void awaitBatch(GenericController generic) throws Exception {
        for (int index = 0; index < 300; index++) {
            AtomicReference<javafx.concurrent.Task<?>> task = new AtomicReference<>();
            onFx(() -> task.set(generic.activeBatchTask()));
            if (task.get() == null) return;
            try { task.get().get(50, TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.TimeoutException ignored) { }
            catch (java.util.concurrent.ExecutionException error) { throw new AssertionError(error.getCause()); }
        }
        throw new AssertionError("Batch did not finish");
    }

    private static String digest(List<String> transcript) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8)));
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void setField(Object owner, String name, Object value) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "FX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static final class CapturingReporter implements StatusReporter {
        private final StatusReporter delegate;
        private final List<String> published = new ArrayList<>();
        private String lastError;
        private CapturingReporter(StatusReporter delegate) { this.delegate = delegate; }
        @Override public void updateStatus(String message) { delegate.updateStatus(message); }
        @Override public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
            delegate.updateInspector(operation, input, output, details);
        }
        @Override public void showError(String title, String message) { lastError = message; }
        @Override public void publish(OperationResult result) {
            published.add(result.toString() + result.getDetails().toString());
            delegate.publish(result);
        }
        String publishedText() { return published.toString(); }
    }
}
