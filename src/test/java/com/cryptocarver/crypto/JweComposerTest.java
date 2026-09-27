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

    @Test
    void flattenedJsonCarriesAadAsBase64UrlAndAuthenticatesIt() throws Exception {
        String json = JweComposer.encrypt(PAYLOAD, "RSA-OAEP-256", "A256GCM", false, HeaderOptions.none(),
                pem("PUBLIC KEY", rsa.getPublic().getEncoded()), SecretEncoding.UTF8, 1000,
                JweComposer.Serialization.FLATTENED, "order-42");
        java.util.Map<String, Object> members = com.nimbusds.jose.util.JSONObjectUtils.parse(json);
        assertEquals(Base64URL.encode("order-42").toString(), members.get("aad"));
        assertTrue(members.containsKey("encrypted_key"));

        String privatePem = pem("PRIVATE KEY", rsa.getPrivate().getEncoded());
        JweComposer.JsonDecryption result = JweComposer.decryptJson(json, privatePem, SecretEncoding.UTF8);
        assertEquals(PAYLOAD, result.payload());
        assertEquals("order-42", result.aad());

        // Interop: an independent Nimbus parse decrypts the same object.
        com.nimbusds.jose.JWEObjectJSON parsed = com.nimbusds.jose.JWEObjectJSON.parse(json);
        parsed.decrypt(new com.nimbusds.jose.crypto.RSADecrypter(rsa.getPrivate()));
        assertEquals(PAYLOAD, parsed.getPayload().toString());

        members.put("aad", Base64URL.encode("order-43").toString());
        String tampered = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(members);
        assertThrows(IllegalArgumentException.class,
                () -> JweComposer.decryptJson(tampered, privatePem, SecretEncoding.UTF8));
    }

    @Test
    void generalJsonWithOneKeyKeepsAlgInTheProtectedHeader() throws Exception {
        String json = JweComposer.encrypt(PAYLOAD, "A128KW", "A128CBC-HS256", true,
                new HeaderOptions("k1", null, null, null, null, null), "00112233445566778899aabbccddeeff",
                SecretEncoding.HEX, 1000, JweComposer.Serialization.GENERAL, null);
        assertTrue(json.contains("\"recipients\""));
        JweComposer.JsonDecryption result = JweComposer.decryptJson(json, "00112233445566778899aabbccddeeff",
                SecretEncoding.HEX);
        assertEquals(PAYLOAD, result.payload());
        assertEquals("A128KW", result.effectiveHeader().get("alg"));
        assertEquals(1, result.recipientCount());
    }

    @Test
    void generalJsonEncryptsForEveryKeyOfAJwks() throws Exception {
        com.nimbusds.jose.jwk.RSAKey rsaJwk = new com.nimbusds.jose.jwk.RSAKey.Builder(
                (java.security.interfaces.RSAPublicKey) rsa.getPublic()).keyID("r").algorithm(JWEAlgorithm.RSA_OAEP_256).build();
        com.nimbusds.jose.jwk.ECKey ecJwk = new com.nimbusds.jose.jwk.ECKey.Builder(com.nimbusds.jose.jwk.Curve.P_256,
                (java.security.interfaces.ECPublicKey) ec.getPublic()).keyID("e").algorithm(JWEAlgorithm.ECDH_ES_A128KW).build();
        String jwks = new com.nimbusds.jose.jwk.JWKSet(java.util.List.of(rsaJwk, ecJwk)).toString();

        String json = JweComposer.encrypt(PAYLOAD, "RSA-OAEP-256", "A256GCM", false,
                new HeaderOptions(null, "JWT", null, null, null, null), jwks, SecretEncoding.UTF8, 1000,
                JweComposer.Serialization.GENERAL, "shared");

        JweComposer.JsonDecryption viaEc = JweComposer.decryptJson(json,
                pem("PRIVATE KEY", ec.getPrivate().getEncoded()), SecretEncoding.UTF8);
        assertEquals(PAYLOAD, viaEc.payload());
        assertEquals(1, viaEc.recipientIndex());
        assertEquals(2, viaEc.recipientCount());
        assertEquals("JWT", viaEc.effectiveHeader().get("typ"));
        assertEquals(PAYLOAD, JweComposer.decryptJson(json, pem("PRIVATE KEY", rsa.getPrivate().getEncoded()),
                SecretEncoding.UTF8).payload());

        assertThrows(IllegalArgumentException.class, () -> JweComposer.encrypt(PAYLOAD, "RSA-OAEP-256", "A256GCM",
                false, HeaderOptions.none(),
                new com.nimbusds.jose.jwk.JWKSet(java.util.List.of(rsaJwk,
                        new com.nimbusds.jose.jwk.ECKey.Builder(ecJwk).algorithm(null).build())).toString(),
                SecretEncoding.UTF8, 1000, JweComposer.Serialization.GENERAL, null));
    }

    @Test
    void compactRefusesAad() {
        assertThrows(IllegalArgumentException.class, () -> JweComposer.encrypt(PAYLOAD, "dir", "A128GCM", false,
                HeaderOptions.none(), "00112233445566778899aabbccddeeff", SecretEncoding.HEX, 1000,
                JweComposer.Serialization.COMPACT, "aad"));
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
