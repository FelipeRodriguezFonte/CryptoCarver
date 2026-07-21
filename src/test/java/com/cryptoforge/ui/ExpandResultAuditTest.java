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
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
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
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            resultHolder[0] = controller.resolveCurrentOutputText();
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Timeout waiting for resolveCurrentOutputText on FX thread");
        return resultHolder[0];
    }

    private void publishOnFxThread(ModernMainController controller, OperationResult result) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            controller.publish(result);
            latch.countDown();
        });
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Timeout waiting for publish on FX thread");
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
}
