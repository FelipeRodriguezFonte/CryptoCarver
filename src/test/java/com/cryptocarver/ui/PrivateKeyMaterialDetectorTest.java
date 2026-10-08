package com.cryptocarver.ui;

import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PrivateKeyMaterialDetectorTest {
    private static final String PRIVATE_JWK = "{\"kty\":\"EC\",\"d\":\"invented-wallet-79\"}";
    private static final String PUBLIC_JWK = "{\"kty\":\"EC\",\"x\":\"invented-x\",\"y\":\"invented-y\"}";

    @Test void arbitraryAndMalformedTextNeverThrows() {
        String[] inputs = { "", "plain arbitrary text", "not JSON {", "{", "{broken JSON", "ey%%%",
                "ey%%%..%%%", "%%%..%%%", "a.b.c.d.e", "{\"kty\":\"EC\",\"d\":broken}",
                "x".repeat(2_000_000), "ey" + "%".repeat(500_000), ".".repeat(500_000),
                "{\"nested\":" + "[".repeat(20_000) + "0" + "]".repeat(20_000) + "}" };
        for (int i = 0; i < inputs.length; i++) {
            String input = inputs[i];
            assertDoesNotThrow(() -> PrivateKeyMaterialDetector.containsPrivateMaterial(input, 0), "fixture " + i);
        }
        assertDoesNotThrow(() -> PrivateKeyMaterialDetector.containsPrivateMaterial(null, 0));
    }

    @Test void privateAndPublicDetectionKeepsItsExistingMeaning() {
        assertTrue(detect("-----BEGIN PRIVATE KEY-----\ninvented\n-----END PRIVATE KEY-----"));
        assertTrue(detect(PRIVATE_JWK));
        assertTrue(detect("{\"kty\":\"oct\",\"k\":\"invented-wallet-79\"}"));
        assertTrue(detect("{\"kty\":\"EC\",\"d\":broken}"));
        assertTrue(detect(Map.of("nested", List.of(Map.of("kty", "EC", "d", "invented")))));
        assertTrue(detect(Base64URL.encode("{\"alg\":\"ES256\"}") + "." + Base64URL.encode(PRIVATE_JWK) + ".AA"));
        assertTrue(detect(Base64URL.encode(PRIVATE_JWK).toString()));
        assertFalse(detect(PUBLIC_JWK));
        assertFalse(detect("arbitrary text"));
        assertFalse(detect("{\"d\":\"ordinary-data-without-kty\"}"));
        assertFalse(PrivateKeyMaterialDetector.containsPrivateMaterial(PRIVATE_JWK, 13));
    }

    private static boolean detect(Object value) {
        return PrivateKeyMaterialDetector.containsPrivateMaterial(value, 0);
    }
}
