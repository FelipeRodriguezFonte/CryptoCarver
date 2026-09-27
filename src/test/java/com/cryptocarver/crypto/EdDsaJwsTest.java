package com.cryptocarver.crypto;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdDsaJwsTest {

    /** RFC 8037 Appendix A.1 / A.4. */
    private static final String RFC8037_JWK = "{\"kty\":\"OKP\",\"crv\":\"Ed25519\","
            + "\"d\":\"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A\","
            + "\"x\":\"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo\"}";
    private static final String RFC8037_JWS = "eyJhbGciOiJFZERTQSJ9.RXhhbXBsZSBvZiBFZDI1NTE5IHNpZ25pbmc."
            + "hgyY0il_MGCjP0JzlnLWG1PPOt7-09PGcvMg3AIbQR6dWbhijcNR4ki4iylGjg5BhVsPt9g7sVvpAr_MuM0KAg";

    @Test
    void reproducesTheRfc8037Signature() throws Exception {
        assertEquals(RFC8037_JWS, JOSEService.signJws("Example of Ed25519 signing", "EdDSA", RFC8037_JWK));
        String publicJwk = OctetKeyPair.parse(RFC8037_JWK).toPublicJWK().toJSONString();
        assertEquals("Example of Ed25519 signing", JOSEService.verifyJws(RFC8037_JWS, "EdDSA", publicJwk));
    }

    @Test
    void derivesThePublicKeyOfTheRfcPrivateKey() throws Exception {
        String privateOnlyPem = pem("PRIVATE KEY", JoseKeyMaterial.privateKey(RFC8037_JWK).getEncoded());
        assertArrayEquals(OctetKeyPair.parse(RFC8037_JWK).getDecodedX(),
                JoseKeyMaterial.rawEdPublicKey(JoseKeyMaterial.publicKey(privateOnlyPem)));
    }

    @Test
    void signsAndValidatesJwtsWithPemKeysOnBothCurves() throws Exception {
        for (String curve : new String[] { "Ed25519", "Ed448" }) {
            KeyPair pair = KeyPairGenerator.getInstance(curve).generateKeyPair();
            String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
            String token = JOSEService.generateSignedJWT("{\"sub\":\"" + curve + "\"}",
                    List.of(new SignerConfig("EdDSA", privatePem)), "Compact", false);
            JwtValidator.Options options = new JwtValidator.Options(null, null, 60, true, false,
                    JoseKeyMaterial.SecretEncoding.UTF8);
            assertTrue(JwtValidator.validate(token, pem("PUBLIC KEY", pair.getPublic().getEncoded()), options,
                    Instant.now()).valid(), curve);
            assertTrue(JwtValidator.validate(token, privatePem, options, Instant.now()).signatureValid(),
                    "public half derived from the private key: " + curve);
        }
    }

    @Test
    void verifiesThroughAJwksAndRejectsTampering() throws Exception {
        OctetKeyPair jwk = new OctetKeyPair.Builder(OctetKeyPair.parse(RFC8037_JWK)).keyID("ed").build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("ed").build(),
                new JWTClaimsSet.Builder().subject("okp").build());
        jwt.sign(EdDsaJws.signer(JoseKeyMaterial.privateKey(RFC8037_JWK)));
        String jwks = new JWKSet(jwk.toPublicJWK()).toString();
        JwtValidator.Options options = new JwtValidator.Options(null, null, 60, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8);
        assertTrue(JwtValidator.validate(jwt.serialize(), jwks, options, Instant.now()).signatureValid());

        String[] parts = jwt.serialize().split("\\.");
        String tampered = parts[0] + "." + new Payload("{\"sub\":\"mallory\"}").toBase64URL() + "." + parts[2];
        assertFalse(JwtValidator.validate(tampered, jwks, options, Instant.now()).signatureValid());
    }

    @Test
    void refusesNonEdwardsKeys() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        String rsaPem = pem("PRIVATE KEY", rsa.generateKeyPair().getPrivate().getEncoded());
        assertThrows(IllegalArgumentException.class, () -> JOSEService.signJws("x", "EdDSA", rsaPem));
        JWSObject object = new JWSObject(new JWSHeader(JWSAlgorithm.EdDSA), new Payload("x"));
        object.sign(EdDsaJws.signer(JoseKeyMaterial.privateKey(RFC8037_JWK)));
        assertTrue(object.serialize().startsWith("eyJhbGciOiJFZERTQSJ9"));
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + java.util.Base64.getMimeEncoder().encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
