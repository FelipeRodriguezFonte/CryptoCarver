package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TitledPane;
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
public class ProcessDesignerUndoRedoTest {

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
    void testUndoRedoPreservesStructuralEqualityAcrossCommandTypes() throws Exception {
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

                controller.handleClearCanvas();
                assertEquals(0, controller.toDefinition().nodes.size());

                // 1. Add Node Command
                controller.handleAddConsoleInput();
                assertEquals(1, controller.toDefinition().nodes.size());
                controller.handleUndo();
                assertEquals(0, controller.toDefinition().nodes.size(), "Undo Add Node must revert to 0 nodes");
                controller.handleRedo();
                assertEquals(1, controller.toDefinition().nodes.size(), "Redo Add Node must restore the node");

                // 2. Duplicate Node Command
                controller.select(controller.toDefinition().nodes.get(0));
                controller.handleDuplicateSelected();
                assertEquals(2, controller.toDefinition().nodes.size());
                controller.handleUndo();
                assertEquals(1, controller.toDefinition().nodes.size(), "Undo Duplicate must revert to 1 node");
                controller.handleRedo();
                assertEquals(2, controller.toDefinition().nodes.size(), "Redo Duplicate must restore to 2 nodes");

                // 3. Connect Nodes Command
                var n1 = controller.toDefinition().nodes.get(0);
                var n2 = controller.toDefinition().nodes.get(1);
                controller.select(n1);
                controller.select(n2);
                controller.handleConnectSelected();
                assertEquals(1, controller.toDefinition().connections.size());
                controller.handleUndo();
                assertEquals(0, controller.toDefinition().connections.size(), "Undo Connect must revert to 0 connections");
                controller.handleRedo();
                assertEquals(1, controller.toDefinition().connections.size(), "Redo Connect must restore the connection");

                // 4. Reverse Connection Command
                var conn = controller.toDefinition().connections.get(0);
                String fromBefore = conn.from;
                String toBefore = conn.to;
                controller.selectConnection(conn);
                controller.handleReverseSelectedConnection();
                var connReversed = controller.toDefinition().connections.get(0);
                assertEquals(fromBefore, connReversed.to, "Reversed connection destination must match old source");
                controller.handleUndo();
                assertEquals(fromBefore, controller.toDefinition().connections.get(0).from, "Undo Reverse must restore original direction");
                controller.handleRedo();
                assertEquals(fromBefore, controller.toDefinition().connections.get(0).to, "Redo Reverse must re-reverse direction");

                // 5. Delete Connection Command
                controller.selectConnection(controller.toDefinition().connections.get(0));
                controller.handleDeleteSelected();
                assertEquals(0, controller.toDefinition().connections.size(), "Delete connection must remove it");
                controller.handleUndo();
                assertEquals(1, controller.toDefinition().connections.size(), "Undo Delete connection must restore it");
                controller.handleRedo();
                assertEquals(0, controller.toDefinition().connections.size(), "Redo Delete connection must delete it again");

                // 6. Tidy Layout Command
                double origX = controller.toDefinition().nodes.get(0).x;
                controller.handleTidyLayout();
                controller.handleUndo();
                assertEquals(origX, controller.toDefinition().nodes.get(0).x, 0.001, "Undo Tidy Layout must restore original position");
                controller.handleRedo();

                // 7. Clear Canvas Command
                controller.handleClearCanvas();
                assertEquals(0, controller.toDefinition().nodes.size(), "Clear canvas must result in 0 nodes");
                controller.handleUndo();
                assertEquals(2, controller.toDefinition().nodes.size(), "Undo Clear Canvas must restore the 2 nodes");
                controller.handleRedo();
                assertEquals(0, controller.toDefinition().nodes.size(), "Redo Clear Canvas must clear canvas again");

                stage.close();
            } catch (Exception e) {
                fail("Undo/Redo test failed: " + e.getMessage());
            }
        });
    }

    @Test
    void testUndoRedoMoveNodeAndChangeConfiguration() throws Exception {
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

                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                var node = controller.toDefinition().nodes.get(0);
                controller.select(node);

                // Test Change Configuration Undo/Redo
                var valueControl = (javafx.scene.control.TextInputControl) controller.getInspectorControl("value");
                assertNotNull(valueControl);
                valueControl.setText("initial-value");
                controller.handleSaveNodeSettings();
                assertEquals("initial-value", controller.toDefinition().nodes.get(0).configuration.get("value"));

                valueControl.setText("updated-value");
                controller.handleSaveNodeSettings();
                assertEquals("updated-value", controller.toDefinition().nodes.get(0).configuration.get("value"));

                controller.handleUndo();
                assertEquals("initial-value", controller.toDefinition().nodes.get(0).configuration.get("value"), "Undo must restore previous configuration value");

                controller.handleRedo();
                assertEquals("updated-value", controller.toDefinition().nodes.get(0).configuration.get("value"), "Redo must restore new configuration value");

                // Test Move Node Undo/Redo via view mouse events
                controller.redraw();
                javafx.scene.layout.Pane canvas = (javafx.scene.layout.Pane) scene.lookup("#workflowCanvas");
                javafx.scene.Node nodeView = canvas.getChildren().stream()
                        .filter(child -> child instanceof javafx.scene.layout.StackPane)
                        .findFirst().orElse(null);
                assertNotNull(nodeView);

                var liveNode = controller.nodes.get(0);
                double initialX = liveNode.x;
                double initialY = liveNode.y;

                // Fire MOUSE_PRESSED to record start position
                nodeView.fireEvent(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                        10, 10, 100, 100,
                        javafx.scene.input.MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, false, null
                ));
                liveNode.x = 200;
                liveNode.y = 220;
                // Fire MOUSE_RELEASED to complete move and record undo command
                nodeView.fireEvent(new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                        10, 10, 100, 100,
                        javafx.scene.input.MouseButton.PRIMARY, 1,
                        false, false, false, false, false, false, false, false, false, false, null
                ));

                double movedX = controller.toDefinition().nodes.get(0).x;
                assertEquals(200, movedX, 0.001);

                controller.handleUndo();
                assertEquals(initialX, controller.toDefinition().nodes.get(0).x, 0.001, "Undo Move Node must restore initial X coordinate");
                assertEquals(initialY, controller.toDefinition().nodes.get(0).y, 0.001, "Undo Move Node must restore initial Y coordinate");

                controller.handleRedo();
                assertEquals(movedX, controller.toDefinition().nodes.get(0).x, 0.001, "Redo Move Node must restore moved X coordinate");

                stage.close();
            } catch (Exception e) {
                fail("Move node / Change configuration undo/redo test failed: " + e.getMessage());
            }
        });
    }
}
