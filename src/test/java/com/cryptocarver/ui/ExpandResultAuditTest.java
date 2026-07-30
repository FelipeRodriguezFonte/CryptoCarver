package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ExpandResultAuditTest {

    private static boolean jfxIsSetup;
    private static String originalUserHome;

    @TempDir
    static Path isolatedUserHome;

    @BeforeAll
    static void initJFX() throws InterruptedException {
        System.setProperty("test.mode", "true");
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", isolatedUserHome.toString());
        if (!jfxIsSetup) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    latch.countDown();
                });
                if (!latch.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX failed to start within timeout");
                }
            } catch (IllegalStateException e) {
                Platform.setImplicitExit(false);
            }
            jfxIsSetup = true;
        }
    }

    @BeforeEach
    void resetSettings() {
        AppSettings.getInstance().resetForTesting();
        ClipboardShelfManager.getInstance().clear();
    }

    @AfterAll
    static void restoreUserHome() {
        if (originalUserHome == null) {
            System.clearProperty("user.home");
        } else {
            System.setProperty("user.home", originalUserHome);
        }
    }

    private void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> exceptionRef = new java.util.concurrent.atomic.AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                exceptionRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX runLater execution timed out");
        }

        if (exceptionRef.get() != null) {
            if (exceptionRef.get() instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(exceptionRef.get());
        }
    }

    private <T> T getField(Object target, String name) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + target.getClass());
    }

    private String getStatusMessage(ModernMainController controller) throws Exception {
        Label statusLabel = getField(controller, "statusLabel");
        return statusLabel != null ? statusLabel.getText() : "";
    }

    @Test
    void testKdfPublishAndActionResolutionConsistency() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                HBox summaryBar = getField(controller, "resultSummaryBar");

                String kdfReport = "=== KDF DERIVATION REPORT ===\nAlgorithm: PBKDF2withHmacSHA256\nDerived Key (Hex): AABBCCDD\nKey Length: 128 bits";
                byte[] derivedBytes = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};

                controller.publish(OperationResult.forOperation("KDF Derivation")
                        .output(derivedBytes, OperationDetail.Classification.SECRET)
                        .enrichedOutput(kdfReport, OperationDetail.Classification.SECRET)
                        .status("KDF completed").build());

                assertTrue(summaryBar.isVisible());
                assertTrue(summaryBar.isManaged());

                String resolvedOutput = controller.resolveCurrentOutputText();
                assertNotNull(resolvedOutput);
                assertTrue(resolvedOutput.contains("KDF DERIVATION REPORT") || resolvedOutput.contains("AABBCCDD") || resolvedOutput.contains("***MASKED***"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testCipherHashingConversionSignatureCertResolution() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                HBox summaryBar = getField(controller, "resultSummaryBar");

                // Cipher
                controller.publish(OperationResult.forOperation("Symmetric Encrypt")
                        .output("CIPHER_HEX_1234".getBytes(StandardCharsets.UTF_8))
                        .status("Encrypted").build());
                assertTrue(summaryBar.isVisible());
                assertEquals("CIPHER_HEX_1234", controller.resolveCurrentOutputText());

                // Hashing
                controller.publish(OperationResult.forOperation("Hashing: SHA-256")
                        .output("HASH_HEX_5678".getBytes(StandardCharsets.UTF_8))
                        .status("Hashed").build());
                assertTrue(summaryBar.isVisible());
                assertEquals("HASH_HEX_5678", controller.resolveCurrentOutputText());

                // Conversion
                controller.publish(OperationResult.forOperation("File Conversion")
                        .output("CONVERTED_TEXT".getBytes(StandardCharsets.UTF_8))
                        .status("Converted").build());
                assertTrue(summaryBar.isVisible());
                assertEquals("CONVERTED_TEXT", controller.resolveCurrentOutputText());

                // Digital Signature
                controller.publish(OperationResult.forOperation("Data Signed")
                        .output("SIGNATURE_BYTES_99".getBytes(StandardCharsets.UTF_8))
                        .status("Signed").build());
                assertTrue(summaryBar.isVisible());
                assertEquals("SIGNATURE_BYTES_99", controller.resolveCurrentOutputText());

                // Certificate Parse
                controller.publish(OperationResult.forOperation("Parse Certificate")
                        .enrichedOutput("=== CERTIFICATE INFORMATION ===\nSubject: CN=Test", OperationDetail.Classification.PUBLIC)
                        .status("Parsed").build());
                assertTrue(summaryBar.isVisible());
                assertTrue(controller.resolveCurrentOutputText().contains("CERTIFICATE INFORMATION"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testActiveFocusOnVisibleResultOverriddenByPublishedSnapshot() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);

                // Setup Operation A with focus on visible result area
                TextArea legacyResultArea = new TextArea("OLD_RESULT_A");
                legacyResultArea.setId("legacyResultArea");
                legacyResultArea.getStyleClass().add("text-area");
                legacyResultArea.setEditable(false);
                ((javafx.scene.layout.Pane) root).getChildren().add(legacyResultArea);

                ResultAreaTracker tracker = getField(controller, "resultAreaTracker");
                tracker.register(legacyResultArea);
                tracker.focus(legacyResultArea);
                tracker.markUpdated(legacyResultArea);

                // Publish Operation B
                controller.publish(OperationResult.forOperation("Operation B")
                        .output("NEW_RESULT_B".getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.PUBLIC)
                        .status("Completed B").build());

                // Assert published snapshot B overrides focused area A for all actions
                assertEquals("NEW_RESULT_B", controller.resolveCurrentOutputText(), "Published snapshot B MUST override focused area A");

                // Execute actual Copy handler
                controller.handleCopyOutput();
                String statusMsg = getStatusMessage(controller);
                assertTrue(statusMsg.contains("copied to clipboard"));

                // Execute actual Add to Shelf handler
                controller.handleAddCurrentOutputToShelf();
                assertEquals(1, ClipboardShelfManager.getInstance().getEntries().size());
                assertEquals("NEW_RESULT_B", ClipboardShelfManager.getInstance().getEntries().get(0).getValue());

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testActualHandlersWithSecretSnapshotUnderMaskedAndRedacted() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                byte[] secretKey = "MY_SUPER_SECRET_KEY".getBytes(StandardCharsets.UTF_8);

                controller.publish(OperationResult.forOperation("KDF Derivation")
                        .output(secretKey, OperationDetail.Classification.SECRET)
                        .status("KDF completed").build());

                // MASKED profile
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);

                controller.handleCopyOutput();
                String copyStatus = getStatusMessage(controller);
                assertTrue(copyStatus.contains("Action blocked"), "Copy must block under MASKED mode");

                controller.handleAddCurrentOutputToShelf();
                String shelfStatus = getStatusMessage(controller);
                assertTrue(shelfStatus.contains("Action blocked"), "Add to Shelf must block under MASKED mode");
                assertTrue(ClipboardShelfManager.getInstance().getEntries().isEmpty(), "No secret entry should be added under MASKED mode");

                // REDACTED profile
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);

                controller.handleCopyOutput();
                String copyStatusRedacted = getStatusMessage(controller);
                assertTrue(copyStatusRedacted.contains("Action blocked"), "Copy must block under REDACTED mode");

                controller.handleAddCurrentOutputToShelf();
                assertTrue(ClipboardShelfManager.getInstance().getEntries().isEmpty(), "No secret entry should be added under REDACTED mode");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testNavigationClearsResultAndSummaryBar() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                HBox summaryBar = getField(controller, "resultSummaryBar");

                controller.publish(OperationResult.forOperation("Symmetric Encrypt")
                        .output("CIPHER_DATA".getBytes(StandardCharsets.UTF_8))
                        .status("Encrypted").build());

                assertTrue(summaryBar.isVisible());
                assertNotNull(controller.resolveCurrentOutputText());

                // Navigate to another route
                controller.navigateToModule("Hashing: SHA-256");

                assertFalse(summaryBar.isVisible(), "Navigation MUST hide the result summary bar");
                assertFalse(summaryBar.isManaged(), "Navigation MUST unmanage the result summary bar");
                assertFalse(controller.hasCurrentResult(), "Navigation MUST clear current published result");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testEditableInputFieldsNeverUsedAsFallback() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                HBox summaryBar = getField(controller, "resultSummaryBar");

                // Ensure no published snapshot
                controller.navigateToModule("Hashing: SHA-256");
                assertFalse(summaryBar.isVisible());

                // Simulate user typing secret text into an editable input area
                GenericController generic = getField(controller, "genericContainerController");
                TextArea hashInput = getField(generic, "hashInputArea");
                hashInput.setText("SECRET_INPUT_PLAINTEXT");
                assertTrue(hashInput.isEditable());

                // Verify that resolveCurrentOutputText NEVER picks up the editable input field
                String activeText = controller.resolveCurrentOutputText();
                assertTrue(activeText == null || activeText.isBlank(), "Editable input field MUST NEVER be returned as result fallback");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testUnmigratedModuleFallbackOnlyForRegisteredNonEditableResultArea() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                // Clear published snapshot
                controller.navigateToModule("Legacy Module");

                // Create and register a non-editable result area added to scene graph
                TextArea legacyResultArea = new TextArea("LEGACY_REPORT_OUTPUT");
                legacyResultArea.setId("legacyResultArea");
                legacyResultArea.getStyleClass().add("text-area");
                legacyResultArea.setEditable(false);
                ((javafx.scene.layout.Pane) root).getChildren().add(legacyResultArea);

                ResultAreaTracker tracker = getField(controller, "resultAreaTracker");
                tracker.register(legacyResultArea);
                tracker.focus(legacyResultArea);
                tracker.markUpdated(legacyResultArea);

                String resolved = controller.resolveCurrentOutputText();
                assertEquals("LEGACY_REPORT_OUTPUT", resolved, "Unmigrated fallback must accept registered non-editable result area");

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void testSecurityProfilesMaskingAndRedaction() throws Exception {
        runAndWait(() -> {
            try {
                URL resource = getClass().getResource("/fxml/main-view-modern.fxml");
                FXMLLoader loader = new FXMLLoader(resource);
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controller.initialize();

                byte[] secretKey = "RAW_SECRET_KEY_BYTES".getBytes(StandardCharsets.UTF_8);

                controller.publish(OperationResult.forOperation("KDF Derivation")
                        .output(secretKey, OperationDetail.Classification.SECRET)
                        .status("KDF done").build());

                // Profile MASKED
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);
                assertEquals("***MASKED***", controller.resolveCurrentOutputText());

                // Profile REDACTED
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
                assertEquals("", controller.resolveCurrentOutputText());

                // Profile FULL_LAB
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                assertEquals("RAW_SECRET_KEY_BYTES", controller.resolveCurrentOutputText());

            } catch (Exception e) {
                fail(e);
            }
        });
    }
}
