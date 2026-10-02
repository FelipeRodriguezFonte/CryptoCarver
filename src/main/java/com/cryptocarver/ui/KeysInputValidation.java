package com.cryptocarver.ui;

/** A presentation-neutral validation failure with a stable field identifier. */
final class KeysInputValidation extends IllegalArgumentException {
    private final String title;
    private final String field;

    KeysInputValidation(String title, String message, String field) {
        super(message);
        this.title = title;
        this.field = field;
    }
    String title() { return title; }
    String field() { return field; }
}
