package com.cryptocarver.codec.impl;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.Codec;
import com.cryptocarver.codec.CodecException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class Iso88591Codec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isEmpty()) return new byte[0];
        try {
            ByteBuffer buffer = StandardCharsets.ISO_8859_1.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new CodecException("Input contains non-ISO-8859-1 characters", e, ByteFormat.TEXT_ISO_8859_1);
        }
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
