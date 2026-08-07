package com.cryptocarver.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class RsaKeyWrapOperationsTest {

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

    private static X509Certificate selfSignedCertificate(KeyPair keyPair) throws Exception {
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "RSA Key Wrap test recipient";
        return CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
    }

    @Test
    void rawOaepRoundTripsAnAesKey() throws Exception {
        KeyPair recipient = rsaKeyPair();
        byte[] aesKey = "0123456789ABCDEF0123456789ABCDEF".substring(0, 32).getBytes();

        RsaKeyWrapOperations.WrapResult result = RsaKeyWrapOperations.wrap(
                aesKey, recipient.getPublic(), null, RsaKeyWrapOperations.WrapProfile.RAW_OAEP);

        assertEquals(RsaKeyWrapOperations.WrapProfile.RAW_OAEP, result.getProfile());
        assertEquals("RSA-OAEP-256", result.getAlgorithm());
        assertNotNull(result.getKcvHex());
        assertFalse(java.util.Arrays.equals(aesKey, result.getWrapped()), "Wrapped output must not equal the plaintext key");

        byte[] recovered = RsaKeyWrapOperations.unwrap(
                result.getWrapped(), recipient.getPrivate(), RsaKeyWrapOperations.WrapProfile.RAW_OAEP);
        assertArrayEquals(aesKey, recovered);
    }

    @Test
    void jweCompactRoundTripsAnAesKey() throws Exception {
        KeyPair recipient = rsaKeyPair();
        byte[] aesKey = new byte[32];
        new java.security.SecureRandom().nextBytes(aesKey);

        RsaKeyWrapOperations.WrapResult result = RsaKeyWrapOperations.wrap(
                aesKey, recipient.getPublic(), null, RsaKeyWrapOperations.WrapProfile.JWE_COMPACT);

        String compact = new String(result.getWrapped(), java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(5, compact.split("\\.", -1).length, "A compact JWE has 5 dot-separated segments");

        byte[] recovered = RsaKeyWrapOperations.unwrap(
                result.getWrapped(), recipient.getPrivate(), RsaKeyWrapOperations.WrapProfile.JWE_COMPACT);
        assertArrayEquals(aesKey, recovered);
    }

    @Test
    void cmsEnvelopedRoundTripsAnAesKeyWithACertificate() throws Exception {
        KeyPair recipient = rsaKeyPair();
        X509Certificate cert = selfSignedCertificate(recipient);
        byte[] aesKey = new byte[16];
        new java.security.SecureRandom().nextBytes(aesKey);

        RsaKeyWrapOperations.WrapResult result = RsaKeyWrapOperations.wrap(
                aesKey, recipient.getPublic(), cert, RsaKeyWrapOperations.WrapProfile.CMS_ENVELOPED);

        byte[] recovered = RsaKeyWrapOperations.unwrap(
                result.getWrapped(), recipient.getPrivate(), RsaKeyWrapOperations.WrapProfile.CMS_ENVELOPED);
        assertArrayEquals(aesKey, recovered);
    }

    @Test
    void cmsEnvelopedRejectsABarePublicKeyWithoutACertificate() throws Exception {
        KeyPair recipient = rsaKeyPair();
        byte[] aesKey = new byte[16];

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> RsaKeyWrapOperations.wrap(
                aesKey, recipient.getPublic(), null, RsaKeyWrapOperations.WrapProfile.CMS_ENVELOPED));
        assertTrue(ex.getMessage().contains("certificate"));
    }

    @Test
    void kcvIsOmittedForNonAesLengthKeyMaterial() throws Exception {
        KeyPair recipient = rsaKeyPair();
        byte[] oddLengthKey = new byte[20]; // not 16/24/32

        RsaKeyWrapOperations.WrapResult result = RsaKeyWrapOperations.wrap(
                oddLengthKey, recipient.getPublic(), null, RsaKeyWrapOperations.WrapProfile.RAW_OAEP);

        assertNull(result.getKcvHex());
    }

    @Test
    void wrapRejectsEmptyKeyMaterial() throws Exception {
        KeyPair recipient = rsaKeyPair();
        assertThrows(IllegalArgumentException.class, () -> RsaKeyWrapOperations.wrap(
                new byte[0], recipient.getPublic(), null, RsaKeyWrapOperations.WrapProfile.RAW_OAEP));
    }
}
