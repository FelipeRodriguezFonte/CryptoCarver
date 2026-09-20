package com.cryptocarver.crypto.smartcard;

import java.util.Locale;

/**
 * Conservative ISO/IEC 7816-4 SW1/SW2 interpreter.
 *
 * <p>Source: ISO/IEC 7816-4, clause 7.1 (status bytes); the same status-word
 * table is reproduced by ETSI TS 102 221, section 11.3.3. Only well-known
 * exact values and the explicitly defined 61xx/6Cxx and family ranges are
 * interpreted here.</p>
 */
public record ApduStatus(int sw1, int sw2, Category category, String meaning) {
    public enum Category {
        SUCCESS, RESPONSE_AVAILABLE, WRONG_LENGTH, WARNING, SECURITY,
        WRONG_PARAMETERS, NOT_SUPPORTED, EXECUTION_ERROR, UNKNOWN
    }

    public ApduStatus {
        if (sw1 < 0 || sw1 > 0xFF || sw2 < 0 || sw2 > 0xFF)
            throw new IllegalArgumentException("SW1/SW2 must be bytes");
        meaning = meaning == null ? "Unknown status" : meaning;
    }

    public static ApduStatus of(int sw1, int sw2) {
        sw1 &= 0xFF;
        sw2 &= 0xFF;
        String code = String.format(Locale.ROOT, "%02X%02X", sw1, sw2);
        if (sw1 == 0x90 && sw2 == 0x00) return new ApduStatus(sw1, sw2, Category.SUCCESS, "Command successfully executed");
        if (sw1 == 0x61) return new ApduStatus(sw1, sw2, Category.RESPONSE_AVAILABLE, "Response bytes available; SW2 is the byte count (00 means 256)");
        if (sw1 == 0x6C) return new ApduStatus(sw1, sw2, Category.WRONG_LENGTH, "Wrong Le; SW2 is the exact expected length (00 means 256)");
        String exact = exactMeaning(code);
        if (exact != null) return new ApduStatus(sw1, sw2, categoryFor(sw1), exact);
        if (sw1 == 0x62) return new ApduStatus(sw1, sw2, Category.WARNING, "Warning: state of non-volatile memory unchanged");
        if (sw1 == 0x63) return new ApduStatus(sw1, sw2, Category.WARNING, "Warning: state of non-volatile memory changed");
        if (sw1 == 0x67) return new ApduStatus(sw1, sw2, Category.WRONG_LENGTH, "Wrong length");
        if (sw1 == 0x68) return new ApduStatus(sw1, sw2, Category.NOT_SUPPORTED, "Functions in CLA not supported");
        if (sw1 == 0x69) return new ApduStatus(sw1, sw2, Category.WRONG_PARAMETERS, "Command not allowed");
        if (sw1 == 0x6A) return new ApduStatus(sw1, sw2, Category.WRONG_PARAMETERS, "Wrong command data or parameters");
        if (sw1 == 0x6B) return new ApduStatus(sw1, sw2, Category.WRONG_PARAMETERS, "Wrong P1 or P2");
        if (sw1 == 0x6D) return new ApduStatus(sw1, sw2, Category.NOT_SUPPORTED, "Instruction code not supported");
        if (sw1 == 0x6E) return new ApduStatus(sw1, sw2, Category.NOT_SUPPORTED, "Class not supported");
        if (sw1 == 0x6F) return new ApduStatus(sw1, sw2, Category.EXECUTION_ERROR, "No precise diagnosis");
        return new ApduStatus(sw1, sw2, Category.UNKNOWN, "Unknown status word");
    }

    public static ApduStatus parse(String hex) {
        if (hex == null || !hex.replaceAll("[\\s:-]", "").matches("(?i)[0-9a-f]{4}"))
            throw new IllegalArgumentException("Status word must be exactly four hexadecimal digits");
        String s = hex.replaceAll("[\\s:-]", "");
        return of(Integer.parseInt(s.substring(0, 2), 16), Integer.parseInt(s.substring(2), 16));
    }

    public String hex() { return String.format(Locale.ROOT, "%02X%02X", sw1, sw2); }

    private static Category categoryFor(int sw1) {
        if (sw1 == 0x64 || sw1 == 0x65) return Category.EXECUTION_ERROR;
        if (sw1 == 0x66) return Category.SECURITY;
        return Category.UNKNOWN;
    }

    private static String exactMeaning(String code) {
        return switch (code) {
            case "6282" -> "Warning: end of file reached before Le bytes";
            case "6283" -> "Selected file invalidated";
            case "6300" -> "Authentication failed";
            case "6400" -> "Execution error; state of non-volatile memory unchanged";
            case "6581" -> "Memory failure";
            case "6700" -> "Wrong length";
            case "6982" -> "Security status not satisfied";
            case "6983" -> "Authentication method blocked";
            case "6985" -> "Conditions of use not satisfied";
            case "6A80" -> "Incorrect parameters in data field";
            case "6A81" -> "Function not supported";
            case "6A82" -> "File or application not found";
            case "6A83" -> "Record not found";
            case "6A84" -> "Not enough memory space";
            case "6A86" -> "Incorrect P1 or P2";
            case "6A88" -> "Referenced data not found";
            case "6B00" -> "Wrong parameters P1-P2";
            case "6D00" -> "Instruction code not supported";
            case "6E00" -> "Class not supported";
            default -> null;
        };
    }
}
