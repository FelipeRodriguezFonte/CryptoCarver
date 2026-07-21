package com.cryptocarver.crypto;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * Inmutable model representing the result of a CMS inspection.
 */
public class CmsInspectionReport {

    public enum ValidationState {
        VALID, INVALID, NOT_EVALUATED, WARNING, ERROR
    }

    public enum CmsContentType {
        SIGNED_DATA, ENVELOPED_DATA, AUTHENTICATED_DATA, COMPRESSED_DATA, UNKNOWN
    }

    public enum ContentState {
        ENCAPSULATED, DETACHED, NOT_APPLICABLE, UNKNOWN
    }

    public static class ValidationStep {
        private final String stepName;
        private final ValidationState state;
        private final String details;

        public ValidationStep(String stepName, ValidationState state, String details) {
            this.stepName = stepName;
            this.state = state;
            this.details = details;
        }

        public String getStepName() { return stepName; }
        public ValidationState getState() { return state; }
        public String getDetails() { return details; }
    }

    public static class SignerInfoSummary {
        private final String sid; // issuer/serial or SKI
        private final String digestAlg;
        private final String encryptionAlg;
        private final ValidationState signatureValid;
        private final ValidationState certificateValid;
        private final Map<String, String> signedAttributes;
        private final Map<String, String> unsignedAttributes;

        public SignerInfoSummary(String sid, String digestAlg, String encryptionAlg,
                               ValidationState signatureValid, ValidationState certificateValid,
                               Map<String, String> signedAttributes, Map<String, String> unsignedAttributes) {
            this.sid = sid;
            this.digestAlg = digestAlg;
            this.encryptionAlg = encryptionAlg;
            this.signatureValid = signatureValid;
            this.certificateValid = certificateValid;
            this.signedAttributes = signedAttributes == null ? Map.of() : Map.copyOf(signedAttributes);
            this.unsignedAttributes = unsignedAttributes == null ? Map.of() : Map.copyOf(unsignedAttributes);
        }

        public String getSid() { return sid; }
        public String getDigestAlg() { return digestAlg; }
        public String getEncryptionAlg() { return encryptionAlg; }
        public ValidationState getSignatureValid() { return signatureValid; }
        public ValidationState getCertificateValid() { return certificateValid; }
        public Map<String, String> getSignedAttributes() { return signedAttributes; }
        public Map<String, String> getUnsignedAttributes() { return unsignedAttributes; }
    }

    public static class RecipientSummary {
        private final String type;
        private final String sid;
        private final String algorithm;

        public RecipientSummary(String type, String sid, String algorithm) {
            this.type = type;
            this.sid = sid;
            this.algorithm = algorithm;
        }

        public String getType() { return type; }
        public String getSid() { return sid; }
        public String getAlgorithm() { return algorithm; }
    }

    public static class CertificateSummary {
        private final String subject;
        private final String issuer;
        private final String serial;
        private final String notBefore;
        private final String notAfter;
        private final String keyAlgorithm;
        private final String sha256Fingerprint;
        private final List<String> keyUsages;

        public CertificateSummary(String subject, String issuer, String serial, String notBefore, String notAfter,
                                String keyAlgorithm, String sha256Fingerprint, List<String> keyUsages) {
            this.subject = subject;
            this.issuer = issuer;
            this.serial = serial;
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            this.keyAlgorithm = keyAlgorithm;
            this.sha256Fingerprint = sha256Fingerprint;
            this.keyUsages = keyUsages == null ? List.of() : List.copyOf(keyUsages);
        }

        public String getSubject() { return subject; }
        public String getIssuer() { return issuer; }
        public String getSerial() { return serial; }
        public String getNotBefore() { return notBefore; }
        public String getNotAfter() { return notAfter; }
        public String getKeyAlgorithm() { return keyAlgorithm; }
        public String getSha256Fingerprint() { return sha256Fingerprint; }
        public List<String> getKeyUsages() { return keyUsages; }
    }

    private final CmsContentType type;
    private final String contentOid;
    private final String contentName;
    private final ContentState contentState;
    private final Long contentSize;
    private final String contentEncryptionAlgorithm;
    private final List<SignerInfoSummary> signers;
    private final List<RecipientSummary> recipients;
    private final List<CertificateSummary> certificates;
    private final List<ValidationStep> validationSteps;
    private final List<String> warnings;

    public CmsInspectionReport(CmsContentType type, String contentOid, String contentName,
                             ContentState contentState, Long contentSize, String contentEncryptionAlgorithm,
                             List<SignerInfoSummary> signers, List<RecipientSummary> recipients,
                             List<CertificateSummary> certificates, List<ValidationStep> validationSteps,
                             List<String> warnings) {
        this.type = type;
        this.contentOid = contentOid;
        this.contentName = contentName;
        this.contentState = contentState;
        this.contentSize = contentSize;
        this.contentEncryptionAlgorithm = contentEncryptionAlgorithm;
        this.signers = signers == null ? List.of() : List.copyOf(signers);
        this.recipients = recipients == null ? List.of() : List.copyOf(recipients);
        this.certificates = certificates == null ? List.of() : List.copyOf(certificates);
        this.validationSteps = validationSteps == null ? List.of() : List.copyOf(validationSteps);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public String getContentEncryptionAlgorithm() { return contentEncryptionAlgorithm; }

    public CmsContentType getType() { return type; }
    public String getContentOid() { return contentOid; }
    public String getContentName() { return contentName; }
    public ContentState getContentState() { return contentState; }
    public Long getContentSize() { return contentSize; }
    public List<SignerInfoSummary> getSigners() { return signers; }
    public List<RecipientSummary> getRecipients() { return recipients; }
    public List<CertificateSummary> getCertificates() { return certificates; }
    public List<ValidationStep> getValidationSteps() { return validationSteps; }
    public List<String> getWarnings() { return warnings; }
}
