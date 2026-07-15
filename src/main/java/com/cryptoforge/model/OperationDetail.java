package com.cryptoforge.model;

/**
 * Represents a structured detail of an operation (e.g. parameter, setting, result metric).
 */
public record OperationDetail(
        String name,
        String value,
        Classification classification,
        boolean multiline,
        String format
) {
    public enum Classification {
        PUBLIC,
        SENSITIVE,
        SECRET
    }

    public OperationDetail {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("OperationDetail name is required");
        }
        if (classification == null) {
            classification = Classification.PUBLIC;
        }
    }

    public static OperationDetail publicDetail(String name, String value) {
        return new OperationDetail(name, value, Classification.PUBLIC, false, null);
    }

    public static OperationDetail publicDetail(String name, String value, boolean multiline, String format) {
        return new OperationDetail(name, value, Classification.PUBLIC, multiline, format);
    }

    public static OperationDetail sensitiveDetail(String name, String value) {
        return new OperationDetail(name, value, Classification.SENSITIVE, false, null);
    }

    public static OperationDetail secretDetail(String name, String value) {
        return new OperationDetail(name, value, Classification.SECRET, false, null);
    }

    public static OperationDetail secretDetail(String name, String value, boolean multiline, String format) {
        return new OperationDetail(name, value, Classification.SECRET, multiline, format);
    }
}
