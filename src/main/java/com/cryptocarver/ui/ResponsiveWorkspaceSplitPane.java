package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;

/**
 * Three-column workspace layout with persisted dividers and laptop-friendly
 * automatic collapsing.  It deliberately owns only layout state so the main
 * controller can continue to address its existing FXML fields directly.
 */
public final class ResponsiveWorkspaceSplitPane extends SplitPane {
    private static final double INSPECTOR_COLLAPSE_WIDTH = 1366.0;
    private static final double TREE_COLLAPSE_WIDTH = 1200.0;
    private static final double TREE_MIN_WIDTH = 200.0;
    private static final double CONTENT_MIN_WIDTH = 560.0;
    private static final double INSPECTOR_MIN_WIDTH = 260.0;

    private boolean inspectorCollapsed;
    private boolean treeCollapsed;
    private boolean restoring;

    public ResponsiveWorkspaceSplitPane() {
        getStyleClass().add("workspace-split-pane");
        widthProperty().addListener((observable, oldWidth, newWidth) -> updateResponsiveState(newWidth.doubleValue()));
        Platform.runLater(this::installWhenReady);
    }

    private void installWhenReady() {
        if (getItems().size() < 3) return;
        Node tree = getItems().get(0);
        Node content = getItems().get(1);
        Node inspector = getItems().get(2);
        minWidth(tree, TREE_MIN_WIDTH);
        minWidth(content, CONTENT_MIN_WIDTH);
        minWidth(inspector, INSPECTOR_MIN_WIDTH);

        ChangeListener<Number> first = (observable, oldValue, newValue) -> {
            if (!restoring && !treeCollapsed && tree.isVisible() && !hasHiddenChild(tree))
                AppSettings.getInstance().setWorkspaceTreeDividerPosition(newValue.doubleValue());
        };
        ChangeListener<Number> second = (observable, oldValue, newValue) -> {
            if (!restoring && !inspectorCollapsed && inspector.isVisible())
                AppSettings.getInstance().setWorkspaceInspectorDividerPosition(newValue.doubleValue());
        };
        // A hidden side panel must give its width to the content, whoever hides it.
        tree.visibleProperty().addListener((observable, before, visible) -> releaseOrRestore(tree, visible, TREE_MIN_WIDTH));
        // The tree pane holds the rail and the section tree; hiding the tree alone leaves only the rail.
        if (tree instanceof javafx.scene.Parent parent) for (Node child : parent.getChildrenUnmodifiable())
            child.visibleProperty().addListener((observable, before, visible) -> releaseOrRestore(tree, tree.isVisible(), TREE_MIN_WIDTH));
        inspector.visibleProperty().addListener((observable, before, visible) -> releaseOrRestore(inspector, visible, INSPECTOR_MIN_WIDTH));
        releaseOrRestore(tree, tree.isVisible(), TREE_MIN_WIDTH);
        releaseOrRestore(inspector, inspector.isVisible(), INSPECTOR_MIN_WIDTH);
        if (getDividers().size() >= 2) {
            getDividers().get(0).positionProperty().removeListener(first);
            getDividers().get(1).positionProperty().removeListener(second);
            getDividers().get(0).positionProperty().addListener(first);
            getDividers().get(1).positionProperty().addListener(second);
        }
        Platform.runLater(this::restoreDividerPositions);
    }

    /** A hidden panel keeps no width, so its divider rests on the edge instead of leaving an empty band. */
    private void releaseOrRestore(Node node, boolean visible, double visibleMinWidth) {
        if (node instanceof Region region) {
            // Only the tree pane has a part (the rail) that stays when the rest is hidden.
            boolean reduced = visible && node == getItems().get(0) && hasHiddenChild(node);
            region.setMinWidth(!visible ? 0 : reduced ? Region.USE_PREF_SIZE : visibleMinWidth);
            region.setMaxWidth(!visible ? 0 : reduced ? Region.USE_PREF_SIZE : Region.USE_COMPUTED_SIZE);
        }
        Platform.runLater(this::restoreDividerPositions);
    }

    private void restoreDividerPositions() {
        if (getDividers().size() < 2) return;
        restoring = true;
        try {
            setDividerPositions(
                    getItems().get(0).isVisible() && !hasHiddenChild(getItems().get(0))
                            ? AppSettings.getInstance().getWorkspaceTreeDividerPosition() : 0.0,
                    getItems().get(2).isVisible() ? AppSettings.getInstance().getWorkspaceInspectorDividerPosition() : 1.0);
        } finally {
            restoring = false;
        }
        updateResponsiveState(getWidth());
    }

    private void updateResponsiveState(double width) {
        if (getItems().size() < 3 || width <= 0) return;
        setCollapsed(getItems().get(2), width < INSPECTOR_COLLAPSE_WIDTH, true);
        setCollapsed(getItems().get(0), width < TREE_COLLAPSE_WIDTH, false);
    }

    private void setCollapsed(Node node, boolean collapse, boolean inspector) {
        boolean alreadyCollapsed = inspector ? inspectorCollapsed : treeCollapsed;
        if (alreadyCollapsed == collapse) return;
        if (inspector) inspectorCollapsed = collapse;
        else treeCollapsed = collapse;
        node.setVisible(!collapse);
        node.setManaged(!collapse);
    }

    private static boolean hasHiddenChild(Node node) {
        return node instanceof javafx.scene.Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(child -> !child.isVisible());
    }

    private static void minWidth(Node node, double value) {
        if (node instanceof Region region) region.setMinWidth(value);
    }
}
