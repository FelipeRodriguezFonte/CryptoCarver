package com.cryptocarver.model.process;

import com.cryptocarver.model.process.handlers.FormatPreservingEncryptionNodeHandler;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatPreservingEncryptionNodeHandlerTest {
    @Test
    void executesNistFf1VectorThroughProcessEngine() throws Exception {
        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "input", 0, 0);
        input.configuration.put("value", "0123456789");
        ProcessDefinition.Node fpe = new ProcessDefinition.Node("fpe", "FPE_ENCRYPT", "fpe", 1, 0);
        fpe.configuration.put("key", "2B7E151628AED2A6ABF7158809CF4F3C");
        fpe.configuration.put("alphabet", "DECIMAL");
        definition.nodes.add(input);
        definition.nodes.add(fpe);
        definition.connections.add(new ProcessDefinition.Connection("input", "fpe", "input"));

        // The vector is also checked directly so this test remains explicit about the
        // representation crossing the Process Designer boundary.
        assertEquals("2433477484", ProcessEngine.execute(definition).get("fpe").render());
    }

    @Test
    void handlerAcceptsKeyAndTweakFromHexPorts() throws Exception {
        FormatPreservingEncryptionNodeHandler handler = new FormatPreservingEncryptionNodeHandler();
        ProcessDefinition.Node node = new ProcessDefinition.Node("fpe", "FPE_ENCRYPT", "fpe", 0, 0);
        node.configuration.put("alphabet", "DECIMAL");
        node.configuration.put("algorithm", "FF1");
        String result = handler.execute(node, Map.of(
                "input", FlowValue.text("0123456789", StandardCharsets.UTF_8),
                "key", FlowValue.hex("2B7E151628AED2A6ABF7158809CF4F3C".getBytes(StandardCharsets.UTF_8)),
                "tweak", FlowValue.hex(new byte[0])), null).render();
        assertEquals("2433477484", result);
    }
}
