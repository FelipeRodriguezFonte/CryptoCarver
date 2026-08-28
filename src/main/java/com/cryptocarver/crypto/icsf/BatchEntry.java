package com.cryptocarver.crypto.icsf;

/**
 * One token as read from the input, before it is analysed.
 *
 * @param index      position in the batch, 1-based
 * @param label      the label declared ahead of the hex, if there was one
 * @param line       input line where it starts, 1-based
 * @param lineCount  how many input lines it consumed
 * @param format     how the block it came from was read
 * @param data       the token bytes; empty when {@code error} is set
 * @param error      a READING error (not an analysis one), or empty
 */
public record BatchEntry(int index, String label, int line, int lineCount,
                         BatchInputFormat.Resolved format, byte[] data, String error) {

    public BatchEntry {
        label = label == null ? "" : label;
        data = data == null ? new byte[0] : data;
        error = error == null ? "" : error;
    }

    public String hex() {
        return IcsfHex.hex(data);
    }

    /** True when the bytes could not be read at all. */
    public boolean failedToRead() {
        return !error.isEmpty();
    }

    /** "#3" or "#3 (MY.KEY.01)", for report lines that name a token. */
    public String display() {
        return "#" + index + (label.isEmpty() ? "" : " (" + label + ")");
    }
}
