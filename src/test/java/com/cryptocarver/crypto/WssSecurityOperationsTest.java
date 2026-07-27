package com.cryptocarver.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

public class WssSecurityOperationsTest {

    private static KeyStore testKs;
    private static String testAlias;
    private static final char[] TEST_PASS = "storepass".toCharArray();
    private static X509Certificate testCert;
    private static KeyStore ecKeyStore;
    private static X509Certificate ecCertificate;
    private static String validXml;

    @BeforeAll
    public static void setup() throws Exception {
        testKs = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream("src/test/resources/testks.p12")) {
            testKs.load(fis, TEST_PASS);
        }
        Enumeration<String> aliases = testKs.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (testKs.isKeyEntry(alias)) {
                testAlias = alias;
                break;
            }
        }
        assertNotNull(testAlias, "Test keystore must contain a key entry.");
        testCert = (X509Certificate) testKs.getCertificate(testAlias);

        KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC");
        ecGenerator.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair ecKeyPair = ecGenerator.generateKeyPair();
        CertificateGenerator.CertificateConfig ecConfig = new CertificateGenerator.CertificateConfig();
        ecConfig.commonName = "WSS ECDSA Test";
        ecConfig.signatureAlgorithm = "SHA384withECDSA";
        ecCertificate = CertificateGenerator.generateSelfSignedCertificate(ecKeyPair, ecConfig);
        ecKeyStore = KeyStore.getInstance("PKCS12");
        ecKeyStore.load(null, TEST_PASS);
        ecKeyStore.setKeyEntry("ec-wss", ecKeyPair.getPrivate(), TEST_PASS,
                new java.security.cert.Certificate[]{ecCertificate});

        validXml = Files.readString(Paths.get("src/test/resources/soap_test.xml"));
    }

    @Test
    public void testSignAndVerifyRoundTrip() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS);

        // Assert structure
        assertTrue(signed.contains("wsse:Security"), "Result should contain wsse:Security");
        assertTrue(signed.contains("wsse:BinarySecurityToken"), "Result should contain the X.509 token");
        assertTrue(signed.contains("wsse:SecurityTokenReference"), "KeyInfo should reference the token");
        assertTrue(signed.contains("Signature"), "Result should contain Signature");
        assertTrue(signed.contains("wsu:Id"), "Result should add wsu:Id to Body");

        WssSecurityOperations.WssVerificationResult result = WssSecurityOperations.verifySoapSignature(signed, testCert);
        assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, result.getStatus(), "Verification should be VALID");

        WssSecurityOperations.WssVerificationResult embeddedResult =
                WssSecurityOperations.verifySoapSignature(signed, null);
        assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, embeddedResult.getStatus(),
                "Verification should resolve the certificate through SecurityTokenReference");
        assertTrue(embeddedResult.getTechnicalDetails().contains("Certificate source: wsse:BinarySecurityToken"));
        assertTrue(embeddedResult.getTechnicalDetails().contains("Trust validation: not performed"));
    }

    @Test
    public void testSoap12RoundTripWithEmbeddedCertificate() throws Exception {
        String soap12 = validXml.replace(
                "http://schemas.xmlsoap.org/soap/envelope/",
                "http://www.w3.org/2003/05/soap-envelope");

        String signed = WssSecurityOperations.signSoapBody(soap12, testKs, testAlias, TEST_PASS);
        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(signed, null);

        assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, result.getStatus());
        assertTrue(result.getTechnicalDetails().contains("SOAP version: 1.2"));
        assertTrue(signed.contains("http://www.w3.org/2003/05/soap-envelope"));
    }

    @Test
    public void testSignedTimestampRoundTrip() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA256,
                WssSecurityOperations.WssTimestampOptions.signed(5));

        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(signed, null);

        assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, result.getStatus());
        assertTrue(signed.contains("wsu:Timestamp"));
        assertTrue(signed.contains("wsu:Created"));
        assertTrue(signed.contains("wsu:Expires"));
        assertTrue(result.getTechnicalDetails().contains("Timestamp signed: yes"));
    }

    @Test
    public void testUnsignedTimestampIsReportedExplicitly() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA256,
                new WssSecurityOperations.WssTimestampOptions(true, 5, false));

        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(signed, null);

        assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, result.getStatus());
        assertTrue(result.getTechnicalDetails().contains("Timestamp signed: no"));
    }

    @Test
    public void testExpiredTimestampIsRejected() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA256,
                new WssSecurityOperations.WssTimestampOptions(true, 5, false));
        String expired = signed
                .replaceFirst("(<wsu:Created>)[^<]+", "$1" + Instant.now().minusSeconds(600))
                .replaceFirst("(<wsu:Expires>)[^<]+", "$1" + Instant.now().minusSeconds(300));

        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(expired, null);

        assertEquals(WssSecurityOperations.WssVerificationResult.Status.INVALID, result.getStatus());
        assertTrue(result.getMessage().contains("expired"));
    }

    @Test
    public void testTamperedSignedTimestampInvalidatesSignature() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA256,
                WssSecurityOperations.WssTimestampOptions.signed(5));
        String tampered = signed.replaceFirst("(<wsu:Created>)[^<]+",
                "$1" + Instant.now().minusSeconds(30));

        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(tampered, null);

        assertEquals(WssSecurityOperations.WssVerificationResult.Status.INVALID, result.getStatus());
    }

    @Test
    public void testTimestampValidityRangeIsValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new WssSecurityOperations.WssTimestampOptions(true, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new WssSecurityOperations.WssTimestampOptions(true, 1441, true));
    }

    @Test
    public void testRejectsUnsupportedSoapNamespace() {
        String notSoap = validXml.replace(
                "http://schemas.xmlsoap.org/soap/envelope/", "urn:not-soap");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WssSecurityOperations.signSoapBody(notSoap, testKs, testAlias, TEST_PASS));
        assertTrue(error.getMessage().contains("Unsupported SOAP Envelope namespace"));
    }

    @Test
    public void testRejectsBrokenSecurityTokenReference() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS);
        String corrupted = signed.replaceFirst("URI=\"#X509-[^\"]+\"", "URI=\"#missing-token\"");

        WssSecurityOperations.WssVerificationResult result =
                WssSecurityOperations.verifySoapSignature(corrupted, null);

        assertEquals(WssSecurityOperations.WssVerificationResult.Status.ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("Referenced BinarySecurityToken was not found"));
    }

    @Test
    public void testSupportsAllRsaSha2Algorithms() throws Exception {
        for (WssSecurityOperations.WssSignatureAlgorithm algorithm : new WssSecurityOperations.WssSignatureAlgorithm[]{
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA256,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA384,
                WssSecurityOperations.WssSignatureAlgorithm.RSA_SHA512}) {
            String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS, algorithm);
            WssSecurityOperations.WssVerificationResult result = WssSecurityOperations.verifySoapSignature(signed, testCert);
            assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, result.getStatus(), algorithm.displayName());
            assertTrue(result.getTechnicalDetails().contains("Algorithm: " + algorithm.displayName()));
        }
    }

    @Test
    public void testSupportsAllEcdsaSha2Algorithms() throws Exception {
        for (WssSecurityOperations.WssSignatureAlgorithm algorithm : new WssSecurityOperations.WssSignatureAlgorithm[]{
                WssSecurityOperations.WssSignatureAlgorithm.ECDSA_SHA256,
                WssSecurityOperations.WssSignatureAlgorithm.ECDSA_SHA384,
                WssSecurityOperations.WssSignatureAlgorithm.ECDSA_SHA512}) {
            String signed = WssSecurityOperations.signSoapBody(validXml, ecKeyStore, "ec-wss", TEST_PASS, algorithm);
            WssSecurityOperations.WssVerificationResult result = WssSecurityOperations.verifySoapSignature(signed, ecCertificate);
            assertEquals(WssSecurityOperations.WssVerificationResult.Status.VALID, result.getStatus(), algorithm.displayName());
            assertTrue(result.getTechnicalDetails().contains("Algorithm: " + algorithm.displayName()));
        }
    }

    @Test
    public void testRejectsAlgorithmAndKeyTypeMismatch() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS,
                        WssSecurityOperations.WssSignatureAlgorithm.ECDSA_SHA256));
        assertTrue(error.getMessage().contains("requires a EC private key"));
    }

    @Test
    public void testVerificationFailsOnTamperedBody() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS);

        // Modify the payload inside the Body
        String tampered = signed.replace("SensitivePayload", "HackedPayload");

        WssSecurityOperations.WssVerificationResult result = WssSecurityOperations.verifySoapSignature(tampered, testCert);
        assertEquals(WssSecurityOperations.WssVerificationResult.Status.INVALID, result.getStatus(), "Verification should be INVALID on tampered body");
    }

    @Test
    public void testRejectsXXEInParsing() throws Exception {
        String xxePayload = "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE soapenv:Envelope [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <Data>&xxe;</Data>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        // Expect parsing exception due to DTD block
        Exception exception = assertThrows(Exception.class, () -> {
            WssSecurityOperations.signSoapBody(xxePayload, testKs, testAlias, TEST_PASS);
        });
        assertTrue(exception.getMessage().contains("DOCTYPE is disallowed") || exception.getMessage().contains("disallow-doctype-decl"));
    }

    @Test
    public void testVerificationFailsOnMissingBodyId() throws Exception {
        // Sign the valid XML
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS);

        // Corrupt it by removing the wsu:Id attribute (a naive replace for testing logic)
        String corrupted = signed.replaceAll("wsu:Id=\"[^\"]+\"", "");

        WssSecurityOperations.WssVerificationResult result = WssSecurityOperations.verifySoapSignature(corrupted, testCert);
        assertEquals(WssSecurityOperations.WssVerificationResult.Status.ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("does not have a wsu:Id"));
    }

    @Test
    public void testRejectsRSASHA1() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS);
        String corrupted = signed.replace("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", "http://www.w3.org/2000/09/xmldsig#rsa-sha1");

        WssSecurityOperations.WssVerificationResult result = WssSecurityOperations.verifySoapSignature(corrupted, testCert);
        assertEquals(WssSecurityOperations.WssVerificationResult.Status.ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("rsa-sha1") || result.getMessage().contains("RSA-SHA1"));
    }

    @Test
    public void testRejectsExternalReferences() throws Exception {
        String signed = WssSecurityOperations.signSoapBody(validXml, testKs, testAlias, TEST_PASS);
        String corrupted = signed.replace("URI=\"#Body-", "URI=\"http://external.site/malicious.xml");

        WssSecurityOperations.WssVerificationResult result = WssSecurityOperations.verifySoapSignature(corrupted, testCert);
        assertEquals(WssSecurityOperations.WssVerificationResult.Status.ERROR, result.getStatus());
        assertTrue(result.getMessage().contains("Signature reference does not match the SOAP Body wsu:Id"));
    }
}
