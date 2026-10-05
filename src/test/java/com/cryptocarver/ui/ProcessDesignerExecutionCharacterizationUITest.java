package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Pins Process Designer execution, dry-run, palette filtering, and supplied-secret visibility. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerExecutionCharacterizationUITest {
    private static final String SYNTHETIC_KEY = "00112233445566778899AABBCCDDEEFF";
    private static final String PUBLIC_PAYLOAD = "process-designer-public-payload";

    @TempDir Path tempDir;
    private AppSettings originalSettings;
    private HistoryManager isolatedHistory;
    private ClipboardShelfManager shelf;
    private List<ClipboardEntry> originalShelf;
    private final List<Stage> fixtureStages = new ArrayList<>();

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "JavaFX toolkit must start");
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void isolateApplicationState() {
        originalSettings = AppSettings.getInstance();
        AppSettings.setInstanceForTesting(new AppSettings(tempDir.resolve("settings.json")));
        AppSettings.getInstance().setLanguagePreference(LanguagePreference.EN);
        I18nService.getInstance().refreshFromSettings();
        isolatedHistory = new HistoryManager(tempDir.resolve("history.json"));
        shelf = ClipboardShelfManager.getInstance();
        originalShelf = new ArrayList<>(shelf.getEntries());
    }

    @AfterEach
    void restoreApplicationState() throws Exception {
        onFx(() -> {
            for (Window window : new ArrayList<>(Window.getWindows())) {
                if (window instanceof Stage stage && (fixtureStages.contains(stage)
                        || "Expanded Result — Process Designer".equals(stage.getTitle()))) {
                    stage.close();
                }
            }
            fixtureStages.clear();
        });
        if (isolatedHistory != null) isolatedHistory.clearHistory();
        if (shelf != null && originalShelf != null && !originalShelf.equals(shelf.getEntries())) {
            shelf.clear();
            for (int index = originalShelf.size() - 1; index >= 0; index--) shelf.addEntry(originalShelf.get(index));
        }
        AppSettings.setInstanceForTesting(originalSettings);
        I18nService.getInstance().refreshFromSettings();
    }

    @Test
    void completeRunDryRunAndPaletteHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        Fixture fixture = onFx(this::openFixture);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            onFx(() -> {
                VBox palette = (VBox) fixture.scene.lookup("#paletteItemsContainer");
                TextField search = (TextField) fixture.scene.lookup("#paletteSearchField");
                int totalCards = paletteCards(palette);
                search.setText("Hash");
                int hashCards = paletteCards(palette);
                search.setText("");
                assertEquals(NodeCatalog.descriptors().size(), totalCards);
                assertTrue(hashCards > 0 && hashCards < totalCards);
                transcript.add("palette cards=" + totalCards + " hash-filter=" + hashCards + " restored=" + paletteCards(palette));

                fixture.controller.handleLoadSha256Preset();
                fixture.controller.onExecutionFinished = finished::countDown;
                fixture.controller.handleRunProcess();
            });
            assertTrue(finished.await(20, TimeUnit.SECONDS), "complete execution must finish");
            onFx(() -> {
                String output = normalizeTrace(((TextArea) fixture.scene.lookup("#executionOutputArea")).getText());
                TableView<ProcessExecutionRow> table = (TableView<ProcessExecutionRow>) fixture.scene.lookup("#executionStatusTable");
                Label status = (Label) fixture.scene.lookup("#processStatusLabel");
                ProgressBar progress = (ProgressBar) fixture.scene.lookup("#processProgressBar");
                List<String> rows = table.getItems().stream()
                        .map(row -> row.getOperation() + "/" + row.getStatus()).toList();
                assertEquals("Completed successfully", status.getText());
                assertEquals(1.0, progress.getProgress(), 0.001);
                assertEquals(3, table.getItems().size());
                assertTrue(output.contains("Hello, CryptoForge"));
                transcript.add("run status=" + status.getText() + " rows=" + rows + " progress=" + progress.getProgress()
                        + " trace=" + output);

                fixture.controller.handleDryRunProcess();
                String dryOutput = normalizeDryRun(((TextArea) fixture.scene.lookup("#executionOutputArea")).getText());
                List<String> dryRows = table.getItems().stream()
                        .map(row -> row.getOperation() + "/" + row.getStatus()).toList();
                assertTrue(dryOutput.contains("PROCESS DESIGNER DRY RUN"));
                assertEquals(3, table.getItems().size());
                assertTrue(dryRows.stream().allMatch(row -> row.endsWith("/READY")), dryRows.toString());
                transcript.add("dry status=" + status.getText() + " rows=" + dryRows + " trace=" + dryOutput);
            });
        } finally {
            closeFixture(fixture);
        }
        assertTranscript("b925165010bd8ff31c1645f094b3a8073ad12bfa834347cb91212425f987afd3", transcript);
    }

    @Test
    void suppliedSecretsStayProtectedAcrossProfilesAndPreflight() throws Exception {
        List<String> transcript = new ArrayList<>();
        Fixture fixture = onFx(this::openFixture);
        try {
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                onFx(() -> configureEncryptionFixture(fixture));
                CountDownLatch finished = new CountDownLatch(1);
                ConcurrentLinkedQueue<NodeExecutionEvent> events = new ConcurrentLinkedQueue<>();
                onFx(() -> {
                    fixture.controller.onNodeExecutionEvent = events::add;
                    fixture.controller.onExecutionFinished = finished::countDown;
                    fixture.controller.handleRunProcess();
                });
                assertTrue(finished.await(20, TimeUnit.SECONDS), "execution must finish under " + profile);
                onFx(() -> {
                    String trace = ((TextArea) fixture.scene.lookup("#executionOutputArea")).getText();
                    Label status = (Label) fixture.scene.lookup("#processStatusLabel");
                    TableView<ProcessExecutionRow> table = (TableView<ProcessExecutionRow>) fixture.scene.lookup("#executionStatusTable");
                    PasswordField keyField = assertInstanceOf(PasswordField.class, fixture.controller.getInspectorControl("key"));
                    String displayedPassword = renderedPassword(keyField);
                    fixture.controller.handleOpenExpandedExecutionResult();
                    String expanded = expandedText();
                    boolean keyInTelemetry = events.stream().map(NodeExecutionEvent::safeMessage)
                            .filter(message -> message != null).anyMatch(message -> message.contains(SYNTHETIC_KEY));
                    boolean keyInRows = table.getItems().stream().anyMatch(row ->
                            (row.getResultValue() != null && row.getResultValue().toString().contains(SYNTHETIC_KEY))
                                    || row.getStepName().contains(SYNTHETIC_KEY));
                    assertEquals(3, table.getItems().size());
                    assertTrue(table.getItems().stream().allMatch(row -> "SUCCESS".equals(row.getStatus())));
                    assertTrue(keyField.getText().equals(SYNTHETIC_KEY), "the synthetic value remains supplied in the password control");
                    assertFalse(displayedPassword.contains(SYNTHETIC_KEY), "PasswordField skin must mask the supplied value");
                    assertFalse(status.getText().contains(SYNTHETIC_KEY));
                    assertFalse(keyInTelemetry);
                    assertFalse(keyInRows);
                    assertFalse(String.valueOf(field(field(fixture.controller, "undoRedoCoordinator"), "undoStack")).contains(SYNTHETIC_KEY));
                    assertFalse(String.valueOf(field(field(fixture.controller, "undoRedoCoordinator"), "redoStack")).contains(SYNTHETIC_KEY));
                    assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                    assertEquals(originalShelf, shelf.getEntries());
                    if (profile == SecretVisibilityProfile.FULL_LAB) {
                        assertTrue(trace.contains(SYNTHETIC_KEY));
                        assertTrue(expanded.contains(SYNTHETIC_KEY));
                    } else {
                        assertFalse(trace.contains(SYNTHETIC_KEY));
                        assertFalse(expanded.contains(SYNTHETIC_KEY));
                    }
                    if (profile == SecretVisibilityProfile.MASKED) {
                        assertTrue(trace.contains("key (HEX): ***MASKED***"));
                    } else if (profile == SecretVisibilityProfile.REDACTED) {
                        assertFalse(trace.contains("key (HEX)"));
                    }
                    transcript.add(profile + " run=SUCCESS rows=" + table.getItems().size()
                            + " inspector=password-masked status=clear table=clear telemetry=clear undo=clear"
                            + " trace=" + (trace.contains(SYNTHETIC_KEY) ? "visible" : profile == SecretVisibilityProfile.MASKED ? "masked" : "omitted")
                            + " expanded=" + (expanded.contains(SYNTHETIC_KEY) ? "visible" : "protected")
                            + " history=empty shelf=unchanged");
                });
            }

            AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);
            onFx(() -> {
                fixture.controller.handleClearCanvas();
                ProcessDefinition.Node cipher = addNode(fixture.controller, "ENCRYPT", "Synthetic preflight cipher");
                cipher.id = "preflight-cipher";
                cipher.configuration.put("algorithm", "NO-SUCH-CIPHER-FOR-CHARACTERIZATION");
                fixture.controller.transientSecrets.computeIfAbsent(cipher.id, ignored -> new java.util.HashMap<>())
                        .put("key", SYNTHETIC_KEY.toCharArray());
                ConcurrentLinkedQueue<NodeExecutionEvent> preflightEvents = new ConcurrentLinkedQueue<>();
                fixture.controller.onNodeExecutionEvent = preflightEvents::add;
                fixture.controller.onExecutionFinished = () -> fail("preflight must not start asynchronous execution");
                Label status = (Label) fixture.scene.lookup("#processStatusLabel");
                Button run = (Button) fixture.scene.lookup("#runProcessButton");
                Button cancel = (Button) fixture.scene.lookup("#cancelProcessButton");
                ProgressBar progress = (ProgressBar) fixture.scene.lookup("#processProgressBar");
                String previousStatus = status.getText();
                boolean previousRunDisabled = run.isDisable();
                boolean previousCancelDisabled = cancel.isDisable();
                double previousProgress = progress.getProgress();
                fixture.controller.handleRunProcess();
                TextArea output = (TextArea) fixture.scene.lookup("#executionOutputArea");
                TableView<ProcessExecutionRow> table = (TableView<ProcessExecutionRow>) fixture.scene.lookup("#executionStatusTable");
                assertEquals(1, table.getItems().size());
                assertEquals("ERROR", table.getItems().get(0).getStatus());
                assertEquals("PRE-FLIGHT", table.getItems().get(0).getOperation());
                assertTrue(output.getText().contains("failed"));
                assertFalse(output.getText().contains(SYNTHETIC_KEY));
                assertEquals(previousStatus, status.getText());
                assertEquals(previousRunDisabled, run.isDisable());
                assertEquals(previousCancelDisabled, cancel.isDisable());
                assertEquals(previousProgress, progress.getProgress(), 0.001);
                assertTrue(preflightEvents.isEmpty());
                assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                assertEquals(originalShelf, shelf.getEntries());
                transcript.add("preflight row=PRE-FLIGHT/ERROR status=unchanged controls=unchanged events=0 secret=clear-absent"
                        + " output=" + normalizeExceptionText(output.getText()) + " history=empty shelf=unchanged");
            });
        } finally {
            closeFixture(fixture);
        }
        assertTranscript("c8160b2b00c26b16ef19b4a43063f2c9685e058e112806dbbacf9f810596f5d1", transcript);
    }

    private void configureEncryptionFixture(Fixture fixture) {
        ProcessDesignerController controller = fixture.controller;
        controller.handleClearCanvas();
        ProcessDefinition.Node input = addNode(controller, "CONSOLE_INPUT", "Public synthetic input");
        ProcessDefinition.Node cipher = addNode(controller, "ENCRYPT", "Synthetic cipher");
        ProcessDefinition.Node output = addNode(controller, "CONSOLE_OUTPUT", "Synthetic output");
        input.id = "public-input";
        cipher.id = "synthetic-cipher";
        output.id = "synthetic-output";
        input.configuration.put("value", PUBLIC_PAYLOAD);
        controller.connections.add(new ProcessDefinition.Connection(input.id, cipher.id, "payload"));
        controller.connections.add(new ProcessDefinition.Connection(cipher.id, output.id, "input"));
        controller.select(cipher);
        ((PasswordField) controller.getInspectorControl("key")).setText(SYNTHETIC_KEY);
        ((CheckBox) controller.getInspectorControl("generateNonce")).setSelected(true);
        controller.handleSaveNodeSettings();
        controller.redraw();
        assertArrayEquals(SYNTHETIC_KEY.toCharArray(), controller.getTransientSecret(cipher.id, "key"));
        assertFalse(controller.toDefinition().nodes.stream().filter(node -> node.id.equals(cipher.id))
                .anyMatch(node -> node.configuration.containsKey("key")));
    }

    private Fixture openFixture() throws Exception {
        FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/process_designer.fxml"));
        TitledPane root = loader.load();
        Scene scene = new Scene(root, 1200, 800);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
        fixtureStages.add(stage);
        return new Fixture(loader.getController(), scene, stage);
    }

    private void closeFixture(Fixture fixture) throws Exception {
        onFx(fixture.stage::close);
        fixtureStages.remove(fixture.stage);
    }

    private static int paletteCards(VBox palette) {
        return (int) palette.getChildren().stream().filter(HBox.class::isInstance).count();
    }

    private static String renderedPassword(PasswordField field) {
        javafx.scene.Node renderedText = field.lookup(".text");
        return renderedText instanceof Text text ? text.getText() : "";
    }

    private static String expandedText() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && "Expanded Result — Process Designer".equals(stage.getTitle())
                    && stage.getScene() != null) {
                return stage.getScene().getRoot().lookupAll(".text-area").stream()
                        .filter(TextArea.class::isInstance).map(TextArea.class::cast)
                        .map(TextArea::getText).findFirst().orElse("");
            }
        }
        return "";
    }

    private static ProcessDefinition.Node addNode(ProcessDesignerController controller, String type, String label) {
        try {
            Method add = ProcessDesignerController.class.getDeclaredMethod("addNode", String.class, String.class,
                    double.class, double.class);
            add.setAccessible(true);
            return (ProcessDefinition.Node) add.invoke(controller, type, label, 100.0, 100.0);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Object field(Object instance, String name) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static String normalizeTrace(String value) {
        return value.replaceAll("\\(\\d+ ms\\)", "(<duration>)");
    }

    private static String normalizeDryRun(String value) {
        return normalizeExceptionText(value);
    }

    private static String normalizeExceptionText(String value) {
        return value.replaceAll("(?im)(?:[\\w.$]*Exception|IOException|[\\w.$]*Error)[^\\r\\n]*", "<jdk-exception>");
    }

    private static void assertTranscript(String expected, List<String> transcript) throws Exception {
        String joined = String.join("\n", transcript);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, actual, joined);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        CountDownLatch finished = new CountDownLatch(1);
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { result[0] = action.call(); }
            catch (Throwable error) { failure[0] = error; }
            finally { finished.countDown(); }
        });
        assertTrue(finished.await(15, TimeUnit.SECONDS), "timed out waiting for JavaFX thread");
        if (failure[0] instanceof Exception error) throw error;
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] != null) throw new RuntimeException(failure[0]);
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    private static void onFx(ThrowingRunnable action) throws Exception {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Fixture(ProcessDesignerController controller, Scene scene, Stage stage) { }
}
