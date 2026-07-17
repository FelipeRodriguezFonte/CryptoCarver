package com.cryptoforge.crypto;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;

import java.io.ByteArrayInputStream;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * CMS (Cryptographic Message Syntax) / PKCS#7 Operations
 */
public class CMSOperations {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Result class for generateSignedData with warnings
     */
    public static class SignedDataResult {
        public final byte[] pkcs7;
        public final List<String> attributeWarnings;

        public SignedDataResult(byte[] pkcs7, List<String> attributeWarnings) {
            this.pkcs7 = pkcs7;
            this.attributeWarnings = attributeWarnings != null ? attributeWarnings : new ArrayList<>();
        }
    }

    /**
     * Generate SignedData PKCS#7 with warnings
     *
     * @param detached If true, creates a detached signature (data not included in
     *                 PKCS#7)
     * @return SignedDataResult with PKCS#7 bytes and attribute warnings
     */
    public static SignedDataResult generateSignedDataWithWarnings(byte[] data, X509Certificate cert,
            PrivateKey privateKey,
            Map<String, String> associatedData, boolean detached) throws Exception {
        // Create CMSProcessableByteArray
        CMSTypedData msg = new CMSProcessableByteArray(data);

        // Create certificate store
        List<X509Certificate> certList = new ArrayList<>();
        certList.add(cert);
        Store certs = new JcaCertStore(certList);

        // Create signer
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(privateKey);

        // Process associated data and collect warnings
        List<String> warnings = new ArrayList<>();

        // Add signer with signed attributes if there's associated data
        if (associatedData != null && !associatedData.isEmpty()) {
            AttributeTableResult result = createAttributeTableWithWarnings(associatedData);
            warnings.addAll(result.warnings);

            gen.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                            .setSignedAttributeGenerator(new DefaultSignedAttributeTableGenerator(result.table))
                            .build(signer, cert));
        } else {
            gen.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                            .build(signer, cert));
        }

        // Add certificates
        gen.addCertificates(certs);

        // Generate CMS
        // encapsulate = !detached (if detached, don't include content)
        CMSSignedData signedData = gen.generate(msg, !detached);

        return new SignedDataResult(signedData.getEncoded(), warnings);
    }

    /**
     * Generate SignedData PKCS#7
     *
     * @param detached If true, creates a detached signature (data not included in
     *                 PKCS#7)
     */
    public static byte[] generateSignedData(byte[] data, X509Certificate cert, PrivateKey privateKey,
            Map<String, String> associatedData, boolean detached) throws Exception {
        return generateSignedDataWithWarnings(data, cert, privateKey, associatedData, detached).pkcs7;
    }

    /**
     * Generate SignedData PKCS#7 (backward compatibility - encapsulated by default)
     */
    public static byte[] generateSignedData(byte[] data, X509Certificate cert, PrivateKey privateKey,
            Map<String, String> associatedData) throws Exception {
        return generateSignedData(data, cert, privateKey, associatedData, false);
    }

    /**
     * Generate EnvelopedData PKCS#7
     */
    public static byte[] generateEnvelopedData(byte[] data, X509Certificate recipientCert) throws Exception {
        CMSEnvelopedDataGenerator gen = new CMSEnvelopedDataGenerator();

        // Add recipient
        gen.addRecipientInfoGenerator(
                new JceKeyTransRecipientInfoGenerator(recipientCert)
                        .setProvider("BC"));

        // Create encryptor (AES256)
        OutputEncryptor encryptor = new JceCMSContentEncryptorBuilder(
                CMSAlgorithm.AES256_CBC)
                .setProvider("BC")
                .build();

        // Generate
        CMSEnvelopedData envelopedData = gen.generate(
                new CMSProcessableByteArray(data),
                encryptor);

        return envelopedData.getEncoded();
    }

    /**
     * Verify SignedData PKCS#7
     */
    public static VerificationResult verifySignedData(byte[] pkcs7Data, X509Certificate cert) throws Exception {
        CMSSignedData signedData = new CMSSignedData(pkcs7Data);

        // Get signers
        Store certStore = signedData.getCertificates();
        SignerInformationStore signers = signedData.getSignerInfos();
        Collection<SignerInformation> c = signers.getSigners();

        boolean verified = false;
        byte[] content = null;
        Map<String, String> associatedData = new HashMap<>();

        for (SignerInformation signer : c) {
            // Get certificate
            Collection<X509CertificateHolder> certCollection = certStore.getMatches(signer.getSID());
            X509CertificateHolder certHolder = certCollection.iterator().next();
            X509Certificate signerCert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certHolder);

            // Verify signature
            SignerInformationVerifier verifier = new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider("BC")
                    .build(cert != null ? cert : signerCert);

            verified = signer.verify(verifier);

            // Extract content
            CMSProcessable signedContent = signedData.getSignedContent();
            if (signedContent != null) {
                content = (byte[]) signedContent.getContent();
            }

            // Extract associated data (signed attributes)
            AttributeTable signedAttributes = signer.getSignedAttributes();
            if (signedAttributes != null) {
                associatedData = extractAttributeTable(signedAttributes);
            }

            break; // Process first signer only
        }

        return new VerificationResult(verified, content, associatedData);
    }

    /**
     * Decrypt EnvelopedData PKCS#7
     */
    public static byte[] decryptEnvelopedData(byte[] pkcs7Data, PrivateKey privateKey) throws Exception {
        CMSEnvelopedData envelopedData = new CMSEnvelopedData(pkcs7Data);

        // Get recipients
        RecipientInformationStore recipients = envelopedData.getRecipientInfos();
        Collection<RecipientInformation> c = recipients.getRecipients();

        for (RecipientInformation recipient : c) {
            // Decrypt
            Recipient jceRecipient = new JceKeyTransEnvelopedRecipient(privateKey)
                    .setProvider("BC");

            return recipient.getContent(jceRecipient);
        }

        throw new Exception("No valid recipient found");
    }

    /**
     * Parse certificate from PEM or DER
     */
    public static X509Certificate parseCertificate(String certData) throws Exception {
        byte[] certBytes;

        if (certData.contains("BEGIN CERTIFICATE")) {
            // PEM format
            certData = certData.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");
            certBytes = Base64.getDecoder().decode(certData);
        } else {
            // Try as base64 or hex
            try {
                certBytes = Base64.getDecoder().decode(certData.replaceAll("\\s+", ""));
            } catch (Exception e) {
                certBytes = hexToBytes(certData.replaceAll("\\s+", ""));
            }
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    /**
     * Create AttributeTable from map
     * Returns warnings as list
     */
    public static class AttributeTableResult {
        public final AttributeTable table;
        public final List<String> warnings;

        public AttributeTableResult(AttributeTable table, List<String> warnings) {
            this.table = table;
            this.warnings = warnings;
        }
    }

    private static AttributeTableResult createAttributeTableWithWarnings(Map<String, String> attributes) {
        ASN1EncodableVector v = new ASN1EncodableVector();
        List<String> warnings = new ArrayList<>();

        // Reserved OIDs that CMS automatically handles
        Set<String> reservedOids = new HashSet<>(Arrays.asList(
                "1.2.840.113549.1.9.3", // contentType - auto-added by CMS
                "1.2.840.113549.1.9.4", // messageDigest - auto-added by CMS
                "1.2.840.113549.1.9.5", // signingTime - auto-added by CMS
                "1.2.840.113549.1.9.52" // cmsAlgorithmProtection - auto-added by CMS
        ));

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            try {
                String oidString = entry.getKey();
                String valueString = entry.getValue();

                // Skip reserved OIDs (they're added automatically by CMS)
                if (reservedOids.contains(oidString)) {
                    warnings.add("⚠ OID " + oidString + " is reserved (auto-added by CMS), skipped");
                    continue;
                }

                // Validate OID format - must be numeric like 1.2.3.4.5
                if (!oidString.matches("^[0-9]+(\\.[0-9]+)+$")) {
                    warnings.add("✗ Invalid OID '" + oidString + "': Must be numeric (e.g., 1.2.3.4.5), skipped");
                    continue;
                }

                ASN1ObjectIdentifier oid = new ASN1ObjectIdentifier(oidString);

                // Try to parse value as different types
                org.bouncycastle.asn1.ASN1Encodable asnValue;

                // If it looks like an OID, use it as OID
                if (valueString.matches("^[0-9]+(\\.[0-9]+)+$")) {
                    asnValue = new ASN1ObjectIdentifier(valueString);
                }
                // If it looks like a date/time
                else if (valueString.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
                    try {
                        asnValue = new org.bouncycastle.asn1.DERUTCTime(valueString);
                    } catch (Exception e) {
                        // Fall back to string
                        asnValue = new org.bouncycastle.asn1.DERUTF8String(valueString);
                    }
                }
                // Default: use as UTF8 string
                else {
                    asnValue = new org.bouncycastle.asn1.DERUTF8String(valueString);
                }

                v.add(new Attribute(oid, new DERSet(asnValue)));
                warnings.add("✓ Added: " + oidString + " = " + valueString);

            } catch (Exception e) {
                warnings.add("✗ Error with '" + entry.getKey() + "': " + e.getMessage());
            }
        }

        return new AttributeTableResult(new AttributeTable(v), warnings);
    }

    /**
     * Create AttributeTable from map (backward compatibility)
     */
    private static AttributeTable createAttributeTable(Map<String, String> attributes) {
        return createAttributeTableWithWarnings(attributes).table;
    }

    /**
     * Extract AttributeTable to map
     */
    private static Map<String, String> extractAttributeTable(AttributeTable table) {
        Map<String, String> result = new HashMap<>();

        Hashtable attrs = table.toHashtable();
        for (Object key : attrs.keySet()) {
            try {
                ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) key;
                Attribute attr = (Attribute) attrs.get(key);
                result.put(oid.getId(), attr.getAttrValues().toString());
            } catch (Exception e) {
                // Skip invalid attributes
            }
        }

        return result;
    }

    /**
     * Helper: hex to bytes
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Result class
     */
    public static class VerificationResult {
        public final boolean verified;
        public final byte[] content;
        public final Map<String, String> associatedData;

        public VerificationResult(boolean verified, byte[] content, Map<String, String> associatedData) {
            this.verified = verified;
            this.content = content;
            this.associatedData = associatedData;
        }
    }
}
