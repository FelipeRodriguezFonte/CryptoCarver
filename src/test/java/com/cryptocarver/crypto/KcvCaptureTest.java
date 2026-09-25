package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cryptocarver.utils.DataConverter;
import org.junit.jupiter.api.Test;

/**
 * KCVs of one generated double-length key, captured from an external tool on 2026-09-25.
 *
 * <p>The tool shows the key after forcing odd parity, {@code B580830EE6617031795119FDC2DA0258},
 * and computes the DES-family KCVs on it. The AES, CMAC and SHA-256 KCVs it prints are
 * computed on the key <i>before</i> the parity adjustment, {@code B581820EE6607130795118FCC3DA0259}
 * (recovered by searching the 2^16 parity-bit variants; it reproduces all three at once).
 * That is why each group below uses its own key.</p>
 *
 * <p>The same capture shows KCV (IBM) {@code 677A} and KCV (ATALLA R) {@code 6523}. No
 * construction tried reproduces them (E(0), E(0123456789ABCDEF), key halves and variants,
 * MDC-2/MDC-4, hashes), so they are not pinned; the code still computes its own guess.</p>
 */
class KcvCaptureTest {

    private static final byte[] ODD_PARITY = DataConverter.hexToBytes("B580830EE6617031795119FDC2DA0258");
    private static final byte[] BEFORE_PARITY = DataConverter.hexToBytes("B581820EE6607130795118FCC3DA0259");

    private static String hex(byte[] b) {
        return DataConverter.bytesToHex(b).toUpperCase();
    }

    @Test
    void desFamilyKcvs() throws Exception {
        assertEquals("00B11B", hex(KeyOperations.calculateKCV_VISA(ODD_PARITY)));
        assertEquals("00B11B", hex(KeyOperations.calculateKCV_ATALLA(ODD_PARITY)));
        assertEquals("AF7B", hex(KeyOperations.calculateKCV_FUTUREX(ODD_PARITY)));
    }

    @Test
    void aesFamilyKcvs() throws Exception {
        assertEquals("870BA3", hex(KeyOperations.calculateKCV_AES(BEFORE_PARITY)));
        assertEquals("33BE01", hex(KeyOperations.calculateKCV_SHA256(BEFORE_PARITY)));
        // Confirms the empty-input convention: AES-CMAC over a zero block gives 2D69B7 here.
        assertEquals("024C7C", hex(KeyOperations.calculateKCV_CMAC(BEFORE_PARITY)));
    }
}
