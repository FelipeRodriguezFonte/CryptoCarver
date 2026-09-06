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
public class ProcessCanvasZoomTest {

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
    void testZoomScalingProducesMatchingLogicalDisplacement() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();

                Scene scene = new Scene(root, 1000, 700);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                Pane canvas = (Pane) scene.lookup("#workflowCanvas");
                assertNotNull(canvas);

                double[] testScales = {0.5, 1.0, 2.0};
                for (double scale : testScales) {
                    controller.handleClearCanvas();
                    controller.setZoom(scale);
                    assertEquals(scale, controller.getZoom(), 0.001);

                    controller.handleAddConsoleInput();
                    ProcessDefinition.Node node = controller.toDefinition().nodes.get(0);
                    node.x = 100;
                    node.y = 100;
                    controller.redraw();

                    Node view = canvas.getChildren().stream()
                            .filter(n -> n.getUserData() == null && n.getLayoutX() == 100)
                            .findFirst()
                            .orElse(null);
                    assertNotNull(view, "Node view must exist on canvas");

                    double initialSceneX = 200.0;
                    double initialSceneY = 200.0;
                    double sceneDeltaX = 50.0;
                    double sceneDeltaY = 40.0;

                    // Simulate MOUSE_PRESSED
                    MouseEvent press = new MouseEvent(
                            MouseEvent.MOUSE_PRESSED,
                            initialSceneX, initialSceneY, initialSceneX, initialSceneY,
                            MouseButton.PRIMARY, 1,
                            false, false, false, false,
                            true, false, false, false, false, false, null
                    );
                    view.fireEvent(press);

                    // Simulate MOUSE_DRAGGED
                    MouseEvent drag = new MouseEvent(
                            MouseEvent.MOUSE_DRAGGED,
                            initialSceneX + sceneDeltaX, initialSceneY + sceneDeltaY, initialSceneX + sceneDeltaX, initialSceneY + sceneDeltaY,
                            MouseButton.PRIMARY, 1,
                            false, false, false, false,
                            true, false, false, false, false, false, null
                    );
                    view.fireEvent(drag);

                    double expectedLogicalDeltaX = sceneDeltaX / scale;
                    double expectedLogicalDeltaY = sceneDeltaY / scale;

                    // Note: snapToGrid might round to nearest 10
                    double expectedX = Math.round((100.0 + expectedLogicalDeltaX) / 10.0) * 10.0;
                    double expectedY = Math.round((100.0 + expectedLogicalDeltaY) / 10.0) * 10.0;

                    assertEquals(expectedX, node.x, 10.0,
                            "Logical displacement X at scale " + scale + " must match expected transform");
                    assertEquals(expectedY, node.y, 10.0,
                            "Logical displacement Y at scale " + scale + " must match expected transform");
                }

                stage.close();
            } catch (Exception e) {
                fail("Zoom drag test failed: " + e.getMessage());
            }
        });
    }
}
