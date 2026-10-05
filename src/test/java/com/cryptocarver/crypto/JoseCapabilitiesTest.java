package com.cryptocarver.crypto;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.JWEObject;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JoseCapabilitiesTest {

    @Test
    void es256kSignsAndVerifies() throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec("secp256k1"));
        KeyPair pair = generator.generateKeyPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());

        String token = JOSEService.signJws("ES256K vector payload", "ES256K", privatePem);
        assertEquals("ES256K vector payload", JOSEService.verifyJws(token, "ES256K", publicPem));

        com.nimbusds.jose.JWSObject nimbusToken = new com.nimbusds.jose.JWSObject(
                new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.ES256K),
                new com.nimbusds.jose.Payload("Nimbus interoperability"));
        com.nimbusds.jose.crypto.ECDSASigner nimbusSigner = new com.nimbusds.jose.crypto.ECDSASigner(
                (java.security.interfaces.ECPrivateKey) pair.getPrivate());
        nimbusSigner.getJCAContext().setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        nimbusToken.sign(nimbusSigner);
        assertEquals("Nimbus interoperability", JOSEService.verifyJws(nimbusToken.serialize(), "ES256K", publicPem));

        com.nimbusds.jose.jwk.ECKey jwk = (com.nimbusds.jose.jwk.ECKey)
                new com.cryptocarver.ui.JOSEController().generateNewJWK("ES256K", "sig");
        String jwkToken = JOSEService.signJws("generated secp256k1", "ES256K", jwk.toJSONString());
        assertEquals("generated secp256k1", JOSEService.verifyJws(jwkToken, "ES256K", jwk.toPublicJWK().toJSONString()));
        assertEquals("EC", JoseKeyMaterial.privateKey(jwk.toJSONString()).getAlgorithm());
    }

    @Test
    void x25519AndX448OkpJwksImportAsJdkKeyPairs() throws Exception {
        OctetKeyPair generatedX25519 = (OctetKeyPair) new com.cryptocarver.ui.JOSEController()
                .generateNewJWK("ECDH-ES", "enc");
        OctetKeyPair generatedX448 = (OctetKeyPair) new com.cryptocarver.ui.JOSEController()
                .generateNewJWK("ECDH-ES-X448", "enc");
        assertEquals(Curve.X25519, generatedX25519.getCurve());
        assertEquals(Curve.X448, generatedX448.getCurve());
        for (var namedCurve : new Object[][] {
                { Curve.X25519, NamedParameterSpec.X25519 }, { Curve.X448, NamedParameterSpec.X448 } }) {
            Curve curve = (Curve) namedCurve[0];
            NamedParameterSpec params = (NamedParameterSpec) namedCurve[1];
            KeyPairGenerator generator = KeyPairGenerator.getInstance(params.getName());
            generator.initialize(params);
            KeyPair pair = generator.generateKeyPair();
            XECPublicKey publicKey = (XECPublicKey) pair.getPublic();
            byte[] x = littleEndian(publicKey.getU(), curve == Curve.X25519 ? 32 : 56);
            byte[] d = ((XECPrivateKey) pair.getPrivate()).getScalar().orElseThrow();
            OctetKeyPair jwk = new OctetKeyPair.Builder(curve,
                    com.nimbusds.jose.util.Base64URL.encode(x))
                    .d(com.nimbusds.jose.util.Base64URL.encode(d)).build();
            assertTrue(JoseKeyMaterial.privateKey(jwk.toJSONString()).getAlgorithm().contains("X"), curve.toString());
            assertTrue(JoseKeyMaterial.publicKey(jwk.toPublicJWK().toJSONString()).getAlgorithm().contains("X"), curve.toString());
            assertEquals(jwk.getX(), com.nimbusds.jose.util.Base64URL.encode(
                    JoseKeyMaterial.rawXPublicKey(JoseKeyMaterial.publicKey(jwk.toJSONString()))));
            assertEquals(jwk.getX(), com.nimbusds.jose.util.Base64URL.encode(
                    JoseKeyMaterial.rawXPublicKey(JoseKeyMaterial.publicKey(pem("PRIVATE KEY",
                            JoseKeyMaterial.privateKey(jwk.toJSONString()).getEncoded())))));
        }
    }

    @Test
    void x25519AndX448SharedSecretsMatchRfc8037AppendixVectors() throws Exception {
        String x25519Secret = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
        String x25519Target = "3p7bfXt9wbTTW2HC7OQ1Nz-DQ8hbeGdNrfx-FG-IK08";
        OctetKeyPair x25519Ephemeral = new OctetKeyPair.Builder(Curve.X25519,
                new com.nimbusds.jose.util.Base64URL("hSDwCYkwp1R0i33ctD73Wg2_Og0mOBr066SpjqqbTmo"))
                .d(com.nimbusds.jose.util.Base64URL.encode(java.util.HexFormat.of().parseHex(x25519Secret))).build();
        OctetKeyPair x25519Bob = new OctetKeyPair.Builder(Curve.X25519,
                new com.nimbusds.jose.util.Base64URL(x25519Target)).build();
        assertEquals("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742",
                java.util.HexFormat.of().formatHex(JoseXdhJwe.agree(
                        JoseKeyMaterial.privateKey(x25519Ephemeral.toJSONString()),
                        JoseKeyMaterial.publicKey(x25519Bob.toJSONString()))));

        String x448Secret = "9a8f4925d1519f5775cf46b04b5800d4ee9ee8bae8bc5565d498c28dd9c9baf574a9419744897391006382a6f127ab1d9ac2d8c0a598726b";
        String x448Target = "PreoKbDNIPW8_AtZm2_sz22kYnEHvbDU80W0MCfYuXL8PjT7QjKhPKcG3LV67D2uB73BxnvzNgk";
        OctetKeyPair x448Ephemeral = new OctetKeyPair.Builder(Curve.X448,
                new com.nimbusds.jose.util.Base64URL("mwj3zDG34-Z9ItWuoSEHSic70rg94Jxj-qc9LCLF2bvINmRyQdlT1AxbEtqIEg1TF3-A5TLEH6A"))
                .d(com.nimbusds.jose.util.Base64URL.encode(java.util.HexFormat.of().parseHex(x448Secret))).build();
        OctetKeyPair x448Dave = new OctetKeyPair.Builder(Curve.X448,
                new com.nimbusds.jose.util.Base64URL(x448Target)).build();
        assertEquals("07fff4181ac6cc95ec1c16a94a0f74d12da232ce40a77552281d282bb60c0b56fd2464c335543936521c24403085d59a449a5037514a879d",
                java.util.HexFormat.of().formatHex(JoseXdhJwe.agree(
                        JoseKeyMaterial.privateKey(x448Ephemeral.toJSONString()),
                        JoseKeyMaterial.publicKey(x448Dave.toJSONString()))));
    }

    @Test
    void x25519AndX448EcdhEsRoundTripForDirectAndAesKwModes() throws Exception {
        for (String curve : new String[] { "X25519", "X448" }) {
            KeyPair pair = KeyPairGenerator.getInstance(curve).generateKeyPair();
            String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
            String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
            for (String algorithm : new String[] { "ECDH-ES", "ECDH-ES+A128KW", "ECDH-ES+A192KW", "ECDH-ES+A256KW" }) {
                String token = JweComposer.encrypt("XDH JOSE payload", algorithm, "A128GCM", false,
                        JweComposer.HeaderOptions.none(), publicPem, JoseKeyMaterial.SecretEncoding.UTF8, 1_000);
                JWEObject jwe = JWEObject.parse(token);
                JweComposer.LoadedKey loaded = new JweComposer.LoadedKey();
                jwe.decrypt(JweComposer.decrypter(jwe.getHeader().getAlgorithm(), privatePem,
                        JoseKeyMaterial.SecretEncoding.UTF8, loaded));
                assertEquals("XDH JOSE payload", jwe.getPayload().toString(), curve + " / " + algorithm);
                assertEquals(curve, ((OctetKeyPair) jwe.getHeader().getEphemeralPublicKey()).getCurve().getName());
            }
        }
    }

    @Test
    void noneJwsGeneratesAndValidatorRequiresExplicitAcceptance() throws Exception {
        String compactJws = JOSEService.signJws("unsigned payload", "none", "unused");
        assertTrue(compactJws.endsWith("."));
        assertEquals("unsigned payload", JOSEService.verifyJws(compactJws, "none", "unused"));
        String token = JOSEService.generateSignedJWT("{\"sub\":\"invented\"}",
                java.util.List.of(new SignerConfig("none", "unused")), "Compact", false);

        JwtValidator.Options defaults = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8);
        assertFalse(JwtValidator.validate(token, "unused", defaults, java.time.Instant.EPOCH).valid());
        JwtValidator.Options explicit = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8, true);
        assertTrue(JwtValidator.validate(token, "unused", explicit, java.time.Instant.EPOCH).valid());
        for (String serialization : new String[] { "Flattened JSON", "General JSON" }) {
            String json = JOSEService.generateSignedJWT("{\"sub\":\"invented\"}",
                    java.util.List.of(new SignerConfig("none", "unused")), serialization, false);
            assertTrue(json.contains("\"signature\":\"\""), serialization);
        }
        for (String serialization : new String[] { "Compact", "Flattened JSON", "General JSON" }) {
            String detached = JOSEService.generateDetachedJWS("detached", java.util.List.of(new SignerConfig("none", "unused")),
                    serialization, false);
            assertTrue(JOSEService.verifyDetachedJWS(detached, "detached", "none", "unused"), serialization);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    private static byte[] littleEndian(java.math.BigInteger value, int length) {
        byte[] big = value.toByteArray();
        if (big[0] == 0) big = Arrays.copyOfRange(big, 1, big.length);
        byte[] little = new byte[length];
        for (int i = 0; i < Math.min(big.length, length); i++) little[i] = big[big.length - 1 - i];
        return little;
    }
}
