package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseOkpCharacterizationUITest {
    @BeforeAll static void start() throws Exception { JoseCharacterizationSupport.startFx(); }
    @Test void curvesEd448JdkInteropRotationConversionAndPrivacy() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                assertNotNull(p.combo("jwkCurveCombo"), "OKP curve selector is missing");
                assertEquals(List.of("Ed25519", "Ed448", "X25519", "X448"), p.combo("jwkCurveCombo").getItems());
                p.combo("jwkCurveCombo").setValue("X448");
                p.combo("jwksRotateAlgoCombo").setValue("EdDSA");
                assertEquals("Ed25519", p.combo("jwkCurveCombo").getValue());
                p.combo("jwksRotateAlgoCombo").setValue("ECDH-ES+A128KW");
                assertEquals("X25519", p.combo("jwkCurveCombo").getValue());
                assertEquals("ECDH-ES+A128KW", p.combo("jwksRotateAlgoCombo").getValue());
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    p.line("curve_label", p.label("jwkCurveLabel").getText());
                    for (String curve : p.combo("jwkCurveCombo").getItems()) {
                        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                        p.combo("jwkKeyTypeCombo").setValue("OKP");
                        p.combo("jwkCurveCombo").setValue(curve);
                        p.combo("jwksRotateAlgoCombo").setValue(curve.startsWith("Ed") ? "EdDSA" : curve.equals("X448") ? "ECDH-ES-X448" : "ECDH-ES");
                        p.area("jwksSecretArea").setText("{\"keys\":[]}");
                        p.invoke("handleRotateKey");
                        OctetKeyPair key = (OctetKeyPair)JWKSet.parse(p.area("jwksSecretArea").getText()).getKeys().get(0);
                        assertEquals(curve, key.getCurve().getName());
                        assertEquals(curve.startsWith("Ed") ? "sig" : "enc", key.getKeyUse().identifier());
                        TextArea pem = new TextArea();
                        p.controller.convertJwkToPem(key.toJSONString(), pem);
                        String privatePem = pem.getText().substring(pem.getText().indexOf("-----BEGIN PRIVATE KEY-----"));
                        JWK imported = JOSEController.asymmetricJwk(privatePem, "OKP", "invented-import");
                        assertEquals(key.computeThumbprint(), imported.computeThumbprint());
                        p.line(curve, "generated;coherent-use;JWK/PEM-thumbprint-preserved");
                        if (curve.equals("Ed448")) {
                            String raw = "invented Ed448 JWS 73";
                            String jws = JOSEService.signJws(raw, "EdDSA", key.toJSONString());
                            assertEquals(raw, JOSEService.verifyJws(jws, "EdDSA", key.toPublicJWK().toJSONString()));
                            JWSObject object = JWSObject.parse(jws);
                            Signature jdk = Signature.getInstance("Ed448");
                            jdk.initVerify(JoseKeyMaterial.publicKey(key.toJSONString())); jdk.update(object.getSigningInput());
                            assertTrue(jdk.verify(object.getSignature().decode()));
                            jdk.initSign(JoseKeyMaterial.privateKey(key.toJSONString())); jdk.update(object.getSigningInput());
                            String independentlySigned = object.getHeader().toBase64URL() + "." + object.getPayload().toBase64URL() + "." + Base64URL.encode(jdk.sign());
                            assertEquals(raw, JOSEService.verifyJws(independentlySigned, "EdDSA", key.toPublicJWK().toJSONString()));
                            p.controller.generateSignedJWT("{\"sub\":\"invented-ed448-73\"}", List.of(new SignerConfig("EdDSA", key.toJSONString())), "Compact", false,
                                    "{\"kid\":\"" + key.getKeyID() + "\"}", p.area("jwtOutputArea"));
                            String token = p.area("jwtOutputArea").getText();
                            p.invoke("handleRotateKey");
                            String rotatedSet = p.area("jwksSecretArea").getText();
                            JWKSet keys = JWKSet.parse(rotatedSet);
                            assertEquals(2, keys.getKeys().size());
                            String publicSet = p.controller.exportPublicJWKS(rotatedSet);
                            assertTrue(JWSObject.parse(token).verify(JOSEService.resolveVerifier(JWSObject.parse(token).getHeader(), publicSet, JoseKeyMaterial.SecretEncoding.UTF8)));
                            JWK next = keys.getKeys().get(1);
                            String nextToken = JOSEService.generateSignedJWT("{\"sub\":\"invented-next-73\"}", List.of(new SignerConfig("EdDSA", next.toJSONString())), "Compact", false, "{\"kid\":\"" + next.getKeyID() + "\"}");
                            assertTrue(JWSObject.parse(nextToken).verify(JOSEService.resolveVerifier(JWSObject.parse(nextToken).getHeader(), publicSet, JoseKeyMaterial.SecretEncoding.UTF8)));
                            p.controller.validateJWTAdvanced(token, publicSet, null, null, 0, false, false, JoseKeyMaterial.SecretEncoding.UTF8,
                                    p.area("jwtDecodedHeaderArea"), p.area("jwtDecodedPayloadArea"), p.label("jwtStatusLabel"));
                            assertTrue(p.reporter.result.getDetails().stream().anyMatch(d -> "Signature".equals(d.name()) && "VALID".equals(d.value())));
                            p.line("Ed448_interop", "JDK-verifies-JOSE;JOSE-verifies-JDK;JWT/JWS-valid;both-rotation-kids-valid");
                        }
                        for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                            AppSettings.getInstance().setSecretVisibilityProfile(profile);
                            p.controller.convertPemToJwk(privatePem, "OKP", "invented-73", p.area("jwkOutputArea"));
                            assertFalse(p.area("jwkOutputArea").getText().contains(key.getD().toString()));
                            p.privacy(p.reporter.result, key.getD().toString());
                            p.line(curve + "_" + profile, p.reporter.result.getStatusMessage());
                        }
                    }
                    TextArea invalid = new TextArea();
                    p.controller.convertJwkToPem("{invented-bad-key}", invalid);
                    assertTrue(invalid.getText().startsWith("Error converting JWK to PEM:"));
                    p.line(language + "_error", "readable;provider-text-not-recorded");
                }
                p.digest("3794f33085d21f997300f67535bdd7e731e7729e0573e82141fc16af1adc76b1");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
