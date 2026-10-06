package com.cryptocarver.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;

class JoseDetachedHeadersTest {
    // RFC 7797 §4 key (RFC 7515 Appendix A.1); publicly documented laboratory vector.
    static final String RFC_KEY="{\"kty\":\"oct\",\"k\":\"AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow\"}";
    static final String RFC_HEADER="eyJhbGciOiJIUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19";
    static final String RFC_SIGNATURE="A5dxf2s96_n5FLueVuW1Z_vh161FwXZC4YLPff6dmDY";
    static JoseTestPki p;
    @BeforeAll static void setup() throws Exception {p=new JoseTestPki();}
    @Test void rfc7797Sections41And42AndJdkMacInterop() throws Exception {
        assertTrue(JOSEService.verifyDetachedJWS(RFC_HEADER+".."+RFC_SIGNATURE,"$.02","HS256",RFC_KEY));
        assertFalse(JOSEService.verifyDetachedJWS(RFC_HEADER+".."+RFC_SIGNATURE,"$.03","HS256",RFC_KEY));
        assertTrue(JOSEService.verifyDetachedJWS("eyJhbGciOiJIUzI1NiJ9..5mvfOroL-g7HyqJoozehmsaqmvTYGEq5jTI1gVvoEoQ","$.02","HS256",RFC_KEY));
        assertTrue(JOSEService.verifyDetachedJWS("{\"protected\":\""+RFC_HEADER+"\",\"signature\":\""+RFC_SIGNATURE+"\"}","$.02","HS256",RFC_KEY));
        for(String mode:List.of("Compact","Flattened JSON","General JSON")) {
            String token=JOSEService.generateDetachedJWS("$.02",List.of(rfcSigner()),mode,true,"{\"kid\":\"invented-rfc-73\"}");
            JWSObject object=object(token,"$.02");
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(OctetSequenceKey.parse(RFC_KEY).toByteArray(),"HmacSHA256"));
            assertArrayEquals(mac.doFinal(object.getSigningInput()),object.getSignature().decode());
            assertEquals(Set.of("b64"),object.getHeader().getCriticalParams());assertFalse(object.getHeader().isBase64URLEncodePayload());
            assertTrue(JOSEService.verifyDetachedJWS(token,"$.02","HS256",RFC_KEY));
        }
    }
    @Test void allEditableHeadersRoundTripAndAreIntegrityProtected() throws Exception {
        JWK pub=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)p.leafKey.getPublic()).keyID("invented-73").build();
        Map<String,Object> custom=new LinkedHashMap<>(p.header(p.chain()).toJSONObject());custom.remove("alg");
        custom.put("kid","invented-73");custom.put("typ","JOSE");custom.put("cty","text/plain");custom.put("jwk",pub.toJSONObject());custom.put("invented","header-73");
        String json=com.nimbusds.jose.util.JSONObjectUtils.toJSONString(custom);
        for(String mode:List.of("Compact","Flattened JSON","General JSON")) for(boolean unencoded:List.of(false,true)) {
            String token=JOSEService.generateDetachedJWS("invented . detached 73",List.of(new SignerConfig("RS256",p.privatePem())),mode,unencoded,json);
            JWSObject object=object(token,"invented . detached 73");
            for(var entry:custom.entrySet()) assertEquals(entry.getValue(),object.getHeader().toJSONObject().get(entry.getKey()),entry.getKey());
            assertTrue(JOSEService.verifyDetachedJWS(token,"invented . detached 73","RS256",new JWKSet(pub).toString()));
            assertFalse(JOSEService.verifyDetachedJWS(token,"changed payload","RS256",p.publicPem()));
            var changed=new JWSHeader.Builder(object.getHeader()).keyID("tampered").build();
            assertFalse(JOSEService.verifyDetachedJWS(changed.toBase64URL()+".."+object.getSignature(),"invented . detached 73","RS256",p.publicPem()));
            if(!mode.equals("Compact")) assertFalse(com.nimbusds.jose.util.JSONObjectUtils.parse(token).containsKey("payload"));
        }
    }
    @Test void normalAndDetachedShareReservedHeaderAndPublicJwkPolicy() throws Exception {
        for(String name:List.of("alg","b64","crit")) {
            String json="{\""+name+"\":null}";
            var signer=List.of(new SignerConfig("RS256",p.privatePem()));
            assertThrows(IllegalArgumentException.class,()->JOSEService.generateDetachedJWS("invented",signer,"Compact",true,json));
            assertThrows(IllegalArgumentException.class,()->JOSEService.generateSignedJWT("{\"sub\":\"invented\"}",signer,"Compact",false,json));
        }
        JWK privateKey=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)p.leafKey.getPublic()).privateKey(p.leafKey.getPrivate()).build();
        for(String key:List.of(privateKey.toJSONString(),RFC_KEY,"{\"kty\":\"invented-invalid\",\"d\":\"invented-secret\"}")) {
            String json="{\"jwk\":"+key+"}";
            assertThrows(IllegalArgumentException.class,()->JOSEService.generateDetachedJWS("invented",List.of(new SignerConfig("RS256",p.privatePem())),"Compact",false,json));
        }
    }
    @Test void generalJsonSelectsTheRequestedAlgorithmAsBefore() throws Exception {
        String token=JOSEService.generateDetachedJWS("invented 73",List.of(new SignerConfig("RS256",p.privatePem()),rfcSigner()),"General JSON",true,"{\"kid\":\"invented-multi-73\"}");
        assertTrue(JOSEService.verifyDetachedJWS(token,"invented 73","RS256",p.publicPem()));
        assertTrue(JOSEService.verifyDetachedJWS(token,"invented 73","HS256",RFC_KEY));
    }
    static SignerConfig rfcSigner() throws Exception {
        return new SignerConfig("HS256",java.util.Base64.getEncoder().encodeToString(OctetSequenceKey.parse(RFC_KEY).toByteArray()),JoseKeyMaterial.SecretEncoding.BASE64);
    }
    static JWSObject object(String token,String payload) throws Exception {
        if(!token.startsWith("{")) return JWSObject.parse(token,new Payload(payload));
        Map<String,Object> map=com.nimbusds.jose.util.JSONObjectUtils.parse(token);
        if(map.get("signatures") instanceof List<?> signatures) map=(Map<String,Object>)signatures.get(0);
        return new JWSObject(new Base64URL((String)map.get("protected")),new Payload(payload),new Base64URL((String)map.get("signature")));
    }
}
