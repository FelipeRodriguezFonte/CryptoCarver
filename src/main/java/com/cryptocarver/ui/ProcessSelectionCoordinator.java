package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coordinates Process Designer node selection and its descriptor-driven inspector. */
final class ProcessSelectionCoordinator {
    record View(Supplier<List<ProcessDefinition.Node>> nodes,
                Supplier<List<ProcessDefinition.Connection>> connections,
                Supplier<LinkedHashSet<String>> selectedNodeIds,
                Supplier<ProcessDefinition.Node> selectedNode,
                Consumer<ProcessDefinition.Node> setSelectedNode,
                Supplier<ProcessDefinition.Connection> selectedConnection,
                Consumer<ProcessDefinition.Connection> setSelectedConnection,
                Runnable saveSelectedNodeSettings,
                Supplier<Label> selectedNodeLabel,
                Supplier<VBox> nodeNameFieldGroup,
                Supplier<TextField> nodeNameField,
                Supplier<Pane> dynamicInspectorContainer,
                Supplier<Map<String, Map<String, char[]>>> transientSecrets,
                Consumer<NodeInspectorRenderer> setDynamicInspectorRenderer,
                Consumer<ProcessDefinition.Node> updateRepresentationContract,
                Runnable redraw,
                Supplier<Button> connectSelectedButton,
                Supplier<MenuButton> connectMenuButton,
                Supplier<Button> reverseConnectionButton,
                Supplier<Button> reverseConnectionToolbarButton,
                Supplier<Button> deleteSelectedButton,
                Supplier<List<String>> orderedConnectionPair,
                Function<ProcessDefinition.Node, Representation> outputRepresentationOf,
                Supplier<ProcessDefinition.Connection> connectionBetweenSelectedNodes,
                Function<String, String> nodeLabel,
                Function<String, String> portLabel,
                Consumer<String> connectToPort,
                Function<String, String> text) {
        View {
            Objects.requireNonNull(nodes);
            Objects.requireNonNull(connections);
            Objects.requireNonNull(selectedNodeIds);
            Objects.requireNonNull(selectedNode);
            Objects.requireNonNull(setSelectedNode);
            Objects.requireNonNull(selectedConnection);
            Objects.requireNonNull(setSelectedConnection);
            Objects.requireNonNull(saveSelectedNodeSettings);
            Objects.requireNonNull(selectedNodeLabel);
            Objects.requireNonNull(nodeNameFieldGroup);
            Objects.requireNonNull(nodeNameField);
            Objects.requireNonNull(dynamicInspectorContainer);
            Objects.requireNonNull(transientSecrets);
            Objects.requireNonNull(setDynamicInspectorRenderer);
            Objects.requireNonNull(updateRepresentationContract);
            Objects.requireNonNull(redraw);
            Objects.requireNonNull(connectSelectedButton);
            Objects.requireNonNull(connectMenuButton);
            Objects.requireNonNull(reverseConnectionButton);
            Objects.requireNonNull(reverseConnectionToolbarButton);
            Objects.requireNonNull(deleteSelectedButton);
            Objects.requireNonNull(orderedConnectionPair);
            Objects.requireNonNull(outputRepresentationOf);
            Objects.requireNonNull(connectionBetweenSelectedNodes);
            Objects.requireNonNull(nodeLabel);
            Objects.requireNonNull(portLabel);
            Objects.requireNonNull(connectToPort);
            Objects.requireNonNull(text);
        }
    }

    void selectNodeById(View view, String nodeId) {
        if (nodeId == null) return;
        ProcessDefinition.Node target = view.nodes().get().stream()
                .filter(node -> nodeId.equals(node.id)).findFirst().orElse(null);
        if (target != null) {
            select(view, target);
            view.redraw().run();
        }
    }

    void select(View view, ProcessDefinition.Node node) {
        view.saveSelectedNodeSettings().run();
        view.setSelectedConnection().accept(null);
        LinkedHashSet<String> selectedNodeIds = view.selectedNodeIds().get();
        if (!selectedNodeIds.contains(node.id) && selectedNodeIds.size() == 2) selectedNodeIds.clear();
        selectedNodeIds.add(node.id);
        view.setSelectedNode().accept(node);
        view.selectedNodeLabel().get().setText(node.type + " · " + node.label);
        VBox nodeNameFieldGroup = view.nodeNameFieldGroup().get();
        if (nodeNameFieldGroup != null) {
            nodeNameFieldGroup.setVisible(true);
            nodeNameFieldGroup.setManaged(true);
        }
        TextField nodeNameField = view.nodeNameField().get();
        if (nodeNameField != null) nodeNameField.setText(node.label == null ? "" : node.label);

        NodeDescriptor descriptor = NodeCatalog.descriptor(node.type).orElse(null);
        Pane dynamicInspectorContainer = view.dynamicInspectorContainer().get();
        if (descriptor != null && dynamicInspectorContainer != null) {
            Map<String, char[]> secrets = view.transientSecrets().get()
                    .computeIfAbsent(node.id, ignored -> new HashMap<>());
            NodeInspectorRenderer renderer = NodeInspectorRenderer.render(
                    descriptor,
                    node,
                    dynamicInspectorContainer,
                    secrets,
                    key -> {
                        view.updateRepresentationContract().accept(view.selectedNode().get());
                        view.redraw().run();
                    }
            );
            view.setDynamicInspectorRenderer().accept(renderer);
        }

        view.updateRepresentationContract().accept(node);
        updateSelectionUi(view);
    }

    void updateSelectionUi(View view) {
        Button connectSelectedButton = view.connectSelectedButton().get();
        MenuButton connectMenuButton = view.connectMenuButton().get();
        if (connectSelectedButton == null && connectMenuButton == null) return;
        LinkedHashSet<String> selectedNodeIds = view.selectedNodeIds().get();
        int count = selectedNodeIds.size();

        if (count == 2) {
            List<String> pair = view.orderedConnectionPair().get();
            ProcessDefinition.Node destination = view.nodes().get().stream()
                    .filter(node -> node.id.equals(pair.get(1))).findFirst().orElse(null);
            if (destination != null) {
                ProcessNodeHandler handler = ProcessEngine.getHandlerFor(destination.type);
                List<ProcessNodeHandler.PortDefinition> ports = handler != null
                        ? handler.inputPorts(destination) : List.of();
                ProcessDefinition.Node sourceNode = view.nodes().get().stream()
                        .filter(node -> node.id.equals(pair.get(0))).findFirst().orElse(null);
                Representation sourceRepresentation = sourceNode == null
                        ? null : view.outputRepresentationOf().apply(sourceNode);
                List<ProcessNodeHandler.PortDefinition> availablePorts = ports.stream()
                        .filter(port -> view.connections().get().stream()
                                .noneMatch(connection -> connection.to.equals(destination.id)
                                        && port.name().equals(connection.targetPort)))
                        .filter(port -> sourceRepresentation == null
                                || port.acceptedRepresentations().contains(sourceRepresentation))
                        .toList();

                if (availablePorts.size() > 1) {
                    if (connectSelectedButton != null) {
                        connectSelectedButton.setVisible(false);
                        connectSelectedButton.setManaged(false);
                    }
                    if (connectMenuButton != null) {
                        connectMenuButton.getItems().clear();
                        connectMenuButton.setText("Connect " + view.nodeLabel().apply(pair.get(0)) + " to...");
                        for (ProcessNodeHandler.PortDefinition port : availablePorts) {
                            MenuItem item = new MenuItem("Connect to " + view.portLabel().apply(port.name()));
                            item.setOnAction(event -> view.connectToPort().accept(port.name()));
                            connectMenuButton.getItems().add(item);
                        }
                        connectMenuButton.setVisible(true);
                        connectMenuButton.setManaged(true);
                    }
                } else {
                    if (connectMenuButton != null) {
                        connectMenuButton.getItems().clear();
                        connectMenuButton.setVisible(false);
                        connectMenuButton.setManaged(false);
                    }
                    if (connectSelectedButton != null) {
                        connectSelectedButton.setVisible(true);
                        connectSelectedButton.setManaged(true);
                        connectSelectedButton.setDisable(availablePorts.isEmpty());
                        connectSelectedButton.setText(availablePorts.isEmpty()
                                ? "No compatible free input ports"
                                : "Connect " + view.nodeLabel().apply(pair.get(0)) + " → "
                                        + view.nodeLabel().apply(pair.get(1)));
                        connectSelectedButton.setOnAction(event -> view.connectToPort().accept(
                                availablePorts.isEmpty() ? null : availablePorts.get(0).name()));
                    }
                }
            }
        } else {
            if (connectMenuButton != null) {
                connectMenuButton.getItems().clear();
                connectMenuButton.setVisible(false);
                connectMenuButton.setManaged(false);
            }
            if (connectSelectedButton != null) {
                connectSelectedButton.setVisible(true);
                connectSelectedButton.setManaged(true);
                connectSelectedButton.setDisable(true);
                connectSelectedButton.setText(view.text().apply("module.process.selectTwo"));
            }
        }

        boolean hasSelectedConnection = view.selectedConnection().get() != null
                || view.connectionBetweenSelectedNodes().get() != null;
        Button reverseConnectionButton = view.reverseConnectionButton().get();
        if (reverseConnectionButton != null) reverseConnectionButton.setDisable(!hasSelectedConnection);
        Button reverseConnectionToolbarButton = view.reverseConnectionToolbarButton().get();
        if (reverseConnectionToolbarButton != null) reverseConnectionToolbarButton.setDisable(!hasSelectedConnection);
        Button deleteSelectedButton = view.deleteSelectedButton().get();
        if (deleteSelectedButton != null) {
            deleteSelectedButton.setText(hasSelectedConnection
                    ? view.text().apply("module.process.deleteSelectedConnectionShortcut")
                    : view.text().apply("module.process.deleteSelectedShortcut"));
            deleteSelectedButton.setDisable(view.selectedNode().get() == null
                    && view.selectedConnection().get() == null && selectedNodeIds.isEmpty());
        }
    }
}
