package com.cryptocarver.ui;

import com.cryptocarver.crypto.CertificateAuthorityOperations;
import com.cryptocarver.crypto.CertificateGenerator;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CertificateChainLogicTest {
    private static String leafPem;
    private static String rootPem;
    private static String expiredPem;

    @BeforeAll
    static void generateSyntheticChain() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var caPair = generator.generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic pure chain root";
        var root = CertificateGenerator.generateRootCA(caPair, config, 1);
        config.commonName = "Synthetic pure chain leaf";
        String csrPem = CertificateGenerator.generateCSR(generator.generateKeyPair(), config);
        var csr = new PKCS10CertificationRequest(Base64.getDecoder().decode(csrPem.replaceAll("-----[^-]+-----|\\s", "")));
        var leaf = CertificateAuthorityOperations.issueFromCsr(csr, root, caPair.getPrivate(), 30, "SHA256withRSA");
        leafPem = CertificateGenerator.exportCertificatePEM(leaf);
        rootPem = CertificateGenerator.exportCertificatePEM(root);
        config.validityDays = -1;
        expiredPem = CertificateGenerator.exportCertificatePEM(CertificateGenerator.generateRootCA(generator.generateKeyPair(), config, 1));
    }

    @Test
    void validatesTheCompleteGeneratedChainAndBuildsItsReport() throws Exception {
        var validation = CertificateChainLogic.validate(leafPem + rootPem, null);
        assertTrue(validation.result().isValid);
        assertEquals(2, validation.chainLength());
        assertTrue(validation.outputText().startsWith("CHAIN VALIDATION: ✅ VALID"));
    }

    @Test
    void reportsTheMissingTrustAnchorAsInvalid() throws Exception {
        var validation = CertificateChainLogic.validate(leafPem, "");
        assertFalse(validation.result().isValid);
        assertEquals(1, validation.chainLength());
        assertTrue(validation.outputText().startsWith("CHAIN VALIDATION: ❌ INVALID"));
    }

    @Test
    void reportsExpiredCertificateMaterialAsInvalid() throws Exception {
        var validation = CertificateChainLogic.validate(expiredPem, null);
        assertFalse(validation.result().isValid);
    }

    @Test
    void rejectsEmptyInputWithTheExistingMessage() {
        var error = assertThrows(KeysInputValidation.class, () -> CertificateChainLogic.validate("  ", null));
        assertEquals("Certificate Chain PEM is required", error.getMessage());
        assertEquals("Input Error", error.title());
    }

    @Test
    void rejectsInputContainingNoPemCertificates() {
        var error = assertThrows(KeysInputValidation.class, () -> CertificateChainLogic.validate("synthetic invalid input", ""));
        assertEquals("No valid PEM certificates found", error.getMessage());
    }
}
