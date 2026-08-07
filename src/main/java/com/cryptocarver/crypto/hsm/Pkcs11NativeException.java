package com.cryptocarver.crypto.hsm;

/**
 * Sanitized failure from the small PKCS#11 native bridge.
 *
 * <p>The return code is retained for diagnostics and tests, while the public
 * inventory service deliberately maps it to a stable user-facing category.
 * Vendor text, paths and native stack traces never cross this boundary.</p>
 */
public final class Pkcs11NativeException extends Exception {
    private final String operation;
    private final long returnCode;

    public Pkcs11NativeException(String operation, long returnCode) {
        super("PKCS#11 operation failed");
        this.operation = operation == null || operation.isBlank() ? "unknown" : operation;
        this.returnCode = returnCode;
    }

    public String operation() {
        return operation;
    }

    public long returnCode() {
        return returnCode;
    }

    public boolean isAlreadyInitialized() {
        return returnCode == Pkcs11NativeConstants.CKR_CRYPTOKI_ALREADY_INITIALIZED;
    }
}
