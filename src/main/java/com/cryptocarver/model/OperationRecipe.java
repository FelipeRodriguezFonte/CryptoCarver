package com.cryptocarver.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Portable, non-secret operation configuration for laboratory replay. */
public final class OperationRecipe {
    public static final int CURRENT_VERSION = 2;
    private static final int MAX_PARAMETERS = 500;
    private static final int MAX_VALUE_CHARS = 1_000_000;
    private static final int MAX_TOTAL_CHARS = 4_000_000;

    public record Step(String operation, String inputType, String outputType, Map<String, String> parameters) {
        public Step {
            if (operation == null || operation.isBlank()) throw new IllegalArgumentException("Step operation is required");
            if (inputType != null && !isValidType(inputType)) throw new IllegalArgumentException("Invalid inputType: " + inputType);
            if (outputType != null && !isValidType(outputType)) throw new IllegalArgumentException("Invalid outputType: " + outputType);

            if (parameters == null) {
                parameters = Map.of();
            } else {
                Map<String, String> clean = new LinkedHashMap<>();
                int totalChars = 0;
                for (Map.Entry<String, String> e : parameters.entrySet()) {
                    if (clean.size() >= MAX_PARAMETERS) throw new IllegalArgumentException("Step has too many parameters");
                    String k = e.getKey(); String v = e.getValue() == null ? "" : e.getValue();
                    if (v.length() > MAX_VALUE_CHARS) throw new IllegalArgumentException("Parameter exceeds value length limit");
                    totalChars += v.length();
                    if (totalChars > MAX_TOTAL_CHARS) throw new IllegalArgumentException("Step exceeds total characters limit");
                    clean.put(k, v);
                }
                parameters = Map.copyOf(clean);
            }
        }
        private boolean isValidType(String t) {
            return "bytes".equals(t) || "text".equals(t) || "file-reference".equals(t) || "any".equals(t);
        }
    }

    private final int version;
    private final String operation; // V1 fallback or primary recipe name
    private final String createdAt;
    private final Map<String, String> parameters; // V1 parameters
    private final java.util.List<Step> steps; // V2 steps

    public OperationRecipe(String operation, java.util.List<OperationDetail> details, SecretVisibility visibility) {
        this(operation, List.of(new Step(operation, "any", "any", sanitizeDetails(details, visibility))));
    }

    public OperationRecipe(String name, java.util.List<Step> steps) {
        this(name, steps, Instant.now().toString());
    }

    private OperationRecipe(String name, java.util.List<Step> steps, String explicitCreatedAt) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Recipe name is required");
        if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("Recipe must have at least one step");

        for (int i = 0; i < steps.size() - 1; i++) {
            String out = steps.get(i).outputType();
            String in = steps.get(i+1).inputType();
            if (out != null && in != null && !out.equals("any") && !in.equals("any") && !out.equals(in)) {
                throw new IllegalArgumentException("Incompatible chain: step " + i + " outputs " + out + " but step " + (i+1) + " expects " + in);
            }
        }

        this.version = CURRENT_VERSION;
        this.operation = name.trim();
        this.createdAt = explicitCreatedAt != null ? explicitCreatedAt : Instant.now().toString();
        this.parameters = steps.get(0).parameters();
        this.steps = List.copyOf(steps);
    }

    @Deprecated
    public OperationRecipe(String operation, Map<String, String> parameters) {
        throw new UnsupportedOperationException("Map-based recipes are no longer supported for security reasons. Use structured OperationDetails instead.");
    }

    public String toJson() { return new GsonBuilder().setPrettyPrinting().create().toJson(this); }
    public static OperationRecipe fromJson(String json) {
        Raw raw = new Gson().fromJson(json, Raw.class);
        if (raw == null) throw new IllegalArgumentException("Recipe JSON is empty");
        if (raw.version == 1 || raw.steps == null || raw.steps.isEmpty()) {
            return new OperationRecipe(raw.operation, List.of(new Step(raw.operation, "any", "any", raw.parameters)), raw.createdAt);
        }
        return new OperationRecipe(raw.operation, raw.steps, raw.createdAt);
    }

    public int version() { return version; }
    public String operation() { return operation; }
    public String createdAt() { return createdAt; }
    public Map<String, String> parameters() { return parameters; }
    public java.util.List<Step> steps() { return steps; }

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
                if (effectiveVisibility != SecretVisibility.FULL_LAB) value = "***MASKED***";
            }

            if (value.length() > MAX_VALUE_CHARS) throw new IllegalArgumentException("Recipe parameter " + key + " exceeds " + MAX_VALUE_CHARS + " characters");
            totalChars += value.length();
            if (totalChars > MAX_TOTAL_CHARS) throw new IllegalArgumentException("Recipe exceeds " + MAX_TOTAL_CHARS + " total characters");
            clean.put(key, value);
        }
        return clean;
    }

    private static final class Raw { int version; String operation; String createdAt; Map<String, String> parameters; java.util.List<Step> steps; }
}
