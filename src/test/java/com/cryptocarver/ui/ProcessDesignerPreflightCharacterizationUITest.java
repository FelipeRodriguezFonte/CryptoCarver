package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.NodeExecutionState;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.Representation;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes early Process Designer failures through the real UI and protects transient secrets. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerPreflightCharacterizationUITest {
    private static final String TEST_ONLY_SECRET = "PREFLIGHT_SYNTHETIC_SECRET_73";

    @TempDir Path tempDir;
    private AppSettings originalSettings;
    private HistoryManager isolatedHistory;
    private ClipboardShelfManager shelf;
    private List<ClipboardEntry> originalShelf;

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
        originalShelf = shelf.getEntries();
    }

    @AfterEach
    void restoreApplicationState() {
        if (isolatedHistory != null) isolatedHistory.clearHistory();
        if (shelf != null && originalShelf != null && !originalShelf.equals(shelf.getEntries())) {
            shelf.clear();
            for (int index = originalShelf.size() - 1; index >= 0; index--) shelf.addEntry(originalShelf.get(index));
        }
        AppSettings.setInstanceForTesting(originalSettings);
        I18nService.getInstance().refreshFromSettings();
    }

    @Test
    void preflightFailuresHaveStablePresentationAndDoNotExposeTransientSecrets() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED,
                    SecretVisibilityProfile.REDACTED)) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                for (FailureCase failureCase : FailureCase.values()) {
                    Fixture fixture = openFixture();
                    try {
                        characterizeFailure(fixture, profile, failureCase, transcript);
                    } finally {
                        fixture.stage.close();
                    }
                }
            }
            assertTrue(isolatedHistory.getHistoryItems().isEmpty());
            assertEquals(originalShelf, shelf.getEntries());
        });
        assertTranscript("1234b586aacd1d973763894b18878d474d46a5849630a6a8e3b061ff0137cf74", transcript);
    }

    private void characterizeFailure(Fixture fixture, SecretVisibilityProfile profile, FailureCase failureCase,
                                     List<String> transcript) {
        ProcessDesignerController controller = fixture.controller;
        controller.handleClearCanvas();
        ProcessDefinition.Node source = addNode(controller, "CONSOLE_INPUT", "Synthetic source");
        ProcessDefinition.Node cipher = addNode(controller, "ENCRYPT", "Synthetic cipher");

        switch (failureCase) {
            case UNKNOWN_ALGORITHM -> { }
            case AAD_WITH_CBC -> {
                controller.connections.add(new ProcessDefinition.Connection(source.id, cipher.id, "aad"));
            }
            case IV_WITH_ECB -> {
                controller.connections.add(new ProcessDefinition.Connection(source.id, cipher.id, "iv"));
            }
        }

        if (failureCase == FailureCase.UNKNOWN_ALGORITHM) {
            // Imported definitions can carry a value that is not offered by the inspector control.
            cipher.configuration.put("algorithm", failureCase.algorithm);
            controller.transientSecrets.computeIfAbsent(cipher.id, ignored -> new java.util.HashMap<>())
                    .put("key", TEST_ONLY_SECRET.toCharArray());
        } else {
            controller.select(cipher);
            ComboBox<String> algorithm = assertInstanceOf(ComboBox.class, controller.getInspectorControl("algorithm"));
            PasswordField key = assertInstanceOf(PasswordField.class, controller.getInspectorControl("key"));
            algorithm.setValue(failureCase.algorithm);
            key.setText(TEST_ONLY_SECRET);
        }
        assertNoInspectorTextLeak(fixture, TEST_ONLY_SECRET);

        TextArea output = (TextArea) fixture.scene.lookup("#executionOutputArea");
        TableView<ProcessExecutionRow> table = (TableView<ProcessExecutionRow>) fixture.scene.lookup("#executionStatusTable");
        Label status = (Label) fixture.scene.lookup("#processStatusLabel");
        javafx.scene.control.Button run = (javafx.scene.control.Button) fixture.scene.lookup("#runProcessButton");
        javafx.scene.control.Button cancel = (javafx.scene.control.Button) fixture.scene.lookup("#cancelProcessButton");
        ProgressBar progress = (ProgressBar) fixture.scene.lookup("#processProgressBar");
        String statusBefore = status.getText();
        boolean runDisabledBefore = run.isDisable();
        boolean cancelDisabledBefore = cancel.isDisable();
        double progressBefore = progress.getProgress();
        List<NodeExecutionEvent> executionEvents = new ArrayList<>();
        controller.onNodeExecutionEvent = executionEvents::add;

        controller.handleRunProcess();

        assertEquals(failureCase.algorithm, cipher.configuration.get("algorithm"));
        assertArrayEquals(TEST_ONLY_SECRET.toCharArray(), controller.getTransientSecret(cipher.id, "key"));
        assertEquals(failureCase.expectedOutput, output.getText());
        assertEquals(1, table.getItems().size());
        ProcessExecutionRow row = table.getItems().get(0);
        assertEquals(List.of("validation", "-", "Validation", "PRE-FLIGHT", "-", "-", "ERROR", "0 ms"),
                List.of(row.getNodeId(), row.getStep(), row.getStepName(), row.getOperation(), row.getInput(),
                        row.getOutput(), row.getStatus(), row.getDuration()));
        assertEquals(statusBefore, status.getText());
        assertEquals(runDisabledBefore, run.isDisable());
        assertEquals(cancelDisabledBefore, cancel.isDisable());
        assertEquals(progressBefore, progress.getProgress(), 0.001);
        assertTrue(executionEvents.isEmpty(), "preflight failure must not publish execution telemetry");
        assertFalse(output.getText().contains(TEST_ONLY_SECRET));
        assertFalse(status.getText().contains(TEST_ONLY_SECRET));
        assertNoInspectorTextLeak(fixture, TEST_ONLY_SECRET);

        Object undoRedo = field(controller, "undoRedoCoordinator");
        assertFalse(String.valueOf(field(undoRedo, "undoStack")).contains(TEST_ONLY_SECRET));
        assertFalse(String.valueOf(field(undoRedo, "redoStack")).contains(TEST_ONLY_SECRET));
        String telemetry = controller.renderExecutionResult(controller.toExecutableDefinition(), Map.of(),
                List.of(new NodeExecutionEvent(cipher.id, 1, cipher.label, cipher.type, NodeExecutionState.SUCCESS,
                        Duration.ZERO, Representation.BINARY, 16, Representation.BINARY, 16, "OK")), null);
        assertFalse(telemetry.contains(TEST_ONLY_SECRET));
        if (profile == SecretVisibilityProfile.MASKED) {
            assertTrue(telemetry.contains("key (HEX): ***MASKED***"), telemetry);
        } else {
            assertFalse(telemetry.contains("key (HEX)"), telemetry);
        }
        assertFalse(status.getText().contains(TEST_ONLY_SECRET));
        assertTrue(isolatedHistory.getHistoryItems().isEmpty());
        assertEquals(originalShelf, shelf.getEntries());
        transcript.add(profile + " " + failureCase.transcript + " row=PRE-FLIGHT/ERROR status=unchanged controls=unchanged"
                + " secret=inspector-safe telemetry=" + (profile == SecretVisibilityProfile.MASKED ? "masked" : "omitted")
                + " history=empty shelf=unchanged");
    }

    private static void assertNoInspectorTextLeak(Fixture fixture, String secret) {
        VBox inspector = (VBox) fixture.scene.lookup("#dynamicInspectorContainer");
        assertNotNull(inspector);
        assertTrue(inspector.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .noneMatch(label -> label.getText() != null && label.getText().contains(secret)));
        assertTrue(inspector.lookupAll(".text-field").stream().filter(TextInputControl.class::isInstance)
                .map(TextInputControl.class::cast).filter(control -> !(control instanceof PasswordField))
                .noneMatch(control -> control.getText() != null && control.getText().contains(secret)));
    }

    private static ProcessDefinition.Node addNode(ProcessDesignerController controller, String type, String label) {
        try {
            var add = ProcessDesignerController.class.getDeclaredMethod("addNode", String.class, String.class,
                    double.class, double.class);
            add.setAccessible(true);
            return (ProcessDefinition.Node) add.invoke(controller, type, label, 100.0, 100.0);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Fixture openFixture() {
        try {
            FXMLLoader loader = UiTestFxml.loader(ProcessDesignerPreflightCharacterizationUITest.class
                    .getResource("/fxml/process_designer.fxml"));
            TitledPane root = loader.load();
            Scene scene = new Scene(root, 1200, 800);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            return new Fixture(loader.getController(), scene, stage);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; } finally { done.countDown(); }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread");
        if (failure[0] instanceof Exception exception) throw exception;
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] != null) throw new RuntimeException(failure[0]);
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

    private static void assertTranscript(String expected, List<String> transcript) throws Exception {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, actual, String.join("\n", transcript));
    }

    private record Fixture(ProcessDesignerController controller, Scene scene, Stage stage) { }

    private enum FailureCase {
        UNKNOWN_ALGORITHM("SYNTHETIC_UNKNOWN_CIPHER", "invalid-algorithm",
                "Process failed: Error on Synthetic cipher: Unsupported encryption algorithm: SYNTHETIC_UNKNOWN_CIPHER"),
        AAD_WITH_CBC("AES/CBC/PKCS7Padding", "aad-on-cbc",
                "Process failed: Validation error: Synthetic cipher is connected to an aad port, but AES/CBC/PKCS7Padding does not support AAD. Remove the connection or change the algorithm."),
        IV_WITH_ECB("AES/ECB/PKCS7Padding", "iv-on-ecb",
                "Process failed: Validation error: Synthetic cipher is connected to an iv port, but AES/ECB/PKCS7Padding does not use an IV. Remove the connection.");

        private final String algorithm;
        private final String transcript;
        private final String expectedOutput;

        FailureCase(String algorithm, String transcript, String expectedOutput) {
            this.algorithm = algorithm;
            this.transcript = transcript;
            this.expectedOutput = expectedOutput;
        }
    }
}
