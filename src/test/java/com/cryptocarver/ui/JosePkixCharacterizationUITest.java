package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JosePkixCharacterizationUITest {
    @BeforeAll static void start() throws Exception { JoseCharacterizationSupport.startFx(); }
    @Test void offlinePkixWarningsAndPublicDetails() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                assertNotNull(p.area("jwtTrustAnchorsArea"), "x5c trust anchors control is missing");
                assertNotNull(p.<TextField>control("jwtCertificateDateField"), "x5c validation date control is missing");
                var fixture = new com.cryptocarver.crypto.JoseTestPki();
                String token = fixture.token(fixture.chain());
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        p.line(language + "_anchors", p.label("jwtTrustAnchorsLabel").getText());
                        p.line(language + "_date", p.label("jwtCertificateDateLabel").getText());
                        assertFalse(p.<CheckBox>control("jwtTrustHeaderKeyCheck").isSelected());
                        p.<CheckBox>control("jwtTrustHeaderKeyCheck").setSelected(true);
                        p.area("jwtValidateTokenArea").setText(token);
                        p.area("jwtValidateKeyArea").clear();
                        p.<CheckBox>control("jwtCheckExpiryCheck").setSelected(false);
                        p.<TextField>control("jwtCertificateDateField").setText("2025-01-01T00:00:00Z");
                        p.area("jwtTrustAnchorsArea").setText(fixture.anchors());
                        p.invoke("handleValidateJWT");
                        assertTrue(p.reporter.result.getDetails().stream().anyMatch(d -> d.name().equals("x5c PKIX") && d.value().equals("PASS")));
                        p.line(profile + "_trusted", "PKIX=PASS;revocation=DISABLED;signature=VALID;public-details");
                        p.privacy(p.reporter.result, fixture.privatePem());
                        p.area("jwtTrustAnchorsArea").clear();
                        p.invoke("handleValidateJWT");
                        String warning = p.reporter.result.getDetails().stream().filter(d -> d.name().equals("Security warning") && d.value().contains(language == LanguagePreference.EN ? "PKIX" : "PKIX")).map(OperationDetail::value).findFirst().orElseThrow();
                        assertTrue(p.area("jwtFindingsArea").getText().contains(warning));
                        assertTrue(p.reporter.result.getDetails().stream().anyMatch(d -> d.name().equals("Signature") && d.value().equals("VALID")));
                        p.line(profile + "_warning", warning);
                        p.<CheckBox>control("jwtTrustHeaderKeyCheck").setSelected(false);
                    }
                    p.<TextField>control("jwtCertificateDateField").setText("invented-invalid-date");
                    p.controller.validateJWTAdvanced(token, fixture.publicPem(), null, null, 0, false, false, JoseCharacterizationSupport.UTF8,
                            p.area("jwtDecodedHeaderArea"), p.area("jwtDecodedPayloadArea"), p.label("jwtStatusLabel"));
                    assertTrue(p.label("jwtStatusLabel").getText().contains(language == LanguagePreference.EN ? "ISO-8601" : "ISO-8601"));
                    p.line(language + "_error", p.label("jwtStatusLabel").getText());
                }
                p.digest("TO_BE_FILLED");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
