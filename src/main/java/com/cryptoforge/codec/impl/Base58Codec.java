package com.cryptoforge.codec.impl;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.Codec;
import com.cryptoforge.codec.CodecException;

import java.math.BigInteger;
import java.util.Arrays;

public class Base58Codec implements Codec {
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isBlank()) return new byte[0];
        BigInteger number = BigInteger.ZERO;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            int digit = ALPHABET.indexOf(character);
            if (digit < 0) {
                throw new CodecException("Invalid Base58 character: " + character, ByteFormat.BASE58, i);
            }
            number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
        }
        byte[] raw = number.toByteArray();
        if (raw.length > 0 && raw[0] == 0) raw = Arrays.copyOfRange(raw, 1, raw.length);
        int leading = 0;
        while (leading < value.length() && value.charAt(leading) == '1') leading++;
        byte[] result = new byte[leading + raw.length];
        System.arraycopy(raw, 0, result, leading, raw.length);
        return result;
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder result = new StringBuilder();
        BigInteger base = BigInteger.valueOf(58);
        while (number.signum() > 0) {
            BigInteger[] quotient = number.divideAndRemainder(base);
            result.append(ALPHABET.charAt(quotient[1].intValue()));
            number = quotient[0];
        }
        for (byte value : bytes) {
            if (value == 0) result.append('1');
            else break;
        }
        return result.reverse().toString();
    }
}
