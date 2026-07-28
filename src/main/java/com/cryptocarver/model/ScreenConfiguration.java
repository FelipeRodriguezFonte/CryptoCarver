package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Portable, complete configuration for one CryptoCarver screen. */
public final class ScreenConfiguration {

    public static final String FORMAT = "cryptocarver-screen-configuration";
    public static final int CURRENT_VERSION = 2;
    private static final int MAX_FIELDS = 1_000;
    private static final int MAX_VALUE_CHARS = 2_000_000;
    private static final int MAX_TOTAL_CHARS = 8_000_000;

    public record Value(String type, String value) {
        public Value {
            if (!"string".equals(type) && !"boolean".equals(type)) {
                throw new IllegalArgumentException("Unsupported configuration value type: " + type);
            }
            value = value == null ? "" : value;
            if (value.length() > MAX_VALUE_CHARS) {
                throw new IllegalArgumentException("Configuration value exceeds size limit");
            }
        }

        static Value from(Object value) {
            if (value instanceof Boolean bool) return new Value("boolean", Boolean.toString(bool));
            return new Value("string", value == null ? "" : String.valueOf(value));
        }

        Object toStateValue() {
            return "boolean".equals(type) ? Boolean.parseBoolean(value) : value;
        }
    }

    private final String format;
    private final int version;
    private final String operation;
    private final String module;
    private final String createdAt;
    private final boolean mayContainSecrets;
    private final SecretVisibilityProfile visibilityProfile;
    private final Map<String, Value> values;

    public ScreenConfiguration(String operation, String module, Map<String, Object> state, SecretVisibilityProfile visibilityProfile) {
        this(FORMAT, CURRENT_VERSION, operation, module, Instant.now().toString(), true, visibilityProfile, encode(state));
    }

    public ScreenConfiguration(String operation, String module, Map<String, Object> state, SecretVisibilityProfile visibilityProfile, boolean mayContainSecrets) {
        this(FORMAT, CURRENT_VERSION, operation, module, Instant.now().toString(), mayContainSecrets, visibilityProfile, encode(state));
    }

    private ScreenConfiguration(String format, int version, String operation, String module, String createdAt,
                                boolean mayContainSecrets, SecretVisibilityProfile visibilityProfile, Map<String, Value> values) {
        if (!FORMAT.equals(format)) throw new IllegalArgumentException("Unsupported configuration format");
        if (version < 1 || version > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported configuration version: " + version);
        }
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("Configuration operation is required");
        if (module == null || module.isBlank()) throw new IllegalArgumentException("Configuration module is required");
        this.format = format;
        this.version = version;
        this.operation = operation.trim();
        this.module = module.trim();
        this.createdAt = createdAt == null || createdAt.isBlank() ? Instant.now().toString() : createdAt;
        this.mayContainSecrets = mayContainSecrets;
        this.visibilityProfile = visibilityProfile == null ? SecretVisibilityProfile.FULL_LAB : visibilityProfile;
        this.values = validateValues(values);
    }

    public String toJson() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(this);
    }

    public static ScreenConfiguration fromJson(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("Configuration JSON is empty");
        Raw raw;
        try {
            raw = new Gson().fromJson(json, Raw.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid configuration JSON", e);
        }
        if (raw == null) throw new IllegalArgumentException("Configuration JSON is empty");
        return new ScreenConfiguration(raw.format, raw.version, raw.operation, raw.module, raw.createdAt,
                raw.mayContainSecrets, raw.visibilityProfile, raw.values);
    }

    public Map<String, Object> toState() {
        Map<String, Object> state = new LinkedHashMap<>();
        values.forEach((key, value) -> state.put(key, value.toStateValue()));
        return state;
    }

    public String format() { return format; }
    public int version() { return version; }
    public String operation() { return operation; }
    public String module() { return module; }
    public String createdAt() { return createdAt; }
    public boolean mayContainSecrets() { return mayContainSecrets; }
    public SecretVisibilityProfile visibilityProfile() { return visibilityProfile; }
    public Map<String, Value> values() { return values; }

    private static Map<String, Value> encode(Map<String, Object> state) {
        Map<String, Value> encoded = new LinkedHashMap<>();
        if (state != null) state.forEach((key, value) -> encoded.put(key, Value.from(value)));
        return encoded;
    }

    private static Map<String, Value> validateValues(Map<String, Value> source) {
        if (source == null) return Map.of();
        if (source.size() > MAX_FIELDS) throw new IllegalArgumentException("Configuration has too many fields");
        Map<String, Value> clean = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Value> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.length() > 300) {
                throw new IllegalArgumentException("Invalid configuration field name");
            }
            Value value = entry.getValue();
            if (value == null) throw new IllegalArgumentException("Null configuration value: " + key);
            total += value.value().length();
            if (total > MAX_TOTAL_CHARS) throw new IllegalArgumentException("Configuration exceeds total size limit");
            clean.put(key, value);
        }
        return Map.copyOf(clean);
    }

    private static final class Raw {
        String format;
        int version;
        String operation;
        String module;
        String createdAt;
        boolean mayContainSecrets;
        SecretVisibilityProfile visibilityProfile;
        Map<String, Value> values;
    }
}
