package com.cryptocarver.crypto;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWECryptoParts;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.crypto.impl.ECDHCryptoProvider;
import com.nimbusds.jose.crypto.impl.AAD;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.util.Set;

/** ECDH-ES JWE over JDK X25519 / X448 providers (RFC 8037, §6.2 RFC 7518). */
public final class JoseXdhJwe extends ECDHCryptoProvider implements JWEEncrypter, JWEDecrypter {
    private final Curve curve;
    private final PublicKey recipientPublicKey;
    private final PrivateKey recipientPrivateKey;

    private JoseXdhJwe(Curve curve, PublicKey publicKey, PrivateKey privateKey) throws JOSEException {
        super(curve, null);
        this.curve = curve;
        this.recipientPublicKey = publicKey;
        this.recipientPrivateKey = privateKey;
    }

    public static JoseXdhJwe encrypter(PublicKey key) throws JOSEException {
        return new JoseXdhJwe(curve(key), key, null);
    }

    public static JoseXdhJwe decrypter(PrivateKey key) throws JOSEException {
        return new JoseXdhJwe(curve(key), null, key);
    }

    @Override
    public Set<Curve> supportedEllipticCurves() {
        return Set.of(Curve.X25519, Curve.X448);
    }

    @Override
    public JWECryptoParts encrypt(JWEHeader header, byte[] clearText, byte[] aad) throws JOSEException {
        if (recipientPublicKey == null) throw new JOSEException("XDH public key is not configured.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(curve.getName());
            generator.initialize(new NamedParameterSpec(curve.getName()));
            KeyPair ephemeral = generator.generateKeyPair();
            OctetKeyPair epk = new OctetKeyPair.Builder(curve,
                    Base64URL.encode(JoseKeyMaterial.rawXPublicKey(ephemeral.getPublic()))).build();
            JWEHeader effectiveHeader = new JWEHeader.Builder(header).ephemeralPublicKey(epk).build();
            byte[] shared = agree(ephemeral.getPrivate(), recipientPublicKey);
            SecretKey z = new SecretKeySpec(shared, "AES");
            byte[] contentAad = java.util.Arrays.equals(AAD.compute(header), aad)
                    ? AAD.compute(effectiveHeader) : aad;
            return encryptWithZ(effectiveHeader, z, clearText, contentAad);
        } catch (JOSEException e) {
            throw e;
        } catch (Exception e) {
            throw new JOSEException("XDH ECDH-ES encryption failed.", e);
        }
    }

    @Override
    public byte[] decrypt(JWEHeader header, Base64URL encryptedKey, Base64URL iv,
            Base64URL cipherText, Base64URL authTag, byte[] aad) throws JOSEException {
        if (recipientPrivateKey == null) throw new JOSEException("XDH private key is not configured.");
        try {
            if (!(header.getEphemeralPublicKey() instanceof OctetKeyPair epk)
                    || !curve.equals(epk.getCurve())) {
                throw new JOSEException("The JWE epk must be an XDH key on the recipient curve.");
            }
            PublicKey ephemeralPublic = JoseKeyMaterial.publicKey(epk.toJSONString());
            SecretKey z = new SecretKeySpec(agree(recipientPrivateKey, ephemeralPublic), "AES");
            return decryptWithZ(header, aad, z, encryptedKey, iv, cipherText, authTag);
        } catch (JOSEException e) {
            throw e;
        } catch (Exception e) {
            throw new JOSEException("XDH ECDH-ES decryption failed.", e);
        }
    }

    static byte[] agree(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        if (!(publicKey instanceof XECPublicKey)) throw new IllegalArgumentException("An XDH public key is required.");
        String curve = JoseKeyMaterial.xdhCurveName(privateKey);
        KeyAgreement agreement = KeyAgreement.getInstance(curve);
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private static Curve curve(java.security.Key key) throws JOSEException {
        String curve = JoseKeyMaterial.xdhCurveName(key);
        if ("X25519".equalsIgnoreCase(curve)) return Curve.X25519;
        if ("X448".equalsIgnoreCase(curve)) return Curve.X448;
        throw new JOSEException("ECDH-ES OKP support requires an X25519 or X448 key.");
    }
}
