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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
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
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes node selection and inspector updates through the real JavaFX view. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerSelectionCharacterizationUITest {
    private static final String TEST_ONLY_SECRET_SENTINEL = "SELECTION_TEST_SECRET_64";

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
    void selectionInspectorAndSecretPresentationHaveStableTranscript() throws Exception {
        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED,
                    SecretVisibilityProfile.REDACTED)) {
                AppSettings.getInstance().setSecretVisibilityProfile(profile);
                Fixture fixture = openFixture();
                try {
                    ProcessDesignerController controller = fixture.controller;
                    controller.handleClearCanvas();
                    ProcessDefinition.Node source = addNode(controller, "RANDOM_BYTES", "Synthetic source");
                    ProcessDefinition.Node target = addNode(controller, "ENCRYPT", "Synthetic cipher");

                    controller.select(source);
                    Button connect = (Button) fixture.scene.lookup("#connectSelectedButton");
                    MenuButton connectMenu = (MenuButton) fixture.scene.lookup("#connectMenuButton");
                    assertTrue(connect.isDisable(), "one selected node must not enable the connect action");
                    assertFalse(connectMenu.isVisible());
                    transcript.add(profile + " single=connect-disabled inspector="
                            + controller.getInspectorControl("length").getClass().getSimpleName());

                    controller.select(target);
                    assertTrue(connectMenu.isVisible(), "multiple compatible target ports must use the menu");
                    assertFalse(connect.isVisible());
                    List<String> menuLabels = connectMenu.getItems().stream().map(item -> item.getText()).toList();
                    assertTrue(menuLabels.size() > 1, "encrypt node must expose multiple compatible free ports");
                    assertEquals("ENCRYPT · Synthetic cipher", ((Label) fixture.scene.lookup("#selectedNodeLabel")).getText());
                    transcript.add("pair=" + menuLabels.size() + "-port-menu selected=target");

                    controller.selectNodeById(source.id);
                    assertEquals(2, controller.selectedNodeIds.size(), "selecting an existing member preserves the pair");
                    assertEquals("RANDOM_BYTES · Synthetic source", ((Label) fixture.scene.lookup("#selectedNodeLabel")).getText());
                    controller.selectNodeById("missing-id");
                    controller.selectNodeById(null);
                    assertEquals("RANDOM_BYTES · Synthetic source", ((Label) fixture.scene.lookup("#selectedNodeLabel")).getText());
                    transcript.add("id-lookup=existing-selects missing-and-null=unchanged pair=2");

                    controller.selectNodeById(target.id);
                    PasswordField key = assertInstanceOf(PasswordField.class, controller.getInspectorControl("key"));
                    key.setText(TEST_ONLY_SECRET_SENTINEL);
                    controller.handleSaveNodeSettings();
                    assertArrayEquals(TEST_ONLY_SECRET_SENTINEL.toCharArray(),
                            controller.getTransientSecret(target.id, "key"));
                    assertNull(target.configuration.get("key"), "secret stays out of the graph definition");
                    assertNoInspectorTextLeak(fixture, TEST_ONLY_SECRET_SENTINEL);

                    controller.selectNodeById(source.id);
                    controller.selectNodeById(target.id);
                    PasswordField restoredKey = assertInstanceOf(PasswordField.class,
                            controller.getInspectorControl("key"));
                    assertEquals(TEST_ONLY_SECRET_SENTINEL, restoredKey.getText(),
                            "switching selections must restore the session secret to the masked field");
                    assertNoInspectorTextLeak(fixture, TEST_ONLY_SECRET_SENTINEL);

                    ProcessDefinition.Connection connection = new ProcessDefinition.Connection(source.id, target.id, "payload");
                    controller.connections.add(connection);
                    controller.selectConnection(connection);
                    assertFalse(connectMenu.isVisible());
                    assertTrue(connect.isDisable());
                    assertEquals("Connection: Synthetic source → Synthetic cipher",
                            ((Label) fixture.scene.lookup("#selectedNodeLabel")).getText());

                    Pane canvas = (Pane) fixture.scene.lookup("#workflowCanvas");
                    canvas.fireEvent(mouseEvent(MouseEvent.MOUSE_CLICKED));
                    assertTrue(connect.isDisable());
                    assertFalse(connectMenu.isVisible());
                    assertTrue(controller.selectedNodeIds.isEmpty());

                    NodeExecutionEvent event = new NodeExecutionEvent(target.id, 1, target.label, target.type,
                            NodeExecutionState.SUCCESS, Duration.ZERO, Representation.BINARY, 16,
                            Representation.BINARY, 16, "OK");
                    ProcessDefinition executable = controller.toExecutableDefinition();
                    String telemetry = controller.renderExecutionResult(executable, Map.of(), List.of(event), null);
                    assertFalse(telemetry.contains(TEST_ONLY_SECRET_SENTINEL), profile + " telemetry leaked the synthetic secret");
                    if (profile == SecretVisibilityProfile.MASKED) {
                        assertTrue(telemetry.contains("key (HEX): ***MASKED***"), telemetry);
                    } else {
                        assertFalse(telemetry.contains("key (HEX)"), telemetry);
                    }
                    assertFalse(controller.processStatusLabel.getText().contains(TEST_ONLY_SECRET_SENTINEL));
                    assertFalse(controller.executionOutputArea.getText().contains(TEST_ONLY_SECRET_SENTINEL));
                    Object undoRedo = field(controller, "undoRedoCoordinator");
                    String undoState = describe(field(undoRedo, "undoStack"));
                    String redoState = describe(field(undoRedo, "redoStack"));
                    assertFalse(undoState.contains(TEST_ONLY_SECRET_SENTINEL), profile + " undo history leaked the secret");
                    assertFalse(redoState.contains(TEST_ONLY_SECRET_SENTINEL), profile + " redo history leaked the secret");
                    assertTrue(isolatedHistory.getHistoryItems().isEmpty());
                    assertEquals(originalShelf, shelf.getEntries());
                    transcript.add(profile + " connection-selection=shown blank-selection=cleared inspector=password-only"
                            + " telemetry=" + (profile == SecretVisibilityProfile.MASKED ? "masked" : "omitted")
                            + " undo-history=clear status=clear shelf=unchanged");
                } finally {
                    fixture.stage.close();
                }
            }
        });
        assertTranscript("76ea24e9f0beb0df58d8ad4b8e0fd978e7968ac2086c3d659173bd6c22d88fdd", transcript);
    }

    private static void assertNoInspectorTextLeak(Fixture fixture, String secret) {
        VBox inspector = (VBox) fixture.scene.lookup("#dynamicInspectorContainer");
        assertNotNull(inspector);
        assertTrue(inspector.lookupAll(".label").stream().filter(Label.class::isInstance)
                .map(Label.class::cast).noneMatch(label -> label.getText() != null && label.getText().contains(secret)));
        assertTrue(inspector.lookupAll(".text-field").stream().filter(javafx.scene.control.TextInputControl.class::isInstance)
                .map(javafx.scene.control.TextInputControl.class::cast)
                .filter(control -> !(control instanceof PasswordField))
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
            FXMLLoader loader = UiTestFxml.loader(ProcessDesignerSelectionCharacterizationUITest.class
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

    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
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

    private static String describe(Object value) {
        return describe(value, new IdentityHashMap<>());
    }

    private static String describe(Object value, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Number || value instanceof Enum<?>) return value.toString();
        if (value instanceof char[] secret) return new String(secret);
        if (seen.put(value, Boolean.TRUE) != null) return "<cycle>";
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder();
            map.forEach((key, item) -> result.append(describe(key, seen)).append('=').append(describe(item, seen)));
            return result.toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder result = new StringBuilder();
            collection.forEach(item -> result.append(describe(item, seen)));
            return result.toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder result = new StringBuilder();
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) result.append(describe(java.lang.reflect.Array.get(value, index), seen));
            return result.toString();
        }
        StringBuilder result = new StringBuilder(value.getClass().getSimpleName());
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    field.setAccessible(true);
                    result.append(describe(field.get(value), seen));
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            }
        }
        return result.toString();
    }

    private static void assertTranscript(String expected, List<String> transcript) throws Exception {
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", transcript).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, actual, String.join("\n", transcript));
    }

    private record Fixture(ProcessDesignerController controller, Scene scene, Stage stage) { }
}
