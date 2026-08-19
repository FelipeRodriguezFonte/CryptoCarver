package com.cryptocarver.crypto.hsm;

import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;

/** Public PKCS#11 mechanism capabilities. No mechanism parameters or objects are included. */
public record Pkcs11MechanismInventory(
        long mechanismId,
        String name,
        long minKeySize,
        long maxKeySize,
        Set<String> flags) {

    public Pkcs11MechanismInventory {
        if (mechanismId < 0) throw new IllegalArgumentException("mechanismId must be non-negative");
        if (minKeySize < 0 || maxKeySize < 0) {
            throw new IllegalArgumentException("mechanism key sizes must be non-negative");
        }
        name = name == null || name.isBlank() ? "CKM_UNKNOWN" : name;
        flags = flags == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(flags));
    }
}
