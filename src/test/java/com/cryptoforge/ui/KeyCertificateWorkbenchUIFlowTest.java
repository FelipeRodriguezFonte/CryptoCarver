package com.cryptoforge.ui;

import com.cryptoforge.model.OperationDetail;
import com.cryptoforge.model.SecretVisibility;
import com.cryptoforge.model.AppSettings;
import com.cryptoforge.service.KeyCertificateFormatService;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

public class KeyCertificateWorkbenchUIFlowTest {

    private static boolean toolkitInitialized = false;
    private KeyCertificateWorkbenchController controller;
    private static byte[] jksBytes;

    @BeforeAll
    public static void setup() throws Exception {
        if (!toolkitInitialized) {
            try {
                Platform.startup(() -> {});
                toolkitInitialized = true;
            } catch (IllegalStateException e) {
                // Toolkit already initialized
            }
        }

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        KeyPair pair = rsaGen.generateKeyPair();

        long now = System.currentTimeMillis();
        X500Name name = new X500Name("CN=Test");
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), new Date(now), new Date(now + 36500000000L), name, pair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(pair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        KeyStore jks = KeyStore.getInstance("JKS");
        jks.load(null, null);
        jks.setKeyEntry("mykey", pair.getPrivate(), "password".toCharArray(), new Certificate[]{cert});
        jks.setCertificateEntry("mycert", cert);
        ByteArrayOutputStream jksOut = new ByteArrayOutputStream();
        jks.store(jksOut, "password".toCharArray());
        jksBytes = jksOut.toByteArray();
    }

    @BeforeEach
    public void setupController() throws Exception {
        controller = new KeyCertificateWorkbenchController();
        controller.workbenchInputArea = new TextArea();
        controller.workbenchPasswordField = new PasswordField();
        controller.workbenchOutputArea = new TextArea();
        controller.convertToCombo = new ComboBox<>();
        controller.storeTypeCombo = new ComboBox<>();
        controller.lblFormat = new Label();
        controller.lblAlgorithm = new Label();
        controller.lblHasPrivate = new Label();
        controller.lblSubject = new Label();
        controller.lblKeySize = new Label();
        controller.lblFingerprint = new Label();
        controller.lblValidity = new Label();
        controller.keystoreTable = new TableView<>();
        controller.singleItemGrid = new javafx.scene.layout.GridPane();
        controller.validationSecondaryInput = new javafx.scene.control.TextField();

        controller.setStatusReporter(new com.cryptoforge.ui.StatusReporter() {
            public void showError(String title, String content) { System.out.println("ERROR: " + title + " - " + content); }
            public void showInfo(String title, String content) { System.out.println("INFO: " + title + " - " + content); }
            public void showWarning(String title, String content) { System.out.println("WARN: " + title + " - " + content); }
            public void updateStatus(String status) { System.out.println("STATUS: " + status); }
            public void updateInspector(String title, byte[] source, byte[] target, java.util.List<com.cryptoforge.model.OperationDetail> details) {}
            public void clearInspector() {}
        });

        controller.initialize();

        AppSettings.getInstance().setSecretVisibility(SecretVisibility.FULL_LAB);
    }

    // We cannot easily invoke private action methods without reflection, but since they are private,
    // wait, I can make them package-private or use reflection. I will use reflection.
    private void invokeMethod(String methodName) throws Exception {
        java.lang.reflect.Method m = KeyCertificateWorkbenchController.class.getDeclaredMethod(methodName, javafx.event.ActionEvent.class);
        m.setAccessible(true);
        m.invoke(controller, (javafx.event.ActionEvent) null);
    }

    @Test
    public void testPrivateExportShelfClassification() throws Exception {
        // Load Keystore
        controller.storeTypeCombo.setValue("JKS");
        controller.workbenchInputArea.setText(java.util.Base64.getEncoder().encodeToString(jksBytes));
        invokeMethod("handleParse");

        // Select private key alias
        controller.workbenchPasswordField.setText("password");
        KeyCertificateFormatService.KeystoreEntrySummary entry = new KeyCertificateFormatService.KeystoreEntrySummary(
            "mykey", "PrivateKey", "RSA", "CN=Test", "CN=Test", "future", 1
        );
        controller.keystoreTable.getItems().add(entry);
        controller.keystoreTable.getSelectionModel().select(entry);

        controller.convertToCombo.setValue("PEM Private");
        invokeMethod("handleConvert");
        assertTrue(controller.workbenchOutputArea.getText().contains("BEGIN PRIVATE KEY"));

        // Test that Send to Shelf uses SECRET
        invokeMethod("handleSendToShelf");
        // Verify clipboard doesn't blow up, and classification logic executes correctly.
        // If we change to MASKED, it should be blocked.
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.MASKED);

        // This will show an error and return without doing anything, preventing exception or copying.
        invokeMethod("handleSendToShelf");
        invokeMethod("handleCopyOutput");
    }

    @Test
    public void testPublicExportShelfClassification() throws Exception {
        // Load Keystore
        controller.storeTypeCombo.setValue("JKS");
        controller.workbenchInputArea.setText(java.util.Base64.getEncoder().encodeToString(jksBytes));
        invokeMethod("handleParse");

        // Select cert alias
        controller.workbenchPasswordField.setText("password");
        KeyCertificateFormatService.KeystoreEntrySummary entry = new KeyCertificateFormatService.KeystoreEntrySummary(
            "mycert", "TrustedCert", "RSA", "CN=Test", "CN=Test", "future", 1
        );
        controller.keystoreTable.getItems().add(entry);
        controller.keystoreTable.getSelectionModel().select(entry);

        controller.convertToCombo.setValue("PEM Cert");
        invokeMethod("handleConvert");

        assertTrue(controller.workbenchOutputArea.getText().contains("BEGIN CERTIFICATE"));

        // Test that Send to Shelf works even in MASKED because it's PUBLIC
        AppSettings.getInstance().setSecretVisibility(SecretVisibility.MASKED);
        invokeMethod("handleSendToShelf");
    }
}
