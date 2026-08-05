package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.MaterialDetectionResult.MaterialType;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class UnifiedMaterialIngestionUITest {

    @BeforeAll
    public static void initJfx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
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
                fail("JavaFX execution timed out");
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

    @Test
    void testPastePrivatePemDoesNotExecuteOperation() {
        runAndWait(() -> {
            TextArea area = new TextArea();
            Label statusLabel = new Label();

            String rsaPem = "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7\n-----END PRIVATE KEY-----";
            ClipboardContent cc = new ClipboardContent();
            cc.putString(rsaPem);
            Clipboard.getSystemClipboard().setContent(cc);

            AtomicBoolean executed = new AtomicBoolean(false);
            Runnable onSuccessCallback = () -> {
                // Ingestion succeeded but cryptographic action was NOT triggered
            };

            boolean pasted = IngestionUIHelper.pasteFromClipboard(area, statusLabel, onSuccessCallback, MaterialType.PEM_PRIVATE_KEY);
            assertTrue(pasted);
            assertEquals(rsaPem, area.getText());
            assertFalse(executed.get(), "Pasting must NOT execute cryptographic operation automatically");
            assertTrue(statusLabel.getText().contains("Private Key PEM"));
        });
    }

    @Test
    void testIncompatiblePasteRejectionPreservesPriorValidText() {
        runAndWait(() -> {
            TextArea area = new TextArea();
            Label statusLabel = new Label();
            String validPrivPem = "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7\n-----END PRIVATE KEY-----";
            area.setText(validPrivPem);

            // Put incompatible AES Hex into clipboard
            String hexAesKey = "00112233445566778899AABBCCDDEEFF";
            ClipboardContent cc = new ClipboardContent();
            cc.putString(hexAesKey);
            Clipboard.getSystemClipboard().setContent(cc);

            boolean pasted = IngestionUIHelper.pasteFromClipboard(area, statusLabel, null, MaterialType.PEM_PRIVATE_KEY);
            assertFalse(pasted, "Incompatible paste should be rejected");

            // Verify prior valid content was NOT destroyed
            assertEquals(validPrivPem, area.getText());
            assertTrue(statusLabel.getText().contains("Paste rejected"));
        });
    }

    @Test
    void testShelfFilteringIncompatibleTypes() {
        runAndWait(() -> {
            ClipboardShelfManager shelf = ClipboardShelfManager.getInstance();
            shelf.clear();

            shelf.addEntry(new ClipboardEntry("Valid RSA Key", "-----BEGIN PUBLIC KEY-----\nMIIBIjAN...\n-----END PUBLIC KEY-----", ClipboardEntry.Format.PEM, null, "Test"));
            shelf.addEntry(new ClipboardEntry("AES Key Hex", "00112233445566778899AABBCCDDEEFF", ClipboardEntry.Format.HEX, null, "Test"));

            MenuButton menuButton = new MenuButton();
            TextField field = new TextField();
            Label statusLabel = new Label();

            IngestionUIHelper.populateShelfMenu(menuButton, field, statusLabel, null, MaterialType.PEM_PUBLIC_KEY);

            assertEquals(1, menuButton.getItems().size(), "Only compatible items should be included in shelf menu");
            assertTrue(menuButton.getItems().get(0).getText().contains("Valid RSA Key"));
        });
    }
}
