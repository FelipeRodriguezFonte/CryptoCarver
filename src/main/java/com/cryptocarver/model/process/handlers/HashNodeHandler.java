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
    public List<com.cryptocarver.model.process.NodeDescriptor> descriptors() {
        return List.of(
            new com.cryptocarver.model.process.NodeDescriptor(
                "HASH",
                "Crypto",
                "module.process.type.hash",
                "module.process.desc.hash",
                "#",
                List.of(
                    new com.cryptocarver.model.process.NodeParameter("algorithm", "module.process.param.algorithm", com.cryptocarver.model.process.ParameterKind.COMBO,
                        List.of("SHA-256", "SHA-512", "SHA-1", "MD5", "SHA-384", "SHA-224", "SHA3-256", "SHA3-512"), "SHA-256")
                )
            )
        );
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return List.of(new PortDefinition("input", Representation.standardValues(), true));
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
