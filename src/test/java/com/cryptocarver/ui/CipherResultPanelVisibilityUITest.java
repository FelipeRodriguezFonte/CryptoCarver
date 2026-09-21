package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class CipherResultPanelVisibilityUITest {

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

    @Test
    void hidesEmptyBoxAndRestoresActionsAndFormatWhenResultExists() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = UiTestFxml.loader("/fxml/cipher.fxml");
            try {
                loader.load();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            ResultPanel panel = (ResultPanel) loader.getNamespace().get("cipherResultPanel");
            TextArea input = (TextArea) loader.getNamespace().get("cipherInputArea");
            TextArea output = (TextArea) loader.getNamespace().get("cipherOutputArea");

            assertFalse(panel.isVisible(), "Empty result panel should not be displayed");
            assertFalse(panel.isManaged(), "Empty result panel should not occupy layout space");

            RecordingReporter reporter = new RecordingReporter();
            CipherController controller = loader.getController();
            controller.initModern(reporter, new ComboBox<>(), new ComboBox<>(), null);
            output.setText("A");

            assertTrue(panel.isVisible());
            assertTrue(panel.isManaged());

            HBox header = (HBox) panel.getChildren().get(0);
            @SuppressWarnings("unchecked")
            ComboBox<String> format = (ComboBox<String>) header.getChildren().get(4);
            assertTrue(format.isVisible(), "Result format selector should remain available");
            format.setValue("Hex");

            HBox actions = (HBox) panel.getChildren().get(3);
            assertEquals(5, actions.getChildren().size());
            for (int index = 0; index < actions.getChildren().size(); index++) {
                ((Button) actions.getChildren().get(index)).fire();
            }

            assertEquals(List.of("copy", "shelf", "expand", "save"), reporter.actions);
            assertEquals("41", input.getText(), "Use as input should retain the selected result format");

            output.clear();
            assertFalse(panel.isVisible());
            assertFalse(panel.isManaged());
        });
    }

    private static void onFxThread(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "FX thread did not complete");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static final class RecordingReporter implements StatusReporter {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void updateStatus(String message) {
        }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output,
                                    List<OperationDetail> details) {
        }

        @Override
        public void showError(String title, String message) {
        }

        @Override
        public void copyCurrentResult() {
            actions.add("copy");
        }

        @Override
        public void addCurrentResultToShelf() {
            actions.add("shelf");
        }

        @Override
        public void expandCurrentResult() {
            actions.add("expand");
        }

        @Override
        public void saveCurrentResultAsSessionStep() {
            actions.add("save");
        }
    }
}
