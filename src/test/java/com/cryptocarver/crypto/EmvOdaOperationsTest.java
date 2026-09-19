package com.cryptocarver.crypto;

import com.cryptocarver.crypto.EmvOdaOperations.DynamicApplicationData;
import com.cryptocarver.crypto.EmvOdaOperations.DynamicMode;
import com.cryptocarver.crypto.EmvOdaOperations.Finding;
import com.cryptocarver.crypto.EmvOdaOperations.IccCertificate;
import com.cryptocarver.crypto.EmvOdaOperations.IssuedCertificate;
import com.cryptocarver.crypto.EmvOdaOperations.IssuerCertificate;
import com.cryptocarver.crypto.EmvOdaOperations.RsaPrivateKey;
import com.cryptocarver.crypto.EmvOdaOperations.RsaPublicKey;
import com.cryptocarver.crypto.EmvOdaOperations.StaticApplicationData;
import com.cryptocarver.crypto.EmvOdaOperations.StaticData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round trips through EMV Book 2 offline data authentication, and the specific
 * ways each of the three schemes goes wrong.
 *
 * <p>The key sizes are chosen to exercise both halves of the certificate
 * layout. A 128-byte CA modulus leaves 92 bytes for an issuer key, so the
 * 96-byte issuer key spills 4 bytes into tag {@code 92}; the 64-byte issuer key
 * fits and must not. Book 2 uses exponent 3 or 65537 and nothing else, so every
 * key here is generated with exponent 3.</p>
 */
class EmvOdaOperationsTest {

    private static final String PAN = "4761739001010119";
    private static final String ISSUER_IDENTIFIER = "47617390";
    /** Two AFL records plus the AIP, as a terminal would have assembled them. */
    private static final String STATIC_DATA =
            "70115A0844AAAAAAAAAAAAAA5F3401009F0702FF00"
                    + "701A5F280208409F0D05B8409CA8009F0E0500100000009F0F05B8689C9800";
    private static final String FUTURE_EXPIRY = expiry(10);
    private static final String PAST_EXPIRY = expiry(-10);

    private static KeyPair ca;
    private static KeyPair issuer;
    private static KeyPair shortIssuer;
    private static KeyPair icc;

    @BeforeAll
    static void generateKeys() throws Exception {
        ca = rsa(1024);
        issuer = rsa(768);
        shortIssuer = rsa(512);
        icc = rsa(512);
    }

    private static KeyPair rsa(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(new RSAKeyGenParameterSpec(bits, BigInteger.valueOf(3)));
        return generator.generateKeyPair();
    }

    private static RsaPublicKey pub(KeyPair pair) {
        return RsaPublicKey.of((RSAPublicKey) pair.getPublic());
    }

    private static RsaPrivateKey priv(KeyPair pair) {
        return RsaPrivateKey.of((RSAPrivateKey) pair.getPrivate());
    }

    private static String expiry(int monthsFromNow) {
        YearMonth when = YearMonth.now().plusMonths(monthsFromNow);
        return String.format("%02d%02d", when.getMonthValue(), when.getYear() % 100);
    }

    private static boolean hasError(List<Finding> findings, String fragment) {
        return findings.stream()
                .anyMatch(f -> "ERROR".equals(f.severity()) && f.message().contains(fragment));
    }

    // =====================================================================
    // Key sizes and the certificate layout
    // =====================================================================

    @Test
    void theCaModulusSizesTheIssuerCertificate() {
        assertEquals(128, pub(ca).modulusLength());
        assertEquals(96, pub(issuer).modulusLength());
        assertEquals(64, pub(shortIssuer).modulusLength());
    }

    @Test
    void aKeyTooLongForTheCertificateSpillsIntoTheRemainder() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");

        assertTrue(issued.hasRemainder(), "a 96-byte key does not fit in 128 - 36 = 92 bytes");
        assertEquals(4, issued.remainder().length() / 2);
    }

    @Test
    void aKeyThatFitsCarriesNoRemainder() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(shortIssuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");

        assertFalse(issued.hasRemainder(), "a 64-byte key fits in 92 bytes and must not spill");
        assertEquals("", issued.remainder());
    }

    @Test
    void theRecoveredBlockIsAsLongAsTheModulusThatRecoveredIt() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");

        String recovered = EmvOdaOperations.recover(issued.certificate(), pub(ca));

        assertEquals(128, recovered.length() / 2);
        assertTrue(recovered.startsWith("6A02"), "header '6A' then certificate format '02'");
        assertTrue(recovered.endsWith("BC"), "trailer 'BC'");
    }

    // =====================================================================
    // SDA — Book 2 clauses 5.3 and 5.4
    // =====================================================================

    @Test
    void staticDataAuthenticationRoundTrips() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");
        String ssad = EmvOdaOperations.signStaticApplicationData(priv(issuer), "1234", STATIC_DATA);

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), issued.exponent(), pub(ca), PAN);
        assertTrue(certificate.passed(), () -> EmvOdaOperations.describe(certificate));
        assertEquals(pub(issuer).modulusHex(), certificate.issuerPublicKey().modulusHex(),
                "the modulus rebuilt from the certificate and the remainder");

        StaticApplicationData data = EmvOdaOperations.verifyStaticApplicationData(
                ssad, certificate.issuerPublicKey(), STATIC_DATA);
        assertTrue(data.passed(), () -> EmvOdaOperations.describe(data));
        assertEquals("1234", data.dataAuthenticationCode());
    }

    @Test
    void changingOneByteOfTheStaticDataBreaksTheSignature() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");
        String ssad = EmvOdaOperations.signStaticApplicationData(priv(issuer), "1234", STATIC_DATA);
        String tampered = "71" + STATIC_DATA.substring(2);

        StaticApplicationData data = EmvOdaOperations.verifyStaticApplicationData(
                ssad, EmvOdaOperations.recoverIssuerPublicKey(issued.certificate(), issued.remainder(),
                        issued.exponent(), pub(ca), PAN).issuerPublicKey(), tampered);

        assertFalse(data.passed());
        assertTrue(hasError(data.findings(), "does not match"));
    }

    @Test
    void theSameSignedStaticDataVerifiesEveryTime() {
        // Not a pathology: it is what SDA is. The report has to say so, because a
        // passing SDA is routinely mistaken for evidence that a card is present.
        String ssad = EmvOdaOperations.signStaticApplicationData(priv(issuer), "1234", STATIC_DATA);

        StaticApplicationData first = EmvOdaOperations.verifyStaticApplicationData(
                ssad, pub(issuer), STATIC_DATA);
        StaticApplicationData replayed = EmvOdaOperations.verifyStaticApplicationData(
                ssad, pub(issuer), STATIC_DATA);

        assertTrue(first.passed());
        assertTrue(replayed.passed());
        assertTrue(first.findings().stream().anyMatch(f -> f.message().contains("replayed")),
                "the report must say that a static signature proves nothing about the card");
    }

    @Test
    void theIssuerIdentifierMustPrefixThePan() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), "55556666", FUTURE_EXPIRY, "000001");

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), issued.exponent(), pub(ca), PAN);

        assertFalse(certificate.passed());
        assertTrue(hasError(certificate.findings(), "not a prefix of the PAN"));
    }

    @Test
    void aShortIssuerIdentifierIsPaddedWithFAndStillMatches() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), "476173", FUTURE_EXPIRY, "000001");

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), issued.exponent(), pub(ca), PAN);

        assertEquals("476173FF", certificate.issuerIdentifier());
        assertTrue(certificate.passed(), () -> EmvOdaOperations.describe(certificate));
    }

    @Test
    void anExpiredCertificateFails() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, PAST_EXPIRY, "000001");

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), issued.exponent(), pub(ca), PAN);

        assertFalse(certificate.passed());
        assertTrue(hasError(certificate.findings(), "expired"));
    }

    @Test
    void theWrongCaKeyFailsOnTheFramingRatherThanTheHash() {
        // Recovery with the wrong key produces noise, not a plausible block, so the
        // report should name the header and not send anyone hunting for a bad hash.
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");
        RsaPublicKey otherCa = wrongKeyLargerThan(issued.certificate());

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), issued.exponent(), otherCa, PAN);

        assertFalse(certificate.passed());
        assertTrue(hasError(certificate.findings(), "not '6A'")
                || hasError(certificate.findings(), "not 'BC'"));
    }

    @Test
    void theExponentIsPartOfTheHashInput() {
        // A card that answers with tag 90 and 92 but no 9F32 recovers a perfectly
        // plausible modulus and then fails the hash. The finding has to point at
        // the exponent, or the next hour goes into the modulus.
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), null, pub(ca), PAN);

        assertFalse(certificate.passed());
        assertTrue(hasError(certificate.findings(), "part of the hash input"));
    }

    @Test
    void aMissingRemainderIsReportedAgainstTheDeclaredKeyLength() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), "", issued.exponent(), pub(ca), PAN);

        assertFalse(certificate.passed());
        assertTrue(hasError(certificate.findings(), "must hold the remaining 4 bytes"));
    }

    @Test
    void aRemainderSuppliedForAKeyThatFitsIsRejected() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(shortIssuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), "AABBCCDD", issued.exponent(), pub(ca), PAN);

        assertFalse(certificate.passed());
        assertTrue(hasError(certificate.findings(), "must be absent"));
    }

    // =====================================================================
    // Static data assembly — Book 2 clause 5.1.1
    // =====================================================================

    @Test
    void theAipIsAppendedOnlyWhenTheTagListAsksForIt() {
        StaticData without = EmvOdaOperations.staticDataToBeAuthenticated(List.of("7005AABB"), "5C00", "");
        StaticData with = EmvOdaOperations.staticDataToBeAuthenticated(List.of("7005AABB"), "5C00", "82");

        assertEquals("7005AABB", without.hex());
        assertEquals("7005AABB5C00", with.hex());
    }

    @Test
    void aTagListWithAnythingButTheAipFails() {
        StaticData data = EmvOdaOperations.staticDataToBeAuthenticated(List.of("7005AABB"), "5C00", "829F02");

        assertTrue(hasError(data.findings(), "shall contain tag '82' and nothing else"));
    }

    @Test
    void recordsAreConcatenatedInTheOrderTheyWereRead() {
        StaticData data = EmvOdaOperations.staticDataToBeAuthenticated(
                List.of("AA", "BB", "", "CC"), null, null);

        assertEquals("AABBCC", data.hex());
    }

    // =====================================================================
    // DDA — Book 2 clauses 6.4 and 6.5
    // =====================================================================

    @Test
    void dynamicDataAuthenticationRoundTrips() {
        Card card = issueCard();
        String ddol = "9F3704" + "01020304";
        String dynamicData = "08" + "1122334455667788";
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, ddol);

        DynamicApplicationData result = EmvOdaOperations.verifyDynamicApplicationData(
                sdad, card.iccKey(), ddol);

        assertTrue(result.passed(), () -> EmvOdaOperations.describe(result));
        assertEquals(DynamicMode.DDA, result.mode());
        assertEquals("1122334455667788", result.iccDynamicNumber());
    }

    @Test
    void aDynamicSignatureDoesNotVerifyAgainstDifferentTerminalData() {
        // The whole point of DDA: the terminal's number is in the hash, so a
        // captured signature is worthless against the next unpredictable number.
        Card card = issueCard();
        String dynamicData = "08" + "1122334455667788";
        String sdad = EmvOdaOperations.signDynamicApplicationData(
                priv(icc), dynamicData, "9F370401020304");

        DynamicApplicationData replayed = EmvOdaOperations.verifyDynamicApplicationData(
                sdad, card.iccKey(), "9F3704DEADBEEF");

        assertFalse(replayed.passed());
        assertTrue(hasError(replayed.findings(), "does not match"));
    }

    @Test
    void theIccCertificateCoversTheStaticData() {
        // Book 2 clause 6.4 step 5. Getting the AFL wrong fails here, one step
        // before anyone thinks to look, and it looks like a key problem.
        IssuedCertificate issuerCertificate = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");
        IssuedCertificate iccCertificate = EmvOdaOperations.signIccCertificate(
                priv(issuer), pub(icc), PAN, FUTURE_EXPIRY, "000002", STATIC_DATA);

        IccCertificate recovered = EmvOdaOperations.recoverIccPublicKey(
                iccCertificate.certificate(), iccCertificate.remainder(), iccCertificate.exponent(),
                pub(issuer), STATIC_DATA + "00", PAN);

        assertFalse(recovered.passed(), "one stray byte of static data must fail the certificate");
        assertTrue(hasError(recovered.findings(), "ICC Public Key Certificate does not match"));
        assertNotNull(issuerCertificate);
    }

    @Test
    void theIccCertificatePanMustMatchTheCard() {
        IssuedCertificate iccCertificate = EmvOdaOperations.signIccCertificate(
                priv(issuer), pub(icc), PAN, FUTURE_EXPIRY, "000002", STATIC_DATA);

        IccCertificate recovered = EmvOdaOperations.recoverIccPublicKey(
                iccCertificate.certificate(), iccCertificate.remainder(), iccCertificate.exponent(),
                pub(issuer), STATIC_DATA, "4761739001010127");

        assertFalse(recovered.passed());
        assertTrue(hasError(recovered.findings(), "Application PAN"));
    }

    @Test
    void anIccKeyTooLongForTheIssuerCertificateSpillsIntoTag9F48() {
        // 96 - 42 = 54 bytes of room for a 64-byte key, so 10 bytes must travel
        // separately and the two halves have to be put back together in order.
        IssuedCertificate iccCertificate = EmvOdaOperations.signIccCertificate(
                priv(issuer), pub(icc), PAN, FUTURE_EXPIRY, "000002", STATIC_DATA);

        assertEquals(10, iccCertificate.remainder().length() / 2);

        IccCertificate recovered = EmvOdaOperations.recoverIccPublicKey(
                iccCertificate.certificate(), iccCertificate.remainder(), iccCertificate.exponent(),
                pub(issuer), STATIC_DATA, PAN);

        assertTrue(recovered.passed(), () -> EmvOdaOperations.describe(recovered));
        assertEquals(pub(icc).modulusHex(), recovered.iccPublicKey().modulusHex());
    }

    // =====================================================================
    // CDA — Book 2 clause 6.6
    // =====================================================================

    @Test
    void combinedDataAuthenticationRoundTrips() {
        Card card = issueCard();
        String transactionData = "000000010000000000000000097801020304";
        String dynamicData = EmvOdaOperations.combinedDynamicData(
                "1122334455667788", "80", "A1B2C3D4E5F60718",
                EmvOdaOperations.transactionDataHashCode(transactionData));
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, "01020304");

        DynamicApplicationData result = EmvOdaOperations.verifyCombinedApplicationData(
                sdad, card.iccKey(), "01020304", "80", transactionData);

        assertTrue(result.passed(), () -> EmvOdaOperations.describe(result));
        assertEquals("80", result.cryptogramInformationData());
        assertEquals("A1B2C3D4E5F60718", result.applicationCryptogram());
        assertEquals("1122334455667788", result.iccDynamicNumber());
    }

    @Test
    void aCdaSignatureDoesNotVerifyAsDda() {
        // Same tag, same recovered format '05', different hash input. Reading a CDA
        // signature as DDA fails on the hash with everything else looking right.
        Card card = issueCard();
        String ddol = "9F3704" + "01020304";
        String dynamicData = EmvOdaOperations.combinedDynamicData(
                "1122334455667788", "80", "A1B2C3D4E5F60718",
                EmvOdaOperations.transactionDataHashCode("00"));
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, "01020304");

        DynamicApplicationData asDda = EmvOdaOperations.verifyDynamicApplicationData(
                sdad, card.iccKey(), ddol);

        assertFalse(asDda.passed(), "CDA hashes the unpredictable number alone, not the DDOL data");
        assertTrue(hasError(asDda.findings(), "does not match"));
    }

    @Test
    void cdaCatchesACidThatDisagreesWithTheResponse() {
        Card card = issueCard();
        String dynamicData = EmvOdaOperations.combinedDynamicData(
                "11223344", "80", "A1B2C3D4E5F60718",
                EmvOdaOperations.transactionDataHashCode("00"));
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, "01020304");

        DynamicApplicationData result = EmvOdaOperations.verifyCombinedApplicationData(
                sdad, card.iccKey(), "01020304", "40", "00");

        assertFalse(result.passed());
        assertTrue(hasError(result.findings(), "different decision from the one it announced"));
    }

    @Test
    void cdaCatchesTransactionDataThatWasChangedAfterSigning() {
        Card card = issueCard();
        String signedOver = "000000012345" + "0978";
        String settledAs = "000000099999" + "0978";
        String dynamicData = EmvOdaOperations.combinedDynamicData(
                "11223344", "80", "A1B2C3D4E5F60718",
                EmvOdaOperations.transactionDataHashCode(signedOver));
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, "01020304");

        DynamicApplicationData result = EmvOdaOperations.verifyCombinedApplicationData(
                sdad, card.iccKey(), "01020304", "80", settledAs);

        assertFalse(result.passed(), "the signature is intact; the transaction under it is not");
        assertTrue(hasError(result.findings(), "Transaction Data Hash Code"));
    }

    @Test
    void cdaReportsTheCryptogramType() {
        Card card = issueCard();
        String dynamicData = EmvOdaOperations.combinedDynamicData(
                "11223344", "80", "A1B2C3D4E5F60718",
                EmvOdaOperations.transactionDataHashCode("00"));
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, "01020304");

        DynamicApplicationData result = EmvOdaOperations.verifyCombinedApplicationData(
                sdad, card.iccKey(), "01020304", "80", "00");

        assertTrue(result.findings().stream().anyMatch(f -> f.message().contains("ARQC")),
                "CID '80' is an ARQC");
    }

    @Test
    void anUnpredictableNumberOfTheWrongLengthIsRejected() {
        Card card = issueCard();
        String dynamicData = EmvOdaOperations.combinedDynamicData(
                "11223344", "80", "A1B2C3D4E5F60718",
                EmvOdaOperations.transactionDataHashCode("00"));
        String sdad = EmvOdaOperations.signDynamicApplicationData(priv(icc), dynamicData, "010203");

        DynamicApplicationData result = EmvOdaOperations.verifyCombinedApplicationData(
                sdad, card.iccKey(), "010203", "80", "00");

        assertFalse(result.passed());
        assertTrue(hasError(result.findings(), "tag '9F37' is 4 bytes"));
    }

    // =====================================================================
    // Inputs
    // =====================================================================

    @Test
    void recoveryRejectsASignatureThatIsNotTheModulusLength() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> EmvOdaOperations.recover("AABBCC", pub(ca)));

        assertTrue(thrown.getMessage().contains("recovery is only defined when they are equal"));
    }

    @Test
    void anExponentOtherThanThreeOrF4IsRejected() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");

        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), "05", pub(ca), PAN);

        assertTrue(hasError(certificate.findings(), "EMV allows only 3 and 65537"));
    }

    @Test
    void oddLengthHexIsRejectedWithTheFieldName() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> EmvOdaOperations.staticDataToBeAuthenticated(List.of("ABC"), null, null));

        assertTrue(thrown.getMessage().contains("AFL record"));
    }

    @Test
    void describeNamesTheVerdictAndTheClauses() {
        IssuedCertificate issued = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");
        IssuerCertificate certificate = EmvOdaOperations.recoverIssuerPublicKey(
                issued.certificate(), issued.remainder(), issued.exponent(), pub(ca), PAN);

        String report = EmvOdaOperations.describe(certificate);

        assertTrue(report.contains("PASSED"));
        assertTrue(report.contains("Book 2, 5.3"));
        assertTrue(report.contains(pub(issuer).modulusHex()));
    }

    /**
     * A wrong CA key whose modulus is numerically above this signature.
     *
     * <p>Two random 1024-bit moduli sit either side of a given signature about
     * half the time, and when the modulus is the smaller one recovery refuses
     * the input outright instead of returning a bad block. Both are correct
     * behaviour; only one of them is what this test is about, so the key is
     * chosen rather than taken on a coin flip.</p>
     */
    private static RsaPublicKey wrongKeyLargerThan(String signature) {
        BigInteger value = new BigInteger(signature, 16);
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                RsaPublicKey candidate = pub(rsa(1024));
                if (value.compareTo(candidate.modulus()) < 0) {
                    return candidate;
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("no 1024-bit modulus above the signature in 20 attempts");
    }

    /** A personalised card: both certificates, ready for the dynamic half. */
    private record Card(RsaPublicKey issuerKey, RsaPublicKey iccKey) {
    }

    private Card issueCard() {
        IssuedCertificate issuerCertificate = EmvOdaOperations.signIssuerCertificate(
                priv(ca), pub(issuer), ISSUER_IDENTIFIER, FUTURE_EXPIRY, "000001");
        IssuerCertificate recoveredIssuer = EmvOdaOperations.recoverIssuerPublicKey(
                issuerCertificate.certificate(), issuerCertificate.remainder(),
                issuerCertificate.exponent(), pub(ca), PAN);
        assertTrue(recoveredIssuer.passed(), () -> EmvOdaOperations.describe(recoveredIssuer));

        IssuedCertificate iccCertificate = EmvOdaOperations.signIccCertificate(
                priv(issuer), pub(icc), PAN, FUTURE_EXPIRY, "000002", STATIC_DATA);
        IccCertificate recoveredIcc = EmvOdaOperations.recoverIccPublicKey(
                iccCertificate.certificate(), iccCertificate.remainder(), iccCertificate.exponent(),
                recoveredIssuer.issuerPublicKey(), STATIC_DATA, PAN);
        assertTrue(recoveredIcc.passed(), () -> EmvOdaOperations.describe(recoveredIcc));

        return new Card(recoveredIssuer.issuerPublicKey(), recoveredIcc.iccPublicKey());
    }
}
