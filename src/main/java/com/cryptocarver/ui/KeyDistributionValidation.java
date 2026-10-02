package com.cryptocarver.ui;

/** A validation outcome carries localization and field keys, never the supplied material. */
final class KeyDistributionValidation extends Exception {
    private final String messageKey;
    private final String fieldKey;

    KeyDistributionValidation(String messageKey, String fieldKey) {
        super(messageKey);
        this.messageKey = messageKey;
        this.fieldKey = fieldKey;
    }

    String messageKey() { return messageKey; }
    String fieldKey() { return fieldKey; }
}
