package com.cryptocarver.model.help;

/** A technical option code and localized explanation, example, and failure keys. */
public record OptionHelp(String code, String descriptionKey, String ruleId, String fieldId,
                         String exampleKey, String errorKey) {
    public OptionHelp(String code, String descriptionKey, String ruleId) {
        this(code, descriptionKey, ruleId, "", "", "");
    }
    public OptionHelp(String code, String descriptionKey, String ruleId, String fieldId) {
        this(code, descriptionKey, ruleId, fieldId, "", "");
    }
    public OptionHelp {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Option code is required");
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        ruleId = ruleId == null ? "" : ruleId;
        fieldId = fieldId == null ? "" : fieldId;
        exampleKey = exampleKey == null ? "" : exampleKey;
        errorKey = errorKey == null ? "" : errorKey;
    }
}
