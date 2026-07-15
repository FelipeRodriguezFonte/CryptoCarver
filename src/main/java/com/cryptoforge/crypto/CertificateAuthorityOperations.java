package com.cryptoforge.crypto;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

/** Laboratory CA operations: validates a CSR and issues an end-entity certificate. */
public final class CertificateAuthorityOperations {
    static {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
    }
    private CertificateAuthorityOperations() { }

    /** Returns a safe default issuer signature algorithm for a private key. */
    public static String suggestSignatureAlgorithm(PrivateKey privateKey) {
        if (privateKey == null) throw new IllegalArgumentException("Issuer private key is required");
        return switch (privateKey.getAlgorithm().toUpperCase()) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            case "ED25519", "EDDSA" -> "Ed25519";
            case "DSA" -> "SHA256withDSA";
            default -> throw new IllegalArgumentException("No default signature algorithm for " + privateKey.getAlgorithm());
        };
    }

    public static X509Certificate issueFromCsr(PKCS10CertificationRequest csr, X509Certificate issuerCertificate,
            PrivateKey issuerPrivateKey, int validityDays, String signatureAlgorithm) throws Exception {
        return issueFromCsr(csr, issuerCertificate, issuerPrivateKey, validityDays, signatureAlgorithm, false, 0);
    }

    /** Issues an intermediate CA from a signed CSR. */
    public static X509Certificate issueIntermediateCaFromCsr(PKCS10CertificationRequest csr, X509Certificate issuerCertificate,
            PrivateKey issuerPrivateKey, int validityDays, String signatureAlgorithm, int pathLength) throws Exception {
        if (pathLength < 0) throw new IllegalArgumentException("Intermediate CA path length cannot be negative");
        return issueFromCsr(csr, issuerCertificate, issuerPrivateKey, validityDays, signatureAlgorithm, true, pathLength);
    }

    private static X509Certificate issueFromCsr(PKCS10CertificationRequest csr, X509Certificate issuerCertificate,
            PrivateKey issuerPrivateKey, int validityDays, String signatureAlgorithm, boolean intermediateCa, int pathLength) throws Exception {
        if (csr == null || issuerCertificate == null || issuerPrivateKey == null) throw new IllegalArgumentException("CSR, issuer certificate and issuer private key are required");
        if (validityDays <= 0) throw new IllegalArgumentException("Validity must be positive");
        validateIssuer(issuerCertificate, issuerPrivateKey);
        JcaPKCS10CertificationRequest request = new JcaPKCS10CertificationRequest(csr).setProvider("BC");
        if (!csr.isSignatureValid(new JcaContentVerifierProviderBuilder().setProvider("BC").build(request.getPublicKey()))) {
            throw new IllegalArgumentException("CSR signature is invalid");
        }
        Date notBefore = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(notBefore);
        calendar.add(Calendar.DAY_OF_YEAR, validityDays);
        Date requestedNotAfter = calendar.getTime();
        Date notAfter = requestedNotAfter.before(issuerCertificate.getNotAfter()) ? requestedNotAfter : issuerCertificate.getNotAfter();
        var builder = new JcaX509v3CertificateBuilder(issuerCertificate, new BigInteger(128, new SecureRandom()),
                notBefore, notAfter, csr.getSubject(), request.getPublicKey());
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, intermediateCa ? new BasicConstraints(pathLength) : new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, intermediateCa
                ? new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature)
                : new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(request.getPublicKey()));
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(issuerCertificate));
        copyRequestedSan(csr, builder);
        var signer = new JcaContentSignerBuilder(signatureAlgorithm).setProvider("BC").build(issuerPrivateKey);
        return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }

    private static void copyRequestedSan(PKCS10CertificationRequest csr, JcaX509v3CertificateBuilder builder) throws Exception {
        var attributes = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
        if (attributes.length == 0) return;
        Extensions extensions = Extensions.getInstance(attributes[0].getAttrValues().getObjectAt(0));
        Extension san = extensions.getExtension(Extension.subjectAlternativeName);
        if (san != null) builder.addExtension(Extension.subjectAlternativeName, san.isCritical(), san.getParsedValue());
    }

    private static void validateIssuer(X509Certificate issuerCertificate, PrivateKey issuerPrivateKey) throws Exception {
        issuerCertificate.checkValidity();
        if (issuerCertificate.getBasicConstraints() < 0) throw new IllegalArgumentException("Issuer certificate is not a CA");
        boolean[] usage = issuerCertificate.getKeyUsage();
        if (usage != null && (usage.length <= 5 || !usage[5])) throw new IllegalArgumentException("Issuer certificate lacks keyCertSign usage");
        if (!KeyMaterialInspector.matches(issuerCertificate.getPublicKey(), issuerPrivateKey)) {
            throw new IllegalArgumentException("Issuer private key does not match the issuer certificate");
        }
    }
}
