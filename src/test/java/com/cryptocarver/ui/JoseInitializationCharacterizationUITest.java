package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Production FXML, deterministic logical control tree, and one stimulus per fresh fixture. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class JoseInitializationCharacterizationUITest {
    @BeforeAll static void start() throws Exception { JoseCharacterizationSupport.startFx(); }

    @Test void initialControlsAndEveryInitializationListener() throws Exception {
        UiTestLifecycleExtension.onFx(() -> {
            LanguagePreference previous = AppSettings.getInstance().getLanguagePreference();
            List<String> lines = new ArrayList<>();
            try {
                withFixture(p -> snapshot(p).forEach((id, value) -> lines.add("initial/" + id + "=" + value)));
                for (String id : List.of("jwtAlgoCombo", "jwtAlgo2Combo", "detachedAlgoCombo", "nestedSignAlgoCombo"))
                    stimulus(lines, id, p -> p.combo(id).setValue("none"));
                for (String id : List.of("jweKeyAlgoCombo", "nestedKeyAlgoCombo", "jwksRotateAlgoCombo"))
                    stimulus(lines, id, p -> p.combo(id).setValue("RSA1_5"));
                for (String id : List.of("jwtKeyArea", "jwtKeyArea2", "detachedSigningKeyArea", "nestedSigningKeyArea",
                        "jwtValidateTokenArea", "jwtValidateKeyArea", "jwePublicKeyArea", "jweInputArea", "jwePrivateKeyArea"))
                    stimulus(lines, id, p -> p.area(id).setText("invented-init-78"));
                stimulus(lines, "jwkTypeOCT", p -> p.combo("jwkKeyTypeCombo").setValue("OCT"));
                stimulus(lines, "jwkTypeOKP", p -> p.combo("jwkKeyTypeCombo").setValue("OKP"));
                stimulus(lines, "jwkCurve", p -> p.combo("jwkCurveCombo").setValue("X448"));
                stimulus(lines, "jwkRotation", p -> p.combo("jwksRotateAlgoCombo").setValue("ECDH-ES-X448"));
                for (String template : List.of("OAuth2 Access Token (JWT)", "OIDC ID Token", "DPoP Proof", "Custom (Empty)"))
                    stimulus(lines, "template/" + template, p -> p.combo("jwtTemplateCombo").setValue(template));
                stimulus(lines, "localeES", p -> p.language(LanguagePreference.ES));
                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    withFixture(p -> {
                        AppSettings.getInstance().setSecretVisibilityProfile(profile);
                        TextArea area = p.area("detachedProtectedHeaderSecretArea");
                        List<String> statuses = new ArrayList<>();
                        p.controller.setReporter(new JoseStatusRecorder(statuses));
                        int[] delivered = {0};
                        area.addEventHandler(KeyEvent.KEY_PRESSED, e -> delivered[0]++);
                        area.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> delivered[0]++);
                        area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, true, false, true));
                        area.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 0, 0, 0, 0, false, null));
                        assertEquals(0, delivered[0], profile + " capture filters");
                        assertEquals(2, statuses.size(), profile + " capture statuses");
                        lines.add(profile + "/detachedCapture=" + statuses);
                    });
                }
                digest("61e52224fbedb92086d81b19dbd2f3b2aad54df991d7e9dcb8c637e5b011b6df", lines);
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { I18nService.getInstance().setPreference(previous); }
        });
    }

    private static final class JoseStatusRecorder implements StatusReporter {
        private final List<String> statuses;
        JoseStatusRecorder(List<String> statuses) { this.statuses = statuses; }
        @Override public void updateStatus(String message) { statuses.add(message); }
        @Override public void updateInspector(String operation, byte[] input, byte[] output,
                List<com.cryptocarver.model.OperationDetail> details) { }
        @Override public void showError(String title, String message) { fail(title + ": " + message); }
    }

    private static void withFixture(Consumer<JoseCharacterizationSupport> work) throws Exception {
        I18nService.getInstance().setPreference(LanguagePreference.EN);
        try (var p = new JoseCharacterizationSupport()) { work.accept(p); }
    }

    private static void stimulus(List<String> lines, String name, Consumer<JoseCharacterizationSupport> event) throws Exception {
        withFixture(p -> {
            Map<String, String> before = snapshot(p);
            event.accept(p);
            lines.add("stimulus=" + name);
            snapshot(p).forEach((id, value) -> {
                if (!Objects.equals(before.get(id), value)) lines.add(name + "/" + id + "=" + value);
            });
            assertTrue(p.reporter.history.isEmpty(), "wiring does not publish history");
        });
    }

    private static Map<String, String> snapshot(JoseCharacterizationSupport p) {
        Map<String, String> states = new TreeMap<>();
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        // Named controls also include controls in panes that have not acquired a skin.
        new TreeMap<>(p.loader.getNamespace()).forEach((id, value) -> {
            if (value instanceof Node node) {
                states.put(id, state(node)); visited.add(node);
            } else if (value instanceof TableColumn<?, ?> column) {
                states.put(id, "TableColumn:text=" + column.getText() + ",visible=" + column.isVisible());
            }
        });
        walk(p.control("joseContainer"), "root", states, visited);
        return states;
    }

    private static void walk(Node node, String path, Map<String, String> states, Set<Node> visited) {
        if (node instanceof Control && visited.add(node)) states.put(path, state(node));
        List<Node> children = new ArrayList<>();
        if (node instanceof Accordion accordion) children.addAll(accordion.getPanes());
        else if (node instanceof ScrollPane scroll && scroll.getContent() != null) children.add(scroll.getContent());
        else if (node instanceof TitledPane pane && pane.getContent() != null) children.add(pane.getContent());
        else if (node instanceof TabPane tabs) {
            for (Tab tab : tabs.getTabs()) if (tab.getContent() != null) children.add(tab.getContent());
        } else if (node instanceof SplitPane split) children.addAll(split.getItems());
        else if (node instanceof Parent parent) children.addAll(parent.getChildrenUnmodifiable());
        for (int i = 0; i < children.size(); i++) walk(children.get(i), path + "/" + i, states, visited);
    }

    private static String state(Node node) {
        String value = node.getClass().getSimpleName() + ":visible=" + node.isVisible()
                + ",managed=" + node.isManaged() + ",disabled=" + node.isDisabled();
        if (node instanceof TextInputControl text) value += ",text=" + text.getText() + ",prompt=" + text.getPromptText();
        if (node instanceof Labeled label) value += ",text=" + label.getText();
        if (node instanceof CheckBox check) value += ",selected=" + check.isSelected();
        if (node instanceof ComboBox<?> combo) value += ",value=" + combo.getValue() + ",prompt=" + combo.getPromptText() + ",index="
                + combo.getSelectionModel().getSelectedIndex() + ",items=" + combo.getItems();
        if (node instanceof TableView<?> table) value += ",items=" + table.getItems();
        return value.replaceAll("(\"(?:iat|exp)\": )\\d+", "$1<TIME>")
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<UUID>")
                .replace("\r\n", "\n").replace("\n", "\\n");
    }

    private static void digest(String expected, List<String> lines) throws Exception {
        String transcript = String.join("\n", lines);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(transcript.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, actual, transcript);
    }
}
