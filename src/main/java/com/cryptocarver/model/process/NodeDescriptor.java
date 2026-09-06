package com.cryptocarver.model.process;

import java.util.List;
import java.util.Objects;

/**
 * Declarative description of a process node type, including palette metadata and its parameters.
 */
public record NodeDescriptor(
    String type,                    // Node type identifier ("HASH", "ENCRYPT", ...)
    String category,                // Palette group ("Inputs", "Conversions", "Crypto", ...)
    String labelKey,                // i18n key for node type title
    String descriptionKey,          // i18n key for node type description
    String icon,                    // Emoji or symbol icon
    List<NodeParameter> parameters  // Configurable parameters
) {
    public NodeDescriptor {
        Objects.requireNonNull(type, "Node type cannot be null");
        Objects.requireNonNull(category, "Category cannot be null");
        Objects.requireNonNull(labelKey, "labelKey cannot be null");
        Objects.requireNonNull(descriptionKey, "descriptionKey cannot be null");
        icon = (icon == null || icon.isBlank()) ? "🧩" : icon;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
