package com.cryptoforge.crypto.hsm;

/** Public metadata for an object discovered through a PKCS#11 token. */
public record Pkcs11ObjectInfo(
        String alias,
        String objectType,
        String algorithm,
        String format,
        String fingerprint) {
}
