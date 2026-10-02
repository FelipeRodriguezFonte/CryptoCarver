package com.cryptocarver.ui;

import com.cryptocarver.crypto.*;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/** Pure local CMS processing, encodings and verification reports. */
final class CmsLogic {
    private CmsLogic() { }
    record Verified(byte[] bytes, CMSOperations.VerificationResult result, CMSOperations.CadesProfile profile,
            CMSOperations.CadesTimestampStatus timestamp, CMSOperations.CadesLongTermStatus longTerm,
            CMSOperations.CadesLongTermValidation longTermValidation, String report) { }

    static Verified verify(String pkcs7Str, String detachedText, java.util.Date validationTime) throws Exception {
        // Clean PEM
        String base64 = pkcs7Str.replace("-----BEGIN PKCS7-----", "")
                .replace("-----END PKCS7-----", "")
                .replaceAll("\\s+", "");
        byte[] pkcs7Bytes = java.util.Base64.getDecoder().decode(base64);

        byte[] detachedData = null;
        if (detachedText != null && !detachedText.trim().isEmpty()) {
            detachedData = detachedText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        // Verify
        CMSOperations.VerificationResult result = CMSOperations.verifySignedData(pkcs7Bytes, null, detachedData);
        CMSOperations.CadesProfile cadesProfile = CMSOperations.inspectCadesProfile(pkcs7Bytes);
        CMSOperations.CadesTimestampStatus timestampStatus = CMSOperations.inspectCadesTimestamp(pkcs7Bytes);
        CMSOperations.CadesLongTermStatus longTerm = CMSOperations.inspectCadesLongTermEvidence(pkcs7Bytes);
        CMSOperations.CadesLongTermValidation longTermValidation =
                CMSOperations.validateCadesLongTermEvidence(pkcs7Bytes, validationTime);

        StringBuilder output = new StringBuilder();
        output.append("VERIFICATION RESULT: ").append(result.verified ? "✅ VALID" : "❌ INVALID").append("\n\n");
        output.append("SIGNATURE PROFILE: ").append(cadesProfile.profile()).append("\n");
        if (cadesProfile.certificateBindingPresent()) {
            output.append("CAdES certificate binding: ")
                    .append(cadesProfile.certificateBindingValid() ? "✅ VALID" : "❌ INVALID")
                    .append("\n");
        }
        output.append(cadesProfile.message()).append("\n\n");
        if (timestampStatus.present()) {
            output.append("CAdES signature timestamp: ")
                    .append(timestampStatus.imprintValid() ? "✅ VALID" : "❌ INVALID").append("\n")
                    .append(timestampStatus.message()).append("\n\n");
        }
        if (cadesProfile.profile().startsWith("CAdES")) {
            output.append("LONG-TERM EVIDENCE: ").append(longTerm.level()).append("\n")
                    .append("CRL evidence: ").append(longTermValidation.crlCount())
                    .append("; signature-valid: ").append(longTermValidation.signatureValidCrlCount())
                    .append("; within declared validity: ").append(longTermValidation.currentCrlCount()).append("\n")
                    .append(longTermValidation.message()).append("\n\n");
        }

        if (result.content != null) {
            output.append("SIGNED CONTENT:\n");
            output.append(new String(result.content, java.nio.charset.StandardCharsets.UTF_8)).append("\n\n");
        } else {
            output.append("Content is detached (not present in signature).\n\n");
        }

        if (!result.associatedData.isEmpty()) {
            output.append("SIGNED ATTRIBUTES:\n");
            for (java.util.Map.Entry<String, String> entry : result.associatedData.entrySet()) {
                output.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        return new Verified(pkcs7Bytes, result, cadesProfile, timestampStatus, longTerm, longTermValidation, output.toString());
    }
    static String onlineReport(com.cryptocarver.crypto.CmsInspectionReport report) {
        String integrity = report.getValidationSteps().stream()
                .filter(step -> "Signature/Integrity".equals(step.getStepName()))
                .map(step -> step.getState().name()).findFirst().orElse("NOT_EVALUATED");
        String text = "CMS/CAdES verification report\n"
                + "Integrity/signature: " + integrity + "\n"
                + "Trust chain: not evaluated (no truststore provided)\n"
                + "Revocation: " + report.getRevocation().status() + "\n"
                + "Evidence: " + report.getRevocation().evidence() + "\n"
                + (report.getRevocation().errors().isEmpty() ? "" : "Reason: " + String.join("; ", report.getRevocation().errors()) + "\n");
        return text;
    }
    static byte[] signLocal(byte[] data, String certPem, String keyPem, boolean detached, boolean cadesBes) throws Exception {
        X509Certificate cert = CertificateGenerator.parseCertificate(certPem);
        PrivateKey privateKey = parsePrivateKeyFromPEM(keyPem);
        return cadesBes ? CMSOperations.generateCadesBes(data, cert, privateKey, null, detached)
                : CMSOperations.generateSignedData(data, cert, privateKey, null, detached);
    }

    static X509Certificate parseRecipient(String certPem) throws Exception {
        return CertificateGenerator.parseCertificate(certPem);
    }

    static byte[] encrypt(byte[] data, X509Certificate cert) throws Exception {
        return CMSOperations.generateEnvelopedData(data, cert);
    }

    static byte[] decryptLocal(byte[] data, String keyPem) throws Exception {
        return CMSOperations.decryptEnvelopedData(data, parsePrivateKeyFromPEM(keyPem));
    }

    static String armor(byte[] bytes) {
        return "-----BEGIN PKCS7-----\n" + java.util.Base64.getEncoder().encodeToString(bytes)
                + "\n-----END PKCS7-----";
    }
    static byte[] decodeCmsArmored(String input) {
        String base64 = input.replace("-----BEGIN PKCS7-----", "")
                .replace("-----END PKCS7-----", "")
                .replaceAll("\\s+", "");
        return java.util.Base64.getDecoder().decode(base64);
    }

    static PrivateKey parsePrivateKeyFromPEM(String pemKey) throws Exception {
        String base64 = pemKey.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] encoded = java.util.Base64.getDecoder().decode(base64);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA"); // Defaulting to RSA for now

        try {
            return keyFactory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            // Try as standard RSA private key (PKCS#1) if needed, but Java mostly supports
            // PKCS#8
            // If BouncyCastle is registered, we can try to use it more robustly
            throw new Exception("Could not parse Private Key. Ensure it is PKCS#8 format (or standard PEM). sent: "
                    + e.getMessage());
        }
    }

    static CMSOperations.CadesLongTermStatus inspectLongTerm(byte[] cms) throws Exception {
        return CMSOperations.inspectCadesLongTermEvidence(cms);
    }

    static byte[] upgradeLongTerm(byte[] cms, java.util.List<X509Certificate> certificates,
            java.util.List<java.security.cert.X509CRL> crls) throws Exception {
        return CMSOperations.addCadesLtEvidence(cms, certificates, crls);
    }

    static void parseEvidence(byte[] bytes, String name, java.util.List<java.security.cert.X509CRL> crls,
            java.util.List<X509Certificate> certificates) {
        try {
            crls.add(CMSOperations.parseX509Crl(bytes));
        } catch (Exception notACrl) {
            try {
                certificates.addAll(CMSOperations.parseX509Certificates(bytes));
            } catch (Exception notACertificate) {
                throw new IllegalArgumentException(name
                        + " is neither a valid X.509 CRL nor X.509 certificate evidence", notACertificate);
            }
        }
    }
}
