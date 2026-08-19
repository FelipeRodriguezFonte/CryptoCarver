package com.cryptocarver.crypto.hsm;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Safe, structured outcome of a local PKCS#11 library diagnostic.
 *
 * <p>The result deliberately contains no authentication input and no token
 * object material. {@link #authenticationAttempted()} is always
 * {@code false}; it is exposed to make that guarantee explicit to callers and
 * UI code.</p>
 */
public record Pkcs11DiagnosticResult(
        Status status,
        Path normalizedLibrary,
        String message,
        String osName,
        String osArchitecture,
        String jvmArchitecture,
        Set<String> expectedExtensions,
        boolean extensionMatches,
        boolean readable,
        boolean sunPkcs11Configured,
        boolean authenticationAttempted) {

    public Pkcs11DiagnosticResult {
        if (status == null) {
            throw new IllegalArgumentException("PKCS#11 diagnostic status is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("PKCS#11 diagnostic message is required");
        }
        expectedExtensions = expectedExtensions == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(expectedExtensions));
    }

    public boolean isSuccessful() {
        return status == Status.OK;
    }

    /** Structured diagnostic states, from local path checks to provider setup. */
    public enum Status {
        OK,
        INVALID_PATH,
        FILE_NOT_FOUND,
        NOT_REGULAR_FILE,
        ACCESS_DENIED,
        NATIVE_LIBRARY_INCOMPATIBLE,
        SUNPKCS11_CONFIGURATION_REJECTED,
        SUNPKCS11_UNAVAILABLE,
        TEMPORARY_CONFIGURATION_FAILED
    }
}
