package com.cryptocarver.utils;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ResultComparator;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.google.gson.GsonBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResultComparisonReportExporter {

    public static String toMarkdown(ClipboardEntry entry1, ClipboardEntry entry2, SecretVisibilityProfile profile) {
        ResultComparator.ComparisonDetails details = ResultComparator.compare(entry1, entry2, profile);

        StringBuilder sb = new StringBuilder();
        sb.append("# Laboratory Result Comparison Report\n\n");
        sb.append("**Comparison Status**: ").append(details.status().getLabel()).append("\n");
        sb.append("**Summary**: ").append(details.summary()).append("\n");
        sb.append("**Security Profile**: ").append(profile).append("\n\n");

        sb.append("## Item 1 Metadata\n");
        appendMetadataMarkdown(sb, entry1, details.fingerprint1(), profile);

        sb.append("\n## Item 2 Metadata\n");
        appendMetadataMarkdown(sb, entry2, details.fingerprint2(), profile);

        sb.append("\n## Technical Comparison Details\n");
        sb.append("```\n");
        sb.append(details.textualDiff() != null ? details.textualDiff() : "No details available").append("\n");
        sb.append("```\n");

        return sb.toString();
    }

    private static void appendMetadataMarkdown(StringBuilder sb, ClipboardEntry entry, String fingerprint,
                                               SecretVisibilityProfile profile) {
        sb.append("- **Label**: ").append(entry.getLabel()).append("\n");
        sb.append("- **Source Operation**: ").append(entry.getSourceOperation() != null ? entry.getSourceOperation() : "—").append("\n");
        sb.append("- **Algorithm**: ").append(entry.getAlgorithm() != null ? entry.getAlgorithm() : "—").append("\n");
        sb.append("- **Timestamp**: ").append(entry.getCreatedAt()).append("\n");
        sb.append("- **Format**: ").append(entry.getFormat()).append("\n");
        sb.append("- **Classification**: ").append(entry.getClassification()).append("\n");
        sb.append("- **Size**: ").append(entry.getByteLength() != null ? entry.getByteLength() + " bytes" : "—").append("\n");
        sb.append("- **SHA-256 Fingerprint**: ").append(fingerprint != null ? fingerprint : "—").append("\n");
        if (!entry.getTags().isEmpty()) {
            sb.append("- **Tags**: ").append(projectAnnotation(String.join(", ", entry.getTags()), entry, profile)).append("\n");
        }
        if (!entry.getNote().isBlank()) {
            sb.append("- **Note**: ").append(projectAnnotation(entry.getNote(), entry, profile)).append("\n");
        }
    }

    private static String projectAnnotation(String value, ClipboardEntry entry, SecretVisibilityProfile profile) {
        boolean sensitive = entry.getClassification() == com.cryptocarver.model.OperationDetail.Classification.SECRET
                || entry.getClassification() == com.cryptocarver.model.OperationDetail.Classification.SENSITIVE;
        if (!sensitive || profile == SecretVisibilityProfile.FULL_LAB) return value;
        return profile == SecretVisibilityProfile.REDACTED ? "[REDACTED]" : "[MASKED]";
    }

    public static String toJson(ClipboardEntry entry1, ClipboardEntry entry2, SecretVisibilityProfile profile) {
        ResultComparator.ComparisonDetails details = ResultComparator.compare(entry1, entry2, profile);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("reportType", "LaboratoryResultComparison");
        root.put("securityProfile", profile.name());
        root.put("status", details.status().name());
        root.put("summary", details.summary());
        root.put("matchPercentage", details.matchPercentage());
        root.put("firstDifferenceOffset", details.firstDifferenceOffset());

        root.put("item1", buildItemMap(entry1, details.fingerprint1(), profile));
        root.put("item2", buildItemMap(entry2, details.fingerprint2(), profile));

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static Map<String, Object> buildItemMap(ClipboardEntry entry, String fingerprint,
                                                    SecretVisibilityProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId().toString());
        map.put("label", entry.getLabel());
        map.put("sourceOperation", entry.getSourceOperation());
        map.put("algorithm", entry.getAlgorithm());
        map.put("timestamp", entry.getCreatedAt().toString());
        map.put("format", entry.getFormat().name());
        map.put("classification", entry.getClassification().name());
        map.put("byteLength", entry.getByteLength());
        map.put("fingerprint", fingerprint);
        map.put("tags", projectAnnotation(String.join(", ", entry.getTags()), entry, profile));
        map.put("note", projectAnnotation(entry.getNote(), entry, profile));
        map.put("pinned", entry.isPinned());
        return map;
    }
}
