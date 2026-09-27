package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACVerifier;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwsSecretEncodingTest {

    private static final byte[] KEY = HexFormat.of().parseHex(
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    private static final String HEX = HexFormat.of().formatHex(KEY);
    private static final String BASE64URL = Base64.getUrlEncoder().withoutPadding().encodeToString(KEY);

    @Test
    void hexSecretSignsWithTheDecodedBytes() throws Exception {
        String token = JOSEService.generateSignedJWT("{\"sub\":\"hex\"}",
                List.of(new SignerConfig("HS256", HEX, SecretEncoding.HEX)), "Compact", false);
        assertTrue(JWSObject.parse(token).verify(new MACVerifier(KEY)));
        assertTrue(JOSEService.createVerifier(JWSAlgorithm.HS256, BASE64URL, SecretEncoding.BASE64)
                .verify(JWSObject.parse(token).getHeader(), JWSObject.parse(token).getSigningInput(),
                        JWSObject.parse(token).getSignature()));
    }

    @Test
    void utf8RemainsTheDefault() throws Exception {
        String token = JOSEService.generateSignedJWT("{\"sub\":\"text\"}",
                List.of(new SignerConfig("HS256", HEX)), "Compact", false);
        assertFalse(JWSObject.parse(token).verify(new MACVerifier(KEY)));
        assertTrue(JWSObject.parse(token).verify(new MACVerifier(HEX.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test
    void detachedJwsVerifiesWithTheSelectedEncoding() throws Exception {
        String detached = JOSEService.generateDetachedJWS("external",
                List.of(new SignerConfig("HS512", HEX, SecretEncoding.HEX)), "Flattened JSON", false);
        assertTrue(JOSEService.verifyDetachedJWS(detached, "external", "HS512", BASE64URL, SecretEncoding.BASE64));
        assertFalse(JOSEService.verifyDetachedJWS(detached, "external", "HS512", HEX, SecretEncoding.UTF8));
    }

    @Test
    void nestedJwtDecodesSigningSecretAndDirectKey() throws Exception {
        String token = JOSEService.generateNestedJWT("{\"sub\":\"nested\"}", "HS256", HEX, "dir", "A256GCM",
                HEX, false, SecretEncoding.HEX);
        assertTrue(JOSEService.verifyNestedJWT(token, HEX, HEX, SecretEncoding.HEX).contains("nested"));
        assertThrows(Exception.class, () -> JOSEService.verifyNestedJWT(token, HEX, HEX, SecretEncoding.UTF8));
    }
}
