package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateLinterTest {
    @Test
    void flagsWeakRsaAndMissingSan() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var config = new CertificateGenerator.CertificateConfig();
        config.addSubjectAlternativeNames = false;
        var certificate = CertificateGenerator.generateSelfSignedCertificate(generator.generateKeyPair(), config);
        var findings = CertificateLinter.lint(certificate);
        assertTrue(findings.stream().anyMatch(finding -> finding.message().contains("below 2048")));
        assertTrue(findings.stream().anyMatch(finding -> finding.message().contains("Subject Alternative Name")));
    }
}
