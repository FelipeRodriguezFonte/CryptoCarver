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
 * Writes the computed style of every visible node for a fixed set of screens and
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
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(15, TimeUnit.SECONDS));
        List<String> lines = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                String selectedTheme = System.getProperty("styleSnapshotTheme");
                String selectedRoute = System.getProperty("styleSnapshotRoute");
                String[] themes = selectedTheme == null ? THEMES : new String[] {selectedTheme};
                String[] routes = selectedRoute == null ? ROUTES : new String[] {selectedRoute};
                for (String theme : themes) {
                    for (String route : routes) {
                        snapshot(theme, route, lines);
                    }
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
        FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
        Parent root = loader.load();
        ModernMainController controller = loader.getController();
        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/" + theme).toExternalForm());
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.show();
        controller.navigateToModule(route);
        root.applyCss();
        root.layout();
        walk(root, theme + " " + route + " ", lines);
        stage.close();
    }

    private void walk(Node node, String path, List<String> lines) {
        String type = node.getClass().getSimpleName().isEmpty() ? node.getClass().getName() : node.getClass().getSimpleName();
        String key = path + "/" + type + (node.getId() == null ? "" : "#" + node.getId());
        if (!node.isVisible()) return;
        StringBuilder value = new StringBuilder(" classes=").append(node.getStyleClass());
        if (node instanceof Region region) {
            value.append(" bg=").append(fills(region.getBackground()))
                    .append(" border=").append(borders(region.getBorder()))
                    .append(" pad=").append(region.getPadding())
                    .append(" minH=").append(region.getMinHeight())
                    .append(" prefH=").append(region.getPrefHeight());
        }
        if (node instanceof Labeled labeled) {
            value.append(" fg=").append(labeled.getTextFill())
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

    private static String fills(Background background) {
        if (background == null) return "[]";
        return background.getFills().stream().map(fill -> String.valueOf(fill.getFill())).toList().toString();
    }

    private static String borders(Border border) {
        if (border == null) return "[]";
        return border.getStrokes().stream().map(stroke -> String.valueOf(stroke.getTopStroke())).toList().toString();
    }
}
