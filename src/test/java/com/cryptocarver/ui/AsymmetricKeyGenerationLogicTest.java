package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AsymmetricKeyGenerationLogicTest {
    @ParameterizedTest
    @ValueSource(strings = {"RSA", "DSA", "ECDSA", "Ed25519"})
    void reportsContainReimportablePemForEveryGenerationType(String algorithm) throws Exception {
        var pair = switch (algorithm) {
            case "RSA" -> AsymmetricKeyOperations.generateRSAKeyPair(1024);
            case "DSA" -> AsymmetricKeyOperations.generateDSAKeyPair("1024/160");
            case "ECDSA" -> AsymmetricKeyOperations.generateECDSAFpKeyPair("secp256r1");
            default -> AsymmetricKeyOperations.generateEd25519KeyPair();
        };
        var result = AsymmetricKeyGenerationLogic.material(pair, algorithm, "secp256r1");
        assertTrue(result.publicReport().contains(result.publicPem()));
        assertTrue(result.privateReport().contains(result.privatePem()));
        assertTrue(result.publicReport().startsWith("=== " + (algorithm.equals("ECDSA") ? "ECDSA F(p)" : algorithm) + " PUBLIC KEY ==="));
        assertArrayEquals(pair.getPublic().getEncoded(), AsymmetricKeyOperations.importPublicKeyPEMAuto(result.publicPem()).getEncoded());
        assertArrayEquals(pair.getPrivate().getEncoded(), AsymmetricKeyOperations.importPrivateKeyPEMAuto(result.privatePem()).getEncoded());
        String report = AsymmetricKeyGenerationLogic.renderGeneratedKeyPair(result.publicPem(), result.privatePem());
        assertTrue(report.startsWith("=== PUBLIC KEY ===\n\n"));
        assertTrue(report.contains("\n\n=== PRIVATE KEY ===\n\n"));
    }
}
