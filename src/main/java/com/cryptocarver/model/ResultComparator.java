package com.cryptocarver.model;

import com.cryptocarver.utils.DataConverter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

public class ResultComparator {

    public enum Status {
        EQUAL("Equal"),
        DIFFERENT("Different"),
        NOT_COMPARABLE("Not Comparable");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public record ComparisonDetails(
            Status status,
            String summary,
            Long firstDifferenceOffset,
            int length1,
            int length2,
            Double matchPercentage,
            String fingerprint1,
            String fingerprint2,
            String textualDiff
    ) {}

    public static ComparisonDetails compare(ClipboardEntry entry1, ClipboardEntry entry2, SecretVisibilityProfile profile) {
        if (entry1 == null || entry2 == null) {
            return new ComparisonDetails(Status.NOT_COMPARABLE, "Invalid entries", null, 0, 0, null, null, null, null);
        }

        boolean sensitive1 = isSensitive(entry1);
        boolean sensitive2 = isSensitive(entry2);
        boolean isRedacted1 = sensitive1 && profile == SecretVisibilityProfile.REDACTED;
        boolean isRedacted2 = sensitive2 && profile == SecretVisibilityProfile.REDACTED;

        if (isRedacted1 || isRedacted2) {
            return new ComparisonDetails(
                    Status.NOT_COMPARABLE,
                    "Content redacted by active security profile (" + profile + ")",
                    null,
                    0,
                    0,
                    null,
                    "[REDACTED]",
                    "[REDACTED]",
                    "Redacted by security profile"
            );
        }

        String val1 = entry1.getValue();
        String val2 = entry2.getValue();
        ClipboardEntry.Format fmt1 = entry1.getFormat();
        ClipboardEntry.Format fmt2 = entry2.getFormat();

        String fp1 = computeFingerprint(val1, fmt1, profile, sensitive1);
        String fp2 = computeFingerprint(val2, fmt2, profile, sensitive2);

        if (!areFormatsCompatible(fmt1, fmt2)) {
            return new ComparisonDetails(
                    Status.NOT_COMPARABLE,
                    "Incompatible formats (" + fmt1 + " vs " + fmt2 + ")",
                    null,
                    val1.length(),
                    val2.length(),
                    null,
                    fp1,
                    fp2,
                    "Formats " + fmt1 + " and " + fmt2 + " cannot be directly compared."
            );
        }

        if (isByteComparable(fmt1, fmt2)) {
            byte[] bytes1 = extractBytes(val1, fmt1);
            byte[] bytes2 = extractBytes(val2, fmt2);
            if (bytes1 == null || bytes2 == null) {
                return new ComparisonDetails(Status.NOT_COMPARABLE, "Unable to decode byte payload", null, 0, 0, null, fp1, fp2, null);
            }
            return compareBytes(bytes1, bytes2, fp1, fp2);
        } else {
            return compareText(val1, val2, fp1, fp2);
        }
    }

    private static boolean isSensitive(ClipboardEntry entry) {
        return entry.getClassification() == OperationDetail.Classification.SECRET ||
               entry.getClassification() == OperationDetail.Classification.SENSITIVE;
    }

    private static boolean areFormatsCompatible(ClipboardEntry.Format f1, ClipboardEntry.Format f2) {
        if (f1 == f2) return true;
        if ((f1 == ClipboardEntry.Format.HEX) && (f2 == ClipboardEntry.Format.HEX)) return true;
        if ((f1 == ClipboardEntry.Format.BASE64 || f1 == ClipboardEntry.Format.BASE64URL) &&
            (f2 == ClipboardEntry.Format.BASE64 || f2 == ClipboardEntry.Format.BASE64URL)) return true;
        if ((f1 == ClipboardEntry.Format.TEXT || f1 == ClipboardEntry.Format.JSON) &&
            (f2 == ClipboardEntry.Format.TEXT || f2 == ClipboardEntry.Format.JSON)) return true;
        return false;
    }

    private static boolean isByteComparable(ClipboardEntry.Format f1, ClipboardEntry.Format f2) {
        return f1 == ClipboardEntry.Format.HEX || f1 == ClipboardEntry.Format.BASE64 || f1 == ClipboardEntry.Format.BASE64URL;
    }

    private static byte[] extractBytes(String val, ClipboardEntry.Format fmt) {
        if (val == null) return new byte[0];
        try {
            if (fmt == ClipboardEntry.Format.HEX) {
                String cleanHex = val.replaceAll("[^0-9A-Fa-f]", "");
                return DataConverter.hexToBytes(cleanHex);
            } else if (fmt == ClipboardEntry.Format.BASE64) {
                return Base64.getDecoder().decode(val.replaceAll("\\s+", ""));
            } else if (fmt == ClipboardEntry.Format.BASE64URL) {
                return Base64.getUrlDecoder().decode(val.replaceAll("\\s+", ""));
            }
            return val.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static ComparisonDetails compareBytes(byte[] b1, byte[] b2, String fp1, String fp2) {
        int len1 = b1.length;
        int len2 = b2.length;
        if (len1 == 0 && len2 == 0) {
            return new ComparisonDetails(Status.EQUAL, "Both payloads are empty", null, 0, 0, 100.0, fp1, fp2, "Empty payloads");
        }

        Long firstDiff = null;
        int minLen = Math.min(len1, len2);
        int matchingBytes = 0;
        for (int i = 0; i < minLen; i++) {
            if (b1[i] == b2[i]) {
                matchingBytes++;
            } else if (firstDiff == null) {
                firstDiff = (long) i;
            }
        }

        int maxLen = Math.max(len1, len2);
        double matchRatio = Math.round(((double) matchingBytes / maxLen) * 1000.0) / 10.0;

        if (len1 == len2 && firstDiff == null) {
            return new ComparisonDetails(Status.EQUAL, "Values match exactly (" + len1 + " bytes)", null, len1, len2, 100.0, fp1, fp2, "100% binary match");
        }

        String summary;
        if (firstDiff != null) {
            summary = String.format("First difference at offset 0x%04X (byte %d). %s match (%d/%d bytes)", firstDiff, firstDiff, matchRatio + "%", matchingBytes, maxLen);
        } else {
            summary = String.format("Length mismatch (%d bytes vs %d bytes). %s prefix match", len1, len2, matchRatio + "%");
        }

        String textDiff = "Length: " + len1 + " B vs " + len2 + " B\n"
                + "First Diff Offset: " + (firstDiff != null ? String.format("0x%04X", firstDiff) : "None (prefix match)") + "\n"
                + "Match Ratio: " + matchRatio + "%";

        return new ComparisonDetails(Status.DIFFERENT, summary, firstDiff, len1, len2, matchRatio, fp1, fp2, textDiff);
    }

    private static ComparisonDetails compareText(String t1, String t2, String fp1, String fp2) {
        String s1 = t1 == null ? "" : t1;
        String s2 = t2 == null ? "" : t2;
        int len1 = s1.length();
        int len2 = s2.length();

        if (Objects.equals(s1, s2)) {
            return new ComparisonDetails(Status.EQUAL, "Text values match exactly (" + len1 + " chars)", null, len1, len2, 100.0, fp1, fp2, "100% text match");
        }

        Long firstDiff = null;
        int minLen = Math.min(len1, len2);
        int matchingChars = 0;
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                matchingChars++;
            } else if (firstDiff == null) {
                firstDiff = (long) i;
            }
        }
        int maxLen = Math.max(len1, len2);
        double matchRatio = Math.round(((double) matchingChars / maxLen) * 1000.0) / 10.0;

        String summary = firstDiff != null
                ? String.format("First text difference at position %d. %s match", firstDiff, matchRatio + "%")
                : String.format("Length mismatch (%d chars vs %d chars)", len1, len2);

        String textDiff = "Length: " + len1 + " chars vs " + len2 + " chars\n"
                + "First Diff Pos: " + (firstDiff != null ? firstDiff.toString() : "None") + "\n"
                + "Match Ratio: " + matchRatio + "%";

        return new ComparisonDetails(Status.DIFFERENT, summary, firstDiff, len1, len2, matchRatio, fp1, fp2, textDiff);
    }

    private static String computeFingerprint(String val, ClipboardEntry.Format format,
                                             SecretVisibilityProfile profile, boolean sensitive) {
        if (val == null || val.isBlank()) return "—";
        if (sensitive && profile == SecretVisibilityProfile.REDACTED) return "[REDACTED]";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] comparableBytes = isByteComparable(format, format)
                    ? extractBytes(val, format)
                    : val.getBytes(StandardCharsets.UTF_8);
            if (comparableBytes == null) return "—";
            byte[] hash = digest.digest(comparableBytes);
            return DataConverter.bytesToHex(hash).substring(0, 16).toUpperCase() + "...";
        } catch (Exception e) {
            return "—";
        }
    }
}
