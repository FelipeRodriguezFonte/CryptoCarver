package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ThemePreference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ScreenConfigurationDialogThemeTest {
    private static boolean toolkitReady;
    @TempDir Path temp;

    @BeforeAll
    static void startToolkit() throws Exception {
        if (toolkitReady) return;
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        toolkitReady = true;
    }

    @BeforeEach
    void isolateSettings() {
        AppSettings.setInstanceForTesting(new AppSettings(temp.resolve("settings.json")));
    }

    @AfterEach
    void resetSettings() { AppSettings.resetInstanceForTesting(); }

    @Test
    void passwordDialogUsesBothThemesWithReadableLabelContrast() throws Exception {
        AtomicReference<List<String>> findings = new AtomicReference<>(new ArrayList<>());
        fx(() -> {
            try {
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                loader.load();
                ModernMainController controller = loader.getController();
                ScreenConfigurationCoordinator coordinator = controller.screenConfigurationCoordinator();
                for (ThemePreference theme : List.of(ThemePreference.LIGHT, ThemePreference.DARK)) {
                    AppSettings.getInstance().setThemePreference(theme);
                    Dialog<char[]> dialog = coordinator.createPasswordDialog(true);
                    dialog.getDialogPane().applyCss();
                    dialog.getDialogPane().layout();
                    assertTrue(dialog.getDialogPane().getStylesheets().stream().anyMatch(s -> s.endsWith("/css/styles.css")));
                    assertTrue(dialog.getDialogPane().getStylesheets().stream().anyMatch(s -> s.endsWith(
                            theme == ThemePreference.DARK ? "/css/theme-dark.css" : "/css/theme-light.css")));
                    for (Node node : descendants(dialog.getDialogPane())) {
                        if (node instanceof Label label && label.getText() != null && !label.getText().isBlank()) {
                            Color background = effectiveBackground(label);
                            Color foreground = label.getTextFill() instanceof Color value ? value : null;
                            if (foreground != null && contrast(foreground, background) < 3.0) {
                                findings.get().add(theme + ":" + label.getText() + " fg=" + foreground
                                        + " bg=" + background + " pane=" + dialog.getDialogPane().getBackground());
                            }
                        }
                    }
                }
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        assertEquals(List.of(), findings.get());
    }

    private static Color effectiveBackground(Node node) {
        for (Node parent = node; parent != null; parent = parent.getParent()) {
            if (parent instanceof Region region) {
                Background background = region.getBackground();
                if (background != null) {
                    for (var fill : background.getFills()) {
                        Color color = color(fill.getFill());
                        if (color != null && color.getOpacity() >= 1) return color;
                    }
                }
            }
        }
        return Color.WHITE;
    }

    private static Color color(Paint paint) {
        if (paint instanceof Color value) return value;
        List<Stop> stops = paint instanceof javafx.scene.paint.LinearGradient gradient ? gradient.getStops()
                : paint instanceof javafx.scene.paint.RadialGradient gradient ? gradient.getStops() : List.of();
        if (stops.isEmpty()) return null;
        double red = 0, green = 0, blue = 0;
        for (Stop stop : stops) {
            red += stop.getColor().getRed(); green += stop.getColor().getGreen(); blue += stop.getColor().getBlue();
        }
        return new Color(red / stops.size(), green / stops.size(), blue / stops.size(), 1);
    }

    private static double contrast(Color first, Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private static double linear(double channel) {
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private static List<Node> descendants(Node root) {
        List<Node> result = new ArrayList<>();
        result.add(root);
        if (root instanceof javafx.scene.Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> result.addAll(descendants(child)));
        }
        return result;
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
            finally { finished.countDown(); }
        });
        assertTrue(finished.await(60, TimeUnit.SECONDS), "JavaFX thread timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
