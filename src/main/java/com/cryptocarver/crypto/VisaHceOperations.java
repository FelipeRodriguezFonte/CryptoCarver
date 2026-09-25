package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Locale;

/**
 * Visa cloud-based payments (HCE): the limited-use key and the two values a phone
 * produces with it, the MSD verification value and the qVSDC cryptogram.
 *
 * <p>Recovered from an external tool's worked examples (captured 2026-09-25) and a
 * second example the tool's vendor publishes; every step reproduces both
 * (VisaHceOperationsTest):</p>
 * <ul>
 *   <li><b>LUK</b> from the card key (UDK) and the account parameter index
 *       Y HHHH CC (year digit, hours since 1 January, count within the hour): the
 *       left half is TDES(UDK) over '1' || YHHHHCC and the right half over
 *       '2' || YHHHHCC, each padded with 80 and zeros to 8 bytes; no parity.</li>
 *   <li><b>MSD verification value</b>: the ATC overlaid on the leftmost 4 digits of
 *       the 8-byte device type, TDES under the LUK, decimalised as a CVV (digits
 *       first, then A-F minus 10), 3 digits.</li>
 *   <li><b>qVSDC cryptogram</b>: ISO 9797-1 algorithm 3 with padding method 2 under
 *       the LUK over the terminal data and then the ICC data (AIP, ATC, CVR).</li>
 * </ul>
 * <p>The card key itself is the usual EMV option A derivation from the MDK.</p>
 */
public final class VisaHceOperations {

    private VisaHceOperations() {
    }

    /** Limited-use key for an account parameter index. */
    public static String limitedUseKey(String udk, String year, String hours, String hourlyCounter) {
        byte[] key = bytes(udk, "UDK", 16);
        String index = digits(year, "year", 1, 2) + digits(hours, "hours", 4, 4) + digits(hourlyCounter, "hourly counter", 2, 2);
        byte[] left = tdes(key, pad80("1" + index));
        byte[] right = tdes(key, pad80("2" + index));
        byte[] out = Arrays.copyOf(left, 16);
        System.arraycopy(right, 0, out, 8, 8);
        return hex(out);
    }

    /** Three-digit MSD verification value. */
    public static String msdVerificationValue(String luk, String atc, String deviceType) {
        String counter = normalize(atc, "ATC");
        String device = normalize(deviceType, "device type");
        if (counter.length() != 4) throw new IllegalArgumentException("The ATC is 2 bytes");
        if (device.length() != 16) throw new IllegalArgumentException("The device type is 8 bytes");
        String block = counter + device.substring(4);
        String result = hex(tdes(bytes(luk, "LUK", 16), DataConverter.hexToBytes(block)));
        StringBuilder digits = new StringBuilder();
        for (char c : result.toCharArray()) if (Character.isDigit(c)) digits.append(c);
        for (char c : result.toCharArray()) if (!Character.isDigit(c)) digits.append((char) ('0' + (c - 'A')));
        return digits.substring(0, 3);
    }

    /** qVSDC application cryptogram over terminal data and ICC data. */
    public static String qvsdcCryptogram(String luk, String terminalData, String iccData) throws Exception {
        bytes(luk, "LUK", 16);
        return EMVOperations.generateARQC(normalize(luk, "LUK"),
                normalize(terminalData, "terminal data") + normalize(iccData, "ICC data"), 2);
    }

    // ---- helpers ----

    private static byte[] pad80(String digits) {
        String padded = digits + (digits.length() % 2 == 0 ? "80" : "8");
        if (padded.length() > 16) throw new IllegalArgumentException("The account parameter index is too long");
        StringBuilder block = new StringBuilder(padded);
        while (block.length() < 16) block.append('0');
        return DataConverter.hexToBytes(block.toString());
    }

    private static String digits(String value, String label, int min, int max) {
        String v = value == null ? "" : value.trim();
        if (!v.matches("\\d{" + min + "," + max + "}")) {
            throw new IllegalArgumentException("The " + label + " is " + (min == max ? min : min + " to " + max) + " digits");
        }
        return v;
    }

    private static byte[] tdes(byte[] key, byte[] block) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            byte[] full = Arrays.copyOf(key, 24);
            System.arraycopy(key, 0, full, 16, 8);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(full, "DESede"));
            return cipher.doFinal(block);
        } catch (Exception e) {
            throw new IllegalStateException("Triple DES is unavailable", e);
        }
    }

    private static byte[] bytes(String hex, String label, int length) {
        byte[] value = DataConverter.hexToBytes(normalize(hex, label));
        if (value.length != length) throw new IllegalArgumentException(label + " must be " + length + " bytes");
        return value;
    }

    private static String normalize(String hex, String label) {
        String value = hex == null ? "" : hex.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!value.matches("([0-9A-F]{2})*")) throw new IllegalArgumentException(label + " must be even-length hexadecimal");
        return value;
    }

    private static String hex(byte[] value) {
        return DataConverter.bytesToHex(value).toUpperCase(Locale.ROOT);
    }
}
