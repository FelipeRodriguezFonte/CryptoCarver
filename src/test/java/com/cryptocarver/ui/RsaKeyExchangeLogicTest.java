package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.Tr31TestVectors;
import com.cryptocarver.model.CryptoEnvelopeCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

class RsaKeyExchangeLogicTest {
    private static String certificate;
    private static String publicPem;
    private static String privatePem;

    @BeforeAll
    static void generateSyntheticRecipient() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        publicPem = AsymmetricKeyOperations.exportPublicKeyPEM(pair.getPublic());
        privatePem = AsymmetricKeyOperations.exportPrivateKeyPEM(pair.getPrivate());
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic RSA exchange unit test";
        certificate = CertificateGenerator.exportCertificatePEM(CertificateGenerator.generateSelfSignedCertificate(pair, config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Raw OAEP", "JWE Compact", "CMS EnvelopedData"})
    void envelopePreservesProfileAndMetadataAndImportsWithoutFxml(String format) throws Exception {
        var exported = RsaKeyExchangeLogic.export(format.equals("CMS EnvelopedData") ? certificate : publicPem,
                Tr31TestVectors.keys[1], format, true, " synthetic-id ", "7");
        var envelope = CryptoEnvelopeCodec.deserializeAuto(exported.outputText());
        assertEquals("synthetic-id", envelope.getKid());
        assertEquals(7, envelope.getKeyVersion());
        assertEquals(exported.profile().name(), envelope.getExtensions().get("profile"));
        // The envelope's profile takes precedence over the import selector.
        var imported = RsaKeyExchangeLogic.importKey(privatePem, exported.outputText(), "unused selector");
        assertEquals(Tr31TestVectors.keys[1], imported.recoveredHex());
        assertTrue(imported.report().contains("Key Version:    7"));
        assertTrue(imported.report().contains("matches recovered key"));
    }

    @Test
    void missingRecipientMapsToRecipientFieldBeforeCrypto() {
        var error = assertThrows(KeyDistributionValidation.class,
                () -> RsaKeyExchangeLogic.export("", "", null, false, "", ""));
        assertEquals("module.keys.rsaKex.required", error.messageKey());
        assertEquals("rsaKexRecipientPemArea", error.fieldKey());
    }

    @Test
    void unrecognizedRecipientMapsToPemField() {
        var error = assertThrows(KeyDistributionValidation.class,
                () -> RsaKeyExchangeLogic.export("synthetic unrecognized PEM", Tr31TestVectors.keys[1], null, false, "", ""));
        assertEquals("module.keys.rsaKex.pemUnrecognized", error.messageKey());
        assertEquals("rsaKexRecipientPemArea", error.fieldKey());
    }

    @Test
    void invalidEnvelopeVersionMapsToVersionField() {
        var error = assertThrows(KeyDistributionValidation.class,
                () -> RsaKeyExchangeLogic.export(publicPem, Tr31TestVectors.keys[1], null, true, "", "not an integer"));
        assertEquals("module.keys.rsaKex.keyVersionInvalid", error.messageKey());
        assertEquals("rsaKexKeyVersionField", error.fieldKey());
    }

    @Test
    void unknownProfileDefaultsToRawOaep() throws Exception {
        var exported = RsaKeyExchangeLogic.export(publicPem, Tr31TestVectors.keys[1], "unknown", false, "", "");
        var imported = RsaKeyExchangeLogic.importKey(privatePem, exported.outputText(), null);
        assertEquals(com.cryptocarver.crypto.RsaKeyWrapOperations.WrapProfile.RAW_OAEP, exported.profile());
        assertEquals(Tr31TestVectors.keys[1], imported.recoveredHex());
        assertTrue(exported.report().contains("Envelope:       no"));
    }
}
