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
    public List<com.cryptocarver.model.process.NodeDescriptor> descriptors() {
        return List.of(
            new com.cryptocarver.model.process.NodeDescriptor(
                "CONSOLE_INPUT",
                "Inputs",
                "module.process.type.consoleInput",
                "module.process.desc.consoleInput",
                "⌨",
                List.of(
                    new com.cryptocarver.model.process.NodeParameter("value", "module.process.param.value", com.cryptocarver.model.process.ParameterKind.MULTILINE, ""),
                    new com.cryptocarver.model.process.NodeParameter("charset", "module.process.param.charset", com.cryptocarver.model.process.ParameterKind.COMBO,
                        List.of("UTF-8", "ISO-8859-1", "US-ASCII", "UTF-16"), "UTF-8")
                )
            ),
            new com.cryptocarver.model.process.NodeDescriptor(
                "CONSOLE_OUTPUT",
                "Outputs",
                "module.process.type.consoleOutput",
                "module.process.desc.consoleOutput",
                "📺",
                List.of()
            )
        );
    }

    @Override
    public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        if ("CONSOLE_INPUT".equals(node.type)) return List.of();
        return List.of(new PortDefinition("input", Representation.standardValues(), true));
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
