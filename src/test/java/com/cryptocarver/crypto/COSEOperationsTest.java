package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class COSEOperationsTest {

    @BeforeAll
    static void installBouncyCastleProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    // ---------------------------------------------------------------- Sign1

    @Test
    void sign1ThenVerify1RecoversThePayloadWithEcKey() throws Exception {
        KeyPair pair = ecKeyPair();
        byte[] payload = "Hola COSE_Sign1 (ES256)".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] message = COSEOperations.sign1(payload, pair.getPrivate(), pair.getPublic(), COSEOperations.SignAlgorithm.ES256);
        assertFalse(Arrays.equals(payload, message), "The COSE message must not equal the raw payload");

        COSEOperations.Sign1Result result = COSEOperations.verify1(message, pair.getPublic());
        assertTrue(result.isVerified());
        assertArrayEquals(payload, result.getPayload());
    }

    @Test
    void sign1ThenVerify1RecoversThePayloadWithRsaPssKey() throws Exception {
        KeyPair pair = rsaKeyPair();
        byte[] payload = "Hola COSE_Sign1 (PS256)".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] message = COSEOperations.sign1(payload, pair.getPrivate(), pair.getPublic(), COSEOperations.SignAlgorithm.PS256);
        COSEOperations.Sign1Result result = COSEOperations.verify1(message, pair.getPublic());

        assertTrue(result.isVerified());
        assertArrayEquals(payload, result.getPayload());
    }

    @Test
    void verify1FlagsATamperedSignatureWithoutThrowing() throws Exception {
        KeyPair signer = ecKeyPair();
        KeyPair impostor = ecKeyPair();
        byte[] payload = "Payload signed by the real key".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] message = COSEOperations.sign1(payload, signer.getPrivate(), signer.getPublic(), COSEOperations.SignAlgorithm.ES256);

        // Verifying against the impostor's public key must fail cleanly (verified=false), not throw.
        COSEOperations.Sign1Result result = COSEOperations.verify1(message, impostor.getPublic());
        assertFalse(result.isVerified());
    }

    @Test
    void sign1RejectsMissingPrivateKey() {
        byte[] payload = "x".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> COSEOperations.sign1(payload, null, null, COSEOperations.SignAlgorithm.ES256));
    }

    @Test
    void verify1RejectsAStructurallyInvalidMessage() throws Exception {
        KeyPair pair = ecKeyPair();
        assertThrows(COSE.CoseException.class,
                () -> COSEOperations.verify1(new byte[]{1, 2, 3, 4}, pair.getPublic()));
    }

    // ---------------------------------------------------------------- Mac0

    @Test
    void mac0ThenVerifyMac0RecoversThePayload() throws Exception {
        byte[] key = randomBytes(32);
        byte[] payload = "Hola COSE_Mac0 (HS256)".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] message = COSEOperations.mac0(payload, key, COSEOperations.MacAlgorithm.HS256);
        COSEOperations.Mac0Result result = COSEOperations.verifyMac0(message, key);

        assertTrue(result.isVerified());
        assertArrayEquals(payload, result.getPayload());
    }

    @Test
    void verifyMac0DetectsAWrongKeyWithoutThrowing() throws Exception {
        byte[] key = randomBytes(32);
        byte[] wrongKey = randomBytes(32);
        byte[] payload = "Payload MACed under the real key".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] message = COSEOperations.mac0(payload, key, COSEOperations.MacAlgorithm.HS256);
        COSEOperations.Mac0Result result = COSEOperations.verifyMac0(message, wrongKey);

        assertFalse(result.isVerified());
    }

    @Test
    void mac0RequiredKeyBytesMatchesAlgorithm() {
        assertEquals(32, COSEOperations.MacAlgorithm.HS256.requiredKeyBytes());
        assertEquals(48, COSEOperations.MacAlgorithm.HS384.requiredKeyBytes());
        assertEquals(64, COSEOperations.MacAlgorithm.HS512.requiredKeyBytes());
    }

    // ---------------------------------------------------------------- Encrypt0

    @Test
    void encrypt0ThenDecrypt0RecoversThePayload() throws Exception {
        byte[] key = randomBytes(COSEOperations.EncryptAlgorithm.A256GCM.requiredKeyBytes());
        byte[] payload = "Hola COSE_Encrypt0 (A256GCM)".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] message = COSEOperations.encrypt0(payload, key, COSEOperations.EncryptAlgorithm.A256GCM);
        assertFalse(Arrays.equals(payload, message));

        byte[] recovered = COSEOperations.decrypt0(message, key);
        assertArrayEquals(payload, recovered);
    }

    @Test
    void encrypt0ProducesADifferentMessageEachTime() throws Exception {
        byte[] key = randomBytes(16);
        byte[] payload = "Same payload, must get a fresh IV each time".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] first = COSEOperations.encrypt0(payload, key, COSEOperations.EncryptAlgorithm.A128GCM);
        byte[] second = COSEOperations.encrypt0(payload, key, COSEOperations.EncryptAlgorithm.A128GCM);

        assertFalse(Arrays.equals(first, second), "Each COSE_Encrypt0 message must use a fresh random IV");
    }

    @Test
    void decrypt0RejectsAWrongKey() throws Exception {
        byte[] key = randomBytes(16);
        byte[] wrongKey = randomBytes(16);
        byte[] payload = "Payload encrypted under the real key".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] message = COSEOperations.encrypt0(payload, key, COSEOperations.EncryptAlgorithm.A128GCM);

        // AEAD tag mismatch — must throw, there is no partial plaintext to report.
        assertThrows(COSE.CoseException.class, () -> COSEOperations.decrypt0(message, wrongKey));
    }

    @Test
    void encrypt0RequiredKeyBytesMatchesAlgorithm() {
        assertEquals(16, COSEOperations.EncryptAlgorithm.A128GCM.requiredKeyBytes());
        assertEquals(24, COSEOperations.EncryptAlgorithm.A192GCM.requiredKeyBytes());
        assertEquals(32, COSEOperations.EncryptAlgorithm.A256GCM.requiredKeyBytes());
    }
}
