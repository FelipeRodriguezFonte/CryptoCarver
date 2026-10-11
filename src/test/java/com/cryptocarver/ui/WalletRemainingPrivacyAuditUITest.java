package com.cryptocarver.ui;

import com.cryptocarver.crypto.CborInspector;
import com.upokecenter.cbor.CBORObject;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.HexFormat;
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

    @Test
    void cborCosePrivateEc2IsHiddenOutsideFullLabAcrossViewsAndHandlers() throws Exception {
        auditCoseKey(coseEc2Private(), "EC2 private", true);
    }

    @Test
    void cborCoseSymmetricKeyIsHiddenOutsideFullLabAcrossViewsAndHandlers() throws Exception {
        auditCoseKey(coseSymmetric(), "symmetric", true);
    }

    @Test
    void cborCosePublicEc2RemainsVisibleInEveryProfile() throws Exception {
        auditCoseKey(coseEc2Public(), "EC2 public", false);
    }

    private void auditCoseKey(CBORObject coseKey, String label, boolean privateMaterial) throws Exception {
        withWallet(() -> {
            List<String> violations = new ArrayList<>();
            // The encoded input has an array enclosing an array enclosing the COSE map.
            byte[] cbor = CBORObject.NewArray().Add(CBORObject.NewArray().Add(coseKey)).EncodeToBytes();
            String inputHex = DataConverter.bytesToHex(cbor);
            String notice = com.cryptocarver.service.I18nService.getInstance()
                    .text("module.wallet.privateJwkHidden");
            List<String> secretForms = privateMaterial ? secretForms(coseKey) : List.of();

            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                for (String view : List.of("tree", "diagnostic", "summary")) {
                    resetShared();
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    shell.navigateTo("CBOR Inspector");
                    replaceReporter();
                    put("cborInputArea", inputHex);
                    combo("cborViewCombo").setValue(view);
                    String expectedInspect = switch (view) {
                        case "diagnostic" -> CborInspector.diagnostic(cbor);
                        case "summary" -> CborInspector.summary(cbor);
                        default -> CborInspector.tree(cbor);
                    };
                    auditCborHandler(profile, label, privateMaterial, secretForms, notice,
                            "handleCborInspect", expectedInspect, expectedInspect, violations);

                    // Restore the same input and view after the previous handler's UI changes.
                    resetShared();
                    replaceReporter();
                    put("cborInputArea", inputHex);
                    combo("cborViewCombo").setValue(view);
                    auditCborHandler(profile, label, privateMaterial, secretForms, notice,
                            "handleCborToJson", CborInspector.toJson(cbor),
                            CborInspector.toJson(CborInspector.parseHex(inputHex)), violations);
                }
            }
            assertTrue(violations.isEmpty(), String.join("\n", violations));
        });
    }

    private void auditCborHandler(SecretVisibilityProfile profile, String label, boolean privateMaterial,
                                  List<String> secretForms, String notice, String handler,
                                  String expectedDisplay, String expectedPublished, List<String> violations) throws Exception {
        resetCborVisibleState();
        showResult("cborOutputArea");
        invoke(handler);
        String caseLabel = profile + " COSE " + label + " " + handler;
        assertNull(recorder.error, caseLabel + " must not fail");
        assertNotNull(recorder.result, caseLabel + " must publish");
        String displayed = text("cborOutputArea");
        String published = new String(recorder.result.getOutput(), StandardCharsets.UTF_8);
        assertFalse(displayed.isBlank(), caseLabel + " must produce output");

        boolean hidden = privateMaterial && profile != SecretVisibilityProfile.FULL_LAB;
        assertEquals(hidden ? notice : expectedDisplay, displayed, caseLabel + " display");
        assertEquals(hidden ? notice : expectedPublished, published, caseLabel + " published output");

        if (privateMaterial && profile != SecretVisibilityProfile.FULL_LAB) {
            for (String secret : secretForms) {
                assertFalse(displayed.contains(secret), caseLabel + " display leaked private bytes");
                assertFalse(published.contains(secret), caseLabel + " published output leaked private bytes");
            }
            inspectSurfaces(profile, "COSE " + label, secretForms, shell, root, new ArrayList<>(), violations);
            String historyFile = Files.readString(tempDir.resolve("history.json"));
            if (secretForms.stream().anyMatch(historyFile::contains)) {
                violations.add(caseLabel + " history.json exposed private bytes");
            }
        }
    }

    private void resetCborVisibleState() throws Exception {
        resetShared();
        put("cborOutputArea", "");
    }

    private static List<String> secretForms(CBORObject coseKey) {
        // CBOR JSON represents byte strings as unpadded base64url. Tree/diagnostic render bytes as hex.
        CBORObject secret = coseKey.get(CBORObject.FromObject(-4));
        if (secret == null) secret = coseKey.get(CBORObject.FromObject(-1));
        byte[] bytes = secret.GetByteString();
        String hex = HexFormat.of().formatHex(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return List.of(hex, hex.toUpperCase(java.util.Locale.ROOT), encoded);
    }

    private static CBORObject coseEc2Private() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();
        byte[] x = Base64.getUrlDecoder().decode(key.getX().toString());
        byte[] y = Base64.getUrlDecoder().decode(key.getY().toString());
        byte[] d = Base64.getUrlDecoder().decode(key.getD().toString());
        return coseEc2(x, y).Add(CBORObject.FromObject(-4), CBORObject.FromObject(d));
    }

    private static CBORObject coseEc2Public() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).generate();
        return coseEc2(Base64.getUrlDecoder().decode(key.getX().toString()),
                Base64.getUrlDecoder().decode(key.getY().toString()));
    }

    private static CBORObject coseEc2(byte[] x, byte[] y) {
        // COSE EC2: integer label 1=kty with value 2; -1=crv, -2=x, -3=y, optional -4=d.
        return CBORObject.NewMap().Add(CBORObject.FromObject(1), CBORObject.FromObject(2))
                .Add(CBORObject.FromObject(-1), CBORObject.FromObject(1))
                .Add(CBORObject.FromObject(-2), CBORObject.FromObject(x))
                .Add(CBORObject.FromObject(-3), CBORObject.FromObject(y));
    }

    private static CBORObject coseSymmetric() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        return CBORObject.NewMap().Add(CBORObject.FromObject(1), CBORObject.FromObject(4))
                .Add(CBORObject.FromObject(-1), CBORObject.FromObject(key.getEncoded()));
    }
}
