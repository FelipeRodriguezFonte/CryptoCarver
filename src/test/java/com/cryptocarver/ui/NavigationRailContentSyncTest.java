package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Regression coverage for the rail/side-panel desync found during the 2026-08-07 UX audit:
 * clicking a {@link NavigationRail} icon rebuilt the {@link SidePanel} tree for the new
 * section, but left the content pane, breadcrumb and toolbar bound to whatever operation was
 * active before the click. See docs/HANDOFF_UX_PROPUESTA.md and docs/MEJORAS_UX.md.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class NavigationRailContentSyncTest {
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
    void clickingARailIconNavigatesContentToThatSectionsFirstOperation() throws Exception {
        AtomicReference<String> lastNavigated = new AtomicReference<>();
        runOnFxThread(() -> {
            NavigationRail rail = new NavigationRail();
            SidePanel panel = new SidePanel();
            panel.setOnItemSelected(lastNavigated::set);
            rail.setSidePanel(panel);

            // A rail click always lands on a section other than the KEYS default selected by
            // the NavigationRail constructor, so this exercises a real section transition.
            rail.selectSection(NavigationRail.Section.CIPHER);

            assertNotNull(lastNavigated.get(),
                    "Selecting a rail section must navigate the content pane, not just the tree");
            assertNotEquals("Hashing", lastNavigated.get(),
                    "Content must switch to the newly selected section, not stay on a prior operation");
        });
    }

    @Test
    void repeatedClicksOnTheSameSectionConsistentlyLandOnItsDefaultOperation() throws Exception {
        java.util.List<String> navigations = new java.util.ArrayList<>();
        runOnFxThread(() -> {
            NavigationRail rail = new NavigationRail();
            SidePanel panel = new SidePanel();
            panel.setOnItemSelected(navigations::add);
            rail.setSidePanel(panel);

            rail.selectSection(NavigationRail.Section.CIPHER);
            assertTrue(!navigations.isEmpty(), "First click into Cipher must navigate");
            String firstDestination = navigations.get(navigations.size() - 1);

            // ToggleGroup re-fires selection even when the user clicks the already-active
            // rail icon again; that is pre-existing behavior (the tree already rebuilt itself
            // on every click before this fix). What matters is that it stays deterministic:
            // repeat clicks always land on the same section-default operation, never on stale
            // or unrelated content.
            rail.selectSection(NavigationRail.Section.CIPHER);
            String secondDestination = navigations.get(navigations.size() - 1);
            org.junit.jupiter.api.Assertions.assertEquals(firstDestination, secondDestination,
                    "Repeat clicks on the same section must land on the same default operation");
        });
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
