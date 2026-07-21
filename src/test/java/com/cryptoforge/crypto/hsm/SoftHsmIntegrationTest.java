package com.cryptoforge.crypto.hsm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.security.Provider;
import java.security.Security;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SoftHsmIntegrationTest {

    private static Pkcs11Session session;
    private static String testAlias;

    @BeforeAll
    public static void setUp() throws Exception {
        String libraryPath = System.getenv("SOFTHSM2_MODULE");
        String confPath = System.getenv("SOFTHSM2_CONF");
        String pinStr = System.getenv("CRYPTOCARVER_SOFTHSM_PIN");
        String slotStr = System.getenv("CRYPTOCARVER_SOFTHSM_SLOT_INDEX");
        String aliasStr = System.getenv("CRYPTOCARVER_SOFTHSM_ALIAS");

        Assumptions.assumeTrue(libraryPath != null && !libraryPath.isBlank(),
                "SOFTHSM2_MODULE is not set, skipping integration test");
        Assumptions.assumeTrue(confPath != null && !confPath.isBlank(),
                "SOFTHSM2_CONF is not set, skipping integration test");
        Assumptions.assumeTrue(pinStr != null && !pinStr.isBlank(),
                "CRYPTOCARVER_SOFTHSM_PIN is not set, skipping integration test");
        Assumptions.assumeTrue(slotStr != null && !slotStr.isBlank(),
                "CRYPTOCARVER_SOFTHSM_SLOT_INDEX is not set, skipping integration test");
        Assumptions.assumeTrue(aliasStr != null && !aliasStr.isBlank(),
                "CRYPTOCARVER_SOFTHSM_ALIAS is not set, skipping integration test");

        System.setProperty("SOFTHSM2_CONF", confPath);

        int slot = Integer.parseInt(slotStr);

        Pkcs11Configuration config = new Pkcs11Configuration("SoftHSM", java.nio.file.Path.of(libraryPath), slot);
        session = Pkcs11SessionManager.getInstance().connect(config, pinStr.toCharArray());

        List<String> aliases = session.listPrivateKeysWithCertificate();
        Assumptions.assumeTrue(aliases.contains(aliasStr), "Configured alias does not exist or has no private key with certificate");
        testAlias = aliasStr;
    }

    @AfterAll
    public static void tearDown() {
        if (session != null) {
            Pkcs11SessionManager.getInstance().disconnect();
        }
    }

    @Test
    public void testJcaMechanisms() {
        Set<String> sigs = session.getSupportedMechanisms("Signature");
        assertNotNull(sigs);
        assertFalse(sigs.isEmpty(), "Provider should expose signature algorithms");
        assertTrue(sigs.contains("SHA256withRSA"), "Should support SHA256withRSA");
    }

    @Test
    public void testSymmetricCrypto() throws Exception {
        String aesAlias = System.getenv("CRYPTOCARVER_SOFTHSM_AES_ALIAS");
        if (aesAlias == null || aesAlias.isBlank()) {
            List<Pkcs11ObjectInfo> secretKeys = session.listObjects().stream()
                    .filter(obj -> "Secret key".equals(obj.objectType()) && obj.algorithm() != null && obj.algorithm().contains("AES"))
                    .toList();
            Assumptions.assumeFalse(secretKeys.isEmpty(), "Skipping symmetric test: No secret key found in token");
            aesAlias = secretKeys.get(0).alias();
        }

        Set<String> ciphers = session.getSupportedMechanisms("Cipher");
        Assumptions.assumeTrue(ciphers.contains("AES/CBC/PKCS5Padding"), "Skipping symmetric test: Token doesn't expose AES/CBC/PKCS5Padding");

        byte[] plaintext = "Secret Message".getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[16];

        byte[] ciphertext = session.encrypt(aesAlias, plaintext, "AES/CBC/PKCS5Padding", iv);
        assertNotNull(ciphertext);
        assertNotEquals(0, ciphertext.length);

        byte[] decrypted = session.decrypt(aesAlias, ciphertext, "AES/CBC/PKCS5Padding", iv);
        assertArrayEquals(plaintext, decrypted, "Decrypted message should match plaintext");
    }

    @Test
    public void testRsaSignature() throws Exception {
        byte[] data = "Test data for RSA signature".getBytes(StandardCharsets.UTF_8);
        byte[] signature = session.sign(testAlias, data, "SHA256withRSA");
        assertNotNull(signature);
        assertNotEquals(0, signature.length);
    }

    @Test
    public void testXAdESPkcs11Signature() throws Exception {
        String xmlContent = "<test><data>Hello XAdES PKCS#11</data></test>";

        String signedXml = com.cryptoforge.crypto.XMLSignatureOperations.signXAdESWithPkcs11(
                xmlContent, testAlias, "XAdES-BASELINE-B", null, "ENVELOPED", null);

        assertNotNull(signedXml);
        assertTrue(signedXml.contains("<ds:Signature"));

        // Positive verification
        com.cryptoforge.crypto.XMLSignatureOperations.VerificationResult result =
                com.cryptoforge.crypto.XMLSignatureOperations.verifyXAdES(signedXml);

        String summary = result.summary();
        assertTrue(summary.contains("Indication: TOTAL_PASSED") || summary.contains("Indication: INDETERMINATE"));

        // Negative verification
        String alteredXml = signedXml.replace("Hello XAdES", "Hacked XAdES");
        com.cryptoforge.crypto.XMLSignatureOperations.VerificationResult failResult =
                com.cryptoforge.crypto.XMLSignatureOperations.verifyXAdES(alteredXml);
        assertTrue(failResult.summary().contains("TOTAL_FAILED") || failResult.summary().contains("FORMAT_FAILURE"));
    }

    @Test
    public void testCmsSignature() throws Exception {
        byte[] data = "Test data for CMS PKCS#11 signature".getBytes(StandardCharsets.UTF_8);
        
        byte[] cmsSignature = session.signCms(testAlias, data, false);
        
        assertNotNull(cmsSignature);
        assertTrue(cmsSignature.length > 0);
        
        // Verify with BouncyCastle
        java.security.cert.Certificate[] certChain = session.getCertificateChain(testAlias);
        com.cryptoforge.crypto.CMSOperations.VerificationResult result = com.cryptoforge.crypto.CMSOperations.verifySignedData(cmsSignature, (java.security.cert.X509Certificate) certChain[0]);
        assertTrue(result.verified, "CMS Signature should be verified successfully by Bouncy Castle");
        assertArrayEquals(data, result.content, "Original data should match recovered data");
    }

    @Test
    public void testCmsEncryption() throws Exception {
        byte[] data = "Test data for CMS PKCS#11 encryption".getBytes(StandardCharsets.UTF_8);

        java.security.cert.Certificate[] certChain = session.getCertificateChain(testAlias);
        assertNotNull(certChain);
        assertTrue(certChain.length > 0);

        // Encrypt with the public certificate from the token
        byte[] encryptedData = com.cryptoforge.crypto.CMSOperations.generateEnvelopedData(data, (java.security.cert.X509Certificate) certChain[0]);
        assertNotNull(encryptedData);
        assertTrue(encryptedData.length > 0);

        // Decrypt using the private key inside the token
        byte[] decryptedData = session.decryptCms(testAlias, encryptedData);

        assertNotNull(decryptedData);
        assertArrayEquals(data, decryptedData, "Decrypted data must match original data");
    }

    @Test
    public void testPadesSignature() throws Exception {
        byte[] unsignedPdf = minimalPdf();
        byte[] signedPdf = session.signPades(testAlias, unsignedPdf, null);

        assertTrue(signedPdf.length > unsignedPdf.length);
        com.cryptoforge.crypto.PadesOperations.PdfSignatureInspection inspection =
                com.cryptoforge.crypto.PadesOperations.inspectSignatures(signedPdf);
        assertTrue(inspection.signatureCount() >= 1);
        assertTrue(inspection.signatures().get(0).contains("ByteRangeCoversDocument=true"));
    }

    @Test
    public void testAsicSSignature() throws Exception {
        byte[] payload = "ASiC-S payload signed by PKCS#11".getBytes(StandardCharsets.UTF_8);
        byte[] container = session.createAsicS(testAlias, payload, "token-payload.txt");
        com.cryptoforge.crypto.AsicOperations.AsicInspection inspection =
                com.cryptoforge.crypto.AsicOperations.inspectAndVerify(container);
        assertEquals("token-payload.txt", inspection.payloadName());
        assertTrue(inspection.signatureValid());
        assertTrue(inspection.certificateBindingValid());
    }

    @Test
    public void testAsicESignature() throws Exception {
        java.util.Map<String, byte[]> payloads = new java.util.LinkedHashMap<>();
        payloads.put("first.txt", "first ASiC-E token payload".getBytes(StandardCharsets.UTF_8));
        payloads.put("second.txt", "second ASiC-E token payload".getBytes(StandardCharsets.UTF_8));
        byte[] container = session.createAsicE(testAlias, payloads);
        com.cryptoforge.crypto.AsicOperations.AsicEInspection inspection =
                com.cryptoforge.crypto.AsicOperations.inspectAndVerifyE(container);
        assertEquals(2, inspection.payloadCount());
        assertTrue(inspection.manifestDigestsValid());
        assertTrue(inspection.signatureValid());
    }

    private static byte[] minimalPdf() throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
