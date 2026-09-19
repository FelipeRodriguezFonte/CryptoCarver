package com.cryptocarver.crypto.hsm;

import java.util.Arrays;

/** Immutable representation of a payShield host message payload. */
public record PayShieldMessage(String header, String code, byte[] body, byte[] trailer) {
    public PayShieldMessage {
        if (header == null || code == null || body == null || trailer == null) {
            throw new IllegalArgumentException("message parts must not be null");
        }
        body = body.clone();
        trailer = trailer.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public byte[] trailer() {
        return trailer.clone();
    }

    public boolean hasTrailer() {
        return trailer.length != 0;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PayShieldMessage that)) {
            return false;
        }
        return header.equals(that.header)
                && code.equals(that.code)
                && Arrays.equals(body, that.body)
                && Arrays.equals(trailer, that.trailer);
    }

    @Override
    public int hashCode() {
        int result = 31 * header.hashCode() + code.hashCode();
        result = 31 * result + Arrays.hashCode(body);
        return 31 * result + Arrays.hashCode(trailer);
    }
}
