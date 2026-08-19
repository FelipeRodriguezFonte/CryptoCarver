package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UiNavigationRegistryTest {

    @Test
    void everyCatalogOperationHasADesktopRoute() {
        for (OperationDescriptor operation : OperationRegistry.getInstance().getAll()) {
            assertTrue(UiNavigationRegistry.resolve(operation.getNavigationPath()).isPresent(),
                    () -> "Missing desktop route for " + operation.getId()
                            + " (" + operation.getNavigationPath() + ")");
        }
    }

    @Test
    void aliasesResolveToTheSameTypedDestination() {
        assertEquals(UiNavigationRegistry.resolve("Certificate Chain"),
                UiNavigationRegistry.resolve("Validate Cert Chain"));
        assertEquals(UiNavigationRegistry.resolve("CMS/PKCS#7 Operations"),
                UiNavigationRegistry.resolve("CMS Verify"));
        assertEquals(UiNavigationRegistry.resolve("OpenPGP (GPG Compatible)"),
                UiNavigationRegistry.resolve("GPG"));
    }

    @Test
    void specialRoutesKeepTheirTypedVariants() {
        assertEquals(UiNavigationRegistry.Variant.ASN1_DECODE,
                UiNavigationRegistry.resolve("Decode ASN.1").orElseThrow().variant());
        assertEquals(UiNavigationRegistry.Variant.ASN1_ENCODE,
                UiNavigationRegistry.resolve("Encode ASN.1").orElseThrow().variant());
        assertEquals(UiNavigationRegistry.Variant.HISTORY_EXPORT,
                UiNavigationRegistry.resolve("Export History").orElseThrow().variant());
    }

    @Test
    void legacyDynamicExecutionNamesResolveToTheirOriginatingWorkspace() {
        assertEquals(UiNavigationRegistry.resolve("Key Derivation (KDF)"),
                UiNavigationRegistry.resolve("Derive - HKDF-SHA256"));
        assertEquals(UiNavigationRegistry.resolve("Key Sharing (XOR Split/Combine)"),
                UiNavigationRegistry.resolve("Split - 3 components"));
        assertEquals(UiNavigationRegistry.resolve("Generate Certificate"),
                UiNavigationRegistry.resolve("Generate Certificate - RSA 2048"));
    }

    @Test
    void publishedRouteMapIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () ->
                UiNavigationRegistry.routes().put("Unexpected", new UiNavigationRegistry.Route(
                        UiNavigationRegistry.Module.GENERIC, "Unexpected")));
    }
}
