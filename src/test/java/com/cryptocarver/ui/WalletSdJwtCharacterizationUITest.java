package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletSdJwtCharacterizationUITest extends WalletExtractionCharacterizationSupport {
    private static final String GOLD = "cd8e794ef9704f2df560bb791abea00388101a2843f21237b51303fc6ca1634e";
    @Test void sdJwtControlsReportsValidationAndReporterReplacement() throws Exception {
        withWallet(() -> {
            List<String> transcript = new ArrayList<>();
            var issuer = new ECKeyGenerator(Curve.P_256).generate();
            var holder = new ECKeyGenerator(Curve.P_256).generate();
            String issuerPrivate = pem("PRIVATE KEY", issuer.toECPrivateKey().getEncoded());
            String issuerPublic = pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded());
            String holderPublic = pem("PUBLIC KEY", holder.toECPublicKey().getEncoded());
            assertEquals(List.of("ES256", "ES384", "ES512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512"), combo("sdJwtAlgoCombo").getItems());
            for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                I18nService.getInstance().setPreference(language);
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    transcript.add(language + "|" + profile);
                    put("sdJwtClaimsArea", "");
                    validation("handleSdJwtIssue", "sdJwtClaimsArea", transcript);
                    put("sdJwtClaimsArea", "{\"iss\":\"urn:invented:wallet79\",\"sub\":\"lab-79\",\"lab_claim\":\"synthetic\"}");
                    put("sdJwtIssuerKeyArea", "");
                    validation("handleSdJwtIssue", "sdJwtIssuerKeyArea", transcript);
                    put("sdJwtIssuerKeyArea", issuerPrivate);
                    put("sdJwtDisclosableArea", "lab_claim"); put("sdJwtDecoyField", "2"); put("sdJwtVctField", "");
                    valid("handleSdJwtIssue", "sdJwtIssueOutputArea", transcript);
                    String issued = text("sdJwtIssueOutputArea");
                    var parsed = SdJwtOperations.parse(issued);
                    assertEquals(1, parsed.disclosures().size());
                    assertEquals(3, parsed.payload().getAsJsonArray("_sd").size());
                    assertTrue(issued.endsWith("~"));
                    transcript.add("issue|" + stableJson(parsed.header().toString()) + "|" + stableJson(parsed.payload().toString()) + "|disclosures=1");
                    // A previously constructed lazy coordinator must observe a newly installed reporter.
                    RecordingReporter old = recorder; replaceReporter();
                    put("sdJwtPresentInputArea", issued); put("sdJwtRevealArea", "lab_claim");
                    put("sdJwtAudienceField", "urn:invented:verifier79"); put("sdJwtNonceField", "invented-nonce-79");
                    put("sdJwtHolderKeyArea", "");
                    validation("handleSdJwtPresent", "sdJwtAudienceField", transcript);
                    put("sdJwtHolderKeyArea", pem("PRIVATE KEY", holder.toECPrivateKey().getEncoded()));
                    valid("handleSdJwtPresent", "sdJwtPresentOutputArea", transcript);
                    assertNotSame(old.result, recorder.result);
                    String presented = text("sdJwtPresentOutputArea");
                    var independent = SdJwtOperations.verify(presented,
                            JOSEService.createVerifier(JWSAlgorithm.ES256, issuerPublic),
                            JOSEService.createVerifier(JWSAlgorithm.ES256, holderPublic), "urn:invented:verifier79", "invented-nonce-79");
                    assertTrue(independent.keyBindingPresent()); assertEquals("synthetic", independent.claims().get("lab_claim").getAsString());
                    transcript.add("present|" + stableJson(SdJwtOperations.parse(presented).keyBindingClaims().toString()) + "|disclosures=1;signature-parts=3");
                    put("sdJwtVerifyInputArea", presented); put("sdJwtVerifyIssuerKeyArea", issuerPublic);
                    put("sdJwtVerifyHolderKeyArea", holderPublic); put("sdJwtVerifyAudienceField", "urn:invented:verifier79"); put("sdJwtVerifyNonceField", "invented-nonce-79");
                    valid("handleSdJwtVerify", "sdJwtVerifyOutputArea", transcript);
                    assertEquals(independent.claimsJson(), text("sdJwtVerifyOutputArea"));
                    transcript.add("verify|" + stableJson(text("sdJwtVerifyOutputArea")));
                    put("sdJwtInspectInputArea", presented);
                    valid("handleSdJwtInspect", "sdJwtInspectOutputArea", transcript);
                    String report = text("sdJwtInspectOutputArea");
                    assertEquals(SdJwtOperations.describe(presented, I18nService.getInstance().getLocale()), report);
                    for (var disclosure : SdJwtOperations.parse(presented).disclosures()) report = report.replace(disclosure.salt(), "<SALT>").replace(disclosure.digest(), "<DIGEST>");
                    List<String> normalized = new ArrayList<>();
                    for (String line : report.split("\\R", -1)) {
                        int json = line.indexOf('{');
                        normalized.add(json < 0 ? line : line.substring(0, json) + stableJson(line.substring(json)));
                    }
                    transcript.add("inspect|" + String.join("\n", normalized));
                    put("sdJwtInspectInputArea", "invalid-synthetic-structure");
                    invalidStructure("handleSdJwtInspect", "sdJwtInspectInputArea", transcript);
                }
            }
            pinTranscript("wallet-1", GOLD, transcript);
        });
    }
}
