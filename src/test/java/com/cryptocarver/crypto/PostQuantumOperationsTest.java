package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class PostQuantumOperationsTest {

    @Test
    void dilithiumSignsAndVerifies() throws Exception {
        KeyPair keyPair = PostQuantumOperations.generateKeyPair("Dilithium2");
        KeyPair reloaded = new KeyPair(
                PostQuantumOperations.importPublicKey("Dilithium2", keyPair.getPublic().getEncoded()),
                PostQuantumOperations.importPrivateKey("Dilithium2", keyPair.getPrivate().getEncoded()));
        byte[] message = "CryptoCarver PQC test".getBytes(StandardCharsets.UTF_8);

        byte[] signature = PostQuantumOperations.sign(reloaded.getPrivate(), message, "Dilithium2");

        assertTrue(PostQuantumOperations.verify(reloaded.getPublic(), message, signature, "Dilithium2"));
        assertFalse(PostQuantumOperations.verify(reloaded.getPublic(), "altered".getBytes(StandardCharsets.UTF_8), signature, "Dilithium2"));
    }

    @Test
    void kyberEncapsulatesAndDecapsulatesTheSameSecret() throws Exception {
        KeyPair keyPair = PostQuantumOperations.generateKeyPair("Kyber512");

        PostQuantumOperations.KEMResult result = PostQuantumOperations.encapsulate(keyPair.getPublic(), "ML-KEM-512");
        byte[] recovered = PostQuantumOperations.decapsulate(keyPair.getPrivate(), result.encapsulation(), "ML-KEM-512");

        assertArrayEquals(result.sharedSecret(), recovered);
    }

    @Test
    void recognizesNistAndBouncyCastleAliasesButRejectsDifferentParameterSets() {
        assertTrue(PostQuantumOperations.areAlgorithmsCompatible("Kyber512", "ML-KEM-512"));
        assertTrue(PostQuantumOperations.areAlgorithmsCompatible("Dilithium3", "ML-DSA-65"));
        assertFalse(PostQuantumOperations.areAlgorithmsCompatible("ML-KEM-512", "ML-KEM-768"));
        assertFalse(PostQuantumOperations.areAlgorithmsCompatible("ML-DSA-44", "ML-DSA-65"));
    }

    @Test
    void rejectsNonPQCKey() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair kp = kpg.generateKeyPair();

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            PostQuantumOperations.sign(kp.getPrivate(), "Test".getBytes(), "ML-DSA-44");
        });
        assertTrue(ex.getMessage().contains("Unknown or unsupported PQC algorithm in key"));
    }
}
