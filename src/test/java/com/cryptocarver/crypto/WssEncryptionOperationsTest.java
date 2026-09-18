package com.cryptocarver.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class WssEncryptionOperationsTest {

    private static final char[] PASSWORD = "storepass".toCharArray();
    private static KeyStore keyStore;
    private static X509Certificate certificate;
    private static String soap;

    @BeforeAll
    static void loadFixtures() throws Exception {
        keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream input = new FileInputStream("src/test/resources/testks.p12")) {
            keyStore.load(input, PASSWORD);
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                certificate = (X509Certificate) keyStore.getCertificate(alias);
                break;
            }
        }
        assertNotNull(certificate);
        soap = Files.readString(Paths.get("src/test/resources/soap_test.xml"));
    }

    @Test
    void allDataAlgorithmsRoundTripWithRsaOaepSha256() {
        for (WssEncryptionOperations.DataEncryptionAlgorithm algorithm
                : WssEncryptionOperations.DataEncryptionAlgorithm.values()) {
            WssEncryptionOperations.OperationResult encrypted = WssEncryptionOperations.encryptSoapBody(
                    soap, certificate, algorithm,
                    WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA256);
            assertEquals(WssEncryptionOperations.OperationResult.Status.SUCCESS,
                    encrypted.status(), encrypted.message());
            assertTrue(encrypted.xml().contains("EncryptedData"));
            assertFalse(encrypted.xml().contains("SensitivePayload"));

            WssEncryptionOperations.OperationResult decrypted =
                    WssEncryptionOperations.decryptSoapBody(encrypted.xml(), keyStore, PASSWORD);
            assertEquals(WssEncryptionOperations.OperationResult.Status.SUCCESS,
                    decrypted.status(), algorithm + ": " + decrypted.message());
            assertTrue(decrypted.xml().contains("SensitivePayload"));
        }
    }

    @Test
    void legacyRsaOaepProfileRoundTrip() {
        WssEncryptionOperations.OperationResult encrypted = WssEncryptionOperations.encryptSoapBody(
                soap, certificate, WssEncryptionOperations.DataEncryptionAlgorithm.AES_256_GCM,
                WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA1);
        assertEquals(WssEncryptionOperations.OperationResult.Status.SUCCESS, encrypted.status(), encrypted.message());
        assertEquals(WssEncryptionOperations.OperationResult.Status.SUCCESS,
                WssEncryptionOperations.decryptSoapBody(encrypted.xml(), keyStore, PASSWORD).status());
    }

    @Test
    void soap12RoundTrip() {
        String soap12 = soap.replace("http://schemas.xmlsoap.org/soap/envelope/",
                "http://www.w3.org/2003/05/soap-envelope");
        WssEncryptionOperations.OperationResult encrypted = WssEncryptionOperations.encryptSoapBody(
                soap12, certificate, WssEncryptionOperations.DataEncryptionAlgorithm.AES_128_GCM,
                WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA256);
        WssEncryptionOperations.OperationResult decrypted =
                WssEncryptionOperations.decryptSoapBody(encrypted.xml(), keyStore, PASSWORD);

        assertEquals(WssEncryptionOperations.OperationResult.Status.SUCCESS, decrypted.status(), decrypted.message());
        assertTrue(decrypted.technicalDetails().contains("SOAP version: 1.2"));
    }

    @Test
    void wrongKeyStoreAndTamperedCiphertextAreRejected() throws Exception {
        WssEncryptionOperations.OperationResult encrypted = WssEncryptionOperations.encryptSoapBody(
                soap, certificate, WssEncryptionOperations.DataEncryptionAlgorithm.AES_256_GCM,
                WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA256);
        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, PASSWORD);
        assertEquals(WssEncryptionOperations.OperationResult.Status.ERROR,
                WssEncryptionOperations.decryptSoapBody(encrypted.xml(), empty, PASSWORD).status());

        assertEquals(WssEncryptionOperations.OperationResult.Status.ERROR,
                WssEncryptionOperations.decryptSoapBody(tamperFirstCipherValue(encrypted.xml()), keyStore, PASSWORD)
                        .status());
    }

    /**
     * Corrupts the first base64 character of the first CipherValue.
     *
     * <p>Overwriting it with a fixed letter is not a corruption when the ciphertext already
     * starts with that letter, and then the "tampered" document decrypts cleanly: a one-in-64
     * failure per run, on random ciphertext. The replacement is chosen against the original
     * instead, so the document always differs.
     */
    private static String tamperFirstCipherValue(String xml) {
        Matcher cipherValue = Pattern.compile("(?:<xenc:CipherValue>)([A-Za-z0-9+/])").matcher(xml);
        assertTrue(cipherValue.find(), "Encrypted SOAP must carry a CipherValue to tamper with");
        char original = cipherValue.group(1).charAt(0);
        char corrupted = original == 'A' ? 'B' : 'A';
        return new StringBuilder(xml)
                .replace(cipherValue.start(1), cipherValue.end(1), String.valueOf(corrupted))
                .toString();
    }

    @Test
    void invalidInputsFailWithoutThrowingSecrets() {
        WssEncryptionOperations.OperationResult result = WssEncryptionOperations.encryptSoapBody(
                soap, null, WssEncryptionOperations.DataEncryptionAlgorithm.AES_256_GCM,
                WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA256);
        assertEquals(WssEncryptionOperations.OperationResult.Status.ERROR, result.status());
        assertFalse(result.message().contains(new String(PASSWORD)));
    }

    @Test
    void undeclaredAlgorithmsAreRejectedBeforeDecryption() {
        WssEncryptionOperations.OperationResult encrypted = WssEncryptionOperations.encryptSoapBody(
                soap, certificate, WssEncryptionOperations.DataEncryptionAlgorithm.AES_256_GCM,
                WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA256);
        String rsa15 = encrypted.xml().replace(
                "http://www.w3.org/2009/xmlenc11#rsa-oaep",
                "http://www.w3.org/2001/04/xmlenc#rsa-1_5");
        WssEncryptionOperations.OperationResult result =
                WssEncryptionOperations.decryptSoapBody(rsa15, keyStore, PASSWORD);

        assertEquals(WssEncryptionOperations.OperationResult.Status.ERROR, result.status());
        assertTrue(result.message().contains("Unsupported WSS key transport algorithm"));
    }

    @Test
    void encryptedDataOutsideDirectSoapBodyIsRejected() {
        WssEncryptionOperations.OperationResult encrypted = WssEncryptionOperations.encryptSoapBody(
                soap, certificate, WssEncryptionOperations.DataEncryptionAlgorithm.AES_256_GCM,
                WssEncryptionOperations.KeyTransportAlgorithm.RSA_OAEP_SHA256);
        String nested = encrypted.xml()
                .replaceFirst("(<[^>]*:Body[^>]*>)(\\s*<xenc:EncryptedData)",
                        "$1<evil:Wrapper xmlns:evil=\"urn:evil\">$2")
                .replaceFirst("(</xenc:EncryptedData>)(\\s*</[^>]*:Body>)", "$1</evil:Wrapper>$2");
        assertNotEquals(encrypted.xml(), nested);

        WssEncryptionOperations.OperationResult result =
                WssEncryptionOperations.decryptSoapBody(nested, keyStore, PASSWORD);

        assertEquals(WssEncryptionOperations.OperationResult.Status.ERROR, result.status());
        assertTrue(result.message().contains("direct child of the SOAP Body"), result.message());
    }
}
