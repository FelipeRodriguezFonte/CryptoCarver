package com.cryptocarver.model.process.handlers;

import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FlowValue;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessNodeHandler;
import java.util.List;
import java.util.Map;
import com.cryptocarver.model.process.Representation;

import java.nio.charset.Charset;
import java.util.Set;

public class ConsoleNodeHandler implements ProcessNodeHandler {
    @Override
    public Set<String> supportedTypes() {
        return Set.of("CONSOLE_INPUT", "CONSOLE_OUTPUT");
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        if ("CONSOLE_INPUT".equals(node.type)) return List.of();
        return List.of(new PortDefinition("input", Set.of(Representation.values()), true));
    }

    @Override
    public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        if ("CONSOLE_INPUT".equals(node.type)) return Representation.TEXT_UTF8;
        return inputs.getOrDefault("input", Representation.BINARY);
    }

    @Override
    public FlowValue execute(ProcessDefinition.Node node, Map<String, FlowValue> inputs, ExecutionContext context) throws Exception {
        if ("CONSOLE_INPUT".equals(node.type)) {
            String val = node.configuration.getOrDefault("value", "");
            String cs = node.configuration.getOrDefault("charset", "UTF-8");
            return FlowValue.text(val, java.nio.charset.Charset.forName(cs));
        } else {
            return inputs.getOrDefault("input", FlowValue.binary(new byte[0]));
        }
    }
}
