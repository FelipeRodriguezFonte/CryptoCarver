package com.cryptocarver.ui;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JOSEControllerJwkConversionTest {

    @Test
    void ecPemBecomesAnEcJwkWithItsCurve() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair pair = generator.generateKeyPair();

        JWK publicJwk = JOSEController.asymmetricJwk(pem("PUBLIC KEY", pair.getPublic().getEncoded()), "EC", "k1");
        assertEquals(Curve.P_384, ((ECKey) publicJwk).getCurve());
        assertEquals("k1", publicJwk.getKeyID());
        assertFalse(publicJwk.isPrivate());

        JWK privateJwk = JOSEController.asymmetricJwk(pem("PRIVATE KEY", pair.getPrivate().getEncoded()), "EC", null);
        assertTrue(privateJwk.isPrivate());
        assertEquals(new ECKey.Builder(Curve.P_384, (ECPublicKey) pair.getPublic()).build().computeThumbprint(),
                privateJwk.computeThumbprint());
    }

    @Test
    void rsaPemKeepsThePrivateHalfAndRejectsAMismatchedType() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());

        JWK jwk = JOSEController.asymmetricJwk(privatePem, "RSA", null);
        assertTrue(jwk.isPrivate());
        assertEquals(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).build().computeThumbprint(),
                jwk.computeThumbprint());
        assertThrows(IllegalArgumentException.class, () -> JOSEController.asymmetricJwk(privatePem, "EC", null));
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
