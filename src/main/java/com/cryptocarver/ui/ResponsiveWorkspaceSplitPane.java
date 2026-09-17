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
            if (!restoring && !treeCollapsed) AppSettings.getInstance().setWorkspaceTreeDividerPosition(newValue.doubleValue());
        };
        ChangeListener<Number> second = (observable, oldValue, newValue) -> {
            if (!restoring && !inspectorCollapsed) AppSettings.getInstance().setWorkspaceInspectorDividerPosition(newValue.doubleValue());
        };
        if (getDividers().size() >= 2) {
            getDividers().get(0).positionProperty().removeListener(first);
            getDividers().get(1).positionProperty().removeListener(second);
            getDividers().get(0).positionProperty().addListener(first);
            getDividers().get(1).positionProperty().addListener(second);
        }
        Platform.runLater(this::restoreDividerPositions);
    }

    private void restoreDividerPositions() {
        if (getDividers().size() < 2) return;
        restoring = true;
        try {
            setDividerPositions(AppSettings.getInstance().getWorkspaceTreeDividerPosition(),
                    AppSettings.getInstance().getWorkspaceInspectorDividerPosition());
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
        minWidth(node, collapse ? 0 : (inspector ? INSPECTOR_MIN_WIDTH : TREE_MIN_WIDTH));
        if (!collapse) Platform.runLater(this::restoreDividerPositions);
    }

    private static void minWidth(Node node, double value) {
        if (node instanceof Region region) region.setMinWidth(value);
    }
}
