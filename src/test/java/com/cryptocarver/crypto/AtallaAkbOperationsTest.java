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

    /**
     * A second capture, and the one that settles the padding rule: a
     * <b>single</b>-length key under a different MFK, read back through the
     * tool's AKB Decode tab.
     *
     * <p>Source: EFTLab BP-Tools Cryptographic Calculator 21.06, Atalla HSM
     * Keys, AKB Decode, captured 2026-09-19.</p>
     */
    @Test
    void aSingleLengthKeyUnderADifferentMfkIsReproduced() {
        String mfk = "2ABC3DEF4567018998107645FED3CBA20123456789ABCDEF";
        String key = "0000000055556666";
        String block = "1PUNE000,"
                + "D3266EC69C61820019F4A9640A8F603DA14F78E154C7522D,"
                + "55720A06F8964B8F";

        assertEquals(block, AtallaAkbOperations.wrap(mfk, HEADER, key, null));

        Unwrapped unwrapped = AtallaAkbOperations.unwrap(mfk, block);
        assertEquals(key, unwrapped.clearKey());
        assertEquals("53535353535353535353535353535353", unwrapped.padding());
        assertTrue(unwrapped.authentic());
    }

    /**
     * The two vectors together give the rule: the key field is a fixed 24
     * bytes, and the filler names the key's length — {@code 53} is ASCII
     * {@code 'S'} after a single-length key, {@code 44} is {@code 'D'} after a
     * double-length one. That reading of the letters is an inference from two
     * points, not a source, but the rule it describes is observed.
     */
    @Test
    void theFillerNamesTheKeyLength() {
        String mfk = "2ABC3DEF4567018998107645FED3CBA20123456789ABCDEF";

        assertEquals("53535353535353535353535353535353",
                AtallaAkbOperations.unwrap(mfk,
                        AtallaAkbOperations.wrap(mfk, HEADER, "0000000055556666", null)).padding());
        assertEquals("4444444444444444",
                AtallaAkbOperations.unwrap(MFK,
                        AtallaAkbOperations.wrap(MFK, HEADER, KEY, null)).padding());
    }

    /**
     * And the case that follows by arithmetic and has not been seen: a
     * 24-byte key fills the field exactly, so there is no filler at all.
     */
    @Test
    void aTripleLengthKeyLeavesNoRoomForFiller() {
        String key24 = "0123456789ABCDEF8080808080808080FEDCBA9876543210";

        Unwrapped unwrapped = AtallaAkbOperations.unwrap(MFK,
                AtallaAkbOperations.wrap(MFK, HEADER, key24, null));

        assertEquals(key24, unwrapped.clearKey());
        assertEquals("", unwrapped.padding());
    }

    // =====================================================================
    // What the construction actually binds
    // =====================================================================

    /**
     * Three blocks, one key, one MFK, and headers differing by one character.
     * This is the direct evidence that the header is the initialisation
     * vector: nothing else about the input changes, and the whole key field
     * and the MAC change with it.
     *
     * <p>Source: EFTLab BP-Tools Cryptographic Calculator 21.06, captured
     * 2026-09-19.</p>
     */
    @Test
    void threeHeadersOverTheSameKeyGiveThreeDifferentBlocks() {
        String[][] captured = {
            {"1PUNE000", "B17A04DCF500DD5F7474C10ACC68D47AC2D2A4CD7948C008", "4FFC0BC8BCC1980E"},
            {"1PUNE100", "1254CC794315E821A244F07E9CE8B51AC668F54602443390", "292E0D97FB036238"},
            {"1SUNE100", "4969F3DD1D8139F876405697B81080C8782115DA20A65140", "742CF89964633E8C"},
        };

        for (String[] row : captured) {
            assertEquals(row[0] + "," + row[1] + "," + row[2],
                    AtallaAkbOperations.wrap(MFK, row[0], KEY, PADDING), row[0]);
        }
    }

    /**
     * The tool called two of those three headers invalid — {@code B5} may not
     * be {@code '1'} — and <b>built the block anyway</b>. So header validity is
     * advisory, and reading an AKB must not depend on it: a bench that
     * refused these three would refuse blocks a real device produced.
     *
     * <p>What the three captures establish about the header is small and is
     * all that is claimed: the tool numbers its bytes {@code B0} to {@code B7}
     * from zero, byte 5 rejects {@code '1'}, and byte 1 accepts both
     * {@code 'P'} and {@code 'S'}. That is not a field table and this bench
     * does not pretend to have one.</p>
     */
    @Test
    void aHeaderTheToolCallsInvalidIsStillReadAndStillUnwraps() {
        String block = AtallaAkbOperations.wrap(MFK, "1PUNE100", KEY, PADDING);

        Unwrapped unwrapped = AtallaAkbOperations.unwrap(MFK, block);

        assertEquals(KEY, unwrapped.clearKey());
        assertTrue(unwrapped.authentic());
    }

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
