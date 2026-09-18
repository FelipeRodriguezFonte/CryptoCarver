package com.cryptocarver.crypto;

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
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class StatusListOperationsTest {

    private static final String LIST_URI = "https://issuer.lab.invalid/statuslists/1";

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

    // ------------------------------------------------------------ bit packing

    /**
     * The specification's own 1-bit worked example. Bit order is the single
     * easiest thing to get backwards here, and getting it backwards produces a
     * list that decompresses perfectly and reports the wrong answers — so it is
     * pinned against the published byte array rather than only round-tripped.
     *
     * <p>The uncompressed bytes are asserted instead of the compressed
     * {@code lst}: the packing is normative, whereas the exact DEFLATE output
     * depends on the compressor's level and is not. Interoperating with
     * <em>their</em> compressor is covered by
     * {@link #decodesTheSpecificationsOwnCompressedList} below, which is the
     * direction that actually matters.</p>
     */
    @Test
    void packsOneBitStatusesLeastSignificantBitFirst() throws Exception {
        int[] statuses = {1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1};
        String encoded = StatusListOperations.encodeList(statuses, 1);
        StatusListOperations.StatusList decoded = StatusListOperations.decodeList(encoded, 1, 16);

        assertArrayEquals(new byte[]{(byte) 0xB9, (byte) 0xA3}, decoded.uncompressed(),
                "The specification's example packs to 0xB9 0xA3");
        assertArrayEquals(statuses, decoded.statuses());
    }

    /** The specification's 2-bit example, same reasoning. */
    @Test
    void packsTwoBitStatusesFourToAByte() throws Exception {
        int[] statuses = {1, 2, 0, 3, 0, 1, 0, 1, 1, 2, 3, 3};
        StatusListOperations.StatusList decoded =
                StatusListOperations.decodeList(StatusListOperations.encodeList(statuses, 2), 2, 12);

        assertArrayEquals(new byte[]{(byte) 0xC9, 0x44, (byte) 0xF9}, decoded.uncompressed());
        assertArrayEquals(statuses, decoded.statuses());
    }

    /** Reads the {@code lst} the specification publishes, produced by somebody
     *  else's compressor. This is what proves the decoder interoperates rather
     *  than merely agreeing with our own encoder. */
    @Test
    void decodesTheSpecificationsOwnCompressedList() throws Exception {
        StatusListOperations.StatusList decoded =
                StatusListOperations.decodeList("eNrbuRgAAhcBXQ", 1, 16);
        assertArrayEquals(new byte[]{(byte) 0xB9, (byte) 0xA3}, decoded.uncompressed());
        assertEquals(1, decoded.statusAt(0));
        assertEquals(0, decoded.statusAt(1));
        assertEquals(1, decoded.statusAt(15));

        StatusListOperations.StatusList twoBit =
                StatusListOperations.decodeList("eNo76fITAAPfAgc", 2, 12);
        assertArrayEquals(new byte[]{(byte) 0xC9, 0x44, (byte) 0xF9}, twoBit.uncompressed());
        assertEquals(3, twoBit.statusAt(3));
    }

    @Test
    void rejectsBitWidthsTheSpecificationDoesNotDefine() {
        assertThrows(IllegalArgumentException.class, () -> StatusListOperations.encodeList(new int[]{0}, 3));
        assertThrows(IllegalArgumentException.class, () -> StatusListOperations.encodeList(new int[]{0}, 16));
    }

    @Test
    void rejectsAStatusTooWideForItsBits() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusListOperations.encodeList(new int[]{0, 2}, 1));
    }

    // ------------------------------------------------------------- resolution

    @Test
    void resolvesACredentialIndexToItsStatus() throws Exception {
        KeyPair issuer = p256();
        int[] statuses = new int[64];
        statuses[7] = StatusListOperations.StatusType.INVALID.value();
        statuses[9] = StatusListOperations.StatusType.SUSPENDED.value();

        String token = StatusListOperations.issueStatusListToken(statuses, 2, LIST_URI,
                Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), 3600,
                JWSAlgorithm.ES256, signer(issuer));

        assertEquals(StatusListOperations.StatusType.VALID.value(),
                StatusListOperations.resolve(StatusListOperations.statusClaim(LIST_URI, 0), token,
                        verifier(issuer)).status());

        StatusListOperations.StatusLookup revoked = StatusListOperations.resolve(
                StatusListOperations.statusClaim(LIST_URI, 7), token, verifier(issuer));
        assertEquals(StatusListOperations.StatusType.INVALID.value(), revoked.status());
        assertTrue(revoked.description().contains("revoked"));

        assertEquals(StatusListOperations.StatusType.SUSPENDED.value(),
                StatusListOperations.resolve(StatusListOperations.statusClaim(LIST_URI, 9), token,
                        verifier(issuer)).status());
    }

    /**
     * A signature says the Issuer made <em>some</em> list, not that it made
     * <em>this</em> credential's list. Without comparing {@code sub} against the
     * credential's {@code uri}, a genuine, correctly signed, unexpired list from
     * a different population could be served in place of the right one and would
     * report everything valid.
     */
    @Test
    void refusesAListPublishedSomewhereElse() throws Exception {
        KeyPair issuer = p256();
        String token = StatusListOperations.issueStatusListToken(new int[16], 1,
                "https://issuer.lab.invalid/statuslists/OTHER", Instant.now(), null, -1,
                JWSAlgorithm.ES256, signer(issuer));

        SecurityException failure = assertThrows(SecurityException.class, () ->
                StatusListOperations.resolve(StatusListOperations.statusClaim(LIST_URI, 0), token,
                        verifier(issuer)));
        assertTrue(failure.getMessage().contains("points at"), failure.getMessage());
    }

    @Test
    void refusesAListSignedByTheWrongKey() throws Exception {
        KeyPair issuer = p256();
        KeyPair impostor = p256();
        String token = StatusListOperations.issueStatusListToken(new int[16], 1, LIST_URI,
                Instant.now(), null, -1, JWSAlgorithm.ES256, signer(issuer));

        assertThrows(SecurityException.class, () -> StatusListOperations.resolve(
                StatusListOperations.statusClaim(LIST_URI, 0), token, verifier(impostor)));
    }

    @Test
    void refusesAnExpiredList() throws Exception {
        KeyPair issuer = p256();
        String token = StatusListOperations.issueStatusListToken(new int[16], 1, LIST_URI,
                Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS), -1,
                JWSAlgorithm.ES256, signer(issuer));

        SecurityException failure = assertThrows(SecurityException.class, () ->
                StatusListOperations.resolve(StatusListOperations.statusClaim(LIST_URI, 0), token,
                        verifier(issuer)));
        assertTrue(failure.getMessage().contains("expired"), failure.getMessage());
    }

    @Test
    void refusesATokenThatIsNotAStatusList() throws Exception {
        KeyPair issuer = p256();
        // A perfectly good JWS, just not a Status List Token.
        com.nimbusds.jose.JWSObject other = new com.nimbusds.jose.JWSObject(
                new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.ES256)
                        .type(new com.nimbusds.jose.JOSEObjectType("JWT")).build(),
                new com.nimbusds.jose.Payload("{\"sub\":\"" + LIST_URI + "\"}"));
        other.sign(signer(issuer));

        assertThrows(SecurityException.class, () -> StatusListOperations.resolve(
                StatusListOperations.statusClaim(LIST_URI, 0), other.serialize(), verifier(issuer)));
    }

    @Test
    void anIndexBeyondTheListIsRefusedRatherThanReadAsValid() throws Exception {
        KeyPair issuer = p256();
        String token = StatusListOperations.issueStatusListToken(new int[16], 1, LIST_URI,
                Instant.now(), null, -1, JWSAlgorithm.ES256, signer(issuer));

        assertThrows(IndexOutOfBoundsException.class, () -> StatusListOperations.resolve(
                StatusListOperations.statusClaim(LIST_URI, 9_999), token, verifier(issuer)));
    }

    @Test
    void describeTalliesTheStatuses() throws Exception {
        KeyPair issuer = p256();
        int[] statuses = new int[32];
        statuses[1] = 1;
        statuses[2] = 1;
        String token = StatusListOperations.issueStatusListToken(statuses, 1, LIST_URI,
                Instant.now(), null, -1, JWSAlgorithm.ES256, signer(issuer));

        String report = StatusListOperations.describe(token);
        assertTrue(report.contains("30 x valid"), report);
        assertTrue(report.contains("2 x invalid (revoked)"), report);
    }

    @Test
    void namesApplicationSpecificStatusValues() {
        assertTrue(StatusListOperations.StatusType.describe(0x0F).contains("application-specific"));
        assertEquals("valid", StatusListOperations.StatusType.describe(0x00));
    }
}
