package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns Process Designer's bounded command history independently from its JavaFX controller. */
final class ProcessUndoRedoCoordinator {
    private static final int MAX_UNDO_STEPS = 60;

    record View(Supplier<ProcessDefinition> currentDefinition,
                Consumer<ProcessDefinition> restoreDefinition) {
        View {
            Objects.requireNonNull(currentDefinition);
            Objects.requireNonNull(restoreDefinition);
        }
    }

    private sealed interface Entry permits SnapshotEntry, PublicCommandEntry { }
    private record SnapshotEntry(String description, ProcessDefinition before, ProcessDefinition after) implements Entry { }
    private record PublicCommandEntry(ProcessDesignerController.DesignerCommand command) implements Entry { }

    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();

    void execute(ProcessDesignerController.DesignerCommand command) {
        command.redo();
        push(new PublicCommandEntry(command));
    }

    private void push(Entry entry) {
        undoStack.push(entry);
        trimUndoStack();
        redoStack.clear();
    }

    void recordStateChange(String description, ProcessDefinition before, View view) {
        ProcessDefinition beforeSnapshot = snapshot(before);
        ProcessDefinition afterSnapshot = snapshot(view.currentDefinition().get());
        push(new SnapshotEntry(description, beforeSnapshot, afterSnapshot));
    }

    void undo(View view) {
        if (undoStack.isEmpty()) return;
        Entry entry = undoStack.pop();
        if (entry instanceof SnapshotEntry snapshot) {
            view.restoreDefinition().accept(snapshot(snapshot.before()));
        } else if (entry instanceof PublicCommandEntry command) {
            command.command().undo();
        }
        redoStack.push(entry);
    }

    void redo(View view) {
        if (redoStack.isEmpty()) return;
        Entry entry = redoStack.pop();
        if (entry instanceof SnapshotEntry snapshot) {
            view.restoreDefinition().accept(snapshot(snapshot.after()));
        } else if (entry instanceof PublicCommandEntry command) {
            command.command().redo();
        }
        undoStack.push(entry);
        trimUndoStack();
    }

    static ProcessDefinition snapshot(ProcessDefinition definition) {
        if (definition == null) return null;
        ProcessDefinition copy = new ProcessDefinition();
        copy.name = definition.name;
        copy.version = definition.version;
        for (ProcessDefinition.Node node : definition.nodes) {
            ProcessDefinition.Node nodeCopy = new ProcessDefinition.Node(
                    node.id, node.type, node.label, node.x, node.y);
            if (node.configuration != null) nodeCopy.configuration.putAll(node.configuration);
            copy.nodes.add(nodeCopy);
        }
        for (ProcessDefinition.Connection connection : definition.connections) {
            copy.connections.add(new ProcessDefinition.Connection(
                    connection.from, connection.to, connection.targetPort));
        }
        return copy;
    }

    private void trimUndoStack() {
        while (undoStack.size() > MAX_UNDO_STEPS) undoStack.removeLast();
    }

}
