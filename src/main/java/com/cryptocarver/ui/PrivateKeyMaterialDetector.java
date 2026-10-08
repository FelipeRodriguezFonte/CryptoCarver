package com.cryptocarver.ui;

import com.nimbusds.jose.util.Base64URL;
import java.util.List;
import java.util.Map;

/** Shared JOSE private-material detection, including nested keys and compact tokens. */
final class PrivateKeyMaterialDetector {
    private PrivateKeyMaterialDetector() { }

    static boolean containsPrivateMaterial(Object value, int depth) {
        try {
            return detectPrivateMaterial(value, depth);
        } catch (RuntimeException | StackOverflowError invalidInput) {
            return false;
        }
    }

    private static boolean detectPrivateMaterial(Object value, int depth) {
        if (value == null || depth > 12) return false;
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("kty") && (map.containsKey("d") || map.containsKey("k"))) return true;
            return map.values().stream().anyMatch(child -> containsPrivateMaterial(child, depth + 1));
        }
        if (value instanceof List<?> list) return list.stream().anyMatch(child -> containsPrivateMaterial(child, depth + 1));
        if (value instanceof String text) {
            if (text.contains("PRIVATE KEY-----")) return true;
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                try { return containsPrivateMaterial(com.nimbusds.jose.util.JSONObjectUtils.parse(trimmed), depth + 1); }
                catch (Exception invalid) { return trimmed.contains("\"kty\"") && (trimmed.contains("\"d\"") || trimmed.contains("\"k\"")); }
            }
            String[] parts = trimmed.split("\\.", -1);
            if (parts.length == 3 || parts.length == 5) {
                if (containsPrivateMaterial(new Base64URL(parts[0]).decodeToString(), depth + 1)) return true;
                if (parts.length == 3 && containsPrivateMaterial(new Base64URL(parts[1]).decodeToString(), depth + 1)) return true;
            } else if (trimmed.startsWith("ey")) {
                return containsPrivateMaterial(new Base64URL(trimmed).decodeToString(), depth + 1);
            }
        }
        return false;
    }

}
