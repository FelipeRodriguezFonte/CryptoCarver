package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.cryptocarver.ui.JoseCharacterizationSupport.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseJwtCharacterizationUITest {
    @BeforeAll static void start() throws Exception { startFx(); }
    @Test void jwtJwsDetachedNestedClaimsErrorsAndPrivacy() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                p.language(LanguagePreference.EN);
                p.area("jwtPayloadArea").setText(PAYLOAD);
                p.area("jwtKeyArea").setText(KEY);
                p.combo("jwtAlgoCombo").setValue("HS256");
                for (String serialization : List.of("Compact", "Flattened JSON", "General JSON")) {
                    p.combo("jwsSerializationCombo").setValue(serialization);
                    p.invoke("handleGenerateSignedJWT");
                    String signed = p.area("jwtOutputArea").getText();
                    assertFalse(signed.isBlank(), p.reporter.error);
                    if (serialization.equals("Compact")) assertEquals(PAYLOAD, JOSEService.verifyJws(signed, "HS256", KEY));
                    else assertTrue(JOSEService.verifyDetachedJWS(signed, PAYLOAD, "HS256", KEY, UTF8));
                    p.published("signed_"+serialization);
                    p.privacy(p.reporter.result, KEY);
                    p.area("detachedPayloadArea").setText(PAYLOAD);
                    p.area("detachedSigningKeyArea").setText(KEY);
                    p.combo("detachedAlgoCombo").setValue("HS256");
                    p.combo("detachedSerializationCombo").setValue(serialization);
                    for (boolean unencoded : List.of(false, true)) {
                        p.check("detachedUnencodedCheck").setSelected(unencoded);
                        p.invoke("handleGenerateDetachedJWS");
                        p.area("detachedVerificationKeyArea").setText(KEY);
                        p.invoke("handleVerifyDetachedJWS");
                        assertEquals("VALID DETACHED SIGNATURE", p.label("detachedStatusLabel").getText());
                        p.line("detached_"+serialization+"_b64="+!unencoded, p.label("detachedStatusLabel").getText());
                    }
                }
                p.combo("jwsSerializationCombo").setValue("General JSON");
                p.area("jwtKeyArea2").setText(KEY+KEY);
                p.combo("jwtAlgo2Combo").setValue("HS512");
                p.invoke("handleGenerateSignedJWT");
                assertTrue(p.area("jwtOutputArea").getText().contains("signatures"));
                p.published("multiple_signers");
                p.area("jwtKeyArea2").clear();
                p.combo("jwsSerializationCombo").setValue("Compact");
                p.invoke("handleGenerateSignedJWT");
                p.area("jwtValidateTokenArea").setText(p.area("jwtOutputArea").getText());
                p.area("jwtValidateKeyArea").setText(KEY);
                p.invoke("handleValidateJWT");
                assertTrue(p.label("jwtStatusLabel").getStyle().contains("green"));
                p.published("validated");
                p.privacy(p.reporter.result, KEY);
                p.combo("jwtAlgoCombo").setValue("none");
                p.area("jwtKeyArea").clear();
                p.invoke("handleGenerateSignedJWT");
                p.area("jwtValidateTokenArea").setText(p.area("jwtOutputArea").getText());
                p.check("jwtAcceptNoneCheck").setSelected(true);
                p.invoke("handleValidateJWT");
                p.published("none_opt_in");
                p.controller.generateNestedJWT(PAYLOAD, "HS256", KEY, "dir", "A256GCM", KEY, false, UTF8, p.area("nestedOutputArea"));
                String nested = p.area("nestedOutputArea").getText();
                assertFalse(nested.isBlank(), p.reporter.error);
                p.controller.verifyNestedJWT(nested, KEY, KEY, UTF8, p.area("nestedPayloadOutputArea"), p.label("nestedStatusLabel"));
                assertEquals(PAYLOAD, p.area("nestedPayloadOutputArea").getText());
                p.published("nested_verified");
                p.privacy(p.reporter.result, KEY, PAYLOAD);
                p.area("jwtPayloadArea").setText("{}");
                ((TextField)p.control("jwtSubField")).setText("invented-claims");
                p.invoke("handleApplyJWTClaims");
                assertTrue(p.area("jwtPayloadArea").getText().contains("invented-claims"));
                p.line("claims", "sub/iat/exp applied;clock normalized");
                for (var language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    p.area("jwtPayloadArea").clear();
                    p.invoke("handleGenerateSignedJWT");
                    assertNotNull(p.reporter.error);
                    p.line(language+"_empty", p.reporter.error);
                    p.combo("jwtAlgoCombo").setValue("none");
                    p.line(language+"_none_warning", p.label("jwtSecurityWarningLabel").getText());
                    p.combo("jwtAlgoCombo").setValue("HS256");
                    p.area("jwtKeyArea").setText("short");
                    p.line(language+"_short_warning", p.label("jwtSecurityWarningLabel").getText());
                    p.area("jwtValidateTokenArea").setText("malformed");
                    p.invoke("handleValidateJWT");
                    assertFalse(p.label("jwtStatusLabel").getText().isBlank());
                    p.line(language+"_invalid", "readable provider error normalized");
                }
                p.digest("UNFIXED");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
