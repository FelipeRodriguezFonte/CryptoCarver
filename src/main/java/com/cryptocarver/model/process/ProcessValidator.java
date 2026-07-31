package com.cryptocarver.model.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates workflow definitions, checks node readiness, detects cycles, and generates Dry Run summaries. */
public final class ProcessValidator {
    private ProcessValidator() { }

    public static DryRunSummary dryRun(ProcessDefinition definition) {
        List<StepValidationResult> validations = validateSteps(definition);

        int ready = 0;
        int warning = 0;
        int incomplete = 0;
        int blocked = 0;
        String firstBlocked = null;

        for (StepValidationResult v : validations) {
            switch (v.status()) {
                case READY -> ready++;
                case WARNING -> warning++;
                case INCOMPLETE -> incomplete++;
                case BLOCKED -> {
                    blocked++;
                    if (firstBlocked == null) {
                        firstBlocked = "[" + v.targetNodeId() + "] " + v.message();
                    }
                }
                case NOT_APPLICABLE -> {}
            }
        }

        List<String> executionOrder = new ArrayList<>();
        List<String> resolvedDependencies = new ArrayList<>();

        if (blocked == 0 && !definition.nodes.isEmpty()) {
            try {
                executionOrder = computeTopologicalOrder(definition);
                for (ProcessDefinition.Connection conn : definition.connections) {
                    resolvedDependencies.add(conn.from + " -> " + conn.to + (conn.targetPort != null ? " (" + conn.targetPort + ")" : ""));
                }
            } catch (Exception e) {
                blocked++;
                if (firstBlocked == null) {
                    firstBlocked = e.getMessage();
                }
            }
        }

        return new DryRunSummary(
            definition.nodes.size(),
            ready,
            warning,
            incomplete,
            blocked,
            firstBlocked,
            executionOrder,
            resolvedDependencies,
            validations
        );
    }

    public static List<StepValidationResult> validateSteps(ProcessDefinition definition) {
        List<StepValidationResult> results = new ArrayList<>();

        if (definition == null || definition.nodes.isEmpty()) {
            results.add(StepValidationResult.blocked("root", "nodes", "No steps configured in process"));
            return results;
        }

        Map<String, ProcessDefinition.Node> nodeMap = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();

        for (ProcessDefinition.Node node : definition.nodes) {
            if (node.id == null || node.id.isBlank()) {
                results.add(StepValidationResult.blocked("unknown", "id", "Node has empty or null ID"));
                continue;
            }
            if (!ids.add(node.id)) {
                results.add(StepValidationResult.blocked(node.id, "id", "Duplicate node ID: " + node.id));
            }
            nodeMap.put(node.id, node);
        }

        // Check broken connections
        Map<String, Map<String, String>> portBindings = new HashMap<>();
        for (String id : ids) portBindings.put(id, new HashMap<>());

        for (ProcessDefinition.Connection conn : definition.connections) {
            if (!ids.contains(conn.from)) {
                results.add(StepValidationResult.blocked(conn.to, "connection", "Connection references non-existent source node: " + conn.from));
            }
            if (!ids.contains(conn.to)) {
                results.add(StepValidationResult.blocked(conn.from, "connection", "Connection references non-existent target node: " + conn.to));
            }
            if (conn.from != null && conn.from.equals(conn.to)) {
                results.add(StepValidationResult.blocked(conn.from, "connection", "Node cannot connect to itself (self-link)"));
            }
            if (ids.contains(conn.to) && ids.contains(conn.from)) {
                String targetPort = conn.targetPort != null && !conn.targetPort.isEmpty() ? conn.targetPort : "payload";
                portBindings.get(conn.to).put(targetPort, conn.from);
            }
        }

        // Cycle detection
        boolean hasCycle = checkCycles(definition, ids);
        if (hasCycle) {
            results.add(StepValidationResult.blocked("workflow", "graph", "Cycle detected in process workflow dependency graph"));
        }

        // Validate individual nodes
        for (ProcessDefinition.Node node : definition.nodes) {
            StepValidationResult nodeResult = validateNode(node, portBindings.get(node.id));
            results.add(nodeResult);
        }

        if (results.stream().noneMatch(r -> r.status() == StepValidationResult.Status.BLOCKED || r.status() == StepValidationResult.Status.INCOMPLETE)) {
            try {
                ProcessEngine.validate(definition);
            } catch (IllegalArgumentException ex) {
                String targetId = "workflow";
                if (definition.nodes != null) {
                    for (ProcessDefinition.Node n : definition.nodes) {
                        if (ex.getMessage() != null && (ex.getMessage().contains(n.label) || ex.getMessage().contains(n.id))) {
                            targetId = n.id;
                            break;
                        }
                    }
                }
                final String tid = targetId;
                results.removeIf(r -> r.targetNodeId().equals(tid));
                results.add(StepValidationResult.blocked(tid, "connection", ex.getMessage()));
            }
        }

        return results;
    }

    public static StepValidationResult validateNode(ProcessDefinition.Node node, Map<String, String> boundPorts) {
        if (node == null || node.type == null || node.type.isBlank()) {
            return StepValidationResult.blocked(node != null ? node.id : "unknown", "type", "Node operation type is missing");
        }

        ProcessNodeHandler handler;
        try {
            handler = ProcessEngine.getHandlerFor(node.type);
        } catch (IllegalArgumentException e) {
            return StepValidationResult.blocked(node.id, "type", "Unknown operation type: " + node.type);
        }

        Map<String, String> config = node.configuration != null ? node.configuration : Map.of();
        Map<String, String> ports = boundPorts != null ? boundPorts : Map.of();
        String type = node.type.toLowerCase();

        if ("console_input".equals(type) && (config.get("value") == null || config.get("value").isBlank())) {
            return StepValidationResult.incomplete(node.id, "nodeValueArea", "Console input text is empty");
        }

        if ("file_input".equals(type) && (config.get("path") == null || config.get("path").isBlank()) && !ports.containsKey("input") && !ports.containsKey("payload")) {
            return StepValidationResult.incomplete(node.id, "nodePathField", "File read path is required");
        }

        if ("hash".equals(type) && (config.get("algorithm") == null || config.get("algorithm").isBlank())) {
            return StepValidationResult.incomplete(node.id, "hashAlgorithmCombo", "Hash algorithm not selected");
        }

        if (List.of("advanced_crypto", "crypto", "encrypt", "decrypt", "sign", "verify", "mac").contains(type)) {
            if (config.get("algorithm") == null || config.get("algorithm").isBlank()) {
                return StepValidationResult.incomplete(node.id, "cryptoAlgorithmCombo", "Cipher algorithm not selected");
            }
        }

        String keyStr = config.get("key");
        if (keyStr == null) keyStr = config.get("manualKey");
        if (keyStr != null && keyStr.contains("[METADATA_ONLY]")) {
            return StepValidationResult.warning(node.id, "manualKeyField", "Referenced key is metadata-only (secrets restricted in current profile)");
        }

        if (keyStr != null && !keyStr.isBlank()) {
            String format = config.getOrDefault("keyFormat", "HEX");
            if ("HEX".equalsIgnoreCase(format) && !isValidHex(keyStr)) {
                return StepValidationResult.blocked(node.id, "manualKeyField", "Invalid Hex key format (must be even length hex string)");
            }
        }

        for (ProcessNodeHandler.PortDefinition port : handler.inputPorts(node)) {
            if (port.required() && !ports.containsKey(port.name())) {
                return StepValidationResult.incomplete(node.id, port.name(), "Missing required connection to port '" + port.name() + "' on node: " + node.label);
            }
        }

        try {
            handler.validateConfiguration(node);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("[METADATA_ONLY]") || msg.contains("metadata-only"))) {
                return StepValidationResult.warning(node.id, "manualKeyField", msg);
            } else if (msg != null && (msg.contains("missing") || msg.contains("required") || msg.contains("not selected") || msg.contains("empty"))) {
                return StepValidationResult.incomplete(node.id, "configuration", msg);
            } else {
                return StepValidationResult.blocked(node.id, "configuration", msg != null ? msg : "Invalid node configuration");
            }
        }

        return StepValidationResult.ready(node.id, node.label + " step configured (" + node.type + ")");
    }

    private static boolean isValidHex(String str) {
        if (str == null) return false;
        String s = str.trim();
        return !s.isEmpty() && s.length() % 2 == 0 && s.matches("[0-9a-fA-F]+");
    }

    private static boolean checkCycles(ProcessDefinition definition, Set<String> ids) {
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : ids) {
            adj.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }

        for (ProcessDefinition.Connection conn : definition.connections) {
            if (ids.contains(conn.from) && ids.contains(conn.to)) {
                adj.get(conn.from).add(conn.to);
                inDegree.put(conn.to, inDegree.get(conn.to) + 1);
            }
        }

        List<String> zeroIn = ids.stream().filter(id -> inDegree.get(id) == 0).collect(Collectors.toList());
        int visited = 0;

        while (!zeroIn.isEmpty()) {
            String curr = zeroIn.remove(0);
            visited++;
            for (String neighbor : adj.get(curr)) {
                int in = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, in);
                if (in == 0) zeroIn.add(neighbor);
            }
        }

        return visited != ids.size();
    }

    public static List<String> computeTopologicalOrder(ProcessDefinition definition) {
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (ProcessDefinition.Node node : definition.nodes) {
            adj.put(node.id, new ArrayList<>());
            inDegree.put(node.id, 0);
        }

        for (ProcessDefinition.Connection conn : definition.connections) {
            if (adj.containsKey(conn.from) && adj.containsKey(conn.to)) {
                adj.get(conn.from).add(conn.to);
                inDegree.put(conn.to, inDegree.get(conn.to) + 1);
            }
        }

        List<String> zeroIn = definition.nodes.stream().map(n -> n.id).filter(id -> inDegree.get(id) == 0).collect(Collectors.toList());
        List<String> sorted = new ArrayList<>();

        while (!zeroIn.isEmpty()) {
            String curr = zeroIn.remove(0);
            sorted.add(curr);
            for (String neighbor : adj.get(curr)) {
                int in = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, in);
                if (in == 0) zeroIn.add(neighbor);
            }
        }

        return sorted;
    }
}
