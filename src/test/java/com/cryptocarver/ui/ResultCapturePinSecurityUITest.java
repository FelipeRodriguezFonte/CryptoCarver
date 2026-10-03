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

    private static void assertProtected(ModernMainController shell, ClipboardShelfManager shelf) {
        String expanded = shell.resolveCurrentOutputText();
        assertTrue(expanded.isBlank() || expanded.equals("***MASKED***"), "Clear PIN capture must be protected for expanded viewer");
        putClipboard("PIN_CAPTURE_SENTINEL"); shell.handleCopyOutput();
        assertEquals("PIN_CAPTURE_SENTINEL", Clipboard.getSystemClipboard().getString());
        shelf.clear(); shell.handleAddCurrentOutputToShelf(); assertTrue(shelf.getEntries().isEmpty());
    }
    private static void putClipboard(String value) { ClipboardContent content = new ClipboardContent(); content.putString(value); Clipboard.getSystemClipboard().setContent(content); }
}
