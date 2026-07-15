package com.cryptoforge.crypto;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.token.AbstractKeyStoreTokenConnection;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.JKSSignatureToken;
import eu.europa.esig.dss.token.KSPrivateKeyEntry;
import eu.europa.esig.dss.token.KeyStoreSignatureTokenConnection;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.List;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XML Security Operations using SD-DSS (XAdES)
 */
public class XMLSignatureOperations {

    private static AbstractKeyStoreTokenConnection createToken(String path, String password) throws java.io.IOException {
        // 1. Try PKCS12
        try {
            Pkcs12SignatureToken p12Token = new Pkcs12SignatureToken(new File(path), new PasswordProtection(password.toCharArray()));
            p12Token.getKeys(); 
            return p12Token;
        } catch (Exception e) {
            // 2. Try JKS
            try {
                JKSSignatureToken jksToken = new JKSSignatureToken(new File(path), new PasswordProtection(password.toCharArray()));
                jksToken.getKeys();
                return jksToken;
            } catch (Exception ex) {
                // 3. Try JCEKS
                try {
                    // SD-DSS KeyStoreSignatureTokenConnection supports explicit KeyStore type
                    return new eu.europa.esig.dss.token.KeyStoreSignatureTokenConnection(
                        new File(path), 
                        "JCEKS", 
                        new PasswordProtection(password.toCharArray())
                    );
                } catch (Exception exc) {
                    // All failed
                    throw new java.io.IOException("Could not open KeyStore as PKCS12, JKS, or JCEKS. " + e.getMessage(), exc);
                }
            }
        }
    }

    /**
     * Get available keys from KeyStore
     * @return List of key descriptions (Alias + Subject DN)
     */
    public static List<String> getKeyAliases(String p12Path, String password) throws Exception {
        try (AbstractKeyStoreTokenConnection token = createToken(p12Path, password)) {
            List<DSSPrivateKeyEntry> keys = token.getKeys();
            List<String> aliases = new java.util.ArrayList<>();
            
            for (DSSPrivateKeyEntry key : keys) {
                String alias = null;
                
                // 1. Try to get Alias from KSPrivateKeyEntry
                if (key instanceof eu.europa.esig.dss.token.KSPrivateKeyEntry) {
                    alias = ((eu.europa.esig.dss.token.KSPrivateKeyEntry) key).getAlias();
                }
                
                // 2. Get readable Subject from underlying X509Certificate
                String subjectLabel = "Unknown Subject";
                String cn = "Unknown CN";
                
                try {
                    // Access standard Java X509Certificate
                    java.security.cert.X509Certificate x509 = key.getCertificate().getCertificate();
                    String dn = x509.getSubjectX500Principal().getName();
                    subjectLabel = dn;
                    
                    // Extract Common Name (CN)
                    // Simple parsing, assuming standard formatting (e.g. "CN=Name, O=Org")
                    // Note: LdapName class would be more robust but this suffices for display
                    if (dn.contains("CN=")) {
                        int start = dn.indexOf("CN=") + 3;
                        int end = dn.indexOf(",", start);
                        if (end == -1) end = dn.length();
                        cn = dn.substring(start, end);
                    } else {
                        cn = dn; // Fallback if no CN
                    }
                } catch (Exception e) {
                    // Fallback to DSS string if standard cert access fails
                    subjectLabel = key.getCertificate().getSubject().toString();
                }

                // 3. Format the display string
                if (alias != null && !alias.isEmpty()) {
                    // Format: "Alias (CN)"
                    aliases.add(alias + " (" + cn + ")");
                } else {
                    // Fallback: "CN (DN)"
                    aliases.add(cn + " (" + subjectLabel + ")");
                }
            }
            return aliases;
        } catch (Exception e) {
            throw new Exception("Failed to load keystore: " + e.getMessage() + ". Check password and file type.", e);
        }
    }

    /**
     * Sign an XML document using XAdES with specific level and key index
     */
    public static String signXAdES(String xmlContent, String p12Path, String password, int keyIndex, String level, String tsaUrl) throws Exception {
        return signXAdES(xmlContent, p12Path, password, keyIndex, level, tsaUrl, "ENVELOPED");
    }

    /**
     * Sign an XML document using XAdES with explicit packaging.
     * Supported values are ENVELOPED, ENVELOPING and DETACHED.
     */
    public static String signXAdES(String xmlContent, String p12Path, String password, int keyIndex,
                                   String level, String tsaUrl, String packaging) throws Exception {
        // 1. Prepare Document
        DSSDocument toSignDocument = new InMemoryDocument(xmlContent.getBytes("UTF-8"));

        // 2. Load Token (Key)
        try (AbstractKeyStoreTokenConnection token = createToken(p12Path, password)) {
            
            List<DSSPrivateKeyEntry> keys = token.getKeys();
            if (keys.isEmpty()) {
                throw new Exception("No keys found in the keystore");
            }
            if (keyIndex < 0 || keyIndex >= keys.size()) {
                throw new Exception("Invalid key index selected");
            }
            DSSPrivateKeyEntry privateKey = keys.get(keyIndex);

            // 3. Prepare Service
            CommonCertificateVerifier verifier = new CommonCertificateVerifier();
            
            // The signing certificate is deliberately not made a trust anchor here.
            // Trust belongs to the validation policy, configured by the verifier.

            XAdESService service = new XAdESService(verifier);
            
            // Configure TSA if URL provided
            if (tsaUrl != null && !tsaUrl.isEmpty()) {
                OnlineTSPSource tspSource = new OnlineTSPSource(tsaUrl);
                service.setTspSource(tspSource);
            }

            // 4. Set Parameters
            XAdESSignatureParameters parameters = new XAdESSignatureParameters();
            
            // Parse level
            SignatureLevel signatureLevel = SignatureLevel.XAdES_BASELINE_B; // default
            if (level != null) {
                try {
                    // Try exact match first
                    signatureLevel = SignatureLevel.valueOf(level);
                } catch (IllegalArgumentException e) {
                    // Try to match partial (e.g. "XAdES-BASELINE-LT" from combo)
                    // SD-DSS enums are like XAdES_BASELINE_B, XAdES_BASELINE_T, etc.
                    String normalized = level.replace("-", "_");
                    try {
                        signatureLevel = SignatureLevel.valueOf(normalized);
                    } catch (IllegalArgumentException ex) {
                        // Keep default
                    }
                }
            }
            parameters.setSignatureLevel(signatureLevel);
            
            parameters.setSignaturePackaging(parsePackaging(packaging));
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(privateKey.getCertificate());
            parameters.setCertificateChain(privateKey.getCertificateChain());

            // 5. Create Data to Sign
            ToBeSigned dataToSign;
            try {
                dataToSign = service.getDataToSign(toSignDocument, parameters);
            } catch (Exception e) {
                System.out.println("DEBUG: XML Sign Exception: " + e.getMessage());
                
                // If validation fails (e.g. self-signed certs), try without including certificate details in TBS
                // "Signing-certificate token was not found" means SD-DSS rejected the cert for the signature property
                if (e.getMessage() != null && (
                    e.getMessage().contains("Signing-certificate token was not found") || 
                    e.getMessage().contains("Unable to verify its validity") ||
                    e.getMessage().contains("Revocation data is missing"))) {
                    
                    System.out.println("DEBUG: Attempting fallback - GenerateTBSWithoutCertificate=true");
                    parameters.setGenerateTBSWithoutCertificate(true);
                    
                    // Crucial: Clear the signing certificate from parameters so SD-DSS doesn't try to validate it again
                    // The key itself is passed to the token.sign method later
                    parameters.setSigningCertificate(null);
                    
                    dataToSign = service.getDataToSign(toSignDocument, parameters);
                } else {
                    throw e;
                }
            }

            // 6. Sign
            SignatureValue signatureValue = token.sign(dataToSign, parameters.getDigestAlgorithm(), privateKey);

            // 7. Sign Document
            DSSDocument signedDocument = service.signDocument(toSignDocument, parameters, signatureValue);

            // 8. Return result
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                signedDocument.writeTo(baos);
                return new String(baos.toByteArray(), "UTF-8");
            }
        }
    }

    /**
     * Sign an XML document using XAdES-BASELINE-B (Default)
     */
    public static String signXAdES(String xmlContent, String p12Path, String password) throws Exception {
        return signXAdES(xmlContent, p12Path, password, 0, "XAdES_BASELINE_B", null);
    }

    private static SignaturePackaging parsePackaging(String packaging) {
        if (packaging == null || packaging.isBlank()) return SignaturePackaging.ENVELOPED;
        try {
            return SignaturePackaging.valueOf(packaging.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported XAdES packaging: " + packaging
                    + ". Use ENVELOPED, ENVELOPING or DETACHED.");
        }
    }

    /**
     * Verify an XML document (XAdES)
     * 
     * @param xmlContent The signed XML content
     * @return Verification report summary
     */
    public static String verifyXAdES(String xmlContent) throws Exception {
        return verifyXAdES(xmlContent, null, null);
    }

    /**
     * Verifies integrity and, when a truststore is supplied, certificate trust.
     * A missing truststore means that the report must be interpreted as an
     * integrity/format result only, not as a trusted signature decision.
     */
    public static String verifyXAdES(String xmlContent, String trustStorePath, String trustStorePassword) throws Exception {
        DSSDocument signedDocument = new InMemoryDocument(xmlContent.getBytes("UTF-8"));

        // Configure Validator
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        boolean trustConfigured = trustStorePath != null && !trustStorePath.isBlank();
        if (trustConfigured) {
            verifier.setTrustedCertSources(loadTrustedCertificates(trustStorePath, trustStorePassword));
            verifier.setCrlSource(new OnlineCRLSource());
            verifier.setOcspSource(new OnlineOCSPSource());
            verifier.setCheckRevocationForUntrustedChains(true);
        }
        
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(signedDocument);
        validator.setCertificateVerifier(verifier);
        
        // Validate
        Reports reports = validator.validateDocument();
        
        // Simple report extraction
        StringBuilder sb = new StringBuilder();
        sb.append("--- Verification Report ---\n");
        sb.append("Trust Policy: ").append(trustConfigured ? "Configured truststore" : "No truststore (integrity only)").append("\n");
        sb.append("Indication: ").append(reports.getSimpleReport().getIndication(reports.getSimpleReport().getFirstSignatureId())).append("\n");
        sb.append("SubIndication: ").append(reports.getSimpleReport().getSubIndication(reports.getSimpleReport().getFirstSignatureId())).append("\n");
        sb.append("Signature Format: ").append(reports.getSimpleReport().getSignatureFormat(reports.getSimpleReport().getFirstSignatureId())).append("\n");
        sb.append("Signed By: ").append(reports.getSimpleReport().getSignedBy(reports.getSimpleReport().getFirstSignatureId())).append("\n");
        
        return sb.toString();
    }

    /**
     * Reads signed XML locally and reports its XMLDSig/XAdES structure. This is
     * an inspection aid only: it does not verify integrity or certificate trust.
     */
    public static String inspectSignedXml(String xmlContent) throws Exception {
        if (xmlContent == null || xmlContent.isBlank()) throw new IllegalArgumentException("Signed XML is required");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        disableExternalEntities(factory);
        Document document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlContent)));
        Element root = document.getDocumentElement();
        NodeList signatures = document.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature");

        StringBuilder report = new StringBuilder("--- Signed XML Inspector (structure only) ---\n");
        report.append("Document root: ").append(nodeName(root)).append("\n");
        report.append("Document bytes (UTF-8): ").append(xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).append("\n");
        report.append("XMLDSig signatures: ").append(signatures.getLength()).append("\n");
        if (signatures.getLength() == 0) {
            report.append("No ds:Signature element was found. This is not an XMLDSig/XAdES signed XML document.\n");
            return report.toString();
        }

        for (int index = 0; index < signatures.getLength(); index++) {
            Element signature = (Element) signatures.item(index);
            report.append("\n[Signature ").append(index + 1).append("]\n");
            appendValue(report, "Id", signature.getAttribute("Id"));
            Element signedInfo = firstDescendant(signature, "SignedInfo");
            if (signedInfo != null) {
                appendAlgorithm(report, "Canonicalization", firstDescendant(signedInfo, "CanonicalizationMethod"));
                appendAlgorithm(report, "Signature method", firstDescendant(signedInfo, "SignatureMethod"));
                NodeList references = signedInfo.getElementsByTagNameNS("*", "Reference");
                report.append("References: ").append(references.getLength()).append("\n");
                for (int refIndex = 0; refIndex < references.getLength(); refIndex++) {
                    Element reference = (Element) references.item(refIndex);
                    report.append("  - Reference ").append(refIndex + 1).append(": URI=")
                            .append(emptyAs(reference.getAttribute("URI"), "(whole document)")).append("\n");
                    Element digestMethod = firstDescendant(reference, "DigestMethod");
                    appendIndentedAlgorithm(report, "Digest", digestMethod);
                    NodeList transforms = reference.getElementsByTagNameNS("*", "Transform");
                    report.append("    Transforms: ").append(transforms.getLength()).append("\n");
                    for (int transformIndex = 0; transformIndex < transforms.getLength(); transformIndex++) {
                        Element transform = (Element) transforms.item(transformIndex);
                        report.append("      * ").append(emptyAs(transform.getAttribute("Algorithm"), "(not declared)")).append("\n");
                    }
                    Element digestValue = firstDescendant(reference, "DigestValue");
                    if (digestValue != null) {
                        String digest = text(digestValue);
                        report.append("    Digest value: ").append(abbreviate(digest, 80)).append(" (Base64 chars: ").append(digest.length()).append(")\n");
                    }
                }
            }
            Element signatureValue = firstDescendant(signature, "SignatureValue");
            if (signatureValue != null) report.append("Signature value: ").append(abbreviate(text(signatureValue), 80)).append("\n");
            appendCertificates(report, signature);
            appendXadesProperties(report, signature);
        }
        report.append("\nNote: this inspector describes embedded data. Use Verify XML for integrity, trust and revocation validation.\n");
        return report.toString();
    }

    private static void disableExternalEntities(DocumentBuilderFactory factory) {
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) { }
        for (String feature : new String[] {
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" }) {
            try { factory.setFeature(feature, false); } catch (Exception ignored) { }
        }
        try { factory.setXIncludeAware(false); } catch (Exception ignored) { }
        factory.setExpandEntityReferences(false);
    }

    private static void appendCertificates(StringBuilder report, Element signature) {
        NodeList nodes = signature.getElementsByTagNameNS("*", "X509Certificate");
        report.append("Embedded X.509 certificates: ").append(nodes.getLength()).append("\n");
        for (int index = 0; index < nodes.getLength(); index++) {
            try {
                byte[] der = Base64.getMimeDecoder().decode(text((Element) nodes.item(index)));
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new java.io.ByteArrayInputStream(der));
                report.append("  - Certificate ").append(index + 1).append(":\n");
                report.append("    Subject: ").append(certificate.getSubjectX500Principal().getName()).append("\n");
                report.append("    Issuer: ").append(certificate.getIssuerX500Principal().getName()).append("\n");
                report.append("    Serial: ").append(certificate.getSerialNumber().toString(16).toUpperCase(java.util.Locale.ROOT)).append("\n");
                report.append("    Validity: ").append(certificate.getNotBefore()).append(" to ").append(certificate.getNotAfter()).append("\n");
                report.append("    Valid at inspection: ").append(certificateValidity(certificate)).append("\n");
                report.append("    Public key: ").append(certificate.getPublicKey().getAlgorithm()).append(" (")
                        .append(certificate.getPublicKey().getEncoded().length * 8).append(" bits encoded)\n");
                report.append("    Certificate signature: ").append(certificate.getSigAlgName()).append("\n");
                report.append("    Basic constraints: ").append(certificate.getBasicConstraints() < 0 ? "end-entity / not a CA" : "CA, path length " + certificate.getBasicConstraints()).append("\n");
                String usages = keyUsages(certificate.getKeyUsage());
                if (!usages.isBlank()) report.append("    Key usage: ").append(usages).append("\n");
                try {
                    List<String> extended = certificate.getExtendedKeyUsage();
                    if (extended != null && !extended.isEmpty()) report.append("    Extended key usage OIDs: ").append(String.join(", ", extended)).append("\n");
                } catch (Exception ignored) { }
                report.append("    SHA-256: ").append(hex(MessageDigest.getInstance("SHA-256").digest(der))).append("\n");
            } catch (Exception e) {
                report.append("  - Certificate ").append(index + 1).append(": unable to parse (").append(e.getMessage()).append(")\n");
            }
        }
    }

    private static void appendXadesProperties(StringBuilder report, Element signature) {
        NodeList qualifying = signature.getElementsByTagNameNS("*", "QualifyingProperties");
        report.append("XAdES qualifying properties: ").append(qualifying.getLength()).append("\n");
        if (qualifying.getLength() == 0) return;
        appendFirstText(report, "Signing time", signature, "SigningTime");
        appendFirstText(report, "Signature policy", signature, "Identifier");
        appendFirstText(report, "Claimed role", signature, "ClaimedRole");
        int signingCertificate = signature.getElementsByTagNameNS("*", "SigningCertificate").getLength();
        int signingCertificateV2 = signature.getElementsByTagNameNS("*", "SigningCertificateV2").getLength();
        report.append("Signing-certificate properties: v1=").append(signingCertificate).append(", v2=").append(signingCertificateV2).append("\n");
        int signatureTimestamps = signature.getElementsByTagNameNS("*", "SignatureTimeStamp").getLength();
        int refsTimestamps = signature.getElementsByTagNameNS("*", "SigAndRefsTimeStamp").getLength();
        int archiveTimestamps = signature.getElementsByTagNameNS("*", "ArchiveTimeStamp").getLength();
        int timestampTokens = signature.getElementsByTagNameNS("*", "EncapsulatedTimeStamp").getLength();
        report.append("Timestamps: signature=").append(signatureTimestamps).append(", sig-and-refs=")
                .append(refsTimestamps).append(", archive=").append(archiveTimestamps)
                .append(", embedded tokens=").append(timestampTokens).append("\n");
    }

    private static String certificateValidity(X509Certificate certificate) {
        java.util.Date now = new java.util.Date();
        if (certificate.getNotBefore().after(now)) return "not yet valid";
        if (certificate.getNotAfter().before(now)) return "expired";
        return "currently within its validity period (trust not assessed here)";
    }

    private static String keyUsages(boolean[] usages) {
        if (usages == null) return "";
        String[] names = { "digitalSignature", "nonRepudiation/contentCommitment", "keyEncipherment", "dataEncipherment", "keyAgreement", "keyCertSign", "cRLSign", "encipherOnly", "decipherOnly" };
        java.util.List<String> enabled = new java.util.ArrayList<>();
        for (int index = 0; index < usages.length && index < names.length; index++) if (usages[index]) enabled.add(names[index]);
        return String.join(", ", enabled);
    }

    private static void appendFirstText(StringBuilder report, String label, Element context, String localName) {
        Element element = firstDescendant(context, localName);
        if (element != null && !text(element).isBlank()) appendValue(report, label, abbreviate(text(element), 160));
    }

    private static Element firstDescendant(Element context, String localName) {
        NodeList nodes = context.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static void appendAlgorithm(StringBuilder report, String label, Element element) {
        if (element != null) appendValue(report, label, element.getAttribute("Algorithm"));
    }

    private static void appendIndentedAlgorithm(StringBuilder report, String label, Element element) {
        if (element != null) report.append("    ").append(label).append(": ").append(element.getAttribute("Algorithm")).append("\n");
    }

    private static void appendValue(StringBuilder report, String label, String value) {
        report.append(label).append(": ").append(emptyAs(value, "(not present)")).append("\n");
    }

    private static String nodeName(Node node) { return node.getNodeName(); }
    private static String text(Element element) { return element.getTextContent().replaceAll("\\s+", "").trim(); }
    private static String emptyAs(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format("%02X", b));
        return out.toString();
    }

    private static eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource loadTrustedCertificates(String path, String password) throws Exception {
        KeyStore keyStore = loadKeyStore(path, password);
        eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource source = new eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource();
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            java.security.cert.Certificate certificate = keyStore.getCertificate(aliases.nextElement());
            if (certificate instanceof X509Certificate) {
                source.addCertificate(new CertificateToken((X509Certificate) certificate));
            }
        }
        return source;
    }

    private static KeyStore loadKeyStore(String path, String password) throws Exception {
        Exception lastFailure = null;
        for (String type : new String[] { "PKCS12", "JKS" }) {
            try (java.io.FileInputStream input = new java.io.FileInputStream(path)) {
                KeyStore keyStore = KeyStore.getInstance(type);
                keyStore.load(input, password == null ? new char[0] : password.toCharArray());
                return keyStore;
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        throw new java.io.IOException("Unable to load truststore as PKCS12 or JKS", lastFailure);
    }
}
