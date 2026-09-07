package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessDefinitionCodec;
import com.cryptocarver.model.process.ProcessEngine;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A node whose required inputs are sensitive is configured entirely through the inspector, so its
 * values live in the session rather than in the definition. Preflight must still be able to tell
 * that node apart from one nobody has filled in.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class ProcessDesignerSuppliedSecretPreflightUITest {

    private static boolean jfx;

    @BeforeAll
    static void initJFX() {
        if (!jfx) {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.startup(() -> { Platform.setImplicitExit(false); latch.countDown(); });
                latch.await(5, TimeUnit.SECONDS);
            } catch (Throwable ignored) {
                try { Platform.setImplicitExit(false); } catch (Throwable ignored2) {}
            }
            jfx = true;
        }
    }

    private void onFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable t) { error[0] = t; } finally { latch.countDown(); }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) fail("UI test timed out");
        if (error[0] instanceof RuntimeException re) throw re;
        if (error[0] instanceof Error e) throw e;
        if (error[0] != null) throw new RuntimeException(error[0]);
    }

    private static ProcessDefinition.Node addNode(ProcessDesignerController controller, String type)
            throws ReflectiveOperationException {
        Method add = ProcessDesignerController.class
                .getDeclaredMethod("addNode", String.class, String.class, double.class, double.class);
        add.setAccessible(true);
        return (ProcessDefinition.Node) add.invoke(controller, type, type, 100.0, 100.0);
    }

    @Test
    void aPaymentNodeConfiguredThroughTheInspectorPassesPreflightAndKeepsItsSecretsOut() throws Exception {
        onFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1200, 800));
                stage.show();

                controller.handleClearCanvas();
                ProcessDefinition.Node node = addNode(controller, "PIN_BLOCK_ENCODE");
                controller.select(node);

                // Nothing filled in yet: the node is genuinely incomplete.
                assertThrows(IllegalArgumentException.class,
                        () -> ProcessEngine.validate(controller.toDefinition()),
                        "an unconfigured payment node must fail preflight");

                ((PasswordField) controller.getInspectorControl("pin")).setText("1234");
                ((PasswordField) controller.getInspectorControl("pan")).setText("4111111111111111");
                controller.handleSaveNodeSettings();

                assertDoesNotThrow(() -> ProcessEngine.validate(controller.toDefinition()),
                        "a fully configured payment node must not break validation of the live graph");

                // The values stay out of the model; only the marker is there.
                assertNull(node.configuration.get("pan"), "PAN must not reach node.configuration");
                assertNull(node.configuration.get("pin"), "PIN must not reach node.configuration");
                assertTrue(NodeCatalog.isSupplied(node, "pan"));
                assertTrue(NodeCatalog.isSupplied(node, "pin"));

                // Saving the process drops the marker, so reopening reports the secret as missing.
                String json = ProcessDefinitionCodec.serialize(controller.toDefinition());
                assertFalse(json.contains("4111111111111111"));
                assertFalse(json.contains("FromSecrets"));
                ProcessDefinition reopened = ProcessDefinitionCodec.deserialize(json);
                assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(reopened),
                        "a reopened process must ask for its secrets again");

                stage.close();
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void clearingTheFieldRemovesTheMarkerAgain() throws Exception {
        onFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1200, 800));
                stage.show();

                controller.handleClearCanvas();
                ProcessDefinition.Node node = addNode(controller, "PIN_BLOCK_ENCODE");
                controller.select(node);
                ((PasswordField) controller.getInspectorControl("pin")).setText("1234");
                ((PasswordField) controller.getInspectorControl("pan")).setText("4111111111111111");
                controller.handleSaveNodeSettings();
                assertTrue(NodeCatalog.isSupplied(node, "pan"));

                ((PasswordField) controller.getInspectorControl("pan")).setText("");
                controller.handleSaveNodeSettings();

                assertFalse(NodeCatalog.isSupplied(node, "pan"),
                        "emptying the field must retract the marker, not leave a stale claim");
                assertThrows(IllegalArgumentException.class,
                        () -> ProcessEngine.validate(controller.toDefinition()));

                stage.close();
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
