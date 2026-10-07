package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Installs the keyboard and pointer handlers owned by the process canvas. */
final class ProcessCanvasEventsCoordinator {
    @FunctionalInterface
    interface InteractiveCurveUpdater {
        void update(double startX, double startY, double endX, double endY);
    }

    record View(Supplier<Pane> workflowCanvas,
                DoubleSupplier currentZoom,
                DoubleConsumer setZoom,
                Runnable handleDeleteSelected,
                Runnable handleUndo,
                Runnable handleRedo,
                Runnable handleDuplicateSelected,
                Supplier<ProcessDefinition.Node> selectedNode,
                Runnable clearSelection,
                Runnable updateSelectionUi,
                Runnable redraw,
                Runnable updateCanvasGeometry,
                BooleanSupplier connectionDragging,
                Supplier<ProcessDefinition.Node> connectionDragSourceNode,
                BooleanSupplier connectionCurveActive,
                Runnable cancelConnectionDrag,
                InteractiveCurveUpdater updateInteractiveCurve) {
        View {
            Objects.requireNonNull(workflowCanvas);
            Objects.requireNonNull(currentZoom);
            Objects.requireNonNull(setZoom);
            Objects.requireNonNull(handleDeleteSelected);
            Objects.requireNonNull(handleUndo);
            Objects.requireNonNull(handleRedo);
            Objects.requireNonNull(handleDuplicateSelected);
            Objects.requireNonNull(selectedNode);
            Objects.requireNonNull(clearSelection);
            Objects.requireNonNull(updateSelectionUi);
            Objects.requireNonNull(redraw);
            Objects.requireNonNull(updateCanvasGeometry);
            Objects.requireNonNull(connectionDragging);
            Objects.requireNonNull(connectionDragSourceNode);
            Objects.requireNonNull(connectionCurveActive);
            Objects.requireNonNull(cancelConnectionDrag);
            Objects.requireNonNull(updateInteractiveCurve);
        }
    }

    void initCanvasEventHandlers(View view) {
        Pane workflowCanvas = view.workflowCanvas().get();
        if (workflowCanvas == null) return;

        DoubleSupplier currentZoom = view.currentZoom();
        DoubleConsumer setZoom = view.setZoom();
        Runnable handleDeleteSelected = view.handleDeleteSelected();
        Runnable handleUndo = view.handleUndo();
        Runnable handleRedo = view.handleRedo();
        Runnable handleDuplicateSelected = view.handleDuplicateSelected();
        Supplier<ProcessDefinition.Node> selectedNode = view.selectedNode();
        Runnable clearSelection = view.clearSelection();
        Runnable updateSelectionUi = view.updateSelectionUi();
        Runnable redraw = view.redraw();
        Runnable updateCanvasGeometry = view.updateCanvasGeometry();
        BooleanSupplier connectionDragging = view.connectionDragging();
        Supplier<ProcessDefinition.Node> connectionDragSourceNode = view.connectionDragSourceNode();
        BooleanSupplier connectionCurveActive = view.connectionCurveActive();
        Runnable cancelConnectionDrag = view.cancelConnectionDrag();
        InteractiveCurveUpdater updateInteractiveCurve = view.updateInteractiveCurve();

        workflowCanvas.setOnScroll(event -> {
            if (event.isControlDown() || event.isShortcutDown()) {
                double delta = event.getDeltaY() > 0 ? 0.08 : -0.08;
                setZoom.accept(currentZoom.getAsDouble() + delta);
                event.consume();
            }
        });

        workflowCanvas.setOnMouseClicked(event -> {
            if (event.getTarget() == workflowCanvas) {
                clearSelection.run();
                updateSelectionUi.run();
                redraw.run();
            }
        });

        workflowCanvas.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                handleDeleteSelected.run();
                event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
                if (event.isShiftDown()) handleRedo.run();
                else handleUndo.run();
                event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.Y) {
                handleRedo.run();
                event.consume();
            } else if (event.isShortcutDown() && event.getCode() == KeyCode.D) {
                handleDuplicateSelected.run();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                clearSelection.run();
                if (connectionCurveActive.getAsBoolean()) cancelConnectionDrag.run();
                updateSelectionUi.run();
                redraw.run();
                event.consume();
            } else if (event.getCode().isArrowKey()) {
                double step = event.isShiftDown() ? 10.0 : 1.0;
                ProcessDefinition.Node selected = selectedNode.get();
                if (selected != null) {
                    if (event.getCode() == KeyCode.UP) selected.y -= step;
                    else if (event.getCode() == KeyCode.DOWN) selected.y += step;
                    else if (event.getCode() == KeyCode.LEFT) selected.x -= step;
                    else if (event.getCode() == KeyCode.RIGHT) selected.x += step;
                    updateCanvasGeometry.run();
                    redraw.run();
                    event.consume();
                }
            }
        });

        workflowCanvas.setOnMouseMoved(event -> {
            if (connectionDragging.getAsBoolean()) {
                ProcessDefinition.Node sourceNode = connectionDragSourceNode.get();
                Point2D local = workflowCanvas.sceneToLocal(event.getSceneX(), event.getSceneY());
                updateInteractiveCurve.update(sourceNode.x + 150, sourceNode.y + 35, local.getX(), local.getY());
            }
        });
    }
}
