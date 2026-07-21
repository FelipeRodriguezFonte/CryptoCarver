package com.cryptocarver.crypto.hsm;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import java.security.GeneralSecurityException;
import java.util.Set;

/** Nimbus adapter that signs JWS signing input through an opaque PKCS#11 key handle. */
public final class Pkcs11JwsSigner implements JWSSigner {
    private static final Set<JWSAlgorithm> SUPPORTED = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
            JWSAlgorithm.EdDSA);

    private final Pkcs11Session session;
    private final String alias;
    private final JCAContext context = new JCAContext();

    public Pkcs11JwsSigner(Pkcs11Session session, String alias) {
        if (session == null) throw new IllegalArgumentException("PKCS#11 session is required");
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("PKCS#11 private-key alias is required");
        this.session = session;
        this.alias = alias;
        context.setProvider(null); // Pkcs11Session explicitly owns the token provider.
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
        JWSAlgorithm algorithm = header == null ? null : header.getAlgorithm();
        if (!SUPPORTED.contains(algorithm)) {
            throw new JOSEException("Unsupported PKCS#11 JWS algorithm: " + algorithm);
        }
        try {
            byte[] derOrRaw = session.sign(alias, signingInput, jcaSignatureName(algorithm));
            byte[] joseSignature = JWSAlgorithm.Family.EC.contains(algorithm)
                    ? ECDSA.transcodeSignatureToConcat(derOrRaw, ECDSA.getSignatureByteArrayLength(algorithm))
                    : derOrRaw;
            return Base64URL.encode(joseSignature);
        } catch (GeneralSecurityException error) {
            throw new JOSEException("PKCS#11 signing failed for alias " + alias, error);
        }
    }

    @Override
    public JCAContext getJCAContext() {
        return context;
    }

    static String jcaSignatureName(JWSAlgorithm algorithm) {
        if (JWSAlgorithm.RS256.equals(algorithm)) return "SHA256withRSA";
        if (JWSAlgorithm.RS384.equals(algorithm)) return "SHA384withRSA";
        if (JWSAlgorithm.RS512.equals(algorithm)) return "SHA512withRSA";
        if (JWSAlgorithm.ES256.equals(algorithm)) return "SHA256withECDSA";
        if (JWSAlgorithm.ES384.equals(algorithm)) return "SHA384withECDSA";
        if (JWSAlgorithm.ES512.equals(algorithm)) return "SHA512withECDSA";
        if (JWSAlgorithm.EdDSA.equals(algorithm)) return "Ed25519";
        throw new IllegalArgumentException("Unsupported PKCS#11 JWS algorithm: " + algorithm);
    }
}
