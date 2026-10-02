package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes the computed style of visible nodes (and transiently hidden scrollbar skin
 * nodes) for a fixed set of screens and
 * both themes, so two builds can be compared with a plain {@code diff}.
 *
 * <p>Not a regression test: it only runs when {@code -DstyleSnapshotOut=<file>} is
 * given. Node keys use type, fx:id and child index only, never style classes, so a
 * change of classes shows up as a value difference rather than as a missing node.
 * Cells inside a {@code VirtualFlow} (lists, trees, tables) are skipped because they
 * are recycled in a different order on each run.
 *
 * <pre>
 * mvn -o -q test -Dtest=ComputedStyleSnapshotTool -DstyleSnapshotOut=/tmp/before.txt
 * # apply the change
 * mvn -o -q test -Dtest=ComputedStyleSnapshotTool -DstyleSnapshotOut=/tmp/after.txt
 * diff /tmp/before.txt /tmp/after.txt
 * </pre>
 */
@EnabledIfSystemProperty(named = "styleSnapshotOut", matches = ".+")
class ComputedStyleSnapshotTool {
    private static final String[] THEMES = {"theme-light.css", "theme-dark.css"};
    private static final String[] ROUTES = {"Hashing", "Symmetric Ciphers", "MAC", "Key Generation",
            "Recent Operations", "Saved Sessions", "Certificates", "JWT (Signed)", "PIN Generation",
            "Clipboard Shelf", "Manual Conversion", "EMV Tool", "Sign SOAP", "Process Designer",
            "Key & Certificate Format Workbench", "Post-Quantum Key Generation", "COSE Sign1",
            "OpenPGP", "PAdES PDF Signatures", "ASiC-S Containers"};

    @Test
    void writeSnapshot() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(15, TimeUnit.SECONDS));
        List<String> lines = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                String selectedTheme = System.getProperty("styleSnapshotTheme");
                String selectedRoute = System.getProperty("styleSnapshotRoute");
                String[] themes = selectedTheme == null ? THEMES : new String[] {selectedTheme};
                String[] routes = selectedRoute == null ? ROUTES : selectedRoute.split(",");
                for (String theme : themes) {
                    for (String route : routes) {
                        snapshot(theme, route, lines);
                    }
                    snapshotDialog(theme, lines);
                    snapshotTree(theme, lines);
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.MINUTES));
        if (failure.get() != null) throw new AssertionError(failure.get());
        Files.write(Path.of(System.getProperty("styleSnapshotOut")), lines, StandardCharsets.UTF_8);
    }

    private void snapshot(String theme, String route, List<String> lines) throws Exception {
        FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
        Parent root = loader.load();
        ModernMainController controller = loader.getController();
        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/" + theme).toExternalForm());
        Stage stage = new Stage();
        String previousRoute = com.cryptocarver.model.AppSettings.getInstance().getLastRoute();
        double previousTree = com.cryptocarver.model.AppSettings.getInstance().getWorkspaceTreeDividerPosition();
        double previousInspector = com.cryptocarver.model.AppSettings.getInstance().getWorkspaceInspectorDividerPosition();
        try {
        stage.setScene(scene);
        stage.show();
        controller.navigateToModule(route);
        ClippedTextAuditTool.settle();
        root.applyCss();
        root.layout();
        root.applyCss(); root.layout();
        neutralizePointerAndFocus(root);
        root.applyCss(); root.layout();
        walk(root, theme + " " + route + " ", lines);
        if (Boolean.getBoolean("styleSnapshotTabs")) {
            for (Node node : root.lookupAll(".tab-pane")) {
                if (!(node instanceof javafx.scene.control.TabPane tabs)) continue;
                boolean visible = true;
                for (Node parent = tabs; parent != null; parent = parent.getParent()) {
                    visible &= parent.isVisible();
                }
                if (!visible) continue;
                int original = tabs.getSelectionModel().getSelectedIndex();
                for (int index = 0; index < tabs.getTabs().size(); index++) {
                    if (index == original) continue;
                    tabs.getSelectionModel().select(index);
                    ClippedTextAuditTool.settle();
                    neutralizePointerAndFocus(root);
                    root.applyCss();
                    root.layout();
                    walk(root, theme + " " + route + " tab=" + index + " ", lines);
                }
                tabs.getSelectionModel().select(original);
            }
        }
        } finally {
            stage.close(); controller.shutdown(); stage.setScene(null);
            com.cryptocarver.model.AppSettings.getInstance().setLastRoute(previousRoute);
            com.cryptocarver.model.AppSettings.getInstance().setWorkspaceTreeDividerPosition(previousTree);
            com.cryptocarver.model.AppSettings.getInstance().setWorkspaceInspectorDividerPosition(previousInspector);
        }
    }

    private void snapshotDialog(String theme, List<String> lines) {
        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        new DialogService().configure(dialog, null, "Style snapshot", null);
        var pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(getClass().getResource("/css/styles.css").toExternalForm(),
                getClass().getResource("/css/" + theme).toExternalForm());
        pane.getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        var chip = new javafx.scene.control.Label("Metadata");
        chip.getStyleClass().addAll("metadata-chip", "metadata-chip-neutral");
        pane.setContent(new javafx.scene.layout.VBox(new javafx.scene.control.Button("Action"),
                new javafx.scene.control.TextField(), new javafx.scene.control.TextArea(),
                new javafx.scene.control.ComboBox<>(), chip));
        try {
            dialog.show();
            pane.applyCss(); pane.layout();
            neutralizePointerAndFocus(pane);
            pane.applyCss(); pane.layout();
            walk(pane, theme + " Scoped dialog", lines);
        } finally { dialog.close(); }
    }

    private void snapshotTree(String theme, List<String> lines) {
        var item = new javafx.scene.control.TreeItem<String>("Category");
        item.getChildren().add(new javafx.scene.control.TreeItem<>("Synthetic caption"));
        var tree = new javafx.scene.control.TreeView<>(item);
        tree.getStyleClass().add("navigation-tree");
        var root = new javafx.scene.layout.VBox(tree);
        var scene = new Scene(root, 400, 300);
        scene.getStylesheets().addAll(getClass().getResource("/css/styles.css").toExternalForm(),
                getClass().getResource("/css/" + theme).toExternalForm());
        var stage = new Stage();
        try {
            stage.setScene(scene); stage.show();
            tree.getSelectionModel().clearSelection();
            tree.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), false);
            root.applyCss(); root.layout();
            walk(tree, theme + " Tree unselected", lines);
            tree.getSelectionModel().select(item);
            root.applyCss(); root.layout();
            walk(tree, theme + " Tree selected", lines);
            tree.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), true);
            root.applyCss(); root.layout();
            walk(tree, theme + " Tree selected focused", lines);
        } finally { stage.close(); stage.setScene(null); }
    }

    private static void neutralizePointerAndFocus(Parent root) {
        // The desktop pointer/window activation must not choose snapshot states.
        java.util.List<Node> nodes = new ArrayList<>(root.lookupAll("*"));
        nodes.add(root);
        for (Node node : nodes) {
            node.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), false);
            node.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"), false);
        }
    }

    private void walk(Node node, String path, List<String> lines) {
        String type = node.getClass().getSimpleName().isEmpty() ? node.getClass().getName() : node.getClass().getSimpleName();
        String key = path + "/" + type + (node.getId() == null ? "" : "#" + node.getId());
        // ScrollBarSkin toggles arrow visibility during its first layout/pulse.
        // Compare their CSS in both states rather than dropping transiently hidden
        // skin nodes from one snapshot. No caption/audit exclusions are changed.
        if (!node.isVisible() && !insideScrollBar(node)) return;
        StringBuilder value = new StringBuilder(" classes=").append(node.getStyleClass());
        if (node instanceof Region region) {
            value.append(" bg=").append(fills(region.getBackground()))
                    .append(" border=").append(borders(region.getBorder()))
                    .append(" pad=").append(region.getPadding())
                    .append(" minW=").append(region.getMinWidth())
                    .append(" prefW=").append(region.getPrefWidth())
                    .append(" maxW=").append(region.getMaxWidth())
                    .append(" minH=").append(region.getMinHeight())
                    .append(" prefH=").append(region.getPrefHeight());
        }
        if (node instanceof javafx.scene.control.TreeView<?>) {
            // Paints only: no virtualized cell text, identity or private data.
            value.append(" treeArrowPaints=").append(node.lookupAll(".tree-disclosure-node > .arrow").stream()
                    .filter(n -> n instanceof Region).map(n -> fills(((Region) n).getBackground()))
                    .sorted().toList());
        }
        if (node instanceof Labeled labeled) {
            value.append(" wrap=").append(labeled.isWrapText())
                    .append(" overrun=").append(labeled.getTextOverrun())
                    .append(" fg=").append(labeled.getTextFill())
                    .append(" font=").append(labeled.getFont().getSize()).append(' ').append(labeled.getFont().getStyle());
        }
        value.append(" opacity=").append(node.getOpacity());
        lines.add(key + " =>" + value);
        // Virtualised cells are recycled in a different order on every run.
        if (node instanceof javafx.scene.control.skin.VirtualFlow<?>) return;
        if (node instanceof Parent parent) {
            int index = 0;
            for (Node child : parent.getChildrenUnmodifiable()) walk(child, key + "[" + index++ + "]", lines);
        }
    }

    private static boolean insideScrollBar(Node node) {
        for (Node parent = node; parent != null; parent = parent.getParent()) {
            if (parent instanceof javafx.scene.control.ScrollBar) return true;
        }
        return false;
    }

    private static String fills(Background background) {
        if (background == null) return "[]";
        return background.getFills().stream().map(fill -> String.valueOf(fill.getFill()) + " radii=" + fill.getRadii()).toList().toString();
    }

    private static String borders(Border border) {
        if (border == null) return "[]";
        return border.getStrokes().stream().map(stroke -> String.valueOf(stroke.getTopStroke()) + " radii=" + stroke.getRadii()).toList().toString();
    }
}
