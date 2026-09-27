package com.cryptocarver.crypto;

import eu.europa.esig.dss.token.KeyStoreSignatureTokenConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** ASiC signatures and verification use ephemeral local PKI and loopback TSA only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsicLtvOfflineTest {
    private Path directory;
    private LocalPkiFixture pki;
    private Path trustStore;
    private static final byte[] PAYLOAD = "ASiC offline payload".getBytes(StandardCharsets.UTF_8);

    @BeforeAll void setup() throws Exception {
        directory = Files.createTempDirectory("cc-asic-ltv-");
        pki = new LocalPkiFixture(directory.resolve("pki"));
        trustStore = directory.resolve("trust.p12");
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, LocalPkiFixture.PASSWORD);
        trust.setCertificateEntry("root", pki.root);
        try (var out = Files.newOutputStream(trustStore)) { trust.store(out, LocalPkiFixture.PASSWORD); }
    }

    @AfterAll void cleanup() throws Exception {
        if (pki != null) {
            try { pki.assertNoRevocationDownloads(); }
            finally { pki.close(); }
        }
        if (directory != null) try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }

    @TestFactory Stream<DynamicTest> everyContainerFormatAndLevelReachesDssProfile() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String container : List.of("S", "E"))
            for (String format : List.of("CAdES", "XAdES"))
                for (String level : List.of("B", "T", "LT", "LTA"))
                    tests.add(DynamicTest.dynamicTest(container + " " + format + " " + level, () -> {
                        List<java.io.File> evidence = evidence(level);
                        byte[] signed = sign(container, format, level, evidence, false);
                        String extension = container.equals("S") ? "signed.asics" : "signed.asice";
                        var result = AdesValidationOperations.validate(signed, extension, trustStore.toFile(),
                                LocalPkiFixture.PASSWORD, goodEvidence(), false);
                        assertEquals(1, result.signatures().size(), result.simpleReportXml());
                        var outcome = result.signatures().get(0);
                        assertEquals(format + "-BASELINE-" + level, outcome.level(), result.simpleReportXml());
                        assertTrue(outcome.passed(), result.simpleReportXml());
                        pki.assertNoRevocationDownloads();
                    }));
        return tests.stream();
    }

    @Test void ltAndLtaRequireExplicitRevocationSource() {
        for (String container : List.of("S", "E"))
            for (String format : List.of("CAdES", "XAdES"))
                for (String level : List.of("LT", "LTA")) {
                    assertThrows(IllegalArgumentException.class,
                            () -> sign(container, format, level, List.of(), false));
                }
        pki.assertNoRevocationDownloads();
    }

    @Test void revokedSignerIsReported() throws Exception {
        for (String container : List.of("S", "E")) for (String format : List.of("CAdES", "XAdES")) {
            byte[] signed = sign(container, format, "B", List.of(), false);
            var result = AdesValidationOperations.validate(signed,
                    container.equals("S") ? "revoked.asics" : "revoked.asice", trustStore.toFile(),
                    LocalPkiFixture.PASSWORD, List.of(pki.revokedCrlFile.toFile(), pki.rootCrlFile.toFile()), false);
            assertFalse(result.signatures().get(0).passed(), result.simpleReportXml());
            assertNotNull(result.signatures().get(0).subIndication(), result.simpleReportXml());
            assertTrue(result.signatures().get(0).subIndication().name().contains("REVOKED"), result.simpleReportXml());
        }
        pki.assertNoRevocationDownloads();
    }

    @Test void inspectorsRecognizeDssProducedXadesAndCades() throws Exception {
        for (String format : List.of("CAdES", "XAdES")) {
            byte[] s = sign("S", format, "B", List.of(), false);
            assertTrue(AsicOperations.inspectAndVerify(s).signatureValid());
            byte[] e = sign("E", format, "B", List.of(), false);
            assertTrue(AsicOperations.inspectAndVerifyE(e).signatureValid());
        }
        pki.assertNoRevocationDownloads();
    }

    @Test void dssInspectorRejectsChangedPayload() throws Exception {
        for (String container : List.of("S", "E")) for (String format : List.of("CAdES", "XAdES")) {
            byte[] changed = replacePayload(sign(container, format, "B", List.of(), false));
            assertThrows(IllegalArgumentException.class, () -> {
                if (container.equals("S")) AsicOperations.inspectAndVerify(changed);
                else AsicOperations.inspectAndVerifyE(changed);
            });
        }
        pki.assertNoRevocationDownloads();
    }

    private byte[] replacePayload(byte[] archive) throws Exception {
        try (var input = new ZipInputStream(new java.io.ByteArrayInputStream(archive));
             var bytes = new java.io.ByteArrayOutputStream();
             var output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] data = input.readAllBytes();
                if ("payload.txt".equals(entry.getName())) data = "altered payload".getBytes(StandardCharsets.UTF_8);
                ZipEntry copy = new ZipEntry(entry.getName());
                if (entry.getMethod() == ZipEntry.STORED) {
                    copy.setMethod(ZipEntry.STORED);
                    copy.setSize(data.length);
                    CRC32 crc = new CRC32(); crc.update(data); copy.setCrc(crc.getValue());
                }
                output.putNextEntry(copy); output.write(data); output.closeEntry();
            }
            output.finish();
            return bytes.toByteArray();
        }
    }

    @TestFactory Stream<DynamicTest> tokenConnectionVariants() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String container : List.of("S", "E"))
            for (String format : List.of("CAdES", "XAdES"))
                for (String level : List.of("B", "T", "LT", "LTA"))
                    tests.add(DynamicTest.dynamicTest("token " + container + " " + format + " " + level, () -> {
                        List<java.io.File> evidence = evidence(level);
                        byte[] signed;
                        try (var token = new KeyStoreSignatureTokenConnection(pki.pkcs12.toFile(), "PKCS12",
                                new KeyStore.PasswordProtection(LocalPkiFixture.PASSWORD))) {
                            signed = container.equals("S")
                                    ? AsicOperations.signAsicS(PAYLOAD, "payload.txt", token, "signer",
                                            format, level, pki.tsaUrl, evidence, false)
                                    : AsicOperations.signAsicE(payloads(), token, "signer",
                                            format, level, pki.tsaUrl, evidence, false);
                        }
                        var result = AdesValidationOperations.validate(signed,
                                container.equals("S") ? "signed.asics" : "signed.asice", trustStore.toFile(),
                                LocalPkiFixture.PASSWORD, goodEvidence(), false);
                        assertEquals(1, result.signatures().size(), result.simpleReportXml());
                        assertEquals(format + "-BASELINE-" + level, result.signatures().get(0).level(), result.simpleReportXml());
                        assertTrue(result.signatures().get(0).passed(), result.simpleReportXml());
                        pki.assertNoRevocationDownloads();
                    }));
        return tests.stream();
    }

    private List<java.io.File> evidence(String level) {
        return level.equals("LT") || level.equals("LTA")
                ? goodEvidence() : List.of();
    }

    private List<java.io.File> goodEvidence() {
        return List.of(pki.goodCrlFile.toFile(), pki.rootCrlFile.toFile());
    }

    private byte[] sign(String container, String format, String level,
                        List<java.io.File> evidence, boolean online) throws Exception {
        return container.equals("S")
                ? AsicOperations.signAsicS(PAYLOAD, "payload.txt", pki.pkcs12.toFile(), LocalPkiFixture.PASSWORD,
                        format, level, pki.tsaUrl, evidence, online)
                : AsicOperations.signAsicE(payloads(), pki.pkcs12.toFile(), LocalPkiFixture.PASSWORD,
                        format, level, pki.tsaUrl, evidence, online);
    }

    private Map<String, byte[]> payloads() {
        return Map.of("payload.txt", PAYLOAD, "nested/second.txt", "second payload".getBytes(StandardCharsets.UTF_8));
    }
}
