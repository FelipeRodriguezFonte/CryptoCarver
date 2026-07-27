package com.cryptocarver.model.process;

import com.cryptocarver.model.process.handlers.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Executes safe MVP workflows using extensible Node Handlers. */
public final class ProcessEngine {
    private ProcessEngine() { }

    private static final List<ProcessNodeHandler> HANDLERS = List.of(
        new ConsoleNodeHandler(),
        new FileNodeHandler(),
        new HashNodeHandler(),
        new CodecNodeHandler(),
        new KeyMaterialNodeHandler(),
        new AdvancedCryptoNodeHandler(),
        new WssNodeHandler(),
        new RandomBytesNodeHandler()
    );

    public static Map<String, Representation> validate(ProcessDefinition definition) {
        Set<String> ids = new HashSet<>();
        Map<String, ProcessDefinition.Node> nodeMap = new HashMap<>();

        for (ProcessDefinition.Node node : definition.nodes) {
            if (node.id == null || node.id.isBlank()) throw new IllegalArgumentException("Node has empty ID");
            if (!ids.add(node.id)) throw new IllegalArgumentException("Duplicate node ID: " + node.id);
            // These flags are derived from the current graph.  Clear all of them
            // before rebuilding port bindings so deleting a connection cannot leave
            // a node believing that its key, IV, or AAD still comes from the flow.
            node.configuration.remove("keyFromFlow");
            node.configuration.remove("ivFromFlow");
            node.configuration.remove("aadFromFlow");
            nodeMap.put(node.id, node);

            getHandlerFor(node.type);
        }

        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : ids) {
            adj.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }

        // targetNodeId -> (targetPortName -> sourceNodeId)
        Map<String, Map<String, String>> portBindings = new HashMap<>();
        for (String id : ids) {
            portBindings.put(id, new HashMap<>());
        }

        for (ProcessDefinition.Connection conn : definition.connections) {
            if (!ids.contains(conn.from)) throw new IllegalArgumentException("Connection from unknown node: " + conn.from);
            if (!ids.contains(conn.to)) throw new IllegalArgumentException("Connection to unknown node: " + conn.to);
            if (conn.from.equals(conn.to)) throw new IllegalArgumentException("Self-link on node: " + conn.from);

            ProcessDefinition.Node targetNode = nodeMap.get(conn.to);
            ProcessNodeHandler targetHandler = getHandlerFor(targetNode.type);
            List<ProcessNodeHandler.PortDefinition> targetPorts = targetHandler.inputPorts(targetNode);

            String assignedPort = conn.targetPort;
            if (assignedPort == null || assignedPort.isEmpty()) {
                if (targetPorts.size() == 1) {
                    assignedPort = targetPorts.get(0).name();
                    conn.targetPort = assignedPort; // Set for compatibility
                } else if (targetPorts.stream().anyMatch(port -> "payload".equals(port.name()))
                        && targetPorts.stream().anyMatch(port -> "key".equals(port.name()))) {
                    // Existing process files predate the optional key port. Their sole inbound link is payload.
                    assignedPort = "payload";
                    conn.targetPort = assignedPort;
                } else if (targetPorts.size() > 1) {
                    throw new IllegalArgumentException("Connection to multi-port node requires explicit targetPort: " + conn.to);
                } else {
                    throw new IllegalArgumentException("Connection to node with no input ports: " + conn.to);
                }
            } else {
                boolean portExists = targetPorts.stream().anyMatch(p -> p.name().equals(conn.targetPort));
                if (!portExists) {
                    if (targetPorts.size() == 1 && "payload".equals(assignedPort)
                            && "input".equals(targetPorts.get(0).name())) {
                        assignedPort = "input";
                        conn.targetPort = assignedPort;
                    } else {
                        throw new IllegalArgumentException("Unknown target port '" + assignedPort + "' on node: " + conn.to);
                    }
                }
            }

            if (portBindings.get(conn.to).containsKey(assignedPort)) {
                throw new IllegalArgumentException("Multiple connections to the same port '" + assignedPort + "' on node: " + conn.to);
            }
            portBindings.get(conn.to).put(assignedPort, conn.from);
            if ("key".equals(assignedPort)) targetNode.configuration.put("keyFromFlow", "true");
            if ("iv".equals(assignedPort)) targetNode.configuration.put("ivFromFlow", "true");
            if ("aad".equals(assignedPort)) targetNode.configuration.put("aadFromFlow", "true");

            adj.get(conn.from).add(conn.to);
            inDegree.put(conn.to, inDegree.get(conn.to) + 1);
        }

        // Validate configuration after graph ports are known: a key may be supplied by an upstream key-material node.
        for (ProcessDefinition.Node node : definition.nodes) {
            getHandlerFor(node.type).validateConfiguration(node);
        }

        // Validate missing required ports
        for (String id : ids) {
            ProcessDefinition.Node node = nodeMap.get(id);
            ProcessNodeHandler handler = getHandlerFor(node.type);
            for (ProcessNodeHandler.PortDefinition port : handler.inputPorts(node)) {
                if (port.required() && !portBindings.get(id).containsKey(port.name())) {
                    throw new IllegalArgumentException("Missing required connection to port '" + port.name() + "' on node: " + node.label);
                }
            }
        }

        // Check input limits and representations
        Map<String, Representation> repMap = new HashMap<>();

        // Cycle detection and representation matching via topological sort
        List<String> sorted = new ArrayList<>();
        List<String> zeroIn = ids.stream().filter(id -> inDegree.get(id) == 0).collect(Collectors.toList());

        while (!zeroIn.isEmpty()) {
            String curr = zeroIn.remove(0);
            sorted.add(curr);

            ProcessDefinition.Node sourceNode = nodeMap.get(curr);
            ProcessNodeHandler sourceHandler = getHandlerFor(sourceNode.type);

            Map<String, Representation> inputsForSource = new HashMap<>();
            if (inDegree.get(curr) > 0) {
                // Not zero-in initially, it was processed
                for (Map.Entry<String, String> entry : portBindings.get(curr).entrySet()) {
                    inputsForSource.put(entry.getKey(), repMap.getOrDefault(entry.getValue(), Representation.BINARY));
                }
            }
            Representation outRep = sourceHandler.outputRepresentation(sourceNode, inputsForSource);
            repMap.put(curr, outRep);

            for (String neighbor : adj.get(curr)) {
                ProcessDefinition.Node targetNode = nodeMap.get(neighbor);
                ProcessNodeHandler targetHandler = getHandlerFor(targetNode.type);

                String targetPortName = null;
                for (Map.Entry<String, String> entry : portBindings.get(neighbor).entrySet()) {
                    if (entry.getValue().equals(curr)) {
                        targetPortName = entry.getKey();
                        break;
                    }
                }

                final String pName = targetPortName;
                ProcessNodeHandler.PortDefinition portDef = targetHandler.inputPorts(targetNode).stream()
                    .filter(p -> p.name().equals(pName))
                    .findFirst().orElseThrow();

                if (!portDef.acceptedRepresentations().contains(outRep)) {
                    throw new IllegalArgumentException(String.format("Invalid connection: port '%s' on '%s' expects %s but receives %s from '%s'", targetPortName, targetNode.label, portDef.acceptedRepresentations(), outRep, sourceNode.label));
                }

                int in = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, in);
                if (in == 0) {
                    zeroIn.add(neighbor);
                }
            }
        }

        if (sorted.size() != ids.size()) {
            throw new IllegalArgumentException("Cycle detected in process workflow");
        }

        return repMap;
    }

    public static Map<String, FlowValue> execute(ProcessDefinition definition, ExecutionContext context) throws Exception {
        validate(definition);

        Map<String, FlowValue> values = new HashMap<>();
        Set<String> completed = new HashSet<>();

        int stepCount = 1;

        try {
            while (completed.size() < definition.nodes.size()) {
                boolean progressed = false;

                for (ProcessDefinition.Node node : definition.nodes) {
                    if (completed.contains(node.id) || !inputsReady(node, definition, completed)) {
                        continue;
                    }

                    Map<String, FlowValue> inputs = inputFor(node, definition, values);

                    FlowValue primaryInput = inputs.get("payload");
                    if (primaryInput == null) primaryInput = inputs.get("input");

                    Representation inRep = primaryInput != null ? primaryInput.representation() : null;
                    int inSize = primaryInput != null ? primaryInput.bytes().length : 0;

                    if (context.eventListener() != null) {
                        context.eventListener().accept(new NodeExecutionEvent(
                            node.id, stepCount, node.label, node.type, NodeExecutionState.RUNNING, Duration.ZERO,
                            inRep, inSize, null, 0, "Running"
                        ));
                    }

                    long start = System.nanoTime();
                    try {
                        ProcessNodeHandler handler = getHandlerFor(node.type);

                        FlowValue output = handler.execute(node, inputs, context);
                        values.put(node.id, output);

                        completed.add(node.id);
                        progressed = true;
                        if (context.eventListener() != null) {
                            context.eventListener().accept(new NodeExecutionEvent(
                                node.id, stepCount, node.label, node.type, NodeExecutionState.SUCCESS, Duration.ofNanos(System.nanoTime() - start),
                                inRep, inSize, output != null ? output.representation() : null, output != null ? output.bytes().length : 0, "Success"
                            ));
                        }
                        stepCount++;
                    } catch (Exception e) {
                        if (context.eventListener() != null) {
                            context.eventListener().accept(new NodeExecutionEvent(
                                node.id, stepCount, node.label, node.type, NodeExecutionState.ERROR, Duration.ofNanos(System.nanoTime() - start),
                                inRep, inSize, null, 0, e.getMessage()
                            ));
                        }
                        throw e;
                    }
                }

                if (!progressed) {
                    throw new IllegalArgumentException("The workflow has a cycle or a missing source block");
                }
            }
        } catch (Exception e) {
            // Mark remaining nodes as skipped
            for (ProcessDefinition.Node node : definition.nodes) {
                if (!completed.contains(node.id)) {
                    if (context.eventListener() != null) {
                        context.eventListener().accept(new NodeExecutionEvent(
                            node.id, stepCount, node.label, node.type, NodeExecutionState.SKIPPED, Duration.ZERO,
                            null, 0, null, 0, "Skipped due to previous error"
                        ));
                    }
                }
            }
            throw e;
        }

        return values;
    }

    public static Map<String, FlowValue> execute(ProcessDefinition definition) throws Exception {
        return execute(definition, new ExecutionContext(FileWritePolicy.FAIL_IF_EXISTS, null));
    }

    /** Convenience method that formats outputs as Strings for UI binding. */
    public static Map<String, String> executeAndRender(ProcessDefinition definition, ExecutionContext context) throws Exception {
        Map<String, FlowValue> raw = execute(definition, context);
        Map<String, String> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, FlowValue> entry : raw.entrySet()) {
            rendered.put(entry.getKey(), entry.getValue().render());
        }
        return rendered;
    }

    public static Map<String, String> executeAndRender(ProcessDefinition definition) throws Exception {
        return executeAndRender(definition, new ExecutionContext(FileWritePolicy.FAIL_IF_EXISTS, null));
    }

    public static ProcessNodeHandler getHandlerFor(String type) {
        for (ProcessNodeHandler handler : HANDLERS) {
            if (handler.supportedTypes().contains(type)) {
                return handler;
            }
        }
        throw new IllegalArgumentException("Node type is not executable: " + type);
    }

    private static Map<String, FlowValue> inputFor(ProcessDefinition.Node node, ProcessDefinition definition, Map<String, FlowValue> values) {
        Map<String, FlowValue> inputs = new HashMap<>();
        for (ProcessDefinition.Connection connection : definition.connections) {
            if (node.id.equals(connection.to)) {
                inputs.put(connection.targetPort, values.getOrDefault(connection.from, FlowValue.binary(new byte[0])));
            }
        }
        return inputs;
    }

    private static boolean inputsReady(ProcessDefinition.Node node, ProcessDefinition definition, Set<String> completed) {
        for (ProcessDefinition.Connection connection : definition.connections) {
            if (node.id.equals(connection.to) && !completed.contains(connection.from)) {
                return false;
            }
        }
        return true;
    }
}
