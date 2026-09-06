package com.cryptocarver.model.process;

import java.util.List;
import java.util.Objects;

/**
 * Declarative descriptor for a single configurable node parameter.
 */
public record NodeParameter(
    String key,                 // Configuration map key
    String labelKey,            // i18n key for display label
    ParameterKind kind,         // UI input control type
    List<String> options,       // Available values for COMBO
    String defaultValue,        // Initial/default configuration value
    boolean sensitive,          // Whether this is sensitive secret data (never serialized)
    String helpKey,             // Optional i18n key for contextual help/tooltip
    String visibleWhen          // Optional conditional visibility ("otherKey=targetValue")
) {
    public NodeParameter {
        Objects.requireNonNull(key, "Parameter key cannot be null");
        Objects.requireNonNull(labelKey, "labelKey cannot be null");
        Objects.requireNonNull(kind, "ParameterKind cannot be null");
        options = options == null ? List.of() : List.copyOf(options);
    }

    public NodeParameter(String key, String labelKey, ParameterKind kind, List<String> options,
                         String defaultValue, boolean sensitive) {
        this(key, labelKey, kind, options, defaultValue, sensitive, null, null);
    }

    public NodeParameter(String key, String labelKey, ParameterKind kind, String defaultValue) {
        this(key, labelKey, kind, List.of(), defaultValue, false, null, null);
    }

    public NodeParameter(String key, String labelKey, ParameterKind kind, List<String> options, String defaultValue) {
        this(key, labelKey, kind, options, defaultValue, false, null, null);
    }

    public NodeParameter(String key, String labelKey, ParameterKind kind, String defaultValue,
                         boolean sensitive, String helpKey, String visibleWhen) {
        this(key, labelKey, kind, List.of(), defaultValue, sensitive, helpKey, visibleWhen);
    }

    public NodeParameter(String key, String labelKey, ParameterKind kind, String defaultValue, String visibleWhen) {
        this(key, labelKey, kind, List.of(), defaultValue, false, null, visibleWhen);
    }

    public static NodeParameter sensitivePassword(String key, String labelKey) {
        return new NodeParameter(key, labelKey, ParameterKind.PASSWORD, List.of(), "", true, null, null);
    }

    public static NodeParameter sensitivePassword(String key, String labelKey, String visibleWhen) {
        return new NodeParameter(key, labelKey, ParameterKind.PASSWORD, List.of(), "", true, null, visibleWhen);
    }
}
