package com.cryptocarver.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtValidatorPhase3Test {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static KeyPair rsa;
    private static String publicPem;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        rsa = generator.generateKeyPair();
        publicPem = pem("PUBLIC KEY", rsa.getPublic().getEncoded());
    }

    @Test
    void rfc9068OidcAndConfirmationClaimsAreReportedPerCheck() throws Exception {
        String accessToken = "invented-access-token";
        String authorizationCode = "invented-code";
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("https://issuer.example")
                .subject("user-7").audience("api://example").expirationTime(Date.from(NOW.plusSeconds(300)))
                .issueTime(Date.from(NOW.minusSeconds(5))).jwtID("jti-7").claim("client_id", "client-9")
                .claim("nonce", "nonce-7").claim("at_hash", hashHalf("SHA-256", accessToken))
                .claim("c_hash", hashHalf("SHA-256", authorizationCode))
                .claim("cnf", Map.of("jkt", "jkt-7", "x5t#S256", "cert-7")).build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("at+jwt"))
                .contentType("application/example").build();
        String token = sign(header, claims, rsa);
        JwtValidator.Advanced advanced = new JwtValidator.Advanced("RS256", "at+jwt", "application/example",
                true, "nonce-7", accessToken, authorizationCode, "jkt-7", "cert-7", Set.of("b64"), false);
        JwtValidator.Options options = new JwtValidator.Options(null, "api://example", 30, true, false,
                JoseKeyMaterial.SecretEncoding.UTF8, false, false, advanced);
        JwtValidator.Result result = JwtValidator.validate(token, publicPem, options, NOW);
        assertTrue(result.signatureValid());
        assertTrue(result.findings().isEmpty(), result.findings().toString());
    }

    @Test
    void algorithmAllowlistAndAccessTokenProfileFailuresAreIndependentFindings() throws Exception {
        JWTClaimsSet sparse = new JWTClaimsSet.Builder().issuer("issuer").subject("subject").build();
        String token = sign(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("JWT")).build(), sparse, rsa);
        JwtValidator.Advanced advanced = new JwtValidator.Advanced("ES256", "at+jwt", null,
                true, null, null, null, null, null, Set.of("b64"), false);
        JwtValidator.Options options = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8, false, false, advanced);
        JwtValidator.Result result = JwtValidator.validate(token, publicPem, options, NOW);
        assertFalse(result.signatureValid());
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("algorithmNotAllowed")));
        for (String required : List.of("exp", "aud", "client_id", "iat", "jti")) {
            assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("missing") && f.argument().equals(required)), required);
        }
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("type")));
    }

    @Test
    void algorithmAllowlistAlsoAppliesToOptedInUnsecuredJwt() throws Exception {
        String token = JoseNoneJws.compact("{\"sub\":\"invented\"}");
        JwtValidator.Advanced advanced = new JwtValidator.Advanced("RS256", null, null, false,
                null, null, null, null, null, Set.of("b64"), false);
        JwtValidator.Options options = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8, true, false, advanced);
        JwtValidator.Result result = JwtValidator.validate(token, null, options, NOW);
        assertFalse(result.signatureValid());
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals("algorithmNotAllowed")));
    }

    @Test
    void rsaPublicKeyUsedAsHmacSecretProducesAlgorithmConfusionWarning() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("invented").build();
        SignedJWT hmac = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        hmac.sign(new MACSigner(publicPem.getBytes(StandardCharsets.UTF_8)));
        JwtValidator.Options options = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8, false, false,
                new JwtValidator.Advanced("HS256", null, null, false, null, null, null,
                        null, null, Set.of("b64"), false));
        JwtValidator.Result result = JwtValidator.validate(hmac.serialize(), publicPem, options, NOW);
        assertFalse(result.signatureValid());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.code().equals("algorithmConfusion")));
    }

    @Test
    void unknownCriticalHeaderCanBeRejectedOrExplicitlyIgnoredWithWarning() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("invented").build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).criticalParams(Set.of("exp")).customParam("exp", true).build();
        String token = sign(header, claims, rsa);
        JwtValidator.Options reject = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8, false, false,
                new JwtValidator.Advanced("RS256", null, null, false, null, null, null,
                        null, null, Set.of("b64"), false));
        JwtValidator.Result rejected = JwtValidator.validate(token, publicPem, reject, NOW);
        assertTrue(rejected.findings().stream().anyMatch(f -> f.code().equals("unsupportedCrit")));
        JwtValidator.Options ignore = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8, false, false,
                new JwtValidator.Advanced("RS256", null, null, false, null, null, null,
                        null, null, Set.of("b64"), true));
        JwtValidator.Result accepted = JwtValidator.validate(token, publicPem, ignore, NOW);
        assertTrue(accepted.signatureValid());
        assertTrue(accepted.warnings().stream().anyMatch(warning -> warning.code().equals("ignoredCrit")));
    }

    private static String sign(JWSHeader header, JWTClaimsSet claims, KeyPair pair) throws Exception {
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(pair.getPrivate()));
        return jwt.serialize();
    }

    private static String hashHalf(String algorithm, String value) throws Exception {
        byte[] digest = MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(digest, digest.length / 2));
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(encoded) + "\n-----END " + type + "-----";
    }
}
