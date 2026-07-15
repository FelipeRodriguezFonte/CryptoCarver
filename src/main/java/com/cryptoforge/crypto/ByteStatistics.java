package com.cryptoforge.crypto;

/** Non-cryptographic diagnostic statistics for byte buffers. */
public final class ByteStatistics {
    private ByteStatistics() { }

    public static String analyze(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Input cannot be empty");
        int[] frequency = new int[256];
        for (byte value : bytes) frequency[value & 0xFF]++;
        double entropy = 0;
        int distinct = 0;
        for (int count : frequency) {
            if (count == 0) continue;
            distinct++;
            double p = (double) count / bytes.length;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        StringBuilder output = new StringBuilder();
        output.append("Bytes: ").append(bytes.length).append('\n');
        output.append("Distinct values: ").append(distinct).append(" / 256\n");
        output.append(String.format(java.util.Locale.ROOT, "Shannon entropy: %.4f bits/byte\n", entropy));
        output.append("Most frequent bytes: ");
        java.util.List<Integer> indexes = new java.util.ArrayList<>();
        for (int i = 0; i < 256; i++) indexes.add(i);
        indexes.sort((a, b) -> Integer.compare(frequency[b], frequency[a]));
        for (int i = 0; i < 5 && frequency[indexes.get(i)] > 0; i++) {
            if (i > 0) output.append(", ");
            int value = indexes.get(i);
            output.append(String.format("%02X (%d)", value, frequency[value]));
        }
        output.append("\nNote: entropy is indicative only; it does not prove randomness.");
        return output.toString();
    }
}
