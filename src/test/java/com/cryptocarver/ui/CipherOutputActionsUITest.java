package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class CipherOutputActionsUITest {

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
    void oneOutputAreaAndGlobalFormatKeepAllFiveResultActions() throws Exception {
        onFxThread(() -> {
            FXMLLoader loader = UiTestFxml.loader("/fxml/cipher.fxml");
            VBox root;
            try {
                root = loader.load();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            assertNull(loader.getNamespace().get("cipherResultPanel"));
            assertFalse(root.getChildren().stream().anyMatch(ResultPanel.class::isInstance));

            TextArea input = (TextArea) loader.getNamespace().get("cipherInputArea");
            TextArea output = (TextArea) loader.getNamespace().get("cipherOutputArea");
            Button copy = button(loader, "cipherCopyResultButton");
            Button shelf = button(loader, "cipherShelfResultButton");
            Button expand = button(loader, "cipherExpandResultButton");
            Button save = button(loader, "cipherSaveResultButton");
            Button chain = button(loader, "cipherUseResultButton");

            FlowPane actions = (FlowPane) copy.getParent();
            assertSame(output.getParent(), actions.getParent());
            assertEquals(List.of(copy, shelf, expand, save, chain), actions.getChildren());
            assertTrue(((VBox) output.getParent()).getChildren().stream()
                    .noneMatch(ComboBox.class::isInstance), "No second output-format selector");
            assertEquals(I18nService.getInstance().text("resultPanel.action.copy"), copy.getText());

            RecordingReporter reporter = new RecordingReporter();
            ComboBox<String> outputFormat = new ComboBox<>();
            outputFormat.setValue("Hexadecimal");
            ((CipherController) loader.getController()).initModern(
                    reporter, new ComboBox<>(), outputFormat, null);

            output.setText("39F8FF00");
            copy.fire();
            shelf.fire();
            expand.fire();
            save.fire();
            chain.fire();

            assertEquals(List.of("copy", "shelf", "expand", "save"), reporter.actions);
            assertEquals("39F8FF00", output.getText());
            assertEquals("39F8FF00", input.getText());
            assertEquals("Hexadecimal", outputFormat.getValue());
        });
    }

    private static Button button(FXMLLoader loader, String id) {
        return (Button) loader.getNamespace().get(id);
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
