package com.cryptoforge.ui;

import com.cryptoforge.model.ClipboardEntry;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("JavaFX runLater execution timed out");
            }
        } catch (InterruptedException e) {
            fail("Interrupted waiting for JavaFX thread");
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
            try {
                GenericController controller = new GenericController();
                
                TextArea manualInputArea = new TextArea();
                ComboBox<String> manualInputFormatCombo = new ComboBox<>();
                manualInputFormatCombo.getItems().addAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Binary", "Decimal");
                
                setPrivateField(controller, "manualInputArea", manualInputArea);
                setPrivateField(controller, "manualInputFormatCombo", manualInputFormatCombo);
                
                controller.fillManualConversionInput("0a1b", ClipboardEntry.Format.HEX);
                
                assertEquals("0a1b", manualInputArea.getText());
                assertEquals("Hexadecimal", manualInputFormatCombo.getValue());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    @EnabledIfSystemProperty(named = "runUiTests", matches = "true")
    void testCipherControllerInputText() {
        runAndWait(() -> {
            try {
                CipherController controller = new CipherController(null, null, null, null, null, null, null);
                
                TextArea inputArea = new TextArea();
                ComboBox<String> inputFormatCombo = new ComboBox<>();
                inputFormatCombo.getItems().addAll("Text (UTF-8)", "Hexadecimal", "Base64", "Base64URL", "Binary", "Decimal");
                
                setPrivateField(controller, "inputArea", inputArea);
                setPrivateField(controller, "inputFormatCombo", inputFormatCombo);
                
                controller.fillSymmetricCipherInput("HelloWorld", ClipboardEntry.Format.TEXT);
                
                assertEquals("HelloWorld", inputArea.getText());
                assertEquals("Text (UTF-8)", inputFormatCombo.getValue());
            } catch (Exception e) {
                fail(e);
            }
        });
    }
}
