package com.cryptoforge.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.cryptoforge.model.OperationResult;
import com.cryptoforge.model.OperationDetail;
import com.cryptoforge.model.AppSettings;
import com.cryptoforge.model.SecretVisibility;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

import static org.junit.jupiter.api.Assertions.*;

public class ExpandResultAuditTest {

    @BeforeAll
    public static void initToolkit() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException e) {
            Platform.setImplicitExit(false);
            // Toolkit already initialized
        }
    }

    private ModernMainController createController() throws Exception {
        ModernMainController controller = new ModernMainController();
        // Inject dummy labels to prevent NullPointerException during updateInspector
        injectField(controller, "operationLabel", new javafx.scene.control.Label());
        injectField(controller, "inputBytesLabel", new javafx.scene.control.Label());
        injectField(controller, "outputBytesLabel", new javafx.scene.control.Label());
        injectField(controller, "statusLabel", new javafx.scene.control.Label());
        injectField(controller, "securityTipLabel", new javafx.scene.control.Label());
        return controller;
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String resolveTextOnFxThread(ModernMainController controller) throws Exception {
        final String[] resultHolder = new String[1];
        final Exception[] err = new Exception[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                resultHolder[0] = controller.resolveCurrentOutputText();
            } catch (Exception e) {
                err[0] = e;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Timeout waiting for resolveCurrentOutputText on FX thread");
        if (err[0] != null) throw err[0];
        return resultHolder[0];
    }

    private void publishOnFxThread(ModernMainController controller, OperationResult result) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] err = new Exception[1];
        Platform.runLater(() -> {
            try {
                controller.publish(result);
            } catch (Exception e) {
                err[0] = e;
            } catch (Error e) {
                err[0] = new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Timeout waiting for publish on FX thread");
        if (err[0] != null) throw err[0];
    }

    @Test
    public void testA_PublishResultAThenB() throws Exception {
        ModernMainController controller = createController();

        OperationResult resultA = OperationResult.forOperation("OpA")
                .output("Output A".getBytes())
                .build();

        OperationResult resultB = OperationResult.forOperation("OpB")
                .output("Output B".getBytes())
                .build();

        publishOnFxThread(controller, resultA);
        assertEquals("Output A", resolveTextOnFxThread(controller));

        publishOnFxThread(controller, resultB);
        assertEquals("Output B", resolveTextOnFxThread(controller));
    }

    @Test
    public void testB_KeyMaterialInspector() throws Exception {
        ModernMainController controller = createController();
        String report = "Key size: 2048\nFingerprint: AA:BB";

        OperationResult result = OperationResult.forOperation("Key Material Inspector")
                .output(report.getBytes())
                .build();

        publishOnFxThread(controller, result);
        assertEquals(report, resolveTextOnFxThread(controller));
    }

    @Test
    public void testC_EnrichedOutputExplicitClassification() throws Exception {
        ModernMainController controller = createController();

        // Let's create an operation with a SECRET enriched output
        String secretText = "=== SECRET PRIVATE KEY EXPORT ===\nBEGIN PRIVATE KEY...";

        OperationResult result = OperationResult.forOperation("Export Private Key")
                .enrichedOutput(secretText, OperationDetail.Classification.SECRET)
                .build();

        publishOnFxThread(controller, result);

        // FULL_LAB -> Should be completely visible
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.FULL_LAB);
        assertEquals(secretText, resolveTextOnFxThread(controller));

        // MASKED -> Should be masked explicitly
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.MASKED);
        assertEquals("***MASKED***", resolveTextOnFxThread(controller));

        // REDACTED -> Should not be available at all
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.REDACTED);
        assertEquals("", resolveTextOnFxThread(controller));
    }

    @Test
    public void testD_EnrichedOutputDerivedClassification() throws Exception {
        ModernMainController controller = createController();

        // Let's create an operation that derives its SECRET classification from a detail
        String reportText = "Operation finished successfully. Extracted Key.";

        OperationResult result = OperationResult.forOperation("Extract Private Key")
                .enrichedOutput(reportText) // No explicit classification passed
                .detail(new OperationDetail("Key", "Private", OperationDetail.Classification.SECRET, true, "PEM"))
                .build();

        publishOnFxThread(controller, result);

        // FULL_LAB -> Visible
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.FULL_LAB);
        assertEquals(reportText, resolveTextOnFxThread(controller));

        // MASKED -> Because there's a SECRET detail, the entire payload is masked
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.MASKED);
        assertEquals("***MASKED***", resolveTextOnFxThread(controller));

        // REDACTED -> Blocked entirely
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.REDACTED);
        assertEquals("", resolveTextOnFxThread(controller));
    }

    @Test
    public void testE_CopyOutputAddShelfEquivalence() throws Exception {
        ModernMainController controller = createController();
        String outputText = "Output that must be consistent";

        OperationResult result = OperationResult.forOperation("Some Op")
                .output(outputText.getBytes())
                .build();

        publishOnFxThread(controller, result);

        // Test resolution. Both handleCopyOutput and handleSendToShelf internally call resolveCurrentOutputText()
        // We verify that the method returns exactly what we expect without looking at UI
        String expandText = resolveTextOnFxThread(controller);
        assertEquals(outputText, expandText);
    }

    private void invokeMethodOnFxThread(Object target, String methodName, Class<?>[] argTypes, Object[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] err = new Exception[1];
        Platform.runLater(() -> {
            try {
                java.lang.reflect.Method m = target.getClass().getDeclaredMethod(methodName, argTypes);
                m.setAccessible(true);
                m.invoke(target, args);
            } catch (Exception e) {
                err[0] = e;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        if (err[0] != null) throw err[0];
    }

    @Test
    public void testF_ContextMenuActions_SecretData() throws Exception {
        ModernMainController controller = createController();
        String secretText = "SECRET_VALUE_XYZ";

        OperationResult result = OperationResult.forOperation("Test Op")
                .enrichedOutput(secretText, OperationDetail.Classification.SECRET)
                .build();

        publishOnFxThread(controller, result);

        javafx.scene.control.TextArea dummyArea = new javafx.scene.control.TextArea();
        injectField(controller, "lastFocusedResultArea", dummyArea);

        // 1. Enriched SECRET + FULL_LAB -> Shelf contains SECRET entry
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.FULL_LAB);
        com.cryptoforge.model.ClipboardShelfManager.getInstance().clear();
        invokeMethodOnFxThread(controller, "handleAddToClipboardShelfSecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class},
            new Object[]{dummyArea, null});
        assertEquals(1, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().size());
        assertEquals(secretText, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().get(0).getValue());
        assertEquals(OperationDetail.Classification.SECRET, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().get(0).getClassification());

        // 2. Enriched SECRET + MASKED -> Shelf does not add entry (blocked)
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.MASKED);
        com.cryptoforge.model.ClipboardShelfManager.getInstance().clear();
        invokeMethodOnFxThread(controller, "handleAddToClipboardShelfSecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class},
            new Object[]{dummyArea, null});
        assertEquals(0, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().size());

        // 3. Context menu Copy on partial selection of secret area -> blocked in MASKED
        invokeMethodOnFxThread(controller, "handleCopySecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class, boolean.class},
            new Object[]{dummyArea, "partial_secret", true});
        // Can verify via status label update
        java.lang.reflect.Field statusLabelField = controller.getClass().getDeclaredField("statusLabel");
        statusLabelField.setAccessible(true);
        javafx.scene.control.Label statusLabel = (javafx.scene.control.Label) statusLabelField.get(controller);
        assertTrue(statusLabel.getText().contains("blocked"));
    }

    @Test
    public void testG_ContextMenuActions_PublicData() throws Exception {
        ModernMainController controller = createController();
        String publicText = "PUBLIC_VALUE_ABC";

        OperationResult result = OperationResult.forOperation("Public Op")
                .enrichedOutput(publicText, OperationDetail.Classification.PUBLIC)
                .build();

        publishOnFxThread(controller, result);

        javafx.scene.control.TextArea dummyArea = new javafx.scene.control.TextArea();
        injectField(controller, "lastFocusedResultArea", dummyArea);

        AppSettings.getInstance().setSecretVisibility(SecretVisibility.REDACTED);
        com.cryptoforge.model.ClipboardShelfManager.getInstance().clear();
        invokeMethodOnFxThread(controller, "handleAddToClipboardShelfSecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class},
            new Object[]{dummyArea, null});

        assertEquals(1, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().size());
        assertEquals(publicText, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().get(0).getValue());
        assertEquals(OperationDetail.Classification.PUBLIC, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().get(0).getClassification());
    }

    @Test
    public void testH_ContextMenuActions_OldAreaNewPublicSnapshot() throws Exception {
        ModernMainController controller = createController();

        // 1. Simulate an OLD area with SECRET data
        javafx.scene.control.TextArea oldArea = new javafx.scene.control.TextArea();
        oldArea.setText("OLD_SECRET_DATA");

        // 2. Publish a NEW result (PUBLIC)
        String newPublicText = "NEW_PUBLIC_DATA";
        OperationResult newResult = OperationResult.forOperation("New Public Op")
                .enrichedOutput(newPublicText, OperationDetail.Classification.PUBLIC)
                .build();

        publishOnFxThread(controller, newResult);

        // We simulate the new area being the one updated by publish
        javafx.scene.control.TextArea newArea = new javafx.scene.control.TextArea();
        newArea.setText(newPublicText);
        injectField(controller, "lastUpdatedResultArea", newArea);

        AppSettings.getInstance().setSecretVisibility(SecretVisibility.REDACTED);
        com.cryptoforge.model.ClipboardShelfManager.getInstance().clear();

        // 3. User tries to select and copy from the OLD area
        invokeMethodOnFxThread(controller, "handleCopySecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class, boolean.class},
            new Object[]{oldArea, "OLD_SECRET", true});

        java.lang.reflect.Field statusLabelField = controller.getClass().getDeclaredField("statusLabel");
        statusLabelField.setAccessible(true);
        javafx.scene.control.Label statusLabel = (javafx.scene.control.Label) statusLabelField.get(controller);
        assertTrue(statusLabel.getText().contains("old or unknown result"));

        // 4. Add to Shelf from OLD area
        invokeMethodOnFxThread(controller, "handleAddToClipboardShelfSecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class},
            new Object[]{oldArea, "OLD_SECRET"});

        assertEquals(0, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().size());
        assertTrue(statusLabel.getText().contains("old or unknown result"));
    }

    @Test
    public void testI_ContextMenuActions_SensitiveMasked() throws Exception {
        ModernMainController controller = createController();
        String sensitiveText = "SENSITIVE_DATA_123";

        OperationResult result = OperationResult.forOperation("Sensitive Op")
                .enrichedOutput(sensitiveText, OperationDetail.Classification.SENSITIVE)
                .build();

        publishOnFxThread(controller, result);

        javafx.scene.control.TextArea dummyArea = new javafx.scene.control.TextArea();
        dummyArea.setText(sensitiveText);
        injectField(controller, "lastUpdatedResultArea", dummyArea);

        AppSettings.getInstance().setSecretVisibility(SecretVisibility.MASKED);
        com.cryptoforge.model.ClipboardShelfManager.getInstance().clear();

        // Context menu Copy on partial selection -> blocked in MASKED for SENSITIVE
        invokeMethodOnFxThread(controller, "handleCopySecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class, boolean.class},
            new Object[]{dummyArea, "partial_sensitive", true});

        java.lang.reflect.Field statusLabelField = controller.getClass().getDeclaredField("statusLabel");
        statusLabelField.setAccessible(true);
        javafx.scene.control.Label statusLabel = (javafx.scene.control.Label) statusLabelField.get(controller);
        assertTrue(statusLabel.getText().contains("blocked"));

        // Add to Shelf on partial selection -> blocked in MASKED for SENSITIVE
        invokeMethodOnFxThread(controller, "handleAddToClipboardShelfSecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class},
            new Object[]{dummyArea, "partial_sensitive"});

        assertEquals(0, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().size());
        assertTrue(statusLabel.getText().contains("blocked"));
    }

    @Test
    public void testJ_ContextMenuActions_SensitiveRedacted() throws Exception {
        ModernMainController controller = createController();
        String sensitiveText = "SENSITIVE_DATA_456";

        OperationResult result = OperationResult.forOperation("Sensitive Op")
                .enrichedOutput(sensitiveText, OperationDetail.Classification.SENSITIVE)
                .build();

        publishOnFxThread(controller, result);

        javafx.scene.control.TextArea dummyArea = new javafx.scene.control.TextArea();
        dummyArea.setText(sensitiveText);
        injectField(controller, "lastUpdatedResultArea", dummyArea);

        AppSettings.getInstance().setSecretVisibility(SecretVisibility.REDACTED);
        com.cryptoforge.model.ClipboardShelfManager.getInstance().clear();

        // Context menu Copy on partial selection -> blocked in REDACTED for SENSITIVE
        invokeMethodOnFxThread(controller, "handleCopySecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class, boolean.class},
            new Object[]{dummyArea, "partial_sensitive", true});

        java.lang.reflect.Field statusLabelField = controller.getClass().getDeclaredField("statusLabel");
        statusLabelField.setAccessible(true);
        javafx.scene.control.Label statusLabel = (javafx.scene.control.Label) statusLabelField.get(controller);
        assertTrue(statusLabel.getText().contains("blocked"));

        // Add to Shelf on partial selection -> blocked in REDACTED for SENSITIVE
        invokeMethodOnFxThread(controller, "handleAddToClipboardShelfSecure",
            new Class<?>[]{javafx.scene.control.TextArea.class, String.class},
            new Object[]{dummyArea, "partial_sensitive"});

        assertEquals(0, com.cryptoforge.model.ClipboardShelfManager.getInstance().getEntries().size());
        assertTrue(statusLabel.getText().contains("blocked"));
    }
}
