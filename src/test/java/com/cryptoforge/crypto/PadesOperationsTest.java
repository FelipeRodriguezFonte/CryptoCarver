package com.cryptoforge.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

class PadesOperationsTest {

    @Test
    void rejectsNonHttpTsaUrlBeforeOpeningSigningMaterial() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PadesOperations.signBaselineT(new byte[] { 1 }, null, new char[0], "ftp://tsa.example.test"));
        assertTrue(error.getMessage().contains("TSA URL"));
    }

    @Test
    void signsPdfIncrementallyWithPadesBaselineB() throws Exception {
        char[] password = "pades-laboratory-password".toCharArray();
        Path keyStoreFile = Files.createTempFile("cryptocarver-pades-", ".p12");
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            CertificateGenerator.CertificateConfig config = new CertificateGenerator.CertificateConfig();
            config.commonName = "PAdES laboratory signer";
            X509Certificate certificate = CertificateGenerator.generateSelfSignedCertificate(keyPair, config);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            keyStore.setKeyEntry("pades", keyPair.getPrivate(), password, new java.security.cert.Certificate[] { certificate });
            try (var output = Files.newOutputStream(keyStoreFile)) {
                keyStore.store(output, password);
            }

            byte[] unsigned = minimalPdf();
            byte[] signed = PadesOperations.signBaselineB(unsigned, keyStoreFile.toFile(), password);

            assertTrue(signed.length > unsigned.length);
            try (PDDocument document = Loader.loadPDF(signed)) {
                assertFalse(document.getSignatureDictionaries().isEmpty(), "Signed PDF must contain a signature dictionary");
            }
            PadesOperations.PdfSignatureInspection inspection = PadesOperations.inspectSignatures(signed);
            assertTrue(inspection.signatureCount() >= 1);
            assertTrue(inspection.signatures().get(0).contains("ByteRange=true"));
            assertTrue(inspection.signatures().get(0).contains("ByteRangeCoversDocument=true"));
            assertTrue(inspection.signatures().get(0).contains("Contents=true"));

            PadesOperations.PadesValidationResult validation = PadesOperations.validate(signed, null, null);
            assertFalse(validation.trustConfigured());
            assertTrue(validation.localCrlCount() == 0);
            assertTrue(validation.summary().contains("No truststore"));
            assertTrue(validation.summary().contains("Revocation: NOT EVALUATED"));
            assertFalse(validation.xmlSimpleReport().isBlank());
            assertFalse(validation.xmlDetailedReport().isBlank());
            assertFalse(validation.xmlEtsiReport().isBlank());

            assertThrows(Exception.class,
                    () -> PadesOperations.validate(signed, null, null, java.util.List.of(keyStoreFile.toFile())));

            byte[] visiblySigned = PadesOperations.signBaselineB(unsigned, keyStoreFile.toFile(), password,
                    new PadesOperations.VisibleSignatureOptions(1, 40, 40, 220, 60, "CryptoCarver laboratory signature"));
            assertTrue(PadesOperations.inspectSignatures(visiblySigned).signatureCount() >= 1);
        } finally {
            java.util.Arrays.fill(password, '\0');
            Files.deleteIfExists(keyStoreFile);
        }
    }

    private static byte[] minimalPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
