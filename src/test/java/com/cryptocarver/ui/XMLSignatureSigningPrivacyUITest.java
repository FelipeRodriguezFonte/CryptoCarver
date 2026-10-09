package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Continuation of phase zero: loaded key material and signing results on the real shell. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class XMLSignatureSigningPrivacyUITest extends EmvExtractionCharacterizationSupport {
    @ParameterizedTest
    @EnumSource(SecretVisibilityProfile.class)
    void signingMustNotPersistTsaUriCredentialsOrExposeLoadedPrivateMaterial(SecretVisibilityProfile profile) throws Exception {
        String keyPassword = "invented-keystore-81-" + UUID.randomUUID();
        String authPassword = "invented-auth-81-" + UUID.randomUUID();
        String uriPassword = "invented-uri-81-" + UUID.randomUUID();
        String tsaUrl = "https://invented:" + uriPassword + "@tsa.invalid:8443/tsr?q=lab";
        var key = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Invented XML 81 Signer";
        var certificate = CertificateGenerator.generateSelfSignedCertificate(key, config);
        var keyFile = tempDir.resolve("invented-signing.p12");
        var store = KeyStore.getInstance("PKCS12");
        store.load(null, keyPassword.toCharArray());
        store.setKeyEntry("invented-signer", key.getPrivate(), keyPassword.toCharArray(),
                new java.security.cert.Certificate[] {certificate});
        try (var output = Files.newOutputStream(keyFile)) { store.store(output, keyPassword.toCharArray()); }
        var xmlFile = tempDir.resolve("invented.xml");
        Files.writeString(xmlFile, "<invoice><amount>81</amount></invoice>");
        List<String> secrets = List.of(keyPassword, authPassword, uriPassword,
                Base64.getEncoder().encodeToString(key.getPrivate().getEncoded()),
                HexFormat.of().formatHex(key.getPrivate().getEncoded()));
        onFx(() -> {
            AppSettings.getInstance().setSecretVisibilityProfile(profile);
            shell.navigateTo("Sign XML (XAdES)");
            var controller = (XMLSignatureController) get(shell, "xmlSecurityContainerController");
            var reporter = new SigningReporter();
            controller.initModule(reporter);
            put(controller, "xmlSignKeyPathField", keyFile.toString());
            put(controller, "xmlSignKeyPasswordField", keyPassword);
            put(controller, "xmlSignInputPathField", xmlFile.toString());
            controller.handleLoadXMLKeys();
            assertTrue(reporter.errors.isEmpty(), "real key loading must succeed");
            assertEquals(1, combo(controller, "xmlSignKeyAliasCombo").getItems().size());
            var violations = new ArrayList<String>();
            inspectSurfaces(profile, "XML key loading", secrets, shell, root, new ArrayList<>(), violations);
            assertTrue(violations.isEmpty(), String.join("\n", violations));

            combo(controller, "xmlSignTsaAuthTypeCombo").setValue("BASIC");
            put(controller, "xmlSignTsaUserField", "invented-user");
            put(controller, "xmlSignTsaPasswordField", authPassword);
            combo(controller, "xmlSignTsaUrlText").setValue(tsaUrl);
            combo(controller, "xmlSignTsaUrlText").getEditor().setText(tsaUrl);
            // BASELINE-B does not request a timestamp: no TSA contact or DNS resolution.
            assertEquals("XAdES-BASELINE-B", combo(controller, "xmlSignLevelCombo").getValue());
            controller.handleSignXML();
            assertTrue(reporter.errors.isEmpty(), "signing must succeed, not just exercise validation");
            assertNotNull(reporter.result);
            assertEquals("XAdES Sign", reporter.result.getOperation());
            TextArea output = (TextArea) get(controller, "xmlSignOutputArea");
            assertTrue(output.getText().contains("SignatureValue"));
            output.requestFocus(); root.applyCss(); root.layout();
            assertEquals(1, shell.getHistoryManager().getHistoryItems().size(), "real publication must store a history entry");
            assertTrue(reporter.infos.contains(com.cryptocarver.service.I18nService.getInstance()
                    .text("module.xml.tsaCredentialsNotSaved")), "production showInfo path must present the warning");
            String settings = Files.readString(tempDir.resolve("settings.json"));
            assertFalse(settings.contains(uriPassword), "the authorized AppSettings correction must be effective");
            assertEquals("https://tsa.invalid:8443/tsr?q=lab", AppSettings.getInstance().getCustomTsaUrl());
            inspectSurfaces(profile, "XAdES Sign", secrets, shell, root, new ArrayList<>(), violations);
            String history = Files.readString(tempDir.resolve("history.json"));
            var record = com.google.gson.JsonParser.parseString(history).getAsJsonArray().get(0).getAsJsonObject();
            if (profile != SecretVisibilityProfile.FULL_LAB) {
                for (String field : List.of("details", "structuredDetails")) {
                    if (record.has(field) && record.get(field).toString().contains(uriPassword)) {
                        violations.add(profile + " history.json [0]." + field + " persists TSA URI credentials (TSA detail)");
                    }
                }
                record.getAsJsonObject("parameters").entrySet().stream()
                        .filter(entry -> entry.getValue().toString().contains(uriPassword))
                        .forEach(entry -> violations.add(profile + " history.json [0].parameters[" + entry.getKey()
                                + "] persists TSA URI credentials"));
                for (String secret : secrets) {
                    if (history.contains(secret)) violations.add(profile + " history.json persists " + (secret.equals(uriPassword) ? "TSA URI password" : "loaded key/separate authentication secret"));
                    if (output.getText().contains(secret)) violations.add(profile + " fx:id=xmlSignOutputArea exposes private material");
                }
            } else {
                assertTrue(history.contains(keyPassword), "FULL_LAB still permits the keystore password in its recipe");
                assertTrue(history.contains(authPassword), "FULL_LAB still permits separate ephemeral auth in its recipe");
                assertTrue(history.contains(uriPassword), "FULL_LAB keeps TSA URL credentials under normal history profile policy");
            }
            assertTrue(violations.isEmpty(), String.join("\n", violations));
            // Stop at any real persisted/visible leak; do not proceed to verification, inspection or tokens.
            // Shared fixture restores settings/Shelf/history and @TempDir removes the synthetic files.
        });
    }

    private static void put(XMLSignatureController controller, String field, String text) throws Exception {
        ((TextInputControl) get(controller, field)).setText(text);
    }
    @SuppressWarnings("unchecked")
    private static ComboBox<String> combo(XMLSignatureController controller, String field) throws Exception {
        return (ComboBox<String>) get(controller, field);
    }
    private final class SigningReporter implements StatusReporter {
        final List<String> errors = new ArrayList<>();
        final List<String> infos = new ArrayList<>();
        OperationResult result;
        public void updateStatus(String message) { shell.updateStatus(message); }
        public void updateInspector(String operation, byte[] input, byte[] output, List<OperationDetail> details) {
            shell.updateInspector(operation, input, output, details);
        }
        public void showError(String title, String message) { errors.add(title); shell.showError(title, message); }
        public void showError(UserFacingError error) { errors.add(error.title()); shell.showError(error); }
        public void showInfo(String title, String message) { infos.add(message); shell.showInfo(title, message); }
        public void publish(OperationResult value) { result = value; shell.publish(value); }
    }
}
