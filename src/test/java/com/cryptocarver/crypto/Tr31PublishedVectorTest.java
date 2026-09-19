package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped TR-31 implementation against a key block produced elsewhere.
 *
 * <p>Everything else that tests {@link TR31} wraps a key and unwraps it again.
 * That proves the two halves agree with each other, which they do even when
 * both derive the key block keys wrongly — the same blind spot that cost this
 * branch an afternoon on the Thales variant scheme, where two published
 * descriptions put a XOR in two different wrong bytes and every round trip
 * stayed green.</p>
 *
 * <p>The block below was produced by another implementation entirely: it is the
 * worked example in the README of <a href="https://github.com/openemv/tr31">
 * openemv/tr31</a>, a library for ANSI X9.143, ASC X9 TR-31 and ISO 20038
 * (retrieved 2026-09-19), together with the protection key that README uses to
 * decode it.</p>
 *
 * <p>The recovered key is not asserted, because the README does not print it.
 * It does not need to be. A version 'B' key block is authenticated with a CMAC
 * over the header and the key data under a key derived from the KBPK, and that
 * MAC is also the encryption IV. Deriving either key differently fails the MAC,
 * so the MAC verifying <em>is</em> the proof that the derivation is right.</p>
 */
class Tr31PublishedVectorTest {

    /** openemv/tr31 README, "Decoding with decryption". */
    private static final String KEY_BLOCK =
            "B0128B1TX00N0300KS18FFFF00A0200001E00000KC0C000169E3KP0C00ECAD626F9F1A826814"
                    + "AA066D86C8C18BD0E14033E1EBEC75BEDF586E6E325F3AA8C0E5";
    private static final String KBPK = "AB2E09DB3EF0BA71E0CE6CD755C23A3B";

    @Test
    void aKeyBlockFromAnotherImplementationUnwrapsAndAuthenticates() throws Exception {
        TR31.UnwrapResult result = new TR31(KBPK).unwrap(KEY_BLOCK);

        assertNotNull(result.key, "the key data was not recovered");
        // The header is not the sixteen fixed characters: it runs to the end of
        // the last optional block, and all of it is under the MAC.
        assertEquals(KEY_BLOCK.substring(0, 64), result.header,
                "the header comes back as it went in, optional blocks included");
        // 64 characters follow the header: 48 of ciphertext and a 16-character
        // MAC. Those 24 encrypted bytes hold a 2-byte bit length, the key and
        // random padding, so the key itself is 16 — a double-length 3DES key,
        // which is what usage 'B1' with algorithm 'T' should carry.
        assertEquals(16, result.key.length);
    }

    @Test
    void theHeaderSaysWhatTheStandardSaysItSays() throws Exception {
        // B 0128 B1 T X 00 N 03 00 — version B, 128 characters, a BDK ('B1'),
        // 3DES ('T'), derivation only ('X'), no export ('N'), three optional
        // blocks. Reading it by hand keeps the test honest about what block
        // this is, rather than trusting whatever the parser decides.
        assertEquals('B', KEY_BLOCK.charAt(0));
        assertEquals("0128", KEY_BLOCK.substring(1, 5));
        assertEquals(128, KEY_BLOCK.length());
        assertEquals("B1", KEY_BLOCK.substring(5, 7));
        assertEquals('T', KEY_BLOCK.charAt(7));
        assertEquals('X', KEY_BLOCK.charAt(8));
        assertEquals('N', KEY_BLOCK.charAt(11));
        assertEquals("03", KEY_BLOCK.substring(12, 14));
    }

    @Test
    void aSingleAlteredCharacterBreaksTheAuthentication() throws Exception {
        // If this passed, the MAC would not be being checked at all, and the
        // test above would prove nothing.
        String tampered = KEY_BLOCK.substring(0, 60) + flip(KEY_BLOCK.charAt(60))
                + KEY_BLOCK.substring(61);

        assertThrows(Exception.class, () -> new TR31(KBPK).unwrap(tampered),
                "a key block whose bytes changed must not authenticate");
    }

    @Test
    void theWrongProtectionKeyIsRejected() {
        String otherKbpk = "AB2E09DB3EF0BA71E0CE6CD755C23A3C";

        assertThrows(Exception.class, () -> new TR31(otherKbpk).unwrap(KEY_BLOCK));
    }

    @Test
    void whatThisImplementationWrapsItAlsoUnwraps() throws Exception {
        // Kept because it is still worth knowing, but note that it is the
        // weaker check: it would stay green with both halves wrong together.
        String key = "0123456789ABCDEFFEDCBA9876543210";
        TR31 tr31 = new TR31(KBPK);

        String block = tr31.wrap("B0000P0TB00N0000", HexFormat.of().parseHex(key));
        TR31.UnwrapResult recovered = tr31.unwrap(block);

        assertEquals(key, HexFormat.of().formatHex(recovered.key).toUpperCase(Locale.ROOT));
        assertTrue(block.startsWith("B"), block);
    }

    private static char flip(char value) {
        return value == '0' ? '1' : '0';
    }
}
