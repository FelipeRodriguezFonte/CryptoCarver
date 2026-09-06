package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
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
public class ProcessCanvasGeometryTest {

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
    void testCanvasExpandsWhenNodePlacedAtLargeCoordinates() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();

                Scene scene = new Scene(root, 950, 650);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();

                controller.handleClearCanvas();

                // Add a node at y = 2000
                controller.handleAddConsoleInput();
                ProcessDefinition.Node nodeY = controller.toDefinition().nodes.get(0);
                nodeY.y = 2000;
                nodeY.x = 100;
                controller.redraw();
                root.applyCss();
                root.layout();

                Pane canvas = (Pane) scene.lookup("#workflowCanvas");
                assertNotNull(canvas, "workflowCanvas must be present");
                assertTrue(canvas.getPrefHeight() >= 2000 + 120 + 200,
                        "Canvas pref height must expand to at least 2320, was: " + canvas.getPrefHeight());
                assertTrue(nodeY.y + 70 <= canvas.getPrefHeight(), "Node must be within canvas vertical bounds");

                // Add a node at x = 1400
                controller.handleAddHash();
                ProcessDefinition.Node nodeX = controller.toDefinition().nodes.get(1);
                nodeX.x = 1400;
                nodeX.y = 100;
                controller.redraw();
                root.applyCss();
                root.layout();

                assertTrue(canvas.getPrefWidth() >= 1400 + 220 + 200,
                        "Canvas pref width must expand to at least 1820, was: " + canvas.getPrefWidth());
                assertTrue(nodeX.x + 150 <= canvas.getPrefWidth(), "Node must be within canvas horizontal bounds");

                ScrollPane scrollPane = (ScrollPane) scene.lookup("#designerWorkspace");
                assertNotNull(scrollPane, "designerWorkspace ScrollPane must be present");
                assertTrue(canvas.getPrefWidth() > scrollPane.getViewportBounds().getWidth(),
                        "Canvas content width (" + canvas.getPrefWidth() + ") must exceed ScrollPane viewport width ("
                                + scrollPane.getViewportBounds().getWidth() + ")");
                assertTrue(canvas.getPrefHeight() > scrollPane.getViewportBounds().getHeight(),
                        "Canvas content height (" + canvas.getPrefHeight() + ") must exceed ScrollPane viewport height ("
                                + scrollPane.getViewportBounds().getHeight() + ")");

                stage.close();
            } catch (Exception e) {
                fail("Geometry test failed: " + e.getMessage());
            }
        });
    }
}
