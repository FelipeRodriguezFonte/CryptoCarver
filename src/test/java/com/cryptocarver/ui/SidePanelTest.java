package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SidePanelTest {
    private static final AtomicReference<Throwable> startupFailure = new AtomicReference<>();

    @BeforeAll
    static void startToolkit() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
            // Shared JavaFX toolkit already started by another test class.
        } catch (Throwable error) {
            startupFailure.set(error);
        }
        if (startupFailure.get() != null) throw new AssertionError(startupFailure.get());
    }

    @Test
    void searchShowsContextAndEnterSelectsTheFirstMatchingOperation() throws Exception {
        AtomicReference<SidePanel> panelRef = new AtomicReference<>();
        AtomicReference<String> selectedRef = new AtomicReference<>();
        runOnFxThread(() -> {
            SidePanel panel = new SidePanel();
            panel.setOnItemSelected(selectedRef::set);
            panelRef.set(panel);

            TextField search = getField(panel, "searchField");
            search.setText("Kyber");
            assertEquals("Search Results (1)", getTree(panel).getRoot().getValue().toString());
            search.fireEvent(new javafx.event.ActionEvent());
            assertEquals("PQC Key Generation", selectedRef.get());

            search.clear();
            assertFalse(getTree(panel).getRoot().getChildren().isEmpty());
        });
        assertNotNull(panelRef.get());
    }

    @Test
    void genericToolsSectionIncludesClipboardShelfWithoutUsingSearch() throws Exception {
        runOnFxThread(() -> {
            SidePanel panel = new SidePanel();
            panel.updateContent(NavigationRail.Section.GENERIC);
            TreeView<?> tree = getTree(panel);
            assertEquals("Generic", tree.getRoot().getValue().toString());
            assertTrue(containsLabel(tree.getRoot(), "Clipboard Shelf"),
                    "Clipboard Shelf must be directly discoverable in the generic tools section");
        });
    }

    private static boolean containsLabel(TreeItem<?> item, String label) {
        if (item == null) return false;
        if (item.getValue() != null && label.equals(item.getValue().toString())) return true;
        return item.getChildren().stream().anyMatch(child -> containsLabel(child, label));
    }

    @SuppressWarnings("unchecked")
    private static TreeView<?> getTree(SidePanel panel) throws Exception {
        return getField(panel, "navigationTree");
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void runOnFxThread(ThrowingRunnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX operation timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
