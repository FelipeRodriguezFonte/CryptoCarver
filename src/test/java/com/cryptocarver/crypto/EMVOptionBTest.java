package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EMVOptionBTest {
    private static final String IMK = "DFADBFEF0123456789866443DFADBFEF";

    /** Vector: https://www.scribd.com/document/802131624/Dubinsky-i-Cryptography-for-Payment-Professionals, App. C.5.2;
     * corroborated by https://github.com/ilya-dubinsky/cfpp/blob/main/c/test/test_emv.c#L221-L249
     * (repository LICENSE is Unlicense; no code copied).
     * SHA-1 input correction independently checked against the published SHA-1 with Python hashlib:
     * BCD 0987654321012345678901 (the book's displayed input has an extra 00 suffix).
     */
    @Test
    void matchesPublishedNineteenDigitOptionBVector() throws Exception {
        var result = EMVOperations.deriveICCMasterKeyOptionB(IMK, "9876543210123456789", "01");
        assertEquals("0987654321012345678901", result.input());
        assertEquals("FBC4FDF02B0EF7F0801D8C0D02DD609D8BEAD233", result.sha1());
        assertEquals("4020708018002609", result.y());
        assertEquals("E5AB98AB5E76F757FEDC7F016E5E2358",
                com.cryptocarver.util.DataConverter.bytesToHex(EMVOperations.adjustParity(
                        com.cryptocarver.util.DataConverter.hexToBytes(result.key()))));
    }

    /** EMV Book 2 v4.4 §A1.4.2, decimalisation Example 2 exercises fallback A..F mapping. */
    @Test
    void decimalisationUsesSecondPassOnlyToFillDigits() throws Exception {
        assertEquals("1230567842417923", EMVOperations.decimalizeY(
                "1230ABCD567842D4B179F2CA345D6789A17B64BB"));
        assertEquals("1368412478176120",
                EMVOperations.decimalizeHash("1B3CABCDD6E8FAD4B1CDF2CAD4FDC78FA17B6EBB"));
        assertEquals("1368412478176120", EMVOperations.decimalizeY(
                "1B3CABCDD6E8FAD4B1CDF2CAD4FDC78FA17B6EBB"));
        assertEquals("5555555555555555", EMVOperations.decimalizeHash("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
    }

    @Test
    void autoMatchesOptionAForSixteenDigitsOrFewer() throws Exception {
        for (String pan : new String[]{"12345678901234", "1234567890123456"}) {
            assertEquals(EMVOperations.deriveICCMasterKey(IMK, pan, "01"),
                    EMVOperations.deriveICCMasterKey(IMK, pan, "01", EMVOperations.IccMasterKeyMethod.AUTO).key());
            assertEquals(EMVOperations.deriveICCMasterKey(IMK, pan, "01"),
                    EMVOperations.deriveICCMasterKeyOptionB(IMK, pan, "01").key());
        }
    }
}
