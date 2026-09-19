package com.cryptocarver.crypto;

import com.cryptocarver.crypto.SafeNetKmOperations.WrappedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SafeNet keys under a KM, against three captured vectors.
 *
 * <p>Source: EFTLab BP-Tools Cryptographic Calculator 21.06, SafeNet Keys
 * Encryption/Decoding, captured 2026-09-19: format 11 with variant 00, and
 * format 13 with variants 01 and 07.</p>
 */
class SafeNetKmOperationsTest {

    private static final String KM = "0123456789ABCDEF8080808080808080FEDCBA9876543210";
    private static final String KEY = "0123456789ABCDEFFEDCBA9876543210";

    // =====================================================================
    // The vectors
    // =====================================================================

    @Test
    void formatElevenWithVariantZeroIsReproduced() {
        WrappedKey wrapped = SafeNetKmOperations.encrypt(KEY, KM, "11", "00");

        assertEquals("968F5C677725C7C4E31E3E4C7ACA58B9", wrapped.cryptogram());
        assertEquals("1111968F5C677725C7C4E31E3E4C7ACA58B9", wrapped.hostStoredKey());
    }

    @Test
    void formatThirteenWithVariantOneIsReproduced() {
        WrappedKey wrapped = SafeNetKmOperations.encrypt(KEY, KM, "13", "01");

        assertEquals("E44E7E4045C6DA5CA6620D6801B4CEE8", wrapped.cryptogram());
        assertEquals("1113E44E7E4045C6DA5CA6620D6801B4CEE8", wrapped.hostStoredKey());
    }

    @Test
    void formatThirteenWithVariantSevenIsReproduced() {
        WrappedKey wrapped = SafeNetKmOperations.encrypt(KEY, KM, "13", "07");

        assertEquals("B3E9B74D4F812682E7E2C7C3DBDCA83B", wrapped.cryptogram());
        assertEquals("1113B3E9B74D4F812682E7E2C7C3DBDCA83B", wrapped.hostStoredKey());
    }

    @Test
    void theVariantsAreTheKeysTheToolPrintedBesideItsResults() {
        assertEquals(KM, SafeNetKmOperations.applyVariant(KM, "00"));
        assertEquals("290B6D4FA183E5C7A8A8A8A8A8A8A8A8D6F492B05E7C1A38",
                SafeNetKmOperations.applyVariant(KM, "01"));
        assertEquals("193B5D7F91B3D5F79898989898989898E6C4A2806E4C2A08",
                SafeNetKmOperations.applyVariant(KM, "07"));
    }

    @Test
    void everyVectorDecryptsBackToTheKey() {
        assertEquals(KEY, SafeNetKmOperations
                .decrypt("968F5C677725C7C4E31E3E4C7ACA58B9", KM, "11", "00").cryptogram());
        assertEquals(KEY, SafeNetKmOperations
                .decrypt("E44E7E4045C6DA5CA6620D6801B4CEE8", KM, "13", "01").cryptogram());
        assertEquals(KEY, SafeNetKmOperations
                .decrypt("B3E9B74D4F812682E7E2C7C3DBDCA83B", KM, "13", "07").cryptogram());
    }

    // =====================================================================
    // The two traps
    // =====================================================================

    /**
     * ECB and CBC-with-a-zero-IV produce the same first block, always. So the
     * two SafeNet formats are indistinguishable by eye on the first eight
     * bytes, and a hand check of "does the cryptogram start right" passes for
     * the wrong format.
     */
    @Test
    void theSameKeyUnderBothFormatsSharesItsFirstBlock() {
        String ecb = SafeNetKmOperations.encrypt(KEY, KM, "11", "01").cryptogram();
        String cbc = SafeNetKmOperations.encrypt(KEY, KM, "13", "01").cryptogram();

        assertEquals(ecb.substring(0, 16), cbc.substring(0, 16));
        assertNotEquals(ecb.substring(16), cbc.substring(16));
    }

    /**
     * Variant 00 leaves the KM untouched. That makes "I forgot to apply the
     * variant" and "the variant is 00" the same code path, and the only thing
     * separating them is that an unknown variant is refused rather than
     * defaulted.
     */
    @Test
    void anUnknownVariantIsRefusedRatherThanTreatedAsNoVariant() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> SafeNetKmOperations.encrypt(KEY, KM, "13", "02"));

        assertTrue(thrown.getMessage().contains("table and not a formula"), thrown.getMessage());
        assertNotEquals(SafeNetKmOperations.applyVariant(KM, "00"),
                SafeNetKmOperations.applyVariant(KM, "01"));
    }

    @Test
    void anUnverifiedKeyFormatIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> SafeNetKmOperations.encrypt(KEY, KM, "12", "00"));

        assertTrue(thrown.getMessage().contains("has not been verified"), thrown.getMessage());
    }

    @Test
    void theVariantTableHoldsOnlyWhatWasSeen() {
        assertEquals(3, SafeNetKmOperations.variants().size());
        assertEquals("PPK", SafeNetKmOperations.variant("01").name());
        assertEquals(0x18, SafeNetKmOperations.variant("07").constant());
    }

    @Test
    void theReportSaysTheStoredFormDoesNotCarryTheVariant() {
        String report = SafeNetKmOperations.describe(
                SafeNetKmOperations.encrypt(KEY, KM, "13", "07"), KM);

        assertTrue(report.contains("not the variant"), report);
        assertTrue(report.contains("1113B3E9B74D4F812682E7E2C7C3DBDCA83B"), report);
    }
}
