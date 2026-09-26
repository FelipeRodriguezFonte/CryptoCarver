package com.cryptocarver.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.Locale;

/** Captured Mastercard ICC Dynamic Number and CAP token calculations. */
public final class MastercardIccDynamicNumber {
    private MastercardIccDynamicNumber() { }

    public record CapResult(String tokenData, String paddedIpb, String compressedBits, String token) { }

    /** Option A derivation from the captured 16-digit PAN/PAN sequence number field. */
    public static String sessionKey(String mkDn, String panField16) {
        byte[] key = bytes(mkDn, "MK-DN");
        if (key.length != 16) throw new IllegalArgumentException("MK-DN must be 16 bytes (double-length TDES)");
        String digits = panField16 == null ? "" : panField16.trim();
        if (!digits.matches("\\d{16}")) throw new IllegalArgumentException("PAN field must contain exactly 16 decimal digits");
        byte[] y = new byte[8];
        for (int i = 0; i < y.length; i++) y[i] = (byte) Integer.parseInt(digits.substring(i * 2, i * 2 + 2), 16);
        byte[] inverse = y.clone();
        for (int i = 0; i < inverse.length; i++) inverse[i] ^= (byte) 0xff;
        return hex(concat(tdes(key, y), tdes(key, inverse)));
    }

    /** First two bytes of TDES(SK, ATC || 0000 || UN). */
    public static String dynamicNumber(String sessionKey, String atc, String un) {
        byte[] key = bytes(sessionKey, "session key");
        byte[] counter = bytes(atc, "ATC");
        byte[] unpredictable = bytes(un, "UN");
        if (key.length != 16) throw new IllegalArgumentException("Session key must be 16 bytes");
        if (counter.length != 2) throw new IllegalArgumentException("ATC must be 2 bytes");
        if (unpredictable.length != 4) throw new IllegalArgumentException("UN must be 4 bytes");
        byte[] input = new byte[8];
        System.arraycopy(counter, 0, input, 0, 2);
        System.arraycopy(unpredictable, 0, input, 4, 4);
        return hex(tdes(key, input)).substring(0, 4);
    }

    /** Compresses token bits selected by IPB. The PAN sequence number is included only when IAF bit 40 (0x40) is set. */
    public static CapResult capToken(String ipb, String iaf, String panSn, String cid, String atc, String ac, String iad) {
        byte[] ipbBytes = bytes(ipb, "IPB");
        byte[] iafBytes = bytes(iaf, "IAF");
        if (iafBytes.length != 1) throw new IllegalArgumentException("IAF must be 1 byte");
        boolean includePsn = (iafBytes[0] & 0x40) != 0;
        byte[] psn = bytes(panSn, "PAN sequence number");
        byte[] cardId = bytes(cid, "CID");
        byte[] counter = bytes(atc, "ATC");
        byte[] cryptogram = bytes(ac, "AC");
        byte[] issuerData = bytes(iad, "IAD");
        if (psn.length != 1) throw new IllegalArgumentException("PAN sequence number must be 1 byte");
        if (cardId.length != 1) throw new IllegalArgumentException("CID must be 1 byte");
        if (counter.length != 2) throw new IllegalArgumentException("ATC must be 2 bytes");
        if (cryptogram.length != 8) throw new IllegalArgumentException("AC must be 8 bytes");
        if (issuerData.length == 0) throw new IllegalArgumentException("IAD must not be empty");
        byte[] tokenBytes = includePsn ? concat(psn, cardId, counter, cryptogram, issuerData)
                : concat(cardId, counter, cryptogram, issuerData);
        if (ipbBytes.length > tokenBytes.length) throw new IllegalArgumentException("IPB cannot be longer than token data");
        byte[] padded = new byte[tokenBytes.length];
        System.arraycopy(ipbBytes, 0, padded, 0, ipbBytes.length);
        StringBuilder selected = new StringBuilder();
        for (int i = 0; i < tokenBytes.length; i++) {
            int mask = padded[i] & 0xff;
            for (int bit = 7; bit >= 0; bit--) if ((mask & (1 << bit)) != 0) selected.append((tokenBytes[i] & (1 << bit)) == 0 ? '0' : '1');
        }
        BigInteger decimal = selected.length() == 0 ? BigInteger.ZERO : new BigInteger(selected.toString(), 2);
        return new CapResult(hex(tokenBytes), hex(padded), selected.toString(), decimal.toString());
    }

    private static byte[] tdes(byte[] key, byte[] block) {
        try {
            byte[] expanded = key.length == 16 ? concat(key, java.util.Arrays.copyOf(key, 8)) : key;
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(expanded, "DESede"));
            return cipher.doFinal(block);
        } catch (Exception e) { throw new IllegalStateException("TDES is unavailable", e); }
    }
    private static byte[] bytes(String value, String label) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("([0-9A-F]{2})+")) throw new IllegalArgumentException(label + " must be even-length hexadecimal");
        return java.util.HexFormat.of().parseHex(normalized);
    }
    private static byte[] concat(byte[]... parts) {
        int length = 0; for (byte[] p : parts) length += p.length;
        byte[] out = new byte[length]; int offset = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, offset, p.length); offset += p.length; }
        return out;
    }
    private static String hex(byte[] value) { return java.util.HexFormat.of().withUpperCase().formatHex(value); }
}
