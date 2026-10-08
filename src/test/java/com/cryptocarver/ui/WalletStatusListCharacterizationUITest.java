package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletStatusListCharacterizationUITest extends WalletExtractionCharacterizationSupport {
    private static final String GOLD = "3b6e931309fc81d8842d56448e61738288209a39cef8d668636d31587e46c50d";
    @Test void localIssueResolveDescribeAndValidation() throws Exception {
        withWallet(() -> {
            shell.navigateTo("Status List");
            replaceReporter();
            var issuer = new ECKeyGenerator(Curve.P_256).generate();
            String privateKey = pem("PRIVATE KEY", issuer.toECPrivateKey().getEncoded());
            String publicKey = pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded());
            String uri = "https://issuer.lab.invalid/status/79";
            List<String> transcript = new ArrayList<>();
            assertEquals(List.of("1", "2", "4", "8"), combo("statusListBitsCombo").getItems());
            assertEquals(List.of("ES256", "ES384", "ES512", "RS256", "RS384", "RS512"), combo("statusListAlgoCombo").getItems());
            for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
                I18nService.getInstance().setPreference(language);
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    transcript.add(language + "|" + profile);
                    put("statusListStatusesArea", ""); validation("handleStatusListIssue", "statusListStatusesArea", transcript);
                    put("statusListStatusesArea", "0,1,2,3"); put("statusListUriField", ""); validation("handleStatusListIssue", "statusListUriField", transcript);
                    put("statusListUriField", uri); put("statusListKeyArea", ""); validation("handleStatusListIssue", "statusListKeyArea", transcript);
                    put("statusListKeyArea", privateKey); combo("statusListBitsCombo").setValue("2");
                    valid("handleStatusListIssue", "statusListOutputArea", transcript);
                    String token = text("statusListOutputArea");
                    JWSObject signed = JWSObject.parse(token); assertTrue(signed.verify(JOSEService.createVerifier(JWSAlgorithm.ES256, publicKey)));
                    assertEquals(3, token.split("\\.", -1).length); assertEquals(86, token.split("\\.", -1)[2].length());
                    transcript.add("issue|" + stableJson(signed.getHeader().toString()) + "|" + stableJson(signed.getPayload().toString()) + "|ES256-signature-length=86");
                    replaceReporter();
                    put("statusListTokenArea", ""); validation("handleStatusListResolve", "statusListTokenArea", transcript);
                    put("statusListTokenArea", token); put("statusListIndexField", ""); validation("handleStatusListResolve", "statusListIndexField", transcript);
                    put("statusListIndexField", "2"); put("statusListVerifyKeyArea", publicKey);
                    valid("handleStatusListResolve", "statusListResolveOutputArea", transcript);
                    var independent = StatusListOperations.resolve(StatusListOperations.statusClaim(uri, 2), token,
                            JOSEService.createVerifier(JWSAlgorithm.ES256, publicKey));
                    assertEquals(2, independent.status());
                    assertEquals("index 2 -> 2 (" + independent.description() + ")\n", text("statusListResolveOutputArea"));
                    transcript.add("resolve|" + text("statusListResolveOutputArea"));
                    put("statusListIndexField", "not-a-number"); put("statusListVerifyKeyArea", "");
                    valid("handleStatusListResolve", "statusListResolveOutputArea", transcript);
                    assertTrue(text("statusListResolveOutputArea").startsWith("index 0 -> 0"));
                    transcript.add("resolve-fallback-unverified|" + text("statusListResolveOutputArea"));
                    valid("handleStatusListDescribe", "statusListResolveOutputArea", transcript);
                    assertEquals(StatusListOperations.describe(token), text("statusListResolveOutputArea"));
                    List<String> report = new ArrayList<>();
                    for (String line : text("statusListResolveOutputArea").replaceAll("\\d+ compressed", "<COMPRESSED_LENGTH> compressed").split("\\R", -1)) {
                        int json = line.indexOf('{'); report.add(json < 0 ? line : line.substring(0, json) + stableJson(line.substring(json)));
                    }
                    transcript.add("describe|" + String.join("\n", report));
                    put("statusListTokenArea", ""); validation("handleStatusListDescribe", "statusListTokenArea", transcript);
                    put("statusListTokenArea", "invalid-local-token"); invalidStructure("handleStatusListResolve", "statusListTokenArea", transcript);
                }
            }
            pinTranscript("wallet-3", GOLD, transcript);
        });
    }
}
