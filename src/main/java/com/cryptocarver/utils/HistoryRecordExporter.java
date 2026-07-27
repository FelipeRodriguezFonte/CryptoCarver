package com.cryptocarver.utils;

import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exports one recorded operation as JSON without leaking values outside its chosen policy. */
public final class HistoryRecordExporter {
    private HistoryRecordExporter() { }

    public static String toJson(HistoryCommand item, SecretVisibilityProfile visibility) {
        if (item == null) {
            throw new IllegalArgumentException("History item is required");
        }
        SecretVisibilityProfile policy = visibility == null ? SecretVisibilityProfile.REDACTED : visibility;
        return gson().toJson(toRecord(item, policy));
    }

    /** Exports a history collection as a portable, policy-filtered JSON bundle. */
    public static String toJson(List<HistoryCommand> items, SecretVisibilityProfile visibility) {
        SecretVisibilityProfile policy = visibility == null ? SecretVisibilityProfile.REDACTED : visibility;
        List<Map<String, Object>> records = new ArrayList<>();
        if (items != null) {
            for (HistoryCommand item : items) {
                if (item != null) {
                    records.add(toRecord(item, policy));
                }
            }
        }
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", "cryptocarver-history-export-v1");
        bundle.put("secretVisibility", policy.name());
        bundle.put("recordCount", records.size());
        bundle.put("records", records);
        return gson().toJson(bundle);
    }

    private static Map<String, Object> toRecord(HistoryCommand item, SecretVisibilityProfile policy) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("format", "cryptocarver-history-record-v1");
        record.put("operation", item.getOperation());
        record.put("timestamp", item.getTimestamp());
        record.put("secretVisibility", policy.name());
        record.put("details", visibleDetails(item.getStructuredDetails(), policy));
        return record;
    }

    private static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    private static List<Map<String, Object>> visibleDetails(List<OperationDetail> details, SecretVisibilityProfile policy) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (details == null) {
            return result;
        }
        for (OperationDetail detail : details) {
            if (detail == null || (policy == SecretVisibilityProfile.REDACTED
                    && detail.classification() == OperationDetail.Classification.SECRET)) {
                continue;
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", detail.name());
            value.put("classification", detail.classification().name());
            value.put("format", detail.format());
            value.put("value", visibleValue(detail, policy));
            result.add(value);
        }
        return result;
    }

    private static String visibleValue(OperationDetail detail, SecretVisibilityProfile policy) {
        if (policy == SecretVisibilityProfile.MASKED && detail.classification() != OperationDetail.Classification.PUBLIC) {
            return "***MASKED***";
        }
        if (policy == SecretVisibilityProfile.REDACTED && detail.classification() == OperationDetail.Classification.SENSITIVE) {
            return "***MASKED***";
        }
        return detail.value() == null ? "" : detail.value();
    }
}
