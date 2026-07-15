package com.cryptoforge.model.batch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded CSV and JSONL reader for laboratory batch operations. */
public final class BatchInputCodec {
    public static final int MAX_ROWS = 10_000;
    private static final int MAX_CELL_CHARS = 100_000;
    private BatchInputCodec() { }

    public static List<Map<String, String>> parseCsv(String csv) {
        List<List<String>> records = parseDelimited(csv == null ? "" : csv);
        if (records.isEmpty()) return List.of();
        List<String> header = records.remove(0);
        validateHeader(header);
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<String> record : records) {
            if (record.size() == 1 && record.get(0).isEmpty()) continue;
            if (record.size() != header.size()) throw new IllegalArgumentException("CSV row has " + record.size() + " columns; expected " + header.size());
            if (rows.size() >= MAX_ROWS) throw new IllegalArgumentException("Batch exceeds " + MAX_ROWS + " rows");
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), validateCell(record.get(i)));
            rows.add(Map.copyOf(row));
        }
        return List.copyOf(rows);
    }

    public static List<Map<String, String>> parseJsonLines(String jsonl) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (jsonl == null || jsonl.isBlank()) return List.of();
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() { }.getType();
        int lineNumber = 0;
        for (String line : jsonl.split("\\R")) {
            lineNumber++;
            if (line.isBlank()) continue;
            Map<String, Object> raw;
            try { raw = gson.fromJson(line, mapType); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid JSONL at line " + lineNumber, e); }
            if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("JSONL line " + lineNumber + " must be an object with values");
            if (rows.size() >= MAX_ROWS) throw new IllegalArgumentException("Batch exceeds " + MAX_ROWS + " rows");
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                if (key.isEmpty()) throw new IllegalArgumentException("JSONL line " + lineNumber + " contains an empty field name");
                if (entry.getValue() instanceof Map || entry.getValue() instanceof List) throw new IllegalArgumentException("JSONL line " + lineNumber + " field " + key + " must be scalar");
                row.put(key, validateCell(entry.getValue() == null ? "" : String.valueOf(entry.getValue())));
            }
            rows.add(Map.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static void validateHeader(List<String> header) {
        if (header.isEmpty() || header.stream().allMatch(String::isBlank)) throw new IllegalArgumentException("CSV header is required");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String name : header) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("CSV header contains an empty field name");
            if (!seen.add(name)) throw new IllegalArgumentException("CSV header contains duplicate field: " + name);
        }
    }

    private static String validateCell(String value) {
        if (value.length() > MAX_CELL_CHARS) throw new IllegalArgumentException("Batch value exceeds " + MAX_CELL_CHARS + " characters");
        return value;
    }

    private static List<List<String>> parseDelimited(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { value.append('"'); i++; }
                    else quoted = false;
                } else value.append(current);
            } else if (current == '"') {
                if (value.length() != 0) throw new IllegalArgumentException("Unexpected quote in CSV field");
                quoted = true;
            } else if (current == ',') { record.add(value.toString()); value.setLength(0); }
            else if (current == '\n' || current == '\r') {
                if (current == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                record.add(value.toString()); value.setLength(0); records.add(record); record = new ArrayList<>();
            } else value.append(current);
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted CSV field");
        if (value.length() > 0 || !record.isEmpty()) { record.add(value.toString()); records.add(record); }
        return records;
    }
}
