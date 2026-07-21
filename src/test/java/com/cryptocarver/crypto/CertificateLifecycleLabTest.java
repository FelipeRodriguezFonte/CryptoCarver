package com.cryptocarver.crypto;

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

        // 8. Test Non-CA Issuer
        CertificateGenerator.CertificateConfig invalidRootConfig = new CertificateGenerator.CertificateConfig();
        invalidRootConfig.commonName = "Fake Root";
        // End-entity basic constraints by default (CA:FALSE)
        X509Certificate fakeRoot = CertificateGenerator.generateSelfSignedCertificate(rootCaPair, invalidRootConfig);
        try {
            CertificateAuthorityOperations.issueFromCsr(csr, fakeRoot, rootCaPair.getPrivate(), 365, "SHA256withRSA");
            fail("Should have rejected non-CA issuer");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("is not a CA"));
        }

        // 9. Test Expired Chain
        java.util.Date futureDate = new java.util.Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 400); // 400 days in future
        CertificateGenerator.ChainValidationResult expiredValidation = CertificateGenerator.validateCertificateChain(chain, futureDate);
        assertFalse(expiredValidation.isValid, "Chain should fail validation due to expiration");

        // 9b. Normal Validation Immediately After
        CertificateGenerator.ChainValidationResult normalValidation = CertificateGenerator.validateCertificateChain(chain);
        assertTrue(normalValidation.isValid, "Chain should be valid when date is reset/null, ensuring no leaked global state");

        // 10. Test Disordered Chain (should still pass with PKIX CertPathBuilder)
        List<X509Certificate> disorderedChain = List.of(rootCert, eeCert);
        CertificateGenerator.ChainValidationResult disorderedValidation = CertificateGenerator.validateCertificateChain(disorderedChain);
        assertTrue(disorderedValidation.isValid, "CertPathBuilder should handle disordered chains automatically");
        assertTrue(disorderedValidation.details.stream().anyMatch(detail -> detail.contains("Target: CN=test.example.com")), "CertPath should contain the correct end-entity certificate");

        // 11. Test Invalid CSR Signature
        // Sign the CSR with a private key that doesn't match the public key inside the CSR
        KeyPair wrongPair = AsymmetricKeyOperations.generateRSAKeyPair(2048);
        org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder badCsrBuilder =
            new org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder(
                new org.bouncycastle.asn1.x500.X500Name("CN=bad"), eePair.getPublic());
        org.bouncycastle.operator.ContentSigner badSigner =
            new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(wrongPair.getPrivate());
        PKCS10CertificationRequest invalidCsr = badCsrBuilder.build(badSigner);

        try {
            CertificateAuthorityOperations.issueFromCsr(invalidCsr, rootCert, rootCaPair.getPrivate(), 365, "SHA256withRSA");
            fail("Should have rejected invalid CSR signature");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("CSR signature is invalid"), "Expected CSR signature invalid message");
        }
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

    @Test
    public void testRevocationAndCrl() throws Exception {
        // 1. Generate Root CA
        KeyPair rootCaPair = AsymmetricKeyOperations.generateRSAKeyPair(2048);
        CertificateGenerator.CertificateConfig rootConfig = new CertificateGenerator.CertificateConfig();
        rootConfig.commonName = "Test Root CA CRL";
        X509Certificate rootCert = CertificateGenerator.generateRootCA(rootCaPair, rootConfig, 0);

        // 2. Issue EE Cert
        KeyPair eePair = AsymmetricKeyOperations.generateRSAKeyPair(2048);
        CertificateGenerator.CertificateConfig eeConfig = new CertificateGenerator.CertificateConfig();
        eeConfig.commonName = "crl.example.com";
        String csrPem = CertificateGenerator.generateCSR(eePair, eeConfig);
        PKCS10CertificationRequest csr = CertificateGenerator.parseCSR(csrPem);
        X509Certificate eeCert = CertificateAuthorityOperations.issueFromCsr(
                csr, rootCert, rootCaPair.getPrivate(), 365, "SHA256withRSA",
                CertificateAuthorityOperations.IssuanceProfile.TLS_SERVER, 0);

        List<X509Certificate> chain = List.of(eeCert, rootCert);

        // 3. Validation without CRL (NOT EVALUATED)
        CertificateGenerator.ChainValidationResult valNoCrl = CertificateGenerator.validateCertificateChain(chain);
        assertTrue(valNoCrl.isValid, "Valid when no CRLs provided");

        // 4. Create empty CRL
        java.security.cert.X509CRL crl = RevocationOperations.generateEmptyCrl(rootCert, rootCaPair.getPrivate(), "SHA256withRSA", 7);
        assertNotNull(crl);

        // 5. Validation with empty CRL (Valid)
        CertificateGenerator.ChainValidationResult valEmptyCrl = CertificateGenerator.validateCertificateChain(chain, List.of(crl));
        assertTrue(valEmptyCrl.isValid,
                "Valid against empty CRL: " + valEmptyCrl.message + " / " + String.join(" | ", valEmptyCrl.details));

        // 6. Revoke EE cert
        java.math.BigInteger serialToRevoke = eeCert.getSerialNumber();
        java.security.cert.X509CRL revokedCrl = RevocationOperations.revokeCertificate(crl, rootCert, rootCaPair.getPrivate(), "SHA256withRSA", serialToRevoke, org.bouncycastle.asn1.x509.CRLReason.keyCompromise, 7);

        // 7. Validation with revoked CRL (Invalid)
        CertificateGenerator.ChainValidationResult valRevoked = CertificateGenerator.validateCertificateChain(chain, List.of(revokedCrl));
        assertFalse(valRevoked.isValid, "Invalid against revoked CRL");
        assertTrue(valRevoked.message.contains("revoked"), "Should indicate revocation");
    }
}
