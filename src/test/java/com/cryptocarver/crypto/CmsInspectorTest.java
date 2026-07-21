package com.cryptocarver.crypto;

import com.cryptocarver.crypto.CmsInspectionReport.CmsContentType;
import com.cryptocarver.crypto.CmsInspectionReport.ContentState;
import com.cryptocarver.crypto.CmsInspectionReport.ValidationState;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CmsInspectorTest {

    private static CmsInspector inspector;

    @BeforeAll
    static void setup() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        inspector = new CmsInspector();
    }

    private KeyPair generateKeyPair() throws Exception {
        return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    private X509Certificate generateCert(KeyPair kp, String name) throws Exception {
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = name;
        return CertificateGenerator.generateSelfSignedCertificate(kp, config);
    }

    @Test
    void testEncapsulatedSignedData_Valid() throws Exception {
        KeyPair kp = generateKeyPair();
        X509Certificate cert = generateCert(kp, "Test Signer");
        byte[] content = "Hello CMS".getBytes();
        byte[] cms = CMSOperations.generateSignedData(content, cert, kp.getPrivate(), null, false);

        CmsInspectionReport report = inspector.inspect(cms);

        assertEquals(CmsContentType.SIGNED_DATA, report.getType());
        assertEquals(ContentState.ENCAPSULATED, report.getContentState());
        assertEquals(1, report.getSigners().size());
        assertEquals(ValidationState.VALID, report.getSigners().get(0).getSignatureValid());
        assertEquals(ValidationState.VALID, report.getSigners().get(0).getCertificateValid());
        assertTrue(report.getValidationSteps().stream().anyMatch(s -> s.getStepName().equals("Signature/Integrity") && s.getState() == ValidationState.VALID));
    }

    @Test
    void testCadesBesReportsCertificateBinding() throws Exception {
        KeyPair kp = generateKeyPair();
        X509Certificate cert = generateCert(kp, "CAdES inspector test");
        byte[] cms = CMSOperations.generateCadesBes("CAdES payload".getBytes(StandardCharsets.UTF_8),
                cert, kp.getPrivate(), null, false);

        CmsInspectionReport report = new CmsInspector().inspect(cms);
        assertTrue(report.getValidationSteps().stream().anyMatch(step ->
                "CAdES-BES Certificate Binding".equals(step.getStepName())
                        && step.getState() == CmsInspectionReport.ValidationState.VALID));
    }

    @Test
    void testDetachedSignedData_NoContent() throws Exception {
        KeyPair kp = generateKeyPair();
        X509Certificate cert = generateCert(kp, "Test Signer Detached");
        byte[] content = "Hello Detached CMS".getBytes();
        byte[] cms = CMSOperations.generateSignedData(content, cert, kp.getPrivate(), null, true);

        CmsInspectionReport report = inspector.inspect(cms);

        assertEquals(ContentState.DETACHED, report.getContentState());
        assertEquals(ValidationState.NOT_EVALUATED, report.getSigners().get(0).getSignatureValid());
        assertTrue(report.getValidationSteps().stream().anyMatch(s -> s.getStepName().equals("Content") && s.getState() == ValidationState.NOT_EVALUATED));
    }

    @Test
    void testDetachedSignedData_WithCorrectAndAlteredContent() throws Exception {
        KeyPair kp = generateKeyPair();
        X509Certificate cert = generateCert(kp, "Test Signer Detached 2");
        byte[] content = "Hello Detached CMS".getBytes();
        byte[] cms = CMSOperations.generateSignedData(content, cert, kp.getPrivate(), null, true);

        // Correct content
        CmsInspectionReport reportOk = inspector.inspect(cms, content, null);
        assertEquals(ValidationState.VALID, reportOk.getSigners().get(0).getSignatureValid());

        // Altered content
        byte[] altered = "Hello Detached cms".getBytes();
        CmsInspectionReport reportFail = inspector.inspect(cms, altered, null);
        assertEquals(ValidationState.INVALID, reportFail.getSigners().get(0).getSignatureValid());
    }

    @Test
    void testSignedData_CertNotIncluded() throws Exception {
        KeyPair kp = generateKeyPair();
        X509Certificate cert = generateCert(kp, "Test Signer No Cert");
        byte[] content = "Hello CMS".getBytes();

        // Use BC directly to generate CMS without cert
        org.bouncycastle.cms.CMSSignedDataGenerator gen = new org.bouncycastle.cms.CMSSignedDataGenerator();
        org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(kp.getPrivate());
        gen.addSignerInfoGenerator(new org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(signer, cert));

        org.bouncycastle.cms.CMSTypedData msg = new org.bouncycastle.cms.CMSProcessableByteArray(content);
        byte[] cms = gen.generate(msg, true).getEncoded();

        CmsInspectionReport report = inspector.inspect(cms);

        assertEquals(1, report.getSigners().size());
        assertEquals(ValidationState.NOT_EVALUATED, report.getSigners().get(0).getCertificateValid());
        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("not found in CMS")));
    }

    @Test
    void testEnvelopedData_OneAndTwoRecipients() throws Exception {
        KeyPair kp1 = generateKeyPair();
        X509Certificate cert1 = generateCert(kp1, "Recipient 1");

        KeyPair kp2 = generateKeyPair();
        X509Certificate cert2 = generateCert(kp2, "Recipient 2");

        byte[] content = "Secret Data".getBytes();

        // One recipient
        byte[] cms1 = CMSOperations.generateEnvelopedData(content, cert1);
        CmsInspectionReport rep1 = inspector.inspect(cms1);
        assertEquals(CmsContentType.ENVELOPED_DATA, rep1.getType());
        assertEquals(1, rep1.getRecipients().size());
        assertTrue(rep1.getRecipients().get(0).getSid().contains("Recipient 1"));

        // Two recipients
        org.bouncycastle.cms.CMSEnvelopedDataGenerator gen = new org.bouncycastle.cms.CMSEnvelopedDataGenerator();
        gen.addRecipientInfoGenerator(new org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator(cert1).setProvider("BC"));
        gen.addRecipientInfoGenerator(new org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator(cert2).setProvider("BC"));

        org.bouncycastle.cms.CMSTypedData msg = new org.bouncycastle.cms.CMSProcessableByteArray(content);
        org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder builder =
                new org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder(org.bouncycastle.cms.CMSAlgorithm.AES256_CBC).setProvider("BC");
        byte[] cms2 = gen.generate(msg, builder.build()).getEncoded();

        CmsInspectionReport rep2 = inspector.inspect(cms2);
        assertEquals(2, rep2.getRecipients().size());
    }

    @Test
    void testCorruptData_AndOversize() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(new byte[]{0x00, 0x01}));

        byte[] largeData = new byte[16 * 1024 * 1024 + 1];
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(largeData));
    }

    @Test
    void testTrustStoreValidation() throws Exception {
        KeyPair kp = generateKeyPair();
        X509Certificate rootCert = generateCert(kp, "Trust Root");

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("root", rootCert);

        byte[] content = "Hello Trusted".getBytes();
        byte[] cms = CMSOperations.generateSignedData(content, rootCert, kp.getPrivate(), null, false);

        CmsInspectionReport rep = inspector.inspect(cms, null, ks);
        assertTrue(rep.getValidationSteps().stream().anyMatch(s -> s.getStepName().equals("Trust Chain") && s.getState() == ValidationState.VALID));

        // Untrusted
        KeyPair kpUn = generateKeyPair();
        X509Certificate unCert = generateCert(kpUn, "Untrusted");
        byte[] cmsUn = CMSOperations.generateSignedData(content, unCert, kpUn.getPrivate(), null, false);

        CmsInspectionReport repUn = inspector.inspect(cmsUn, null, ks);
        assertTrue(repUn.getValidationSteps().stream().anyMatch(s -> s.getStepName().equals("Trust Chain") && s.getState() == ValidationState.INVALID));
    }

    @Test
    void testTrustChainShortCircuit() throws Exception {
        KeyPair kp = generateKeyPair();
        X509Certificate rootCert = generateCert(kp, "Trust Root 2");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("root", rootCert);

        byte[] content = "Hello".getBytes();
        // Generate with detached content
        byte[] cms = CMSOperations.generateSignedData(content, rootCert, kp.getPrivate(), null, true);

        // Supply WRONG content to make signature INVALID
        byte[] wrongContent = "Wrong Content".getBytes();

        CmsInspectionReport rep = inspector.inspect(cms, wrongContent, ks);
        assertTrue(rep.getValidationSteps().stream().anyMatch(s -> s.getStepName().equals("Signature/Integrity") && s.getState() == ValidationState.INVALID));
        // Trust chain should short-circuit and be NOT_EVALUATED
        assertTrue(rep.getValidationSteps().stream().anyMatch(s -> s.getStepName().equals("Trust Chain") && s.getState() == ValidationState.NOT_EVALUATED));
    }

    @Test
    void testIntermediateCertPathBuild() throws Exception {
        KeyPair rootKp = generateKeyPair();
        CertificateGenerator.CertificateConfig rootConfig = new CertificateGenerator.CertificateConfig();
        rootConfig.commonName = "Root CA";
        X509Certificate rootCert = CertificateGenerator.generateRootCA(rootKp, rootConfig, 1);

        KeyPair leafKp = generateKeyPair();
        org.bouncycastle.operator.ContentSigner sigGen = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSAEncryption").build(rootKp.getPrivate());
        org.bouncycastle.asn1.x500.X500Name subjectName = new org.bouncycastle.asn1.x500.X500Name("CN=Leaf");
        org.bouncycastle.asn1.x500.X500Name issuerName = org.bouncycastle.asn1.x500.X500Name.getInstance(rootCert.getSubjectX500Principal().getEncoded());
        org.bouncycastle.cert.X509v3CertificateBuilder certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                issuerName,
                java.math.BigInteger.valueOf(System.currentTimeMillis()),
                new java.util.Date(System.currentTimeMillis() - 100000),
                new java.util.Date(System.currentTimeMillis() + 100000),
                subjectName,
                leafKp.getPublic()
        );
        certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, new org.bouncycastle.asn1.x509.BasicConstraints(false));
        certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.authorityKeyIdentifier, false, new org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils().createAuthorityKeyIdentifier(rootCert.getPublicKey()));
        certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier, false, new org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils().createSubjectKeyIdentifier(leafKp.getPublic()));
        org.bouncycastle.cert.X509CertificateHolder certHolder = certBuilder.build(sigGen);
        X509Certificate leafCert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("root", rootCert); // Only root is in truststore

        // Create CMS signed by leaf, and embed both leaf and root (or just leaf, since leaf is signed by root)
        java.util.List<Certificate> certList = java.util.Arrays.asList(leafCert, rootCert);
        org.bouncycastle.cert.jcajce.JcaCertStore cmsCerts = new org.bouncycastle.cert.jcajce.JcaCertStore(certList);

        org.bouncycastle.cms.CMSSignedDataGenerator gen = new org.bouncycastle.cms.CMSSignedDataGenerator();
        org.bouncycastle.cms.SignerInfoGenerator signerInfoGenerator = new org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
        ).build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC").build(leafKp.getPrivate()), leafCert);
        gen.addSignerInfoGenerator(signerInfoGenerator);
        gen.addCertificates(cmsCerts);

        org.bouncycastle.cms.CMSTypedData msg = new org.bouncycastle.cms.CMSProcessableByteArray("Hello Chain".getBytes());
        org.bouncycastle.cms.CMSSignedData signedData = gen.generate(msg, true);
        byte[] cms = signedData.getEncoded();

        CmsInspectionReport rep = inspector.inspect(cms, null, ks);
        assertTrue(rep.getValidationSteps().stream().anyMatch(s -> s.getStepName().equals("Trust Chain") && s.getState() == ValidationState.VALID));
    }
}
