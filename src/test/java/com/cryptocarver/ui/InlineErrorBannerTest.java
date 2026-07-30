package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
public class InlineErrorBannerTest {

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
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                fail("JavaFX execution timed out");
            }
        } catch (InterruptedException e) {
            fail("Interrupted waiting for JavaFX thread");
        }
    }

    @Test
    void testFxmlStaticContractForErrorBannerComponents() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputStream is = getClass().getResourceAsStream("/fxml/main-view-modern.fxml");
        assertNotNull(is, "main-view-modern.fxml not found");

        Document doc = db.parse(is);

        assertElementWithFxIdExists(doc, "errorBanner", "HBox");
        assertElementWithFxIdExists(doc, "errorBannerTitle", "Label");
        assertElementWithFxIdExists(doc, "errorBannerRemedy", "Label");
        assertElementWithFxIdExists(doc, "errorBannerGoToFieldBtn", "Button");
        assertElementWithFxIdExists(doc, "errorBannerCopyDetailsBtn", "Button");
        assertElementWithFxIdExists(doc, "errorBannerCloseBtn", "Button");
    }

    private void assertElementWithFxIdExists(Document doc, String fxId, String tagName) {
        NodeList elements = doc.getElementsByTagName(tagName);
        boolean found = false;
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            if (fxId.equals(el.getAttribute("fx:id"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "main-view-modern.fxml must contain " + tagName + " with fx:id='" + fxId + "'");
    }

    @Test
    void testInlineErrorPresenterBannerLifecycleAndFieldHighlighting() {
        runAndWait(() -> {
            HBox banner = new HBox();
            Label titleLabel = new Label();
            Label remedyLabel = new Label();
            Button goToFieldBtn = new Button();
            Button copyDetailsBtn = new Button();
            Button closeBtn = new Button();

            InlineErrorPresenter presenter = new InlineErrorPresenter(
                    banner, titleLabel, remedyLabel, goToFieldBtn, copyDetailsBtn, closeBtn
            );

            assertFalse(presenter.isVisible());

            // Root layout container with a target text area
            VBox root = new VBox();
            TextArea inputArea = new TextArea();
            inputArea.setId("cipherInputArea");
            root.getChildren().add(inputArea);

            UserFacingError error = new UserFacingError(
                    "Decryption Failed",
                    "Ciphertext padding error.",
                    "Verify key and IV padding.",
                    "cipherInputArea"
            );

            presenter.showError(error, root);

            assertTrue(presenter.isVisible());
            assertTrue(banner.isManaged());
            assertEquals("Decryption Failed", titleLabel.getText());
            assertEquals("Verify key and IV padding.", remedyLabel.getText());
            assertTrue(goToFieldBtn.isVisible());
            assertTrue(goToFieldBtn.isManaged());
            assertTrue(inputArea.getStyleClass().contains("field-error"), "Target field must receive .field-error style class");

            // Text editing removes .field-error class
            inputArea.setText("New edited content");
            assertFalse(inputArea.getStyleClass().contains("field-error"), "Editing text must remove .field-error style class");

            // Close banner
            presenter.hideBanner();
            assertFalse(presenter.isVisible());
            assertFalse(banner.isManaged());
        });
    }

    @Test
    void testGoToFieldFocusesTargetControl() {
        runAndWait(() -> {
            HBox banner = new HBox();
            Label titleLabel = new Label();
            Label remedyLabel = new Label();
            Button goToFieldBtn = new Button();
            Button copyDetailsBtn = new Button();
            Button closeBtn = new Button();

            InlineErrorPresenter presenter = new InlineErrorPresenter(
                    banner, titleLabel, remedyLabel, goToFieldBtn, copyDetailsBtn, closeBtn
            );

            VBox root = new VBox();
            TextField keyField = new TextField();
            keyField.setId("signaturePrivateKeyArea");
            root.getChildren().add(keyField);
            new Scene(root);

            UserFacingError error = new UserFacingError(
                    "Missing Private Key",
                    "No key provided.",
                    "Paste private key.",
                    "signaturePrivateKeyArea"
            );

            presenter.showError(error, root);

            // Focus on root first
            root.requestFocus();

            // Trigger Go to Field
            presenter.goToField(root);
            assertTrue(keyField.isFocused() || (keyField.getScene() != null && keyField.getScene().getFocusOwner() == keyField),
                    "goToField must grant focus to the target node");
        });
    }

    @Test
    void testTechnicalDetailsComprehensiveSecretRedaction() {
        UserFacingError error = new UserFacingError(
                "GCM Tag Error",
                "Failed verifying key=00112233445566778899AABBCCDDEEFF, privateKey=33445566778899AABBCCDDEEFF001122, AAD=112233445566 with payload=SecretData1234567890ABCDEF0123456789ABCDEF and PEM=-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC...\n-----END PRIVATE KEY-----",
                "Check key and IV parameters.",
                "gcmTagField",
                new RuntimeException("AEADBadTagException: key 00112233445566778899AABBCCDDEEFF failed tag verification 99887766554433221100AABBCCDDEEFF")
        );

        String redactedReport = InlineErrorPresenter.formatRedactedTechnicalDetails(error);

        assertNotNull(redactedReport);
        assertTrue(redactedReport.contains("[CryptoCarver Error Technical Report]"));
        assertFalse(redactedReport.contains("00112233445566778899AABBCCDDEEFF"), "Technical report must NOT leak raw 128-bit key hex");
        assertFalse(redactedReport.contains("33445566778899AABBCCDDEEFF001122"), "Technical report must NOT leak raw private key hex");
        assertFalse(redactedReport.contains("MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC"), "Technical report must NOT leak raw PEM block");
        assertFalse(redactedReport.contains("SecretData1234567890ABCDEF0123456789ABCDEF"), "Technical report must NOT leak secret payload");
        assertTrue(redactedReport.contains("[REDACTED"), "Report must contain redaction placeholders");
    }

    @Test
    void testRealAuthenticationControllerHandlersTriggerInlineErrorBanner() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                // Get injected controls
                HBox banner = (HBox) getPrivateField(controller, "errorBanner");
                Label titleLabel = (Label) getPrivateField(controller, "errorBannerTitle");

                AuthenticationController authController = (AuthenticationController) getPrivateField(controller, "authenticationContainerController");
                assertNotNull(authController, "authenticationContainerController must be initialized");

                ComboBox<String> macAlgoCombo = (ComboBox<String>) getPrivateField(authController, "authMacAlgorithmCombo");
                macAlgoCombo.setValue("HMAC-SHA256");

                TextField macKeyField = (TextField) getPrivateField(authController, "authMacKeyField");
                macKeyField.setText("00112233445566778899AABBCCDDEEFF");

                TextArea authInput = (TextArea) getPrivateField(authController, "authInputArea");
                authInput.setText(""); // Empty input -> trigger missing input error

                // Execute real MAC handler
                authController.handleGenerateMAC();

                assertTrue(banner.isVisible(), "Banner must be visible when handleGenerateMAC fails");
                assertTrue(titleLabel.getText().contains("Preflight") || titleLabel.getText().contains("Missing"),
                        "Title must indicate missing setup or input data");

                // Execute real MAC verify handler with missing verify value
                authController.handleVerifyMAC();
                assertTrue(banner.isVisible(), "Banner must be visible when handleVerifyMAC fails");
                assertTrue(titleLabel.getText().contains("Preflight") || titleLabel.getText().contains("Missing") || titleLabel.getText().contains("MAC"),
                        "Title must indicate MAC verification issue");

            } catch (Exception e) {
                fail("Failed testing AuthenticationController handlers: " + e.getMessage());
            }
        });
    }

    @Test
    void testRealInvalidSignatureVerificationPreservesErrorBanner() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                HBox banner = (HBox) getPrivateField(controller, "errorBanner");
                Label titleLabel = (Label) getPrivateField(controller, "errorBannerTitle");

                AuthenticationController authController = (AuthenticationController) getPrivateField(controller, "authenticationContainerController");

                // Generate RSA key pair for testing
                java.security.KeyPair keyPair = com.cryptocarver.crypto.AsymmetricKeyOperations.generateRSAKeyPair(2048);
                setPrivateField(authController, "currentPublicKey", keyPair.getPublic());

                String validPubKeyPem = com.cryptocarver.crypto.AsymmetricKeyOperations.exportPublicKeyPEM(keyPair.getPublic());
                TextArea pubKeyArea = (TextArea) getPrivateField(authController, "signaturePublicKeyArea");
                pubKeyArea.setText(validPubKeyPem);

                ComboBox<String> inputFormatCombo = (ComboBox<String>) getPrivateField(authController, "inputFormatCombo");
                if (inputFormatCombo != null) {
                    inputFormatCombo.setValue("Text (UTF-8)");
                }

                ComboBox<String> sigAlgoCombo = (ComboBox<String>) getPrivateField(authController, "signatureAlgorithmCombo");
                sigAlgoCombo.setValue("RSA-SHA256-PKCS1");

                TextField verifyField = (TextField) getPrivateField(authController, "signatureVerifyField");
                verifyField.setText("00112233445566778899AABBCCDDEEFF"); // Corrupt signature

                TextArea inputArea = (TextArea) getPrivateField(authController, "authInputArea");
                inputArea.setText("Test message content");

                // Execute real signature verify handler
                authController.handleVerify();

                assertTrue(banner.isVisible(), "Banner MUST remain visible after invalid signature verification result is published!");
                assertEquals("Verification Failed", titleLabel.getText());

            } catch (Exception e) {
                fail("Failed testing invalid signature verification banner preservation: " + e.getMessage());
            }
        });
    }

    @Test
    void testRealKeysControllerCertificateHandlersTriggerInlineErrorBanner() {
        runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load();
                ModernMainController controller = loader.getController();

                HBox banner = (HBox) getPrivateField(controller, "errorBanner");
                Label titleLabel = (Label) getPrivateField(controller, "errorBannerTitle");

                KeysController keysController = controller.getKeysController();
                assertNotNull(keysController, "keysController must be initialized");

                TextArea certInput = (TextArea) getPrivateField(keysController, "certInputArea");
                certInput.setText(""); // Empty cert input -> trigger missing certificate error

                // Execute real parse certificate handler
                keysController.handleParseCertificate();

                assertTrue(banner.isVisible(), "Banner must be visible when certificate parsing fails");
                assertEquals("Missing Certificate Input", titleLabel.getText());

            } catch (Exception e) {
                fail("Failed testing KeysController certificate handlers: " + e.getMessage());
            }
        });
    }

    private Object getPrivateField(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    }

    private void setPrivateField(Object obj, String fieldName, Object val) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, val);
    }
}
