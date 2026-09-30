package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in, one locale/theme per JVM: -DclippedTextAuditOut=target/clipped-text-audit
 * -DclippedTextAuditLocale=es -DclippedTextAuditTheme=light.
 * Compares the skin's LabeledText with the UI caption; never reads input fields.
 */
@EnabledIfSystemProperty(named = "clippedTextAuditOut", matches = ".+")
class ClippedTextAuditTool {
    record Finding(String screen, String path, String type, String classes, String full,
                   String visible, double width, double preferred, String parent) {
        String line() {
            return screen + " | " + path + " | " + type + " | " + classes + " | full=" + safe(full)
                    + " | visible=" + safe(visible) + " | width=" + String.format(Locale.ROOT, "%.1f", width)
                    + " | pref=" + String.format(Locale.ROOT, "%.1f", preferred) + " | parent=" + parent;
        }
    }
    record Audit(List<Finding> findings, List<String> exclusions) { }

    static void onFx(Callable<Void> action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException initialized) { started.countDown(); }
        if (!started.await(15, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timeout");
        FutureTask<Void> task = new FutureTask<>(action);
        Platform.runLater(task);
        task.get(90, TimeUnit.SECONDS);
    }

    static Audit inspect(String screen, Parent root) {
        root.applyCss(); root.layout(); root.applyCss(); root.layout();
        List<Finding> findings = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        walk(screen, root, "", findings, excluded);
        findings.sort(Comparator.comparing(Finding::screen).thenComparing(Finding::path));
        Collections.sort(excluded);
        return new Audit(findings, excluded);
    }

    private static void walk(String screen, Node node, String path, List<Finding> out, List<String> excluded) {
        String key = path + "/" + node.getClass().getSimpleName() + (node.getId() == null ? "" : "#" + node.getId());
        if (!node.isVisible()) { excluded.add(screen + " | " + key + " | hidden subtree"); return; }
        if (node instanceof Labeled label && label.getText() != null && !label.getText().isBlank()) {
            // Cells are data, including combo selections and list/tree/table content.
            // Header Labels and Tab header Labels remain eligible: their skins expose LabeledText.
            if (!label.isManaged()) excluded.add(screen + " | " + key + " | unmanaged caption");
            else if (label instanceof Cell<?>) excluded.add(screen + " | " + key + " | data cell (text omitted)");
            else {
                Node skinText = label.lookup(".text");
                if (skinText instanceof Text text && text.getClass().getSimpleName().equals("LabeledText")) {
                    String full = label.getText();
                    if (label.isMnemonicParsing()) full = full.replace("__", "\u0000").replace("_", "").replace("\u0000", "_");
                    if (!full.equals(text.getText())) out.add(new Finding(screen, key, label.getClass().getSimpleName(),
                            label.getStyleClass().toString(), full, text.getText(), label.getWidth(), label.prefWidth(-1),
                            node.getParent() == null ? "none" : node.getParent().getClass().getSimpleName()));
                } else excluded.add(screen + " | " + key + " | no reliable LabeledText in skin");
            }
        }
        // Do not descend into cells: their nested Labels can contain secrets or user data.
        if (node instanceof Cell<?>) return;
        if (node instanceof Parent parent) {
            int i = 0;
            for (Node child : parent.getChildrenUnmodifiable()) walk(screen, child, key + "[" + i++ + "]", out, excluded);
        }
    }

    static void withScreen(String route, String theme, java.util.function.Consumer<Parent> action) throws Exception {
        var loader = Fxml.loader("/fxml/main-view-modern.fxml");
        Parent root = loader.load();
        ModernMainController controller = loader.getController();
        Stage stage = new Stage();
        try {
            Scene scene = new Scene(root, 1400, 900);
            scene.getStylesheets().add(ClippedTextAuditTool.class.getResource("/css/styles.css").toExternalForm());
            scene.getStylesheets().add(ClippedTextAuditTool.class.getResource("/css/theme-" + theme + ".css").toExternalForm());
            stage.setScene(scene); stage.show();
            controller.navigateToModule(route);
            root.applyCss(); root.layout();
            action.accept(root);
        } finally {
            stage.close(); controller.shutdown(); stage.setScene(null);
        }
    }

    static String safe(String text) {
        // Keep the published audit independent of product/vendor names in legacy routes.
        return text.replaceAll("(?i)thales|payshield", "[legacy]").replace("\n", "\\n").replace("\r", "\\r");
    }

    @Test void writeAudit() throws Exception {
        String locale = System.getProperty("clippedTextAuditLocale", "en");
        String theme = System.getProperty("clippedTextAuditTheme", "light");
        if (!Set.of("en", "es").contains(locale) || !Set.of("light", "dark").contains(theme))
            throw new IllegalArgumentException("Expected en/es and light/dark");
        Map<UiNavigationRegistry.Route, String> routes = new LinkedHashMap<>();
        UiNavigationRegistry.routes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> routes.putIfAbsent(e.getValue(), e.getKey()));
        List<String> lines = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        List<String> exclusions = new ArrayList<>();
        onFx(() -> {
            var settings = AppSettings.getInstance();
            var language = settings.getLanguagePreference();
            String last = settings.getLastRoute();
            try {
                I18nService.getInstance().setPreference(LanguagePreference.valueOf(locale.toUpperCase(Locale.ROOT)));
                for (String route : new TreeSet<>(routes.values())) {
                    withScreen(route, theme, root -> {
                        Audit audit = inspect(safe(route), root);
                        findings.addAll(audit.findings()); exclusions.addAll(audit.exclusions());
                        lines.add(safe(route) + " = " + audit.findings().size());
                    });
                }
            } finally {
                settings.setLastRoute(last);
                I18nService.getInstance().setPreference(language);
            }
            return null;
        });
        lines.add("TOTAL = " + findings.size());
        lines.add("\nFINDINGS (skin truncation; off-viewport alone is never a finding):");
        findings.stream().sorted(Comparator.comparing(Finding::screen).thenComparing(Finding::path))
                .map(Finding::line).forEach(lines::add);
        lines.add("\nEXCLUSIONS (no text values):");
        exclusions.stream().sorted().map(ClippedTextAuditTool::safe).forEach(lines::add);
        Path directory = Path.of(System.getProperty("clippedTextAuditOut"));
        Files.createDirectories(directory);
        Files.write(directory.resolve(locale + "-" + theme + ".txt"), lines);
    }
}
