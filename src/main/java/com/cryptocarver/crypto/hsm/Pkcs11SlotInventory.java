package com.cryptocarver.crypto.hsm;

import java.util.List;

/** One selectable slot and, when present, its public token/mechanism metadata. */
public record Pkcs11SlotInventory(
        int slotListIndex,
        long slotId,
        boolean tokenPresent,
        Pkcs11TokenInventory token,
        List<Pkcs11MechanismInventory> mechanisms) {

    public Pkcs11SlotInventory {
        if (slotListIndex < 0) throw new IllegalArgumentException("slotListIndex must be non-negative");
        if (slotId < 0) throw new IllegalArgumentException("slotId must be non-negative");
        if (!tokenPresent && token != null) {
            throw new IllegalArgumentException("a slot without a token cannot expose token metadata");
        }
        if (mechanisms == null) mechanisms = List.of();
        mechanisms = List.copyOf(mechanisms);
    }

    public boolean selectable() {
        return tokenPresent && token != null;
    }
}
