package com.cryptocarver.crypto;

import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CertificateAuthorityOperationsTest {
    @Test
    void issuesEndEntityFromValidCsrAndCopiesSan() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var rootPair = generator.generateKeyPair();
        var root = CertificateGenerator.generateRootCA(rootPair, new CertificateGenerator.CertificateConfig(), 1);
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "issued.example.test";
        config.sanDnsNames = java.util.List.of("issued.example.test");
        config.sanIpAddresses = java.util.List.of();
        String pem = CertificateGenerator.generateCSR(generator.generateKeyPair(), config);
        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----|\\s", "")));
        var issued = CertificateAuthorityOperations.issueFromCsr(csr, root, rootPair.getPrivate(), 90, "SHA256withRSA");
        issued.verify(root.getPublicKey());
        assertEquals(-1, issued.getBasicConstraints());
        assertNotNull(issued.getSubjectAlternativeNames());
    }

    @Test
    void issuesIntermediateCaWithRequestedPathLength() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var rootPair = generator.generateKeyPair();
        var root = CertificateGenerator.generateRootCA(rootPair, new CertificateGenerator.CertificateConfig(), 2);
        String pem = CertificateGenerator.generateCSR(generator.generateKeyPair(), new CertificateGenerator.CertificateConfig());
        var csr = new PKCS10CertificationRequest(Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----|\\s", "")));
        var intermediate = CertificateAuthorityOperations.issueIntermediateCaFromCsr(csr, root, rootPair.getPrivate(), 90, "SHA256withRSA", 0);
        intermediate.verify(root.getPublicKey());
        assertEquals(0, intermediate.getBasicConstraints());
        assertNotNull(intermediate.getKeyUsage());
    }

    @Test
    void suggestsSignatureAlgorithmForEcIssuer() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        assertEquals("SHA256withECDSA", CertificateAuthorityOperations.suggestSignatureAlgorithm(generator.generateKeyPair().getPrivate()));
    }

    @Test
    void rejectsIssuerPrivateKeyThatDoesNotMatchCaCertificate() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var rootPair = generator.generateKeyPair();
        var root = CertificateGenerator.generateRootCA(rootPair, new CertificateGenerator.CertificateConfig(), 1);
        String pem = CertificateGenerator.generateCSR(generator.generateKeyPair(), new CertificateGenerator.CertificateConfig());
        var csr = new PKCS10CertificationRequest(Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----|\\s", "")));
        assertThrows(IllegalArgumentException.class, () -> CertificateAuthorityOperations.issueFromCsr(csr, root,
                generator.generateKeyPair().getPrivate(), 30, "SHA256withRSA"));
    }
}
