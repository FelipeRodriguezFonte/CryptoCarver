package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;

import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardShelfUX13UITest {

    @TempDir static Path tempDir;
    private static ClipboardShelfManager manager;

    @BeforeAll
    static void initJavaFX() throws Exception {
        if (!Platform.isFxApplicationThread()) {
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException ignored) {}
        }
        manager = ClipboardShelfManager.getInstance();
    }

    private void runAndWait(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
            Platform.runLater(() -> {
                try {
                    action.run();
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void testClipboardShelfUX13HonestEmptyStateAndMultiSelectionCompare() throws Exception {
        runAndWait(() -> {
            try {
                manager.clear();

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/clipboard_shelf.fxml"));
                Parent root = loader.load();
                ClipboardShelfController controller = loader.getController();

                Scene scene = new Scene(root, 950, 650);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();

                // 1. Verify honest empty state
                Label placeholder = (Label) controller.shelfTable.getPlaceholder();
                assertNotNull(placeholder);
                assertEquals("No saved laboratory results yet", placeholder.getText());

                // 2. Add 2 entries
                ClipboardEntry entry1 = new ClipboardEntry("Entry A", "00112233", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Hash", "SHA-256");
                ClipboardEntry entry2 = new ClipboardEntry("Entry B", "00119933", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Hash", "SHA-256");
                manager.addEntry(entry1);
                manager.addEntry(entry2);

                controller.refresh();

                // Select both entries
                controller.shelfTable.getSelectionModel().selectAll();

                // Verification: Compare button enabled for 2 items
                assertEquals(2, controller.shelfTable.getSelectionModel().getSelectedItems().size());
                assertFalse(controller.compareBtn.isDisabled(), "Compare button must be enabled when 2 comparable items are selected");

                stage.close();
            } catch (Exception e) {
                fail("UI Test failed: " + e.getMessage());
            }
        });
    }

    @Test
    void testMaskedAndRedactedProfileDoesNotLeakSecretsInShelf() throws Exception {
        runAndWait(() -> {
            try {
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);

                ClipboardEntry secretEntry = new ClipboardEntry(
                        "Secret Key", "SUPER_CONFIDENTIAL_KEY_123", ClipboardEntry.Format.TEXT,
                        OperationDetail.Classification.SECRET, "KeyGen"
                );
                manager.addEntry(secretEntry);

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/clipboard_shelf.fxml"));
                Parent root = loader.load();
                ClipboardShelfController controller = loader.getController();

                Scene scene = new Scene(root, 950, 650);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();

                controller.refresh();
                controller.shelfTable.getSelectionModel().select(0);

                // Verification: Redacted profile hides values from copy / use in / details
                assertTrue(controller.copyBtn.isDisabled(), "Copy must be disabled under REDACTED profile for secret entry");
                assertTrue(controller.detailsArea.getText().contains("[REDACTED]"), "Details area must show [REDACTED]");
                assertFalse(controller.detailsArea.getText().contains("SUPER_CONFIDENTIAL_KEY_123"), "Details area must NOT contain secret value");

                stage.close();
            } catch (Exception e) {
                fail("Redacted UI test failed: " + e.getMessage());
            } finally {
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
            }
        });
    }
}
