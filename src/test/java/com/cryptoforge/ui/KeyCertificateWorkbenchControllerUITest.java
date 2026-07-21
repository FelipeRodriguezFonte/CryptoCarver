package com.cryptoforge.ui;

import com.cryptoforge.service.KeyCertificateFormatService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class KeyCertificateWorkbenchControllerUITest {

    @BeforeAll
    public static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    @Test
    public void testControllerInjection() throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Throwable[] error = new Throwable[1];

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/key_certificate_workbench.fxml"));
                Parent root = loader.load();
                KeyCertificateWorkbenchController controller = loader.getController();
                assertNotNull(controller);
                
                controller.setStatusReporter(new StatusReporter() {
                    @Override
                    public void updateStatus(String msg) {}
                    @Override
                    public void showError(String title, String content) {}
                    @Override
                    public void showInfo(String title, String content) {}
                    @Override
                    public void publish(com.cryptoforge.model.OperationResult result) {}
                    @Override
                    public void updateInspector(String title, byte[] rawInput, byte[] rawOutput, java.util.List<com.cryptoforge.model.OperationDetail> details) {}
                });
                
            } catch (Throwable e) {
                error[0] = e;
            } finally {
                latch.countDown();
            }
        });
        
        latch.await();
        if (error[0] != null) {
            fail("Failed to load FXML: " + error[0].getMessage(), error[0]);
        }
    }
}
