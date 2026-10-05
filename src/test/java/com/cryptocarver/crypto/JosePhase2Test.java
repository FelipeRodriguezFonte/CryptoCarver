package com.cryptocarver.crypto;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.*;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JosePhase2Test {
    @Test
    void useAndKeyOperationsArePreservedAndMismatchesAreAdvisory() throws Exception {
        OctetSequenceKey source = new OctetSequenceKey.Builder(new byte[32])
                .keyUse(KeyUse.ENCRYPTION).keyOperations(Set.of(KeyOperation.ENCRYPT)).build();
        String warning = JoseJwkPolicy.metadataWarning(source.toJSONString(), JoseJwkPolicy.Operation.SIGN);
        assertEquals("use=enc for sig operation; key_ops does not include sign", warning);
        JWK updated = JoseJwkPolicy.withMetadata(source, "sig", "sign, verify");
        assertEquals(KeyUse.SIGNATURE, updated.getKeyUse());
        assertEquals(Set.of(KeyOperation.SIGN, KeyOperation.VERIFY), updated.getKeyOperations());
        assertNull(JoseJwkPolicy.metadataWarning(updated.toJSONString(), JoseJwkPolicy.Operation.SIGN));
    }

    @Test
    void additionalProtectedHeadersAreGeneratedAndParsedWithoutNetworkLookup() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
        String token = JOSEService.generateSignedJWT("{\"sub\":\"phase2\"}",
                List.of(new SignerConfig("RS256", privatePem)), "Compact", false,
                "{\"kid\":\"invented-key\",\"jku\":\"https://invalid.example/jwks\",\"x5u\":\"https://invalid.example/cert\"}");
        JWSHeader header = com.nimbusds.jwt.SignedJWT.parse(token).getHeader();
        assertEquals("invented-key", header.getKeyID());
        assertEquals("https://invalid.example/jwks", header.getJWKURL().toString());
        assertEquals("https://invalid.example/cert", header.getX509CertURL().toString());
        assertTrue(com.nimbusds.jwt.SignedJWT.parse(token).verify(JOSEService.createVerifier(
                header.getAlgorithm(), publicPem, JoseKeyMaterial.SecretEncoding.UTF8)));
        assertThrows(IllegalArgumentException.class, () -> JOSEService.generateSignedJWT(
                "{\"sub\":\"phase2\"}", List.of(new SignerConfig("RS256", privatePem)),
                "Compact", false, "{\"alg\":\"none\"}"));
    }

    @Test
    void embeddedJwkIsIgnoredUnlessTheValidatorOptionIsExplicit() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
        RSAKey publicJwk = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) pair.getPublic()).build();
        String token = JOSEService.generateSignedJWT("{\"sub\":\"phase2\"}",
                List.of(new SignerConfig("RS256", privatePem)), "Compact", false,
                "{\"jwk\":" + publicJwk.toJSONString() + "}");
        JwtValidator.Options explicitExternalKey = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8);
        assertTrue(JwtValidator.validate(token, publicPem, explicitExternalKey, Instant.parse("2026-01-01T00:00:00Z")).valid());
        assertThrows(Exception.class, () -> JwtValidator.validate(token, "", explicitExternalKey,
                Instant.parse("2026-01-01T00:00:00Z")));
        JwtValidator.Options trustTokenKey = new JwtValidator.Options(null, null, 0, false, false,
                JoseKeyMaterial.SecretEncoding.UTF8, false, true);
        assertTrue(JwtValidator.validate(token, "", trustTokenKey, Instant.parse("2026-01-01T00:00:00Z")).valid());
    }

    @Test
    void x5tMismatchRejectsAnEmbeddedCertificateBeforeVerification() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
        var name = new org.bouncycastle.asn1.x500.X500Name("CN=CryptoCarver test");
        var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name,
                java.math.BigInteger.ONE, new java.util.Date(1_700_000_000_000L),
                new java.util.Date(1_900_000_000_000L), name, pair.getPublic());
        var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(pair.getPrivate());
        var certificate = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(signer));
        var encoded = com.nimbusds.jose.util.Base64.encode(certificate.getEncoded());
        JWSHeader validThumbprints = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                .x509CertChain(List.of(encoded))
                .x509CertThumbprint(new com.nimbusds.jose.util.Base64URL(java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(java.security.MessageDigest.getInstance("SHA-1").digest(certificate.getEncoded()))))
                .x509CertSHA256Thumbprint(new com.nimbusds.jose.util.Base64URL(java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()))))
                .build();
        assertTrue(JoseKeyMaterial.publicKey(JoseJwkPolicy.publicKeyMaterialFromHeader(validThumbprints))
                .getAlgorithm().equalsIgnoreCase("RSA"));
        JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                .x509CertChain(List.of(encoded))
                .x509CertThumbprint(new com.nimbusds.jose.util.Base64URL("AA"))
                .build();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> JoseJwkPolicy.publicKeyMaterialFromHeader(header));
        assertEquals("x5t does not match the first certificate in x5c.", failure.getMessage());
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n" + java.util.Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(encoded) + "\n-----END " + type + "-----";
    }
}
