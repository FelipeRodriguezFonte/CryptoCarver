package com.cryptoforge.crypto;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.bouncycastle.cert.X509CertificateHolder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TsaValidationTest {

    @BeforeAll
    static void setUp() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void testValidateTokenWithLocalPki() throws Exception {
        // 1. Generate Root CA
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair rootKp = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        Date startDate = new Date(now - 1000000);
        Date endDate = new Date(now + 100000000);

        X500Name rootName = new X500Name("CN=Test Root CA");
        X509v3CertificateBuilder rootBuilder = new JcaX509v3CertificateBuilder(
                rootName, BigInteger.valueOf(1), startDate, endDate, rootName, rootKp.getPublic());
        rootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        rootBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(rootKp.getPublic()));

        var signerBuilder = new JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC");
        X509Certificate rootCert = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(rootBuilder.build(signerBuilder.build(rootKp.getPrivate())));

        // 2. Generate TSA Cert
        KeyPair tsaKp = kpg.generateKeyPair();
        X500Name tsaName = new X500Name("CN=Test TSA");
        X509v3CertificateBuilder tsaBuilder = new JcaX509v3CertificateBuilder(
                rootName, BigInteger.valueOf(2), startDate, endDate, tsaName, tsaKp.getPublic());

        tsaBuilder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

        tsaBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(tsaKp.getPublic()));
        tsaBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(rootCert));

        X509CertificateHolder tsaCertHolder = tsaBuilder.build(signerBuilder.build(rootKp.getPrivate()));
        X509Certificate tsaCert = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(tsaCertHolder);

        // 3. Save TrustStore with Root CA
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setCertificateEntry("root", rootCert);
        Path ksPath = tempDir.resolve("truststore.jks");
        try (FileOutputStream fos = new FileOutputStream(ksPath.toFile())) {
            ks.store(fos, "changeit".toCharArray());
        }

        // 4. Generate Timestamp Token for a hash
        byte[] data = "Hello World".getBytes();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);

        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        reqGen.setCertReq(true);
        TimeStampRequest req = reqGen.generate(org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256, digest);

        var digestCalculator = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build().get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256));
        var signerInfoGen = new org.bouncycastle.cms.SignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(signerBuilder.build(tsaKp.getPrivate()), tsaCertHolder);

        TimeStampTokenGenerator tokenGen = new TimeStampTokenGenerator(
                signerInfoGen, digestCalculator, new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.3.4.5"));

        var certList = new java.util.ArrayList<org.bouncycastle.cert.X509CertificateHolder>();
        certList.add(tsaCertHolder);
        org.bouncycastle.util.CollectionStore<org.bouncycastle.cert.X509CertificateHolder> certStore = new org.bouncycastle.util.CollectionStore<>(certList);
        tokenGen.addCertificates(certStore);

        TimeStampResponseGenerator respGen = new TimeStampResponseGenerator(tokenGen, org.bouncycastle.tsp.TSPAlgorithms.ALLOWED);
        var response = respGen.generate(req, BigInteger.valueOf(123), new Date());
        byte[] responseBytes = response.getEncoded();

        // 5. Test validation with TrustStore -> SUCCESS
        String report = TsaDiagnostics.validateToken(responseBytes, data, ksPath.toString(), "changeit");
        if (!report.contains("4. Trust Chain: SUCCESS")) {
            throw new RuntimeException("Validation failed. Report:\n" + report);
        }
        assertTrue(report.contains("2. Imprint Match: SUCCESS"));
        assertTrue(report.contains("3. CMS Signature & EKU: SUCCESS"));
        assertTrue(report.contains("4. Trust Chain: SUCCESS"));

        // 6. Test validation with Empty TrustStore -> FAILED Trust Chain
        KeyStore emptyKs = KeyStore.getInstance("JKS");
        emptyKs.load(null, null);
        Path emptyKsPath = tempDir.resolve("empty.jks");
        try (FileOutputStream fos = new FileOutputStream(emptyKsPath.toFile())) {
            emptyKs.store(fos, "changeit".toCharArray());
        }

        String report2 = TsaDiagnostics.validateToken(responseBytes, data, emptyKsPath.toString(), "changeit");
        assertTrue(report2.contains("2. Imprint Match: SUCCESS"));
        assertTrue(report2.contains("3. CMS Signature & EKU: SUCCESS"));
        assertTrue(report2.contains("4. Trust Chain: FAILED"));

        // 7. Test validation with null data -> NOT EVALUATED
        String report3 = TsaDiagnostics.validateToken(responseBytes, null, ksPath.toString(), "changeit");
        assertTrue(report3.contains("2. Imprint Match: NOT EVALUATED"));
    }

    @Test
    void testValidateTokenWithoutEku() throws Exception {
        // 1. Generate Root CA
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair rootKp = kpg.generateKeyPair();
        long now = System.currentTimeMillis();
        Date startDate = new Date(now - 1000000);
        Date endDate = new Date(now + 100000000);
        X500Name rootName = new X500Name("CN=Test Root CA");
        X509v3CertificateBuilder rootBuilder = new JcaX509v3CertificateBuilder(
                rootName, BigInteger.valueOf(1), startDate, endDate, rootName, rootKp.getPublic());
        rootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        rootBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(rootKp.getPublic()));
        var signerBuilder = new JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC");
        X509Certificate rootCert = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(rootBuilder.build(signerBuilder.build(rootKp.getPrivate())));

        // 2. Generate TSA Cert WITH EKU (for BC generation) and WITHOUT EKU (for testing)
        KeyPair tsaKp = kpg.generateKeyPair();
        X500Name tsaName = new X500Name("CN=Test TSA NO EKU");

        // WITH EKU
        X509v3CertificateBuilder tsaBuilderValid = new JcaX509v3CertificateBuilder(
                rootName, BigInteger.valueOf(2), startDate, endDate, tsaName, tsaKp.getPublic());
        tsaBuilderValid.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        tsaBuilderValid.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(tsaKp.getPublic()));
        tsaBuilderValid.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(rootCert));
        X509CertificateHolder tsaCertHolderValid = tsaBuilderValid.build(signerBuilder.build(rootKp.getPrivate()));

        // WITHOUT EKU
        X509v3CertificateBuilder tsaBuilderInvalid = new JcaX509v3CertificateBuilder(
                rootName, BigInteger.valueOf(2), startDate, endDate, tsaName, tsaKp.getPublic());
        tsaBuilderInvalid.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(tsaKp.getPublic()));
        tsaBuilderInvalid.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(rootCert));
        X509CertificateHolder tsaCertHolderInvalid = tsaBuilderInvalid.build(signerBuilder.build(rootKp.getPrivate()));

        // 3. Save TrustStore with Root CA
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setCertificateEntry("root", rootCert);
        Path ksPath = tempDir.resolve("truststore_noeku.jks");
        try (FileOutputStream fos = new FileOutputStream(ksPath.toFile())) {
            ks.store(fos, "changeit".toCharArray());
        }

        // 4. Generate Timestamp Token using valid cert (to bypass BC's strict generation check)
        byte[] data = "Hello World".getBytes();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        reqGen.setCertReq(true);
        TimeStampRequest req = reqGen.generate(org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256, digest);

        var digestCalculator = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build().get(new org.bouncycastle.asn1.x509.AlgorithmIdentifier(org.bouncycastle.asn1.nist.NISTObjectIdentifiers.id_sha256));
        var signerInfoGen = new org.bouncycastle.cms.SignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(signerBuilder.build(tsaKp.getPrivate()), tsaCertHolderValid);
        TimeStampTokenGenerator tokenGen = new TimeStampTokenGenerator(
                signerInfoGen, digestCalculator, new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.3.4.5"));
        var certList = new java.util.ArrayList<org.bouncycastle.cert.X509CertificateHolder>();
        certList.add(tsaCertHolderValid);
        tokenGen.addCertificates(new org.bouncycastle.util.CollectionStore<>(certList));
        TimeStampResponseGenerator respGen = new TimeStampResponseGenerator(tokenGen, org.bouncycastle.tsp.TSPAlgorithms.ALLOWED);
        var response = respGen.generate(req, BigInteger.valueOf(123), new Date());
        org.bouncycastle.tsp.TimeStampToken validToken = response.getTimeStampToken();

        // Replace the valid certificate with the invalid one (without EKU) in the CMS structure
        org.bouncycastle.cms.CMSSignedData tamperedData = org.bouncycastle.cms.CMSSignedData.replaceCertificatesAndCRLs(
                validToken.toCMSSignedData(),
                new org.bouncycastle.util.CollectionStore<>(java.util.List.of(tsaCertHolderInvalid)),
                validToken.toCMSSignedData().getAttributeCertificates(),
                validToken.toCMSSignedData().getCRLs()
        );
        org.bouncycastle.tsp.TimeStampToken invalidToken = new org.bouncycastle.tsp.TimeStampToken(tamperedData);
        byte[] responseBytes = new org.bouncycastle.tsp.TimeStampResponseGenerator(tokenGen, org.bouncycastle.tsp.TSPAlgorithms.ALLOWED)
            .generate(req, BigInteger.valueOf(123), new Date()).getEncoded();
        // Wait, replace the token in the response:
        org.bouncycastle.asn1.cmp.PKIFreeText freeText = new org.bouncycastle.asn1.cmp.PKIFreeText("Granted");
        org.bouncycastle.asn1.cmp.PKIStatusInfo statusInfo = new org.bouncycastle.asn1.cmp.PKIStatusInfo(
                org.bouncycastle.asn1.cmp.PKIStatus.granted, freeText);
        org.bouncycastle.asn1.tsp.TimeStampResp tamperedResp = new org.bouncycastle.asn1.tsp.TimeStampResp(statusInfo, org.bouncycastle.asn1.cms.ContentInfo.getInstance(invalidToken.toCMSSignedData().getEncoded()));
        responseBytes = tamperedResp.getEncoded();

        // 5. Test validation
        String report = TsaDiagnostics.validateToken(responseBytes, data, ksPath.toString(), "changeit");
        assertTrue(report.contains("3. CMS Signature & EKU: FAILED"), "CMS & EKU step must fail because EKU is missing");
        assertTrue(report.contains("4. Trust Chain: NOT TRUSTED"), "Trust Chain step must report NOT TRUSTED");
        assertFalse(report.contains("4. Trust Chain: SUCCESS"), "Trust Chain step must NOT report SUCCESS");
    }
}
