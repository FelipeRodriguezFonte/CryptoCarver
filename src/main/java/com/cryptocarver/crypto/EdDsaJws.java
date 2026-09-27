package com.cryptocarver.crypto;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECKey;
import java.util.Set;

/**
 * EdDSA (Ed25519 / Ed448, RFC 8037) JWS signing through the JDK provider:
 * Nimbus only offers EdDSA with the optional Tink dependency.
 */
public final class EdDsaJws {

    private static final Set<JWSAlgorithm> ALGORITHMS = Set.of(JWSAlgorithm.EdDSA);

    private EdDsaJws() {
    }

    public static JWSSigner signer(PrivateKey key) {
        requireEdEc(key);
        return new JWSSigner() {
            private final JCAContext context = new JCAContext();

            @Override
            public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
                requireEdDsa(header);
                try {
                    Signature signature = Signature.getInstance("EdDSA");
                    signature.initSign(key);
                    signature.update(signingInput);
                    return Base64URL.encode(signature.sign());
                } catch (Exception e) {
                    throw new JOSEException("EdDSA signing failed: " + e.getMessage(), e);
                }
            }

            @Override
            public Set<JWSAlgorithm> supportedJWSAlgorithms() {
                return ALGORITHMS;
            }

            @Override
            public JCAContext getJCAContext() {
                return context;
            }
        };
    }

    public static JWSVerifier verifier(PublicKey key) {
        requireEdEc(key);
        return new JWSVerifier() {
            private final JCAContext context = new JCAContext();

            @Override
            public boolean verify(JWSHeader header, byte[] signedContent, Base64URL signature) throws JOSEException {
                if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm())) return false;
                try {
                    Signature verifier = Signature.getInstance("EdDSA");
                    verifier.initVerify(key);
                    verifier.update(signedContent);
                    return verifier.verify(signature.decode());
                } catch (java.security.SignatureException malformed) {
                    return false;
                } catch (Exception e) {
                    throw new JOSEException("EdDSA verification failed: " + e.getMessage(), e);
                }
            }

            @Override
            public Set<JWSAlgorithm> supportedJWSAlgorithms() {
                return ALGORITHMS;
            }

            @Override
            public JCAContext getJCAContext() {
                return context;
            }
        };
    }

    private static void requireEdDsa(JWSHeader header) throws JOSEException {
        if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm())) {
            throw new JOSEException("EdDSA signer cannot sign " + header.getAlgorithm());
        }
    }

    private static void requireEdEc(java.security.Key key) {
        if (!(key instanceof EdECKey)) {
            throw new IllegalArgumentException("EdDSA needs an Ed25519 or Ed448 key; the supplied key is "
                    + key.getAlgorithm() + ".");
        }
    }
}
