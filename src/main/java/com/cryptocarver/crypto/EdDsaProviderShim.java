package com.cryptocarver.crypto;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyFactorySpi;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * Compatibility shim that lets {@code cose-java} 1.1.0 sign/verify with Ed25519 keys, which it
 * cannot do out of the box. Two independent, empirically-confirmed gaps in that library (not
 * assumed — traced through its decompiled source and proven with a standalone reproduction before
 * writing this class):
 *
 * <ol>
 *   <li>Its EdDSA signing/verification code
 *       (`SignCommon.computeSignature`/`validateSignature`) hardcodes
 *       {@code Signature.getInstance("NonewithEdDSA", "EdDSA")} — a JCA provider literally named
 *       {@code "EdDSA"}, from the old, now-unmaintained {@code net.i2p.crypto.eddsa} project. This
 *       app registers neither that provider nor any other provider under that exact name.</li>
 *   <li>{@code OneKey.CheckOkpKey()} — the internal method that turns raw OKP key material back
 *       into JCA {@link PrivateKey}/{@link PublicKey} objects — does the same thing:
 *       {@code KeyFactory.getInstance("EdDSA", "EdDSA")}.</li>
 * </ol>
 *
 * <p>This class registers a real {@link Provider} under that exact name, {@code "EdDSA"}, whose
 * {@code Signature}/{@code KeyFactory} services are thin delegators to whatever real
 * implementation the JDK itself already provides for the standard {@code "Ed25519"} algorithm
 * (built in since JDK 15, JEP 339 — no BouncyCastle involved here; BC 1.78.1 was checked and does
 * <b>not</b> expose an {@code Ed25519}/{@code EdDSA} {@code Signature} service, only
 * {@code KeyFactory}). This is a standard, narrowly-scoped JCA technique for bridging a library
 * that hardcodes an old/nonstandard provider name to whatever real implementation is actually
 * available — it does not change how Ed25519 signing itself works, only which provider object
 * cose-java's hardcoded lookup resolves to.</p>
 */
final class EdDsaProviderShim extends Provider {

    private static final String PROVIDER_NAME = "EdDSA";
    private static volatile boolean registered;

    private EdDsaProviderShim() {
        super(PROVIDER_NAME, "1.0", "Delegates to the JDK's built-in Ed25519 support so cose-java's "
                + "hardcoded Signature/KeyFactory provider-name lookup (\"EdDSA\") resolves to something real.");
        putService(new Service(this, "Signature", "NonewithEdDSA",
                DelegatingSignatureSpi.class.getName(), null, null));
        putService(new Service(this, "KeyFactory", "EdDSA",
                DelegatingKeyFactorySpi.class.getName(), null, null));
    }

    /** Idempotent; safe to call before every EdDSA operation. */
    static void ensureRegistered() {
        if (registered) {
            return;
        }
        synchronized (EdDsaProviderShim.class) {
            if (!registered) {
                if (Security.getProvider(PROVIDER_NAME) == null) {
                    Security.addProvider(new EdDsaProviderShim());
                }
                registered = true;
            }
        }
    }

    /** Delegates every call to a lazily-created {@code Signature.getInstance("Ed25519", "SunEC")}. */
    public static final class DelegatingSignatureSpi extends SignatureSpi {
        private Signature delegate;

        private Signature delegate() throws GeneralSecurityException {
            if (delegate == null) {
                delegate = Signature.getInstance("Ed25519", "SunEC");
            }
            return delegate;
        }

        @Override
        protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
            try {
                delegate().initVerify(publicKey);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeyException(e);
            }
        }

        @Override
        protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
            try {
                delegate().initSign(privateKey);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeyException(e);
            }
        }

        @Override
        protected void engineUpdate(byte b) throws SignatureException {
            delegate.update(b);
        }

        @Override
        protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
            delegate.update(b, off, len);
        }

        @Override
        protected byte[] engineSign() throws SignatureException {
            return delegate.sign();
        }

        @Override
        protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
            return delegate.verify(sigBytes);
        }

        @Override
        protected void engineSetParameter(String param, Object value) {
            throw new UnsupportedOperationException("Parameters are not needed for Ed25519");
        }

        @Override
        protected Object engineGetParameter(String param) {
            throw new UnsupportedOperationException("Parameters are not needed for Ed25519");
        }
    }

    /** Delegates every call to a lazily-created {@code KeyFactory.getInstance("EdDSA", "SunEC")}. */
    public static final class DelegatingKeyFactorySpi extends KeyFactorySpi {
        private KeyFactory delegate() throws GeneralSecurityException {
            return KeyFactory.getInstance("EdDSA", "SunEC");
        }

        @Override
        protected PrivateKey engineGeneratePrivate(KeySpec keySpec) throws InvalidKeySpecException {
            try {
                return delegate().generatePrivate(keySpec);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeySpecException(e.getMessage(), e);
            }
        }

        @Override
        protected PublicKey engineGeneratePublic(KeySpec keySpec) throws InvalidKeySpecException {
            try {
                return delegate().generatePublic(keySpec);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeySpecException(e.getMessage(), e);
            }
        }

        @Override
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException {
            try {
                return delegate().getKeySpec(key, keySpec);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeySpecException(e.getMessage(), e);
            }
        }

        @Override
        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            try {
                return delegate().translateKey(key);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeyException(e);
            }
        }
    }
}
