package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class ProcessDesignerWindowUITest {

    private static boolean jfxIsSetup;

    @BeforeAll
    static void initJFX() {
        if (!jfxIsSetup) {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    latch.countDown();
                });
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            } catch (Throwable ignored) {
                try {
                    Platform.setImplicitExit(false);
                } catch (Throwable ignored2) {}
            }
            jfxIsSetup = true;
        }
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timeout waiting for JavaFX thread");
        if (error[0] != null) {
            throw new RuntimeException(error[0]);
        }
    }

    @Test
    void testOpenDetachedWindowAndRestoreWithoutStateDuplication() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();

                Stage primary = new Stage();
                primary.setScene(new Scene(root, 1000, 700));
                primary.show();

                // 1. Establish initial process state
                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                assertEquals(1, controller.toDefinition().nodes.size());
                controller.toDefinition().nodes.get(0).configuration.put("value", "initial test");

                Node initialContent = root.getContent();
                assertNotNull(initialContent, "Content inside root must not be null initially");

                // 2. Open detached window
                controller.handleOpenWindow();
                assertTrue(ProcessDesignerWindow.isShowing(), "ProcessDesignerWindow must be showing");
                assertNotNull(ProcessDesignerWindow.getStage(), "Detached stage must not be null");

                // Verify placeholder installed in original host
                assertTrue(root.getContent() instanceof Label, "Original root content must be replaced with placeholder");

                // Verify state remains intact in the detached view
                assertEquals(1, controller.toDefinition().nodes.size(), "Node count must be preserved in detached window");
                assertEquals("initial test", controller.toDefinition().nodes.get(0).configuration.get("value"));

                // 3. Close detached window and verify restoration
                ProcessDesignerWindow.close();
                assertFalse(ProcessDesignerWindow.isShowing(), "Window must not be showing after close");

                assertSame(initialContent, root.getContent(), "Original content must be restored into root TitledPane");
                assertEquals(1, controller.toDefinition().nodes.size(), "State must remain unchanged after re-docking");

                primary.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
