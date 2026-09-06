package com.cryptocarver.model.process;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** JSON codec for workflow files. Workflow definitions deliberately never contain key material. */
public final class ProcessDefinitionCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ProcessDefinitionCodec() { }
    public static String serialize(ProcessDefinition process) {
        ProcessDefinition safeCopy = new ProcessDefinition();
        safeCopy.version = process.version;
        safeCopy.name = process.name;
        safeCopy.nodes = new java.util.ArrayList<>();
        java.util.Set<String> sensitiveKeys = NodeCatalog.allSensitiveKeys();
        for (ProcessDefinition.Node n : process.nodes) {
            ProcessDefinition.Node safeNode = new ProcessDefinition.Node(n.id, n.type, n.label, n.x, n.y);
            if (n.configuration != null) {
                java.util.Map<String, String> safeConfig = new java.util.LinkedHashMap<>(n.configuration);
                for (String sensitiveKey : sensitiveKeys) {
                    safeConfig.remove(sensitiveKey);
                }
                safeNode.configuration.putAll(safeConfig);
            }
            safeCopy.nodes.add(safeNode);
        }
        safeCopy.connections = new java.util.ArrayList<>();
        for (ProcessDefinition.Connection c : process.connections) {
            safeCopy.connections.add(new ProcessDefinition.Connection(c.from, c.to, c.targetPort));
        }
        return GSON.toJson(safeCopy);
    }
    public static ProcessDefinition deserialize(String json) {
        ProcessDefinition process = GSON.fromJson(json, ProcessDefinition.class);
        if (process == null || (process.version != 1 && process.version != 2 && process.version != 3)) throw new IllegalArgumentException("Unsupported workflow file version: " + (process != null ? process.version : "null"));
        java.util.Set<String> sensitiveKeys = NodeCatalog.allSensitiveKeys();
        if (process.nodes != null) {
            for (ProcessDefinition.Node n : process.nodes) {
                if (n.configuration != null) {
                    for (String sensitiveKey : sensitiveKeys) {
                        n.configuration.remove(sensitiveKey);
                    }
                }
            }
        }
        return process;
    }
}
