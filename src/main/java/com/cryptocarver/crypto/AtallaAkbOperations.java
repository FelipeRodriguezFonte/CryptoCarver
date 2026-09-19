package com.cryptocarver.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Atalla Key Blocks (AKB) — reading one, checking it, and building one.
 *
 * <p>An AKB is three comma-separated fields:</p>
 *
 * <pre>
 *   1PUNE000,B17A04DCF500DD5F7474C10ACC68D47AC2D2A4CD7948C008,4FFC0BC8BCC1980E
 *   \______/ \_____________________________________________/ \______________/
 *    header            key under the MFK                            MAC
 * </pre>
 *
 * <h2>Where this came from</h2>
 *
 * <p>Not from a specification. Atalla has never published the AKB algorithm,
 * and this bench refused to implement it three times rather than guess: four
 * secondary sources described it, no two of them the same, and a wrapper that
 * agrees with its own unwrapper proves nothing at all.</p>
 *
 * <p>It was settled instead by asking EFTLab's BP-Tools Cryptographic
 * Calculator 21.06 — the tool this bench chases parity with — to protect a
 * known key under a known MFK, and then recovering the construction from that
 * one input/output pair. Every step below was confirmed against it:</p>
 *
 * <ul>
 *   <li><b>The encryption key is {@code MFK XOR 45..45}</b> and <b>the MAC key
 *       is {@code MFK XOR 4D..4D}</b>, across all 24 bytes. That is the same
 *       variant method ANSI X9.143 uses for its versions A and C — {@code 'E'}
 *       for encryption, {@code 'M'} for MAC — which is a pleasant surprise for
 *       a format that predates it.</li>
 *   <li><b>The key is encrypted in 3DES-CBC with the header's eight ASCII bytes
 *       as the IV.</b> This is what binds the header to the key: change one
 *       character of the header and the key decrypts to rubbish. It is also why
 *       the header is exactly eight characters.</li>
 *   <li><b>The MAC covers the header and the <em>encrypted</em> key field</b>,
 *       as a 3DES CBC-MAC with a zero IV, and is the full eight bytes with no
 *       truncation. The same "over the ciphertext, not the plaintext" that
 *       caught us in the Thales Key Block; see
 *       {@link ThalesKeyBlockOperations}.</li>
 * </ul>
 *
 * <p>Note that {@code 44} and {@code 45} both reproduce the vector, and
 * {@code 4C} and {@code 4D} both reproduce the MAC. DES ignores the low bit of
 * every key byte, so a variant constant can never be pinned down past its
 * parity bit by a test vector alone. {@code 45} and {@code 4D} are written here
 * because they are what X9.143 uses and what the ASCII letters {@code 'E'} and
 * {@code 'M'} are.</p>
 *
 * <h2>What is still not known</h2>
 *
 * <p><b>The header's fields.</b> {@code 1PUNE000} parses here as eight
 * characters with a version digit in front, and no further meaning is claimed,
 * because no source for the field table has been verified. Guessing at it would
 * produce a validator that rejects good blocks.</p>
 *
 * <p><b>The triple-length case.</b> Two vectors fix the padding rule: the key
 * field is always 24 bytes, and the spare room is filled with one repeated
 * byte that names the key's length. A 16-byte key is followed by eight
 * {@code 44}s and an 8-byte key by sixteen {@code 53}s — which, read as ASCII,
 * are {@code 'D'} for double and {@code 'S'} for single. That reading is a
 * two-point inference and not a source; what follows from it, that a
 * 24-byte key has no padding at all, is arithmetic rather than a guess, but it
 * has not been seen either. {@link #wrap} still takes the padding as an
 * argument, and its default follows the rule above.</p>
 */
public final class AtallaAkbOperations {

    /** The ASCII header is the CBC IV, so it is exactly one DES block wide. */
    public static final int HEADER_LENGTH = 8;

    /** The key field is a fixed 24 bytes whatever the key's length. */
    private static final int KEY_FIELD_BYTES = 24;

    /** Filler after a single-length key: ASCII 'S'. */
    private static final byte PAD_SINGLE = 0x53;

    /** Filler after a double-length key: ASCII 'D'. */
    private static final byte PAD_DOUBLE = 0x44;

    private AtallaAkbOperations() {
    }

    // =====================================================================
    // The three fields
    // =====================================================================

    /** An AKB as it travels: header, key under the MFK, MAC. */
    public record Akb(String header, String encryptedKey, String mac) {

        /** The comma-separated form a host stores. */
        public String raw() {
            return header + "," + encryptedKey + "," + mac;
        }

        /** The version character, {@code '1'} in everything seen so far. */
        public char versionId() {
            return header.charAt(0);
        }
    }

    /** What an AKB gives back once the MFK opens it. */
    public record Unwrapped(Akb akb, String clearKey, String padding, boolean authentic,
                            String expectedMac) {
    }

    /**
     * Reads an AKB. The header is ASCII and the other two fields are
     * hexadecimal, so this is stricter than it looks: a header of the wrong
     * length is not a formatting quibble, it is a different IV.
     */
    public static Akb parse(String input) {
        String trimmed = input == null ? "" : input.trim().replaceAll("[\\r\\n\\t]", "");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("There is no key block to read");
        }
        String[] fields = trimmed.split(",", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("An AKB is three comma-separated fields — header, "
                    + "key under the MFK, MAC — and this has " + fields.length);
        }
        String header = fields[0];
        if (header.length() != HEADER_LENGTH) {
            throw new IllegalArgumentException("The header is " + HEADER_LENGTH + " characters, "
                    + "not " + header.length() + ". It is the CBC initialisation vector, so its "
                    + "length is not cosmetic");
        }
        if (!header.matches("[\\x20-\\x7E]+")) {
            throw new IllegalArgumentException("The header must be printable ASCII; it is used "
                    + "directly as eight bytes of initialisation vector");
        }
        String encrypted = normalizeHex(fields[1], "the key under the MFK");
        if (encrypted.isEmpty() || (encrypted.length() / 2) % 8 != 0) {
            throw new IllegalArgumentException("The key field is " + (encrypted.length() / 2)
                    + " bytes, which 3DES-CBC cannot have produced");
        }
        String mac = normalizeHex(fields[2], "the MAC");
        if (mac.length() != 16) {
            throw new IllegalArgumentException("The MAC is eight bytes — a whole DES block, "
                    + "untruncated — not " + (mac.length() / 2));
        }
        return new Akb(header, encrypted, mac);
    }

    // =====================================================================
    // The two variants of the MFK
    // =====================================================================

    /** {@code MFK XOR 45..45}: the encryption variant, 'E'. */
    public static String encryptionKey(String mfk) {
        return variantOf(mfk, (byte) 0x45);
    }

    /** {@code MFK XOR 4D..4D}: the authentication variant, 'M'. */
    public static String authenticationKey(String mfk) {
        return variantOf(mfk, (byte) 0x4D);
    }

    private static String variantOf(String mfk, byte variant) {
        byte[] key = masterFileKey(mfk);
        byte[] out = new byte[key.length];
        for (int i = 0; i < key.length; i++) {
            out[i] = (byte) (key[i] ^ variant);
        }
        return hex(out);
    }

    private static byte[] masterFileKey(String mfk) {
        byte[] key = bytes(normalizeHex(mfk, "MFK"));
        if (key.length != 16 && key.length != 24) {
            throw new IllegalArgumentException("An MFK is a double- or triple-length TDES key "
                    + "(16 or 24 bytes), not " + key.length);
        }
        return key;
    }

    // =====================================================================
    // Wrapping
    // =====================================================================

    /**
     * Protects a key under an MFK.
     *
     * @param padding the filler after the key, supplied so a test can be
     *                reproducible; {@code null} repeats the {@code 44} byte
     *                observed in the captured vector, out to 24 bytes
     */
    public static String wrap(String mfk, String header, String clearKey, String padding) {
        String head = header == null ? "" : header.trim();
        if (head.length() != HEADER_LENGTH) {
            throw new IllegalArgumentException("The header is " + HEADER_LENGTH + " characters");
        }
        byte[] key = bytes(normalizeHex(clearKey, "clear key"));
        if (key.length == 0 || key.length % 8 != 0 || key.length > KEY_FIELD_BYTES) {
            throw new IllegalArgumentException("The key is 8, 16 or 24 bytes, not " + key.length);
        }

        byte[] padBytes;
        if (padding == null) {
            padBytes = new byte[KEY_FIELD_BYTES - key.length];
            Arrays.fill(padBytes, fillerFor(key.length));
        } else {
            padBytes = bytes(normalizeHex(padding, "padding"));
            if ((key.length + padBytes.length) % 8 != 0) {
                throw new IllegalArgumentException("The key and its padding come to "
                        + (key.length + padBytes.length) + " bytes, which is not a multiple of 8");
            }
        }

        byte[] plain = concat(key, padBytes);
        byte[] iv = head.getBytes(StandardCharsets.US_ASCII);
        byte[] encrypted = cbc(bytes(encryptionKey(mfk)), iv, plain, true);
        String mac = mac(mfk, head, hex(encrypted));
        return head + "," + hex(encrypted) + "," + mac;
    }

    /** Draws its own padding from a secure random, which is what a real device does. */
    public static String wrap(String mfk, String header, String clearKey) {
        byte[] key = bytes(normalizeHex(clearKey, "clear key"));
        byte[] random = new byte[Math.max(0, KEY_FIELD_BYTES - key.length)];
        new SecureRandom().nextBytes(random);
        // Deliberately not the 'S'/'D' filler: a real device may pad with
        // anything, and a caller who wants the tool's output byte for byte
        // passes null instead.
        return wrap(mfk, header, clearKey, hex(random));
    }

    /**
     * Opens an AKB and checks its MAC.
     *
     * <p>The MAC is checked against the header and the <b>encrypted</b> key
     * field. Both are reported: a caller that ignores {@link Unwrapped#authentic()}
     * and uses the key anyway has learned nothing from the MAC being there.</p>
     */
    public static Unwrapped unwrap(String mfk, String input) {
        Akb akb = parse(input);
        String expected = mac(mfk, akb.header(), akb.encryptedKey());
        boolean authentic = expected.equalsIgnoreCase(akb.mac());

        byte[] iv = akb.header().getBytes(StandardCharsets.US_ASCII);
        byte[] plain = cbc(bytes(encryptionKey(mfk)), iv, bytes(akb.encryptedKey()), false);

        // Nothing in the block says where the key ends: the header carries the
        // length and its field table is not known. So the key is found by
        // stripping whole 8-byte blocks of filler off the end, and both parts
        // are handed back named rather than silently joined.
        //
        // A key whose own last block were entirely 'D' or 'S' would be read
        // short. That is a degenerate key, and the alternative — trusting a
        // header field nobody has documented — is worse.
        int keyBytes = plain.length;
        byte filler = plain[plain.length - 1];
        if (filler == PAD_SINGLE || filler == PAD_DOUBLE) {
            while (keyBytes > 8 && isFiller(plain, keyBytes - 8, filler)) {
                keyBytes -= 8;
            }
        }
        String clearKey = hex(Arrays.copyOfRange(plain, 0, keyBytes));
        String padding = hex(Arrays.copyOfRange(plain, keyBytes, plain.length));
        return new Unwrapped(akb, clearKey, padding, authentic, expected);
    }

    /** ASCII 'S' after a single-length key, 'D' after a double-length one. */
    private static byte fillerFor(int keyLength) {
        return keyLength == 8 ? PAD_SINGLE : PAD_DOUBLE;
    }

    private static boolean isFiller(byte[] plain, int from, byte filler) {
        for (int i = from; i < from + 8; i++) {
            if (plain[i] != filler) {
                return false;
            }
        }
        return true;
    }

    /** 3DES CBC-MAC with a zero IV over the header and the encrypted key field. */
    private static String mac(String mfk, String header, String encryptedKey) {
        byte[] data = concat(header.getBytes(StandardCharsets.US_ASCII),
                bytes(normalizeHex(encryptedKey, "encrypted key")));
        byte[] chained = cbc(bytes(authenticationKey(mfk)), new byte[8], data, true);
        return hex(Arrays.copyOfRange(chained, chained.length - 8, chained.length));
    }

    // =====================================================================
    // Reporting
    // =====================================================================

    public static String describe(Unwrapped unwrapped) {
        Akb akb = unwrapped.akb();
        StringBuilder report = new StringBuilder();
        report.append("Atalla Key Block\n");
        report.append("  header        : ").append(akb.header())
                .append("   (also the CBC initialisation vector)\n");
        report.append("  version       : ").append(akb.versionId()).append('\n');
        report.append("  key under MFK : ").append(akb.encryptedKey()).append('\n');
        report.append("  MAC           : ").append(akb.mac())
                .append(unwrapped.authentic() ? "   MATCHES" : "   DOES NOT MATCH").append('\n');
        if (!unwrapped.authentic()) {
            report.append("                  expected ").append(unwrapped.expectedMac()).append('\n');
        }
        report.append("\nUnwrapped\n");
        report.append("  key           : ").append(unwrapped.clearKey()).append('\n');
        report.append("  padding       : ").append(unwrapped.padding()).append('\n');
        report.append("  check value   : ").append(checkValue(unwrapped.clearKey())).append('\n');
        if (!unwrapped.authentic()) {
            report.append("\nThe key above came out of the block, but the MAC does not match, so\n");
            report.append("either the MFK is wrong or the block was altered. Do not trust the key.\n");
        }
        report.append("\nHeader bytes\n");
        for (int at = 0; at < HEADER_LENGTH; at++) {
            report.append("  B").append(at).append(" : '").append(akb.header().charAt(at)).append("'\n");
        }
        report.append("\nThe header's field layout is not decoded. Atalla has not published it and\n");
        report.append("no source for it has been verified, so this bench reports the header as the\n");
        report.append("eight characters it is rather than inventing meanings for them. They are\n");
        report.append("numbered B0 to B7 above because that is how the vendor's own tool names\n");
        report.append("them when it rejects one, so a complaint about \"B5\" can be located.\n");
        return report.toString();
    }

    /** The first three bytes of the all-zero block under the key, as every HSM prints it. */
    public static String checkValue(String clearKey) {
        byte[] zero = new byte[8];
        return hex(cbc(bytes(normalizeHex(clearKey, "key")), new byte[8], zero, true)).substring(0, 6);
    }

    // =====================================================================
    // Bytes
    // =====================================================================

    private static byte[] cbc(byte[] key, byte[] iv, byte[] data, boolean encrypt) {
        byte[] full = new byte[24];
        if (key.length == 16) {
            System.arraycopy(key, 0, full, 0, 16);
            System.arraycopy(key, 0, full, 16, 8);
        } else if (key.length == 8) {
            for (int i = 0; i < 3; i++) {
                System.arraycopy(key, 0, full, i * 8, 8);
            }
        } else {
            full = key.clone();
        }
        try {
            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(full, "DESede"), new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Triple DES is unavailable", e);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] joined = new byte[left.length + right.length];
        System.arraycopy(left, 0, joined, 0, left.length);
        System.arraycopy(right, 0, joined, left.length, right.length);
        return joined;
    }

    static String normalizeHex(String value, String label) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (cleaned.isEmpty()) {
            return "";
        }
        if (!cleaned.matches("[0-9A-F]+") || (cleaned.length() % 2) != 0) {
            throw new IllegalArgumentException(label + " must be even-length hexadecimal");
        }
        return cleaned;
    }

    private static byte[] bytes(String hex) {
        String normalized = normalizeHex(hex, "hexadecimal value");
        return normalized.isEmpty() ? new byte[0] : HexFormat.of().parseHex(normalized);
    }

    private static String hex(byte[] data) {
        return HexFormat.of().formatHex(data).toUpperCase(Locale.ROOT);
    }
}
