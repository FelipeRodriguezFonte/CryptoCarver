package com.cryptocarver.crypto;

import com.cryptocarver.crypto.CmsInspectionReport.ValidationState;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end PAdES/CMS/chain revocation tests with loopback-only evidence. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PadesLtLtaOfflineTest {
    Path temp;
    LocalPkiFixture pki;

    @BeforeAll void createPki() throws Exception {
        temp = java.nio.file.Files.createTempDirectory("cc-pades-offline-");
        pki = new LocalPkiFixture(temp.resolve("pki"));
    }
    @AfterAll void closeTsa() throws Exception {
        if (pki != null) {
            try { pki.assertNoRevocationDownloads(); }
            finally {
                pki.close();
                try (var paths = java.nio.file.Files.walk(temp)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { java.nio.file.Files.deleteIfExists(path); } catch (Exception ignored) { } });
                }
            }
        }
    }

    @Test void baselineTUsesLoopbackTsaAndDssRecognizesSignatureTimestamp() throws Exception {
        byte[] signed = PadesOperations.signBaselineT(minimalPdf(), pki.pkcs12.toFile(), LocalPkiFixture.PASSWORD, pki.tsaUrl);
        PadesOperations.PadesValidationResult report = PadesOperations.validate(signed, null, null);
        assertTrue(report.dssProfile().toUpperCase().contains("BASELINE-T"), report.summary());
        assertFalse(report.dssProfile().toUpperCase().contains("BASELINE-LT"), report.dssProfile());
        assertTrue(report.xmlDetailedReport().toLowerCase().contains("timestamp"), report.xmlDetailedReport());
    }

    @Test void baselineLTEmbedsCrlAndDssRecognizesLongTermProfile() throws Exception {
        byte[] signed = PadesOperations.signBaselineLT(minimalPdf(), pki.pkcs12.toFile(), LocalPkiFixture.PASSWORD,
                pki.tsaUrl, List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()), false);
        PadesOperations.EmbeddedEvidence evidence = PadesOperations.inspectEmbeddedEvidence(signed);
        assertTrue(evidence.certificateCount() >= 2, evidence.toString());
        assertTrue(evidence.crlCount() > 0, evidence.toString());
        PadesOperations.PadesValidationResult report = PadesOperations.validate(signed, null, null,
                List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()));
        assertTrue(report.dssProfile().toUpperCase().contains("BASELINE-LT"), report.summary());
        assertTrue(report.summary().contains("Effective profile: PAdES Baseline-LT"), report.summary());
        assertThrows(IllegalArgumentException.class, () -> PadesOperations.signBaselineLT(minimalPdf(), pki.pkcs12.toFile(),
                LocalPkiFixture.PASSWORD, pki.tsaUrl, List.of(), false));
    }

    @Test void baselineLTAEmbedsArchiveTimestampAndDssRecognizesProfile() throws Exception {
        byte[] signed = PadesOperations.signBaselineLTA(minimalPdf(), pki.pkcs12.toFile(), LocalPkiFixture.PASSWORD,
                pki.tsaUrl, List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()), false);
        PadesOperations.PadesValidationResult report = PadesOperations.validate(signed, trustStore().toFile(),
                LocalPkiFixture.PASSWORD, List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()));
        assertTrue(report.dssProfile().toUpperCase().contains("BASELINE-LTA"), report.summary());
        assertTrue(report.archiveTimestampCount() > 0, report.summary());
        assertTrue(report.archiveTimestampCryptographicIntegrity(), report.summary());
    }

    @Test void revokedSignerIsRejectedByPadesAndLocalChainAndCmsValidation() throws Exception {
        byte[] signed = PadesOperations.signBaselineLT(minimalPdf(), pki.pkcs12.toFile(), LocalPkiFixture.PASSWORD,
                pki.tsaUrl, List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()), false);
        PadesOperations.PadesValidationResult pdfReport = PadesOperations.validate(signed, null, null,
                List.of(pki.revokedCrlFile.toFile(), pki.rootCrlFile.toFile()));
        assertEquals(com.cryptocarver.service.RevocationValidationService.Status.REVOKED, pdfReport.revocation().status(), pdfReport.summary());

        var chainGood = CertificateGenerator.validateCertificateChain(List.of(pki.signer, pki.intermediate, pki.root), (java.util.Date) null, List.of(pki.goodCrl, pki.rootCrl));
        var chainRevoked = CertificateGenerator.validateCertificateChain(List.of(pki.signer, pki.intermediate, pki.root), (java.util.Date) null, List.of(pki.revokedCrl, pki.rootCrl));
        assertTrue(chainGood.isValid, chainGood.message + chainGood.details);
        assertFalse(chainRevoked.isValid, chainRevoked.message + chainRevoked.details);

        byte[] cms = signedCms();
        var cmsGood = new CmsInspector().inspect(cms, null, trustStoreObject(), false,
                List.of(pki.crlDocument(false), new eu.europa.esig.dss.model.InMemoryDocument(pki.rootCrl.getEncoded(), "root.crl")));
        var cmsRevoked = new CmsInspector().inspect(cms, null, trustStoreObject(), false,
                List.of(pki.crlDocument(true), new eu.europa.esig.dss.model.InMemoryDocument(pki.rootCrl.getEncoded(), "root.crl")));
        assertEquals(com.cryptocarver.service.RevocationValidationService.Status.GOOD, cmsGood.getRevocation().status(), cmsGood.getRevocation().toString());
        assertEquals(com.cryptocarver.service.RevocationValidationService.Status.REVOKED, cmsRevoked.getRevocation().status(), cmsRevoked.getRevocation().toString());
        assertTrue(cmsRevoked.getValidationSteps().stream().anyMatch(step -> step.getStepName().equals("Revocation") && step.getState() == ValidationState.INVALID));
    }

    @Test void tokenConnectionAcceptsPkcs12AndProducesLt() throws Exception {
        try (var token = new eu.europa.esig.dss.token.KeyStoreSignatureTokenConnection(pki.pkcs12.toFile(), "PKCS12",
                new java.security.KeyStore.PasswordProtection(LocalPkiFixture.PASSWORD))) {
            String alias = "signer";
            byte[] signedT = PadesOperations.signWithTokenConnection(minimalPdf(), token, alias, pki.tsaUrl);
            assertTrue(PadesOperations.validate(signedT, null, null).dssProfile().toUpperCase().contains("BASELINE-T"));
            byte[] signed = PadesOperations.signBaselineLTWithTokenConnection(minimalPdf(), token, alias, pki.tsaUrl,
                    List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()), false, null);
            assertTrue(PadesOperations.inspectEmbeddedEvidence(signed).crlCount() > 0);
            assertTrue(PadesOperations.validate(signed, null, null, List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()))
                    .dssProfile().toUpperCase().contains("BASELINE-LT"));
            byte[] signedLta = PadesOperations.signBaselineLTAWithTokenConnection(minimalPdf(), token, alias, pki.tsaUrl,
                    List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()), false, null);
            PadesOperations.PadesValidationResult lta = PadesOperations.validate(signedLta, trustStore().toFile(),
                    LocalPkiFixture.PASSWORD, List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile()));
            assertTrue(lta.dssProfile().toUpperCase().contains("BASELINE-LTA"), lta.summary());
            assertTrue(lta.archiveTimestampCryptographicIntegrity(), lta.summary());
        }
    }

    @Test void padesValidationAcceptsLocalOcspAndReportsRevocation() throws Exception {
        byte[] signed = PadesOperations.signBaselineT(minimalPdf(), pki.pkcs12.toFile(), LocalPkiFixture.PASSWORD, pki.tsaUrl);
        PadesOperations.PadesValidationResult good = PadesOperations.validate(signed, trustStore().toFile(), LocalPkiFixture.PASSWORD,
                List.of(pki.goodOcspFile.toFile(), pki.rootCrlFile.toFile()), false);
        assertEquals(com.cryptocarver.service.RevocationValidationService.Status.GOOD, good.revocation().status(), good.summary());
        PadesOperations.PadesValidationResult revoked = PadesOperations.validate(signed, trustStore().toFile(), LocalPkiFixture.PASSWORD,
                List.of(pki.revokedOcspFile.toFile(), pki.rootCrlFile.toFile()), false);
        assertEquals(com.cryptocarver.service.RevocationValidationService.Status.REVOKED, revoked.revocation().status(), revoked.summary());
    }

    private Path trustStore() throws Exception {
        KeyStore store = trustStoreObject(); Path file = temp.resolve("truststore.p12");
        try (var out = java.nio.file.Files.newOutputStream(file)) { store.store(out, LocalPkiFixture.PASSWORD); }
        return file;
    }
    private KeyStore trustStoreObject() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12"); store.load(null, LocalPkiFixture.PASSWORD);
        store.setCertificateEntry("root", pki.root); return store;
    }
    private byte[] signedCms() throws Exception {
        byte[] content = "local CMS revocation check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pki.signerKeys.getPrivate()),
                        new JcaX509CertificateHolder(pki.signer)));
        generator.addCertificates(new JcaCertStore(List.of(pki.signer, pki.intermediate, pki.root)));
        return generator.generate(new CMSProcessableByteArray(content), true).getEncoded();
    }
    private static byte[] minimalPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); return output.toByteArray();
        }
    }
}
