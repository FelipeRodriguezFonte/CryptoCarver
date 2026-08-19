package com.cryptocarver.ui;

/** Recoverable validation failure carrying the real FXML field that needs correction. */
final class FieldValidationException extends IllegalArgumentException {
    private final String fieldKey;

    FieldValidationException(String message, String fieldKey) {
        super(message);
        this.fieldKey = fieldKey;
    }

    FieldValidationException(String message, Throwable cause, String fieldKey) {
        super(message, cause);
        this.fieldKey = fieldKey;
    }

    String fieldKey() {
        return fieldKey;
    }
}
