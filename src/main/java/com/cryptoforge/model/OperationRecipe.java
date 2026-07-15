package com.cryptoforge.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Portable, non-secret operation configuration for laboratory replay. */
public final class OperationRecipe {
    public static final int CURRENT_VERSION = 1;
    // A UI snapshot contains the state of many controls; retain a bounded but practical recipe size.
    private static final int MAX_PARAMETERS = 500;
    private static final int MAX_VALUE_CHARS = 1_000_000;
    private static final int MAX_TOTAL_CHARS = 4_000_000;
    private final int version;
    private final String operation;
    private final String createdAt;
    private final Map<String, String> parameters;

    public OperationRecipe(String operation, java.util.List<OperationDetail> details, SecretVisibility visibility) {
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("Recipe operation is required");
        this.version = CURRENT_VERSION;
        this.operation = operation.trim();
        this.createdAt = Instant.now().toString();
        this.parameters = sanitizeDetails(details, visibility);
    }

    @Deprecated
    public OperationRecipe(String operation, Map<String, String> parameters) {
        throw new UnsupportedOperationException("Map-based recipes are no longer supported for security reasons. Use structured OperationDetails instead.");
    }

    private OperationRecipe(int version, String operation, String createdAt, Map<String, String> parameters) {
        if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported recipe version: " + version);
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("Recipe operation is required");
        this.version = version;
        this.operation = operation;
        this.createdAt = createdAt == null ? Instant.now().toString() : createdAt;
        this.parameters = parameters;
    }

    public String toJson() { return new GsonBuilder().setPrettyPrinting().create().toJson(this); }
    public static OperationRecipe fromJson(String json) {
        Raw raw = new Gson().fromJson(json, Raw.class);
        if (raw == null) throw new IllegalArgumentException("Recipe JSON is empty");
        OperationRecipe recipe = new OperationRecipe(raw.version, raw.operation, raw.createdAt, new LinkedHashMap<>());
        if (raw.parameters != null) recipe.parameters.putAll(raw.parameters);
        return recipe;
    }
    
    public int version() { return version; }
    public String operation() { return operation; }
    public String createdAt() { return createdAt; }
    public Map<String, String> parameters() { return Map.copyOf(parameters); }

    private static Map<String, String> sanitizeDetails(java.util.List<OperationDetail> details, SecretVisibility visibility) {
        SecretVisibility effectiveVisibility = visibility == null ? SecretVisibility.REDACTED : visibility;
        Map<String, String> clean = new LinkedHashMap<>(); 
        if (details == null) return clean;
        int totalChars = 0;
        
        for (OperationDetail detail : details) {
            if (clean.size() >= MAX_PARAMETERS) throw new IllegalArgumentException("Recipe has too many parameters (maximum " + MAX_PARAMETERS + ")");
            
            String key = detail.name() == null ? "" : detail.name().trim();
            if (key.isEmpty()) continue;
            
            OperationDetail.Classification clazz = detail.classification();
            String value = detail.value() == null ? "" : detail.value();
            
            if (clazz == OperationDetail.Classification.SECRET) {
                if (effectiveVisibility == SecretVisibility.REDACTED) continue;
                if (effectiveVisibility == SecretVisibility.MASKED) value = "***MASKED***";
            } else if (clazz == OperationDetail.Classification.SENSITIVE) {
                // SENSITIVE policy: MASKED when visibility is REDACTED or MASKED. Kept only on FULL_LAB.
                if (effectiveVisibility != SecretVisibility.FULL_LAB) value = "***MASKED***";
            }
            
            if (value.length() > MAX_VALUE_CHARS) throw new IllegalArgumentException("Recipe parameter " + key + " exceeds " + MAX_VALUE_CHARS + " characters");
            totalChars += value.length();
            if (totalChars > MAX_TOTAL_CHARS) throw new IllegalArgumentException("Recipe exceeds " + MAX_TOTAL_CHARS + " total characters");
            clean.put(key, value);
        }
        return clean;
    }

    private static final class Raw { int version; String operation; String createdAt; Map<String, String> parameters; }
}
