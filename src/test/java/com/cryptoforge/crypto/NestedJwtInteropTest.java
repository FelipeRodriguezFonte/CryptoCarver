package com.cryptoforge.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NestedJwtInteropTest {

    @Test
    void testNestedJwtServiceFullCycle() throws Exception {
        String payloadJson = "{\"sub\":\"nested-subject\",\"iss\":\"test-issuer\"}";
        String signAlgoStr = "HS256";
        String signKey = "12345678901234567890123456789012";
        String keyAlgoStr = "dir";
        String contentAlgoStr = "A256GCM";
        String encKey = "abcdefghijklmnopqrstuvwxyz123456";

        String nestedToken = JOSEService.generateNestedJWT(payloadJson, signAlgoStr, signKey, keyAlgoStr, contentAlgoStr, encKey);

        String decryptedPayload = JOSEService.verifyNestedJWT(nestedToken, encKey, signKey);

        assertTrue(decryptedPayload.contains("nested-subject"));
    }

    @Test
    void testNestedJwtServiceWrongDecryptionKey() throws Exception {
        String payloadJson = "{\"sub\":\"nested-subject\"}";
        String signKey = "12345678901234567890123456789012";
        String encKey = "abcdefghijklmnopqrstuvwxyz123456";

        String nestedToken = JOSEService.generateNestedJWT(payloadJson, "HS256", signKey, "dir", "A256GCM", encKey);

        String wrongEncKey = "654321zyxwvutsrqponmlkjihgfedcba";
        assertThrows(Exception.class, () -> {
            JOSEService.verifyNestedJWT(nestedToken, wrongEncKey, signKey);
        });
    }

    @Test
    void testNestedJwtServiceWrongVerificationKey() throws Exception {
        String payloadJson = "{\"sub\":\"nested-subject\"}";
        String signKey = "12345678901234567890123456789012";
        String encKey = "abcdefghijklmnopqrstuvwxyz123456";

        String nestedToken = JOSEService.generateNestedJWT(payloadJson, "HS256", signKey, "dir", "A256GCM", encKey);

        String wrongSignKey = "09876543210987654321098765432109";
        assertThrows(JOSEException.class, () -> {
            JOSEService.verifyNestedJWT(nestedToken, encKey, wrongSignKey);
        });
    }

    @Test
    void testNestedJwtAlteredSignature() throws Exception {
        // 1. Manually create signed JWT
        byte[] signSecret = "12345678901234567890123456789012".getBytes();
        JWSSigner signer = new MACSigner(signSecret);
        SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), new JWTClaimsSet.Builder().subject("test").build());
        signedJWT.sign(signer);

        // Tamper with the signature portion (last part of token)
        String[] parts = signedJWT.serialize().split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "." + "invalid_signature";

        // 2. Encrypt the tampered JWT
        byte[] encSecret = "abcdefghijklmnopqrstuvwxyz123456".getBytes();
        JWEObject jweObject = new JWEObject(new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).contentType("JWT").build(), new Payload(tamperedToken));
        jweObject.encrypt(new DirectEncrypter(encSecret));

        String nestedToken = jweObject.serialize();

        // 3. Verify nested JWT using JOSEService
        Exception e = assertThrows(Exception.class, () -> {
            JOSEService.verifyNestedJWT(nestedToken, new String(encSecret), new String(signSecret));
        });
        // It could be a ParseException from Nimbus or JOSEException from our side
        assertTrue(e.getMessage().contains("Invalid signature") || e.getMessage().contains("decrypted, but inner signature verification failed") || e.getMessage().contains("Invalid JWS"));
    }
}
