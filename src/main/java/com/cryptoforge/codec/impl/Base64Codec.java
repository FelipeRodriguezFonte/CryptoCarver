package com.cryptoforge.codec.impl;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.Codec;
import com.cryptoforge.codec.CodecException;

import java.util.Base64;

public class Base64Codec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isBlank()) return new byte[0];
        String normalized = value.replaceAll("-----BEGIN [A-Z0-9 ]+-----", "")
                .replaceAll("-----END [A-Z0-9 ]+-----", "")
                .replaceAll("\\s", "");
        
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException e) {
            throw new CodecException("Invalid Base64 string", e, ByteFormat.BASE64);
        }
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        return Base64.getEncoder().encodeToString(bytes);
    }
}
