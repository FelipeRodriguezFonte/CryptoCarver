package com.cryptocarver.crypto;

public class VerificationResult {
    private final boolean success;
    private final String message;

    public VerificationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
