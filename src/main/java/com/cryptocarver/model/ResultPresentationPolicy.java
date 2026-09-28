package com.cryptocarver.model;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure decisions and formatting used when presenting operation results. */
public final class ResultPresentationPolicy {
    private ResultPresentationPolicy() {}

    public static boolean isPrivateMaterialPlaceholder(String text) {
        if (text == null) return false;
        String normalized = text.toUpperCase(Locale.ROOT);
        return normalized.contains("PRIVATE KEY MATERIAL") && normalized.contains("NOT RECORDED");
    }

    public static boolean isCompletePrivateKeyMaterial(String text) {
        if (text == null || isPrivateMaterialPlaceholder(text)) return false;
        String normalized = text.toUpperCase(Locale.ROOT);
        return normalized.contains("-----BEGIN ") && normalized.contains("PRIVATE KEY-----")
                && normalized.contains("-----END ") && normalized.contains("PRIVATE KEY-----");
    }

    public static boolean isShelfCaptureBlockedByVisibility(OperationDetail.Classification classification,
                                                              SecretVisibilityProfile profile) {
        return profile != SecretVisibilityProfile.FULL_LAB
                && (classification == OperationDetail.Classification.SECRET
                || classification == OperationDetail.Classification.SENSITIVE);
    }

    public static OperationDetail.Classification classifyPublishedResult(OperationResult result) {
        if (result == null) return OperationDetail.Classification.PUBLIC;
        OperationDetail.Classification classification = max(result.getOutputClassification(), result.getEnrichedOutputClassification());
        for (OperationDetail detail : result.getDetails()) classification = max(classification, detail.classification());
        return classification;
    }

    private static OperationDetail.Classification max(OperationDetail.Classification a, OperationDetail.Classification b) {
        if (a == OperationDetail.Classification.SECRET || b == OperationDetail.Classification.SECRET) return OperationDetail.Classification.SECRET;
        if (a == OperationDetail.Classification.SENSITIVE || b == OperationDetail.Classification.SENSITIVE) return OperationDetail.Classification.SENSITIVE;
        return OperationDetail.Classification.PUBLIC;
    }

    public static String renderBytesForDisplay(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        return isPrintableUtf8(bytes) ? new String(bytes, StandardCharsets.UTF_8) : toHex(bytes);
    }

    public static boolean isPrintableUtf8(byte[] bytes) {
        if (bytes == null) return false;
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.codePoints().allMatch(c -> !Character.isISOControl(c) || c == '\n' || c == '\r' || c == '\t');
        } catch (CharacterCodingException ex) { return false; }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = "0123456789ABCDEF".toCharArray(), out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) { int n = bytes[i] & 255; out[i * 2] = hex[n >>> 4]; out[i * 2 + 1] = hex[n & 15]; }
        return new String(out);
    }

    public static List<OperationDetail> detailsForHistory(OperationResult result) {
        List<OperationDetail> details = new ArrayList<>(result.getDetails());
        addPayloadDetail(details, "Input", result.getInput()); addPayloadDetail(details, "Output", result.getOutput());
        return details;
    }

    private static void addPayloadDetail(List<OperationDetail> details, String name, byte[] bytes) {
        if (bytes == null) return;
        String rendered = renderBytesForDisplay(bytes);
        String format = isPrintableUtf8(bytes) ? "UTF-8" : "Hex";
        details.add(new OperationDetail(name + " (" + bytes.length + " bytes)", rendered,
                OperationDetail.Classification.SENSITIVE, rendered.indexOf('\n') >= 0 || rendered.length() > 120, format));
    }
}
