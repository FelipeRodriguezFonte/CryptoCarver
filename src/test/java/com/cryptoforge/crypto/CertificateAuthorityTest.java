package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateAuthorityTest {
    @Test
    void rootCaHasCaConstraintsAndSelfSignature() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var certificate = CertificateGenerator.generateRootCA(generator.generateKeyPair(), new CertificateGenerator.CertificateConfig(), 1);
        certificate.verify(certificate.getPublicKey());
        assertEquals(1, certificate.getBasicConstraints());
        assertTrue(certificate.getKeyUsage()[5]); // keyCertSign
    }
}
