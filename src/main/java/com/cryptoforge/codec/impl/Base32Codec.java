package com.cryptoforge.codec.impl;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.Codec;
import com.cryptoforge.codec.CodecException;

import org.apache.commons.codec.binary.Base32;

public class Base32Codec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isBlank()) return new byte[0];
        Base32 codec = new Base32();
        String normalized = value.replaceAll("\\s", "");
        if (!codec.isInAlphabet(normalized.replace("=", ""))) {
            throw new CodecException("Invalid Base32 value: contains characters outside the Base32 alphabet", ByteFormat.BASE32);
        }
        try {
            return codec.decode(normalized);
        } catch (Exception e) {
            throw new CodecException("Failed to decode Base32", e, ByteFormat.BASE32);
        }
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        return new Base32().encodeToString(bytes);
    }
}
