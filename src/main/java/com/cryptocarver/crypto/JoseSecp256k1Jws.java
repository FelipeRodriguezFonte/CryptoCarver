package com.cryptocarver.crypto;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Set;

/** ES256K (RFC 8812) signing and verification through the bundled BC provider. */
public final class JoseSecp256k1Jws {
    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.ES256K;
    private static final java.security.Provider PROVIDER = new BouncyCastleProvider();

    private JoseSecp256k1Jws() { }

    public static JWSSigner signer(PrivateKey key) throws JOSEException {
        if (!(key instanceof ECPrivateKey ec) || !CurveCheck.secp256k1(ec)) {
            throw new IllegalArgumentException("ES256K requires a secp256k1 EC private key.");
        }
        return new JWSSigner() {
            private final JCAContext context = new JCAContext();
            @Override public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
                requireAlgorithm(header);
                try {
                    Signature signature = Signature.getInstance("SHA256withECDSA", PROVIDER);
                    signature.initSign(ec);
                    signature.update(signingInput);
                    return Base64URL.encode(ECDSA.transcodeSignatureToConcat(signature.sign(), 32));
                } catch (Exception e) {
                    throw new JOSEException("ES256K signing failed.", e);
                }
            }
            @Override public Set<JWSAlgorithm> supportedJWSAlgorithms() { return Set.of(ALGORITHM); }
            @Override public JCAContext getJCAContext() { return context; }
        };
    }

    public static JWSVerifier verifier(PublicKey key) {
        if (!(key instanceof ECPublicKey ec) || !CurveCheck.secp256k1(ec)) {
            throw new IllegalArgumentException("ES256K requires a secp256k1 EC public key.");
        }
        return new JWSVerifier() {
            private final JCAContext context = new JCAContext();
            @Override public boolean verify(JWSHeader header, byte[] signingInput, Base64URL encodedSignature)
                    throws JOSEException {
                requireAlgorithm(header);
                try {
                    byte[] raw = encodedSignature.decode();
                    ECDSA.ensureLegalSignature(raw, ALGORITHM);
                    Signature signature = Signature.getInstance("SHA256withECDSA", PROVIDER);
                    signature.initVerify(ec);
                    signature.update(signingInput);
                    return signature.verify(ECDSA.transcodeSignatureToDER(raw));
                } catch (Exception e) {
                    throw new JOSEException("ES256K verification failed.", e);
                }
            }
            @Override public Set<JWSAlgorithm> supportedJWSAlgorithms() { return Set.of(ALGORITHM); }
            @Override public JCAContext getJCAContext() { return context; }
        };
    }

    private static void requireAlgorithm(JWSHeader header) throws JOSEException {
        if (header == null || !ALGORITHM.equals(header.getAlgorithm())) {
            throw new JOSEException("ES256K signer supports only alg=ES256K.");
        }
    }

    private static final class CurveCheck {
        static boolean secp256k1(java.security.interfaces.ECKey key) {
            try {
                return com.nimbusds.jose.jwk.Curve.SECP256K1.equals(
                        com.nimbusds.jose.jwk.Curve.forECParameterSpec(key.getParams()));
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
