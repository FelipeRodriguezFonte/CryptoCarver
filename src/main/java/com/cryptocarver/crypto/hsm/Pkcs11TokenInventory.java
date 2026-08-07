package com.cryptocarver.crypto.hsm;

import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;

/** Sanitized public token metadata. Serial numbers and model data are absent by design. */
public record Pkcs11TokenInventory(String label, String manufacturer, Set<String> flags) {
    public Pkcs11TokenInventory {
        label = sanitizeText(label);
        manufacturer = sanitizeText(manufacturer);
        flags = flags == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(flags));
    }

    private static String sanitizeText(String value) {
        if (value == null) return null;
        String sanitized = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return sanitized.isBlank() ? null : sanitized;
    }
}
