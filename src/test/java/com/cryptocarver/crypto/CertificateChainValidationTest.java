package com.cryptocarver.crypto;

import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateChainValidationTest {
    @Test
    void validatesLeafAndLaboratoryRootChain() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var rootPair = generator.generateKeyPair();
        CertificateGenerator.CertificateConfig rootConfig = new CertificateGenerator.CertificateConfig();
        rootConfig.commonName = "Root CA";
        var root = CertificateGenerator.generateRootCA(rootPair, rootConfig, 1);
        CertificateGenerator.CertificateConfig leafConfig = new CertificateGenerator.CertificateConfig();
        leafConfig.commonName = "Leaf Cert";
        String pem = CertificateGenerator.generateCSR(generator.generateKeyPair(), leafConfig);
        var csr = new PKCS10CertificationRequest(Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----|\\s", "")));
        var leaf = CertificateAuthorityOperations.issueFromCsr(csr, root, rootPair.getPrivate(), 30, "SHA256withRSA");
        var result = CertificateGenerator.validateCertificateChain(List.of(leaf, root));
        assertTrue(result.isValid, result.message);
        assertTrue(result.details.stream().anyMatch(detail -> detail.contains("Trust Anchor")));
    }
}
