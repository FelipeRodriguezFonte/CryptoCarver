package com.cryptocarver.ui;

import javafx.scene.Parent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleLoaderTest {

    @Test
    void loadsFromClasspathOnceAndWiresOnlyOnce() throws Exception {
        AtomicInteger wires = new AtomicInteger();
        ModuleLoader loader = new ModuleLoader(getClass())
                .register("utilities", "/module-loader.fxml", ignored -> wires.incrementAndGet());

        Parent first = loader.load("utilities", true);
        Parent second = loader.load("utilities", true);

        assertSame(first, second);
        assertEquals(1, wires.get());
        assertTrue(loader.isCached("utilities"));
        assertEquals("utilities", loader.visibleRoute().orElseThrow());
        assertSame(first, loader.cached("utilities").orElseThrow());
    }

    @Test
    void preloadDoesNotSelectRouteAndInvalidateReloads() throws Exception {
        ModuleLoader loader = new ModuleLoader(getClass()).register("utilities", "/module-loader.fxml");

        Parent first = loader.preload("utilities");
        assertFalse(loader.visibleRoute().isPresent());
        loader.invalidate("utilities");
        Parent second = loader.load("utilities", false);

        assertFalse(first == second);
        assertTrue(loader.isCached("utilities"));
    }

    @Test
    void rejectsUnknownAndNonClasspathRoutes() {
        ModuleLoader loader = new ModuleLoader();
        assertThrows(IllegalArgumentException.class, () -> loader.load("missing", false));
        assertThrows(IllegalArgumentException.class, () -> loader.register("remote", "https://example.test/module.fxml"));
    }
}
