package com.cryptocarver.crypto.hsm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Pkcs11SessionEncapsulationTest {

    private static Pkcs11Session session;
    private static String testAlias;

    @BeforeAll
    public static void setUp() throws Exception {
        String libraryPath = System.getenv("SOFTHSM2_MODULE");
        String confPath = System.getenv("SOFTHSM2_CONF");
        String pinStr = System.getenv("CRYPTOCARVER_SOFTHSM_PIN");
        String slotStr = System.getenv("CRYPTOCARVER_SOFTHSM_SLOT_INDEX");
        String aliasStr = System.getenv("CRYPTOCARVER_SOFTHSM_ALIAS");

        if (libraryPath != null && confPath != null && pinStr != null && slotStr != null && aliasStr != null) {
            System.setProperty("SOFTHSM2_CONF", confPath);
            int slot = Integer.parseInt(slotStr);
            Pkcs11Configuration config = new Pkcs11Configuration("SoftHSM", java.nio.file.Path.of(libraryPath), slot);
            session = Pkcs11SessionManager.getInstance().connect(config, pinStr.toCharArray());
            testAlias = aliasStr;
        }
    }

    @AfterAll
    public static void tearDown() {
        if (session != null) {
            Pkcs11SessionManager.getInstance().disconnect();
        }
    }

    @Test
    public void testNoPublicApiExposesSensitiveTypes() {
        Method[] methods = Pkcs11Session.class.getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers())) {
                Class<?> returnType = method.getReturnType();
                if (returnType.equals(PrivateKey.class)) {
                    fail("Pkcs11Session exposes PrivateKey in method: " + method.getName());
                }
                if (returnType.equals(KeyStore.class)) {
                    fail("Pkcs11Session exposes KeyStore in method: " + method.getName());
                }
                if (returnType.equals(byte[].class)) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("key") && (name.contains("export") || name.contains("get"))) {
                        fail("Pkcs11Session might expose key bytes in method: " + method.getName());
                    }
                }
            }
        }
    }

    @Test
    public void testUpdateCertificateChainEncapsulation() throws NoSuchMethodException {
        Method method = Pkcs11Session.class.getMethod("updateCertificateChain", String.class, Certificate[].class);

        // Ensure the method is public so it can be used, but returns void so it doesn't leak anything
        assertTrue(Modifier.isPublic(method.getModifiers()), "updateCertificateChain must be public");
        assertEquals(void.class, method.getReturnType(), "updateCertificateChain must return void to maintain encapsulation");
    }

    @Test
    public void testSoftHsmUpdateCertificateChain() throws Exception {
        Assumptions.assumeTrue(session != null, "SoftHSM not configured, skipping functional test");
        Assumptions.assumeTrue(session.listPrivateKeysWithCertificate().contains(testAlias), "Configured alias does not exist");

        // 1. Valid update with original chain
        String originalPem = session.certificateChainPem(testAlias);
        List<String> pemCerts = java.util.Arrays.asList(originalPem.split("-----BEGIN CERTIFICATE-----"));
        List<X509Certificate> originalChain = new java.util.ArrayList<>();
        for (String part : pemCerts) {
            if (part.trim().isEmpty()) continue;
            String pem = "-----BEGIN CERTIFICATE-----" + part;
            int endIndex = pem.indexOf("-----END CERTIFICATE-----");
            if (endIndex != -1) {
                originalChain.add(com.cryptocarver.crypto.CertificateGenerator.parseCertificate(pem.substring(0, endIndex + 25)));
            }
        }

        // Functional test: update with valid chain shouldn't throw exception
        assertDoesNotThrow(() -> session.updateCertificateChain(testAlias, originalChain.toArray(new Certificate[0])));

        // 2. Reject key mismatch
        java.security.KeyPair wrongPair = com.cryptocarver.crypto.AsymmetricKeyOperations.generateRSAKeyPair(2048);
        com.cryptocarver.crypto.CertificateGenerator.CertificateConfig wrongConfig = new com.cryptocarver.crypto.CertificateGenerator.CertificateConfig();
        wrongConfig.commonName = "Mismatch Key Test";
        X509Certificate wrongCert = com.cryptocarver.crypto.CertificateGenerator.generateSelfSignedCertificate(wrongPair, wrongConfig);

        Exception e = assertThrows(java.security.GeneralSecurityException.class, () ->
            session.updateCertificateChain(testAlias, new Certificate[]{wrongCert})
        );
        assertTrue(e.getMessage().contains("does not match the token's public key"));

        // 3. Reject duplicate certs
        Exception e2 = assertThrows(java.security.GeneralSecurityException.class, () ->
            session.updateCertificateChain(testAlias, new Certificate[]{originalChain.get(0), originalChain.get(0)})
        );
        assertTrue(e2.getMessage().contains("duplicate"));
    }
}
