package com.cryptocarver.codec;

public interface Codec {
    byte[] decode(String value) throws CodecException;
    String encode(byte[] bytes) throws CodecException;
}
