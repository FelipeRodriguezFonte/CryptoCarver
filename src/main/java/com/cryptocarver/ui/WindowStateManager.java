package com.cryptocarver.ui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Applies persisted geometry while keeping the complete decorated window reachable. */
public final class WindowStateManager {
    private WindowStateManager() { }

    public static void restore(Stage stage, WindowStateStore.State state, Rectangle2D fallbackArea) {
        if (stage == null || state == null) return;
        Rectangle2D area = matchingArea(state, fallbackArea);
        if (Double.isFinite(state.width()) && Double.isFinite(state.height())) {
            stage.setWidth(Math.min(state.width(), area.getWidth()));
            stage.setHeight(Math.min(state.height(), area.getHeight()));
        }
        if (Double.isFinite(state.x())) stage.setX(state.x());
        if (Double.isFinite(state.y())) stage.setY(state.y());
        if (state.maximized()) stage.setMaximized(true);
        confine(stage, area);
    }

    public static void confine(Stage stage, Rectangle2D area) {
        if (stage.isMaximized()) return;
        stage.setX(clamp(stage.getX(), area.getMinX(), area.getMaxX() - stage.getWidth()));
        stage.setY(clamp(stage.getY(), area.getMinY(), area.getMaxY() - stage.getHeight()));
    }

    public static WindowStateStore.State capture(Stage stage, Screen screen) {
        Rectangle2D bounds = screen == null ? null : screen.getVisualBounds();
        return new WindowStateStore.State(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(),
                stage.isMaximized(), bounds == null ? Double.NaN : bounds.getMinX(),
                bounds == null ? Double.NaN : bounds.getMinY(), bounds == null ? Double.NaN : bounds.getWidth(),
                bounds == null ? Double.NaN : bounds.getHeight());
    }

    private static Rectangle2D matchingArea(WindowStateStore.State state, Rectangle2D fallback) {
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D candidate = screen.getVisualBounds();
            if (close(candidate.getMinX(), state.monitorX()) && close(candidate.getMinY(), state.monitorY())
                    && close(candidate.getWidth(), state.monitorWidth()) && close(candidate.getHeight(), state.monitorHeight())) {
                return candidate;
            }
        }
        return fallback;
    }

    private static boolean close(double left, double right) {
        return Double.isFinite(right) && Math.abs(left - right) < 2;
    }

    private static double clamp(double value, double min, double max) {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }
}
