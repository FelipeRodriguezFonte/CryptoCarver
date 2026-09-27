package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtValidatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final JwtValidator.Options LAX = new JwtValidator.Options(null, null, 60, true, false, SecretEncoding.UTF8);

    @Test
    void reportsEachFailedClaimWithoutTouchingThePayload() throws Exception {
        String token = hs256(new JWTClaimsSet.Builder().issuer("a").audience("x")
                .expirationTime(Date.from(NOW.minusSeconds(3600))).build(), null);
        JwtValidator.Result result = JwtValidator.validate(token, "secret",
                new JwtValidator.Options("b", "y", 60, true, false, SecretEncoding.UTF8), NOW);
        assertTrue(result.signatureValid());
        assertEquals(List.of("issuer", "audience", "expired"),
                result.findings().stream().map(JwtValidator.Finding::code).toList());
        assertFalse(result.payload().contains("VALIDATION"));
        assertFalse(result.valid());
    }

    @Test
    void clockSkewTolerance() throws Exception {
        String token = hs256(new JWTClaimsSet.Builder().expirationTime(Date.from(NOW.minusSeconds(30)))
                .notBeforeTime(Date.from(NOW.plusSeconds(30))).build(), null);
        assertTrue(JwtValidator.validate(token, "secret", LAX, NOW).valid());
        assertEquals(2, JwtValidator.validate(token, "secret",
                new JwtValidator.Options(null, null, 0, true, false, SecretEncoding.UTF8), NOW).findings().size());
    }

    @Test
    void oidcStrictRequiresCoreClaims() throws Exception {
        String token = hs256(new JWTClaimsSet.Builder().subject("s").build(), null);
        JwtValidator.Result result = JwtValidator.validate(token, "secret",
                new JwtValidator.Options(null, null, 60, false, true, SecretEncoding.UTF8), NOW);
        assertEquals(List.of("exp", "iat", "iss", "aud"),
                result.findings().stream().map(JwtValidator.Finding::argument).toList());
    }

    @Test
    void unknownCriticalHeaderIsAFinding() throws Exception {
        String token = hs256(new JWTClaimsSet.Builder().build(), Set.of("exp-policy"));
        assertEquals("unsupportedCrit", JwtValidator.validate(token, "secret", LAX, NOW).findings().get(0).code());
    }

    @Test
    void wrongSecretIsAnInvalidSignature() throws Exception {
        String token = hs256(new JWTClaimsSet.Builder().build(), null);
        assertFalse(JwtValidator.validate(token, "other", LAX, NOW).signatureValid());
    }

    @Test
    void verifiesWithJwksByKidAndWithASingleOctJwk() throws Exception {
        RSAKey signing = new RSAKeyGenerator(2048).keyID("k2").generate();
        RSAKey other = new RSAKeyGenerator(2048).keyID("k1").generate();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k2").build(),
                new JWTClaimsSet.Builder().subject("rsa").build());
        jwt.sign(new RSASSASigner(signing));
        String jwks = new JWKSet(List.of(other.toPublicJWK(), signing.toPublicJWK())).toString();
        assertTrue(JwtValidator.validate(jwt.serialize(), jwks, LAX, NOW).valid());

        String token = hs256(new JWTClaimsSet.Builder().build(), null);
        String octJwk = new OctetSequenceKey.Builder("secret".getBytes()).build().toJSONString();
        assertTrue(JwtValidator.validate(token, octJwk, LAX, NOW).signatureValid());
    }

    @Test
    void rsaPublicKeyCannotBeUsedAsAnHmacSecret() throws Exception {
        String token = hs256(new JWTClaimsSet.Builder().build(), null);
        String pem = "-----BEGIN PUBLIC KEY-----\n" + java.util.Base64.getMimeEncoder().encodeToString(
                new RSAKeyGenerator(2048).generate().toRSAPublicKey().getEncoded()) + "\n-----END PUBLIC KEY-----";
        assertThrows(IllegalArgumentException.class, () -> JwtValidator.validate(token, pem, LAX, NOW));
    }

    private static String hs256(JWTClaimsSet claims, Set<String> critical) throws Exception {
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT);
        if (critical != null) {
            header.criticalParams(critical);
            for (String name : critical) header.customParam(name, "x");
        }
        SignedJWT jwt = new SignedJWT(header.build(), claims);
        jwt.sign(new JOSEService.PromiscuousMACSigner("secret", JWSAlgorithm.HS256));
        return jwt.serialize();
    }
}
