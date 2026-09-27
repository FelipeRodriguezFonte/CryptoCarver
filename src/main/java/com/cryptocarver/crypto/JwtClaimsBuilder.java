package com.cryptocarver.crypto;

import com.nimbusds.jose.util.JSONObjectUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Merges the JOSE module's claims-builder fields into a JWT payload. */
public final class JwtClaimsBuilder {

    private JwtClaimsBuilder() {
    }

    /**
     * Sets iss/sub/aud (when not blank), iat and exp on the existing payload,
     * keeping every other claim. A blank payload starts a new object.
     */
    public static String apply(String payload, String iss, String sub, String aud, long validForHours,
            long nowEpochSeconds) {
        Map<String, Object> claims = new LinkedHashMap<>();
        if (payload != null && !payload.isBlank()) {
            try {
                claims.putAll(JSONObjectUtils.parse(payload));
            } catch (java.text.ParseException e) {
                throw new IllegalArgumentException("The payload is not a JSON object; fix or clear it before applying claims.", e);
            }
        }
        putIfPresent(claims, "iss", iss);
        putIfPresent(claims, "sub", sub);
        putIfPresent(claims, "aud", aud);
        claims.put("iat", nowEpochSeconds);
        claims.put("exp", Math.addExact(nowEpochSeconds, Math.multiplyExact(validForHours, 3600L)));
        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(claims);
    }

    private static void putIfPresent(Map<String, Object> claims, String name, String value) {
        if (value != null && !value.isBlank()) claims.put(name, value.trim());
    }
}
