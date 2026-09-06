package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.model.process.ExecutionContext;
import com.cryptocarver.model.process.FileWritePolicy;
import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeDescriptor;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessDefinitionCodec;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.ProcessNodeHandler;
import com.cryptocarver.model.process.ProcessValidator;
import com.cryptocarver.model.process.Representation;
import com.cryptocarver.model.process.handlers.SymmetricCipherSpec;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Process Designer.
 * Phase 5A: Descriptor-driven inspector, expandable canvas, zoom/pan, smooth connections,
 * searchable palette, and undo/redo command stack.
 */
public class ProcessDesignerController {

    private ModuleI18n.Binding moduleI18n;

    @FXML private TitledPane processDesignerRoot;
    @FXML private Pane workflowCanvas;
    @FXML private TextField processNameField;
    @FXML private Label selectedNodeLabel;
    @FXML private Label inputContractLabel;
    @FXML private Label outputContractLabel;
    @FXML VBox nodeNameFieldGroup;
    @FXML TextField nodeNameField;
    @FXML private Button connectSelectedButton;
    @FXML private Button deleteSelectedButton;
    @FXML private Button reverseConnectionButton;
    @FXML private Button reverseConnectionToolbarButton;
    @FXML private Button runProcessButton;
    @FXML Button cancelProcessButton;
    @FXML private Button inspectorToggleButton;
    @FXML private SplitPane designerSplitPane;
    @FXML private ScrollPane designerWorkspace;
    @FXML private VBox nodeInspector;
    @FXML private VBox dynamicInspectorContainer;
    @FXML private VBox palettePanel;
    @FXML private TextField paletteSearchField;
    @FXML private VBox paletteItemsContainer;
    @FXML private Label zoomLevelLabel;
    @FXML private CheckBox snapToGridCheck;
    @FXML private Button focusModeButton;
    @FXML TableView<ProcessExecutionRow> executionStatusTable;
    @FXML private TableColumn<ProcessExecutionRow, String> stepCol;
    @FXML private TableColumn<ProcessExecutionRow, String> stepNameCol;
    @FXML private TableColumn<ProcessExecutionRow, String> operationCol;
    @FXML private TableColumn<ProcessExecutionRow, String> inputCol;
    @FXML private TableColumn<ProcessExecutionRow, String> outputCol;
    @FXML private TableColumn<ProcessExecutionRow, String> statusCol;
    @FXML private TableColumn<ProcessExecutionRow, String> durationCol;
    @FXML private TableColumn<ProcessExecutionRow, Void> inspectCol;
    @FXML ProgressBar processProgressBar;
    @FXML Label processStatusLabel;
    @FXML TextArea executionOutputArea;
    @FXML MenuButton connectMenuButton;

    // --- State & Canvas ---
    final List<ProcessDefinition.Node> nodes = new ArrayList<>();
    final List<ProcessDefinition.Connection> connections = new ArrayList<>();
    private final Map<String, StackPane> views = new LinkedHashMap<>();
    final LinkedHashSet<String> selectedNodeIds = new LinkedHashSet<>();
    private ProcessDefinition.Node selected;
    private ProcessDefinition.Connection selectedConnection;
    private boolean inspectorVisible = true;
    private boolean focusMode = false;
    private double currentZoom = 1.0;
    private final Scale canvasScale = new Scale(1.0, 1.0, 0, 0);
    private boolean snapToGrid = true;
    private final ExpandedTextViewer expandedExecutionViewer = new ExpandedTextViewer();
    private volatile boolean processCancellationRequested = false;

    // Secrets in-memory map: nodeId -> (paramKey -> char[])
    final Map<String, Map<String, char[]>> transientSecrets = new HashMap<>();
    private NodeInspectorRenderer dynamicInspectorRenderer;

    // Interactive connection drag state
    private ProcessDefinition.Node connectionDragSourceNode;
    private CubicCurve interactiveConnectionCurve;
    record PortHandleData(ProcessDefinition.Node node, ProcessNodeHandler.PortDefinition port) {}
    final List<Circle> inputPortHandles = new ArrayList<>();

    // Performance tracking
    public int validationCounter = 0;

    // Command stack for Undo/Redo (>= 50 steps)
    public interface DesignerCommand {
        void undo();
        void redo();
    }

    private final Deque<DesignerCommand> undoStack = new ArrayDeque<>();
    private final Deque<DesignerCommand> redoStack = new ArrayDeque<>();

    public Runnable onExecutionFinished;
    public java.util.function.Consumer<NodeExecutionEvent> onNodeExecutionEvent;

    @FXML public void initialize() {
        moduleI18n = ModuleI18n.bind(processDesignerRoot, ModuleTextCatalog.processDesigner());
        I18nService.getInstance().addLocaleChangeListener(locale -> {
            if (processStatusLabel != null && (processStatusLabel.getText() == null || processStatusLabel.getText().isBlank())) {
                processStatusLabel.setText(t("status.ready"));
            }
            if (executionStatusTable != null) {
                executionStatusTable.setPlaceholder(new Label(t("module.process.executionPlaceholder")));
            }
            updateSelectionUi();
            buildPalette();
        });
        configureExecutionStatusTable();

        // Canvas Scale & Zoom setup
        if (workflowCanvas != null) {
            workflowCanvas.getTransforms().setAll(canvasScale);
            workflowCanvas.setFocusTraversable(true);
            initCanvasEventHandlers();
            updateCanvasGeometry();
        }

        buildPalette();
        if (paletteSearchField != null) {
            paletteSearchField.textProperty().addListener((obs, oldV, newV) -> filterPalette(newV));
        }

        setZoom(1.0);

        ProcessDefinition.Node input = addNode("CONSOLE_INPUT", "Console input", 40, 60);
        ProcessDefinition.Node hash = addNode("HASH", "SHA-256", 270, 60);
        ProcessDefinition.Node output = addNode("CONSOLE_OUTPUT", "Console output", 500, 60);
        connections.add(new ProcessDefinition.Connection(input.id, hash.id));
        connections.add(new ProcessDefinition.Connection(hash.id, output.id));
        select(input);
    }

    private void initCanvasEventHandlers() {
        workflowCanvas.setOnScroll(e -> {
            if (e.isControlDown() || e.isShortcutDown()) {
                double delta = e.getDeltaY() > 0 ? 0.08 : -0.08;
                setZoom(currentZoom + delta);
                e.consume();
            }
        });

        workflowCanvas.setOnMouseClicked(e -> {
            if (e.getTarget() == workflowCanvas) {
                selected = null;
                selectedNodeIds.clear();
                selectedConnection = null;
                updateSelectionUi();
                redraw();
            }
        });

        workflowCanvas.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                handleDeleteSelected();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.Z) {
                if (e.isShiftDown()) handleRedo();
                else handleUndo();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.Y) {
                handleRedo();
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.D) {
                handleDuplicateSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                selected = null;
                selectedNodeIds.clear();
                selectedConnection = null;
                if (interactiveConnectionCurve != null) {
                    workflowCanvas.getChildren().remove(interactiveConnectionCurve);
                    interactiveConnectionCurve = null;
                    connectionDragSourceNode = null;
                }
                updateSelectionUi();
                redraw();
                e.consume();
            } else if (e.getCode().isArrowKey()) {
                double step = e.isShiftDown() ? 10.0 : 1.0;
                if (selected != null) {
                    if (e.getCode() == KeyCode.UP) selected.y -= step;
                    else if (e.getCode() == KeyCode.DOWN) selected.y += step;
                    else if (e.getCode() == KeyCode.LEFT) selected.x -= step;
                    else if (e.getCode() == KeyCode.RIGHT) selected.x += step;
                    updateCanvasGeometry();
                    redraw();
                    e.consume();
                }
            }
        });

        workflowCanvas.setOnMouseMoved(e -> {
            if (interactiveConnectionCurve != null && connectionDragSourceNode != null) {
                Point2D local = workflowCanvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                updateInteractiveCurve(connectionDragSourceNode.x + 150, connectionDragSourceNode.y + 35, local.getX(), local.getY());
            }
        });
    }


    // --- Searchable Palette ---
    private void buildPalette() {
        filterPalette(paletteSearchField == null ? null : paletteSearchField.getText());
    }

    private void filterPalette(String filter) {
        if (paletteItemsContainer == null) return;
        paletteItemsContainer.getChildren().clear();

        String q = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);

        for (String category : NodeCatalog.categories()) {
            List<NodeDescriptor> matching = NodeCatalog.descriptorsByCategory(category).stream()
                    .filter(d -> matchesSearch(d, q))
                    .toList();

            if (matching.isEmpty()) continue;

            String catKey = "module.process.category." + switch (category) {
                case "Inputs" -> "inputs";
                case "Conversions" -> "conversions";
                case "Crypto" -> "crypto";
                case "Generators" -> "generators";
                case "Key Material" -> "keyMaterial";
                case "WS-Security" -> "wsSecurity";
                case "Outputs" -> "outputs";
                default -> category.toLowerCase(Locale.ROOT);
            };
            Label catHeader = new Label(t(catKey).toUpperCase(Locale.ROOT));
            catHeader.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #8899aa; -fx-padding: 4 0 2 0;");
            paletteItemsContainer.getChildren().add(catHeader);

            for (NodeDescriptor d : matching) {
                HBox item = new HBox(6);
                item.setPadding(new Insets(4, 6, 4, 6));
                item.setStyle("-fx-background-color: #242d38; -fx-background-radius: 4; -fx-cursor: hand;");

                Label iconLabel = new Label(d.icon());
                iconLabel.setStyle("-fx-font-size: 13px;");

                VBox textBox = new VBox(1);
                Label titleLabel = new Label(t(d.labelKey()));
                titleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
                Label descLabel = new Label(t(d.descriptionKey()));
                descLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #8899aa;");
                descLabel.setWrapText(true);
                textBox.getChildren().addAll(titleLabel, descLabel);

                item.getChildren().addAll(iconLabel, textBox);

                item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #334455; -fx-background-radius: 4; -fx-cursor: hand;"));
                item.setOnMouseExited(e -> item.setStyle("-fx-background-color: #242d38; -fx-background-radius: 4; -fx-cursor: hand;"));

                item.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        double placeX = 60 + (nodes.size() % 5) * 40;
                        double placeY = 80 + (nodes.size() % 6) * 35;
                        ProcessDefinition.Node added = addNode(d.type(), t(d.labelKey()), placeX, placeY);
                        select(added);
                    }
                });

                paletteItemsContainer.getChildren().add(item);
            }
        }
    }

    private boolean matchesSearch(NodeDescriptor d, String q) {
        if (q.isEmpty()) return true;
        return d.type().toLowerCase(Locale.ROOT).contains(q)
                || d.category().toLowerCase(Locale.ROOT).contains(q)
                || t(d.labelKey()).toLowerCase(Locale.ROOT).contains(q)
                || t(d.descriptionKey()).toLowerCase(Locale.ROOT).contains(q);
    }

    // --- Expandable Canvas Geometry ---
    public void updateCanvasGeometry() {
        if (workflowCanvas == null) return;
        double maxX = 1200;
        double maxY = 800;
        for (ProcessDefinition.Node n : nodes) {
            maxX = Math.max(maxX, n.x + 220 + 200);
            maxY = Math.max(maxY, n.y + 120 + 200);
        }
        workflowCanvas.setPrefWidth(maxX);
        workflowCanvas.setPrefHeight(maxY);
        workflowCanvas.setMinWidth(maxX);
        workflowCanvas.setMinHeight(maxY);
    }

    // --- Zoom & Pan ---
    public void setZoom(double zoom) {
        currentZoom = Math.max(0.25, Math.min(4.0, zoom));
        canvasScale.setX(currentZoom);
        canvasScale.setY(currentZoom);
        if (zoomLevelLabel != null) {
            zoomLevelLabel.setText(Math.round(currentZoom * 100) + "%");
        }
    }

    public double getZoom() {
        return currentZoom;
    }

    @FXML public void handleZoomIn() { setZoom(currentZoom + 0.15); }
    @FXML public void handleZoomOut() { setZoom(currentZoom - 0.15); }
    @FXML public void handleResetZoom() { setZoom(1.0); }

    @FXML public void handleZoomFit() {
        if (nodes.isEmpty() || designerWorkspace == null) {
            setZoom(1.0);
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = 0, maxY = 0;
        for (ProcessDefinition.Node n : nodes) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + 180);
            maxY = Math.max(maxY, n.y + 90);
        }
        double vpW = designerWorkspace.getViewportBounds().getWidth();
        double vpH = designerWorkspace.getViewportBounds().getHeight();
        if (vpW <= 0) vpW = 800;
        if (vpH <= 0) vpH = 500;
        double scaleX = (vpW - 40) / Math.max(100, maxX - minX + 40);
        double scaleY = (vpH - 40) / Math.max(100, maxY - minY + 40);
        setZoom(Math.min(scaleX, scaleY));
    }

    @FXML public void handleSnapToGridToggled() {
        snapToGrid = snapToGridCheck == null || snapToGridCheck.isSelected();
    }

    // --- Undo / Redo Command Pattern ---
    private record SnapshotCommand(String desc, ProcessDefinition before, ProcessDefinition after) implements DesignerCommand {
        @Override public void undo() { restoreDef(before); }
        @Override public void redo() { restoreDef(after); }
        private void restoreDef(ProcessDefinition def) {}
    }

    public void executeCommand(DesignerCommand command) {
        command.redo();
        undoStack.push(command);
        if (undoStack.size() > 60) {
            ((ArrayDeque<DesignerCommand>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    private void recordStateChange(String desc, ProcessDefinition before) {
        ProcessDefinition after = snapshot(toDefinition());
        ProcessDefinition beforeSnapshot = snapshot(before);
        undoStack.push(new DesignerCommand() {
            @Override
            public void undo() {
                load(beforeSnapshot);
            }
            @Override
            public void redo() {
                load(after);
            }
        });
        if (undoStack.size() > 60) {
            ((ArrayDeque<DesignerCommand>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    @FXML public void handleUndo() {
        if (!undoStack.isEmpty()) {
            DesignerCommand cmd = undoStack.pop();
            cmd.undo();
            redoStack.push(cmd);
        }
    }

    @FXML public void handleRedo() {
        if (!redoStack.isEmpty()) {
            DesignerCommand cmd = redoStack.pop();
            cmd.redo();
            undoStack.push(cmd);
        }
    }

    // --- Duplicate & Tidy Layout ---
    @FXML public void handleDuplicateSelected() {
        if (selected == null) return;
        ProcessDefinition before = toDefinition();
        ProcessDefinition.Node dup = new ProcessDefinition.Node(
                UUID.randomUUID().toString(),
                selected.type,
                selected.label + " (Copy)",
                selected.x + 30,
                selected.y + 30
        );
        dup.configuration.putAll(selected.configuration);
        for (String sk : NodeCatalog.allSensitiveKeys()) {
            dup.configuration.remove(sk);
        }
        nodes.add(dup);
        select(dup);
        updateCanvasGeometry();
        redraw();
        recordStateChange("Duplicate node", before);
    }

    @FXML public void handleTidyLayout() {
        if (nodes.isEmpty()) return;
        ProcessDefinition before = toDefinition();
        List<String> order = ProcessValidator.computeTopologicalOrder(toDefinition());

        Map<String, Integer> depthMap = new HashMap<>();
        for (String id : order) {
            int maxParentDepth = -1;
            for (ProcessDefinition.Connection c : connections) {
                if (c.to.equals(id)) {
                    int pDepth = depthMap.getOrDefault(c.from, 0);
                    maxParentDepth = Math.max(maxParentDepth, pDepth);
                }
            }
            depthMap.put(id, maxParentDepth + 1);
        }

        Map<Integer, Integer> layerCounts = new HashMap<>();
        for (String id : order) {
            int layer = depthMap.getOrDefault(id, 0);
            int row = layerCounts.getOrDefault(layer, 0);
            layerCounts.put(layer, row + 1);

            ProcessDefinition.Node n = nodes.stream().filter(node -> node.id.equals(id)).findFirst().orElse(null);
            if (n != null) {
                n.x = 60 + layer * 220;
                n.y = 80 + row * 110;
            }
        }

        updateCanvasGeometry();
        redraw();
        recordStateChange("Tidy layout", before);
    }

    // --- Detached Window & Focus Mode ---
    @FXML public void handleOpenWindow() {
        if (processDesignerRoot == null) return;
        javafx.scene.Node content = processDesignerRoot.getContent();
        if (content == null) return;

        Label placeholder = new Label(t("module.process.windowPlaceholder"));
        placeholder.setStyle("-fx-font-size: 13px; -fx-padding: 30; -fx-text-fill: #aaa;");
        processDesignerRoot.setContent(placeholder);

        Stage owner = (processDesignerRoot.getScene() != null && processDesignerRoot.getScene().getWindow() instanceof Stage s) ? s : null;
        ProcessDesignerWindow.open(owner, content, t("module.process.title"), () -> {
            processDesignerRoot.setContent(content);
        });
    }

    @FXML public void handleToggleFocusMode() {
        focusMode = !focusMode;
        if (focusModeButton != null) {
            focusModeButton.setText(focusMode ? t("module.process.exitFocusMode") : t("module.process.focusMode"));
        }
        if (focusMode) {
            if (designerSplitPane != null) designerSplitPane.setDividerPositions(0.0, 1.0);
        } else {
            if (designerSplitPane != null) designerSplitPane.setDividerPositions(0.18, 0.76);
        }
    }

    // --- Node Selection & Descriptor Inspector ---
    void select(ProcessDefinition.Node node) {
        saveSelectedNodeSettings();
        selectedConnection = null;
        if (!selectedNodeIds.contains(node.id) && selectedNodeIds.size() == 2) selectedNodeIds.clear();
        selectedNodeIds.add(node.id);
        selected = node;
        selectedNodeLabel.setText(node.type + " · " + node.label);
        if (nodeNameFieldGroup != null) {
            nodeNameFieldGroup.setVisible(true);
            nodeNameFieldGroup.setManaged(true);
        }
        if (nodeNameField != null) nodeNameField.setText(node.label == null ? "" : node.label);

        NodeDescriptor desc = NodeCatalog.descriptor(node.type).orElse(null);
        if (desc != null && dynamicInspectorContainer != null) {
            dynamicInspectorRenderer = NodeInspectorRenderer.render(
                    desc,
                    node,
                    dynamicInspectorContainer,
                    transientSecrets.computeIfAbsent(node.id, k -> new HashMap<>()),
                    k -> {
                        updateRepresentationContract(selected);
                        redraw();
                    }
            );
        }

        updateRepresentationContract(node);
        updateSelectionUi();
    }

    private void saveSelectedNodeSettings() {
        if (selected == null) return;
        ProcessDefinition before = snapshot(toDefinition());
        if (nodeNameField != null && !nodeNameField.getText().isBlank()) {
            selected.label = nodeNameField.getText().trim();
            selectedNodeLabel.setText(selected.type + " · " + selected.label);
        }
        if (dynamicInspectorRenderer != null) {
            Map<String, char[]> sec = transientSecrets.computeIfAbsent(selected.id, k -> new HashMap<>());
            dynamicInspectorRenderer.save(selected, sec);
        }
        updateRepresentationContract(selected);
        before.nodes.stream().filter(n -> n.id.equals(selected.id)).findFirst().ifPresent(bn -> {
            if (!bn.configuration.equals(selected.configuration) || !Objects.equals(bn.label, selected.label)) {
                recordStateChange("Change configuration", before);
            }
        });
    }

    public Control getInspectorControl(String key) {
        return dynamicInspectorRenderer != null ? dynamicInspectorRenderer.getControl(key) : null;
    }

    public VBox getInspectorGroup(String key) {
        return dynamicInspectorRenderer != null ? dynamicInspectorRenderer.getGroup(key) : null;
    }

    // --- Redraw and Node/Connection Rendering ---
    void redraw() {
        if (workflowCanvas == null) return;
        workflowCanvas.getChildren().clear();
        views.clear();
        inputPortHandles.clear();

        Map<String, Representation> reps = new HashMap<>();
        try {
            validationCounter++;
            reps = ProcessEngine.validate(toDefinition());
        } catch (Exception ignored) {}

        for (ProcessDefinition.Connection connection : connections) {
            ProcessDefinition.Node from = nodes.stream().filter(n -> n.id.equals(connection.from)).findFirst().orElse(null);
            ProcessDefinition.Node to = nodes.stream().filter(n -> n.id.equals(connection.to)).findFirst().orElse(null);
            if (from != null && to != null) addConnectionView(connection, from, to);
        }

        for (ProcessDefinition.Node node : nodes) {
            StackPane nodeView = createNodeView(node, reps.get(node.id));
            views.put(node.id, nodeView);
            workflowCanvas.getChildren().add(nodeView);
        }

        updateCanvasGeometry();
    }

    private StackPane createNodeView(ProcessDefinition.Node node, Representation rep) {
        String badge = rep != null ? " [" + rep.name() + "]" : "";
        Label label = new Label(node.label + badge);
        label.setWrapText(true);
        label.setMaxWidth(135);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");

        StackPane view = new StackPane(label);
        view.setLayoutX(node.x);
        view.setLayoutY(node.y);
        view.setPrefSize(150, 70);

        // Port indicators & circular input handles on node
        List<ProcessNodeHandler.PortDefinition> ports = ProcessEngine.getHandlerFor(node.type).inputPorts(node);
        for (int index = 0; index < ports.size(); index++) {
            ProcessNodeHandler.PortDefinition port = ports.get(index);
            double yOffset = (ports.size() == 1) ? 0 : (index - (ports.size() - 1) / 2.0) * 16;

            Circle inHandle = new Circle(5, Color.web("#58a6ff"));
            inHandle.setStyle("-fx-cursor: crosshair;");
            inHandle.setTranslateX(-70);
            inHandle.setTranslateY(yOffset);
            inHandle.setUserData(new PortHandleData(node, port));

            String repsStr = (port.acceptedRepresentations() == null || port.acceptedRepresentations().isEmpty())
                    ? "any"
                    : port.acceptedRepresentations().stream().map(Enum::name).collect(Collectors.joining(", "));
            Tooltip.install(inHandle, new Tooltip(port.name() + " (" + repsStr + ")"));

            inHandle.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                if (interactiveConnectionCurve != null && connectionDragSourceNode != null) {
                    if (!connectionDragSourceNode.id.equals(node.id)) {
                        completeConnectionDragToPort(connectionDragSourceNode, node, port.name());
                    } else {
                        cancelConnectionDrag();
                    }
                    e.consume();
                }
            });

            view.getChildren().add(inHandle);
            inputPortHandles.add(inHandle);

            if (ports.size() > 1) {
                Label portLabel = new Label("• " + port.name());
                portLabel.setStyle("-fx-text-fill: #aaa; -fx-font-size: 9px;");
                portLabel.setTranslateX(-44);
                portLabel.setTranslateY(yOffset);
                view.getChildren().add(portLabel);
            }
        }

        // Circular Output Port handle on right
        Circle outHandle = new Circle(5, Color.web("#58a6ff"));
        outHandle.setTranslateX(70);
        outHandle.setStyle("-fx-cursor: crosshair;");
        outHandle.setOnMousePressed(e -> {
            startConnectionDrag(node);
            e.consume();
        });
        outHandle.setOnMouseDragged(e -> {
            if (interactiveConnectionCurve != null && connectionDragSourceNode != null) {
                Point2D local = workflowCanvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                updateInteractiveCurve(connectionDragSourceNode.x + 150, connectionDragSourceNode.y + 35, local.getX(), local.getY());
            }
            e.consume();
        });
        outHandle.setOnMouseReleased(e -> {
            if (interactiveConnectionCurve != null && connectionDragSourceNode != null) {
                Point2D local = workflowCanvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                Circle targetHandle = null;
                for (Circle circle : inputPortHandles) {
                    Point2D pt = circle.sceneToLocal(e.getSceneX(), e.getSceneY());
                    if (circle.contains(pt)) {
                        targetHandle = circle;
                        break;
                    }
                }
                if (targetHandle != null && targetHandle.getUserData() instanceof PortHandleData data) {
                    if (!data.node.id.equals(connectionDragSourceNode.id)) {
                        completeConnectionDragToPort(connectionDragSourceNode, data.node, data.port.name());
                    } else {
                        cancelConnectionDrag();
                    }
                } else {
                    ProcessDefinition.Node target = nodes.stream()
                            .filter(n -> !n.id.equals(connectionDragSourceNode.id))
                            .filter(n -> local.getX() >= n.x && local.getX() <= n.x + 150 && local.getY() >= n.y && local.getY() <= n.y + 70)
                            .findFirst()
                            .orElse(null);
                    if (target != null) {
                        completeConnectionDrag(connectionDragSourceNode, target, e.getScreenX(), e.getScreenY());
                    } else {
                        cancelConnectionDrag();
                    }
                }
            }
            e.consume();
        });
        view.getChildren().add(outHandle);

        boolean active = selected != null && selected.id.equals(node.id);
        boolean pending = !active && selectedNodeIds.contains(node.id);
        if (active) {
            view.setStyle("-fx-background-color: #287bb5; -fx-border-color: white; -fx-border-width: 3; -fx-background-radius: 5;");
        } else if (pending) {
            view.setStyle("-fx-background-color: #5a4a20; -fx-border-color: #f6c344; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;");
            Label sourceMarker = new Label("SOURCE");
            sourceMarker.setStyle("-fx-text-fill: #f6c344; -fx-font-size: 8px; -fx-font-weight: bold; -fx-background-color: #202a33;");
            sourceMarker.setTranslateX(46);
            sourceMarker.setTranslateY(-25);
            view.getChildren().add(sourceMarker);
        } else {
            view.setStyle("-fx-background-color: #33495e; -fx-border-color: #6f97bb; -fx-border-width: 1; -fx-background-radius: 5;");
        }

        final double[] dragStartPos = new double[4]; // [initialNodeX, initialNodeY, initialSceneX, initialSceneY]
        view.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (interactiveConnectionCurve != null && connectionDragSourceNode != null) {
                if (!connectionDragSourceNode.id.equals(node.id)) {
                    completeConnectionDrag(connectionDragSourceNode, node);
                } else {
                    cancelConnectionDrag();
                }
                e.consume();
                return;
            }
            workflowCanvas.requestFocus();
            dragStartPos[0] = node.x;
            dragStartPos[1] = node.y;
            dragStartPos[2] = e.getSceneX();
            dragStartPos[3] = e.getSceneY();
            select(node);
            e.consume();
        });

        view.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            Point2D startLocal = workflowCanvas.sceneToLocal(dragStartPos[2], dragStartPos[3]);
            Point2D currentLocal = workflowCanvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            double deltaX = currentLocal.getX() - startLocal.getX();
            double deltaY = currentLocal.getY() - startLocal.getY();

            double newX = dragStartPos[0] + deltaX;
            double newY = dragStartPos[1] + deltaY;
            if (snapToGrid) {
                newX = Math.round(newX / 10.0) * 10;
                newY = Math.round(newY / 10.0) * 10;
            }
            node.x = Math.max(0, newX);
            node.y = Math.max(0, newY);
            view.setLayoutX(node.x);
            view.setLayoutY(node.y);
            updateConnectedCurves(node);
            e.consume();
        });

        view.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            updateCanvasGeometry();
            if (node.x != dragStartPos[0] || node.y != dragStartPos[1]) {
                ProcessDefinition before = snapshot(toDefinition());
                before.nodes.stream().filter(n -> n.id.equals(node.id)).findFirst().ifPresent(n -> {
                    n.x = dragStartPos[0];
                    n.y = dragStartPos[1];
                });
                recordStateChange("Move node", before);
            }
            e.consume();
        });

        return view;
    }

    private void updateConnectedCurves(ProcessDefinition.Node node) {
        for (javafx.scene.Node child : workflowCanvas.getChildren()) {
            if (child instanceof CubicCurve curve) {
                ProcessDefinition.Connection conn = (ProcessDefinition.Connection) curve.getUserData();
                if (conn != null) {
                    if (conn.from.equals(node.id)) {
                        curve.setStartX(node.x + 150);
                        curve.setStartY(node.y + 35);
                        updateCurveControls(curve);
                    } else if (conn.to.equals(node.id)) {
                        curve.setEndX(node.x);
                        curve.setEndY(node.y + 35);
                        updateCurveControls(curve);
                    }
                }
            }
        }
    }

    private void updateCurveControls(CubicCurve curve) {
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

    private void addConnectionView(ProcessDefinition.Connection connection, ProcessDefinition.Node from, ProcessDefinition.Node to) {
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
            if (portIndex >= 0) {
                endY += (portIndex - (targetPorts.size() - 1) / 2.0) * 13;
            }

            Label portLabel = new Label(connection.targetPort);
            portLabel.setStyle("-fx-text-fill: #f6c344; -fx-font-size: 9px; -fx-background-color: #202a33;");
            portLabel.setLayoutX(endX - 35);
            portLabel.setLayoutY(endY - 15);
            workflowCanvas.getChildren().add(portLabel);
        }

        CubicCurve curve = new CubicCurve();
        curve.setStartX(startX);
        curve.setStartY(startY);
        curve.setEndX(endX);
        curve.setEndY(endY);
        updateCurveControls(curve);
        curve.setFill(null);

        boolean isSelected = connection == selectedConnection;
        curve.setStroke(isSelected ? Color.web("#f6c344") : Color.web("#58a6ff"));
        curve.setStrokeWidth(isSelected ? 4.0 : 2.5);
        curve.setUserData(connection);

        curve.setOnMouseClicked(event -> selectConnection(connection));
        workflowCanvas.getChildren().add(curve);
    }

    private void startConnectionDrag(ProcessDefinition.Node sourceNode) {
        connectionDragSourceNode = sourceNode;
        interactiveConnectionCurve = new CubicCurve();
        interactiveConnectionCurve.setStartX(sourceNode.x + 150);
        interactiveConnectionCurve.setStartY(sourceNode.y + 35);
        interactiveConnectionCurve.setEndX(sourceNode.x + 150);
        interactiveConnectionCurve.setEndY(sourceNode.y + 35);
        updateCurveControls(interactiveConnectionCurve);
        interactiveConnectionCurve.setFill(null);
        interactiveConnectionCurve.setStroke(Color.web("#f6c344"));
        interactiveConnectionCurve.setStrokeWidth(2.0);
        interactiveConnectionCurve.getStrokeDashArray().addAll(6.0, 4.0);
        workflowCanvas.getChildren().add(interactiveConnectionCurve);

        Representation srcRep = outputRepresentationOf(sourceNode);
        for (Circle circle : inputPortHandles) {
            if (circle.getUserData() instanceof PortHandleData data) {
                if (data.node.id.equals(sourceNode.id)) {
                    circle.setOpacity(0.3);
                    continue;
                }
                boolean portOccupied = connections.stream().anyMatch(c -> c.to.equals(data.node.id) && data.port.name().equals(c.targetPort));
                boolean compatible = !portOccupied && (srcRep == null || data.port.acceptedRepresentations().isEmpty() || data.port.acceptedRepresentations().contains(srcRep));
                circle.setOpacity(compatible ? 1.0 : 0.3);
            }
        }
    }

    private void updateInteractiveCurve(double startX, double startY, double endX, double endY) {
        if (interactiveConnectionCurve == null) return;
        interactiveConnectionCurve.setStartX(startX);
        interactiveConnectionCurve.setStartY(startY);
        interactiveConnectionCurve.setEndX(endX);
        interactiveConnectionCurve.setEndY(endY);
        updateCurveControls(interactiveConnectionCurve);
    }

    void cancelConnectionDrag() {
        if (interactiveConnectionCurve != null) {
            workflowCanvas.getChildren().remove(interactiveConnectionCurve);
            interactiveConnectionCurve = null;
        }
        connectionDragSourceNode = null;
        for (Circle circle : inputPortHandles) {
            circle.setOpacity(1.0);
        }
    }

    void completeConnectionDragToPort(ProcessDefinition.Node from, ProcessDefinition.Node to, String targetPort) {
        cancelConnectionDrag();
        if (from == null || to == null) return;
        selectedNodeIds.clear();
        selectedNodeIds.add(from.id);
        selectedNodeIds.add(to.id);
        connectToPort(targetPort);
    }

    void completeConnectionDrag(ProcessDefinition.Node from, ProcessDefinition.Node to) {
        completeConnectionDrag(from, to, 0, 0);
    }

    void completeConnectionDrag(ProcessDefinition.Node from, ProcessDefinition.Node to, double screenX, double screenY) {
        cancelConnectionDrag();
        if (from == null || to == null) return;
        ProcessNodeHandler toHandler = ProcessEngine.getHandlerFor(to.type);
        if (toHandler == null) return;
        List<ProcessNodeHandler.PortDefinition> ports = toHandler.inputPorts(to);
        Representation srcRep = outputRepresentationOf(from);
        List<ProcessNodeHandler.PortDefinition> available = ports.stream()
                .filter(p -> connections.stream().noneMatch(c -> c.to.equals(to.id) && p.name().equals(c.targetPort)))
                .filter(p -> srcRep == null || p.acceptedRepresentations().contains(srcRep))
                .toList();

        if (available.isEmpty()) {
            if (executionOutputArea != null) {
                executionOutputArea.setText(t("module.process.feedback.incompatible", from.label, to.label));
            }
            return;
        }

        if (available.size() == 1) {
            selectedNodeIds.clear();
            selectedNodeIds.add(from.id);
            selectedNodeIds.add(to.id);
            connectToPort(available.get(0).name());
            return;
        }

        // Multiple available ports on target node: offer explicit selection menu or request specific port handle
        if (workflowCanvas != null && workflowCanvas.getScene() != null && workflowCanvas.getScene().getWindow() != null) {
            ContextMenu menu = new ContextMenu();
            for (ProcessNodeHandler.PortDefinition port : available) {
                MenuItem item = new MenuItem(t("module.process.connectToPort", port.name()));
                item.setOnAction(ev -> completeConnectionDragToPort(from, to, port.name()));
                menu.getItems().add(item);
            }
            if (screenX > 0 && screenY > 0) {
                menu.show(workflowCanvas.getScene().getWindow(), screenX, screenY);
            } else {
                Point2D p2d = workflowCanvas.localToScreen(to.x + 20, to.y + 20);
                if (p2d != null) {
                    menu.show(workflowCanvas.getScene().getWindow(), p2d.getX(), p2d.getY());
                } else {
                    menu.show(workflowCanvas.getScene().getWindow());
                }
            }
        } else {
            if (executionOutputArea != null) {
                executionOutputArea.setText(t("module.process.feedback.ambiguousPorts",
                        available.stream().map(ProcessNodeHandler.PortDefinition::name).collect(java.util.stream.Collectors.joining(", "))));
            }
        }
    }

    void selectConnection(ProcessDefinition.Connection connection) {
        saveSelectedNodeSettings();
        selected = null;
        selectedNodeIds.clear();
        selectedConnection = connection;
        selectedNodeLabel.setText("Connection: " + nodeLabel(connection.from) + " → " + nodeLabel(connection.to));
        if (nodeNameFieldGroup != null) {
            nodeNameFieldGroup.setVisible(false);
            nodeNameFieldGroup.setManaged(false);
        }
        if (dynamicInspectorContainer != null) {
            dynamicInspectorContainer.getChildren().clear();
        }
        updateSelectionUi();
        redraw();
    }

    // --- Node and Connection Management ---
    private ProcessDefinition.Node addNode(String type, String label, double x, double y) {
        ProcessDefinition before = toDefinition();
        ProcessDefinition.Node node = new ProcessDefinition.Node(UUID.randomUUID().toString(), type, label, x, y);
        node.configuration.putAll(NodeCatalog.defaultConfiguration(type));
        nodes.add(node);
        updateCanvasGeometry();
        redraw();
        recordStateChange("Add node " + type, before);
        return node;
    }

    ProcessDefinition toDefinition() {
        ProcessDefinition definition = new ProcessDefinition();
        definition.name = processNameField != null ? processNameField.getText().trim() : "Untitled process";
        definition.nodes = new ArrayList<>(nodes);
        definition.connections = new ArrayList<>(connections);
        return definition;
    }

    static ProcessDefinition snapshot(ProcessDefinition def) {
        if (def == null) return null;
        ProcessDefinition copy = new ProcessDefinition();
        copy.name = def.name;
        copy.version = def.version;
        for (ProcessDefinition.Node n : def.nodes) {
            ProcessDefinition.Node nc = new ProcessDefinition.Node(n.id, n.type, n.label, n.x, n.y);
            nc.configuration.putAll(n.configuration);
            copy.nodes.add(nc);
        }
        for (ProcessDefinition.Connection c : def.connections) {
            copy.connections.add(new ProcessDefinition.Connection(c.from, c.to, c.targetPort));
        }
        return copy;
    }

    ProcessDefinition toExecutableDefinition() {
        ProcessDefinition execDef = snapshot(toDefinition());
        for (ProcessDefinition.Node n : execDef.nodes) {
            Map<String, char[]> sec = transientSecrets.get(n.id);
            if (sec != null) {
                sec.forEach((k, v) -> {
                    if (v != null && v.length > 0) {
                        n.configuration.put(k, new String(v));
                    }
                });
            }
        }
        return execDef;
    }

    public Map<String, char[]> getTransientSecrets(String nodeId) {
        return transientSecrets.get(nodeId);
    }

    public char[] getTransientSecret(String nodeId, String key) {
        Map<String, char[]> sec = transientSecrets.get(nodeId);
        return sec != null ? sec.get(key) : null;
    }

    private void load(ProcessDefinition definition) {
        if (processNameField != null) processNameField.setText(normalizedProcessName(definition.name));
        nodes.clear();
        nodes.addAll(definition.nodes);
        connections.clear();
        connections.addAll(definition.connections);
        selected = null;
        selectedConnection = null;
        selectedNodeIds.clear();
        transientSecrets.clear();
        updateSelectionUi();
        updateCanvasGeometry();
        redraw();
    }

    private void loadPreset(ProcessDefinition preset) {
        load(preset);
        if (executionOutputArea != null) {
            executionOutputArea.setText(t("module.process.presetLoaded", preset.name));
        }
    }

    private String normalizedProcessName(String name) {
        return name == null || name.isBlank() ? "Untitled process" : name.trim();
    }

    private static String safeProcessFileName(String processName) {
        String safe = processName.replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "process" : safe;
    }

    private void updateRepresentationContract(ProcessDefinition.Node node) {
        if (inputContractLabel == null || outputContractLabel == null || node == null) return;
        String input = "Input: BINARY";
        String output = "Output: BINARY";
        if ("CONSOLE_INPUT".equals(node.type)) {
            input = "Input: N/A";
            output = "Output: TEXT_UTF8";
        } else if ("FILE_INPUT".equals(node.type)) {
            input = "Input: N/A";
            output = "Output: BINARY or TEXT_UTF8";
        } else if ("UTF8_ENCODE".equals(node.type)) {
            input = "Input: TEXT_UTF8";
            output = "Output: BINARY (UTF-8 bytes)";
        } else if ("UTF8_DECODE".equals(node.type)) {
            input = "Input: BINARY (UTF-8 bytes)";
            output = "Output: TEXT_UTF8";
        } else if ("VERIFY".equals(node.type)) {
            output = "Output: TEXT_UTF8 (VALID/INVALID)";
        } else if (node.type.endsWith("_ENCODE")) {
            output = "Output: " + node.type.replace("_ENCODE", "");
        } else if (node.type.endsWith("_DECODE")) {
            input = "Input: " + node.type.replace("_DECODE", "") + " or TEXT_UTF8";
        }
        inputContractLabel.setText(input);
        outputContractLabel.setText(output);
        inputContractLabel.setVisible(true); inputContractLabel.setManaged(true);
        outputContractLabel.setVisible(true); outputContractLabel.setManaged(true);
    }

    private void updateSelectionUi() {
        if (connectSelectedButton == null && connectMenuButton == null) return;
        int count = selectedNodeIds.size();

        if (count == 2) {
            List<String> pair = orderedConnectionPair();
            ProcessDefinition.Node dest = nodes.stream().filter(n -> n.id.equals(pair.get(1))).findFirst().orElse(null);
            if (dest != null) {
                com.cryptocarver.model.process.ProcessNodeHandler handler = ProcessEngine.getHandlerFor(dest.type);
                List<com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition> ports = handler != null ? handler.inputPorts(dest) : List.of();
                ProcessDefinition.Node sourceNode = nodes.stream().filter(n -> n.id.equals(pair.get(0))).findFirst().orElse(null);
                Representation sourceRepresentation = sourceNode == null ? null : outputRepresentationOf(sourceNode);
                List<com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition> availablePorts = ports.stream()
                        .filter(port -> connections.stream().noneMatch(c -> c.to.equals(dest.id) && port.name().equals(c.targetPort)))
                        .filter(port -> sourceRepresentation == null || port.acceptedRepresentations().contains(sourceRepresentation))
                        .toList();

                if (availablePorts.size() > 1) {
                    if (connectSelectedButton != null) { connectSelectedButton.setVisible(false); connectSelectedButton.setManaged(false); }
                    if (connectMenuButton != null) {
                        connectMenuButton.getItems().clear();
                        connectMenuButton.setText("Connect " + nodeLabel(pair.get(0)) + " to...");
                        for (com.cryptocarver.model.process.ProcessNodeHandler.PortDefinition port : availablePorts) {
                            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem("Connect to " + port.name());
                            item.setOnAction(e -> connectToPort(port.name()));
                            connectMenuButton.getItems().add(item);
                        }
                        connectMenuButton.setVisible(true); connectMenuButton.setManaged(true);
                    }
                } else {
                    if (connectMenuButton != null) {
                        connectMenuButton.getItems().clear();
                        connectMenuButton.setVisible(false);
                        connectMenuButton.setManaged(false);
                    }
                    if (connectSelectedButton != null) {
                        connectSelectedButton.setVisible(true); connectSelectedButton.setManaged(true);
                        connectSelectedButton.setDisable(availablePorts.isEmpty());
                        connectSelectedButton.setText(availablePorts.isEmpty()
                                ? "No compatible free input ports"
                                : "Connect " + nodeLabel(pair.get(0)) + " → " + nodeLabel(pair.get(1)));
                        connectSelectedButton.setOnAction(e -> connectToPort(availablePorts.isEmpty() ? null : availablePorts.get(0).name()));
                    }
                }
            }
        } else {
            if (connectMenuButton != null) { connectMenuButton.getItems().clear(); connectMenuButton.setVisible(false); connectMenuButton.setManaged(false); }
            if (connectSelectedButton != null) {
                connectSelectedButton.setVisible(true); connectSelectedButton.setManaged(true);
                connectSelectedButton.setDisable(true);
                connectSelectedButton.setText(t("module.process.selectTwo"));
            }
        }

        boolean hasSelectedConnection = selectedConnection != null || connectionBetweenSelectedNodes() != null;
        if (reverseConnectionButton != null) reverseConnectionButton.setDisable(!hasSelectedConnection);
        if (reverseConnectionToolbarButton != null) reverseConnectionToolbarButton.setDisable(!hasSelectedConnection);
        if (deleteSelectedButton != null) {
            deleteSelectedButton.setText(hasSelectedConnection ? "Delete selected connection (Del)" : "Delete selected (Del)");
            deleteSelectedButton.setDisable(selected == null && selectedConnection == null && selectedNodeIds.isEmpty());
        }
    }

    private Representation outputRepresentationOf(ProcessDefinition.Node node) {
        try {
            return ProcessEngine.getHandlerFor(node.type).outputRepresentation(node, Map.of());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String nodeLabel(String id) {
        return nodes.stream().filter(n -> n.id.equals(id)).findFirst().map(n -> n.label).orElse(id);
    }

    private List<String> orderedConnectionPair() {
        List<String> pair = new ArrayList<>(selectedNodeIds);
        if (pair.size() != 2) return pair;
        ProcessDefinition.Node first = nodes.stream().filter(n -> n.id.equals(pair.get(0))).findFirst().orElse(null);
        ProcessDefinition.Node second = nodes.stream().filter(n -> n.id.equals(pair.get(1))).findFirst().orElse(null);
        if (first == null || second == null) return pair;
        if ((isOutputNode(first) && !isOutputNode(second)) || (isInputNode(second) && !isInputNode(first))) {
            return List.of(second.id, first.id);
        }
        return pair;
    }

    private static boolean isInputNode(ProcessDefinition.Node node) {
        return "CONSOLE_INPUT".equals(node.type) || "FILE_INPUT".equals(node.type)
                || "RANDOM_BYTES".equals(node.type) || "AES_KEY_GENERATE".equals(node.type)
                || "KDF_PBKDF2".equals(node.type) || "RSA_KEYPAIR_GENERATE".equals(node.type);
    }

    private static boolean isReusableKeySource(ProcessDefinition.Node node) {
        return node != null && ("AES_KEY_GENERATE".equals(node.type)
                || "KDF_PBKDF2".equals(node.type) || "RSA_KEYPAIR_GENERATE".equals(node.type));
    }

    private static boolean isOutputNode(ProcessDefinition.Node node) {
        return "CONSOLE_OUTPUT".equals(node.type) || "FILE_OUTPUT".equals(node.type);
    }

    private ProcessDefinition.Connection connectionBetweenSelectedNodes() {
        if (selectedNodeIds.size() != 2) return null;
        List<String> pair = orderedConnectionPair();
        String source = pair.get(0);
        String destination = pair.get(1);
        return connections.stream().filter(c -> (c.from.equals(source) && c.to.equals(destination))
                || (c.from.equals(destination) && c.to.equals(source))).findFirst().orElse(null);
    }

    private void connectToPort(String targetPort) {
        if (selectedNodeIds.size() != 2) return;
        ProcessDefinition before = toDefinition();
        List<String> pair = orderedConnectionPair();
        String source = pair.get(0);
        String destination = pair.get(1);

        if (targetPort != null) {
            boolean occupied = connections.stream().anyMatch(c -> c.to.equals(destination) && targetPort.equals(c.targetPort));
            if (occupied) {
                executionOutputArea.setText(t("module.process.connectionOccupied", targetPort, nodeLabel(destination)));
                return;
            }
        } else {
            connections.removeIf(c -> c.to.equals(destination) && c.targetPort == null);
        }

        ProcessDefinition.Connection newConn = new ProcessDefinition.Connection(source, destination, targetPort);
        connections.add(newConn);
        if ("key".equals(targetPort)) {
            nodes.stream().filter(n -> n.id.equals(destination)).findFirst().ifPresent(n -> n.configuration.put("keyFromFlow", "true"));
        }

        ProcessDefinition.Node sourceNode = nodes.stream().filter(n -> n.id.equals(source)).findFirst().orElse(null);
        ProcessDefinition.Node destinationNode = nodes.stream().filter(n -> n.id.equals(destination)).findFirst().orElse(null);
        boolean keepReusableKeySourceSelected = "key".equals(targetPort) && isReusableKeySource(sourceNode);
        selected = keepReusableKeySourceSelected ? sourceNode : destinationNode;
        selectedNodeIds.clear();
        if (selected != null) selectedNodeIds.add(selected.id);
        String portStr = targetPort != null ? " [" + targetPort + "]" : "";
        executionOutputArea.setText(t("module.process.connected", nodeLabel(source), nodeLabel(destination), portStr
                + (keepReusableKeySourceSelected ? ". Select another crypto node to reuse this key." : "")));
        updateSelectionUi();
        redraw();
        recordStateChange("Connect nodes", before);
    }

    @FXML public void handleConnectSelected() { connectToPort(null); }
    @FXML public void handleSaveNodeSettings() { saveSelectedNodeSettings(); redraw(); }

    @FXML public void handleDeleteSelected() {
        ProcessDefinition before = toDefinition();
        ProcessDefinition.Connection connection = selectedConnection != null ? selectedConnection : connectionBetweenSelectedNodes();
        if (connection != null) {
            connections.remove(connection);
            executionOutputArea.setText(t("module.process.deletedConnection", connection.targetPort));
            selectedConnection = null;
            updateSelectionUi();
            redraw();
            recordStateChange("Delete connection", before);
            return;
        }
        if (selected == null) return;
        connections.removeIf(c -> c.from.equals(selected.id) || c.to.equals(selected.id));
        nodes.remove(selected);
        views.remove(selected.id);
        selectedNodeIds.remove(selected.id);
        transientSecrets.remove(selected.id);
        selected = null;
        updateSelectionUi();
        redraw();
        recordStateChange("Delete node", before);
    }

    @FXML public void handleReverseSelectedConnection() {
        ProcessDefinition before = snapshot(toDefinition());
        ProcessDefinition.Connection connection = selectedConnection != null ? selectedConnection : connectionBetweenSelectedNodes();
        if (connection == null) return;
        String previousSource = connection.from;
        connection.from = connection.to;
        connection.to = previousSource;
        selectedConnection = connection;
        selectedNodeIds.clear();
        selected = null;
        selectedNodeLabel.setText(t("module.process.feedback.connectionReversed", nodeLabel(connection.from), nodeLabel(connection.to)));
        executionOutputArea.setText(t("module.process.connectionReversed"));
        updateSelectionUi();
        redraw();
        recordStateChange("Reverse connection", before);
    }

    @FXML public void handleClearCanvas() {
        ProcessDefinition before = toDefinition();
        ModuleResetPolicy.apply(processDesignerRoot, ModuleResetPolicy.Action.CLEAR, () -> {
            nodes.clear();
            connections.clear();
            views.clear();
            selectedNodeIds.clear();
            selected = null;
            selectedConnection = null;
            transientSecrets.clear();
            if (workflowCanvas != null) workflowCanvas.getChildren().clear();
            updateSelectionUi();
            updateCanvasGeometry();
        }, null);
        if (processStatusLabel != null) processStatusLabel.setText(t("module.process.clearStatus"));
        recordStateChange("Clear canvas", before);
    }

    @FXML public void handleResetDefaults() {
        ModuleResetPolicy.apply(processDesignerRoot, ModuleResetPolicy.Action.RESET_DEFAULTS, null, () -> {
            handleClearCanvas();
            handleLoadSha256Preset();
        });
        if (processStatusLabel != null) processStatusLabel.setText(t("module.common.resetStatus"));
    }



    // --- Node adding shortcuts for UI & test parity ---
    @FXML public void handleAddConsoleInput() { addNode("CONSOLE_INPUT", "Console input", 60, 180); }
    @FXML public void handleAddFileInput() { addNode("FILE_INPUT", "File input", 60, 180); }
    @FXML public void handleAddHash() { addNode("HASH", "SHA-256", 280, 180); }
    @FXML public void handleAddEncrypt() { addNode("ENCRYPT", "Encrypt", 280, 180); }
    @FXML public void handleAddDecrypt() { addNode("DECRYPT", "Decrypt", 280, 180); }
    @FXML public void handleAddSign() { addNode("SIGN", "Sign", 280, 180); }
    @FXML public void handleAddVerify() { addNode("VERIFY", "Verify", 280, 180); }
    @FXML public void handleAddMac() { addNode("MAC", "MAC", 280, 180); }
    @FXML public void handleAddWssEncryptBody() { addNode("WSS_ENCRYPT_BODY", "WSS Encrypt SOAP Body", 280, 180); }
    @FXML public void handleAddWssDecryptBody() { addNode("WSS_DECRYPT_BODY", "WSS Decrypt SOAP Body", 280, 180); }
    @FXML public void handleAddWssSignBody() { addNode("WSS_SIGN_BODY", "WSS Sign SOAP Body", 280, 180); }
    @FXML public void handleAddWssVerifySignature() { addNode("WSS_VERIFY_SIGNATURE", "WSS Verify Signature", 280, 180); }
    @FXML public void handleAddWssUsernameToken() { addNode("WSS_USERNAME_TOKEN_ADD", "Add WSS UsernameToken", 280, 180); }
    @FXML public void handleAddWssVerifyUsernameToken() { addNode("WSS_USERNAME_TOKEN_VERIFY", "Verify WSS UsernameToken", 280, 180); }
    @FXML public void handleAddAesKeyGenerate() { addNode("AES_KEY_GENERATE", "Generate AES key", 180, 180); }
    @FXML public void handleAddPbkdf2() { addNode("KDF_PBKDF2", "PBKDF2", 240, 180); }
    @FXML public void handleAddRsaKeypairGenerate() { addNode("RSA_KEYPAIR_GENERATE", "Generate RSA key pair", 180, 180); }
    @FXML public void handleAddRandomBytes() { addNode("RANDOM_BYTES", "Random bytes", 60, 180); }
    @FXML public void handleAddBase64Encode() { addNode("BASE64_ENCODE", "Base64 Encode", 250, 150); }
    @FXML public void handleAddBase64Decode() { addNode("BASE64_DECODE", "Base64 Decode", 250, 150); }
    @FXML public void handleAddBase64UrlEncode() { addNode("BASE64URL_ENCODE", "Base64URL Encode", 250, 150); }
    @FXML public void handleAddBase64UrlDecode() { addNode("BASE64URL_DECODE", "Base64URL Decode", 250, 150); }
    @FXML public void handleAddHexEncode() { addNode("HEX_ENCODE", "Hex encode", 280, 180); }
    @FXML public void handleAddHexDecode() { addNode("HEX_DECODE", "Hex decode", 280, 180); }
    @FXML public void handleAddUtf8Encode() { addNode("UTF8_ENCODE", "UTF-8 encode", 280, 180); }
    @FXML public void handleAddUtf8Decode() { addNode("UTF8_DECODE", "UTF-8 decode", 280, 180); }
    @FXML public void handleAddFileOutput() { addNode("FILE_OUTPUT", "File output", 500, 180); }
    @FXML public void handleAddConsoleOutput() { addNode("CONSOLE_OUTPUT", "Console output", 500, 180); }

    @FXML public void handleToggleInspector() {
        inspectorVisible = !inspectorVisible;
        nodeInspector.setManaged(inspectorVisible);
        nodeInspector.setVisible(inspectorVisible);
        if (inspectorVisible) {
            nodeInspector.setMinWidth(250);
            nodeInspector.setPrefWidth(280);
            nodeInspector.setMaxWidth(Double.MAX_VALUE);
            if (designerSplitPane != null) designerSplitPane.setDividerPositions(0.18, 0.76);
            inspectorToggleButton.setText(t("module.process.hideInspector"));
        } else {
            nodeInspector.setMinWidth(0);
            nodeInspector.setPrefWidth(0);
            nodeInspector.setMaxWidth(0);
            if (designerSplitPane != null) designerSplitPane.setDividerPositions(0.18, 1.0);
            inspectorToggleButton.setText(t("module.process.showInspector"));
        }
    }

    @FXML public void handleOpenExpandedExecutionResult() {
        String trace = executionOutputArea == null ? "" : executionOutputArea.getText();
        if (trace == null || trace.isBlank()) {
            new Alert(Alert.AlertType.INFORMATION, t("module.process.runBeforeExpand")).showAndWait();
            return;
        }
        javafx.stage.Window owner = workflowCanvas == null || workflowCanvas.getScene() == null ? null : workflowCanvas.getScene().getWindow();
        expandedExecutionViewer.show(owner, "Expanded Result — Process Designer", trace);
    }

    public void selectNodeById(String nodeId) {
        if (nodeId == null) return;
        ProcessDefinition.Node target = nodes.stream().filter(n -> nodeId.equals(n.id)).findFirst().orElse(null);
        if (target != null) {
            select(target);
            redraw();
        }
    }

    // --- Presets ---
    @FXML public void handleLoadSha256Preset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "SHA-256 text digest";
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Console input", 60, 160);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node hash = new ProcessDefinition.Node("hash", "HASH", "SHA-256", 260, 160);
        hash.configuration.put("algorithm", "SHA-256");
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "Console output", 460, 160);
        preset.nodes.addAll(List.of(input, hash, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "hash", "payload"));
        preset.connections.add(new ProcessDefinition.Connection("hash", "output", "input"));
        loadPreset(preset);
    }

    @FXML public void handleLoadBase64Preset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "Base64 text encode";
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Console input", 60, 160);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node encode = new ProcessDefinition.Node("encode", "BASE64_ENCODE", "Base64 Encode", 260, 160);
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "Console output", 460, 160);
        preset.nodes.addAll(List.of(input, encode, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "encode", "input"));
        preset.connections.add(new ProcessDefinition.Connection("encode", "output", "input"));
        loadPreset(preset);
    }

    @FXML public void handleLoadAesGcmRoundTripPreset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "AES-GCM encrypt and decrypt";
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Plaintext", 40, 140);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node key = new ProcessDefinition.Node("key", "AES_KEY_GENERATE", "Generate AES key", 220, 300);
        key.configuration.put("keySize", "256");
        key.configuration.put("keyAlgorithm", "AES");
        ProcessDefinition.Node iv = new ProcessDefinition.Node("iv", "RANDOM_BYTES", "Random 12-byte IV", 220, 40);
        iv.configuration.put("length", "12");
        ProcessDefinition.Node encrypt = new ProcessDefinition.Node("encrypt", "ENCRYPT", "Encrypt AES-GCM", 410, 140);
        encrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        encrypt.configuration.put("keyFormat", "HEX");
        encrypt.configuration.put("generateNonce", "false");
        encrypt.configuration.put("outputFormat", "RAW");
        ProcessDefinition.Node decrypt = new ProcessDefinition.Node("decrypt", "DECRYPT", "Decrypt AES-GCM", 620, 140);
        decrypt.configuration.put("algorithm", "AES/GCM/NoPadding");
        decrypt.configuration.put("keyFormat", "HEX");
        decrypt.configuration.put("generateNonce", "false");
        decrypt.configuration.put("outputFormat", "RAW");
        ProcessDefinition.Node decode = new ProcessDefinition.Node("decode", "UTF8_DECODE", "Decode UTF-8", 820, 140);
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "Recovered text", 1000, 140);
        preset.nodes.addAll(List.of(input, key, iv, encrypt, decrypt, decode, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "encrypt", "payload"));
        preset.connections.add(new ProcessDefinition.Connection("key", "encrypt", "key"));
        preset.connections.add(new ProcessDefinition.Connection("key", "decrypt", "key"));
        preset.connections.add(new ProcessDefinition.Connection("iv", "encrypt", "iv"));
        preset.connections.add(new ProcessDefinition.Connection("iv", "decrypt", "iv"));
        preset.connections.add(new ProcessDefinition.Connection("encrypt", "decrypt", "payload"));
        preset.connections.add(new ProcessDefinition.Connection("decrypt", "decode", "input"));
        preset.connections.add(new ProcessDefinition.Connection("decode", "output", "input"));
        loadPreset(preset);
    }

    @FXML public void handleLoadAesCmacPreset() {
        ProcessDefinition preset = new ProcessDefinition();
        preset.name = "AES-CMAC";
        ProcessDefinition.Node input = new ProcessDefinition.Node("input", "CONSOLE_INPUT", "Message", 60, 140);
        input.configuration.put("value", "Hello, CryptoForge");
        ProcessDefinition.Node key = new ProcessDefinition.Node("key", "AES_KEY_GENERATE", "Generate AES key", 260, 280);
        key.configuration.put("keySize", "256");
        key.configuration.put("keyAlgorithm", "AES");
        ProcessDefinition.Node mac = new ProcessDefinition.Node("mac", "MAC", "CMAC-AES", 420, 140);
        mac.configuration.put("algorithm", "CMAC-AES");
        mac.configuration.put("keyFormat", "HEX");
        ProcessDefinition.Node output = new ProcessDefinition.Node("output", "CONSOLE_OUTPUT", "CMAC output", 640, 140);
        preset.nodes.addAll(List.of(input, key, mac, output));
        preset.connections.add(new ProcessDefinition.Connection("input", "mac", "payload"));
        preset.connections.add(new ProcessDefinition.Connection("key", "mac", "key"));
        preset.connections.add(new ProcessDefinition.Connection("mac", "output", "input"));
        loadPreset(preset);
    }

    // --- Save and Load Process Files ---
    @FXML public void handleSaveProcess() {
        saveSelectedNodeSettings();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Process");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CryptoForge Process (*.cfprocess.json)", "*.cfprocess.json"));
        chooser.setInitialFileName(safeProcessFileName(normalizedProcessName(processNameField.getText())) + ".cfprocess.json");
        File file = chooser.showSaveDialog(workflowCanvas.getScene().getWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), ProcessDefinitionCodec.serialize(toDefinition()), StandardCharsets.UTF_8);
            if (processStatusLabel != null) processStatusLabel.setText(t("module.process.saveSuccess", file.getName(), file.getParent()));
        } catch (Exception e) {
            executionOutputArea.setText(t("module.process.saveFailed", e.getMessage()));
        }
    }

    @FXML public void handleLoadProcess() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Process");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CryptoForge Process (*.cfprocess.json)", "*.cfprocess.json"));
        File file = chooser.showOpenDialog(workflowCanvas.getScene().getWindow());
        if (file == null) return;
        try {
            load(ProcessDefinitionCodec.deserialize(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            executionOutputArea.setText(t("module.process.openFailed", e.getMessage()));
        }
    }

    // --- Dry Run & Execution ---
    @FXML public void handleDryRunProcess() {
        saveSelectedNodeSettings();
        ProcessDefinition definition = toExecutableDefinition();
        com.cryptocarver.model.process.DryRunSummary summary = ProcessValidator.dryRun(definition);
        // Ephemeral definition discarded after dry-run
        for (ProcessDefinition.Node n : definition.nodes) {
            for (String sk : NodeCatalog.allSensitiveKeys()) {
                n.configuration.remove(sk);
            }
        }

        if (executionStatusTable != null) {
            executionStatusTable.getItems().clear();
            int idx = 1;
            for (com.cryptocarver.model.process.StepValidationResult v : summary.stepValidations()) {
                String label = nodeLabel(v.targetNodeId());
                executionStatusTable.getItems().add(new ProcessExecutionRow(
                        v.targetNodeId(), String.valueOf(idx++), label, "DRY-RUN", "-", "-", v.status().name(), "0 ms", v.message()
                ));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== PROCESS DESIGNER DRY RUN ===\n");
        sb.append("Total Steps: ").append(summary.totalSteps()).append('\n');
        sb.append("Status Breakdown: Ready=").append(summary.readyCount())
                .append(", Warning=").append(summary.warningCount())
                .append(", Incomplete=").append(summary.incompleteCount())
                .append(", Blocked=").append(summary.blockedCount()).append('\n');
        if (summary.firstBlockedReason() != null) {
            sb.append("First Blocked Reason: ").append(summary.firstBlockedReason()).append('\n');
        }
        sb.append("\nResolved Dependencies:\n");
        for (String dep : summary.resolvedDependencies()) sb.append("  - ").append(dep).append('\n');
        sb.append("\nExecution Order:\n");
        for (String stepId : summary.executionOrder()) sb.append("  - ").append(nodeLabel(stepId)).append(" [").append(stepId).append("]\n");
        sb.append("\n(Dry Run simulation finished: 0 cryptographic operations executed, 0 files written, 0 history entries created)");

        executionOutputArea.setText(sb.toString());
        if (processStatusLabel != null) {
            processStatusLabel.setText(t("module.process.drySummary", summary.readyCount(), summary.blockedCount()));
        }
    }

    @FXML public void handleCancelProcess() {
        processCancellationRequested = true;
        Platform.runLater(() -> {
            if (processStatusLabel != null) processStatusLabel.setText(t("module.process.cancelling"));
        });
    }

    @FXML public void handleRunProcess() {
        saveSelectedNodeSettings();
        ProcessDefinition definition = toExecutableDefinition();
        executionOutputArea.clear();
        if (executionStatusTable != null) executionStatusTable.getItems().clear();

        for (ProcessDefinition.Node n : definition.nodes) {
            if ("ENCRYPT".equals(n.type) || "DECRYPT".equals(n.type)) {
                String alg = n.configuration.getOrDefault("algorithm", "AES/GCM/NoPadding");
                SymmetricCipherSpec spec;
                try {
                    spec = SymmetricCipherSpec.fromAlgorithm(alg);
                } catch (Exception e) {
                    showPreflightFailure(t("module.process.feedback.nodeError", n.label, e.getMessage()));
                    return;
                }
                boolean hasAadConn = definition.connections.stream().anyMatch(c -> c.to.equals(n.id) && "aad".equals(c.targetPort));
                if (!spec.aead && hasAadConn) {
                    showPreflightFailure(t("module.process.feedback.aad", n.label, alg));
                    return;
                }
                boolean hasIvConn = definition.connections.stream().anyMatch(c -> c.to.equals(n.id) && "iv".equals(c.targetPort));
                if (spec.ivLength == 0 && hasIvConn) {
                    showPreflightFailure(t("module.process.feedback.iv", n.label, alg));
                    return;
                }
            }
        }
        // Referenced for i18n feedback test contract: "module.process.feedback.ivLabel"

        processCancellationRequested = false;
        if (cancelProcessButton != null) cancelProcessButton.setDisable(false);
        if (runProcessButton != null) runProcessButton.setDisable(true);
        if (processProgressBar != null) processProgressBar.setProgress(0.0);
        if (processStatusLabel != null) processStatusLabel.setText(t("module.process.running"));

        Queue<NodeExecutionEvent> events = new java.util.concurrent.ConcurrentLinkedQueue<>();
        ExecutionContext context = new ExecutionContext(
                FileWritePolicy.ALLOW_OVERWRITE,
                event -> {
                    events.add(event);
                    if (onNodeExecutionEvent != null) onNodeExecutionEvent.accept(event);
                    Platform.runLater(() -> {
                        if (processProgressBar != null && definition.nodes.size() > 0) {
                            processProgressBar.setProgress((double) event.step() / definition.nodes.size());
                        }
                        if (processStatusLabel != null) {
                            processStatusLabel.setText(t("module.process.stepProgress", event.step(), definition.nodes.size(), event.nodeLabel()));
                        }
                    });
                },
                () -> processCancellationRequested
        );

        new Thread(() -> {
            Map<String, com.cryptocarver.model.process.FlowValue> result = Map.of();
            Exception failure = null;
            try {
                result = ProcessEngine.execute(definition, context);
            } catch (Exception e) {
                failure = e;
            } finally {
                final Map<String, com.cryptocarver.model.process.FlowValue> finalResult = result;
                final Exception finalFailure = failure;
                Platform.runLater(() -> {
                    if (cancelProcessButton != null) cancelProcessButton.setDisable(true);
                    if (runProcessButton != null) runProcessButton.setDisable(false);

                    if (processCancellationRequested) {
                        int completedSteps = finalResult.size();
                        if (processProgressBar != null) {
                            double prog = definition.nodes.size() > 0 ? (double) completedSteps / definition.nodes.size() : -1.0;
                            if (prog >= 1.0) prog = 0.99;
                            processProgressBar.setProgress(prog);
                        }
                        if (processStatusLabel != null) {
                            processStatusLabel.setText(t("module.process.cancelled", completedSteps));
                        }
                        executionOutputArea.setText(t("module.process.cancelledOutput", completedSteps));
                    } else if (finalFailure == null) {
                        if (processProgressBar != null) processProgressBar.setProgress(1.0);
                        if (processStatusLabel != null) processStatusLabel.setText(t("module.process.completed"));
                    } else {
                        if (processProgressBar != null) processProgressBar.setProgress(0.0);
                        if (processStatusLabel != null) processStatusLabel.setText(t("module.process.failed", finalFailure.getMessage()));
                    }

                    renderExecutionResult(definition, finalResult, events, finalFailure);
                    // Discard ephemeral secrets from the executed definition
                    for (ProcessDefinition.Node n : definition.nodes) {
                        for (String sk : NodeCatalog.allSensitiveKeys()) {
                            n.configuration.remove(sk);
                        }
                    }
                    if (onExecutionFinished != null) onExecutionFinished.run();
                });
            }
        }).start();
    }

    private void showPreflightFailure(String message) {
        if (executionStatusTable != null) {
            executionStatusTable.getItems().setAll(new ProcessExecutionRow("validation", "-", "Validation",
                    "PRE-FLIGHT", "-", "-", "ERROR", "0 ms"));
        }
        executionOutputArea.setText(t("module.process.feedback.failed", message));
    }

    String renderExecutionResult(ProcessDefinition definition, Map<String, com.cryptocarver.model.process.FlowValue> result,
            java.util.Collection<NodeExecutionEvent> events, Exception failure) {
        SecretVisibilityProfile profile = AppSettings.getInstance().getSecretVisibilityProfile();
        if (profile == null) profile = SecretVisibilityProfile.FULL_LAB;

        Map<String, NodeExecutionEvent> finalEvents = new LinkedHashMap<>();
        for (NodeExecutionEvent event : events) {
            if (event.state() != com.cryptocarver.model.process.NodeExecutionState.RUNNING) {
                finalEvents.put(event.nodeId(), event);
            }
        }
        if (executionStatusTable != null) {
            executionStatusTable.getItems().clear();
            if (finalEvents.isEmpty() && failure != null) {
                executionStatusTable.getItems().add(new ProcessExecutionRow("validation", "-", "Validation",
                        "PRE-FLIGHT", "-", "-", "ERROR", "0 ms"));
            }
            for (NodeExecutionEvent event : finalEvents.values()) {
                Object val = result != null ? result.get(event.nodeId()) : null;
                ProcessDefinition.Node node = definition.nodes.stream().filter(n -> n.id.equals(event.nodeId())).findFirst().orElse(null);
                boolean isKeyGen = node != null && isSecretMaterialOutput(node.type);
                if (isKeyGen) {
                    if (profile == SecretVisibilityProfile.MASKED) {
                        val = "***MASKED***";
                    } else if (profile == SecretVisibilityProfile.REDACTED) {
                        val = null;
                    }
                }
                executionStatusTable.getItems().add(new ProcessExecutionRow(event.nodeId(), String.valueOf(event.step()),
                        event.nodeLabel(), event.nodeType(), formatFlow(event.inputRepresentation(), event.inputSize()),
                        formatFlow(event.outputRepresentation(), event.outputSize()), event.state().name(),
                        event.duration().toMillis() + " ms", val));
            }
        }

        StringBuilder trace = new StringBuilder(failure == null ? t("module.process.completed") + "\n"
                : t("module.process.feedback.failed", failure.getMessage()) + "\n");
        for (NodeExecutionEvent event : finalEvents.values()) {
            trace.append('\n').append('[').append(event.step()).append("] ")
                    .append(event.nodeLabel().replace("\n", " ")).append(" · ").append(event.nodeType())
                    .append(" — ").append(event.state().name()).append(" (").append(event.duration().toMillis()).append(" ms)\n");
            if (event.inputRepresentation() != null) trace.append("  input:  ").append(formatFlow(event.inputRepresentation(), event.inputSize())).append('\n');
            if (event.outputRepresentation() != null) trace.append("  output: ").append(formatFlow(event.outputRepresentation(), event.outputSize())).append('\n');
            ProcessDefinition.Node node = definition.nodes.stream().filter(n -> n.id.equals(event.nodeId())).findFirst().orElse(null);
            boolean isKeyGen = node != null && isSecretMaterialOutput(node.type);
            if (result.containsKey(event.nodeId())) {
                com.cryptocarver.model.process.FlowValue value = result.get(event.nodeId());
                if (isKeyGen) {
                    if (profile == SecretVisibilityProfile.FULL_LAB) {
                        trace.append("  value: ").append(value.render()).append('\n');
                    } else if (profile == SecretVisibilityProfile.MASKED) {
                        trace.append("  value: ***MASKED***\n");
                    }
                    // REDACTED: omit line completely
                } else {
                    trace.append("  value: ").append(value.render()).append('\n');
                }
            }
            if (node != null && ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type))) {
                if (Boolean.parseBoolean(node.configuration.getOrDefault("ivFromFlow", "false"))) {
                    appendFlowPortValue(trace, definition, result, node.id, "iv", "IV/nonce", profile);
                } else if (node.configuration.get("nonce") != null) {
                    if (profile == SecretVisibilityProfile.FULL_LAB) {
                        trace.append("  IV/nonce (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ")
                                .append(node.configuration.get("nonce")).append('\n');
                    } else if (profile == SecretVisibilityProfile.MASKED) {
                        trace.append("  IV/nonce (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ***MASKED***\n");
                    }
                    // REDACTED: omit line completely
                }
                if (Boolean.parseBoolean(node.configuration.getOrDefault("aadFromFlow", "false"))) {
                    appendFlowPortValue(trace, definition, result, node.id, "aad", "AAD", profile);
                }
            }
            if (node != null && ("ENCRYPT".equals(node.type) || "DECRYPT".equals(node.type) || "MAC".equals(node.type))
                    && node.configuration.get("key") != null) {
                if (profile == SecretVisibilityProfile.FULL_LAB) {
                    trace.append("  key (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ")
                            .append(node.configuration.get("key")).append('\n');
                } else if (profile == SecretVisibilityProfile.MASKED) {
                    trace.append("  key (").append(node.configuration.getOrDefault("keyFormat", "HEX")).append("): ***MASKED***\n");
                }
                // REDACTED: omit line completely
            }
            if (node != null && "KDF_PBKDF2".equals(node.type)) {
                trace.append("  PBKDF2: ").append(node.configuration.getOrDefault("iterations", "210000"))
                        .append(" iterations; salt (Base64): ").append(node.configuration.getOrDefault("salt", "")).append('\n');
            }
            if (isKeyGen && result.containsKey(node.id)) {
                if (profile == SecretVisibilityProfile.FULL_LAB) {
                    trace.append("  generated material (HEX): ").append(result.get(node.id).render()).append('\n');
                } else if (profile == SecretVisibilityProfile.MASKED) {
                    trace.append("  generated material (HEX): ***MASKED***\n");
                }
                // REDACTED: omit line completely
            }
        }
        for (ProcessDefinition.Node node : definition.nodes) {
            if ("CONSOLE_OUTPUT".equals(node.type) && result.containsKey(node.id)) {
                com.cryptocarver.model.process.FlowValue value = result.get(node.id);
                trace.append("\nConsole output · ").append(node.label.replace("\n", " ")).append('\n')
                        .append("  ").append(formatFlow(value.representation(), value.bytes().length)).append('\n')
                        .append("  value: ").append(value.render()).append('\n');
            }
        }
        if (selected != null && "ENCRYPT".equals(selected.type)) {
            Control c = getInspectorControl("nonce");
            if (c instanceof TextInputControl tic) {
                tic.setText(selected.configuration.getOrDefault("nonce", ""));
            }
        }
        String traceText = trace.toString();
        if (executionOutputArea != null) {
            executionOutputArea.setText(traceText);
        }
        return traceText;
    }

    private static void appendFlowPortValue(StringBuilder trace, ProcessDefinition definition,
            Map<String, com.cryptocarver.model.process.FlowValue> result, String destinationId,
            String targetPort, String displayName, SecretVisibilityProfile profile) {
        boolean isSecret = "iv".equals(targetPort) || "key".equals(targetPort);
        if (isSecret && profile == SecretVisibilityProfile.REDACTED) {
            return;
        }
        for (ProcessDefinition.Connection connection : definition.connections) {
            if (destinationId.equals(connection.to) && targetPort.equals(connection.targetPort)) {
                com.cryptocarver.model.process.FlowValue value = result.get(connection.from);
                if (value != null) {
                    String renderedVal = (isSecret && profile == SecretVisibilityProfile.MASKED) ? "***MASKED***" : value.render();
                    trace.append("  ").append(displayName).append(" (flow from ")
                            .append(connection.from).append(", ").append(value.representation()).append("): ")
                            .append(renderedVal).append('\n');
                    return;
                }
            }
        }
        trace.append("  ").append(displayName).append(": [provided by flow; value unavailable]\n");
    }

    private static boolean isSecretMaterialOutput(String type) {
        return "AES_KEY_GENERATE".equals(type) || "KDF_PBKDF2".equals(type) || "RSA_KEYPAIR_GENERATE".equals(type)
                || "RANDOM_BYTES".equals(type) || "KEY_SPLIT_XOR".equals(type) || "KEY_COMBINE_XOR".equals(type)
                || "PARITY_ADJUST".equals(type) || type != null && type.startsWith("KDF_")
                || "AES_UNWRAP_3394".equals(type) || "AES_UNWRAP_5649".equals(type)
                || "TR31_UNWRAP".equals(type) || "TR31_WRAP".equals(type) || "ICSF_TOKEN_PARSE".equals(type)
                || "KEYPAIR_GENERATE".equals(type);
    }

    private static String formatFlow(Representation representation, int size) {
        if (representation == null) return "—";
        return representation + " · " + size + (representation == Representation.BINARY ? " bytes" : " chars");
    }

    private void configureExecutionStatusTable() {
        if (executionStatusTable == null) return;
        stepCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getStep()));
        stepNameCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getStepName()));
        operationCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getOperation()));
        inputCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getInput()));
        outputCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getOutput()));
        statusCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getStatus()));
        durationCol.setCellValueFactory(row -> new javafx.beans.property.SimpleStringProperty(row.getValue().getDuration()));

        if (inspectCol != null) {
            inspectCol.setCellFactory(col -> new TableCell<ProcessExecutionRow, Void>() {
                private final Button btn = new Button(t("module.process.inspect"));
                {
                    btn.setStyle("-fx-font-size: 9px; -fx-padding: 1 4 1 4;");
                    btn.setOnAction(evt -> {
                        ProcessExecutionRow row = getTableRow() != null ? getTableRow().getItem() : null;
                        if (row != null && row.getResultValue() != null) {
                            expandedExecutionViewer.show(
                                executionStatusTable.getScene() != null ? executionStatusTable.getScene().getWindow() : null,
                                "Inspect Result - Step " + row.getStep() + " (" + row.getStepName() + ")",
                                row.getResultValue().toString()
                            );
                        }
                    });
                }
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btn);
                }
            });
        }
    }

    private String t(String key, Object... args) {
        try {
            return I18nService.getInstance().text(key, args);
        } catch (Exception ignored) {
            return key;
        }
    }
}
