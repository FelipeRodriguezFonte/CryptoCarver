package com.cryptoforge.crypto;

import com.cryptoforge.util.DataConverter;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Utilities for interpreting decrypted byte streams that use EBCDIC code pages. */
public final class EBCDICConverter {

    private static final Map<String, String> CODE_PAGES = new LinkedHashMap<>();

    static {
        CODE_PAGES.put("IBM037 — US/Canada", "Cp037");
        CODE_PAGES.put("IBM273 — Germany", "Cp273");
        CODE_PAGES.put("IBM277 — Denmark/Norway", "Cp277");
        CODE_PAGES.put("IBM278 — Finland/Sweden", "Cp278");
        CODE_PAGES.put("IBM280 — Italy", "Cp280");
        CODE_PAGES.put("IBM284 — Spain", "Cp284");
        CODE_PAGES.put("IBM285 — United Kingdom", "Cp285");
        CODE_PAGES.put("IBM297 — France", "Cp297");
        CODE_PAGES.put("IBM420 — Arabic", "Cp420");
        CODE_PAGES.put("IBM424 — Hebrew", "Cp424");
        CODE_PAGES.put("IBM500 — International", "Cp500");
        CODE_PAGES.put("IBM838 — Thai", "Cp838");
        CODE_PAGES.put("IBM870 — Latin-2 (Central Europe)", "Cp870");
        CODE_PAGES.put("IBM871 — Iceland", "Cp871");
        CODE_PAGES.put("IBM875 — Greek", "Cp875");
        CODE_PAGES.put("IBM918 — Pakistan/Urdu", "Cp918");
        CODE_PAGES.put("IBM1025 — Cyrillic", "Cp1025");
        CODE_PAGES.put("IBM1026 — Turkish", "Cp1026");
        CODE_PAGES.put("IBM1047 — Latin-1 Open Systems", "Cp1047");
        CODE_PAGES.put("IBM1140 — US/Canada Euro", "Cp1140");
        CODE_PAGES.put("IBM1141 — Germany Euro", "Cp1141");
        CODE_PAGES.put("IBM1142 — Denmark/Norway Euro", "Cp1142");
        CODE_PAGES.put("IBM1143 — Finland/Sweden Euro", "Cp1143");
        CODE_PAGES.put("IBM1144 — Italy Euro", "Cp1144");
        CODE_PAGES.put("IBM1145 — Spain Euro", "Cp1145");
        CODE_PAGES.put("IBM1146 — United Kingdom Euro", "Cp1146");
        CODE_PAGES.put("IBM1147 — France Euro", "Cp1147");
        CODE_PAGES.put("IBM1148 — International Euro", "Cp1148");
        CODE_PAGES.put("IBM1149 — Iceland Euro", "Cp1149");
    }

    private EBCDICConverter() { }

    public static Map<String, String> supportedCodePages() {
        return Collections.unmodifiableMap(CODE_PAGES);
    }

    public static String decode(byte[] bytes, String codePageLabel) {
        return new String(bytes, charsetFor(codePageLabel));
    }

    public static byte[] encode(String text, String codePageLabel) {
        return text.getBytes(charsetFor(codePageLabel));
    }

    public static byte[] parseBytes(String input, String format) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Input cannot be empty");
        if ("Base64".equals(format)) return DataConverter.decodeBase64Flexible(input);
        if (!"Hexadecimal".equals(format)) throw new IllegalArgumentException("Unsupported byte format: " + format);
        if (!DataConverter.isValidHex(input)) throw new IllegalArgumentException("Invalid hexadecimal input");
        return DataConverter.hexToBytes(input);
    }

    private static Charset charsetFor(String codePageLabel) {
        String charset = CODE_PAGES.get(codePageLabel);
        if (charset == null) throw new IllegalArgumentException("Unsupported EBCDIC code page: " + codePageLabel);
        return Charset.forName(charset);
    }
}
