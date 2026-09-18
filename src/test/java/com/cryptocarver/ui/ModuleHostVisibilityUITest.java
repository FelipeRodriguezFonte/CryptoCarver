package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationRegistry;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every registered operation must put something on screen.
 *
 * <p>Modules load on demand into a {@link ModuleHost}, and nine module roots still carry
 * {@code visible="false" managed="false"} from when the shell inlined them with {@code
 * fx:include} — back then the root <em>was</em> the container the shell showed. With a host in
 * between, a root left hidden never appears and the host collapses to nothing: 33 operations
 * opened a blank pane while the shell reported the module as visible and the whole suite stayed
 * green, because the module really was loaded, wired and correct.
 *
 * <p>Nothing about a module's state distinguished that from working, so this measures what the
 * user gets instead: after navigating, the content area must hold a visible child with height.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModuleHostVisibilityUITest {

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
    void everyOperationRendersSomething() throws Exception {
        final ModernMainController[] shell = new ModernMainController[1];
        final Parent[] root = new Parent[1];
        fx(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                root[0] = loader.load();
                shell[0] = loader.getController();
                Stage stage = new Stage();
                stage.setScene(new Scene(root[0], 1400, 900));
                stage.show();
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });

        Method route = ModernMainController.class.getDeclaredMethod("handleItemSelected", String.class);
        route.setAccessible(true);
        Field contentContainer = ModernMainController.class.getDeclaredField("contentContainer");
        contentContainer.setAccessible(true);

        List<String> blank = new ArrayList<>();
        for (OperationDescriptor operation : OperationRegistry.getInstance().getAll()) {
            String name = operation.getTitle();
            fx(() -> {
                try {
                    route.invoke(shell[0], name);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
            fx(() -> {
                root[0].applyCss();
                root[0].layout();
                if (tallestVisibleChild(contentContainer, shell[0]) <= 1.0) blank.add(name);
            });
        }

        assertTrue(blank.isEmpty(),
                "These operations open an empty content area — their module is loaded but never shown: " + blank);
    }

    /** Height of the tallest child the content area is actually showing. */
    private static double tallestVisibleChild(Field contentContainer, ModernMainController shell) {
        try {
            Parent container = (Parent) contentContainer.get(shell);
            double tallest = 0;
            for (Node child : container.getChildrenUnmodifiable()) {
                if (!child.isVisible() || !child.isManaged()) continue;
                double height = child instanceof Region region
                        ? region.getHeight()
                        : child.getBoundsInParent().getHeight();
                tallest = Math.max(tallest, height);
            }
            return tallest;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not read the shell's content area", failure);
        }
    }
}
