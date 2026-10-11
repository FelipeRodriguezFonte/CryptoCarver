package com.cryptocarver.model.help;

import java.util.List;

/** One user-selectable action within an operation. */
public record ActionHelp(String id, String titleKey, String descriptionKey, List<FieldHelp> fields,
                         List<OptionHelp> options, String exampleKey, String referenceId) {
    public ActionHelp {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Action ID is required");
        titleKey = titleKey == null ? "" : titleKey;
        descriptionKey = descriptionKey == null ? "" : descriptionKey;
        fields = fields == null ? List.of() : List.copyOf(fields);
        options = options == null ? List.of() : List.copyOf(options);
        exampleKey = exampleKey == null ? "" : exampleKey;
        referenceId = referenceId == null ? "" : referenceId;
    }
}
