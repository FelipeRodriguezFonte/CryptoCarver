package com.cryptoforge.ui;

import com.cryptoforge.model.OperationResult;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ASN1ControllerTest {

    private static boolean jfxIsSetup;

    @BeforeAll
    static void initJFX() throws InterruptedException {
        if (!jfxIsSetup) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
            } catch (IllegalStateException alreadyStarted) {
                // The suite shares a single JavaFX runtime. Another UI test may
                // have initialized it first in this same Surefire JVM.
                jfxIsSetup = true;
                return;
            }
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX failed to start within timeout");
            }
            jfxIsSetup = true;
        }
    }

    private <T> void setField(Object target, String name, T value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> exRef = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                exRef.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("runLater timed out");
        }
        if (exRef.get() != null) {
            throw new RuntimeException(exRef.get());
        }
    }

    private ASN1Controller controller;
    private AtomicReference<OperationResult> publishedResultRef;
    private AtomicReference<String> errorTitleRef;
    private AtomicReference<String> errorMsgRef;
    private StatusReporter mockReporter;

    @BeforeEach
    void setUp() throws Exception {
        controller = new ASN1Controller();
        publishedResultRef = new AtomicReference<>();
        errorTitleRef = new AtomicReference<>();
        errorMsgRef = new AtomicReference<>();

        mockReporter = new StatusReporter() {
            @Override public void updateStatus(String s) {}
            @Override public void updateInspector(String s, byte[] b1, byte[] b2, java.util.List<com.cryptoforge.model.OperationDetail> d) {}
            @Override public void addToHistory(String s, java.util.List<com.cryptoforge.model.OperationDetail> d) {}
            @Override public void showError(String t, String c) {
                errorTitleRef.set(t);
                errorMsgRef.set(c);
            }
            @Override public void showInfo(String t, String c) {}
            @Override public void publish(OperationResult result) {
                publishedResultRef.set(result);
            }
        };

        runAndWait(() -> {
            try {
                controller.init(mockReporter);
                setField(controller, "asn1InputFormatCombo", new ComboBox<String>());
                setField(controller, "asn1EncodeInputFormatCombo", new ComboBox<String>());
                setField(controller, "asn1EncodeTypeCombo", new ComboBox<String>());
                setField(controller, "asn1InputArea", new TextArea());
                setField(controller, "asn1EncodeInputArea", new TextArea());
                setField(controller, "asn1EncodeOutputArea", new TextArea());
                setField(controller, "asn1StatusLabel", new javafx.scene.control.Label());
                setField(controller, "asn1DetailsArea", new TextArea());
                setField(controller, "asn1TypeCombo", new ComboBox<String>());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testParseValidASN1() throws Exception {
        runAndWait(() -> {
            try {
                ComboBox<String> inputFormatCombo = getField(controller, "asn1InputFormatCombo");
                inputFormatCombo.setValue("Hexadecimal");
                
                ComboBox<String> typeCombo = getField(controller, "asn1TypeCombo");
                typeCombo.setValue("Auto-detect");

                TextArea inputArea = getField(controller, "asn1InputArea");
                inputArea.setText("300A02012A0C0548656C6C6F"); // Sequence(Int(42), UTF8String("Hello"))

                Method parse = ASN1Controller.class.getDeclaredMethod("handleParseASN1");
                parse.setAccessible(true);
                parse.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        OperationResult res = publishedResultRef.get();
        assertNotNull(res, "Parse should publish a result");
        assertEquals("ASN.1 Parse", res.getOperation());
        assertNotNull(res.getInput(), "Input should be recorded");
        assertTrue(res.getDetails().size() >= 2, "Should contain parsed details");
        assertTrue(res.getStatusMessage().contains("Parsed successfully"), "Status should indicate success");
    }

    @Test
    void testParseInvalidDER() throws Exception {
        runAndWait(() -> {
            try {
                ComboBox<String> inputFormatCombo = getField(controller, "asn1InputFormatCombo");
                inputFormatCombo.setValue("Hexadecimal");

                ComboBox<String> typeCombo = getField(controller, "asn1TypeCombo");
                typeCombo.setValue("Auto-detect");

                TextArea inputArea = getField(controller, "asn1InputArea");
                inputArea.setText("300C02012A0C05"); // Incomplete DER

                Method parse = ASN1Controller.class.getDeclaredMethod("handleParseASN1");
                parse.setAccessible(true);
                parse.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertNull(publishedResultRef.get(), "Should not publish on failure");
        assertNotNull(errorTitleRef.get(), "Should show error dialog");
        assertEquals("Parse Error", errorTitleRef.get());
    }

    @Test
    void testEncodeValidASN1() throws Exception {
        runAndWait(() -> {
            try {
                ComboBox<String> typeCombo = getField(controller, "asn1EncodeTypeCombo");
                typeCombo.setValue("INTEGER");

                ComboBox<String> formatCombo = getField(controller, "asn1EncodeInputFormatCombo");
                formatCombo.setValue("Decimal");

                TextArea inputArea = getField(controller, "asn1EncodeInputArea");
                inputArea.setText("42");

                Method encode = ASN1Controller.class.getDeclaredMethod("handleEncodeASN1");
                encode.setAccessible(true);
                encode.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        OperationResult res = publishedResultRef.get();
        assertNotNull(res, "Encode should publish a result");
        assertEquals("ASN.1 Encode", res.getOperation());
        assertNotNull(res.getOutput(), "Output should be recorded");
        assertTrue(res.getDetails().size() >= 3, "Should contain encode details");
        assertEquals("Encoded successfully", res.getStatusMessage());
    }

    @Test
    void testEncodeInvalidInput() throws Exception {
        runAndWait(() -> {
            try {
                ComboBox<String> typeCombo = getField(controller, "asn1EncodeTypeCombo");
                typeCombo.setValue("INTEGER");

                ComboBox<String> formatCombo = getField(controller, "asn1EncodeInputFormatCombo");
                formatCombo.setValue("Decimal");

                TextArea inputArea = getField(controller, "asn1EncodeInputArea");
                inputArea.setText("not-a-number");

                Method encode = ASN1Controller.class.getDeclaredMethod("handleEncodeASN1");
                encode.setAccessible(true);
                encode.invoke(controller);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertNull(publishedResultRef.get(), "Should not publish on failure");
        assertNotNull(errorTitleRef.get(), "Should show error dialog");
        assertEquals("ASN.1 Encode Error", errorTitleRef.get());
    }
}
