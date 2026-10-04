package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates data transformations for node duplication and automatic canvas layout. */
final class ProcessLayoutCoordinator {
    record View(Supplier<ProcessDefinition.Node> selectedNode,
                Supplier<ProcessDefinition> currentDefinition,
                Consumer<ProcessDefinition.Node> addNode,
                Consumer<ProcessDefinition.Node> selectNode,
                Runnable refreshCanvas,
                BiConsumer<String, ProcessDefinition> recordStateChange) {
        View {
            Objects.requireNonNull(selectedNode);
            Objects.requireNonNull(currentDefinition);
            Objects.requireNonNull(addNode);
            Objects.requireNonNull(selectNode);
            Objects.requireNonNull(refreshCanvas);
            Objects.requireNonNull(recordStateChange);
        }
    }

    void duplicateSelected(View view) {
        ProcessDefinition.Node selected = view.selectedNode().get();
        if (selected == null) return;

        ProcessDefinition before = ProcessUndoRedoCoordinator.snapshot(view.currentDefinition().get());
        ProcessDefinition.Node duplicate = duplicate(selected);
        view.addNode().accept(duplicate);
        view.selectNode().accept(duplicate);
        view.refreshCanvas().run();
        view.recordStateChange().accept("Duplicate node", before);
    }

    void tidyLayout(View view) {
        ProcessDefinition definition = view.currentDefinition().get();
        if (definition.nodes.isEmpty()) return;

        ProcessDefinition before = ProcessUndoRedoCoordinator.snapshot(definition);
        var order = ProcessValidator.computeTopologicalOrder(definition);
        Map<String, ProcessDefinition.Node> nodesById = new HashMap<>();
        for (ProcessDefinition.Node node : definition.nodes) nodesById.putIfAbsent(node.id, node);

        Map<String, Integer> depthMap = new HashMap<>();
        Map<Integer, Integer> layerCounts = new HashMap<>();
        for (String id : order) {
            int maxParentDepth = -1;
            for (ProcessDefinition.Connection connection : definition.connections) {
                if (connection.to.equals(id)) {
                    int parentDepth = depthMap.getOrDefault(connection.from, 0);
                    maxParentDepth = Math.max(maxParentDepth, parentDepth);
                }
            }
            int depth = maxParentDepth + 1;
            depthMap.put(id, depth);
            int row = layerCounts.getOrDefault(depth, 0);
            layerCounts.put(depth, row + 1);

            ProcessDefinition.Node node = nodesById.get(id);
            if (node != null) {
                node.x = 60 + depth * 220;
                node.y = 80 + row * 110;
            }
        }

        view.refreshCanvas().run();
        view.recordStateChange().accept("Tidy layout", before);
    }

    private static ProcessDefinition.Node duplicate(ProcessDefinition.Node selected) {
        ProcessDefinition.Node duplicate = new ProcessDefinition.Node(
                java.util.UUID.randomUUID().toString(), selected.type, selected.label + " (Copy)",
                selected.x + 30, selected.y + 30);
        duplicate.configuration.putAll(selected.configuration);
        duplicate.configuration.keySet().removeIf(key -> NodeCatalog.allSensitiveKeys().contains(key)
                || NodeCatalog.isSuppliedMarker(key) || key.endsWith("FromFlow"));
        return duplicate;
    }
}
