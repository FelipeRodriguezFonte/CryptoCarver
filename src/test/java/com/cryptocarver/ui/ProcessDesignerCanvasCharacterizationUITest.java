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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes canvas rendering, selection styling, and linked-node movement. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerCanvasCharacterizationUITest {
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
    void redrawSelectionAndLinkedMoveHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            Fixture fixture = openFixture();
            try {
                ProcessDesignerController controller = fixture.controller;
                controller.handleClearCanvas();
                ProcessDefinition.Node source = addNode(controller, "CONSOLE_INPUT", "Canvas source", 60, 80);
                ProcessDefinition.Node target = addNode(controller, "HEX_ENCODE", "Canvas target", 320, 80);

                controller.select(source);
                controller.select(target);
                controller.redraw();
                Pane canvas = (Pane) fixture.scene.lookup("#workflowCanvas");
                List<StackPane> views = canvas.getChildren().stream().filter(StackPane.class::isInstance)
                        .map(StackPane.class::cast).toList();
                StackPane sourceView = nodeView(views, "Canvas source");
                StackPane targetView = nodeView(views, "Canvas target");
                assertTrue(labelTexts(sourceView).contains("SOURCE"));
                assertTrue(sourceView.getStyle().contains("#5a4a20"));
                assertTrue(targetView.getStyle().contains("#287bb5"));
                transcript.add("selection source=pending marker=SOURCE target=active");

                controller.completeConnectionDragToPort(source, target, "input");

                int validationsBefore = controller.validationCounter;
                controller.redraw();
                views = canvas.getChildren().stream().filter(StackPane.class::isInstance)
                        .map(StackPane.class::cast).toList();
                List<CubicCurve> curves = curves(canvas);
                assertEquals(2, views.size());
                assertEquals(1, curves.size());
                assertEquals(1, controller.validationCounter - validationsBefore);
                sourceView = nodeView(views, "Canvas source");
                targetView = nodeView(views, "Canvas target");
                assertTrue(labelTexts(sourceView).stream().anyMatch(text -> text.contains("[TEXT_UTF8]")));
                assertTrue(labelTexts(targetView).stream().anyMatch(text -> text.contains("[HEX]")));
                assertTrue(canvas.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> "input".equals(label.getText())), "connection must label its target port");
                assertEquals(1, inputHandles(targetView));
                assertEquals(210, curves.get(0).getStartX(), 0.001);
                assertEquals(115, curves.get(0).getStartY(), 0.001);
                assertEquals(320, curves.get(0).getEndX(), 0.001);
                assertEquals(115, curves.get(0).getEndY(), 0.001);
                transcript.add("redraw nodes=2 curve=1 reps=TEXT_UTF8/HEX port=input input-handles=1 validation=+1");

                ProcessDefinition.Connection connection = controller.connections.get(0);
                controller.selectConnection(connection);
                CubicCurve selectedCurve = curves(canvas).get(0);
                assertEquals(4.0, selectedCurve.getStrokeWidth(), 0.001);
                assertEquals("0xf6c344ff", selectedCurve.getStroke().toString());
                transcript.add("selected-connection stroke=0xf6c344ff width=4.0");

                sourceView = nodeView(canvas.getChildren().stream().filter(StackPane.class::isInstance)
                        .map(StackPane.class::cast).toList(), "Canvas source");
                sourceView.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 100, 100));
                sourceView.fireEvent(mouseEvent(MouseEvent.MOUSE_DRAGGED, 117, 126));
                assertEquals(80, source.x, 0.001);
                assertEquals(110, source.y, 0.001);
                assertEquals(230, curves(canvas).get(0).getStartX(), 0.001);
                assertEquals(145, curves(canvas).get(0).getStartY(), 0.001);
                sourceView.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, 117, 126));
                controller.handleUndo();
                assertEquals(60, controller.nodes.get(0).x, 0.001);
                assertEquals(80, controller.nodes.get(0).y, 0.001);
                controller.handleRedo();
                assertEquals(80, controller.nodes.get(0).x, 0.001);
                assertEquals(110, controller.nodes.get(0).y, 0.001);
                transcript.add("drag snap=80,110 curve-start=230,145 undo=60,80 redo=80,110");

                assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                assertEquals(originalShelf, shelf.getEntries());
            } finally {
                fixture.stage.close();
            }
        });
        assertTranscript("e8d1bafcbc0a35484c18428979328002eb7658976c380b61f40c2a63118ff014", transcript);
    }

    private static ProcessDefinition.Node addNode(ProcessDesignerController controller, String type, String label,
                                                  double x, double y) {
        try {
            var add = ProcessDesignerController.class.getDeclaredMethod("addNode", String.class, String.class,
                    double.class, double.class);
            add.setAccessible(true);
            return (ProcessDefinition.Node) add.invoke(controller, type, label, x, y);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static StackPane nodeView(List<StackPane> views, String prefix) {
        return views.stream().filter(view -> labelTexts(view).stream().anyMatch(text -> text.startsWith(prefix)))
                .findFirst().orElseThrow(() -> new AssertionError("Missing view for " + prefix + " in "
                        + views.stream().map(ProcessDesignerCanvasCharacterizationUITest::labelTexts).toList()));
    }

    private static List<String> labelTexts(StackPane view) {
        return view.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                .map(Label::getText).toList();
    }

    private static int inputHandles(StackPane view) {
        return (int) view.getChildren().stream().filter(Circle.class::isInstance).count() - 1;
    }

    private static List<CubicCurve> curves(Pane canvas) {
        return canvas.getChildren().stream().filter(CubicCurve.class::isInstance).map(CubicCurve.class::cast).toList();
    }

    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> type, double x, double y) {
        boolean down = type != MouseEvent.MOUSE_RELEASED;
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, down, false, false, false, false, false, null);
    }

    private static Fixture openFixture() {
        try {
            FXMLLoader loader = UiTestFxml.loader(ProcessDesignerCanvasCharacterizationUITest.class
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

    private static void assertTranscript(String expected, List<String> transcript) throws Exception {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, actual, String.join("\n", transcript));
    }

    private record Fixture(ProcessDesignerController controller, Scene scene, Stage stage) { }
}
