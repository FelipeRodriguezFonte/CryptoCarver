package com.cryptocarver.crypto;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.RFC3394WrapEngine;
import org.bouncycastle.crypto.engines.RFC5649WrapEngine;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * AES key-wrapping helpers for laboratory use.
 *
 * <p>RFC 3394 protects key data that is a multiple of 64 bits. RFC 5649 adds
 * padding and therefore also supports short or non-aligned key material.</p>
 */
public final class KeyWrapOperations {
    private static final int AES_BLOCK_SIZE = 16;

    private KeyWrapOperations() {
    }

    /** Wraps RFC 3394 key data. Input must be at least 16 bytes and 64-bit aligned. */
    public static byte[] wrapRfc3394(byte[] kek, byte[] keyData) {
        validateKek(kek);
        if (keyData == null || keyData.length < AES_BLOCK_SIZE || keyData.length % 8 != 0) {
            throw new IllegalArgumentException("RFC 3394 key data must be at least 16 bytes and a multiple of 8 bytes");
        }
        RFC3394WrapEngine engine = new RFC3394WrapEngine(new AESEngine());
        engine.init(true, new KeyParameter(kek));
        return engine.wrap(keyData, 0, keyData.length);
    }

    /** Unwraps and verifies RFC 3394 key data. */
    public static byte[] unwrapRfc3394(byte[] kek, byte[] wrapped) throws InvalidCipherTextException {
        validateKek(kek);
        if (wrapped == null || wrapped.length < 24 || wrapped.length % 8 != 0) {
            throw new IllegalArgumentException("RFC 3394 wrapped data must be at least 24 bytes and a multiple of 8 bytes");
        }
        RFC3394WrapEngine engine = new RFC3394WrapEngine(new AESEngine());
        engine.init(false, new KeyParameter(kek));
        return engine.unwrap(wrapped, 0, wrapped.length);
    }

    /** Wraps arbitrary-length key data using RFC 5649 (AES Key Wrap with Padding). */
    public static byte[] wrapRfc5649(byte[] kek, byte[] keyData) {
        validateKek(kek);
        if (keyData == null || keyData.length == 0) {
            throw new IllegalArgumentException("Key data cannot be empty");
        }
        RFC5649WrapEngine engine = new RFC5649WrapEngine(new AESEngine());
        engine.init(true, new KeyParameter(kek));
        return engine.wrap(keyData, 0, keyData.length);
    }

    /** Unwraps and verifies RFC 5649 padded key data. */
    public static byte[] unwrapRfc5649(byte[] kek, byte[] wrapped) throws InvalidCipherTextException {
        validateKek(kek);
        if (wrapped == null || wrapped.length < AES_BLOCK_SIZE || wrapped.length % 8 != 0) {
            throw new IllegalArgumentException("RFC 5649 wrapped data must be at least 16 bytes and a multiple of 8 bytes");
        }
        RFC5649WrapEngine engine = new RFC5649WrapEngine(new AESEngine());
        engine.init(false, new KeyParameter(kek));
        return engine.unwrap(wrapped, 0, wrapped.length);
    }

    private static void validateKek(byte[] kek) {
        if (kek == null || (kek.length != 16 && kek.length != 24 && kek.length != 32)) {
            throw new IllegalArgumentException("AES KEK must be 16, 24, or 32 bytes");
        }
    }
}
