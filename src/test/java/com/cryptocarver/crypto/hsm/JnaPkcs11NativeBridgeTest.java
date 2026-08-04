package com.cryptocarver.crypto.hsm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class JnaPkcs11NativeBridgeTest {
    @Test
    void directModuleBindingDeclaresOnlyCGetFunctionList() throws ClassNotFoundException {
        Class<?> entryPoint = Class.forName(
                JnaPkcs11NativeBridge.class.getName() + "$Pkcs11Functions");

        assertEquals(List.of("C_GetFunctionList"), Arrays.stream(entryPoint.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList());
    }

    @Test
    void resolvesFunctionListBeforeAnyFunctionTableCall() throws Exception {
        List<String> calls = new ArrayList<>();
        FunctionTableFixture table = new FunctionTableFixture(calls);
        JnaPkcs11NativeBridge bridge = new JnaPkcs11NativeBridge(library -> () -> {
            calls.add("C_GetFunctionList");
            return table;
        });

        Pkcs11NativeBridge.NativeSession session = bridge.initialize(Path.of("fixture-module"));
        session.close();

        assertEquals(List.of("C_GetFunctionList", "C_Initialize", "C_Finalize"), calls);
    }

    @Test
    void alreadyInitializedDoesNotCreateSessionOrFinalizeForeignInitialization() {
        List<String> calls = new ArrayList<>();
        FunctionTableFixture table = new FunctionTableFixture(calls);
        table.initializeReturnCode = Pkcs11NativeConstants.CKR_CRYPTOKI_ALREADY_INITIALIZED;
        JnaPkcs11NativeBridge bridge = new JnaPkcs11NativeBridge(library -> () -> {
            calls.add("C_GetFunctionList");
            return table;
        });

        Pkcs11NativeException failure = assertThrows(Pkcs11NativeException.class,
                () -> bridge.initialize(Path.of("fixture-module")));

        assertEquals("C_Initialize", failure.operation());
        assertTrueAlreadyInitialized(failure);
        assertEquals(List.of("C_GetFunctionList", "C_Initialize"), calls);
        assertFalse(calls.contains("C_Finalize"));
    }

    @Test
    void functionListAndTokenStructuresUseNativeHeaderAlignment() {
        int functionPointerCount = 68; // v2 CK_FUNCTION_LIST, as declared by the OASIS header.
        JnaPkcs11NativeBridge.FunctionList functionList = new JnaPkcs11NativeBridge.FunctionList();
        int firstPointerOffset = align(2, Native.POINTER_SIZE);
        assertEquals(firstPointerOffset, functionList.nativeFieldOffset("cInitialize"));
        assertEquals(firstPointerOffset + Native.POINTER_SIZE,
                functionList.nativeFieldOffset("cFinalize"));
        assertEquals(align(firstPointerOffset + functionPointerCount * Native.POINTER_SIZE,
                        Native.POINTER_SIZE), functionList.size());

        JnaPkcs11NativeBridge.TokenInfo tokenInfo = new JnaPkcs11NativeBridge.TokenInfo();
        int tokenFixedPrefix = 32 + 32 + 16 + 16;
        assertEquals(tokenFixedPrefix, tokenInfo.nativeFieldOffset("flags"));
        assertEquals(align(tokenFixedPrefix + (11 * NativeLong.SIZE) + 2 + 2 + 16,
                        NativeLong.SIZE), tokenInfo.size());
        assertEquals(align(3 * NativeLong.SIZE, NativeLong.SIZE),
                new JnaPkcs11NativeBridge.MechanismInfo().size());

        assertEquals(2, new JnaPkcs11NativeBridge.Version().size());
    }

    private static int align(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : value + alignment - remainder;
    }

    private static void assertTrueAlreadyInitialized(Pkcs11NativeException failure) {
        if (!failure.isAlreadyInitialized()) {
            throw new AssertionError("Expected CKR_CRYPTOKI_ALREADY_INITIALIZED");
        }
    }

    private static final class FunctionTableFixture implements JnaPkcs11NativeBridge.FunctionTable {
        private final List<String> calls;
        private long initializeReturnCode = Pkcs11NativeConstants.CKR_OK;

        private FunctionTableFixture(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public NativeLong initialize() {
            calls.add("C_Initialize");
            return new NativeLong(initializeReturnCode);
        }

        @Override
        public NativeLong finalizeModule(Pointer reserved) {
            calls.add("C_Finalize");
            return new NativeLong(Pkcs11NativeConstants.CKR_OK);
        }

        @Override
        public NativeLong getSlotList(byte tokenPresent, Pointer slotList, NativeLongByReference count) {
            calls.add("C_GetSlotList");
            return new NativeLong(Pkcs11NativeConstants.CKR_OK);
        }

        @Override
        public NativeLong getTokenInfo(NativeLong slotId, JnaPkcs11NativeBridge.TokenInfo info) {
            calls.add("C_GetTokenInfo");
            return new NativeLong(Pkcs11NativeConstants.CKR_OK);
        }

        @Override
        public NativeLong getMechanismList(
                NativeLong slotId, Pointer mechanismList, NativeLongByReference count) {
            calls.add("C_GetMechanismList");
            return new NativeLong(Pkcs11NativeConstants.CKR_OK);
        }

        @Override
        public NativeLong getMechanismInfo(
                NativeLong slotId, NativeLong mechanism, JnaPkcs11NativeBridge.MechanismInfo info) {
            calls.add("C_GetMechanismInfo");
            return new NativeLong(Pkcs11NativeConstants.CKR_OK);
        }
    }
}
