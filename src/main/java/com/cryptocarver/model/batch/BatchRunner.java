package com.cryptocarver.model.batch;

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
        if (rows == null || rows.isEmpty()) return new Report(List.of(), false);

        int totalRows = rows.size();
        RowResult[] results = new RowResult[totalRows];
        java.util.concurrent.atomic.AtomicInteger completedRows = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(
                Math.min(4, Runtime.getRuntime().availableProcessors()));
        try {
            List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < totalRows; index++) {
                final int i = index;
                final Map<String, String> row = rows.get(i);

                tasks.add(() -> {
                    if (cancellationRequested != null && cancellationRequested.getAsBoolean()) return null;

                    Map<String, String> input = Map.copyOf(row == null ? Map.of() : row);
                    RowResult result;
                    try {
                        Map<String, String> output = operation.execute(input);
                        result = new RowResult(i + 1, input, Map.copyOf(output == null ? Map.of() : new LinkedHashMap<>(output)), null);
                    } catch (Exception e) {
                        String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
                        result = new RowResult(i + 1, input, Map.of(), message);
                    }

                    results[i] = result;

                    int currentCompleted = completedRows.incrementAndGet();
                    if (progressListener != null) progressListener.completed(currentCompleted, totalRows);

                    return null;
                });
            }

            for (java.util.concurrent.Callable<Void> task : tasks) {
                executor.submit(task);
            }

            executor.shutdown();
            boolean cancelled = false;
            while (!executor.isTerminated()) {
                if (!cancelled && cancellationRequested != null && cancellationRequested.getAsBoolean()) {
                    executor.shutdownNow();
                    cancelled = true;
                }
                try {
                    executor.awaitTermination(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    cancelled = true;
                    Thread.currentThread().interrupt();
                }
            }
            // A worker may have raised the cancellation flag just before the
            // executor terminated.  Treat that run as cancelled as well: a
            // caller must never receive an exportable partial report.
            if (!cancelled && cancellationRequested != null && cancellationRequested.getAsBoolean()) {
                cancelled = true;
            }
            if (cancelled) return new Report(List.of(), true);
        } finally {
            executor.shutdownNow();
        }

        return new Report(filterNonNull(results), false);
    }

    private static List<RowResult> filterNonNull(RowResult[] array) {
        List<RowResult> list = new ArrayList<>();
        for (RowResult r : array) {
            if (r != null) list.add(r);
        }
        return List.copyOf(list);
    }
}
