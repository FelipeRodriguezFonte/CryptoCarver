package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AsymmetricKeyOperationsAutoImportTest {
    @Test
    void importsEcPemWithoutAssumingRsa() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var imported = AsymmetricKeyOperations.importPrivateKeyPEMAuto(AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate()));
        assertEquals("EC", imported.getAlgorithm());
    }
}
