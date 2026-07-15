package com.cryptoforge.codec.impl;

import com.cryptoforge.codec.ByteFormat;
import com.cryptoforge.codec.Codec;
import com.cryptoforge.codec.CodecException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class AsciiCodec implements Codec {
    @Override
    public byte[] decode(String value) throws CodecException {
        if (value.isEmpty()) return new byte[0];
        try {
            return StandardCharsets.US_ASCII.newEncoder()
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value)).array();
        } catch (CharacterCodingException e) {
            throw new CodecException("Input contains non-ASCII characters", e, ByteFormat.TEXT_ASCII);
        }
    }

    @Override
    public String encode(byte[] bytes) throws CodecException {
        if (bytes.length == 0) return "";
        try {
            return StandardCharsets.US_ASCII.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new CodecException("Input bytes are not valid ASCII", e, ByteFormat.TEXT_ASCII);
        }
    }
}
