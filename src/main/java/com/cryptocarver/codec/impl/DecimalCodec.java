package com.cryptocarver.codec.impl;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.Codec;
import com.cryptocarver.codec.CodecException;

public class DecimalCodec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isBlank()) return new byte[0];
        String[] values = value.trim().split("[,\\s]+");
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            try {
                int val = Integer.parseInt(values[i]);
                if (val < 0 || val > 255) {
                    throw new CodecException("Decimal byte " + val + " is outside 0..255", ByteFormat.DECIMAL, i);
                }
                bytes[i] = (byte) val;
            } catch (NumberFormatException e) {
                throw new CodecException("Invalid decimal byte: " + values[i], e, ByteFormat.DECIMAL);
            }
        }
        return bytes;
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        StringBuilder result = new StringBuilder(bytes.length * 4);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) result.append(' ');
            result.append(bytes[i] & 0xFF);
        }
        return result.toString();
    }
}
