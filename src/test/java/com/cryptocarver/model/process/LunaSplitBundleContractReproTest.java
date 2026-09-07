package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Regression for the 5B.2b component-bundle representation contract. */
class LunaSplitBundleContractReproTest {
    @Test
    void splitBundleMustBeRejectedByAPlainHexPortDuringPreflight() {
        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node source = new ProcessDefinition.Node("src", "CONSOLE_INPUT", "Key", 0, 0);
        source.configuration.put("value", "00112233445566778899AABBCCDDEEFF");
        ProcessDefinition.Node hex = new ProcessDefinition.Node("hex", "HEX_ENCODE", "Hex", 100, 0);
        ProcessDefinition.Node split = new ProcessDefinition.Node("split", "KEY_SPLIT_XOR", "Split", 200, 0);
        split.configuration.put("componentCount", "3");
        ProcessDefinition.Node parity = new ProcessDefinition.Node("parity", "PARITY_ADJUST", "Parity", 300, 0);
        definition.nodes.add(source); definition.nodes.add(hex); definition.nodes.add(split); definition.nodes.add(parity);
        definition.connections.add(new ProcessDefinition.Connection("src", "hex", "input"));
        definition.connections.add(new ProcessDefinition.Connection("hex", "split", "key"));
        definition.connections.add(new ProcessDefinition.Connection("split", "parity", "key"));

        assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition));
    }
}
