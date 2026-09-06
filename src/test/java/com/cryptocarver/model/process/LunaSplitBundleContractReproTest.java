package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Permanent regression test for the 5B.2a component-bundle representation contract. */
class LunaSplitBundleContractReproTest {

    private static ProcessDefinition splitInto(String downstreamType, String downstreamPort) {
        ProcessDefinition def = new ProcessDefinition();
        ProcessDefinition.Node source = new ProcessDefinition.Node("src", "CONSOLE_INPUT", "Key", 0, 0);
        source.configuration.put("value", "00112233445566778899AABBCCDDEEFF");
        ProcessDefinition.Node toHex = new ProcessDefinition.Node("hex", "HEX_ENCODE", "Hex", 100, 0);
        ProcessDefinition.Node split = new ProcessDefinition.Node("split", "KEY_SPLIT_XOR", "Split", 200, 0);
        split.configuration.put("componentCount", "3");
        ProcessDefinition.Node down = new ProcessDefinition.Node("down", downstreamType, downstreamType, 300, 0);
        def.nodes.add(source);
        def.nodes.add(toHex);
        def.nodes.add(split);
        def.nodes.add(down);
        def.connections.add(new ProcessDefinition.Connection("src", "hex", "input"));
        def.connections.add(new ProcessDefinition.Connection("hex", "split", "key"));
        def.connections.add(new ProcessDefinition.Connection("split", "down", downstreamPort));
        return def;
    }

    @Test
    void splitBundleMustBeRejectedByAPlainHexPortDuringValidation() {
        ProcessDefinition def = splitInto("PARITY_ADJUST", "key");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ProcessEngine.validate(def));
        assertTrue(error.getMessage().contains("HEX_COMPONENTS")
                || error.getMessage().contains("Invalid connection"));
    }
}
