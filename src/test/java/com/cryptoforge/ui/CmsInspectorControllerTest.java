package com.cryptoforge.ui;

import com.cryptoforge.crypto.CMSOperations;
import com.cryptoforge.crypto.CertificateGenerator;
import com.cryptoforge.model.OperationDetail;
import com.cryptoforge.model.OperationResult;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CmsInspectorControllerTest {

    @BeforeAll
    static void initBC() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            // Initialize JavaFX toolkit if needed (though we only instantiate non-stage controls)
            Platform.startup(() -> {});
        } catch (Exception ignored) {
            // Toolkit already initialized
        }
    }

    private <T> void setField(Object target, String name, T value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
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

    private CmsInspectorController controller;
    private AtomicReference<OperationResult> publishedResultRef;
    private AtomicReference<String> errorTitleRef;
    private AtomicReference<String> errorMsgRef;
    private StatusReporter mockReporter;

    @BeforeEach
    void setUp() throws Exception {
        controller = new CmsInspectorController();
        publishedResultRef = new AtomicReference<>();
        errorTitleRef = new AtomicReference<>();
        errorMsgRef = new AtomicReference<>();

        mockReporter = new StatusReporter() {
            @Override public void updateStatus(String s) {}
            @Override public void updateInspector(String s, byte[] b1, byte[] b2, List<OperationDetail> d) {}
            @Override public void addToHistory(String s, List<OperationDetail> d) {}
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
                setField(controller, "cmsInputArea", new TextArea());
                setField(controller, "cmsDetachedCheck", new CheckBox());
                setField(controller, "cmsContentArea", new TextArea());
                setField(controller, "truststorePasswordField", new PasswordField());
                setField(controller, "cmsReportArea", new TextArea());
                setField(controller, "cmsFilePathLabel", new Label());
                setField(controller, "cmsContentPathLabel", new Label());
                setField(controller, "truststorePathLabel", new Label());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private KeyPair generateKeyPair() throws Exception {
        return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    @Test
    void testAtomicPublicationAndNoPasswordLeak() throws Exception {
        KeyPair kp = generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Test Password Leak";
        X509Certificate cert = CertificateGenerator.generateSelfSignedCertificate(kp, config);

        byte[] content = "Hello Inspector".getBytes();
        byte[] cms = CMSOperations.generateSignedData(content, cert, kp.getPrivate(), null, false);

        String secretPass = "SuperSecretTruststore123!";

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("cert", cert);

        File tempKs = File.createTempFile("testks", ".p12");
        tempKs.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempKs)) {
            ks.store(fos, secretPass.toCharArray());
        }

        runAndWait(() -> {
            try {
                setField(controller, "cmsFileBytes", cms);
                setField(controller, "truststoreFile", tempKs);
                PasswordField pf = new PasswordField();
                pf.setText(secretPass);
                setField(controller, "truststorePasswordField", pf);

                // Act
                controller.handleInspectCms(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        OperationResult result = publishedResultRef.get();
        assertNotNull(result, "OperationResult should be published atomically");
        assertEquals("CMS_INSPECTOR", result.getOperation());
        assertNotNull(result.getOutput());

        String reportText = new String(result.getOutput());
        assertTrue(reportText.contains("SIGNED_DATA"));
        assertFalse(reportText.contains(secretPass), "Truststore password leaked in report!");

        assertNull(errorMsgRef.get(), "Should not have errors");

        // Also check history detail doesn't contain password
        for (OperationDetail detail : result.getDetails()) {
            assertFalse(detail.value().contains(secretPass), "Truststore password leaked in details!");
        }
    }
}
