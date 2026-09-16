package com.cryptocarver.model;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, clear-text snapshot of one operation added to a session trail.
 *
 * <p>This model deliberately keeps every value, including secret material, so
 * an operator can reconstruct the exact laboratory workflow. Binary payloads
 * are preserved losslessly as hexadecimal and, when valid printable UTF-8,
 * also as readable text. Each entry includes the preceding hash so ordering
 * changes are detectable. Callers must warn users before persisting it.</p>
 */
public final class SessionOperationStep implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String GENESIS_HASH = "GENESIS";

    private String id;
    private String timestamp;
    private String title;
    private List<String> tags;
    private String operation;
    private String status;
    private boolean inputPresent;
    private boolean outputPresent;
    private int inputLength;
    private int outputLength;
    private String inputHex;
    private String outputHex;
    private String inputText;
    private String outputText;
    private String inputFingerprint;
    private String outputFingerprint;
    private String enrichedOutput;
    private String enrichedOutputFingerprint;
    private OperationDetail.Classification outputClassification;
    private OperationDetail.Classification enrichedOutputClassification;
    private List<OperationDetail> details;
    private Map<String, String> parameters;
    private String previousHash;
    private String entryHash;

    private SessionOperationStep() {
        // Gson constructor.
    }

    static SessionOperationStep capture(OperationResult result, String title, List<String> tags,
                                        Map<String, ?> parameters, String previousHash) {
        if (result == null) throw new IllegalArgumentException("Operation result is required");

        SessionOperationStep step = new SessionOperationStep();
        step.id = UUID.randomUUID().toString();
        step.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        step.title = normalizedTitle(title, result.getOperation());
        step.tags = normalizedTags(tags);
        step.operation = result.getOperation();
        step.status = result.getStatusMessage();

        byte[] input = result.getInput();
        byte[] output = result.getOutput();
        step.inputPresent = input != null;
        step.outputPresent = output != null;
        step.inputLength = input == null ? 0 : input.length;
        step.outputLength = output == null ? 0 : output.length;
        step.inputHex = hex(input);
        step.outputHex = hex(output);
        step.inputText = printableUtf8(input);
        step.outputText = printableUtf8(output);
        step.inputFingerprint = fingerprint(input);
        step.outputFingerprint = fingerprint(output);
        step.enrichedOutput = result.getEnrichedOutput();
        step.enrichedOutputFingerprint = fingerprint(step.enrichedOutput);
        step.outputClassification = classification(result.getOutputClassification());
        step.enrichedOutputClassification = classification(result.getEnrichedOutputClassification());
        step.details = copiedDetails(result.getDetails());
        step.parameters = copiedParameters(parameters);
        step.previousHash = validPreviousHash(previousHash);
        step.entryHash = step.calculateHash();
        return step;
    }

    SessionOperationStep relink(String newPreviousHash) {
        SessionOperationStep linked = copyOf(this);
        linked.previousHash = validPreviousHash(newPreviousHash);
        linked.entryHash = linked.calculateHash();
        return linked;
    }

    static SessionOperationStep copyOf(SessionOperationStep source) {
        if (source == null) throw new IllegalArgumentException("Session operation step is required");
        SessionOperationStep copy = new SessionOperationStep();
        copy.id = source.id;
        copy.timestamp = source.timestamp;
        copy.title = source.title;
        copy.tags = normalizedTags(source.tags);
        copy.operation = source.operation;
        copy.status = source.status;
        copy.inputPresent = source.inputPresent;
        copy.outputPresent = source.outputPresent;
        copy.inputLength = source.inputLength;
        copy.outputLength = source.outputLength;
        copy.inputHex = source.inputHex;
        copy.outputHex = source.outputHex;
        copy.inputText = source.inputText;
        copy.outputText = source.outputText;
        copy.inputFingerprint = source.inputFingerprint;
        copy.outputFingerprint = source.outputFingerprint;
        copy.enrichedOutput = source.enrichedOutput;
        copy.enrichedOutputFingerprint = source.enrichedOutputFingerprint;
        copy.outputClassification = classification(source.outputClassification);
        copy.enrichedOutputClassification = classification(source.enrichedOutputClassification);
        copy.details = copiedDetails(source.details);
        copy.parameters = copiedParameters(source.parameters);
        copy.previousHash = source.previousHash;
        copy.entryHash = source.entryHash;
        return copy;
    }

    boolean hasValidHash(String expectedPreviousHash) {
        return validPreviousHash(expectedPreviousHash).equals(previousHash)
                && entryHash != null
                && entryHash.equals(calculateHash());
    }

    private String calculateHash() {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(canonical, id);
        appendCanonical(canonical, timestamp);
        appendCanonical(canonical, title);
        for (String tag : safeList(tags)) appendCanonical(canonical, tag);
        appendCanonical(canonical, operation);
        appendCanonical(canonical, status);
        appendCanonical(canonical, Boolean.toString(inputPresent));
        appendCanonical(canonical, Boolean.toString(outputPresent));
        appendCanonical(canonical, Integer.toString(inputLength));
        appendCanonical(canonical, Integer.toString(outputLength));
        appendCanonical(canonical, inputHex);
        appendCanonical(canonical, outputHex);
        appendCanonical(canonical, inputText);
        appendCanonical(canonical, outputText);
        appendCanonical(canonical, inputFingerprint);
        appendCanonical(canonical, outputFingerprint);
        appendCanonical(canonical, enrichedOutput);
        appendCanonical(canonical, enrichedOutputFingerprint);
        appendCanonical(canonical, classification(outputClassification).name());
        appendCanonical(canonical, classification(enrichedOutputClassification).name());
        for (OperationDetail detail : safeDetails(details)) {
            appendCanonical(canonical, detail.name());
            appendCanonical(canonical, detail.value());
            appendCanonical(canonical, detail.classification().name());
            appendCanonical(canonical, Boolean.toString(detail.multiline()));
            appendCanonical(canonical, detail.format());
        }
        for (Map.Entry<String, String> parameter : safeParameters(parameters).entrySet()) {
            appendCanonical(canonical, parameter.getKey());
            appendCanonical(canonical, parameter.getValue());
        }
        appendCanonical(canonical, previousHash);
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendCanonical(StringBuilder builder, String value) {
        String safe = value == null ? "" : value;
        builder.append(safe.length()).append(':').append(safe).append('\n');
    }

    private static List<OperationDetail> copiedDetails(List<OperationDetail> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        List<OperationDetail> copied = new ArrayList<>();
        for (OperationDetail detail : source) {
            if (detail == null) continue;
            copied.add(new OperationDetail(detail.name(), detail.value(), classification(detail.classification()),
                    detail.multiline(), detail.format()));
        }
        return copied;
    }

    private static Map<String, String> copiedParameters(Map<String, ?> source) {
        Map<String, String> copied = new LinkedHashMap<>();
        if (source == null) return copied;
        source.forEach((key, value) -> {
            if (key != null) copied.put(key, value == null ? "" : String.valueOf(value));
        });
        return copied;
    }

    private static String normalizedTitle(String title, String fallback) {
        String normalized = safeText(title).trim();
        return normalized.isEmpty() ? safeText(fallback).trim() : normalized;
    }

    private static List<String> normalizedTags(List<String> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String tag : source) {
            String normalized = safeText(tag).trim();
            if (!normalized.isEmpty() && !result.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String validPreviousHash(String value) {
        return value == null || value.isBlank() ? GENESIS_HASH : value;
    }

    private static OperationDetail.Classification classification(OperationDetail.Classification value) {
        return value == null ? OperationDetail.Classification.PUBLIC : value;
    }

    private static String hex(byte[] value) {
        if (value == null) return null;
        char[] encoded = new char[value.length * 2];
        char[] alphabet = "0123456789ABCDEF".toCharArray();
        for (int index = 0; index < value.length; index++) {
            int current = value[index] & 0xff;
            encoded[index * 2] = alphabet[current >>> 4];
            encoded[index * 2 + 1] = alphabet[current & 0x0f];
        }
        return new String(encoded);
    }

    private static String printableUtf8(byte[] value) {
        if (value == null) return null;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
            return text.codePoints().allMatch(codePoint -> !Character.isISOControl(codePoint)
                    || codePoint == '\n' || codePoint == '\r' || codePoint == '\t') ? text : null;
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static String fingerprint(byte[] value) {
        return value == null ? "-" : sha256(value);
    }

    private static String fingerprint(String value) {
        return value == null || value.isEmpty() ? "-" : sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte item : digest) encoded.append(String.format(Locale.ROOT, "%02X", item & 0xff));
            return encoded.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static List<String> safeList(List<String> source) {
        return source == null ? List.of() : source;
    }

    private static List<OperationDetail> safeDetails(List<OperationDetail> source) {
        return source == null ? List.of() : source;
    }

    private static Map<String, String> safeParameters(Map<String, String> source) {
        return source == null ? Map.of() : source;
    }

    public String getId() { return id; }
    public String getTimestamp() { return timestamp; }
    public String getTitle() { return title; }
    public List<String> getTags() { return Collections.unmodifiableList(new ArrayList<>(safeList(tags))); }
    public String getOperation() { return operation; }
    public String getStatus() { return status; }
    public boolean isInputPresent() { return inputPresent; }
    public boolean isOutputPresent() { return outputPresent; }
    public int getInputLength() { return inputLength; }
    public int getOutputLength() { return outputLength; }
    public String getInputHex() { return inputHex; }
    public String getOutputHex() { return outputHex; }
    public String getInputText() { return inputText; }
    public String getOutputText() { return outputText; }
    public String getInputFingerprint() { return inputFingerprint; }
    public String getOutputFingerprint() { return outputFingerprint; }
    public String getEnrichedOutput() { return enrichedOutput; }
    public String getEnrichedOutputFingerprint() { return enrichedOutputFingerprint; }
    public OperationDetail.Classification getOutputClassification() { return classification(outputClassification); }
    public OperationDetail.Classification getEnrichedOutputClassification() {
        return classification(enrichedOutputClassification);
    }
    public List<OperationDetail> getDetails() {
        return Collections.unmodifiableList(new ArrayList<>(safeDetails(details)));
    }
    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(safeParameters(parameters)));
    }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }
}
