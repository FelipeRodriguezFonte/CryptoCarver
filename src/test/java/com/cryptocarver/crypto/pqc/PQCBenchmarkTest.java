package com.cryptocarver.crypto.pqc;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import static org.junit.jupiter.api.Assertions.*;

class PQCBenchmarkTest {

    @BeforeAll
    static void initJFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    @Test
    void testCancelBenchmarkAndPartialResults() throws Exception {
        PQCBenchmark benchmark = new PQCBenchmark("ML-KEM-512", 1000);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();

        Thread t = new Thread(() -> {
            started.countDown();
            try {
                resultRef.set(benchmark.call());
            } catch (Exception e) {
            } finally {
                finished.countDown();
            }
        });

        t.start();
        started.await();

        Thread.sleep(50);

        benchmark.cancel();
        t.interrupt();

        finished.await(5, TimeUnit.SECONDS);

        String partial = benchmark.getPartialResult();

        assertNotNull(partial);
        assertTrue(partial.contains("Benchmark canceled after ") || partial.contains("Benchmark canceled before completing any iterations."));
        if (!partial.contains("before completing any iterations")) {
            assertTrue(partial.contains("p95:"), "Should contain p95 metrics");
        }
    }
}
