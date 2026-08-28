package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Navigating to a pane Keys includes rather than owns must put it in front of the user.
 *
 * <p>PKCS#11 Profiles and the two ICSF / CCA panes are siblings that follow the symmetric
 * accordion. Expanding one of them moved nothing else, so with a tall accordion pane open
 * above them they sat below the fold and the scroll pane never followed: the pane reported
 * itself expanded while the screen did not change. These tests pin both halves of the fix —
 * the accordion yields, and the viewport travels to the pane.</p>
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class IcsfNavigationUITest {

    /** How much of the pane must be on screen for a user to see that navigation did something. */
    private static final double MIN_VISIBLE_HEIGHT = 100.0;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        Platform.setImplicitExit(false);
        assertTrue(latch.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    private static void onFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "FX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** The reveal is deferred with Platform.runLater, so the queue has to turn over twice. */
    private static void settle() throws Exception {
        onFxThread(() -> { });
        onFxThread(() -> { });
    }

    private static Node findById(Parent root, String id) {
        java.util.Deque<Node> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (id.equals(node.getId())) return node;
            if (node instanceof Parent parent) queue.addAll(parent.getChildrenUnmodifiable());
        }
        return null;
    }

    private static TitledPane pane(Parent root, String id) {
        Node node = findById(root, id);
        assertTrue(node instanceof TitledPane, "expected a TitledPane with id " + id);
        return (TitledPane) node;
    }

    private static Accordion symmetricAccordion(Parent root) {
        Node container = findById(root, "symmetricKeysContainer");
        assertTrue(container instanceof Parent, "symmetricKeysContainer not found");
        for (Node child : ((Parent) container).getChildrenUnmodifiable()) {
            if (child instanceof Accordion accordion) return accordion;
        }
        return null;
    }

    private static ScrollPane mainScrollPane(ModernMainController controller) throws Exception {
        java.lang.reflect.Field field = ModernMainController.class.getDeclaredField("mainScrollPane");
        field.setAccessible(true);
        return (ScrollPane) field.get(controller);
    }

    /** Height of the pane that actually falls inside the scroll pane's viewport. */
    private static double visibleHeight(ScrollPane scrollPane, TitledPane pane) {
        Bounds viewport = scrollPane.localToScene(scrollPane.getBoundsInLocal());
        Bounds paneBounds = pane.localToScene(pane.getBoundsInLocal());
        if (viewport == null || paneBounds == null) return 0;
        double top = Math.max(viewport.getMinY(), paneBounds.getMinY());
        double bottom = Math.min(viewport.getMaxY(), paneBounds.getMaxY());
        return Math.max(0, bottom - top);
    }

    /** A shown main view with Key Generation open, which is how a user reaches Keys. */
    private static Fixture openKeysWithATallPaneExpanded() throws Exception {
        Fixture fixture = new Fixture();
        onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(IcsfNavigationUITest.class.getResource("/fxml/main-view-modern.fxml"));
            Parent root = loader.load();
            ModernMainController controller = loader.getController();
            Scene scene = new Scene(root, 1400, 900);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            controller.navigateToModule("Key Generation");
            root.applyCss();
            root.layout();
            fixture.root = root;
            fixture.controller = controller;
            fixture.stage = stage;
        });
        settle();
        onFxThread(() -> assertNotNull(symmetricAccordion(fixture.root).getExpandedPane(),
                "precondition: Key Generation must be open above the included panes"));
        return fixture;
    }

    private static class Fixture {
        Parent root;
        ModernMainController controller;
        Stage stage;

        void navigate(String operation) throws Exception {
            onFxThread(() -> {
                controller.navigateToModule(operation);
                root.applyCss();
                root.layout();
            });
            settle();
            onFxThread(() -> {
                root.applyCss();
                root.layout();
            });
        }

        void close() throws Exception {
            onFxThread(() -> stage.close());
        }
    }

    @Test
    void navigatingToTheIcsfTokenPaneBringsItIntoView() throws Exception {
        Fixture fixture = openKeysWithATallPaneExpanded();
        try {
            fixture.navigate("ICSF / CCA Key Token Analyzer");
            onFxThread(() -> {
                TitledPane icsf = pane(fixture.root, "icsfTokenPane");
                assertTrue(icsf.isExpanded(), "navigation did not expand the ICSF token pane");
                assertNull(symmetricAccordion(fixture.root).getExpandedPane(),
                        "the accordion stayed open and kept pushing the ICSF pane down");
                double visible = visibleHeight(mainScrollPane(fixture.controller), icsf);
                assertTrue(visible >= MIN_VISIBLE_HEIGHT,
                        "the ICSF token pane was expanded but only " + visible
                                + "px of it reached the viewport, so navigating there shows nothing");
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void navigatingToTheIcsfBatchPaneOpensTheBatchPaneAndNotTheTokenOne() throws Exception {
        Fixture fixture = openKeysWithATallPaneExpanded();
        try {
            fixture.navigate("ICSF / CCA Batch Analysis");
            onFxThread(() -> {
                TitledPane batch = pane(fixture.root, "icsfBatchPane");
                TitledPane token = pane(fixture.root, "icsfTokenPane");
                assertTrue(batch.isExpanded(), "navigation did not expand the ICSF batch pane");
                assertFalse(token.isExpanded(), "the token pane opened alongside the batch one");
                double visible = visibleHeight(mainScrollPane(fixture.controller), batch);
                assertTrue(visible >= MIN_VISIBLE_HEIGHT,
                        "only " + visible + "px of the ICSF batch pane reached the viewport");
            });
        } finally {
            fixture.close();
        }
    }

    @Test
    void navigatingBackToAnAccordionPaneClosesTheIncludedPane() throws Exception {
        Fixture fixture = openKeysWithATallPaneExpanded();
        try {
            fixture.navigate("ICSF / CCA Key Token Analyzer");
            fixture.navigate("Key Generation");
            onFxThread(() -> {
                assertFalse(pane(fixture.root, "icsfTokenPane").isExpanded(),
                        "the ICSF pane stayed open after navigating back to the accordion");
                assertNotNull(symmetricAccordion(fixture.root).getExpandedPane(),
                        "Key Generation did not reopen");
            });
        } finally {
            fixture.close();
        }
    }
}
