package com.cryptoforge.utils;

import com.cryptoforge.model.HistoryItem;
import com.cryptoforge.model.OperationDetail;
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
            this.key = key;
            this.value1 = value1 == null ? "" : value1;
            this.value2 = value2 == null ? "" : value2;
            this.isDifferent = !this.value1.equals(this.value2);
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
