package com.cryptoforge.codec.impl;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.Codec;
import com.cryptoforge.codec.CodecException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Base58CheckCodec implements Codec {
    private final Codec base58 = new Base58Codec();

    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isBlank()) return new byte[0];
        byte[] complete;
        try {
            complete = base58.decode(value);
        } catch (CodecException e) {
            throw new CodecException(e.getMessage(), e.getCause(), ByteFormat.BASE58_CHECK);
        }

        if (complete.length < 4) {
            throw new CodecException("Base58Check value is too short to contain a checksum", ByteFormat.BASE58_CHECK);
        }

        byte[] payload = Arrays.copyOf(complete, complete.length - 4);
        byte[] checksum = Arrays.copyOfRange(complete, complete.length - 4, complete.length);

        if (!Arrays.equals(checksum, Arrays.copyOf(sha256d(payload), 4))) {
            throw new CodecException("Invalid Base58Check checksum", ByteFormat.BASE58_CHECK);
        }
        return payload;
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        byte[] checksum = sha256d(bytes);
        byte[] complete = Arrays.copyOf(bytes, bytes.length + 4);
        System.arraycopy(checksum, 0, complete, bytes.length, 4);
        return base58.encode(complete);
    }

    private static byte[] sha256d(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(digest.digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
