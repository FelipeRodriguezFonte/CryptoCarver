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
        if (collapsedContent(node)) {
            excluded.add(screen + " | " + key + " | collapsed TitledPane content"); return;
        }
        if (!node.isVisible()) { excluded.add(screen + " | " + key + " | hidden subtree"); return; }
        if (node instanceof Labeled label && label.getText() != null && !label.getText().isBlank()) {
            // Cells are data, including combo selections and list/tree/table content.
            // Header Labels and Tab header Labels remain eligible: their skins expose LabeledText.
            if (!label.isManaged()) excluded.add(screen + " | " + key + " | unmanaged caption");
            else if (label instanceof Cell<?>) excluded.add(screen + " | " + key + " | data cell (text omitted)");
            else {
                Node skinText = label.lookupAll(".text").stream()
                        .filter(n -> n instanceof Text && ownsSkinText(label, n)).findFirst().orElse(null);
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

    private static boolean ownsSkinText(Labeled owner, Node text) {
        for (Node n = text.getParent(); n != null; n = n.getParent()) {
            if (n instanceof Labeled) return n == owner;
        }
        return false;
    }

    private static boolean collapsedContent(Node node) {
        for (Node n = node.getParent(); n != null; n = n.getParent()) {
            if (n instanceof TitledPane pane && !pane.isExpanded() && pane.getContent() == node) return true;
        }
        return false;
    }

    /** Drain deferred shell initialization before inspecting the settled UI. */
    static void settle() {
        for (int pass = 0; pass < 2; pass++) {
            Object loop = new Object();
            Platform.runLater(() -> Platform.exitNestedEventLoop(loop, null));
            Platform.enterNestedEventLoop(loop);
        }
    }

    static void withScreen(String route, String theme, java.util.function.Consumer<Parent> action) throws Exception {
        var settings = AppSettings.getInstance();
        double tree = settings.getWorkspaceTreeDividerPosition();
        double inspector = settings.getWorkspaceInspectorDividerPosition();
        String last = settings.getLastRoute(), startup = settings.getStartupRoute();
        double scale = settings.getTextScale();
        boolean compact = settings.isCompactDensity();
        var visibility = settings.getSecretVisibilityProfile();
        Stage stage = new Stage();
        ModernMainController controller = null;
        try {
            settings.setWorkspaceTreeDividerPosition(0.22);
            settings.setWorkspaceInspectorDividerPosition(0.78);
            settings.setLastRoute(""); settings.setStartupRoute("");
            settings.setTextScale(1.0); settings.setCompactDensity(false);
            settings.setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            Parent root = loader.load();
            controller = loader.getController();
            Scene scene = new Scene(root, 1400, 900);
            scene.getStylesheets().add(ClippedTextAuditTool.class.getResource("/css/styles.css").toExternalForm());
            scene.getStylesheets().add(ClippedTextAuditTool.class.getResource("/css/theme-" + theme + ".css").toExternalForm());
            stage.setScene(scene); stage.show();
            controller.navigateToModule(route);
            settle();
            root.applyCss(); root.layout();
            action.accept(root);
        } finally {
            stage.close();
            if (controller != null) controller.shutdown();
            stage.setScene(null);
            settings.setWorkspaceTreeDividerPosition(tree);
            settings.setWorkspaceInspectorDividerPosition(inspector);
            settings.setLastRoute(last); settings.setStartupRoute(startup);
            settings.setTextScale(scale); settings.setCompactDensity(compact);
            settings.setSecretVisibilityProfile(visibility);
        }
    }

    static void auditStates(String route, Parent root, List<String> counts,
                            List<Finding> findings, List<String> exclusions) {
        collect(route, root, counts, findings, exclusions);
        // Reveal each tab independently, restoring its original selection. This covers
        // alternate FXML panels without multiplying every possible tab combination.
        List<TabPane> tabs = root.lookupAll(".tab-pane").stream().filter(n -> n instanceof TabPane)
                .map(n -> (TabPane) n).filter(ClippedTextAuditTool::visibleThroughParents)
                .sorted(Comparator.comparing(n -> Objects.toString(n.getId(), ""))).toList();
        int paneIndex = 0;
        for (TabPane pane : tabs) {
            int original = pane.getSelectionModel().getSelectedIndex();
            try {
                for (int index = 0; index < pane.getTabs().size(); index++) {
                    if (index == original || pane.getTabs().get(index).isDisable()) continue;
                    pane.getSelectionModel().select(index);
                    collect(route + " / tabs " + paneIndex + ":" + index, root, counts, findings, exclusions);
                }
            } finally { pane.getSelectionModel().select(original); }
            paneIndex++;
        }
        if (route.equals("Process Designer")) {
            Button toggle = (Button) root.lookup("#inspectorToggleButton");
            toggle.fire();
            try { collect(route + " / inspector hidden", root, counts, findings, exclusions); }
            finally { toggle.fire(); }
        }
        if (route.equals("Key & Certificate Format Workbench")) {
            Node table = root.lookup("#keystoreTable");
            Node grid = root.lookup("#singleItemGrid");
            boolean tv = table.isVisible(), tm = table.isManaged(), gv = grid.isVisible(), gm = grid.isManaged();
            try {
                grid.setVisible(false); grid.setManaged(false);
                table.setVisible(true); table.setManaged(true);
                collect(route + " / store headers", root, counts, findings, exclusions);
            } finally {
                table.setVisible(tv); table.setManaged(tm); grid.setVisible(gv); grid.setManaged(gm);
            }
        }
    }

    private static boolean visibleThroughParents(Node node) {
        for (Node n = node; n != null; n = n.getParent()) if (!n.isVisible()) return false;
        return true;
    }

    static Audit infoDialog(String theme) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.getButtonTypes().setAll(new ButtonType(I18nService.getInstance().text("dialog.ok"), ButtonBar.ButtonData.OK_DONE));
        new DialogService().configure(alert, null, "Information", null);
        DialogService.prepareInformationalDialog(alert);
        alert.getDialogPane().getStylesheets().setAll(
                ClippedTextAuditTool.class.getResource("/css/styles.css").toExternalForm(),
                ClippedTextAuditTool.class.getResource("/css/theme-" + theme + ".css").toExternalForm());
        alert.setContentText("Information");
        try {
            alert.show();
            return inspect("Information dialog", alert.getDialogPane());
        } finally { alert.close(); }
    }

    private static void collect(String route, Parent root, List<String> counts,
                                List<Finding> findings, List<String> exclusions) {
        Audit audit = inspect(safe(route), root);
        findings.addAll(audit.findings()); exclusions.addAll(audit.exclusions());
        counts.add(safe(route) + " = " + audit.findings().size());
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
        var settings = AppSettings.getInstance();
        var language = settings.getLanguagePreference();
        String last = settings.getLastRoute();
        try {
            onFx(() -> {
                I18nService.getInstance().setPreference(LanguagePreference.valueOf(locale.toUpperCase(Locale.ROOT)));
                return null;
            });
            String selection = System.getProperty("clippedTextAuditRoutes", "");
            Set<String> selected = selection.isBlank() ? Set.of() : Set.of(selection.split("\\|"));
            if (!routes.values().containsAll(selected)) throw new IllegalArgumentException("Unknown audit route");
            for (String route : new TreeSet<>(routes.values())) {
                if (!selected.isEmpty() && !selected.contains(route)) continue;
                onFx(() -> {
                    withScreen(route, theme, root -> auditStates(route, root, lines, findings, exclusions));
                    return null;
                });
                // Let window/pulse cleanup execute between screens; do not retain a whole
                // registry's scenes on one FX event. Collection bounds native skin pressure.
                onFx(() -> null);
                System.gc();
            }
            onFx(() -> {
                Audit audit = infoDialog(theme);
                findings.addAll(audit.findings()); exclusions.addAll(audit.exclusions());
                lines.add("Information dialog = " + audit.findings().size());
                return null;
            });
        } finally {
            onFx(() -> {
                settings.setLastRoute(last);
                I18nService.getInstance().setPreference(language);
                return null;
            });
        }
        Collections.sort(lines);
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
