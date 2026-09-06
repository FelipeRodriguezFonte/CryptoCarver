package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class ProcessCanvasPerformanceTest {

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
        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("UI test timed out waiting for FX thread");
        }
        if (error[0] != null) {
            if (error[0] instanceof Exception e) throw e;
            throw new RuntimeException(error[0]);
        }
    }

    @Test
    void testDraggingNodeDoesNotTriggerValidationDuringDragEvents() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();

                Scene scene = new Scene(root, 1100, 750);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                controller.handleClearCanvas();

                // Add 40 nodes to test scalability
                for (int i = 0; i < 40; i++) {
                    controller.handleAddHash();
                    ProcessDefinition.Node n = controller.toDefinition().nodes.get(i);
                    n.x = 40 + (i % 8) * 120;
                    n.y = 40 + (i / 8) * 80;
                }
                controller.redraw();

                Pane canvas = (Pane) scene.lookup("#workflowCanvas");
                assertNotNull(canvas);

                Node firstNodeView = canvas.getChildren().stream()
                        .filter(n -> n.getUserData() == null)
                        .findFirst()
                        .orElse(null);
                assertNotNull(firstNodeView, "First node view must exist");

                // Reset validation counter before drag session
                controller.validationCounter = 0;

                // Simulate MOUSE_PRESSED
                MouseEvent press = new MouseEvent(
                        MouseEvent.MOUSE_PRESSED,
                        0, 0, 100, 100,
                        MouseButton.PRIMARY, 1,
                        false, false, false, false,
                        true, false, false, false, false, false, null
                );
                firstNodeView.fireEvent(press);

                // Fire 100 MOUSE_DRAGGED events
                for (int i = 1; i <= 100; i++) {
                    MouseEvent drag = new MouseEvent(
                            MouseEvent.MOUSE_DRAGGED,
                            0, 0, 100 + i, 100 + i,
                            MouseButton.PRIMARY, 1,
                            false, false, false, false,
                            true, false, false, false, false, false, null
                    );
                    firstNodeView.fireEvent(drag);
                }

                // Invariant: Validation must NOT run inside MOUSE_DRAGGED
                assertEquals(0, controller.validationCounter,
                        "Validation counter must be 0 during dragging (no ProcessEngine.validate inside MOUSE_DRAGGED)");

                // When mouse is released, validation counter may increment at most once if redraw is needed
                MouseEvent release = new MouseEvent(
                        MouseEvent.MOUSE_RELEASED,
                        0, 0, 200, 200,
                        MouseButton.PRIMARY, 1,
                        false, false, false, false,
                        false, false, false, false, false, false, null
                );
                firstNodeView.fireEvent(release);

                assertTrue(controller.validationCounter <= 1,
                        "Validation counter after release must be <= 1, was: " + controller.validationCounter);

                stage.close();
            } catch (Exception e) {
                fail("Performance test failed: " + e.getMessage());
            }
        });
    }
}
