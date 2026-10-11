package com.cryptocarver.model.help;

/** Help text keys and control-independent identity for one semantic field. */
public record FieldHelp(String id, String labelKey, String descriptionKey, String ruleId) {
    public FieldHelp {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Field ID is required");
        labelKey = labelKey == null ? "" : labelKey;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        ruleId = ruleId == null ? "" : ruleId;
    }
}
