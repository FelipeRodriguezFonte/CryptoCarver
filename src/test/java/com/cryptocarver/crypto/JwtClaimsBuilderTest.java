package com.cryptocarver.crypto;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtClaimsBuilderTest {

    @Test
    void mergesIntoTheExistingPayloadAndKeepsOtherClaims() throws Exception {
        String json = JwtClaimsBuilder.apply("{\"scope\":\"read\",\"iss\":\"old\",\"n\":1516239022}",
                "auth.server", "user_1", "", 2, 1_000_000L);
        JWTClaimsSet claims = JWTClaimsSet.parse(json);
        assertEquals("read", claims.getStringClaim("scope"));
        assertEquals("auth.server", claims.getIssuer());
        assertEquals("user_1", claims.getSubject());
        assertTrue(claims.getAudience().isEmpty(), "blank aud is omitted");
        assertEquals(1_000_000L, claims.getIssueTime().getTime() / 1000);
        assertEquals(1_007_200L, claims.getExpirationTime().getTime() / 1000);
        assertTrue(json.contains("1516239022"), "integer claims stay integers");
    }

    @Test
    void quotesInFieldsProduceValidJson() throws Exception {
        String json = JwtClaimsBuilder.apply("", "a\"b", "<sub>", "api", 1, 10L);
        JWTClaimsSet claims = JWTClaimsSet.parse(json);
        assertEquals("a\"b", claims.getIssuer());
        assertEquals("<sub>", claims.getSubject());
        assertFalse(json.contains("\\u003c"));
    }

    @Test
    void refusesToOverwriteAPayloadThatIsNotJson() {
        assertThrows(IllegalArgumentException.class,
                () -> JwtClaimsBuilder.apply("not json", "iss", null, null, 1, 10L));
    }
}
