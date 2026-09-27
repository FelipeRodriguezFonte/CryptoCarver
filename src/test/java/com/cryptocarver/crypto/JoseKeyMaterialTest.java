package com.cryptocarver.crypto;

import com.cryptocarver.crypto.JoseKeyMaterial.SecretEncoding;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.Curve;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoseKeyMaterialTest {

    private static KeyPair rsa;
    private static KeyPair ec;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        rsa = rsaGen.generateKeyPair();
        KeyPairGenerator ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        ec = ecGen.generateKeyPair();
    }

    @Test
    void readsPkcs8AndSubjectPublicKeyInfoPem() throws Exception {
        assertEquals(rsa.getPrivate(), JoseKeyMaterial.rsaPrivateKey(pem("PRIVATE KEY", rsa.getPrivate().getEncoded())));
        assertEquals(rsa.getPublic(), JoseKeyMaterial.rsaPublicKey(pem("PUBLIC KEY", rsa.getPublic().getEncoded())));
        assertEquals(ec.getPublic(), JoseKeyMaterial.ecPublicKey(pem("PUBLIC KEY", ec.getPublic().getEncoded())));
    }

    @Test
    void readsTraditionalPkcs1AndSec1Pem() throws Exception {
        String pkcs1Private = writePem(rsa.getPrivate());
        assertTrue(pkcs1Private.contains("BEGIN RSA PRIVATE KEY"));
        assertEquals(((java.security.interfaces.RSAPrivateKey) rsa.getPrivate()).getPrivateExponent(),
                JoseKeyMaterial.rsaPrivateKey(pkcs1Private).getPrivateExponent());

        RSAPublicKey pub = (RSAPublicKey) rsa.getPublic();
        String pkcs1Public = pem("RSA PUBLIC KEY",
                new org.bouncycastle.asn1.pkcs.RSAPublicKey(pub.getModulus(), pub.getPublicExponent()).getEncoded());
        assertEquals(pub.getModulus(), JoseKeyMaterial.rsaPublicKey(pkcs1Public).getModulus());

        // OpenSSL-style SEC1: the curve OID travels inside the ECPrivateKey structure.
        java.security.interfaces.ECPrivateKey ecPrivate = (java.security.interfaces.ECPrivateKey) ec.getPrivate();
        String sec1 = pem("EC PRIVATE KEY", new org.bouncycastle.asn1.sec.ECPrivateKey(256, ecPrivate.getS(),
                new org.bouncycastle.asn1.x9.X962Parameters(org.bouncycastle.asn1.sec.SECObjectIdentifiers.secp256r1))
                .getEncoded());
        assertEquals(((java.security.interfaces.ECPrivateKey) ec.getPrivate()).getS(),
                JoseKeyMaterial.ecPrivateKey(sec1).getS());
    }

    @Test
    void readsCertificatePublicKey() throws Exception {
        X500Name name = new X500Name("CN=JOSE test");
        var holder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE, new Date(),
                new Date(System.currentTimeMillis() + 86_400_000L), name, rsa.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(rsa.getPrivate()));
        String certificate = writePem(new JcaX509CertificateConverter().getCertificate(holder));
        assertEquals(rsa.getPublic(), JoseKeyMaterial.rsaPublicKey(certificate));
    }

    @Test
    void readsJwkAndSingleKeyJwks() throws Exception {
        RSAKey rsaJwk = new RSAKey.Builder((RSAPublicKey) rsa.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) rsa.getPrivate()).build();
        assertEquals(rsa.getPublic(), JoseKeyMaterial.rsaPublicKey(rsaJwk.toPublicJWK().toJSONString()));
        assertEquals(((java.security.interfaces.RSAPrivateKey) rsa.getPrivate()).getPrivateExponent(),
                JoseKeyMaterial.rsaPrivateKey(rsaJwk.toJSONString()).getPrivateExponent());

        ECKey ecJwk = new ECKey.Builder(Curve.P_256, (java.security.interfaces.ECPublicKey) ec.getPublic()).build();
        assertEquals(ec.getPublic(), JoseKeyMaterial.ecPublicKey(new JWKSet(ecJwk).toString()));
        assertThrows(IllegalArgumentException.class,
                () -> JoseKeyMaterial.publicKey(new JWKSet(java.util.List.of(ecJwk, rsaJwk.toPublicJWK())).toString()));
    }

    @Test
    void derivesPublicKeyFromPrivateKey() throws Exception {
        assertEquals(rsa.getPublic(), JoseKeyMaterial.publicKey(pem("PRIVATE KEY", rsa.getPrivate().getEncoded())));
        assertEquals(ec.getPublic(), JoseKeyMaterial.publicKey(pem("PRIVATE KEY", ec.getPrivate().getEncoded())));
    }

    @Test
    void readsBareBase64Der() throws Exception {
        assertEquals(rsa.getPublic(), JoseKeyMaterial.publicKey(Base64.getEncoder().encodeToString(rsa.getPublic().getEncoded())));
    }

    @Test
    void rejectsPublicOnlyMaterialWhenPrivateIsNeeded() {
        assertThrows(IllegalArgumentException.class,
                () -> JoseKeyMaterial.privateKey(pem("PUBLIC KEY", rsa.getPublic().getEncoded())));
        assertThrows(IllegalArgumentException.class,
                () -> JoseKeyMaterial.ecPublicKey(pem("PUBLIC KEY", rsa.getPublic().getEncoded())));
    }

    @Test
    void decodesSecretsWithTheSelectedEncoding() {
        byte[] expected = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, JoseKeyMaterial.secret("0123456789abcdef", SecretEncoding.UTF8));
        assertArrayEquals(expected, JoseKeyMaterial.secret("30313233 34353637 3839616263646566", SecretEncoding.HEX));
        assertArrayEquals(expected, JoseKeyMaterial.secret("MDEyMzQ1Njc4OWFiY2RlZg==", SecretEncoding.BASE64));
        assertArrayEquals(expected, JoseKeyMaterial.secret("MDEyMzQ1Njc4OWFiY2RlZg", SecretEncoding.BASE64));
        assertThrows(IllegalArgumentException.class, () -> JoseKeyMaterial.secret("zz", SecretEncoding.HEX));
        assertThrows(IllegalArgumentException.class,
                () -> JoseKeyMaterial.secret("-----BEGIN PUBLIC KEY-----", SecretEncoding.UTF8));
        assertEquals(SecretEncoding.UTF8, SecretEncoding.fromLabel(null));
        assertEquals(SecretEncoding.HEX, SecretEncoding.fromLabel("Hex"));
    }

    private static String pem(String type, byte[] der) throws Exception {
        StringWriter out = new StringWriter();
        try (org.bouncycastle.util.io.pem.PemWriter writer = new org.bouncycastle.util.io.pem.PemWriter(out)) {
            writer.writeObject(new PemObject(type, der));
        }
        return out.toString();
    }

    private static String writePem(Object object) throws Exception {
        StringWriter out = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(out)) {
            writer.writeObject(object);
        }
        return out.toString();
    }
}
