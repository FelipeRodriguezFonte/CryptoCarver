package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessEngine;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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
 * A connection made without naming a port is not portless: the engine later binds it to the
 * target's default input port. The canvas has to know that when it decides whether the port is
 * already taken, or the user builds a graph that only fails when they press Run.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class ProcessDesignerDuplicatePortUITest {

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

    private static ProcessDefinition.Node add(ProcessDesignerController c, String type) throws ReflectiveOperationException {
        Method m = ProcessDesignerController.class
                .getDeclaredMethod("addNode", String.class, String.class, double.class, double.class);
        m.setAccessible(true);
        return (ProcessDefinition.Node) m.invoke(c, type, type, 100.0, 100.0);
    }

    private static void connect(ProcessDesignerController c, ProcessDefinition.Node from, ProcessDefinition.Node to) {
        c.select(from);
        c.select(to);
        c.handleConnectSelected();
    }

    @Test
    void theDefaultConnectPathRefusesAPortAnExplicitLinkAlreadyHolds() throws Exception {
        onFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController c = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1200, 800));
                stage.show();

                c.handleClearCanvas();
                ProcessDefinition.Node first = add(c, "CONSOLE_INPUT");
                ProcessDefinition.Node second = add(c, "CONSOLE_INPUT");
                ProcessDefinition.Node hash = add(c, "HASH");

                // A link dropped on the port handle takes "input" explicitly.
                c.completeConnectionDragToPort(first, hash, "input");
                assertEquals(1, c.toDefinition().connections.size());

                // The select-two-blocks path must not stack a second link on that same port.
                connect(c, second, hash);

                ProcessDefinition definition = c.toDefinition();
                assertEquals(1, definition.connections.size(),
                        "the occupied port must be refused, not doubled up");
                assertDoesNotThrow(() -> ProcessEngine.validate(definition),
                        "whatever the canvas allows must survive preflight");
                stage.close();
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void reconnectingThroughTheDefaultPathStillReplacesTheEarlierDefaultLink() throws Exception {
        onFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                TitledPane root = loader.load();
                ProcessDesignerController c = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1200, 800));
                stage.show();

                c.handleClearCanvas();
                ProcessDefinition.Node first = add(c, "CONSOLE_INPUT");
                ProcessDefinition.Node second = add(c, "CONSOLE_INPUT");
                ProcessDefinition.Node hash = add(c, "HASH");

                connect(c, first, hash);
                assertEquals(1, c.toDefinition().connections.size());

                // Changing your mind about the source keeps working: one link, new source.
                connect(c, second, hash);
                ProcessDefinition definition = c.toDefinition();
                assertEquals(1, definition.connections.size(), "the earlier default link is replaced");
                assertEquals(second.id, definition.connections.get(0).from);
                assertDoesNotThrow(() -> ProcessEngine.validate(definition));
                stage.close();
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
