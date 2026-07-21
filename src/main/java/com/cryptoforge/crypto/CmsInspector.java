package com.cryptoforge.crypto;

import com.cryptoforge.crypto.CmsInspectionReport.*;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.util.Store;
import org.bouncycastle.util.encoders.Base64;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;

public class CmsInspector {
    public static final int MAX_CMS_SIZE = 16 * 1024 * 1024; // 16 MiB

    public CmsInspectionReport inspect(byte[] inputBytes) throws Exception {
        return inspect(inputBytes, null, null);
    }

    public CmsInspectionReport inspect(byte[] inputBytes, byte[] detachedContent, KeyStore trustStore) throws Exception {
        if (inputBytes == null || inputBytes.length == 0) {
            throw new IllegalArgumentException("CMS input is empty");
        }
        if (inputBytes.length > MAX_CMS_SIZE) {
            throw new IllegalArgumentException("CMS input exceeds maximum size of 16 MiB");
        }

        byte[] derBytes = decodeIfNecessary(inputBytes);

        try (ASN1InputStream asn1Stream = new ASN1InputStream(new ByteArrayInputStream(derBytes))) {
            Object obj = asn1Stream.readObject();
            ContentInfo info = ContentInfo.getInstance(obj);
            if (info == null) {
                throw new IllegalArgumentException("Not a valid CMS/PKCS#7 object (no ContentInfo found)");
            }

            ASN1ObjectIdentifier contentType = info.getContentType();

            if (CMSObjectIdentifiers.signedData.equals(contentType)) {
                return inspectSignedData(derBytes, detachedContent, trustStore);
            } else if (CMSObjectIdentifiers.envelopedData.equals(contentType)) {
                return inspectEnvelopedData(derBytes);
            } else if (CMSObjectIdentifiers.authenticatedData.equals(contentType)) {
                return createUnsupportedReport(CmsContentType.AUTHENTICATED_DATA, contentType.getId());
            } else if (CMSObjectIdentifiers.compressedData.equals(contentType)) {
                return createUnsupportedReport(CmsContentType.COMPRESSED_DATA, contentType.getId());
            } else {
                return createUnsupportedReport(CmsContentType.UNKNOWN, contentType.getId());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CMS: " + e.getMessage(), e);
        }
    }

    private byte[] decodeIfNecessary(byte[] input) {
        String str = new String(input);
        if (str.contains("-----BEGIN PKCS7-----") || str.contains("-----BEGIN CMS-----")) {
            // Very basic PEM extractor for CMS
            String b64 = str.replaceAll("-----[A-Z0-9 ]+-----", "").replaceAll("\\s+", "");
            return Base64.decode(b64);
        } else if (str.matches("^[a-zA-Z0-9+/=\\s]+$") && !str.trim().isEmpty() && input[0] != 0x30) {
            try {
                return Base64.decode(str.replaceAll("\\s+", ""));
            } catch (Exception ignored) {}
        }
        return input;
    }

    private CmsInspectionReport createUnsupportedReport(CmsContentType type, String oid) {
        return new CmsInspectionReport(type, oid, "Unsupported or Unknown",
                ContentState.NOT_APPLICABLE, null, null,
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("CMS type " + oid + " is not fully supported for inspection."));
    }

    private CmsInspectionReport inspectSignedData(byte[] derBytes, byte[] detachedContent, KeyStore trustStore) throws Exception {
        CMSSignedData signedData;
        ContentState contentState = ContentState.UNKNOWN;
        List<String> warnings = new ArrayList<>();
        List<ValidationStep> validationSteps = new ArrayList<>();
        Long contentSize = null;

        validationSteps.add(new ValidationStep("CMS Structure", ValidationState.VALID, "Parseable SignedData"));

        if (detachedContent != null) {
            signedData = new CMSSignedData(new CMSProcessableByteArray(detachedContent), derBytes);
            contentState = ContentState.DETACHED;
            contentSize = (long) detachedContent.length;
        } else {
            signedData = new CMSSignedData(derBytes);
            if (signedData.getSignedContent() != null) {
                contentState = ContentState.ENCAPSULATED;
                byte[] contentBytes = (byte[]) signedData.getSignedContent().getContent();
                contentSize = contentBytes != null ? (long) contentBytes.length : null;
            } else {
                contentState = ContentState.DETACHED;
                warnings.add("Detached signature, but no original content provided.");
            }
        }

        // Validate content availability
        if (contentState == ContentState.DETACHED && detachedContent == null) {
            validationSteps.add(new ValidationStep("Content", ValidationState.NOT_EVALUATED, "Detached content missing"));
        } else {
            validationSteps.add(new ValidationStep("Content", ValidationState.VALID, "Content present (" + contentState + ")"));
        }

        Store<X509CertificateHolder> certStore = signedData.getCertificates();
        SignerInformationStore signers = signedData.getSignerInfos();

        List<CertificateSummary> certificates = extractCertificates(certStore);
        List<SignerInfoSummary> signerSummaries = new ArrayList<>();

        JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider("BC");

        for (SignerInformation signer : signers.getSigners()) {
            String sid = signer.getSID().getIssuer() != null
                    ? signer.getSID().getIssuer().toString() + " #" + signer.getSID().getSerialNumber()
                    : "SKI: " + Arrays.toString(signer.getSID().getSubjectKeyIdentifier());

            String digestAlg = signer.getDigestAlgOID();
            String encAlg = signer.getEncryptionAlgOID();

            ValidationState sigValid = ValidationState.NOT_EVALUATED;
            ValidationState certValid = ValidationState.NOT_EVALUATED;

            Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
            X509Certificate signerCert = null;

            if (!certCollection.isEmpty()) {
                signerCert = certConverter.getCertificate(certCollection.iterator().next());
                try {
                    signerCert.checkValidity(new Date());
                    certValid = ValidationState.VALID;
                } catch (Exception e) {
                    certValid = ValidationState.INVALID;
                    warnings.add("Signer certificate for " + sid + " is expired or not yet valid.");
                }
            } else {
                warnings.add("Signer certificate for " + sid + " not found in CMS.");
            }

            if (signerCert != null && (contentState == ContentState.ENCAPSULATED || detachedContent != null)) {
                try {
                    SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerCert);
                    if (signer.verify(verifier)) {
                        sigValid = ValidationState.VALID;
                    } else {
                        sigValid = ValidationState.INVALID;
                    }
                } catch (org.bouncycastle.cms.CMSSignerDigestMismatchException e) {
                    sigValid = ValidationState.INVALID;
                } catch (Exception e) {
                    sigValid = ValidationState.ERROR;
                    warnings.add("Error verifying signature for " + sid + ": " + e.getMessage());
                }
            }

            Map<String, String> signedAttrs = new HashMap<>();
            if (signer.getSignedAttributes() != null) {
                signedAttrs = extractAttributes(signer.getSignedAttributes());
            }

            Map<String, String> unsignedAttrs = new HashMap<>();
            if (signer.getUnsignedAttributes() != null) {
                unsignedAttrs = extractAttributes(signer.getUnsignedAttributes());
            }

            signerSummaries.add(new SignerInfoSummary(sid, digestAlg, encAlg, sigValid, certValid, signedAttrs, unsignedAttrs));
        }

        // Aggregate validation steps
        boolean allSigsValid = !signerSummaries.isEmpty();
        boolean anySigEvaluated = false;
        boolean allCertsValid = !signerSummaries.isEmpty();
        boolean anyCertEvaluated = false;

        for (SignerInfoSummary s : signerSummaries) {
            if (s.getSignatureValid() == ValidationState.VALID || s.getSignatureValid() == ValidationState.INVALID || s.getSignatureValid() == ValidationState.ERROR) {
                anySigEvaluated = true;
                if (s.getSignatureValid() != ValidationState.VALID) allSigsValid = false;
            } else {
                allSigsValid = false;
            }

            if (s.getCertificateValid() == ValidationState.VALID || s.getCertificateValid() == ValidationState.INVALID) {
                anyCertEvaluated = true;
                if (s.getCertificateValid() != ValidationState.VALID) allCertsValid = false;
            } else {
                allCertsValid = false;
            }
        }

        validationSteps.add(new ValidationStep("Signature/Integrity",
            anySigEvaluated ? (allSigsValid ? ValidationState.VALID : ValidationState.INVALID) : ValidationState.NOT_EVALUATED,
            signerSummaries.size() + " signer(s)"));

        validationSteps.add(new ValidationStep("Signer Certificate",
            anyCertEvaluated ? (allCertsValid ? ValidationState.VALID : ValidationState.INVALID) : ValidationState.NOT_EVALUATED,
            "Presence and current time validity"));

        CMSOperations.CadesProfile cadesProfile = CMSOperations.inspectCadesProfile(derBytes);
        if (cadesProfile.profile().startsWith("CAdES")) {
            validationSteps.add(new ValidationStep("CAdES-BES Certificate Binding",
                    cadesProfile.certificateBindingValid() ? ValidationState.VALID : ValidationState.INVALID,
                    cadesProfile.message()));
            CMSOperations.CadesTimestampStatus timestamp = CMSOperations.inspectCadesTimestamp(derBytes);
            if (timestamp.present()) {
                validationSteps.add(new ValidationStep("CAdES-T Signature Timestamp",
                        timestamp.imprintValid() ? ValidationState.VALID : ValidationState.INVALID,
                        timestamp.message()));
                if (trustStore != null && timestamp.imprintValid()) {
                    try {
                        org.bouncycastle.tsp.TimeStampToken token =
                                CMSOperations.cadesSignatureTimestampToken(derBytes);
                        Collection<X509CertificateHolder> tsaMatches =
                                token.getCertificates().getMatches(token.getSID());
                        if (tsaMatches.isEmpty()) {
                            throw new IllegalArgumentException("Timestamp token has no TSA signer certificate");
                        }
                        X509Certificate tsaCertificate = certConverter.getCertificate(tsaMatches.iterator().next());
                        validatePKIX(tsaCertificate, trustStore, token.getCertificates(), certConverter);
                        validationSteps.add(new ValidationStep("CAdES-T TSA Trust", ValidationState.VALID,
                                "TSA signer certificate chains to the supplied truststore"));
                    } catch (Exception e) {
                        validationSteps.add(new ValidationStep("CAdES-T TSA Trust", ValidationState.INVALID,
                                "TSA trust validation failed: " + e.getMessage()));
                    }
                } else {
                    validationSteps.add(new ValidationStep("CAdES-T TSA Trust", ValidationState.NOT_EVALUATED,
                            trustStore == null ? "No truststore provided" : "Timestamp integrity is invalid"));
                }
            }
            CMSOperations.CadesLongTermStatus longTerm =
                    CMSOperations.inspectCadesLongTermEvidence(derBytes);
            CMSOperations.CadesLongTermValidation longTermValidation =
                    CMSOperations.validateCadesLongTermEvidence(derBytes, new java.util.Date());
            validationSteps.add(new ValidationStep("CAdES-LT Evidence",
                    "CAdES-LT".equals(longTerm.level()) ? ValidationState.WARNING : ValidationState.NOT_EVALUATED,
                    "CAdES-LT".equals(longTerm.level())
                            ? longTermValidation.message()
                            : longTerm.message()));
        } else {
            validationSteps.add(new ValidationStep("CAdES Profile", ValidationState.NOT_EVALUATED,
                    cadesProfile.message()));
        }

        // Trust chain validation
        if (trustStore != null) {
            ValidationState trustState = ValidationState.VALID;
            StringBuilder trustDetails = new StringBuilder("PKIX path build ");
            boolean contentValid = !(contentState == ContentState.DETACHED && detachedContent == null);

            if (!anySigEvaluated || !allSigsValid || !anyCertEvaluated || !allCertsValid || !contentValid) {
                trustState = ValidationState.NOT_EVALUATED;
                trustDetails.setLength(0);
                trustDetails.append("Skipped due to invalid/missing signature or content");
            } else {
                for (SignerInformation signer : signers.getSigners()) {
                    Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
                    if (!certCollection.isEmpty()) {
                        X509Certificate signerCert = certConverter.getCertificate(certCollection.iterator().next());
                        try {
                            validatePKIX(signerCert, trustStore, certStore, certConverter);
                        } catch (Exception e) {
                            trustState = ValidationState.INVALID;
                            trustDetails.append("failed: ").append(e.getMessage());
                            break; // fail fast
                        }
                    } else {
                        trustState = ValidationState.INVALID;
                        trustDetails.append("failed: missing signer cert");
                        break;
                    }
                }
            }
            if (trustState == ValidationState.VALID) {
                trustDetails.append("successful");
            }
            validationSteps.add(new ValidationStep("Trust Chain", trustState, trustDetails.toString()));
        } else {
            validationSteps.add(new ValidationStep("Trust Chain", ValidationState.NOT_EVALUATED, "No truststore provided"));
        }

        String contentOidStr = signedData.getSignedContentTypeOID();

        return new CmsInspectionReport(CmsContentType.SIGNED_DATA, contentOidStr, "SignedData",
                contentState, contentSize, null, signerSummaries, Collections.emptyList(),
                certificates, validationSteps, warnings);
    }

    private CmsInspectionReport inspectEnvelopedData(byte[] derBytes) throws Exception {
        CMSEnvelopedData envData = new CMSEnvelopedData(derBytes);
        List<String> warnings = new ArrayList<>();
        List<ValidationStep> validationSteps = new ArrayList<>();

        validationSteps.add(new ValidationStep("CMS Structure", ValidationState.VALID, "Parseable EnvelopedData"));

        String contentOid = envData.toASN1Structure().getContentType().getId();

        List<RecipientSummary> recipients = new ArrayList<>();
        for (RecipientInformation recipient : envData.getRecipientInfos().getRecipients()) {
            String sid = "Unknown";
            if (recipient.getRID() instanceof org.bouncycastle.cms.KeyTransRecipientId) {
                org.bouncycastle.cms.KeyTransRecipientId rid = (org.bouncycastle.cms.KeyTransRecipientId) recipient.getRID();
                sid = rid.getIssuer() != null
                        ? rid.getIssuer().toString() + " #" + rid.getSerialNumber()
                        : "SKI: " + Arrays.toString(rid.getSubjectKeyIdentifier());
            } else {
                sid = "RID type: " + recipient.getRID().getClass().getSimpleName();
            }
            recipients.add(new RecipientSummary(recipient.getClass().getSimpleName(), sid, recipient.getKeyEncryptionAlgOID()));
        }

        return new CmsInspectionReport(CmsContentType.ENVELOPED_DATA, contentOid, "EnvelopedData",
                ContentState.NOT_APPLICABLE, null, envData.getEncryptionAlgOID(), Collections.emptyList(),
                recipients, Collections.emptyList(), Collections.emptyList(), warnings);
    }

    private List<CertificateSummary> extractCertificates(Store<X509CertificateHolder> certStore) throws Exception {
        List<CertificateSummary> result = new ArrayList<>();
        JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider("BC");

        for (X509CertificateHolder holder : certStore.getMatches(null)) {
            X509Certificate cert = certConverter.getCertificate(holder);
            List<String> keyUsages = new ArrayList<>();
            boolean[] ku = cert.getKeyUsage();
            if (ku != null) {
                if (ku.length > 0 && ku[0]) keyUsages.add("digitalSignature");
                if (ku.length > 1 && ku[1]) keyUsages.add("nonRepudiation");
                if (ku.length > 2 && ku[2]) keyUsages.add("keyEncipherment");
                if (ku.length > 3 && ku[3]) keyUsages.add("dataEncipherment");
                if (ku.length > 4 && ku[4]) keyUsages.add("keyAgreement");
                if (ku.length > 5 && ku[5]) keyUsages.add("keyCertSign");
                if (ku.length > 6 && ku[6]) keyUsages.add("cRLSign");
            }
            if (cert.getExtendedKeyUsage() != null) {
                keyUsages.addAll(cert.getExtendedKeyUsage());
            }

            String fingerprint = new String(org.bouncycastle.util.encoders.Hex.encode(
                    java.security.MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));

            result.add(new CertificateSummary(
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName(),
                    cert.getSerialNumber().toString(16),
                    cert.getNotBefore().toString(),
                    cert.getNotAfter().toString(),
                    cert.getPublicKey().getAlgorithm(),
                    fingerprint,
                    keyUsages
            ));
        }
        return result;
    }

    private Map<String, String> extractAttributes(AttributeTable table) {
        Map<String, String> attrs = new HashMap<>();
        org.bouncycastle.asn1.ASN1EncodableVector vec = table.toASN1EncodableVector();
        for (int i = 0; i < vec.size(); i++) {
            org.bouncycastle.asn1.cms.Attribute attr = org.bouncycastle.asn1.cms.Attribute.getInstance(vec.get(i));
            attrs.put(attr.getAttrType().getId(), attr.getAttrValues().toString());
        }
        return attrs;
    }

    private void validatePKIX(X509Certificate targetCert, KeyStore trustStore, Store<X509CertificateHolder> certStore, JcaX509CertificateConverter certConverter) throws Exception {
        // Implement simple PKIX validation
        X509CertSelector selector = new X509CertSelector();
        selector.setCertificate(targetCert);

        PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trustStore, selector);
        pkixParams.setRevocationEnabled(false); // No network calls

        // Setup trust anchors from trustStore
        boolean hasAnchors = false;
        for (TrustAnchor ta : pkixParams.getTrustAnchors()) {
             if (ta != null) hasAnchors = true;
        }
        if (!hasAnchors) {
             throw new Exception("Truststore is empty or contains no trusted certs");
        }

        // Add intermediate certificates to CertStore
        if (certStore != null) {
            List<X509Certificate> certList = new ArrayList<>();
            for (X509CertificateHolder holder : certStore.getMatches(null)) {
                certList.add(certConverter.getCertificate(holder));
            }
            java.security.cert.CertStore certStoreObj = java.security.cert.CertStore.getInstance("Collection",
                    new java.security.cert.CollectionCertStoreParameters(certList));
            pkixParams.addCertStore(certStoreObj);
        }

        CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
        builder.build(pkixParams);
    }
}
