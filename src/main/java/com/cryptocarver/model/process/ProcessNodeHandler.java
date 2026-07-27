package com.cryptocarver.model.process;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProcessNodeHandler {
    Set<String> supportedTypes();

    List<PortDefinition> inputPorts(ProcessDefinition.Node node);

    Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs);

    FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception;

    default void validateConfiguration(ProcessDefinition.Node node) throws IllegalArgumentException {
        // default no-op
    }

    public record PortDefinition(String name, Set<Representation> acceptedRepresentations, boolean required) {}
}
