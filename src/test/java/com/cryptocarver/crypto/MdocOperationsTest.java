package com.cryptocarver.crypto;

import COSE.Attribute;
import COSE.HeaderKeys;
import COSE.OneKey;
import COSE.Sign1Message;
import com.upokecenter.cbor.CBORObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MdocOperationsTest {

    private static final String CLAIMS = """
            {"org.iso.18013.5.1": {
               "family_name": "Doe",
               "given_name": "John",
               "document_number": "ES-1234567",
               "issuing_country": "ES"
            }}""";

    @BeforeAll
    static void installBouncyCastleProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // --------------------------------------------------------------- issuing

    @Test
    void issuesAndVerifiesADocument() throws Exception {
        KeyPair issuer = p256();
        byte[] mdoc = issue(issuer, null);

        MdocOperations.VerificationReport report =
                MdocOperations.verify(mdoc, issuer.getPublic(), Instant.now());

        assertTrue(report.issuerSignatureValid());
        assertTrue(report.allDigestsMatched(), report.findings().toString());
        assertEquals(MdocOperations.MDL_DOCTYPE, report.mso().docType());
        assertEquals("1.0", report.mso().version());
        assertEquals("SHA-256", report.mso().digestAlgorithm());

        List<MdocOperations.Item> items = report.disclosed().get(MdocOperations.MDL_NAMESPACE);
        assertEquals(4, items.size());
        assertTrue(items.stream().anyMatch(i -> "family_name".equals(i.elementIdentifier())
                && "Doe".equals(i.valueAsText())));
    }

    /**
     * The trap the whole format turns on. The MSO commits to a digest of
     * {@code IssuerSignedItemBytes}, which is {@code #6.24(bstr .cbor
     * IssuerSignedItem)} — the tag-24 wrapped bytes. Hashing the
     * {@code IssuerSignedItem} map inside them instead produces a digest that
     * matches nothing, and the 2020 draft's wording ("the binary data of the
     * IssuerSignedItem") is ambiguous enough that it is worth pinning which of
     * the two this implementation does.
     */
    @Test
    void theDigestIsTakenOverTheTag24BytesNotTheItemInsideThem() throws Exception {
        KeyPair issuer = p256();
        byte[] mdoc = issue(issuer, null);

        MdocOperations.IssuerSigned parsed = MdocOperations.parse(mdoc);
        MdocOperations.MobileSecurityObject mso =
                MdocOperations.readMobileSecurityObject(parsed.issuerAuth());
        MdocOperations.Item item = parsed.namespaces().get(MdocOperations.MDL_NAMESPACE).get(0);

        byte[] committed = mso.valueDigests().get(MdocOperations.MDL_NAMESPACE).get(item.digestId());
        byte[] overTaggedBytes = MessageDigest.getInstance("SHA-256").digest(item.itemBytes());
        byte[] overTheItemItself = MessageDigest.getInstance("SHA-256").digest(
                CBORObject.DecodeFromBytes(item.itemBytes()).Untag().GetByteString());

        assertArrayEquals(committed, overTaggedBytes,
                "The MSO digest must be the one over the tag 24 bytes");
        assertFalse(MessageDigest.isEqual(committed, overTheItemItself),
                "Hashing the item inside the tag must NOT match, or this test proves nothing");

        // And the tagged bytes really do start with the tag: D8 18.
        assertEquals((byte) 0xD8, item.itemBytes()[0]);
        assertEquals((byte) 0x18, item.itemBytes()[1]);
    }

    @Test
    void everyElementGetsItsOwnSaltOfAtLeastSixteenBytes() throws Exception {
        byte[] mdoc = issue(p256(), null);
        List<MdocOperations.Item> items =
                MdocOperations.parse(mdoc).namespaces().get(MdocOperations.MDL_NAMESPACE);

        for (MdocOperations.Item item : items) {
            assertTrue(item.random().length >= 16, "Salt is " + item.random().length + " bytes");
        }
        assertFalse(java.util.Arrays.equals(items.get(0).random(), items.get(1).random()),
                "A salt reused across elements would let one digest be checked against another value");
    }

    // ---------------------------------------------------- selective disclosure

    /**
     * The point of the format: the MSO commits to every element, the holder
     * hands over only some, and the ones handed over still verify against the
     * same untouched signature.
     */
    @Test
    void droppingElementsLeavesTheRestVerifiable() throws Exception {
        KeyPair issuer = p256();
        byte[] mdoc = issue(issuer, null);

        CBORObject root = CBORObject.DecodeFromBytes(mdoc);
        CBORObject items = root.get("nameSpaces").get(MdocOperations.MDL_NAMESPACE);
        CBORObject narrowed = CBORObject.NewArray();
        narrowed.Add(items.get(0));
        root.get("nameSpaces").set(MdocOperations.MDL_NAMESPACE, narrowed);

        MdocOperations.VerificationReport report = MdocOperations.verify(
                root.EncodeToBytes(), issuer.getPublic(), Instant.now());

        assertTrue(report.issuerSignatureValid(), "The issuer signature is untouched by disclosure");
        assertTrue(report.allDigestsMatched(), report.findings().toString());
        assertEquals(1, report.disclosed().get(MdocOperations.MDL_NAMESPACE).size());
    }

    @Test
    void aChangedElementValueBreaksItsDigest() throws Exception {
        KeyPair issuer = p256();
        byte[] mdoc = issue(issuer, null);

        CBORObject root = CBORObject.DecodeFromBytes(mdoc);
        CBORObject items = root.get("nameSpaces").get(MdocOperations.MDL_NAMESPACE);
        CBORObject firstItem = CBORObject.DecodeFromBytes(items.get(0).Untag().GetByteString());
        firstItem.set("elementValue", CBORObject.FromObject("Forged"));
        CBORObject retagged = CBORObject.FromObjectAndTag(
                CBORObject.FromObject(firstItem.EncodeToBytes()), 24);
        items.set(0, retagged);

        MdocOperations.VerificationReport report = MdocOperations.verify(
                root.EncodeToBytes(), issuer.getPublic(), Instant.now());

        assertTrue(report.issuerSignatureValid(), "The MSO signature itself is still intact...");
        assertFalse(report.allDigestsMatched(), "...but the element no longer matches what it commits to");
        assertTrue(report.findings().stream()
                .anyMatch(f -> "ERROR".equals(f.severity()) && f.message().contains("digest")));
    }

    @Test
    void refusesAnItemThatIsNotWrappedInTag24() throws Exception {
        byte[] mdoc = issue(p256(), null);
        CBORObject root = CBORObject.DecodeFromBytes(mdoc);
        CBORObject items = root.get("nameSpaces").get(MdocOperations.MDL_NAMESPACE);
        items.set(0, CBORObject.DecodeFromBytes(items.get(0).Untag().GetByteString()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> MdocOperations.parse(root.EncodeToBytes()));
        assertTrue(failure.getMessage().contains("tag 24"), failure.getMessage());
    }

    // ---------------------------------------------------------------- trust

    @Test
    void carriesTheSignerCertificateAsX5chain() throws Exception {
        KeyPair issuer = p256();
        X509Certificate certificate = certificate(issuer, "CN=Document Signer,C=ES");
        byte[] mdoc = MdocOperations.issue(MdocOperations.MDL_DOCTYPE, CLAIMS, "SHA-256",
                validity(), issuer.getPrivate(), certificate, null);

        MdocOperations.VerificationReport report =
                MdocOperations.verify(mdoc, issuer.getPublic(), Instant.now());
        assertEquals(certificate, report.signerCertificate());
    }

    /** Verifying against the document's own certificate proves consistency, not
     *  provenance, and the report has to say so rather than look like a pass. */
    @Test
    void warnsWhenItVerifiesAgainstTheDocumentsOwnCertificate() throws Exception {
        byte[] mdoc = issue(p256(), null);

        MdocOperations.VerificationReport report = MdocOperations.verify(mdoc, null, Instant.now());
        assertTrue(report.issuerSignatureValid());
        assertTrue(report.findings().stream().anyMatch(f -> "WARN".equals(f.severity())
                && f.message().contains("not that a trusted issuer signed it")),
                report.findings().toString());
    }

    @Test
    void refusesADocumentSignedByAnotherKey() throws Exception {
        byte[] mdoc = issue(p256(), null);
        MdocOperations.VerificationReport report =
                MdocOperations.verify(mdoc, p256().getPublic(), Instant.now());

        assertFalse(report.issuerSignatureValid());
        assertFalse(report.allDigestsMatched());
    }

    // ------------------------------------------------------------- validity

    @Test
    void reportsADocumentUsedOutsideItsValidity() throws Exception {
        KeyPair issuer = p256();
        byte[] mdoc = issue(issuer, null);

        assertTrue(MdocOperations.verify(mdoc, issuer.getPublic(),
                        Instant.now().minus(30, ChronoUnit.DAYS)).findings().stream()
                .anyMatch(f -> f.message().contains("not valid until")));

        assertTrue(MdocOperations.verify(mdoc, issuer.getPublic(),
                        Instant.now().plus(400, ChronoUnit.DAYS)).findings().stream()
                .anyMatch(f -> f.message().contains("stopped being valid")));
    }

    @Test
    void refusesADigestAlgorithmTheStandardDoesNotAllow() throws Exception {
        KeyPair issuer = p256();
        X509Certificate certificate = certificate(issuer, "CN=DS,C=ES");
        assertThrows(IllegalArgumentException.class, () -> MdocOperations.issue(
                MdocOperations.MDL_DOCTYPE, CLAIMS, "SHA-1", validity(),
                issuer.getPrivate(), certificate, null));
    }

    // --------------------------------------------------------- device auth

    /** A device signature is computed over a structure neither side transmits;
     *  both rebuild it, which is what ties a presentation to one session. */
    @Test
    void verifiesADeviceSignatureAgainstItsSession() throws Exception {
        KeyPair device = p256();
        byte[] transcript = sessionTranscript("session-one");
        byte[] deviceNameSpaces = MdocOperations.issuerSignedItemBytes(
                CBORObject.NewMap().EncodeToBytes());

        byte[] deviceSignature = signDetached(device,
                MdocOperations.deviceAuthenticationPayload(
                        transcript, MdocOperations.MDL_DOCTYPE, deviceNameSpaces));

        assertTrue(MdocOperations.verifyDeviceSignature(deviceSignature, transcript,
                MdocOperations.MDL_DOCTYPE, deviceNameSpaces, device.getPublic()));
    }

    /** Replaying the same signature into a different session must fail: that is
     *  the whole reason the session transcript is inside the signed structure. */
    @Test
    void refusesADeviceSignatureReplayedIntoAnotherSession() throws Exception {
        KeyPair device = p256();
        byte[] deviceNameSpaces = MdocOperations.issuerSignedItemBytes(
                CBORObject.NewMap().EncodeToBytes());
        byte[] deviceSignature = signDetached(device,
                MdocOperations.deviceAuthenticationPayload(
                        sessionTranscript("session-one"), MdocOperations.MDL_DOCTYPE, deviceNameSpaces));

        assertFalse(MdocOperations.verifyDeviceSignature(deviceSignature,
                sessionTranscript("session-two"), MdocOperations.MDL_DOCTYPE,
                deviceNameSpaces, device.getPublic()));
    }

    @Test
    void theDeviceAuthenticationStructureStartsWithItsOwnName() {
        byte[] payload = MdocOperations.deviceAuthenticationPayload(
                sessionTranscript("s"), MdocOperations.MDL_DOCTYPE,
                MdocOperations.issuerSignedItemBytes(CBORObject.NewMap().EncodeToBytes()));

        CBORObject structure = CBORObject.DecodeFromBytes(
                CBORObject.DecodeFromBytes(payload).Untag().GetByteString());
        assertEquals("DeviceAuthentication", structure.get(0).AsString());
        assertEquals(4, structure.size());
        assertEquals(MdocOperations.MDL_DOCTYPE, structure.get(2).AsString());
    }

    // -------------------------------------------------------------- reporting

    @Test
    void describeListsTheDisclosedElements() throws Exception {
        KeyPair issuer = p256();
        byte[] mdoc = issue(issuer, p256().getPublic());

        String english = MdocOperations.describe(mdoc, issuer.getPublic(), Instant.now(),
                java.util.Locale.ENGLISH);
        assertTrue(english.contains("family_name"), english);
        assertTrue(english.contains(MdocOperations.MDL_DOCTYPE), english);
        assertTrue(english.contains("device key: present"), english);

        String spanish = MdocOperations.describe(mdoc, issuer.getPublic(), Instant.now(),
                java.util.Locale.forLanguageTag("es"));
        assertTrue(spanish.contains("Elementos divulgados"), spanish);
    }

    // ------------------------------------------------------------- fixtures

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static MdocOperations.ValidityInfo validity() {
        Instant now = Instant.now();
        return new MdocOperations.ValidityInfo(now,
                now.minus(1, ChronoUnit.DAYS), now.plus(365, ChronoUnit.DAYS), null);
    }

    private static byte[] issue(KeyPair issuer, java.security.PublicKey deviceKey) throws Exception {
        return MdocOperations.issue(MdocOperations.MDL_DOCTYPE, CLAIMS, "SHA-256", validity(),
                issuer.getPrivate(), certificate(issuer, "CN=Document Signer,C=ES"), deviceKey);
    }

    private static X509Certificate certificate(KeyPair pair, String subject) throws Exception {
        X500Name name = new X500Name(subject);
        Instant now = Instant.now();
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(
                new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()),
                        Date.from(now.minus(1, ChronoUnit.DAYS)),
                        Date.from(now.plus(365, ChronoUnit.DAYS)),
                        name, pair.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withECDSA")
                                .setProvider("BC").build(pair.getPrivate())));
    }

    /** A stand-in for the real thing: three elements, as ISO/IEC 18013-5 has it
     *  (device engagement, reader key, handover). Its content does not matter
     *  here — only that two sessions differ. */
    private static byte[] sessionTranscript(String marker) {
        CBORObject transcript = CBORObject.NewArray();
        transcript.Add(CBORObject.Null);
        transcript.Add(CBORObject.Null);
        CBORObject handover = CBORObject.NewArray();
        handover.Add(CBORObject.FromObject("LabHandover"));
        handover.Add(CBORObject.FromObject(marker));
        transcript.Add(handover);
        return transcript.EncodeToBytes();
    }

    private static byte[] signDetached(KeyPair signer, byte[] payload) throws Exception {
        Sign1Message signature = new Sign1Message(false, false);
        signature.SetContent(payload);
        signature.addAttribute(HeaderKeys.Algorithm, COSE.AlgorithmID.ECDSA_256.AsCBOR(),
                Attribute.PROTECTED);
        signature.sign(new OneKey(signer.getPublic(), signer.getPrivate()));
        return signature.EncodeToBytes();
    }
}
