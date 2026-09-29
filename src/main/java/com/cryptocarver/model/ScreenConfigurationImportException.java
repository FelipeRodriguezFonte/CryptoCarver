package com.cryptocarver.model;

/** Safe, typed failure raised while reading or decoding a screen configuration. */
public final class ScreenConfigurationImportException extends IllegalArgumentException {
    public enum Reason {
        WRONG_PASSWORD_OR_TAMPERED,
        NOT_A_CONFIGURATION,
        UNSUPPORTED_VERSION,
        UNREADABLE,
        EMPTY
    }

    private final Reason reason;

    public ScreenConfigurationImportException(Reason reason) {
        super("Screen configuration import failed: " + reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
