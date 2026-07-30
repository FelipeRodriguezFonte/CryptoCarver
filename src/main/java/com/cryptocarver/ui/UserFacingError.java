package com.cryptocarver.ui;

/**
 * Immutable user-facing error model for non-blocking actionable error reporting.
 */
public record UserFacingError(
        String title,
        String detail,
        String remedy,
        String fieldKey,
        Throwable cause
) {
    public UserFacingError(String title, String detail, String remedy) {
        this(title, detail, remedy, null, null);
    }

    public UserFacingError(String title, String detail, String remedy, String fieldKey) {
        this(title, detail, remedy, fieldKey, null);
    }

    public UserFacingError(String title, String detail, String remedy, Throwable cause) {
        this(title, detail, remedy, null, cause);
    }
}
