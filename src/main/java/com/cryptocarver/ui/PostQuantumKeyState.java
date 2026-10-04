package com.cryptocarver.ui;

import java.security.PrivateKey;
import java.security.PublicKey;

/** Shared in-memory key material for the post-quantum UI coordinators. */
final class PostQuantumKeyState {
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private byte[] bobSecret;

    PublicKey publicKey() { return publicKey; }
    PrivateKey privateKey() { return privateKey; }
    byte[] bobSecret() { return bobSecret; }

    void setPublicKey(PublicKey key) { publicKey = key; }
    void setPrivateKey(PrivateKey key) { privateKey = key; }
    void setBobSecret(byte[] secret) { bobSecret = secret; }
}
