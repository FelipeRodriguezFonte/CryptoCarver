package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ShellWindowLifecycleUITest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    private record Fixture(Stage window, WeakReference<Parent> root,
                           WeakReference<ModernMainController> shell, OperationExecutor executor) { }

    @Test void replacingSceneUnregistersOldShellAndShutsDownItsExecutor() throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            try {
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                fixture.set(new Fixture(stage, new WeakReference<>(root), new WeakReference<>(shell), shell.getOperationExecutor()));
                // Keep the window alive, as when the app installs another screen.
                stage.setScene(new Scene(new StackPane()));
            } catch (Exception error) { throw new AssertionError(error); }
        });
        Fixture owned = fixture.getAndSet(null);
        try {
            // submitRawTaskForTest intentionally ignores submissions after shutdown;
            // assert the executor state directly instead of expecting a rejection.
            assertTrue(executorField(owned.executor(), "workerExecutor").isShutdown());
            assertTrue(executorField(owned.executor(), "timerExecutor").isShutdown());
            assertReleased(owned.shell());
            assertReleased(owned.root());
            assertNotNull(owned.window().getScene(), "Replacement window must remain usable");
        } finally {
            UiTestLifecycleExtension.onFx(() -> { owned.window().close(); owned.window().setScene(null); });
        }
    }

    private static ExecutorService executorField(OperationExecutor executor, String name) throws Exception {
        var field = OperationExecutor.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ExecutorService) field.get(executor);
    }

    static void assertReleased(WeakReference<?> reference) throws Exception {
        for (int attempt = 0; attempt < 12 && reference.get() != null; attempt++) {
            UiTestLifecycleExtension.onFx(() -> { });
            System.gc();
            Thread.sleep(25);
        }
        assertNull(reference.get(), "Closed UI fixture is still strongly reachable");
    }
}
