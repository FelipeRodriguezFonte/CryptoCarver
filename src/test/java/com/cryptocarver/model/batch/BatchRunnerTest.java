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

        BatchRunner.Report report = BatchRunner.run(rows, (rowNum, input) -> {
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

        BatchRunner.Report report = BatchRunner.run(rows, (rowNum, input) -> {
            int id = Integer.parseInt(input.get("id"));
            if (id > 5) {
                cancelled.set(true); // Trigger cancellation mid-flight
            }
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Map.of("processed_id", input.get("id"));
        }, cancelled::get);

        assertNotNull(report);
        assertTrue(report.cancelled(), "Report should be marked as cancelled");
        assertFalse(report.results().isEmpty(), "Partial results should be returned on cancellation");
    }
    @Test
    void testCancellationWaitsForWorkers() {
        List<Map<String, String>> rows = List.of(Map.of("id", "1"));
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean workerFinished = new java.util.concurrent.atomic.AtomicBoolean(false);

        BatchRunner.Report report = BatchRunner.run(rows, (rowNum, input) -> {
            cancelled.set(true); // Cancel while inside the worker
            try { Thread.sleep(200); } catch (InterruptedException e) { /* ignored */ }
            workerFinished.set(true);
            return Map.of("processed_id", "1");
        }, cancelled::get);

        assertTrue(report.cancelled());
        assertTrue(workerFinished.get(), "BatchRunner should block until the bad worker actually finishes");
    }

    @Test
    void testBatchCryptoOperations() throws Exception {
        List<Map<String, String>> rows = List.of(
            Map.of("id", "1", "input", "secret 1"),
            Map.of("id", "2", "input", "secret 2")
        );
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 1);

        // Encrypt with GCM
        BatchRunner.RowOperation encryptGcm = (rowNum, row) -> Map.of("result", com.cryptocarver.crypto.LineRecordCipher.encryptRecord(
                row.get("input"), key, "AES-256-GCM", null, com.cryptocarver.crypto.LineFileCipher.Encoding.BASE64URL, null, java.nio.charset.StandardCharsets.UTF_8, false));
        BatchRunner.Report encReport = BatchRunner.run(rows, encryptGcm, () -> false);
        assertEquals(2, encReport.succeeded());

        String cipher1 = encReport.results().get(0).output().get("result");
        String cipher2 = encReport.results().get(1).output().get("result");

        List<Map<String, String>> encRows = List.of(
            Map.of("id", "1", "input", cipher1),
            Map.of("id", "2", "input", cipher2)
        );

        // Decrypt with GCM
        BatchRunner.RowOperation decryptGcm = (rowNum, row) -> Map.of("result", com.cryptocarver.crypto.LineRecordCipher.decryptRecord(
                row.get("input"), key, "AES-256-GCM", null, null, com.cryptocarver.crypto.LineFileCipher.Encoding.BASE64URL, java.nio.charset.StandardCharsets.UTF_8, 0));
        BatchRunner.Report decReport = BatchRunner.run(encRows, decryptGcm, () -> false);
        assertEquals(2, decReport.succeeded());
        assertEquals("secret 1", decReport.results().get(0).output().get("result"));
        assertEquals("secret 2", decReport.results().get(1).output().get("result"));
    }

    @Test
    void testBatchCryptoExceptions() throws Exception {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 2);

        List<Map<String, String>> rows = List.of(
            Map.of("id", "1", "input", "secret 1")
        );

        // Encrypt with ChaCha20
        BatchRunner.RowOperation encryptChaCha = (rowNum, row) -> Map.of("result", com.cryptocarver.crypto.LineRecordCipher.encryptRecord(
                row.get("input"), key, "ChaCha20-Poly1305", null, com.cryptocarver.crypto.LineFileCipher.Encoding.BASE64URL, null, java.nio.charset.StandardCharsets.UTF_8, false));
        BatchRunner.Report encReport = BatchRunner.run(rows, encryptChaCha, () -> false);
        assertEquals(1, encReport.succeeded());
        String cipher = encReport.results().get(0).output().get("result");

        // Tamper cipher (assuming it's base64url, modifying the last char should invalidate the tag)
        String tampered = cipher.substring(0, cipher.length() - 1) + (cipher.endsWith("A") ? "B" : "A");

        List<Map<String, String>> decRows = List.of(
            Map.of("id", "1", "input", tampered)
        );

        // Decrypt tampered row
        BatchRunner.RowOperation decryptChaCha = (rowNum, row) -> Map.of("result", com.cryptocarver.crypto.LineRecordCipher.decryptRecord(
                row.get("input"), key, "ChaCha20-Poly1305", null, null, com.cryptocarver.crypto.LineFileCipher.Encoding.BASE64URL, java.nio.charset.StandardCharsets.UTF_8, 0));
        BatchRunner.Report decReport = BatchRunner.run(decRows, decryptChaCha, () -> false);

        assertEquals(0, decReport.succeeded());
        assertEquals(1, decReport.failed());
        assertFalse(decReport.results().get(0).succeeded());
        assertNotNull(decReport.results().get(0).error()); // AEADBadTagException
    }

    @Test
    void testBatchCryptoCbcAndEbcdic() throws Exception {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 3);
        byte[] iv = new byte[16];
        java.util.Arrays.fill(iv, (byte) 4);

        // Use IBM037
        java.nio.charset.Charset cs = java.nio.charset.Charset.forName("IBM037");

        List<Map<String, String>> rows = List.of(
            Map.of("id", "1", "input", "secret cbc")
        );

        BatchRunner.RowOperation encryptCbc = (rowNum, row) -> Map.of("result", com.cryptocarver.crypto.LineRecordCipher.encryptRecord(
                row.get("input"), key, "AES-256-CBC", null, com.cryptocarver.crypto.LineFileCipher.Encoding.HEXADECIMAL, iv, cs, false));
        BatchRunner.Report encReport = BatchRunner.run(rows, encryptCbc, () -> false);
        assertEquals(1, encReport.succeeded());
        String cipher = encReport.results().get(0).output().get("result");

        List<Map<String, String>> encRows = List.of(
            Map.of("id", "1", "input", cipher)
        );
        BatchRunner.RowOperation decryptCbc = (rowNum, row) -> Map.of("result", com.cryptocarver.crypto.LineRecordCipher.decryptRecord(
                row.get("input"), key, "AES-256-CBC", null, iv, com.cryptocarver.crypto.LineFileCipher.Encoding.HEXADECIMAL, cs, rowNum));
        BatchRunner.Report decReport = BatchRunner.run(encRows, decryptCbc, () -> false);
        assertEquals(1, decReport.succeeded());
        assertEquals("secret cbc", decReport.results().get(0).output().get("result"));
    }

    @Test
    void testStopOnError() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean errorOccurred = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<Map<String, String>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(Map.of("id", String.valueOf(i)));
        }

        BatchRunner.RowOperation op = (rowNum, row) -> {
            int id = Integer.parseInt(row.get("id"));
            if (id == 5) {
                errorOccurred.set(true);
                throw new Exception("Intentional failure");
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Map.of("processed_id", row.get("id"));
        };

        BatchRunner.Report report = BatchRunner.run(rows, op, () -> errorOccurred.get());
        assertTrue(report.cancelled(), "Batch should be cancelled when stop on error triggers");
        assertTrue(report.succeeded() < 100); // Many rows shouldn't be processed
    }
}
