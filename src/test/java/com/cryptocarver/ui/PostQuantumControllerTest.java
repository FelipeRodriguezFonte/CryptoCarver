package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PostQuantumControllerTest {

    @Test
    void rejectsInvalidPQCImport() throws Exception {
        PostQuantumController controller = new PostQuantumController();
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair kp = kpg.generateKeyPair();
        String rsaPub = "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            controller.importKeysFromContents(List.of(rsaPub));
        });
        assertTrue(ex.getMessage().contains("Cannot detect PQC algorithm"));
        assertNull(controller.getCurrentPublicKey());
    }

    @Test
    void rejectsMismatchedPairAndDoesNotModifyState() throws Exception {
        PostQuantumController controller = new PostQuantumController();
        java.security.KeyPair kp = com.cryptocarver.crypto.PostQuantumOperations.generateKeyPair("ML-DSA-44");
        String pubPem = "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";
        java.security.KeyPair kp2 = com.cryptocarver.crypto.PostQuantumOperations.generateKeyPair("ML-DSA-65");
        String privPem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getEncoder().encodeToString(kp2.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            controller.importKeysFromContents(List.of(pubPem, privPem));
        });
        assertTrue(ex.getMessage().contains("Mismatched keys"));
        assertNull(controller.getCurrentPublicKey());
        assertNull(controller.getCurrentPrivateKey());
    }
}
