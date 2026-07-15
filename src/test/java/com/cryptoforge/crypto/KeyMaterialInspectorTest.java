package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyMaterialInspectorTest {
    @Test
    void describesRsaKeyWithFingerprintAndStrengthGuidance() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var key = generator.generateKeyPair().getPublic();
        String report = KeyMaterialInspector.describeKey(key);
        assertTrue(report.contains("RSA modulus: 1024 bits"));
        assertTrue(report.contains("SHA-256 fingerprint:"));
        assertTrue(report.contains("below 2048 bits"));
    }

    @Test
    void cryptographicallyMatchesRsaKeyPairs() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = generator.generateKeyPair();
        var second = generator.generateKeyPair();
        assertTrue(KeyMaterialInspector.matches(first.getPublic(), first.getPrivate()));
        org.junit.jupiter.api.Assertions.assertFalse(KeyMaterialInspector.matches(first.getPublic(), second.getPrivate()));
    }
}
