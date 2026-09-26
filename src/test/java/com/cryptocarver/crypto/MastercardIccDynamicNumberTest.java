package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MastercardIccDynamicNumberTest {
    private static final String MK = "0123456789ABCDEFFEDCBA9876543210";
    private static final String PAN = "4111111111111111";

    @Test void capturedDynamicNumbers() {
        String sk = MastercardIccDynamicNumber.sessionKey(MK, PAN);
        assertEquals("69D9405C8462F4109CB20DC8B99F3BD9", sk);
        assertEquals("6AB2", MastercardIccDynamicNumber.dynamicNumber(sk, "0001", "00000000"));
        assertEquals("816A", MastercardIccDynamicNumber.dynamicNumber(sk, "0001", "00000001"));
    }

    @Test void capturedCapTokenIncludesIntermediates() {
        MastercardIccDynamicNumber.CapResult cap = MastercardIccDynamicNumber.capToken(
                "00007FFFFF00000000000000000000208000", "40", "00", "80", "0001",
                "5AC19AC9FE1360F3", "06010A03A41000");
        assertEquals("008000015AC19AC9FE1360F306010A03A41000", cap.tokenData());
        assertEquals("00007FFFFF0000000000000000000020800000", cap.paddedIpb());
        assertEquals("0000000000000010101101001", cap.compressedBits());
        assertEquals("1385", cap.token());
    }

    @Test void rejectsBadLengthsAndUnconfirmedNoPsnFlag() {
        assertThrows(IllegalArgumentException.class, () -> MastercardIccDynamicNumber.sessionKey(MK, "4111"));
        assertThrows(IllegalArgumentException.class, () -> MastercardIccDynamicNumber.dynamicNumber("00", "0001", "00000000"));
        assertThrows(IllegalArgumentException.class, () -> MastercardIccDynamicNumber.capToken("00", "40", "00", "80", "0001", "00", "00"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MastercardIccDynamicNumber.capToken(
                "00007FFFFF00000000000000000000208000", "00", "00", "80", "0001", "5AC19AC9FE1360F3", "06010A03A41000"));
        assertTrue(error.getMessage().contains("IAF bit 40"));
    }
}
