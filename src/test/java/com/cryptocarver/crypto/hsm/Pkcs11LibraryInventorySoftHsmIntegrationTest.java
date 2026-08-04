package com.cryptocarver.crypto.hsm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Optional no-login SoftHSM check; it does not use a PIN or inspect objects. */
@Tag("integration")
class Pkcs11LibraryInventorySoftHsmIntegrationTest {
    @Test
    void inventoriesAtLeastOneSlotAndPublicMechanismsWithoutLogin() {
        String module = System.getenv("SOFTHSM2_MODULE");
        Assumptions.assumeTrue(module != null && !module.isBlank(),
                "SoftHSM is optional; set SOFTHSM2_MODULE to run this integration check");

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService().inventory(Path.of(module));

        assertTrue(result.status() == Pkcs11InventoryResult.Status.OK
                        || result.status() == Pkcs11InventoryResult.Status.MECHANISMS_NOT_AVAILABLE,
                () -> "Unexpected sanitized inventory status: " + result.status());
        assertFalse(result.slots().isEmpty(), "SoftHSM should expose at least one slot");
        assertTrue(result.slots().stream().anyMatch(slot -> !slot.mechanisms().isEmpty()),
                "SoftHSM should expose public mechanisms without login");
        assertFalse(result.authenticationAttempted());
        assertFalse(result.userSessionOpened());
        assertFalse(result.keyStoreAccessed());
        assertFalse(result.objectQueryAttempted());
        assertTrue(result.finalizationAttempted());
        assertTrue(result.finalizationSucceeded());

        String slotEvidence = result.slots().stream()
                .map(slot -> "index=" + slot.slotListIndex()
                        + ",id=" + slot.slotId()
                        + ",mechanisms=" + slot.mechanisms().stream()
                                .map(mechanism -> mechanism.mechanismId() + ":" + mechanism.name())
                                .toList())
                .toList()
                .toString();
        System.out.println("[pkcs11-integration] real public inventory: " + slotEvidence);
    }
}
