package com.cryptocarver.ui;

import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.Representation;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** Draws process nodes, ports, and connections on the JavaFX canvas. */
final class ProcessCanvasRenderer {
    record PortHandleData(ProcessDefinition.Node node, ProcessNodeHandler.PortDefinition port) { }

    @FunctionalInterface
    interface InteractiveCurveUpdater {
        void update(double startX, double startY, double endX, double endY);
    }

    @FunctionalInterface
    interface PortDropHandler {
        void drop(ProcessDefinition.Node from, ProcessDefinition.Node to, String targetPort);
    }

    @FunctionalInterface
    interface ScreenDropHandler {
        void drop(ProcessDefinition.Node from, ProcessDefinition.Node to, double screenX, double screenY);
    }

    record View(Supplier<Pane> workflowCanvas,
                Supplier<List<ProcessDefinition.Node>> nodes,
                Supplier<List<ProcessDefinition.Connection>> connections,
                Supplier<ProcessDefinition> currentDefinition,
                Supplier<LinkedHashSet<String>> selectedNodeIds,
                Supplier<ProcessDefinition.Node> selectedNode,
                Supplier<ProcessDefinition.Connection> selectedConnection,
                Consumer<ProcessDefinition.Connection> selectConnection,
                Supplier<Boolean> snapToGrid,
                BooleanSupplier connectionDragging,
                Supplier<ProcessDefinition.Node> connectionDragSourceNode,
                Consumer<ProcessDefinition.Node> select,
                Consumer<ProcessDefinition.Node> startConnectionDrag,
                InteractiveCurveUpdater updateInteractiveCurve,
                Runnable cancelConnectionDrag,
                PortDropHandler completeConnectionDragToPort,
                BiConsumer<ProcessDefinition.Node, ProcessDefinition.Node> completeConnectionDrag,
                ScreenDropHandler completeConnectionDragAtScreen,
                Runnable updateCanvasGeometry,
                Runnable incrementValidationCounter,
                BiConsumer<String, ProcessDefinition> recordStateChange,
                Function<String, String> portLabel) {
        View {
            java.util.Objects.requireNonNull(workflowCanvas);
            java.util.Objects.requireNonNull(nodes);
            java.util.Objects.requireNonNull(connections);
            java.util.Objects.requireNonNull(currentDefinition);
            java.util.Objects.requireNonNull(selectedNodeIds);
            java.util.Objects.requireNonNull(selectedNode);
            java.util.Objects.requireNonNull(selectedConnection);
            java.util.Objects.requireNonNull(selectConnection);
            java.util.Objects.requireNonNull(snapToGrid);
            java.util.Objects.requireNonNull(connectionDragging);
            java.util.Objects.requireNonNull(connectionDragSourceNode);
            java.util.Objects.requireNonNull(select);
            java.util.Objects.requireNonNull(startConnectionDrag);
            java.util.Objects.requireNonNull(updateInteractiveCurve);
            java.util.Objects.requireNonNull(cancelConnectionDrag);
            java.util.Objects.requireNonNull(completeConnectionDragToPort);
            java.util.Objects.requireNonNull(completeConnectionDrag);
            java.util.Objects.requireNonNull(completeConnectionDragAtScreen);
            java.util.Objects.requireNonNull(updateCanvasGeometry);
            java.util.Objects.requireNonNull(incrementValidationCounter);
            java.util.Objects.requireNonNull(recordStateChange);
            java.util.Objects.requireNonNull(portLabel);
        }
    }

    private final Map<String, StackPane> views = new LinkedHashMap<>();
    private final List<Circle> inputPortHandles = new ArrayList<>();

    void redraw(View view) {
        Pane workflowCanvas = view.workflowCanvas().get();
        if (workflowCanvas == null) return;
        workflowCanvas.getChildren().clear();
        views.clear();
        inputPortHandles.clear();

        Map<String, Representation> reps = new HashMap<>();
        try {
            view.incrementValidationCounter().run();
            // Validation normalizes default ports and derived flow markers, so it must only see a snapshot.
            reps = ProcessEngine.validate(ProcessUndoRedoCoordinator.snapshot(view.currentDefinition().get()));
        } catch (Exception ignored) { }

        for (ProcessDefinition.Connection connection : view.connections().get()) {
            ProcessDefinition.Node from = view.nodes().get().stream()
                    .filter(node -> node.id.equals(connection.from)).findFirst().orElse(null);
            ProcessDefinition.Node to = view.nodes().get().stream()
                    .filter(node -> node.id.equals(connection.to)).findFirst().orElse(null);
            if (from != null && to != null) addConnectionView(view, connection, from, to);
        }

        for (ProcessDefinition.Node node : view.nodes().get()) {
            StackPane nodeView = createNodeView(view, node, reps.get(node.id));
            views.put(node.id, nodeView);
            workflowCanvas.getChildren().add(nodeView);
        }

        view.updateCanvasGeometry().run();
    }

    void removeView(String nodeId) {
        views.remove(nodeId);
    }

    void clearViews() {
        views.clear();
    }

    List<Circle> inputPortHandles() {
        return inputPortHandles;
    }

    void updateCurveControls(CubicCurve curve) {
        double startX = curve.getStartX();
        double startY = curve.getStartY();
        double endX = curve.getEndX();
        double endY = curve.getEndY();
        double offset = Math.max(40, Math.abs(endX - startX) * 0.5);
        curve.setControlX1(startX + offset);
        curve.setControlY1(startY);
        curve.setControlX2(endX - offset);
        curve.setControlY2(endY);
    }

    private StackPane createNodeView(View view, ProcessDefinition.Node node, Representation rep) {
        String badge = rep != null ? " [" + rep.name() + "]" : "";
        Label label = new Label(node.label + badge);
        label.setWrapText(true);
        label.setMaxWidth(135);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");

        StackPane nodeView = new StackPane(label);
        nodeView.setLayoutX(node.x);
        nodeView.setLayoutY(node.y);
        nodeView.setPrefSize(150, 70);

        List<ProcessNodeHandler.PortDefinition> ports = ProcessEngine.getHandlerFor(node.type).inputPorts(node);
        for (int index = 0; index < ports.size(); index++) {
            ProcessNodeHandler.PortDefinition port = ports.get(index);
            double yOffset = ports.size() == 1 ? 0 : (index - (ports.size() - 1) / 2.0) * 16;

            Circle inHandle = new Circle(5, Color.web("#58a6ff"));
            inHandle.setStyle("-fx-cursor: crosshair;");
            inHandle.setTranslateX(-70);
            inHandle.setTranslateY(yOffset);
            inHandle.setUserData(new PortHandleData(node, port));

            String representations = port.acceptedRepresentations() == null || port.acceptedRepresentations().isEmpty()
                    ? "any"
                    : port.acceptedRepresentations().stream().map(Enum::name).collect(Collectors.joining(", "));
            Tooltip.install(inHandle, new Tooltip(view.portLabel().apply(port.name()) + " (" + representations + ")"));

            inHandle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                if (view.connectionDragging().getAsBoolean()) {
                    ProcessDefinition.Node dragSource = view.connectionDragSourceNode().get();
                    if (!dragSource.id.equals(node.id)) {
                        view.completeConnectionDragToPort().drop(dragSource, node, port.name());
                    } else {
                        view.cancelConnectionDrag().run();
                    }
                    event.consume();
                }
            });

            nodeView.getChildren().add(inHandle);
            inputPortHandles.add(inHandle);

            if (ports.size() > 1) {
                Label inputLabel = new Label("• " + view.portLabel().apply(port.name()));
                inputLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 9px;");
                inputLabel.setTranslateX(-44);
                inputLabel.setTranslateY(yOffset);
                nodeView.getChildren().add(inputLabel);
            }
        }

        Circle outHandle = new Circle(5, Color.web("#58a6ff"));
        outHandle.setTranslateX(70);
        outHandle.setStyle("-fx-cursor: crosshair;");
        outHandle.setOnMousePressed(event -> {
            view.startConnectionDrag().accept(node);
            event.consume();
        });
        outHandle.setOnMouseDragged(event -> {
            if (view.connectionDragging().getAsBoolean()) {
                ProcessDefinition.Node dragSource = view.connectionDragSourceNode().get();
                Point2D local = view.workflowCanvas().get().sceneToLocal(event.getSceneX(), event.getSceneY());
                view.updateInteractiveCurve().update(dragSource.x + 150, dragSource.y + 35, local.getX(), local.getY());
            }
            event.consume();
        });
        outHandle.setOnMouseReleased(event -> {
            if (view.connectionDragging().getAsBoolean()) {
                ProcessDefinition.Node dragSource = view.connectionDragSourceNode().get();
                Point2D local = view.workflowCanvas().get().sceneToLocal(event.getSceneX(), event.getSceneY());
                Circle targetHandle = null;
                for (Circle circle : inputPortHandles) {
                    Point2D point = circle.sceneToLocal(event.getSceneX(), event.getSceneY());
                    if (circle.contains(point)) {
                        targetHandle = circle;
                        break;
                    }
                }
                if (targetHandle != null && targetHandle.getUserData() instanceof PortHandleData data) {
                    if (!data.node().id.equals(dragSource.id)) {
                        view.completeConnectionDragToPort().drop(dragSource, data.node(), data.port().name());
                    } else {
                        view.cancelConnectionDrag().run();
                    }
                } else {
                    ProcessDefinition.Node target = view.nodes().get().stream()
                            .filter(candidate -> !candidate.id.equals(dragSource.id))
                            .filter(candidate -> local.getX() >= candidate.x && local.getX() <= candidate.x + 150
                                    && local.getY() >= candidate.y && local.getY() <= candidate.y + 70)
                            .findFirst().orElse(null);
                    if (target != null) {
                        view.completeConnectionDragAtScreen().drop(dragSource, target,
                                event.getScreenX(), event.getScreenY());
                    } else {
                        view.cancelConnectionDrag().run();
                    }
                }
            }
            event.consume();
        });
        nodeView.getChildren().add(outHandle);

        boolean active = view.selectedNode().get() != null && view.selectedNode().get().id.equals(node.id);
        boolean pending = !active && view.selectedNodeIds().get().contains(node.id);
        if (active) {
            nodeView.setStyle("-fx-background-color: #287bb5; -fx-border-color: white; -fx-border-width: 3; -fx-background-radius: 5;");
        } else if (pending) {
            nodeView.setStyle("-fx-background-color: #5a4a20; -fx-border-color: #f6c344; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;");
            Label sourceMarker = new Label("SOURCE");
            sourceMarker.setStyle("-fx-text-fill: #f6c344; -fx-font-size: 8px; -fx-font-weight: bold; -fx-background-color: #202a33;");
            sourceMarker.setTranslateX(46);
            sourceMarker.setTranslateY(-25);
            nodeView.getChildren().add(sourceMarker);
        } else {
            nodeView.setStyle("-fx-background-color: #33495e; -fx-border-color: #6f97bb; -fx-border-width: 1; -fx-background-radius: 5;");
        }

        final double[] dragStartPos = new double[4];
        nodeView.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (view.connectionDragging().getAsBoolean()) {
                ProcessDefinition.Node dragSource = view.connectionDragSourceNode().get();
                if (!dragSource.id.equals(node.id)) {
                    view.completeConnectionDrag().accept(dragSource, node);
                } else {
                    view.cancelConnectionDrag().run();
                }
                event.consume();
                return;
            }
            view.workflowCanvas().get().requestFocus();
            dragStartPos[0] = node.x;
            dragStartPos[1] = node.y;
            dragStartPos[2] = event.getSceneX();
            dragStartPos[3] = event.getSceneY();
            view.select().accept(node);
            event.consume();
        });

        nodeView.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            Point2D startLocal = view.workflowCanvas().get().sceneToLocal(dragStartPos[2], dragStartPos[3]);
            Point2D currentLocal = view.workflowCanvas().get().sceneToLocal(event.getSceneX(), event.getSceneY());
            double deltaX = currentLocal.getX() - startLocal.getX();
            double deltaY = currentLocal.getY() - startLocal.getY();

            double newX = dragStartPos[0] + deltaX;
            double newY = dragStartPos[1] + deltaY;
            if (view.snapToGrid().get()) {
                newX = Math.round(newX / 10.0) * 10;
                newY = Math.round(newY / 10.0) * 10;
            }
            node.x = Math.max(0, newX);
            node.y = Math.max(0, newY);
            nodeView.setLayoutX(node.x);
            nodeView.setLayoutY(node.y);
            updateConnectedCurves(view, node);
            event.consume();
        });

        nodeView.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            view.updateCanvasGeometry().run();
            if (node.x != dragStartPos[0] || node.y != dragStartPos[1]) {
                ProcessDefinition before = ProcessUndoRedoCoordinator.snapshot(view.currentDefinition().get());
                before.nodes.stream().filter(candidate -> candidate.id.equals(node.id)).findFirst().ifPresent(previous -> {
                    previous.x = dragStartPos[0];
                    previous.y = dragStartPos[1];
                });
                view.recordStateChange().accept("Move node", before);
            }
            event.consume();
        });

        return nodeView;
    }

    private void updateConnectedCurves(View view, ProcessDefinition.Node node) {
        for (javafx.scene.Node child : view.workflowCanvas().get().getChildren()) {
            if (child instanceof CubicCurve curve) {
                ProcessDefinition.Connection connection = (ProcessDefinition.Connection) curve.getUserData();
                if (connection != null) {
                    if (connection.from.equals(node.id)) {
                        curve.setStartX(node.x + 150);
                        curve.setStartY(node.y + 35);
                        updateCurveControls(curve);
                    } else if (connection.to.equals(node.id)) {
                        curve.setEndX(node.x);
                        curve.setEndY(node.y + 35);
                        updateCurveControls(curve);
                    }
                }
            }
        }
    }

    private void addConnectionView(View view, ProcessDefinition.Connection connection,
                                   ProcessDefinition.Node from, ProcessDefinition.Node to) {
        double startX = from.x + 150;
        double startY = from.y + 35;
        double endX = to.x;
        double endY = to.y + 35;

        if (connection.targetPort != null) {
            List<ProcessNodeHandler.PortDefinition> targetPorts = ProcessEngine.getHandlerFor(to.type).inputPorts(to);
            int portIndex = -1;
            for (int index = 0; index < targetPorts.size(); index++) {
                if (connection.targetPort.equals(targetPorts.get(index).name())) {
                    portIndex = index;
                    break;
                }
            }
            if (portIndex >= 0) endY += (portIndex - (targetPorts.size() - 1) / 2.0) * 13;

            Label portLabel = new Label(connection.targetPort);
            portLabel.setStyle("-fx-text-fill: #f6c344; -fx-font-size: 9px; -fx-background-color: #202a33;");
            portLabel.setLayoutX(endX - 35);
            portLabel.setLayoutY(endY - 15);
            view.workflowCanvas().get().getChildren().add(portLabel);
        }

        CubicCurve curve = new CubicCurve();
        curve.setStartX(startX);
        curve.setStartY(startY);
        curve.setEndX(endX);
        curve.setEndY(endY);
        updateCurveControls(curve);
        curve.setFill(null);

        boolean selected = connection == view.selectedConnection().get();
        curve.setStroke(selected ? Color.web("#f6c344") : Color.web("#58a6ff"));
        curve.setStrokeWidth(selected ? 4.0 : 2.5);
        curve.setUserData(connection);
        curve.setOnMouseClicked(event -> view.selectConnection().accept(connection));
        view.workflowCanvas().get().getChildren().add(curve);
    }
}
