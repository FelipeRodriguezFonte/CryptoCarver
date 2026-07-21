package com.cryptocarver.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JwsJsonInteropTest {

    @Test
    void testJwsJsonGeneralMultiSignature() throws Exception {
        String payloadJson = "{\"sub\":\"test-subject\",\"iss\":\"test-issuer\"}";
        String secret1 = "12345678901234567890123456789012";
        String secret2 = "abcdefghijklmnopqrstuvwxyz123456";

        List<SignerConfig> signers = Arrays.asList(
                new SignerConfig("HS256", secret1),
                new SignerConfig("HS256", secret2)
        );

        String json = JOSEService.generateSignedJWT(payloadJson, signers, "General JSON", false);

        JWSObjectJSON parsedObject = JWSObjectJSON.parse(json);
        assertEquals(JWSObjectJSON.State.SIGNED, parsedObject.getState());
        assertEquals("test-subject", JWTClaimsSet.parse(parsedObject.getPayload().toJSONObject()).getSubject());

        assertEquals(2, parsedObject.getSignatures().size());

        assertTrue(parsedObject.getSignatures().get(0).verify(new MACVerifier(secret1)));
        assertTrue(parsedObject.getSignatures().get(1).verify(new MACVerifier(secret2)));
    }

    @Test
    void testJwsJsonDetachedUnencoded() throws Exception {
        String rawPayload = "{\"sub\":\"simple-payload\"}"; // Must be valid JSON for JOSEService to parse as JWTClaimsSet
        String secret = "12345678901234567890123456789012";

        List<SignerConfig> signers = Arrays.asList(new SignerConfig("HS256", secret));

        // b64=false means payload won't be base64url encoded. For JSON, we expect detached if it's not base64 encoded by nimbus directly,
        // but JOSEService puts the raw string or detached. Let's see how JOSEService did it:
        // json.put("payload", jwsObject.getPayload().toBase64URL().toString()) -- wait, in JOSEService we check:
        // if (!unencodedPayload) { json.put("payload", ... ); }
        // So payload is explicitly omitted, creating a detached signature.
        String json = JOSEService.generateSignedJWT(rawPayload, signers, "Flattened JSON", true);

        // Nimbus JWSObjectJSON.parse() throws on missing payload, so we parse the JSON manually
        // and construct a detached JWSObject to verify the signature.
        java.util.Map<String, Object> map = new com.google.gson.Gson().fromJson(json, java.util.Map.class);
        String protectedHeader = (String) map.get("protected");
        String signature = (String) map.get("signature");

        JWSObject parsedObject = new JWSObject(
                new com.nimbusds.jose.util.Base64URL(protectedHeader),
                new Payload(rawPayload),
                new com.nimbusds.jose.util.Base64URL(signature)
        );

        assertTrue(parsedObject.verify(new MACVerifier(secret)));
    }

    @Test
    void testJOSEServiceDetachedVerification() throws Exception {
        String rawPayload = "any arbitrary payload content, not even JSON!";
        String secret = "12345678901234567890123456789012";
        List<SignerConfig> signers = Arrays.asList(new SignerConfig("HS256", secret));

        // Generate detached via the new method
        String json = JOSEService.generateDetachedJWS(rawPayload, signers, "Flattened JSON", true);

        // Make sure it does not contain payload
        assertFalse(json.contains("\"payload\""));

        // Verify using the new verify method
        boolean valid = JOSEService.verifyDetachedJWS(json, rawPayload, "HS256", secret);
        assertTrue(valid);

        // Verify with wrong payload
        boolean invalid = JOSEService.verifyDetachedJWS(json, "wrong payload", "HS256", secret);
        assertFalse(invalid);
    }
}
