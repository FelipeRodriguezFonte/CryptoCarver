package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.paint.Color;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coordinates graph connections and the transient port-drag interaction. */
final class ProcessConnectionCoordinator {
    record View(Supplier<List<ProcessDefinition.Node>> nodes,
                Supplier<List<ProcessDefinition.Connection>> connections,
                Supplier<LinkedHashSet<String>> selectedNodeIds,
                Supplier<ProcessDefinition.Node> selectedNode,
                Consumer<ProcessDefinition.Node> setSelectedNode,
                Supplier<Pane> workflowCanvas,
                Supplier<List<Circle>> inputPortHandles,
                Function<ProcessDefinition.Node, Representation> outputRepresentationOf,
                Consumer<CubicCurve> updateCurveControls,
                Supplier<ProcessDefinition> currentDefinition,
                Supplier<List<String>> orderedConnectionPair,
                Function<String, String> nodeLabel,
                Function<String, String> portLabel,
                BiFunction<String, Object[], String> text,
                Supplier<TextArea> executionOutputArea,
                Runnable updateSelectionUi,
                Runnable redraw,
                BiConsumer<String, ProcessDefinition> recordStateChange) {
        View {
            Objects.requireNonNull(nodes);
            Objects.requireNonNull(connections);
            Objects.requireNonNull(selectedNodeIds);
            Objects.requireNonNull(selectedNode);
            Objects.requireNonNull(setSelectedNode);
            Objects.requireNonNull(workflowCanvas);
            Objects.requireNonNull(inputPortHandles);
            Objects.requireNonNull(outputRepresentationOf);
            Objects.requireNonNull(updateCurveControls);
            Objects.requireNonNull(currentDefinition);
            Objects.requireNonNull(orderedConnectionPair);
            Objects.requireNonNull(nodeLabel);
            Objects.requireNonNull(portLabel);
            Objects.requireNonNull(text);
            Objects.requireNonNull(executionOutputArea);
            Objects.requireNonNull(updateSelectionUi);
            Objects.requireNonNull(redraw);
            Objects.requireNonNull(recordStateChange);
        }
    }

    private ProcessDefinition.Node connectionDragSourceNode;
    private CubicCurve interactiveConnectionCurve;

    ProcessDefinition.Node dragSourceNode() { return connectionDragSourceNode; }

    CubicCurve interactiveCurve() { return interactiveConnectionCurve; }

    boolean isDragging() { return interactiveConnectionCurve != null && connectionDragSourceNode != null; }

    void startConnectionDrag(View view, ProcessDefinition.Node sourceNode) {
        connectionDragSourceNode = sourceNode;
        interactiveConnectionCurve = new CubicCurve();
        interactiveConnectionCurve.setStartX(sourceNode.x + 150);
        interactiveConnectionCurve.setStartY(sourceNode.y + 35);
        interactiveConnectionCurve.setEndX(sourceNode.x + 150);
        interactiveConnectionCurve.setEndY(sourceNode.y + 35);
        view.updateCurveControls().accept(interactiveConnectionCurve);
        interactiveConnectionCurve.setFill(null);
        interactiveConnectionCurve.setStroke(Color.web("#f6c344"));
        interactiveConnectionCurve.setStrokeWidth(2.0);
        interactiveConnectionCurve.getStrokeDashArray().addAll(6.0, 4.0);
        view.workflowCanvas().get().getChildren().add(interactiveConnectionCurve);

        Representation sourceRepresentation = view.outputRepresentationOf().apply(sourceNode);
        for (Circle circle : view.inputPortHandles().get()) {
            if (circle.getUserData() instanceof ProcessDesignerController.PortHandleData data) {
                if (data.node().id.equals(sourceNode.id)) {
                    circle.setOpacity(0.3);
                    continue;
                }
                boolean occupied = view.connections().get().stream()
                        .anyMatch(connection -> connection.to.equals(data.node().id)
                                && data.port().name().equals(connection.targetPort));
                boolean compatible = !occupied && (sourceRepresentation == null
                        || data.port().acceptedRepresentations().isEmpty()
                        || data.port().acceptedRepresentations().contains(sourceRepresentation));
                circle.setOpacity(compatible ? 1.0 : 0.3);
            }
        }
    }

    void updateInteractiveCurve(View view, double startX, double startY, double endX, double endY) {
        if (interactiveConnectionCurve == null) return;
        interactiveConnectionCurve.setStartX(startX);
        interactiveConnectionCurve.setStartY(startY);
        interactiveConnectionCurve.setEndX(endX);
        interactiveConnectionCurve.setEndY(endY);
        view.updateCurveControls().accept(interactiveConnectionCurve);
    }

    void cancelConnectionDrag(View view) {
        if (interactiveConnectionCurve != null) {
            view.workflowCanvas().get().getChildren().remove(interactiveConnectionCurve);
            interactiveConnectionCurve = null;
        }
        connectionDragSourceNode = null;
        for (Circle circle : view.inputPortHandles().get()) circle.setOpacity(1.0);
    }

    void completeConnectionDragToPort(View view, ProcessDefinition.Node from,
                                      ProcessDefinition.Node to, String targetPort) {
        cancelConnectionDrag(view);
        if (from == null || to == null) return;
        view.selectedNodeIds().get().clear();
        view.selectedNodeIds().get().add(from.id);
        view.selectedNodeIds().get().add(to.id);
        connectToPort(view, targetPort);
    }

    void completeConnectionDrag(View view, ProcessDefinition.Node from, ProcessDefinition.Node to) {
        completeConnectionDrag(view, from, to, 0, 0);
    }

    void completeConnectionDrag(View view, ProcessDefinition.Node from, ProcessDefinition.Node to,
                                double screenX, double screenY) {
        cancelConnectionDrag(view);
        if (from == null || to == null) return;
        ProcessNodeHandler destinationHandler = ProcessEngine.getHandlerFor(to.type);
        if (destinationHandler == null) return;
        List<ProcessNodeHandler.PortDefinition> ports = destinationHandler.inputPorts(to);
        Representation sourceRepresentation = view.outputRepresentationOf().apply(from);
        List<ProcessNodeHandler.PortDefinition> available = ports.stream()
                .filter(port -> view.connections().get().stream()
                        .noneMatch(connection -> connection.to.equals(to.id)
                                && port.name().equals(connection.targetPort)))
                .filter(port -> sourceRepresentation == null
                        || port.acceptedRepresentations().contains(sourceRepresentation))
                .toList();

        if (available.isEmpty()) {
            TextArea output = view.executionOutputArea().get();
            if (output != null) output.setText(view.text().apply("module.process.feedback.incompatible",
                    new Object[] {from.label, to.label}));
            return;
        }

        if (available.size() == 1) {
            view.selectedNodeIds().get().clear();
            view.selectedNodeIds().get().add(from.id);
            view.selectedNodeIds().get().add(to.id);
            connectToPort(view, available.get(0).name());
            return;
        }

        Pane workflowCanvas = view.workflowCanvas().get();
        if (workflowCanvas != null && workflowCanvas.getScene() != null
                && workflowCanvas.getScene().getWindow() != null) {
            ContextMenu menu = new ContextMenu();
            for (ProcessNodeHandler.PortDefinition port : available) {
                MenuItem item = new MenuItem(view.text().apply("module.process.connectToPort",
                        new Object[] {view.portLabel().apply(port.name())}));
                item.setOnAction(event -> completeConnectionDragToPort(view, from, to, port.name()));
                menu.getItems().add(item);
            }
            if (screenX > 0 && screenY > 0) {
                menu.show(workflowCanvas.getScene().getWindow(), screenX, screenY);
            } else {
                Point2D point = workflowCanvas.localToScreen(to.x + 20, to.y + 20);
                if (point != null) {
                    menu.show(workflowCanvas.getScene().getWindow(), point.getX(), point.getY());
                } else {
                    menu.show(workflowCanvas.getScene().getWindow());
                }
            }
        } else {
            TextArea output = view.executionOutputArea().get();
            if (output != null) {
                String portsText = available.stream().map(ProcessNodeHandler.PortDefinition::name)
                        .collect(java.util.stream.Collectors.joining(", "));
                output.setText(view.text().apply("module.process.feedback.ambiguousPorts",
                        new Object[] {portsText}));
            }
        }
    }

    void connectToPort(View view, String targetPort) {
        LinkedHashSet<String> selectedNodeIds = view.selectedNodeIds().get();
        if (selectedNodeIds.size() != 2) return;
        ProcessDefinition before = ProcessUndoRedoCoordinator.snapshot(view.currentDefinition().get());
        List<String> pair = view.orderedConnectionPair().get();
        String source = pair.get(0);
        String destination = pair.get(1);

        // Resolve the engine's default input so a portless link cannot stack on an occupied port.
        String effectivePort = targetPort != null ? targetPort : defaultInputPort(view, destination);
        if (effectivePort != null) {
            connectionsFor(view).removeIf(connection -> connection.to.equals(destination)
                    && connection.targetPort == null
                    && effectivePort.equals(defaultInputPort(view, destination)));
            String port = effectivePort;
            boolean occupied = connectionsFor(view).stream()
                    .anyMatch(connection -> connection.to.equals(destination) && port.equals(connection.targetPort));
            if (occupied) {
                view.executionOutputArea().get().setText(view.text().apply("module.process.connectionOccupied",
                        new Object[] {effectivePort, view.nodeLabel().apply(destination)}));
                return;
            }
        } else {
            connectionsFor(view).removeIf(connection -> connection.to.equals(destination)
                    && connection.targetPort == null);
        }

        ProcessDefinition.Connection newConnection = new ProcessDefinition.Connection(source, destination, targetPort);
        connectionsFor(view).add(newConnection);
        if ("key".equals(targetPort)) {
            view.nodes().get().stream().filter(node -> node.id.equals(destination))
                    .findFirst().ifPresent(node -> node.configuration.put("keyFromFlow", "true"));
        }

        ProcessDefinition.Node sourceNode = view.nodes().get().stream()
                .filter(node -> node.id.equals(source)).findFirst().orElse(null);
        ProcessDefinition.Node destinationNode = view.nodes().get().stream()
                .filter(node -> node.id.equals(destination)).findFirst().orElse(null);
        boolean keepReusableKeySourceSelected = "key".equals(targetPort) && isReusableKeySource(sourceNode);
        ProcessDefinition.Node selected = keepReusableKeySourceSelected ? sourceNode : destinationNode;
        view.setSelectedNode().accept(selected);
        selectedNodeIds.clear();
        if (selected != null) selectedNodeIds.add(selected.id);
        String portText = targetPort != null ? " [" + targetPort + "]" : "";
        view.executionOutputArea().get().setText(view.text().apply("module.process.connected", new Object[] {
                view.nodeLabel().apply(source), view.nodeLabel().apply(destination), portText
                        + (keepReusableKeySourceSelected ? ". Select another crypto node to reuse this key." : "")
        }));
        view.updateSelectionUi().run();
        view.redraw().run();
        view.recordStateChange().accept("Connect nodes", before);
    }

    private static List<ProcessDefinition.Connection> connectionsFor(View view) { return view.connections().get(); }

    private static String defaultInputPort(View view, String nodeId) {
        ProcessDefinition.Node node = view.nodes().get().stream()
                .filter(candidate -> candidate.id.equals(nodeId)).findFirst().orElse(null);
        if (node == null) return null;
        List<ProcessNodeHandler.PortDefinition> ports;
        try {
            ports = ProcessEngine.getHandlerFor(node.type).inputPorts(node);
        } catch (RuntimeException unknownType) {
            return null;
        }
        if (ports.size() == 1) return ports.get(0).name();
        boolean payloadAndKey = ports.stream().anyMatch(port -> "payload".equals(port.name()))
                && ports.stream().anyMatch(port -> "key".equals(port.name()));
        return payloadAndKey ? "payload" : null;
    }

    private static boolean isReusableKeySource(ProcessDefinition.Node node) {
        return node != null && ("AES_KEY_GENERATE".equals(node.type)
                || "KDF_PBKDF2".equals(node.type) || "RSA_KEYPAIR_GENERATE".equals(node.type));
    }
}
