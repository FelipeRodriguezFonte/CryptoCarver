package com.cryptocarver.crypto.icsf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the synthetic key tokens the tests analyse.
 *
 * <p>This lives in test scope on purpose. Building tokens needs the default
 * Control Vectors of Table 676, which the analyser never needs: it reads CVs, it
 * does not mint them. Keeping the table here stops it becoming production
 * surface that nothing in production uses.</p>
 */
final class IcsfTestTokens {

    private IcsfTestTokens() { }

    /** Default left/right Control Vectors per key type and key length (Table 676, pp. 1669-1671). */
    private static final Map<String, Map<Integer, String[]>> DEFAULT_CV = buildDefaults();

    private static Map<String, Map<Integer, String[]>> buildDefaults() {
        Map<String, Map<Integer, String[]>> map = new LinkedHashMap<>();
        map.put("DATA", Map.of(
                8, new String[]{"00007D0003000000", "0000000000000000"},
                16, new String[]{"00007D0003410000", "00007D0003210000"},
                24, new String[]{"00007D0003600081", "00007D0003600081"}));
        map.put("CIPHER", Map.of(
                16, new String[]{"0003710003410000", "0003710003210000"},
                24, new String[]{"0003710003600081", "0003710003600081"}));
        map.put("MAC", Map.of(
                8, new String[]{"00054D0003000000", "0000000000000000"},
                16, new String[]{"00054D0003410000", "00054D0003210000"},
                24, new String[]{"00054D0003600081", "00054D0003600081"}));
        map.put("EXPORTER", Map.of(
                16, new String[]{"00417D0003410000", "00417D0003210000"},
                24, new String[]{"00417D0003600081", "00417D0003600081"}));
        map.put("IMPORTER", Map.of(
                16, new String[]{"00427D0003410000", "00427D0003210000"},
                24, new String[]{"00427D0003600081", "00427D0003600081"}));
        map.put("PINVER", Map.of(
                16, new String[]{"0022420003410000", "0022420003210000"}));
        map.put("IPINENC", Map.of(
                16, new String[]{"00215F0003410000", "00215F0003210000"}));
        return map;
    }

    /** The zero Control Vector of a legacy DATA key, which is not in the table above. */
    static final String ZERO_CV_TYPE = "DATA-ZERO-CV";

    static byte[] hex(String value) {
        return IcsfHex.clean(value);
    }

    /** A DES external token of the given type and length, with Table 676's default CV. */
    static byte[] des(String keyType, int keyLength) {
        return des(keyType, keyLength, null, false, null);
    }

    /**
     * A DES external token, with the knobs the tests need.
     *
     * @param keyType    a Table 676 type, or {@link #ZERO_CV_TYPE} for a zero CV
     * @param keyLength  8, 16 or 24
     * @param byte59     overrides byte 59 and recalculates the TVV; {@code null} leaves it alone
     * @param noExport   clears CV bit 17 (NO-XPORT), compensating byte 2's parity
     * @param keyMaterial the ciphertext to embed; {@code null} for a deterministic filler
     */
    static byte[] des(String keyType, int keyLength, Integer byte59,
                      boolean noExport, byte[] keyMaterial) {
        byte[] cvLeft;
        byte[] cvRight;
        if (ZERO_CV_TYPE.equals(keyType)) {
            cvLeft = new byte[8];
            cvRight = new byte[8];
        } else {
            Map<Integer, String[]> byLength = DEFAULT_CV.get(keyType);
            if (byLength == null) throw new IllegalArgumentException("Unknown key type: " + keyType);
            String[] pair = byLength.get(keyLength);
            if (pair == null) {
                throw new IllegalArgumentException("Table 676 defines no " + keyType + " CV for a "
                        + keyLength + "-byte key");
            }
            cvLeft = hex(pair[0]);
            cvRight = hex(pair[1]);
        }
        if (noExport) {
            // bit 17 = 0 (NO-XPORT) and bit 23 compensating byte 2's even-zero parity,
            // which is exactly the pair of bits the type lookup masks out.
            cvLeft[2] ^= 0x41;
            if (!IcsfHex.isAllZero(cvRight, 0, 8)) cvRight[2] ^= 0x41;
        }
        byte[] material = keyMaterial;
        if (material == null) {
            material = new byte[keyLength];
            for (int index = 0; index < keyLength; index++) {
                material[index] = (byte) ((0x11 * (index + 1)) & 0xFF);
            }
        }
        byte[] token = externalDes(material, cvLeft, cvRight, false, 0, false);
        if (byte59 != null) {
            token[59] = byte59.byteValue();
            writeTvv(token);
        }
        return token;
    }

    /**
     * Assembles a 64-byte DES external token (X'02') with its TVV.
     *
     * @param wrapMethod flag byte 7 bits 0-2: 0 ECB, 1 WRAP-ENH, 2 WRAPENH2, 3 WRAPENH3
     */
    static byte[] externalDes(byte[] keyMaterial, byte[] cvLeft, byte[] cvRight,
                              boolean nocv, int wrapMethod, boolean clearKey) {
        byte[] token = new byte[64];
        token[0] = 0x02;
        token[4] = (byte) (keyMaterial.length == 8 ? 0x00 : 0x01);

        boolean cvPresent = !IcsfHex.isAllZero(cvLeft, 0, cvLeft.length)
                || !IcsfHex.isAllZero(cvRight, 0, cvRight.length);
        int flag6 = 0;
        if (!clearKey) flag6 |= 0x80;
        if (cvPresent) flag6 |= 0x40;
        if (nocv) flag6 |= 0x20;
        token[6] = (byte) flag6;
        token[7] = (byte) ((wrapMethod & 0b111) << 5);

        System.arraycopy(keyMaterial, 0, token, 16, Math.min(8, keyMaterial.length));
        if (keyMaterial.length > 8) System.arraycopy(keyMaterial, 8, token, 24, 8);
        if (keyMaterial.length > 16) System.arraycopy(keyMaterial, 16, token, 48, 8);

        System.arraycopy(cvLeft, 0, token, 32, 8);
        if (keyMaterial.length > 8) System.arraycopy(cvRight, 0, token, 40, 8);

        if (!cvPresent) {
            // Byte 59 only means anything for DATA keys with a zero CV (Table 616).
            token[59] = (byte) switch (keyMaterial.length) {
                case 8 -> 0x00;
                case 16 -> 0x10;
                default -> 0x20;
            };
        }
        writeTvv(token);
        return token;
    }

    /** An internal AES fixed-length token (X'01' / X'04') declaring a 128-bit clear key. */
    static byte[] aesFixed() {
        byte[] token = new byte[64];
        token[0] = 0x01;
        token[4] = 0x04;
        token[56] = 0x00;
        token[57] = (byte) 128;
        writeTvv(token);
        return token;
    }

    /** An internal DES token whose MKVP and TVV are both zero: a non-KDSR CKDS record as stored. */
    static byte[] unmaterialisedInternalDes() {
        byte[] token = des("IMPORTER", 16);
        token[0] = 0x01;                    // internal
        java.util.Arrays.fill(token, 8, 16, (byte) 0);   // no MKVP
        java.util.Arrays.fill(token, 60, 64, (byte) 0);  // no TVV
        return token;
    }

    /**
     * A variable-length symmetric token (Table 618): internal AES CIPHER key.
     *
     * <p>Layout, so the offsets below are readable: header 0-29, associated data from
     * 30 with 16 fixed bytes, then kuf usage fields, kmf management fields, the key
     * name and the payload. {@code adl} must equal
     * {@code 16 + kuf*2 + kmf*2 + kl + iead + uad} or the parser rightly complains.</p>
     *
     * @param degradedHistory sets the management bit saying the key was once ECB-wrapped
     * @param compliantTagged sets the compliant-tagged management bit
     */
    static byte[] variableLength(boolean degradedHistory, boolean compliantTagged) {
        int usageFields = 2;
        int managementFields = 3;
        int associatedDataLength = 16 + usageFields * 2 + managementFields * 2;   // kl/iead/uad all zero
        int payloadBits = 256;
        int payloadStart = 30 + associatedDataLength;
        int length = payloadStart + payloadBits / 8;

        byte[] token = new byte[length];
        token[0] = 0x01;                                  // internal
        token[2] = (byte) ((length >> 8) & 0xFF);
        token[3] = (byte) (length & 0xFF);
        token[4] = 0x05;                                  // variable-length version
        token[8] = 0x03;                                  // encrypted under the master key
        token[9] = 0x01;                                  // AES master key MKVP
        for (int index = 0; index < 16; index++) token[10 + index] = (byte) (0xA0 + index);
        token[26] = 0x02;                                 // AESKW
        token[27] = 0x02;                                 // SHA-256
        token[28] = 0x01;                                 // fixed payload version

        token[30] = 0x01;                                 // associated data version
        token[32] = (byte) ((associatedDataLength >> 8) & 0xFF);
        token[33] = (byte) (associatedDataLength & 0xFF);
        token[38] = (byte) ((payloadBits >> 8) & 0xFF);
        token[39] = (byte) (payloadBits & 0xFF);
        token[41] = 0x02;                                 // AES
        token[43] = 0x01;                                 // key type CIPHER
        token[44] = (byte) usageFields;

        token[45] = (byte) 0xC0;                          // usage field 1: encrypt + decrypt
        token[47] = 0x00;                                 // usage field 2: CBC

        int managementCountOffset = 45 + usageFields * 2;
        token[managementCountOffset] = (byte) managementFields;
        int management = managementCountOffset + 1;
        // Export with a symmetric key, plus the compliant-tagged bit when asked for.
        token[management] = (byte) (0x80 | (compliantTagged ? 0x01 : 0x00));
        token[management + 2] = 0x00;                     // completeness: complete
        if (degradedHistory) token[management + 3] = 0x01; // history: was ECB-wrapped
        token[management + 4] = 0x02;                     // pedigree original: randomly generated
        token[management + 5] = 0x09;                     // pedigree current: imported from a CV token

        for (int index = 0; index < payloadBits / 8; index++) {
            token[payloadStart + index] = (byte) (0x50 + index);
        }
        return token;
    }

    /**
     * A PKA token (Tables 637-646) carrying only an RSA public key section.
     *
     * <p>Header 0-7, then concatenated sections. The X'04' section holds the exponent
     * and modulus lengths at relative offsets 6, 8 and 10, and the values after 12.</p>
     */
    static byte[] pkaPublicRsa() {
        int exponentLength = 3;
        int modulusLength = 128;
        int sectionLength = 12 + exponentLength + modulusLength;
        int length = 8 + sectionLength;

        byte[] token = new byte[length];
        token[0] = 0x1F;                                  // internal PKA
        token[1] = 0x00;
        token[2] = (byte) ((length >> 8) & 0xFF);
        token[3] = (byte) (length & 0xFF);

        int section = 8;
        token[section] = 0x04;                            // RSA public key section
        token[section + 1] = 0x00;
        token[section + 2] = (byte) ((sectionLength >> 8) & 0xFF);
        token[section + 3] = (byte) (sectionLength & 0xFF);
        token[section + 7] = (byte) exponentLength;       // relative offset 6, low byte
        token[section + 8] = 0x04;                        // modulus bits: 1024
        token[section + 9] = 0x00;
        token[section + 10] = 0x00;                       // modulus bytes: 128
        token[section + 11] = (byte) modulusLength;
        token[section + 12] = 0x01;                       // e = 65537
        token[section + 13] = 0x00;
        token[section + 14] = 0x01;
        for (int index = 0; index < modulusLength; index++) {
            token[section + 15 + index] = (byte) (0xC0 + index);
        }
        return token;
    }

    /** A DES token that is not 64 bytes, for the unexpected-length finding. */
    static byte[] truncatedDes() {
        byte[] full = des("IMPORTER", 16);
        byte[] short64 = new byte[60];
        System.arraycopy(full, 0, short64, 0, 60);
        return short64;
    }

    /** Writes the correct TVV into bytes 60-63. */
    static void writeTvv(byte[] token) {
        long tvv = IcsfTvv.compute(token);
        token[60] = (byte) ((tvv >>> 24) & 0xFF);
        token[61] = (byte) ((tvv >>> 16) & 0xFF);
        token[62] = (byte) ((tvv >>> 8) & 0xFF);
        token[63] = (byte) (tvv & 0xFF);
    }

    /** Writes bytes the way a host dump does: high digits on one row, low digits on the next. */
    static String twoRows(byte[] data) {
        return IcsfHex.toTwoRows(data);
    }

    /** Uppercase linear hexadecimal. */
    static String hex(byte[] data) {
        return IcsfHex.hex(data);
    }
}
