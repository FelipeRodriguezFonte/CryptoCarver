package com.cryptocarver.crypto.hsm;

import java.util.Arrays;

/** Parsed response: header, response command, two-character error, data and trailer. */
public record PayShieldResponse(String header, String responseCode, String errorCode,
                                byte[] data, byte[] trailer) {
    public PayShieldResponse {
        if (header == null || responseCode == null || errorCode == null
                || data == null || trailer == null) {
            throw new IllegalArgumentException("response parts must not be null");
        }
        data = data.clone();
        trailer = trailer.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
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
        if (!(other instanceof PayShieldResponse that)) {
            return false;
        }
        return header.equals(that.header)
                && responseCode.equals(that.responseCode)
                && errorCode.equals(that.errorCode)
                && Arrays.equals(data, that.data)
                && Arrays.equals(trailer, that.trailer);
    }

    @Override
    public int hashCode() {
        int result = 31 * header.hashCode() + responseCode.hashCode();
        result = 31 * result + errorCode.hashCode();
        result = 31 * result + Arrays.hashCode(data);
        return 31 * result + Arrays.hashCode(trailer);
    }
}
