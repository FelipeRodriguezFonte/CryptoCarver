package com.cryptocarver.model.batch;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BatchRunnerTest {

    @Test
    void testConcurrentStableOrder() {
        int count = 1000;
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(Map.of("id", String.valueOf(i)));
        }

        BatchRunner.Report report = BatchRunner.run(rows, input -> {
            // Introduce variable sleep to ensure threads finish out of order
            int id = Integer.parseInt(input.get("id"));
            try {
                Thread.sleep((id % 10) * 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of("processed_id", input.get("id"));
        }, () -> false);

        assertNotNull(report);
        assertEquals(count, report.results().size());
        assertEquals(count, report.succeeded());

        for (int i = 0; i < count; i++) {
            BatchRunner.RowResult result = report.results().get(i);
            assertEquals(i + 1, result.rowNumber());
            assertEquals(String.valueOf(i), result.input().get("id"));
            assertEquals(String.valueOf(i), result.output().get("processed_id"));
        }
    }

    @Test
    void testCancellation() {
        int count = 1000;
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(Map.of("id", String.valueOf(i)));
        }

        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

        BatchRunner.Report report = BatchRunner.run(rows, input -> {
            int id = Integer.parseInt(input.get("id"));
            if (id > 5) {
                cancelled.set(true); // Trigger cancellation mid-flight
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Map.of("processed_id", input.get("id"));
        }, cancelled::get);

        assertNotNull(report);
        assertTrue(report.cancelled(), "Report should be marked as cancelled");
        assertTrue(report.results().isEmpty(), "No partial results should be returned on cancellation");
    }
    @Test
    void testCancellationWaitsForWorkers() {
        List<Map<String, String>> rows = List.of(Map.of("id", "1"));
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean workerFinished = new java.util.concurrent.atomic.AtomicBoolean(false);

        BatchRunner.Report report = BatchRunner.run(rows, input -> {
            cancelled.set(true); // Cancel while inside the worker
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignored */ }
            workerFinished.set(true);
            return Map.of("processed_id", "1");
        }, cancelled::get);

        assertTrue(report.cancelled());
        assertTrue(workerFinished.get(), "BatchRunner should block until the bad worker actually finishes");
    }
}
