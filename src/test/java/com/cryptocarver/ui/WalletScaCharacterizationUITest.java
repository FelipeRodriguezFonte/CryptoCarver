package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.SdJwtOperations;
import com.cryptocarver.crypto.Ts12ScaOperations;
import com.cryptocarver.crypto.XMLSignatureOperations;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WalletScaCharacterizationUITest extends WalletRemainingCharacterizationSupport {
    private static final String GOLD = "6922b34954cd3c9a884f92653ddd413ef4f60d5abaa4d0739e690ca626403caf";
    private static final String PAYMENT = "{\"amount\": \"123.45\", \"currency\": \"EUR\", \"payee\": \"Comercio de Pruebas\"}";
    private static final String OTHER_PAYMENT = "{\"amount\": \"999.99\", \"currency\": \"EUR\", \"payee\": \"Otro Comercio\"}";
    private static final String AUDIENCE = "https://psp.lab.invalid";

    @Test void buildsAndVerifiesScaInspectsRequestsAndValidatesLocalDocuments() throws Exception {
        ECKey issuer = new ECKeyGenerator(Curve.P_256).generate();
        ECKey holder = new ECKeyGenerator(Curve.P_256).generate();
        String issuerPublic = pem("PUBLIC KEY", issuer.toECPublicKey().getEncoded());
        String holderPublic = pem("PUBLIC KEY", holder.toECPublicKey().getEncoded());
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");
        String otherEntry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), OTHER_PAYMENT, "sha-256");
        String presentation = presentation(issuer, holder, entry);

        ECKey verifierKey = new ECKeyGenerator(Curve.P_256).generate();
        JWSObject request = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("oauth-authz-req+jwt")).build(),
                new Payload("""
                        {"client_id":"x509_san_dns:verifier.lab.invalid","response_type":"vp_token",\
                        "response_mode":"direct_post.jwt","nonce":"n-80-invented","state":"s80",\
                        "dcql_query":{"credentials":[{"id":"pid","format":"dc+sd-jwt"}]}}"""));
        request.sign(new ECDSASigner(verifierKey));
        String verifierPublic = pem("PUBLIC KEY", verifierKey.toECPublicKey().getEncoded());

        String signedXml = signedXml();

        withWallet(() -> {
            List<String> transcript = new ArrayList<>();
            forEachLanguageAndProfile(transcript, lines -> {
                shell.navigateTo("SCA / OpenID4VP");
                replaceReporter();
                put("scaPayloadArea", "");
                lines.add(observe("handleScaBuild", "scaEntryArea"));
                put("scaPayloadArea", PAYMENT);
                put("scaCredentialIdsField", "sca");
                put("scaTransactionDataArea", "");
                combo("scaTypeCombo").setValue(Ts12ScaOperations.TransactionType.PAYMENT.urn());
                lines.add(observe("handleScaBuild", "scaEntryArea"));
                assertEquals(entry, text("scaEntryArea"));
                lines.add("build|copied-to-verify=" + entry.equals(text("scaTransactionDataArea")));
                put("scaTransactionDataArea", "kept-by-user");
                lines.add(observe("handleScaBuild", "scaEntryArea"));
                lines.add("build|verify-pane-kept=" + "kept-by-user".equals(text("scaTransactionDataArea")));
                put("scaPayloadArea", "{not json");
                lines.add(observe("handleScaBuild", "scaEntryArea"));

                put("scaPresentationArea", "");
                lines.add(observe("handleScaVerify", "scaOutputArea"));
                put("scaPresentationArea", presentation);
                put("scaIssuerKeyArea", "");
                lines.add(observe("handleScaVerify", "scaOutputArea"));
                put("scaIssuerKeyArea", issuerPublic);
                put("scaHolderKeyArea", holderPublic);
                put("scaTransactionDataArea", entry);
                put("scaAudienceField", AUDIENCE);
                put("scaNonceField", "nonce-80");
                put("scaResponseModeField", "direct_post.jwt");
                lines.add(observe("handleScaVerify", "scaOutputArea"));
                put("scaTransactionDataArea", otherEntry);
                lines.add(observe("handleScaVerify", "scaOutputArea"));
                put("scaPresentationArea", "not-a-presentation");
                lines.add(observe("handleScaVerify", "scaOutputArea"));

                put("oid4vpRequestArea", "");
                lines.add(observe("handleOid4vpInspect", "oid4vpOutputArea"));
                put("oid4vpRequestArea", request.serialize());
                put("oid4vpKeyArea", verifierPublic);
                lines.add(observe("handleOid4vpInspect", "oid4vpOutputArea"));
                put("oid4vpKeyArea", "");
                lines.add(observe("handleOid4vpInspect", "oid4vpOutputArea"));

                shell.navigateTo("AdES Validation");
                replaceReporter();
                put("adesDocumentArea", "");
                lines.add(observe("handleAdesValidate", "adesOutputArea"));
                lines.add(observe("handleAdesEtsiReport", "adesOutputArea"));
                put("adesFileNameField", "signed.xml");
                put("adesDocumentArea", Base64.getEncoder().encodeToString(signedXml.getBytes(StandardCharsets.UTF_8)));
                lines.add(observe("handleAdesValidate", "adesOutputArea"));
                lines.add(observe("handleAdesEtsiReport", "adesOutputArea"));
                lines.add("ades|etsi-xml=" + text("adesOutputArea").contains("ValidationReport"));
            });
            pinTranscript("wallet-6", GOLD, transcript);
        });
    }

    private static String presentation(ECKey issuer, ECKey holder, String entry) throws Exception {
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue("""
                {"iss": "https://bank.lab.invalid", "category": "urn:eu:europa:ec:eudi:sua:sca",
                 "account_holder": "John Doe"}""", List.of("account_holder"), 0,
                SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, new ECDSASigner(issuer), null);
        JsonObject extra = new JsonObject();
        extra.addProperty("jti", UUID.randomUUID().toString() + UUID.randomUUID());
        extra.addProperty("response_mode", "direct_post.jwt");
        JsonArray amr = new JsonArray();
        amr.add("pin_6_or_more_digits");
        amr.add("key_in_local_native_wscd");
        extra.add("amr", amr);
        JsonArray hashes = new JsonArray();
        hashes.add(Ts12ScaOperations.hashTransactionData(entry, "sha-256"));
        extra.add("transaction_data_hashes", hashes);
        extra.addProperty("transaction_data_hashes_alg", "sha-256");
        return SdJwtOperations.present(issued,
                issued.disclosures().stream().map(SdJwtOperations.Disclosure::digest).toList(),
                new SdJwtOperations.KeyBinding(AUDIENCE, "nonce-80", JWSAlgorithm.ES256,
                        new ECDSASigner(holder), Instant.now(), extra));
    }

    private String signedXml() throws Exception {
        String password = "invented-wallet-80-" + UUID.randomUUID();
        var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Invented Wallet 80 Signer";
        var certificate = CertificateGenerator.generateSelfSignedCertificate(pair, config);
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, password.toCharArray());
        store.setKeyEntry("signer", pair.getPrivate(), password.toCharArray(),
                new java.security.cert.Certificate[]{certificate});
        var keyFile = tempDir.resolve("wallet-80-signing.p12");
        try (var out = Files.newOutputStream(keyFile)) { store.store(out, password.toCharArray()); }
        return XMLSignatureOperations.signXAdES("<invoice><amount>80</amount></invoice>", keyFile.toString(), password);
    }
}
