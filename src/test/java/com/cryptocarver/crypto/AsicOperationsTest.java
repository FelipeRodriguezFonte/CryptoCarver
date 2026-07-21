package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.Test;

class AsicOperationsTest {

    @Test
    void createsAndVerifiesMinimalAsicS() throws Exception {
        char[] password = "asic-laboratory-password".toCharArray();
        Path keyStoreFile = Files.createTempFile("cryptocarver-asic-", ".p12");
        try {
            KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = "ASiC laboratory signer";
            X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(pair, config);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            keyStore.setKeyEntry("asic", pair.getPrivate(), password, new java.security.cert.Certificate[] { certificate });
            try (var output = Files.newOutputStream(keyStoreFile)) {
                keyStore.store(output, password);
            }

            byte[] content = "ASiC-S payload".getBytes(StandardCharsets.UTF_8);
            byte[] container = AsicOperations.createAsicS(content, "payload.txt", keyStoreFile.toFile(), password);
            AsicOperations.AsicInspection inspection = AsicOperations.inspectAndVerify(container);

            assertEquals("payload.txt", inspection.payloadName());
            assertEquals(3, inspection.entryCount());
            assertTrue(inspection.mimeTypeValid());
            assertTrue(inspection.signatureValid());
            assertEquals("CAdES-BES", inspection.cadesProfile());
            assertTrue(inspection.certificateBindingValid());
            assertEquals(CmsInspectionReport.ValidationState.NOT_EVALUATED, inspection.trustState());
            assertThrows(IllegalArgumentException.class,
                    () -> AsicOperations.createAsicS(content, "../unsafe.txt", keyStoreFile.toFile(), password));

            java.util.Map<String, byte[]> payloads = new java.util.LinkedHashMap<>();
            payloads.put("docs/first.txt", "first document".getBytes(StandardCharsets.UTF_8));
            payloads.put("docs/second.txt", "second document".getBytes(StandardCharsets.UTF_8));
            byte[] asicE = AsicOperations.createAsicE(payloads, keyStoreFile.toFile(), password);
            assertEquals(AsicOperations.ASIC_E_MIME_TYPE, AsicOperations.detectDeclaredMimeType(asicE));
            AsicOperations.AsicEInspection asicEInspection = AsicOperations.inspectAndVerifyE(asicE);
            assertEquals(2, asicEInspection.payloadCount());
            assertTrue(asicEInspection.mimeTypeValid());
            assertTrue(asicEInspection.manifestDigestsValid());
            assertTrue(asicEInspection.signatureReferenceValid());
            assertTrue(asicEInspection.signatureValid());
            assertEquals("CAdES-BES", asicEInspection.cadesProfile());
            assertEquals(CmsInspectionReport.ValidationState.NOT_EVALUATED, asicEInspection.trustState());

            KeyStore localTrustStore = KeyStore.getInstance("PKCS12");
            localTrustStore.load(null, password);
            localTrustStore.setCertificateEntry("asic-trust-anchor", certificate);
            AsicOperations.AsicInspection trustedAsicSInspection = AsicOperations.inspectAndVerify(container, localTrustStore);
            assertEquals(CmsInspectionReport.ValidationState.VALID, trustedAsicSInspection.trustState());
            AsicOperations.AsicEInspection trustedInspection = AsicOperations.inspectAndVerifyE(asicE, localTrustStore);
            assertEquals(CmsInspectionReport.ValidationState.VALID, trustedInspection.trustState());
        } finally {
            java.util.Arrays.fill(password, '\0');
            Files.deleteIfExists(keyStoreFile);
        }
    }
}
