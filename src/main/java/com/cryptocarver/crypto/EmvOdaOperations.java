package com.cryptocarver.crypto;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * EMV Offline Data Authentication: SDA, DDA and CDA, both halves — recovering
 * and checking what a card presents, and signing the same structures so there
 * is something to check.
 *
 * <p>Everything here is EMV 4.x Book 2, clauses 5 (SDA) and 6 (offline dynamic
 * data authentication), with the RSA signature scheme of Annex A2.1: ISO/IEC
 * 9796-2 scheme 1, digital signature giving message recovery. The payload is
 * not hashed and signed alongside the data; it <em>is</em> the signature, and
 * you get it back by raising the signature to the public exponent.</p>
 *
 * <h2>The recovered block, and what the hash actually covers</h2>
 *
 * <p>A recovered block is always exactly as long as the modulus that recovered
 * it, and always shaped the same way:</p>
 *
 * <pre>
 *   '6A' | format | ...payload... | 20-byte hash | 'BC'
 *     0      1                        N-21        N-1
 * </pre>
 *
 * <p>The hash input is <b>bytes 1 through N-22 of the recovered block</b> —
 * from the format byte up to but not including the hash itself — followed by
 * material that never travelled inside the signature. That trailing material
 * is the whole point of each scheme, and it differs every time:</p>
 *
 * <table border="1">
 *   <caption>Trailing hash input by structure</caption>
 *   <tr><th>Structure</th><th>Appended after the recovered bytes</th></tr>
 *   <tr><td>Issuer PK certificate (tag {@code 90})</td>
 *       <td>PK remainder ({@code 92}), PK exponent ({@code 9F32})</td></tr>
 *   <tr><td>ICC PK certificate (tag {@code 9F46})</td>
 *       <td>PK remainder ({@code 9F48}), PK exponent ({@code 9F47}),
 *           <b>and the static data to be authenticated</b></td></tr>
 *   <tr><td>SSAD (tag {@code 93})</td><td>the static data to be authenticated</td></tr>
 *   <tr><td>SDAD, DDA (tag {@code 9F4B})</td><td>the DDOL data the terminal sent</td></tr>
 *   <tr><td>SDAD, CDA (tag {@code 9F4B})</td><td>the 4-byte Unpredictable Number, and nothing else</td></tr>
 * </table>
 *
 * <h2>Three things that cost an afternoon</h2>
 * <ol>
 *   <li><b>The ICC certificate covers the static data.</b> Book 2 clause 6.4
 *       step 5 appends the static data to be authenticated to the ICC
 *       certificate's hash input. So an AFL you assembled wrongly does not fail
 *       at the dynamic signature, where you would look for it — it fails while
 *       recovering the ICC public key, which reads like a key or a certificate
 *       problem and is not one.</li>
 *   <li><b>The public key exponent is inside the hash.</b> Recovering a
 *       plausible-looking modulus and then getting a hash mismatch usually means
 *       the exponent (tag {@code 9F32} or {@code 9F47}) is missing or wrong, not
 *       that the modulus is.</li>
 *   <li><b>DDA and CDA share a structure and not an input.</b> Same tag, same
 *       recovered format {@code '05'}, but DDA hashes the DDOL data and CDA
 *       hashes only the Unpredictable Number. Verifying a CDA signature as DDA
 *       fails on the hash with everything else looking correct.</li>
 * </ol>
 *
 * <h2>What SDA is worth</h2>
 * <p>SDA proves that an issuer signed this data once. It does not prove a card
 * is present: the signature is static, so anything that can read it can replay
 * it, which is why SDA-only cards are cloneable onto a chip that answers with
 * the same bytes. DDA and CDA fix that by signing something the terminal chose
 * this transaction. CDA goes further and binds the Application Cryptogram into
 * the same signature, so the card cannot be made to authenticate one
 * transaction and settle another.</p>
 *
 * <p>This is a bench, not a terminal. It performs no card risk management, sets
 * no TVR bits, and decides nothing about a transaction.</p>
 */
public final class EmvOdaOperations {

    /** Book 2 Annex A2.1 — the recovered data header, on every structure here. */
    public static final int RECOVERED_HEADER = 0x6A;
    /** Book 2 Annex A2.1 — the recovered data trailer, on every structure here. */
    public static final int RECOVERED_TRAILER = 0xBC;

    public static final int FORMAT_ISSUER_CERTIFICATE = 0x02;
    public static final int FORMAT_STATIC_APPLICATION_DATA = 0x03;
    public static final int FORMAT_ICC_CERTIFICATE = 0x04;
    public static final int FORMAT_DYNAMIC_APPLICATION_DATA = 0x05;

    /** Book 2 Annex B — the only hash algorithm indicator EMV defines. */
    public static final int HASH_ALGORITHM_SHA1 = 0x01;
    /** Book 2 Annex B2.1 — the only public key algorithm indicator EMV defines. */
    public static final int PUBLIC_KEY_ALGORITHM_RSA = 0x01;

    /** Book 2 Table 27 — the mandatory upper bound for every modulus in EMV. */
    public static final int MAXIMUM_MODULUS_BYTES = 248;

    private static final byte PAD = (byte) 0xBB;
    private static final int HASH_BYTES = 20;
    /** Header, hash and trailer: what Annex A2.1 spends before any payload. */
    private static final int SCHEME_OVERHEAD = 22;

    /** {@code N_CA - 36}: the issuer certificate's own fields cost 14 bytes on top of the scheme's 22. */
    public static final int ISSUER_CERTIFICATE_OVERHEAD = SCHEME_OVERHEAD + 14;
    /** {@code N_I - 42}: the ICC certificate carries a 10-byte PAN, so 20 bytes on top of 22. */
    public static final int ICC_CERTIFICATE_OVERHEAD = SCHEME_OVERHEAD + 20;
    /** {@code N_I - 26}: format, hash indicator and the 2-byte Data Authentication Code. */
    public static final int STATIC_DATA_OVERHEAD = SCHEME_OVERHEAD + 4;
    /** {@code N_IC - L_DD - 25}: format, hash indicator and the dynamic data length byte. */
    public static final int DYNAMIC_DATA_OVERHEAD = SCHEME_OVERHEAD + 3;

    private static final BigInteger EXPONENT_3 = BigInteger.valueOf(3);
    private static final BigInteger EXPONENT_F4 = BigInteger.valueOf(65537);

    private static final String ERROR = "ERROR";
    private static final String WARNING = "WARNING";
    private static final String INFO = "INFO";

    private EmvOdaOperations() {
    }

    // =====================================================================
    // Keys
    // =====================================================================

    /**
     * An EMV RSA public key. The modulus is held as hexadecimal rather than as a
     * {@link BigInteger} because in EMV its <em>length in bytes</em> is a
     * protocol field — it sizes every recovered block — and a BigInteger would
     * quietly lose a leading zero byte that the card counted.
     */
    public record RsaPublicKey(String modulusHex, String exponentHex) {

        public RsaPublicKey {
            modulusHex = normalizeHex(modulusHex, "Modulus");
            exponentHex = normalizeHex(exponentHex, "Public key exponent");
            if (modulusHex.isEmpty()) {
                throw new IllegalArgumentException("Modulus must not be empty");
            }
            if (exponentHex.isEmpty()) {
                throw new IllegalArgumentException("Public key exponent must not be empty");
            }
        }

        public static RsaPublicKey of(String modulusHex, String exponentHex) {
            return new RsaPublicKey(modulusHex, exponentHex);
        }

        /** Reads a JCE key, taking the modulus at its natural EMV byte length. */
        public static RsaPublicKey of(java.security.interfaces.RSAPublicKey key) {
            return new RsaPublicKey(unsignedHex(key.getModulus()), unsignedHex(key.getPublicExponent()));
        }

        public BigInteger modulus() {
            return new BigInteger(1, bytes(modulusHex));
        }

        public BigInteger exponent() {
            return new BigInteger(1, bytes(exponentHex));
        }

        /** {@code N_CA}, {@code N_I} or {@code N_IC}, depending on whose key this is. */
        public int modulusLength() {
            return modulusHex.length() / 2;
        }

        public int exponentLength() {
            return exponentHex.length() / 2;
        }
    }

    /** The signing half. Only the modulus and the private exponent are needed. */
    public record RsaPrivateKey(String modulusHex, String privateExponentHex) {

        public RsaPrivateKey {
            modulusHex = normalizeHex(modulusHex, "Modulus");
            privateExponentHex = normalizeHex(privateExponentHex, "Private exponent");
            if (modulusHex.isEmpty() || privateExponentHex.isEmpty()) {
                throw new IllegalArgumentException("A private key needs both a modulus and a private exponent");
            }
        }

        public static RsaPrivateKey of(java.security.interfaces.RSAPrivateKey key) {
            return new RsaPrivateKey(unsignedHex(key.getModulus()), unsignedHex(key.getPrivateExponent()));
        }

        public BigInteger modulus() {
            return new BigInteger(1, bytes(modulusHex));
        }

        public BigInteger privateExponent() {
            return new BigInteger(1, bytes(privateExponentHex));
        }

        public int modulusLength() {
            return modulusHex.length() / 2;
        }
    }

    // =====================================================================
    // Results
    // =====================================================================

    /**
     * One observation, carrying the Book 2 clause it comes from so it can be
     * taken back to the specification instead of argued about.
     *
     * @param severity {@code ERROR} for what Book 2 calls a failure, {@code WARNING}
     *                 for what is merely suspicious, {@code INFO} for an observation
     * @param clause   the Book 2 clause, or {@code null}
     */
    public record Finding(String severity, String clause, String message) {
    }

    /** What the three schemes have in common: a recovered block and a verdict. */
    public interface Recovered {
        String recoveredHex();

        List<Finding> findings();

        default boolean passed() {
            return findings().stream().noneMatch(f -> ERROR.equals(f.severity()));
        }
    }

    /** Book 2 clause 5.3 / 6.3, recovered from tag {@code 90}. */
    public record IssuerCertificate(String issuerIdentifier,
                                    String expiryDate,
                                    String serialNumber,
                                    int hashAlgorithmIndicator,
                                    int publicKeyAlgorithmIndicator,
                                    int declaredKeyLength,
                                    int declaredExponentLength,
                                    RsaPublicKey issuerPublicKey,
                                    String recoveredHex,
                                    List<Finding> findings) implements Recovered {
    }

    /** Book 2 clause 6.4, recovered from tag {@code 9F46}. */
    public record IccCertificate(String pan,
                                 String expiryDate,
                                 String serialNumber,
                                 int hashAlgorithmIndicator,
                                 int publicKeyAlgorithmIndicator,
                                 int declaredKeyLength,
                                 int declaredExponentLength,
                                 RsaPublicKey iccPublicKey,
                                 String recoveredHex,
                                 List<Finding> findings) implements Recovered {
    }

    /** Book 2 clause 5.4, recovered from tag {@code 93}. */
    public record StaticApplicationData(String dataAuthenticationCode,
                                        int hashAlgorithmIndicator,
                                        int padLength,
                                        String recoveredHex,
                                        List<Finding> findings) implements Recovered {
    }

    /** Which of the two dynamic schemes produced a tag {@code 9F4B}. They share
     *  a layout and differ in what the terminal appends to the hash input. */
    public enum DynamicMode {
        /** Book 2 clause 6.5 — the trailing hash input is the DDOL data. */
        DDA,
        /** Book 2 clause 6.6 — the trailing hash input is the Unpredictable Number alone. */
        CDA
    }

    /**
     * Book 2 clause 6.5.2 or 6.6.2, recovered from tag {@code 9F4B}.
     *
     * <p>The four CDA fields are {@code null} under {@link DynamicMode#DDA},
     * where the ICC Dynamic Data carries only the ICC Dynamic Number.</p>
     */
    public record DynamicApplicationData(DynamicMode mode,
                                         String iccDynamicData,
                                         String iccDynamicNumber,
                                         String cryptogramInformationData,
                                         String applicationCryptogram,
                                         String transactionDataHashCode,
                                         int hashAlgorithmIndicator,
                                         int padLength,
                                         String recoveredHex,
                                         List<Finding> findings) implements Recovered {
    }

    /** The static data to be authenticated, plus what was odd about assembling it. */
    public record StaticData(String hex, List<Finding> findings) {
    }

    /** An issued certificate: the tag, and the two data objects that go with it. */
    public record IssuedCertificate(String certificate, String remainder, String exponent) {

        /** Whether the key was too long to fit in the certificate and spilled into a remainder. */
        public boolean hasRemainder() {
            return !remainder.isEmpty();
        }
    }

    // =====================================================================
    // Recovery — the primitive
    // =====================================================================

    /**
     * Book 2 Annex A2.1: raises the signature to the public exponent and returns
     * the block at exactly the modulus length.
     *
     * <p>This is a raw modular exponentiation, not a JCE signature verification.
     * Going through {@code Cipher} would strip or reject the ISO 9796-2 framing
     * that is the entire subject here.</p>
     */
    public static String recover(String signature, RsaPublicKey key) {
        String normalized = normalizeHex(signature, "Signature");
        int n = key.modulusLength();
        if (normalized.length() / 2 != n) {
            throw new IllegalArgumentException("Book 2 Annex A2.1: the signature is "
                    + (normalized.length() / 2) + " bytes but the modulus is " + n
                    + "; recovery is only defined when they are equal");
        }
        BigInteger value = new BigInteger(1, bytes(normalized));
        if (value.compareTo(key.modulus()) >= 0) {
            throw new IllegalArgumentException("The signature is numerically larger than the modulus, "
                    + "so it was not produced with this key");
        }
        return hex(fixedLength(value.modPow(key.exponent(), key.modulus()), n));
    }

    /** The signing counterpart of {@link #recover}: a raw {@code m^d mod n}. */
    private static String sign(byte[] block, RsaPrivateKey key) {
        int n = key.modulusLength();
        if (block.length != n) {
            throw new IllegalStateException("Built a " + block.length + "-byte block for a " + n + "-byte modulus");
        }
        BigInteger value = new BigInteger(1, block);
        if (value.compareTo(key.modulus()) >= 0) {
            throw new IllegalArgumentException("The block does not fit under this modulus");
        }
        return hex(fixedLength(value.modPow(key.privateExponent(), key.modulus()), n));
    }

    // =====================================================================
    // Static data to be authenticated
    // =====================================================================

    /**
     * Book 3 clause 10.3, with Book 2 clause 5.1.1: the records the AFL points
     * at, in the order the terminal read them, followed by the AIP if — and only
     * if — the Static Data Authentication Tag List asks for it.
     *
     * <p>The tag list may contain nothing but {@code '82'}. Book 2 clauses 5.4
     * step 5 and 6.4 step 5 both say authentication has failed otherwise, which
     * is the only sanctioned use of an otherwise open-looking list.</p>
     *
     * @param records the record values already stripped of their template where
     *                Book 3 requires it, since that decision belongs to whoever
     *                read the card
     */
    public static StaticData staticDataToBeAuthenticated(List<String> records, String aip, String sdaTagList) {
        List<Finding> findings = new ArrayList<>();
        StringBuilder data = new StringBuilder();
        for (String record : records == null ? List.<String>of() : records) {
            if (record == null || record.isBlank()) {
                continue;
            }
            data.append(normalizeHex(record, "AFL record"));
        }
        if (data.length() == 0) {
            findings.add(new Finding(WARNING, "Book 3, 10.3",
                    "No AFL records were supplied, so the static data is empty or the AIP alone"));
        }
        String tagList = normalizeHex(sdaTagList, "Static Data Authentication Tag List");
        if (!tagList.isEmpty()) {
            if (!"82".equals(tagList)) {
                findings.add(new Finding(ERROR, "Book 2, 5.4 step 5",
                        "The Static Data Authentication Tag List is '" + tagList
                                + "'; it shall contain tag '82' and nothing else"));
            }
            String aipValue = normalizeHex(aip, "Application Interchange Profile");
            if (aipValue.isEmpty()) {
                findings.add(new Finding(ERROR, "Book 2, 5.1.1",
                        "The tag list asks for the AIP but no AIP was supplied"));
            } else {
                data.append(aipValue);
                findings.add(new Finding(INFO, "Book 2, 5.1.1",
                        "The AIP is appended to the static data because tag '9F4A' is present"));
            }
        } else if (!normalizeHex(aip, "Application Interchange Profile").isEmpty()) {
            findings.add(new Finding(WARNING, "Book 2, 5.1.1",
                    "An AIP was supplied but there is no Static Data Authentication Tag List, "
                            + "so the AIP is not part of the static data"));
        }
        return new StaticData(data.toString(), List.copyOf(findings));
    }

    // =====================================================================
    // Issuer public key — Book 2 clause 5.3 / 6.3
    // =====================================================================

    /**
     * Recovers the Issuer Public Key from tag {@code 90} and rebuilds the
     * modulus from the certificate and, when the key was too long to fit, the
     * remainder in tag {@code 92}.
     *
     * @param pan the Application PAN, used for the issuer identifier check of
     *            clause 5.3 step 8; may be {@code null} to skip that step
     */
    public static IssuerCertificate recoverIssuerPublicKey(String certificate,
                                                           String remainder,
                                                           String exponent,
                                                           RsaPublicKey caPublicKey,
                                                           String pan) {
        Objects.requireNonNull(caPublicKey, "caPublicKey");
        int nCa = caPublicKey.modulusLength();
        String recoveredHex = recover(certificate, caPublicKey);
        byte[] recovered = bytes(recoveredHex);
        String remainderHex = normalizeHex(remainder, "Issuer public key remainder");
        String exponentHex = normalizeHex(exponent, "Issuer public key exponent");

        List<Finding> findings = new ArrayList<>();
        checkFraming(recovered, FORMAT_ISSUER_CERTIFICATE, "Book 2, 5.3", "the Issuer Public Key Certificate", findings);

        int leftmostLength = nCa - ISSUER_CERTIFICATE_OVERHEAD;
        if (leftmostLength <= 0) {
            throw new IllegalArgumentException("A " + nCa + "-byte CA modulus leaves no room for an issuer key; "
                    + "Book 2 needs more than " + ISSUER_CERTIFICATE_OVERHEAD + " bytes");
        }

        String issuerIdentifier = hex(Arrays.copyOfRange(recovered, 2, 6));
        String expiryDate = hex(Arrays.copyOfRange(recovered, 6, 8));
        String serialNumber = hex(Arrays.copyOfRange(recovered, 8, 11));
        int hashIndicator = recovered[11] & 0xFF;
        int keyAlgorithm = recovered[12] & 0xFF;
        int declaredKeyLength = recovered[13] & 0xFF;
        int declaredExponentLength = recovered[14] & 0xFF;
        byte[] leftmost = Arrays.copyOfRange(recovered, 15, 15 + leftmostLength);

        checkIndicators(hashIndicator, keyAlgorithm, "Book 2, 5.3 step 11", findings);
        checkExpiry(expiryDate, "Book 2, 5.3 step 9", findings);
        checkIssuerIdentifier(issuerIdentifier, pan, findings);
        checkExponent(exponentHex, declaredExponentLength, "Book 2, 5.3", "Issuer", findings);

        RsaPublicKey issuerKey = assembleKey(leftmost, remainderHex, exponentHex, declaredKeyLength,
                leftmostLength, nCa, "Issuer", "92", "Book 2, 5.3 step 12", findings);

        byte[] hashInput = concat(Arrays.copyOfRange(recovered, 1, nCa - 21), bytes(remainderHex), bytes(exponentHex));
        checkHash(hashInput, recovered, nCa, hashIndicator, "Book 2, 5.3 step 7",
                "the Issuer Public Key Certificate", findings);

        if (issuerKey != null && issuerKey.modulusLength() > nCa) {
            findings.add(new Finding(ERROR, "Book 2, Table 27",
                    "The issuer modulus is " + issuerKey.modulusLength() + " bytes and the CA modulus is "
                            + nCa + "; Book 2 requires N_I to be at most N_CA"));
        }

        return new IssuerCertificate(issuerIdentifier, expiryDate, serialNumber, hashIndicator, keyAlgorithm,
                declaredKeyLength, declaredExponentLength, issuerKey, recoveredHex, List.copyOf(findings));
    }

    // =====================================================================
    // ICC public key — Book 2 clause 6.4
    // =====================================================================

    /**
     * Recovers the ICC Public Key from tag {@code 9F46}.
     *
     * <p>Note the {@code staticDataToBeAuthenticated} argument: clause 6.4 step 5
     * folds the static data into this certificate's hash input. Supplying the
     * wrong static data fails here, one step before anyone is looking for it.</p>
     */
    public static IccCertificate recoverIccPublicKey(String certificate,
                                                     String remainder,
                                                     String exponent,
                                                     RsaPublicKey issuerPublicKey,
                                                     String staticDataToBeAuthenticated,
                                                     String pan) {
        Objects.requireNonNull(issuerPublicKey, "issuerPublicKey");
        int nI = issuerPublicKey.modulusLength();
        String recoveredHex = recover(certificate, issuerPublicKey);
        byte[] recovered = bytes(recoveredHex);
        String remainderHex = normalizeHex(remainder, "ICC public key remainder");
        String exponentHex = normalizeHex(exponent, "ICC public key exponent");
        String staticHex = normalizeHex(staticDataToBeAuthenticated, "Static data to be authenticated");

        List<Finding> findings = new ArrayList<>();
        checkFraming(recovered, FORMAT_ICC_CERTIFICATE, "Book 2, 6.4", "the ICC Public Key Certificate", findings);

        int leftmostLength = nI - ICC_CERTIFICATE_OVERHEAD;
        if (leftmostLength <= 0) {
            throw new IllegalArgumentException("A " + nI + "-byte issuer modulus leaves no room for an ICC key; "
                    + "Book 2 needs more than " + ICC_CERTIFICATE_OVERHEAD + " bytes");
        }

        String recoveredPan = hex(Arrays.copyOfRange(recovered, 2, 12));
        String expiryDate = hex(Arrays.copyOfRange(recovered, 12, 14));
        String serialNumber = hex(Arrays.copyOfRange(recovered, 14, 17));
        int hashIndicator = recovered[17] & 0xFF;
        int keyAlgorithm = recovered[18] & 0xFF;
        int declaredKeyLength = recovered[19] & 0xFF;
        int declaredExponentLength = recovered[20] & 0xFF;
        byte[] leftmost = Arrays.copyOfRange(recovered, 21, 21 + leftmostLength);

        checkIndicators(hashIndicator, keyAlgorithm, "Book 2, 6.4 step 10", findings);
        checkExpiry(expiryDate, "Book 2, 6.4 step 9", findings);
        checkPan(recoveredPan, pan, findings);
        checkExponent(exponentHex, declaredExponentLength, "Book 2, 6.4", "ICC", findings);

        if (staticHex.isEmpty()) {
            findings.add(new Finding(WARNING, "Book 2, 6.4 step 5",
                    "No static data to be authenticated was supplied; the hash below covers an empty tail, "
                            + "which is almost never what a real card signed"));
        }

        RsaPublicKey iccKey = assembleKey(leftmost, remainderHex, exponentHex, declaredKeyLength,
                leftmostLength, nI, "ICC", "9F48", "Book 2, 6.4 step 11", findings);

        byte[] hashInput = concat(Arrays.copyOfRange(recovered, 1, nI - 21),
                bytes(remainderHex), bytes(exponentHex), bytes(staticHex));
        checkHash(hashInput, recovered, nI, hashIndicator, "Book 2, 6.4 step 7",
                "the ICC Public Key Certificate", findings);

        if (iccKey != null && iccKey.modulusLength() > nI) {
            findings.add(new Finding(ERROR, "Book 2, Table 27",
                    "The ICC modulus is " + iccKey.modulusLength() + " bytes and the issuer modulus is "
                            + nI + "; Book 2 requires N_IC to be at most N_I"));
        }

        return new IccCertificate(recoveredPan, expiryDate, serialNumber, hashIndicator, keyAlgorithm,
                declaredKeyLength, declaredExponentLength, iccKey, recoveredHex, List.copyOf(findings));
    }

    // =====================================================================
    // SDA — Book 2 clause 5.4
    // =====================================================================

    /**
     * Verifies the Signed Static Application Data of tag {@code 93} against the
     * recovered Issuer Public Key.
     *
     * <p>A pass means the issuer signed this data. It does not mean a card is
     * present: the same bytes verify every time, from any source.</p>
     */
    public static StaticApplicationData verifyStaticApplicationData(String ssad,
                                                                    RsaPublicKey issuerPublicKey,
                                                                    String staticDataToBeAuthenticated) {
        Objects.requireNonNull(issuerPublicKey, "issuerPublicKey");
        int nI = issuerPublicKey.modulusLength();
        String recoveredHex = recover(ssad, issuerPublicKey);
        byte[] recovered = bytes(recoveredHex);
        String staticHex = normalizeHex(staticDataToBeAuthenticated, "Static data to be authenticated");

        List<Finding> findings = new ArrayList<>();
        checkFraming(recovered, FORMAT_STATIC_APPLICATION_DATA, "Book 2, 5.4",
                "the Signed Static Application Data", findings);

        int hashIndicator = recovered[2] & 0xFF;
        String dataAuthenticationCode = hex(Arrays.copyOfRange(recovered, 3, 5));
        int padLength = nI - STATIC_DATA_OVERHEAD;
        if (padLength < 0) {
            throw new IllegalArgumentException("A " + nI + "-byte issuer modulus is too small for an SSAD");
        }
        checkPadding(recovered, 5, padLength, "Book 2, 5.4", findings);
        if (hashIndicator != HASH_ALGORITHM_SHA1) {
            findings.add(new Finding(ERROR, "Book 2, Annex B",
                    "Hash algorithm indicator '" + byteHex(hashIndicator) + "' is not defined in EMV"));
        }

        byte[] hashInput = concat(Arrays.copyOfRange(recovered, 1, nI - 21), bytes(staticHex));
        checkHash(hashInput, recovered, nI, hashIndicator, "Book 2, 5.4 step 7",
                "the Signed Static Application Data", findings);

        findings.add(new Finding(INFO, "Book 2, 5.4",
                "The Data Authentication Code '" + dataAuthenticationCode + "' belongs in tag '9F45'"));
        findings.add(new Finding(INFO, null,
                "SDA authenticates data, not the card: the signature is constant, so it can be replayed "
                        + "by anything that can read it"));

        return new StaticApplicationData(dataAuthenticationCode, hashIndicator, padLength,
                recoveredHex, List.copyOf(findings));
    }

    // =====================================================================
    // DDA — Book 2 clause 6.5.2
    // =====================================================================

    /**
     * Verifies a DDA dynamic signature (tag {@code 9F4B}) against the recovered
     * ICC Public Key.
     *
     * @param terminalDynamicData the concatenated DDOL values the terminal sent
     *                            in INTERNAL AUTHENTICATE. Book 2 clause 6.5.1
     *                            requires the DDOL to include the Unpredictable
     *                            Number (tag {@code 9F37}); without it the card
     *                            is signing something it could have signed
     *                            yesterday.
     */
    public static DynamicApplicationData verifyDynamicApplicationData(String sdad,
                                                                      RsaPublicKey iccPublicKey,
                                                                      String terminalDynamicData) {
        List<Finding> findings = new ArrayList<>();
        String terminalHex = normalizeHex(terminalDynamicData, "Terminal dynamic data");
        if (terminalHex.isEmpty()) {
            findings.add(new Finding(WARNING, "Book 2, 6.5.1",
                    "No DDOL data was supplied; a DDOL must contain the Unpredictable Number, "
                            + "or the signature is not bound to this transaction"));
        }
        Parsed parsed = parseDynamic(sdad, iccPublicKey, terminalHex, "Book 2, 6.5.2", findings);
        String dynamicNumber = readDynamicNumber(parsed.iccDynamicData(), findings);
        return new DynamicApplicationData(DynamicMode.DDA, hex(parsed.iccDynamicData()), dynamicNumber,
                null, null, null, parsed.hashIndicator(), parsed.padLength(), parsed.recoveredHex(),
                List.copyOf(findings));
    }

    // =====================================================================
    // CDA — Book 2 clause 6.6.2
    // =====================================================================

    /**
     * Verifies a CDA dynamic signature (tag {@code 9F4B}) and unpacks the
     * Application Cryptogram that CDA binds into it.
     *
     * <p>Only the Unpredictable Number is appended to the hash input — not the
     * DDOL data. The transaction is bound in differently: the card hashes the
     * whole exchange into a Transaction Data Hash Code inside the signature.</p>
     *
     * @param cryptogramInformationData the cleartext CID from the GENERATE AC
     *                                  response, for the cross-check of clause
     *                                  6.6.2 step 6; {@code null} to skip it
     * @param transactionData           the concatenation of clause 6.6.2 step 10 —
     *                                  PDOL values, CDOL1 values, then the tags,
     *                                  lengths and values the card returned except
     *                                  the signature itself; {@code null} to skip
     *                                  the Transaction Data Hash Code check
     */
    public static DynamicApplicationData verifyCombinedApplicationData(String sdad,
                                                                       RsaPublicKey iccPublicKey,
                                                                       String unpredictableNumber,
                                                                       String cryptogramInformationData,
                                                                       String transactionData) {
        List<Finding> findings = new ArrayList<>();
        String unHex = normalizeHex(unpredictableNumber, "Unpredictable Number");
        if (unHex.length() != 8) {
            findings.add(new Finding(ERROR, "Book 2, 6.6.2 step 7",
                    "The Unpredictable Number is " + (unHex.length() / 2) + " bytes; tag '9F37' is 4 bytes, "
                            + "and it is the only thing appended to a CDA hash input"));
        }
        Parsed parsed = parseDynamic(sdad, iccPublicKey, unHex, "Book 2, 6.6.2", findings);
        byte[] dynamic = parsed.iccDynamicData();

        String dynamicNumber = null;
        String cid = null;
        String cryptogram = null;
        String hashCode = null;
        if (dynamic.length < 32) {
            findings.add(new Finding(ERROR, "Book 2, Table 18",
                    "CDA needs at least 32 bytes of ICC Dynamic Data — dynamic number, CID, cryptogram and "
                            + "Transaction Data Hash Code — but there are " + dynamic.length));
        } else {
            int numberLength = dynamic[0] & 0xFF;
            if (numberLength < 2 || numberLength > 8 || 1 + numberLength + 29 > dynamic.length) {
                findings.add(new Finding(ERROR, "Book 2, Table 18",
                        "The ICC Dynamic Number length is " + numberLength + "; Book 2 allows 2 to 8"));
            } else {
                int at = 1;
                dynamicNumber = hex(Arrays.copyOfRange(dynamic, at, at + numberLength));
                at += numberLength;
                cid = byteHex(dynamic[at] & 0xFF);
                at += 1;
                cryptogram = hex(Arrays.copyOfRange(dynamic, at, at + 8));
                at += 8;
                hashCode = hex(Arrays.copyOfRange(dynamic, at, at + HASH_BYTES));

                String expectedCid = normalizeHex(cryptogramInformationData, "Cryptogram Information Data");
                if (!expectedCid.isEmpty() && !expectedCid.equals(cid)) {
                    findings.add(new Finding(ERROR, "Book 2, 6.6.2 step 6",
                            "The CID inside the signature is '" + cid + "' but the response carried '"
                                    + expectedCid + "'; the card is signing a different decision from the one "
                                    + "it announced"));
                }
                findings.add(new Finding(INFO, "Book 2, Table 18",
                        "Cryptogram type: " + cryptogramType(cid)));

                String transactionHex = normalizeHex(transactionData, "Transaction data");
                if (transactionHex.isEmpty()) {
                    findings.add(new Finding(WARNING, "Book 2, 6.6.2 step 10",
                            "No transaction data was supplied, so the Transaction Data Hash Code was not checked; "
                                    + "the signature is verified but not yet tied to this transaction's contents"));
                } else {
                    String computed = hex(sha1(bytes(transactionHex)));
                    if (computed.equals(hashCode)) {
                        findings.add(new Finding(INFO, "Book 2, 6.6.2 step 12",
                                "The Transaction Data Hash Code matches the supplied transaction data"));
                    } else {
                        findings.add(new Finding(ERROR, "Book 2, 6.6.2 step 12",
                                "The Transaction Data Hash Code is '" + hashCode + "' but the supplied "
                                        + "transaction data hashes to '" + computed + "'"));
                    }
                }
            }
        }

        return new DynamicApplicationData(DynamicMode.CDA, hex(dynamic), dynamicNumber, cid, cryptogram,
                hashCode, parsed.hashIndicator(), parsed.padLength(), parsed.recoveredHex(), List.copyOf(findings));
    }

    private record Parsed(byte[] iccDynamicData, int hashIndicator, int padLength, String recoveredHex) {
    }

    private static Parsed parseDynamic(String sdad, RsaPublicKey iccPublicKey, String trailing,
                                       String clause, List<Finding> findings) {
        Objects.requireNonNull(iccPublicKey, "iccPublicKey");
        int nIc = iccPublicKey.modulusLength();
        String recoveredHex = recover(sdad, iccPublicKey);
        byte[] recovered = bytes(recoveredHex);

        checkFraming(recovered, FORMAT_DYNAMIC_APPLICATION_DATA, clause,
                "the Signed Dynamic Application Data", findings);

        int hashIndicator = recovered[2] & 0xFF;
        if (hashIndicator != HASH_ALGORITHM_SHA1) {
            findings.add(new Finding(ERROR, "Book 2, Annex B",
                    "Hash algorithm indicator '" + byteHex(hashIndicator) + "' is not defined in EMV"));
        }
        int dynamicLength = recovered[3] & 0xFF;
        int maximum = nIc - 25;
        if (dynamicLength > maximum) {
            throw new IllegalArgumentException("The recovered ICC Dynamic Data Length is " + dynamicLength
                    + " but a " + nIc + "-byte modulus allows at most " + maximum
                    + "; this is not a signature made with this key");
        }
        byte[] dynamic = Arrays.copyOfRange(recovered, 4, 4 + dynamicLength);
        int padLength = nIc - dynamicLength - DYNAMIC_DATA_OVERHEAD;
        checkPadding(recovered, 4 + dynamicLength, padLength, clause, findings);

        byte[] hashInput = concat(Arrays.copyOfRange(recovered, 1, nIc - 21), bytes(trailing));
        checkHash(hashInput, recovered, nIc, hashIndicator, clause,
                "the Signed Dynamic Application Data", findings);

        return new Parsed(dynamic, hashIndicator, padLength, recoveredHex);
    }

    private static String readDynamicNumber(byte[] dynamic, List<Finding> findings) {
        if (dynamic.length < 3) {
            findings.add(new Finding(WARNING, "Book 2, 6.5.1",
                    "The ICC Dynamic Data is " + dynamic.length + " bytes; Book 2 expects a 1-byte length "
                            + "followed by a 2 to 8 byte ICC Dynamic Number"));
            return null;
        }
        int length = dynamic[0] & 0xFF;
        if (length < 2 || length > 8 || 1 + length > dynamic.length) {
            findings.add(new Finding(WARNING, "Book 2, 6.5.1",
                    "The ICC Dynamic Number declares " + length + " bytes; Book 2 allows 2 to 8"));
            return null;
        }
        String number = hex(Arrays.copyOfRange(dynamic, 1, 1 + length));
        findings.add(new Finding(INFO, "Book 2, 6.5.2",
                "The ICC Dynamic Number '" + number + "' belongs in tag '9F4C'"));
        return number;
    }

    // =====================================================================
    // Signing — the other half, so there is something to verify
    // =====================================================================

    /** Book 2 clause 5.1 Table 2: signs an Issuer Public Key with the CA private key. */
    public static IssuedCertificate signIssuerCertificate(RsaPrivateKey caPrivateKey,
                                                          RsaPublicKey issuerPublicKey,
                                                          String issuerIdentifier,
                                                          String expiryDate,
                                                          String serialNumber) {
        Objects.requireNonNull(caPrivateKey, "caPrivateKey");
        Objects.requireNonNull(issuerPublicKey, "issuerPublicKey");
        int nCa = caPrivateKey.modulusLength();
        int nI = issuerPublicKey.modulusLength();
        int leftmostLength = nCa - ISSUER_CERTIFICATE_OVERHEAD;
        requireRoom(leftmostLength, nCa, ISSUER_CERTIFICATE_OVERHEAD, "issuer");
        if (nI > nCa) {
            throw new IllegalArgumentException("Book 2 Table 27 requires the issuer modulus to be at most the "
                    + "CA modulus, but they are " + nI + " and " + nCa + " bytes");
        }

        byte[] head = concat(
                new byte[] {(byte) FORMAT_ISSUER_CERTIFICATE},
                fixed(issuerIdentifier, 4, "Issuer identifier", (byte) 0xFF),
                fixed(expiryDate, 2, "Certificate expiration date", (byte) 0x00),
                fixed(serialNumber, 3, "Certificate serial number", (byte) 0x00),
                new byte[] {(byte) HASH_ALGORITHM_SHA1, (byte) PUBLIC_KEY_ALGORITHM_RSA,
                        (byte) nI, (byte) issuerPublicKey.exponentLength()});

        Split split = split(bytes(issuerPublicKey.modulusHex()), leftmostLength);
        byte[] exponent = bytes(issuerPublicKey.exponentHex());
        byte[] hash = sha1(concat(head, split.leftmost(), split.remainder(), exponent));
        byte[] block = concat(new byte[] {(byte) RECOVERED_HEADER}, head, split.leftmost(), hash,
                new byte[] {(byte) RECOVERED_TRAILER});
        return new IssuedCertificate(sign(block, caPrivateKey), hex(split.remainder()),
                issuerPublicKey.exponentHex());
    }

    /**
     * Book 2 clause 6.1 Table 10: signs an ICC Public Key with the issuer private
     * key. The static data goes into the hash, exactly as clause 6.4 step 5 will
     * expect when a terminal recovers it.
     */
    public static IssuedCertificate signIccCertificate(RsaPrivateKey issuerPrivateKey,
                                                       RsaPublicKey iccPublicKey,
                                                       String pan,
                                                       String expiryDate,
                                                       String serialNumber,
                                                       String staticDataToBeAuthenticated) {
        Objects.requireNonNull(issuerPrivateKey, "issuerPrivateKey");
        Objects.requireNonNull(iccPublicKey, "iccPublicKey");
        int nI = issuerPrivateKey.modulusLength();
        int nIc = iccPublicKey.modulusLength();
        int leftmostLength = nI - ICC_CERTIFICATE_OVERHEAD;
        requireRoom(leftmostLength, nI, ICC_CERTIFICATE_OVERHEAD, "ICC");
        if (nIc > nI) {
            throw new IllegalArgumentException("Book 2 Table 27 requires the ICC modulus to be at most the "
                    + "issuer modulus, but they are " + nIc + " and " + nI + " bytes");
        }

        byte[] head = concat(
                new byte[] {(byte) FORMAT_ICC_CERTIFICATE},
                fixed(pan, 10, "Application PAN", (byte) 0xFF),
                fixed(expiryDate, 2, "Certificate expiration date", (byte) 0x00),
                fixed(serialNumber, 3, "Certificate serial number", (byte) 0x00),
                new byte[] {(byte) HASH_ALGORITHM_SHA1, (byte) PUBLIC_KEY_ALGORITHM_RSA,
                        (byte) nIc, (byte) iccPublicKey.exponentLength()});

        Split split = split(bytes(iccPublicKey.modulusHex()), leftmostLength);
        byte[] exponent = bytes(iccPublicKey.exponentHex());
        byte[] staticData = bytes(normalizeHex(staticDataToBeAuthenticated, "Static data to be authenticated"));
        byte[] hash = sha1(concat(head, split.leftmost(), split.remainder(), exponent, staticData));
        byte[] block = concat(new byte[] {(byte) RECOVERED_HEADER}, head, split.leftmost(), hash,
                new byte[] {(byte) RECOVERED_TRAILER});
        return new IssuedCertificate(sign(block, issuerPrivateKey), hex(split.remainder()),
                iccPublicKey.exponentHex());
    }

    /** Book 2 clause 5.1 Table 3: the issuer signs the card's static data. Produces tag {@code 93}. */
    public static String signStaticApplicationData(RsaPrivateKey issuerPrivateKey,
                                                   String dataAuthenticationCode,
                                                   String staticDataToBeAuthenticated) {
        Objects.requireNonNull(issuerPrivateKey, "issuerPrivateKey");
        int nI = issuerPrivateKey.modulusLength();
        int padLength = nI - STATIC_DATA_OVERHEAD;
        requireRoom(padLength + 1, nI, STATIC_DATA_OVERHEAD, "static application data");

        byte[] head = concat(
                new byte[] {(byte) FORMAT_STATIC_APPLICATION_DATA, (byte) HASH_ALGORITHM_SHA1},
                fixed(dataAuthenticationCode, 2, "Data Authentication Code", (byte) 0x00));
        byte[] pad = padding(padLength);
        byte[] staticData = bytes(normalizeHex(staticDataToBeAuthenticated, "Static data to be authenticated"));
        byte[] hash = sha1(concat(head, pad, staticData));
        byte[] block = concat(new byte[] {(byte) RECOVERED_HEADER}, head, pad, hash,
                new byte[] {(byte) RECOVERED_TRAILER});
        return sign(block, issuerPrivateKey);
    }

    /**
     * Book 2 clause 6.5.1 Table 14 and clause 6.6.1 Table 17: the card signs its
     * dynamic data. Produces tag {@code 9F4B}.
     *
     * @param terminalDynamicData the DDOL data for DDA, or the 4-byte
     *                            Unpredictable Number alone for CDA
     */
    public static String signDynamicApplicationData(RsaPrivateKey iccPrivateKey,
                                                    String iccDynamicData,
                                                    String terminalDynamicData) {
        Objects.requireNonNull(iccPrivateKey, "iccPrivateKey");
        int nIc = iccPrivateKey.modulusLength();
        byte[] dynamic = bytes(normalizeHex(iccDynamicData, "ICC Dynamic Data"));
        if (dynamic.length > 255) {
            throw new IllegalArgumentException("The ICC Dynamic Data Length is one byte, so the data cannot "
                    + "exceed 255 bytes; this is " + dynamic.length);
        }
        int padLength = nIc - dynamic.length - DYNAMIC_DATA_OVERHEAD;
        if (padLength < 0) {
            throw new IllegalArgumentException("A " + nIc + "-byte ICC modulus leaves room for at most "
                    + (nIc - 25) + " bytes of ICC Dynamic Data, not " + dynamic.length);
        }

        byte[] head = concat(
                new byte[] {(byte) FORMAT_DYNAMIC_APPLICATION_DATA, (byte) HASH_ALGORITHM_SHA1,
                        (byte) dynamic.length},
                dynamic);
        byte[] pad = padding(padLength);
        byte[] trailing = bytes(normalizeHex(terminalDynamicData, "Terminal dynamic data"));
        byte[] hash = sha1(concat(head, pad, trailing));
        byte[] block = concat(new byte[] {(byte) RECOVERED_HEADER}, head, pad, hash,
                new byte[] {(byte) RECOVERED_TRAILER});
        return sign(block, iccPrivateKey);
    }

    /**
     * Book 2 Table 18: the leftmost bytes of the ICC Dynamic Data under CDA.
     *
     * @param transactionDataHashCode the 20-byte hash of clause 6.6.1 — use
     *                                {@link #transactionDataHashCode(String)} to
     *                                compute it from the concatenated exchange
     */
    public static String combinedDynamicData(String iccDynamicNumber,
                                             String cryptogramInformationData,
                                             String applicationCryptogram,
                                             String transactionDataHashCode) {
        String number = normalizeHex(iccDynamicNumber, "ICC Dynamic Number");
        int length = number.length() / 2;
        if (length < 2 || length > 8) {
            throw new IllegalArgumentException("Book 2 Table 18 allows an ICC Dynamic Number of 2 to 8 bytes, "
                    + "not " + length);
        }
        return byteHex(length)
                + number
                + hex(fixed(cryptogramInformationData, 1, "Cryptogram Information Data", (byte) 0x00))
                + hex(fixed(applicationCryptogram, 8, "Application Cryptogram", (byte) 0x00))
                + hex(fixed(transactionDataHashCode, HASH_BYTES, "Transaction Data Hash Code", (byte) 0x00));
    }

    /** Book 2 clause 6.6.1: SHA-1 over the concatenated transaction exchange. */
    public static String transactionDataHashCode(String transactionData) {
        return hex(sha1(bytes(normalizeHex(transactionData, "Transaction data"))));
    }

    // =====================================================================
    // Reporting
    // =====================================================================

    public static String describe(IssuerCertificate certificate) {
        StringBuilder report = new StringBuilder();
        report.append("Issuer Public Key Certificate (tag '90')\n");
        report.append("  issuer identifier : ").append(certificate.issuerIdentifier()).append('\n');
        report.append("  expiry (MMYY)     : ").append(certificate.expiryDate()).append('\n');
        report.append("  serial number     : ").append(certificate.serialNumber()).append('\n');
        report.append("  hash / key algo   : ").append(byteHex(certificate.hashAlgorithmIndicator()))
                .append(" / ").append(byteHex(certificate.publicKeyAlgorithmIndicator())).append('\n');
        report.append("  declared key      : ").append(certificate.declaredKeyLength()).append(" bytes, exponent ")
                .append(certificate.declaredExponentLength()).append(" bytes\n");
        appendKey(report, certificate.issuerPublicKey());
        appendFindings(report, certificate.findings(), certificate.passed());
        return report.toString();
    }

    public static String describe(IccCertificate certificate) {
        StringBuilder report = new StringBuilder();
        report.append("ICC Public Key Certificate (tag '9F46')\n");
        report.append("  recovered PAN     : ").append(certificate.pan()).append('\n');
        report.append("  expiry (MMYY)     : ").append(certificate.expiryDate()).append('\n');
        report.append("  serial number     : ").append(certificate.serialNumber()).append('\n');
        report.append("  hash / key algo   : ").append(byteHex(certificate.hashAlgorithmIndicator()))
                .append(" / ").append(byteHex(certificate.publicKeyAlgorithmIndicator())).append('\n');
        report.append("  declared key      : ").append(certificate.declaredKeyLength()).append(" bytes, exponent ")
                .append(certificate.declaredExponentLength()).append(" bytes\n");
        appendKey(report, certificate.iccPublicKey());
        appendFindings(report, certificate.findings(), certificate.passed());
        return report.toString();
    }

    public static String describe(StaticApplicationData data) {
        StringBuilder report = new StringBuilder();
        report.append("Signed Static Application Data (tag '93')\n");
        report.append("  data authentication code : ").append(data.dataAuthenticationCode()).append('\n');
        report.append("  hash algorithm indicator : ").append(byteHex(data.hashAlgorithmIndicator())).append('\n');
        report.append("  pad pattern              : ").append(data.padLength()).append(" bytes of 'BB'\n");
        appendFindings(report, data.findings(), data.passed());
        return report.toString();
    }

    public static String describe(DynamicApplicationData data) {
        StringBuilder report = new StringBuilder();
        report.append("Signed Dynamic Application Data (tag '9F4B'), ").append(data.mode()).append('\n');
        report.append("  ICC dynamic data    : ").append(data.iccDynamicData()).append('\n');
        if (data.iccDynamicNumber() != null) {
            report.append("  ICC dynamic number  : ").append(data.iccDynamicNumber()).append('\n');
        }
        if (data.mode() == DynamicMode.CDA) {
            report.append("  cryptogram info     : ").append(String.valueOf(data.cryptogramInformationData()))
                    .append('\n');
            report.append("  application crypt.  : ").append(String.valueOf(data.applicationCryptogram()))
                    .append('\n');
            report.append("  transaction hash    : ").append(String.valueOf(data.transactionDataHashCode()))
                    .append('\n');
        }
        report.append("  pad pattern         : ").append(data.padLength()).append(" bytes of 'BB'\n");
        appendFindings(report, data.findings(), data.passed());
        return report.toString();
    }

    private static void appendKey(StringBuilder report, RsaPublicKey key) {
        if (key == null) {
            report.append("  recovered key     : could not be reassembled\n");
            return;
        }
        report.append("  recovered modulus : ").append(key.modulusLength()).append(" bytes\n");
        report.append("    ").append(key.modulusHex()).append('\n');
        report.append("  recovered exponent: ").append(key.exponentHex()).append('\n');
    }

    private static void appendFindings(StringBuilder report, List<Finding> findings, boolean passed) {
        report.append("  verdict           : ").append(passed ? "PASSED" : "FAILED").append('\n');
        if (findings.isEmpty()) {
            return;
        }
        report.append("\nFindings\n");
        for (Finding finding : findings) {
            report.append("  [").append(finding.severity()).append(']');
            if (finding.clause() != null) {
                report.append(' ').append(finding.clause()).append(':');
            }
            report.append(' ').append(finding.message()).append('\n');
        }
    }

    // =====================================================================
    // Checks
    // =====================================================================

    private static void checkFraming(byte[] recovered, int expectedFormat, String clause,
                                     String what, List<Finding> findings) {
        int header = recovered[0] & 0xFF;
        int trailer = recovered[recovered.length - 1] & 0xFF;
        int format = recovered[1] & 0xFF;
        if (header != RECOVERED_HEADER) {
            findings.add(new Finding(ERROR, clause, "The recovered data header is '" + byteHex(header)
                    + "', not '6A'; " + what + " was not signed by this key"));
        }
        if (trailer != RECOVERED_TRAILER) {
            findings.add(new Finding(ERROR, clause, "The recovered data trailer is '" + byteHex(trailer)
                    + "', not 'BC'; " + what + " was not signed by this key"));
        }
        if (format != expectedFormat) {
            findings.add(new Finding(ERROR, clause, "The format byte is '" + byteHex(format) + "' but "
                    + what + " must carry '" + byteHex(expectedFormat) + "'"));
        }
    }

    private static void checkIndicators(int hashIndicator, int keyAlgorithm, String clause, List<Finding> findings) {
        if (hashIndicator != HASH_ALGORITHM_SHA1) {
            findings.add(new Finding(ERROR, "Book 2, Annex B", "Hash algorithm indicator '"
                    + byteHex(hashIndicator) + "' is not defined in EMV, which assigns '01' to SHA-1"));
        }
        if (keyAlgorithm != PUBLIC_KEY_ALGORITHM_RSA) {
            findings.add(new Finding(ERROR, clause, "Public key algorithm indicator '" + byteHex(keyAlgorithm)
                    + "' is not recognised; EMV Annex B2.1 assigns '01' to RSA"));
        }
    }

    private static void checkExpiry(String expiryDate, String clause, List<Finding> findings) {
        YearMonth expiry = parseExpiry(expiryDate);
        if (expiry == null) {
            findings.add(new Finding(ERROR, clause, "The certificate expiration date '" + expiryDate
                    + "' is not a valid MMYY"));
            return;
        }
        YearMonth now = YearMonth.now();
        if (expiry.isBefore(now)) {
            findings.add(new Finding(ERROR, clause, "The certificate expired at the end of " + expiry));
        } else {
            findings.add(new Finding(INFO, clause, "The certificate is valid through the end of " + expiry));
        }
    }

    /** MMYY carries no century, so the two digits are read into a window around today. */
    private static YearMonth parseExpiry(String expiryDate) {
        if (expiryDate.length() != 4 || !expiryDate.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        int month = Integer.parseInt(expiryDate.substring(0, 2));
        int year = Integer.parseInt(expiryDate.substring(2, 4));
        if (month < 1 || month > 12) {
            return null;
        }
        int thisYear = YearMonth.now().getYear();
        int full = (thisYear / 100) * 100 + year;
        if (full < thisYear - 50) {
            full += 100;
        }
        return YearMonth.of(full, month);
    }

    private static void checkIssuerIdentifier(String issuerIdentifier, String pan, List<Finding> findings) {
        String digits = normalizeHex(pan, "PAN").replace("F", "");
        if (digits.isEmpty()) {
            findings.add(new Finding(INFO, "Book 2, 5.3 step 8",
                    "No PAN was supplied, so the issuer identifier was not checked against it"));
            return;
        }
        String prefix = issuerIdentifier.replaceAll("F+$", "");
        if (prefix.length() < 3) {
            findings.add(new Finding(ERROR, "Book 2, 5.3 step 8",
                    "The issuer identifier '" + issuerIdentifier + "' unpads to fewer than 3 digits"));
            return;
        }
        if (!digits.startsWith(prefix)) {
            findings.add(new Finding(ERROR, "Book 2, 5.3 step 8",
                    "The issuer identifier '" + prefix + "' is not a prefix of the PAN"));
        } else {
            findings.add(new Finding(INFO, "Book 2, 5.3 step 8",
                    "The issuer identifier '" + prefix + "' matches the leading PAN digits"));
        }
    }

    private static void checkPan(String recoveredPan, String pan, List<Finding> findings) {
        String supplied = normalizeHex(pan, "PAN");
        if (supplied.isEmpty()) {
            findings.add(new Finding(INFO, "Book 2, 6.4 step 8",
                    "No Application PAN was supplied, so the recovered PAN was not checked against it"));
            return;
        }
        String left = recoveredPan.replaceAll("F+$", "");
        String right = supplied.replaceAll("F+$", "");
        if (!left.equals(right)) {
            findings.add(new Finding(ERROR, "Book 2, 6.4 step 8",
                    "The recovered PAN is '" + left + "' but the card's Application PAN is '" + right + "'"));
        } else {
            findings.add(new Finding(INFO, "Book 2, 6.4 step 8",
                    "The recovered PAN matches the card's Application PAN"));
        }
    }

    private static void checkExponent(String exponentHex, int declaredLength, String clause,
                                      String whose, List<Finding> findings) {
        if (exponentHex.isEmpty()) {
            findings.add(new Finding(ERROR, clause, "The " + whose + " Public Key Exponent is missing. It is "
                    + "part of the hash input, so the certificate cannot verify without it"));
            return;
        }
        int length = exponentHex.length() / 2;
        if (length != declaredLength) {
            findings.add(new Finding(ERROR, clause, "The certificate declares a " + declaredLength
                    + "-byte exponent but " + length + " bytes were supplied"));
        }
        BigInteger value = new BigInteger(1, bytes(exponentHex));
        if (!value.equals(EXPONENT_3) && !value.equals(EXPONENT_F4)) {
            findings.add(new Finding(ERROR, "Book 2, Annex B2.1", "The exponent is " + value
                    + "; EMV allows only 3 and 65537"));
        }
    }

    private static void checkPadding(byte[] recovered, int offset, int length, String clause,
                                     List<Finding> findings) {
        for (int i = offset; i < offset + length; i++) {
            if (recovered[i] != PAD) {
                findings.add(new Finding(WARNING, clause, "The pad pattern is not all 'BB' from offset "
                        + offset + "; the recovered block may not be what it claims"));
                return;
            }
        }
    }

    private static void checkHash(byte[] hashInput, byte[] recovered, int modulusLength,
                                  int hashIndicator, String clause, String what, List<Finding> findings) {
        byte[] recoveredHash = Arrays.copyOfRange(recovered, modulusLength - 21, modulusLength - 1);
        byte[] computed = sha1(hashInput);
        if (Arrays.equals(recoveredHash, computed)) {
            findings.add(new Finding(INFO, clause, "The hash over " + what + " matches"));
            return;
        }
        String extra = hashIndicator == HASH_ALGORITHM_SHA1 ? ""
                : " (computed with SHA-1, since indicator '" + byteHex(hashIndicator) + "' names no algorithm)";
        findings.add(new Finding(ERROR, clause, "The hash over " + what + " does not match: recovered '"
                + hex(recoveredHash) + "', computed '" + hex(computed) + "'" + extra));
    }

    private record Split(byte[] leftmost, byte[] remainder) {
    }

    /** Book 2: the key fills the certificate and spills into a remainder only if it must. */
    private static Split split(byte[] modulus, int leftmostLength) {
        if (modulus.length <= leftmostLength) {
            byte[] leftmost = new byte[leftmostLength];
            Arrays.fill(leftmost, PAD);
            System.arraycopy(modulus, 0, leftmost, 0, modulus.length);
            return new Split(leftmost, new byte[0]);
        }
        return new Split(Arrays.copyOfRange(modulus, 0, leftmostLength),
                Arrays.copyOfRange(modulus, leftmostLength, modulus.length));
    }

    private static RsaPublicKey assembleKey(byte[] leftmost, String remainderHex, String exponentHex,
                                            int declaredLength, int leftmostLength, int outerLength,
                                            String whose, String remainderTag, String clause,
                                            List<Finding> findings) {
        if (declaredLength == 0) {
            findings.add(new Finding(ERROR, clause, "The certificate declares a zero-length " + whose
                    + " Public Key"));
            return null;
        }
        if (declaredLength > MAXIMUM_MODULUS_BYTES) {
            findings.add(new Finding(ERROR, "Book 2, Table 27", "The certificate declares a " + declaredLength
                    + "-byte " + whose + " modulus; EMV caps every modulus at " + MAXIMUM_MODULUS_BYTES));
        }
        byte[] remainder = bytes(remainderHex);
        String modulusHex;
        if (declaredLength <= leftmostLength) {
            if (remainder.length > 0) {
                findings.add(new Finding(ERROR, clause, "The " + whose + " key is " + declaredLength
                        + " bytes and fits in the certificate, so tag '" + remainderTag
                        + "' must be absent, but " + remainder.length + " bytes were supplied"));
            }
            for (int i = declaredLength; i < leftmostLength; i++) {
                if (leftmost[i] != PAD) {
                    findings.add(new Finding(WARNING, clause, "The bytes after the " + whose
                            + " key are not all 'BB'; the declared key length may be wrong"));
                    break;
                }
            }
            modulusHex = hex(Arrays.copyOfRange(leftmost, 0, declaredLength));
        } else {
            int expected = declaredLength - leftmostLength;
            if (remainder.length != expected) {
                findings.add(new Finding(ERROR, clause, "The " + whose + " key is " + declaredLength
                        + " bytes, so tag '" + remainderTag + "' must hold the remaining " + expected
                        + " bytes, but it holds " + remainder.length
                        + " (the certificate carries " + leftmostLength + " of a " + outerLength
                        + "-byte block)"));
                return null;
            }
            modulusHex = hex(leftmost) + hex(remainder);
        }
        if (exponentHex.isEmpty()) {
            return null;
        }
        return new RsaPublicKey(modulusHex, exponentHex);
    }

    private static void requireRoom(int available, int modulusLength, int overhead, String what) {
        if (available <= 0) {
            throw new IllegalArgumentException("A " + modulusLength + "-byte modulus leaves no room for an "
                    + what + " signature; EMV spends " + overhead + " bytes on framing alone");
        }
    }

    private static String cryptogramType(String cid) {
        int value = (Integer.parseInt(cid, 16) & 0xC0) >> 6;
        return switch (value) {
            case 0 -> "AAC, declined. Book 2 clause 6.6.2 says CDA has failed if the card answers with an AAC";
            case 1 -> "TC, approved offline";
            case 2 -> "ARQC, online authorisation requested";
            default -> "AAR, reserved; a CDA response should carry no signature at all";
        };
    }

    // =====================================================================
    // Bytes
    // =====================================================================

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by EMV Book 2 Annex B and is not available", e);
        }
    }

    private static byte[] padding(int length) {
        byte[] pad = new byte[length];
        Arrays.fill(pad, PAD);
        return pad;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] joined = new byte[total];
        int at = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, at, part.length);
            at += part.length;
        }
        return joined;
    }

    /** Right-pads a field to its EMV length, the way a personalisation bureau would. */
    private static byte[] fixed(String value, int length, String label, byte pad) {
        String normalized = normalizeHex(value, label);
        byte[] supplied = bytes(normalized);
        if (supplied.length > length) {
            throw new IllegalArgumentException(label + " must be at most " + length + " bytes, not "
                    + supplied.length);
        }
        byte[] field = new byte[length];
        Arrays.fill(field, pad);
        System.arraycopy(supplied, 0, field, 0, supplied.length);
        return field;
    }

    private static byte[] fixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        byte[] out = new byte[length];
        if (raw.length > length) {
            System.arraycopy(raw, raw.length - length, out, 0, length);
        } else {
            System.arraycopy(raw, 0, out, length - raw.length, raw.length);
        }
        return out;
    }

    private static String unsignedHex(BigInteger value) {
        byte[] raw = value.toByteArray();
        int from = 0;
        while (from < raw.length - 1 && raw[from] == 0) {
            from++;
        }
        return hex(Arrays.copyOfRange(raw, from, raw.length));
    }

    static String normalizeHex(String value, String label) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (cleaned.isEmpty()) {
            return "";
        }
        if (!cleaned.matches("[0-9A-F]+") || (cleaned.length() % 2) != 0) {
            throw new IllegalArgumentException(label + " must be even-length hexadecimal");
        }
        return cleaned;
    }

    private static byte[] bytes(String hex) {
        return hex.isEmpty() ? new byte[0] : HexFormat.of().parseHex(hex);
    }

    private static String hex(byte[] data) {
        return HexFormat.of().formatHex(data).toUpperCase(Locale.ROOT);
    }

    private static String byteHex(int value) {
        return String.format("%02X", value & 0xFF);
    }
}
