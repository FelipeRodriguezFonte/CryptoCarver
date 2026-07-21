package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class OpenPgpOperationsTest {

    private static final char[] PASSPHRASE = "laboratory-passphrase".toCharArray();

    @Test
    void encryptDecryptRoundTripUsesArmoredOpenPgp() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver OpenPGP Test <test@example.invalid>", PASSPHRASE);
        String plaintext = "OpenPGP interoperability test ñ";

        String encrypted = OpenPgpOperations.encrypt(plaintext, keys.publicKeyArmored());
        OpenPgpOperations.DecryptionResult result = OpenPgpOperations.decrypt(
                encrypted, keys.secretKeyArmored(), PASSPHRASE);

        assertTrue(encrypted.startsWith("-----BEGIN PGP MESSAGE-----"));
        assertArrayEquals(plaintext.getBytes(StandardCharsets.UTF_8), result.plaintext());
        assertTrue(result.integrityProtected());
        assertNotNull(result.recipientKeyId());
    }

    @Test
    void decryptRejectsWrongPassphrase() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver OpenPGP Test <test@example.invalid>", PASSPHRASE);
        String encrypted = OpenPgpOperations.encrypt("secret", keys.publicKeyArmored());

        assertThrows(Exception.class, () -> OpenPgpOperations.decrypt(
                encrypted, keys.secretKeyArmored(), "wrong-passphrase".toCharArray()));
    }

    @Test
    void detachedSignatureDetectsChangedData() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver OpenPGP Test <test@example.invalid>", PASSPHRASE);
        byte[] data = "signed content".getBytes(StandardCharsets.UTF_8);

        String signature = OpenPgpOperations.signDetached(data, keys.secretKeyArmored(), PASSPHRASE);
        OpenPgpOperations.VerificationResult valid = OpenPgpOperations.verifyDetached(
                "signed content", signature, keys.publicKeyArmored());
        OpenPgpOperations.VerificationResult altered = OpenPgpOperations.verifyDetached(
                "signed contenT", signature, keys.publicKeyArmored());

        assertTrue(signature.startsWith("-----BEGIN PGP SIGNATURE-----"));
        assertTrue(valid.valid());
        assertFalse(altered.valid());
    }

    @Test
    void detachedSignatureUsesExactBinaryBytes() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver Binary Test <binary@example.invalid>", PASSPHRASE);
        byte[] binary = new byte[] {0, (byte) 0xFF, 0x0A, (byte) 0x80, 0x55};
        String signature = OpenPgpOperations.signDetached(binary, keys.secretKeyArmored(), PASSPHRASE);

        assertTrue(OpenPgpOperations.verifyDetached(binary, signature, keys.publicKeyArmored()).valid());
        assertFalse(OpenPgpOperations.verifyDetached(new byte[] {0, (byte) 0xFF, 0x0A, (byte) 0x80, 0x54},
                signature, keys.publicKeyArmored()).valid());
    }

    @Test
    void detachedSignatureInspectionReportsPublicPacketMetadata() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver Signature Inspector <sig@example.invalid>", PASSPHRASE);
        String signature = OpenPgpOperations.signDetached("inspect me", keys.secretKeyArmored(), PASSPHRASE);

        OpenPgpOperations.DetachedSignatureInspection inspection =
                OpenPgpOperations.inspectDetachedSignature(signature);

        assertEquals(keys.keyId(), inspection.signerKeyId());
        assertEquals("Binary document", inspection.signatureType());
        assertEquals("SHA-256", inspection.hashAlgorithm());
        assertTrue(inspection.keyAlgorithm().startsWith("RSA"));
        assertNotNull(inspection.creationTime());
    }

    @Test
    void inspectionReportsOnlyPublicMetadataForPublicAndSecretRings() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver Inspector <inspect@example.invalid>", PASSPHRASE);

        OpenPgpOperations.KeyInspection publicInspection = OpenPgpOperations.inspectKey(keys.publicKeyArmored());
        OpenPgpOperations.KeyInspection secretInspection = OpenPgpOperations.inspectKey(keys.secretKeyArmored());

        assertFalse(publicInspection.secretKeyMaterial());
        assertTrue(secretInspection.secretKeyMaterial());
        assertEquals(keys.keyId(), publicInspection.keyId());
        assertEquals(keys.fingerprint(), secretInspection.fingerprint());
        assertTrue(publicInspection.encryptionCapable());
        assertTrue(secretInspection.signingCapable());
        assertTrue(publicInspection.userIds().get(0).contains("Inspector"));
    }

    @Test
    void attachedSignatureCarriesAndVerifiesBinaryContent() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver Attached <attached@example.invalid>", PASSPHRASE);
        byte[] data = new byte[] {0, 1, 2, (byte) 0xFF, 10};

        String signed = OpenPgpOperations.signAttached(data, keys.secretKeyArmored(), PASSPHRASE);
        OpenPgpOperations.SignedMessageVerificationResult result = OpenPgpOperations.verifyAttached(
                signed, keys.publicKeyArmored());

        assertTrue(signed.startsWith("-----BEGIN PGP MESSAGE-----"));
        assertTrue(result.valid());
        assertArrayEquals(data, result.content());
    }

    @Test
    void clearSignedTextRoundTripsAndDetectsModification() throws Exception {
        OpenPgpOperations.KeyPairMaterial keys = OpenPgpOperations.generateRsaKeyPair(
                "CryptoCarver Clear Sign <clear@example.invalid>", PASSPHRASE);
        String text = "Line one\nLine two";

        String clearSigned = OpenPgpOperations.clearSign(text, keys.secretKeyArmored(), PASSPHRASE);
        OpenPgpOperations.SignedMessageVerificationResult valid = OpenPgpOperations.verifyClearSigned(
                clearSigned, keys.publicKeyArmored());
        OpenPgpOperations.SignedMessageVerificationResult altered = OpenPgpOperations.verifyClearSigned(
                clearSigned.replace("Line two", "Line TWO"), keys.publicKeyArmored());

        assertTrue(clearSigned.startsWith("-----BEGIN PGP SIGNED MESSAGE-----"));
        assertTrue(valid.valid());
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), valid.content());
        assertFalse(altered.valid());
    }
}
