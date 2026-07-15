package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XMLSignatureOperationsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void signsAndVerifiesBaselineBWithExplicitTrustStore() throws Exception {
        char[] password = "changeit".toCharArray();
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
        config.commonName = "CryptoCarver XML Test";
        X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);

        Path signingStore = tempDirectory.resolve("signing.p12");
        KeyStore signingKeyStore = KeyStore.getInstance("PKCS12");
        signingKeyStore.load(null, password);
        signingKeyStore.setKeyEntry("signer", keyPair.getPrivate(), password, new java.security.cert.Certificate[] { certificate });
        try (OutputStream output = java.nio.file.Files.newOutputStream(signingStore)) {
            signingKeyStore.store(output, password);
        }

        Path trustStore = tempDirectory.resolve("trust.p12");
        KeyStore trustedKeyStore = KeyStore.getInstance("PKCS12");
        trustedKeyStore.load(null, password);
        trustedKeyStore.setCertificateEntry("test-root", certificate);
        try (OutputStream output = java.nio.file.Files.newOutputStream(trustStore)) {
            trustedKeyStore.store(output, password);
        }

        String signed = XMLSignatureOperations.signXAdES("<invoice><amount>100</amount></invoice>", signingStore.toString(), "changeit");
        String report = XMLSignatureOperations.verifyXAdES(signed, trustStore.toString(), "changeit");
        String inspection = XMLSignatureOperations.inspectSignedXml(signed);

        assertTrue(signed.contains("Signature"));
        assertTrue(report.contains("Trust Policy: Configured truststore"));
        assertTrue(inspection.contains("XMLDSig signatures: 1"));
        assertTrue(inspection.contains("Embedded X.509 certificates:"));
        assertTrue(inspection.contains("Transforms:"));
        assertTrue(inspection.contains("Valid at inspection:"));
    }

    @Test
    void inspectorMakesUnsignedXmlExplicit() throws Exception {
        String report = XMLSignatureOperations.inspectSignedXml("<document><value>test</value></document>");

        assertTrue(report.contains("XMLDSig signatures: 0"));
        assertTrue(report.contains("not an XMLDSig/XAdES"));
    }
}
