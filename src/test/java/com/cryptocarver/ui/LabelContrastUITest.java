package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class LabelContrastUITest {
    private record Finding(String theme, String module, String label, double ratio) { }

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); latch.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { latch.countDown(); }
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @Test void visibleLabelsHaveAccessibleContrast() throws Exception {
        List<Finding> findings = new ArrayList<>();
        fx(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                Stage stage = new Stage();
                Scene scene = new Scene(root, 1400, 900);
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                for (String theme : List.of("theme-light.css", "theme-dark.css")) {
                    scene.getStylesheets().removeIf(s -> s.endsWith("theme-light.css") || s.endsWith("theme-dark.css"));
                    scene.getStylesheets().add(getClass().getResource("/css/" + theme).toExternalForm());
                    measure(root, theme, "Shell", findings);
                    for (String module : List.of("Generic", "Symmetric Keys", "Symmetric Ciphers", "History", "Saved Sessions")) {
                        controller.navigateToModule(module);
                        root.applyCss(); root.layout();
                        measure(root, theme, module, findings);
                    }
                }
                stage.close();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertTrue(findings.isEmpty(), "Visible labels below 3:1: " + findings.stream().distinct().toList());
    }

    private static void measure(Parent root, String theme, String module, List<Finding> findings) {
        root.applyCss(); root.layout();
        for (Node node : descendants(root)) {
            if (!(node instanceof Label label) || label.getText() == null || label.getText().isBlank() || !visible(label)) continue;
            Color foreground = label.getTextFill() instanceof Color c ? c : null;
            Color background = effectiveBackground(label);
            if (foreground == null || background == null) continue;
            double ratio = contrast(foreground, background);
            if (ratio < 3.0) {
                String id = label.getId() == null ? "" : "#" + label.getId();
                StringBuilder ownerPath = new StringBuilder();
                for (Node p=label.getParent(); p!=null && p!=root; p=p.getParent()) {
                    if (!ownerPath.isEmpty()) ownerPath.append(" <- ");
                    ownerPath.append(p.getClass().getSimpleName());
                    if (p.getId()!=null) ownerPath.append('#').append(p.getId());
                    if (!p.getStyleClass().isEmpty()) ownerPath.append(p.getStyleClass());
                }
                String owner = ownerPath.toString();
                findings.add(new Finding(theme, module, id + label.getStyleClass() + " parent=" + owner
                        + " fg=" + foreground + " bg=" + background, ratio));
            }
        }
    }
    private static boolean visible(Node n) {
        for (Node p=n; p!=null; p=p.getParent()) if (!p.isVisible() || (p instanceof javafx.scene.layout.Region r && (r.getWidth() <= 0 || r.getHeight() <= 0))) return false;
        return true;
    }
    private static Color effectiveBackground(Node node) {
        for (Node p=node; p!=null; p=p.getParent()) if (p instanceof javafx.scene.layout.Region r) {
            Background bg=r.getBackground();
            if (bg!=null) for (BackgroundFill fill:bg.getFills()) if (fill.getFill() instanceof Color c && c.getOpacity() >= 1) return c;
        }
        return Color.WHITE;
    }
    private static double contrast(Color a, Color b) {
        double x=luminance(a), y=luminance(b); return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
    private static double luminance(Color c) {
        double r=linear(c.getRed()), g=linear(c.getGreen()), b=linear(c.getBlue()); return .2126*r+.7152*g+.0722*b;
    }
    private static double linear(double c) { return c <= .04045 ? c/12.92 : Math.pow((c+.055)/1.055,2.4); }
    private static List<Node> descendants(Node root) {
        List<Node> all=new ArrayList<>(); all.add(root); if(root instanceof Parent p) p.getChildrenUnmodifiable().forEach(n->all.addAll(descendants(n))); return all;
    }
    private static void fx(Runnable action) throws Exception {
        CountDownLatch latch=new CountDownLatch(1); java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(()->{try{action.run();}catch(Throwable t){failure.set(t);}finally{latch.countDown();}});
        assertTrue(latch.await(90,TimeUnit.SECONDS),"FX thread timed out"); if(failure.get()!=null) throw new AssertionError(failure.get());
    }
}
