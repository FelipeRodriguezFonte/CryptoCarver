package com.cryptocarver.crypto;

import com.cryptocarver.crypto.ThalesLmkOperations.Lmk;
import com.cryptocarver.crypto.ThalesLmkOperations.Scheme;
import com.cryptocarver.crypto.ThalesLmkOperations.WrappedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The variant scheme against the tool we chase parity with.
 *
 * <p>Everything else about this scheme rests on a manual that contradicts
 * itself: clause 7.1 of the payShield host programmer's manual prints
 * {@code 6A} as the variant for 1 where clause 7.2.3 and the manual's own
 * worked example print {@code A6}. Two widely cited descriptions on the web
 * get it wrong as well, each in a different byte. So these vectors are not
 * decoration — they are the only evidence that is not paper.</p>
 *
 * <p>Source: an external tool, Thales Keys
 * Encryption/Decoding, captured 2026-09-19. The tool's own LMK pair 00-01 was
 * used, and its Variant dropdown selects the LMK variant directly rather than
 * through a key type code; here the same thing is reached through key types
 * {@code 100} and {@code 200}, whose leading digit is the variant.</p>
 */
class ThalesLmkExternalVectorTest {

    /** The tool's built-in LMK pair 00-01. */
    private static final Lmk LMK = Lmk.of("01010101010101017902CD1FD36EF8BA");

    private static final String KEY = "0123456789ABCDEFFEDCBA9876543210";

    @Test
    void variantOneUnderSchemeU() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(KEY, "100", Scheme.U, LMK);

        assertEquals("A12049CC30D20BE0DC6E5E2A98FADE9E", wrapped.cryptogram());
        assertEquals("08D7B4", wrapped.checkValue());
        assertEquals("UA12049CC30D20BE0DC6E5E2A98FADE9E", wrapped.tagged());
    }

    @Test
    void variantTwoUnderSchemeU() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(KEY, "200", Scheme.U, LMK);

        assertEquals("BD7F64B9F9875B731BAED72101DEA65B", wrapped.cryptogram());
        assertEquals("08D7B4", wrapped.checkValue());
    }

    @Test
    void bothVectorsDecryptBackToTheKey() {
        assertEquals(KEY, ThalesLmkOperations
                .decrypt("UA12049CC30D20BE0DC6E5E2A98FADE9E", "100", Scheme.U, LMK).cryptogram());
        assertEquals(KEY, ThalesLmkOperations
                .decrypt("UBD7F64B9F9875B731BAED72101DEA65B", "200", Scheme.U, LMK).cryptogram());
    }

    /**
     * The point of the two vectors together. If the scheme variant were applied
     * to both halves the same way — the obvious reading, and the one a hand
     * check reaches for — the first half would still be right and only the
     * second would be wrong. Scheme U is {@code A6} for the left part and
     * {@code 5A} for the right, and nothing but a second part proves it.
     */
    @Test
    void theTwoHalvesOfSchemeUUseDifferentSchemeVariants() {
        String cryptogram = ThalesLmkOperations.encrypt(KEY, "100", Scheme.U, LMK).cryptogram();

        // The left half of a wrong implementation that uses A6 throughout would
        // agree; the right half is what separates them.
        assertEquals("A12049CC30D20BE0", cryptogram.substring(0, 16));
        assertEquals("DC6E5E2A98FADE9E", cryptogram.substring(16));
        assertNotEquals("6E9808FEE052777C", cryptogram.substring(16),
                "that is what A6 on both halves produces, and it is what the hardware does not");
    }
}
