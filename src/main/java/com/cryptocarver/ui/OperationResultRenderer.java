package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Applies secret visibility and byte rendering rules to a normalized operation result. */
final class OperationResultRenderer {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private OperationResultRenderer() {
    }

    static String render(OperationResult result, SecretVisibilityProfile visibility) {
        if (result == null) return "";
        SecretVisibilityProfile policy = visibility == null ? SecretVisibilityProfile.REDACTED : visibility;

        String protectedResult = protectedValue(classification(result.getDetails()), policy);
        if (protectedResult != null) return protectedResult;

        if (result.getEnrichedOutput() != null && !result.getEnrichedOutput().isBlank()) {
            protectedResult = protectedValue(result.getEnrichedOutputClassification(), policy);
            return protectedResult == null ? result.getEnrichedOutput() : protectedResult;
        }

        byte[] output = result.getOutput();
        if (output == null || output.length == 0) return summary(result, policy);

        protectedResult = protectedValue(result.getOutputClassification(), policy);
        return protectedResult == null ? renderBytes(output) : protectedResult;
    }

    static OperationDetail.Classification classification(OperationResult result) {
        if (result == null) return OperationDetail.Classification.PUBLIC;
        OperationDetail.Classification classification = max(
                result.getOutputClassification(), result.getEnrichedOutputClassification());
        return max(classification, classification(result.getDetails()));
    }

    static String renderBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        if (isPrintableUtf8(bytes)) return new String(bytes, StandardCharsets.UTF_8);
        char[] encoded = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            encoded[index * 2] = HEX[value >>> 4];
            encoded[index * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(encoded);
    }

    static boolean isPrintableUtf8(byte[] bytes) {
        if (bytes == null) return false;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return text.codePoints().allMatch(codePoint -> !Character.isISOControl(codePoint)
                    || codePoint == '\n' || codePoint == '\r' || codePoint == '\t');
        } catch (CharacterCodingException ignored) {
            return false;
        }
    }

    private static String summary(OperationResult result, SecretVisibilityProfile policy) {
        StringBuilder summary = new StringBuilder("Operation: ").append(result.getOperation());
        if (result.getStatusMessage() != null && !result.getStatusMessage().isBlank()) {
            summary.append("\nStatus: ").append(result.getStatusMessage());
        }
        for (OperationDetail detail : result.getDetails()) {
            if (detail == null) continue;
            String protectedValue = protectedValue(detail.classification(), policy);
            if (protectedValue != null && protectedValue.isEmpty()) continue;
            String value = protectedValue == null ? detail.value() : protectedValue;
            summary.append("\n").append(detail.name()).append(": ").append(value == null ? "" : value);
        }
        return summary.toString();
    }

    /** Null means visible, an empty string means redacted, otherwise the replacement text. */
    private static String protectedValue(OperationDetail.Classification classification, SecretVisibilityProfile policy) {
        if (classification == OperationDetail.Classification.SECRET) {
            if (policy == SecretVisibilityProfile.REDACTED) return "";
            if (policy == SecretVisibilityProfile.MASKED) return "***MASKED***";
        } else if (classification == OperationDetail.Classification.SENSITIVE
                && policy != SecretVisibilityProfile.FULL_LAB) {
            return "***MASKED***";
        }
        return null;
    }

    private static OperationDetail.Classification classification(Iterable<OperationDetail> details) {
        OperationDetail.Classification classification = OperationDetail.Classification.PUBLIC;
        if (details == null) return classification;
        for (OperationDetail detail : details) {
            if (detail == null) continue;
            classification = max(classification, detail.classification());
            if (classification == OperationDetail.Classification.SECRET) break;
        }
        return classification;
    }

    private static OperationDetail.Classification max(OperationDetail.Classification first,
                                                       OperationDetail.Classification second) {
        OperationDetail.Classification left = first == null ? OperationDetail.Classification.PUBLIC : first;
        OperationDetail.Classification right = second == null ? OperationDetail.Classification.PUBLIC : second;
        if (left == OperationDetail.Classification.SECRET || right == OperationDetail.Classification.SECRET) {
            return OperationDetail.Classification.SECRET;
        }
        if (left == OperationDetail.Classification.SENSITIVE || right == OperationDetail.Classification.SENSITIVE) {
            return OperationDetail.Classification.SENSITIVE;
        }
        return OperationDetail.Classification.PUBLIC;
    }
}
