package com.cryptocarver.model.process;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The supplied-secret marker records that the session holds a sensitive value, never the value
 * itself, and it must not survive a round trip through a process file.
 */
class SuppliedSecretMarkerTest {

    @Test
    void markerNamesTheParameterItAccompanies() {
        assertEquals("panFromSecrets", NodeCatalog.suppliedMarker("pan"));
        assertTrue(NodeCatalog.isSuppliedMarker("panFromSecrets"));
        assertFalse(NodeCatalog.isSuppliedMarker("pan"));
        assertFalse(NodeCatalog.isSuppliedMarker(null));
    }

    @Test
    void isSuppliedOnlyReadsTheMarker() {
        ProcessDefinition.Node node = new ProcessDefinition.Node("n", "PIN_BLOCK_ENCODE", "PIN", 0, 0);
        assertFalse(NodeCatalog.isSupplied(node, "pan"));
        node.configuration.put(NodeCatalog.suppliedMarker("pan"), "true");
        assertTrue(NodeCatalog.isSupplied(node, "pan"));
        node.configuration.put(NodeCatalog.suppliedMarker("pan"), "false");
        assertFalse(NodeCatalog.isSupplied(node, "pan"));
    }

    @Test
    void markersNeverSurviveSerialization() {
        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node node = new ProcessDefinition.Node("n", "PIN_BLOCK_ENCODE", "PIN", 0, 0);
        node.configuration.put("format", "Format 0 (ISO-0)");
        node.configuration.put(NodeCatalog.suppliedMarker("pan"), "true");
        node.configuration.put(NodeCatalog.suppliedMarker("pin"), "true");
        definition.nodes.add(node);

        String json = ProcessDefinitionCodec.serialize(definition);
        assertFalse(json.contains("FromSecrets"), "a marker describes the session, not the process: " + json);
        assertTrue(json.contains("ISO-0"), "ordinary configuration must still be written");

        ProcessDefinition reopened = ProcessDefinitionCodec.deserialize(json);
        assertFalse(NodeCatalog.isSupplied(reopened.nodes.get(0), "pan"),
                "a reopened process cannot claim a secret it does not carry");
    }

    @Test
    void aMarkerSmuggledIntoAFileIsDroppedOnRead() {
        String json = """
                {"version":3,"name":"smuggled","nodes":[{"id":"n","type":"PIN_BLOCK_ENCODE",
                "label":"PIN","x":0,"y":0,"configuration":{"panFromSecrets":"true"}}],"connections":[]}
                """;
        ProcessDefinition reopened = ProcessDefinitionCodec.deserialize(json);
        assertFalse(NodeCatalog.isSupplied(reopened.nodes.get(0), "pan"));
    }

    @Test
    void aNodeWithoutSuppliedSecretsStillFailsPreflight() {
        ProcessDefinition definition = new ProcessDefinition();
        definition.nodes.add(new ProcessDefinition.Node("n", "PIN_BLOCK_ENCODE", "PIN", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ProcessEngine.validate(definition),
                "the marker must not weaken the guard for a node nobody configured");
    }

    @Test
    void aNodeWhoseSecretsAreHeldInSessionPassesPreflight() {
        ProcessDefinition definition = new ProcessDefinition();
        ProcessDefinition.Node node = new ProcessDefinition.Node("n", "PIN_BLOCK_ENCODE", "PIN", 0, 0);
        node.configuration.put("format", "Format 0 (ISO-0)");
        node.configuration.put(NodeCatalog.suppliedMarker("pin"), "true");
        node.configuration.put(NodeCatalog.suppliedMarker("pan"), "true");
        definition.nodes.add(node);
        assertDoesNotThrow(() -> ProcessEngine.validate(definition));
    }
}
