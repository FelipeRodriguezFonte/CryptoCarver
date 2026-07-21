package com.cryptocarver.pin;

public final class Pin {
    private Pin() {
    }

    public static String generateIbm3624Pin(
            byte[] pvk,
            String conversionTable,
            String offset,
            String pan,
            int panVerifyOffset,
            int panVerifyLength,
            String panPad) {
        if (pvk == null || !(pvk.length == 8 || pvk.length == 16 || pvk.length == 24))
            throw new IllegalArgumentException("PVK must be a DES key (8/16/24 bytes)");

        if (conversionTable == null || conversionTable.length() != 16 || !CryptoTools.asciiNumeric(conversionTable))
            throw new IllegalArgumentException("Conversion table must 16 digits");

        if (offset == null || offset.length() < 4 || offset.length() > 16 || !CryptoTools.asciiNumeric(offset))
            throw new IllegalArgumentException("Offset must be from 4 to 16 digits");

        if (pan == null || pan.length() > 19 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be less than 19 digits");

        if (panPad == null || panPad.length() != 1 || !CryptoTools.asciiHexChar(panPad))
            throw new IllegalArgumentException("PAN pad character must be valid hex digit");

        String validationData = safeSubstr(pan, panVerifyOffset, panVerifyOffset + panVerifyLength);
        if (validationData.length() != panVerifyLength)
            throw new IllegalArgumentException("PAN verify offset and length must be within provided PAN");

        String vd = validationData.length() > 16 ? validationData.substring(0, 16) : validationData;
        vd = rpad(vd, 16, panPad.charAt(0)).toUpperCase();

        byte[] interm = TDes.encryptEcbNoPadding(pvk, CryptoTools.hexToBytes(vd));
        String intermHex = CryptoTools.bytesToHexUpper(interm);

        String translated = translateHexByTable(intermHex, conversionTable);

        StringBuilder out = new StringBuilder(offset.length());
        for (int i = 0; i < offset.length(); i++) {
            int sum = (translated.charAt(i) - '0') + (offset.charAt(i) - '0');
            out.append((char) ('0' + (sum % 10)));
        }
        return out.toString();
    }

    public static String generateIbm3624Offset(
            byte[] pvk,
            String conversionTable,
            String pin,
            String pan,
            int panVerifyOffset,
            int panVerifyLength,
            String panPad) {
        if (pvk == null || !(pvk.length == 8 || pvk.length == 16 || pvk.length == 24))
            throw new IllegalArgumentException("PVK must be a DES key (8/16/24 bytes)");

        if (conversionTable == null || conversionTable.length() != 16 || !CryptoTools.asciiNumeric(conversionTable))
            throw new IllegalArgumentException("Conversion table must 16 digits");

        if (pin == null || pin.length() < 4 || pin.length() > 16 || !CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN must be from 4 to 16 digits");

        if (pan == null || pan.length() > 19 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be less than 19 digits");

        if (panPad == null || panPad.length() != 1 || !CryptoTools.asciiHexChar(panPad))
            throw new IllegalArgumentException("PAN pad character must be valid hex digit");

        String validationData = safeSubstr(pan, panVerifyOffset, panVerifyOffset + panVerifyLength);
        if (validationData.length() != panVerifyLength)
            throw new IllegalArgumentException("PAN verify offset and length must be within provided PAN");

        String vd = validationData.length() > 16 ? validationData.substring(0, 16) : validationData;
        vd = rpad(vd, 16, panPad.charAt(0)).toUpperCase();

        byte[] interm = TDes.encryptEcbNoPadding(pvk, CryptoTools.hexToBytes(vd));
        String intermHex = CryptoTools.bytesToHexUpper(interm);
        String translated = translateHexByTable(intermHex, conversionTable);

        StringBuilder out = new StringBuilder(pin.length());
        for (int i = 0; i < pin.length(); i++) {
            int val = 10 + (pin.charAt(i) - '0') - (translated.charAt(i) - '0');
            out.append((char) ('0' + (val % 10)));
        }
        return out.toString();
    }

    public static String generateVisaPvv(byte[] pvk, String pvki, String pin, String pan) {
        if (pvk == null || !(pvk.length == 8 || pvk.length == 16 || pvk.length == 24))
            throw new IllegalArgumentException("PVK must be a DES key (8/16/24 bytes)");

        if (pvki == null || pvki.length() != 1 || !CryptoTools.asciiNumeric(pvki))
            throw new IllegalArgumentException("PVKI must be 1 digit from \"0\" to \"9\"");

        if (pin == null || pin.length() != 4 || !CryptoTools.asciiNumeric(pin))
            throw new IllegalArgumentException("PIN must be 4 digits");

        if (pan == null || pan.length() < 12 || !CryptoTools.asciiNumeric(pan))
            throw new IllegalArgumentException("PAN must be more than 12 digits");

        String tspInput = pan.substring(pan.length() - 12, pan.length() - 1) + pvki + pin;
        byte[] tspEnc = TDes.encryptEcbNoPadding(pvk, CryptoTools.hexToBytes(tspInput));
        String tsp = toHexLower(tspEnc);

        StringBuilder pvv = new StringBuilder(4);
        for (int i = 0; i < tsp.length() && pvv.length() < 4; i++) {
            char c = tsp.charAt(i);
            if (c >= '0' && c <= '9')
                pvv.append(c);
        }

        if (pvv.length() < 4) {
            for (int i = 0; i < tsp.length() && pvv.length() < 4; i++) {
                char c = tsp.charAt(i);
                if (c >= 'a' && c <= 'f') {
                    pvv.append((char) ('0' + (c - 'a')));
                }
            }
        }

        return pvv.toString();
    }

    // Helpers
    private static String safeSubstr(String s, int start, int end) {
        if (start < 0)
            start = 0;
        if (end < 0)
            end = 0;
        if (start > s.length())
            return "";
        if (end > s.length())
            end = s.length();
        if (end < start)
            return "";
        return s.substring(start, end);
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

    private static String translateHexByTable(String hexUpper, String conversionTableDigits) {
        String src = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder(hexUpper.length());
        for (int i = 0; i < hexUpper.length(); i++) {
            char c = hexUpper.charAt(i);
            int idx = src.indexOf(c);
            if (idx < 0)
                throw new IllegalArgumentException("Non-hex char in intermediate: " + c);
            sb.append(conversionTableDigits.charAt(idx));
        }
        return sb.toString();
    }

    private static String toHexLower(byte[] data) {
        String upper = CryptoTools.bytesToHexUpper(data);
        return upper.toLowerCase(java.util.Locale.ROOT);
    }
}
