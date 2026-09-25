package com.cryptocarver.model.process;

import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.SdJwtOperations;
import com.cryptocarver.crypto.StatusListOperations;
import com.cryptocarver.model.process.handlers.WalletCredentialNodeHandler;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSASigner;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WalletCredentialNodeHandlerTest {

    private final WalletCredentialNodeHandler handler = new WalletCredentialNodeHandler();

    @Test
    void trustedEntityListJsonNodeExposesOptionalCertificates() throws Exception {
        ProcessDefinition.Node inspect = node("TRUSTED_ENTITY_LIST_JSON_INSPECT");
        var ports = handler.inputPorts(inspect);
        assertEquals(3, ports.size());
        assertEquals("trustedEntityListJson", ports.get(0).name());
        assertTrue(ports.get(0).required());
        assertEquals("listSignerCertificate", ports.get(1).name());
        assertFalse(ports.get(1).required());
        assertEquals("certificateToFind", ports.get(2).name());
        assertFalse(ports.get(2).required());

        String list = "{\"LoTE\":{\"ListAndSchemeInformation\":{\"LoTEVersionIdentifier\":1,"
                + "\"LoTESequenceNumber\":1,\"SchemeOperatorName\":[{\"lang\":\"en\",\"value\":\"Lab\"}],"
                + "\"ListIssueDateTime\":\"2025-11-01T00:00:00Z\",\"NextUpdate\":\"2025-12-01T00:00:00Z\"}}}";
        var source = FlowValue.text(list, StandardCharsets.UTF_8);
        String report = handler.execute(inspect, Map.of("trustedEntityListJson", source), null).render();
        assertTrue(report.contains("signature: no signature present"), report);
        assertThrows(IllegalArgumentException.class, () -> handler.execute(inspect,
                Map.of("trustedEntityListJson", source,
                        "listSignerCertificate", FlowValue.binary(new byte[]{1, 2, 3})), null));
        assertThrows(IllegalArgumentException.class, () -> handler.execute(inspect,
                Map.of("trustedEntityListJson", source,
                        "certificateToFind", FlowValue.binary(new byte[]{1, 2, 3})), null));
    }

    private static final String CLAIMS = """
            {"iss":"https://issuer.lab.invalid","sub":"u1","given_name":"John","family_name":"Doe"}""";

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String pem(java.security.Key key, String label) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(key.getEncoded())
                + "\n-----END " + label + "-----\n";
    }

    /** The whole chain a wallet actually walks: issue, narrow, verify. */
    @Test
    void issuePresentAndVerifyRunEndToEndOnTheCanvas() throws Exception {
        KeyPair issuer = p256();
        String privatePem = pem(issuer.getPrivate(), "PRIVATE KEY");
        String publicPem = pem(issuer.getPublic(), "PUBLIC KEY");

        String issued = handler.execute(
                node("SDJWT_ISSUE", "algorithm", "ES256", "key", privatePem,
                        "disclosablePaths", "given_name\nfamily_name", "decoyCount", "2"),
                Map.of("claims", FlowValue.text(CLAIMS, StandardCharsets.UTF_8)), null).render();
        assertTrue(issued.endsWith("~"));
        assertEquals(2, SdJwtOperations.parse(issued).disclosures().size());

        String narrowed = handler.execute(
                node("SDJWT_PRESENT", "revealClaims", "given_name"),
                Map.of("sdJwt", FlowValue.text(issued, StandardCharsets.UTF_8)), null).render();
        assertEquals(1, SdJwtOperations.parse(narrowed).disclosures().size());

        String claims = handler.execute(
                node("SDJWT_VERIFY", "algorithm", "ES256", "issuerPublicKey", publicPem),
                Map.of("presentation", FlowValue.text(narrowed, StandardCharsets.UTF_8)), null).render();
        assertTrue(claims.contains("\"given_name\":\"John\""), claims);
        assertFalse(claims.contains("family_name"), "The withheld claim must not come back: " + claims);
    }

    /** Claims are named, not digested, because a canvas node is configured once
     *  and the digest of a claim is different on every issuance — fresh salt. */
    @Test
    void presentingAClaimTheCredentialDoesNotCarryFailsClearly() throws Exception {
        KeyPair issuer = p256();
        String issued = handler.execute(
                node("SDJWT_ISSUE", "algorithm", "ES256", "key", pem(issuer.getPrivate(), "PRIVATE KEY"),
                        "disclosablePaths", "given_name"),
                Map.of("claims", FlowValue.text(CLAIMS, StandardCharsets.UTF_8)), null).render();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                handler.execute(node("SDJWT_PRESENT", "revealClaims", "salary"),
                        Map.of("sdJwt", FlowValue.text(issued, StandardCharsets.UTF_8)), null));
        assertTrue(failure.getMessage().contains("salary"), failure.getMessage());
    }

    @Test
    void keyBindingTravelsThroughTheCanvasAndVerifies() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();

        String issued = handler.execute(
                node("SDJWT_ISSUE", "algorithm", "ES256", "key", pem(issuer.getPrivate(), "PRIVATE KEY"),
                        "disclosablePaths", "given_name"),
                Map.of("claims", FlowValue.text(CLAIMS, StandardCharsets.UTF_8)), null).render();

        String bound = handler.execute(
                node("SDJWT_PRESENT", "revealClaims", "given_name",
                        "audience", "https://verifier.lab.invalid", "nonce", "n1",
                        "keyBindingAlgorithm", "ES256", "holderKey", pem(holder.getPrivate(), "PRIVATE KEY")),
                Map.of("sdJwt", FlowValue.text(issued, StandardCharsets.UTF_8)), null).render();
        assertFalse(bound.endsWith("~"));

        String claims = handler.execute(
                node("SDJWT_VERIFY", "algorithm", "ES256",
                        "issuerPublicKey", pem(issuer.getPublic(), "PUBLIC KEY"),
                        "holderPublicKey", pem(holder.getPublic(), "PUBLIC KEY"),
                        "audience", "https://verifier.lab.invalid", "nonce", "n1"),
                Map.of("presentation", FlowValue.text(bound, StandardCharsets.UTF_8)), null).render();
        assertTrue(claims.contains("John"));
    }

    /** A KB-JWT with no audience is bound to nobody, and a nonce with no key to
     *  sign it does nothing. Half a configuration must not produce a presentation
     *  that looks bound and isn't. */
    @Test
    void halfAKeyBindingConfigurationIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(
                node("SDJWT_PRESENT", "nonce", "n1", "holderKey", "k")));
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(
                node("SDJWT_PRESENT", "audience", "https://verifier.lab.invalid")));
        assertDoesNotThrow(() -> handler.validateConfiguration(node("SDJWT_PRESENT")));
    }

    @Test
    void vcNodeRequiresACredentialType() {
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(
                node("SDJWT_ISSUE_VC", "algorithm", "ES256", "key", "k")));
        assertDoesNotThrow(() -> handler.validateConfiguration(
                node("SDJWT_ISSUE_VC", "algorithm", "ES256", "key", "k", "vct", "urn:eudi:pid:1")));
    }

    @Test
    void decoyCountMustBeAWholeNumber() {
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(
                node("SDJWT_ISSUE", "algorithm", "ES256", "key", "k", "decoyCount", "lots")));
        assertThrows(IllegalArgumentException.class, () -> handler.validateConfiguration(
                node("SDJWT_ISSUE", "algorithm", "ES256", "key", "k", "decoyCount", "-1")));
    }

    @Test
    void statusListNodesResolveAndSummarise() throws Exception {
        KeyPair issuer = p256();
        String uri = "https://issuer.lab.invalid/statuslists/1";
        int[] statuses = new int[16];
        statuses[3] = StatusListOperations.StatusType.INVALID.value();
        String token = StatusListOperations.issueStatusListToken(statuses, 2, uri,
                Instant.now(), null, -1, JWSAlgorithm.ES256,
                new ECDSASigner((ECPrivateKey) issuer.getPrivate()));

        String resolved = handler.execute(
                node("STATUS_LIST_RESOLVE", "algorithm", "ES256",
                        "issuerPublicKey", pem(issuer.getPublic(), "PUBLIC KEY"),
                        "statusClaim", StatusListOperations.statusClaim(uri, 3)),
                Map.of("statusListToken", FlowValue.text(token, StandardCharsets.UTF_8)), null).render();
        assertTrue(resolved.contains("revoked"), resolved);

        String described = handler.execute(node("STATUS_LIST_DESCRIBE"),
                Map.of("statusListToken", FlowValue.text(token, StandardCharsets.UTF_8)), null).render();
        assertTrue(described.contains("1 x invalid (revoked)"), described);
    }

    @Test
    void cborNodesRenderAndConvert() throws Exception {
        byte[] cbor = CborInspector.fromJson("{\"a\":1,\"b\":[true,null]}");

        assertTrue(handler.execute(node("CBOR_INSPECT", "view", "tree"),
                Map.of("cbor", FlowValue.binary(cbor)), null).render().contains("map (2)"));
        assertTrue(handler.execute(node("CBOR_INSPECT", "view", "diagnostic"),
                Map.of("cbor", FlowValue.binary(cbor)), null).render().startsWith("{"));
        assertTrue(handler.execute(node("CBOR_INSPECT", "view", "summary"),
                Map.of("cbor", FlowValue.binary(cbor)), null).render().contains("bytes"));

        assertEquals("{\"a\":1,\"b\":[true,null]}", handler.execute(node("CBOR_TO_JSON"),
                Map.of("cbor", FlowValue.binary(cbor)), null).render());
        assertArrayEquals(cbor, handler.execute(node("CBOR_FROM_JSON"),
                Map.of("json", FlowValue.text("{\"a\":1,\"b\":[true,null]}", StandardCharsets.UTF_8)), null).bytes());
    }

    /** Private key material must be declared {@link ParameterKind#PASSWORD}:
     *  that is what keeps it in {@code transientSecrets} and out of a saved
     *  {@code .cfprocess.json}. */
    @Test
    void privateKeyParametersAreDeclaredAsSecrets() {
        for (NodeDescriptor descriptor : handler.descriptors()) {
            for (NodeParameter parameter : descriptor.parameters()) {
                if (parameter.key().equals("key") || parameter.key().equals("holderKey")) {
                    assertEquals(ParameterKind.PASSWORD, parameter.kind(),
                            parameter.key() + " in " + descriptor.type() + " carries private key material");
                }
                if (parameter.key().endsWith("PublicKey")) {
                    assertEquals(ParameterKind.MULTILINE, parameter.kind(),
                            parameter.key() + " in " + descriptor.type() + " is a public key");
                }
            }
        }
    }

    /**
     * No parameter name may mean a private key in one node and a public key in
     * another. An earlier draft called both "holderKey", which reads as an
     * invitation to paste a private key into the verifier — and a verifier never
     * needs one. Kept as a test rather than a comment because the same slip is
     * easy to reintroduce when a node is added.
     */
    @Test
    void aParameterNameMeansTheSameThingInEveryNode() {
        java.util.Map<String, ParameterKind> kinds = new java.util.HashMap<>();
        for (NodeDescriptor descriptor : handler.descriptors()) {
            for (NodeParameter parameter : descriptor.parameters()) {
                ParameterKind previous = kinds.putIfAbsent(parameter.key(), parameter.kind());
                if (previous != null) {
                    assertEquals(previous, parameter.kind(),
                            "'" + parameter.key() + "' is declared as " + previous
                                    + " elsewhere but as " + parameter.kind() + " in " + descriptor.type());
                }
            }
        }
    }

    private static ProcessDefinition.Node node(String type, String... values) {
        ProcessDefinition.Node node = new ProcessDefinition.Node(type, type, type, 0, 0);
        for (int i = 0; i < values.length; i += 2) {
            node.configuration.put(values[i], values[i + 1]);
        }
        return node;
    }
}
