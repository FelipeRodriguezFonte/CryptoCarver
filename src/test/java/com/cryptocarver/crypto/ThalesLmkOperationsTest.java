package com.cryptocarver.crypto;

import com.cryptocarver.crypto.ThalesLmkOperations.KeyType;
import com.cryptocarver.crypto.ThalesLmkOperations.Lmk;
import com.cryptocarver.crypto.ThalesLmkOperations.Match;
import com.cryptocarver.crypto.ThalesLmkOperations.Scheme;
import com.cryptocarver.crypto.ThalesLmkOperations.WrappedKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checked against the worked example in the payShield 10K Host Programmer's
 * Manual, clause 7.2.3, which carries every intermediate value.
 *
 * <p>That example matters more than a round trip. Encrypt and decrypt agree
 * with each other even when both apply a variant to the wrong byte, and two
 * widely cited descriptions of this scheme do exactly that, in two different
 * wrong places. Only a vector from the vendor settles it.</p>
 */
class ThalesLmkOperationsTest {

    /** Clause 7.2.3 — the test LMK pair the worked example uses, stated in full there. */
    private static final Lmk LMK_28_29 = Lmk.of("1A1A1A1A1A1A1A1A1C1C1C1C1C1C1C1C");

    /** Clause 7.2.3 — "Test MK-SMI", and the two components it is formed from. */
    private static final String MK_SMI = "F1F1F1F1F1F1F1F1C1C1C1C1C1C1C1C1";
    private static final String COMPONENT_1 = "50505050505050505050505050505050";
    private static final String COMPONENT_2 = "A1A1A1A1A1A1A1A19090909090909090";

    /** Clause 7.2.3, the printed result of the FK console command. */
    private static final String EXPECTED_CRYPTOGRAM = "5178C9D3D1052B15BF6AEC458B4A4564";
    private static final String EXPECTED_CHECK_VALUE = "8357D9";

    // =====================================================================
    // The manual's worked example, step by step
    // =====================================================================

    @Test
    void theWorkedExampleFromTheManualReproducesByteForByte() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);

        assertEquals(EXPECTED_CRYPTOGRAM, wrapped.cryptogram());
        assertEquals(EXPECTED_CHECK_VALUE, wrapped.checkValue());
        assertEquals("U" + EXPECTED_CRYPTOGRAM, wrapped.tagged());
    }

    /**
     * The FK console session printed in clause 7.2.3 does not produce the key
     * the same clause says it does.
     *
     * <p>Its two components XOR to {@code F1F1...C0C0...}, one bit away from the
     * stated Test MK-SMI of {@code F1F1...C1C1...}. The printed cryptogram is
     * the one for {@code C1C1...}, so the error is in the components: the right
     * half of component 2 should be {@code 91} repeated, not {@code 90}.</p>
     *
     * <p>This is recorded rather than quietly corrected because anyone keying
     * that console session into a real HSM gets a different key and a different
     * check value from the ones the page prints, and will reasonably suspect
     * their HSM before they suspect the manual.</p>
     */
    @Test
    void theComponentsPrintedInTheManualDoNotFormTheKeyItStates() {
        assertEquals("F1F1F1F1F1F1F1F1C0C0C0C0C0C0C0C0", xor(COMPONENT_1, COMPONENT_2),
                "The components as printed are one bit off the stated Test MK-SMI");
        assertEquals(MK_SMI, xor(COMPONENT_1, "A1A1A1A1A1A1A1A19191919191919191"),
                "With 91 in place of 90, the components form the stated key");

        // And the stated key is the one that produces the printed cryptogram,
        // which is what settles which of the two lines is wrong.
        assertEquals(EXPECTED_CRYPTOGRAM,
                ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29).cryptogram());
    }

    private static String xor(String left, String right) {
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < left.length(); i += 2) {
            int a = Integer.parseInt(left.substring(i, i + 2), 16);
            int b = Integer.parseInt(right.substring(i, i + 2), 16);
            combined.append(String.format("%02X", a ^ b));
        }
        return combined.toString();
    }

    @Test
    void theLmkVariantLandsOnTheFirstByteOfTheLmk() {
        // Clause 7.2, step 3: 1A XOR 5A = 40, replacing the leftmost byte.
        assertEquals("401A1A1A1A1A1A1A1C1C1C1C1C1C1C1C",
                ThalesLmkOperations.applyLmkVariant(LMK_28_29, 2, false));
    }

    @Test
    void theSchemeVariantLandsOnTheFirstByteOfTheRightHalf() {
        // Clause 7.2.3, steps 3 and 5: 1C XOR A6 = BA for the left part of the
        // key, 1C XOR 5A = 46 for the right part. Both leave byte 0 as the key
        // type left it.
        String variantLmk = ThalesLmkOperations.applyLmkVariant(LMK_28_29, 2, false);
        assertEquals("401A1A1A1A1A1A1A", variantLmk.substring(0, 16));

        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);
        // The left part of the key encrypted under ...BA1C..., per the manual.
        assertEquals("5178C9D3D1052B15", wrapped.cryptogram().substring(0, 16));
        // The right part under ...461C...
        assertEquals("BF6AEC458B4A4564", wrapped.cryptogram().substring(16));
    }

    @Test
    void theVariantConstantsAreTheOnesTheManualLists() {
        // Clause 7.2. Getting one of these wrong silently produces a key that
        // the HSM will reject as the wrong type rather than as corrupt.
        assertEquals(0xA6, ThalesLmkOperations.variantByte(1));
        assertEquals(0x5A, ThalesLmkOperations.variantByte(2));
        assertEquals(0x6A, ThalesLmkOperations.variantByte(3));
        assertEquals(0xDE, ThalesLmkOperations.variantByte(4));
        assertEquals(0x2B, ThalesLmkOperations.variantByte(5));
        assertEquals(0x50, ThalesLmkOperations.variantByte(6));
        assertEquals(0x74, ThalesLmkOperations.variantByte(7));
        assertEquals(0x9C, ThalesLmkOperations.variantByte(8));
        assertEquals(0xFA, ThalesLmkOperations.variantByte(9));
        assertEquals(0x00, ThalesLmkOperations.variantByte(0));
    }

    @Test
    void theManualsOwnTypoIsNotReproduced() {
        // Clause 7.1 lists the double-length left part as 6A. Its own Example 1
        // and clause 7.2.3 both say A6, and the worked example only comes out
        // right with A6. If someone "corrects" this to 6A, the vector fails —
        // this test says why before they go looking.
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);
        assertEquals(EXPECTED_CRYPTOGRAM, wrapped.cryptogram(),
                "The double-length left part variant is A6, not the 6A printed in clause 7.1");
    }

    // =====================================================================
    // The key type table
    // =====================================================================

    @Test
    void aKeyTypeCodeIsAVariantFollowedByAPairCode() {
        KeyType mkSmi = ThalesLmkOperations.keyType("209");
        assertEquals("MK-SMI", mkSmi.name());
        assertEquals("28-29", mkSmi.lmkPair());
        assertEquals(2, mkSmi.variant());
    }

    @Test
    void theTableCarriesTheKeyTypesTheManualNames() {
        assertEquals("ZMK", ThalesLmkOperations.keyType("000").name());
        assertEquals("04-05", ThalesLmkOperations.keyType("000").lmkPair());
        assertEquals("ZPK / PEK", ThalesLmkOperations.keyType("001").name());
        assertEquals("06-07", ThalesLmkOperations.keyType("001").lmkPair());
        assertEquals("PVK / PVVK", ThalesLmkOperations.keyType("002").name());
        assertEquals("14-15", ThalesLmkOperations.keyType("002").lmkPair());
        assertEquals("TAK", ThalesLmkOperations.keyType("003").name());
        assertEquals("CVK / CSCK", ThalesLmkOperations.keyType("402").name());
        assertEquals("ZAK", ThalesLmkOperations.keyType("008").name());
        assertEquals("26-27", ThalesLmkOperations.keyType("008").lmkPair(),
                "ZAK is pair code 08, which is LMK 26-27, not 28-29");
        assertEquals("BDK-1", ThalesLmkOperations.keyType("009").name());
        assertEquals("28-29", ThalesLmkOperations.keyType("009").lmkPair());
        assertEquals("MK-AC", ThalesLmkOperations.keyType("109").name());
        assertEquals("ZEK", ThalesLmkOperations.keyType("00A").name());
        assertEquals("DEK / TEK", ThalesLmkOperations.keyType("00B").name());
        assertEquals("HMAC", ThalesLmkOperations.keyType("10C").name());
    }

    @Test
    void thePairCodeDoesNotFollowFromArithmetic() {
        // Codes 00 and 01 are LMK 04-05 and 06-07, then 02 jumps to 14-15.
        // Anyone deriving the pair from the code by multiplication gets the
        // first two right and everything after them wrong.
        assertEquals("04-05", ThalesLmkOperations.keyType("000").lmkPair());
        assertEquals("06-07", ThalesLmkOperations.keyType("001").lmkPair());
        assertEquals("14-15", ThalesLmkOperations.keyType("002").lmkPair());
        assertEquals("16-17", ThalesLmkOperations.keyType("003").lmkPair());
    }

    @Test
    void anUnknownKeyTypeSaysHowACodeIsBuilt() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ThalesLmkOperations.keyType("999"));
        assertTrue(thrown.getMessage().contains("[variant][pair code]"), thrown.getMessage());
    }

    // =====================================================================
    // Round trips and schemes
    // =====================================================================

    @Test
    void whatIsWrappedComesBack() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);
        WrappedKey recovered = ThalesLmkOperations.decrypt(wrapped.cryptogram(), "209", Scheme.U, LMK_28_29);

        assertEquals(MK_SMI, recovered.cryptogram());
        assertEquals(EXPECTED_CHECK_VALUE, recovered.checkValue());
    }

    @Test
    void aTaggedCryptogramIsAcceptedAsPasted() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);

        WrappedKey recovered = ThalesLmkOperations.decrypt(wrapped.tagged(), "209", Scheme.U, LMK_28_29);

        assertEquals(MK_SMI, recovered.cryptogram());
    }

    @Test
    void aCryptogramTaggedForAnotherSchemeIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ThalesLmkOperations.decrypt("T" + EXPECTED_CRYPTOGRAM, "209", Scheme.U, LMK_28_29));
        assertTrue(thrown.getMessage().contains("tagged 'T'"), thrown.getMessage());
    }

    @Test
    void theKeyTypeChangesTheResult() {
        // Key separation is the point of the variant: the same key bytes under
        // the same LMK must not come out the same for two different key types.
        String asMkSmi = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29).cryptogram();
        String asMkSmc = ThalesLmkOperations.encrypt(MK_SMI, "309", Scheme.U, LMK_28_29).cryptogram();

        assertFalse(asMkSmi.equals(asMkSmc));
    }

    @Test
    void theTwoHalvesOfADoubleLengthKeyAreNotInterchangeable() {
        // A6 on one half and 5A on the other is what stops a left half being
        // replayed as a right one.
        String key = "0123456789ABCDEF0123456789ABCDEF";
        String cryptogram = ThalesLmkOperations.encrypt(key, "209", Scheme.U, LMK_28_29).cryptogram();

        assertFalse(cryptogram.substring(0, 16).equals(cryptogram.substring(16)),
                "Identical halves must not encrypt to identical blocks");
    }

    @Test
    void aSingleLengthKeyTakesNoSchemeVariant() {
        // Clause 7.1 gives scheme variants only to double- and triple-length
        // keys; a single-length key has no parts to keep in order.
        String key = "0123456789ABCDEF";
        WrappedKey wrapped = ThalesLmkOperations.encrypt(key, "001", Scheme.Z, LMK_28_29);

        assertEquals(key, ThalesLmkOperations.decrypt(wrapped.cryptogram(), "001", Scheme.Z, LMK_28_29)
                .cryptogram());
        assertEquals(wrapped.cryptogram(), wrapped.tagged(), "Scheme Z carries no tag");
    }

    @Test
    void aTripleLengthKeyUsesThreeVariants() {
        String key = "0123456789ABCDEF0123456789ABCDEFFEDCBA9876543210";
        WrappedKey wrapped = ThalesLmkOperations.encrypt(key, "209", Scheme.T, LMK_28_29);

        assertEquals('T', wrapped.tagged().charAt(0));
        assertEquals(key, ThalesLmkOperations.decrypt(wrapped.tagged(), "209", Scheme.T, LMK_28_29)
                .cryptogram());
    }

    @Test
    void aKeyOfTheWrongLengthForItsSchemeIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ThalesLmkOperations.encrypt("0123456789ABCDEF", "209", Scheme.U, LMK_28_29));
        assertTrue(thrown.getMessage().contains("16-byte key"), thrown.getMessage());
    }

    @Test
    void theKeyBlockSchemeIsRefusedByNameRatherThanMisread() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> Scheme.of('S'));
        assertTrue(thrown.getMessage().contains("Key Block"), thrown.getMessage());
    }

    // =====================================================================
    // Components
    // =====================================================================

    @Test
    void aComponentTakesAFurtherFfSoItCannotPassAsAKey() {
        // Clause 7.2.3.1. Without this, one component of a key formed from
        // several would decrypt as though it were the finished key.
        String asKey = ThalesLmkOperations.applyLmkVariant(LMK_28_29, 2, false);
        String asComponent = ThalesLmkOperations.applyLmkVariant(LMK_28_29, 2, true);

        assertEquals("401A1A1A1A1A1A1A1C1C1C1C1C1C1C1C", asKey);
        assertEquals("BF1A1A1A1A1A1A1A1C1C1C1C1C1C1C1C", asComponent, "40 XOR FF = BF");

        String wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29, true).cryptogram();
        assertFalse(wrapped.equals(EXPECTED_CRYPTOGRAM));
    }

    // =====================================================================
    // Lookup
    // =====================================================================

    @Test
    void lookupFindsTheKeyTypeACryptogramWasWrappedUnder() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);

        List<Match> matches = ThalesLmkOperations.lookup(wrapped.tagged(), EXPECTED_CHECK_VALUE, LMK_28_29);

        assertTrue(matches.stream().anyMatch(m -> m.keyType().code().equals("209")),
                "The key type it was wrapped under must be among the matches");
        assertTrue(matches.stream().allMatch(m -> m.clearKey().equals(MK_SMI)),
                "Every match must recover the same key, since only the variant reaches the cipher");
    }

    @Test
    void lookupReportsEveryKeyTypeThatSharesThatVariant() {
        // MK-SMI is variant 2 of pair code 09. Any other key type that is also
        // variant 2 of pair code 09 is indistinguishable, and the report says so
        // rather than picking one and sounding certain.
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);
        List<Match> matches = ThalesLmkOperations.lookup(wrapped.tagged(), EXPECTED_CHECK_VALUE, LMK_28_29);

        String report = ThalesLmkOperations.describe(matches);
        assertTrue(report.contains("209"), report);
        if (matches.size() > 1) {
            assertTrue(report.contains("cannot be told apart"), report);
        }
    }

    @Test
    void lookupFindsNothingUnderTheWrongLmk() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);

        List<Match> matches = ThalesLmkOperations.lookup(wrapped.tagged(), EXPECTED_CHECK_VALUE,
                Lmk.of("0101010101010101F1F1F1F1F1F1F1F1"));

        assertTrue(matches.isEmpty());
        assertTrue(ThalesLmkOperations.describe(matches).contains("not the one it was wrapped under"));
    }

    @Test
    void lookupRejectsACheckValueThatIsNotOne() {
        assertThrows(IllegalArgumentException.class,
                () -> ThalesLmkOperations.lookup("U" + EXPECTED_CRYPTOGRAM, "83", LMK_28_29));
    }

    // =====================================================================
    // Inputs and reporting
    // =====================================================================

    @Test
    void anLmkMustBeDoubleOrTripleLength() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> Lmk.of("0123456789ABCDEF"));
        assertTrue(thrown.getMessage().contains("16 byte"), thrown.getMessage());
    }

    @Test
    void describeShowsBothVariantsAndWhereTheyLand() {
        WrappedKey wrapped = ThalesLmkOperations.encrypt(MK_SMI, "209", Scheme.U, LMK_28_29);

        String report = ThalesLmkOperations.describe(wrapped, LMK_28_29);

        assertTrue(report.contains("MK-SMI"), report);
        assertTrue(report.contains("28-29"), report);
        assertTrue(report.contains("401A1A1A1A1A1A1A1C1C1C1C1C1C1C1C"), report);
        assertTrue(report.contains("XOR A6"), report);
        assertTrue(report.contains("XOR 5A"), report);
        assertTrue(report.contains("right half"), report);
        assertTrue(report.contains(EXPECTED_CHECK_VALUE), report);
    }
}
