package com.cryptocarver.ui;

import com.cryptocarver.model.process.NodeCatalog;
import com.cryptocarver.model.process.NodeExecutionEvent;
import com.cryptocarver.model.process.NodeParameter;
import com.cryptocarver.model.process.ProcessDefinition;
import com.cryptocarver.model.process.ProcessDefinitionCodec;
import com.cryptocarver.model.process.ProcessEngine;
import com.cryptocarver.model.process.Representation;
import com.cryptocarver.service.I18nService;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Controller for Process Designer.
 * Phase 5A: Descriptor-driven inspector, expandable canvas, zoom/pan, smooth connections,
 * searchable palette, and undo/redo command stack.
 */
public class ProcessDesignerController {
    /** Held so the locale listener stays registered: I18nService keeps only a weak reference. */
    private java.util.function.Consumer<java.util.Locale> localeChangeListener;

    private final DialogService dialogService = new DialogService();

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
    final LinkedHashSet<String> selectedNodeIds = new LinkedHashSet<>();
    private ProcessDefinition.Node selected;
    private ProcessDefinition.Connection selectedConnection;
    private boolean inspectorVisible = true;
    private boolean focusMode = false;
    private double currentZoom = 1.0;
    private final Scale canvasScale = new Scale(1.0, 1.0, 0, 0);
    private boolean snapToGrid = true;
    private ProcessExecutionCoordinator processExecutionCoordinator;
    private ProcessPaletteCoordinator processPaletteCoordinator;

    // Secrets in-memory map: nodeId -> (paramKey -> char[])
    final Map<String, Map<String, char[]>> transientSecrets = new HashMap<>();
    private NodeInspectorRenderer dynamicInspectorRenderer;

    // Performance tracking
    public int validationCounter = 0;

    // Command stack for Undo/Redo (>= 50 steps)
    public interface DesignerCommand {
        void undo();
        void redo();
    }

    private final ProcessUndoRedoCoordinator undoRedoCoordinator = new ProcessUndoRedoCoordinator();
    private final ProcessLayoutCoordinator layoutCoordinator = new ProcessLayoutCoordinator();
    private final ProcessSelectionCoordinator selectionCoordinator = new ProcessSelectionCoordinator();
    private final ProcessConnectionCoordinator connectionCoordinator = new ProcessConnectionCoordinator();
    private ProcessCanvasRenderer processCanvasRenderer;
    private ProcessPreflightPresenter processPreflightPresenter;

    public Runnable onExecutionFinished;
    public java.util.function.Consumer<NodeExecutionEvent> onNodeExecutionEvent;

    @FXML public void initialize() {
        moduleI18n = ModuleI18n.bind(processDesignerRoot, ModuleTextCatalog.processDesigner());
        localeChangeListener = locale -> {
            if (processStatusLabel != null && (processStatusLabel.getText() == null || processStatusLabel.getText().isBlank())) {
                processStatusLabel.setText(t("status.ready"));
            }
            if (executionStatusTable != null) {
                executionStatusTable.setPlaceholder(new Label(t("module.process.executionPlaceholder")));
            }
            updateSelectionUi();
            buildPalette();
        };
        I18nService.getInstance().addLocaleChangeListener(localeChangeListener);
        configureExecutionStatusTable();
        // The narrow inspector must accommodate localized captions on GTK and macOS.
        if (nodeInspector != null) {
            for (var node : nodeInspector.lookupAll(".button")) {
                if (node instanceof Button button) {
                    button.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                    button.setWrapText(true);
                    button.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                }
            }
        }

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
                if (connectionCoordinator.interactiveCurve() != null) cancelConnectionDrag();
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
            if (connectionCoordinator.isDragging()) {
                ProcessDefinition.Node sourceNode = connectionCoordinator.dragSourceNode();
                Point2D local = workflowCanvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                updateInteractiveCurve(sourceNode.x + 150, sourceNode.y + 35, local.getX(), local.getY());
            }
        });
    }


    // --- Searchable Palette ---
    private ProcessPaletteCoordinator processPaletteCoordinator() {
        if (processPaletteCoordinator == null) processPaletteCoordinator = new ProcessPaletteCoordinator();
        return processPaletteCoordinator;
    }

    private ProcessPaletteCoordinator.View paletteView() {
        return new ProcessPaletteCoordinator.View(() -> paletteItemsContainer, this::t,
                () -> nodes.size(), this::addNode, this::select);
    }

    private void buildPalette() { processPaletteCoordinator().buildPalette(paletteView(), paletteSearchField == null ? null : paletteSearchField.getText()); }

    private void filterPalette(String filter) { processPaletteCoordinator().filterPalette(paletteView(), filter); }

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
    public void executeCommand(DesignerCommand command) {
        undoRedoCoordinator.execute(command);
    }

    private void recordStateChange(String desc, ProcessDefinition before) {
        undoRedoCoordinator.recordStateChange(desc, before, undoRedoView());
    }

    @FXML public void handleUndo() { undoRedoCoordinator.undo(undoRedoView()); }

    @FXML public void handleRedo() { undoRedoCoordinator.redo(undoRedoView()); }

    private ProcessUndoRedoCoordinator.View undoRedoView() {
        return new ProcessUndoRedoCoordinator.View(this::toDefinition, this::load);
    }

    // --- Duplicate & Tidy Layout ---
    @FXML public void handleDuplicateSelected() { layoutCoordinator.duplicateSelected(layoutView()); }

    @FXML public void handleTidyLayout() { layoutCoordinator.tidyLayout(layoutView()); }

    private ProcessLayoutCoordinator.View layoutView() {
        return new ProcessLayoutCoordinator.View(() -> selected, this::toDefinition, nodes::add,
                this::select, () -> { updateCanvasGeometry(); redraw(); }, this::recordStateChange);
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
    void select(ProcessDefinition.Node node) { selectionCoordinator.select(selectionView(), node); }

    private ProcessSelectionCoordinator.View selectionView() {
        return new ProcessSelectionCoordinator.View(
                () -> nodes,
                () -> connections,
                () -> selectedNodeIds,
                () -> selected,
                node -> selected = node,
                () -> selectedConnection,
                connection -> selectedConnection = connection,
                this::saveSelectedNodeSettings,
                () -> selectedNodeLabel,
                () -> nodeNameFieldGroup,
                () -> nodeNameField,
                () -> dynamicInspectorContainer,
                () -> transientSecrets,
                renderer -> dynamicInspectorRenderer = renderer,
                this::updateRepresentationContract,
                this::redraw,
                () -> connectSelectedButton,
                () -> connectMenuButton,
                () -> reverseConnectionButton,
                () -> reverseConnectionToolbarButton,
                () -> deleteSelectedButton,
                this::orderedConnectionPair,
                this::outputRepresentationOf,
                this::connectionBetweenSelectedNodes,
                this::nodeLabel,
                this::portLabel,
                this::connectToPort,
                key -> t(key));
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
    void redraw() { processCanvasRenderer().redraw(canvasView()); }

    private ProcessCanvasRenderer processCanvasRenderer() {
        if (processCanvasRenderer == null) processCanvasRenderer = new ProcessCanvasRenderer();
        return processCanvasRenderer;
    }

    private ProcessCanvasRenderer.View canvasView() {
        return new ProcessCanvasRenderer.View(
                () -> workflowCanvas,
                () -> nodes,
                () -> connections,
                this::toDefinition,
                () -> selectedNodeIds,
                () -> selected,
                () -> selectedConnection,
                this::selectConnection,
                () -> snapToGrid,
                connectionCoordinator::isDragging,
                connectionCoordinator::dragSourceNode,
                this::select,
                this::startConnectionDrag,
                this::updateInteractiveCurve,
                this::cancelConnectionDrag,
                this::completeConnectionDragToPort,
                (from, to) -> completeConnectionDrag(from, to),
                (from, to, screenX, screenY) -> completeConnectionDrag(from, to, screenX, screenY),
                this::updateCanvasGeometry,
                () -> validationCounter++,
                this::recordStateChange,
                this::portLabel);
    }

    private void startConnectionDrag(ProcessDefinition.Node sourceNode) { connectionCoordinator.startConnectionDrag(connectionView(), sourceNode); }

    private void updateInteractiveCurve(double startX, double startY, double endX, double endY) { connectionCoordinator.updateInteractiveCurve(connectionView(), startX, startY, endX, endY); }

    void cancelConnectionDrag() { connectionCoordinator.cancelConnectionDrag(connectionView()); }

    void completeConnectionDragToPort(ProcessDefinition.Node from, ProcessDefinition.Node to, String targetPort) { connectionCoordinator.completeConnectionDragToPort(connectionView(), from, to, targetPort); }

    void completeConnectionDrag(ProcessDefinition.Node from, ProcessDefinition.Node to) { connectionCoordinator.completeConnectionDrag(connectionView(), from, to); }

    void completeConnectionDrag(ProcessDefinition.Node from, ProcessDefinition.Node to, double screenX, double screenY) { connectionCoordinator.completeConnectionDrag(connectionView(), from, to, screenX, screenY); }

    private ProcessConnectionCoordinator.View connectionView() {
        return new ProcessConnectionCoordinator.View(
                () -> nodes,
                () -> connections,
                () -> selectedNodeIds,
                () -> selected,
                node -> selected = node,
                () -> workflowCanvas,
                () -> processCanvasRenderer().inputPortHandles(),
                this::outputRepresentationOf,
                processCanvasRenderer()::updateCurveControls,
                this::toDefinition,
                this::orderedConnectionPair,
                this::nodeLabel,
                this::portLabel,
                this::t,
                () -> executionOutputArea,
                this::updateSelectionUi,
                this::redraw,
                this::recordStateChange);
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
        return ProcessUndoRedoCoordinator.snapshot(def);
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

    private void updateSelectionUi() { selectionCoordinator.updateSelectionUi(selectionView()); }

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

    private void connectToPort(String targetPort) { connectionCoordinator.connectToPort(connectionView(), targetPort); }

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
        processCanvasRenderer().removeView(selected.id);
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
            processCanvasRenderer().clearViews();
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
            nodeInspector.setMinWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
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
            dialogService.info("Process Designer", t("module.process.runBeforeExpand"));
            return;
        }
        javafx.stage.Window owner = workflowCanvas == null || workflowCanvas.getScene() == null ? null : workflowCanvas.getScene().getWindow();
        processExecutionCoordinator().showExpandedResult(owner, trace);
    }

    public void selectNodeById(String nodeId) {
        selectionCoordinator.selectNodeById(selectionView(), nodeId);
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
    @FXML public void handleDryRunProcess() { processExecutionCoordinator().dryRun(executionView()); }

    @FXML public void handleCancelProcess() { processExecutionCoordinator().cancel(executionView()); }

    @FXML public void handleRunProcess() { processExecutionCoordinator().run(executionView()); }

    private ProcessExecutionCoordinator processExecutionCoordinator() {
        if (processExecutionCoordinator == null) processExecutionCoordinator = new ProcessExecutionCoordinator();
        return processExecutionCoordinator;
    }

    private ProcessExecutionCoordinator.View executionView() {
        return new ProcessExecutionCoordinator.View(this::saveSelectedNodeSettings, this::toExecutableDefinition,
                () -> executionStatusTable, () -> stepCol, () -> stepNameCol, () -> operationCol,
                () -> inputCol, () -> outputCol, () -> statusCol, () -> durationCol, () -> inspectCol,
                () -> runProcessButton, () -> cancelProcessButton, () -> processProgressBar,
                () -> processStatusLabel, () -> executionOutputArea, () -> selected, this::getInspectorControl,
                this::nodeLabel, this::t, this::showPreflightFailure,
                () -> onNodeExecutionEvent, () -> onExecutionFinished);
    }

    private void showPreflightFailure(String message) {
        processPreflightPresenter().show(preflightView(), message);
    }

    private ProcessPreflightPresenter processPreflightPresenter() {
        if (processPreflightPresenter == null) processPreflightPresenter = new ProcessPreflightPresenter();
        return processPreflightPresenter;
    }

    private ProcessPreflightPresenter.View preflightView() {
        return new ProcessPreflightPresenter.View(() -> executionStatusTable, () -> executionOutputArea,
                message -> t("module.process.feedback.failed", message));
    }

    String renderExecutionResult(ProcessDefinition definition, Map<String, com.cryptocarver.model.process.FlowValue> result,
            java.util.Collection<NodeExecutionEvent> events, Exception failure) {
        return processExecutionCoordinator().renderExecutionResult(executionView(), definition, result, events, failure);
    }

    private void configureExecutionStatusTable() { processExecutionCoordinator().configureExecutionStatusTable(executionView()); }

    private String t(String key, Object... args) {
        try {
            return I18nService.getInstance().text(key, args);
        } catch (Exception ignored) {
            return key;
        }
    }

    private String portLabel(String name) {
        String key = "module.process.port." + name;
        java.util.ResourceBundle bundle = I18nService.getInstance().getBundle();
        return bundle != null && bundle.containsKey(key) ? t(key) : name;
    }
}
