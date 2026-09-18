package com.cryptocarver.crypto;

import eu.europa.esig.dss.asic.cades.ASiCWithCAdESSignatureParameters;
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESService;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One validator across formats. The documents here are signed with DSS itself,
 * so what is asserted is that the facade reads a real CAdES and a real ASiC-E
 * and reports the baseline level and the ETSI report for both.
 */
class AdesValidationOperationsTest {

    private static final char[] PASSWORD = "lab".toCharArray();

    @TempDir
    Path temp;

    @BeforeAll
    static void installBouncyCastleProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void readsACAdESSignatureAndItsBaselineLevel() throws Exception {
        Path keystore = keystore();
        byte[] signed = signCades(keystore, "hello CAdES".getBytes(StandardCharsets.UTF_8));

        AdesValidationOperations.Result result =
                AdesValidationOperations.validate(signed, "signature.p7m", null, null);

        assertEquals(1, result.signatures().size());
        AdesValidationOperations.SignatureOutcome outcome = result.signatures().get(0);
        assertEquals("CAdES-BASELINE-B", outcome.level());
        assertFalse(outcome.selfContained(),
                "A B-B signature carries no revocation evidence, so it cannot outlive its certificate");
        assertNotNull(outcome.indication());
    }

    @Test
    void readsAnAsicEContainer() throws Exception {
        Path keystore = keystore();
        byte[] container = signAsicE(keystore, "hello ASiC".getBytes(StandardCharsets.UTF_8));

        AdesValidationOperations.Result result =
                AdesValidationOperations.validate(container, "container.asice", null, null);

        assertEquals(1, result.signatures().size());
        assertTrue(result.signatures().get(0).level().startsWith("CAdES-BASELINE"),
                result.signatures().get(0).level());
    }

    /** The artefact a conformance exercise asks for. */
    @Test
    void emitsTheEtsiValidationReport() throws Exception {
        byte[] signed = signCades(keystore(), "report".getBytes(StandardCharsets.UTF_8));
        AdesValidationOperations.Result result =
                AdesValidationOperations.validate(signed, "signature.p7m", null, null);

        assertNotNull(result.etsiValidationReportXml());
        assertTrue(result.etsiValidationReportXml().contains("ValidationReport"),
                result.etsiValidationReportXml().substring(0, Math.min(200, result.etsiValidationReportXml().length())));
        assertNotNull(result.simpleReportXml());
    }

    /**
     * Without a trust anchor the chain ends nowhere, and a chain that ends
     * nowhere is unfinished rather than valid. The result has to say
     * INDETERMINATE rather than look like a pass.
     */
    @Test
    void withoutATrustAnchorTheResultCannotPass() throws Exception {
        byte[] signed = signCades(keystore(), "no anchor".getBytes(StandardCharsets.UTF_8));
        AdesValidationOperations.Result result =
                AdesValidationOperations.validate(signed, "signature.p7m", null, null);

        assertFalse(result.trustAnchorSupplied());
        assertNotEquals(Indication.TOTAL_PASSED, result.signatures().get(0).indication());
        assertTrue(AdesValidationOperations.describe(result, java.util.Locale.forLanguageTag("es"))
                .contains("INDETERMINATE"));
    }

    @Test
    void refusesAnEmptyDocument() {
        assertThrows(IllegalArgumentException.class,
                () -> AdesValidationOperations.validate(new byte[0], "x.p7m", null, null));
    }

    @Test
    void theSummaryNamesTheLevelAndWhatItBuys() throws Exception {
        byte[] signed = signCades(keystore(), "summary".getBytes(StandardCharsets.UTF_8));
        String english = AdesValidationOperations.describe(
                AdesValidationOperations.validate(signed, "signature.p7m", null, null),
                java.util.Locale.ENGLISH);

        assertTrue(english.contains("CAdES-BASELINE-B"), english);
        assertTrue(english.contains("does not survive the certificate expiring"), english);
        assertTrue(english.contains("TS 119 102-2"), english);
    }

    // -------------------------------------------------------------- fixtures

    private Path keystore() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();

        X500Name name = new X500Name("CN=AdES Lab Signer,C=ES");
        Instant now = Instant.now();
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(
                new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()),
                        Date.from(now.minus(1, ChronoUnit.DAYS)),
                        Date.from(now.plus(365, ChronoUnit.DAYS)), name, pair.getPublic())
                        .build(new JcaContentSignerBuilder("SHA256withECDSA")
                                .setProvider("BC").build(pair.getPrivate())));

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, PASSWORD);
        store.setKeyEntry("signer", pair.getPrivate(), PASSWORD, new Certificate[]{certificate});
        Path path = temp.resolve("signer.p12");
        try (FileOutputStream output = new FileOutputStream(path.toFile())) {
            store.store(output, PASSWORD);
        }
        return path;
    }

    private static byte[] signCades(Path keystore, byte[] content) throws Exception {
        try (Pkcs12SignatureToken token = new Pkcs12SignatureToken(
                Files.readAllBytes(keystore), new KeyStore.PasswordProtection(PASSWORD))) {
            DSSPrivateKeyEntry entry = token.getKeys().get(0);
            DSSDocument document = new InMemoryDocument(content, "content.txt");

            CAdESSignatureParameters parameters = new CAdESSignatureParameters();
            parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_B);
            parameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(entry.getCertificate());
            parameters.setCertificateChain(entry.getCertificateChain());

            CAdESService service = new CAdESService(new CommonCertificateVerifier());
            ToBeSigned toBeSigned = service.getDataToSign(document, parameters);
            SignatureValue value = token.sign(toBeSigned, parameters.getDigestAlgorithm(), entry);
            return read(service.signDocument(document, parameters, value));
        }
    }

    private static byte[] signAsicE(Path keystore, byte[] content) throws Exception {
        try (Pkcs12SignatureToken token = new Pkcs12SignatureToken(
                Files.readAllBytes(keystore), new KeyStore.PasswordProtection(PASSWORD))) {
            DSSPrivateKeyEntry entry = token.getKeys().get(0);
            DSSDocument document = new InMemoryDocument(content, "content.txt");

            ASiCWithCAdESSignatureParameters parameters = new ASiCWithCAdESSignatureParameters();
            parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_B);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.aSiC().setContainerType(ASiCContainerType.ASiC_E);
            parameters.setSigningCertificate(entry.getCertificate());
            parameters.setCertificateChain(entry.getCertificateChain());

            ASiCWithCAdESService service = new ASiCWithCAdESService(new CommonCertificateVerifier());
            ToBeSigned toBeSigned = service.getDataToSign(document, parameters);
            SignatureValue value = token.sign(toBeSigned, parameters.getDigestAlgorithm(), entry);
            return read(service.signDocument(document, parameters, value));
        }
    }

    private static byte[] read(DSSDocument document) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.writeTo(output);
        return output.toByteArray();
    }
}
