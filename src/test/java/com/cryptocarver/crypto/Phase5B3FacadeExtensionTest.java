package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class Phase5B3FacadeExtensionTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test void jwsFacadeReturnsOnlyAuthenticatedPayload() throws Exception {
        String token = JOSEService.signJws("authenticated", "HS256", KEY);
        assertEquals("authenticated", JOSEService.verifyJws(token, "HS256", KEY));
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        assertThrows(Exception.class, () -> JOSEService.verifyJws(tampered, "HS256", KEY));
    }

    @Test void jweFacadeRejectsOaepSha1AndAuthenticatesPlaintext() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> JOSEService.encryptJwe("payload", "RSA-OAEP", "A256GCM", KEY));
        String token = JOSEService.encryptJwe("payload", "dir", "A256GCM", KEY);
        assertEquals("payload", JOSEService.decryptJwe(token, KEY));
    }

    @Test void xmlFacadeRejectsDoctypeAndExternalEntity() {
        String hostile = "<!DOCTYPE x [<!ENTITY ext SYSTEM \"file:///etc/passwd\">]><x>&ext;</x>";
        assertThrows(Exception.class, () -> XMLSignatureOperations.requireSafeXml(hostile));
    }

    @Test void certificateFacadeRejectsCertificateOutsideLocalTruststore() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "untrusted.example";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(pair, config);
        CertificateGenerator.TrustValidationResult result = CertificateGenerator.validateAgainstTrustStore(
                certificate, new File("src/test/resources/testks.p12"), "storepass".toCharArray());
        assertFalse(result.valid());
    }
}
