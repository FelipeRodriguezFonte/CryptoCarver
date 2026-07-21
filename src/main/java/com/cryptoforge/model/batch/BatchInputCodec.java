package com.cryptoforge.model.batch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded CSV and JSONL reader for laboratory batch operations. */
public final class BatchInputCodec {
    public static final int MAX_ROWS = 10_000;
    private static final int MAX_CELL_CHARS = 100_000;
    public static final long MAX_TOTAL_CHARS = 100_000_000L; // ~100MB input limit

    private BatchInputCodec() { }

    private static class BoundedReader extends Reader {
        private final Reader in;
        private final long maxChars;
        private long readChars = 0;

        BoundedReader(Reader in, long maxChars) { this.in = in; this.maxChars = maxChars; }
        @Override public int read(char[] cbuf, int off, int len) throws IOException {
            int r = in.read(cbuf, off, len);
            if (r > 0) {
                readChars += r;
                if (readChars > maxChars) throw new IOException("Maximum input size exceeded (" + maxChars + " chars)");
            }
            return r;
        }
        @Override public void close() throws IOException { in.close(); }
        @Override public boolean markSupported() { return in.markSupported(); }
        @Override public void mark(int readAheadLimit) throws IOException { in.mark(readAheadLimit); }
        @Override public void reset() throws IOException { in.reset(); }
    }

    private static String readLineBounded(BufferedReader br, int limit) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = br.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') {
                sb.append((char) c);
                if (sb.length() > limit) throw new IOException("Line length exceeds maximum allowed (" + limit + " chars)");
            }
        }
        if (sb.length() == 0 && c == -1) return null;
        return sb.toString();
    }

    public static List<Map<String, String>> parseCsv(String csv) { return parseCsv(new StringReader(csv == null ? "" : csv), MAX_ROWS); }
    public static List<Map<String, String>> parseCsv(String csv, int limit) { return parseCsv(new StringReader(csv == null ? "" : csv), limit); }

    public static List<Map<String, String>> parseCsv(Reader reader, int limit) {
        try {
            Reader safeReader = new BoundedReader(reader, MAX_TOTAL_CHARS);
            if (!safeReader.markSupported()) safeReader = new BufferedReader(safeReader);
            List<List<String>> records = parseDelimited(safeReader, limit + 1);
            if (records.isEmpty()) return List.of();
            List<String> header = records.remove(0);
            validateHeader(header);
            List<Map<String, String>> rows = new ArrayList<>();
            for (List<String> record : records) {
                if (record.size() == 1 && record.get(0).isEmpty()) continue;
                if (record.size() != header.size()) throw new IllegalArgumentException("CSV row has " + record.size() + " columns; expected " + header.size());
                if (rows.size() >= limit) break;
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) row.put(header.get(i), validateCell(record.get(i)));
                rows.add(Map.copyOf(row));
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read CSV", e);
        }
    }

    public static List<Map<String, String>> parseJsonLines(String jsonl) { return parseJsonLines(new StringReader(jsonl == null ? "" : jsonl), MAX_ROWS); }
    public static List<Map<String, String>> parseJsonLines(String jsonl, int limit) { return parseJsonLines(new StringReader(jsonl == null ? "" : jsonl), limit); }

    public static List<Map<String, String>> parseJsonLines(Reader reader, int limit) {
        List<Map<String, String>> rows = new ArrayList<>();
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() { }.getType();
        int lineNumber = 0;
        try (BufferedReader br = new BufferedReader(new BoundedReader(reader, MAX_TOTAL_CHARS))) {
            String line;
            while ((line = readLineBounded(br, MAX_CELL_CHARS * 50)) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                Map<String, Object> raw;
                try { raw = gson.fromJson(line, mapType); }
                catch (Exception e) { throw new IllegalArgumentException("Invalid JSONL at line " + lineNumber, e); }
                if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("JSONL line " + lineNumber + " must be an object with values");
                if (rows.size() >= limit) break;
                Map<String, String> row = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : raw.entrySet()) {
                    String key = entry.getKey() == null ? "" : entry.getKey().trim();
                    if (key.isEmpty()) throw new IllegalArgumentException("JSONL line " + lineNumber + " contains an empty field name");
                    if (entry.getValue() instanceof Map || entry.getValue() instanceof List) throw new IllegalArgumentException("JSONL line " + lineNumber + " field " + key + " must be scalar");
                    row.put(key, validateCell(entry.getValue() == null ? "" : String.valueOf(entry.getValue())));
                }
                rows.add(Map.copyOf(row));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read JSONL", e);
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

    private static List<List<String>> parseDelimited(Reader reader, int limit) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        int current;
        while ((current = reader.read()) != -1) {
            if (quoted) {
                if (current == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        value.append('"');
                        if (value.length() > MAX_CELL_CHARS) throw new IllegalArgumentException("Batch value exceeds maximum characters");
                    } else { quoted = false; if (next != -1) reader.reset(); }
                } else {
                    value.append((char) current);
                    if (value.length() > MAX_CELL_CHARS) throw new IllegalArgumentException("Batch value exceeds maximum characters");
                }
            } else if (current == '"') {
                if (value.length() != 0) throw new IllegalArgumentException("Unexpected quote in CSV field");
                quoted = true;
            } else if (current == ',') { record.add(value.toString()); value.setLength(0); }
            else if (current == '\n' || current == '\r') {
                if (current == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.reset();
                }
                record.add(value.toString()); value.setLength(0); records.add(record); record = new ArrayList<>();
                if (records.size() >= limit) return records;
            } else {
                value.append((char) current);
                if (value.length() > MAX_CELL_CHARS) throw new IllegalArgumentException("Batch value exceeds maximum characters");
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted CSV field");
        if (value.length() > 0 || !record.isEmpty()) { record.add(value.toString()); records.add(record); }
        return records;
    }
}
