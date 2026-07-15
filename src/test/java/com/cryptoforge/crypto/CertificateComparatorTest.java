package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateComparatorTest {
    @Test
    void reportsSubjectAndPublicKeyDifferences() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = new CertificateGenerator.CertificateConfig();
        first.commonName = "one.example.test";
        var second = new CertificateGenerator.CertificateConfig();
        second.commonName = "two.example.test";
        String report = CertificateComparator.compare(
                CertificateGenerator.generateSelfSignedCertificate(generator.generateKeyPair(), first),
                CertificateGenerator.generateSelfSignedCertificate(generator.generateKeyPair(), second));
        assertTrue(report.contains("[DIFF] Subject"));
        assertTrue(report.contains("Public Key SHA-256"));
    }
}
