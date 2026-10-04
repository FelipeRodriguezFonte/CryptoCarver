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

    private final View view;
    private final Deque<ProcessDesignerController.DesignerCommand> undoStack = new ArrayDeque<>();
    private final Deque<ProcessDesignerController.DesignerCommand> redoStack = new ArrayDeque<>();

    ProcessUndoRedoCoordinator(View view) {
        this.view = Objects.requireNonNull(view);
    }

    void execute(ProcessDesignerController.DesignerCommand command) {
        command.redo();
        push(command);
    }

    private void push(ProcessDesignerController.DesignerCommand command) {
        undoStack.push(command);
        trimUndoStack();
        redoStack.clear();
    }

    void recordStateChange(String description, ProcessDefinition before) {
        ProcessDefinition beforeSnapshot = snapshot(before);
        ProcessDefinition afterSnapshot = snapshot(view.currentDefinition().get());
        push(new SnapshotCommand(description, beforeSnapshot, afterSnapshot));
    }

    void undo() {
        if (undoStack.isEmpty()) return;
        ProcessDesignerController.DesignerCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
    }

    void redo() {
        if (redoStack.isEmpty()) return;
        ProcessDesignerController.DesignerCommand command = redoStack.pop();
        command.redo();
        undoStack.push(command);
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

    private final class SnapshotCommand implements ProcessDesignerController.DesignerCommand {
        private final String description;
        private final ProcessDefinition before;
        private final ProcessDefinition after;

        private SnapshotCommand(String description, ProcessDefinition before, ProcessDefinition after) {
            this.description = description;
            this.before = before;
            this.after = after;
        }

        @Override public void undo() { restore(before); }
        @Override public void redo() { restore(after); }

        private void restore(ProcessDefinition definition) {
            view.restoreDefinition().accept(snapshot(definition));
        }
    }
}
