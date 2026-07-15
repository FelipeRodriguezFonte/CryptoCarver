package com.cryptoforge.model.batch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Executes independent batch rows while retaining an auditable result for every attempted row. */
public final class BatchRunner {
    private BatchRunner() { }

    @FunctionalInterface public interface RowOperation {
        Map<String, String> execute(Map<String, String> input) throws Exception;
    }
    @FunctionalInterface public interface ProgressListener {
        void completed(int completedRows, int totalRows);
    }
    public record RowResult(int rowNumber, Map<String, String> input, Map<String, String> output, String error) {
        public boolean succeeded() { return error == null; }
    }
    public record Report(List<RowResult> results, boolean cancelled) {
        public long succeeded() { return results.stream().filter(RowResult::succeeded).count(); }
        public long failed() { return results.size() - succeeded(); }
    }

    public static Report run(List<Map<String, String>> rows, RowOperation operation, BooleanSupplier cancellationRequested) {
        return run(rows, operation, cancellationRequested, null);
    }

    public static Report run(List<Map<String, String>> rows, RowOperation operation, BooleanSupplier cancellationRequested,
            ProgressListener progressListener) {
        if (operation == null) throw new IllegalArgumentException("Batch operation is required");
        List<RowResult> results = new ArrayList<>();
        if (rows == null) return new Report(List.of(), false);
        for (int index = 0; index < rows.size(); index++) {
            if (cancellationRequested != null && cancellationRequested.getAsBoolean()) return new Report(List.copyOf(results), true);
            Map<String, String> input = Map.copyOf(rows.get(index) == null ? Map.of() : rows.get(index));
            try {
                Map<String, String> output = operation.execute(input);
                results.add(new RowResult(index + 1, input, Map.copyOf(output == null ? Map.of() : new LinkedHashMap<>(output)), null));
            } catch (Exception e) {
                String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
                results.add(new RowResult(index + 1, input, Map.of(), message));
            }
            if (progressListener != null) progressListener.completed(index + 1, rows.size());
        }
        return new Report(List.copyOf(results), false);
    }
}
