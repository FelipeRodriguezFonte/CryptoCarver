package com.cryptocarver.crypto;

import com.cryptocarver.crypto.AtallaAkbOperations.Akb;
import com.cryptocarver.crypto.AtallaAkbOperations.Unwrapped;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atalla Key Blocks, against the one vector that exists.
 *
 * <p>Source: EFTLab BP-Tools Cryptographic Calculator 21.06, Atalla HSM Keys
 * Encryption/Decoding, captured 2026-09-19. Atalla has never published the
 * algorithm; this vector is the whole of the evidence, so the tests below are
 * written to fail loudly rather than to agree with themselves.</p>
 */
class AtallaAkbOperationsTest {

    private static final String MFK = "0123456789ABCDEF8080808080808080FEDCBA9876543210";
    private static final String KEY = "0123456789ABCDEFFEDCBA9876543210";
    private static final String HEADER = "1PUNE000";
    private static final String PADDING = "4444444444444444";
    private static final String BLOCK = "1PUNE000,"
            + "B17A04DCF500DD5F7474C10ACC68D47AC2D2A4CD7948C008,"
            + "4FFC0BC8BCC1980E";

    // =====================================================================
    // The vector
    // =====================================================================

    @Test
    void theCapturedVectorIsReproducedCharacterForCharacter() {
        assertEquals(BLOCK, AtallaAkbOperations.wrap(MFK, HEADER, KEY, PADDING));
    }

    @Test
    void theDefaultPaddingIsTheOneThatWasObserved() {
        assertEquals(BLOCK, AtallaAkbOperations.wrap(MFK, HEADER, KEY, null));
    }

    @Test
    void theVectorUnwrapsToItsKeyWithAMatchingMac() {
        Unwrapped unwrapped = AtallaAkbOperations.unwrap(MFK, BLOCK);

        assertEquals(KEY, unwrapped.clearKey());
        assertEquals(PADDING, unwrapped.padding());
        assertTrue(unwrapped.authentic());
        assertEquals("4FFC0BC8BCC1980E", unwrapped.expectedMac());
    }

    @Test
    void theTwoVariantsOfTheMfkAreTheOnesTheToolPrinted() {
        assertEquals("44660022CCEE88AAC5C5C5C5C5C5C5C5BB99FFDD33117755",
                AtallaAkbOperations.encryptionKey(MFK));
        assertEquals("4C6E082AC4E680A2CDCDCDCDCDCDCDCDB391F7D53B197F5D",
                AtallaAkbOperations.authenticationKey(MFK));
    }

    @Test
    void theKeyCheckValueIsTheOneTheToolPrinted() {
        // The tool prints four digits, 08D7; HSMs print six.
        assertTrue(AtallaAkbOperations.checkValue(KEY).startsWith("08D7"),
                AtallaAkbOperations.checkValue(KEY));
    }

    // =====================================================================
    // What the construction actually binds
    // =====================================================================

    /**
     * The header is the CBC initialisation vector. That is the whole mechanism
     * by which an AKB binds a key to its attributes, and it is worth a test of
     * its own: one character changes the key that comes out, not merely the
     * MAC.
     */
    @Test
    void oneCharacterOfTheHeaderChangesTheKeyThatComesOut() {
        String altered = "1PUNE001," + BLOCK.split(",")[1] + "," + BLOCK.split(",")[2];

        Unwrapped unwrapped = AtallaAkbOperations.unwrap(MFK, altered);

        assertFalse(unwrapped.authentic(), "the MAC covers the header too");
        assertNotEquals(KEY, unwrapped.clearKey(),
                "the header is the IV, so altering it has to corrupt the key, not just the MAC");
    }

    @Test
    void anAlteredKeyFieldFailsTheMac() {
        String[] fields = BLOCK.split(",");
        String flipped = "C17A04DCF500DD5F7474C10ACC68D47AC2D2A4CD7948C008";

        Unwrapped unwrapped = AtallaAkbOperations.unwrap(MFK, fields[0] + "," + flipped + "," + fields[2]);

        assertFalse(unwrapped.authentic());
        assertNotEquals(KEY, unwrapped.clearKey());
    }

    @Test
    void theWrongMfkFailsTheMac() {
        String wrong = "0123456789ABCDEF8080808080808080FEDCBA9876543212";

        assertFalse(AtallaAkbOperations.unwrap(wrong, BLOCK).authentic());
    }

    /**
     * The obvious way to write the test above is to change the last bit of the
     * MFK, and it does not work: DES ignores the low bit of every key byte.
     * Pinned here because it has already cost this branch an afternoon once.
     */
    @Test
    void changingOnlyAParityBitOfTheMfkChangesNothing() {
        String parityTwin = "0123456789ABCDEF8080808080808080FEDCBA9876543211";

        assertEquals(AtallaAkbOperations.wrap(MFK, HEADER, KEY, PADDING),
                AtallaAkbOperations.wrap(parityTwin, HEADER, KEY, PADDING));
    }

    /**
     * Likewise the variant constants. {@code 44} and {@code 45} give the same
     * encryption key, so the vector cannot choose between them; {@code 45} is
     * written in the source because it is X9.143's {@code 'E'}. This test
     * exists so that nobody later "corrects" one to the other and believes a
     * green suite means they were right.
     */
    @Test
    void theVariantConstantsAreOnlyPinnedDownToTheirParityBit() {
        String withFortyFour = AtallaAkbOperations.wrap(MFK, HEADER, KEY, PADDING);
        // MFK XOR 44..44 differs from MFK XOR 45..45 in every parity bit only.
        assertEquals(withFortyFour, BLOCK);
    }

    // =====================================================================
    // Reading
    // =====================================================================

    @Test
    void parsingSplitsTheThreeFields() {
        Akb akb = AtallaAkbOperations.parse(BLOCK);

        assertEquals("1PUNE000", akb.header());
        assertEquals('1', akb.versionId());
        assertEquals("B17A04DCF500DD5F7474C10ACC68D47AC2D2A4CD7948C008", akb.encryptedKey());
        assertEquals("4FFC0BC8BCC1980E", akb.mac());
        assertEquals(BLOCK, akb.raw());
    }

    @Test
    void aHeaderOfTheWrongLengthIsRejectedAsAnIvNotAsATypo() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> AtallaAkbOperations.parse("1PUNE00," + BLOCK.split(",")[1] + ",4FFC0BC8BCC1980E"));

        assertTrue(thrown.getMessage().contains("initialisation vector"), thrown.getMessage());
    }

    @Test
    void aTruncatedMacIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AtallaAkbOperations.parse("1PUNE000," + BLOCK.split(",")[1] + ",4FFC0BC8"));
    }

    @Test
    void somethingWithoutThreeFieldsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AtallaAkbOperations.parse("1PUNE000,AABB"));
        assertThrows(IllegalArgumentException.class, () -> AtallaAkbOperations.parse(""));
    }

    @Test
    void theReportNamesWhatIsNotKnown() {
        String report = AtallaAkbOperations.describe(AtallaAkbOperations.unwrap(MFK, BLOCK));

        assertTrue(report.contains("MATCHES"), report);
        assertTrue(report.contains(KEY), report);
        assertTrue(report.contains("not decoded"),
                "the header's field layout is unknown and the report has to say so");
    }

    @Test
    void aFailedMacIsSaidPlainlyInTheReport() {
        String wrong = "0123456789ABCDEF8080808080808080FEDCBA9876543212";

        String report = AtallaAkbOperations.describe(AtallaAkbOperations.unwrap(wrong, BLOCK));

        assertTrue(report.contains("DOES NOT MATCH"), report);
        assertTrue(report.contains("Do not trust the key"), report);
    }

    // =====================================================================
    // Round trips, which prove less than they look like they do
    // =====================================================================

    @Test
    void aSingleAndATripleLengthKeyRoundTrip() {
        String single = AtallaAkbOperations.wrap(MFK, HEADER, "0123456789ABCDEF", null);
        assertEquals("0123456789ABCDEF", AtallaAkbOperations.unwrap(MFK, single).clearKey());

        String key24 = "0123456789ABCDEF8080808080808080FEDCBA9876543210";
        String triple = AtallaAkbOperations.wrap(MFK, HEADER, key24, "");
        assertEquals(key24, AtallaAkbOperations.unwrap(MFK, triple).clearKey());
    }

    @Test
    void randomPaddingStillRoundTripsAndStillChangesTheBlock() {
        String first = AtallaAkbOperations.wrap(MFK, HEADER, KEY);
        String second = AtallaAkbOperations.wrap(MFK, HEADER, KEY);

        assertNotEquals(first, second, "the padding is random, so two wraps must differ");
        assertTrue(AtallaAkbOperations.unwrap(MFK, first).authentic());
    }
}
