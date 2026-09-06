package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
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
public class ProcessDesignerConnectionUITest {

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
    void testConnectionViaPortDragAndKeyboardRoutes() throws Exception {
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

                // 1. Add Console Input (outputs TEXT_UTF8) and Hash (accepts BINARY)
                controller.handleAddConsoleInput();
                controller.handleAddHash();
                var nodes = controller.toDefinition().nodes;
                var input = nodes.get(0);
                var hash = nodes.get(1);

                Pane canvas = (Pane) scene.lookup("#workflowCanvas");
                assertNotNull(canvas);

                StackPane inputView = (StackPane) canvas.getChildren().stream()
                        .filter(n -> n instanceof StackPane && n.getLayoutX() == input.x)
                        .findFirst()
                        .orElse(null);
                assertNotNull(inputView, "Input view must exist");

                StackPane hashView = (StackPane) canvas.getChildren().stream()
                        .filter(n -> n instanceof StackPane && n.getLayoutX() == hash.x)
                        .findFirst()
                        .orElse(null);
                assertNotNull(hashView, "Hash view must exist");

                Circle outHandle = (Circle) inputView.getChildren().stream()
                        .filter(n -> n instanceof Circle)
                        .findFirst()
                        .orElse(null);
                assertNotNull(outHandle, "Output port handle circle must exist on node view");

                // Test interactive port drag: Press on outHandle, drag, and click/release on target
                outHandle.fireEvent(new MouseEvent(
                        MouseEvent.MOUSE_PRESSED,
                        0, 0, 150, 40,
                        MouseButton.PRIMARY, 1,
                        false, false, false, false,
                        true, false, false, false, false, false, null
                ));

                // Click on target hashView to complete drag connection
                hashView.fireEvent(new MouseEvent(
                        MouseEvent.MOUSE_PRESSED,
                        0, 0, 290, 40,
                        MouseButton.PRIMARY, 1,
                        false, false, false, false,
                        true, false, false, false, false, false, null
                ));

                ProcessDefinition def = controller.toDefinition();
                assertEquals(1, def.connections.size(), "Port drag must create connection between compatible nodes");
                assertEquals(input.id, def.connections.get(0).from);
                assertEquals(hash.id, def.connections.get(0).to);

                // 2. Test Incompatible Connection Feedback
                // Hash produces BINARY (or its output), try connecting Hash to Console Input (which has NO input ports)
                controller.completeConnectionDrag(hash, input);
                // Connection count must remain 1 (incompatible connection rejected)
                assertEquals(1, controller.toDefinition().connections.size(),
                        "Incompatible connection must be rejected");
                assertTrue(controller.executionOutputArea.getText().contains("Incompatible")
                                || controller.executionOutputArea.getText().contains("incompatible"),
                        "Incompatible connection attempt must display user feedback in executionOutputArea");

                // 3. Test Keyboard Accessible Route: Select 2 nodes -> Connect button
                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                controller.handleAddHexEncode();
                var newNodes = controller.toDefinition().nodes;
                var source = newNodes.get(0);
                var target = newNodes.get(1);

                controller.select(source);
                controller.select(target);

                Button connectBtn = (Button) scene.lookup("#connectSelectedButton");
                assertNotNull(connectBtn, "connectSelectedButton must be present");
                assertFalse(connectBtn.isDisabled(), "Connect button must be enabled when 2 nodes are selected");

                connectBtn.fireEvent(new javafx.event.ActionEvent());
                assertEquals(1, controller.toDefinition().connections.size(),
                        "Keyboard accessible route ('Select 2 -> Connect') must create connection");

                stage.close();
            } catch (Exception e) {
                fail("Connection UI test failed: " + e.getMessage());
            }
        });
    }
}
