package com.cryptocarver.ui;

import com.cryptocarver.model.help.HelpContext;

/**
 * Immutable user-facing error model for non-blocking actionable error reporting.
 */
public record UserFacingError(
        String title,
        String detail,
        String remedy,
        String fieldKey,
        Throwable cause,
        HelpContext helpContext
) {
    public UserFacingError(String title, String detail, String remedy) {
        this(title, detail, remedy, null, null, null);
    }

    public UserFacingError(String title, String detail, String remedy, String fieldKey) {
        this(title, detail, remedy, fieldKey, null, null);
    }

    public UserFacingError(String title, String detail, String remedy, Throwable cause) {
        this(title, detail, remedy, null, cause, null);
    }

    /** Preserves the existing five-argument constructor for current consumers. */
    public UserFacingError(String title, String detail, String remedy, String fieldKey, Throwable cause) {
        this(title, detail, remedy, fieldKey, cause, null);
    }
}
