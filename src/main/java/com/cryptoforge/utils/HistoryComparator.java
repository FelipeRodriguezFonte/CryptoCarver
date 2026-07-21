package com.cryptoforge.utils;

import com.cryptoforge.model.HistoryItem;
import com.cryptoforge.model.OperationDetail;
import com.cryptoforge.model.SecretVisibility;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

public class HistoryComparator {

    public static class DiffEntry {
        public final String key;
        public final String value1;
        public final String value2;
        public final boolean isDifferent;

        public DiffEntry(String key, String value1, String value2) {
            this(key, value1, value2, !java.util.Objects.equals(value1, value2));
        }

        private DiffEntry(String key, String value1, String value2, boolean isDifferent) {
            this.key = key;
            this.value1 = value1 == null ? "" : value1;
            this.value2 = value2 == null ? "" : value2;
            this.isDifferent = isDifferent;
        }
    }

    public static List<DiffEntry> compare(HistoryItem item1, HistoryItem item2) {
        Map<String, String> map1 = extractMap(item1);
        Map<String, String> map2 = extractMap(item2);

        List<DiffEntry> diffs = new ArrayList<>();
        List<String> allKeys = new ArrayList<>();

        for (String key : map1.keySet()) {
            if (!allKeys.contains(key)) allKeys.add(key);
        }
        for (String key : map2.keySet()) {
            if (!allKeys.contains(key)) allKeys.add(key);
        }

        for (String key : allKeys) {
            diffs.add(new DiffEntry(key, map1.get(key), map2.get(key)));
        }

        return diffs;
    }

    /**
     * Compares structured history details under a display policy.  In masked
     * and redacted modes raw UI state is excluded because it has no sensitivity
     * classification. Diff highlighting still reflects the underlying values,
     * without showing them.
     */
    public static List<DiffEntry> compare(HistoryItem item1, HistoryItem item2, SecretVisibility visibility) {
        SecretVisibility policy = visibility == null ? SecretVisibility.REDACTED : visibility;
        Map<String, VisibleValue> map1 = extractVisibleDetails(item1, policy);
        Map<String, VisibleValue> map2 = extractVisibleDetails(item2, policy);
        List<String> allKeys = new ArrayList<>();
        map1.keySet().forEach(key -> { if (!allKeys.contains(key)) allKeys.add(key); });
        map2.keySet().forEach(key -> { if (!allKeys.contains(key)) allKeys.add(key); });

        List<DiffEntry> diffs = new ArrayList<>();
        for (String key : allKeys) {
            VisibleValue first = map1.get(key);
            VisibleValue second = map2.get(key);
            String firstRaw = first == null ? null : first.raw();
            String secondRaw = second == null ? null : second.raw();
            diffs.add(new DiffEntry(key,
                    first == null ? "" : first.display(),
                    second == null ? "" : second.display(),
                    !java.util.Objects.equals(firstRaw, secondRaw)));
        }
        return diffs;
    }

    private record VisibleValue(String raw, String display) { }

    private static Map<String, VisibleValue> extractVisibleDetails(HistoryItem item, SecretVisibility policy) {
        Map<String, VisibleValue> values = new LinkedHashMap<>();
        if (item == null || item.getStructuredDetails() == null) {
            return values;
        }
        for (OperationDetail detail : item.getStructuredDetails()) {
            if (detail == null || (policy == SecretVisibility.REDACTED
                    && detail.classification() == OperationDetail.Classification.SECRET)) {
                continue;
            }
            String raw = detail.value() == null ? "" : detail.value();
            String display = raw;
            if (policy == SecretVisibility.MASKED && detail.classification() != OperationDetail.Classification.PUBLIC) {
                display = "***MASKED***";
            } else if (policy == SecretVisibility.REDACTED
                    && detail.classification() == OperationDetail.Classification.SENSITIVE) {
                display = "***MASKED***";
            }
            values.put(detail.name(), new VisibleValue(raw, display));
        }
        return values;
    }

    private static Map<String, String> extractMap(HistoryItem item) {
        Map<String, String> map = new LinkedHashMap<>();

        // Add uiState first
        if (item.getUiState() != null) {
            for (Map.Entry<String, Object> entry : item.getUiState().entrySet()) {
                map.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }

        // Add structured details, overriding uiState if same key exists
        if (item.getStructuredDetails() != null) {
            for (OperationDetail detail : item.getStructuredDetails()) {
                map.put(detail.name(), detail.value());
            }
        }

        return map;
    }
}
