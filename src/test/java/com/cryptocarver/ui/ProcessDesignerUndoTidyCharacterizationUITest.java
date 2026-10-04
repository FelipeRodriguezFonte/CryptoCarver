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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

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

/** Characterizes Process Designer undo/redo, duplication, and layout through the real JavaFX view. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerUndoTidyCharacterizationUITest {
    private static final String TEST_ONLY_SECRET_SENTINEL = "TEST_ONLY_SECRET_SENTINEL_63";

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
            for (int index = originalShelf.size() - 1; index >= 0; index--) {
                shelf.addEntry(originalShelf.get(index));
            }
        }
        AppSettings.setInstanceForTesting(originalSettings);
        I18nService.getInstance().refreshFromSettings();
    }

    @Test
    void addMoveConnectDeleteAndUndoLimitHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            Fixture fixture = openFixture();
            try {
                ProcessDesignerController controller = fixture.controller;
                controller.handleClearCanvas();

                controller.handleAddConsoleInput();
                assertEquals(1, controller.nodes.size());
                controller.handleUndo();
                assertEquals(0, controller.nodes.size());
                controller.handleRedo();
                assertEquals(1, controller.nodes.size());
                transcript.add("add undo=0 redo=1");

                ProcessDefinition.Node input = controller.nodes.get(0);
                double originalX = input.x;
                double originalY = input.y;
                controller.redraw();
                Pane canvas = (Pane) fixture.scene.lookup("#workflowCanvas");
                StackPane nodeView = canvas.getChildren().stream()
                        .filter(StackPane.class::isInstance)
                        .map(StackPane.class::cast)
                        .filter(view -> view.getLayoutX() == input.x && view.getLayoutY() == input.y)
                        .findFirst().orElseThrow();
                nodeView.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, true));
                input.x = 200;
                input.y = 220;
                nodeView.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, false));
                assertEquals(200, controller.nodes.get(0).x, 0.001);
                controller.handleUndo();
                assertEquals(originalX, controller.nodes.get(0).x, 0.001);
                assertEquals(originalY, controller.nodes.get(0).y, 0.001);
                controller.handleRedo();
                assertEquals(200, controller.nodes.get(0).x, 0.001);
                assertEquals(220, controller.nodes.get(0).y, 0.001);
                transcript.add("move undo=" + originalX + "," + originalY + " redo=200.0,220.0");

                controller.handleAddHash();
                ProcessDefinition.Node hash = controller.nodes.get(1);
                controller.select(controller.nodes.get(0));
                controller.select(controller.nodes.stream().filter(node -> node.id.equals(hash.id)).findFirst().orElseThrow());
                controller.handleConnectSelected();
                assertEquals(1, controller.connections.size());
                controller.handleUndo();
                assertEquals(0, controller.connections.size());
                controller.handleRedo();
                assertEquals(1, controller.connections.size());
                transcript.add("connect undo=0 redo=1");

                controller.select(controller.nodes.stream().filter(node -> node.id.equals(hash.id)).findFirst().orElseThrow());
                controller.handleDeleteSelected();
                assertEquals(1, controller.nodes.size());
                assertEquals(0, controller.connections.size());
                controller.handleUndo();
                assertEquals(2, controller.nodes.size());
                assertEquals(1, controller.connections.size());
                controller.handleRedo();
                assertEquals(1, controller.nodes.size());
                assertEquals(0, controller.connections.size());
                transcript.add("delete-node undo=2/1 redo=1/0");

                controller.handleClearCanvas();
                for (int index = 0; index < 61; index++) controller.handleAddConsoleInput();
                for (int index = 0; index < 60; index++) controller.handleUndo();
                assertEquals(1, controller.nodes.size(), "the 60 newest undo commands remain available");
                controller.handleUndo();
                assertEquals(1, controller.nodes.size(), "the command older than the limit was discarded");
                for (int index = 0; index < 60; index++) controller.handleRedo();
                assertEquals(61, controller.nodes.size());
                transcript.add("limit=60 retained-after-undo=1 redo=61");

                assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                assertEquals(originalShelf, shelf.getEntries());
            } finally {
                fixture.stage.close();
            }
        });
        assertTranscript("bd51e23d16b05e0820fdddabddbbd1153413949ffd2d534d2f7a9346cac2e79f", transcript);
    }

    @Test
    void duplicatesKeepPortsButDropLinksAndSuppliedMarkers() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            Fixture fixture = openFixture();
            try {
                ProcessDesignerController controller = fixture.controller;
                controller.handleClearCanvas();
                ProcessDefinition.Node payloadSource = addNode(controller, "CONSOLE_INPUT");
                ProcessDefinition.Node keySource = addNode(controller, "CONSOLE_INPUT");
                ProcessDefinition.Node encrypt = addNode(controller, "ENCRYPT");
                controller.completeConnectionDragToPort(payloadSource, encrypt, "payload");
                controller.completeConnectionDragToPort(keySource, encrypt, "key");
                List<String> originalPorts = com.cryptocarver.model.process.ProcessEngine
                        .getHandlerFor(encrypt.type).inputPorts(encrypt).stream()
                        .map(port -> port.name()).toList();
                assertEquals(List.of("payload", "key"), controller.connections.stream()
                        .map(connection -> connection.targetPort).toList());

                controller.select(encrypt);
                controller.handleDuplicateSelected();
                ProcessDefinition.Node connectedClone = controller.nodes.get(controller.nodes.size() - 1);
                assertEquals("ENCRYPT", connectedClone.type);
                assertEquals(2, controller.connections.size(), "duplication must not clone the source links");
                assertFalse(controller.connections.stream().anyMatch(connection ->
                                connection.from.equals(connectedClone.id) || connection.to.equals(connectedClone.id)),
                        "the duplicate starts disconnected");
                assertFalse(connectedClone.configuration.containsKey("keyFromFlow"),
                        "a disconnected duplicate must not retain its source's key-port marker");
                assertEquals(originalPorts, com.cryptocarver.model.process.ProcessEngine
                        .getHandlerFor(connectedClone.type).inputPorts(connectedClone).stream()
                        .map(port -> port.name()).toList());
                transcript.add("connected clone ports=" + String.join(",", originalPorts)
                        + " links=0 source-links=2 flow-markers=clear");

                ProcessDefinition.Node isolated = addNode(controller, "HASH");
                controller.select(isolated);
                controller.handleDuplicateSelected();
                ProcessDefinition.Node isolatedClone = controller.nodes.get(controller.nodes.size() - 1);
                assertFalse(controller.connections.stream().anyMatch(connection ->
                        connection.from.equals(isolatedClone.id) || connection.to.equals(isolatedClone.id)));
                transcript.add("unconnected clone links=0");

                assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                assertEquals(originalShelf, shelf.getEntries());
            } finally {
                fixture.stage.close();
            }
        });
        assertTranscript("PENDING", transcript);
    }

    @Test
    void tidyUndoRestoresPositionsAndRedoRestoresTheLayout() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            Fixture fixture = openFixture();
            try {
                ProcessDesignerController controller = fixture.controller;
                controller.handleClearCanvas();
                ProcessDefinition.Node input = addNode(controller, "CONSOLE_INPUT");
                ProcessDefinition.Node hash = addNode(controller, "HASH");
                ProcessDefinition.Node output = addNode(controller, "CONSOLE_OUTPUT");
                controller.completeConnectionDragToPort(input, hash, "input");
                controller.completeConnectionDragToPort(hash, output, "input");

                input.x = 470; input.y = 330;
                hash.x = 110; hash.y = 510;
                output.x = 720; output.y = 160;
                double[][] original = positions(controller.nodes);
                controller.redraw();
                controller.handleTidyLayout();
                assertPositions(controller.nodes, new double[][] {{60, 80}, {280, 80}, {500, 80}});
                controller.handleUndo();
                assertPositions(controller.nodes, original);
                assertEquals(2, controller.connections.size());
                controller.handleRedo();
                assertPositions(controller.nodes, new double[][] {{60, 80}, {280, 80}, {500, 80}});
                assertEquals(2, controller.connections.size());
                transcript.add("tidy=60,80|280,80|500,80 undo=470,330|110,510|720,160 redo=tidy links=2");

                assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                assertEquals(originalShelf, shelf.getEntries());
            } finally {
                fixture.stage.close();
            }
        });
        assertTranscript("63030e66741efc18ab040a3c7d3e0eca6982b6a51e85bf7bfc8592278b4f42ed", transcript);
    }

    @Test
    void undoAndRedoDoNotExposeTransientNodeSecrets() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED,
                    SecretVisibilityProfile.REDACTED)) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                Fixture fixture = openFixture();
                try {
                    ProcessDesignerController controller = fixture.controller;
                    controller.handleClearCanvas();
                    ProcessDefinition.Node encrypt = addNode(controller, "ENCRYPT");
                    controller.select(encrypt);
                    PasswordField key = (PasswordField) controller.getInspectorControl("key");
                    key.setText(TEST_ONLY_SECRET_SENTINEL);
                    controller.handleSaveNodeSettings();
                    assertArrayEquals(TEST_ONLY_SECRET_SENTINEL.toCharArray(),
                            controller.getTransientSecret(encrypt.id, "key"));
                    ProcessDefinition executableWithTransientSecret = controller.toExecutableDefinition();
                    assertEquals(TEST_ONLY_SECRET_SENTINEL,
                            executableWithTransientSecret.nodes.get(0).configuration.get("key"));
                    NodeExecutionEvent event = new NodeExecutionEvent(encrypt.id, 1, encrypt.label, encrypt.type,
                            NodeExecutionState.SUCCESS, Duration.ZERO, Representation.BINARY, 16,
                            Representation.BINARY, 16, "OK");

                    assertNoSecretLeak(controller, executableWithTransientSecret, event, profile);
                    controller.handleDuplicateSelected();
                    ProcessDefinition.Node duplicate = controller.nodes.get(controller.nodes.size() - 1);
                    assertFalse(duplicate.configuration.containsKey("keyFromSecrets"),
                            "a disconnected duplicate must not claim an uncopied transient secret");
                    controller.handleUndo();
                    assertNoSecretLeak(controller, executableWithTransientSecret, event, profile);
                    controller.handleRedo();
                    assertNoSecretLeak(controller, executableWithTransientSecret, event, profile);
                    assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                    assertEquals(originalShelf, shelf.getEntries());
                    transcript.add(profile + " undo/redo history=empty telemetry=redacted status=clear shelf=unchanged");
                } finally {
                    fixture.stage.close();
                }
            }
        });
        assertTranscript("222095cc203b765651dac4f9924e20c7ea54ee7205942a524b9cab20d0ef2528", transcript);
    }

    private void assertNoSecretLeak(ProcessDesignerController controller, ProcessDefinition telemetryDefinition,
                                    NodeExecutionEvent event, SecretVisibilityProfile profile) {
        String trace = controller.renderExecutionResult(telemetryDefinition, Map.of(), List.of(event), null);
        assertFalse(trace.contains(TEST_ONLY_SECRET_SENTINEL), profile + " telemetry leaked the test-only secret");
        if (profile == SecretVisibilityProfile.MASKED) {
            assertTrue(trace.contains("key (HEX): ***MASKED***"), trace);
        } else {
            assertFalse(trace.contains("key (HEX)"), trace);
        }
        var status = field(controller, "processStatusLabel", javafx.scene.control.Label.class).getText();
        TextArea output = field(controller, "executionOutputArea", TextArea.class);
        assertFalse(status.contains(TEST_ONLY_SECRET_SENTINEL), profile + " status leaked the test-only secret");
        assertFalse(output.getText().contains(TEST_ONLY_SECRET_SENTINEL), profile + " output leaked the test-only secret");
    }

    private static double[][] positions(List<ProcessDefinition.Node> nodes) {
        return nodes.stream().map(node -> new double[] {node.x, node.y}).toArray(double[][]::new);
    }

    private static void assertPositions(List<ProcessDefinition.Node> nodes, double[][] expected) {
        assertEquals(expected.length, nodes.size());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index][0], nodes.get(index).x, 0.001, "x at node " + index);
            assertEquals(expected[index][1], nodes.get(index).y, 0.001, "y at node " + index);
        }
    }

    private static ProcessDefinition.Node addNode(ProcessDesignerController controller, String type) {
        try {
            var method = ProcessDesignerController.class.getDeclaredMethod("addNode", String.class, String.class,
                    double.class, double.class);
            method.setAccessible(true);
            return (ProcessDefinition.Node) method.invoke(controller, type, type, 100.0, 100.0);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Fixture openFixture() throws Exception {
        FXMLLoader loader = UiTestFxml.loader(
                ProcessDesignerUndoTidyCharacterizationUITest.class.getResource("/fxml/process_designer.fxml"));
        TitledPane root = loader.load();
        Scene scene = new Scene(root, 1200, 800);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        root.applyCss();
        root.layout();
        return new Fixture(loader.getController(), scene, stage);
    }

    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> type, boolean primaryDown) {
        return new MouseEvent(type, 10, 10, 100, 100, MouseButton.PRIMARY, 1,
                false, false, false, false, primaryDown, false, false, false, false, false, null);
    }

    private static void onFx(ThrowingRunnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable error) { failure[0] = error; }
            finally { done.countDown(); }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread");
        if (failure[0] instanceof Exception exception) throw exception;
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }

    private static String digest(List<String> transcript) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertTranscript(String expected, List<String> transcript) throws Exception {
        assertEquals(expected, digest(transcript), String.join("\n", transcript));
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private record Fixture(ProcessDesignerController controller, Scene scene, Stage stage) { }
}
