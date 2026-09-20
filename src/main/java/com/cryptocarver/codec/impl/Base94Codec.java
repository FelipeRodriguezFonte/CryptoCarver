package com.cryptocarver.codec.impl;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.Codec;
import com.cryptocarver.codec.CodecException;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Reversible base-94 binary-to-text codec using the 94 printable ASCII
 * characters U+0021 ('!') through U+007E ('~'). The alphabet is explicit so
 * it is not confused with base-94 schemes that reserve whitespace.
 */
public final class Base94Codec implements Codec {
    private static final int BASE = 94;
    private static final char ZERO = '!';
    private static final char LAST = '~';

    @Override
    public byte[] decode(String value) throws CodecException {
        if (value == null || value.isEmpty()) return new byte[0];
        BigInteger number = BigInteger.ZERO;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < ZERO || character > LAST) {
                throw new CodecException("Invalid Base94 character: " + character, ByteFormat.BASE94, index);
            }
            number = number.multiply(BigInteger.valueOf(BASE)).add(BigInteger.valueOf(character - ZERO));
        }
        byte[] raw = number.toByteArray();
        if (raw.length > 0 && raw[0] == 0) raw = Arrays.copyOfRange(raw, 1, raw.length);
        int leading = 0;
        while (leading < value.length() && value.charAt(leading) == ZERO) leading++;
        byte[] result = new byte[leading + raw.length];
        System.arraycopy(raw, 0, result, leading, raw.length);
        return result;
    }

    @Override
    public String encode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder result = new StringBuilder();
        while (number.signum() > 0) {
            BigInteger[] quotient = number.divideAndRemainder(BigInteger.valueOf(BASE));
            result.append((char) (ZERO + quotient[1].intValue()));
            number = quotient[0];
        }
        for (byte value : bytes) {
            if (value == 0) result.append(ZERO);
            else break;
        }
        return result.reverse().toString();
    }
}
