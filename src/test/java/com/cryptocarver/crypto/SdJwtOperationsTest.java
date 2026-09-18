package com.cryptocarver.crypto;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SdJwtOperationsTest {

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

    private static final String CLAIMS = """
            {
              "iss": "https://issuer.lab.invalid",
              "sub": "user-42",
              "given_name": "John",
              "family_name": "Doe",
              "birthdate": "1940-01-01",
              "address": { "locality": "Vigo", "country": "ES" },
              "nationalities": ["ES", "PT"]
            }
            """;

    // ------------------------------------------------------- RFC 9901 vectors

    /**
     * The two worked examples of RFC 9901. These are the normative part of the
     * whole scheme — the digest is taken over the US-ASCII bytes of the
     * base64url string, not of the JSON it decodes to — so they are checked
     * directly rather than only through a round-trip, which would pass even if
     * both ends were wrong in the same way.
     *
     * <p>Both the Disclosure strings and the digests below were recomputed
     * independently with openssl before being written here.</p>
     */
    @Test
    void reproducesTheRfc9901DisclosureVectors() {
        String familyName = SdJwtOperations.encodeDisclosure(
                "eluV5Og3gSNII8EYnsxA_A", "family_name", new JsonPrimitive("Doe"));
        assertEquals("WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd", familyName,
                "Issuing with the RFC's own salt must reproduce the RFC's own Disclosure string");
        assertEquals("TGf4oLbgwd5JQaHyKVQZU9UdGE0w5rtDsrZzfUaomLo",
                SdJwtOperations.digestOf(familyName, SdJwtOperations.HashAlgorithm.SHA_256));

        String givenName = SdJwtOperations.encodeDisclosure(
                "2GLC42sKQveCfGfryNRN9w", "given_name", new JsonPrimitive("John"));
        assertEquals("WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiSm9obiJd", givenName);
        assertEquals("jsu9yVulwQQlhFlM_3JlzMaSFzglhQG0DpfayQwLUK4",
                SdJwtOperations.digestOf(givenName, SdJwtOperations.HashAlgorithm.SHA_256));
    }

    /** The digest covers the encoded string, so a single changed character in it
     *  must move the digest — the property that makes a Disclosure tamper-evident. */
    @Test
    void digestFollowsTheEncodedStringNotTheValue() {
        String encoded = SdJwtOperations.encodeDisclosure("salt", "a", new JsonPrimitive("b"));
        String tampered = encoded.substring(0, encoded.length() - 1)
                + (encoded.endsWith("A") ? "B" : "A");
        assertNotEquals(SdJwtOperations.digestOf(encoded, SdJwtOperations.HashAlgorithm.SHA_256),
                SdJwtOperations.digestOf(tampered, SdJwtOperations.HashAlgorithm.SHA_256));
    }

    // ------------------------------------------------------------- round trip

    @Test
    void disclosingEverythingRebuildsTheOriginalPayload() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS,
                List.of("given_name", "family_name", "birthdate", "address.locality", "nationalities[]"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        String presentation = SdJwtOperations.present(issued, digests(issued), null);
        SdJwtOperations.VerifiedSdJwt verified =
                SdJwtOperations.verify(presentation, verifier(issuer), null, null, null);

        JsonObject expected = JsonParser.parseString(CLAIMS).getAsJsonObject();
        JsonObject actual = verified.claims();
        for (String claim : List.of("iss", "sub", "given_name", "family_name", "birthdate")) {
            assertEquals(expected.get(claim), actual.get(claim), claim + " did not survive the round trip");
        }
        assertEquals("Vigo", actual.getAsJsonObject("address").get("locality").getAsString());
        assertEquals("ES", actual.getAsJsonObject("address").get("country").getAsString(),
                "A claim that was never made disclosable must stay in the clear where it was");
        assertEquals(2, actual.getAsJsonArray("nationalities").size());
        assertFalse(actual.has("_sd"), "The _sd machinery must not survive into the verified payload");
        assertFalse(actual.has("_sd_alg"));
    }

    @Test
    void withheldClaimsAreAbsentAndTheRestStillVerifies() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS,
                List.of("given_name", "family_name", "birthdate"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        String onlyGivenName = issued.disclosures().stream()
                .filter(d -> "given_name".equals(d.claimName()))
                .findFirst().orElseThrow().digest();

        SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(
                SdJwtOperations.present(issued, List.of(onlyGivenName), null),
                verifier(issuer), null, null, null);

        assertEquals("John", verified.claims().get("given_name").getAsString());
        assertFalse(verified.claims().has("family_name"), "A withheld claim must not appear");
        assertFalse(verified.claims().has("birthdate"));
        assertEquals("user-42", verified.claims().get("sub").getAsString(),
                "Claims that were never selectively disclosable are always present");
    }

    /** RFC 9901 §4: without a KB-JWT "the last separating tilde character MUST
     *  NOT be omitted". The trailing tilde is the only thing distinguishing the
     *  two serializations, so it gets its own test. */
    @Test
    void trailingTildeMarksTheAbsenceOfKeyBinding() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("given_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        String bare = SdJwtOperations.present(issued, digests(issued), null);
        assertTrue(bare.endsWith("~"), "A presentation without key binding must end in a tilde");

        String bound = SdJwtOperations.present(issued, digests(issued),
                new SdJwtOperations.KeyBinding("https://verifier.lab.invalid", "n-0S6_WzA2Mj",
                        JWSAlgorithm.ES256, signer(holder)));
        assertFalse(bound.endsWith("~"), "A presentation with key binding ends with the KB-JWT");
    }

    @Test
    void keyBindingVerifiesAndCarriesAudienceAndNonce() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("given_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        String presentation = SdJwtOperations.present(issued, digests(issued),
                new SdJwtOperations.KeyBinding("https://verifier.lab.invalid", "n-0S6_WzA2Mj",
                        JWSAlgorithm.ES256, signer(holder)));

        SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(presentation,
                verifier(issuer), verifier(holder), "https://verifier.lab.invalid", "n-0S6_WzA2Mj");

        assertTrue(verified.keyBindingPresent());
        assertTrue(verified.notes().isEmpty(), "Nothing should have been skipped: " + verified.notes());
        assertTrue(verified.keyBindingClaims().has("sd_hash"));
    }

    @Test
    void aKeyBindingFromAnotherPresentationIsRejected() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS,
                List.of("given_name", "family_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        // A KB-JWT built over both Disclosures...
        String full = SdJwtOperations.present(issued, digests(issued),
                new SdJwtOperations.KeyBinding("https://verifier.lab.invalid", "nonce-1",
                        JWSAlgorithm.ES256, signer(holder)));
        String keyBindingJwt = full.substring(full.lastIndexOf('~') + 1);

        // ...moved onto a narrower presentation of the same credential. The
        // signature on the KB-JWT is still perfectly valid; only sd_hash catches this.
        String narrower = SdJwtOperations.present(issued,
                List.of(issued.disclosures().get(0).digest()), null);
        String forged = narrower + keyBindingJwt;

        SecurityException failure = assertThrows(SecurityException.class, () ->
                SdJwtOperations.verify(forged, verifier(issuer), verifier(holder),
                        "https://verifier.lab.invalid", "nonce-1"));
        assertTrue(failure.getMessage().contains("sd_hash"), failure.getMessage());
    }

    @Test
    void wrongNonceIsRejected() throws Exception {
        KeyPair issuer = p256();
        KeyPair holder = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("given_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);
        String presentation = SdJwtOperations.present(issued, digests(issued),
                new SdJwtOperations.KeyBinding("https://verifier.lab.invalid", "nonce-issued",
                        JWSAlgorithm.ES256, signer(holder)));

        assertThrows(SecurityException.class, () -> SdJwtOperations.verify(presentation,
                verifier(issuer), verifier(holder), "https://verifier.lab.invalid", "nonce-replayed"));
    }

    /** RFC 9901 §7.3: a Disclosure the payload does not reference was never
     *  signed by anyone. Accepting it would let a Holder staple on any claim. */
    @Test
    void aDisclosureNoDigestPointsAtIsRejected() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("given_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        String smuggled = SdJwtOperations.encodeDisclosure(
                "aaaaaaaaaaaaaaaaaaaaaa", "is_over_18", new JsonPrimitive(true));
        String presentation = SdJwtOperations.present(issued, digests(issued), null);
        String forged = presentation + smuggled + "~";

        SecurityException failure = assertThrows(SecurityException.class, () ->
                SdJwtOperations.verify(forged, verifier(issuer), null, null, null));
        assertTrue(failure.getMessage().contains("not referenced"), failure.getMessage());
    }

    @Test
    void theSameDisclosurePresentedTwiceIsRejected() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("given_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);
        String presentation = SdJwtOperations.present(issued, digests(issued), null);
        String doubled = presentation + issued.disclosures().get(0).encoded() + "~";

        assertThrows(SecurityException.class, () ->
                SdJwtOperations.verify(doubled, verifier(issuer), null, null, null));
    }

    @Test
    void aTamperedIssuerSignatureIsRejected() throws Exception {
        KeyPair issuer = p256();
        KeyPair impostor = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("given_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);
        String presentation = SdJwtOperations.present(issued, digests(issued), null);

        assertThrows(SecurityException.class, () ->
                SdJwtOperations.verify(presentation, verifier(impostor), null, null, null));
    }

    // ------------------------------------------------------------- structures

    @Test
    void arrayElementsBecomeIndividuallyDisclosable() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("nationalities[]"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        assertEquals(2, issued.disclosures().size());
        assertTrue(issued.disclosures().stream().allMatch(SdJwtOperations.Disclosure::isArrayElement));

        String onlyFirst = issued.disclosures().get(0).digest();
        SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(
                SdJwtOperations.present(issued, List.of(onlyFirst), null),
                verifier(issuer), null, null, null);

        assertEquals(1, verified.claims().getAsJsonArray("nationalities").size(),
                "A withheld array element disappears instead of leaving a placeholder");
    }

    @Test
    void decoyDigestsDoNotChangeWhatVerifies() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS,
                List.of("given_name", "family_name"),
                8, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(issued.serialized());
        assertEquals(10, parsed.payload().getAsJsonArray("_sd").size(),
                "Two real digests plus eight decoys");

        SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(
                SdJwtOperations.present(issued, digests(issued), null), verifier(issuer), null, null, null);
        assertEquals("John", verified.claims().get("given_name").getAsString());
        assertEquals("Doe", verified.claims().get("family_name").getAsString());
    }

    @Test
    void sha512IsHonouredEndToEnd() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS, List.of("given_name"),
                0, SdJwtOperations.HashAlgorithm.SHA_512, JWSAlgorithm.ES256, signer(issuer), null);

        assertEquals("sha-512",
                SdJwtOperations.parse(issued.serialized()).payload().get("_sd_alg").getAsString());
        SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(
                SdJwtOperations.present(issued, digests(issued), null), verifier(issuer), null, null, null);
        assertEquals("John", verified.claims().get("given_name").getAsString());
    }

    // ------------------------------------------------------------- VC profile

    @Test
    void theVcProfileSetsTypVctAndConfirmationKey() throws Exception {
        KeyPair issuer = p256();
        String jwk = """
                {"kty":"EC","crv":"P-256","x":"0000","y":"1111"}""";

        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issueVerifiableCredential(
                CLAIMS, "urn:eudi:pid:1", "https://issuer.lab.invalid", jwk,
                StatusListOperations.statusClaim("https://issuer.lab.invalid/status/1", 7),
                List.of("birthdate"), 0, SdJwtOperations.HashAlgorithm.SHA_256,
                JWSAlgorithm.ES256, signer(issuer));

        SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(issued.serialized());
        assertEquals(SdJwtOperations.SD_JWT_VC_TYPE, parsed.header().get("typ").getAsString());
        assertEquals("urn:eudi:pid:1", parsed.payload().get("vct").getAsString());
        assertTrue(parsed.payload().getAsJsonObject("cnf").has("jwk"));
        assertEquals(7, parsed.payload().getAsJsonObject("status")
                .getAsJsonObject("status_list").get("idx").getAsInt());
    }

    @Test
    void aCredentialWithoutAVctIsRefused() throws Exception {
        KeyPair issuer = p256();
        assertThrows(IllegalArgumentException.class, () -> SdJwtOperations.issueVerifiableCredential(
                CLAIMS, "  ", "https://issuer.lab.invalid", null, null,
                List.of(), 0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer)));
    }

    // -------------------------------------------------------------- inspector

    @Test
    void parseReadsAPresentationWithoutAnyKeys() throws Exception {
        KeyPair issuer = p256();
        SdJwtOperations.IssuedSdJwt issued = SdJwtOperations.issue(CLAIMS,
                List.of("given_name", "address.locality"),
                0, SdJwtOperations.HashAlgorithm.SHA_256, JWSAlgorithm.ES256, signer(issuer), null);

        SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(issued.serialized());
        assertEquals(2, parsed.disclosures().size());
        assertFalse(parsed.hasKeyBinding());
        assertTrue(SdJwtOperations.describe(issued.serialized(), java.util.Locale.forLanguageTag("es"))
                .contains("Disclosures presentadas"));

        // A nested disclosure puts its digest in the _sd of the object it belongs
        // to, not at the top level.
        assertTrue(parsed.payload().getAsJsonObject("address").has("_sd"));
    }

    @Test
    void somethingThatIsNotAnSdJwtIsRejectedClearly() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SdJwtOperations.parse("eyJhbGciOiJFUzI1NiJ9.e30.signature"));
        assertTrue(failure.getMessage().contains("tilde"), failure.getMessage());
    }

    private static List<String> digests(SdJwtOperations.IssuedSdJwt issued) {
        return issued.disclosures().stream()
                .map(SdJwtOperations.Disclosure::digest)
                .collect(Collectors.toList());
    }
}
