package com.cryptocarver.crypto;

import java.security.interfaces.RSAKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Small, deterministic X.509 linter intended for laboratory diagnostics. */
public final class CertificateLinter {
    private CertificateLinter() { }

    public record Finding(String severity, String message) { }

    public static List<Finding> lint(X509Certificate certificate) {
        List<Finding> findings = new ArrayList<>();
        Date now = Date.from(Instant.now());
        if (certificate.getNotAfter().before(now)) findings.add(new Finding("ERROR", "Certificate has expired."));
        else if (certificate.getNotBefore().after(now)) findings.add(new Finding("ERROR", "Certificate is not valid yet."));
        else if (certificate.getNotAfter().getTime() - certificate.getNotBefore().getTime() > 825L * 24 * 60 * 60 * 1000) {
            findings.add(new Finding("WARN", "Validity exceeds 825 days; review the intended certificate profile."));
        }
        String signature = certificate.getSigAlgName().toUpperCase();
        if (signature.contains("MD5") || signature.contains("SHA1")) findings.add(new Finding("ERROR", "Legacy signature algorithm: " + certificate.getSigAlgName()));
        if (certificate.getPublicKey() instanceof RSAKey rsa && rsa.getModulus().bitLength() < 2048) {
            findings.add(new Finding("ERROR", "RSA public key is below 2048 bits."));
        }
        if (certificate.getKeyUsage() == null) findings.add(new Finding("WARN", "KeyUsage extension is absent."));
        try {
            if (certificate.getSubjectAlternativeNames() == null || certificate.getSubjectAlternativeNames().isEmpty()) {
                findings.add(new Finding("WARN", "Subject Alternative Name extension is absent."));
            }
        } catch (Exception e) {
            findings.add(new Finding("WARN", "Could not parse Subject Alternative Names."));
        }
        if (findings.isEmpty()) findings.add(new Finding("INFO", "No baseline lint findings."));
        return List.copyOf(findings);
    }
}
