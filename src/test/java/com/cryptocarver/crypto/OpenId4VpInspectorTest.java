package com.cryptocarver.crypto;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenId4VpInspectorTest {

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static String jar(KeyPair signer, String claims) throws Exception {
        JWSObject jws = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("oauth-authz-req+jwt")).build(),
                new Payload(claims));
        jws.sign(new ECDSASigner((ECPrivateKey) signer.getPrivate()));
        return jws.serialize();
    }

    @Test
    void readsASignedRequestAndItsClientIdentifierScheme() throws Exception {
        KeyPair verifierKey = p256();
        String request = jar(verifierKey, """
                {"client_id": "x509_san_dns:verifier.lab.invalid",
                 "response_type": "vp_token", "response_mode": "direct_post.jwt",
                 "nonce": "n-0S6_WzA2Mj", "state": "s1",
                 "dcql_query": {"credentials": [{"id": "pid", "format": "dc+sd-jwt"}]}}""");

        OpenId4VpInspector.RequestReport report = OpenId4VpInspector.inspectRequest(
                request, new ECDSAVerifier((ECPublicKey) verifierKey.getPublic()));

        assertTrue(report.signatureVerified());
        assertEquals(OpenId4VpInspector.ClientIdScheme.X509_SAN_DNS, report.scheme());
        assertEquals("x509_san_dns:verifier.lab.invalid", report.clientId());
        assertTrue(report.findings().stream().allMatch(f -> "INFO".equals(f.severity())),
                report.findings().toString());
    }

    @Test
    void refusesARequestSignedByAnotherKey() throws Exception {
        String request = jar(p256(), """
                {"client_id": "x509_san_dns:verifier.lab.invalid", "nonce": "n1",
                 "response_mode": "direct_post.jwt"}""");

        OpenId4VpInspector.RequestReport report = OpenId4VpInspector.inspectRequest(
                request, new ECDSAVerifier((ECPublicKey) p256().getPublic()));

        assertFalse(report.signatureVerified());
        assertTrue(report.findings().stream()
                .anyMatch(f -> "ERROR".equals(f.severity()) && f.message().contains("does not verify")));
    }

    /** A missing nonce is not a detail: it is what ties the holder's signature
     *  to this request rather than to any earlier one. */
    @Test
    void reportsAMissingNonce() throws Exception {
        OpenId4VpInspector.RequestReport report = OpenId4VpInspector.inspectRequest(
                """
                {"client_id": "x509_san_dns:verifier.lab.invalid", "response_mode": "direct_post.jwt"}""",
                null);

        assertTrue(report.findings().stream()
                .anyMatch(f -> "ERROR".equals(f.severity()) && f.message().contains("nonce")));
    }

    @Test
    void saysWhenTheRedirectUriSchemeAuthenticatesNobody() throws Exception {
        OpenId4VpInspector.RequestReport report = OpenId4VpInspector.inspectRequest(
                """
                {"client_id": "redirect_uri:https://verifier.lab.invalid/cb", "nonce": "n1",
                 "response_mode": "direct_post"}""", null);

        assertEquals(OpenId4VpInspector.ClientIdScheme.REDIRECT_URI, report.scheme());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("does not authenticate")));
    }

    /** Older deployments still carry the scheme in its own claim. */
    @Test
    void readsThePreOneDotZeroClientIdSchemeClaim() throws Exception {
        OpenId4VpInspector.RequestReport report = OpenId4VpInspector.inspectRequest(
                """
                {"client_id": "verifier.lab.invalid", "client_id_scheme": "x509_san_dns",
                 "nonce": "n1", "response_mode": "direct_post.jwt"}""", null);

        assertEquals(OpenId4VpInspector.ClientIdScheme.X509_SAN_DNS, report.scheme());
    }

    /** Transaction data is a payment: amount, payee, account. Asking for it back
     *  through an unencrypted response mode puts that in the front channel. */
    @Test
    void warnsWhenPaymentDetailsWouldComeBackUnencrypted() throws Exception {
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"),
                """
                {"amount": "123.45", "currency": "EUR", "payee": "Comercio"}""", "sha-256");

        OpenId4VpInspector.RequestReport report = OpenId4VpInspector.inspectRequest(
                """
                {"client_id": "x509_san_dns:psp.lab.invalid", "nonce": "n1",
                 "response_mode": "direct_post", "transaction_data": ["%s"]}""".formatted(entry), null);

        assertEquals(1, report.transactionData().size());
        assertEquals("urn:eudi:sca:payment:1", report.transactionData().get(0).get("type").getAsString());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("in the clear")));
    }

    @Test
    void decodesTransactionDataIntoTheReport() throws Exception {
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.EMANDATE, List.of("sca"),
                """
                {"creditor": "Compania de Pruebas"}""", "sha-256");

        String text = OpenId4VpInspector.describe(
                """
                {"client_id": "x509_san_dns:psp.lab.invalid", "nonce": "n1",
                 "response_mode": "direct_post.jwt", "transaction_data": ["%s"]}""".formatted(entry),
                null, java.util.Locale.forLanguageTag("es"));

        assertTrue(text.contains("Datos de transacción"), text);
        assertTrue(text.contains("Compania de Pruebas"), text);
        assertTrue(text.contains("esquema"), text);
    }

    @Test
    void anUnsignedPlainRequestIsReportedAsSuch() throws Exception {
        OpenId4VpInspector.RequestReport report = OpenId4VpInspector.inspectRequest(
                """
                {"client_id": "x509_san_dns:v.lab.invalid", "nonce": "n1",
                 "response_mode": "direct_post.jwt"}""", null);

        assertFalse(report.signatureVerified());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("not signed")));
    }
}
