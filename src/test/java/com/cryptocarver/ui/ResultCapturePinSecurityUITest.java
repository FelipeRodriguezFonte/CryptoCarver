package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ResultCapturePinSecurityUITest {
    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
    }

    @Test void clearPinEncodeAndDecodeCannotEscapeRestrictedProfiles() throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            AppSettings settings = AppSettings.getInstance();
            SecretVisibilityProfile previous = settings.getSecretVisibilityProfile(); String route = settings.getLastRoute();
            ClipboardShelfManager shelf = ClipboardShelfManager.getInstance(); ModernMainController shell = null;
            String clipboard = Clipboard.getSystemClipboard().getString();
            try {
                settings.setLastRoute(""); shelf.clear();
                var loader = Fxml.loader("/fxml/main-view-modern.fxml"); Parent root = loader.load(); shell = loader.getController();
                new javafx.scene.Scene(root, 1400, 900);
                shell.navigateToModule("Clear PIN Blocks"); root.applyCss(); root.layout();
                Field field = ModernMainController.class.getDeclaredField("paymentsController"); field.setAccessible(true);
                PaymentsController payments = (PaymentsController) field.get(shell);
                ((TextField) root.lookup("#pinField")).setText("1234");
                ((TextField) root.lookup("#panFieldEncode")).setText("4000001234567899");
                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    settings.setSecretVisibilityProfile(profile);
                    payments.handleEncodePinBlock();
                    Field publishedField = ModernMainController.class.getDeclaredField("lastPublishedResultSnapshot");
                    publishedField.setAccessible(true);
                    OperationResult encoded = (OperationResult) publishedField.get(shell);
                    assertNotNull(encoded); assertNotNull(encoded.getOutput());
                    String block = java.util.HexFormat.of().withUpperCase().formatHex(encoded.getOutput());
                    assertProtected(shell, shelf);
                    ((TextField) root.lookup("#pinBlockField")).setText(block);
                    ((TextField) root.lookup("#panFieldDecode")).setText("4000001234567899");
                    payments.handleDecodePinBlock();
                    assertEquals("Decode PIN Block", ((OperationResult) publishedField.get(shell)).getOperation());
                    assertProtected(shell, shelf);
                }
            } catch (Throwable failure) { error.set(failure); }
            finally {
                shelf.clear(); if (shell != null) shell.shutdown();
                settings.setSecretVisibilityProfile(previous); settings.setLastRoute(route);
                if (clipboard == null) Clipboard.getSystemClipboard().clear(); else putClipboard(clipboard);
                done.countDown();
            }
        });
        assertTrue(done.await(45, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
    }

    private enum PinSurface { DECODED, UNPROTECTED, MIXED_REPORT }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.EnumSource(PinSurface.class)
    void encryptedPinSurfacesCannotEscapeRestrictedProfiles(PinSurface surface) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            AppSettings settings = AppSettings.getInstance();
            SecretVisibilityProfile previous = settings.getSecretVisibilityProfile(); String route = settings.getLastRoute();
            ClipboardShelfManager shelf = ClipboardShelfManager.getInstance(); ModernMainController shell = null;
            String clipboard = Clipboard.getSystemClipboard().getString();
            java.io.ByteArrayOutputStream capturedLogs = new java.io.ByteArrayOutputStream();
            java.io.PrintStream previousOut = System.out, previousErr = System.err;
            java.io.PrintStream logStream = new java.io.PrintStream(capturedLogs, true, java.nio.charset.StandardCharsets.UTF_8);
            System.setOut(logStream); System.setErr(logStream);
            try {
                settings.setLastRoute(""); shelf.clear();
                var loader = Fxml.loader("/fxml/main-view-modern.fxml"); Parent root = loader.load(); shell = loader.getController();
                new javafx.scene.Scene(root, 1400, 900);
                shell.navigateToModule("Encrypted PIN Blocks"); root.applyCss(); root.layout();
                Field controllerField = ModernMainController.class.getDeclaredField("paymentsController"); controllerField.setAccessible(true);
                PaymentsController payments = (PaymentsController) controllerField.get(shell);
                Field publishedField = ModernMainController.class.getDeclaredField("lastPublishedResultSnapshot"); publishedField.setAccessible(true);
                String fixtureKey = "0123456789ABCDEFFEDCBA9876543210";
                ((TextField) root.lookup("#encPinField")).setText("1234");
                ((TextField) root.lookup("#encPanFieldEncode")).setText("4000001234567899");
                ((TextField) root.lookup("#encPinBlockKeyField")).setText(fixtureKey);
                ((TextField) root.lookup("#encPinBlockKeyFieldDecode")).setText(fixtureKey);
                ((TextField) root.lookup("#encPanFieldDecode")).setText("4000001234567899");
                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    settings.setSecretVisibilityProfile(profile);
                    ((TextField) root.lookup("#encPinBlockKeyField")).setText(fixtureKey);
                    payments.handleEncodeEncryptedPinBlock();
                    OperationResult encrypted = (OperationResult) publishedField.get(shell);
                    assertNotNull(encrypted); assertEquals("Encode Encrypted PIN Block", encrypted.getOperation());
                    assertNotNull(encrypted.getOutput());
                    switch (surface) {
                        case DECODED -> {
                            ((TextField) root.lookup("#encPinBlockFieldDecode")).setText(java.util.HexFormat.of().withUpperCase().formatHex(encrypted.getOutput()));
                            payments.handleDecodeEncryptedPinBlock();
                            OperationResult decoded = (OperationResult) publishedField.get(shell);
                            assertEquals("Decode Encrypted PIN Block", decoded.getOperation());
                            assertTrue(java.util.Arrays.equals("1234".getBytes(java.nio.charset.StandardCharsets.UTF_8), decoded.getOutput()), "The real handler must recover the invented PIN");
                            assertProtected(shell, shelf);
                        }
                        case UNPROTECTED -> {
                            ((TextField) root.lookup("#encPinBlockKeyField")).clear();
                            payments.handleEncodeEncryptedPinBlock();
                            assertEquals("Encode Encrypted PIN Block", ((OperationResult) publishedField.get(shell)).getOperation());
                            assertProtected(shell, shelf);
                        }
                        case MIXED_REPORT -> {
                            Field trackerField = ModernMainController.class.getDeclaredField("resultAreaTracker"); trackerField.setAccessible(true);
                            ResultAreaTracker tracker = (ResultAreaTracker) trackerField.get(shell);
                            javafx.scene.control.TextArea report = (javafx.scene.control.TextArea) root.lookup("#encResultArea");
                            tracker.register(report); tracker.focus(report); tracker.markUpdated(report);
                            assertTrue(tracker.isValidShelfCaptureArea(report), "Fixture must exercise a visible registered mixed report");
                            int pinOffset = report.getText().indexOf("1234");
                            assertTrue(pinOffset >= 0); report.selectRange(pinOffset, pinOffset + 4);
                            putClipboard("PIN_REPORT_SENTINEL");
                            var copySelection = ModernMainController.class.getDeclaredMethod("handleCopySecure", javafx.scene.control.TextArea.class, String.class, boolean.class);
                            copySelection.setAccessible(true); copySelection.invoke(shell, report, report.getSelectedText(), true);
                            assertTrue("PIN_REPORT_SENTINEL".equals(Clipboard.getSystemClipboard().getString()), "Restricted selection must not copy plain PIN material");
                            String ciphertext = shell.resolveCurrentOutputText();
                            assertFalse(ciphertext.isBlank() || ciphertext.equals("***MASKED***"), "The encrypted artifact remains available");
                            assertEquals(shell.renderPublishedResult(encrypted, profile), ciphertext);
                            shelf.clear(); shell.handleAddCurrentOutputToShelf();
                            assertTrue(shelf.getEntries().isEmpty(), "Restricted Shelf must not capture a report containing the clear block");
                            tracker.clearSelection();
                        }
                    }
                }
                String logs = capturedLogs.toString(java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(logs.contains(fixtureKey));
                assertFalse(logs.contains("4000001234567899"));
                assertFalse(logs.contains("041234FEDCBA9876"));
                assertFalse(java.util.regex.Pattern.compile("\\b1234\\b").matcher(logs).find(), "Invented PIN must not reach application logs");
            } catch (Throwable failure) { error.set(failure); }
            finally {
                shelf.clear(); if (shell != null) shell.shutdown();
                settings.setSecretVisibilityProfile(previous); settings.setLastRoute(route);
                if (clipboard == null) Clipboard.getSystemClipboard().clear(); else putClipboard(clipboard);
                System.setOut(previousOut); System.setErr(previousErr); logStream.close();
                done.countDown();
            }
        });
        assertTrue(done.await(45, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
    }

    private static void assertProtected(ModernMainController shell, ClipboardShelfManager shelf) {
        String expanded = shell.resolveCurrentOutputText();
        assertTrue(expanded.isBlank() || expanded.equals("***MASKED***"), "Clear PIN capture must be protected for expanded viewer");
        putClipboard("PIN_CAPTURE_SENTINEL"); shell.handleCopyOutput();
        assertEquals("PIN_CAPTURE_SENTINEL", Clipboard.getSystemClipboard().getString());
        shelf.clear(); shell.handleAddCurrentOutputToShelf(); assertTrue(shelf.getEntries().isEmpty());
    }
    private static void putClipboard(String value) { ClipboardContent content = new ClipboardContent(); content.putString(value); Clipboard.getSystemClipboard().setContent(content); }
}
