package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WindowStateStoreTest {
    @TempDir Path temp;

    @Test
    void persistsGeometryAndMaximizedState() {
        WindowStateStore store = new WindowStateStore(temp.resolve("window.json"));
        WindowStateStore.State expected = new WindowStateStore.State(42, 55, 1200, 800, true, 0, 0, 1920, 1080);
        store.save(expected);
        assertEquals(expected, store.load());
    }

    @Test
    void malformedAndUnsafeGeometryFallsBackSafely() throws Exception {
        Path file = temp.resolve("window.json");
        java.nio.file.Files.writeString(file, "{\"width\":-10,\"height\":999999,\"x\":\"bad\"}");
        WindowStateStore.State state = new WindowStateStore(file).load();
        assertEquals(1400, state.width());
        assertEquals(900, state.height());
        assertTrue(Double.isNaN(state.x()));
    }
}
