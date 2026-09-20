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
 * format 13 with variants 01 and 07. Captured 2026-09-20: the triple-length
 * formats 12 (ECB) and 14 (CBC) with variants 02, 06 and 08.</p>
 */
class SafeNetKmOperationsTest {

    private static final String KM = "0123456789ABCDEF8080808080808080FEDCBA9876543210";
    private static final String KEY = "0123456789ABCDEFFEDCBA9876543210";
    private static final String KEY24 = "0123456789ABCDEFFEDCBA98765432100123456788ABCDEE";

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

    /** Triple-length vectors: format, variant, cryptogram, KM with the variant applied. */
    private static final String[][] TRIPLE = {
        {"12", "02", "72F8F4F863BB42305C28B3C897AF15DACFD0909359B3B555",
            "25076143AD8FE9CBA4A4A4A4A4A4A4A4DAF89EBC52701634"},
        {"12", "06", "E7ECA228ED775FAC1F418AD575C370DBA48057B618ED44B4",
            "21036547A98BEDCFA0A0A0A0A0A0A0A0DEFC9AB856741230"},
        {"12", "08", "0F15CFAFE694B0058FED6DA3BCA8D59E5951AF3CD83EF0A7",
            "153751739DBFD9FB9494949494949494EAC8AE8C62402604"},
        {"14", "02", "72F8F4F863BB423045A0EFB5F6CCCDF037814E147C6FC109",
            "25076143AD8FE9CBA4A4A4A4A4A4A4A4DAF89EBC52701634"},
        {"14", "06", "E7ECA228ED775FACC37345A4566432880C37BC5D99C933D7",
            "21036547A98BEDCFA0A0A0A0A0A0A0A0DEFC9AB856741230"},
        {"14", "08", "0F15CFAFE694B0051DA9C8591546C51BE37CCA029100D678",
            "153751739DBFD9FB9494949494949494EAC8AE8C62402604"},
    };

    @Test
    void theTripleLengthFormatsAreReproducedForEveryCapturedVariant() {
        for (String[] vector : TRIPLE) {
            WrappedKey wrapped = SafeNetKmOperations.encrypt(KEY24, KM, vector[0], vector[1]);

            assertEquals(vector[2], wrapped.cryptogram(), vector[0] + "/" + vector[1]);
            assertEquals("19" + vector[0] + vector[2], wrapped.hostStoredKey(), vector[0] + "/" + vector[1]);
            assertEquals("08D7B4", wrapped.checkValue());
            assertEquals(vector[3], SafeNetKmOperations.applyVariant(KM, vector[1]));
            assertEquals(KEY24, SafeNetKmOperations.decrypt(vector[2], KM, vector[0], vector[1]).cryptogram());
        }
    }

    /**
     * The tool's decoding screen run on the same 24-byte value: format, variant,
     * what came out, and the check value it printed beside that.
     */
    private static final String[][] DECODED = {
        {"12", "02", "618D1F4D4BE852C8508BE03CCD3AFF5ED9BE349C4CCFD625", "95637B"},
        {"12", "06", "85330D137223A142F85D75FD735F939E9F3E871C61EA546F", "73AF1E"},
        {"12", "08", "B2B4F3703E1C92075E37FC2B6B93FF673C233FB69FD26A22", "8B3715"},
        {"14", "02", "618D1F4D4BE852C851A8A55B449132B127628E043A9BE435", "B9FDC5"},
        {"14", "06", "85330D137223A142F97E309AFAF45E7161E23D8417BE667F", "CDBDA7"},
        {"14", "08", "B2B4F3703E1C92075F14B94CE2383288C2FF852EE9865832", "DF50C6"},
    };

    @Test
    void theToolsDecodingOfTheTripleLengthValueIsReproduced() {
        for (String[] vector : DECODED) {
            WrappedKey decoded = SafeNetKmOperations.decrypt(KEY24, KM, vector[0], vector[1]);

            assertEquals(vector[2], decoded.cryptogram(), vector[0] + "/" + vector[1]);
            assertEquals(vector[3], decoded.checkValue(), vector[0] + "/" + vector[1]);
        }
    }

    @Test
    void aKeyOfTheWrongLengthForTheFormatIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SafeNetKmOperations.encrypt(KEY, KM, "12", "02"));
        assertThrows(IllegalArgumentException.class, () -> SafeNetKmOperations.encrypt(KEY24, KM, "11", "00"));
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
                () -> SafeNetKmOperations.encrypt(KEY, KM, "13", "03"));

        assertTrue(thrown.getMessage().contains("table and not a formula"), thrown.getMessage());
        assertNotEquals(SafeNetKmOperations.applyVariant(KM, "00"),
                SafeNetKmOperations.applyVariant(KM, "01"));
    }

    @Test
    void anUnverifiedKeyFormatIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> SafeNetKmOperations.encrypt(KEY, KM, "10", "00"));

        assertTrue(thrown.getMessage().contains("has not been verified"), thrown.getMessage());
    }

    @Test
    void theVariantTableHoldsOnlyWhatWasSeen() {
        assertEquals(6, SafeNetKmOperations.variants().size());
        assertEquals(0x24, SafeNetKmOperations.variant("02").constant());
        assertEquals(0x20, SafeNetKmOperations.variant("06").constant());
        assertEquals(0x14, SafeNetKmOperations.variant("08").constant());
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
