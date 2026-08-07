package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class TR34OperationsTest {

    @BeforeAll
    static void installBouncyCastleProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair, String cn) throws Exception {
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = cn;
        return CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
    }

    @Test
    void distributeThenReceiveRecoversTheKeyAndVerifiesTheSignature() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);

        byte[] distributed = TR34Operations.distributeKey(
                key, senderCert, sender.getPrivate(), receiverCert, "kek-001");

        assertFalse(java.util.Arrays.equals(key, distributed), "Distributed bytes must not equal the plaintext key");

        TR34Operations.ReceivedKey received = TR34Operations.receiveKey(distributed, receiver.getPrivate(), senderCert);

        assertTrue(received.isSignatureVerified());
        assertArrayEquals(key, received.getKey());
        assertEquals("kek-001", received.getKeyId());
    }

    @Test
    void receiveFlagsAnUnverifiedSignatureWithoutThrowing() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair impostor = rsaKeyPair();
        X509Certificate impostorCert = selfSignedCertificate(impostor, "TR-34 test impostor");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        byte[] key = new byte[16];
        byte[] distributed = TR34Operations.distributeKey(key, senderCert, sender.getPrivate(), receiverCert, (String) null);

        // Receiver expects the impostor's cert instead of the real sender's — verification must
        // fail cleanly (verified=false), not throw.
        TR34Operations.ReceivedKey received = TR34Operations.receiveKey(distributed, receiver.getPrivate(), impostorCert);

        assertFalse(received.isSignatureVerified());
    }

    @Test
    void distributeRejectsMissingReceiverCertificate() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        byte[] key = new byte[16];

        assertThrows(IllegalArgumentException.class,
                () -> TR34Operations.distributeKey(key, senderCert, sender.getPrivate(), null, (String) null));
    }

    @Test
    void distributeRejectsEmptyKeyMaterial() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        assertThrows(IllegalArgumentException.class, () -> TR34Operations.distributeKey(
                new byte[0], senderCert, sender.getPrivate(), receiverCert, (String) null));
    }

    @Test
    void receiveRejectsMissingExpectedSenderCertificate() throws Exception {
        KeyPair receiver = rsaKeyPair();
        assertThrows(IllegalArgumentException.class,
                () -> TR34Operations.receiveKey(new byte[]{1, 2, 3}, receiver.getPrivate(), null));
    }

    @Test
    void associatedDataIsOptional() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        byte[] key = new byte[16];
        byte[] distributed = TR34Operations.distributeKey(key, senderCert, sender.getPrivate(), receiverCert, (String) null);
        TR34Operations.ReceivedKey received = TR34Operations.receiveKey(distributed, receiver.getPrivate(), senderCert);

        assertTrue(received.isSignatureVerified());
        assertNull(received.getKeyId());
    }

    @Test
    void twoPassRoundTripVerifiesSignatureAndNonceBinding() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        byte[] nonce = TR34Operations.generateChallengeNonce();

        byte[] distributed = TR34Operations.distributeKeyTwoPass(
                key, senderCert, sender.getPrivate(), receiverCert, nonce, "kek-002");

        TR34Operations.ReceivedKey received =
                TR34Operations.receiveKeyTwoPass(distributed, receiver.getPrivate(), senderCert, nonce);

        assertTrue(received.isSignatureVerified());
        assertTrue(received.isNonceVerified());
        assertArrayEquals(key, received.getKey());
        assertEquals("kek-002", received.getKeyId());
    }

    @Test
    void twoPassReceiveDetectsAMismatchedOrReplayedNonce() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        byte[] key = new byte[16];
        byte[] nonceUsedForDistribution = TR34Operations.generateChallengeNonce();
        byte[] distributed = TR34Operations.distributeKeyTwoPass(
                key, senderCert, sender.getPrivate(), receiverCert, nonceUsedForDistribution, null);

        // The receiver expects a DIFFERENT nonce than the one actually bound — e.g. a captured,
        // replayed old response being presented against a fresh challenge. The signature is still
        // perfectly valid (it really was signed by the real sender); only the freshness check fails.
        byte[] freshExpectedNonce = TR34Operations.generateChallengeNonce();
        TR34Operations.ReceivedKey received = TR34Operations.receiveKeyTwoPass(
                distributed, receiver.getPrivate(), senderCert, freshExpectedNonce);

        assertTrue(received.isSignatureVerified(), "Signature must still verify — only the nonce binding is stale");
        assertFalse(received.isNonceVerified(), "A mismatched nonce must be flagged, not silently accepted");
        assertArrayEquals(key, received.getKey());
    }

    @Test
    void oneNonPassReceiveNeverReportsNonceVerified() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        byte[] key = new byte[16];
        byte[] distributed = TR34Operations.distributeKey(key, senderCert, sender.getPrivate(), receiverCert, (String) null);
        TR34Operations.ReceivedKey received = TR34Operations.receiveKey(distributed, receiver.getPrivate(), senderCert);

        assertTrue(received.isSignatureVerified());
        assertFalse(received.isNonceVerified(), "One-pass results never claim a nonce was checked");
    }

    @Test
    void generateChallengeNonceProducesDistinctSixteenByteValues() {
        byte[] first = TR34Operations.generateChallengeNonce();
        byte[] second = TR34Operations.generateChallengeNonce();

        assertEquals(16, first.length);
        assertEquals(16, second.length);
        assertFalse(java.util.Arrays.equals(first, second), "Two challenges must not collide");
    }

    @Test
    void distributeKeyTwoPassRejectsMissingNonce() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");
        byte[] key = new byte[16];

        assertThrows(IllegalArgumentException.class, () -> TR34Operations.distributeKeyTwoPass(
                key, senderCert, sender.getPrivate(), receiverCert, null, "kek-003"));
        assertThrows(IllegalArgumentException.class, () -> TR34Operations.distributeKeyTwoPass(
                key, senderCert, sender.getPrivate(), receiverCert, new byte[0], "kek-003"));
    }

    @Test
    void receiveKeyTwoPassRejectsMissingExpectedNonce() throws Exception {
        KeyPair sender = rsaKeyPair();
        X509Certificate senderCert = selfSignedCertificate(sender, "TR-34 test KDH");
        KeyPair receiver = rsaKeyPair();
        X509Certificate receiverCert = selfSignedCertificate(receiver, "TR-34 test KRD");

        byte[] key = new byte[16];
        byte[] distributed = TR34Operations.distributeKeyTwoPass(
                key, senderCert, sender.getPrivate(), receiverCert, TR34Operations.generateChallengeNonce(), null);

        assertThrows(IllegalArgumentException.class, () -> TR34Operations.receiveKeyTwoPass(
                distributed, receiver.getPrivate(), senderCert, null));
        assertThrows(IllegalArgumentException.class, () -> TR34Operations.receiveKeyTwoPass(
                distributed, receiver.getPrivate(), senderCert, new byte[0]));
    }
}
