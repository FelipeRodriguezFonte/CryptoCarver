package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduction and regression test for Blocker 1:
 * Verifies that user edits in the dynamic inspector (e.g. changing algorithm to AES/CBC/PKCS7Padding)
 * persist in the node's configuration and are NOT overwritten by stale bridge controls.
 */
@Tag("ui")
public class LunaBridgeOverwriteReproTest {

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

    private void runOnFxThread(Runnable action) throws Exception {
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
            fail("Timeout waiting for FX thread");
        }
        if (error[0] != null) {
            if (error[0] instanceof Exception e) throw e;
            throw new RuntimeException(error[0]);
        }
    }

    @Test
    void testInspectorEditsPersistAndAreNotOverwrittenByCompatibilityBridge() throws Exception {
        runOnFxThread(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController controller = loader.getController();

                Scene scene = new Scene(root, 950, 650);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
                root.applyCss();
                root.layout();

                controller.handleClearCanvas();
                controller.handleAddEncrypt();

                ProcessDefinition.Node enc = controller.toDefinition().nodes.get(0);
                controller.select(enc);

                // Inspector control for "algorithm"
                @SuppressWarnings("unchecked")
                ComboBox<String> algoCombo = (ComboBox<String>) controller.getInspectorControl("algorithm");
                assertNotNull(algoCombo, "Dynamic inspector must render an algorithm combo");

                // User edits the algorithm in the dynamic inspector
                algoCombo.setValue("AES/CBC/PKCS7Padding");

                // Save node settings
                controller.handleSaveNodeSettings();

                // Re-fetch definition to verify persistence
                ProcessDefinition def = controller.toDefinition();
                assertEquals("AES/CBC/PKCS7Padding", def.nodes.get(0).configuration.get("algorithm"),
                        "User edit in inspector must persist in node configuration");

                stage.close();
            } catch (Exception e) {
                fail("Test failed with exception: " + e.getMessage());
            }
        });
    }
}
