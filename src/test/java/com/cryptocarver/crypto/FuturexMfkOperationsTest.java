package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Futurex keys under an MFK, against five captured vectors.
 *
 * <p>Source: an external tool, Futurex Keys
 * Encryption/Decoding, captured 2026-09-19 — the same key under the same MFK,
 * once for each of the modifiers 0 to 4; modifiers 5 and 6 on 2026-09-25.</p>
 */
class FuturexMfkOperationsTest {

    private static final String MFK = "D2DE5CD9110F4CAB11111111111111110123456789ABCDEF";
    private static final String KEY = "0123456789ABCDEFFEDCBA9876543210";

    private static final String[] CRYPTOGRAMS = {
        "F0700DDBFB49DDD5A3280E65263A6EED",
        "0C3D7CCA80E2840B527FA6C59A3F3719",
        "2CDF6713C9F803984F4BEA1D4D722BEC",
        "B0F6A8F20743633D46F0961A03878480",
        "C6CA6CCD5C517A0F0511740BB7ED1860",
        // Modifiers 5 and 6, captured 2026-09-25.
        "00B3F28359E799BED5B17C73A31CD80B",
        "41B6B78C97C28807A8BF0C56BC437807",
    };

    @Test
    void everyCapturedModifierIsReproduced() {
        for (int modifier = 0; modifier < CRYPTOGRAMS.length; modifier++) {
            assertEquals(CRYPTOGRAMS[modifier],
                    FuturexMfkOperations.encrypt(KEY, MFK, modifier).cryptogram(),
                    "modifier " + modifier);
        }
    }

    @Test
    void everyCapturedModifierDecryptsBack() {
        for (int modifier = 0; modifier < CRYPTOGRAMS.length; modifier++) {
            assertEquals(KEY, FuturexMfkOperations.decrypt(CRYPTOGRAMS[modifier], MFK, modifier)
                    .cryptogram(), "modifier " + modifier);
        }
    }

    @Test
    void theModifierIsEightTimesItsNumberInEachPartsFirstByte() {
        assertEquals(MFK, FuturexMfkOperations.applyModifier(MFK, 0));
        assertEquals("DADE5CD9110F4CAB19111111111111110923456789ABCDEF",
                FuturexMfkOperations.applyModifier(MFK, 1));
        assertEquals("F2DE5CD9110F4CAB31111111111111112123456789ABCDEF",
                FuturexMfkOperations.applyModifier(MFK, 4));
    }

    /**
     * Why five vectors were asked for rather than one. With modifier 0 alone
     * this scheme looks like plain ECB with no key separation at all — the
     * modifier leaves the MFK untouched — and an implementation built on that
     * one capture would round-trip perfectly and be wrong for every other key
     * in the HSM.
     */
    @Test
    void modifierZeroLeavesTheMasterKeyAloneAndTheOthersDoNot() {
        assertEquals(MFK, FuturexMfkOperations.applyModifier(MFK, 0));
        for (int modifier = 1; modifier <= 4; modifier++) {
            assertNotEquals(MFK, FuturexMfkOperations.applyModifier(MFK, modifier));
            assertNotEquals(CRYPTOGRAMS[0], CRYPTOGRAMS[modifier]);
        }
    }

    @Test
    void theCheckValueIsTheStandardOne() {
        assertEquals("08D7B4", FuturexMfkOperations.checkValue(KEY));
    }

    /**
     * The rule would extrapolate to modifier 7 without complaint. It is
     * refused, because a guess about key separation is the one guess this
     * bench has already paid for twice.
     */
    @Test
    void anUnverifiedModifierIsRefusedByNumber() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> FuturexMfkOperations.encrypt(KEY, MFK, 7));

        assertTrue(thrown.getMessage().contains("verified against a vector"), thrown.getMessage());
    }

    @Test
    void aDoubleLengthMfkIsAcceptedAndAShortOneIsNot() {
        assertEquals("786DCE0EE3CB07CF5D590C65C17A0E29",
                FuturexMfkOperations.encrypt(KEY, "D2DE5CD9110F4CAB1111111111111111", 0).cryptogram());
        assertThrows(IllegalArgumentException.class,
                () -> FuturexMfkOperations.encrypt(KEY, "0123456789ABCDEF", 0));
    }

    @Test
    void theReportSaysThereIsNothingToAuthenticate() {
        String report = FuturexMfkOperations.describe(
                FuturexMfkOperations.encrypt(KEY, MFK, 2), MFK);

        assertTrue(report.contains("no authentication"), report);
        assertTrue(report.contains("2CDF6713C9F803984F4BEA1D4D722BEC"), report);
    }
}
