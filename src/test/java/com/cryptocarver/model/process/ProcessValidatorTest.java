package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ProcessValidatorTest {

    @Test
    void testCycleDetection() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("node1", "CONSOLE_INPUT", "Console 1", 0, 0);
        ProcessDefinition.Node n2 = new ProcessDefinition.Node("node2", "HASH", "Hash 1", 0, 0);
        def.nodes.addAll(List.of(n1, n2));

        // Cycle: n1 -> n2 and n2 -> n1
        def.connections.add(new ProcessDefinition.Connection("node1", "node2", "input"));
        def.connections.add(new ProcessDefinition.Connection("node2", "node1", "input"));

        DryRunSummary summary = ProcessValidator.dryRun(def);

        assertTrue(summary.blockedCount() > 0, "Cycle graph must be blocked");
        assertNotNull(summary.firstBlockedReason(), "Cycle must report first blocked reason");
        assertTrue(summary.firstBlockedReason().toLowerCase().contains("cycle"), "Reason must mention cycle");
        assertFalse(summary.isRunnable(), "Graph with cycle must not be runnable");
    }

    @Test
    void testBrokenReferenceDetection() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("node1", "CONSOLE_INPUT", "Console 1", 0, 0);
        def.nodes.add(n1);

        // Connection to non-existent node3
        def.connections.add(new ProcessDefinition.Connection("node1", "node3", "input"));

        DryRunSummary summary = ProcessValidator.dryRun(def);

        assertTrue(summary.blockedCount() > 0, "Broken reference must be blocked");
        assertNotNull(summary.firstBlockedReason());
        assertTrue(summary.firstBlockedReason().contains("node3"), "First blocked reason must mention missing node");
    }

    @Test
    void testIncompatibleFormatOrMissingParameters() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node node = new ProcessDefinition.Node("node1", "crypto", "Crypto Node", 0, 0);
        // Missing algorithm parameter
        def.nodes.add(node);

        List<StepValidationResult> validations = ProcessValidator.validateSteps(def);
        assertEquals(1, validations.size());
        assertEquals(StepValidationResult.Status.INCOMPLETE, validations.get(0).status());
        assertEquals("cryptoAlgorithmCombo", validations.get(0).targetFieldKey());
    }

    @Test
    void testDryRunWithoutExecutionOrHistorySideEffects() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("node1", "CONSOLE_INPUT", "Input", 0, 0);
        n1.configuration.put("value", "Test Hello");
        ProcessDefinition.Node n2 = new ProcessDefinition.Node("node2", "HASH", "SHA-256", 0, 0);
        n2.configuration.put("algorithm", "SHA-256");

        def.nodes.addAll(List.of(n1, n2));
        def.connections.add(new ProcessDefinition.Connection("node1", "node2", "input"));

        DryRunSummary summary = ProcessValidator.dryRun(def);

        assertTrue(summary.isRunnable());
        assertEquals(2, summary.totalSteps());
        assertEquals(2, summary.readyCount());
        assertEquals(0, summary.blockedCount());
        assertEquals(2, summary.executionOrder().size());
        assertEquals("node1", summary.executionOrder().get(0));
        assertEquals("node2", summary.executionOrder().get(1));
    }

    @Test
    void testCancellationPreventsSubsequentSteps() throws Exception {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("n1", "CONSOLE_INPUT", "Step 1", 0, 0);
        n1.configuration.put("value", "Data");
        ProcessDefinition.Node n2 = new ProcessDefinition.Node("n2", "HASH", "Step 2", 0, 0);
        n2.configuration.put("algorithm", "SHA-256");

        def.nodes.addAll(List.of(n1, n2));
        def.connections.add(new ProcessDefinition.Connection("n1", "n2", "input"));

        AtomicBoolean cancelled = new AtomicBoolean(true); // Cancel immediately
        ExecutionContext context = new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, null, cancelled::get);

        Map<String, FlowValue> result = ProcessEngine.execute(def, context);

        assertTrue(result.size() < def.nodes.size(), "Cancelled execution must return before executing subsequent steps");
    }

    @Test
    void testUnknownOperationIsBlocked() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("n1", "UNKNOWN_OP_TYPE", "Unknown Node", 0, 0);
        def.nodes.add(n1);

        DryRunSummary summary = ProcessValidator.dryRun(def);

        assertEquals(1, summary.blockedCount(), "Unknown operation must be blocked");
        assertEquals(0, summary.readyCount());
        assertTrue(summary.firstBlockedReason().contains("Unknown operation type"), "Reason must mention unknown operation");
    }

    @Test
    void testInvalidHexKeyIsBlocked() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("n1", "ENCRYPT", "Encrypt Node", 0, 0);
        n1.configuration.put("algorithm", "AES/GCM/NoPadding");
        n1.configuration.put("keyFormat", "HEX");
        n1.configuration.put("key", "not-a-valid-hex-string");
        def.nodes.add(n1);

        DryRunSummary summary = ProcessValidator.dryRun(def);

        assertEquals(1, summary.blockedCount(), "Invalid hex key must be blocked");
        assertTrue(summary.firstBlockedReason().contains("Invalid Hex key format"));
    }

    @Test
    void testMetadataOnlyKeyReturnsWarning() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "Text Input", 0, 0);
        input.configuration.put("value", "test data");
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("n1", "ENCRYPT", "Encrypt Node", 0, 0);
        n1.configuration.put("algorithm", "AES/ECB/PKCS5Padding");
        n1.configuration.put("keyFormat", "HEX");
        n1.configuration.put("key", "key-ref-123 [METADATA_ONLY]");
        def.nodes.addAll(List.of(input, n1));
        def.connections.add(new ProcessDefinition.Connection("in", "n1", "payload"));

        DryRunSummary summary = ProcessValidator.dryRun(def);

        assertEquals(1, summary.warningCount(), "Metadata-only key must return warning");
        assertEquals(0, summary.blockedCount());
    }

    @Test
    void testRepresentationMismatchIsBlocked() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node n1 = new ProcessDefinition.Node("n1", "CONSOLE_INPUT", "Text Input", 0, 0);
        n1.configuration.put("value", "Hello");
        ProcessDefinition.Node n2 = new ProcessDefinition.Node("n2", "HEX_ENCODE", "Hex Encode", 0, 0);
        ProcessDefinition.Node n3 = new ProcessDefinition.Node("n3", "DECRYPT", "Decrypt Node", 0, 0);
        n3.configuration.put("algorithm", "AES/ECB/PKCS5Padding");
        n3.configuration.put("keyFormat", "HEX");
        n3.configuration.put("key", "00112233445566778899AABBCCDDEEFF");

        def.nodes.addAll(List.of(n1, n2, n3));
        def.connections.add(new ProcessDefinition.Connection("n1", "n2", "input"));
        def.connections.add(new ProcessDefinition.Connection("n2", "n3", "payload"));

        DryRunSummary summary = ProcessValidator.dryRun(def);

        assertTrue(summary.blockedCount() > 0, "Representation mismatch must be blocked");
        assertTrue(summary.firstBlockedReason().contains("expects") && summary.firstBlockedReason().contains("receives"));
    }
}
