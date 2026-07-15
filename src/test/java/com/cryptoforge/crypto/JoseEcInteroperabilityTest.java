package com.cryptoforge.crypto;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JoseEcInteroperabilityTest {
    @Test
    void es256SignsSerializesParsesAndVerifies() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();

        JWSObject signed = new JWSObject(new JWSHeader(JWSAlgorithm.ES256), new Payload("CryptoCarver ES256 test"));
        signed.sign(new ECDSASigner((ECPrivateKey) pair.getPrivate()));
        JWSObject parsed = JWSObject.parse(signed.serialize());

        assertTrue(parsed.verify(new ECDSAVerifier((ECPublicKey) pair.getPublic())));
    }

    @Test
    void es256DetachedJwsVerifiesOnlyWithExternalPayload() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        Payload payload = new Payload("detached-payload");
        JWSObject signed = new JWSObject(new JWSHeader(JWSAlgorithm.ES256), payload);
        signed.sign(new ECDSASigner((ECPrivateKey) pair.getPrivate()));

        String compactDetached = signed.serialize(true);
        assertTrue(compactDetached.split("\\.", -1)[1].isEmpty());
        assertTrue(JWSObject.parse(compactDetached, payload).verify(new ECDSAVerifier((ECPublicKey) pair.getPublic())));
    }
}
