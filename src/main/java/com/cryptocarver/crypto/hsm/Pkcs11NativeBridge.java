package com.cryptocarver.crypto.hsm;

import java.nio.file.Path;
import java.util.List;

/**
 * Narrow, injectable PKCS#11 ABI surface used by the local inventory.
 *
 * <p>This interface intentionally contains no session, login, password,
 * object, certificate or key operation. Implementations must initialize the
 * module before returning a session and must finalize it from {@link
 * NativeSession#close()}.</p>
 */
public interface Pkcs11NativeBridge {
    NativeSession initialize(Path library) throws Pkcs11NativeException;

    interface NativeSession extends AutoCloseable {
        List<NativeSlot> slots() throws Pkcs11NativeException;

        NativeToken token(long slotId) throws Pkcs11NativeException;

        List<Long> mechanisms(long slotId) throws Pkcs11NativeException;

        NativeMechanism mechanism(long slotId, long mechanismId) throws Pkcs11NativeException;

        @Override
        void close() throws Pkcs11NativeException;
    }

    /** A slot in the module's returned slot-list order. */
    record NativeSlot(int slotListIndex, long slotId, boolean tokenPresent) {
        public NativeSlot {
            if (slotListIndex < 0) throw new IllegalArgumentException("slotListIndex must be non-negative");
            if (slotId < 0) throw new IllegalArgumentException("slotId must be non-negative");
        }
    }

    /** Only public token fields needed by the inventory; serial/model are absent. */
    record NativeToken(String label, String manufacturer, long flags) { }

    /** Public mechanism information returned by C_GetMechanismInfo. */
    record NativeMechanism(long minKeySize, long maxKeySize, long flags) {
        public NativeMechanism {
            if (minKeySize < 0 || maxKeySize < 0) {
                throw new IllegalArgumentException("mechanism key sizes must be non-negative");
            }
        }
    }
}
