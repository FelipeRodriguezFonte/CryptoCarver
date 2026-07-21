package com.cryptoforge.crypto.hsm;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Process-local owner for the single laboratory PKCS#11 token session.
 *
 * <p>It has no persistence layer: users supply the library configuration and
 * PIN for each application session. UI controllers share opaque token handles
 * through this manager and never receive raw key bytes.</p>
 */
public final class Pkcs11SessionManager {
    private static final Pkcs11SessionManager INSTANCE = new Pkcs11SessionManager();
    private Pkcs11Session session;

    private Pkcs11SessionManager() {
    }

    public static Pkcs11SessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized Pkcs11Session connect(Pkcs11Configuration configuration, char[] pin)
            throws GeneralSecurityException, IOException {
        disconnect();
        session = Pkcs11Session.open(configuration, pin);
        return session;
    }

    public synchronized boolean isConnected() {
        return session != null;
    }

    public synchronized Pkcs11Session requireSession() {
        if (session == null) {
            throw new IllegalStateException("No PKCS#11 token is connected. Open Keys > Tools > PKCS#11 Token first.");
        }
        return session;
    }

    /** Returns aliases of secret-key objects suitable for cipher or MAC use. */
    public synchronized List<String> listSecretKeyAliases() throws GeneralSecurityException {
        return requireSession().listObjects().stream()
                .filter(object -> "Secret key".equals(object.objectType()))
                .map(Pkcs11ObjectInfo::alias)
                .toList();
    }

    /** Returns aliases which expose a private-key handle for token signing. */
    public synchronized List<String> listPrivateKeyAliases() throws GeneralSecurityException {
        return requireSession().listObjects().stream()
                .filter(object -> "Private key".equals(object.objectType()))
                .map(Pkcs11ObjectInfo::alias)
                .toList();
    }

    public synchronized List<String> listCertificateAliases() throws GeneralSecurityException {
        return requireSession().listCertificateAliases();
    }

    public synchronized void disconnect() {
        if (session != null) {
            session.close();
            session = null;
        }
    }
}
