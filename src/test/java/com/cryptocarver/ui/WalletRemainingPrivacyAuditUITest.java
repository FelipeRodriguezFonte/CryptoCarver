package com.cryptocarver.ui;

import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.util.DataConverter;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assignment 80 phase-zero audit of the Wallet handlers left in the controller: a pasted
 * structure that carries an invented private JWK must not reach any visible or persisted
 * surface outside FULL_LAB. Local inputs only; nothing is fetched.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletRemainingPrivacyAuditUITest extends WalletExtractionCharacterizationSupport {
    /** Schema-valid TS 119 602 list, same shape as the crypto-level fixture; invented data only. */
    static final String TRUSTED_ENTITY_LIST = """
            {"LoTE":{"ListAndSchemeInformation":{"LoTEVersionIdentifier":1,"LoTESequenceNumber":7,\
            "SchemeOperatorName":[{"lang":"en","value":"Example authority"}],\
            "ListIssueDateTime":"2025-11-01T00:00:00Z","NextUpdate":"2025-12-01T00:00:00Z"},\
            "TrustedEntitiesList":[{"TrustedEntityInformation":{"TEName":[{"lang":"en","value":"Example provider"}],\
            "TEAddress":{"TEPostalAddress":[{"lang":"en","StreetAddress":"1 Example Street","Locality":"Madrid","Country":"ES"}],\
            "TEElectronicAddress":[{"lang":"en","uriValue":"mailto:contact@example.test"}]},\
            "TEInformationURI":[{"lang":"en","uriValue":"https://example.test"}]},\
            "TrustedEntityServices":[{"ServiceInformation":{"ServiceName":[{"lang":"en","value":"Wallet service"}],\
            "ServiceDigitalIdentity":{"OtherIds":["provider-1"]},\
            "ServiceTypeIdentifier":"https://example.test/type","ServiceStatus":"https://example.test/granted",\
            "StatusStartingTime":"2025-11-01T00:00:00Z","ServiceInformationExtensions":[{"qualifier":"example"}]}}]}]}}""";

    private record Case(String name, String route, String handler, String output) { }

    private static final List<Case> CASES = List.of(
            new Case("cbor-inspect", "CBOR Inspector", "handleCborInspect", "cborOutputArea"),
            new Case("cbor-to-json", "CBOR Inspector", "handleCborToJson", "cborOutputArea"),
            new Case("json-to-cbor", "CBOR Inspector", "handleCborFromJson", "cborFromJsonOutputArea"),
            new Case("trusted-entity-list-json", "Trusted Entity List JSON",
                    "handleTrustedEntityListJsonInspect", "trustedListOutputArea"),
            new Case("openid4vp", "SCA / OpenID4VP", "handleOid4vpInspect", "oid4vpOutputArea"),
            new Case("sca-transaction-data", "SCA / OpenID4VP", "handleScaBuild", "scaEntryArea"));

    @Test
    void pastedPrivateJwkDoesNotReachResultsOutsideFullLab() throws Exception {
        withWallet(() -> {
            List<String> violations = new ArrayList<>();
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                for (Case audited : CASES) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    resetShared();
                    shell.navigateTo(audited.route());
                    replaceReporter();
                    ECKey privateKey = new ECKeyGenerator(Curve.P_256).generate();
                    String jwk = privateKey.toJSONString();
                    String marker = privateKey.getD().toString();
                    // The same private scalar as it travels inside CBOR text strings and base64url JSON.
                    String markerHex = DataConverter.bytesToHex(marker.getBytes(StandardCharsets.UTF_8)).toUpperCase();
                    List<String> secrets = List.of(marker, markerHex);
                    arrange(audited.name(), jwk);

                    showResult(audited.output());
                    invoke(audited.handler());
                    String label = profile + " " + audited.name();
                    assertNull(recorder.error, label + " must not fail");
                    assertNotNull(recorder.result, label + " must publish");
                    String shown = text(audited.output());
                    assertFalse(shown.isBlank(), label + " must produce a result");
                    boolean hidden = shown.equals(
                            com.cryptocarver.service.I18nService.getInstance().text("module.wallet.privateJwkHidden"));
                    if (profile == SecretVisibilityProfile.FULL_LAB) {
                        assertFalse(hidden, label + " keeps the original result");
                    } else {
                        if (!hidden) violations.add(label + " fx:id=" + audited.output() + " shows the result of a private JWK");
                        if (secrets.stream().anyMatch(shown::contains)) {
                            violations.add(label + " fx:id=" + audited.output() + " exposes the private scalar");
                        }
                        inspectSurfaces(profile, audited.name(), secrets, shell, root, new ArrayList<>(), violations);
                        String persisted = Files.readString(tempDir.resolve("history.json"));
                        if (secrets.stream().anyMatch(persisted::contains)) {
                            violations.add(label + " history.json exposes the private scalar");
                        }
                    }
                }
            }
            assertTrue(violations.isEmpty(), String.join("\n", violations));
        });
    }

    private void arrange(String name, String jwk) throws Exception {
        switch (name) {
            case "cbor-inspect", "cbor-to-json" -> {
                put("cborInputArea", DataConverter.bytesToHex(CborInspector.fromJson("{\"lab_private_fixture\":" + jwk + "}")));
                combo("cborViewCombo").setValue("tree");
            }
            case "json-to-cbor" -> put("cborJsonArea", "{\"lab_private_fixture\":" + jwk + "}");
            case "trusted-entity-list-json" -> {
                put("trustedEntityListJsonArea", TRUSTED_ENTITY_LIST.replace("{\"qualifier\":\"example\"}", jwk));
                put("trustedEntityListSignerCertArea", "");
                put("trustedEntityListSearchCertArea", "");
            }
            case "openid4vp" -> {
                ECKey signer = new ECKeyGenerator(Curve.P_256).generate();
                JWSObject request = new JWSObject(
                        new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("oauth-authz-req+jwt")).build(),
                        new Payload("""
                                {"client_id":"x509_san_dns:verifier.lab.invalid","response_type":"vp_token",\
                                "response_mode":"direct_post.jwt","nonce":"n-80-invented","state":"s80",\
                                "client_metadata":{"jwks":{"keys":[%s]}},\
                                "dcql_query":{"credentials":[{"id":"pid","format":"dc+sd-jwt"}]}}""".formatted(jwk)));
                request.sign(new ECDSASigner(signer));
                put("oid4vpRequestArea", request.serialize());
                put("oid4vpKeyArea", "");
            }
            case "sca-transaction-data" -> {
                put("scaPayloadArea", "{\"amount\":\"80.00\",\"currency\":\"EUR\",\"lab_private_fixture\":" + jwk + "}");
                put("scaCredentialIdsField", "sca");
                put("scaTransactionDataArea", "");
            }
            default -> throw new IllegalArgumentException(name);
        }
    }
}
