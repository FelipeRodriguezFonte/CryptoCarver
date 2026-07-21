package com.cryptocarver.crypto;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.esf.RevocationValues;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.util.*;

/**
 * CMS (Cryptographic Message Syntax) / PKCS#7 Operations
 */
public class CMSOperations {
    private static final ASN1ObjectIdentifier CADES_CERTIFICATE_VALUES =
            new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.23");
    private static final ASN1ObjectIdentifier CADES_REVOCATION_VALUES =
            new ASN1ObjectIdentifier("1.2.840.113549.1.9.16.2.24");

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Parses a DER or PEM encoded X.509 CRL selected locally by the user.
     * PEM is normalized here rather than relying on provider-specific support
     * in {@link CertificateFactory}, so the UI extension filter is truthful on
     * every supported platform.
     */
    public static X509CRL parseX509Crl(byte[] encoded) throws Exception {
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("CRL evidence is empty");
        }
        byte[] der = encoded;
        String text = new String(encoded, StandardCharsets.US_ASCII);
        if (text.contains("-----BEGIN X509 CRL-----") || text.contains("-----BEGIN CRL-----")) {
            String base64 = text.replaceAll("-----BEGIN (?:X509 )?CRL-----", "")
                    .replaceAll("-----END (?:X509 )?CRL-----", "")
                    .replaceAll("\\s+", "");
            try {
                der = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("CRL PEM evidence is not valid Base64", invalid);
            }
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (ByteArrayInputStream input = new ByteArrayInputStream(der)) {
            return (X509CRL) factory.generateCRL(input);
        }
    }

    /**
     * Parses one or more local X.509 certificates in DER or PEM form. PEM
     * bundles are handled explicitly so an intermediate-chain file behaves the
     * same on macOS, Windows and Linux JREs.
     */
    public static List<X509Certificate> parseX509Certificates(byte[] encoded) throws Exception {
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("Certificate evidence is empty");
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        String text = new String(encoded, StandardCharsets.US_ASCII);
        List<X509Certificate> certificates = new ArrayList<>();
        java.util.regex.Matcher pem = java.util.regex.Pattern.compile(
                "-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----", java.util.regex.Pattern.DOTALL)
                .matcher(text);
        while (pem.find()) {
            byte[] der;
            try {
                der = Base64.getMimeDecoder().decode(pem.group(1));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Certificate PEM evidence is not valid Base64", invalid);
            }
            try (ByteArrayInputStream input = new ByteArrayInputStream(der)) {
                certificates.add((X509Certificate) factory.generateCertificate(input));
            }
        }
        if (!certificates.isEmpty()) return List.copyOf(certificates);

        try (ByteArrayInputStream input = new ByteArrayInputStream(encoded)) {
            for (java.security.cert.Certificate certificate : factory.generateCertificates(input)) {
                if (!(certificate instanceof X509Certificate x509)) {
                    throw new IllegalArgumentException("Certificate evidence is not X.509");
                }
                certificates.add(x509);
            }
        }
        if (certificates.isEmpty()) throw new IllegalArgumentException("No X.509 certificate was found in evidence");
        return List.copyOf(certificates);
    }

    /**
     * Result class for generateSignedData with warnings
     */
    public static class SignedDataResult {
        public final byte[] pkcs7;
        public final List<String> attributeWarnings;

        public SignedDataResult(byte[] pkcs7, List<String> attributeWarnings) {
            this.pkcs7 = pkcs7;
            this.attributeWarnings = attributeWarnings != null ? attributeWarnings : new ArrayList<>();
        }
    }

    /**
     * Generate SignedData PKCS#7 with warnings
     *
     * @param detached If true, creates a detached signature (data not included in
     *                 PKCS#7)
     * @return SignedDataResult with PKCS#7 bytes and attribute warnings
     */
    public static SignedDataResult generateSignedDataWithWarnings(byte[] data, X509Certificate cert,
            PrivateKey privateKey,
            Map<String, String> associatedData, boolean detached) throws Exception {
        return generateSignedDataWithProvider(data, cert, privateKey, Security.getProvider("BC"), associatedData, detached);
    }

    /**
     * Generates CMS with an explicitly supplied JCA signing provider. This is
     * used by PKCS#11 sessions so their private-key handle never leaves the
     * token while CMS assembly and digest calculation remain in-process.
     */
    public static SignedDataResult generateSignedDataWithProvider(byte[] data, X509Certificate cert,
            PrivateKey privateKey, java.security.Provider signingProvider,
            Map<String, String> associatedData, boolean detached) throws Exception {
        return generateSignedDataWithProfile(data, cert, privateKey, signingProvider, associatedData, detached, false);
    }

    /**
     * Generates the CAdES-BES subset of CMS SignedData. In addition to normal
     * CMS signed attributes, it binds the signing certificate through
     * {@code signingCertificateV2} (ESSCertIDv2, SHA-256). Timestamping and
     * long-term validation are deliberately separate profiles, not implied here.
     */
    public static SignedDataResult generateCadesBesWithProvider(byte[] data, X509Certificate cert,
            PrivateKey privateKey, java.security.Provider signingProvider,
            Map<String, String> associatedData, boolean detached) throws Exception {
        return generateSignedDataWithProfile(data, cert, privateKey, signingProvider, associatedData, detached, true);
    }

    public static byte[] generateCadesBes(byte[] data, X509Certificate cert, PrivateKey privateKey,
                                          Map<String, String> associatedData, boolean detached) throws Exception {
        return generateCadesBesWithProvider(data, cert, privateKey, Security.getProvider("BC"),
                associatedData, detached).pkcs7;
    }

    private static SignedDataResult generateSignedDataWithProfile(byte[] data, X509Certificate cert,
            PrivateKey privateKey, java.security.Provider signingProvider,
            Map<String, String> associatedData, boolean detached, boolean cadesBes) throws Exception {
        if (signingProvider == null) throw new IllegalArgumentException("Signing provider is required");
        // Create CMSProcessableByteArray
        CMSTypedData msg = new CMSProcessableByteArray(data);

        // Create certificate store
        List<X509Certificate> certList = new ArrayList<>();
        certList.add(cert);
        Store certs = new JcaCertStore(certList);

        // Create signer
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithmFor(privateKey))
                .setProvider(signingProvider)
                .build(privateKey);

        // Process associated data and collect warnings
        List<String> warnings = new ArrayList<>();

        AttributeTable attributes = null;
        if (associatedData != null && !associatedData.isEmpty()) {
            AttributeTableResult result = createAttributeTableWithWarnings(associatedData);
            warnings.addAll(result.warnings);
            attributes = result.table;
        }
        if (cadesBes) {
            AttributeTable cadesAttributes = cadesSigningCertificateAttributes(cert);
            if (attributes == null) {
                attributes = cadesAttributes;
            } else {
                attributes = attributes.add(PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                        cadesAttributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2).getAttrValues().getObjectAt(0));
            }
            warnings.add("CAdES-BES: signingCertificateV2 (SHA-256) added");
        }
        JcaSignerInfoGeneratorBuilder signerInfoBuilder = new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build());
        if (attributes != null) {
            signerInfoBuilder.setSignedAttributeGenerator(new DefaultSignedAttributeTableGenerator(attributes));
        }
        gen.addSignerInfoGenerator(signerInfoBuilder.build(signer, cert));

        // Add certificates
        gen.addCertificates(certs);

        // Generate CMS
        // encapsulate = !detached (if detached, don't include content)
        CMSSignedData signedData = gen.generate(msg, !detached);

        return new SignedDataResult(signedData.getEncoded(), warnings);
    }

    private static AttributeTable cadesSigningCertificateAttributes(X509Certificate certificate) throws Exception {
        byte[] fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        ESSCertIDv2 certificateId = new ESSCertIDv2(
                new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256), fingerprint);
        SigningCertificateV2 signingCertificate = new SigningCertificateV2(certificateId);
        return new AttributeTable(new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                new DERSet(signingCertificate)));
    }

    private static String signatureAlgorithmFor(PrivateKey privateKey) {
        String algorithm = privateKey == null ? "" : privateKey.getAlgorithm();
        if ("RSA".equalsIgnoreCase(algorithm)) return "SHA256withRSA";
        if ("EC".equalsIgnoreCase(algorithm) || "ECDSA".equalsIgnoreCase(algorithm)) return "SHA256withECDSA";
        if ("Ed25519".equalsIgnoreCase(algorithm) || "EdDSA".equalsIgnoreCase(algorithm)) return "Ed25519";
        throw new IllegalArgumentException("Unsupported CMS signing key algorithm: " + algorithm);
    }

    /**
     * Generate SignedData PKCS#7
     *
     * @param detached If true, creates a detached signature (data not included in
     *                 PKCS#7)
     */
    public static byte[] generateSignedData(byte[] data, X509Certificate cert, PrivateKey privateKey,
            Map<String, String> associatedData, boolean detached) throws Exception {
        return generateSignedDataWithWarnings(data, cert, privateKey, associatedData, detached).pkcs7;
    }

    /**
     * Generate SignedData PKCS#7 (backward compatibility - encapsulated by default)
     */
    public static byte[] generateSignedData(byte[] data, X509Certificate cert, PrivateKey privateKey,
            Map<String, String> associatedData) throws Exception {
        return generateSignedData(data, cert, privateKey, associatedData, false);
    }

    /**
     * Generate EnvelopedData PKCS#7
     */
    public static byte[] generateEnvelopedData(byte[] data, X509Certificate recipientCert) throws Exception {
        CMSEnvelopedDataGenerator gen = new CMSEnvelopedDataGenerator();

        // Add recipient
        gen.addRecipientInfoGenerator(
                new JceKeyTransRecipientInfoGenerator(recipientCert)
                        .setProvider("BC"));

        // Create encryptor (AES256)
        OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(
                CMSAlgorithm.AES256_CBC)
                .setProvider("BC")
                .build();

        // Generate
        CMSEnvelopedData envelopedData = gen.generate(
                new CMSProcessableByteArray(data),
                encryptor);

        return envelopedData.getEncoded();
    }

    /**
     * Verify SignedData PKCS#7
     */
    public static VerificationResult verifySignedData(byte[] pkcs7Data, X509Certificate cert) throws Exception {
        return verifySignedData(pkcs7Data, cert, null);
    }

    public static VerificationResult verifySignedData(byte[] pkcs7Data, X509Certificate cert, byte[] detachedData) throws Exception {
        CMSSignedData signedData;
        if (detachedData != null) {
            signedData = new CMSSignedData(new org.bouncycastle.cms.CMSProcessableByteArray(detachedData), pkcs7Data);
        } else {
            signedData = new CMSSignedData(pkcs7Data);
        }

        // Get signers
        Store certStore = signedData.getCertificates();
        SignerInformationStore signers = signedData.getSignerInfos();
        Collection<SignerInformation> c = signers.getSigners();

        boolean verified = false;
        byte[] content = null;
        Map<String, String> associatedData = new HashMap<>();

        for (SignerInformation signer : c) {
            // Get certificate
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
            X509CertificateHolder certHolder = certCollection.iterator().next();
            X509Certificate signerCert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // Verify signature
            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider("BC")
                    .build(cert != null ? cert : signerCert);

            try {
                verified = signer.verify(verifier);
            } catch (CMSSignerDigestMismatchException invalidDetachedContent) {
                // A detached CMS may be structurally valid while the caller supplies
                // different external content. That is a verification failure, not an
                // operational error to be surfaced by the UI.
                verified = false;
            }

            // Extract content
            CMSProcessable signedContent = signedData.getSignedContent();
            if (signedContent != null) {
                content = (byte[]) signedContent.getContent();
            }

            // Extract associated data (signed attributes)
            AttributeTable signedAttributes = signer.getSignedAttributes();
            if (signedAttributes != null) {
                associatedData = extractAttributeTable(signedAttributes);
            }

            break; // Process first signer only
        }

        return new VerificationResult(verified, content, associatedData);
    }

    /**
     * Inspects the CAdES-BES certificate binding independently from signature
     * verification. This identifies the profile and checks that its
     * {@code signingCertificateV2} hash points to the embedded signer
     * certificate, rather than merely reporting that the attribute exists.
     */
    public static CadesProfile inspectCadesProfile(byte[] pkcs7Data) throws Exception {
        CMSSignedData signedData = new CMSSignedData(pkcs7Data);
        SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
        AttributeTable attributes = signer.getSignedAttributes();
        Attribute certificateAttribute = attributes == null ? null
                : attributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2);
        if (certificateAttribute == null) {
            return new CadesProfile("CMS / PKCS#7", false, false,
                    "No signingCertificateV2 attribute: this is not CAdES-BES.");
        }

        SigningCertificateV2 signingCertificate = SigningCertificateV2.getInstance(
                certificateAttribute.getAttrValues().getObjectAt(0));
        ESSCertIDv2[] certificateIds = signingCertificate.getCerts();
        if (certificateIds.length == 0) {
            return new CadesProfile("CAdES-BES", true, false,
                    "signingCertificateV2 does not contain a certificate identifier.");
        }

        Collection<X509CertificateHolder> matches = signedData.getCertificates().getMatches(signer.getSID());
        if (matches.isEmpty()) {
            return new CadesProfile("CAdES-BES", true, false,
                    "The signer certificate is not embedded in the CMS container.");
        }
        X509CertificateHolder signerCertificate = matches.iterator().next();
        ESSCertIDv2 certificateId = certificateIds[0];
        String digestName = digestNameForCades(certificateId.getHashAlgorithm());
        if (digestName == null) {
            return new CadesProfile("CAdES-BES", true, false,
                    "Unsupported signingCertificateV2 digest: " + certificateId.getHashAlgorithm().getAlgorithm().getId());
        }
        byte[] expected = certificateId.getCertHash();
        byte[] actual = java.security.MessageDigest.getInstance(digestName).digest(signerCertificate.getEncoded());
        boolean valid = java.security.MessageDigest.isEqual(expected, actual);
        boolean hasTimestamp = signer.getUnsignedAttributes() != null
                && signer.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken) != null;
        String profileName = hasTimestamp ? "CAdES-T" : "CAdES-BES";
        return new CadesProfile(profileName, true, valid,
                valid ? "signingCertificateV2 matches the embedded signer certificate."
                        : "signingCertificateV2 does not match the embedded signer certificate.");
    }

    /** Returns the exact signature value that RFC 3161 must timestamp for CAdES-T. */
    public static byte[] cadesSignatureValue(byte[] cadesBes) throws Exception {
        CMSSignedData signedData = new CMSSignedData(cadesBes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
        return signer.getSignature();
    }

    /**
     * Upgrades a valid CAdES-BES container to CAdES-T by attaching a RFC 3161
     * signature timestamp token. The response imprint is checked against the
     * signer value before it is embedded.
     */
    public static byte[] addCadesTSignatureTimestamp(byte[] cadesBes, byte[] timestampResponse) throws Exception {
        CadesProfile profile = inspectCadesProfile(cadesBes);
        if (!"CAdES-BES".equals(profile.profile()) || !profile.certificateBindingValid()) {
            throw new IllegalArgumentException("CAdES-T requires a valid CAdES-BES signingCertificateV2 binding");
        }
        byte[] signatureValue = cadesSignatureValue(cadesBes);
        if (!TsaDiagnostics.tokenMatchesData(timestampResponse, signatureValue)) {
            throw new IllegalArgumentException("Timestamp token imprint does not match the CAdES signature value");
        }

        org.bouncycastle.tsp.TimeStampResponse response = new org.bouncycastle.tsp.TimeStampResponse(timestampResponse);
        org.bouncycastle.tsp.TimeStampToken token = response.getTimeStampToken();
        if (token == null) {
            throw new IllegalArgumentException("Timestamp response does not contain a RFC 3161 token");
        }

        CMSSignedData signedData = new CMSSignedData(cadesBes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
        AttributeTable unsigned = signer.getUnsignedAttributes();
        if (unsigned == null) {
            unsigned = new AttributeTable(new ASN1EncodableVector());
        }
        unsigned = unsigned.add(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                token.toCMSSignedData().toASN1Structure());
        SignerInformation timestampedSigner = SignerInformation.replaceUnsignedAttributes(signer, unsigned);
        return CMSSignedData.replaceSigners(signedData, new SignerInformationStore(timestampedSigner)).getEncoded();
    }

    /** Reports whether the container includes a CAdES signature timestamp token. */
    public static CadesTimestampStatus inspectCadesTimestamp(byte[] cmsBytes) throws Exception {
        org.bouncycastle.tsp.TimeStampToken token;
        byte[] signatureValue;
        try {
            CMSSignedData signedData = new CMSSignedData(cmsBytes);
            SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
            signatureValue = signer.getSignature();
            token = extractCadesSignatureTimestamp(signedData, signer);
        } catch (NoSuchElementException absent) {
            return new CadesTimestampStatus(false, false, "No CAdES signature timestamp token is present.");
        }
        try {
            Collection<X509CertificateHolder> tsaCertificates = token.getCertificates().getMatches(token.getSID());
            if (tsaCertificates.isEmpty()) {
                return new CadesTimestampStatus(true, false,
                        "Timestamp token does not embed its TSA signer certificate.");
            }
            X509Certificate tsaCertificate = new JcaX509CertificateConverter().setProvider("BC")
                    .getCertificate(tsaCertificates.iterator().next());
            token.validate(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(tsaCertificate));
            List<String> extendedKeyUsage = tsaCertificate.getExtendedKeyUsage();
            if (extendedKeyUsage == null
                    || !extendedKeyUsage.contains(org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_timeStamping.getId())) {
                return new CadesTimestampStatus(true, false,
                        "Timestamp token signer certificate is missing the required timeStamping EKU.");
            }
            String algorithm = switch (token.getTimeStampInfo().getMessageImprintAlgOID().getId()) {
                case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
                case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
                case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
                default -> null;
            };
            if (algorithm == null) {
                return new CadesTimestampStatus(true, false, "Unsupported timestamp imprint algorithm.");
            }
            boolean matches = java.security.MessageDigest.isEqual(token.getTimeStampInfo().getMessageImprintDigest(),
                    java.security.MessageDigest.getInstance(algorithm).digest(signatureValue));
            return new CadesTimestampStatus(true, matches,
                    matches ? "RFC 3161 token signature, timeStamping EKU and imprint are valid."
                            : "RFC 3161 timestamp imprint does not match the signature value.");
        } catch (Exception error) {
            return new CadesTimestampStatus(true, false, "Invalid RFC 3161 timestamp token: " + error.getMessage());
        }
    }

    /** Extracts the embedded CAdES-T RFC 3161 token, if present. */
    public static org.bouncycastle.tsp.TimeStampToken cadesSignatureTimestampToken(byte[] cmsBytes) throws Exception {
        CMSSignedData signedData = new CMSSignedData(cmsBytes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
        try {
            return extractCadesSignatureTimestamp(signedData, signer);
        } catch (NoSuchElementException absent) {
            throw new IllegalArgumentException("No CAdES signature timestamp token is present", absent);
        }
    }

    /**
     * Detects whether a CAdES container actually carries the certificate and
     * revocation evidence required by LT. Presence is not a revocation-freshness
     * decision; that requires a validation policy and validation time.
     */
    public static CadesLongTermStatus inspectCadesLongTermEvidence(byte[] cmsBytes) throws Exception {
        CadesProfile profile = inspectCadesProfile(cmsBytes);
        if (!profile.profile().startsWith("CAdES")) {
            return new CadesLongTermStatus("CMS / PKCS#7", false, false,
                    "Not a CAdES container: LT evidence is not applicable.");
        }
        CMSSignedData signedData = new CMSSignedData(cmsBytes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
        AttributeTable unsigned = signer.getUnsignedAttributes();
        boolean certificateValues = unsigned != null && unsigned.get(CADES_CERTIFICATE_VALUES) != null;
        boolean revocationValues = unsigned != null && unsigned.get(CADES_REVOCATION_VALUES) != null;
        if (certificateValues && revocationValues) {
            return new CadesLongTermStatus("CAdES-LT", true, true,
                    "Certificate and revocation evidence are embedded; freshness and policy remain to be validated.");
        }
        return new CadesLongTermStatus(profile.profile(), certificateValues, revocationValues,
                "CAdES-LT evidence incomplete: certificateValues=" + certificateValues
                        + ", revocationValues=" + revocationValues + ".");
    }

    /**
     * Performs the local, cryptographic part of CAdES-LT CRL evidence checking.
     * This never downloads revocation data and deliberately does not claim that
     * a certificate path is trusted or that a CRL applies to a particular
     * signer. It only reports whether embedded CRLs can be verified with an
     * embedded certificate having the matching issuer DN and whether their
     * declared validity window contains {@code validationTime}.
     */
    public static CadesLongTermValidation validateCadesLongTermEvidence(byte[] cmsBytes, Date validationTime)
            throws Exception {
        Objects.requireNonNull(validationTime, "validationTime");
        CadesLongTermStatus structural = inspectCadesLongTermEvidence(cmsBytes);
        if (!"CAdES-LT".equals(structural.level())) {
            return new CadesLongTermValidation(structural.level(), 0, 0, 0,
                    "CAdES-LT evidence is not complete; CRL validation was not performed.");
        }

        CMSSignedData signedData = new CMSSignedData(cmsBytes);
        SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
        AttributeTable unsigned = signer.getUnsignedAttributes();
        Attribute certificateAttribute = unsigned.get(CADES_CERTIFICATE_VALUES);
        Attribute revocationAttribute = unsigned.get(CADES_REVOCATION_VALUES);
        if (certificateAttribute == null || revocationAttribute == null) {
            return new CadesLongTermValidation("CAdES-LT", 0, 0, 0,
                    "CAdES-LT evidence attributes are missing.");
        }

        List<X509CertificateHolder> certificateEvidence = new ArrayList<>();
        for (X509CertificateHolder holder : signedData.getCertificates().getMatches(null)) {
            certificateEvidence.add(holder);
        }
        org.bouncycastle.asn1.ASN1Sequence certificateValues = org.bouncycastle.asn1.ASN1Sequence.getInstance(
                certificateAttribute.getAttrValues().getObjectAt(0));
        for (org.bouncycastle.asn1.ASN1Encodable value : certificateValues) {
            X509CertificateHolder holder = new X509CertificateHolder(
                    org.bouncycastle.asn1.x509.Certificate.getInstance(value));
            boolean duplicate = certificateEvidence.stream().anyMatch(existing -> {
                try {
                    return Arrays.equals(existing.getEncoded(), holder.getEncoded());
                } catch (Exception ignored) {
                    return false;
                }
            });
            if (!duplicate) certificateEvidence.add(holder);
        }

        RevocationValues revocationValues = RevocationValues.getInstance(
                revocationAttribute.getAttrValues().getObjectAt(0));
        CertificateList[] crls = revocationValues.getCrlVals();
        int signatureValid = 0;
        int current = 0;
        for (CertificateList value : crls) {
            X509CRLHolder crl = new X509CRLHolder(value);
            boolean verified = false;
            for (X509CertificateHolder issuer : certificateEvidence) {
                if (!crl.getIssuer().equals(issuer.getSubject())) continue;
                try {
                    verified = crl.isSignatureValid(new JcaContentVerifierProviderBuilder()
                            .setProvider("BC").build(issuer));
                    if (verified) break;
                } catch (Exception ignored) {
                    // A same-DN certificate which cannot verify this CRL is not its issuer.
                }
            }
            if (verified) signatureValid++;
            Date thisUpdate = crl.getThisUpdate();
            Date nextUpdate = crl.getNextUpdate();
            if (verified && !validationTime.before(thisUpdate)
                    && (nextUpdate == null || !validationTime.after(nextUpdate))) {
                current++;
            }
        }
        String message = "Embedded CRLs: " + crls.length + "; signature-valid: " + signatureValid
                + "; within declared validity at " + validationTime + ": " + current
                + ". No certificate-path, revocation-applicability, freshness-policy or online validation is claimed.";
        return new CadesLongTermValidation("CAdES-LT", crls.length, signatureValid, current, message);
    }

    /**
     * Adds local certificate and CRL evidence to a valid CAdES-T container.
     *
     * <p>This is an offline packaging operation: it embeds the supplied CRLs
     * but does not fetch them, decide their freshness, or make a trust decision.
     * Inspect the resulting file with a truststore afterwards.</p>
     */
    public static byte[] addCadesLtEvidence(byte[] cadesT, Collection<X509Certificate> extraCertificates,
                                             Collection<X509CRL> crls) throws Exception {
        CadesProfile profile = inspectCadesProfile(cadesT);
        CadesTimestampStatus timestamp = inspectCadesTimestamp(cadesT);
        if (!"CAdES-T".equals(profile.profile()) || !profile.certificateBindingValid() || !timestamp.imprintValid()) {
            throw new IllegalArgumentException("CAdES-LT evidence requires a valid CAdES-T signature");
        }
        if (crls == null || crls.isEmpty()) {
            throw new IllegalArgumentException("At least one CRL is required to create CAdES-LT evidence");
        }

        CMSSignedData signedData = new CMSSignedData(cadesT);
        SignerInformation signer = signedData.getSignerInfos().getSigners().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CMS SignedData contains no signer"));
        AttributeTable unsigned = signer.getUnsignedAttributes();
        if (unsigned == null) unsigned = new AttributeTable(new ASN1EncodableVector());

        ASN1EncodableVector certificates = new ASN1EncodableVector();
        Set<String> seenCertificates = new HashSet<>();
        for (X509CertificateHolder holder : signedData.getCertificates().getMatches(null)) {
            byte[] encoded = holder.getEncoded();
            if (seenCertificates.add(Base64.getEncoder().encodeToString(encoded))) {
                certificates.add(holder.toASN1Structure());
            }
        }
        if (extraCertificates != null) {
            for (X509Certificate certificate : extraCertificates) {
                if (certificate == null) continue;
                byte[] encoded = certificate.getEncoded();
                if (seenCertificates.add(Base64.getEncoder().encodeToString(encoded))) {
                    certificates.add(org.bouncycastle.asn1.x509.Certificate.getInstance(encoded));
                }
            }
        }
        if (certificates.size() == 0) {
            throw new IllegalArgumentException("No certificate evidence is available for CAdES-LT");
        }

        CertificateList[] revocationValues = new CertificateList[crls.size()];
        int index = 0;
        for (X509CRL crl : crls) {
            if (crl == null) throw new IllegalArgumentException("CRL evidence cannot contain null values");
            revocationValues[index++] = CertificateList.getInstance(crl.getEncoded());
        }
        unsigned = unsigned.add(CADES_CERTIFICATE_VALUES, new org.bouncycastle.asn1.DERSequence(certificates));
        unsigned = unsigned.add(CADES_REVOCATION_VALUES,
                new org.bouncycastle.asn1.esf.RevocationValues(revocationValues, null, null));

        SignerInformation upgradedSigner = SignerInformation.replaceUnsignedAttributes(signer, unsigned);
        return CMSSignedData.replaceSigners(signedData, new SignerInformationStore(upgradedSigner)).getEncoded();
    }

    private static org.bouncycastle.tsp.TimeStampToken extractCadesSignatureTimestamp(CMSSignedData signedData,
                                                                                       SignerInformation signer) throws Exception {
        AttributeTable unsigned = signer.getUnsignedAttributes();
        Attribute timestampAttribute = unsigned == null ? null
                : unsigned.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
        if (timestampAttribute == null) throw new NoSuchElementException("No CAdES timestamp");
        org.bouncycastle.asn1.cms.ContentInfo contentInfo = org.bouncycastle.asn1.cms.ContentInfo.getInstance(
                timestampAttribute.getAttrValues().getObjectAt(0));
        return new org.bouncycastle.tsp.TimeStampToken(new CMSSignedData(contentInfo));
    }

    private static String digestNameForCades(AlgorithmIdentifier algorithm) {
        String oid = algorithm == null ? NISTObjectIdentifiers.id_sha256.getId() : algorithm.getAlgorithm().getId();
        return switch (oid) {
            case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
            case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
            case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
            default -> null;
        };
    }

    /**
     * Decrypt EnvelopedData PKCS#7 using default BC provider
     */
    public static byte[] decryptEnvelopedData(byte[] pkcs7Data, PrivateKey privateKey) throws Exception {
        return decryptEnvelopedData(pkcs7Data, privateKey, null);
    }

    /**
     * Decrypt EnvelopedData PKCS#7 using specific provider for the private key
     */
    public static byte[] decryptEnvelopedData(byte[] pkcs7Data, PrivateKey privateKey, java.security.Provider keyProvider) throws Exception {
        CMSEnvelopedData envelopedData = new CMSEnvelopedData(pkcs7Data);

        // Get recipients
        RecipientInformationStore recipients = envelopedData.getRecipientInfos();
        Collection<RecipientInformation> c = recipients.getRecipients();

        for (RecipientInformation recipient : c) {
            try {
                // Decrypt
                JceKeyTransEnvelopedRecipient jceRecipient = new JceKeyTransEnvelopedRecipient(privateKey);
                if (keyProvider != null) {
                    jceRecipient.setProvider(keyProvider);
                    if (!"BC".equals(keyProvider.getName())) {
                        jceRecipient.setContentProvider("BC");
                    }
                } else {
                    jceRecipient.setProvider("BC");
                }

                return recipient.getContent(jceRecipient);
            } catch (Exception e) {
                // Continue trying other recipients
            }
        }

        throw new Exception("No recipient could be decrypted with the supplied key");
    }

    /**
     * Parse certificate from PEM or DER
     */
    public static X509Certificate parseCertificate(String certData) throws Exception {
        byte[] certBytes;

        if (certData.contains("BEGIN CERTIFICATE")) {
            // PEM format
            certData = certData.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");
            certBytes = Base64.getDecoder().decode(certData);
        } else {
            // Try as base64 or hex
            try {
                certBytes = Base64.getDecoder().decode(certData.replaceAll("\\s+", ""));
            } catch (Exception e) {
                certBytes = hexToBytes(certData.replaceAll("\\s+", ""));
            }
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    /**
     * Create AttributeTable from map
     * Returns warnings as list
     */
    public static class AttributeTableResult {
        public final AttributeTable table;
        public final List<String> warnings;

        public AttributeTableResult(AttributeTable table, List<String> warnings) {
            this.table = table;
            this.warnings = warnings;
        }
    }

    private static AttributeTableResult createAttributeTableWithWarnings(Map<String, String> attributes) {
        ASN1EncodableVector v = new ASN1EncodableVector();
        List<String> warnings = new ArrayList<>();

        // Reserved OIDs that CMS automatically handles
        Set<String> reservedOids = new HashSet<>(Arrays.asList(
                "1.2.840.113549.1.9.3", // contentType - auto-added by CMS
                "1.2.840.113549.1.9.4", // messageDigest - auto-added by CMS
                "1.2.840.113549.1.9.5", // signingTime - auto-added by CMS
                "1.2.840.113549.1.9.52" // cmsAlgorithmProtection - auto-added by CMS
        ));

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            try {
                String oidString = entry.getKey();
                String valueString = entry.getValue();

                // Skip reserved OIDs (they're added automatically by CMS)
                if (reservedOids.contains(oidString)) {
                    warnings.add("⚠ OID " + oidString + " is reserved (auto-added by CMS), skipped");
                    continue;
                }

                // Validate OID format - must be numeric like 1.2.3.4.5
                if (!oidString.matches("^[0-9]+(\\.[0-9]+)+$")) {
                    warnings.add("✗ Invalid OID '" + oidString + "': Must be numeric (e.g., 1.2.3.4.5), skipped");
                    continue;
                }

                ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier(oidString);

                // Try to parse value as different types
                org.bouncycastle.asn1.ASN1Encodable asnValue;

                // If it looks like an OID, use it as OID
                if (valueString.matches("^[0-9]+(\\.[0-9]+)+$")) {
                    asnValue = new ASN1ObjectIdentifier(valueString);
                }
                // If it looks like a date/time
                else if (valueString.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
                    try {
                        asnValue = new org.bouncycastle.asn1.DERUTCTime(valueString);
                    } catch (Exception e) {
                        // Fall back to string
                        asnValue = new org.bouncycastle.asn1.DERUTF8String(valueString);
                    }
                }
                // Default: use as UTF8 string
                else {
                    asnValue = new org.bouncycastle.asn1.DERUTF8String(valueString);
                }

                v.add(new Attribute(oid, new DERSet(asnValue)));
                warnings.add("✓ Added: " + oidString + " = " + valueString);

            } catch (Exception e) {
                warnings.add("✗ Error with '" + entry.getKey() + "': " + e.getMessage());
            }
        }

        return new AttributeTableResult(new AttributeTable(v), warnings);
    }

    /**
     * Create AttributeTable from map (backward compatibility)
     */
    private static AttributeTable createAttributeTable(Map<String, String> attributes) {
        return createAttributeTableWithWarnings(attributes).table;
    }

    /**
     * Extract AttributeTable to map
     */
    private static Map<String, String> extractAttributeTable(AttributeTable table) {
        Map<String, String> result = new HashMap<>();

        Hashtable attrs = table.toHashtable();
        for (Object key : attrs.keySet()) {
            try {
                ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) key;
                Attribute attr = (Attribute) attrs.get(key);
                result.put(oid.getId(), attr.getAttrValues().toString());
            } catch (Exception e) {
                // Skip invalid attributes
            }
        }

        return result;
    }

    /**
     * Helper: hex to bytes
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Result class
     */
    public static class VerificationResult {
        public final boolean verified;
        public final byte[] content;
        public final Map<String, String> associatedData;

        public VerificationResult(boolean verified, byte[] content, Map<String, String> associatedData) {
            this.verified = verified;
            this.content = content;
            this.associatedData = associatedData;
        }
    }

    /** CAdES profile metadata, not a substitute for trust or revocation validation. */
    public record CadesProfile(String profile, boolean certificateBindingPresent,
                               boolean certificateBindingValid, String message) { }

    /** CAdES-T timestamp presence and imprint validation; it does not establish TSA trust. */
    public record CadesTimestampStatus(boolean present, boolean imprintValid, String message) { }

    /** Structural LT evidence status; does not claim revocation evidence is current or trusted. */
    public record CadesLongTermStatus(String level, boolean certificateValuesPresent,
                                      boolean revocationValuesPresent, String message) { }

    /** Local CRL-evidence result; it is intentionally narrower than full LTV validation. */
    public record CadesLongTermValidation(String level, int crlCount, int signatureValidCrlCount,
                                          int currentCrlCount, String message) { }
}
