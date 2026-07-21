package com.cryptocarver.model;

import java.time.LocalDateTime;
import java.util.UUID;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class ClipboardEntry {
    private final UUID id;
    private final LocalDateTime createdAt;
    private final String label;
    private final String value;
    private final Format format;
    private final OperationDetail.Classification classification;
    private final String sourceOperation;
    private final Integer byteLength;

    public enum Format {
        TEXT,
        HEX,
        BASE64,
        BASE64URL,
        PEM,
        JSON,
        BINARY_DESCRIPTION,
        UNKNOWN;

        public static Format inferFormat(String text) {
            if (text == null || text.isBlank()) return UNKNOWN;
            String trimmed = text.trim();
            if (trimmed.startsWith("-----BEGIN") && trimmed.contains("-----END")) {
                return PEM;
            }
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    JsonParser.parseString(trimmed);
                    return JSON;
                } catch (JsonSyntaxException e) {
                    // Ignore, not valid JSON
                }
            }

            String hexClean = trimmed.replaceAll("[\\s:]+", "");
            // Must have some HEX-specific letters to not confuse with plain digits, unless it's clearly hex formatted (e.g. 0a:1b).
            // But for safety, if it's even length and matches Hex, we accept it if it's just digits, as many systems do.
            if (hexClean.length() > 0 && hexClean.length() % 2 == 0 && hexClean.matches("^[0-9A-Fa-f]+$")) {
                // If it's pure decimal without spaces/colons, it could be TEXT. Let's assume HEX for now as it's common.
                return HEX;
            }

            // Check JWT (Base64URL)
            if (!trimmed.contains(" ") && trimmed.matches("^[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+$")) {
                return BASE64URL;
            }

            // Check Base64URL
            if (!trimmed.contains(" ") && trimmed.length() > 0 && trimmed.matches("^[A-Za-z0-9_\\-]+$")) {
                if (trimmed.length() >= 16 && (trimmed.contains("-") || trimmed.contains("_"))) {
                    return BASE64URL;
                }
            }

            // Check Base64 (ignore newlines, but NOT spaces)
            String b64Clean = trimmed.replaceAll("[\r\n]+", "");
            if (!b64Clean.contains(" ") && b64Clean.length() > 0 && b64Clean.length() % 4 == 0 && b64Clean.matches("^[A-Za-z0-9+/]+={0,2}$")) {
                // Avoid classifying plain text words (e.g. "HelloWorld") as Base64
                if (b64Clean.matches(".*[0-9].*") || b64Clean.matches(".*[A-Z].*") && b64Clean.matches(".*[a-z].*") || b64Clean.endsWith("=")) {
                    return BASE64;
                }
            }
            return TEXT;
        }
    }

    public ClipboardEntry(String label, String value, Format format,
                          OperationDetail.Classification classification,
                          String sourceOperation) {
        this(UUID.randomUUID(), LocalDateTime.now(), label != null ? label : "Copied Value",
             value != null ? value : "", format != null ? format : Format.UNKNOWN,
             classification != null ? classification : OperationDetail.Classification.SENSITIVE,
             sourceOperation);
    }

    private ClipboardEntry(UUID id, LocalDateTime createdAt, String label, String value,
                           Format format, OperationDetail.Classification classification,
                           String sourceOperation) {
        this.id = id;
        this.createdAt = createdAt;
        this.label = label;
        this.value = value;
        this.format = format;
        this.classification = classification;
        this.sourceOperation = sourceOperation;
        this.byteLength = calculateByteLength(this.value, this.format);
    }

    public ClipboardEntry withLabel(String newLabel) {
        return new ClipboardEntry(this.id, this.createdAt, newLabel, this.value, this.format, this.classification, this.sourceOperation);
    }

    private Integer calculateByteLength(String val, Format fmt) {
        if (val == null || val.isBlank()) return 0;
        try {
            switch (fmt) {
                case HEX:
                    // Only count valid hex chars
                    String cleanHex = val.replaceAll("[^0-9A-Fa-f]", "");
                    return cleanHex.length() / 2;
                case BASE64:
                case BASE64URL:
                case PEM:
                    // Very rough estimate for base64: length * 3 / 4, ignoring padding and newlines
                    String cleanB64 = val.replaceAll("[\\s=]+", ""); // exclude whitespace and padding
                    if (fmt == Format.PEM) {
                        cleanB64 = cleanB64.replaceAll("-----(BEGIN|END).*?-----", "");
                    }
                    return (int) Math.ceil(cleanB64.length() * 3.0 / 4.0);
                case TEXT:
                case JSON:
                    return val.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public UUID getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public Format getFormat() { return format; }
    public OperationDetail.Classification getClassification() { return classification; }
    public String getSourceOperation() { return sourceOperation; }
    public Integer getByteLength() { return byteLength; }
}
