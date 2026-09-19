package com.cryptocarver.crypto.hsm;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Fields evidenced by the currently available successful NC response. */
public record PayShieldNcResponse(String lmkCheckValue, String firmwareVersion) {
    private static final int LMK_CHECK_VALUE_LENGTH = 16;
    private static final int CAPTURED_DATA_LENGTH = 25;

    /**
     * Decomposes only the exact successful shape that has been captured. A
     * different firmware may return a different suffix, so falling back to an
     * opaque body is safer than guessing offsets from one sample.
     */
    public static Optional<PayShieldNcResponse> from(PayShieldResponse response) {
        if (!"ND".equals(response.responseCode()) || !"00".equals(response.errorCode())) {
            return Optional.empty();
        }
        String data = new String(response.data(), StandardCharsets.US_ASCII);
        if (data.length() != CAPTURED_DATA_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new PayShieldNcResponse(
                data.substring(0, LMK_CHECK_VALUE_LENGTH),
                data.substring(LMK_CHECK_VALUE_LENGTH)));
    }
}
