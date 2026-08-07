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
        Map<String, String> execute(int rowNumber, Map<String, String> input) throws Exception;
    }
    @FunctionalInterface public interface ProgressListener {
        void completed(int completedRows, int totalRows);
    }
    public record RowResult(int rowNumber, Map<String, String> input, Map<String, String> output, String error) {
        public boolean succeeded() { return error == null; }
    }
    /**
     * A cancelled report may retain completed row results for diagnostics, but
     * those results are not a successful batch outcome and must not be published
     * or exported by consumers.
     */
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
        java.util.concurrent.atomic.AtomicBoolean cancellationStarted = new java.util.concurrent.atomic.AtomicBoolean(false);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(
                Math.min(4, Runtime.getRuntime().availableProcessors()));
        try {
            List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < totalRows; index++) {
                final int i = index;
                final Map<String, String> row = rows.get(i);

                tasks.add(() -> {
                    if (cancellationStarted.get()
                            || cancellationRequested != null && cancellationRequested.getAsBoolean()) return null;

                    Map<String, String> input = Map.copyOf(row == null ? Map.of() : row);
                    RowResult result;
                    try {
                        if (cancellationStarted.get()
                                || cancellationRequested != null && cancellationRequested.getAsBoolean()
                                || Thread.currentThread().isInterrupted()) {
                            if (Thread.currentThread().isInterrupted()) Thread.currentThread().interrupt();
                            return null;
                        }

                        Map<String, String> output = operation.execute(i + 1, input);
                        if (Thread.currentThread().isInterrupted()) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                        result = new RowResult(i + 1, input, Map.copyOf(output == null ? Map.of() : new LinkedHashMap<>(output)), null);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
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
            boolean runnerInterrupted = false;
            while (!executor.isTerminated()) {
                if (!cancelled && cancellationRequested != null && cancellationRequested.getAsBoolean()) {
                    cancellationStarted.set(true);
                    executor.shutdownNow();
                    cancelled = true;
                }
                try {
                    executor.awaitTermination(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    cancellationStarted.set(true);
                    executor.shutdownNow();
                    cancelled = true;
                    runnerInterrupted = true;
                }
            }
            if (runnerInterrupted) Thread.currentThread().interrupt();
            // A worker may have raised the cancellation flag just before the
            // executor terminated. Treat that run as cancelled as well, but
            // preserve the collected RowResults so callers can inspect the error.
            if (!cancelled && cancellationRequested != null && cancellationRequested.getAsBoolean()) {
                cancelled = true;
            }
            if (cancelled) return new Report(filterNonNull(results), true);
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
