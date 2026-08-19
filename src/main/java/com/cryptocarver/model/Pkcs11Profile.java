package com.cryptocarver.model;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A persistent profile for a PKCS#11 hardware token connection.
 * <p>
 * This record deliberately omits any PIN or password field to enforce
 * that sensitive credentials are never stored on disk. The user must
 * always supply the PIN explicitly when opening a session.
 * </p>
 *
 * @param name    A descriptive label for the token (e.g., "Nitrokey HSM").
 * @param library The absolute path to the native PKCS#11 driver (e.g., ".so", ".dylib", ".dll").
 * @param slot    The logical slot index for the JVM SunPKCS11 provider (usually 0).
 */
public record Pkcs11Profile(String name, String library, int slot) {
    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_LIBRARY_LENGTH = 4096;

    public Pkcs11Profile {
        name = normalizeName(name);
        library = normalizeLibrary(library);
        if (slot < 0) {
            throw new IllegalArgumentException("PKCS#11 slot must be zero or greater");
        }
    }

    /** Alias matching the SunPKCS11 terminology while preserving the old API. */
    public int slotListIndex() {
        return slot;
    }

    /** Stable comparison key used to enforce case-insensitive name uniqueness. */
    public static String normalizedNameKey(String value) {
        return normalizeName(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("PKCS#11 profile name is required");
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("PKCS#11 profile name is required");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("PKCS#11 profile name is too long");
        }
        return normalized;
    }

    private static String normalizeLibrary(String value) {
        if (value == null) {
            throw new IllegalArgumentException("PKCS#11 library path is required");
        }
        String candidate = value.trim();
        if (candidate.isBlank()) {
            throw new IllegalArgumentException("PKCS#11 library path is required");
        }
        if (candidate.length() > MAX_LIBRARY_LENGTH) {
            throw new IllegalArgumentException("PKCS#11 library path is too long");
        }

        final Path path;
        try {
            path = Path.of(candidate);
        } catch (InvalidPathException invalidPath) {
            throw new IllegalArgumentException("PKCS#11 library path is invalid");
        }
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("PKCS#11 library path must be absolute");
        }
        String normalized = path.normalize().toString();
        if (normalized.length() > MAX_LIBRARY_LENGTH) {
            throw new IllegalArgumentException("PKCS#11 library path is too long");
        }
        return normalized;
    }
}
