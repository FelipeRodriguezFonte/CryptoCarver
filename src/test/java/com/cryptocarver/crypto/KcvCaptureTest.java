package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cryptocarver.utils.DataConverter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * KCVs of generated keys, captured from an external tool on 2026-09-25: double-length
 * and 256-bit keys, with parity none, odd and even.
 *
 * <p>The DES-family values are computed on the key the tool displays. The AES-family
 * values (AES, SHA-256, CMAC, CKCV AES) are not, in two ways that were recovered from
 * the captures and are the tool's, not the standards':</p>
 * <ul>
 *   <li>They use the key <i>before</i> parity is forced. The displayed key is the
 *       adjusted one; the original was found by searching the parity-bit variants,
 *       and one variant reproduces all four values at once.</li>
 *   <li>For a 256-bit key they use only its first 24 bytes, as an AES-192 key. Keys
 *       generated with no parity forced match that directly.</li>
 * </ul>
 * <p>So the AES-family cases below feed the key the tool actually used. This bench
 * computes on the whole key it is given.</p>
 *
 * <p>The same captures show KCV (IBM) and KCV (ATALLA R) for four double-length keys
 * (677A/6523, FF6E/D6E3, E0FE/7846, 8FA8/1E7A) and for the two validated
 * keys above (F9AE/CBBE, B85B/46A5). Nothing tried reproduces them
 * (E and D of fixed blocks under the key, its halves and variants, byte and nibble
 * selections, key variants and IBM CCA control vectors, MDC-2/MDC-4, CRC-16,
 * CMACs, hashes), so they are not pinned.</p>
 */
class KcvCaptureTest {

    private static String hex(byte[] b) {
        return DataConverter.bytesToHex(b).toUpperCase();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // displayed key (parity),                 VISA/ATALLA, FUTUREX, CKCV (TDEA)
            "B580830EE6617031795119FDC2DA0258, 00B11B, AF7B, 26E8CD56A5", // odd
            "69C752BD4D131DCD70D5E958D510298D, A9CDD6, B3B7, 552B6748CF", // none
            "313152858C79AB1CEF5832A129BA98B9, BCA65A, 04AA, 4EC8ECF337", // odd
            "5C3F0A0506E2BB8E88BE1414057790D2, 54A0E5, 6214, 8D718AA1C1", // even
            "0123456789ABCDEFFEDCBA9876543210, 08D7B4, 1A4D, 0A82458664", // key validation, classic test key
    })
    void desFamily(String key, String visa, String futurex, String ckcvTdea) throws Exception {
        byte[] k = DataConverter.hexToBytes(key);
        assertEquals(visa, hex(KeyOperations.calculateKCV_VISA(k)));
        assertEquals(visa, hex(KeyOperations.calculateKCV_ATALLA(k)));
        assertEquals(futurex, hex(KeyOperations.calculateKCV_FUTUREX(k)));
        assertEquals(ckcvTdea, hex(KeyOperations.calculateCKCV_TDEA(k)));
    }

    /** Two weak DES halves: the tool prints VISA and FUTUREX but CKCV (TDEA) N/A; this bench computes all three. */
    @org.junit.jupiter.api.Test
    void weakDesKeyStillHasVisaAndFuturexKcvs() throws Exception {
        byte[] k = DataConverter.hexToBytes("0101010101010101FEFEFEFEFEFEFEFE");
        assertEquals("9295B5", hex(KeyOperations.calculateKCV_VISA(k)));
        assertEquals("1069", hex(KeyOperations.calculateKCV_FUTUREX(k)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // key the tool computed on,                                   AES,    SHA256, CMAC,   CKCV (AES)
            "B581820EE6607130795118FCC3DA0259,                             870BA3, 33BE01, 024C7C, 0220970103", // 128, odd, before parity
            "69C752BD4D131DCD70D5E958D510298D,                             2D5879, 5E1FED, 7F2AF2, 84AAC0922D", // 128, none
            "303053848D78AA1DEE5933A128BA98B8,                             63C885, 91DA1D, 8631FE, 5711FC4A4A", // 128, odd, before parity
            "5C3E0B0407E2BB8F88BE1514047691D2,                             367676, 1ECFF9, 45E7EB, 94F7DE711F", // 128, even, before parity
            "DE432B89811A7683C0F7BEB6BCEBAEEAEAAB2711DE30DA24,             BA15D4, 6B1868, D809DE, E8C3ADE353", // 256 none, first 24
            "79AD7A2D6A958528BB4B99226A8BCF8B3EC90D196B866A41,             B56E89, 953918, 40E3B4, BB5C7B474E", // 256 none, first 24
            "E950E773F39E6A1E3287B5C53B9E046B86EE18DA1D722C10,             33AE6D, F517B4, 9DB0D2, 44F10A0FC8", // 256 odd, first 24 before parity
            "757AAA224E825ECA057F3663B97149EAFABBA2593C49695B,             DEEFFF, 845AC4, AEBC67, F015B2B6A7", // 256 even, first 24 before parity
            "0123456789ABCDEFFEDCBA9876543210,                             D5C825, 411D3F, 37BA90, 2090A67375", // key validation: as given
            "0101010101010101FEFEFEFEFEFEFEFE,                             354A19, 86DECA, C19136, 08066AB4C2", // key validation: as given
    })
    void aesFamily(String key, String aes, String sha256, String cmac, String ckcvAes) throws Exception {
        byte[] k = DataConverter.hexToBytes(key);
        assertEquals(aes, hex(KeyOperations.calculateKCV_AES(k)));
        assertEquals(sha256, hex(KeyOperations.calculateKCV_SHA256(k)));
        assertEquals(cmac, hex(KeyOperations.calculateKCV_CMAC(k)));
        assertEquals(ckcvAes, hex(KeyOperations.calculateCKCV_AES(k)));
    }
}
