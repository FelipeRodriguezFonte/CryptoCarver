package com.cryptocarver.ui;

import com.cryptocarver.crypto.JweComposer;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.service.I18nService;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.jwk.RSAKey;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static com.cryptocarver.ui.JoseCharacterizationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseJweCharacterizationUITest {
    @BeforeAll static void start() throws Exception { startFx(); }

    @Test void jweSerializationsHeadersRoundTripAndPrivacy() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                p.language(LanguagePreference.EN);
                p.area("jwePayloadArea").setText(PAYLOAD);
                p.area("jwePublicKeyArea").setText(KEY);
                p.combo("jweKeyAlgoCombo").setValue("dir");
                p.combo("jweContentAlgoCombo").setValue("A256GCM");
                ((TextField)p.control("jweKidField")).setText("recipient-1");
                ((TextField)p.control("jweTypField")).setText("JWE");
                ((TextField)p.control("jweCtyField")).setText("application/json");
                ((TextField)p.control("jweAadField")).setText("associated-data");
                p.check("jweCompressCheck").setSelected(true);
                for (String serialization : List.of("Compact", "Flattened JSON", "General JSON")) {
                    p.combo("jweSerializationCombo").setValue(serialization);
                    if (serialization.equals("Compact")) ((TextField)p.control("jweAadField")).clear();
                    else ((TextField)p.control("jweAadField")).setText("associated-data");
                    p.reporter.error = null;
                    p.reporter.result = null;
                    p.invoke("handleGenerateJWE");
                    String encrypted = p.area("jweOutputArea").getText();
                    assertFalse(encrypted.isBlank(), p.reporter.error);
                    assertNull(p.reporter.error);
                    if (serialization.equals("Compact")) {
                        JWEObject object = JWEObject.parse(encrypted);
                        assertEquals("recipient-1", object.getHeader().getKeyID());
                        assertEquals("JWE", object.getHeader().getType().toString());
                        assertEquals("application/json", object.getHeader().getContentType());
                        assertEquals("DEF", object.getHeader().toJSONObject().get("zip"));
                    } else {
                        var json = com.nimbusds.jose.util.JSONObjectUtils.parse(encrypted);
                        assertTrue(json.containsKey("aad"));
                        assertEquals("recipient-1", JweComposer.decryptJson(encrypted, KEY, UTF8)
                                .effectiveHeader().get("kid"));
                    }
                    p.published("generated_"+serialization);
                    p.line("generated_payload", "roundtrip fixture fixed");
                    p.area("jweInputArea").setText(encrypted);
                    p.area("jwePrivateKeyArea").setText(KEY);
                    p.reporter.error = null;
                    p.reporter.result = null;
                    p.invoke("handleDecryptJWE");
                    assertNull(p.reporter.error);
                    assertEquals(PAYLOAD, p.area("jweDecodedPayloadArea").getText());
                    assertTrue(p.label("jweStatusLabel").getStyle().contains("green"));
                    if (serialization.equals("Compact")) {
                        assertTrue(p.area("jweDecryptedKeyArea").getText().contains("not displayed automatically"));
                        assertFalse(p.area("jweDecryptedKeyArea").getText().contains(KEY));
                    }
                    p.published("decrypted_"+serialization);
                    p.privacy(p.reporter.result, KEY, PAYLOAD);
                }
                p.digest("UNFIXED");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void jweAlgorithmWarningsValidationAndManualCek() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                RSAKey rsa = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
                String publicPem = pem("PUBLIC KEY", rsa.toPublicKey().getEncoded());
                String privatePem = pem("PRIVATE KEY", rsa.toPrivateKey().getEncoded());
                for (String algorithm : List.of("RSA1_5", "RSA-OAEP")) {
                    p.area("jwePayloadArea").setText(PAYLOAD);
                    p.area("jwePublicKeyArea").setText(publicPem);
                    p.combo("jweKeyAlgoCombo").setValue(algorithm);
                    p.combo("jweContentAlgoCombo").setValue("A256GCM");
                    p.combo("jweSerializationCombo").setValue("Compact");
                    for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                        p.language(language);
                        String expected = I18nService.getInstance().text(algorithm.equals("RSA1_5")
                                ? "module.jose.warning.rsa15" : "module.jose.warning.oaepSha1");
                        assertEquals(expected, p.label("jweSecurityWarningLabel").getText());
                        p.reporter.error = null;
                        p.reporter.result = null;
                        p.invoke("handleGenerateJWE");
                        String encrypted = p.area("jweOutputArea").getText();
                        assertFalse(encrypted.isBlank(), p.reporter.error);
                        p.published(language+"_"+algorithm);
                        assertTrue(p.reporter.result.getDetails().stream().anyMatch(d ->
                                d.classification() == OperationDetail.Classification.PUBLIC && expected.equals(d.value())));
                        p.area("jweInputArea").setText(encrypted);
                        p.area("jwePrivateKeyArea").setText(privatePem);
                        p.reporter.error = null;
                        p.reporter.result = null;
                        p.invoke("handleDecryptJWE");
                        assertEquals(PAYLOAD, p.area("jweDecodedPayloadArea").getText());
                        assertEquals(64, p.area("jweDecryptedKeyArea").getText().length());
                        p.published(language+"_"+algorithm+"_decrypt");
                        p.privacy(p.reporter.result, privatePem, PAYLOAD,
                                p.area("jweDecryptedKeyArea").getText());
                        p.line(language+"_"+algorithm+"_cek", "manual CEK recovered;random value normalized");
                    }
                }

                for (String algorithm : List.of("PBES2-HS256+A128KW", "A256KW")) {
                    String fixedSecret = algorithm.startsWith("PBES2") ? "fixed characterization password" : KEY;
                    p.area("jwePayloadArea").setText(PAYLOAD);
                    p.area("jwePublicKeyArea").setText(fixedSecret);
                    p.area("jwePrivateKeyArea").setText(fixedSecret);
                    p.combo("jweKeyAlgoCombo").setValue(algorithm);
                    p.combo("jweContentAlgoCombo").setValue("A256GCM");
                    p.combo("jweSerializationCombo").setValue("Compact");
                    ((TextField)p.control("jwePbes2IterField")).setText("1000");
                    p.reporter.error = null;
                    p.reporter.result = null;
                    p.invoke("handleGenerateJWE");
                    String encrypted = p.area("jweOutputArea").getText();
                    assertFalse(encrypted.isBlank(), p.reporter.error);
                    assertNull(p.reporter.error);
                    p.area("jweInputArea").setText(encrypted);
                    p.reporter.error = null;
                    p.reporter.result = null;
                    p.invoke("handleDecryptJWE");
                    assertNull(p.reporter.error);
                    assertEquals(PAYLOAD, p.area("jweDecodedPayloadArea").getText());
                    assertEquals(64, p.area("jweDecryptedKeyArea").getText().length());
                    p.published(algorithm+"_decrypted");
                    p.privacy(p.reporter.result, fixedSecret, PAYLOAD,
                            p.area("jweDecryptedKeyArea").getText());
                    p.line(algorithm+"_roundtrip", "payload roundtrip;manual CEK length=32;random value normalized");
                }

                p.language(LanguagePreference.EN);
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    p.combo("jweKeyAlgoCombo").setValue("dir");
                    p.area("jwePublicKeyArea").setText(KEY);
                    p.area("jwePayloadArea").clear();
                    p.reporter.error = null;
                    p.reporter.result = null;
                    p.invoke("handleGenerateJWE");
                    assertNotNull(p.reporter.error);
                    assertFalse(p.reporter.error.isBlank());
                    String inputMessage = I18nService.getInstance().text("module.jose.feedback.inputRequired");
                    String inputRemedy = I18nService.getInstance().text("preflight.remedy.input");
                    assertTrue(p.reporter.error.contains(inputMessage) || p.reporter.error.contains(inputRemedy));
                    p.line(language+"_empty_payload", p.reporter.error);
                    p.area("jwePayloadArea").setText(PAYLOAD);
                    ((TextField)p.control("jwePbes2IterField")).setText("999");
                    p.combo("jweKeyAlgoCombo").setValue("PBES2-HS256+A128KW");
                    p.reporter.error = null;
                    p.reporter.result = null;
                    p.invoke("handleGenerateJWE");
                    assertNotNull(p.reporter.error);
                    assertFalse(p.reporter.error.isBlank());
                    assertTrue(p.reporter.error.contains(inputRemedy));
                    p.line(language+"_invalid_iterations", p.reporter.error);
                }

                p.digest("UNFIXED");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private static String pem(String type, byte[] der) {
        String body = java.util.Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
