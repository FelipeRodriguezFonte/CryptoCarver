package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDefinitionSecretsTest {

    @Test
    void sensitiveParametersAreNeverSerializedAndNeverDeserialized() {
        List<NodeDescriptor> descriptors = NodeCatalog.descriptors();
        assertFalse(descriptors.isEmpty());

        int nodeIndex = 1;
        List<ProcessDefinition.Node> testNodes = new ArrayList<>();

        for (NodeDescriptor descriptor : descriptors) {
            String nodeId = "node_" + nodeIndex++;
            ProcessDefinition.Node node = new ProcessDefinition.Node(nodeId, descriptor.type(), descriptor.type(), 100, 100);

            for (NodeParameter param : descriptor.parameters()) {
                if (param.sensitive()) {
                    // Intentionally place a distinct secret in configuration to test filter
                    node.configuration.put(param.key(), "SUPER_SECRET_" + param.key() + "_" + nodeId);
                } else if (!param.defaultValue().isBlank()) {
                    node.configuration.put(param.key(), param.defaultValue());
                }
            }
            testNodes.add(node);
        }

        ProcessDefinition definition = new ProcessDefinition();
        definition.name = "Process with sensitive parameters";
        definition.nodes.addAll(testNodes);

        // 1. Serialize: verify no SUPER_SECRET string appears in JSON
        String json = ProcessDefinitionCodec.serialize(definition);
        assertFalse(json.contains("SUPER_SECRET"), "Serialized JSON must never contain secrets!");

        for (String sensitiveKey : NodeCatalog.allSensitiveKeys()) {
            assertFalse(json.contains("\"" + sensitiveKey + "\""),
                    "Serialized JSON must not contain sensitive key: " + sensitiveKey);
        }

        // 2. Deserialize: verify that sensitive parameters are not in configuration
        ProcessDefinition loaded = ProcessDefinitionCodec.deserialize(json);
        for (ProcessDefinition.Node loadedNode : loaded.nodes) {
            var descOpt = NodeCatalog.descriptor(loadedNode.type);
            assertTrue(descOpt.isPresent());
            for (NodeParameter param : descOpt.get().parameters()) {
                if (param.sensitive()) {
                    assertNull(loadedNode.configuration.get(param.key()),
                            "Deserialized configuration must not contain secret key: " + param.key() + " in node " + loadedNode.id);
                }
            }
        }
    }

    @Test
    void defaultConfigurationNeverContainsSensitiveParameters() {
        for (NodeDescriptor descriptor : NodeCatalog.descriptors()) {
            Map<String, String> defaultConfig = NodeCatalog.defaultConfiguration(descriptor.type());
            for (NodeParameter param : descriptor.parameters()) {
                if (param.sensitive()) {
                    assertNull(defaultConfig.get(param.key()),
                            "Default configuration for node " + descriptor.type() + " must not contain sensitive parameter: " + param.key());
                }
            }
            for (String sk : NodeCatalog.allSensitiveKeys()) {
                assertNull(defaultConfig.get(sk),
                        "Default configuration for node " + descriptor.type() + " must not contain any sensitive key: " + sk);
            }
        }
    }

    @Test
    void inMemoryNodeModelNeverExposesSensitiveKeysInConfiguration() {
        for (NodeDescriptor descriptor : NodeCatalog.descriptors()) {
            ProcessDefinition.Node node = new ProcessDefinition.Node("node-1", descriptor.type(), descriptor.type(), 0, 0);
            node.configuration.putAll(NodeCatalog.defaultConfiguration(descriptor.type()));
            for (String sk : NodeCatalog.allSensitiveKeys()) {
                assertNull(node.configuration.get(sk),
                        "In-memory configuration for node " + descriptor.type() + " must not contain sensitive key: " + sk);
            }
        }
    }
}
