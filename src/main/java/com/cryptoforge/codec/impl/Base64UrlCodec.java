package com.cryptoforge.codec.impl;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.Codec;
import com.cryptoforge.codec.CodecException;

import java.util.Base64;

public class Base64UrlCodec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isEmpty()) return new byte[0];
        
        // Strict Base64URL without padding: only A-Z, a-z, 0-9, -, _
        if (!value.matches("^[A-Za-z0-9_-]+$")) {
            throw new CodecException("Invalid Base64URL value: contains padding (=), whitespace, or illegal characters", ByteFormat.BASE64_URL);
        }

        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new CodecException("Invalid Base64URL value", e, ByteFormat.BASE64_URL);
        }
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        // Unpadded RFC 4648 Base64URL suitable for JOSE
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
