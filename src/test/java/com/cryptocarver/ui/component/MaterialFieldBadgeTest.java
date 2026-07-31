package com.cryptocarver.ui.component;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.SharedMaterialParser;
import com.cryptocarver.crypto.SignatureOperations;
import com.cryptocarver.model.OperationPreflightEngine;
import com.cryptocarver.model.PreflightReport;
import com.cryptocarver.model.PreflightStatus;
import com.cryptocarver.ui.UiStateSnapshot;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MaterialFieldBadgeTest {

    public static class TestCryptoController {
        @FXML public TextField kdfSaltField = new TextField("1234567890abcdef");
        @FXML public TextField kdfInfoField = new TextField("app-context");
        @FXML public TextField signatureVerifyField = new TextField("sig-data-1234");
        @FXML public TextField authMacVerifyField = new TextField("mac-data-5678");
        @FXML public TextArea certInputArea = new TextArea("-----BEGIN CERTIFICATE-----\n...");
        @FXML public TextArea authInputArea = new TextArea("secret auth payload");
        @FXML public TextArea signaturePrivateKeyArea = new TextArea("-----BEGIN PRIVATE KEY-----\n...");
        @FXML public TextArea cipherInputArea = new TextArea("secret cipher payload");
        @FXML public TextField symmetricKeyField = new TextField("00112233445566778899aabbccddeeff");
    }

    @BeforeAll
    public static void initJFX() {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    public void testHexFormatValidationAndByteCount() {
        TextField input = new TextField();
        MaterialFieldBadge badge = new MaterialFieldBadge("Manual Key");
        badge.attach(input, "Hex");

        // Empty input
        assertEquals(MaterialFieldBadge.Status.EMPTY, badge.getCurrentStatus());
        assertEquals(0, badge.getCurrentByteCount());
        assertTrue(badge.getText().contains("Empty"));

        // Valid 32-byte (64 hex char) AES-256 key
        input.setText("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        assertEquals(32, badge.getCurrentByteCount());
        assertTrue(badge.getText().contains("Valid · 32 bytes"));
        assertFalse(input.getStyleClass().contains("input-field-error"));

        // Odd length hex
        input.setText("00112233445566778899aabbccddeeff0");
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("Invalid"));
        assertTrue(input.getStyleClass().contains("input-field-error"));

        // Invalid hex characters
        input.setText("00112233445566778899aabbccddeeffZZ");
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("Invalid hex chars"));
    }

    @Test
    public void testExpectedBytesConstraint() {
        TextField input = new TextField("00112233445566778899aabbccddeeff"); // 16 bytes
        MaterialFieldBadge badge = new MaterialFieldBadge("AES Key");
        badge.setExpectedBytes(32); // Require 32 bytes (AES-256)
        badge.attach(input, "Hex");

        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("16B (expected 32B)"));

        // Update input to 32 bytes
        input.setText("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        assertEquals(32, badge.getCurrentByteCount());
    }

    @Test
    public void testBase64FormatValidation() {
        TextField input = new TextField();
        ComboBox<String> formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll("UTF-8", "Hex", "Base64");
        formatCombo.setValue("Base64");

        MaterialFieldBadge badge = new MaterialFieldBadge("Payload");
        badge.attach(input, formatCombo);

        // Valid Base64: "SGVsbG8gV29ybGQ=" ("Hello World", 11 bytes)
        input.setText("SGVsbG8gV29ybGQ=");
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        assertEquals(11, badge.getCurrentByteCount());

        // Invalid Base64
        input.setText("Invalid_Base64!@#");
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("Invalid Base64"));
    }

    @Test
    public void testFormatChangePreservesInputText() {
        TextField input = new TextField("Hello World!"); // 12 UTF-8 bytes
        ComboBox<String> formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll("UTF-8", "Hex", "Base64");
        formatCombo.setValue("UTF-8");

        MaterialFieldBadge badge = new MaterialFieldBadge("Data");
        badge.attach(input, formatCombo);

        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        assertEquals(12, badge.getCurrentByteCount());

        // Change format combo to Hex (without modifying input text)
        formatCombo.setValue("Hex");
        assertEquals("Hello World!", input.getText(), "Input text must NOT be erased or converted on format change");
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("Invalid hex chars"));
    }

    @Test
    public void testKeyReferenceState() {
        TextField input = new TextField();
        MaterialFieldBadge badge = new MaterialFieldBadge("Key Source");

        // Available key reference
        badge.updateStateKeyReference("MyAESKey", "AES-256", "1A2B3C", true);
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("MyAESKey (AES-256) KCV: 1A2B3C · Available"));

        // Metadata-only key reference
        badge.updateStateKeyReference("MissingKey", "AES-128", "000000", false);
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("Metadata-only"));
    }

    @Test
    public void testIncompleteStateForRequiredSalt() {
        TextField input = new TextField("");
        MaterialFieldBadge badge = new MaterialFieldBadge("Salt");
        badge.attach(input, "Hex");

        badge.updateStateIncomplete("Salt required");
        assertEquals(MaterialFieldBadge.Status.INCOMPLETE, badge.getCurrentStatus());
        assertTrue(badge.getText().contains("Incomplete · Salt required"));
    }

    @Test
    public void testAadHexAndAsciiHandling() {
        TextField input = new TextField();
        MaterialFieldBadge badge = new MaterialFieldBadge("AAD");
        badge.attach(input, "Hex / ASCII");

        // Hex AAD: 4 bytes (8 hex chars)
        input.setText("00112233");
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        assertEquals(4, badge.getCurrentByteCount());
        assertTrue(badge.getText().contains("Valid · 4 bytes"));
        assertTrue(badge.getText().contains("[Hex]"));

        // Non-hex ASCII AAD: "hello" (5 bytes)
        input.setText("hello");
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        assertEquals(5, badge.getCurrentByteCount());
        assertTrue(badge.getText().contains("Valid · 5 bytes"));
        assertTrue(badge.getText().contains("[ASCII]"));
    }

    @Test
    public void testAadNonAsciiCharactersMatchHandler() {
        TextField input = new TextField();
        MaterialFieldBadge badge = new MaterialFieldBadge("AAD");
        badge.attach(input, "Hex / ASCII");

        // Non-ASCII input "ñ €"
        String nonAsciiText = "ñ €";
        input.setText(nonAsciiText);

        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());

        // US-ASCII byte length matches CipherController getAADBytes fallback
        int expectedAsciiLength = nonAsciiText.trim().getBytes(StandardCharsets.US_ASCII).length;
        assertEquals(expectedAsciiLength, badge.getCurrentByteCount(), "Badge AAD byte count must match US-ASCII handler fallback");
    }

    @Test
    public void testCertificatePemAndDerHandling() throws Exception {
        TextField input = new TextField();
        MaterialFieldBadge badge = new MaterialFieldBadge("Certificate");
        badge.attach(input, "PEM / DER");

        // Corrupt PEM payload with invalid Base64 chars -> must be INVALID
        input.setText("-----BEGIN CERTIFICATE-----\nMIICXzCCAcgCCQ... \n-----END CERTIFICATE-----");
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus(), "Corrupt PEM payload with '...' must evaluate to INVALID");
        assertTrue(badge.getText().contains("Invalid"));

        // Arbitrary hex string that is NOT a valid DER certificate -> must be INVALID
        input.setText("3082014a020101");
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus(), "Arbitrary hex sequence must evaluate to INVALID X.509 DER structure");
        assertTrue(badge.getText().contains("Invalid X.509 DER structure"));

        // Valid self-signed X.509 PEM certificate
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "TestCert";
        config.organization = "CryptoCarver";
        config.validityDays = 365;
        X509Certificate cert = CertificateGenerator.generateSelfSignedCertificate(pair, config);
        String validPem = CertificateGenerator.exportCertificatePEM(cert);

        input.setText(validPem);
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus(), "Valid self-signed PEM certificate must evaluate to VALID");
        assertEquals(cert.getEncoded().length, badge.getCurrentByteCount());
        assertTrue(badge.getText().contains("[PEM]"));
    }

    @Test
    public void testBadgeVisibilityAndManagedBinding() {
        TextField input = new TextField();
        MaterialFieldBadge badge = new MaterialFieldBadge("IV / Nonce");
        badge.attach(input, "Hex");

        assertTrue(badge.isVisible());
        assertTrue(badge.isManaged());

        // Hide input control
        input.setVisible(false);
        input.setManaged(false);

        assertFalse(badge.isVisible(), "Badge visible property must bind to target input control");
        assertFalse(badge.isManaged(), "Badge managed property must bind to target input control");
    }

    @Test
    public void testSpaceNormalizationMatchHandlers() {
        TextField input = new TextField("  hello  ");
        MaterialFieldBadge badge = new MaterialFieldBadge("Text");
        badge.attach(input, "UTF-8");

        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
        // Byte count of trimmed "hello" is 5 bytes
        assertEquals(5, badge.getCurrentByteCount(), "MaterialFieldBadge byte count must apply trim to match handlers");
    }

    @Test
    public void testSecretRedactionInHistorySnapshots() {
        TestCryptoController controller = new TestCryptoController();

        Map<String, Object> state = UiStateSnapshot.captureHistoryRecipe(controller);

        assertFalse(state.isEmpty(), "Captured history recipe map must NOT be empty");

        String[] expectedSecretKeys = {
            "TestCryptoController.kdfSaltField",
            "TestCryptoController.kdfInfoField",
            "TestCryptoController.signatureVerifyField",
            "TestCryptoController.authMacVerifyField",
            "TestCryptoController.certInputArea",
            "TestCryptoController.authInputArea",
            "TestCryptoController.signaturePrivateKeyArea",
            "TestCryptoController.cipherInputArea",
            "TestCryptoController.symmetricKeyField"
        };

        for (String expectedKey : expectedSecretKeys) {
            assertTrue(state.containsKey(expectedKey), "State map must contain key " + expectedKey);
            assertEquals("[REDACTED_SECRET]", state.get(expectedKey), "Field " + expectedKey + " must be redacted as [REDACTED_SECRET] in history snapshots");
        }
    }

    @Test
    public void testInvalidPemCannotReachReady() {
        TextField pemInput = new TextField("-----BEGIN PRIVATE KEY-----\ncorrupt_base64_payload===\n-----END PRIVATE KEY-----");
        MaterialFieldBadge badge = new MaterialFieldBadge("Private Key");
        badge.attach(pemInput, "PEM");

        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus(), "Corrupt PEM must have INVALID badge status");

        PreflightReport report = OperationPreflightEngine.checkDigitalSignature(
            "message to sign",
            "RSA-SHA256-PKCS1",
            pemInput.getText(),
            null,
            false,
            true
        );

        assertNotEquals(PreflightStatus.READY, report.getOverallStatus(), "Corrupt PEM must NOT reach preflight READY status");
        assertEquals(PreflightStatus.BLOCKED, report.getOverallStatus(), "Corrupt PEM must produce BLOCKED preflight status");
    }

    @Test
    public void testPreflightReadyMatchesRealExecution() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        String pemPrivate = AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate());

        PreflightReport report = OperationPreflightEngine.checkDigitalSignature(
            "Hello World",
            "RSA-SHA256-PKCS1",
            pemPrivate,
            null,
            false,
            true
        );

        assertEquals(PreflightStatus.READY, report.getOverallStatus(), "Valid parameters must produce READY preflight status");

        byte[] sig = SignatureOperations.sign("Hello World".getBytes(StandardCharsets.UTF_8), pair.getPrivate(), "RSA-SHA256-PKCS1");
        assertNotNull(sig);
        assertTrue(sig.length > 0, "Execution must succeed when preflight report is READY");
    }

    @Test
    public void testSnapshotsFullAndEditableInputsContainNoSecrets() {
        TestCryptoController controller = new TestCryptoController();

        Map<String, Object> historyState = UiStateSnapshot.captureHistoryRecipe(controller);

        for (String key : new String[]{
            "TestCryptoController.symmetricKeyField",
            "TestCryptoController.cipherInputArea",
            "TestCryptoController.kdfSaltField",
            "TestCryptoController.signatureVerifyField"
        }) {
            assertEquals("[REDACTED_SECRET]", historyState.get(key), "History recipe snapshot must redact " + key);
        }
    }

    @Test
    public void testHexBase64Utf8ExactByteCounting() throws Exception {
        String hexInput = "48656c6c6f"; // "Hello" (5 bytes)
        assertEquals(5, SharedMaterialParser.parseBytesByFormat(hexInput, "Hex").length);

        String b64Input = Base64.getEncoder().encodeToString("CryptoCarver".getBytes(StandardCharsets.UTF_8));
        assertEquals(12, SharedMaterialParser.parseBytesByFormat(b64Input, "Base64").length);

        String utf8Input = "Hola Mundo 🌍";
        assertEquals("Hola Mundo 🌍".getBytes(StandardCharsets.UTF_8).length, SharedMaterialParser.parseBytesByFormat(utf8Input, "UTF-8").length);
    }

    @Test
    public void testValidBase64SignatureBadgeAndPreflightReady() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        byte[] sigBytes = SignatureOperations.sign("Hello World".getBytes(StandardCharsets.UTF_8), pair.getPrivate(), "RSA-SHA256-PKCS1");
        String b64Sig = Base64.getEncoder().encodeToString(sigBytes);

        MaterialFieldBadge badge = new MaterialFieldBadge("Signature to verify");
        TextField sigField = new TextField(b64Sig);
        badge.attach(sigField, "Hex / Base64");
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());

        String pubPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";

        PreflightReport report = OperationPreflightEngine.checkDigitalSignature(
            "Hello World",
            "RSA-SHA256-PKCS1",
            pubPem,
            b64Sig,
            false,
            false // Verify mode
        );
        assertEquals(PreflightStatus.READY, report.getOverallStatus());
    }

    @Test
    public void testInvalidSignatureAndMacPreflightBlocked() throws Exception {
        String invalidSig = "!!!InvalidBase64OrHex!!!";

        MaterialFieldBadge badgeSig = new MaterialFieldBadge("Signature to verify");
        TextField sigField = new TextField(invalidSig);
        badgeSig.attach(sigField, "Hex / Base64");
        assertEquals(MaterialFieldBadge.Status.INVALID, badgeSig.getCurrentStatus());

        PreflightReport sigReport = OperationPreflightEngine.checkDigitalSignature(
            "Hello World",
            "RSA-SHA256-PKCS1",
            "dummyKey",
            invalidSig,
            false,
            false // Verify mode
        );
        assertEquals(PreflightStatus.BLOCKED, sigReport.getOverallStatus());
        assertTrue(sigReport.getChecks().stream().anyMatch(c -> "signatureVerifyField".equals(c.getTargetControlKey()) && c.getStatus() == PreflightStatus.BLOCKED));

        PreflightReport macReport = OperationPreflightEngine.checkMac(
            "Hello World",
            "HMAC-SHA256",
            "Manual Input",
            "00112233445566778899AABBCCDDEEFF",
            null,
            invalidSig,
            false, // Verify mode
            false
        );
        assertEquals(PreflightStatus.BLOCKED, macReport.getOverallStatus());
        assertTrue(macReport.getChecks().stream().anyMatch(c -> "authMacVerifyField".equals(c.getTargetControlKey()) && c.getStatus() == PreflightStatus.BLOCKED));
    }

    @Test
    public void testSuperficialBase64PemIsInvalid() {
        String superficialPem = "-----BEGIN PRIVATE KEY-----\nSGVsbG8gV29ybGQ=\n-----END PRIVATE KEY-----";

        MaterialFieldBadge badge = new MaterialFieldBadge("Private Key");
        TextArea area = new TextArea(superficialPem);
        badge.attach(area, "PEM");
        assertEquals(MaterialFieldBadge.Status.INVALID, badge.getCurrentStatus());
    }

    @Test
    public void testValidPemIsBadgeValid() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privPem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";

        MaterialFieldBadge badge = new MaterialFieldBadge("Private Key");
        TextArea area = new TextArea(privPem);
        badge.attach(area, "PEM");
        assertEquals(MaterialFieldBadge.Status.VALID, badge.getCurrentStatus());
    }
}
