package com.cryptoforge.service;

import com.cryptoforge.model.SecretVisibility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Base64;

public class KeyCertificateFormatServiceTest {

    private static KeyCertificateFormatService service;
    private static KeyPair rsaPair;
    private static KeyPair ecPair;

    @BeforeAll
    public static void setup() throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        service = new KeyCertificateFormatService();
        
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        rsaPair = rsaGen.generateKeyPair();
        
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(256);
        ecPair = ecGen.generateKeyPair();
    }

    @Test
    public void testDetectDerPublicKey() throws Exception {
        byte[] pubBytes = rsaPair.getPublic().getEncoded();
        KeyCertificateFormatService.DetectionResult res = service.detect(pubBytes, null);
        assertEquals(KeyCertificateFormatService.FormatType.DER_PUBLIC_KEY, res.type);
        assertEquals("RSA", res.algorithm);
    }
    
    @Test
    public void testDetectDerPrivateKey() throws Exception {
        byte[] privBytes = rsaPair.getPrivate().getEncoded();
        KeyCertificateFormatService.DetectionResult res = service.detect(privBytes, null);
        assertEquals(KeyCertificateFormatService.FormatType.DER_PRIVATE_KEY, res.type);
        assertTrue(res.hasPrivateKey);
    }

    @Test
    public void testDetectPEMPublicKey() throws Exception {
        String pem = "-----BEGIN PUBLIC KEY-----\n" +
                     Base64.getEncoder().encodeToString(ecPair.getPublic().getEncoded()) +
                     "\n-----END PUBLIC KEY-----";
        KeyCertificateFormatService.DetectionResult res = service.detect(pem.getBytes(), null);
        assertEquals(KeyCertificateFormatService.FormatType.PEM_PUBLIC_KEY, res.type);
        assertEquals("EC", res.algorithm);
    }
    
    @Test
    public void testDetectPEMPrivateKey() throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n" +
                     Base64.getEncoder().encodeToString(ecPair.getPrivate().getEncoded()) +
                     "\n-----END PRIVATE KEY-----";
        KeyCertificateFormatService.DetectionResult res = service.detect(pem.getBytes(), null);
        assertEquals(KeyCertificateFormatService.FormatType.PEM_PRIVATE_KEY, res.type);
        assertEquals("EC", res.algorithm);
        assertTrue(res.hasPrivateKey);
    }
    
    @Test
    public void testConvertPublicToJWK() throws Exception {
        byte[] pubBytes = rsaPair.getPublic().getEncoded();
        KeyCertificateFormatService.DetectionResult res = service.detect(pubBytes, null);
        String jwk = service.convert(res, "JWK", SecretVisibility.FULL_LAB);
        assertTrue(jwk.contains("\"kty\":\"RSA\""));
    }
    
    @Test
    public void testConvertPrivateMaskedThrows() throws Exception {
        byte[] privBytes = rsaPair.getPrivate().getEncoded();
        KeyCertificateFormatService.DetectionResult res = service.detect(privBytes, null);
        Exception e = assertThrows(Exception.class, () -> {
            service.convert(res, "PEM", SecretVisibility.MASKED);
        });
        assertTrue(e.getMessage().contains("Policy prevents exporting"));
    }
    
    @Test
    public void testConvertPrivateFullLab() throws Exception {
        byte[] privBytes = rsaPair.getPrivate().getEncoded();
        KeyCertificateFormatService.DetectionResult res = service.detect(privBytes, null);
        String pem = service.convert(res, "PEM", SecretVisibility.FULL_LAB);
        assertTrue(pem.contains("BEGIN PRIVATE KEY"));
    }

    @Test
    public void testValidatePairValid() throws Exception {
        boolean valid = service.validatePair(rsaPair.getPublic().getEncoded(), rsaPair.getPrivate().getEncoded());
        assertTrue(valid);
    }

    @Test
    public void testValidatePairInvalid() throws Exception {
        boolean valid = service.validatePair(rsaPair.getPublic().getEncoded(), ecPair.getPrivate().getEncoded());
        assertFalse(valid);
    }
}
