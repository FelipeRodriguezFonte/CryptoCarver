package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.HashOperations;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import java.util.List;
import java.util.Map;
import com.cryptocarver.model.process.Representation;

import java.util.Set;

public class HashNodeHandler implements ProcessNodeHandler {
    @Override
    public Set<String> supportedTypes() {
        return Set.of("HASH");
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return List.of(new PortDefinition("input", Set.of(Representation.values()), true));
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.BINARY;
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        FlowValue input = inputs.getOrDefault("input", FlowValue.binary(new byte[0]));
        String alg = node.configuration.getOrDefault("algorithm", "SHA-256");
        byte[] hash = com.cryptocarver.crypto.HashOperations.calculateHash(input.bytes(), alg);
        return FlowValue.binary(hash);
    }
}
