package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dry Run and Run must agree. A graph that Dry Run reports as ready has to execute.
 */
class LunaDryRunDivergenceReproTest {

    private static ProcessDefinition graphWithDuplicateInput() {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node in = new ProcessDefinition.Node("in", "CONSOLE_INPUT", "Console input", 0, 0);
        in.configuration.put("value", "Hola");
        ProcessDefinition.Node hash = new ProcessDefinition.Node("hash", "HASH", "SHA-256", 100, 0);
        hash.configuration.put("algorithm", "SHA-256");
        ProcessDefinition.Node out = new ProcessDefinition.Node("out", "CONSOLE_OUTPUT", "Console output", 200, 0);
        def.nodes.add(in);
        def.nodes.add(hash);
        def.nodes.add(out);
        // One link made by dropping on the port handle, one made through the
        // "select 2 blocks and connect" path, which leaves targetPort null.
        def.connections.add(new ProcessDefinition.Connection("in", "hash", "input"));
        def.connections.add(new ProcessDefinition.Connection("in", "hash", null));
        def.connections.add(new ProcessDefinition.Connection("hash", "out", "input"));
        return def;
    }

    @Test
    void dryRunMustNotReportAGraphReadyWhenExecutionRefusesIt() {
        ProcessDefinition forDryRun = graphWithDuplicateInput();
        DryRunSummary summary = ProcessValidator.dryRun(forDryRun);

        ProcessDefinition forRun = graphWithDuplicateInput();
        Exception runFailure = assertThrows(Exception.class,
                () -> ProcessEngine.execute(forRun, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> { })));

        assertTrue(summary.blockedCount() > 0,
                "Dry Run reported nothing blocked (ready=" + summary.readyCount()
                        + ", warning=" + summary.warningCount()
                        + ", incomplete=" + summary.incompleteCount()
                        + ", blocked=" + summary.blockedCount()
                        + ") but Run failed with: " + runFailure.getMessage());
    }

    @Test
    void theErrorNamesTheBlockNotItsInternalIdentifier() {
        ProcessDefinition def = graphWithDuplicateInput();
        Exception failure = assertThrows(Exception.class,
                () -> ProcessEngine.execute(def, new ExecutionContext(FileWritePolicy.ALLOW_OVERWRITE, e -> { })));
        String message = String.valueOf(failure.getMessage());
        assertTrue(message.contains("SHA-256"),
                "the message should name the block the user sees, was: " + message);
    }
}
