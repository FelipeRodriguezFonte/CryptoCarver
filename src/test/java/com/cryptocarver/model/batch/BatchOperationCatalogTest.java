package com.cryptocarver.model.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BatchOperationCatalogTest {

    @Test
    @DisplayName("Catalog lists all 11 initial permitted operations in exact expected order")
    void testCatalogAvailableOperations() {
        List<String> ops = BatchOperationCatalog.getAvailableOperations();
        assertNotNull(ops);
        assertEquals(11, ops.size());

        assertEquals("SHA-256 (UTF-8 → Hex)", ops.get(0));
        assertEquals("SHA-384 (UTF-8 → Hex)", ops.get(1));
        assertEquals("SHA-512 (UTF-8 → Hex)", ops.get(2));

        assertEquals("UTF-8 → Hexadecimal", ops.get(3));
        assertEquals("Hexadecimal → UTF-8", ops.get(4));

        assertEquals("UTF-8 → Base64", ops.get(5));
        assertEquals("Base64 → UTF-8", ops.get(6));

        assertEquals("Hexadecimal → Base64", ops.get(7));
        assertEquals("Base64 → Hexadecimal", ops.get(8));

        assertEquals("UTF-8 → Base64URL", ops.get(9));
        assertEquals("Base64URL → UTF-8", ops.get(10));
    }

    @Test
    @DisplayName("Strict allowlist rejects heuristic/partial matching and non-permitted operations")
    void testStrictAllowlistRejections() {
        // Required forbidden examples
        assertFalse(BatchOperationCatalog.isSupportedOperation("AES-GCM Base64 secret"));
        assertFalse(BatchOperationCatalog.isSupportedOperation("custom SHA-256"));
        assertFalse(BatchOperationCatalog.isSupportedOperation("RSA Hex"));
        assertFalse(BatchOperationCatalog.isSupportedOperation("Base64 payload"));

        // Null and blank rejections
        assertFalse(BatchOperationCatalog.isSupportedOperation(null));
        assertFalse(BatchOperationCatalog.isSupportedOperation(""));
        assertFalse(BatchOperationCatalog.isSupportedOperation("   "));

        // Execution of rejected operations throws IllegalArgumentException with clean message
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                BatchOperationCatalog.execute("AES-GCM Base64 secret", Map.of("in", "val"), "in", "out"));
        assertEquals("Unsupported batch operation: AES-GCM Base64 secret", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                BatchOperationCatalog.execute("custom SHA-256", Map.of("in", "val"), "in", "out"));
        assertEquals("Unsupported batch operation: custom SHA-256", ex2.getMessage());
    }

    @Test
    @DisplayName("Explicit historical aliases resolve to canonical names")
    void testExplicitAliasesResolution() {
        assertEquals(BatchOperationCatalog.SHA256_UTF8_HEX, BatchOperationCatalog.resolveOperationName("SHA-256"));
        assertEquals(BatchOperationCatalog.SHA384_UTF8_HEX, BatchOperationCatalog.resolveOperationName("SHA-384"));
        assertEquals(BatchOperationCatalog.SHA512_UTF8_HEX, BatchOperationCatalog.resolveOperationName("SHA-512"));
        assertEquals(BatchOperationCatalog.UTF8_TO_HEX, BatchOperationCatalog.resolveOperationName("utf-8 → hex"));
        assertEquals(BatchOperationCatalog.UTF8_TO_HEX, BatchOperationCatalog.resolveOperationName("utf-8 -> hex"));
        assertEquals(BatchOperationCatalog.HEX_TO_UTF8, BatchOperationCatalog.resolveOperationName("hex → utf-8"));
        assertEquals(BatchOperationCatalog.HEX_TO_UTF8, BatchOperationCatalog.resolveOperationName("hex -> utf-8"));
        assertEquals(BatchOperationCatalog.UTF8_TO_BASE64URL, BatchOperationCatalog.resolveOperationName("utf-8 -> base64url"));
        assertEquals(BatchOperationCatalog.BASE64URL_TO_UTF8, BatchOperationCatalog.resolveOperationName("base64url -> utf-8"));
        assertEquals(BatchOperationCatalog.HEX_TO_BASE64, BatchOperationCatalog.resolveOperationName("hex -> base64"));
        assertEquals(BatchOperationCatalog.BASE64_TO_HEX, BatchOperationCatalog.resolveOperationName("base64 -> hex"));
    }

    @Test
    @DisplayName("Unit tests: Hash operations (SHA-256, SHA-384, SHA-512)")
    void testHashOperations() throws Exception {
        Map<String, String> row = Map.of("in", "Hello World");

        // SHA-256
        Map<String, String> res256 = BatchOperationCatalog.execute("SHA-256 (UTF-8 → Hex)", row, "in", "out");
        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e", res256.get("out"));

        // SHA-384
        Map<String, String> res384 = BatchOperationCatalog.execute("SHA-384 (UTF-8 → Hex)", row, "in", "out");
        assertEquals("99514329186b2f6ae4a1329e7ee6c610a729636335174ac6b740f9028396fcc803d0e93863a7c3d90f86beee782f4f3f", res384.get("out"));

        // SHA-512
        Map<String, String> res512 = BatchOperationCatalog.execute("SHA-512 (UTF-8 → Hex)", row, "in", "out");
        assertEquals("2c74fd17edafd80e8447b0d46741ee243b7eb74dd2149a0ab1b9246fb30382f27e853d8585719e0e67cbda0daa8f51671064615d645ae27acb15bfb1447f459b", res512.get("out"));
    }

    @Test
    @DisplayName("Unit tests: Conversion UTF-8 ↔ Hexadecimal")
    void testUtf8HexConversions() throws Exception {
        Map<String, String> rowText = Map.of("data", "CryptoCarver");

        // UTF-8 -> Hex
        Map<String, String> resHex = BatchOperationCatalog.execute("UTF-8 → Hexadecimal", rowText, "data", "res");
        assertEquals("43727970746f436172766572", resHex.get("res"));

        // Hex -> UTF-8
        Map<String, String> rowHex = Map.of("data", "43727970746f436172766572");
        Map<String, String> resText = BatchOperationCatalog.execute("Hexadecimal → UTF-8", rowHex, "data", "res");
        assertEquals("CryptoCarver", resText.get("res"));
    }

    @Test
    @DisplayName("Unit tests: Conversion UTF-8 ↔ Base64")
    void testUtf8Base64Conversions() throws Exception {
        Map<String, String> rowText = Map.of("data", "Batch Runner 2026");

        // UTF-8 -> Base64
        Map<String, String> resB64 = BatchOperationCatalog.execute("UTF-8 → Base64", rowText, "data", "res");
        assertEquals("QmF0Y2ggUnVubmVyIDIwMjY=", resB64.get("res"));

        // Base64 -> UTF-8
        Map<String, String> rowB64 = Map.of("data", "QmF0Y2ggUnVubmVyIDIwMjY=");
        Map<String, String> resText = BatchOperationCatalog.execute("Base64 → UTF-8", rowB64, "data", "res");
        assertEquals("Batch Runner 2026", resText.get("res"));
    }

    @Test
    @DisplayName("Unit tests: Conversion Hexadecimal ↔ Base64")
    void testHexBase64Conversions() throws Exception {
        Map<String, String> rowHex = Map.of("col", "DEADBEEF");

        // Hex -> Base64
        Map<String, String> resB64 = BatchOperationCatalog.execute("Hexadecimal → Base64", rowHex, "col", "out");
        assertEquals("3q2+7w==", resB64.get("out"));

        // Base64 -> Hex
        Map<String, String> rowB64 = Map.of("col", "3q2+7w==");
        Map<String, String> resHex = BatchOperationCatalog.execute("Base64 → Hexadecimal", rowB64, "col", "out");
        assertEquals("deadbeef", resHex.get("out"));
    }

    @Test
    @DisplayName("Unit tests: Base64URL encode/decode")
    void testBase64UrlConversions() throws Exception {
        Map<String, String> rowText = Map.of("col", "subject?name=value&type=test");

        // UTF-8 -> Base64URL
        Map<String, String> resUrl = BatchOperationCatalog.execute("UTF-8 → Base64URL", rowText, "col", "out");
        assertEquals("c3ViamVjdD9uYW1lPXZhbHVlJnR5cGU9dGVzdA", resUrl.get("out"));

        // Base64URL -> UTF-8
        Map<String, String> rowUrl = Map.of("col", "c3ViamVjdD9uYW1lPXZhbHVlJnR5cGU9dGVzdA");
        Map<String, String> resText = BatchOperationCatalog.execute("Base64URL → UTF-8", rowUrl, "col", "out");
        assertEquals("subject?name=value&type=test", resText.get("out"));
    }

    @Test
    @DisplayName("Error sanitization: Corrupt inputs return exact fixed messages without leaking internal exceptions")
    void testErrorSanitizationExactMessages() {
        // Corrupt Base64URL
        IllegalArgumentException exB64Url = assertThrows(IllegalArgumentException.class, () ->
                BatchOperationCatalog.execute("Base64URL → UTF-8", Map.of("data", "!!!CORRUPT_BASE64_URL!!!"), "data", "res"));
        assertEquals("Invalid Base64URL format", exB64Url.getMessage());
        assertNull(exB64Url.getCause(), "No cause should be attached to sanitized row error");

        // Corrupt Hexadecimal (odd length)
        IllegalArgumentException exHexOdd = assertThrows(IllegalArgumentException.class, () ->
                BatchOperationCatalog.execute("Hexadecimal → UTF-8", Map.of("data", "437"), "data", "res"));
        assertEquals("Invalid Hexadecimal format", exHexOdd.getMessage());

        // Corrupt Hexadecimal (invalid character)
        IllegalArgumentException exHexChar = assertThrows(IllegalArgumentException.class, () ->
                BatchOperationCatalog.execute("Hexadecimal → UTF-8", Map.of("data", "43727970746fZZ"), "data", "res"));
        assertEquals("Invalid Hexadecimal format", exHexChar.getMessage());

        // Corrupt Base64
        IllegalArgumentException exB64 = assertThrows(IllegalArgumentException.class, () ->
                BatchOperationCatalog.execute("Base64 → UTF-8", Map.of("data", "!!!NOT_BASE64!!!"), "data", "res"));
        assertEquals("Invalid Base64 format", exB64.getMessage());

        // Missing column
        IllegalArgumentException exMissing = assertThrows(IllegalArgumentException.class, () ->
                BatchOperationCatalog.execute("UTF-8 → Base64", Map.of("other_col", "val"), "missing_col", "out"));
        assertEquals("missing_col field is required", exMissing.getMessage());
    }

    @Test
    @DisplayName("CSV & JSONL: Multi-row execution with configurable input/output column names")
    void testCsvAndJsonlConfigurableColumns() throws Exception {
        String csvData = "src_payload,comment\nHello,first\nWorld,second\n";
        List<Map<String, String>> csvRows = BatchInputCodec.parseCsv(csvData);
        assertEquals(2, csvRows.size());

        BatchRunner.Report csvReport = BatchRunner.run(csvRows,
                (rowNum, row) -> BatchOperationCatalog.execute("UTF-8 → Hexadecimal", row, "src_payload", "hex_output"),
                () -> false);

        assertEquals(2, csvReport.succeeded());
        assertEquals("48656c6c6f", csvReport.results().get(0).output().get("hex_output"));
        assertEquals("576f726c64", csvReport.results().get(1).output().get("hex_output"));

        String jsonlData = "{\"input_str\":\"Crypto\"}\n{\"input_str\":\"Carver\"}\n";
        List<Map<String, String>> jsonlRows = BatchInputCodec.parseJsonLines(jsonlData);
        assertEquals(2, jsonlRows.size());

        BatchRunner.Report jsonlReport = BatchRunner.run(jsonlRows,
                (rowNum, row) -> BatchOperationCatalog.execute("UTF-8 → Base64", row, "input_str", "b64_output"),
                () -> false);

        assertEquals(2, jsonlReport.succeeded());
        assertEquals("Q3J5cHRv", jsonlReport.results().get(0).output().get("b64_output"));
        assertEquals("Q2FydmVy", jsonlReport.results().get(1).output().get("b64_output"));
    }

    @Test
    @DisplayName("Isolated error per row and stop-on-error mode without exposing sensitive data")
    void testIsolatedRowErrorsAndStopOnError() throws Exception {
        List<Map<String, String>> rows = List.of(
                Map.of("hex", "48656c6c6f"), // Valid "Hello"
                Map.of("hex", "INVALID_HEX_STRING"), // Bad row
                Map.of("hex", "576f726c64")  // Valid "World"
        );

        // Stop on error = false: bad row fails with sanitized error, other rows succeed
        BatchRunner.Report reportNoErrorStop = BatchRunner.run(rows,
                (rowNum, row) -> BatchOperationCatalog.execute("Hexadecimal → UTF-8", row, "hex", "txt"),
                () -> false);

        assertFalse(reportNoErrorStop.cancelled());
        assertEquals(2, reportNoErrorStop.succeeded());
        assertEquals(1, reportNoErrorStop.failed());

        BatchRunner.RowResult badResult = reportNoErrorStop.results().get(1);
        assertFalse(badResult.succeeded());
        assertEquals("Invalid Hexadecimal format", badResult.error());

        // Stop on error = true: failure cancels execution
        AtomicBoolean errorFlag = new AtomicBoolean(false);
        BatchRunner.Report reportStopOnError = BatchRunner.run(rows,
                (rowNum, row) -> {
                    try {
                        return BatchOperationCatalog.execute("Hexadecimal → UTF-8", row, "hex", "txt");
                    } catch (Exception e) {
                        errorFlag.set(true);
                        throw e;
                    }
                },
                errorFlag::get);

        assertTrue(reportStopOnError.cancelled());
    }

    @Test
    @DisplayName("Truly concurrent cancellation test using CountDownLatch synchronization in separate worker thread")
    void testConcurrentCancellationWithLatches() throws Exception {
        int rowCount = 50;
        int workerCount = Math.min(4, Runtime.getRuntime().availableProcessors());
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            rows.add(Map.of("val", "data-" + i));
        }

        CountDownLatch workersStartedLatch = new CountDownLatch(workerCount);
        CountDownLatch rowBlockLatch = new CountDownLatch(1);
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        Set<Integer> startedRows = ConcurrentHashMap.newKeySet();
        Set<Integer> startedAfterCancellation = ConcurrentHashMap.newKeySet();
        Set<Integer> transformedRows = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();

        AtomicReference<BatchRunner.Report> reportRef = new AtomicReference<>();
        Thread runnerThread = new Thread(() -> {
            try {
                reportRef.set(BatchRunner.run(rows, (rowNum, row) -> {
                    startedRows.add(rowNum);
                    if (cancellationRequested.get()) startedAfterCancellation.add(rowNum);
                    workersStartedLatch.countDown();
                    rowBlockLatch.await();
                    transformedRows.add(rowNum);
                    return BatchOperationCatalog.execute("UTF-8 → Base64", row, "val", "out");
                }, cancellationRequested::get));
            } catch (Throwable t) {
                workerFailure.set(t);
            }
        }, "batch-cancellation-test-worker");
        runnerThread.setDaemon(true);

        runnerThread.start();

        // Wait until every worker slot has entered a row operation.
        boolean started = workersStartedLatch.await(2, TimeUnit.SECONDS);
        if (!started) {
            cancellationRequested.set(true);
            rowBlockLatch.countDown();
            runnerThread.interrupt();
        }
        assertTrue(started, "All Batch Runner workers failed to start a row operation");

        // Request cancellation while all currently active row operations are blocked.
        cancellationRequested.set(true);
        Set<Integer> startedBeforeCancellation = Set.copyOf(startedRows);

        // Do not swallow InterruptedException: shutdownNow must interrupt the blocked operations.

        // Join worker thread
        runnerThread.join(3000);
        if (runnerThread.isAlive()) {
            rowBlockLatch.countDown();
            runnerThread.interrupt();
            runnerThread.join(1000);
        }
        assertFalse(runnerThread.isAlive(), "Worker thread did not terminate within timeout");
        assertNull(workerFailure.get(), "Batch Runner worker thread failed unexpectedly");

        BatchRunner.Report report = reportRef.get();
        assertNotNull(report, "Batch report should not be null");
        assertTrue(report.cancelled(), "Report must be marked as cancelled");
        assertFalse(startedBeforeCancellation.isEmpty(), "At least one row must have started before cancellation");
        assertTrue(startedAfterCancellation.isEmpty(), "No row operation may start after cancellation");
        assertTrue(transformedRows.stream().allMatch(startedBeforeCancellation::contains),
                "Only rows started before cancellation may transform");
        assertTrue(transformedRows.isEmpty(), "Interrupted row operations must not continue their transformation");

        // A cancelled report is diagnostic-only: no partial result can be published/exported as success.
        assertEquals("", BatchOutputCodec.toJsonLines(report));
        assertEquals("", BatchOutputCodec.toCsv(report));
    }

    @Test
    @DisplayName("Regression test: LineRecordCipher operations remain working")
    void testBatchLineRecordCipherRegression() throws Exception {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 7);

        List<Map<String, String>> rows = List.of(
                Map.of("id", "1", "secret", "top secret 1"),
                Map.of("id", "2", "secret", "top secret 2")
        );

        BatchRunner.RowOperation encryptOp = (rowNum, row) -> Map.of("cipher",
                com.cryptocarver.crypto.LineRecordCipher.encryptRecord(
                        row.get("secret"), key, "AES-256-GCM", null,
                        com.cryptocarver.crypto.LineFileCipher.Encoding.BASE64URL, null,
                        java.nio.charset.StandardCharsets.UTF_8, false));

        BatchRunner.Report encReport = BatchRunner.run(rows, encryptOp, () -> false);
        assertEquals(2, encReport.succeeded());
    }
}
