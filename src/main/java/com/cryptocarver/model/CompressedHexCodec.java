package com.cryptocarver.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the two-column hexadecimal representation used by some host tools.
 * A host view such as {@code AFD12\nCA123} is read by columns and expands to
 * {@code ACFAD11223}.  The inverse operation splits even and odd positions.
 */
public final class CompressedHexCodec {
    private CompressedHexCodec() { }

    /** Interleaves exactly two equal-length hexadecimal rows. */
    public static String expandTwoRows(String twoRows) {
        if (twoRows == null || twoRows.isBlank()) {
            throw new IllegalArgumentException("Provide two hexadecimal rows");
        }
        List<String> rows = new ArrayList<>();
        for (String line : twoRows.strip().split("\\R")) {
            String normalized = normalize(line);
            if (!normalized.isEmpty()) {
                rows.add(normalized);
            }
        }
        if (rows.size() != 2) {
            throw new IllegalArgumentException("Compressed hex must contain exactly two non-empty rows");
        }
        if (rows.get(0).length() != rows.get(1).length()) {
            throw new IllegalArgumentException("Both hexadecimal rows must have the same length");
        }

        StringBuilder expanded = new StringBuilder(rows.get(0).length() * 2);
        for (int index = 0; index < rows.get(0).length(); index++) {
            expanded.append(rows.get(0).charAt(index));
            expanded.append(rows.get(1).charAt(index));
        }
        return expanded.toString();
    }

    /** Splits a hexadecimal value into the two rows expected by the host view. */
    public static String compressToTwoRows(String hex) {
        String normalized = normalize(hex);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Provide a hexadecimal value");
        }
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("Hexadecimal input must contain an even number of characters");
        }

        StringBuilder firstRow = new StringBuilder(normalized.length() / 2);
        StringBuilder secondRow = new StringBuilder(normalized.length() / 2);
        for (int index = 0; index < normalized.length(); index += 2) {
            firstRow.append(normalized.charAt(index));
            secondRow.append(normalized.charAt(index + 1));
        }
        return firstRow.append(System.lineSeparator()).append(secondRow).toString();
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        if (!normalized.isEmpty() && !normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException("Input contains non-hexadecimal characters");
        }
        return normalized;
    }
}
