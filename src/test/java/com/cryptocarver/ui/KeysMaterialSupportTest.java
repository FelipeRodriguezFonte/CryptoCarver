package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CertificateGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KeysMaterialSupportTest {
    private static KeyPair pair;
    private static X509Certificate issuer;
    private static X509Certificate differentName;
    private static X509Certificate differentSigningKey;

    @BeforeAll
    static void generateSyntheticCertificates() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic material issuer";
        issuer = CertificateGenerator.generateRootCA(pair, config, 1);
        differentSigningKey = CertificateGenerator.generateRootCA(generator.generateKeyPair(), config, 1);
        config.commonName = "Synthetic different subject";
        differentName = CertificateGenerator.generateRootCA(pair, config, 1);
    }

    @Test
    void publicPemAndCertificateExposeTheSameGeneratedPublicKey() throws Exception {
        var publicPem = AsymmetricKeyOperations.exportPublicKeyPEM(pair.getPublic());
        var certificatePem = CertificateGenerator.exportCertificatePEM(issuer);
        assertArrayEquals(pair.getPublic().getEncoded(), KeysMaterialSupport.parsePublicMaterial(publicPem).getEncoded());
        assertArrayEquals(pair.getPublic().getEncoded(), KeysMaterialSupport.parsePublicMaterial(certificatePem).getEncoded());
    }

    @Test
    void importedPrivatePemSignsForTheOriginalPublicKey() throws Exception {
        var privatePem = AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate());
        var imported = KeysMaterialSupport.parsePrivateMaterial(privatePem);
        byte[] message = "Synthetic parser verification".getBytes(StandardCharsets.UTF_8);
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(imported);
        signer.update(message);
        byte[] signed = signer.sign();
        signer.initVerify(pair.getPublic());
        signer.update(message);
        assertTrue(signer.verify(signed));
    }

    @Test
    void emptyPublicMaterialIsRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> KeysMaterialSupport.parsePublicMaterial(" "));
    }

    @Test
    void emptyPrivateMaterialIsRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException.class, () -> KeysMaterialSupport.parsePrivateMaterial(" "));
    }

    @Test
    void verifiedIssuerRequiresMatchingSubject() {
        assertTrue(KeysMaterialSupport.isVerifiedIssuer(issuer, issuer));
        assertFalse(KeysMaterialSupport.isVerifiedIssuer(issuer, differentName));
    }

    @Test
    void matchingSubjectDoesNotReplaceSignatureVerification() {
        assertEquals(issuer.getSubjectX500Principal(), differentSigningKey.getIssuerX500Principal());
        assertFalse(KeysMaterialSupport.isVerifiedIssuer(issuer, differentSigningKey));
    }

    @Test
    void sanValuesTrimAndDiscardEmptyEntries() {
        assertEquals(List.of("a.example", "b.example"), KeysMaterialSupport.commaSeparatedValues(" a.example, ,b.example, "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void absentSanValuesProduceAnEmptyList(String input) {
        assertTrue(KeysMaterialSupport.commaSeparatedValues(input).isEmpty());
    }
}
