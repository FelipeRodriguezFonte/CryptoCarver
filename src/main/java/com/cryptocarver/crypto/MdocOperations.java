package com.cryptocarver.crypto;

import COSE.AlgorithmID;
import COSE.Attribute;
import COSE.HeaderKeys;
import COSE.MessageTag;
import COSE.OneKey;
import COSE.Sign1Message;
import com.upokecenter.cbor.CBORObject;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * mdoc / mDL — ISO/IEC 18013-5, the European wallet's other credential format,
 * next to SD-JWT VC.
 *
 * <p>Where SD-JWT hides a claim behind a salted hash of a base64url string,
 * mdoc hides it behind a salted hash of a CBOR structure, and the whole document
 * is CBOR and COSE rather than JSON and JOSE. The selective-disclosure idea is
 * the same; the encoding is what differs, and the encoding is where the traps
 * are.</p>
 *
 * <h2>The detail everything depends on</h2>
 * <p>The digests in the Mobile Security Object are taken over
 * {@code IssuerSignedItemBytes}, which is {@code #6.24(bstr .cbor
 * IssuerSignedItem)} — the <b>tag-24 wrapped</b> bytes, not the
 * {@code IssuerSignedItem} map inside them. Hash the map and every digest
 * mismatches; hash the tagged bytes and it verifies. The 2020 DIS draft says
 * only "the binary data of the IssuerSignedItem", which is exactly ambiguous
 * enough to get wrong, so this was checked against worked examples where the
 * hashed input is visibly prefixed {@code D8 18}. {@link #issuerSignedItemBytes}
 * is the one place that wrapping is done, and {@link #verify} re-derives the
 * digest from the bytes as received rather than from anything re-encoded.</p>
 *
 * <h2>Scope</h2>
 * <p>Issuance and offline verification of the issuer-signed half: the
 * {@code IssuerSigned} structure, its {@code issuerAuth} COSE_Sign1, the Mobile
 * Security Object and the per-element digests. Device authentication is covered
 * to the extent it can be offline — {@link #deviceAuthenticationPayload} builds
 * the structure a device signature is computed over, and
 * {@link #verifyDeviceSignature} checks one against it, given the session
 * transcript. A {@code deviceMac} cannot be checked here at all and is reported
 * rather than guessed at: its key is derived by ECDH against the reader's
 * ephemeral private key, which exists only inside that session.</p>
 *
 * <p>Nothing is transported. Device engagement over NFC or BLE, and the session
 * encryption around it, are the reader's business, not a laboratory's.</p>
 */
public final class MdocOperations {

    /** CBOR tag 24, "encoded CBOR data item" — the wrapping this format is built on. */
    private static final int TAG_ENCODED_CBOR = 24;
    /** The COSE header label x5chain carries the document signer certificate in. */
    private static final int COSE_X5CHAIN = 33;
    /** The namespace of the mDL itself; other document types bring their own. */
    public static final String MDL_NAMESPACE = "org.iso.18013.5.1";
    public static final String MDL_DOCTYPE = "org.iso.18013.5.1.mDL";
    /** ISO/IEC 18013-5 requires at least 16 bytes of salt per item. */
    private static final int MINIMUM_RANDOM_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    public record ValidityInfo(Instant signed, Instant validFrom, Instant validUntil, Instant expectedUpdate) {
    }

    /**
     * One disclosed element, with the bytes its digest is taken over kept
     * alongside it — recomputing them later from a re-encoded map is precisely
     * the mistake this format punishes.
     */
    public record Item(int digestId, byte[] random, String elementIdentifier,
                       CBORObject elementValue, byte[] itemBytes) {

        public String valueAsText() {
            return elementValue.getType() == com.upokecenter.cbor.CBORType.TextString
                    ? elementValue.AsString()
                    : elementValue.toString();
        }
    }

    public record MobileSecurityObject(String version,
                                       String digestAlgorithm,
                                       String docType,
                                       Map<String, Map<Integer, byte[]>> valueDigests,
                                       CBORObject deviceKeyInfo,
                                       ValidityInfo validity) {
    }

    /** @param namespaces namespace to the items disclosed inside it */
    public record IssuerSigned(Map<String, List<Item>> namespaces, byte[] issuerAuth) {
    }

    public record Finding(String severity, String message) {
    }

    public record VerificationReport(boolean issuerSignatureValid,
                                     X509Certificate signerCertificate,
                                     MobileSecurityObject mso,
                                     Map<String, List<Item>> disclosed,
                                     List<Finding> findings) {

        public boolean allDigestsMatched() {
            return findings.stream().noneMatch(f -> "ERROR".equals(f.severity()));
        }
    }

    private MdocOperations() {
    }

    // ------------------------------------------------------------------ issue

    /**
     * Issues an {@code IssuerSigned} structure: one {@code IssuerSignedItem} per
     * element, the Mobile Security Object over their digests, and the
     * {@code issuerAuth} COSE_Sign1 over the MSO.
     *
     * @param namespacesJson {@code {"org.iso.18013.5.1": {"family_name": "Doe", ...}}}
     * @param deviceKey      the holder's public key, bound into the MSO so that a
     *                       device signature can later be tied to this document;
     *                       {@code null} issues a document no device can
     *                       authenticate, which is a laboratory case, not a real one
     *
     * <p><b>A limit of taking claims as JSON.</b> A real mDL carries CBOR tags on
     * some elements — {@code birth_date} is a {@code full-date}, tag 1004 — and
     * JSON has no way to say so, so elements issued here are untagged. Documents
     * from here are therefore good for exercising the digest and signature
     * machinery, and are not byte-identical to an issuer's.</p>
     */
    public static byte[] issue(String docType,
                               String namespacesJson,
                               String digestAlgorithm,
                               ValidityInfo validity,
                               PrivateKey signingKey,
                               X509Certificate signerCertificate,
                               PublicKey deviceKey) throws Exception {
        CBORObject input = CBORObject.FromJSONString(namespacesJson);
        if (input.getType() != com.upokecenter.cbor.CBORType.Map) {
            throw new IllegalArgumentException("Namespaces must be a JSON object keyed by namespace");
        }

        CBORObject issuerNameSpaces = CBORObject.NewMap();
        CBORObject valueDigests = CBORObject.NewMap();
        int digestId = 0;

        for (CBORObject namespaceKey : input.getKeys()) {
            String namespace = namespaceKey.AsString();
            CBORObject elements = input.get(namespaceKey);
            CBORObject items = CBORObject.NewArray();
            CBORObject digests = CBORObject.NewMap();

            for (CBORObject elementKey : elements.getKeys()) {
                byte[] salt = new byte[MINIMUM_RANDOM_BYTES];
                RANDOM.nextBytes(salt);

                CBORObject item = CBORObject.NewMap();
                item.set("digestID", CBORObject.FromObject(digestId));
                item.set("random", CBORObject.FromObject(salt));
                item.set("elementIdentifier", CBORObject.FromObject(elementKey.AsString()));
                item.set("elementValue", elements.get(elementKey));

                byte[] itemBytes = issuerSignedItemBytes(item.EncodeToBytes());
                items.Add(CBORObject.DecodeFromBytes(itemBytes));
                digests.set(CBORObject.FromObject(digestId), CBORObject.FromObject(
                        digest(itemBytes, digestAlgorithm)));
                digestId++;
            }
            issuerNameSpaces.set(namespace, items);
            valueDigests.set(namespace, digests);
        }

        CBORObject mso = CBORObject.NewMap();
        mso.set("version", CBORObject.FromObject("1.0"));
        mso.set("digestAlgorithm", CBORObject.FromObject(digestAlgorithm));
        mso.set("valueDigests", valueDigests);
        if (deviceKey != null) {
            CBORObject deviceKeyInfo = CBORObject.NewMap();
            deviceKeyInfo.set("deviceKey", CBORObject.DecodeFromBytes(
                    new OneKey(deviceKey, null).AsCBOR().EncodeToBytes()));
            mso.set("deviceKeyInfo", deviceKeyInfo);
        }
        mso.set("docType", CBORObject.FromObject(docType));
        mso.set("validityInfo", encodeValidity(validity));

        byte[] msoBytes = issuerSignedItemBytes(mso.EncodeToBytes());

        // Untagged COSE_Sign1, as the standard requires; alg in the protected
        // header and the signer certificate as x5chain in the unprotected one.
        Sign1Message signature = new Sign1Message(false);
        signature.SetContent(msoBytes);
        signature.addAttribute(HeaderKeys.Algorithm, signingAlgorithm(signingKey).AsCBOR(), Attribute.PROTECTED);
        signature.addAttribute(CBORObject.FromObject(COSE_X5CHAIN),
                CBORObject.FromObject(signerCertificate.getEncoded()), Attribute.UNPROTECTED);
        signature.sign(new OneKey(signerCertificate.getPublicKey(), signingKey));

        CBORObject issuerSigned = CBORObject.NewMap();
        issuerSigned.set("nameSpaces", issuerNameSpaces);
        issuerSigned.set("issuerAuth", CBORObject.DecodeFromBytes(signature.EncodeToBytes()));
        return issuerSigned.EncodeToBytes();
    }

    // ------------------------------------------------------------------ parse

    public static IssuerSigned parse(byte[] issuerSignedCbor) {
        CBORObject root = CBORObject.DecodeFromBytes(issuerSignedCbor);
        Map<String, List<Item>> namespaces = new LinkedHashMap<>();

        CBORObject nameSpaces = root.get("nameSpaces");
        if (nameSpaces != null) {
            for (CBORObject namespaceKey : nameSpaces.getKeys()) {
                List<Item> items = new ArrayList<>();
                for (CBORObject taggedItem : nameSpaces.get(namespaceKey).getValues()) {
                    items.add(readItem(taggedItem));
                }
                namespaces.put(namespaceKey.AsString(), List.copyOf(items));
            }
        }
        CBORObject issuerAuth = root.get("issuerAuth");
        return new IssuerSigned(namespaces, issuerAuth == null ? null : issuerAuth.EncodeToBytes());
    }

    private static Item readItem(CBORObject taggedItem) {
        if (!taggedItem.HasMostOuterTag(TAG_ENCODED_CBOR)) {
            throw new IllegalArgumentException(
                    "An IssuerSignedItem must be a tag 24 byte string; this one is " + taggedItem.getType());
        }
        // The bytes as received. Re-encoding the decoded map would be a different
        // byte string in general, and its digest would not match.
        byte[] itemBytes = taggedItem.EncodeToBytes();
        CBORObject item = CBORObject.DecodeFromBytes(taggedItem.Untag().GetByteString());
        return new Item(
                item.get("digestID").AsInt32(),
                item.get("random").GetByteString(),
                item.get("elementIdentifier").AsString(),
                item.get("elementValue"),
                itemBytes);
    }

    public static MobileSecurityObject readMobileSecurityObject(byte[] issuerAuthCbor) throws Exception {
        Sign1Message signature = (Sign1Message) Sign1Message.DecodeFromBytes(issuerAuthCbor, MessageTag.Sign1);
        CBORObject tagged = CBORObject.DecodeFromBytes(signature.GetContent());
        CBORObject mso = CBORObject.DecodeFromBytes(tagged.Untag().GetByteString());

        Map<String, Map<Integer, byte[]>> valueDigests = new LinkedHashMap<>();
        CBORObject digests = mso.get("valueDigests");
        for (CBORObject namespaceKey : digests.getKeys()) {
            Map<Integer, byte[]> perNamespace = new LinkedHashMap<>();
            CBORObject entries = digests.get(namespaceKey);
            for (CBORObject digestKey : entries.getKeys()) {
                perNamespace.put(digestKey.AsInt32(), entries.get(digestKey).GetByteString());
            }
            valueDigests.put(namespaceKey.AsString(), perNamespace);
        }
        return new MobileSecurityObject(
                text(mso, "version"),
                text(mso, "digestAlgorithm"),
                text(mso, "docType"),
                valueDigests,
                mso.get("deviceKeyInfo"),
                decodeValidity(mso.get("validityInfo")));
    }

    // ----------------------------------------------------------------- verify

    /**
     * Verifies an {@code IssuerSigned}: the issuer's signature over the Mobile
     * Security Object, then every disclosed element's digest against the one the
     * MSO commits to, then validity.
     *
     * @param issuerKey the key to verify {@code issuerAuth} with, or {@code null}
     *                  to take the x5chain certificate the document carries —
     *                  which proves the document is internally consistent and
     *                  nothing about who issued it
     * @param at        the instant to judge validity at
     */
    public static VerificationReport verify(byte[] issuerSignedCbor, PublicKey issuerKey, Instant at)
            throws Exception {
        IssuerSigned issuerSigned = parse(issuerSignedCbor);
        List<Finding> findings = new ArrayList<>();

        if (issuerSigned.issuerAuth() == null) {
            throw new IllegalArgumentException("This IssuerSigned structure carries no issuerAuth");
        }
        Sign1Message signature = (Sign1Message)
                Sign1Message.DecodeFromBytes(issuerSigned.issuerAuth(), MessageTag.Sign1);
        X509Certificate signer = x5chainCertificate(signature);

        PublicKey verificationKey = issuerKey;
        if (verificationKey == null) {
            if (signer == null) {
                throw new IllegalArgumentException(
                        "No verification key was supplied and the document carries no x5chain certificate");
            }
            verificationKey = signer.getPublicKey();
            findings.add(new Finding("WARN",
                    "Verified against the certificate the document itself carries: this shows the document"
                            + " is internally consistent, not that a trusted issuer signed it."));
        }
        boolean signatureValid = signature.validate(new OneKey(verificationKey, null));
        if (!signatureValid) {
            findings.add(new Finding("ERROR", "The issuerAuth signature does not verify."));
        }

        MobileSecurityObject mso = readMobileSecurityObject(issuerSigned.issuerAuth());

        for (Map.Entry<String, List<Item>> entry : issuerSigned.namespaces().entrySet()) {
            Map<Integer, byte[]> committed = mso.valueDigests().get(entry.getKey());
            if (committed == null) {
                findings.add(new Finding("ERROR",
                        "The MSO commits to no digests for namespace '" + entry.getKey() + "'."));
                continue;
            }
            for (Item item : entry.getValue()) {
                byte[] expected = committed.get(item.digestId());
                if (expected == null) {
                    findings.add(new Finding("ERROR", "Element '" + item.elementIdentifier()
                            + "' has digest ID " + item.digestId() + ", which the MSO does not commit to."));
                    continue;
                }
                byte[] actual = digest(item.itemBytes(), mso.digestAlgorithm());
                if (!MessageDigest.isEqual(expected, actual)) {
                    findings.add(new Finding("ERROR", "Element '" + item.elementIdentifier()
                            + "' does not match the digest the issuer signed."));
                }
                if (item.random().length < MINIMUM_RANDOM_BYTES) {
                    findings.add(new Finding("WARN", "Element '" + item.elementIdentifier()
                            + "' carries only " + item.random().length
                            + " bytes of salt; ISO/IEC 18013-5 requires at least " + MINIMUM_RANDOM_BYTES
                            + ", because a short salt lets a small value space be brute-forced."));
                }
            }
        }

        ValidityInfo validity = mso.validity();
        if (validity != null && at != null) {
            if (validity.validFrom() != null && at.isBefore(validity.validFrom())) {
                findings.add(new Finding("ERROR", "The document is not valid until " + validity.validFrom()));
            }
            if (validity.validUntil() != null && at.isAfter(validity.validUntil())) {
                findings.add(new Finding("ERROR", "The document stopped being valid at " + validity.validUntil()));
            }
        }

        if (findings.isEmpty()) {
            findings.add(new Finding("INFO", "Signature and every disclosed digest check out."));
        }
        return new VerificationReport(signatureValid, signer, mso, issuerSigned.namespaces(), List.copyOf(findings));
    }

    // ---------------------------------------------------------- device auth

    /**
     * Builds the payload a device signature is computed over:
     * {@code #6.24(bstr .cbor ["DeviceAuthentication", SessionTranscript, DocType,
     * DeviceNameSpacesBytes])}.
     *
     * <p>The structure is never transmitted — both sides build it independently
     * and only the signature travels, which is what binds a presentation to one
     * session and stops it being replayed into another.</p>
     */
    public static byte[] deviceAuthenticationPayload(byte[] sessionTranscriptCbor,
                                                     String docType,
                                                     byte[] deviceNameSpacesBytes) {
        CBORObject structure = CBORObject.NewArray();
        structure.Add(CBORObject.FromObject("DeviceAuthentication"));
        structure.Add(CBORObject.DecodeFromBytes(sessionTranscriptCbor));
        structure.Add(CBORObject.FromObject(docType));
        structure.Add(CBORObject.DecodeFromBytes(deviceNameSpacesBytes));
        return issuerSignedItemBytes(structure.EncodeToBytes());
    }

    /**
     * Verifies a {@code deviceSignature} against the session it claims to belong
     * to. The COSE_Sign1 has a nil payload — the content is detached and is the
     * structure {@link #deviceAuthenticationPayload} rebuilds.
     */
    public static boolean verifyDeviceSignature(byte[] deviceSignatureCose,
                                                byte[] sessionTranscriptCbor,
                                                String docType,
                                                byte[] deviceNameSpacesBytes,
                                                PublicKey deviceKey) throws Exception {
        Sign1Message signature = (Sign1Message)
                Sign1Message.DecodeFromBytes(deviceSignatureCose, MessageTag.Sign1);
        signature.SetContent(deviceAuthenticationPayload(
                sessionTranscriptCbor, docType, deviceNameSpacesBytes));
        return signature.validate(new OneKey(deviceKey, null));
    }

    // ------------------------------------------------------------------ utils

    /** {@code #6.24(bstr .cbor X)} — the wrapping the whole format turns on. */
    public static byte[] issuerSignedItemBytes(byte[] encoded) {
        return CBORObject.FromObjectAndTag(CBORObject.FromObject(encoded), TAG_ENCODED_CBOR)
                .EncodeToBytes();
    }

    private static byte[] digest(byte[] input, String algorithm) throws Exception {
        return MessageDigest.getInstance(requireDigestAlgorithm(algorithm)).digest(input);
    }

    /** ISO/IEC 18013-5 Table 24 allows exactly these three, spelled this way. */
    private static String requireDigestAlgorithm(String algorithm) {
        return switch (algorithm == null ? "" : algorithm) {
            case "SHA-256", "SHA-384", "SHA-512" -> algorithm;
            default -> throw new IllegalArgumentException(
                    "ISO/IEC 18013-5 allows SHA-256, SHA-384 or SHA-512, not '" + algorithm + "'");
        };
    }

    private static AlgorithmID signingAlgorithm(PrivateKey key) {
        if (!"EC".equals(key.getAlgorithm()) && !"ECDSA".equals(key.getAlgorithm())) {
            throw new IllegalArgumentException(
                    "ISO/IEC 18013-5 allows ES256, ES384, ES512 and EdDSA; this key is " + key.getAlgorithm());
        }
        return AlgorithmID.ECDSA_256;
    }

    private static X509Certificate x5chainCertificate(Sign1Message signature) throws Exception {
        CBORObject x5chain = signature.findAttribute(CBORObject.FromObject(COSE_X5CHAIN));
        if (x5chain == null) {
            return null;
        }
        // One certificate is the usual case; a chain arrives as an array, whose
        // first entry is the document signer.
        byte[] encoded = x5chain.getType() == com.upokecenter.cbor.CBORType.Array
                ? x5chain.get(0).GetByteString()
                : x5chain.GetByteString();
        return (X509Certificate) java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static CBORObject encodeValidity(ValidityInfo validity) {
        CBORObject info = CBORObject.NewMap();
        info.set("signed", tdate(validity.signed()));
        info.set("validFrom", tdate(validity.validFrom()));
        info.set("validUntil", tdate(validity.validUntil()));
        if (validity.expectedUpdate() != null) {
            info.set("expectedUpdate", tdate(validity.expectedUpdate()));
        }
        return info;
    }

    /** {@code tdate} is an RFC 3339 string under CBOR tag 0. */
    private static CBORObject tdate(Instant instant) {
        return CBORObject.FromObjectAndTag(
                CBORObject.FromObject(instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()), 0);
    }

    private static ValidityInfo decodeValidity(CBORObject info) {
        if (info == null) {
            return null;
        }
        return new ValidityInfo(
                instant(info.get("signed")),
                instant(info.get("validFrom")),
                instant(info.get("validUntil")),
                instant(info.get("expectedUpdate")));
    }

    private static Instant instant(CBORObject value) {
        if (value == null) {
            return null;
        }
        CBORObject untagged = value.isTagged() ? value.Untag() : value;
        return Instant.parse(untagged.AsString());
    }

    private static String text(CBORObject map, String key) {
        CBORObject value = map.get(key);
        return value == null ? null : value.AsString();
    }

    // -------------------------------------------------------------- reporting

    public static String describe(byte[] issuerSignedCbor, PublicKey issuerKey, Instant at, Locale locale)
            throws Exception {
        boolean spanish = locale != null && "es".equals(locale.getLanguage());
        VerificationReport report = verify(issuerSignedCbor, issuerKey, at);
        MobileSecurityObject mso = report.mso();
        StringBuilder text = new StringBuilder();

        text.append(spanish ? "mdoc / mDL (ISO/IEC 18013-5)" : "mdoc / mDL (ISO/IEC 18013-5)").append('\n');
        text.append("  docType        : ").append(mso.docType()).append('\n');
        text.append("  version        : ").append(mso.version()).append('\n');
        text.append("  digestAlgorithm: ").append(mso.digestAlgorithm()).append('\n');
        if (mso.validity() != null) {
            text.append("  ").append(spanish ? "validez" : "validity")
                    .append("        : ").append(mso.validity().validFrom())
                    .append(" .. ").append(mso.validity().validUntil()).append('\n');
        }
        if (report.signerCertificate() != null) {
            text.append("  ").append(spanish ? "firmante" : "signer")
                    .append("       : ").append(report.signerCertificate().getSubjectX500Principal()).append('\n');
        }
        text.append("  ").append(spanish ? "clave de dispositivo" : "device key")
                .append(": ").append(mso.deviceKeyInfo() == null
                        ? (spanish ? "ausente" : "absent")
                        : (spanish ? "presente" : "present")).append('\n');

        text.append('\n').append(spanish ? "Elementos divulgados" : "Disclosed elements").append('\n');
        for (Map.Entry<String, List<Item>> entry : report.disclosed().entrySet()) {
            text.append("  ").append(entry.getKey()).append('\n');
            for (Item item : entry.getValue()) {
                text.append("    [").append(item.digestId()).append("] ")
                        .append(item.elementIdentifier()).append(" = ")
                        .append(item.valueAsText()).append('\n');
            }
        }

        text.append('\n').append(spanish ? "Hallazgos" : "Findings").append('\n');
        for (Finding finding : report.findings()) {
            text.append("  [").append(finding.severity()).append("] ").append(finding.message()).append('\n');
        }
        return text.toString();
    }
}
