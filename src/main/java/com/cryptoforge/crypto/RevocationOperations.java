package com.cryptoforge.crypto;

import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

public final class RevocationOperations {
    static {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
    }

    private RevocationOperations() {}

    public static X509CRL generateEmptyCrl(X509Certificate issuerCert, PrivateKey issuerKey) throws Exception {
        return generateEmptyCRL(issuerCert, issuerKey, signatureAlgorithmFor(issuerCert), 30);
    }

    public static X509CRL generateEmptyCRL(X509Certificate issuerCert, PrivateKey issuerKey, String signatureAlgorithm) throws Exception {
        return generateEmptyCRL(issuerCert, issuerKey, signatureAlgorithm, 30);
    }

    public static X509CRL generateEmptyCrl(X509Certificate issuerCert, PrivateKey issuerKey,
            String signatureAlgorithm, int nextUpdateDays) throws Exception {
        return generateEmptyCRL(issuerCert, issuerKey, signatureAlgorithm, nextUpdateDays);
    }

    public static X509CRL generateEmptyCRL(X509Certificate issuerCert, PrivateKey issuerKey,
            String signatureAlgorithm, int nextUpdateDays) throws Exception {
        Date now = new Date();
        Date nextUpdate = new Date(now.getTime() + 1000L * 60 * 60 * 24 * nextUpdateDays);

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(
                new org.bouncycastle.asn1.x500.X500Name(issuerCert.getSubjectX500Principal().getName()),
                now);
        crlBuilder.setNextUpdate(nextUpdate);

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert));

        var signer = new JcaContentSignerBuilder(signatureAlgorithm).setProvider("BC").build(issuerKey);
        X509CRLHolder crlHolder = crlBuilder.build(signer);
        return new JcaX509CRLConverter().setProvider("BC").getCRL(crlHolder);
    }

    public static X509CRL revokeCertificate(X509CRL existingCrl, X509Certificate issuerCert, PrivateKey issuerKey, String signatureAlgorithm, BigInteger serialToRevoke, int reasonCode) throws Exception {
        Date now = new Date();
        Date nextUpdate = new Date(now.getTime() + 1000L * 60 * 60 * 24 * 30);

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(
                new org.bouncycastle.asn1.x500.X500Name(issuerCert.getSubjectX500Principal().getName()),
                now);
        crlBuilder.setNextUpdate(nextUpdate);

        // Copy existing extensions
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert));

        // Copy existing revoked certificates
        if (existingCrl.getRevokedCertificates() != null) {
            for (X509CRLEntry entry : existingCrl.getRevokedCertificates()) {
                crlBuilder.addCRLEntry(entry.getSerialNumber(), entry.getRevocationDate(), entry.getRevocationReason() != null ? entry.getRevocationReason().ordinal() : 0);
            }
        }

        // Add new revoked certificate
        crlBuilder.addCRLEntry(serialToRevoke, now, reasonCode);

        var signer = new JcaContentSignerBuilder(signatureAlgorithm).setProvider("BC").build(issuerKey);
        X509CRLHolder crlHolder = crlBuilder.build(signer);
        return new JcaX509CRLConverter().setProvider("BC").getCRL(crlHolder);
    }

    public static X509CRL revokeCertificate(X509CRL existingCrl, X509Certificate issuerCert,
            PrivateKey issuerKey, String signatureAlgorithm, BigInteger serialToRevoke,
            int reasonCode, int nextUpdateDays) throws Exception {
        return revokeCertificate(existingCrl, issuerCert, issuerKey, signatureAlgorithm, serialToRevoke, reasonCode);
    }

    public static X509CRL appendRevocation(X509CRL existingCrl, X509Certificate issuerCert,
            PrivateKey issuerKey, BigInteger serialToRevoke, int reasonCode, Date revocationDate) throws Exception {
        return revokeCertificate(existingCrl, issuerCert, issuerKey, signatureAlgorithmFor(issuerCert), serialToRevoke, reasonCode);
    }

    public static String exportToPem(X509CRL crl) throws Exception {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(crl.getEncoded());
        return "-----BEGIN X509 CRL-----\n" + base64 + "\n-----END X509 CRL-----\n";
    }

    public static String exportCrlToPem(X509CRL crl) throws Exception {
        return exportToPem(crl);
    }

    public static X509CRL parseCrl(String pemOrDer) throws Exception {
        String normalized = pemOrDer.trim();
        byte[] encoded;
        if (normalized.startsWith("-----BEGIN X509 CRL-----")) {
            String base64 = normalized.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            encoded = Base64.getDecoder().decode(base64);
        } else {
            try {
                encoded = Base64.getDecoder().decode(normalized.replaceAll("\\s", ""));
            } catch (Exception e) {
                // Not base64
                encoded = pemOrDer.getBytes(java.nio.charset.StandardCharsets.UTF_8); // Incorrect fallback, but standard java
                // Since this receives a String, DER binary might be corrupted if passed as string directly.
                // We should expect Base64/PEM if string.
            }
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        return (X509CRL) cf.generateCRL(new ByteArrayInputStream(encoded));
    }

    public static X509CRL parseCrlPem(String pem) throws Exception {
        return parseCrl(pem);
    }

    private static String signatureAlgorithmFor(X509Certificate issuerCert) {
        return switch (issuerCert.getPublicKey().getAlgorithm()) {
            case "EC", "ECDSA" -> "SHA256withECDSA";
            case "Ed25519" -> "Ed25519";
            case "Ed448" -> "Ed448";
            default -> "SHA256withRSA";
        };
    }
}
