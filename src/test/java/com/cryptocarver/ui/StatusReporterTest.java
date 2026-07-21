package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cryptocarver.model.OperationResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusReporterTest {

    @Test
    void publishUpdatesAllThreeOperationSurfaces() {
        RecordingReporter reporter = new RecordingReporter();
        reporter.publish(OperationResult.forOperation("PQC Sign")
                .input(new byte[] {1}).output(new byte[] {2})
                .details(Map.of("Algorithm", "ML-DSA-44"))
                .status("Signature generated").build());

        assertEquals("PQC Sign", reporter.lastInspectorOp);
        assertEquals("ML-DSA-44", reporter.lastHistoryDetails.get(0).value());
        assertEquals("Signature generated", reporter.lastMessage);
    }

    private static class RecordingReporter implements StatusReporter {
        String lastMessage;
        String lastHistoryOp;
        String lastInspectorOp;
        java.util.List<com.cryptocarver.model.OperationDetail> lastHistoryDetails;
        java.util.List<com.cryptocarver.model.OperationDetail> lastInspectorDetails;

        @Override public void updateStatus(String message) { this.lastMessage = message; }

        @Override
        public void updateInspector(String operation, byte[] input, byte[] output, java.util.List<com.cryptocarver.model.OperationDetail> details) {
            this.lastInspectorOp = operation;
            this.lastInspectorDetails = details;
        }

        @Override public void showError(String title, String message) {}

        @Override
        public void addToHistory(String operation, java.util.List<com.cryptocarver.model.OperationDetail> details) {
            this.lastHistoryOp = operation;
            this.lastHistoryDetails = details;
        }
    }
}
