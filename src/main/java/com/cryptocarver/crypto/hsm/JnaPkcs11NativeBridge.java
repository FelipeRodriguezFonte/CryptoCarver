package com.cryptocarver.crypto.hsm;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JNA implementation of the public PKCS#11 C ABI.
 *
 * <p>The module exports only one symbol directly: {@code C_GetFunctionList}.
 * All inventory calls are made through the function pointers returned in the
 * standard {@code CK_FUNCTION_LIST}. The complete v2 function-list shape is
 * represented as a naturally aligned JNA {@link Structure}; no
 * function-pointer offsets are calculated by hand.</p>
 *
 * <p>Only the module selected by the caller is loaded. This class has no
 * provider, keystore, session, login, object or credential API. PKCS#11
 * {@code C_Finalize} is called exactly once for every successful
 * {@code C_Initialize}; an already-initialized module is rejected without
 * finalizing an initialization owned by another caller.</p>
 */
public final class JnaPkcs11NativeBridge implements Pkcs11NativeBridge {
    private static final int MAX_NATIVE_ENTRIES = 100_000;

    private final ModuleLoader moduleLoader;

    public JnaPkcs11NativeBridge() {
        this(JnaPkcs11NativeBridge::loadModule);
    }

    /** Package-private seam for ABI/order tests; production uses the default constructor. */
    JnaPkcs11NativeBridge(ModuleLoader moduleLoader) {
        this.moduleLoader = java.util.Objects.requireNonNull(moduleLoader, "module loader is required");
    }

    @Override
    public NativeSession initialize(Path library) throws Pkcs11NativeException {
        if (library == null) throw new IllegalArgumentException("library is required");

        // The loader resolves C_GetFunctionList only. It must return the table
        // before this method is allowed to invoke C_Initialize.
        ModuleEntryPoint module = moduleLoader.load(library);
        if (module == null) {
            throw new Pkcs11NativeException("C_GetFunctionList", Long.MIN_VALUE);
        }
        FunctionTable functionTable = module.functionList();
        if (functionTable == null) {
            throw new Pkcs11NativeException("C_GetFunctionList", Long.MIN_VALUE);
        }
        NativeLong returnValue = functionTable.initialize();
        long code = code(returnValue);
        if (code == Pkcs11NativeConstants.CKR_CRYPTOKI_ALREADY_INITIALIZED) {
            throw new Pkcs11NativeException("C_Initialize", code);
        }
        if (code != Pkcs11NativeConstants.CKR_OK) {
            throw new Pkcs11NativeException("C_Initialize", code);
        }
        return new Session(functionTable);
    }

    private static ModuleEntryPoint loadModule(Path library) {
        Pkcs11Functions entryPoint = Native.load(library.toString(), Pkcs11Functions.class);
        return new JnaModuleEntryPoint(entryPoint);
    }

    private static long code(NativeLong returnValue) {
        return returnValue == null ? Long.MIN_VALUE : returnValue.longValue();
    }

    private static List<Long> readNativeLongList(
            String operation,
            SlotListCall call) throws Pkcs11NativeException {
        NativeLongByReference count = new NativeLongByReference();
        check(operation, call.invoke(Pointer.NULL, count));
        long requested = count.getValue().longValue();
        ensureCount(requested);
        if (requested == 0) return List.of();

        for (int attempt = 0; attempt < 3; attempt++) {
            Memory memory = new Memory(Math.multiplyExact(requested, NativeLong.SIZE));
            NativeLongByReference actual = new NativeLongByReference(new NativeLong(requested));
            NativeLong returnValue = call.invoke(memory, actual);
            long returnCode = code(returnValue);
            if (returnCode == Pkcs11NativeConstants.CKR_BUFFER_TOO_SMALL) {
                requested = actual.getValue().longValue();
                ensureCount(requested);
                continue;
            }
            if (returnCode != Pkcs11NativeConstants.CKR_OK) {
                throw new Pkcs11NativeException(operation, returnCode);
            }
            long actualCount = actual.getValue().longValue();
            ensureCount(actualCount);
            List<Long> values = new ArrayList<>((int) actualCount);
            for (int i = 0; i < actualCount; i++) {
                values.add(memory.getNativeLong((long) i * NativeLong.SIZE).longValue());
            }
            return List.copyOf(values);
        }
        throw new Pkcs11NativeException(operation, Pkcs11NativeConstants.CKR_BUFFER_TOO_SMALL);
    }

    private static void check(String operation, NativeLong returnValue) throws Pkcs11NativeException {
        long returnCode = code(returnValue);
        if (returnCode != Pkcs11NativeConstants.CKR_OK) {
            throw new Pkcs11NativeException(operation, returnCode);
        }
    }

    private static void ensureCount(long count) throws Pkcs11NativeException {
        if (count < 0 || count > MAX_NATIVE_ENTRIES) {
            throw new Pkcs11NativeException("native list size", count);
        }
    }

    @FunctionalInterface
    private interface SlotListCall {
        NativeLong invoke(Pointer list, NativeLongByReference count) throws Pkcs11NativeException;
    }

    private static final class Session implements NativeSession {
        private final FunctionTable functionTable;
        private boolean closed;

        private Session(FunctionTable functionTable) {
            this.functionTable = functionTable;
        }

        @Override
        public List<NativeSlot> slots() throws Pkcs11NativeException {
            List<Long> allSlots = readNativeLongList(
                    "C_GetSlotList", (list, count) -> functionTable.getSlotList((byte) 0, list, count));
            List<Long> tokenSlots = readNativeLongList(
                    "C_GetSlotList", (list, count) -> functionTable.getSlotList((byte) 1, list, count));
            java.util.Set<Long> present = new java.util.HashSet<>(tokenSlots);
            List<NativeSlot> result = new ArrayList<>(allSlots.size());
            for (int i = 0; i < allSlots.size(); i++) {
                long slotId = allSlots.get(i);
                result.add(new NativeSlot(i, slotId, present.contains(slotId)));
            }
            return List.copyOf(result);
        }

        @Override
        public NativeToken token(long slotId) throws Pkcs11NativeException {
            TokenInfo info = new TokenInfo();
            check("C_GetTokenInfo", functionTable.getTokenInfo(new NativeLong(slotId), info));
            info.read();
            return new NativeToken(decode(info.label), decode(info.manufacturerID), info.flags.longValue());
        }

        @Override
        public List<Long> mechanisms(long slotId) throws Pkcs11NativeException {
            return readNativeLongList(
                    "C_GetMechanismList",
                    (list, count) -> functionTable.getMechanismList(new NativeLong(slotId), list, count));
        }

        @Override
        public NativeMechanism mechanism(long slotId, long mechanismId) throws Pkcs11NativeException {
            MechanismInfo info = new MechanismInfo();
            check("C_GetMechanismInfo", functionTable.getMechanismInfo(
                    new NativeLong(slotId), new NativeLong(mechanismId), info));
            info.read();
            return new NativeMechanism(info.minKeySize.longValue(), info.maxKeySize.longValue(), info.flags.longValue());
        }

        @Override
        public void close() throws Pkcs11NativeException {
            if (closed) return;
            closed = true;
            check("C_Finalize", functionTable.finalizeModule(Pointer.NULL));
        }
    }

    private static String decode(byte[] value) {
        if (value == null) return null;
        return new String(value, StandardCharsets.UTF_8)
                .replace('\0', ' ')
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
    }

    /** The only symbol resolved directly from the native module. */
    private interface Pkcs11Functions extends Library {
        NativeLong C_GetFunctionList(PointerByReference functionList);
    }

    private static final class JnaModuleEntryPoint implements ModuleEntryPoint {
        private final Pkcs11Functions entryPoint;

        private JnaModuleEntryPoint(Pkcs11Functions entryPoint) {
            this.entryPoint = entryPoint;
        }

        @Override
        public FunctionTable functionList() throws Pkcs11NativeException {
            PointerByReference reference = new PointerByReference();
            check("C_GetFunctionList", entryPoint.C_GetFunctionList(reference));
            Pointer pointer = reference.getValue();
            if (pointer == null) {
                throw new Pkcs11NativeException("C_GetFunctionList", Long.MIN_VALUE);
            }
            return new JnaFunctionTable(new FunctionList(pointer));
        }
    }

    private static final class JnaFunctionTable implements FunctionTable {
        private final Function cInitialize;
        private final Function cFinalize;
        private final Function cGetSlotList;
        private final Function cGetTokenInfo;
        private final Function cGetMechanismList;
        private final Function cGetMechanismInfo;

        private JnaFunctionTable(FunctionList list) throws Pkcs11NativeException {
            cInitialize = function("C_Initialize", list.cInitialize);
            cFinalize = function("C_Finalize", list.cFinalize);
            cGetSlotList = function("C_GetSlotList", list.cGetSlotList);
            cGetTokenInfo = function("C_GetTokenInfo", list.cGetTokenInfo);
            cGetMechanismList = function("C_GetMechanismList", list.cGetMechanismList);
            cGetMechanismInfo = function("C_GetMechanismInfo", list.cGetMechanismInfo);
        }

        private static Function function(String operation, Pointer pointer) throws Pkcs11NativeException {
            if (pointer == null) throw new Pkcs11NativeException(operation, Long.MIN_VALUE);
            return Function.getFunction(pointer, Function.C_CONVENTION);
        }

        private static NativeLong invoke(String operation, Function function, Object... arguments)
                throws Pkcs11NativeException {
            try {
                return (NativeLong) function.invoke(NativeLong.class, arguments);
            } catch (RuntimeException | LinkageError failure) {
                throw new Pkcs11NativeException(operation, Long.MIN_VALUE);
            }
        }

        @Override
        public NativeLong initialize() throws Pkcs11NativeException {
            return invoke("C_Initialize", cInitialize, Pointer.NULL);
        }

        @Override
        public NativeLong finalizeModule(Pointer reserved) throws Pkcs11NativeException {
            return invoke("C_Finalize", cFinalize, reserved);
        }

        @Override
        public NativeLong getSlotList(byte tokenPresent, Pointer slotList, NativeLongByReference count)
                throws Pkcs11NativeException {
            return invoke("C_GetSlotList", cGetSlotList, tokenPresent, slotList, count);
        }

        @Override
        public NativeLong getTokenInfo(NativeLong slotId, TokenInfo info) throws Pkcs11NativeException {
            return invoke("C_GetTokenInfo", cGetTokenInfo, slotId, info);
        }

        @Override
        public NativeLong getMechanismList(NativeLong slotId, Pointer mechanismList, NativeLongByReference count)
                throws Pkcs11NativeException {
            return invoke("C_GetMechanismList", cGetMechanismList, slotId, mechanismList, count);
        }

        @Override
        public NativeLong getMechanismInfo(NativeLong slotId, NativeLong mechanism, MechanismInfo info)
                throws Pkcs11NativeException {
            return invoke("C_GetMechanismInfo", cGetMechanismInfo, slotId, mechanism, info);
        }
    }

    @Structure.FieldOrder({
            "version",
            "cInitialize", "cFinalize", "cGetInfo", "cGetFunctionList",
            "cGetSlotList", "cGetSlotInfo", "cGetTokenInfo", "cGetMechanismList", "cGetMechanismInfo",
            "cInitToken", "cInitPin", "cSetPin",
            "cOpenSession", "cCloseSession", "cCloseAllSessions", "cGetSessionInfo",
            "cGetOperationState", "cSetOperationState", "cLogin", "cLogout",
            "cCreateObject", "cCopyObject", "cDestroyObject", "cGetObjectSize",
            "cGetAttributeValue", "cSetAttributeValue", "cFindObjectsInit", "cFindObjects", "cFindObjectsFinal",
            "cEncryptInit", "cEncrypt", "cEncryptUpdate", "cEncryptFinal",
            "cDecryptInit", "cDecrypt", "cDecryptUpdate", "cDecryptFinal",
            "cDigestInit", "cDigest", "cDigestUpdate", "cDigestKey", "cDigestFinal",
            "cSignInit", "cSign", "cSignUpdate", "cSignFinal", "cSignRecoverInit", "cSignRecover",
            "cVerifyInit", "cVerify", "cVerifyUpdate", "cVerifyFinal", "cVerifyRecoverInit", "cVerifyRecover",
            "cDigestEncryptUpdate", "cDecryptDigestUpdate", "cSignEncryptUpdate", "cDecryptVerifyUpdate",
            "cGenerateKey", "cGenerateKeyPair", "cWrapKey", "cUnwrapKey", "cDeriveKey",
            "cSeedRandom", "cGenerateRandom", "cGetFunctionStatus", "cCancelFunction", "cWaitForSlotEvent"
    })
    public static final class FunctionList extends Structure {
        /** CK_FUNCTION_LIST begins with an inline CK_VERSION structure. */
        public Version version = new Version();
        public Pointer cInitialize;
        public Pointer cFinalize;
        public Pointer cGetInfo;
        public Pointer cGetFunctionList;
        public Pointer cGetSlotList;
        public Pointer cGetSlotInfo;
        public Pointer cGetTokenInfo;
        public Pointer cGetMechanismList;
        public Pointer cGetMechanismInfo;
        public Pointer cInitToken;
        public Pointer cInitPin;
        public Pointer cSetPin;
        public Pointer cOpenSession;
        public Pointer cCloseSession;
        public Pointer cCloseAllSessions;
        public Pointer cGetSessionInfo;
        public Pointer cGetOperationState;
        public Pointer cSetOperationState;
        public Pointer cLogin;
        public Pointer cLogout;
        public Pointer cCreateObject;
        public Pointer cCopyObject;
        public Pointer cDestroyObject;
        public Pointer cGetObjectSize;
        public Pointer cGetAttributeValue;
        public Pointer cSetAttributeValue;
        public Pointer cFindObjectsInit;
        public Pointer cFindObjects;
        public Pointer cFindObjectsFinal;
        public Pointer cEncryptInit;
        public Pointer cEncrypt;
        public Pointer cEncryptUpdate;
        public Pointer cEncryptFinal;
        public Pointer cDecryptInit;
        public Pointer cDecrypt;
        public Pointer cDecryptUpdate;
        public Pointer cDecryptFinal;
        public Pointer cDigestInit;
        public Pointer cDigest;
        public Pointer cDigestUpdate;
        public Pointer cDigestKey;
        public Pointer cDigestFinal;
        public Pointer cSignInit;
        public Pointer cSign;
        public Pointer cSignUpdate;
        public Pointer cSignFinal;
        public Pointer cSignRecoverInit;
        public Pointer cSignRecover;
        public Pointer cVerifyInit;
        public Pointer cVerify;
        public Pointer cVerifyUpdate;
        public Pointer cVerifyFinal;
        public Pointer cVerifyRecoverInit;
        public Pointer cVerifyRecover;
        public Pointer cDigestEncryptUpdate;
        public Pointer cDecryptDigestUpdate;
        public Pointer cSignEncryptUpdate;
        public Pointer cDecryptVerifyUpdate;
        public Pointer cGenerateKey;
        public Pointer cGenerateKeyPair;
        public Pointer cWrapKey;
        public Pointer cUnwrapKey;
        public Pointer cDeriveKey;
        public Pointer cSeedRandom;
        public Pointer cGenerateRandom;
        public Pointer cGetFunctionStatus;
        public Pointer cCancelFunction;
        public Pointer cWaitForSlotEvent;

        FunctionList() {
            // CK_FUNCTION_LIST follows the platform C ABI: CK_VERSION is
            // followed by naturally aligned function pointers.
            super();
        }

        FunctionList(Pointer pointer) {
            super(pointer);
            read();
        }

        int nativeFieldOffset(String fieldName) {
            return fieldOffset(fieldName);
        }
    }

    /** Inline CK_VERSION representation from the PKCS#11 public header. */
    @Structure.FieldOrder({"major", "minor"})
    public static final class Version extends Structure {
        public byte major;
        public byte minor;

        public Version() {
            super();
        }
    }

    @Structure.FieldOrder({
            "label", "manufacturerID", "model", "serialNumber", "flags",
            "maxSessionCount", "sessionCount", "maxRwSessionCount", "rwSessionCount",
            "maxPinLen", "minPinLen", "totalPublicMemory", "freePublicMemory",
            "totalPrivateMemory", "freePrivateMemory", "hardwareVersion", "firmwareVersion", "utcTime"
    })
    public static final class TokenInfo extends Structure {
        public byte[] label = new byte[32];
        public byte[] manufacturerID = new byte[32];
        public byte[] model = new byte[16];
        public byte[] serialNumber = new byte[16];
        public NativeLong flags = new NativeLong();
        public NativeLong maxSessionCount = new NativeLong();
        public NativeLong sessionCount = new NativeLong();
        public NativeLong maxRwSessionCount = new NativeLong();
        public NativeLong rwSessionCount = new NativeLong();
        public NativeLong maxPinLen = new NativeLong();
        public NativeLong minPinLen = new NativeLong();
        public NativeLong totalPublicMemory = new NativeLong();
        public NativeLong freePublicMemory = new NativeLong();
        public NativeLong totalPrivateMemory = new NativeLong();
        public NativeLong freePrivateMemory = new NativeLong();
        public byte[] hardwareVersion = new byte[2];
        public byte[] firmwareVersion = new byte[2];
        public byte[] utcTime = new byte[16];

        TokenInfo() {
            // CK_TOKEN_INFO uses native CK_ULONG alignment; do not pack it.
            super();
        }

        int nativeFieldOffset(String fieldName) {
            return fieldOffset(fieldName);
        }
    }

    @Structure.FieldOrder({"minKeySize", "maxKeySize", "flags"})
    public static final class MechanismInfo extends Structure {
        public NativeLong minKeySize = new NativeLong();
        public NativeLong maxKeySize = new NativeLong();
        public NativeLong flags = new NativeLong();

        MechanismInfo() {
            // CK_MECHANISM_INFO uses native CK_ULONG alignment.
            super();
        }
    }

    @FunctionalInterface
    interface ModuleLoader {
        ModuleEntryPoint load(Path library);
    }

    interface ModuleEntryPoint {
        FunctionTable functionList() throws Pkcs11NativeException;
    }

    /** Tight seam used by tests; production implementation contains only the six allowed table calls. */
    interface FunctionTable {
        NativeLong initialize() throws Pkcs11NativeException;

        NativeLong finalizeModule(Pointer reserved) throws Pkcs11NativeException;

        NativeLong getSlotList(byte tokenPresent, Pointer slotList, NativeLongByReference count)
                throws Pkcs11NativeException;

        NativeLong getTokenInfo(NativeLong slotId, TokenInfo info) throws Pkcs11NativeException;

        NativeLong getMechanismList(NativeLong slotId, Pointer mechanismList, NativeLongByReference count)
                throws Pkcs11NativeException;

        NativeLong getMechanismInfo(NativeLong slotId, NativeLong mechanism, MechanismInfo info)
                throws Pkcs11NativeException;
    }
}
