package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every section must actually render something when the user opens it.
 *
 * <p>Modules load on demand into a {@link ModuleHost}. Nine module roots still carry
 * {@code visible="false" managed="false"} from when the shell inlined them with {@code
 * fx:include} — back then the root *was* the container the shell showed, so starting hidden
 * was right. With a host in between, a root left hidden never appears: the section opened on a
 * blank pane, with the shell reporting the module as visible and every existing test passing,
 * because the module was loaded, wired and correct. Only its height gave it away.
 *
 * <p>So this asserts on height. The shell loads this module without a user, so the assertion
 * is about what a user would see.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModuleHostVisibilityUITest {

    /** One operation per module, chosen to route to a different host each time. */
    private static final Map<String, String> OPERATION_BY_HOST = new LinkedHashMap<>(Map.of(
            "Symmetric Ciphers", "cipherContainer",
            "Key Lab", "keysContainer",
            "Hashing", "genericContainer",
            "Digital Signatures", "authenticationContainer",
            "Clear PIN Blocks", "paymentsContainer"));

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "FX thread did not run the action");
    }

    @Test
    void openingASectionRendersItsModule() throws Exception {
        final ModernMainController[] shell = new ModernMainController[1];
        fx(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                shell[0] = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 1400, 900));
                stage.show();
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });

        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);

        for (Map.Entry<String, String> entry : OPERATION_BY_HOST.entrySet()) {
            fx(() -> {
                try {
                    route.invoke(shell[0], entry.getKey());
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            fx(() -> {
                ModuleHost host = readHost(shell[0], entry.getValue());
                assertTrue(host.isVisible() && host.isManaged(),
                        entry.getKey() + " must show " + entry.getValue());
                assertTrue(host.prefHeight(-1) > 0,
                        entry.getKey() + " opened an empty pane: " + entry.getValue()
                                + " has no height, so the module is loaded but never displayed");
            });
        }
    }

    private static ModuleHost readHost(ModernMainController shell, String field) {
        try {
            java.lang.reflect.Field declared = ModernMainController.class.getDeclaredField(field);
            declared.setAccessible(true);
            return (ModuleHost) declared.get(shell);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("No module host named " + field, failure);
        }
    }
}
