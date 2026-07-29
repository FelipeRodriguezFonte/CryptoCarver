package com.cryptocarver.service;

import com.cryptocarver.model.MaterialDetectionResult;
import com.cryptocarver.model.MaterialDetectionResult.MaterialType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoMaterialDetectorTest {

    @Test
    void testEmptyInput() {
        MaterialDetectionResult result = CryptoMaterialDetector.detect("");
        assertEquals(MaterialType.EMPTY, result.getType());
        assertTrue(result.isValid());
    }

    @Test
    void testPemPrivateKeyDetection() {
        String rsaPem = "-----BEGIN PRIVATE KEY-----\n" +
                "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7\n" +
                "-----END PRIVATE KEY-----";
        MaterialDetectionResult result = CryptoMaterialDetector.detect(rsaPem);
        assertEquals(MaterialType.PEM_PRIVATE_KEY, result.getType());
        assertTrue(result.isSecret());
        assertTrue(result.getStatusLabelText().contains("Private Key PEM"));
    }

    @Test
    void testPemPublicKeyDetection() {
        String rsaPubPem = "-----BEGIN PUBLIC KEY-----\n" +
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuu\n" +
                "-----END PUBLIC KEY-----";
        MaterialDetectionResult result = CryptoMaterialDetector.detect(rsaPubPem);
        assertEquals(MaterialType.PEM_PUBLIC_KEY, result.getType());
        assertFalse(result.isSecret());
        assertTrue(result.getStatusLabelText().contains("Public Key PEM"));
    }

    @Test
    void testPemCertificateDetection() {
        String certPem = "-----BEGIN CERTIFICATE-----\n" +
                "MIICXDCCAcWgAwIBAgIU\n" +
                "-----END CERTIFICATE-----";
        MaterialDetectionResult result = CryptoMaterialDetector.detect(certPem);
        assertEquals(MaterialType.PEM_CERTIFICATE, result.getType());
        assertFalse(result.isSecret());
        assertTrue(result.getStatusLabelText().contains("X.509 Certificate"));
    }

    @Test
    void testHexDetection() {
        String hex = "00112233445566778899AABBCCDDEEFF";
        MaterialDetectionResult result = CryptoMaterialDetector.detect(hex);
        assertEquals(MaterialType.HEX, result.getType());
        assertEquals(16, result.getByteLength());
        assertEquals(128, result.getKeySizeBits());
        assertTrue(result.getStatusLabelText().contains("16 bytes"));
    }

    @Test
    void testJwkDetection() {
        String jwk = "{\"kty\":\"RSA\",\"n\":\"0vx7ago...\",\"e\":\"AQAB\"}";
        MaterialDetectionResult result = CryptoMaterialDetector.detect(jwk);
        assertEquals(MaterialType.JWK, result.getType());
        assertEquals("RSA", result.getAlgorithm());
        assertTrue(result.getStatusLabelText().contains("JWK (RSA)"));
    }

    @Test
    void testJsonArrayDetection() {
        MaterialDetectionResult result = CryptoMaterialDetector.detect("[{\"name\":\"laboratory sample\"}]");
        assertEquals(MaterialType.JSON, result.getType());
        assertTrue(result.isValid());
    }

    @Test
    void testJwtDetection() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        MaterialDetectionResult result = CryptoMaterialDetector.detect(jwt);
        assertEquals(MaterialType.JWT, result.getType());
        assertEquals("JWS", result.getAlgorithm());
        assertTrue(result.getStatusLabelText().contains("JWT / JWS"));
    }

    @Test
    void testOpenPgpDetection() {
        String pgpPub = "-----BEGIN PGP PUBLIC KEY BLOCK-----\nVersion: GnuPG v2\n\nmQENBF...";
        MaterialDetectionResult result = CryptoMaterialDetector.detect(pgpPub);
        assertEquals(MaterialType.OPENPGP_PUBLIC_KEY, result.getType());
        assertFalse(result.isSecret());

        String pgpSec = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nVersion: GnuPG v2\n\nlQOYBF...";
        MaterialDetectionResult secResult = CryptoMaterialDetector.detect(pgpSec);
        assertEquals(MaterialType.OPENPGP_PRIVATE_KEY, secResult.getType());
        assertTrue(secResult.isSecret());
    }
}
