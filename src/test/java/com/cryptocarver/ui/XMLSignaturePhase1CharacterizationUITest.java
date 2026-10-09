package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Stable, UI-backed characterization of phase 1 without opening file dialogs. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class XMLSignaturePhase1CharacterizationUITest extends EmvExtractionCharacterizationSupport {
    @Test
    void signsAndInspectsXmlAndKeepsSaveValidation() throws Exception {
        String password = "invented-phase1-" + UUID.randomUUID();
        var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig(); config.commonName = "Phase 1 Invented";
        var cert = CertificateGenerator.generateSelfSignedCertificate(pair, config);
        var store = KeyStore.getInstance("PKCS12"); store.load(null, password.toCharArray());
        store.setKeyEntry("signer", pair.getPrivate(), password.toCharArray(), new java.security.cert.Certificate[]{cert});
        var keyFile = tempDir.resolve("signing.p12");
        try (var out = Files.newOutputStream(keyFile)) { store.store(out, password.toCharArray()); }
        var inputFile = tempDir.resolve("input.xml");
        Files.writeString(inputFile, "<invoice><amount>81</amount></invoice>");

        onFx(() -> {
            shell.navigateTo("Sign XML (XAdES)");
            var controller = (XMLSignatureController) get(shell, "xmlSecurityContainerController");
            var reporter = new CharacterizationReporter(); controller.initModule(reporter);
            ((TextInputControl) get(controller, "xmlSignInputPathField")).setText(inputFile.toString());
            ((TextInputControl) get(controller, "xmlSignKeyPathField")).setText(keyFile.toString());
            ((TextInputControl) get(controller, "xmlSignKeyPasswordField")).setText(password);
            controller.handleLoadXMLKeys();
            controller.handleSignXML();
            String signed = ((TextInputControl) get(controller, "xmlSignOutputArea")).getText();
            assertFalse(signed.isBlank()); assertTrue(signed.contains("SignatureValue"));
            assertEquals("XAdES Sign", reporter.result.getOperation());
            ((TextInputControl) get(controller, "xmlInspectInputArea")).setText(signed);
            controller.handleInspectSignedXML();
            String inspected = ((TextInputControl) get(controller, "xmlInspectReportArea")).getText();
            assertTrue(inspected.contains("XMLDSig signatures: 1"));
            assertEquals("Inspect Signed XML", reporter.result.getOperation());
            ((TextInputControl) get(controller, "xmlSignOutputArea")).clear();
            controller.handleSaveSignedXML(); // empty output reports validation without opening a chooser
            assertEquals("Save Error", reporter.errors.get(0));
            String transcript = "sign=" + signed.contains("SignatureValue")
                    + "|sign-published=" + "XAdES Sign".equals(reporter.operations.get(0))
                    + "|inspect=" + inspected.contains("XMLDSig signatures: 1")
                    + "|inspect-published=" + "Inspect Signed XML".equals(reporter.operations.get(1))
                    + "|save-validation=" + reporter.errors.contains("Save Error");
            assertEquals("6fe9a0e7ea7ea421928d23b3ab0295e79faa32ac896ebf5de593e426083caa38",
                    HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(transcript.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        });
    }

    private final class CharacterizationReporter implements StatusReporter {
        OperationResult result;
        final java.util.ArrayList<String> operations = new java.util.ArrayList<>();
        final java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        public void updateStatus(String message) { shell.updateStatus(message); }
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) { shell.updateInspector(operation, input, output, details); }
        public void showError(String title, String message) { errors.add(title); }
        public void showError(UserFacingError error) { errors.add(error.title()); }
        public void showInfo(String title, String message) { }
        public void publish(OperationResult value) { result = value; operations.add(value.getOperation()); shell.publish(value); }
    }
}
