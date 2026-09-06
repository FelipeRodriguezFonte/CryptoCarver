package com.cryptocarver.model.process.handlers;

import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RandomBytesNodeHandler implements ProcessNodeHandler {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public Set<String> supportedTypes() {
        return Set.of("RANDOM_BYTES");
    }

    @Override
    public List<com.cryptocarver.model.process.NodeDescriptor> descriptors() {
        return List.of(
            new com.cryptocarver.model.process.NodeDescriptor(
                "RANDOM_BYTES",
                "Generators",
                "module.process.type.randomBytes",
                "module.process.desc.randomBytes",
                "🎲",
                List.of(
                    new com.cryptocarver.model.process.NodeParameter("length", "module.process.param.length",
                        com.cryptocarver.model.process.ParameterKind.NUMBER, "16")
                )
            )
        );
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        return List.of();
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return Representation.BINARY;
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        int length = Integer.parseInt(node.configuration.getOrDefault("length", "16"));
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return FlowValue.binary(bytes);
    }

    @Override
    public void validateConfiguration(ProcessDefinition.Node node) throws IllegalArgumentException {
        try {
            int length = Integer.parseInt(node.configuration.getOrDefault("length", "16"));
            if (length < 1 || length > 65536) {
                throw new IllegalArgumentException("Random bytes length must be between 1 and 65536");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Random bytes length must be a valid integer");
        }
    }
}
