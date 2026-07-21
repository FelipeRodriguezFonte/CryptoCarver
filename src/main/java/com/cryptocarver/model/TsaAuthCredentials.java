package com.cryptocarver.model;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Ephemeral, in-memory only credentials for TSA endpoints.
 * Explicitly designed to never be persisted in AppSettings or logs.
 */
public record TsaAuthCredentials(AuthType type, String username, String tokenOrPassword) {

    public enum AuthType {
        NONE, BASIC, BEARER
    }

    /** Returns the properly formatted HTTP Authorization header value, or null if NONE. */
    public String getAuthorizationHeader() {
        if (type == AuthType.NONE) return null;
        if (type == AuthType.BEARER) {
            return "Bearer " + tokenOrPassword;
        }
        if (type == AuthType.BASIC) {
            String credentials = (username != null ? username : "") + ":" + (tokenOrPassword != null ? tokenOrPassword : "");
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    @Override
    public String toString() {
        return "TsaAuthCredentials[type=" + type + ", username=" + username + ", tokenOrPassword=***REDACTED***]";
    }
}
