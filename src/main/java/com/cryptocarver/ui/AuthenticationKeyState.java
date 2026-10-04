package com.cryptocarver.ui;

import java.security.PrivateKey;
import java.security.PublicKey;

/** Mutable in-memory key cache shared by Authentication's signature and key coordinators. */
final class AuthenticationKeyState {
    private PrivateKey privateKey;
    private PublicKey publicKey;

    PrivateKey privateKey() { return privateKey; }
    void setPrivateKey(PrivateKey value) { privateKey = value; }
    PublicKey publicKey() { return publicKey; }
    void setPublicKey(PublicKey value) { publicKey = value; }
}
