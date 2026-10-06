package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.nimbusds.jose.util.Base64URL;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named="runUiTests", matches="true")
class JoseInspectorPrivacyCharacterizationUITest {
    @BeforeAll static void start() throws Exception { JoseCharacterizationSupport.startFx(); }
    @Test void privateHeaderPayloadAndPemCannotReachInspectorCopyOrExpand() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            try (var p = new JoseCharacterizationSupport()) {
                String secret = "invented-private-oct-73";
                String jwk = "{\"kty\":\"oct\",\"k\":\"" + secret + "\"}";
                String header = Base64URL.encode("{\"alg\":\"HS256\",\"jwk\":" + jwk + "}").toString();
                List<String> inputs = List.of(header + ".e30.AA", "eyJhbGciOiJIUzI1NiJ9." + Base64URL.encode(jwk) + ".AA", "-----BEGIN PRIVATE KEY-----\n" + secret + "\n-----END PRIVATE KEY-----");
                for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                    p.language(language);
                    for (String input : inputs) {
                        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                        TextFlow flow = p.control("inspectorOutputFlow");
                        p.controller.inspectToken(input, flow);
                        assertTrue(p.controller.getInspectorReportText().contains(secret));
                        // Copy Report and Expand Report both obtain this same live report text.
                        for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                            AppSettings.getInstance().setSecretVisibilityProfile(profile);
                            String report = p.controller.getInspectorReportText();
                            assertFalse(report.contains(secret), "changing profile must protect previously rendered reports");
                            p.controller.inspectToken(input, flow);
                            assertFalse(p.controller.getInspectorReportText().contains(secret));
                            assertTrue(p.reporter.history.isEmpty(), "inspection must not publish private raw data");
                            p.line(language + "_" + profile, p.controller.getInspectorReportText());
                        }
                    }
                }
                p.digest("TO_BE_FILLED");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
