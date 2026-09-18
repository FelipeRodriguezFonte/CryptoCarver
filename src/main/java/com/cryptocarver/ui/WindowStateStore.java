package com.cryptocarver.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Persists only non-sensitive window geometry, with defensive bounds validation. */
public final class WindowStateStore {
    private static final double MIN_SIZE = 640;
    private static final double MAX_SIZE = 10000;
    private final Path file;

    public WindowStateStore() {
        this(Paths.get(System.getProperty("user.home", System.getProperty("java.io.tmpdir")),
                ".cryptocarver", "window-state.json"));
    }

    public WindowStateStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public State load() {
        try {
            if (!Files.isRegularFile(file)) return State.empty();
            State state = new Gson().fromJson(Files.readString(file), State.class);
            return state == null ? State.empty() : state.sanitized();
        } catch (Exception ignored) {
            return State.empty();
        }
    }

    public void save(State state) {
        if (state == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(state.sanitized()));
        } catch (IOException ignored) {
            // Window convenience state must never prevent shutdown.
        }
    }

    public record State(double x, double y, double width, double height, boolean maximized,
                        double monitorX, double monitorY, double monitorWidth, double monitorHeight) {
        public static State empty() {
            return new State(Double.NaN, Double.NaN, 1400, 900, false,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        public State sanitized() {
            return new State(finite(x, Double.NaN), finite(y, Double.NaN),
                    bounded(width, 1400), bounded(height, 900), maximized,
                    finite(monitorX, Double.NaN), finite(monitorY, Double.NaN),
                    bounded(monitorWidth, Double.NaN), bounded(monitorHeight, Double.NaN));
        }

        private static double finite(double value, double fallback) {
            return Double.isFinite(value) ? value : fallback;
        }

        private static double bounded(double value, double fallback) {
            return Double.isFinite(value) && value >= MIN_SIZE && value <= MAX_SIZE ? value : fallback;
        }
    }
}
