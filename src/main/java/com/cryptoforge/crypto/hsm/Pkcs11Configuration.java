package com.cryptoforge.crypto.hsm;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Connection settings for a laboratory PKCS#11 token.
 *
 * <p>This model intentionally stores no PIN. A caller supplies it only when a
 * {@link Pkcs11Session} is opened. The {@code slotListIndex} is the index used
 * by the JDK SunPKCS11 provider, not a vendor-specific slot identifier.</p>
 */
public record Pkcs11Configuration(String name, Path library, int slotListIndex) {
    public Pkcs11Configuration {
        name = normalizeName(name);
        library = Objects.requireNonNull(library, "PKCS#11 library path is required").toAbsolutePath().normalize();
        if (slotListIndex < 0) {
            throw new IllegalArgumentException("PKCS#11 slot list index must be zero or greater");
        }
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "CryptoCarverToken";
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("PKCS#11 token name must contain letters or numbers");
        }
        return normalized;
    }

    /** Returns the temporary SunPKCS11 provider configuration; it contains no credentials. */
    String toSunPkcs11Configuration() {
        return "name = " + name + System.lineSeparator()
                + "library = " + library + System.lineSeparator()
                + "slotListIndex = " + slotListIndex + System.lineSeparator();
    }
}
