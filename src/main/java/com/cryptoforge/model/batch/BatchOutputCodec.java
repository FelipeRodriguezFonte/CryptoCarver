package com.cryptoforge.model.batch;

import com.google.gson.Gson;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable, spreadsheet-friendly exporters for batch execution reports. */
public final class BatchOutputCodec {
    private BatchOutputCodec() { }

    public static String toJsonLines(BatchRunner.Report report) {
        if (report == null || report.results().isEmpty()) return "";
        Gson gson = new Gson(); StringBuilder output = new StringBuilder();
        for (BatchRunner.RowResult row : report.results()) {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("row", row.rowNumber()); object.put("status", row.succeeded() ? "ok" : "error");
            object.put("input", row.input()); object.put("output", row.output());
            if (!row.succeeded()) object.put("error", row.error());
            output.append(gson.toJson(object)).append('\n');
        }
        return output.toString();
    }

    public static String toCsv(BatchRunner.Report report) {
        if (report == null || report.results().isEmpty()) return "row,status,error\n";
        Set<String> inputKeys = new LinkedHashSet<>(), outputKeys = new LinkedHashSet<>();
        report.results().forEach(row -> { inputKeys.addAll(row.input().keySet()); outputKeys.addAll(row.output().keySet()); });
        StringBuilder output = new StringBuilder("row,status,error");
        inputKeys.forEach(key -> output.append(',').append(escape("input_" + key)));
        outputKeys.forEach(key -> output.append(',').append(escape("output_" + key)));
        output.append('\n');
        for (BatchRunner.RowResult row : report.results()) {
            output.append(row.rowNumber()).append(',').append(row.succeeded() ? "ok" : "error").append(',').append(escape(row.error()));
            inputKeys.forEach(key -> output.append(',').append(escape(row.input().get(key))));
            outputKeys.forEach(key -> output.append(',').append(escape(row.output().get(key))));
            output.append('\n');
        }
        return output.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
