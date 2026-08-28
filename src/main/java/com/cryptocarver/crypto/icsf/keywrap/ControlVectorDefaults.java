package com.cryptocarver.crypto.icsf.keywrap;

import com.cryptocarver.crypto.icsf.IcsfHex;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default Control Vectors per key type and key length, APG Table 676, pp. 1669-1671.
 *
 * <p>These are the CVs the host itself would apply, so producing a token that a real
 * host would accept starts here. On a single-length key the token's right-hand CV is
 * zero (Table 616, offset 40).</p>
 *
 * <p>Distinct from {@link com.cryptocarver.crypto.icsf.DesControlVector}, which reads a
 * CV that already exists to describe what a token permits. This one supplies the CV that
 * a key of a given type ought to carry.</p>
 */
public final class ControlVectorDefaults {

    private ControlVectorDefaults() { }

    /** The CV pair of one key type at one key length. */
    public record Pair(byte[] left, byte[] right) {
        public Pair {
            left = left.clone();
            right = right.clone();
        }

        @Override
        public byte[] left() {
            return left.clone();
        }

        @Override
        public byte[] right() {
            return right.clone();
        }
    }

    /** Key type whose CV is all zeroes, which is how legacy DATA keys were carried. */
    public static final String DATA_ZERO_CV = "DATA (CV cero)";

    private static final Map<String, Map<Integer, Pair>> TABLE = new LinkedHashMap<>();

    private static void put(String type, int length, String left, String right) {
        TABLE.computeIfAbsent(type, k -> new LinkedHashMap<>())
                .put(length, new Pair(IcsfHex.clean(left), IcsfHex.clean(right)));
    }

    static {
        put("CIPHER", 8, "0003710003000000", "0000000000000000");
        put("CIPHER", 16, "0003710003410000", "0003710003210000");
        put("CIPHER", 24, "0003710003600081", "0003710003600081");
        put("CIPHERXI", 16, "000C500003C00000", "000C500003A00000");
        put("CIPHERXL", 16, "000C710003C00000", "000C710003A00000");
        put("CIPHERXO", 16, "000C600003C00000", "000C600003A00000");
        put("CVARDEC", 8, "003F420003000000", "0000000000000000");
        put("CVARENC", 8, "003F480003000000", "0000000000000000");
        put("CVARPINE", 8, "003F410003000000", "0000000000000000");
        put("CVARXCVL", 8, "003F440003000000", "0000000000000000");
        put("CVARXCVR", 8, "003F470003000000", "0000000000000000");
        put("DATA", 8, "00007D0003000000", "0000000000000000");
        put("DATA", 16, "00007D0003410000", "00007D0003210000");
        put("DATA", 24, "00007D0003600081", "00007D0003600081");
        put("DATA (CV cero)", 8, "0000000000000000", "0000000000000000");
        put("DATA (CV cero)", 16, "0000000000000000", "0000000000000000");
        put("DATA (CV cero)", 24, "0000000000000000", "0000000000000000");
        put("DATAC", 16, "0000710003410000", "0000710003210000");
        put("DATAM", 16, "00004D0003410000", "00004D0003210000");
        put("DATAMV", 16, "0000440003410000", "0000440003210000");
        put("DECIPHER", 8, "0003500003000000", "0000000000000000");
        put("DECIPHER", 16, "0003500003410000", "0003500003210000");
        put("DECIPHER", 24, "0003500003600081", "0003500003600081");
        put("DKYGENKY", 16, "0071440003410000", "0071440003210000");
        put("ENCIPHER", 8, "0003600003000000", "0000000000000000");
        put("ENCIPHER", 16, "0003600003410000", "0003600003210000");
        put("ENCIPHER", 24, "0003600003600081", "0003600003600081");
        put("EXPORTER", 16, "00417D0003410000", "00417D0003210000");
        put("EXPORTER", 24, "00417D0003600081", "00417D0003600081");
        put("IKEYXLAT", 16, "0042420003410000", "0042420003210000");
        put("IMP-PKA", 16, "0042050003410000", "0042050003210000");
        put("IMP-PKA", 24, "0042050003600081", "0042050003600081");
        put("IMPORTER", 16, "00427D0003410000", "00427D0003210000");
        put("IMPORTER", 24, "00427D0003600081", "00427D0003600081");
        put("IPINENC", 16, "00215F0003410000", "00215F0003210000");
        put("IPINENC", 24, "00215F0003600000", "00215F0003600000");
        put("MAC", 8, "00054D0003000000", "0000000000000000");
        put("MAC", 16, "00054D0003410000", "00054D0003210000");
        put("MAC", 24, "00054D0003600081", "00054D0003600081");
        put("MACVER", 8, "0005440003000000", "0000000000000000");
        put("MACVER", 16, "0005440003410000", "0005440003210000");
        put("MACVER", 24, "0005440003600081", "0005440003600081");
        put("OKEYXLAT", 16, "0041420003410000", "0041420003210000");
        put("OPINENC", 16, "0024770003410000", "0024770003210000");
        put("OPINENC", 24, "0024770003600081", "0024770003600081");
        put("PINGEN", 16, "00227E0003410000", "00227E0003210000");
        put("PINGEN", 24, "00227E0003600081", "00227E0003600081");
        put("PINVER", 16, "0022420003410000", "0022420003210000");
        put("PINVER", 24, "0022420003600081", "0022420003600081");
    }

    /** Every key type the table defines, in alphabetical order. */
    public static List<String> keyTypes() {
        List<String> types = new ArrayList<>(TABLE.keySet());
        java.util.Collections.sort(types);
        return types;
    }

    /** The key lengths Table 676 defines for a key type. */
    public static List<Integer> lengthsFor(String type) {
        Map<Integer, Pair> row = TABLE.get(type);
        if (row == null) return List.of();
        return new ArrayList<>(row.keySet());
    }

    public static boolean knows(String type) {
        return TABLE.containsKey(type);
    }

    /**
     * The default CV pair of a key type at a key length.
     *
     * @throws IllegalArgumentException when the type is unknown, or Table 676 defines no
     *                                  CV for that type at that length
     */
    public static Pair forType(String type, int keyLength) {
        Map<Integer, Pair> row = TABLE.get(type);
        if (row == null) throw new IllegalArgumentException("Unknown key type: " + type);
        Pair pair = row.get(keyLength);
        if (pair == null) {
            StringBuilder available = new StringBuilder();
            for (Integer length : row.keySet()) {
                if (available.length() > 0) available.append(", ");
                available.append(length).append(" bytes");
            }
            throw new IllegalArgumentException("Table 676 defines no " + type + " CV for a "
                    + keyLength + "-byte key (defined: " + available + ").");
        }
        return pair;
    }

    /**
     * The key type a left-hand CV names, when Table 676 knows one.
     *
     * <p>Bit 17 (export) and bit 23 (the parity of byte 2) are masked off: both move
     * without changing which key type the CV is.</p>
     */
    public static Optional<String> typeOf(byte[] leftCv) {
        if (leftCv == null || leftCv.length < 3) return Optional.empty();
        if (IcsfHex.isAllZero(leftCv, 0, leftCv.length)) return Optional.of(DATA_ZERO_CV);
        int wantedByte1 = leftCv[1] & 0xFF;
        int wantedByte2 = leftCv[2] & 0b00111110;
        for (Map.Entry<String, Map<Integer, Pair>> entry : TABLE.entrySet()) {
            for (Pair pair : entry.getValue().values()) {
                byte[] left = pair.left();
                if (IcsfHex.isAllZero(left, 0, left.length)) continue;
                if ((left[1] & 0xFF) == wantedByte1 && (left[2] & 0b00111110) == wantedByte2) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /** Key form from CV bits 40-42, APG p. 1678. Returned as a stable code, not prose. */
    public static String keyForm(byte[] cv) {
        if (cv == null || cv.length < 6) return "";
        return switch ((cv[5] >> 5) & 0b111) {
            case 0b000 -> "SIMPLE";
            case 0b010 -> "LEFT_HALF_OF_DOUBLE";
            case 0b001 -> "RIGHT_HALF_OF_DOUBLE";
            case 0b011 -> "TRIPLE";
            case 0b110 -> "LEFT_HALF_GUARANTEED_UNIQUE";
            case 0b101 -> "RIGHT_HALF_GUARANTEED_UNIQUE";
            case 0b111 -> "TRIPLE_GUARANTEED_UNIQUE";
            default -> "RESERVED";
        };
    }
}
