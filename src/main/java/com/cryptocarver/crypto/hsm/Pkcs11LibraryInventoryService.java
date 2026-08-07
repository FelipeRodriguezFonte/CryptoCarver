package com.cryptocarver.crypto.hsm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only local inventory of a validated PKCS#11 module.
 *
 * <p>The service uses only the public PKCS#11 slot/token/mechanism functions.
 * It never receives a PIN or password, creates a Java keystore, opens a user
 * session or enumerates objects. The injected bridge is also the boundary used
 * by tests, so no native library is needed for deterministic fixture tests.</p>
 */
public final class Pkcs11LibraryInventoryService {
    private final Pkcs11NativeBridge bridge;

    public Pkcs11LibraryInventoryService() {
        this(new JnaPkcs11NativeBridge());
    }

    public Pkcs11LibraryInventoryService(Pkcs11NativeBridge bridge) {
        this.bridge = java.util.Objects.requireNonNull(bridge, "PKCS#11 bridge is required");
    }

    /**
     * Inventories only a library whose FUNC-05A result was successful. The
     * diagnostic path is consumed internally and is never returned or placed
     * in an error message.
     */
    public Pkcs11InventoryResult inventory(Pkcs11DiagnosticResult validatedLibrary) {
        if (validatedLibrary == null || !validatedLibrary.isSuccessful()
                || validatedLibrary.normalizedLibrary() == null) {
            return result(Pkcs11InventoryResult.Status.LIBRARY_NOT_VALIDATED,
                    "La biblioteca PKCS#11 debe validarse correctamente antes de inventariarla.",
                    List.of(), false, false);
        }
        return inventory(validatedLibrary.normalizedLibrary());
    }

    /**
     * Convenience entry point for callers that already performed validation.
     * It repeats only a local regular-file check and does not inspect contents.
     */
    public Pkcs11InventoryResult inventory(Path validatedLibrary) {
        final Path normalized;
        try {
            if (validatedLibrary == null) {
                return result(Pkcs11InventoryResult.Status.LIBRARY_NOT_VALIDATED,
                        "La biblioteca PKCS#11 debe validarse correctamente antes de inventariarla.",
                        List.of(), false, false);
            }
            normalized = validatedLibrary.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
                return result(Pkcs11InventoryResult.Status.LIBRARY_NOT_LOADABLE,
                        "La biblioteca PKCS#11 no está disponible para carga local.",
                        List.of(), false, false);
            }
        } catch (RuntimeException failure) {
            return result(Pkcs11InventoryResult.Status.LIBRARY_NOT_LOADABLE,
                    "La biblioteca PKCS#11 no está disponible para carga local.",
                    List.of(), false, false);
        }

        return collect(normalized);
    }

    private Pkcs11InventoryResult collect(Path library) {
        Pkcs11NativeBridge.NativeSession session = null;
        List<Pkcs11SlotInventory> slots = new ArrayList<>();
        Pkcs11InventoryResult.Status status = Pkcs11InventoryResult.Status.OK;
        String message = "Inventario PKCS#11 obtenido sin autenticación.";
        boolean finalizationAttempted = false;
        boolean finalizationSucceeded = false;

        try {
            session = bridge.initialize(library);
            if (session == null) {
                status = Pkcs11InventoryResult.Status.LIBRARY_NOT_LOADABLE;
                message = "La biblioteca PKCS#11 no se pudo cargar localmente.";
            } else {
                List<Pkcs11NativeBridge.NativeSlot> nativeSlots = session.slots();
                if (nativeSlots == null || nativeSlots.isEmpty()) {
                    status = Pkcs11InventoryResult.Status.NO_SLOTS;
                    message = "El módulo PKCS#11 no informa slots.";
                } else {
                    List<Pkcs11NativeBridge.NativeSlot> orderedSlots = nativeSlots.stream()
                            .sorted(Comparator.comparingInt(Pkcs11NativeBridge.NativeSlot::slotListIndex)
                                    .thenComparingLong(Pkcs11NativeBridge.NativeSlot::slotId))
                            .toList();
                    boolean tokenFound = false;
                    boolean mechanismFound = false;
                    for (Pkcs11NativeBridge.NativeSlot nativeSlot : orderedSlots) {
                        if (!nativeSlot.tokenPresent()) {
                            slots.add(new Pkcs11SlotInventory(
                                    nativeSlot.slotListIndex(), nativeSlot.slotId(), false, null, List.of()));
                            continue;
                        }

                        tokenFound = true;
                        Pkcs11NativeBridge.NativeToken nativeToken = session.token(nativeSlot.slotId());
                        Pkcs11TokenInventory token = new Pkcs11TokenInventory(
                                nativeToken == null ? null : nativeToken.label(),
                                nativeToken == null ? null : nativeToken.manufacturer(),
                                nativeToken == null
                                        ? Set.of()
                                        : Pkcs11NativeConstants.tokenFlags(nativeToken.flags()));

                        List<Long> mechanismIds = session.mechanisms(nativeSlot.slotId());
                        List<Pkcs11MechanismInventory> mechanisms = new ArrayList<>();
                        if (mechanismIds != null) {
                            for (Long mechanismId : mechanismIds.stream()
                                    .filter(id -> id != null && id >= 0)
                                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new))) {
                                Pkcs11NativeBridge.NativeMechanism nativeMechanism =
                                        session.mechanism(nativeSlot.slotId(), mechanismId);
                                if (nativeMechanism == null) continue;
                                mechanisms.add(new Pkcs11MechanismInventory(
                                        mechanismId,
                                        Pkcs11NativeConstants.mechanismName(mechanismId),
                                        nativeMechanism.minKeySize(),
                                        nativeMechanism.maxKeySize(),
                                        Pkcs11NativeConstants.mechanismFlags(nativeMechanism.flags())));
                            }
                        }
                        mechanismFound |= !mechanisms.isEmpty();
                        mechanisms.sort(Comparator.comparingLong(Pkcs11MechanismInventory::mechanismId));
                        slots.add(new Pkcs11SlotInventory(
                                nativeSlot.slotListIndex(), nativeSlot.slotId(), true, token, mechanisms));
                    }
                    if (!tokenFound) {
                        status = Pkcs11InventoryResult.Status.SLOTS_WITHOUT_TOKEN;
                        message = "El módulo informa slots, pero no hay ningún token presente.";
                    } else if (!mechanismFound) {
                        status = Pkcs11InventoryResult.Status.MECHANISMS_NOT_AVAILABLE;
                        message = "El token está presente, pero no informa mecanismos disponibles.";
                    }
                }
            }
        } catch (Pkcs11NativeException failure) {
            if ("C_Initialize".equals(failure.operation()) && failure.isAlreadyInitialized()) {
                status = Pkcs11InventoryResult.Status.LIBRARY_ALREADY_INITIALIZED;
                message = "El módulo PKCS#11 ya estaba inicializado por otra instancia; no se ha finalizado esa inicialización.";
            } else if ("C_Initialize".equals(failure.operation())
                    || "C_GetFunctionList".equals(failure.operation())) {
                status = Pkcs11InventoryResult.Status.LIBRARY_NOT_LOADABLE;
                message = "La biblioteca PKCS#11 no se pudo cargar localmente.";
            } else {
                status = Pkcs11InventoryResult.Status.QUERY_ERROR;
                message = "No se pudo consultar el inventario público del módulo PKCS#11.";
            }
        } catch (LinkageError failure) {
            status = Pkcs11InventoryResult.Status.LIBRARY_NOT_LOADABLE;
            message = "La biblioteca PKCS#11 no es compatible con la arquitectura local o no se puede cargar.";
        } catch (RuntimeException failure) {
            status = Pkcs11InventoryResult.Status.QUERY_ERROR;
            message = "No se pudo consultar el inventario público del módulo PKCS#11.";
        } finally {
            if (session != null) {
                finalizationAttempted = true;
                try {
                    session.close();
                    finalizationSucceeded = true;
                } catch (Pkcs11NativeException failure) {
                    status = Pkcs11InventoryResult.Status.QUERY_ERROR;
                    message = "El módulo PKCS#11 no pudo finalizarse de forma segura.";
                } catch (LinkageError | RuntimeException failure) {
                    status = Pkcs11InventoryResult.Status.QUERY_ERROR;
                    message = "El módulo PKCS#11 no pudo finalizarse de forma segura.";
                }
            }
        }

        return result(status, message, slots, finalizationAttempted, finalizationSucceeded);
    }

    private static Pkcs11InventoryResult result(
            Pkcs11InventoryResult.Status status,
            String message,
            List<Pkcs11SlotInventory> slots,
            boolean finalizationAttempted,
            boolean finalizationSucceeded) {
        return new Pkcs11InventoryResult(status, message, slots,
                false, false, false, false, finalizationAttempted, finalizationSucceeded);
    }
}
