package com.cryptocarver.model;

/** Removes credentials embedded in an HTTP(S) TSA URL while preserving all other spelling. */
public final class TsaUrlSanitizer {
    private TsaUrlSanitizer() { }

    public static String withoutUserInfo(String value) {
        if (value == null) return null;
        try {
            java.net.URI uri = new java.net.URI(value);
            if (uri.getRawUserInfo() == null) return value;
            String authority = uri.getRawAuthority();
            int start = value.indexOf("//") + 2;
            return value.substring(0, start)
                    + authority.substring(authority.lastIndexOf('@') + 1)
                    + value.substring(start + authority.length());
        } catch (java.net.URISyntaxException ignored) {
            return value;
        }
    }
}
