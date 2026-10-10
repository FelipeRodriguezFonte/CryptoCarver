package com.cryptocarver.ui;

import com.cryptocarver.model.LanguagePreference;
import com.nimbusds.jose.jwk.*;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static com.cryptocarver.ui.JoseCharacterizationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseJwkCharacterizationUITest {
    @BeforeAll static void start() throws Exception { startFx(); }

    @Test void jwkGenerationConversionMetadataAndJwks() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                p.language(LanguagePreference.EN);
                for (String algorithm : List.of("RS256", "ES256", "ES384", "ES512", "ES256K",
                        "EdDSA", "ECDH-ES", "ECDH-ES-X448", "HS256")) {
                    String use = algorithm.startsWith("ES") || algorithm.equals("RS256")
                            || algorithm.equals("EdDSA") || algorithm.equals("HS256") ? "sig" : "enc";
                    JWK generated = p.controller.generateNewJWK(algorithm, use);
                    assertTrue(generated.isPrivate());
                    assertEquals(use, generated.getKeyUse().identifier());
                    p.line("generated_" + algorithm, generated.getKeyType() + ":" + curve(generated)
                            + ":private:" + generated.getKeyUse().identifier());

                    if (!(generated instanceof OctetSequenceKey)) {
                        TextArea output = new TextArea();
                        p.controller.convertJwkToPem(generated.toJSONString(), output);
                        String converted = output.getText();
                        assertTrue(converted.contains("BEGIN PUBLIC KEY"), algorithm + ": " + converted);
                        assertTrue(converted.contains("BEGIN PRIVATE KEY"), algorithm + ": " + converted);
                        String privatePem = converted.substring(converted.indexOf("-----BEGIN PRIVATE KEY-----"));
                        JWK roundTrip = JOSEController.asymmetricJwk(privatePem, null, null);
                        assertEquals(generated.computeThumbprint(), roundTrip.computeThumbprint());
                        TextArea thumbprint = new TextArea();
                        p.controller.calculateThumbprint(generated.toJSONString(), thumbprint);
                        assertTrue(thumbprint.getText().endsWith(generated.computeThumbprint().toString()));
                        p.line("pem_roundtrip_" + algorithm, curve(roundTrip) + ":thumbprint-preserved");
                    }
                }

                var ed448 = java.security.KeyPairGenerator.getInstance("Ed448").generateKeyPair();
                String ed448Pem = pem("PRIVATE KEY", ed448.getPrivate().getEncoded());
                JWK ed448Jwk = JOSEController.asymmetricJwk(ed448Pem, "OKP", "invented-ed448");
                assertEquals(Curve.Ed448, ((OctetKeyPair)ed448Jwk).getCurve());
                p.line("ed448_pem_import", "OKP:Ed448:private");

                JWK oct = p.controller.generateNewJWK("HS256", "sig");
                String octMaterial = java.util.Base64.getEncoder().encodeToString(((OctetSequenceKey)oct).toByteArray());
                TextArea octOutput = new TextArea();
                p.controller.convertJwkToPem(oct.toJSONString(), octOutput);
                assertTrue(octOutput.getText().contains("Base64:"));
                TextArea imported = new TextArea();
                p.controller.convertPemToJwk(octMaterial, "OCT", "invented-oct", "sig", "sign,verify", imported);
                JWK parsedOct = JWK.parse(imported.getText().split("\\n\\n// Thumbprint")[0]);
                assertEquals("sig", parsedOct.getKeyUse().identifier());
                assertEquals(List.of("sign", "verify"), parsedOct.getKeyOperations().stream()
                        .map(KeyOperation::identifier).sorted().toList());
                assertEquals("invented-oct", parsedOct.getKeyID());
                p.line("oct_roundtrip", "oct:metadata-preserved;secret-bytes=" + ((OctetSequenceKey)oct).toByteArray().length);

                // The OCT secret is read in the explicitly selected format, never guessed.
                String secretHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
                String secretThumbprint = "WqjPPRvAP8oYbAqCwMErhzTg-Quaz-vLx_cef07yhOs";
                assertEquals(List.of("UTF-8", "Hex", "Base64 / Base64URL"), p.combo("jwkSecretFormatCombo").getItems());
                assertEquals("Base64 / Base64URL", p.combo("jwkSecretFormatCombo").getValue());
                assertTrue(p.combo("jwkSecretFormatCombo").isDisable());
                p.combo("jwkKeyTypeCombo").setValue("OCT");
                assertFalse(p.combo("jwkSecretFormatCombo").isDisable());
                for (String[] input : new String[][] {
                        {"Hex", secretHex, "true"},
                        {"Base64 / Base64URL", "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", "true"},
                        {"Base64 / Base64URL", secretHex, "false"},
                        {"UTF-8", secretHex, "false"}}) {
                    p.combo("jwkSecretFormatCombo").setValue(input[0]);
                    p.area("jwkInputArea").setText(input[1]);
                    p.invoke("handlePemToJwk");
                    assertEquals(Boolean.parseBoolean(input[2]),
                            p.area("jwkOutputArea").getText().contains("// Thumbprint (SHA-256): " + secretThumbprint), input[0]);
                }
                p.combo("jwkSecretFormatCombo").setValue("Hex");
                p.area("jwkInputArea").setText("not-hex");
                p.invoke("handlePemToJwk");
                assertTrue(p.area("jwkOutputArea").getText().startsWith("Error converting to JWK:"));
                p.combo("jwkSecretFormatCombo").setValue("Base64 / Base64URL");
                p.combo("jwkKeyTypeCombo").setValue("RSA");
                p.area("jwkInputArea").clear();

                JWK rsa = p.controller.generateNewJWK("RS256", "sig");
                p.area("jwksSecretArea").setText("{\"keys\":[]}");
                p.combo("jwksRotateAlgoCombo").setValue("RS256");
                p.invoke("handleRotateKey");
                String set = p.area("jwksSecretArea").getText();
                assertEquals(1, JWKSet.parse(set).getKeys().size());
                String publicSet = p.controller.exportPublicJWKS(set);
                JWK publicKey = JWKSet.parse(publicSet).getKeys().get(0);
                assertFalse(publicKey.isPrivate());
                p.area("jwksSecretArea").setText(set);
                p.line("jwks_rotate_export", "one-private-key;public-export-contains-no-private-half");

                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    TextArea invalid = new TextArea();
                    p.controller.convertPemToJwk("invented-invalid-pem", "RSA", null, invalid);
                    assertTrue(invalid.getText().startsWith("Error converting to JWK:"));
                    p.line(language + "_invalid_pem", "readable-error");
                    invalid.clear();
                    p.controller.convertJwkToPem("{invalid-json", invalid);
                    assertTrue(invalid.getText().startsWith("Error converting JWK to PEM:"));
                    p.line(language + "_invalid_jwk", "readable-error");
                }
                p.digest("315e6810e2bc477b3f4de37ff4bc6c1b81acc057f1b4a5bbebb5f3e8a88c9d1a");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private static String curve(JWK key) {
        if (key instanceof ECKey ec) return ec.getCurve().getName();
        if (key instanceof OctetKeyPair okp) return okp.getCurve().getName();
        return key.getKeyType().getValue();
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + java.util.Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
