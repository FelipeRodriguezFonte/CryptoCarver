package com.cryptocarver.model;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.google.gson.Gson;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

public class GeneratedAsymmetricKeySummaryTest {

    @BeforeAll
    public static void setUp() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    @DisplayName("RSA Key Pair summary metadata and compatibility rules")
    public void testRsaSummary() throws Exception {
        KeyPair keyPair = AsymmetricKeyOperations.generateRSAKeyPair(2048);
        GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "RSA", "2048 bits");

        assertEquals("RSA", summary.getAlgorithm());
        assertEquals("2048 bits", summary.getCurveOrKeySize());
        assertNotNull(summary.getPublicFingerprintTruncated());
        assertEquals(16, summary.getPublicFingerprintTruncated().length());
        assertEquals("Generated locally", summary.getOrigin());

        assertTrue(summary.isEncryptionSupported());
        assertTrue(summary.isSignatureSupported());
        assertTrue(summary.isCertificateSupported());
        assertTrue(summary.getCompatibleUses().contains("ENCRYPTION"));
    }

    @Test
    @DisplayName("ECDSA Key Pair summary metadata and compatibility rules")
    public void testEcdsaSummary() throws Exception {
        KeyPair keyPair = AsymmetricKeyOperations.generateECDSAFpKeyPair("secp256r1");
        GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "ECDSA", "secp256r1");

        assertEquals("ECDSA", summary.getAlgorithm());
        assertEquals("secp256r1", summary.getCurveOrKeySize());
        assertNotNull(summary.getPublicFingerprintTruncated());

        assertFalse(summary.isEncryptionSupported(), "ECDSA must not support RSA encryption");
        assertTrue(summary.isSignatureSupported());
        assertTrue(summary.isCertificateSupported());
    }

    @Test
    @DisplayName("DSA Key Pair summary metadata and compatibility rules")
    public void testDsaSummary() throws Exception {
        KeyPair keyPair = AsymmetricKeyOperations.generateDSAKeyPair("2048");
        GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "DSA", "2048 bits");

        assertEquals("DSA", summary.getAlgorithm());
        assertEquals("2048 bits", summary.getCurveOrKeySize());
        assertFalse(summary.isEncryptionSupported());
        assertTrue(summary.isSignatureSupported());
    }

    @Test
    @DisplayName("Ed25519 Key Pair summary metadata and compatibility rules")
    public void testEd25519Summary() throws Exception {
        KeyPair keyPair = AsymmetricKeyOperations.generateEd25519KeyPair();
        GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "Ed25519", "Ed25519 (255-bit curve)");

        assertEquals("Ed25519", summary.getAlgorithm());
        assertEquals("Ed25519 (255-bit curve)", summary.getCurveOrKeySize());
        assertFalse(summary.isEncryptionSupported());
        assertTrue(summary.isSignatureSupported());
    }

    @Test
    @DisplayName("Private key PEM and bytes do not leak in toString() or Gson serialization")
    public void testNonLeakage() throws Exception {
        KeyPair keyPair = AsymmetricKeyOperations.generateRSAKeyPair(2048);
        GeneratedAsymmetricKeySummary summary = new GeneratedAsymmetricKeySummary(keyPair, "RSA", "2048 bits");

        String privPem = summary.getPrivateKeyPem();
        assertNotNull(privPem);
        assertTrue(privPem.contains("BEGIN PRIVATE KEY"));

        assertFalse(summary.toString().contains("BEGIN PRIVATE KEY"), "toString() must not contain private key PEM");
        assertFalse(summary.toString().contains("privateKeyPem"), "toString() must not contain privateKeyPem field");

        Gson gson = new Gson();
        String json = gson.toJson(summary);
        assertFalse(json.contains("BEGIN PRIVATE KEY"), "Gson serialization must not contain private key PEM");
        assertFalse(json.contains("privateKeyBytes"), "Gson serialization must not contain transient privateKeyBytes");
        assertFalse(json.contains("privateKeyPem"), "Gson serialization must not contain transient privateKeyPem");
    }
}
