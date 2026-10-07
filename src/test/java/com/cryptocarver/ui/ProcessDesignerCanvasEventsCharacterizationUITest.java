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
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Pins installed canvas handlers, priority and consumption without OS-specific geometry. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerCanvasEventsCharacterizationUITest {
    @TempDir Path tempDir;
    private AppSettings originalSettings;
    private ClipboardShelfManager shelf;
    private List<ClipboardEntry> originalShelf;
    private HistoryManager isolatedHistory;
    private Stage stage;

    @BeforeAll static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @BeforeEach void isolateState() {
        originalSettings = AppSettings.getInstance();
        AppSettings.setInstanceForTesting(new AppSettings(tempDir.resolve("settings.json")));
        AppSettings.getInstance().setLanguagePreference(LanguagePreference.EN);
        I18nService.getInstance().refreshFromSettings();
        shelf = ClipboardShelfManager.getInstance();
        originalShelf = new ArrayList<>(shelf.getEntries());
        isolatedHistory = new HistoryManager(tempDir.resolve("history.json"));
    }

    @AfterEach void restoreState() throws Exception {
        try { onFx(() -> { if (stage != null) stage.close(); return null; }); }
        finally {
            isolatedHistory.clearHistory();
            if (!originalShelf.equals(shelf.getEntries())) {
                shelf.clear();
                for (int i = originalShelf.size() - 1; i >= 0; i--) shelf.addEntry(originalShelf.get(i));
            }
            AppSettings.setInstanceForTesting(originalSettings);
            I18nService.getInstance().refreshFromSettings();
        }
    }

    @Test void installedEventsHavePortableTranscript() throws Exception {
        List<String> transcript = onFx(() -> {
            FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/process_designer.fxml"));
            TitledPane root = loader.load();
            ProcessDesignerController controller = loader.getController();
            Scene scene = new Scene(root, 1200, 800);
            stage = new Stage(); stage.setScene(scene); stage.show(); root.applyCss(); root.layout();
            Pane canvas = (Pane) scene.lookup("#workflowCanvas");
            assertNotNull(canvas.getOnScroll()); assertNotNull(canvas.getOnMouseClicked());
            assertNotNull(canvas.getOnKeyPressed()); assertNotNull(canvas.getOnMouseMoved());
            List<String> lines = new ArrayList<>();
            controller.handleClearCanvas(); controller.handleAddConsoleInput();
            ProcessDefinition.Node source = controller.nodes.get(0); controller.select(source);

            assertTrue(key(canvas, KeyCode.RIGHT, false, false));
            assertTrue(key(canvas, KeyCode.UP, true, false));
            assertEquals(61, source.x); assertEquals(170, source.y);
            assertTrue(key(canvas, KeyCode.LEFT, false, false));
            assertTrue(key(canvas, KeyCode.DOWN, true, false));
            assertEquals(60, source.x); assertEquals(180, source.y);
            lines.add("arrows=61,170/60,180;consumed=true;steps=1,10");

            assertTrue(key(canvas, KeyCode.D, false, true)); assertEquals(2, controller.nodes.size());
            assertTrue(key(canvas, KeyCode.Z, false, true)); assertEquals(1, controller.nodes.size());
            assertTrue(key(canvas, KeyCode.Z, true, true)); assertEquals(2, controller.nodes.size());
            assertTrue(key(canvas, KeyCode.Z, false, true)); assertEquals(1, controller.nodes.size());
            assertTrue(key(canvas, KeyCode.Y, false, true)); assertEquals(2, controller.nodes.size());
            controller.select(controller.nodes.get(1));
            assertTrue(key(canvas, KeyCode.DELETE, false, false)); assertEquals(1, controller.nodes.size());
            assertTrue(key(canvas, KeyCode.Z, false, true)); assertEquals(2, controller.nodes.size());
            controller.select(controller.nodes.get(1));
            assertTrue(key(canvas, KeyCode.BACK_SPACE, false, false)); assertEquals(1, controller.nodes.size());
            lines.add("D/Z/shift-Z/Z/Y/delete/Z/backspace=2/1/2/1/2/1/2/1;consumed=true");

            controller.setZoom(1.0);
            ScrollEvent wheel = scroll(false, false, 40);
            canvas.getOnScroll().handle(wheel);
            assertFalse(wheel.isConsumed()); assertEquals(1.0, controller.getZoom(), 0.001);
            wheel = scroll(true, false, 40); canvas.getOnScroll().handle(wheel);
            assertTrue(wheel.isConsumed()); assertEquals(1.08, controller.getZoom(), 0.001);
            wheel = scroll(true, false, -40); canvas.getOnScroll().handle(wheel);
            assertTrue(wheel.isConsumed()); assertEquals(1.0, controller.getZoom(), 0.001);
            wheel = scroll(true, true, 40); canvas.getOnScroll().handle(wheel);
            assertTrue(wheel.isConsumed()); assertEquals(1.08, controller.getZoom(), 0.001);
            lines.add("wheel=normal:1.00/unconsumed;control:1.08,1.00;shortcut:1.08/consumed");

            source = controller.nodes.get(0); controller.select(source);
            javafx.scene.Node child = canvas.getChildren().get(0);
            canvas.getOnMouseClicked().handle(mouse(MouseEvent.MOUSE_CLICKED).copyFor(child, child));
            assertTrue(controller.selectedNodeIds.contains(source.id));
            canvas.getOnMouseClicked().handle(mouse(MouseEvent.MOUSE_CLICKED).copyFor(canvas, canvas));
            assertTrue(controller.selectedNodeIds.isEmpty());
            assertFalse(key(canvas, KeyCode.RIGHT, false, false));
            assertFalse(key(canvas, KeyCode.A, false, false));
            lines.add("click=child:preserve/background:clear;empty-arrow/other-key=unconsumed");

            controller.select(source);
            var start = ProcessDesignerController.class.getDeclaredMethod("startConnectionDrag", ProcessDefinition.Node.class);
            start.setAccessible(true); start.invoke(controller, source);
            var field = ProcessDesignerController.class.getDeclaredField("connectionCoordinator");
            field.setAccessible(true);
            ProcessConnectionCoordinator connection = (ProcessConnectionCoordinator) field.get(controller);
            var curve = connection.interactiveCurve(); assertNotNull(curve); assertTrue(connection.isDragging());
            MouseEvent moved = mouse(MouseEvent.MOUSE_MOVED).copyFor(canvas, canvas);
            Point2D expected = canvas.sceneToLocal(moved.getSceneX(), moved.getSceneY());
            canvas.getOnMouseMoved().handle(moved);
            assertEquals(source.x + 150, curve.getStartX(), 0.001);
            assertEquals(source.y + 35, curve.getStartY(), 0.001);
            assertEquals(expected.getX(), curve.getEndX(), 0.001);
            assertEquals(expected.getY(), curve.getEndY(), 0.001);
            assertFalse(moved.isConsumed());
            assertTrue(key(canvas, KeyCode.ESCAPE, false, false));
            assertTrue(controller.selectedNodeIds.isEmpty()); assertFalse(connection.isDragging());
            assertNull(connection.interactiveCurve()); assertFalse(canvas.getChildren().contains(curve));
            lines.add("move=sceneToLocal/anchor-equal/unconsumed;escape=selection-cleared/drag-cancelled/curve-removed/consumed");
            assertEquals(originalShelf, shelf.getEntries()); assertTrue(isolatedHistory.getHistoryItems().isEmpty());
            return lines;
        });
        String joined = String.join("\n", transcript);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        assertEquals("964211ade5ffbf3408904c5eca1160b21bc8227864b80bd2aa83dff50f2c24c3", digest, joined);
    }

    private static boolean key(Pane canvas, KeyCode code, boolean shift, boolean shortcut) {
        KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut, false, shortcut);
        canvas.getOnKeyPressed().handle(event);
        return event.isConsumed();
    }

    private static ScrollEvent scroll(boolean control, boolean meta, double delta) {
        return new ScrollEvent(ScrollEvent.SCROLL, 0, 0, 0, 0,
                false, control, false, meta, false, false, 0, delta, 0, delta,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 300, 400, 300, 400, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        Object[] result = new Object[1]; Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try { result[0] = action.call(); }
            catch (Throwable error) { failure[0] = error; }
            finally { finished.countDown(); }
        });
        assertTrue(finished.await(15, TimeUnit.SECONDS), "timed out waiting for JavaFX");
        if (failure[0] instanceof Exception error) throw error;
        if (failure[0] instanceof Error error) throw error;
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }
}
