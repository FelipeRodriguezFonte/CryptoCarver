package com.cryptocarver.model;

/** Pure normalization and profile selection rules for the shared format toolbar. */
public final class FormatProfilePolicy {
    private FormatProfilePolicy() { }

    public static String operation(String operation) {
        return operation != null && operation.startsWith("Hashing:") ? "Hashing" : operation;
    }

    public static String normalize(String format) {
        if ("Plain Text".equalsIgnoreCase(format) || "Text".equalsIgnoreCase(format)) {
            return "Text (UTF-8)";
        }
        return format;
    }
}
