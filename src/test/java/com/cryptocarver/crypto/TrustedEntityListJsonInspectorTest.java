package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

class TrustedEntityListJsonInspectorTest {
    private static final String LIST = """
      {"LoTE":{"ListAndSchemeInformation":{"LoTEVersionIdentifier":1,"LoTESequenceNumber":7,"SchemeOperatorName":[{"lang":"en","value":"Example authority"}],"ListIssueDateTime":"2025-11-01T00:00:00Z","NextUpdate":"2025-12-01T00:00:00Z"},"TrustedEntitiesList":[{"TrustedEntityInformation":{"TEName":[{"lang":"en","value":"Example provider"}],"TEAddress":{"TEPostalAddress":[{"lang":"en","StreetAddress":"1 Example Street","Locality":"Madrid","Country":"ES"}],"TEElectronicAddress":[{"lang":"en","uriValue":"mailto:contact@example.test"}]},"TEInformationURI":[{"lang":"en","uriValue":"https://example.test"}]},"TrustedEntityServices":[{"ServiceInformation":{"ServiceName":[{"lang":"en","value":"Wallet service"}],"ServiceDigitalIdentity":{"OtherIds":[{"OtherId":"provider-1"}]},"ServiceTypeIdentifier":"https://example.test/type","ServiceStatus":"https://example.test/granted","StatusStartingTime":"2025-11-01T00:00:00Z","ServiceInformationExtensions":[{"qualifier":"example"}]}}]}]}}""";
    @Test void readsSchemeProviderServiceStatusAndQualifiers() {
        var list = TrustedEntityListJsonInspector.parse(validJson().getBytes(StandardCharsets.UTF_8));
        assertEquals("Example authority", list.scheme().operatorName()); assertEquals(7, list.scheme().sequenceNumber());
        var service = list.services().get(0); assertEquals("Example provider", service.providerName()); assertEquals("https://example.test/granted", service.status());
        assertEquals("{\"qualifier\":\"example\"}", service.qualifiers().get(0));
    }
    @Test void describesUnsignedListAsUnsigned() {
        String report = TrustedEntityListJsonInspector.describe(validJson().getBytes(StandardCharsets.UTF_8), Locale.ENGLISH);
        assertTrue(report.contains("Wallet service")); assertTrue(report.contains("signature: no signature present"));
    }
    @Test void verifiesCompactAndJsonJadesWithExplicitSigner() throws Exception {
        Signer signer = signer("Signer");
        String compact = signed(validJson(), signer);
        String report = TrustedEntityListJsonInspector.describe(compact.getBytes(StandardCharsets.UTF_8), Locale.ENGLISH, signer.certificate());
        assertTrue(report.contains("signature: valid (signer:"), report);
        String[] parts = compact.split("\\.");
        String json = "{\"payload\":\"" + parts[1] + "\",\"protected\":\"" + parts[0]
                + "\",\"signature\":\"" + parts[2] + "\"}";
        String jsonReport = TrustedEntityListJsonInspector.describe(json.getBytes(StandardCharsets.UTF_8), Locale.ENGLISH, signer.certificate());
        assertTrue(jsonReport.contains("signature: valid (signer:"), jsonReport);
    }
    @Test void rejectsTamperedPayloadAndDifferentTrustAnchor() throws Exception {
        Signer signer = signer("Signer");
        Signer other = signer("Other");
        String compact = signed(validJson(), signer);
        String[] parts = compact.split("\\.");
        parts[1] = com.nimbusds.jose.util.Base64URL.encode(validJson().replace("Wallet service", "Altered service")
                .getBytes(StandardCharsets.UTF_8)).toString();
        String tampered = String.join(".", parts);
        assertTrue(TrustedEntityListJsonInspector.describe(tampered.getBytes(StandardCharsets.UTF_8), Locale.ENGLISH, signer.certificate())
                .contains("signature: invalid"));
        assertTrue(TrustedEntityListJsonInspector.describe(compact.getBytes(StandardCharsets.UTF_8), Locale.ENGLISH, other.certificate())
                .contains("signature: invalid"));
        assertTrue(TrustedEntityListJsonInspector.describe(compact.getBytes(StandardCharsets.UTF_8), Locale.ENGLISH)
                .contains("signature: not verified (no trust anchor provided)"));
    }
    @Test void rejectsMalformedListWithClearMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TrustedEntityListJsonInspector.parse("{\"LoTE\":{}}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("ListAndSchemeInformation"));
    }
    @Test void rejectsProviderMissingTheRequiredAddress() {
        String malformed = validJson().replace("\"TEAddress\":{\"TEPostalAddress\":[{\"lang\":\"en\",\"StreetAddress\":\"1 Example Street\",\"Locality\":\"Madrid\",\"Country\":\"ES\"}],\"TEElectronicAddress\":[{\"lang\":\"en\",\"uriValue\":\"mailto:contact@example.test\"}]},", "");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TrustedEntityListJsonInspector.parse(malformed.getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("TEAddress"));
    }
    @Test void readsSeveralEntitiesAndServices() {
        JsonObject root = JsonParser.parseString(validJson()).getAsJsonObject();
        var entities = root.getAsJsonObject("LoTE").getAsJsonArray("TrustedEntitiesList");
        JsonObject first = entities.get(0).getAsJsonObject();
        first.getAsJsonArray("TrustedEntityServices").add(first.getAsJsonArray("TrustedEntityServices").get(0).deepCopy());
        JsonObject second = first.deepCopy();
        second.getAsJsonObject("TrustedEntityInformation").getAsJsonArray("TEName").get(0).getAsJsonObject().addProperty("value", "Second provider");
        entities.add(second);
        var list = TrustedEntityListJsonInspector.parse(root.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(4, list.services().size());
        assertEquals("Second provider", list.services().get(2).providerName());
    }
    @Test void acceptsListWithoutTrustedEntities() {
        String listOnly = validJson().replaceFirst(",\"TrustedEntitiesList\":\\[.*", "}}");
        assertTrue(TrustedEntityListJsonInspector.parse(listOnly.getBytes(StandardCharsets.UTF_8)).services().isEmpty());
    }
    @Test void rejectsInvalidNextUpdateWithItsPath() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                TrustedEntityListJsonInspector.parse(validJson().replace("2025-12-01T00:00:00Z", "not-a-date")
                        .getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("NextUpdate"));
    }
    @Test void acceptsSchemaValidOptionalFields() {
        String withOptionals = validJson().replace("\"NextUpdate\":\"2025-12-01T00:00:00Z\"",
                "\"LoTEType\":\"https://example.test/type\",\"SchemeTerritory\":\"ES\",\"NextUpdate\":\"2025-12-01T00:00:00Z\"");
        assertDoesNotThrow(() -> TrustedEntityListJsonInspector.parse(withOptionals.getBytes(StandardCharsets.UTF_8)));
    }
    private static String validJson() {
        return LIST.replace("\"OtherIds\":[{\"OtherId\":\"provider-1\"}]", "\"OtherIds\":[\"provider-1\"]");
    }
    private record Signer(KeyPair keys, X509Certificate certificate) { }
    private static Signer signer(String name) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=" + name + ",C=ES");
        Instant now = Instant.now();
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(Math.abs(System.nanoTime())),
                        Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(1, ChronoUnit.DAYS)),
                        subject, keys.getPublic()).build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
        return new Signer(keys, certificate);
    }
    private static String signed(String payload, Signer signer) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .x509CertChain(java.util.List.of(Base64.encode(signer.certificate().getEncoded())))
                .customParam("x5t#256", com.nimbusds.jose.util.Base64URL.encode(
                        java.security.MessageDigest.getInstance("SHA-256").digest(signer.certificate().getEncoded())).toString())
                .customParam("iat", Instant.now().getEpochSecond()).build();
        JWSObject jws = new JWSObject(header, new Payload(payload));
        jws.sign(new RSASSASigner(signer.keys().getPrivate()));
        return jws.serialize();
    }
}
