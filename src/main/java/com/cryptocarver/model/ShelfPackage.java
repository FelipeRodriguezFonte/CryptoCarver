package com.cryptocarver.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned, non-secret metadata accompanying a Clipboard Shelf result.
 * Values are deliberately whitelisted: this is not a general purpose bag for
 * serialising controller state or credentials.
 */
public final class ShelfPackage {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String AUTHENTICATED_CIPHER = "authenticated-cipher";

    private static final List<String> ALLOWED_FIELDS = List.of(
            "ciphertext", "algorithm", "mode", "padding", "format", "authTag", "nonce", "aad");

    private final int schemaVersion;
    private final String type;
    private final Map<String, String> artifacts;
    private final List<String> compatibleTargets;

    private ShelfPackage(int schemaVersion, String type, Map<String, String> artifacts,
                         List<String> compatibleTargets) {
        this.schemaVersion = schemaVersion;
        this.type = type;
        this.artifacts = Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
        this.compatibleTargets = List.copyOf(compatibleTargets);
    }

    public static ShelfPackage authenticatedCipher(Map<String, String> artifacts) {
        Map<String, String> clean = new LinkedHashMap<>();
        if (artifacts != null) {
            artifacts.forEach((key, value) -> {
                if (ALLOWED_FIELDS.contains(key) && value != null && !value.isBlank()) {
                    clean.put(key, value);
                }
            });
        }
        if (!clean.containsKey("ciphertext") || !clean.containsKey("algorithm")
                || !clean.containsKey("mode") || !clean.containsKey("format")
                || !clean.containsKey("authTag") || !clean.containsKey("nonce")) {
            throw new IllegalArgumentException("Authenticated cipher shelf package is incomplete");
        }
        return new ShelfPackage(CURRENT_SCHEMA_VERSION, AUTHENTICATED_CIPHER, clean,
                List.of("SYMMETRIC_CIPHER"));
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getType() { return type; }
    public Map<String, String> getArtifacts() { return artifacts; }
    public List<String> getCompatibleTargets() { return compatibleTargets; }

    public String artifact(String name) { return artifacts.get(name); }

    public String displaySummary() {
        if (AUTHENTICATED_CIPHER.equals(type)) {
            return "STRUCTURED · " + artifact("algorithm") + "/" + artifact("mode")
                    + " · fields: ciphertext, auth tag, nonce/IV"
                    + (artifacts.containsKey("aad") ? ", AAD" : "");
        }
        return "STRUCTURED · " + type;
    }
}
