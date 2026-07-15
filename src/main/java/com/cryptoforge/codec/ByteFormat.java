package com.cryptoforge.codec;

public enum ByteFormat {
    HEX("Hexadecimal"),
    BASE64("Base64"),
    BASE64_URL("Base64URL"),
    BASE32("Base32"),
    BASE58("Base58"),
    BASE58_CHECK("Base58Check"),
    BINARY("Binary"),
    DECIMAL("Decimal"),
    
    // Text encodings
    TEXT_UTF8("Text (UTF-8)"),
    TEXT_ASCII("Text (ASCII)"),
    TEXT_ISO_8859_1("Text (ISO-8859-1)");

    private final String displayName;

    ByteFormat(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
    
    public static ByteFormat fromDisplayName(String name) {
        if ("Text".equals(name)) return TEXT_UTF8; // Fallback for some old UI names
        for (ByteFormat format : values()) {
            if (format.getDisplayName().equalsIgnoreCase(name)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown format: " + name);
    }
}
