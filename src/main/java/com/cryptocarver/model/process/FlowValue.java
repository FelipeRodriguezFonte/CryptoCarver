package com.cryptocarver.model.process;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public record FlowValue(byte[] bytes, Representation representation, Charset charset) {
    public static FlowValue binary(byte[] bytes) {
        return new FlowValue(bytes, Representation.BINARY, null);
    }

    public static FlowValue text(String text, Charset charset) {
        return new FlowValue(text.getBytes(charset), Representation.TEXT_UTF8, charset);
    }

    public static FlowValue hex(byte[] bytes) {
        return new FlowValue(bytes, Representation.HEX, null);
    }

    public static FlowValue base64(byte[] bytes) {
        return new FlowValue(bytes, Representation.BASE64, null);
    }

    public static FlowValue base64Url(byte[] bytes) {
        return new FlowValue(bytes, Representation.BASE64URL, null);
    }

    public String render() {
        if (representation == Representation.TEXT_UTF8) {
            return new String(bytes, charset != null ? charset : StandardCharsets.UTF_8);
        } else if (representation == Representation.HEX) {
            return new String(bytes, StandardCharsets.UTF_8); // Hex encoded text
        } else if (representation == Representation.BASE64 || representation == Representation.BASE64URL) {
            return new String(bytes, StandardCharsets.UTF_8); // Base64 encoded text
        } else {
            return HexFormat.of().withUpperCase().formatHex(bytes); // BINARY fallback
        }
    }
}
