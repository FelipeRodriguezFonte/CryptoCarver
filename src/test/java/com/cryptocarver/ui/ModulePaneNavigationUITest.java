package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Labeled;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-shell navigation transcript for all routes into accordion modules. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ModulePaneNavigationUITest {
    private static final String EXPECTED_EN_SHA256 = "4cd76346b8916a3acd35bdc222301d26d25cc27b53cdf5c270898fd814d4a4ee";
    private static final String EXPECTED_ES_SHA256 = "e58cb04e849926b3173a9d3bba09f352f20fc0ec13d9c4a34432f7e59638a995";
    private static final List<UiNavigationRegistry.Module> MODULES = List.of(
            UiNavigationRegistry.Module.KEYS_SYMMETRIC,
            UiNavigationRegistry.Module.KEYS_ASYMMETRIC,
            UiNavigationRegistry.Module.CERTIFICATES,
            UiNavigationRegistry.Module.GENERIC,
            UiNavigationRegistry.Module.POST_QUANTUM,
            UiNavigationRegistry.Module.XML_SECURITY,
            UiNavigationRegistry.Module.WSS_SECURITY,
            UiNavigationRegistry.Module.EMV,
            UiNavigationRegistry.Module.CIPHER,
            UiNavigationRegistry.Module.AUTHENTICATION,
            UiNavigationRegistry.Module.PAYMENTS);

    private ModernMainController shell;
    private Parent root;
    private Stage stage;
    private SecretVisibilityProfile previousVisibility;
    private LanguagePreference previousLanguage;
    private String previousRoute;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        fx(() -> Platform.setImplicitExit(false));
    }

    @BeforeEach
    void loadProductionShell() throws Exception {
        AppSettings settings = AppSettings.getInstance();
        previousVisibility = settings.getSecretVisibilityProfile();
        previousLanguage = settings.getLanguagePreference();
        previousRoute = settings.getLastRoute();
        settings.setLastRoute("");
        settings.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
        fx(() -> {
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            root = loader.load();
            shell = loader.getController();
            stage = new Stage();
            stage.setScene(new Scene(root, 1400, 900));
        });
    }

    @AfterEach
    void releaseShellAndRestoreSettings() throws Exception {
        fx(() -> {
            if (shell != null) shell.shutdown();
            if (stage != null) { stage.close(); stage.setScene(null); }
            AppSettings.getInstance().setSecretVisibilityProfile(previousVisibility);
            I18nService.getInstance().setPreference(previousLanguage);
            AppSettings.getInstance().setLastRoute(previousRoute);
        });
    }

    @Test
    void sortedRouteTranscriptAcrossEnglishAndSpanish() throws Exception {
        List<Map.Entry<String, UiNavigationRegistry.Route>> routes = UiNavigationRegistry.routes().entrySet().stream()
                .filter(e -> MODULES.contains(e.getValue().module()))
                .sorted(Map.Entry.comparingByKey()).toList();
        assertTrue(!routes.isEmpty());
        Map<UiNavigationRegistry.Module, String> warmRoutes = new java.util.EnumMap<>(UiNavigationRegistry.Module.class);
        routes.forEach(entry -> warmRoutes.putIfAbsent(entry.getValue().module(), entry.getKey()));
        fx(() -> {
            I18nService.getInstance().setPreference(LanguagePreference.EN);
            for (String route : warmRoutes.values()) shell.navigateToModule(route);
            root.applyCss();
            root.layout();
        });
        List<String> transcript = new ArrayList<>();
        for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
            for (var entry : routes) {
                AtomicReference<String> row = new AtomicReference<>();
                fx(() -> {
                    I18nService.getInstance().setPreference(language);
                    root.applyCss();
                    root.layout();
                    UiNavigationRegistry.Module module = entry.getValue().module();
                    String hostId = hostId(module);
                    Node host = root.lookup("#" + hostId);
                    assertNotNull(host, "missing host for " + module + " route " + entry.getKey());
                    setCollapsed(host);
                    shell.navigateToModule(entry.getKey());
                    root.applyCss();
                    root.layout();
                    String selected = selectedPane(host);
                    String crumb = breadcrumb();
                    assertTrue(!crumb.isBlank() && !crumb.equals("<missing>"),
                            "missing breadcrumb for " + entry.getKey());
                    row.set(language.name() + "\t" + safe(entry.getKey()) + "\t" + module.name()
                            + "\t" + safe(entry.getValue().section()) + "\t" + safe(selected) + "\t" + crumb);
                });
                transcript.add(row.get());
            }
        }
        String content = String.join("\n", transcript) + "\n";
        String expected;
        try (var stream = getClass().getResourceAsStream("/com/cryptocarver/ui/module-pane-navigation-baseline.tsv")) {
            assertNotNull(stream, "missing reviewed navigation baseline");
            expected = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(expected, content, "navigation transcript changed; review pane selection and breadcrumbs");
        String en = String.join("\n", transcript.stream().filter(row -> row.startsWith("EN\t")).toList()) + "\n";
        String es = String.join("\n", transcript.stream().filter(row -> row.startsWith("ES\t")).toList()) + "\n";
        assertEquals(EXPECTED_EN_SHA256, sha256(en));
        assertEquals(EXPECTED_ES_SHA256, sha256(es));
        System.out.println("Reviewed module route transcript: " + transcript.size()
                + " rows; EN sha256=" + sha256(en) + "; ES sha256=" + sha256(es));
    }

    @Test
    void dsaRoutesSelectTheirOwnPane() throws Exception {
        assertAsymmetricRoutes("DSA Key Generation", "dsaKeySizeCombo");
    }

    private void assertAsymmetricRoutes(String section, String controlId) throws Exception {
        var aliases = UiNavigationRegistry.routes().entrySet().stream()
                .filter(entry -> entry.getValue().module() == UiNavigationRegistry.Module.KEYS_ASYMMETRIC
                        && section.equals(entry.getValue().section()))
                .map(Map.Entry::getKey).sorted().toList();
        assertTrue(!aliases.isEmpty());
        for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
            for (String alias : aliases) {
                fx(() -> {
                    I18nService.getInstance().setPreference(language);
                    shell.navigateToModule(alias);
                    root.applyCss();
                    root.layout();
                    Accordion accordion = findAccordion(root.lookup("#asymmetricKeysContainer"));
                    assertNotNull(accordion);
                    TitledPane expected = accordion.getPanes().stream()
                            .filter(pane -> pane.getContent().lookup("#" + controlId) != null)
                            .findFirst().orElseThrow();
                    assertSame(expected, accordion.getExpandedPane(), language + " " + alias);
                });
            }
        }
    }

    private static Accordion findAccordion(Node node) {
        if (node instanceof Accordion accordion) return accordion;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Accordion found = findAccordion(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** These modules route to sections, with no canonical accordion pane in Route.section(). */
    @Test
    void sectionOnlyAccordionModulesAreRecordedSeparately() throws Exception {
        List<UiNavigationRegistry.Module> modules = List.of(UiNavigationRegistry.Module.JOSE,
                UiNavigationRegistry.Module.COSE, UiNavigationRegistry.Module.WALLET);
        var routes = UiNavigationRegistry.routes().entrySet().stream()
                .filter(entry -> modules.contains(entry.getValue().module()))
                .sorted(Map.Entry.comparingByKey()).toList();
        Map<UiNavigationRegistry.Module, String> warmRoutes = new java.util.EnumMap<>(UiNavigationRegistry.Module.class);
        routes.forEach(entry -> warmRoutes.putIfAbsent(entry.getValue().module(), entry.getKey()));
        fx(() -> {
            I18nService.getInstance().setPreference(LanguagePreference.EN);
            for (String route : warmRoutes.values()) shell.navigateToModule(route);
            root.applyCss();
            root.layout();
        });
        List<String> rows = new ArrayList<>();
        for (LanguagePreference language : List.of(LanguagePreference.EN, LanguagePreference.ES)) {
            for (var entry : routes) {
                AtomicReference<String> row = new AtomicReference<>();
                fx(() -> {
                    I18nService.getInstance().setPreference(language);
                    Node host = root.lookup("#" + entry.getValue().module().name().toLowerCase(java.util.Locale.ROOT));
                    assertNotNull(host);
                    assertEquals(null, entry.getValue().section(), "section routes have no pane destination");
                    setCollapsed(host);
                    shell.navigateToModule(entry.getKey());
                    root.applyCss();
                    root.layout();
                    assertTrue(!breadcrumb().isBlank() && !breadcrumb().equals("<missing>"));
                    row.set(language.name() + "\t" + safe(entry.getKey()) + "\t" + entry.getValue().module().name()
                            + "\t" + selectedPane(host) + "\t" + breadcrumb());
                });
                rows.add(row.get());
            }
        }
        String content = String.join("\n", rows) + "\n";
        try (var stream = getClass().getResourceAsStream("/com/cryptocarver/ui/module-section-navigation-baseline.tsv")) {
            assertNotNull(stream);
            assertEquals(new String(stream.readAllBytes(), StandardCharsets.UTF_8), content);
        }
        String en = String.join("\n", rows.stream().filter(row -> row.startsWith("EN\t")).toList()) + "\n";
        String es = String.join("\n", rows.stream().filter(row -> row.startsWith("ES\t")).toList()) + "\n";
        assertEquals("d724ed9eea9748381aa32d7012c2553733ef147d09991ea7e2015bb61b41d8af", sha256(en));
        assertEquals("7029a5c66e7b736b7ac6cac553f9cdb184de1989e24c98e46dce458831e9a17c", sha256(es));
    }

    private String breadcrumb() {
        Node node = root.lookup("#breadcrumbContainer");
        if (!(node instanceof Parent parent)) return "<missing>";
        List<String> parts = new ArrayList<>();
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Labeled labeled && labeled.isVisible() && !labeled.getText().isBlank()
                    && !labeled.getText().trim().equals("›"))
                parts.add(labeled.getText().trim());
        }
        return safe(String.join(" > ", parts));
    }

    private static String hostId(UiNavigationRegistry.Module module) {
        return switch (module) {
            case KEYS_SYMMETRIC, KEYS_ASYMMETRIC -> "keysContainer";
            case CERTIFICATES -> "certificatesContainer";
            case GENERIC -> "genericContainer";
            case POST_QUANTUM -> "postQuantumContainer";
            case XML_SECURITY -> "xmlSecurityContainer";
            case WSS_SECURITY -> "wssSecurityContainer";
            case EMV -> "emvContainer";
            case CIPHER -> "cipherContainer";
            case AUTHENTICATION -> "authenticationContainer";
            case PAYMENTS -> "paymentsContainer";
            default -> throw new IllegalArgumentException("Not an accordion module: " + module);
        };
    }

    private static void setCollapsed(Node node) {
        if (node instanceof Accordion accordion) accordion.setExpandedPane(null);
        if (node instanceof TitledPane pane) pane.setExpanded(false);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) setCollapsed(child);
        }
    }

    private static String selectedPane(Node host) {
        List<String> expanded = new ArrayList<>();
        collectExpanded(host, host, expanded);
        return expanded.isEmpty() ? "<none>" : String.join(" + ", expanded);
    }

    private static void collectExpanded(Node node, Node host, List<String> expanded) {
        if (!visibleWithin(node, host)) return;
        if (node instanceof TitledPane pane && pane.isExpanded()) {
            expanded.add(pane.getText());
            return;
        }
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) collectExpanded(child, host, expanded);
    }

    private static boolean visibleWithin(Node node, Node host) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
            if (current == host) return true;
        }
        return false;
    }

    private static String safe(String value) {
        if (value == null) return "<null>";
        String sanitized = value;
        for (String prefix : vendorPrefixes())
            sanitized = sanitized.replaceAll("(?i)" + java.util.regex.Pattern.quote(prefix), "");
        return sanitized.trim().replace('\t', ' ').replace('\n', ' ');
    }

    private static List<String> vendorPrefixes() {
        List<String> prefixes = new ArrayList<>();
        for (UiNavigationRegistry.Route route : UiNavigationRegistry.routes().values()) {
            String section = route.section();
            if (section == null) continue;
            for (String suffix : List.of(" Variant LMK", " Host Command Bank")) {
                int offset = section.lastIndexOf(suffix);
                if (offset > 0) prefixes.add(section.substring(0, offset));
            }
        }
        return prefixes.stream().distinct().toList();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    @FunctionalInterface private interface FxAction { void run() throws Exception; }
    private static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(45, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
