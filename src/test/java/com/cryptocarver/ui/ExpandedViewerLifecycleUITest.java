package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ExpandedViewerLifecycleUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    private record Released(Object viewer, WeakReference<Stage> stage, WeakReference<Scene> scene) { }

    @Test void closingTextViewerReleasesItsSceneWhileViewerRemainsAlive() throws Exception {
        AtomicReference<Released> result = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            ExpandedTextViewer viewer = new ExpandedTextViewer();
            viewer.show(null, "Lifecycle probe", "Public lifecycle fixture");
            Stage stage = field(viewer, "stage");
            result.set(new Released(viewer, new WeakReference<>(stage), new WeakReference<>(stage.getScene())));
            viewer.hide();
            assertNull(field(viewer, "stage"));
            assertNull(field(viewer, "contentArea"));
            viewer.show(null, "Reopened", "Fresh public snapshot");
            assertEquals("Fresh public snapshot", ((TextArea) field(viewer, "contentArea")).getText());
            viewer.hide();
        });
        ShellWindowLifecycleUITest.assertReleased(result.get().scene());
        ShellWindowLifecycleUITest.assertReleased(result.get().stage());
        assertNotNull(result.get().viewer());
    }

    @Test void closingTableViewerDropsItsSnapshotAndScene() throws Exception {
        AtomicReference<Released> result = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            ExpandedTableViewer viewer = new ExpandedTableViewer();
            viewer.show(null, "Lifecycle probe", new TableView<>());
            Stage stage = field(viewer, "stage");
            result.set(new Released(viewer, new WeakReference<>(stage), new WeakReference<>(stage.getScene())));
            stage.close();
            assertNull(field(viewer, "expandedTable"));
            assertEquals(java.util.List.of(), field(viewer, "allRows"));
            viewer.show(null, "Reopened", new TableView<>());
            viewer.dispose();
        });
        ShellWindowLifecycleUITest.assertReleased(result.get().scene());
        ShellWindowLifecycleUITest.assertReleased(result.get().stage());
        assertNotNull(result.get().viewer());
    }

    @Test void shellShutdownClosesAnOpenResultWindow() throws Exception {
        AtomicReference<Released> result = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            ModernMainController shell = new ModernMainController();
            ExpandedTextViewer viewer = field(shell, "expandedTextViewer");
            viewer.show(null, "Lifecycle probe", "Public snapshot");
            Stage stage = field(viewer, "stage");
            result.set(new Released(shell, new WeakReference<>(stage), new WeakReference<>(stage.getScene())));
            shell.shutdown();
            assertFalse(stage.isShowing());
            assertNull(field(viewer, "stage"));
        });
        ShellWindowLifecycleUITest.assertReleased(result.get().scene());
        ShellWindowLifecycleUITest.assertReleased(result.get().stage());
        assertNotNull(result.get().viewer(), "The shell is still alive during the GC assertion");
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
