package com.cryptocarver.crypto;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.PasswordBasedEncrypter;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.impl.ContentCryptoProvider;
import com.nimbusds.jose.jca.JWEJCAContext;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JweManualCekRecoveryTest {

    private static final String PAYLOAD = "manual-cek-preview-payload";
    private static final byte[] AES_128_KEY = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_256_KEY = "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PASSWORD = "preview-password".getBytes(StandardCharsets.UTF_8);

    @Test
    void inventoryIsLimitedToAlreadyLoadedLocalKeyMaterial() {
        Set<JWEAlgorithm> supported = JWEManualCekRecovery.supportedAlgorithms();

        assertTrue(supported.containsAll(Set.of(
                JWEAlgorithm.RSA_OAEP_256, JWEAlgorithm.RSA1_5,
                JWEAlgorithm.A128KW, JWEAlgorithm.A256KW,
                JWEAlgorithm.ECDH_ES, JWEAlgorithm.ECDH_ES_A128KW,
                JWEAlgorithm.ECDH_ES_A256KW,
                JWEAlgorithm.PBES2_HS256_A128KW, JWEAlgorithm.PBES2_HS512_A256KW,
                JWEAlgorithm.DIR)));
        assertFalse(supported.contains(JWEAlgorithm.A128GCMKW));
        assertFalse(supported.contains(JWEAlgorithm.ECDH_1PU));
    }

    @Test
    void rsaOaep256AndRsa15RecoverTheSameAuthenticatedContent() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).generate();

        for (JWEAlgorithm algorithm : Set.of(JWEAlgorithm.RSA_OAEP_256, JWEAlgorithm.RSA1_5)) {
            JWEObject jwe = encrypt(
                    new JWEHeader.Builder(algorithm, EncryptionMethod.A256GCM).build(),
                    new RSAEncrypter(key.toRSAPublicKey()));
            byte[] cek = JWEManualCekRecovery.recover(jwe, key.toRSAPrivateKey(), null);

            assertEquals(PAYLOAD, decryptContentWithCek(jwe, cek));
            jwe = JWEObject.parse(jwe.serialize());
            jwe.decrypt(new RSADecrypter(key.toRSAPrivateKey()));
            assertEquals(PAYLOAD, jwe.getPayload().toString());
        }
    }

    @Test
    void aesKwRecoversAndRejectsWrongOrIncompatibleKey() throws Exception {
        JWEObject jwe = encrypt(
                new JWEHeader.Builder(JWEAlgorithm.A256KW, EncryptionMethod.A256GCM).build(),
                new AESEncrypter(AES_256_KEY));
        byte[] cek = JWEManualCekRecovery.recover(jwe, null, AES_256_KEY);

        assertEquals(PAYLOAD, decryptContentWithCek(jwe, cek));
        assertThrows(JWEManualCekRecovery.ManualCekRecoveryException.class,
                () -> JWEManualCekRecovery.recover(jwe, null, AES_128_KEY));
        assertThrows(JWEManualCekRecovery.ManualCekRecoveryException.class,
                () -> JWEManualCekRecovery.recover(jwe, null, new byte[15]));
    }

    @Test
    void ecdhEsDirectAndKeyWrapRecoverUsingTheExactHeaderParameters() throws Exception {
        ECKey recipient = new ECKeyGenerator(Curve.P_256).generate();

        for (JWEAlgorithm algorithm : Set.of(JWEAlgorithm.ECDH_ES, JWEAlgorithm.ECDH_ES_A256KW)) {
            JWEObject jwe = encrypt(
                    new JWEHeader.Builder(algorithm, EncryptionMethod.A256GCM)
                            .agreementPartyUInfo(Base64URL.encode("alice"))
                            .agreementPartyVInfo(Base64URL.encode("service"))
                            .build(),
                    new ECDHEncrypter(recipient.toECPublicKey()));
            byte[] cek = JWEManualCekRecovery.recover(jwe, recipient.toECPrivateKey(), null);

            assertEquals(PAYLOAD, decryptContentWithCek(jwe, cek));
            JWEObject normal = JWEObject.parse(jwe.serialize());
            normal.decrypt(new ECDHDecrypter(recipient.toECPrivateKey()));
            assertEquals(PAYLOAD, normal.getPayload().toString());
        }
    }

    @Test
    void pbes2RecoversUsingHeaderSaltAndIterationCount() throws Exception {
        JWEObject jwe = encrypt(
                new JWEHeader.Builder(JWEAlgorithm.PBES2_HS512_A256KW, EncryptionMethod.A256GCM).build(),
                new PasswordBasedEncrypter(PASSWORD, 16, 2048));
        byte[] cek = JWEManualCekRecovery.recover(jwe, null, PASSWORD);

        assertEquals(PAYLOAD, decryptContentWithCek(jwe, cek));
        assertThrows(JWEManualCekRecovery.ManualCekRecoveryException.class,
                () -> JWEManualCekRecovery.recover(jwe, null, "wrong-password".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void directEncryptionIsExplicitAndDoesNotReturnTheDirectCek() throws Exception {
        JWEObject jwe = encrypt(
                new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).build(),
                new DirectEncrypter(AES_256_KEY));

        assertEquals(null, jwe.getEncryptedKey());
        JWEManualCekRecovery.ManualCekRecoveryException error = assertThrows(
                JWEManualCekRecovery.ManualCekRecoveryException.class,
                () -> JWEManualCekRecovery.recover(jwe, null, AES_256_KEY));
        assertTrue(error.getMessage().contains("direct key"));
        assertEquals(PAYLOAD, decryptContentWithCek(jwe, AES_256_KEY));
    }

    @Test
    void rejectsAdulteratedHeaderAndCorruptCompactJwe() throws Exception {
        JWEObject original = encrypt(
                new JWEHeader.Builder(JWEAlgorithm.A256KW, EncryptionMethod.A256GCM).build(),
                new AESEncrypter(AES_256_KEY));
        String[] parts = original.serialize().split("\\.", -1);
        String adulteratedHeader = new JWEHeader.Builder(JWEAlgorithm.A256KW, EncryptionMethod.A256GCM)
                .keyID("adulterated")
                .build().toBase64URL().toString();
        JWEObject adulterated = JWEObject.parse(adulteratedHeader + "." + parts[1] + "." + parts[2] + "." + parts[3] + "." + parts[4]);

        assertThrows(JOSEException.class, () -> adulterated.decrypt(new AESDecrypter(AES_256_KEY)));
        assertThrows(ParseException.class, () -> JWEObject.parse("not-a-jwe"));
    }

    private static JWEObject encrypt(JWEHeader header, JWEEncrypter encrypter) throws Exception {
        JWEObject jwe = new JWEObject(header, new Payload(PAYLOAD));
        jwe.encrypt(encrypter);
        return jwe;
    }

    private static String decryptContentWithCek(JWEObject jwe, byte[] cek) throws JOSEException {
        byte[] aad = jwe.getHeader().toBase64URL().toString().getBytes(StandardCharsets.US_ASCII);
        byte[] payload = ContentCryptoProvider.decrypt(
                jwe.getHeader(), aad, jwe.getEncryptedKey(), jwe.getIV(), jwe.getCipherText(), jwe.getAuthTag(),
                new SecretKeySpec(cek, "AES"), new JWEJCAContext());
        return new String(payload, StandardCharsets.UTF_8);
    }
}
