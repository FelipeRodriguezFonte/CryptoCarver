package com.cryptocarver.model.process;

import com.cryptocarver.model.process.handlers.HsmHostCommandNodeHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HsmHostCommandNodeHandlerTest {
    private final HsmHostCommandNodeHandler handler = new HsmHostCommandNodeHandler();

    @Test
    void everyTypeHasADescriptorAndRejectsEmptyConfiguration() {
        assertEquals(3, HsmHostCommandNodeHandler.TYPES.size());
        for (String type : HsmHostCommandNodeHandler.TYPES) {
            assertTrue(handler.descriptors().stream().anyMatch(d -> d.type().equals(type)), type);
            ProcessDefinition definition = new ProcessDefinition();
            definition.nodes.add(new ProcessDefinition.Node("n", type, type, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition), type);
        }
    }

    @Test
    void composesNcAndParsesTheSuppliedResponse() throws Exception {
        ProcessDefinition.Node compose = node("HSM_HOST_COMPOSE");
        compose.configuration.put("header", "0000");
        compose.configuration.put("commandCode", "NC");
        compose.configuration.put("headerLength", "4");
        assertEquals("0000NC", handler.execute(compose, Map.of(), null).render());

        // Source: docs/REVISION_CHATGPT_1_Y_PAQUETE_2.md, reviewed 2026-09-19.
        // The original capture provenance is missing; the dedicated capture
        // recipe requests a replacement request/response pair.
        ProcessDefinition.Node parse = node("HSM_HOST_PARSE_RESPONSE");
        parse.configuration.put("frame", "0000ND007B44AC1DDEE2A94B0007-E000");
        parse.configuration.put("headerLength", "4");
        String report = handler.execute(parse, Map.of(), null).render();
        assertTrue(report.contains("response=ND"), report);
        assertTrue(report.contains("error=00"), report);
        assertTrue(report.contains("dataOpaque=7B44AC1DDEE2A94B0007-E000"), report);
        assertTrue(report.contains("lmkCheckValue=7B44AC1DDEE2A94B"), report);
        assertTrue(report.contains("firmwareVersion=0007-E000"), report);
    }

    @Test
    void framesAndBodiesAreTransientSecrets() {
        for (NodeDescriptor descriptor : handler.descriptors()) {
            for (NodeParameter parameter : descriptor.parameters()) {
                if (parameter.key().equals("frame") || parameter.key().equals("body")
                        || parameter.key().equals("trailer")) {
                    assertTrue(parameter.sensitive(), descriptor.type() + ":" + parameter.key());
                }
            }
        }

        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node node = node("HSM_HOST_PARSE_RESPONSE");
        node.configuration.put("frame", "SECRET_FRAME_123456789");
        definition.nodes.add(node);
        String json = ProcessDefinitionCodec.serialize(definition);
        assertFalse(json.contains("SECRET_FRAME"), json);
        assertFalse(json.contains("\"frame\""), json);
    }

    private static ProcessDefinition.Node node(String type) {
        return new ProcessDefinition.Node(type.toLowerCase(), type, type, 0, 0);
    }
}
