package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.crypto.CertificateGenerator;
import com.cryptocarver.crypto.TR34Operations;
import com.cryptocarver.crypto.Tr31TestVectors;
import com.cryptocarver.model.CryptoEnvelopeCodec;
import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

class Tr34LogicTest {
    private static String senderPrivate;
    private static String receiverPrivate;
    private static String senderCertificate;
    private static String receiverCertificate;

    @BeforeAll
    static void generateSyntheticParties() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var sender = generator.generateKeyPair();
        var receiver = generator.generateKeyPair();
        senderPrivate = AsymmetricKeyOperations.exportPrivateKeyPEM(sender.getPrivate());
        receiverPrivate = AsymmetricKeyOperations.exportPrivateKeyPEM(receiver.getPrivate());
        var config = new CertificateGenerator.CertificateConfig();
        config.commonName = "Synthetic TR-34 unit test";
        senderCertificate = CertificateGenerator.exportCertificatePEM(CertificateGenerator.generateSelfSignedCertificate(sender, config));
        receiverCertificate = CertificateGenerator.exportCertificatePEM(CertificateGenerator.generateSelfSignedCertificate(receiver, config));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void envelopeRoundTripFormatsBothPassProfilesWithoutFxml(boolean twoPass) throws Exception {
        String nonce = twoPass ? DataConverter.bytesToHex(TR34Operations.generateChallengeNonce()) : "";
        var exported = distribute(nonce, true);
        var envelope = CryptoEnvelopeCodec.deserializeAuto(exported.outputText());
        assertEquals("TR34-CMS", envelope.getAlg());
        assertEquals("synthetic-id", envelope.getKid());
        assertNotNull(envelope.getKcv());
        var imported = Tr34Logic.receive(receiverPrivate, senderCertificate, exported.outputText(), nonce);
        assertEquals(Tr31TestVectors.keys[1], imported.recoveredHex());
        assertTrue(imported.received().isSignatureVerified());
        assertEquals(twoPass, imported.received().isNonceVerified());
        assertTrue(imported.report().contains("Envelope KCV:"));
        assertTrue(imported.report().contains("Key ID (authenticated): synthetic-id"));
    }

    @Test
    void nonceMismatchFormatsUntrustedReceptionWithoutChangingRecoveredBytes() throws Exception {
        String nonce = DataConverter.bytesToHex(TR34Operations.generateChallengeNonce());
        var exported = distribute(nonce, false);
        var imported = Tr34Logic.receive(receiverPrivate, senderCertificate, exported.outputText(), "00000000000000000000000000000000");
        assertTrue(imported.received().isSignatureVerified());
        assertFalse(imported.received().isNonceVerified());
        assertEquals(Tr31TestVectors.keys[1], imported.recoveredHex());
        assertTrue(imported.report().contains("NO — possible replay"));
    }

    @Test
    void missingSenderPrivateKeyIsValidatedBeforePemParsing() {
        var error = assertThrows(KeyDistributionValidation.class,
                () -> Tr34Logic.distribute("", "", "", "", "", "", false));
        assertEquals("module.keys.tr34.required", error.messageKey());
        assertEquals("tr34SenderPrivateKeyArea", error.fieldKey());
    }

    @Test
    void nonHexBindingNonceMapsToBindingField() {
        var error = assertThrows(KeyDistributionValidation.class, () -> distribute("not hex", false));
        assertEquals("module.keys.tr34.keyInvalid", error.messageKey());
        assertEquals("tr34BindingNonceField", error.fieldKey());
    }

    @Test
    void nonHexChallengeMapsToChallengeField() throws Exception {
        var exported = distribute("", false);
        var error = assertThrows(KeyDistributionValidation.class,
                () -> Tr34Logic.receive(receiverPrivate, senderCertificate, exported.outputText(), "not hex"));
        assertEquals("module.keys.tr34.keyInvalid", error.messageKey());
        assertEquals("tr34ChallengeNonceField", error.fieldKey());
    }

    private static Tr34Logic.DistributeResult distribute(String nonce, boolean envelope) throws Exception {
        return Tr34Logic.distribute(senderPrivate, senderCertificate, receiverCertificate,
                Tr31TestVectors.keys[1], " synthetic-id ", nonce, envelope);
    }
}
