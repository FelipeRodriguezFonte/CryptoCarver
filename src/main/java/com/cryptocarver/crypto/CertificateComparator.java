package com.cryptocarver.crypto;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/** Produces a concise field-level diff for two X.509 certificates. */
public final class CertificateComparator {
    private CertificateComparator() { }

    public static String compare(X509Certificate left, X509Certificate right) {
        if (left == null || right == null) throw new IllegalArgumentException("Both certificates are required");
        List<String> differences = new ArrayList<>();
        diff(differences, "Subject", left.getSubjectX500Principal().getName(), right.getSubjectX500Principal().getName());
        diff(differences, "Issuer", left.getIssuerX500Principal().getName(), right.getIssuerX500Principal().getName());
        diff(differences, "Serial", left.getSerialNumber().toString(16), right.getSerialNumber().toString(16));
        diff(differences, "Not Before", left.getNotBefore().toInstant().toString(), right.getNotBefore().toInstant().toString());
        diff(differences, "Not After", left.getNotAfter().toInstant().toString(), right.getNotAfter().toInstant().toString());
        diff(differences, "Signature Algorithm", left.getSigAlgName(), right.getSigAlgName());
        diff(differences, "Public Key SHA-256", KeyMaterialInspector.fingerprint(left.getPublicKey().getEncoded()),
                KeyMaterialInspector.fingerprint(right.getPublicKey().getEncoded()));
        diff(differences, "Basic Constraints", Integer.toString(left.getBasicConstraints()), Integer.toString(right.getBasicConstraints()));
        diff(differences, "KeyUsage", java.util.Arrays.toString(left.getKeyUsage()), java.util.Arrays.toString(right.getKeyUsage()));
        try {
            diff(differences, "Subject Alternative Names", String.valueOf(left.getSubjectAlternativeNames()), String.valueOf(right.getSubjectAlternativeNames()));
        } catch (Exception e) {
            differences.add("[WARN] Subject Alternative Names could not be compared");
        }
        StringBuilder report = new StringBuilder("========================================\nX.509 CERTIFICATE COMPARISON\n========================================\n\n")
                .append("Left SHA-256: ").append(KeyMaterialInspector.fingerprint(encoded(left))).append("\n")
                .append("Right SHA-256: ").append(KeyMaterialInspector.fingerprint(encoded(right))).append("\n\n");
        if (differences.isEmpty()) report.append("✓ No differences in the inspected certificate fields.");
        else for (String difference : differences) report.append(difference).append("\n");
        return report.toString();
    }

    private static void diff(List<String> output, String field, String left, String right) {
        if (!java.util.Objects.equals(left, right)) output.add("[DIFF] " + field + "\n  Left:  " + left + "\n  Right: " + right);
    }

    private static byte[] encoded(X509Certificate certificate) {
        try { return certificate.getEncoded(); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot encode certificate", e); }
    }
}
