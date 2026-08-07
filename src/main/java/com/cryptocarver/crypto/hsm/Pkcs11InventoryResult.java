package com.cryptocarver.crypto.hsm;

import java.util.List;
import java.util.Optional;

/**
 * Safe outcome of a no-login PKCS#11 inventory.
 *
 * <p>The boolean evidence fields are intentionally explicit: this service
 * never authenticates, opens a user session, accesses a Java keystore or
 * queries token objects.</p>
 */
public record Pkcs11InventoryResult(
        Status status,
        String message,
        List<Pkcs11SlotInventory> slots,
        boolean authenticationAttempted,
        boolean userSessionOpened,
        boolean keyStoreAccessed,
        boolean objectQueryAttempted,
        boolean finalizationAttempted,
        boolean finalizationSucceeded) {

    public Pkcs11InventoryResult {
        if (status == null) throw new IllegalArgumentException("inventory status is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("inventory message is required");
        slots = slots == null ? List.of() : List.copyOf(slots);
        if (authenticationAttempted || userSessionOpened || keyStoreAccessed || objectQueryAttempted) {
            throw new IllegalArgumentException("the no-login inventory cannot report prohibited operations");
        }
        if (finalizationSucceeded && !finalizationAttempted) {
            throw new IllegalArgumentException("successful finalization requires an attempt");
        }
    }

    public boolean isSuccessful() {
        return status == Status.OK;
    }

    /** Returns the slot that can be persisted as a SunPKCS11 slotListIndex. */
    public Optional<Pkcs11SlotInventory> selectableSlot(int slotListIndex) {
        return slots.stream()
                .filter(Pkcs11SlotInventory::selectable)
                .filter(slot -> slot.slotListIndex() == slotListIndex)
                .findFirst();
    }

    public enum Status {
        OK,
        LIBRARY_NOT_VALIDATED,
        LIBRARY_NOT_LOADABLE,
        LIBRARY_ALREADY_INITIALIZED,
        NO_SLOTS,
        SLOTS_WITHOUT_TOKEN,
        MECHANISMS_NOT_AVAILABLE,
        QUERY_ERROR
    }
}
