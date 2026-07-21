package com.cryptocarver.codec.impl;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.Codec;
import com.cryptocarver.codec.CodecException;

public class BinaryCodec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isBlank()) return new byte[0];
        String normalized = value.replaceAll("[\\s:-]", "");

        if (!normalized.matches("[01]+")) {
            throw new CodecException("Binary value contains invalid characters (only 0 and 1 allowed)", ByteFormat.BINARY);
        }

        if (normalized.length() % 8 != 0) {
            throw new CodecException("Binary value length must be a multiple of 8 bits (complete bytes)", ByteFormat.BINARY);
        }

        byte[] bytes = new byte[normalized.length() / 8];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(normalized.substring(i * 8, i * 8 + 8), 2);
        }
        return bytes;
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        StringBuilder result = new StringBuilder(bytes.length * 9);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) result.append(' ');
            result.append(String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF)).replace(' ', '0'));
        }
        return result.toString();
    }
}
