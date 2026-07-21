package com.cryptoforge.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * X.509 Certificate Generator
 *
 * Supports generation of self-signed certificates in multiple formats:
 * - PEM (Base64 encoded)
 * - DER (Binary)
 * - PKCS#12 (.p12/.pfx with private key)
 * - JKS (Java KeyStore)
 *
 * @author Felipe
 */
public class CertificateGenerator {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Certificate configuration class
     */
    public static class CertificateConfig {
        // Subject/Issuer fields
        public String commonName = "localhost";
        public String organization = "Crypto Org";
        public String organizationalUnit = "IT Security";
        public String locality = "Madrid";
        public String state = "Madrid";
        public String country = "ES";
        public String email = null; // Optional - only added if provided

        // Certificate properties
        public int validityDays = 365;
        public String serialNumber = null; // Auto-generated if null
        public String signatureAlgorithm = "SHA256withRSA";

        // Extensions
        public boolean addKeyUsage = true;
        public boolean addExtendedKeyUsage = true;
        public boolean addSubjectAlternativeNames = true;
        public List<String> sanDnsNames = new ArrayList<>();
        public List<String> sanIpAddresses = new ArrayList<>();

        public CertificateConfig() {
            // Default SAN
            sanDnsNames.add("localhost");
            sanIpAddresses.add("127.0.0.1");
        }
    }

    /**
     * Generate self-signed X.509 certificate
     *
     * @param keyPair RSA/DSA/ECDSA key pair
     * @param config  Certificate configuration
     * @return X509Certificate
     */
    public static X509Certificate generateSelfSignedCertificate(
            KeyPair keyPair,
            CertificateConfig config) throws Exception {

        // Build subject/issuer DN
        String dn = buildDistinguishedName(config);
        X500Name issuer = new X500Name(dn);
        X500Name subject = new X500Name(dn);

        // Serial number
        BigInteger serialNumber = config.serialNumber != null ? new BigInteger(config.serialNumber, 16)
                : BigInteger.valueOf(System.currentTimeMillis());

        // Validity period
        Date notBefore = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(notBefore);
        calendar.add(Calendar.DAY_OF_YEAR, config.validityDays);
        Date notAfter = calendar.getTime();

        // Build certificate
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic());

        // Add extensions
        if (config.addKeyUsage) {
            certBuilder.addExtension(
                    Extension.keyUsage,
                    true,
                    new KeyUsage(
                            KeyUsage.digitalSignature |
                                    KeyUsage.keyEncipherment |
                                    KeyUsage.dataEncipherment |
                                    KeyUsage.keyAgreement));
        }

        if (config.addExtendedKeyUsage) {
            certBuilder.addExtension(
                    Extension.extendedKeyUsage,
                    false,
                    new ExtendedKeyUsage(new KeyPurposeId[] {
                            KeyPurposeId.id_kp_serverAuth,
                            KeyPurposeId.id_kp_clientAuth,
                            KeyPurposeId.id_kp_codeSigning,
                            KeyPurposeId.id_kp_emailProtection
                    }));
        }

        if (config.addSubjectAlternativeNames &&
                (!config.sanDnsNames.isEmpty() || !config.sanIpAddresses.isEmpty())) {
            certBuilder.addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    buildSubjectAlternativeNames(config));
        }

        // Basic Constraints (CA:FALSE for end-entity cert)
        certBuilder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(false));

        // Subject Key Identifier
        certBuilder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                createSubjectKeyIdentifier(keyPair.getPublic()));

        // Sign certificate
        ContentSigner signer = new JcaContentSignerBuilder(config.signatureAlgorithm)
                .setProvider("BC")
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);

        // Convert to X509Certificate
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);
    }

    /** Generates a self-signed root CA certificate for laboratory chains. */
    public static X509Certificate generateRootCA(KeyPair keyPair, CertificateConfig config, int pathLength) throws Exception {
        if (pathLength < 0) throw new IllegalArgumentException("CA path length cannot be negative");
        X500Name name = new X500Name(buildDistinguishedName(config));
        Date notBefore = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(notBefore);
        calendar.add(Calendar.DAY_OF_YEAR, config.validityDays);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name,
                config.serialNumber == null ? new BigInteger(128, new SecureRandom()) : new BigInteger(config.serialNumber, 16),
                notBefore, calendar.getTime(), name, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(pathLength));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(keyPair.getPublic()));
        ContentSigner signer = new JcaContentSignerBuilder(config.signatureAlgorithm).setProvider("BC").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }

    /**
     * Build Distinguished Name from config
     */
    public static String buildDistinguishedName(CertificateConfig config) {
        StringBuilder dn = new StringBuilder();

        if (config.commonName != null) {
            dn.append("CN=").append(config.commonName);
        }
        if (config.organizationalUnit != null) {
            if (dn.length() > 0)
                dn.append(", ");
            dn.append("OU=").append(config.organizationalUnit);
        }
        if (config.organization != null) {
            if (dn.length() > 0)
                dn.append(", ");
            dn.append("O=").append(config.organization);
        }
        if (config.locality != null) {
            if (dn.length() > 0)
                dn.append(", ");
            dn.append("L=").append(config.locality);
        }
        if (config.state != null) {
            if (dn.length() > 0)
                dn.append(", ");
            dn.append("ST=").append(config.state);
        }
        if (config.country != null) {
            if (dn.length() > 0)
                dn.append(", ");
            dn.append("C=").append(config.country);
        }
        if (config.email != null) {
            if (dn.length() > 0)
                dn.append(", ");
            dn.append("E=").append(config.email);
        }

        return dn.toString();
    }

    /**
     * Build Subject Alternative Names extension
     */
    public static GeneralNames buildSubjectAlternativeNames(CertificateConfig config) {
        List<GeneralName> names = new ArrayList<>();

        // DNS names
        for (String dns : config.sanDnsNames) {
            names.add(new GeneralName(GeneralName.dNSName, dns));
        }

        // IP addresses
        for (String ip : config.sanIpAddresses) {
            names.add(new GeneralName(GeneralName.iPAddress, ip));
        }

        return new GeneralNames(names.toArray(new GeneralName[0]));
    }

    /**
     * Create Subject Key Identifier
     */
    private static SubjectKeyIdentifier createSubjectKeyIdentifier(PublicKey publicKey) throws Exception {
        byte[] encoded = publicKey.getEncoded();
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash = sha1.digest(encoded);
        return new SubjectKeyIdentifier(hash);
    }

    /**
     * Export certificate to PEM format
     *
     * @param certificate X509 Certificate
     * @return PEM-encoded string
     */
    public static String exportCertificatePEM(X509Certificate certificate) throws Exception {
        byte[] encoded = certificate.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);

        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE-----\n");

        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64.substring(i, Math.min(i + 64, base64.length()))).append("\n");
        }

        pem.append("-----END CERTIFICATE-----\n");
        return pem.toString();
    }

    /**
     * Export certificate to DER format (binary)
     *
     * @param certificate X509 Certificate
     * @return DER-encoded bytes
     */
    public static byte[] exportCertificateDER(X509Certificate certificate) throws Exception {
        return certificate.getEncoded();
    }

    /**
     * Export certificate and private key to PKCS#12 format (.p12/.pfx)
     *
     * @param certificate X509 Certificate
     * @param privateKey  Private key
     * @param password    Password to protect the PKCS#12 file
     * @param alias       Alias for the certificate/key entry
     * @return PKCS#12 keystore as byte array
     */
    public static byte[] exportCertificatePKCS12(
            X509Certificate certificate,
            PrivateKey privateKey,
            String password,
            String alias) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);

        Certificate[] chain = new Certificate[] { certificate };
        keyStore.setKeyEntry(alias, privateKey, password.toCharArray(), chain);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        keyStore.store(baos, password.toCharArray());

        return baos.toByteArray();
    }

    /**
     * Export certificate and private key to JKS format (Java KeyStore)
     *
     * @param certificate X509 Certificate
     * @param privateKey  Private key
     * @param password    Password to protect the keystore
     * @param alias       Alias for the certificate/key entry
     * @return JKS keystore as byte array
     */
    public static byte[] exportCertificateJKS(
            X509Certificate certificate,
            PrivateKey privateKey,
            String password,
            String alias) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        Certificate[] chain = new Certificate[] { certificate };
        keyStore.setKeyEntry(alias, privateKey, password.toCharArray(), chain);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        keyStore.store(baos, password.toCharArray());

        return baos.toByteArray();
    }

    /**
     * Save certificate to file
     *
     * @param certificate X509 Certificate
     * @param filePath    Output file path
     * @param format      Format: "PEM", "DER"
     */
    public static void saveCertificateToFile(
            X509Certificate certificate,
            String filePath,
            String format) throws Exception {

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            if (format.equalsIgnoreCase("PEM")) {
                String pem = exportCertificatePEM(certificate);
                fos.write(pem.getBytes());
            } else if (format.equalsIgnoreCase("DER")) {
                byte[] der = exportCertificateDER(certificate);
                fos.write(der);
            } else {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }
        }
    }

    /**
     * Save PKCS#12 to file
     *
     * @param certificate X509 Certificate
     * @param privateKey  Private key
     * @param filePath    Output file path (.p12 or .pfx)
     * @param password    Password
     * @param alias       Alias
     */
    public static void savePKCS12ToFile(
            X509Certificate certificate,
            PrivateKey privateKey,
            String filePath,
            String password,
            String alias) throws Exception {

        byte[] pkcs12 = exportCertificatePKCS12(certificate, privateKey, password, alias);

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(pkcs12);
        }
    }

    /**
     * Generate Certificate Signing Request (CSR)
     *
     * @param keyPair Key pair
     * @param config  Certificate configuration
     * @return PKCS#10 CSR as PEM string
     */
    public static String generateCSR(KeyPair keyPair, CertificateConfig config) throws Exception {
        String dn = buildDistinguishedName(config);
        X500Name subject = new X500Name(dn);

        PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(subject,
                keyPair.getPublic());

        // Request SANs through the PKCS#9 extensionRequest attribute so a CA can
        // honor them. They are not certificate extensions until the CA issues it.
        if (config.addSubjectAlternativeNames && (!config.sanDnsNames.isEmpty() || !config.sanIpAddresses.isEmpty())) {
            ExtensionsGenerator extensions = new ExtensionsGenerator();
            extensions.addExtension(Extension.subjectAlternativeName, false, buildSubjectAlternativeNames(config));
            csrBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions.generate());
        }

        ContentSigner signer = new JcaContentSignerBuilder(config.signatureAlgorithm)
                .setProvider("BC")
                .build(keyPair.getPrivate());

        PKCS10CertificationRequest csr = csrBuilder.build(signer);

        byte[] encoded = csr.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);

        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE REQUEST-----\n");

        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64.substring(i, Math.min(i + 64, base64.length()))).append("\n");
        }

        pem.append("-----END CERTIFICATE REQUEST-----\n");

        return pem.toString();
    }



    /**
     * Parse PKCS#10 CSR from PEM string
     *
     * @param pemCsr CSR in PEM format
     * @return PKCS10CertificationRequest
     */
    public static PKCS10CertificationRequest parseCSR(String pemCsr) throws Exception {
        if (pemCsr == null || pemCsr.isEmpty()) {
            throw new Exception("CSR input is empty");
        }

        String cleaned = pemCsr.trim();
        if (!cleaned.contains("-----BEGIN CERTIFICATE REQUEST-----")) {
            cleaned = "-----BEGIN CERTIFICATE REQUEST-----\n" + cleaned + "\n-----END CERTIFICATE REQUEST-----";
        }

        String b64 = cleaned.replaceAll("-----BEGIN CERTIFICATE REQUEST-----", "")
                            .replaceAll("-----END CERTIFICATE REQUEST-----", "")
                            .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(b64);
        return new PKCS10CertificationRequest(encoded);
    }

    /**
     * Get detailed certificate information
     *
     * @param certificate X509 Certificate
     * @return Formatted string with certificate details
     */
    public static String getCertificateInfo(X509Certificate certificate) {
        StringBuilder info = new StringBuilder();

        info.append("X.509 Certificate Information\n");
        info.append("===============================\n\n");

        info.append("Version: ").append(certificate.getVersion()).append("\n");
        info.append("Serial Number: ").append(certificate.getSerialNumber().toString(16).toUpperCase()).append("\n\n");

        info.append("Issuer: ").append(certificate.getIssuerDN()).append("\n");
        info.append("Subject: ").append(certificate.getSubjectDN()).append("\n\n");

        info.append("Valid From: ").append(certificate.getNotBefore()).append("\n");
        info.append("Valid To: ").append(certificate.getNotAfter()).append("\n\n");

        info.append("Signature Algorithm: ").append(certificate.getSigAlgName()).append("\n");
        info.append("Public Key Algorithm: ").append(certificate.getPublicKey().getAlgorithm()).append("\n\n");

        info.append("Public Key:\n");
        byte[] pubKeyBytes = certificate.getPublicKey().getEncoded();
        info.append(formatHexDump(pubKeyBytes, 16)).append("\n\n");

        info.append("Signature:\n");
        byte[] signature = certificate.getSignature();
        info.append(formatHexDump(signature, 16)).append("\n");

        return info.toString();
    }

    /**
     * Format bytes as hex dump
     */
    private static String formatHexDump(byte[] data, int bytesPerLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02X ", data[i]));
            if ((i + 1) % bytesPerLine == 0) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Verify certificate signature
     *
     * @param certificate X509 Certificate
     * @return true if signature is valid
     */
    public static boolean verifyCertificateSignature(X509Certificate certificate) {
        try {
            // Prefer the JCA-selected provider: it interoperates with the
            // provider that materialized the certificate from CertificateFactory.
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (Exception e) {
            try {
                certificate.verify(certificate.getPublicKey(), "BC");
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /**
     * Get signature algorithm options for key type
     *
     * @param keyAlgorithm Key algorithm (RSA, DSA, ECDSA)
     * @return List of compatible signature algorithms
     */
    public static List<String> getSignatureAlgorithms(String keyAlgorithm) {
        List<String> algorithms = new ArrayList<>();

        switch (keyAlgorithm.toUpperCase()) {
            case "RSA":
                algorithms.add("SHA1withRSA");
                algorithms.add("SHA256withRSA");
                algorithms.add("SHA384withRSA");
                algorithms.add("SHA512withRSA");
                algorithms.add("SHA3-256withRSA");
                algorithms.add("SHA3-384withRSA");
                algorithms.add("SHA3-512withRSA");
                break;

            case "DSA":
                algorithms.add("SHA1withDSA");
                algorithms.add("SHA256withDSA");
                algorithms.add("SHA384withDSA");
                algorithms.add("SHA512withDSA");
                break;

            case "ECDSA":
            case "EC":
                algorithms.add("SHA1withECDSA");
                algorithms.add("SHA256withECDSA");
                algorithms.add("SHA384withECDSA");
                algorithms.add("SHA512withECDSA");
                algorithms.add("SHA3-256withECDSA");
                algorithms.add("SHA3-384withECDSA");
                algorithms.add("SHA3-512withECDSA");
                break;

            default:
                algorithms.add("SHA256withRSA");
        }

        return algorithms;
    }

    /**
     * Parse X.509 certificate from PEM string
     *
     * @param pemCert Certificate in PEM format
     * @return X509Certificate
     */
    public static X509Certificate parseCertificate(String pemCert) throws Exception {
        if (pemCert == null || pemCert.isEmpty()) {
            throw new Exception("Certificate input is empty");
        }

        // Clean input: remove invisible characters but keep structure if possible?
        // Actually CertificateFactory needs the headers.

        String cleaned = pemCert.trim();

        // Check if input looks like a Distinguished Name (DN) instead of a certificate
        if (cleaned.startsWith("CN=") || cleaned.startsWith("O=") || cleaned.startsWith("C=") ||
                cleaned.startsWith("OU=") || cleaned.contains(", O=") || cleaned.contains(", C=")) {
            throw new Exception(
                    "Input appears to be a Distinguished Name (DN), not a certificate.\nPlease paste the full PEM encoded certificate (starting with -----BEGIN CERTIFICATE-----).");
        }

        // Check for headers
        if (!cleaned.contains("-----BEGIN CERTIFICATE-----")) {
            // Assume raw Base64 and wrap it
            cleaned = "-----BEGIN CERTIFICATE-----\n" + cleaned + "\n-----END CERTIFICATE-----";
        }

        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(
                cleaned.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return (X509Certificate) factory.generateCertificate(bais);
    }

    /**
     * Parse a chain of X.509 certificates from a PEM string.
     *
     * @param pemChain Certificate chain in PEM format
     * @return List of X509Certificate
     */
    public static List<X509Certificate> parseCertificateChain(String pemChain) throws Exception {
        if (pemChain == null || pemChain.isEmpty()) {
            throw new Exception("Certificate input is empty");
        }

        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(
                pemChain.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        List<X509Certificate> chain = new ArrayList<>();
        Collection<? extends Certificate> certs = factory.generateCertificates(bais);
        for (Certificate cert : certs) {
            chain.add((X509Certificate) cert);
        }
        return chain;
    }

    /**
     * Validate a certificate chain
     *
     * @param chain List of X509Certificates (End-entity first, Root last, or
     *              unordered)
     * @return ValidationResult with status and details
     */
    public static class ChainValidationResult {
        public boolean isValid;
        public String message;
        public List<String> details = new ArrayList<>();

        public ChainValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }
    }

    /**
     * Validation result for a single certificate
     */
    public static class CertificateValidationResult {
        public boolean isValid;
        public String status; // Checks performed (e.g. "Signature Verified (Issuer)", "Dates Valid")
        public String message; // Detailed message
        public List<String> details = new ArrayList<>();

        public CertificateValidationResult(boolean isValid, String status, String message) {
            this.isValid = isValid;
            this.status = status;
            this.message = message;
        }
    }

    /**
     * Validate a single certificate, optionally against an issuer
     *
     * @param cert   The certificate to validate
     * @param issuer The issuer certificate (optional, can be null)
     * @return Validation result
     */
    public static CertificateValidationResult validateCertificate(X509Certificate cert, X509Certificate issuer) {
        CertificateValidationResult result = new CertificateValidationResult(true, "Valid",
                "Certificate parses correctly");

        try {
            // 1. Check validity dates
            cert.checkValidity();
            result.details.add("Dates: VALID (" + cert.getNotBefore() + " to " + cert.getNotAfter() + ")");
        } catch (java.security.cert.CertificateExpiredException e) {
            result.isValid = false;
            result.status = "Expired";
            result.message = "Certificate expired on " + cert.getNotAfter();
            result.details.add("Dates: EXPIRED");
        } catch (java.security.cert.CertificateNotYetValidException e) {
            result.isValid = false;
            result.status = "Not Yet Valid";
            result.message = "Certificate valid from " + cert.getNotBefore();
            result.details.add("Dates: NOT YET VALID");
        }

        // 2. Verify Signature
        try {
            if (issuer != null) {
                // Verify against provided issuer
                cert.verify(issuer.getPublicKey());
                result.details.add("Signature: VERIFIED (Signed by provided Issuer)");

                // Also nice to check if issuer is valid
                try {
                    issuer.checkValidity();
                    result.details.add("Issuer: VALID Dates");
                } catch (Exception e) {
                    result.details.add("Issuer: INVALID Dates (" + e.getMessage() + ")");
                    // We don't fail the main cert validation just because issuer is expired, but
                    // good to note
                }

            } else {
                // No issuer provided. Check if self-signed.
                if (isSelfSigned(cert)) {
                    // Verify against itself
                    if (verifyCertificateSignature(cert)) {
                        result.details.add("Signature: VERIFIED (Self-Signed)");
                    } else {
                        result.isValid = false;
                        result.status = "Invalid Signature";
                        result.message = "Self-signed signature verification failed";
                        result.details.add("Signature: INVALID (Self-Signed)");
                    }
                } else {
                    // Not self-signed, and no issuer provided.
                    // We can only validate dates.
                    result.status = "Dates Valid (Incomplete Chain)";
                    result.message = "Certificate is valid, but issuer is missing to verify signature.";
                    result.details.add("Signature: NOT VERIFIED (Issuer Not Provided)");
                }
            }
        } catch (Exception e) {
            result.isValid = false;
            result.status = "Invalid Signature";
            result.message = "Signature Verification Failed: " + e.getMessage();
            result.details.add("Signature: INVALID");
        }

        return result;
    }

    public static ChainValidationResult validateCertificateChain(List<X509Certificate> chain) {
        return validateCertificateChain(chain, null);
    }

    public static ChainValidationResult validateCertificateChain(List<X509Certificate> chain, java.util.Date validationDate) {
        if (chain == null || chain.isEmpty()) {
            return new ChainValidationResult(false, "Chain is empty");
        }

        ChainValidationResult result = new ChainValidationResult(true, "Chain is valid");

        try {
            Set<java.security.cert.TrustAnchor> anchors = new java.util.HashSet<>();
            List<X509Certificate> intermediates = new ArrayList<>();

            // Collect all subjects to determine issuers
            Set<javax.security.auth.x500.X500Principal> allSubjects = new java.util.HashSet<>();
            Set<javax.security.auth.x500.X500Principal> allIssuers = new java.util.HashSet<>();

            for (X509Certificate cert : chain) {
                if (isSelfSigned(cert)) {
                    anchors.add(new java.security.cert.TrustAnchor(cert, null));
                } else {
                    intermediates.add(cert);
                    allIssuers.add(cert.getIssuerX500Principal());
                }
                if (!allSubjects.add(cert.getSubjectX500Principal())) {
                    return new ChainValidationResult(false, "Duplicate certificates found in chain: " + cert.getSubjectX500Principal());
                }
            }

            // Identify the target (leaf) certificate: A certificate whose subject is not an issuer for any other cert in the chain,
            // unless it's a self-signed chain of 1, in which case it is both.
            List<X509Certificate> leaves = new ArrayList<>();
            for (X509Certificate cert : chain) {
                if (!allIssuers.contains(cert.getSubjectX500Principal())) {
                    leaves.add(cert);
                }
            }

            if (leaves.size() == 0 && chain.size() == 1 && anchors.size() == 1) {
                leaves.add(chain.get(0));
            } else if (leaves.size() != 1) {
                return new ChainValidationResult(false, "Ambiguous target certificate: expected exactly 1 leaf, found " + leaves.size());
            }

            X509Certificate endEntity = leaves.get(0);

            if (anchors.isEmpty()) {
                return new ChainValidationResult(false, "Incomplete Chain: no self-signed CA root was provided");
            }

            java.security.cert.CertStore certStore = java.security.cert.CertStore.getInstance(
                    "Collection",
                    new java.security.cert.CollectionCertStoreParameters(intermediates),
                    "BC"
            );

            java.security.cert.X509CertSelector selector = new java.security.cert.X509CertSelector();
            selector.setCertificate(endEntity);
            java.security.cert.PKIXBuilderParameters pkixParams = new java.security.cert.PKIXBuilderParameters(anchors, selector);
            pkixParams.addCertStore(certStore);
            pkixParams.setRevocationEnabled(false);

            if (validationDate != null) {
                pkixParams.setDate(validationDate);
            }

            java.security.cert.CertPathBuilder builder = java.security.cert.CertPathBuilder.getInstance("PKIX", "BC");
            java.security.cert.PKIXCertPathBuilderResult pkixResult =
                    (java.security.cert.PKIXCertPathBuilderResult) builder.build(pkixParams);

            result.details.add("PKIX Validation Successful");
            result.details.add("Target: " + endEntity.getSubjectX500Principal());
            result.details.add("Trust Anchor: " + pkixResult.getTrustAnchor().getTrustedCert().getSubjectX500Principal());
            result.details.add("Path Length: " + pkixResult.getCertPath().getCertificates().size());
        } catch (java.security.cert.CertPathBuilderException e) {
            result.isValid = false;
            result.message = "Broken Chain";
            result.details.add("PKIX Validation Failed: " + e.getMessage());
        } catch (Exception e) {
            result.isValid = false;
            result.message = "PKIX Setup Failed";
            result.details.add("Error: " + e.getMessage());
        }

        return result;
    }

    private static boolean isSelfSigned(X509Certificate cert) {
        return cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());
    }
}
