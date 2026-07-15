package com.cryptoforge.crypto;

import com.cryptoforge.util.DataConverter;

import java.security.Key;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;

/** Human-readable diagnostics for key and certificate material. */
public final class KeyMaterialInspector {
    private KeyMaterialInspector() { }

    public static String describeKey(Key key) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");
        byte[] encoded = key.getEncoded();
        StringBuilder report = heading("KEY MATERIAL REPORT");
        report.append("Kind: ").append(key instanceof PrivateKey ? "Private key" : key instanceof PublicKey ? "Public key" : "Secret key").append('\n');
        report.append("Algorithm: ").append(key.getAlgorithm()).append('\n');
        report.append("Format: ").append(key.getFormat() == null ? "non-exportable" : key.getFormat()).append('\n');
        if (encoded != null) {
            report.append("Encoded size: ").append(encoded.length).append(" bytes\n");
            report.append("SHA-256 fingerprint: ").append(fingerprint(encoded)).append('\n');
        } else {
            report.append("Fingerprint: unavailable (non-exportable key)\n");
        }
        if (key instanceof RSAKey rsa) report.append("RSA modulus: ").append(rsa.getModulus().bitLength()).append(" bits\n");
        if (key instanceof ECKey ec && ec.getParams() != null) {
            report.append("EC field size: ").append(ec.getParams().getCurve().getField().getFieldSize()).append(" bits\n");
        }
        appendGuidance(report, key);
        return report.toString();
    }

    public static String describeCertificate(X509Certificate certificate) {
        if (certificate == null) throw new IllegalArgumentException("Certificate cannot be null");
        StringBuilder report = heading("X.509 CERTIFICATE REPORT");
        report.append("Subject: ").append(certificate.getSubjectX500Principal()).append('\n');
        report.append("Issuer: ").append(certificate.getIssuerX500Principal()).append('\n');
        report.append("Serial: ").append(certificate.getSerialNumber().toString(16).toUpperCase()).append('\n');
        report.append("Validity: ").append(certificate.getNotBefore()).append(" → ").append(certificate.getNotAfter()).append('\n');
        report.append("Signature algorithm: ").append(certificate.getSigAlgName()).append('\n');
        report.append("Certificate SHA-256: ").append(fingerprint(encoded(certificate))).append('\n');
        report.append("\nPUBLIC KEY\n").append(describeKey(certificate.getPublicKey()));
        boolean[] usage = certificate.getKeyUsage();
        if (usage != null) report.append("KeyUsage bits: ").append(java.util.Arrays.toString(usage)).append('\n');
        report.append("Basic constraints: ").append(certificate.getBasicConstraints() < 0 ? "end-entity" : "CA path length " + certificate.getBasicConstraints()).append('\n');
        report.append("\nCERTIFICATE LINT\n");
        for (CertificateLinter.Finding finding : CertificateLinter.lint(certificate)) {
            report.append('[').append(finding.severity()).append("] ").append(finding.message()).append('\n');
        }
        return report.toString();
    }

    public static String fingerprint(byte[] data) {
        try {
            return DataConverter.bytesToHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Verifies a public/private pair by signing a random challenge with the private key. */
    public static boolean matches(PublicKey publicKey, PrivateKey privateKey) {
        if (publicKey == null || privateKey == null || !publicKey.getAlgorithm().equalsIgnoreCase(privateKey.getAlgorithm())) {
            return false;
        }
        try {
            String signatureAlgorithm = signatureAlgorithm(publicKey.getAlgorithm());
            byte[] challenge = new byte[32];
            new java.security.SecureRandom().nextBytes(challenge);
            Signature signer = Signature.getInstance(signatureAlgorithm);
            signer.initSign(privateKey);
            signer.update(challenge);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String signatureAlgorithm(String keyAlgorithm) {
        return switch (keyAlgorithm.toUpperCase()) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            case "DSA" -> "SHA256withDSA";
            case "ED25519", "EDDSA" -> "Ed25519";
            default -> throw new IllegalArgumentException("No signature comparison is available for " + keyAlgorithm);
        };
    }

    private static byte[] encoded(X509Certificate certificate) {
        try { return certificate.getEncoded(); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot encode certificate", e); }
    }

    private static StringBuilder heading(String title) {
        return new StringBuilder("========================================\n").append(title)
                .append("\n========================================\n");
    }

    private static void appendGuidance(StringBuilder report, Key key) {
        if (key instanceof RSAKey rsa && rsa.getModulus().bitLength() < 2048) {
            report.append("⚠️ RSA key below 2048 bits: unsuitable for new use.\n");
        }
        if ("DSA".equalsIgnoreCase(key.getAlgorithm())) report.append("⚠️ DSA is legacy; prefer Ed25519 or ECDSA for new signatures.\n");
        if (key instanceof PrivateKey) report.append("⚠️ Private material shown for this laboratory only; do not export it in production.\n");
    }
}
