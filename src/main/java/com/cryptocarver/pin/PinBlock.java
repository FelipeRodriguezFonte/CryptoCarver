package com.cryptocarver.pin;

import java.security.SecureRandom;

public final class PinBlock {
    private static final SecureRandom RNG = new SecureRandom();

    private PinBlock() {
    }

    // ---------- ISO 0 (ANSI) ----------
    public static byte[] encodePinblockIso0(String pin, String pan) {
        if (pin == null || pin.length() < 4 || pin.length() > 12 || !CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN must be between 4 and 12 digits long");
        if (pan == null || pan.length() < 13 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be at least 13 digits long");

        byte[] pinBlock = new byte[8];
        pinBlock[0] = (byte) pin.length();
        String pinHex = pin + "F".repeat(14 - pin.length());
        byte[] pinTail = CryptoTools.hexToBytes(pinHex);
        pinBlock[0] = (byte) (pin.length() & 0x0F);
        System.arraycopy(pinTail, 0, pinBlock, 1, 7);

        byte[] panBlock = new byte[8];
        System.arraycopy(new byte[] { 0x00, 0x00 }, 0, panBlock, 0, 2);
        byte[] panPart = CryptoTools.hexToBytes(pan.substring(pan.length() - 13, pan.length() - 1));
        System.arraycopy(panPart, 0, panBlock, 2, 6);

        return CryptoTools.xor(pinBlock, panBlock);
    }

    public static String decodePinblockIso0(byte[] pinblock, String pan) {
        if (pan == null || pan.length() < 13 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be at least 13 digits long");
        if (pinblock == null || pinblock.length != 8)
            throw new IllegalArgumentException("PIN block must be 8 bytes long");

        byte[] panBlock = new byte[8];
        System.arraycopy(new byte[] { 0x00, 0x00 }, 0, panBlock, 0, 2);
        byte[] panPart = CryptoTools.hexToBytes(pan.substring(pan.length() - 13, pan.length() - 1));
        System.arraycopy(panPart, 0, panBlock, 2, 6);

        String block = CryptoTools.bytesToHexUpper(CryptoTools.xor(pinblock, panBlock));

        if (block.charAt(0) != '0')
            throw new IllegalArgumentException(
                    "PIN block is not ISO format 0: control field `" + block.charAt(0) + "`");

        int pinLen = Integer.parseInt(String.valueOf(block.charAt(1)), 16);
        if (pinLen < 4 || pinLen > 12)
            throw new IllegalArgumentException("PIN length must be between 4 and 12: `" + pinLen + "`");

        String filler = block.substring(pinLen + 2);
        String expected = "F".repeat(14 - pinLen);
        if (!filler.equals(expected))
            throw new IllegalArgumentException("PIN block filler is incorrect: `" + filler + "`");

        String pin = block.substring(2, 2 + pinLen);
        if (!CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN is not numeric: `" + pin + "`");
        return pin;
    }

    // ---------- ISO 2 ----------
    public static byte[] encodePinblockIso2(String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 12 || !CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN must be between 4 and 12 digits long");
        byte first = (byte) ((0x20) | (pin.length() & 0x0F));
        byte[] tail = CryptoTools.hexToBytes(pin + "F".repeat(14 - pin.length()));
        byte[] out = new byte[8];
        out[0] = first;
        System.arraycopy(tail, 0, out, 1, 7);
        return out;
    }

    public static String decodePinblockIso2(byte[] pinblock) {
        if (pinblock == null || pinblock.length != 8)
            throw new IllegalArgumentException("PIN block must be 8 bytes long");

        String block = CryptoTools.bytesToHexUpper(pinblock);

        if (block.charAt(0) != '2')
            throw new IllegalArgumentException(
                    "PIN block is not ISO format 2: control field `" + block.charAt(0) + "`");

        int pinLen = Integer.parseInt(String.valueOf(block.charAt(1)), 16);
        if (pinLen < 4 || pinLen > 12)
            throw new IllegalArgumentException("PIN length must be between 4 and 12: `" + pinLen + "`");

        String filler = block.substring(pinLen + 2);
        String expected = "F".repeat(14 - pinLen);
        if (!filler.equals(expected))
            throw new IllegalArgumentException("PIN block filler is incorrect: `" + filler + "`");

        String pin = block.substring(2, 2 + pinLen);
        if (!CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN is not numeric: `" + pin + "`");
        return pin;
    }

    // ---------- ISO 3 ----------
    public static byte[] encodePinblockIso3(String pin, String pan) {
        if (pin == null || pin.length() < 4 || pin.length() > 12 || !CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN must be between 4 and 12 digits long");
        if (pan == null || pan.length() < 13 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be at least 13 digits long");

        String randomPad = randomChars("ABCDEF", 10);
        byte first = (byte) ((0x30) | (pin.length() & 0x0F));
        String body = pin + randomPad.substring(0, 14 - pin.length());
        byte[] tail = CryptoTools.hexToBytes(body);

        byte[] pinBlock = new byte[8];
        pinBlock[0] = first;
        System.arraycopy(tail, 0, pinBlock, 1, 7);

        byte[] panBlock = new byte[8];
        System.arraycopy(new byte[] { 0x00, 0x00 }, 0, panBlock, 0, 2);
        byte[] panPart = CryptoTools.hexToBytes(pan.substring(pan.length() - 13, pan.length() - 1));
        System.arraycopy(panPart, 0, panBlock, 2, 6);

        return CryptoTools.xor(pinBlock, panBlock);
    }

    public static String decodePinblockIso3(byte[] pinblock, String pan) {
        if (pan == null || pan.length() < 13 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be at least 13 digits long");
        if (pinblock == null || pinblock.length != 8)
            throw new IllegalArgumentException("PIN block must be 8 bytes long");

        byte[] panBlock = new byte[8];
        System.arraycopy(new byte[] { 0x00, 0x00 }, 0, panBlock, 0, 2);
        byte[] panPart = CryptoTools.hexToBytes(pan.substring(pan.length() - 13, pan.length() - 1));
        System.arraycopy(panPart, 0, panBlock, 2, 6);

        String block = CryptoTools.bytesToHexUpper(CryptoTools.xor(pinblock, panBlock));

        if (block.charAt(0) != '3')
            throw new IllegalArgumentException(
                    "PIN block is not ISO format 3: control field `" + block.charAt(0) + "`");

        int pinLen = Integer.parseInt(String.valueOf(block.charAt(1)), 16);
        if (pinLen < 4 || pinLen > 12)
            throw new IllegalArgumentException("PIN length must be between 4 and 12: `" + pinLen + "`");

        String filler = block.substring(pinLen + 2);
        for (int i = 0; i < filler.length(); i++) {
            char c = filler.charAt(i);
            if ("ABCDEF".indexOf(c) < 0) {
                throw new IllegalArgumentException("PIN block filler is incorrect: `" + filler + "`");
            }
        }

        String pin = block.substring(2, 2 + pinLen);
        if (!CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN is not numeric: `" + pin + "`");
        return pin;
    }

    // ---------- ISO 4 ----------
    public static byte[] encodePinFieldIso4(String pin) {
        if (pin == null || pin.length() < 4 || pin.length() > 12 || !CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN must be between 4 and 12 digits long");

        byte[] rnd = new byte[8];
        RNG.nextBytes(rnd);
        String randomPad = CryptoTools.bytesToHexUpper(rnd);

        String pinLenHexLowNibble = Integer.toHexString(pin.length()).toUpperCase();
        String pinFieldStr = "4" + pinLenHexLowNibble + pin + "A".repeat(14 - pin.length()) + randomPad;
        return CryptoTools.hexToBytes(pinFieldStr);
    }

    public static byte[] encodePanFieldIso4(String pan) {
        if (pan == null || pan.length() < 1 || pan.length() > 19 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be between 1 and 19 digits long.");

        int lenNibble = Math.max(0, pan.length() - 12);
        String panField = String.valueOf(lenNibble) + lpad(pan, 12, '0');
        panField = rpad(panField, 32, '0');
        return CryptoTools.hexToBytes(panField);
    }

    public static byte[] encipherPinblockIso4(byte[] key, String pin, String pan) {
        byte[] pinField = encodePinFieldIso4(pin);
        byte[] panField = encodePanFieldIso4(pan);
        byte[] a = AesEcb.encryptEcbNoPadding(key, pinField);
        byte[] b = CryptoTools.xor(a, panField);
        return AesEcb.encryptEcbNoPadding(key, b);
    }

    public static String decodePinFieldIso4(byte[] pinField) {
        if (pinField == null || pinField.length != 16)
            throw new IllegalArgumentException("PIN field must be 16 bytes long");

        String s = CryptoTools.bytesToHexUpper(pinField);
        if (s.charAt(0) != '4')
            throw new IllegalArgumentException("PIN block is not ISO format 4: control field `" + s.charAt(0) + "`");

        int pinLen = Integer.parseInt(String.valueOf(s.charAt(1)), 16);
        if (pinLen < 4 || pinLen > 12)
            throw new IllegalArgumentException("PIN length must be between 4 and 12: `" + pinLen + "`");

        String filler = s.substring(pinLen + 2, 16);
        String expected = "A".repeat(14 - pinLen);
        if (!filler.equals(expected))
            throw new IllegalArgumentException("PIN block filler is incorrect: `" + filler + "`");

        String pin = s.substring(2, 2 + pinLen);
        if (!CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN is not numeric: `" + pin + "`");

        return pin;
    }

    public static String decipherPinblockIso4(byte[] key, byte[] pinBlock, String pan) {
        byte[] b = AesEcb.decryptEcbNoPadding(key, pinBlock);
        byte[] panField = encodePanFieldIso4(pan);
        byte[] a = CryptoTools.xor(b, panField);
        byte[] pinField = AesEcb.decryptEcbNoPadding(key, a);
        return decodePinFieldIso4(pinField);
    }

    // ---------- Helpers ----------
    private static String randomChars(String alphabet, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++)
            sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static String lpad(String s, int len, char pad) {
        if (s.length() >= len)
            return s;
        StringBuilder sb = new StringBuilder(len);
        while (sb.length() < len - s.length())
            sb.append(pad);
        sb.append(s);
        return sb.toString();
    }

    private static String rpad(String s, int len, char pad) {
        if (s.length() >= len)
            return s;
        StringBuilder sb = new StringBuilder(len);
        sb.append(s);
        while (sb.length() < len)
            sb.append(pad);
        return sb.toString();
    }
}
