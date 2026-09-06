package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
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
public class LunaSecretLeakReproTest {

    private static boolean jfxIsSetup;

    @BeforeAll
    static void initJFX() {
        if (!jfxIsSetup) {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                Platform.startup(() -> { Platform.setImplicitExit(false); latch.countDown(); });
                latch.await(5, TimeUnit.SECONDS);
            } catch (Throwable ignored) {
                try { Platform.setImplicitExit(false); } catch (Throwable ignored2) {}
            }
            jfxIsSetup = true;
        }
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable t) { error[0] = t; } finally { latch.countDown(); }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) fail("timed out");
        if (error[0] != null) {
            if (error[0] instanceof RuntimeException re) throw re;
            if (error[0] instanceof Error err) throw err;
            throw new RuntimeException(error[0]);
        }
    }

    @Test
    void sensitiveParametersAreNeverWrittenToNodeConfiguration() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1200, 800));
                stage.show();

                controller.handleClearCanvas();
                controller.handleAddEncrypt();
                ProcessDefinition.Node node = controller.toDefinition().nodes.get(0);
                controller.select(node);

                ((ComboBox<String>) controller.getInspectorControl("keyFormat")).setValue("HEX");
                ((TextInputControl) controller.getInspectorControl("key")).setText("00112233445566778899AABBCCDDEEFF");
                controller.handleSaveNodeSettings();

                String keyInConfig = node.configuration.get("key");
                stage.close();

                assertNull(keyInConfig, "Sensitive parameter 'key' was written into node.configuration: " + keyInConfig);
                assertNotNull(controller.getTransientSecret(node.id, "key"), "Secret key must be preserved in transientSecrets");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void undoDoesNotRestorePlaintextKeyIntoConfiguration() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1200, 800));
                stage.show();

                controller.handleClearCanvas();
                controller.handleAddEncrypt();
                ProcessDefinition.Node node = controller.toDefinition().nodes.get(0);
                controller.select(node);

                ((ComboBox<String>) controller.getInspectorControl("keyFormat")).setValue("HEX");
                ((TextInputControl) controller.getInspectorControl("key")).setText("00112233445566778899AABBCCDDEEFF");
                controller.handleSaveNodeSettings();

                // Clear canvas (empties nodes and transientSecrets)
                controller.handleClearCanvas();
                assertEquals(0, controller.toDefinition().nodes.size());

                // Ctrl+Z / undo restores the node
                controller.handleUndo();
                assertEquals(1, controller.toDefinition().nodes.size());

                ProcessDefinition.Node restored = controller.toDefinition().nodes.get(0);
                boolean containsKey = restored.configuration.containsKey("key");
                stage.close();

                assertFalse(containsKey, "Undo restored a node whose configuration still carries the plaintext key");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
