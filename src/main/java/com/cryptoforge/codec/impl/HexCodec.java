package com.cryptoforge.codec.impl;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.Codec;
import com.cryptoforge.codec.CodecException;

public class HexCodec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isBlank()) return new byte[0];

        String normalized = value.replaceAll("[\\s:-]", "");

        if (!normalized.matches("[0-9A-Fa-f]+")) {
            throw new CodecException("Invalid hexadecimal string: contains non-hex characters", ByteFormat.HEX);
        }

        if (normalized.length() % 2 != 0) {
            throw new CodecException("Hexadecimal string must have an even length (found " + normalized.length() + " chars)", ByteFormat.HEX);
        }

        int len = normalized.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(normalized.charAt(i), 16) << 4)
                                 + Character.digit(normalized.charAt(i + 1), 16));
        }

        return data;
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
