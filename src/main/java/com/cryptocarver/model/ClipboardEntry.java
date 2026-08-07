package com.cryptocarver.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
    private final String algorithm;
    private final Integer byteLength;
    private final List<String> tags;
    private final String note;
    private final boolean pinned;

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
            if (hexClean.length() > 0 && hexClean.length() % 2 == 0 && hexClean.matches("^[0-9A-Fa-f]+$")) {
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

            // Check Base64
            String b64Clean = trimmed.replaceAll("[\r\n]+", "");
            if (!b64Clean.contains(" ") && b64Clean.length() > 0 && b64Clean.length() % 4 == 0 && b64Clean.matches("^[A-Za-z0-9+/]+={0,2}$")) {
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
        this(label, value, format, classification, sourceOperation, null, java.util.Collections.emptyList(), "", false);
    }

    public ClipboardEntry(String label, String value, Format format,
                          OperationDetail.Classification classification,
                          String sourceOperation, String algorithm) {
        this(label, value, format, classification, sourceOperation, algorithm, java.util.Collections.emptyList(), "", false);
    }

    public ClipboardEntry(String label, String value, Format format,
                          OperationDetail.Classification classification,
                          String sourceOperation, String algorithm,
                          List<String> tags, String note, boolean pinned) {
        this(UUID.randomUUID(), LocalDateTime.now(), label != null ? label : "Copied Value",
             value != null ? value : "", format != null ? format : Format.UNKNOWN,
             classification != null ? classification : OperationDetail.Classification.SENSITIVE,
             sourceOperation, algorithm, sanitizeTags(tags), sanitizeNote(note), pinned);
    }

    private ClipboardEntry(UUID id, LocalDateTime createdAt, String label, String value,
                           Format format, OperationDetail.Classification classification,
                           String sourceOperation, String algorithm,
                           List<String> tags, String note, boolean pinned) {
        this.id = id;
        this.createdAt = createdAt;
        this.label = label;
        this.value = value;
        this.format = format;
        this.classification = classification;
        this.sourceOperation = sourceOperation;
        this.algorithm = algorithm;
        this.tags = sanitizeTags(tags);
        this.note = sanitizeNote(note);
        this.pinned = pinned;
        this.byteLength = calculateByteLength(this.value, this.format);
    }

    public ClipboardEntry withLabel(String newLabel) {
        return new ClipboardEntry(this.id, this.createdAt, newLabel, this.value, this.format,
                this.classification, this.sourceOperation, this.algorithm, this.tags, this.note, this.pinned);
    }

    public ClipboardEntry withTagsAndNote(List<String> newTags, String newNote) {
        return new ClipboardEntry(this.id, this.createdAt, this.label, this.value, this.format,
                this.classification, this.sourceOperation, this.algorithm, newTags, newNote, this.pinned);
    }

    public ClipboardEntry withPinned(boolean newPinned) {
        return new ClipboardEntry(this.id, this.createdAt, this.label, this.value, this.format,
                this.classification, this.sourceOperation, this.algorithm, this.tags, this.note, newPinned);
    }

    public static List<String> sanitizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) return java.util.Collections.emptyList();
        List<String> clean = new java.util.ArrayList<>();
        for (String t : rawTags) {
            if (t == null) continue;
            String trimmed = t.trim();
            if (!trimmed.isBlank() && !clean.contains(trimmed)) {
                clean.add(trimmed);
                if (clean.size() == 12) break; // Max 12 tags
            }
        }
        return java.util.Collections.unmodifiableList(clean);
    }

    public static String sanitizeNote(String rawNote) {
        if (rawNote == null) return "";
        if (rawNote.length() > 1000) {
            return rawNote.substring(0, 1000);
        }
        return rawNote;
    }

    private Integer calculateByteLength(String val, Format fmt) {
        if (val == null || val.isBlank()) return 0;
        try {
            switch (fmt) {
                case HEX:
                    String cleanHex = val.replaceAll("[^0-9A-Fa-f]", "");
                    return cleanHex.length() / 2;
                case BASE64:
                    return Base64.getDecoder().decode(val.replaceAll("\\s+", "")).length;
                case BASE64URL:
                    return Base64.getUrlDecoder().decode(val.replaceAll("\\s+", "")).length;
                case PEM:
                    String pemBody = val
                            .replaceAll("-----BEGIN [^-]+-----", "")
                            .replaceAll("-----END [^-]+-----", "")
                            .replaceAll("\\s+", "");
                    return Base64.getDecoder().decode(pemBody).length;
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
    public String getAlgorithm() { return algorithm; }
    public Integer getByteLength() { return byteLength; }
    public List<String> getTags() { return tags == null ? java.util.Collections.emptyList() : tags; }
    public String getNote() { return note == null ? "" : note; }
    public boolean isPinned() { return pinned; }

    /**
     * Normalizes an entry read from an older shelf schema. Gson leaves newly
     * added fields null when they are absent from persisted JSON, so this
     * method supplies deterministic identity and safe defaults without
     * changing the stored result value.
     */
    public ClipboardEntry normalizePersisted() {
        String normalizedValue = value != null ? value : "";
        String identity = String.valueOf(label) + "\u0000" + normalizedValue + "\u0000" + String.valueOf(sourceOperation);
        UUID normalizedId = id != null ? id : UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        LocalDateTime normalizedCreatedAt = createdAt != null
                ? createdAt
                : LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);
        String normalizedLabel = label != null && !label.isBlank() ? label : "Saved Laboratory Result";
        Format normalizedFormat = format != null ? format : Format.inferFormat(normalizedValue);
        OperationDetail.Classification normalizedClassification = classification != null
                ? classification
                : OperationDetail.Classification.SENSITIVE;
        String normalizedSource = sourceOperation != null ? sourceOperation : "Unknown";
        return new ClipboardEntry(normalizedId, normalizedCreatedAt, normalizedLabel, normalizedValue,
                normalizedFormat, normalizedClassification, normalizedSource, algorithm,
                tags, note, pinned);
    }
}
