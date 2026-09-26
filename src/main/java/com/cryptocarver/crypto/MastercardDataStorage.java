package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Locale;

/**
 * Mastercard Integrated Data Storage (EMV Contactless Book C-2, Kernel 2): the data
 * storage partial key and the DES one-way function OWHF2 that yields the DS Digest.
 *
 * <p>Both follow Book C-2 v2.6 section 8.2 to the letter and reproduce the external
 * tool's worked example (captured 2026-09-26; MastercardDataStorageTest). The DS
 * Summary function (OWHF1) the card computes is not in Book C-2 and is not here.</p>
 */
public final class MastercardDataStorage {

    private MastercardDataStorage() {
    }

    /** DSPK = DSPKL || DSPKR, each byte of the DS ID read as two decimal digits, times 2. */
    public static String partialKey(String dsId) {
        byte[] id = bytes(dsId, "DS ID");
        if (id.length < 6) throw new IllegalArgumentException("The DS ID is at least 6 bytes");
        byte[] out = new byte[12];
        for (int i = 0; i < 6; i++) {
            out[i] = digitPair(id[i]);
            out[6 + i] = digitPair(id[id.length - 6 + i]);
        }
        return hex(out);
    }

    /**
     * OWHF2: KL = DSPKL || OID[5..6], KR = DSPKR || OID[7..8];
     * R = DES(KL)[DES-1(KR)[DES(KL)[OID XOR PD]]] XOR PD.
     *
     * @param operatorId the 8-byte OID; all zeros when the permanent/volatile slot rule applies
     */
    public static String owhf2(String dsId, String operatorId, String input) {
        byte[] dspk = DataConverter.hexToBytes(partialKey(dsId));
        byte[] oid = bytes(operatorId, "operator ID");
        byte[] pd = bytes(input, "DS input");
        if (oid.length != 8 || pd.length != 8) throw new IllegalArgumentException("OID and DS input are 8 bytes");
        byte[] kl = new byte[8];
        byte[] kr = new byte[8];
        System.arraycopy(dspk, 0, kl, 0, 6);
        System.arraycopy(oid, 4, kl, 6, 2);
        System.arraycopy(dspk, 6, kr, 0, 6);
        System.arraycopy(oid, 6, kr, 6, 2);
        byte[] x = xor(oid, pd);
        byte[] r = des(kl, des(kr, des(kl, x, true), false), true);
        return hex(xor(r, pd));
    }

    private static byte digitPair(byte b) {
        int value = ((b & 0xF0) >> 4) * 10 + (b & 0x0F);
        return (byte) (value * 2);
    }

    private static byte[] des(byte[] key, byte[] block, boolean encrypt) {
        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "DES"));
            return cipher.doFinal(block);
        } catch (Exception e) {
            throw new IllegalStateException("DES is unavailable", e);
        }
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = a.clone();
        for (int i = 0; i < out.length; i++) out[i] ^= b[i];
        return out;
    }

    private static byte[] bytes(String hex, String label) {
        String value = hex == null ? "" : hex.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!value.matches("([0-9A-F]{2})+")) throw new IllegalArgumentException(label + " must be even-length hexadecimal");
        return DataConverter.hexToBytes(value);
    }

    private static String hex(byte[] value) {
        return DataConverter.bytesToHex(value).toUpperCase(Locale.ROOT);
    }
}
