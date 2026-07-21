package com.cryptocarver.codec.impl;

import com.cryptocarver.codec.ByteFormat;
import com.cryptocarver.codec.Codec;
import com.cryptocarver.codec.CodecException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class Utf8Codec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isEmpty()) return new byte[0];
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new CodecException("Input contains invalid characters for UTF-8 encoding (e.g. isolated surrogates)", e, ByteFormat.TEXT_UTF8);
        }
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new CodecException("Input bytes are not valid UTF-8", e, ByteFormat.TEXT_UTF8);
        }
    }
}
