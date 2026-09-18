package com.cryptocarver.crypto;

import com.google.gson.JsonObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class Ts12ScaOperationsTest {

    private static final String PAYMENT = """
            {"amount": "123.45", "currency": "EUR", "payee": "Comercio de Pruebas",
             "payee_account": "ES9121000418450200051332", "execution_date": "2026-09-19"}""";

    private static final String CLAIMS = """
            {"iss": "https://bank.lab.invalid", "category": "urn:eu:europa:ec:eudi:sua:sca",
             "account_holder": "John Doe"}""";

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static JWSSigner signer(KeyPair pair) throws Exception {
        return new ECDSASigner((ECPrivateKey) pair.getPrivate());
    }

    private static JWSVerifier verifier(KeyPair pair) throws Exception {
        return new ECDSAVerifier((ECPublicKey) pair.getPublic());
    }

    // ------------------------------------------------------ transaction data

    /** OpenID4VP hashes the base64url string as transmitted, the same rule
     *  SD-JWT uses for a Disclosure. Re-encoding the JSON inside would give a
     *  different string and a hash that matches nothing. */
    @Test
    void theHashCoversTheEncodedEntryNotTheJsonInsideIt() throws Exception {
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");

        String overTheString = Ts12ScaOperations.hashTransactionData(entry, "sha-256");
        String reEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new com.google.gson.Gson().toJson(
                        Ts12ScaOperations.decodeTransactionData(entry)).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(entry, reEncoded,
                "This implementation's own round trip is stable, so the next assertion is about the rule, not about formatting");
        assertEquals(overTheString, Ts12ScaOperations.hashTransactionData(reEncoded, "sha-256"));
        assertNotEquals(overTheString, Ts12ScaOperations.hashTransactionData(entry + "x", "sha-256"));
    }

    @Test
    void theEntryCarriesTheTypeAndThePayload() {
        JsonObject entry = Ts12ScaOperations.decodeTransactionData(
                Ts12ScaOperations.encodeTransactionData(
                        Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256"));

        assertEquals("urn:eudi:sca:payment:1", entry.get("type").getAsString());
        assertEquals("123.45", entry.getAsJsonObject("payload").get("amount").getAsString());
        assertEquals("sha-256", entry.getAsJsonArray("transaction_data_hashes_alg").get(0).getAsString());
    }

    @Test
    void allFourTransactionTypesResolve() {
        for (Ts12ScaOperations.TransactionType type : Ts12ScaOperations.TransactionType.values()) {
            assertEquals(type, Ts12ScaOperations.TransactionType.fromUrn(type.urn()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> Ts12ScaOperations.TransactionType.fromUrn("urn:eudi:sca:invented:9"));
    }

    // ---------------------------------------------------------- amr factors

    @Test
    void amrTokensAreClassifiedAsTs12DefinesThem() {
        assertEquals(Ts12ScaOperations.AmrCategory.KNOWLEDGE,
                Ts12ScaOperations.categoryOf("pin_6_or_more_digits"));
        assertEquals(Ts12ScaOperations.AmrCategory.POSSESSION,
                Ts12ScaOperations.categoryOf("key_in_local_native_wscd"));
        assertEquals(Ts12ScaOperations.AmrCategory.INHERENCE,
                Ts12ScaOperations.categoryOf("face_device"));
        assertNull(Ts12ScaOperations.categoryOf("magic_wand"));
        assertNull(Ts12ScaOperations.categoryOf("other"),
                "'other' is in all three lists, so it cannot be attributed to one");
    }

    // ------------------------------------------------------- dynamic linking

    @Test
    void acceptsAPresentationBoundToTheTransactionAndTwoFactors() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");

        String presentation = presentation(issuer, holder, List.of(entry),
                "pin_6_or_more_digits", "key_in_local_native_wscd", "direct_post.jwt", randomJti());

        Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(presentation, List.of(entry),
                verifier(issuer), verifier(holder), "https://psp.lab.invalid", "nonce-1", "direct_post.jwt");

        assertTrue(report.acceptable(), report.findings().toString());
        assertEquals(2, report.factors().size());
        assertTrue(Ts12ScaOperations.describe(report, java.util.Locale.forLanguageTag("es"))
                .contains("El enlace dinámico se sostiene"));
    }

    /**
     * The point of the whole mechanism. A presentation captured for one payment
     * must not verify against another: the amount and the payee are inside the
     * hash the holder signed.
     */
    @Test
    void refusesAPresentationReplayedAgainstADifferentPayment() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        String signed = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");
        String different = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"),
                PAYMENT.replace("123.45", "9999.00"), "sha-256");

        String presentation = presentation(issuer, holder, List.of(signed),
                "pin_6_or_more_digits", "face_device", "direct_post.jwt", randomJti());

        Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(presentation, List.of(different),
                verifier(issuer), verifier(holder), "https://psp.lab.invalid", "nonce-1", "direct_post.jwt");

        assertFalse(report.acceptable());
        assertTrue(report.findings().stream().anyMatch(f -> "ERROR".equals(f.severity())
                && f.message().contains("did not sign this transaction")), report.findings().toString());
    }

    /** Two knowledge tokens are one factor twice, not two factors. */
    @Test
    void refusesTwoTokensFromTheSameCategory() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");

        String presentation = presentation(issuer, holder, List.of(entry),
                "pin_6_or_more_digits", "pattern", "direct_post.jwt", randomJti());

        Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(presentation, List.of(entry),
                verifier(issuer), verifier(holder), "https://psp.lab.invalid", "nonce-1", "direct_post.jwt");

        assertFalse(report.acceptable());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("two different ones")));
        assertEquals(1, report.factors().size());
    }

    @Test
    void refusesACredentialThatIsNotAnScaAttestation() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");

        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(
                """
                {"iss": "https://bank.lab.invalid", "account_holder": "John Doe"}""",
                List.of(), 0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256,
                signer(issuer), null);
        String presentation = SdJwtOperations.present(issued, List.of(),
                keyBinding(holder, List.of(entry), "pin_6_or_more_digits", "face_device",
                        "direct_post.jwt", randomJti()));

        Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(presentation, List.of(entry),
                verifier(issuer), verifier(holder), "https://psp.lab.invalid", "nonce-1", "direct_post.jwt");

        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("not an SCA attestation")));
    }

    @Test
    void refusesAResponseModeThatDoesNotMatchTheRequest() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");
        String presentation = presentation(issuer, holder, List.of(entry),
                "pin_6_or_more_digits", "face_device", "fragment", randomJti());

        Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(presentation, List.of(entry),
                verifier(issuer), verifier(holder), "https://psp.lab.invalid", "nonce-1", "direct_post.jwt");

        assertFalse(report.acceptable());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("response_mode")));
    }

    /** jti is the authentication code, so a counter is worth flagging. */
    @Test
    void questionsAShortJti() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        String entry = Ts12ScaOperations.encodeTransactionData(
                Ts12ScaOperations.TransactionType.PAYMENT, List.of("sca"), PAYMENT, "sha-256");
        String presentation = presentation(issuer, holder, List.of(entry),
                "pin_6_or_more_digits", "face_device", "direct_post.jwt", "7");

        Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(presentation, List.of(entry),
                verifier(issuer), verifier(holder), "https://psp.lab.invalid", "nonce-1", "direct_post.jwt");

        assertTrue(report.findings().stream().anyMatch(f -> "WARN".equals(f.severity())
                && f.message().contains("jti")), report.findings().toString());
    }

    @Test
    void refusesAPresentationWithNoKeyBindingAtAll() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of(), 0,
                SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);
        String bare = SdJwtOperations.present(issued, List.of(), null);

        Ts12ScaOperations.ScaReport report = Ts12ScaOperations.verify(bare, List.of(),
                verifier(issuer), null, null, null, null);

        assertFalse(report.acceptable());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("bound to nothing")));
    }

    // -------------------------------------------------------------- fixtures

    private static String randomJti() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String presentation(KeyPair issuer, KeyPair holder, List<String> entries,
                                       String firstAmr, String secondAmr,
                                       String responseMode, String jti) throws Exception {
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("account_holder"), 0,
                SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);
        return SdJwtOperations.present(issued,
                issued.disclosures().stream().map(SdJwtOperations.Disclosure::digest).collect(Collectors.toList()),
                keyBinding(holder, entries, firstAmr, secondAmr, responseMode, jti));
    }

    private static SdJwtOperations.KeyBinding keyBinding(KeyPair holder, List<String> entries,
                                                         String firstAmr, String secondAmr,
                                                         String responseMode, String jti) throws Exception {
        JsonObject extra = new JsonObject();
        extra.addProperty("jti", jti);
        extra.addProperty("response_mode", responseMode);
        com.google.gson.JsonArray amr = new com.google.gson.JsonArray();
        amr.add(firstAmr);
        amr.add(secondAmr);
        extra.add("amr", amr);
        com.google.gson.JsonArray hashes = new com.google.gson.JsonArray();
        for (String entry : entries) {
            hashes.add(Ts12ScaOperations.hashTransactionData(entry, "sha-256"));
        }
        extra.add("transaction_data_hashes", hashes);
        extra.addProperty("transaction_data_hashes_alg", "sha-256");

        return new SdJwtOperations.KeyBinding("https://psp.lab.invalid", "nonce-1",
                JWSAlgorithm.ES256, signer(holder), java.time.Instant.now(), extra);
    }
}
