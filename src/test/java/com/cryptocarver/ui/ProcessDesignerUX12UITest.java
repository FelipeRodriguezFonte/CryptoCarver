package com.cryptocarver.ui;

import com.cryptocarver.model.process.DryRunSummary;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessValidator;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerUX12UITest {

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
    void testProcessDesignerUX12ValidationDryRunAndInspector() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();
                assertNotNull(controller, "ProcessDesignerController must be loaded");

                Scene scene = new Scene(root, 950, 650);
                URL cssResource = getClass().getResource("/css/styles.css");
                assertNotNull(cssResource, "styles.css must exist");
                scene.getStylesheets().add(cssResource.toExternalForm());

                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();

                root.applyCss();
                root.layout();

                // 1. Clear default preset and add Console Input & Hash steps
                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                controller.handleAddHash();

                // 2. Execute Dry Run
                controller.handleDryRunProcess();
                String text = controller.executionOutputArea.getText();
                assertTrue(text.contains("PROCESS DESIGNER DRY RUN"), "Output must contain header");
                assertTrue(text.contains("Total Steps: 2"), "Output must report Total Steps: 2 but was: " + text);
                assertTrue(text.contains("Resolved Dependencies:"), "Output must show resolved dependencies");
                assertTrue(text.contains("Execution Order:"), "Output must show execution order");
                assertTrue(text.contains("0 cryptographic operations executed, 0 files written, 0 history entries created"),
                        "Dry Run must explicitly confirm zero side effects");

                stage.close();
            } catch (Exception e) {
                fail("UI test failed: " + e.getMessage());
            }
        });
    }

    @Test
    void testProcessDesignerUX12CancellationAndInspection() throws Exception {
        CountDownLatch finishedLatch = new CountDownLatch(1);
        final ProcessDesignerController[] controllerRef = new ProcessDesignerController[1];
        final ProcessDefinition[] defRef = new ProcessDefinition[1];
        final Stage[] stageRef = new Stage[1];
        final java.util.List<com.cryptocarver.model.process.NodeExecutionEvent> recordedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();

        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();
                controllerRef[0] = controller;

                Scene scene = new Scene(root, 950, 650);
                Stage stage = new Stage();
                stageRef[0] = stage;
                stage.setScene(scene);
                stage.show();

                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                controller.nodeValueArea.setText("Payload to cancel");
                controller.handleSaveNodeSettings();
                controller.handleAddHash();

                ProcessDefinition def = controller.toDefinition();
                defRef[0] = def;

                controller.connections.add(new ProcessDefinition.Connection(def.nodes.get(0).id, def.nodes.get(1).id, "input"));
                controller.onExecutionFinished = finishedLatch::countDown;

                // Request cancellation DURING step 1 execution (when step 1 emits RUNNING event)
                controller.onNodeExecutionEvent = event -> {
                    recordedEvents.add(event);
                    if (event.state() == com.cryptocarver.model.process.NodeExecutionState.RUNNING && event.step() == 1) {
                        controller.handleCancelProcess();
                    }
                };

                // 1. Start execution with cancellation NOT active beforehand
                controller.handleRunProcess();

            } catch (Exception e) {
                fail("Cancellation UI test setup failed: " + e.getMessage());
            }
        });

        // Wait for process execution and UI rendering to complete
        assertTrue(finishedLatch.await(5, TimeUnit.SECONDS), "Process execution must finish within 5 seconds");

        // Verify UI state after cancellation on FX Application Thread
        runAndWait(() -> {
            try {
                ProcessDesignerController controller = controllerRef[0];
                ProcessDefinition def = defRef[0];

                // Verification 1: Step 2 node produces NO event
                boolean step2EventFired = recordedEvents.stream().anyMatch(e -> def.nodes.get(1).id.equals(e.nodeId()));
                assertFalse(step2EventFired, "Step 2 node must NOT produce any execution event");

                // Verification 2: Step 2 produces NO result in the status table
                int tableCount = controller.executionStatusTable.getItems().size();
                assertEquals(1, tableCount, "Only step 1 completed result must be present in execution table");

                // Verification 3: Step 2 node is NOT present in completed table rows
                ProcessExecutionRow row = controller.executionStatusTable.getItems().get(0);
                assertEquals(def.nodes.get(0).id, row.getNodeId(), "Execution table must contain only step 1 ID");

                // Verification 4: Status label & progress bar confirm safe cancellation after step 1
                String expectedCancellation = I18nService.getInstance().text("module.process.cancelled", 1);
                assertEquals(expectedCancellation, controller.processStatusLabel.getText(),
                        "Status label must report the localized cancellation state");
                assertTrue(controller.processProgressBar.getProgress() < 1.0,
                        "Progress bar must be strictly capped below 1.0 upon cancellation");

                stageRef[0].close();
            } catch (Exception e) {
                fail("Cancellation UI test assertion failed: " + e.getMessage());
            }
        });
    }

    @Test
    void testProcessDesignerUX12ValidationSelectionAndNarrowLayout() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();
                assertNotNull(controller);

                // Set narrow layout width 400px
                Scene scene = new Scene(root, 400, 600);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();

                root.applyCss();
                root.layout();

                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                controller.handleDryRunProcess();

                // Select validation step in execution table
                if (!controller.executionStatusTable.getItems().isEmpty()) {
                    controller.executionStatusTable.getSelectionModel().select(0);
                    ProcessExecutionRow row = controller.executionStatusTable.getSelectionModel().getSelectedItem();
                    assertNotNull(row);
                    controller.selectNodeById(row.getNodeId());
                }

                stage.close();
            } catch (Exception e) {
                fail("Validation selection & narrow layout UI test failed: " + e.getMessage());
            }
        });
    }

    @Test
    void testBatchRunnerUX12DryRun() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("step1", "CONSOLE_INPUT", "Step 1", 0, 0);
        n1.configuration.put("value", "test");
        def.nodes.add(n1);

        DryRunSummary summary = ProcessValidator.dryRun(def);
        assertEquals(1, summary.totalSteps());
        assertEquals(1, summary.readyCount());
        assertEquals(0, summary.blockedCount());
    }
}
