package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;

/**
 * EMV issuer-script secure messaging for Mastercard and Visa: session keys, the
 * enciphered PIN of a PIN CHANGE/UNBLOCK command, and the command MAC.
 *
 * <p>Both profiles were recovered from the worked examples an external tool ships
 * (captured 2026-09-25) and every step reproduces them; see EmvSecureMessagingTest.
 * They differ in the session key and the PIN data, and share the MAC:</p>
 * <ul>
 *   <li><b>Mastercard</b>: UDK by EMV option A from the MK and the 16 digits of
 *       PAN||PSN (odd parity); session key by SKD over R = AC + command number,
 *       its third byte set to F0 for the left half and 0F for the right, no parity
 *       adjustment; PIN as an ISO format 2 block under the session key, TDES ECB.</li>
 *   <li><b>Visa</b>: session key = UDK with the ATC XORed into the last two bytes of
 *       the left half and its complement into those of the right; PIN as an ISO
 *       format 0 style block XORed with 00000000 || the last 4 bytes of UDK-A (the
 *       left half of the card's encryption UDK), sent as 08 || block || 80 00..,
 *       TDES ECB.</li>
 *   <li><b>MAC</b>: ISO 9797-1 algorithm 3 (single-DES CBC, then D/E with the right
 *       and left halves), padding method 2, over CLA INS P1 P2 Lc, then ATC and AC,
 *       then the command data.</li>
 * </ul>
 */
public final class EmvSecureMessaging {

    private EmvSecureMessaging() {
    }

    // ---- Mastercard ----

    /** Card key by EMV option A from a 16-digit PAN||PSN value, odd parity. */
    public static String mastercardUdk(String mk, String panSequence16) {
        byte[] key = bytes(mk, "MK", 16);
        byte[] y = bytes(panSequence16, "PAN/sequence number", 8);
        byte[] udk = concat(tdes(key, y, true), tdes(key, xorConstant(y, (byte) 0xFF), true));
        return hex(oddParity(udk));
    }

    /** SKD session key from the card key, the application cryptogram and the command number. */
    public static String mastercardSessionKey(String udk, String applicationCryptogram, int commandNumber) {
        if (commandNumber < 0) throw new IllegalArgumentException("The command number is not negative");
        byte[] key = bytes(udk, "UDK", 16);
        BigInteger r = new BigInteger(1, bytes(applicationCryptogram, "AC", 8)).add(BigInteger.valueOf(commandNumber));
        byte[] base = fixed(r.toByteArray(), 8);
        byte[] left = base.clone();
        left[2] = (byte) 0xF0;
        byte[] right = base.clone();
        right[2] = (byte) 0x0F;
        return hex(concat(tdes(key, left, true), tdes(key, right, true)));
    }

    /** New PIN as an ISO format 2 block under the encryption session key. */
    public static String mastercardEncryptedPin(String sessionKeyEnc, String pin) {
        return hex(tdes(bytes(sessionKeyEnc, "session key", 16), pinBlock('2', pin), true));
    }

    // ---- Visa ----

    /** Session key: ATC into the left half, its complement into the right. */
    public static String visaSessionKey(String udk, String atc) {
        byte[] key = bytes(udk, "UDK", 16);
        byte[] counter = bytes(atc, "ATC", 2);
        byte[] out = key.clone();
        out[6] ^= counter[0];
        out[7] ^= counter[1];
        out[14] ^= (byte) ~counter[0];
        out[15] ^= (byte) ~counter[1];
        return hex(out);
    }

    /** New PIN bound to the card's encryption UDK and enciphered under the session key. */
    public static String visaEncryptedPin(String sessionKeyEnc, String udkEnc, String pin) {
        byte[] udk = bytes(udkEnc, "UDK ENC", 16);
        byte[] block = pinBlock('0', pin);
        for (int i = 0; i < 4; i++) block[4 + i] ^= udk[4 + i];
        byte[] data = new byte[16];
        data[0] = 0x08;
        System.arraycopy(block, 0, data, 1, 8);
        data[9] = (byte) 0x80;
        return hex(tdes(bytes(sessionKeyEnc, "session key", 16), data, true));
    }

    // ---- MAC ----

    /**
     * Command MAC: ISO 9797-1 algorithm 3 with padding method 2 over the header,
     * ATC, AC and command data. Returns all 8 bytes; the command usually carries 4.
     */
    public static String commandMac(String sessionKeyMac, String header, String atc, String applicationCryptogram,
                                    String commandData) throws Exception {
        String data = normalize(header, "header") + normalize(atc, "ATC") + normalize(applicationCryptogram, "AC")
                + normalize(commandData, "command data");
        return EMVOperations.generateScriptMAC(normalize(sessionKeyMac, "session key"), data);
    }

    // ---- helpers ----

    private static byte[] pinBlock(char control, String pin) {
        if (pin == null || !pin.matches("\\d{4,12}")) throw new IllegalArgumentException("The PIN is 4 to 12 digits");
        StringBuilder field = new StringBuilder().append(control).append(Integer.toHexString(pin.length()).toUpperCase(Locale.ROOT))
                .append(pin);
        while (field.length() < 16) field.append('F');
        return DataConverter.hexToBytes(field.toString());
    }

    private static byte[] tdes(byte[] key, byte[] data, boolean encrypt) {
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(concat(key, Arrays.copyOf(key, 8)), "DESede"));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Triple DES is unavailable", e);
        }
    }

    private static byte[] oddParity(byte[] key) {
        byte[] out = key.clone();
        for (int i = 0; i < out.length; i++) {
            int b = out[i] & 0xFE;
            out[i] = (byte) (Integer.bitCount(b) % 2 == 0 ? b | 1 : b);
        }
        return out;
    }

    private static byte[] xorConstant(byte[] data, byte constant) {
        byte[] out = data.clone();
        for (int i = 0; i < out.length; i++) out[i] ^= constant;
        return out;
    }

    private static byte[] fixed(byte[] value, int length) {
        byte[] out = new byte[length];
        int copy = Math.min(length, value.length);
        System.arraycopy(value, value.length - copy, out, length - copy, copy);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
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
