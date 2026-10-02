package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CertificateGenerator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class CmsLogicTest {
    private static KeyPair signer;
    private static X509Certificate certificate;
    private static String certificatePem;
    private static String privatePem;

    @BeforeAll
    static void generateSyntheticSigner() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signer = generator.generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic pure CMS signer";
        certificate = CertificateGenerator.generateSelfSignedCertificate(signer, config);
        certificatePem = CertificateGenerator.exportCertificatePEM(certificate);
        privatePem = AsymmetricKeyOperations.exportPrivateKeyPEM(signer.getPrivate());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void signsAndVerifiesLocalCmsAndCadesWithBothContentModes(boolean detached, boolean cadesBes) throws Exception {
        String content = " Synthetic CMS content with whitespace ";
        byte[] signed = CmsLogic.signLocal(content.getBytes(StandardCharsets.UTF_8), certificatePem, privatePem, detached, cadesBes);
        var verified = CmsLogic.verify(CmsLogic.armor(signed), detached ? content : null, new Date());
        assertTrue(verified.result().verified);
        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), verified.result().content);
        assertEquals(cadesBes ? "CAdES-BES" : "CMS / PKCS#7", verified.profile().profile());
        assertTrue(verified.report().startsWith("VERIFICATION RESULT: ✅ VALID"));
        assertArrayEquals(signed, CmsLogic.decodeCmsArmored(CmsLogic.armor(signed)));
    }

    @Test
    void encryptsAndDecryptsWithTheGeneratedLocalKey() throws Exception {
        byte[] data = "Synthetic CMS envelope".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = CmsLogic.encrypt(data, CmsLogic.parseRecipient(certificatePem));
        assertArrayEquals(data, CmsLogic.decryptLocal(encrypted, privatePem));
    }

    @Test
    void rejectsVerificationWithAMismatchedEmbeddedCertificate() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic other CMS certificate";
        var other = CertificateGenerator.generateSelfSignedCertificate(generator.generateKeyPair(), config);
        byte[] signed = CmsLogic.signLocal("Synthetic mismatch".getBytes(StandardCharsets.UTF_8),
                CertificateGenerator.exportCertificatePEM(other), privatePem, false, false);
        var result = CmsLogic.verify(CmsLogic.armor(signed), null, new Date());
        assertFalse(result.result().verified);
        assertTrue(result.report().startsWith("VERIFICATION RESULT: ❌ INVALID"));
    }

    @Test
    void parsesSelectedCertificateEvidenceWithoutReclassifyingItAsACrl() {
        var crls = new java.util.ArrayList<java.security.cert.X509CRL>();
        var certificates = new java.util.ArrayList<X509Certificate>();
        CmsLogic.parseEvidence(certificatePem.getBytes(StandardCharsets.UTF_8), "synthetic.pem", crls, certificates);
        assertTrue(crls.isEmpty());
        assertEquals(java.util.List.of(certificate), certificates);
    }

    @Test
    void rejectsAnEvidenceFileWithNeitherSupportedEncoding() {
        var error = assertThrows(IllegalArgumentException.class, () -> CmsLogic.parseEvidence(
                new byte[] {1, 2, 3}, "synthetic.bin", new java.util.ArrayList<>(), new java.util.ArrayList<>()));
        assertEquals("synthetic.bin is neither a valid X.509 CRL nor X.509 certificate evidence", error.getMessage());
    }
}
