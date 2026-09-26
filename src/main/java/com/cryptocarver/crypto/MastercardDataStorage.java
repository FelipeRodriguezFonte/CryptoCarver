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
 * tool's worked example (captured 2026-09-26; MastercardDataStorageTest).</p>
 *
 * <p>The DS Summary the card computes (OWHF1) is not in Book C-2. {@link #summary}
 * reproduces the tool's two captures step by step, every intermediate included; the
 * second one varied each input so that none of them can stand in for another.</p>
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

    /**
     * DS Summary (OWHF1):
     * A = (RCP high nibble OR GAC indicator) || last 3 digits of the currency code;
     * X1 = DS Summary 1 XOR (00000000 || last 2 bytes of the amount || DS UN[3..4]);
     * X2 = A || UN || DS UN[1..2];
     * K1 = DSPKL || DS UN[1..2], K2 = DSPKR || DS UN[3..4];
     * K3 = DES(K1)[X1] XOR X1;
     * R = DES(K3)[DES-1(K2)[DES(K3)[X2]]] XOR X1 XOR X2.
     */
    public static String summary(String dsId, String dsSummary1, String amountAuthorized, String currencyCode,
                                 String referenceControlParameter, String generateAcIndicator,
                                 String dsUnpredictableNumber, String unpredictableNumber) {
        byte[] dspk = DataConverter.hexToBytes(partialKey(dsId));
        byte[] s1 = fixed(dsSummary1, "DS Summary 1", 8);
        byte[] amount = fixed(amountAuthorized, "amount authorized", 6);
        byte[] dsUn = fixed(dsUnpredictableNumber, "DS unpredictable number", 4);
        byte[] un = fixed(unpredictableNumber, "unpredictable number", 4);
        String currency = currencyCode == null ? "" : currencyCode.trim();
        if (!currency.matches("\\d{3,4}")) throw new IllegalArgumentException("The currency code is 3 or 4 digits");
        int rcp = fixed(referenceControlParameter, "reference control parameter", 1)[0] & 0xFF;
        int gac = fixed(generateAcIndicator, "generate AC indicator", 1)[0] & 0xFF;
        byte[] a = DataConverter.hexToBytes(Integer.toHexString(((rcp >> 4) | gac) & 0x0F) + currency.substring(currency.length() - 3));

        byte[] x1 = s1.clone();
        x1[4] ^= amount[4];
        x1[5] ^= amount[5];
        x1[6] ^= dsUn[2];
        x1[7] ^= dsUn[3];
        byte[] x2 = new byte[8];
        System.arraycopy(a, 0, x2, 0, 2);
        System.arraycopy(un, 0, x2, 2, 4);
        System.arraycopy(dsUn, 0, x2, 6, 2);
        byte[] k1 = new byte[8];
        byte[] k2 = new byte[8];
        System.arraycopy(dspk, 0, k1, 0, 6);
        System.arraycopy(dsUn, 0, k1, 6, 2);
        System.arraycopy(dspk, 6, k2, 0, 6);
        System.arraycopy(dsUn, 2, k2, 6, 2);
        byte[] k3 = xor(des(k1, x1, true), x1);
        byte[] r = des(k3, des(k2, des(k3, x2, true), false), true);
        return hex(xor(xor(r, x1), x2));
    }

    private static byte[] fixed(String hex, String label, int length) {
        byte[] value = bytes(hex, label);
        if (value.length != length) throw new IllegalArgumentException("The " + label + " is " + length + " bytes");
        return value;
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
