package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.cryptocarver.crypto.JweComposer.HeaderOptions;
import com.nimbusds.jose.CompressionAlgorithm;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JweComposerTest {

    private static final String PAYLOAD = "{\"card\":\"4111111111111111\"}";
    private static KeyPair rsa;
    private static KeyPair ec;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        rsa = rsaGen.generateKeyPair();
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        ec = ecGen.generateKeyPair();
    }

    static java.util.List<String> keyAlgorithms() {
        return JweComposer.KEY_ALGORITHMS;
    }

    @ParameterizedTest
    @MethodSource("keyAlgorithms")
    void everyOfferedKeyAlgorithmRoundTrips(String alg) throws Exception {
        String[] keys = keysFor(JWEAlgorithm.parse(alg), EncryptionMethod.A256GCM);
        String token = JweComposer.encrypt(PAYLOAD, alg, "A256GCM", false, HeaderOptions.none(),
                keys[0], SecretEncoding.HEX, 1000);
        assertEquals(PAYLOAD, decrypt(token, keys[1]));
    }

    @Test
    void everyContentAlgorithmRoundTrips() throws Exception {
        for (String enc : JweComposer.CONTENT_ALGORITHMS) {
            String token = JweComposer.encrypt(PAYLOAD, "RSA-OAEP-256", enc, true, HeaderOptions.none(),
                    pem("PUBLIC KEY", rsa.getPublic().getEncoded()), SecretEncoding.UTF8, 1000);
            assertEquals(PAYLOAD, decrypt(token, pem("PRIVATE KEY", rsa.getPrivate().getEncoded())), enc);
        }
    }

    @Test
    void writesOptionalHeaderParameters() throws Exception {
        HeaderOptions options = new HeaderOptions(" rsa-2026 ", "JWT", "JWT", "Alice", "Bob",
                "{\"iss\":\"issuer\",\"x-trace\":7}");
        String token = JweComposer.encrypt(PAYLOAD, "ECDH-ES", "A128GCM", true, options,
                pem("PUBLIC KEY", ec.getPublic().getEncoded()), SecretEncoding.UTF8, 1000);
        JWEHeader header = JWEObject.parse(token).getHeader();
        assertEquals("rsa-2026", header.getKeyID());
        assertEquals("JWT", header.getType().getType());
        assertEquals("JWT", header.getContentType());
        assertEquals(new Base64URL("QWxpY2U"), header.getAgreementPartyUInfo());
        assertEquals(new Base64URL("Qm9i"), header.getAgreementPartyVInfo());
        assertEquals("issuer", header.getCustomParam("iss"));
        assertEquals(CompressionAlgorithm.DEF, header.getCompressionAlgorithm());
        assertEquals(PAYLOAD, decrypt(token, pem("PRIVATE KEY", ec.getPrivate().getEncoded())));
    }

    @Test
    void blankHeaderOptionsAreOmitted() throws Exception {
        String token = JweComposer.encrypt(PAYLOAD, "dir", "A128GCM", false,
                new HeaderOptions("", " ", null, "", "", "  "), "00112233445566778899aabbccddeeff",
                SecretEncoding.HEX, 1000);
        JWEHeader header = JWEObject.parse(token).getHeader();
        assertNull(header.getKeyID());
        assertNull(header.getType());
        assertEquals(2, header.toJSONObject().size());
    }

    @Test
    void customParametersCannotOverrideAlgorithmFields() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JweComposer.encrypt(PAYLOAD, "dir", "A128GCM", false,
                        new HeaderOptions(null, null, null, null, null, "{\"alg\":\"none\"}"),
                        "00112233445566778899aabbccddeeff", SecretEncoding.HEX, 1000));
        assertTrue(error.getMessage().contains("'alg'"));
        assertThrows(IllegalArgumentException.class,
                () -> JweComposer.encrypt(PAYLOAD, "dir", "A128GCM", false,
                        new HeaderOptions(null, null, null, null, null, "not json"),
                        "00112233445566778899aabbccddeeff", SecretEncoding.HEX, 1000));
    }

    @Test
    void aesKeyLengthMismatchNamesTheExpectedSize() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JweComposer.encrypt(PAYLOAD, "A256KW", "A256GCM", false, HeaderOptions.none(),
                        "00112233445566778899aabbccddeeff", SecretEncoding.HEX, 1000));
        assertTrue(error.getMessage().contains("256-bit"), error.getMessage());
    }

    @Test
    void pbes2UsesTheRequestedIterationCount() throws Exception {
        String token = JweComposer.encrypt(PAYLOAD, "PBES2-HS256+A128KW", "A128GCM", false, HeaderOptions.none(),
                "correct horse", SecretEncoding.UTF8, 4096);
        assertEquals(4096, JWEObject.parse(token).getHeader().getPBES2Count());
        assertEquals(PAYLOAD, decrypt(token, "correct horse", SecretEncoding.UTF8));
    }

    @Test
    void legacyRsaAlgorithmsAreRefusedForEncryption() {
        String publicPem = pem("PUBLIC KEY", rsa.getPublic().getEncoded());
        for (String alg : new String[] { "RSA1_5", "RSA-OAEP" }) {
            assertThrows(IllegalArgumentException.class, () -> JweComposer.encrypt(PAYLOAD, alg, "A256GCM", false,
                    HeaderOptions.none(), publicPem, SecretEncoding.UTF8, 1000));
        }
    }

    private static String[] keysFor(JWEAlgorithm alg, EncryptionMethod enc) {
        if (JWEAlgorithm.Family.RSA.contains(alg)) {
            return new String[] { pem("PUBLIC KEY", rsa.getPublic().getEncoded()), pem("PRIVATE KEY", rsa.getPrivate().getEncoded()) };
        }
        if (JWEAlgorithm.Family.ECDH_ES.contains(alg)) {
            return new String[] { pem("PUBLIC KEY", ec.getPublic().getEncoded()), pem("PRIVATE KEY", ec.getPrivate().getEncoded()) };
        }
        int bytes = JWEAlgorithm.DIR.equals(alg) ? enc.cekBitLength() / 8
                : alg.getName().contains("128") ? 16 : alg.getName().contains("192") ? 24 : 32;
        byte[] secret = new byte[bytes];
        Arrays.fill(secret, (byte) 0x5A);
        String hex = HexFormat.of().formatHex(secret);
        return new String[] { hex, hex };
    }

    private static String decrypt(String token, String key) throws Exception {
        return decrypt(token, key, SecretEncoding.HEX);
    }

    private static String decrypt(String token, String key, SecretEncoding encoding) throws Exception {
        JWEObject object = JWEObject.parse(token);
        object.decrypt(JweComposer.decrypter(object.getHeader().getAlgorithm(), key, encoding,
                new JweComposer.LoadedKey()));
        return object.getPayload().toString();
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + java.util.Base64.getMimeEncoder().encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
