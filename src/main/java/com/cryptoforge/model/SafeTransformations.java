package com.cryptoforge.model;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.CodecRegistry;
import com.cryptoforge.codec.CodecException;

import java.security.MessageDigest;

/** Stateless transformations safe to expose through batch and local automation interfaces. */
public final class SafeTransformations {
    private SafeTransformations() { }

    public static String sha256(String value) throws Exception {
        byte[] input = CodecRegistry.getInstance().decode(value, ByteFormat.TEXT_UTF8);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
        return CodecRegistry.getInstance().encode(digest, ByteFormat.HEX).toLowerCase();
    }

    public static String encodeBase64Url(String value) {
        byte[] input = CodecRegistry.getInstance().decode(value, ByteFormat.TEXT_UTF8);
        return CodecRegistry.getInstance().encode(input, ByteFormat.BASE64_URL);
    }

    public static String decodeBase64Url(String value) {
        try {
            byte[] decoded = CodecRegistry.getInstance().decode(value, ByteFormat.BASE64_URL);
            return CodecRegistry.getInstance().encode(decoded, ByteFormat.TEXT_UTF8);
        } catch (CodecException e) {
            throw new IllegalArgumentException("Invalid Base64URL string", e);
        }
    }
}
