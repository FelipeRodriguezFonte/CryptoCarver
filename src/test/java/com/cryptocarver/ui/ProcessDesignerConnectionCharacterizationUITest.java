package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes port selection, connection drag, and undo through the real JavaFX canvas. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerConnectionCharacterizationUITest {
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
    void dragConnectRejectUndoAndCompatibilityHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            Fixture drag = openFixture();
            try {
                ProcessDesignerController controller = drag.controller;
                controller.handleClearCanvas();
                ProcessDefinition.Node source = addNode(controller, "HASH", "Hash source");
                ProcessDefinition.Node target = addNode(controller, "ENCRYPT", "Cipher target");
                controller.completeConnectionDragToPort(source, target, "key");
                assertEquals(1, controller.connections.size());
                int undoDepth = undoDepth(controller);
                controller.redraw();

                Circle ownInput = inputHandle(controller, source, "input");
                Circle occupiedInput = inputHandle(controller, target, "key");
                Circle freeInput = inputHandle(controller, target, "payload");
                Circle output = outputHandle(drag.scene, source);
                output.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1));
                assertEquals(2, curves(drag.scene).size(), "a transient curve must be added beside the stored link");
                assertEquals(0.3, ownInput.getOpacity(), 0.001, "a node cannot target itself");
                assertEquals(0.3, occupiedInput.getOpacity(), 0.001, "an occupied target port is dimmed");
                assertEquals(1.0, freeInput.getOpacity(), 0.001, "a compatible free port remains available");
                CubicCurve transientCurve = curves(drag.scene).stream().filter(curve -> curve.getUserData() == null)
                        .findFirst().orElseThrow();
                double initialEndX = transientCurve.getEndX();
                output.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 620, 440));
                assertNotEquals(initialEndX, transientCurve.getEndX(), 0.001, "pointer movement updates the transient curve");
                output.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, 950, 760));
                assertEquals(1, curves(drag.scene).size(), "releasing over empty canvas cancels the transient curve");
                assertEquals(1.0, ownInput.getOpacity(), 0.001, "cancel restores port opacity");
                assertTrue(controller.connections.size() == 1);
                Object connectionCoordinator = field(controller, "connectionCoordinator");
                assertNull(field(connectionCoordinator, "connectionDragSourceNode"));
                assertNull(field(connectionCoordinator, "interactiveConnectionCurve"));
                transcript.add("drag=started moved empty-release=cancel own-port=dim occupied-port=dim free-port=bright opacity=restored");

                output.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1));
                occupiedInput.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1));
                assertEquals(1, controller.connections.size(), "dropping on the occupied key port must be rejected");
                assertTrue(controller.executionOutputArea.getText().toLowerCase().contains("occupied"));
                assertEquals(undoDepth, undoDepth(controller), "rejected connections must not add an undo entry");
                assertEquals(1.0, occupiedInput.getOpacity(), 0.001);
                transcript.add("occupied-port-drop=refused links=1 undo=unchanged feedback=shown");
            } finally {
                drag.stage.close();
            }

            Fixture reusable = openFixture();
            try {
                ProcessDesignerController controller = reusable.controller;
                controller.handleClearCanvas();
                ProcessDefinition.Node keySource = addNode(controller, "AES_KEY_GENERATE", "Reusable key");
                ProcessDefinition.Node cipher = addNode(controller, "ENCRYPT", "Cipher");
                Circle output = outputHandle(reusable.scene, keySource);
                Circle keyPort = inputHandle(controller, cipher, "key");
                output.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1));
                keyPort.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1));

                assertEquals(1, controller.connections.size());
                assertEquals("key", controller.connections.get(0).targetPort);
                assertEquals("true", cipher.configuration.get("keyFromFlow"));
                assertEquals(List.of(keySource.id), new ArrayList<>(controller.selectedNodeIds),
                        "a reusable key source remains selected after connecting");
                controller.handleUndo();
                assertTrue(controller.connections.isEmpty());
                ProcessDefinition.Node restoredCipher = node(controller, cipher.id);
                assertFalse(restoredCipher.configuration.containsKey("keyFromFlow"));
                controller.handleRedo();
                assertEquals(1, controller.connections.size());
                assertEquals("true", node(controller, cipher.id).configuration.get("keyFromFlow"));
                transcript.add("key-port-drop=connected source-retained=true undo=link-and-marker-removed redo=link-and-marker-restored");
            } finally {
                reusable.stage.close();
            }

            Fixture incompatible = openFixture();
            try {
                ProcessDesignerController controller = incompatible.controller;
                controller.handleClearCanvas();
                ProcessDefinition.Node binary = addNode(controller, "HASH", "Binary source");
                ProcessDefinition.Node noInput = addNode(controller, "CONSOLE_INPUT", "Input-less target");
                controller.completeConnectionDrag(binary, noInput);
                assertTrue(controller.connections.isEmpty());
                assertTrue(controller.executionOutputArea.getText().toLowerCase().contains("incompatible"));
                transcript.add("incompatible-drag=refused links=0 feedback=shown");
            } finally {
                incompatible.stage.close();
            }

            assertTrue(isolatedHistory.getHistoryItems().isEmpty());
            assertEquals(originalShelf, shelf.getEntries());
            transcript.add("persistent-history=empty shelf=unchanged");
        });
        assertTranscript("e8baa0c2d865ec4f5267eb2cc6aeecdaae884985cb7b6be4686151adb1b97310", transcript);
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

    private static ProcessDefinition.Node node(ProcessDesignerController controller, String id) {
        return controller.nodes.stream().filter(candidate -> candidate.id.equals(id)).findFirst().orElseThrow();
    }

    private static Circle inputHandle(ProcessDesignerController controller, ProcessDefinition.Node node, String port) {
        ProcessCanvasRenderer renderer = (ProcessCanvasRenderer) field(controller, "processCanvasRenderer");
        return renderer.inputPortHandles().stream()
                .filter(circle -> circle.getUserData() instanceof ProcessCanvasRenderer.PortHandleData data
                        && data.node().id.equals(node.id) && data.port().name().equals(port))
                .findFirst().orElseThrow(() -> new AssertionError("Missing input handle " + port));
    }

    private static Circle outputHandle(Scene scene, ProcessDefinition.Node node) {
        return nodeView(scene, node).getChildren().stream().filter(Circle.class::isInstance).map(Circle.class::cast)
                .filter(circle -> circle.getTranslateX() > 0).findFirst().orElseThrow();
    }

    private static StackPane nodeView(Scene scene, ProcessDefinition.Node node) {
        Pane canvas = (Pane) scene.lookup("#workflowCanvas");
        return canvas.getChildren().stream().filter(StackPane.class::isInstance).map(StackPane.class::cast)
                .filter(view -> view.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText() != null && label.getText().startsWith(node.label)))
                .findFirst().orElseThrow(() -> new AssertionError("Missing view for " + node.label));
    }

    private static List<CubicCurve> curves(Scene scene) {
        Pane canvas = (Pane) scene.lookup("#workflowCanvas");
        return canvas.getChildren().stream().filter(CubicCurve.class::isInstance).map(CubicCurve.class::cast).toList();
    }

    private static int undoDepth(ProcessDesignerController controller) {
        Object coordinator = field(controller, "undoRedoCoordinator");
        return ((Collection<?>) field(coordinator, "undoStack")).size();
    }

    private static Fixture openFixture() {
        try {
            FXMLLoader loader = UiTestFxml.loader(ProcessDesignerConnectionCharacterizationUITest.class
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

    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> type, double x, double y) {
        boolean down = type != MouseEvent.MOUSE_RELEASED;
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, down, false, false, false, false, false, null);
    }

    private static void onFx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; } finally { done.countDown(); }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "Timed out waiting for the JavaFX thread");
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
}
