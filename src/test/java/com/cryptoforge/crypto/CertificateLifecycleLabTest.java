package com.cryptoforge.crypto;

import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CertificateLifecycleLabTest {

    @Test
    public void testFullLifecycle_RSA() throws Exception {
        // 1. Generate Root CA KeyPair
        KeyPair rootCaPair = AsymmetricKeyOperations.generateRSAKeyPair(2048);

        // 2. Generate Root CA Certificate
        CertificateGenerator.CertificateConfig rootConfig = new CertificateGenerator.CertificateConfig();
        rootConfig.commonName = "Test Root CA";
        rootConfig.organization = null;
        rootConfig.organizationalUnit = null;
        rootConfig.locality = null;
        rootConfig.state = null;
        rootConfig.country = null;
        X509Certificate rootCert = CertificateGenerator.generateRootCA(rootCaPair, rootConfig, 0);
        assertTrue(CertificateGenerator.verifyCertificateSignature(rootCert), "Root CA should be validly self-signed");

        // 3. Generate End-Entity KeyPair
        KeyPair eePair = AsymmetricKeyOperations.generateRSAKeyPair(2048);

        // 4. Generate End-Entity CSR
        CertificateGenerator.CertificateConfig eeConfig = new CertificateGenerator.CertificateConfig();
        eeConfig.commonName = "test.example.com";
        eeConfig.organization = null;
        eeConfig.organizationalUnit = null;
        eeConfig.locality = null;
        eeConfig.state = null;
        eeConfig.country = null;
        eeConfig.sanDnsNames = List.of("test.example.com", "api.example.com");
        eeConfig.addSubjectAlternativeNames = true;

        String csrPem = CertificateGenerator.generateCSR(eePair, eeConfig);
        assertNotNull(csrPem);
        assertTrue(csrPem.contains("BEGIN CERTIFICATE REQUEST"));

        // 5. CA Issues Certificate from CSR
        PKCS10CertificationRequest csr = CertificateGenerator.parseCSR(csrPem);
        X509Certificate eeCert = CertificateAuthorityOperations.issueFromCsr(
                csr,
                rootCert,
                rootCaPair.getPrivate(),
                365,
                "SHA256withRSA"
        );

        assertEquals("CN=test.example.com", eeCert.getSubjectX500Principal().getName());

        // 6. Validate Chain
        List<X509Certificate> chain = List.of(eeCert, rootCert);
        CertificateGenerator.ChainValidationResult validation = CertificateGenerator.validateCertificateChain(chain);
        assertTrue(validation.isValid, "Chain should be fully valid offline");

        // 7. Verify Failure if Root is missing
        List<X509Certificate> incompleteChain = List.of(eeCert);
        CertificateGenerator.ChainValidationResult incompleteValidation = CertificateGenerator.validateCertificateChain(incompleteChain);
        assertFalse(incompleteValidation.isValid, "Chain without root CA should fail validation");
    }

    @Test
    public void testFullLifecycle_ECDSA() throws Exception {
        // 1. Generate Root CA KeyPair (ECDSA)
        KeyPair rootCaPair = AsymmetricKeyOperations.generateECDSAFpKeyPair("secp256r1");

        // 2. Generate Root CA Certificate
        CertificateGenerator.CertificateConfig rootConfig = new CertificateGenerator.CertificateConfig();
        rootConfig.commonName = "Test ECDSA Root CA";
        rootConfig.organization = null;
        rootConfig.organizationalUnit = null;
        rootConfig.locality = null;
        rootConfig.state = null;
        rootConfig.country = null;
        rootConfig.signatureAlgorithm = "SHA256withECDSA";
        X509Certificate rootCert = CertificateGenerator.generateRootCA(rootCaPair, rootConfig, 0);
        assertTrue(CertificateGenerator.verifyCertificateSignature(rootCert));

        // 3. Generate End-Entity KeyPair
        KeyPair eePair = AsymmetricKeyOperations.generateECDSAFpKeyPair("secp256r1");

        // 4. Generate End-Entity CSR
        CertificateGenerator.CertificateConfig eeConfig = new CertificateGenerator.CertificateConfig();
        eeConfig.commonName = "ecdsa.example.com";
        eeConfig.organization = null;
        eeConfig.organizationalUnit = null;
        eeConfig.locality = null;
        eeConfig.state = null;
        eeConfig.country = null;
        eeConfig.signatureAlgorithm = "SHA256withECDSA";

        String csrPem = CertificateGenerator.generateCSR(eePair, eeConfig);

        // 5. CA Issues Certificate from CSR
        PKCS10CertificationRequest csr = CertificateGenerator.parseCSR(csrPem);
        X509Certificate eeCert = CertificateAuthorityOperations.issueFromCsr(
                csr,
                rootCert,
                rootCaPair.getPrivate(),
                365,
                "SHA256withECDSA"
        );

        assertEquals("CN=ecdsa.example.com", eeCert.getSubjectX500Principal().getName());

        // 6. Validate Chain
        List<X509Certificate> chain = List.of(eeCert, rootCert);
        CertificateGenerator.ChainValidationResult validation = CertificateGenerator.validateCertificateChain(chain);
        assertTrue(validation.isValid, "ECDSA chain should be fully valid offline");
    }
}
