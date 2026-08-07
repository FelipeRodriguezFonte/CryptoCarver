package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class ClipboardShelfInjectionTest {

    @BeforeAll
    public static void initJfx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Platform already started
            latch.countDown();
        }
        latch.await(5, TimeUnit.SECONDS);
    }

    private void runAndWait(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("JavaFX runLater execution timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted waiting for JavaFX thread");
        }
        if (failure.get() != null) {
            Throwable throwable = failure.get();
            if (throwable instanceof AssertionError error) {
                throw error;
            }
            throw new AssertionError("Exception in JavaFX test action", throwable);
        }
    }

    private void setPrivateField(Object object, String fieldName, Object value) throws Exception {
        Class<?> clazz = object.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    @Test
    @EnabledIfSystemProperty(named = "runUiTests", matches = "true")
    void testGenericControllerManualInputHex() {
        runAndWait(() -> {
            GenericController controller = new GenericController();

            TextArea manualInputArea = new TextArea();
            ComboBox<String> manualInputFormatCombo = new ComboBox<>();
            manualInputFormatCombo.getItems().addAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Binary", "Decimal");

            try {
                setPrivateField(controller, "manualInputArea", manualInputArea);
                setPrivateField(controller, "manualInputFormatCombo", manualInputFormatCombo);
            } catch (Exception e) {
                throw new AssertionError("Could not inject GenericController fields", e);
            }

            controller.fillManualConversionInput("0a1b", ClipboardEntry.Format.HEX);

            assertEquals("0a1b", manualInputArea.getText());
            assertEquals("Hexadecimal", manualInputFormatCombo.getValue());
        });
    }

    @Test
    @EnabledIfSystemProperty(named = "runUiTests", matches = "true")
    void testCipherControllerInputText() {
        runAndWait(() -> {
            CipherController controller = new CipherController(null, null, null, null, null, null, null);

            TextArea cipherInputArea = new TextArea();
            ComboBox<String> cipherInputFormatCombo = new ComboBox<>();
            cipherInputFormatCombo.getItems().addAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Binary", "Decimal");

            try {
                setPrivateField(controller, "cipherInputArea", cipherInputArea);
                setPrivateField(controller, "cipherInputFormatCombo", cipherInputFormatCombo);
            } catch (Exception e) {
                throw new AssertionError("Could not inject CipherController fields", e);
            }

            controller.fillSymmetricCipherInput("HelloWorld", ClipboardEntry.Format.TEXT);

            assertEquals("HelloWorld", cipherInputArea.getText());
            assertEquals("Text (UTF-8)", cipherInputFormatCombo.getValue());
        });
    }
}
