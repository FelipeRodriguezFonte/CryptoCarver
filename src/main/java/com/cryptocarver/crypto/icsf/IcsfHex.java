package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.List;

/** Reading the two shapes a host dump writes key tokens in, and writing them back out. */
public final class IcsfHex {

    private IcsfHex() { }

    /**
     * Separators dropped before parsing. The last one is U+00A0: copying from a
     * terminal emulator routinely yields non-breaking spaces, and a token that
     * fails to parse for that reason looks like corrupt input.
     */
    private static final String[] SEPARATORS = {"0x", "0X", ",", ":", "-", "\n", "\r", "\t", " ", " "};

    /**
     * Turns a hex string into bytes, dropping the usual separators.
     *
     * <p>Linear mode: separators are removed and the digits concatenated in
     * order. Serves both "01 AF" and hex stacked one byte per line.</p>
     *
     * @throws IllegalArgumentException on an odd digit count or a non-hex character
     */
    public static byte[] clean(String text) {
        if (text == null) throw new IllegalArgumentException("No input");
        String stripped = text.strip();
        for (String separator : SEPARATORS) {
            stripped = stripped.replace(separator, "");
        }
        if (stripped.length() % 2 != 0) {
            throw new IllegalArgumentException("The number of hexadecimal digits is odd ("
                    + stripped.length() + "). A token is a whole number of bytes.");
        }
        return parseHex(stripped, "Input is not hexadecimal");
    }

    /**
     * De-interleaves a host dump written in TWO ROWS.
     *
     * <p>Some host dumps print each byte in a column: the top row carries the
     * high hex digit and the bottom row the low one, so the bytes have to be read
     * column by column and interleaved character by character:</p>
     *
     * <pre>
     *   row 1:  0123456
     *   row 2:  afcd123
     *           -&gt; 0a 1f 2c 3d 41 52 63
     * </pre>
     *
     * <p>Exactly the first two non-blank lines are taken, and both must have the
     * same number of columns.</p>
     *
     * @throws IllegalArgumentException if there are not exactly two rows, if they
     *                                  differ in length, or if they are not hex
     */
    public static byte[] deinterleaveTwoRows(String text) {
        List<String> rows = new ArrayList<>();
        if (text != null) {
            for (String line : text.split("\\R", -1)) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()) rows.add(trimmed);
            }
        }
        if (rows.size() < 2) {
            throw new IllegalArgumentException(
                    "Two-row mode needs two rows of hexadecimal digits (high row and low row).");
        }
        if (rows.size() > 2) {
            throw new IllegalArgumentException(rows.size() + " rows with content were found; two-row mode "
                    + "takes exactly two (the high-digit row and the low-digit row).");
        }
        String high = rows.get(0).replace(" ", "");
        String low = rows.get(1).replace(" ", "");
        if (high.length() != low.length()) {
            throw new IllegalArgumentException("The two rows have different lengths (" + high.length()
                    + " and " + low.length() + " digits). In two-row format each column is one byte, "
                    + "so they must match.");
        }
        StringBuilder interleaved = new StringBuilder(high.length() * 2);
        for (int index = 0; index < high.length(); index++) {
            interleaved.append(high.charAt(index)).append(low.charAt(index));
        }
        return parseHex(interleaved.toString(), "Input is not hexadecimal (two-row mode)");
    }

    /** Writes bytes back out as two host rows: high digits above, low digits below. */
    public static String toTwoRows(byte[] data) {
        StringBuilder high = new StringBuilder();
        StringBuilder low = new StringBuilder();
        for (byte value : data) {
            String pair = String.format(java.util.Locale.ROOT, "%02X", value & 0xFF);
            high.append(pair.charAt(0));
            low.append(pair.charAt(1));
        }
        return high + "\n" + low;
    }

    /** Uppercase hex of the whole array. */
    public static String hex(byte[] data) {
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte value : data) out.append(String.format(java.util.Locale.ROOT, "%02X", value & 0xFF));
        return out.toString();
    }

    /** Uppercase hex of a slice, clamped so a truncated token cannot throw. */
    public static String hex(byte[] data, int from, int to) {
        return hex(slice(data, from, to));
    }

    /** A copy of {@code [from, to)}, clamped to the array. Never throws on a short token. */
    public static byte[] slice(byte[] data, int from, int to) {
        int start = Math.max(0, Math.min(from, data.length));
        int end = Math.max(start, Math.min(to, data.length));
        byte[] out = new byte[end - start];
        System.arraycopy(data, start, out, 0, out.length);
        return out;
    }

    /** True when every byte of {@code [from, to)} is zero. A short slice counts as zero-filled. */
    public static boolean isAllZero(byte[] data, int from, int to) {
        for (byte value : slice(data, from, to)) {
            if (value != 0) return false;
        }
        return true;
    }

    /** Big-endian unsigned 16-bit value at {@code offset}, or 0 past the end. */
    public static int u16(byte[] data, int offset) {
        if (offset < 0 || offset + 2 > data.length) return 0;
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /** Byte at {@code offset}, or 0 past the end. */
    public static int u8(byte[] data, int offset) {
        return (offset < 0 || offset >= data.length) ? 0 : data[offset] & 0xFF;
    }

    /**
     * Bit {@code n} counted from the most significant bit, the way the manual numbers them
     * (bit 0 is 0x80).
     */
    public static boolean bit(int byteValue, int n) {
        return (byteValue & (0x80 >> n)) != 0;
    }

    /** The offset-annotated hex dump used in the text report. */
    public static String grouped(byte[] data, int perLine) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < data.length; index += perLine) {
            int end = Math.min(index + perLine, data.length);
            StringBuilder columns = new StringBuilder();
            for (int inner = index; inner < end; inner++) {
                if (columns.length() > 0) columns.append(' ');
                columns.append(String.format(java.util.Locale.ROOT, "%02X", data[inner] & 0xFF));
            }
            out.append(String.format(java.util.Locale.ROOT, "  %04d: %s%n", index, columns));
        }
        return out.toString();
    }

    public static String grouped(byte[] data) {
        return grouped(data, 16);
    }

    private static byte[] parseHex(String digits, String failureMessage) {
        byte[] out = new byte[digits.length() / 2];
        for (int index = 0; index < out.length; index++) {
            int high = Character.digit(digits.charAt(index * 2), 16);
            int low = Character.digit(digits.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException(failureMessage + ": '"
                        + digits.charAt(high < 0 ? index * 2 : index * 2 + 1) + "' is not a hex digit");
            }
            out[index] = (byte) ((high << 4) | low);
        }
        return out;
    }
}
