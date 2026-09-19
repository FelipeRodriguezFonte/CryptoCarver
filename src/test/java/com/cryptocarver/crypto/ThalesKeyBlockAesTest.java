package com.cryptocarver.crypto;

import com.cryptocarver.crypto.ThalesKeyBlockOperations.Unwrapped;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AES Key Block scheme — version ID {@code '1'}.
 *
 * <p>This was refused by name until today. Version {@code '1'} does not vary
 * its LMK the way version {@code '0'} does, it derives from it, and chapter 8
 * of the payShield manual says only "a variant of the LMK" for both. Guessing
 * at a key derivation function is not a thing one does.</p>
 *
 * <p>Source: EFTLab BP-Tools Cryptographic Calculator 21.06, Thales Key Block,
 * captured 2026-09-19. The tool prints the derived KBEK and KBAK beside the
 * block, which is the whole reason this is now known.</p>
 */
class ThalesKeyBlockAesTest {

    private static final String KBPK =
            "9B71333A13F9FAE72F9D0E2DAB4AD6784718012F9244033F3F26A2DE0C8AA11A";
    private static final String KEY = "000102030405060708090A0B0C0D0E0F";
    private static final String HEADER = "10096B0TN00E0002";
    private static final String PADDING = "53F831C75646159457F7FDC0B5D3";
    private static final String BLOCK = HEADER
            + "F21247ABCAF1AAB60415020B95858FA2D3D35DCE0B9CA675739A1C6EC3670611"
            + "07D66472D5097626";

    // =====================================================================
    // The derivation
    // =====================================================================

    @Test
    void theDerivedKeysAreTheOnesTheToolPrinted() {
        assertEquals("1B39BAA881FCEDC1CD5138CC5A31F2E82E1BB7EA3B29CF875F6AAB21268F0D17",
                ThalesKeyBlockOperations.aesEncryptionKey(KBPK));
        assertEquals("B9C8CD6543528A1C6859765503FBB3A87C533BC73933513AF534A90F525308F2",
                ThalesKeyBlockOperations.aesAuthenticationKey(KBPK));
    }

    /**
     * The two derivations differ in one bit of one byte of the derivation
     * data — the key usage, {@code 0000} against {@code 0001}. Worth pinning,
     * because an implementation that forgot to vary it would still round-trip
     * perfectly against itself and would encrypt and authenticate with the
     * same key.
     */
    @Test
    void theEncryptionAndAuthenticationKeysAreNotTheSameKey() {
        assertNotEquals(ThalesKeyBlockOperations.aesEncryptionKey(KBPK),
                ThalesKeyBlockOperations.aesAuthenticationKey(KBPK));
    }

    @Test
    void theDerivationIsNotTheVariantMethodOfTheThreeDesScheme() {
        String shared = "000102030405060708090A0B0C0D0E0F";

        assertNotEquals(ThalesKeyBlockOperations.encryptionKey(shared),
                ThalesKeyBlockOperations.aesEncryptionKey(shared),
                "version '1' derives; version '0' varies. They are different constructions");
    }

    // =====================================================================
    // The block
    // =====================================================================

    @Test
    void theCapturedVectorIsReproducedCharacterForCharacter() {
        assertEquals(BLOCK, ThalesKeyBlockOperations.wrap(KBPK, HEADER, KEY, PADDING));
    }

    @Test
    void theVectorUnwrapsToItsKeyWithAMatchingAuthenticator() {
        Unwrapped unwrapped = ThalesKeyBlockOperations.unwrap(KBPK, BLOCK);

        assertEquals(KEY, unwrapped.clearKey());
        assertEquals(128, unwrapped.keyBits());
        assertEquals(PADDING, unwrapped.padding());
        assertTrue(unwrapped.authentic());
        assertEquals("07D66472D5097626", unwrapped.expectedAuthenticator());
    }

    @Test
    void theAuthenticatorIsEightBytesWhereTheThreeDesSchemeUsesFour() {
        assertEquals(16, ThalesKeyBlockOperations.parse(BLOCK).authenticator().length());
    }

    @Test
    void theBlockDeclaresItsOwnLength() {
        assertEquals(BLOCK.length(), 96);
        assertEquals("0096", BLOCK.substring(1, 5));
    }

    /**
     * The header is the initialisation vector — all sixteen characters of it,
     * one whole AES block, where the 3DES scheme takes the first eight. So a
     * changed header corrupts the key rather than only failing the MAC.
     */
    @Test
    void alteringTheHeaderCorruptsTheKeyAndNotJustTheAuthenticator() {
        String altered = "10096B0TN00E0003" + BLOCK.substring(16);

        Unwrapped unwrapped = ThalesKeyBlockOperations.unwrap(KBPK, altered);

        assertFalse(unwrapped.authentic());
        assertNotEquals(KEY, unwrapped.clearKey());
    }

    /**
     * With the wrong LMK the recovered length is nonsense, and the code leads
     * with the authenticator rather than with the nonsense: a key length of
     * 47316 bits is a symptom, not a finding.
     */
    @Test
    void theWrongLmkIsReportedAsAFailedAuthenticatorAndNotAsAStrangeLength() {
        String wrong = "9B71333A13F9FAE72F9D0E2DAB4AD6784718012F9244033F3F26A2DE0C8AA11B";

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ThalesKeyBlockOperations.unwrap(wrong, BLOCK));

        assertTrue(thrown.getMessage().startsWith("The authenticator does not match"),
                thrown.getMessage());
    }

    /**
     * A second capture, same KBPK, different key and a different header — the
     * algorithm character moves from {@code T} to {@code A}. Two vectors under
     * one KBPK are what separate "the derivation is right" from "the
     * derivation happens to work for this one header", because the header is
     * the initialisation vector.
     *
     * <p>Source: the same tool, captured 2026-09-19.</p>
     */
    @Test
    void aSecondCaptureWithADifferentHeaderIsAlsoReproduced() {
        String header = "10096B0AN00E0002";
        String key = "0D6B02388AC8EF491902342C5B0EDAD5";
        String padding = "1FCF86E2401375FF98B3D12C6D0D";
        String block = header
                + "1DFDC97FED3ACD531E0F85F811B10D8700B58E7D5CD72CB465DCCE8B79B67CDE"
                + "6298505CCB7BAD47";

        assertEquals(block, ThalesKeyBlockOperations.wrap(KBPK, header, key, padding));

        Unwrapped unwrapped = ThalesKeyBlockOperations.unwrap(KBPK, block);
        assertEquals(key, unwrapped.clearKey());
        assertEquals(padding, unwrapped.padding());
        assertTrue(unwrapped.authentic());
    }

    // =====================================================================
    // The other KBPK lengths, which have no vector
    // =====================================================================

    /**
     * A 256-bit KBPK needs two CMAC blocks; a 128-bit one needs a single block
     * and a different algorithm code in the derivation data. Only the 256-bit
     * case was captured, so this asserts the shape rather than the value, and
     * says so.
     */
    @Test
    void shorterKeyBlockLmksDeriveKeysOfTheirOwnLength() {
        assertEquals(32, ThalesKeyBlockOperations
                .aesEncryptionKey("000102030405060708090A0B0C0D0E0F").length());
        assertEquals(48, ThalesKeyBlockOperations
                .aesEncryptionKey("000102030405060708090A0B0C0D0E0F1011121314151617").length());
    }

    @Test
    void aTwoHundredAndFiftySixBitKeyRoundTripsUnderTheAesScheme() {
        String header = "10144D0AN00E0000";
        String key = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";

        String block = ThalesKeyBlockOperations.wrap(KBPK, header, key, null);
        Unwrapped unwrapped = ThalesKeyBlockOperations.unwrap(KBPK, block);

        assertEquals(key, unwrapped.clearKey());
        assertEquals(256, unwrapped.keyBits());
        assertTrue(unwrapped.authentic());
    }

    /**
     * And the standing caveat: the round trip above agrees with itself. It
     * proves the code is self-consistent for a 256-bit key, not that a payShield
     * would accept the block. Only the 128-bit vector above is evidence.
     */
    @Test
    void theThreeDesSchemeStillWorksAndIsStillADifferentConstruction() {
        String desBlock = "00072B0TN00E0002"
                + "56A37F894FD49E61DD3FA27FDE8919D07F7AA966F8BF39AB"
                + "31D00034";

        Unwrapped unwrapped = ThalesKeyBlockOperations
                .unwrap("0123456789ABCDEF8080808080808080FEDCBA9876543210", desBlock);

        assertEquals("735B3125EFF2E04ABFBFA1670180A168", unwrapped.clearKey());
        assertTrue(unwrapped.authentic());
    }
}
