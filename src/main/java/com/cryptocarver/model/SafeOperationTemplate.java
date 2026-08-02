package com.cryptocarver.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Model representing a safe, non-secret operation template.
 *
 * <p>Templates contain only non-sensitive configuration parameters (e.g. algorithms,
 * modes, padding, format encodings). They never contain keys, passwords, PINs,
 * IVs, nonces, inputs, outputs, or redacted secrets.</p>
 */
public final class SafeOperationTemplate {

    public static final String CURRENT_VERSION = "1.0";
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_PARAMETERS = 20;

    private String formatVersion = CURRENT_VERSION;
    private String id;
    private String name;
    private String module;
    private String createdAt;
    private String updatedAt;
    private String description;
    private Map<String, String> parameters = new LinkedHashMap<>();

    public SafeOperationTemplate() {
        this.id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public SafeOperationTemplate(String name, String module, String description, Map<String, String> parameters) {
        this();
        setName(name);
        setModule(module);
        setDescription(description);
        setParameters(parameters);
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(String formatVersion) {
        if (formatVersion == null || !CURRENT_VERSION.equals(formatVersion.trim())) {
            throw new IllegalArgumentException("Unsupported template version: " + formatVersion);
        }
        this.formatVersion = formatVersion.trim();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Template name exceeds maximum length of " + MAX_NAME_LENGTH + " characters");
        }
        this.name = trimmed;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("Template module cannot be empty");
        }
        this.module = module.trim();
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt == null ? Instant.now().toString() : createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt == null ? Instant.now().toString() : updatedAt;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        if (description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Template description exceeds maximum length of " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        this.description = description == null ? "" : description.trim();
    }

    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public void setParameters(Map<String, String> params) {
        if (params == null) {
            this.parameters = new LinkedHashMap<>();
            return;
        }
        if (params.size() > MAX_PARAMETERS) {
            throw new IllegalArgumentException("Template parameters exceed maximum limit of " + MAX_PARAMETERS);
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                String key = entry.getKey().trim();
                String value = entry.getValue() == null ? "" : entry.getValue().trim();
                copy.put(key, SafeTemplateAllowlist.normalizeFormatValue(key, value));
            }
        }
        this.parameters = copy;
    }

    public void updateTimestamp() {
        this.updatedAt = Instant.now().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafeOperationTemplate template = (SafeOperationTemplate) o;
        return Objects.equals(id, template.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
