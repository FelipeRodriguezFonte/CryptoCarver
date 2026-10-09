package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Stable, UI-backed characterization of key loading and trust store profiles; no dialogs. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class XMLSignaturePhase2CharacterizationUITest extends EmvExtractionCharacterizationSupport {
    @Test
    @SuppressWarnings("unchecked")
    void loadsSigningAliasesAndTrustStoreProfiles() throws Exception {
        String password = "invented-phase2-" + UUID.randomUUID();
        var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig(); config.commonName = "Phase 2 Invented";
        var cert = CertificateGenerator.generateSelfSignedCertificate(pair, config);
        var store = KeyStore.getInstance("PKCS12"); store.load(null, password.toCharArray());
        store.setKeyEntry("signer", pair.getPrivate(), password.toCharArray(), new java.security.cert.Certificate[]{cert});
        var keyFile = tempDir.resolve("phase2-signing.p12");
        try (var out = Files.newOutputStream(keyFile)) { store.store(out, password.toCharArray()); }
        var trustFile = tempDir.resolve("phase2-trust.p12");
        Files.write(trustFile, new byte[]{0});

        List<String> transcript = new ArrayList<>();
        onFx(() -> {
            shell.navigateTo("Sign XML (XAdES)");
            var controller = (XMLSignatureController) get(shell, "xmlSecurityContainerController");
            var reporter = new CharacterizationReporter(); controller.initModule(reporter);
            var keyPath = (TextInputControl) get(controller, "xmlSignKeyPathField");
            var keyPassword = (TextInputControl) get(controller, "xmlSignKeyPasswordField");
            var aliases = (ComboBox<String>) get(controller, "xmlSignKeyAliasCombo");

            keyPath.clear(); keyPassword.clear();
            controller.handleLoadXMLKeys();
            transcript.add("missing-input errors=" + reporter.errors + " aliases=" + aliases.getItems().size());
            reporter.reset();

            keyPath.setText(keyFile.toString()); keyPassword.setText(password);
            controller.handleLoadXMLKeys();
            transcript.add("loaded aliases=" + aliases.getItems().size()
                    + " selected=" + aliases.getSelectionModel().getSelectedIndex()
                    + " status=" + reporter.statuses + " errors=" + reporter.errors);
            assertFalse(String.join("\n", reporter.statuses).contains(password));
            reporter.reset();

            keyPassword.setText(password + "-wrong");
            controller.handleLoadXMLKeys();
            transcript.add("wrong-password errors=" + reporter.errors + " statuses=" + reporter.statuses
                    + " aliases-kept=" + aliases.getItems().size());
            assertFalse(String.join("\n", reporter.messages).contains(password));
            reporter.reset();

            AppSettings.getInstance().saveTrustStoreProfile("Invented Trust", trustFile.toString(), "PKCS12");
            var profileCombo = (ComboBox<String>) get(controller, "xmlVerifyTrustStoreProfileCombo");
            var trustPath = (TextInputControl) get(controller, "xmlVerifyTrustStorePathField");
            var trustPassword = (TextInputControl) get(controller, "xmlVerifyTrustStorePasswordField");
            trustPassword.setText("invented-trust-password");
            profileCombo.setValue(null);
            controller.handleLoadXMLTrustStoreProfile();
            transcript.add("blank-profile path-empty=" + trustPath.getText().isEmpty()
                    + " password-kept=" + !trustPassword.getText().isEmpty() + " statuses=" + reporter.statuses);
            profileCombo.setValue("Unknown Trust");
            controller.handleLoadXMLTrustStoreProfile();
            transcript.add("unknown-profile path-empty=" + trustPath.getText().isEmpty()
                    + " statuses=" + reporter.statuses + " errors=" + reporter.errors);
            profileCombo.setValue("Invented Trust");
            controller.handleLoadXMLTrustStoreProfile();
            transcript.add("known-profile path-set=" + trustFile.toString().equals(trustPath.getText())
                    + " password-cleared=" + trustPassword.getText().isEmpty() + " statuses=" + reporter.statuses);
        });
        pinTranscript("xmlsig-phase2",
                "8de3a5f03ef1355e6dc0b62c3adc0b6d326a0a674f40395f17b61295b32aec97", transcript);
    }

    private final class CharacterizationReporter implements StatusReporter {
        final List<String> errors = new ArrayList<>();
        final List<String> statuses = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        void reset() { errors.clear(); statuses.clear(); messages.clear(); }
        public void updateStatus(String message) { statuses.add(message); shell.updateStatus(message); }
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) { shell.updateInspector(operation, input, output, details); }
        public void showError(String title, String message) { errors.add(title); messages.add(message); }
        public void showError(UserFacingError error) { errors.add(error.title()); messages.add(error.detail()); }
        public void showInfo(String title, String message) { }
        public void publish(OperationResult value) { shell.publish(value); }
    }
}
