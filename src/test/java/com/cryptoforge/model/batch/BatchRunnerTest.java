package com.cryptoforge.model.batch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BatchRunnerTest {
    @Test void isolatesFailuresAndReturnsEveryAttemptedRow() {
        BatchRunner.Report report = BatchRunner.run(List.of(Map.of("v", "ok"), Map.of("v", "bad")), input -> {
            if ("bad".equals(input.get("v"))) throw new IllegalArgumentException("bad input");
            return Map.of("result", input.get("v").toUpperCase());
        }, () -> false);
        assertEquals(1, report.succeeded()); assertEquals(1, report.failed()); assertEquals("OK", report.results().get(0).output().get("result"));
    }
    @Test void observesCancellationBeforeTheNextRow() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        BatchRunner.Report report = BatchRunner.run(List.of(Map.of("v", "1"), Map.of("v", "2")), input -> { cancelled.set(true); return input; }, cancelled::get);
        assertTrue(report.cancelled()); assertEquals(1, report.results().size());
    }
    @Test void exportsResultsWithStatusAndEscapedValues() {
        BatchRunner.Report report = BatchRunner.run(List.of(Map.of("v", "a,b")), input -> Map.of("result", "x\"y"), () -> false);
        assertTrue(BatchOutputCodec.toCsv(report).contains("\"a,b\""));
        assertTrue(BatchOutputCodec.toJsonLines(report).contains("\"status\":\"ok\""));
    }
}
