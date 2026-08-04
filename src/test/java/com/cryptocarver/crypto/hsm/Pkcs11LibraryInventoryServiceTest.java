package com.cryptocarver.crypto.hsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Pkcs11LibraryInventoryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void normalizesFixtureSlotsAndMechanismsInDeterministicOrder() throws IOException {
        Path library = fixtureLibrary();
        RecordingBridge bridge = new RecordingBridge();
        bridge.slots = List.of(
                new Pkcs11NativeBridge.NativeSlot(1, 42, true),
                new Pkcs11NativeBridge.NativeSlot(0, 7, true));
        bridge.tokens.put(42L, new Pkcs11NativeBridge.NativeToken(" Token B ", " Vendor ",
                Pkcs11NativeConstants.CKF_TOKEN_INITIALIZED));
        bridge.tokens.put(7L, new Pkcs11NativeBridge.NativeToken("Token A", "Vendor", 0));
        bridge.mechanisms.put(42L, List.of(0x40L, 0x01L));
        bridge.mechanisms.put(7L, List.of(0x1087L));
        bridge.mechanismInfo.put(key(42, 0x40), new Pkcs11NativeBridge.NativeMechanism(
                2048, 4096, Pkcs11NativeConstants.CKF_SIGN));
        bridge.mechanismInfo.put(key(42, 0x01), new Pkcs11NativeBridge.NativeMechanism(
                1024, 4096, Pkcs11NativeConstants.CKF_VERIFY));
        bridge.mechanismInfo.put(key(7, 0x1087), new Pkcs11NativeBridge.NativeMechanism(
                16, 32, Pkcs11NativeConstants.CKF_ENCRYPT | Pkcs11NativeConstants.CKF_DECRYPT));

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge).inventory(validated(library));

        assertEquals(Pkcs11InventoryResult.Status.OK, result.status());
        assertEquals(List.of(0, 1), result.slots().stream()
                .map(Pkcs11SlotInventory::slotListIndex).toList());
        assertEquals(List.of(7L, 42L), result.slots().stream()
                .map(Pkcs11SlotInventory::slotId).toList());
        assertEquals(List.of(1L, 64L), result.slots().get(1).mechanisms().stream()
                .map(Pkcs11MechanismInventory::mechanismId).toList());
        assertEquals("CKM_SHA256_RSA_PKCS", result.slots().get(1).mechanisms().get(1).name());
        assertEquals("Token B", result.slots().get(1).token().label());
        assertEquals(Set.of("TOKEN_INITIALIZED"), result.slots().get(1).token().flags());
        assertTrue(result.selectableSlot(1).isPresent());
        assertTrue(bridge.initialized);
        assertTrue(bridge.finalized);
        assertSafeEvidence(result);
    }

    @Test
    void distinguishesModuleWithoutSlotsAndFinalizes() throws IOException {
        RecordingBridge bridge = new RecordingBridge();
        bridge.slots = List.of();

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge)
                .inventory(validated(fixtureLibrary()));

        assertEquals(Pkcs11InventoryResult.Status.NO_SLOTS, result.status());
        assertTrue(result.slots().isEmpty());
        assertTrue(result.finalizationAttempted());
        assertTrue(result.finalizationSucceeded());
        assertSafeEvidence(result);
    }

    @Test
    void distinguishesSlotsWithoutTokenAndMechanismsWithoutAvailability() throws IOException {
        RecordingBridge noToken = new RecordingBridge();
        noToken.slots = List.of(new Pkcs11NativeBridge.NativeSlot(0, 19, false));
        Pkcs11InventoryResult noTokenResult = new Pkcs11LibraryInventoryService(noToken)
                .inventory(validated(fixtureLibrary()));
        assertEquals(Pkcs11InventoryResult.Status.SLOTS_WITHOUT_TOKEN, noTokenResult.status());
        assertFalse(noTokenResult.slots().get(0).selectable());
        assertTrue(noToken.finalized);

        RecordingBridge noMechanisms = new RecordingBridge();
        noMechanisms.slots = List.of(new Pkcs11NativeBridge.NativeSlot(0, 20, true));
        noMechanisms.tokens.put(20L, new Pkcs11NativeBridge.NativeToken("Token", "Vendor", 0));
        noMechanisms.mechanisms.put(20L, List.of());
        Pkcs11InventoryResult noMechanismResult = new Pkcs11LibraryInventoryService(noMechanisms)
                .inventory(validated(fixtureLibrary()));
        assertEquals(Pkcs11InventoryResult.Status.MECHANISMS_NOT_AVAILABLE, noMechanismResult.status());
        assertTrue(noMechanisms.finalized);
    }

    @Test
    void sanitizesNativeFailureAndStillFinalizes() throws IOException {
        RecordingBridge bridge = new RecordingBridge();
        bridge.slotsFailure = new Pkcs11NativeException("C_GetTokenInfo", 0xDEAD);
        bridge.slots = List.of(new Pkcs11NativeBridge.NativeSlot(0, 1, true));

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge)
                .inventory(validated(fixtureLibrary()));

        assertEquals(Pkcs11InventoryResult.Status.QUERY_ERROR, result.status());
        assertTrue(result.message().contains("consultar"));
        assertFalse(result.message().contains("DEAD"));
        assertFalse(result.message().contains("C_GetTokenInfo"));
        assertFalse(result.message().contains("java."));
        assertTrue(result.finalizationAttempted());
        assertTrue(result.finalizationSucceeded());
        assertSafeEvidence(result);
    }

    @Test
    void cancellationStillClosesTheNativeModule() throws IOException {
        RecordingBridge bridge = new RecordingBridge();
        bridge.slotsFailure = new CancellationException("cancelled by caller");

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge)
                .inventory(validated(fixtureLibrary()));

        assertEquals(Pkcs11InventoryResult.Status.QUERY_ERROR, result.status());
        assertTrue(bridge.finalized);
        assertTrue(result.finalizationSucceeded());
    }

    @Test
    void reportsFinalizationFailureWithoutLeakingNativeDetails() throws IOException {
        RecordingBridge bridge = new RecordingBridge();
        bridge.closeFailure = true;

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge)
                .inventory(validated(fixtureLibrary()));

        assertEquals(Pkcs11InventoryResult.Status.QUERY_ERROR, result.status());
        assertTrue(result.finalizationAttempted());
        assertFalse(result.finalizationSucceeded());
        assertTrue(result.message().contains("finalizarse"));
        assertFalse(result.message().contains("CAFE"));
    }

    @Test
    void distinguishesForeignInitializationWithoutFinalizingIt() throws IOException {
        RecordingBridge bridge = new RecordingBridge();
        bridge.initializeFailure = new Pkcs11NativeException(
                "C_Initialize", Pkcs11NativeConstants.CKR_CRYPTOKI_ALREADY_INITIALIZED);

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge)
                .inventory(validated(fixtureLibrary()));

        assertEquals(Pkcs11InventoryResult.Status.LIBRARY_ALREADY_INITIALIZED, result.status());
        assertFalse(result.finalizationAttempted());
        assertFalse(result.finalizationSucceeded());
        assertTrue(result.message().contains("otra instancia"));
    }

    @Test
    void doesNotAcceptAnUnvalidatedDiagnostic() throws IOException {
        RecordingBridge bridge = new RecordingBridge();
        Pkcs11DiagnosticResult failed = new Pkcs11DiagnosticResult(
                Pkcs11DiagnosticResult.Status.FILE_NOT_FOUND,
                fixtureLibrary(), "not valid", "Darwin", "aarch64", "64-bit",
                Set.of(".dylib"), true, false, false, false);

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge).inventory(failed);

        assertEquals(Pkcs11InventoryResult.Status.LIBRARY_NOT_VALIDATED, result.status());
        assertFalse(bridge.initialized);
        assertFalse(result.finalizationAttempted());
    }

    @Test
    void publicNoLoginSurfaceHasNoCredentialOrForbiddenTypes() {
        for (Class<?> type : List.of(Pkcs11LibraryInventoryService.class, Pkcs11NativeBridge.class,
                Pkcs11InventoryResult.class, Pkcs11SlotInventory.class, Pkcs11TokenInventory.class,
                Pkcs11MechanismInventory.class)) {
            for (Method method : type.getMethods()) {
                assertFalse(method.getName().toLowerCase().contains("login"));
                assertFalse(method.getName().toLowerCase().contains("password"));
                assertFalse(method.getName().toLowerCase().contains("pin"));
                assertFalse(method.getParameterCount() > 0
                        && java.util.Arrays.stream(method.getParameterTypes()).anyMatch(char[].class::equals));
                assertFalse(method.getReturnType().getName().equals("java.security.KeyStore"));
            }
        }
    }

    @Test
    void missingLibraryIsNotLoaded() {
        RecordingBridge bridge = new RecordingBridge();

        Pkcs11InventoryResult result = new Pkcs11LibraryInventoryService(bridge)
                .inventory(temporaryDirectory.resolve("missing-pkcs11-module"));

        assertEquals(Pkcs11InventoryResult.Status.LIBRARY_NOT_LOADABLE, result.status());
        assertFalse(bridge.initialized);
        assertFalse(result.authenticationAttempted());
    }

    private Path fixtureLibrary() throws IOException {
        return Files.createTempFile(temporaryDirectory, "validated-pkcs11-module-", ".so");
    }

    private static Pkcs11DiagnosticResult validated(Path library) {
        return new Pkcs11DiagnosticResult(Pkcs11DiagnosticResult.Status.OK, library,
                "ok", "Darwin", "aarch64", "64-bit (aarch64)",
                Set.of(".dylib", ".so"), true, true, true, false);
    }

    private static String key(long slotId, long mechanismId) {
        return slotId + ":" + mechanismId;
    }

    private static void assertSafeEvidence(Pkcs11InventoryResult result) {
        assertFalse(result.authenticationAttempted());
        assertFalse(result.userSessionOpened());
        assertFalse(result.keyStoreAccessed());
        assertFalse(result.objectQueryAttempted());
        assertNotNull(result.message());
    }

    private static final class RecordingBridge implements Pkcs11NativeBridge {
        private List<NativeSlot> slots = List.of();
        private final Map<Long, NativeToken> tokens = new HashMap<>();
        private final Map<Long, List<Long>> mechanisms = new HashMap<>();
        private final Map<String, NativeMechanism> mechanismInfo = new HashMap<>();
        private Exception slotsFailure;
        private Pkcs11NativeException initializeFailure;
        private boolean closeFailure;
        private boolean initialized;
        private boolean finalized;

        @Override
        public NativeSession initialize(Path library) throws Pkcs11NativeException {
            initialized = true;
            if (initializeFailure != null) throw initializeFailure;
            return new NativeSession() {
                @Override
                public List<NativeSlot> slots() throws Pkcs11NativeException {
                    if (slotsFailure instanceof Pkcs11NativeException failure) throw failure;
                    if (slotsFailure instanceof RuntimeException failure) throw failure;
                    return slots;
                }

                @Override
                public NativeToken token(long slotId) {
                    return tokens.get(slotId);
                }

                @Override
                public List<Long> mechanisms(long slotId) {
                    return mechanisms.getOrDefault(slotId, List.of());
                }

                @Override
                public NativeMechanism mechanism(long slotId, long mechanismId) {
                    return mechanismInfo.get(key(slotId, mechanismId));
                }

                @Override
                public void close() throws Pkcs11NativeException {
                    finalized = true;
                    if (closeFailure) throw new Pkcs11NativeException("C_Finalize", 0xCAFE);
                }
            };
        }
    }
}
