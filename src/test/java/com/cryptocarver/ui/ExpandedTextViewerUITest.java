package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Regression coverage for reusing the independent long-result viewer. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ExpandedTextViewerUITest {

    @BeforeAll
    static void startToolkit() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
            // The JavaFX toolkit is shared with the other UI tests.
        }
    }

    @Test
    void secondOpeningReplacesThePreviousResultAndTitle() throws Exception {
        runOnFxThread(() -> {
            ExpandedTextViewer viewer = new ExpandedTextViewer();
            viewer.show(null, "Expanded Result — DES Key Generation", "DES-RESULT");

            viewer.show(null, "Expanded Result — EdDSA Key Generation", "EDDSA-RESULT");

            TextArea content = getField(viewer, "contentArea");
            Stage stage = getField(viewer, "stage");
            assertEquals("EDDSA-RESULT", content.getText());
            assertEquals("Expanded Result — EdDSA Key Generation", stage.getTitle());
            assertEquals(0, content.getCaretPosition());
            stage.hide();
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void runOnFxThread(ThrowingRunnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX operation timed out");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
