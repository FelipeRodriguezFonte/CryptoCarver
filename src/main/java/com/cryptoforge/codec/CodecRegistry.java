package com.cryptoforge.codec;

import com.cryptoforge.codec.impl.*;

import java.util.EnumMap;
import java.util.Map;

public class CodecRegistry {
    private static final CodecRegistry INSTANCE = new CodecRegistry();
    private final Map<ByteFormat, Codec> codecs = new EnumMap<>(ByteFormat.class);

    private CodecRegistry() {
        codecs.put(ByteFormat.HEX, new HexCodec());
        codecs.put(ByteFormat.BASE64, new Base64Codec());
        codecs.put(ByteFormat.BASE64_URL, new Base64UrlCodec());
        codecs.put(ByteFormat.BASE32, new Base32Codec());
        codecs.put(ByteFormat.BASE58, new Base58Codec());
        codecs.put(ByteFormat.BASE58_CHECK, new Base58CheckCodec());
        codecs.put(ByteFormat.BINARY, new BinaryCodec());
        codecs.put(ByteFormat.DECIMAL, new DecimalCodec());

        codecs.put(ByteFormat.TEXT_UTF8, new Utf8Codec());
        codecs.put(ByteFormat.TEXT_ASCII, new AsciiCodec());
        codecs.put(ByteFormat.TEXT_ISO_8859_1, new Iso88591Codec());
    }

    public static CodecRegistry getInstance() {
        return INSTANCE;
    }

    public Codec getCodec(ByteFormat format) {
        Codec codec = codecs.get(format);
        if (codec == null) {
            throw new UnsupportedOperationException("Codec not implemented for format: " + format);
        }
        return codec;
    }

    public byte[] decode(String value, ByteFormat format) {
        if (value == null) {
            throw new CodecException("Input value is null", format);
        }
        return getCodec(format).decode(value);
    }

    public String encode(byte[] bytes, ByteFormat format) {
        if (bytes == null) {
            throw new CodecException("Input bytes are null", format);
        }
        return getCodec(format).encode(bytes);
    }
}
