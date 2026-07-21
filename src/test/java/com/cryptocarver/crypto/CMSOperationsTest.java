package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.util.Date;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CMSOperationsTest {

    @BeforeAll
    static void installBouncyCastleProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void verifiesDetachedSignedDataOnlyWithTheOriginalContent() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "CMS detached test";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
        byte[] original = "Detached CMS payload".getBytes(StandardCharsets.UTF_8);

        byte[] cms = CMSOperations.generateSignedData(original, certificate, keyPair.getPrivate(), null, true);

        CMSOperations.VerificationResult verified =
                CMSOperations.verifySignedData(cms, certificate, original);
        assertTrue(verified.verified);
        assertArrayEquals(original, verified.content);

        CMSOperations.VerificationResult altered = CMSOperations.verifySignedData(
                cms, certificate, "Detached CMS payloaD".getBytes(StandardCharsets.UTF_8));
        assertFalse(altered.verified);
    }

    @Test
    void cadesBesBindsTheSignerCertificateAndVerifies() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "CAdES-BES test";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
        byte[] original = "CAdES-BES payload".getBytes(StandardCharsets.UTF_8);

        byte[] cades = CMSOperations.generateCadesBes(original, certificate, keyPair.getPrivate(), null, false);
        CMSOperations.VerificationResult verified = CMSOperations.verifySignedData(cades, certificate);
        assertTrue(verified.verified);
        assertArrayEquals(original, verified.content);

        CMSSignedData signedData = new CMSSignedData(cades);
        SignerInformation signer = signedData.getSignerInfos().getSigners().iterator().next();
        assertTrue(signer.getSignedAttributes().get(PKCSObjectIdentifiers.id_aa_signingCertificateV2) != null,
                "CAdES-BES must include signingCertificateV2");

        CMSOperations.CadesProfile profile = CMSOperations.inspectCadesProfile(cades);
        assertEquals("CAdES-BES", profile.profile());
        assertTrue(profile.certificateBindingPresent());
        assertTrue(profile.certificateBindingValid());
    }

    @Test
    void ordinaryCmsIsNotMisreportedAsCadesBes() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "CMS profile test";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);

        byte[] cms = CMSOperations.generateSignedData("plain CMS".getBytes(StandardCharsets.UTF_8),
                certificate, keyPair.getPrivate(), null, false);
        CMSOperations.CadesProfile profile = CMSOperations.inspectCadesProfile(cms);

        assertEquals("CMS / PKCS#7", profile.profile());
        assertFalse(profile.certificateBindingPresent());
    }

    @Test
    void cadesTAttachesAValidatedRfc3161SignatureTimestamp() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "CAdES-T signer";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
        byte[] cadesBes = CMSOperations.generateCadesBes("CAdES-T payload".getBytes(StandardCharsets.UTF_8),
                certificate, keyPair.getPrivate(), null, false);

        TimestampFixture timestampFixture = localTimestampResponse(CMSOperations.cadesSignatureValue(cadesBes));
        byte[] cadesT = CMSOperations.addCadesTSignatureTimestamp(cadesBes, timestampFixture.response());

        CMSOperations.CadesProfile profile = CMSOperations.inspectCadesProfile(cadesT);
        CMSOperations.CadesTimestampStatus timestamp = CMSOperations.inspectCadesTimestamp(cadesT);
        assertEquals("CAdES-T", profile.profile());
        assertTrue(profile.certificateBindingValid());
        assertTrue(timestamp.present());
        assertTrue(timestamp.imprintValid(), timestamp.message());
        CMSOperations.CadesLongTermStatus longTerm = CMSOperations.inspectCadesLongTermEvidence(cadesT);
        assertEquals("CAdES-T", longTerm.level());
        assertFalse(longTerm.certificateValuesPresent());
        assertFalse(longTerm.revocationValuesPresent());

        java.security.KeyStore trustStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("local-tsa", timestampFixture.certificate());
        CmsInspectionReport report = new CmsInspector().inspect(cadesT, null, trustStore);
        assertTrue(report.getValidationSteps().stream().anyMatch(step ->
                "CAdES-T TSA Trust".equals(step.getStepName())
                        && step.getState() == CmsInspectionReport.ValidationState.VALID));
    }

    @Test
    void cadesLtEmbedsLocalCertificateAndCrlEvidenceWithoutNetwork() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "CAdES-LT signer";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
        byte[] cadesBes = CMSOperations.generateCadesBes("CAdES-LT payload".getBytes(StandardCharsets.UTF_8),
                certificate, keyPair.getPrivate(), null, false);
        TimestampFixture timestampFixture = localTimestampResponse(CMSOperations.cadesSignatureValue(cadesBes));
        byte[] cadesT = CMSOperations.addCadesTSignatureTimestamp(cadesBes, timestampFixture.response());

        byte[] cadesLt = CMSOperations.addCadesLtEvidence(cadesT, java.util.List.of(),
                java.util.List.of(localCrl(certificate, keyPair)));
        CMSOperations.CadesLongTermStatus longTerm = CMSOperations.inspectCadesLongTermEvidence(cadesLt);

        assertEquals("CAdES-LT", longTerm.level());
        assertTrue(longTerm.certificateValuesPresent());
        assertTrue(longTerm.revocationValuesPresent());
        CMSOperations.CadesLongTermValidation validation =
                CMSOperations.validateCadesLongTermEvidence(cadesLt, new Date());
        assertEquals(1, validation.crlCount());
        assertEquals(1, validation.signatureValidCrlCount(), validation.message());
        assertEquals(1, validation.currentCrlCount(), validation.message());
    }

    @Test
    void parsesDerAndPemCrlEvidenceConsistently() throws Exception {
        KeyPair issuer = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "PEM CRL issuer";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(issuer, config);
        java.security.cert.X509CRL crl = localCrl(certificate, issuer);

        byte[] der = crl.getEncoded();
        String pem = "-----BEGIN X509 CRL-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END X509 CRL-----\n";

        assertArrayEquals(der, CMSOperations.parseX509Crl(der).getEncoded());
        assertArrayEquals(der, CMSOperations.parseX509Crl(pem.getBytes(StandardCharsets.US_ASCII)).getEncoded());
    }

    @Test
    void parsesDerAndPemCertificateEvidenceConsistently() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "PEM certificate evidence";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
        byte[] der = certificate.getEncoded();
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END CERTIFICATE-----\n";

        assertArrayEquals(der, CMSOperations.parseX509Certificates(der).get(0).getEncoded());
        assertArrayEquals(der, CMSOperations.parseX509Certificates(pem.getBytes(StandardCharsets.US_ASCII))
                .get(0).getEncoded());
    }

    private static TimestampFixture localTimestampResponse(byte[] data) throws Exception {
        KeyPair tsaKeys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        long now = System.currentTimeMillis();
        Date from = new Date(now - 60_000);
        Date until = new Date(now + 86_400_000);
        org.bouncycastle.asn1.x500.X500Name name = new org.bouncycastle.asn1.x500.X500Name("CN=Local Test TSA");
        org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder certificateBuilder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name, BigInteger.ONE, from, until, name,
                        tsaKeys.getPublic());
        certificateBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.extendedKeyUsage, true,
                new org.bouncycastle.asn1.x509.ExtendedKeyUsage(
                        org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_timeStamping));
        org.bouncycastle.cert.X509CertificateHolder certificate = certificateBuilder.build(
                new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC").build(tsaKeys.getPrivate()));
        org.bouncycastle.operator.DigestCalculator digestCalculator =
                new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
                        .get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256));
        org.bouncycastle.cms.SignerInfoGenerator signerInfo = new org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder(
                new org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC").build(tsaKeys.getPrivate()), certificate);
        org.bouncycastle.tsp.TimeStampTokenGenerator generator = new org.bouncycastle.tsp.TimeStampTokenGenerator(
                signerInfo, digestCalculator, new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.3.4.5"));
        generator.addCertificates(new org.bouncycastle.util.CollectionStore<>(java.util.List.of(certificate)));
        org.bouncycastle.tsp.TimeStampRequestGenerator requestGenerator =
                new org.bouncycastle.tsp.TimeStampRequestGenerator();
        requestGenerator.setCertReq(true);
        org.bouncycastle.tsp.TimeStampRequest request = requestGenerator.generate(
                org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256,
                java.security.MessageDigest.getInstance("SHA-256").digest(data));
        byte[] response = new org.bouncycastle.tsp.TimeStampResponseGenerator(generator, org.bouncycastle.tsp.TSPAlgorithms.ALLOWED)
                .generate(request, BigInteger.TEN, new Date()).getEncoded();
        X509Certificate x509Certificate = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(certificate);
        return new TimestampFixture(response, x509Certificate);
    }

    private record TimestampFixture(byte[] response, X509Certificate certificate) { }

    private static java.security.cert.X509CRL localCrl(X509Certificate issuerCertificate, KeyPair issuer) throws Exception {
        long now = System.currentTimeMillis();
        org.bouncycastle.cert.X509v2CRLBuilder builder = new org.bouncycastle.cert.X509v2CRLBuilder(
                new org.bouncycastle.asn1.x500.X500Name(issuerCertificate.getSubjectX500Principal().getName()),
                new Date(now - 60_000));
        builder.setNextUpdate(new Date(now + 86_400_000));
        org.bouncycastle.cert.X509CRLHolder crl = builder.build(
                new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC").build(issuer.getPrivate()));
        return new org.bouncycastle.cert.jcajce.JcaX509CRLConverter().setProvider("BC").getCRL(crl);
    }

    @Test
    void testEnvelopedDataDecryption_Positive() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "CMS envelop test";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
        byte[] original = "Secret Enveloped Payload".getBytes(StandardCharsets.UTF_8);

        byte[] cms = CMSOperations.generateEnvelopedData(original, certificate);

        byte[] decrypted = CMSOperations.decryptEnvelopedData(cms, keyPair.getPrivate(), Security.getProvider("BC"));
        assertArrayEquals(original, decrypted);
    }

    @Test
    void testEnvelopedDataDecryption_NegativeWrongKey() throws Exception {
        KeyPair keyPair1 = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config1 = new CertificateGenerator.CertificateConfig();
        config1.commonName = "CMS envelop test 1";
        X509Certificate cert1 = CertificateGenerator.generateSelfSignedCertificate(keyPair1, config1);

        KeyPair keyPair2 = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        byte[] original = "Secret Enveloped Payload".getBytes(StandardCharsets.UTF_8);
        byte[] cms = CMSOperations.generateEnvelopedData(original, cert1);

        Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            CMSOperations.decryptEnvelopedData(cms, keyPair2.getPrivate(), Security.getProvider("BC"));
        });
        org.junit.jupiter.api.Assertions.assertEquals("No recipient could be decrypted with the supplied key", e.getMessage());
    }

    @Test
    void testEnvelopedDataDecryption_MultiRecipient() throws Exception {
        KeyPair keyPair1 = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config1 = new CertificateGenerator.CertificateConfig();
        config1.commonName = "Recipient 1";
        X509Certificate cert1 = CertificateGenerator.generateSelfSignedCertificate(keyPair1, config1);

        KeyPair keyPair2 = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config2 = new CertificateGenerator.CertificateConfig();
        config2.commonName = "Recipient 2";
        X509Certificate cert2 = CertificateGenerator.generateSelfSignedCertificate(keyPair2, config2);

        byte[] original = "Secret Multi-Recipient Payload".getBytes(StandardCharsets.UTF_8);

        // BouncyCastle CMSEnvelopedDataGenerator handles multiple recipients
        org.bouncycastle.cms.CMSEnvelopedDataGenerator gen = new org.bouncycastle.cms.CMSEnvelopedDataGenerator();
        gen.addRecipientInfoGenerator(new org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator(cert1).setProvider("BC"));
        gen.addRecipientInfoGenerator(new org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator(cert2).setProvider("BC"));

        org.bouncycastle.cms.CMSTypedData msg = new org.bouncycastle.cms.CMSProcessableByteArray(original);
        org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder builder =
                new org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder(org.bouncycastle.cms.CMSAlgorithm.AES256_CBC).setProvider("BC");
        org.bouncycastle.cms.CMSEnvelopedData envData = gen.generate(msg, builder.build());
        byte[] cms = envData.getEncoded();

        // Should be able to decrypt with cert2's private key (second recipient in the list)
        byte[] decrypted = CMSOperations.decryptEnvelopedData(cms, keyPair2.getPrivate(), Security.getProvider("BC"));
        assertArrayEquals(original, decrypted);
    }
}
