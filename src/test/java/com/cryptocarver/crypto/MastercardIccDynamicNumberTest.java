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

    /** Captured with IAF 00: the PAN sequence number is left out and the IPB needs no padding. */
    @Test void capturedCapTokenWithoutPanSequenceNumber() {
        MastercardIccDynamicNumber.CapResult cap = MastercardIccDynamicNumber.capToken(
                "00007FFFFF00000000000000000000208000", "00", "00", "80", "0001",
                "5AC19AC9FE1360F3", "06010A03A41000");
        assertEquals("8000015AC19AC9FE1360F306010A03A41000", cap.tokenData());
        assertEquals("00007FFFFF00000000000000000000208000", cap.paddedIpb());
        assertEquals("0000001010110101100000110", cap.compressedBits());
        assertEquals("355078", cap.token());
    }

    @Test void rejectsBadLengths() {
        assertThrows(IllegalArgumentException.class, () -> MastercardIccDynamicNumber.sessionKey(MK, "4111"));
        assertThrows(IllegalArgumentException.class, () -> MastercardIccDynamicNumber.dynamicNumber("00", "0001", "00000000"));
        assertThrows(IllegalArgumentException.class, () -> MastercardIccDynamicNumber.capToken("00", "40", "00", "80", "0001", "00", "00"));
    }
}
